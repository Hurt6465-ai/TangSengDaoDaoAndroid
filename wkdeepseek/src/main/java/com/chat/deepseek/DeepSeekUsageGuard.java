package com.chat.deepseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

/**
 * Local pacing guard for the WebView based DeepSeek integration.
 *
 * This is not an anti-detection mechanism. It only prevents accidental double taps and rapid
 * retry storms when the page is slow or its DOM changes. All state is local to this device.
 */
final class DeepSeekUsageGuard {
    private static final String PREF = "wk_deepseek_usage_guard_v1";
    private static final String KEY_GLOBAL_LAST_SUBMIT = "global:last_submit";
    private static final String KEY_GLOBAL_BLOCKED_UNTIL = "global:blocked_until";
    private static final String KEY_GLOBAL_FAILURES = "global:failures";
    private static final String KEY_GLOBAL_LAST_FAILURE = "global:last_failure";

    private static final long MIN_GLOBAL_INTERVAL_MS = 2_500L;
    private static final long MIN_CHANNEL_INTERVAL_MS = 6_000L;
    private static final long FAILURE_RESET_MS = 10L * 60L * 1000L;
    private static final long[] FAILURE_BACKOFF_MS = {3_000L, 8_000L, 20_000L, 45_000L};

    private DeepSeekUsageGuard() {
    }

    static long remainingMs(Context context, DeepSeekRequest request) {
        if (context == null) return 0L;
        SharedPreferences preferences = preferences(context);
        long now = System.currentTimeMillis();
        long remaining = Math.max(0L,
                preferences.getLong(KEY_GLOBAL_BLOCKED_UNTIL, 0L) - now);
        remaining = Math.max(remaining,
                preferences.getLong(KEY_GLOBAL_LAST_SUBMIT, 0L)
                        + MIN_GLOBAL_INTERVAL_MS - now);
        String channelKey = channelLastSubmitKey(request);
        if (!TextUtils.isEmpty(channelKey)) {
            remaining = Math.max(remaining,
                    preferences.getLong(channelKey, 0L)
                            + MIN_CHANNEL_INTERVAL_MS - now);
        }
        return Math.max(0L, remaining);
    }

    static void recordSubmitted(Context context, DeepSeekRequest request) {
        if (context == null) return;
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = preferences(context).edit()
                .putLong(KEY_GLOBAL_LAST_SUBMIT, now)
                .putInt(KEY_GLOBAL_FAILURES, 0)
                .remove(KEY_GLOBAL_BLOCKED_UNTIL)
                .remove(KEY_GLOBAL_LAST_FAILURE);
        String channelKey = channelLastSubmitKey(request);
        if (!TextUtils.isEmpty(channelKey)) editor.putLong(channelKey, now);
        editor.apply();
    }

    static long recordFailure(Context context, String reason) {
        if (context == null) return 0L;
        SharedPreferences preferences = preferences(context);
        long now = System.currentTimeMillis();
        long lastFailure = preferences.getLong(KEY_GLOBAL_LAST_FAILURE, 0L);
        int failures = now - lastFailure > FAILURE_RESET_MS
                ? 0 : Math.max(0, preferences.getInt(KEY_GLOBAL_FAILURES, 0));
        failures = Math.min(FAILURE_BACKOFF_MS.length, failures + 1);
        long delay = FAILURE_BACKOFF_MS[failures - 1];
        preferences.edit()
                .putInt(KEY_GLOBAL_FAILURES, failures)
                .putLong(KEY_GLOBAL_LAST_FAILURE, now)
                .putLong(KEY_GLOBAL_BLOCKED_UNTIL, now + delay)
                .apply();
        DeepSeekHistoryLog.log("LOCAL_BACKOFF_SET",
                "failures=" + failures + " delay_ms=" + delay + " reason=" + safe(reason));
        return delay;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String channelLastSubmitKey(DeepSeekRequest request) {
        if (request == null || TextUtils.isEmpty(request.selfUid)
                || TextUtils.isEmpty(request.channelId)) {
            return "";
        }
        String raw = request.selfUid.trim() + "\n" + request.channelType + "\n"
                + request.channelId.trim();
        String encoded = Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP | Base64.URL_SAFE);
        return "channel:" + encoded + ":last_submit";
    }

    private static String safe(String value) {
        if (value == null) return "unknown";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }
}
