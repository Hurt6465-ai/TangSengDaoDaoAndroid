package com.chat.partnerlist;

import android.content.Context;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Time helpers compatible with minSdk 24 without java.time core-library desugaring. */
public final class PartnerListTime {
    private static final TimeZone BUSINESS_ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long MINUTE_MS = 60_000L;
    private static final long STATUS_VISIBLE_WINDOW_MS = 60L * MINUTE_MS;

    private static final String[] OFFSET_PATTERNS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "yyyy-MM-dd'T'HH:mm:ssXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ssZ"
    };
    private static final String[] UTC_PATTERNS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss'Z'"
    };
    private static final String[] LOCAL_PATTERNS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
    };

    private PartnerListTime() {}

    public static String currentDayKey() {
        return dayKey(System.currentTimeMillis());
    }

    public static String dayKey(long timestamp) {
        long now = normalizeMillis(timestamp);
        if (now <= 0L) now = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance(BUSINESS_ZONE, Locale.US);
        calendar.setTimeInMillis(now);
        calendar.add(Calendar.HOUR_OF_DAY, -4);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        format.setTimeZone(BUSINESS_ZONE);
        return format.format(calendar.getTime());
    }

    public static boolean isRecentlyActive(int online, long lastActiveAt, long serverTime) {
        if (online == 1) return true;
        long now = serverTime > 0 ? normalizeMillis(serverTime) : System.currentTimeMillis();
        long ts = normalizeMillis(lastActiveAt);
        return ts > 0 && Math.max(0L, now - ts) < STATUS_VISIBLE_WINDOW_MS;
    }

    /** Only show useful short states: online now, or minutes ago within one hour. */
    public static String activeLabel(Context context, int online, long lastActiveAt, long serverTime) {
        if (context == null) return "";
        if (online == 1) return context.getString(R.string.partnerlist_online_now);
        long now = serverTime > 0 ? normalizeMillis(serverTime) : System.currentTimeMillis();
        long ts = normalizeMillis(lastActiveAt);
        if (ts <= 0) return "";
        long diff = Math.max(0L, now - ts);
        if (diff >= STATUS_VISIBLE_WINDOW_MS) return "";
        long minutes = Math.max(1L, diff / MINUTE_MS);
        return context.getString(R.string.partnerlist_online_minutes_ago, minutes);
    }

    public static boolean isWithinDays(String rawTime, long serverTime, int days) {
        return isWithinDays(parseFlexibleTime(rawTime), serverTime, days);
    }

    public static boolean isWithinDays(long timestamp, long serverTime, int days) {
        long joinedAt = normalizeMillis(timestamp);
        if (joinedAt <= 0 || days <= 0) return false;
        long now = serverTime > 0 ? normalizeMillis(serverTime) : System.currentTimeMillis();
        long diff = now - joinedAt;
        long window;
        try {
            window = Math.multiplyExact((long) days, 24L * 60L * 60L * 1000L);
        } catch (ArithmeticException ignored) {
            window = Long.MAX_VALUE;
        }
        return diff >= 0 && diff < window;
    }

    /** Accepts unix seconds/millis, ISO offsets, UTC Z timestamps and common local server times. */
    public static long parseFlexibleTime(String raw) {
        if (raw == null) return 0L;
        String value = raw.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) return 0L;
        try {
            return normalizeMillis(Long.parseLong(value));
        } catch (Throwable ignored) {
        }

        value = normalizeFractionalSeconds(value);
        Long parsed = parseWithPatterns(value, OFFSET_PATTERNS, BUSINESS_ZONE);
        if (parsed != null) return parsed;
        parsed = parseWithPatterns(value, UTC_PATTERNS, UTC);
        if (parsed != null) return parsed;
        parsed = parseWithPatterns(value, LOCAL_PATTERNS, BUSINESS_ZONE);
        return parsed == null ? 0L : parsed;
    }

    private static String normalizeFractionalSeconds(String value) {
        int dot = value.indexOf('.');
        if (dot < 0) return value;
        int end = dot + 1;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        int digits = end - dot - 1;
        if (digits <= 0) return value;
        String fraction = value.substring(dot + 1, end);
        if (digits > 3) fraction = fraction.substring(0, 3);
        else if (digits == 1) fraction += "00";
        else if (digits == 2) fraction += "0";
        return value.substring(0, dot + 1) + fraction + value.substring(end);
    }

    private static Long parseWithPatterns(String value, String[] patterns, TimeZone timeZone) {
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            format.setTimeZone(timeZone);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(value, position);
            if (date != null && position.getIndex() == value.length()) return date.getTime();
        }
        return null;
    }

    public static long normalizeMillis(long value) {
        if (value <= 0) return 0L;
        if (value < 10_000_000_000L) {
            try {
                return Math.multiplyExact(value, 1000L);
            } catch (ArithmeticException ignored) {
                return 0L;
            }
        }
        return value;
    }

    public static long nextDueAt(long rotateAt, long retryAt) {
        return Math.max(normalizeMillis(rotateAt), normalizeMillis(retryAt));
    }

    public static long nextDayBoundaryMillis() {
        return nextDayBoundaryMillis(System.currentTimeMillis());
    }

    public static long nextDayBoundaryMillis(long timestamp) {
        long nowMillis = normalizeMillis(timestamp);
        if (nowMillis <= 0L) nowMillis = System.currentTimeMillis();
        Calendar now = Calendar.getInstance(BUSINESS_ZONE, Locale.US);
        now.setTimeInMillis(nowMillis);
        Calendar boundary = (Calendar) now.clone();
        boundary.set(Calendar.HOUR_OF_DAY, 4);
        boundary.set(Calendar.MINUTE, 0);
        boundary.set(Calendar.SECOND, 0);
        boundary.set(Calendar.MILLISECOND, 0);
        if (!boundary.after(now)) boundary.add(Calendar.DAY_OF_MONTH, 1);
        return boundary.getTimeInMillis();
    }
}
