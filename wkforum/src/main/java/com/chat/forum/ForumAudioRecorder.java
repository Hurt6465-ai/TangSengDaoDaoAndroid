package com.chat.forum;

import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Recorder used only by forum voice comments.
 *
 * Kept inside wkforum so pause/resume behavior cannot change IM chat recording.
 */
final class ForumAudioRecorder {
    interface LevelListener {
        void onLevel(int amplitude);
    }

    private enum State { IDLE, RECORDING, PAUSED }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> levels = new ArrayList<>();
    private final Runnable levelSampler = new Runnable() {
        @Override
        public void run() {
            if (state != State.RECORDING || recorder == null) return;
            try {
                double amplitude = recorder.getMaxAmplitude();
                double db = amplitude > 1d ? 20d * Math.log10(amplitude) : 0d;
                levels.add((int) db);
                LevelListener listener = levelListener;
                if (listener != null) listener.onLevel((int) amplitude);
            } catch (RuntimeException ignored) {
                // The recorder may be stopping while the sampler is running.
            }
            handler.postDelayed(this, 80L);
        }
    };

    private MediaRecorder recorder;
    private File outputFile;
    private State state = State.IDLE;
    private LevelListener levelListener;

    void setLevelListener(LevelListener listener) {
        levelListener = listener;
    }

    void start(@NonNull File file) throws IOException {
        release(false);
        outputFile = file;
        levels.clear();

        MediaRecorder next = new MediaRecorder();
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC);
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            next.setAudioEncodingBitRate(24_000);
            next.setAudioSamplingRate(16_000);
            next.setOutputFile(file.getAbsolutePath());
            next.prepare();
            next.start();
            recorder = next;
            state = State.RECORDING;
            handler.post(levelSampler);
        } catch (IOException | RuntimeException error) {
            try {
                next.release();
            } catch (Throwable ignored) {
            }
            recorder = null;
            outputFile = null;
            state = State.IDLE;
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("无法开始录音", error);
        }
    }

    void pause() {
        if (state != State.RECORDING || recorder == null) return;
        recorder.pause();
        handler.removeCallbacks(levelSampler);
        state = State.PAUSED;
    }

    void resume() {
        if (state != State.PAUSED || recorder == null) return;
        recorder.resume();
        state = State.RECORDING;
        handler.post(levelSampler);
    }

    void stop() {
        release(false);
    }

    void cancel() {
        File file = outputFile;
        release(false);
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @NonNull
    byte[] getLevels() {
        byte[] result = new byte[levels.size()];
        for (int i = 0; i < levels.size(); i++) {
            byte value = levels.get(i).byteValue();
            result[i] = value == 0 ? 2 : value;
        }
        return result;
    }

    private void release(boolean deleteFile) {
        handler.removeCallbacks(levelSampler);
        MediaRecorder current = recorder;
        File file = outputFile;
        recorder = null;
        outputFile = null;
        state = State.IDLE;
        if (current != null) {
            try {
                current.setOnErrorListener(null);
                current.setOnInfoListener(null);
                current.setPreviewDisplay(null);
                current.stop();
            } catch (RuntimeException ignored) {
                // A very short or failed recording may not have valid media data.
            } finally {
                try {
                    current.release();
                } catch (Throwable ignored) {
                }
            }
        }
        if (deleteFile && file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
