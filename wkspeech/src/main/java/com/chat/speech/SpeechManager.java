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
import com.chat.speech.debug.SpeechDebugLog;
import com.chat.speech.model.SpeechSegment;
import com.chat.speech.model.TtsSource;
import com.chat.speech.service.ByteDanceOfflineServiceClient;

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
    private final ByteDanceOfflineServiceClient byteDanceOfflineClient;
    private final AtomicLong requestGeneration = new AtomicLong(0L);
    private final Object taskLock = new Object();

    private Future<?> activeTask;
    private MediaPlayer currentPlayer;
    private long lastFallbackToastAt;

    private SpeechManager(Context context) {
        app = context.getApplicationContext();
        byteDanceOfflineClient = new ByteDanceOfflineServiceClient(app);
    }

    public static synchronized SpeechManager get(Context context) {
        if (instance == null) instance = new SpeechManager(context);
        return instance;
    }

    public static void speak(Context context, String text) {
        get(context).speakAuto(text);
    }

    /** Compatibility entry used by the learning module. */
    public static void speak(Context context, String text, String lang, String mode) {
        get(context).speakDetailed(text, null, lang, mode);
    }

    /** Learning entry that keeps both Hanzi and the teacher-provided pinyin. */
    public static void speak(
            Context context,
            String text,
            String pinyin,
            String lang,
            String mode
    ) {
        get(context).speakDetailed(text, pinyin, lang, mode);
    }

    public void speakAuto(String text) {
        if (text == null || text.trim().isEmpty()) return;
        SpeechPrefs prefs = new SpeechPrefs(app);
        TtsSource activeSource = prefs.getActiveSource();
        if (activeSource != null
                && TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(activeSource.type)
                && !containsMyanmar(text)
                && shouldUseOfflineForAuto(text)) {
            // Restore the known-working route used before the automatic-language rewrite:
            // Chinese and tone-marked pinyin go straight to the selected offline engine.
            speakDetailed(text, null, SpeechSegment.LANG_ZH, "word");
            return;
        }
        speakDetailed(text, null, SpeechSegment.LANG_OTHER, "auto");
    }

    private void speakDetailed(String text, String pinyin, String lang, String mode) {
        if (text == null || text.trim().isEmpty()) return;
        String cleanText = text.trim();
        long generation = requestGeneration.incrementAndGet();
        cancelCurrentWork(false);

        SpeechPrefs prefs = new SpeechPrefs(app);
        TtsSource activeSource = prefs.getActiveSource();
        String sourceId = activeSource == null ? "" : activeSource.id;
        String sourceType = activeSource == null ? "" : activeSource.type;
        SpeechDebugLog.append(app, "manager.route sourceId=" + sourceId
                + " sourceType=" + sourceType
                + " mode=" + safeMode(mode)
                + " lang=" + (lang == null ? "" : lang)
                + " byteReady=" + prefs.isByteDancePackageReady());

        if (activeSource == null || TtsSource.TYPE_SYSTEM.equals(activeSource.type)) {
            SystemTtsEngine.get(app).speak(
                    cleanText,
                    prefs.getSystemRate(),
                    prefs.getSystemPitch()
            );
            return;
        }

        if (TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(activeSource.type)
                && !prefs.isByteDancePackageReady()) {
            SpeechDebugLog.append(app, "manager.bytedance_not_ready root="
                    + prefs.getByteDancePackageRoot());
            showOfflineFailure(generation,
                    new IllegalStateException("拼音专用语音资源未准备好，请重新导入模型"));
            return;
        }

        synchronized (taskLock) {
            if (TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(activeSource.type)) {
                if ("auto".equalsIgnoreCase(mode)) {
                    activeTask = executor.submit(() -> synthesizeAutoWithByteDanceAndPlay(
                            generation,
                            cleanText,
                            prefs,
                            activeSource
                    ));
                } else {
                    activeTask = executor.submit(() -> synthesizeOfflineAndPlay(
                            generation,
                            cleanText,
                            pinyin,
                            lang,
                            mode,
                            prefs,
                            activeSource
                    ));
                }
            } else {
                activeTask = executor.submit(() -> synthesizeAndPlay(
                        generation,
                        cleanText,
                        prefs,
                        activeSource
                ));
            }
        }
    }

    public void stop() {
        requestGeneration.incrementAndGet();
        cancelCurrentWork(true);
    }

    private void synthesizeAutoWithByteDanceAndPlay(
            long generation,
            String originalText,
            SpeechPrefs prefs,
            TtsSource activeSource
    ) {
        try {
            List<SpeechSegment> segments = SpeechSegmenter.splitByLanguage(originalText);
            if (segments.isEmpty()) {
                segments = new ArrayList<>();
                segments.add(new SpeechSegment(originalText.trim(), SpeechSegment.LANG_OTHER));
            }

            List<File> files = new ArrayList<>();
            SourcePlan onlinePlan = null;
            for (SpeechSegment segment : segments) {
                ensureCurrent(generation);
                boolean useOffline = SpeechSegment.LANG_ZH.equals(segment.lang)
                        || shouldUseOfflineForAuto(segment.text);
                if (useOffline) {
                    files.add(byteDanceOfflineClient.synthesize(
                            segment.text,
                            null,
                            "word",
                            prefs.getByteDanceVoice(),
                            prefs.getByteDanceSampleRate(),
                            prefs.getRatePercent(),
                            prefs.getPitchPercent()
                    ));
                } else {
                    if (onlinePlan == null) {
                        onlinePlan = new SourcePlan(buildOnlineSourceOrder(prefs, activeSource));
                        if (onlinePlan.sources.isEmpty()) {
                            throw new IllegalStateException("没有可用的自然语音源");
                        }
                    }
                    files.add(synthesizeSegment(segment, prefs, onlinePlan));
                }
            }
            ensureCurrent(generation);
            mainHandler.post(() -> {
                if (isCurrent(generation)) playFiles(files, 0, generation, 0L);
            });
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            // The user explicitly selected the imported offline voice. Never disguise an
            // offline failure by silently playing Android's system TTS; that made a broken
            // ByteDance route look successful and made diagnosis impossible.
            Log.w(TAG, "ByteDance automatic route failed; system fallback disabled", error);
            showOfflineFailure(generation, error);
        }
    }

    private void synthesizeOfflineAndPlay(
            long generation,
            String originalText,
            String pinyin,
            String lang,
            String mode,
            SpeechPrefs prefs,
            TtsSource activeSource
    ) {
        try {
            ensureCurrent(generation);
            if (SpeechSegment.LANG_MY.equals(lang) || containsMyanmar(originalText)) {
                throw new IllegalStateException("当前字节离线包只包含中文模型");
            }
            List<File> files = new ArrayList<>();
            boolean nativePinyinMode = "spelling".equalsIgnoreCase(mode)
                    && pinyin != null
                    && !pinyin.trim().isEmpty();

            if (nativePinyinMode) {
                // Keep the two inputs separate. First let the ByteDance frontend read the complete
                // tone-marked pinyin through its native pinyin path, then read the original Hanzi
                // once as a complete word or phrase, like a teacher finishing a spelling drill.
                SpeechDebugLog.append(app, "manager.spelling_sequence=pinyin_then_word");
                files.add(byteDanceOfflineClient.synthesize(
                        originalText,
                        pinyin,
                        "spelling",
                        prefs.getByteDanceVoice(),
                        prefs.getByteDanceSampleRate(),
                        prefs.getRatePercent(),
                        prefs.getPitchPercent()
                ));
                SpeechDebugLog.append(app, "manager.spelling_pinyin_ready");
                ensureCurrent(generation);
                files.add(byteDanceOfflineClient.synthesize(
                        originalText,
                        null,
                        "word",
                        prefs.getByteDanceVoice(),
                        prefs.getByteDanceSampleRate(),
                        prefs.getRatePercent(),
                        prefs.getPitchPercent()
                ));
                SpeechDebugLog.append(app, "manager.spelling_word_ready");
            } else {
                files.add(byteDanceOfflineClient.synthesize(
                        originalText,
                        pinyin,
                        mode,
                        prefs.getByteDanceVoice(),
                        prefs.getByteDanceSampleRate(),
                        prefs.getRatePercent(),
                        prefs.getPitchPercent()
                ));
            }
            ensureCurrent(generation);
            long interFileDelayMs = nativePinyinMode ? 280L : 0L;
            mainHandler.post(() -> {
                if (isCurrent(generation)) playFiles(files, 0, generation, interFileDelayMs);
            });
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            Log.w(TAG, "ByteDance offline TTS failed; system fallback disabled", error);
            showOfflineFailure(generation, error);
        }
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
                if (isCurrent(generation)) playFiles(files, 0, generation, 0L);
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
        } else if (activeSource != null && TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(activeSource.type)) {
            addSource(result, added, prefs.getSourceById(TtsSource.edgeWebSocketTemplate().id));
            addSource(result, added, prefs.getSourceById(TtsSource.builtinMsTranslator().id));
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

    private void cancelCurrentWork(boolean stopRemoteOfflineEngine) {
        synchronized (taskLock) {
            if (activeTask != null) {
                activeTask.cancel(true);
                activeTask = null;
            }
        }
        edgeEngine.cancelActive();
        msEngine.cancelActive();
        // Starting the next word must not send STOP_ENGINE to the reusable ByteDance process.
        // The vendor SDK returns -900 when a stopped engine is reused. The old request result is
        // already discarded by requestGeneration; only an explicit user stop tears down the
        // remote offline engine. Its single-thread service serializes any request still finishing.
        if (stopRemoteOfflineEngine) byteDanceOfflineClient.cancelActive();
        try {
            SystemTtsEngine.get(app).stop();
        } catch (Throwable ignored) {
        }
        mainHandler.post(this::releaseCurrentPlayer);
    }

    private void playFiles(
            List<File> files,
            int index,
            long generation,
            long interFileDelayMs
    ) {
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
                if (!isCurrent(generation)) return;
                Runnable next = () -> playFiles(files, index + 1, generation, interFileDelayMs);
                if (interFileDelayMs > 0L && index + 1 < files.size()) {
                    mainHandler.postDelayed(next, interFileDelayMs);
                } else {
                    next.run();
                }
            });
            player.setOnErrorListener((failed, what, extra) -> {
                try {
                    failed.release();
                } catch (Throwable ignored) {
                }
                if (currentPlayer == failed) currentPlayer = null;
                if (isCurrent(generation)) {
                    playFiles(files, index + 1, generation, interFileDelayMs);
                }
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
            if (isCurrent(generation)) {
                playFiles(files, index + 1, generation, interFileDelayMs);
            }
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

    private void showOfflineFailure(long generation, Throwable error) {
        if (!isCurrent(generation)) return;
        String detail = describeError(error);
        SpeechDebugLog.append(app, "manager.bytedance_failed " + detail);
        mainHandler.post(() -> {
            if (!isCurrent(generation)) return;
            Toast.makeText(
                    app,
                    "拼音专用语音失败：" + detail,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private static String describeError(Throwable error) {
        if (error == null) return "未知错误";
        Throwable current = error;
        String message = "";
        int depth = 0;
        while (current != null && depth < 6) {
            String value = current.getMessage();
            if (value != null && !value.trim().isEmpty()) {
                message = value.trim();
                break;
            }
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
            depth++;
        }
        return message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static String safeMode(String mode) {
        return mode == null || mode.trim().isEmpty() ? "word" : mode.trim();
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

    private static boolean shouldUseOfflineForAuto(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            if ((cp >= 0x3400 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF)) {
                return true;
            }
            switch (cp) {
                case 'ā': case 'á': case 'ǎ': case 'à':
                case 'ē': case 'é': case 'ě': case 'è':
                case 'ī': case 'í': case 'ǐ': case 'ì':
                case 'ō': case 'ó': case 'ǒ': case 'ò':
                case 'ū': case 'ú': case 'ǔ': case 'ù':
                case 'ǖ': case 'ǘ': case 'ǚ': case 'ǜ':
                case 'ü': case 'Ā': case 'Á': case 'Ǎ': case 'À':
                case 'Ē': case 'É': case 'Ě': case 'È':
                case 'Ī': case 'Í': case 'Ǐ': case 'Ì':
                case 'Ō': case 'Ó': case 'Ǒ': case 'Ò':
                case 'Ū': case 'Ú': case 'Ǔ': case 'Ù':
                case 'Ǖ': case 'Ǘ': case 'Ǚ': case 'Ǜ':
                case 'Ü':
                    return true;
                default:
                    break;
            }
            offset += Character.charCount(cp);
        }
        return false;
    }

    private static boolean containsMyanmar(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u1000' && c <= '\u109f') return true;
        }
        return false;
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
