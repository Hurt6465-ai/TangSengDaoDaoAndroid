package com.chat.speech.engine;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.UUID;

public class SystemTtsEngine {
    private static SystemTtsEngine instance;
    private final Context app;
    private TextToSpeech tts;
    private boolean ready;
    private String pendingText;
    private float pendingRate = 1.0f;
    private float pendingPitch = 1.0f;

    private SystemTtsEngine(Context context) {
        app = context.getApplicationContext();
    }

    public static synchronized SystemTtsEngine get(Context context) {
        if (instance == null) instance = new SystemTtsEngine(context);
        return instance;
    }

    public synchronized void speak(String text) {
        speak(text, 1.0f, 1.0f);
    }

    public synchronized void speak(String text, float rate, float pitch) {
        if (text == null || text.trim().isEmpty()) return;
        pendingRate = safeRate(rate);
        pendingPitch = safePitch(pitch);
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
                    if (p != null) speakNow(p, pendingRate, pendingPitch);
                }
            });
            return;
        }
        if (!ready) {
            pendingText = text;
            return;
        }
        speakNow(text, pendingRate, pendingPitch);
    }

    public synchronized void stop() {
        pendingText = null;
        if (tts != null) tts.stop();
    }

    private void speakNow(String text, float rate, float pitch) {
        try {
            tts.setSpeechRate(safeRate(rate));
            tts.setPitch(safePitch(pitch));
        } catch (Throwable ignored) {
        }
        String utteranceId = "tsdd_" + UUID.randomUUID();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            //noinspection deprecation
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private float safeRate(float value) {
        return Math.max(0.5f, Math.min(2.0f, value));
    }

    private float safePitch(float value) {
        return Math.max(0.5f, Math.min(1.8f, value));
    }
}
