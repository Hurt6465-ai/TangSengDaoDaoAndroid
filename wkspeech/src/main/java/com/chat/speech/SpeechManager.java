package com.chat.speech;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.chat.speech.engine.MsTranslatorCompatibleEngine;
import com.chat.speech.engine.SystemTtsEngine;
import com.chat.speech.model.SpeechSegment;

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
        if (!prefs.isMsEnabled()) {
            SystemTtsEngine.get(app).speak(text);
            return;
        }
        executor.execute(() -> {
            try {
                List<SpeechSegment> segments = SpeechSegmenter.splitByLanguage(text);
                List<File> files = new ArrayList<>();
                for (SpeechSegment segment : segments) {
                    String voice = prefs.voiceForLang(segment.lang);
                    String locale = SpeechSegment.LANG_MY.equals(segment.lang) ? SpeechSegment.LANG_MY : SpeechSegment.LANG_ZH;
                    files.add(msEngine.synthesize(app, segment.text, voice, locale, prefs.getAudioFormat()));
                }
                mainHandler.post(() -> playFiles(files, 0));
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(app, "微软兼容源失败，已切换系统 TTS：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    SystemTtsEngine.get(app).speak(text);
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
