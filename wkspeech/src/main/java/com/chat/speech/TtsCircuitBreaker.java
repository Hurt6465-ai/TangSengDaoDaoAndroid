package com.chat.speech;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small persistent circuit breaker for unofficial online TTS sources.
 * Two consecutive failures pause that source for ten minutes, avoiding a timeout on every word.
 */
final class TtsCircuitBreaker {
    private static final String PREF = "tsdd_speech_health";
    private static final int FAILURE_THRESHOLD = 2;
    private static final long COOLDOWN_MILLIS = 10L * 60L * 1000L;

    private TtsCircuitBreaker() {
    }

    static boolean canAttempt(Context context, String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) return true;
        SharedPreferences preferences = prefs(context);
        long disabledUntil = preferences.getLong(key(sourceId, "disabled_until"), 0L);
        if (disabledUntil <= System.currentTimeMillis()) {
            if (disabledUntil > 0L) {
                preferences.edit().remove(key(sourceId, "disabled_until")).apply();
            }
            return true;
        }
        return false;
    }

    static void recordSuccess(Context context, String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) return;
        prefs(context).edit()
                .remove(key(sourceId, "failures"))
                .remove(key(sourceId, "disabled_until"))
                .putLong(key(sourceId, "last_success"), System.currentTimeMillis())
                .apply();
    }

    static void recordFailure(Context context, String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) return;
        SharedPreferences preferences = prefs(context);
        String failureKey = key(sourceId, "failures");
        int failures = preferences.getInt(failureKey, 0) + 1;
        SharedPreferences.Editor editor = preferences.edit()
                .putLong(key(sourceId, "last_failure"), System.currentTimeMillis());
        if (failures >= FAILURE_THRESHOLD) {
            editor.putInt(failureKey, 0)
                    .putLong(
                            key(sourceId, "disabled_until"),
                            System.currentTimeMillis() + COOLDOWN_MILLIS
                    );
        } else {
            editor.putInt(failureKey, failures);
        }
        editor.apply();
    }

    static void reset(Context context, String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) return;
        prefs(context).edit()
                .remove(key(sourceId, "failures"))
                .remove(key(sourceId, "disabled_until"))
                .remove(key(sourceId, "last_failure"))
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String key(String sourceId, String suffix) {
        return sourceId.replaceAll("[^A-Za-z0-9_.-]", "_") + "_" + suffix;
    }
}
