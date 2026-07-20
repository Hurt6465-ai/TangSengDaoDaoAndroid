package com.chat.forum;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.chat.base.WKBaseApplication;

import java.util.Locale;

/**
 * Centralized forum text and locale helpers.
 *
 * The forum module contains several non-Activity helpers (network, image compression,
 * audio recording). Using the application resources here keeps their user-facing errors
 * consistent with the language selected in the app.
 */
final class ForumText {
    private ForumText() {
    }

    @NonNull
    static Context context() {
        Context context = WKBaseApplication.getInstance().getContext();
        if (context == null) {
            throw new IllegalStateException("Forum context is not initialized");
        }
        return context;
    }

    @NonNull
    static String get(@StringRes int id, Object... args) {
        Context context = context();
        return args == null || args.length == 0
                ? context.getString(id)
                : context.getString(id, args);
    }

    @NonNull
    static String[] array(@ArrayRes int id) {
        return context().getResources().getStringArray(id);
    }

    @NonNull
    private static Locale currentLocale() {
        Configuration configuration = context().getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList locales = configuration.getLocales();
            return locales.isEmpty() ? Locale.getDefault() : locales.get(0);
        }
        return configuration.locale == null ? Locale.getDefault() : configuration.locale;
    }

    /** Locale tag understood by the forum backend. */
    @NonNull
    static String languageTag() {
        Locale locale = currentLocale();
        String language = locale.getLanguage();
        String script = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? locale.getScript() : "";
        String country = locale.getCountry();
        if ("my".equalsIgnoreCase(language)) return "my-MM";
        if ("zh".equalsIgnoreCase(language)) {
            boolean traditional = "Hant".equalsIgnoreCase(script)
                    || "TW".equalsIgnoreCase(country)
                    || "HK".equalsIgnoreCase(country)
                    || "MO".equalsIgnoreCase(country);
            return traditional ? "zh-TW" : "zh-CN";
        }
        return "en-US";
    }

    @NonNull
    static String relativeTime(long value) {
        if (value <= 0) return get(R.string.forum_now);
        long timestamp = value < 1_000_000_000_000L ? value * 1000L : value;
        long diff = Math.max(0L, System.currentTimeMillis() - timestamp);
        if (diff < 60_000L) return get(R.string.forum_now);
        if (diff < 3_600_000L) {
            return get(R.string.forum_minutes_ago, Math.max(1L, diff / 60_000L));
        }
        if (diff < 86_400_000L) {
            return get(R.string.forum_hours_ago, Math.max(1L, diff / 3_600_000L));
        }
        if (diff < 7L * 86_400_000L) {
            return get(R.string.forum_days_ago, Math.max(1L, diff / 86_400_000L));
        }
        java.text.DateFormat format = java.text.DateFormat.getDateInstance(
                java.text.DateFormat.MEDIUM, currentLocale());
        return format.format(new java.util.Date(timestamp));
    }
}
