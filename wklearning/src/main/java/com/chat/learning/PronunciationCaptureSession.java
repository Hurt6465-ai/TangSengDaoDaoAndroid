package com.chat.learning;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Captures one microphone stream, saves it as WAV and feeds the same PCM stream to
 * the Android speech recognizer on Android 13+.
 *
 * On older Android versions the platform has no public audio-injection API, so the
 * class keeps a best-effort simultaneous AudioRecord + SpeechRecognizer fallback.
 */
final class PronunciationCaptureSession {
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNEL_COUNT = 1;
    private static final int BITS_PER_SAMPLE = 16;
    private static final long MAX_CAPTURE_MS = 7_000L;
    private static final long RESULT_GRACE_MS = 2_500L;

    // API 33 RecognizerIntent constants are kept as strings so this module still
    // compiles in host projects whose compile SDK has not yet exposed the fields.
    private static final String EXTRA_AUDIO_SOURCE = "android.speech.extra.AUDIO_SOURCE";
    private static final String EXTRA_AUDIO_SOURCE_CHANNEL_COUNT =
            "android.speech.extra.AUDIO_SOURCE_CHANNEL_COUNT";
    private static final String EXTRA_AUDIO_SOURCE_ENCODING =
            "android.speech.extra.AUDIO_SOURCE_ENCODING";
    private static final String EXTRA_AUDIO_SOURCE_SAMPLING_RATE =
            "android.speech.extra.AUDIO_SOURCE_SAMPLING_RATE";
    private static final String EXTRA_BIASING_STRINGS = "android.speech.extra.BIASING_STRINGS";

    interface Listener {
        void onStateChanged(State state);
        void onRms(float rmsDb);
        void onPartialResult(String text);
        void onFinished(Result result);
    }

    enum State {
        PREPARING,
        LISTENING,
        PROCESSING
    }

    static final class Result {
        final String recognizedText;
        final float recognizerConfidence;
        final File recordingFile;
        final int recognitionError;
        final boolean sharedPcmStream;

        Result(String recognizedText, float recognizerConfidence, File recordingFile,
               int recognitionError, boolean sharedPcmStream) {
            this.recognizedText = recognizedText == null ? "" : recognizedText;
            this.recognizerConfidence = recognizerConfidence;
            this.recordingFile = recordingFile;
            this.recognitionError = recognitionError;
            this.sharedPcmStream = sharedPcmStream;
        }
    }

    private final Context context;
    private final String targetWord;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private AudioRecord audioRecord;
    private Thread captureThread;
    private ParcelFileDescriptor pipeRead;
    private ParcelFileDescriptor pipeWrite;
    private File rawFile;
    private File wavFile;

    private volatile boolean captureRunning;
    private volatile boolean released;
    private boolean recordingDone;
    private boolean recognitionDone;
    private boolean delivered;
    private boolean sharedPcmStream;
    private String recognizedText = "";
    private float recognizerConfidence = -1f;
    private int recognitionError;

    private final Runnable maxDurationStop = this::stop;
    private final Runnable resultTimeout = () -> {
        if (!recognitionDone) {
            recognitionDone = true;
            if (recognitionError == 0) recognitionError = SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        }
        maybeDeliver();
    };

    PronunciationCaptureSession(Context context, String targetWord, Listener listener) {
        this.context = context.getApplicationContext();
        this.targetWord = targetWord == null ? "" : targetWord.trim();
        this.listener = listener;
    }

    void start() {
        if (released || captureRunning) return;
        resetResultState();
        listener.onStateChanged(State.PREPARING);

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            recognitionDone = true;
            recognitionError = SpeechRecognizer.ERROR_CLIENT;
            startCapture(false);
            return;
        }

        sharedPcmStream = Build.VERSION.SDK_INT >= 33;
        try {
            if (sharedPcmStream) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                pipeRead = pipe[0];
                pipeWrite = pipe[1];
            }

            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new InternalRecognitionListener());

            Intent intent = createRecognitionIntent();
            if (sharedPcmStream && pipeRead != null) {
                intent.putExtra(EXTRA_AUDIO_SOURCE, pipeRead);
                intent.putExtra(EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, CHANNEL_COUNT);
                intent.putExtra(EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                intent.putExtra(EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE);
            }

            if (sharedPcmStream) {
                recognizer.startListening(intent);
                startCapture(true);
            } else {
                // Older Android has no public way to inject one captured stream into
                // SpeechRecognizer. Start recording first, then recognition as the
                // most compatible best-effort fallback.
                startCapture(false);
                recognizer.startListening(intent);
            }
        } catch (Throwable error) {
            recognitionDone = true;
            recognitionError = SpeechRecognizer.ERROR_CLIENT;
            closePipeRead();
            closePipeWrite();
            if (!captureRunning) startCapture(false);
        }
    }

    void stop() {
        if (released) return;
        if (captureRunning) {
            captureRunning = false;
            try {
                if (audioRecord != null && audioRecord.getRecordingState()
                        == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (Throwable ignored) { }
        }
        listener.onStateChanged(State.PROCESSING);
        main.removeCallbacks(maxDurationStop);
        main.removeCallbacks(resultTimeout);
        main.postDelayed(resultTimeout, RESULT_GRACE_MS);
    }

    void release() {
        released = true;
        main.removeCallbacks(maxDurationStop);
        main.removeCallbacks(resultTimeout);
        captureRunning = false;
        try {
            if (audioRecord != null && audioRecord.getRecordingState()
                    == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
        } catch (Throwable ignored) { }
        try {
            if (recognizer != null) recognizer.cancel();
        } catch (Throwable ignored) { }
        try {
            if (recognizer != null) recognizer.destroy();
        } catch (Throwable ignored) { }
        recognizer = null;
        closePipeWrite();
        closePipeRead();
    }

    private void resetResultState() {
        main.removeCallbacks(maxDurationStop);
        main.removeCallbacks(resultTimeout);
        recordingDone = false;
        recognitionDone = false;
        delivered = false;
        recognizedText = "";
        recognizerConfidence = -1f;
        recognitionError = 0;
    }

    private Intent createRecognitionIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 350L);
        if (!targetWord.isEmpty()) {
            ArrayList<String> bias = new ArrayList<>();
            bias.add(targetWord);
            intent.putStringArrayListExtra(EXTRA_BIASING_STRINGS, bias);
        }
        return intent;
    }

    private void startCapture(boolean writeToRecognizerPipe) {
        try {
            int minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minBuffer <= 0) minBuffer = SAMPLE_RATE;
            int bufferSize = Math.max(minBuffer * 2, 4096);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord not initialized");
            }

            File directory = new File(context.getCacheDir(), "learning-pronunciation");
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("Cannot create pronunciation cache directory");
            }
            long stamp = System.currentTimeMillis();
            rawFile = new File(directory, "practice_" + stamp + ".pcm");
            wavFile = new File(directory, "practice_" + stamp + ".wav");

            audioRecord.startRecording();
            captureRunning = true;
            listener.onStateChanged(State.LISTENING);
            main.postDelayed(maxDurationStop, MAX_CAPTURE_MS);

            final int finalBufferSize = bufferSize;
            captureThread = new Thread(
                    () -> captureLoop(finalBufferSize, writeToRecognizerPipe),
                    "word-pronunciation-capture");
            captureThread.start();
        } catch (Throwable error) {
            captureRunning = false;
            releaseAudioRecord();
            closePipeWrite();
            recordingDone = true;
            recognitionDone = true;
            recognitionError = SpeechRecognizer.ERROR_AUDIO;
            maybeDeliver();
        }
    }

    private void captureLoop(int bufferSize, boolean writeToRecognizerPipe) {
        long totalPcmBytes = 0L;
        OutputStream recognizerOutput = null;
        try (FileOutputStream rawOutput = new FileOutputStream(rawFile)) {
            if (writeToRecognizerPipe && pipeWrite != null) {
                recognizerOutput = new ParcelFileDescriptor.AutoCloseOutputStream(pipeWrite);
                pipeWrite = null; // AutoCloseOutputStream now owns it.
            }
            byte[] buffer = new byte[bufferSize];
            while (captureRunning && !released) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    rawOutput.write(buffer, 0, read);
                    totalPcmBytes += read;
                    if (recognizerOutput != null) {
                        try {
                            recognizerOutput.write(buffer, 0, read);
                            recognizerOutput.flush();
                        } catch (IOException pipeClosed) {
                            closeQuietly(recognizerOutput);
                            recognizerOutput = null;
                        }
                    }
                    final float rms = calculateRmsDb(buffer, read);
                    main.post(() -> {
                        if (!released) listener.onRms(rms);
                    });
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION
                        || read == AudioRecord.ERROR_BAD_VALUE
                        || read == AudioRecord.ERROR_DEAD_OBJECT) {
                    break;
                }
            }
            rawOutput.flush();
        } catch (Throwable ignored) {
            if (recognitionError == 0) recognitionError = SpeechRecognizer.ERROR_AUDIO;
        } finally {
            closeQuietly(recognizerOutput);
            releaseAudioRecord();
            captureRunning = false;
            try {
                if (rawFile != null && rawFile.isFile() && totalPcmBytes > 0) {
                    writeWav(rawFile, wavFile, totalPcmBytes);
                }
            } catch (Throwable ignored) { }
            if (rawFile != null) {
                //noinspection ResultOfMethodCallIgnored
                rawFile.delete();
            }
            recordingDone = true;
            main.post(() -> {
                if (!released) {
                    listener.onStateChanged(State.PROCESSING);
                    maybeDeliver();
                }
            });
        }
    }

    private void maybeDeliver() {
        if (released || delivered || !recordingDone || !recognitionDone) return;
        delivered = true;
        main.removeCallbacks(maxDurationStop);
        main.removeCallbacks(resultTimeout);
        closePipeRead();
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Throwable ignored) { }
            recognizer = null;
        }
        File output = wavFile != null && wavFile.isFile() && wavFile.length() > 44 ? wavFile : null;
        listener.onFinished(new Result(
                recognizedText,
                recognizerConfidence,
                output,
                recognitionError,
                sharedPcmStream));
    }

    private void releaseAudioRecord() {
        try { if (audioRecord != null) audioRecord.release(); } catch (Throwable ignored) { }
        audioRecord = null;
    }

    private void closePipeRead() {
        try { if (pipeRead != null) pipeRead.close(); } catch (Throwable ignored) { }
        pipeRead = null;
    }

    private void closePipeWrite() {
        try { if (pipeWrite != null) pipeWrite.close(); } catch (Throwable ignored) { }
        pipeWrite = null;
    }

    private void closeQuietly(OutputStream output) {
        try { if (output != null) output.close(); } catch (Throwable ignored) { }
    }

    private float calculateRmsDb(byte[] pcm, int length) {
        if (length < 2) return -80f;
        double sum = 0.0;
        int samples = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (pcm[i] & 0xFF) | (pcm[i + 1] << 8);
            sum += sample * (double) sample;
            samples++;
        }
        if (samples == 0 || sum <= 0.0) return -80f;
        double rms = Math.sqrt(sum / samples);
        return (float) (20.0 * Math.log10(rms / 32768.0 + 1e-9));
    }

    private void writeWav(File raw, File wav, long pcmLength) throws IOException {
        try (FileInputStream input = new FileInputStream(raw);
             FileOutputStream output = new FileOutputStream(wav)) {
            long dataLength = pcmLength + 36;
            byte[] header = new byte[44];
            header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
            writeLittleEndianInt(header, 4, dataLength);
            header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
            header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
            writeLittleEndianInt(header, 16, 16);
            writeLittleEndianShort(header, 20, 1);
            writeLittleEndianShort(header, 22, CHANNEL_COUNT);
            writeLittleEndianInt(header, 24, SAMPLE_RATE);
            int byteRate = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8;
            writeLittleEndianInt(header, 28, byteRate);
            writeLittleEndianShort(header, 32, CHANNEL_COUNT * BITS_PER_SAMPLE / 8);
            writeLittleEndianShort(header, 34, BITS_PER_SAMPLE);
            header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
            writeLittleEndianInt(header, 40, pcmLength);
            output.write(header);

            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        }
    }

    private void writeLittleEndianInt(byte[] target, int offset, long value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private void writeLittleEndianShort(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private final class InternalRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {
            if (!released) listener.onStateChanged(State.LISTENING);
        }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { stop(); }

        @Override public void onError(int error) {
            recognitionError = error;
            recognitionDone = true;
            stop();
            maybeDeliver();
        }

        @Override public void onResults(Bundle results) {
            ArrayList<String> values = results == null ? null
                    : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (values != null && !values.isEmpty() && values.get(0) != null) {
                recognizedText = values.get(0).trim();
            }
            float[] confidence = results == null ? null
                    : results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (confidence != null && confidence.length > 0) recognizerConfidence = confidence[0];
            recognitionDone = true;
            stop();
            maybeDeliver();
        }

        @Override public void onPartialResults(Bundle partialResults) {
            ArrayList<String> values = partialResults == null ? null
                    : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (values != null && !values.isEmpty() && values.get(0) != null && !released) {
                listener.onPartialResult(values.get(0).trim());
            }
        }

        @Override public void onEvent(int eventType, Bundle params) { }
    }
}
