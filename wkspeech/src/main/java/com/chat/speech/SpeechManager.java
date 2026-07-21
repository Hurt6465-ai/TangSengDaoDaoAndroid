package com.chat.speech;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.chat.speech.engine.EdgeTtsEngine;
import com.chat.speech.engine.MsTranslatorCompatibleEngine;
import com.chat.speech.engine.SystemTtsEngine;
import com.chat.speech.model.SpeechSegment;
import com.chat.speech.model.TtsSource;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/** Central TTS router: Edge -> Microsoft compatible source -> Android system TTS. */
public class SpeechManager {
    private static final String TAG = "SpeechManager";
    private static SpeechManager instance;

    private final Context app;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final EdgeTtsEngine edgeEngine = new EdgeTtsEngine();
    private final MsTranslatorCompatibleEngine msEngine = new MsTranslatorCompatibleEngine();
    private final AtomicLong requestGeneration = new AtomicLong(0L);
    private final Object taskLock = new Object();

    private Future<?> activeTask;
    private MediaPlayer currentPlayer;
    private long lastFallbackToastAt;

    private SpeechManager(Context context) {
        app = context.getApplicationContext();
    }

    public static synchronized SpeechManager get(Context context) {
        if (instance == null) instance = new SpeechManager(context);
        return instance;
    }

    public static void speak(Context context, String text) {
        get(context).speakAuto(text);
    }

    public void speakAuto(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String cleanText = text.trim();
        long generation = requestGeneration.incrementAndGet();
        cancelCurrentWork();

        SpeechPrefs prefs = new SpeechPrefs(app);
        TtsSource activeSource = prefs.getActiveSource();
        if (activeSource == null || TtsSource.TYPE_SYSTEM.equals(activeSource.type)) {
            SystemTtsEngine.get(app).speak(
                    cleanText,
                    prefs.getSystemRate(),
                    prefs.getSystemPitch()
            );
            return;
        }

        synchronized (taskLock) {
            activeTask = executor.submit(() -> synthesizeAndPlay(
                    generation,
                    cleanText,
                    prefs,
                    activeSource
            ));
        }
    }

    public void stop() {
        requestGeneration.incrementAndGet();
        cancelCurrentWork();
    }

    private void synthesizeAndPlay(
            long generation,
            String originalText,
            SpeechPrefs prefs,
            TtsSource activeSource
    ) {
        try {
            List<SpeechSegment> segments = buildSegments(originalText, prefs.isMixedReadEnabled());
            SourcePlan sourcePlan = new SourcePlan(buildOnlineSourceOrder(prefs, activeSource));
            if (sourcePlan.sources.isEmpty()) {
                throw new IllegalStateException("没有可用的在线语音源");
            }

            List<File> files = new ArrayList<>();
            for (SpeechSegment segment : segments) {
                ensureCurrent(generation);
                files.add(synthesizeSegment(segment, prefs, sourcePlan));
            }
            ensureCurrent(generation);
            mainHandler.post(() -> {
                if (isCurrent(generation)) playFiles(files, 0, generation);
            });
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            Log.w(TAG, "Online TTS failed, falling back to system TTS", error);
            mainHandler.post(() -> {
                if (!isCurrent(generation)) return;
                maybeShowFallbackToast();
                SystemTtsEngine.get(app).speak(
                        originalText,
                        prefs.getSystemRate(),
                        prefs.getSystemPitch()
                );
            });
        }
    }

    private File synthesizeSegment(
            SpeechSegment segment,
            SpeechPrefs prefs,
            SourcePlan plan
    ) throws Exception {
        Exception lastError = null;
        for (int index = plan.preferredIndex; index < plan.sources.size(); index++) {
            TtsSource source = plan.sources.get(index);
            if (!TtsCircuitBreaker.canAttempt(app, source.id)) continue;
            try {
                String voice = prefs.voiceForLang(segment.lang);
                String locale = SpeechSegment.LANG_MY.equals(segment.lang)
                        ? SpeechSegment.LANG_MY
                        : SpeechSegment.LANG_ZH;
                String format = source.audioFormat == null || source.audioFormat.trim().isEmpty()
                        ? SpeechPrefs.DEFAULT_AUDIO_FORMAT
                        : source.audioFormat.trim();
                File file;
                if (TtsSource.TYPE_EDGE_WEBSOCKET.equals(source.type)) {
                    file = edgeEngine.synthesize(
                            app,
                            source,
                            segment.text,
                            voice,
                            locale,
                            format,
                            prefs.getRatePercent(),
                            prefs.getPitchPercent()
                    );
                } else if (TtsSource.TYPE_MS_TRANSLATOR.equals(source.type)) {
                    file = msEngine.synthesize(
                            app,
                            source,
                            segment.text,
                            voice,
                            locale,
                            format,
                            prefs.getRatePercent(),
                            prefs.getPitchPercent()
                    );
                } else {
                    continue;
                }
                TtsCircuitBreaker.recordSuccess(app, source.id);
                plan.preferredIndex = index;
                return file;
            } catch (InterruptedException cancelled) {
                throw cancelled;
            } catch (Exception error) {
                lastError = error;
                TtsCircuitBreaker.recordFailure(app, source.id);
                Log.w(TAG, "TTS source failed: " + source.name, error);
            }
        }
        throw lastError == null
                ? new IllegalStateException("在线语音源处于临时熔断状态")
                : lastError;
    }

    private List<TtsSource> buildOnlineSourceOrder(SpeechPrefs prefs, TtsSource activeSource) {
        List<TtsSource> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        if (activeSource != null && activeSource.canSpeakOnline()) {
            addSource(result, added, activeSource);
        }

        if (activeSource != null && TtsSource.TYPE_MS_TRANSLATOR.equals(activeSource.type)) {
            addSource(result, added, prefs.getSourceById(TtsSource.edgeWebSocketTemplate().id));
        } else {
            addSource(result, added, prefs.getSourceById(TtsSource.builtinMsTranslator().id));
        }

        // Imported or malformed active sources still get the recommended online chain.
        if (result.isEmpty()) {
            addSource(result, added, prefs.getSourceById(TtsSource.edgeWebSocketTemplate().id));
            addSource(result, added, prefs.getSourceById(TtsSource.builtinMsTranslator().id));
        }
        return result;
    }

    private static void addSource(
            List<TtsSource> result,
            Set<String> added,
            TtsSource source
    ) {
        if (source == null || !source.canSpeakOnline()) return;
        source.normalize();
        if (added.add(source.id)) result.add(source);
    }

    private static List<SpeechSegment> buildSegments(String text, boolean mixedReadEnabled) {
        if (mixedReadEnabled) return SpeechSegmenter.splitByLanguage(text);
        List<SpeechSegment> segments = new ArrayList<>();
        segments.add(new SpeechSegment(text.trim(), SpeechSegment.LANG_ZH));
        return segments;
    }

    private void cancelCurrentWork() {
        synchronized (taskLock) {
            if (activeTask != null) {
                activeTask.cancel(true);
                activeTask = null;
            }
        }
        edgeEngine.cancelActive();
        msEngine.cancelActive();
        try {
            SystemTtsEngine.get(app).stop();
        } catch (Throwable ignored) {
        }
        mainHandler.post(this::releaseCurrentPlayer);
    }

    private void playFiles(List<File> files, int index, long generation) {
        if (!isCurrent(generation) || files == null || index >= files.size()) return;
        File file = files.get(index);
        try {
            releaseCurrentPlayer();
            MediaPlayer player = new MediaPlayer();
            currentPlayer = player;
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(completed -> {
                try {
                    completed.release();
                } catch (Throwable ignored) {
                }
                if (currentPlayer == completed) currentPlayer = null;
                if (isCurrent(generation)) playFiles(files, index + 1, generation);
            });
            player.setOnErrorListener((failed, what, extra) -> {
                try {
                    failed.release();
                } catch (Throwable ignored) {
                }
                if (currentPlayer == failed) currentPlayer = null;
                if (isCurrent(generation)) playFiles(files, index + 1, generation);
                return true;
            });
            player.prepare();
            if (isCurrent(generation)) {
                player.start();
            } else {
                releaseCurrentPlayer();
            }
        } catch (Exception error) {
            Log.w(TAG, "Unable to play synthesized audio", error);
            releaseCurrentPlayer();
            if (isCurrent(generation)) playFiles(files, index + 1, generation);
        }
    }

    private void releaseCurrentPlayer() {
        MediaPlayer player = currentPlayer;
        currentPlayer = null;
        if (player == null) return;
        try {
            player.stop();
        } catch (Throwable ignored) {
        }
        try {
            player.release();
        } catch (Throwable ignored) {
        }
    }

    private void maybeShowFallbackToast() {
        long now = System.currentTimeMillis();
        if (now - lastFallbackToastAt < 30_000L) return;
        lastFallbackToastAt = now;
        Toast.makeText(
                app,
                "在线自然语音暂不可用，已使用系统语音",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void ensureCurrent(long generation) throws InterruptedException {
        if (!isCurrent(generation) || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("语音请求已取消");
        }
    }

    private boolean isCurrent(long generation) {
        return requestGeneration.get() == generation;
    }

    private static final class SourcePlan {
        final List<TtsSource> sources;
        int preferredIndex;

        SourcePlan(List<TtsSource> sources) {
            this.sources = sources == null ? new ArrayList<>() : sources;
        }
    }
}
