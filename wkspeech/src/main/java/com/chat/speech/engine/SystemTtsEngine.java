package com.chat.speech.engine;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.UUID;

public class SystemTtsEngine {
    private static SystemTtsEngine instance;
    private final Context app;
    private TextToSpeech tts;
    private boolean ready;
    private String pendingText;

    private SystemTtsEngine(Context context) {
        app = context.getApplicationContext();
    }

    public static synchronized SystemTtsEngine get(Context context) {
        if (instance == null) instance = new SystemTtsEngine(context);
        return instance;
    }

    public synchronized void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (tts == null) {
            pendingText = text;
            tts = new TextToSpeech(app, status -> {
                ready = status == TextToSpeech.SUCCESS;
                if (ready) {
                    try {
                        tts.setLanguage(Locale.getDefault());
                    } catch (Throwable ignored) {
                    }
                    String p;
                    synchronized (SystemTtsEngine.this) {
                        p = pendingText;
                        pendingText = null;
                    }
                    if (p != null) speakNow(p);
                }
            });
            return;
        }
        if (!ready) {
            pendingText = text;
            return;
        }
        speakNow(text);
    }

    public synchronized void stop() {
        if (tts != null) tts.stop();
    }

    private void speakNow(String text) {
        String utteranceId = "tsdd_" + UUID.randomUUID();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            //noinspection deprecation
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }
}
