package com.chat.speech;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.chat.speech.engine.MsTranslatorCompatibleEngine;
import com.chat.speech.engine.SystemTtsEngine;
import com.chat.speech.model.SpeechSegment;
import com.chat.speech.model.TtsSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpeechManager {
    private static SpeechManager instance;

    private final Context app;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MsTranslatorCompatibleEngine msEngine = new MsTranslatorCompatibleEngine();
    private MediaPlayer currentPlayer;

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
        stop();
        SpeechPrefs prefs = new SpeechPrefs(app);
        TtsSource source = prefs.getActiveSource();
        if (source == null || TtsSource.TYPE_SYSTEM.equals(source.type)) {
            SystemTtsEngine.get(app).speak(text, prefs.getSystemRate(), prefs.getSystemPitch());
            return;
        }
        if (!TtsSource.TYPE_MS_TRANSLATOR.equals(source.type)) {
            Toast.makeText(app, "当前 TTS 源暂未接入朗读引擎，已使用系统 TTS", Toast.LENGTH_LONG).show();
            SystemTtsEngine.get(app).speak(text, prefs.getSystemRate(), prefs.getSystemPitch());
            return;
        }
        executor.execute(() -> {
            try {
                List<SpeechSegment> segments;
                if (prefs.isMixedReadEnabled()) {
                    segments = SpeechSegmenter.splitByLanguage(text);
                } else {
                    segments = new ArrayList<>();
                    segments.add(new SpeechSegment(text.trim(), SpeechSegment.LANG_ZH));
                }
                List<File> files = new ArrayList<>();
                for (SpeechSegment segment : segments) {
                    String voice = prefs.voiceForLang(segment.lang);
                    String locale = SpeechSegment.LANG_MY.equals(segment.lang) ? SpeechSegment.LANG_MY : SpeechSegment.LANG_ZH;
                    files.add(msEngine.synthesize(app, source, segment.text, voice, locale, prefs.getAudioFormat(), prefs.getRatePercent(), prefs.getPitchPercent()));
                }
                mainHandler.post(() -> playFiles(files, 0));
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(app, "当前在线语音源失败，已切换系统 TTS：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    SystemTtsEngine.get(app).speak(text, prefs.getSystemRate(), prefs.getSystemPitch());
                });
            }
        });
    }

    public void stop() {
        try {
            SystemTtsEngine.get(app).stop();
        } catch (Throwable ignored) {
        }
        mainHandler.post(() -> {
            if (currentPlayer != null) {
                try {
                    currentPlayer.stop();
                } catch (Throwable ignored) {
                }
                try {
                    currentPlayer.release();
                } catch (Throwable ignored) {
                }
                currentPlayer = null;
            }
        });
    }

    private void playFiles(List<File> files, int index) {
        if (files == null || index >= files.size()) return;
        File file = files.get(index);
        try {
            currentPlayer = new MediaPlayer();
            currentPlayer.setDataSource(file.getAbsolutePath());
            currentPlayer.setOnCompletionListener(mp -> {
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
                if (currentPlayer == mp) currentPlayer = null;
                playFiles(files, index + 1);
            });
            currentPlayer.setOnErrorListener((mp, what, extra) -> {
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
                if (currentPlayer == mp) currentPlayer = null;
                playFiles(files, index + 1);
                return true;
            });
            currentPlayer.prepare();
            currentPlayer.start();
        } catch (Exception e) {
            playFiles(files, index + 1);
        }
    }
}
