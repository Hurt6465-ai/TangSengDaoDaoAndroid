package com.chat.partnerlist;

import android.content.Context;

import com.chat.partnerlist.R;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PartnerListTime {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);

    private PartnerListTime() {}

    public static String currentDayKey() {
        return ZonedDateTime.now(BUSINESS_ZONE).minusHours(4).format(DAY_FORMAT);
    }

    public static boolean isRecentlyActive(int online, long lastActiveAt, long serverTime) {
        if (online == 1) return true;
        long now = serverTime > 0 ? serverTime : System.currentTimeMillis();
        long ts = normalizeMillis(lastActiveAt);
        return ts > 0 && Math.max(0L, now - ts) < 10L * 60L * 1000L;
    }

    public static String activeLabel(Context context, int online, long lastActiveAt, long serverTime) {
        if (context == null) return "";
        if (online == 1) return context.getString(R.string.partnerlist_active_10m);
        long now = serverTime > 0 ? serverTime : System.currentTimeMillis();
        long ts = normalizeMillis(lastActiveAt);
        if (ts <= 0) return context.getString(R.string.partnerlist_active_recently);
        long diff = Math.max(0L, now - ts);
        long minute = 60_000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (diff < 10L * minute) return context.getString(R.string.partnerlist_active_10m);
        if (diff < hour) return context.getString(R.string.partnerlist_active_minutes, Math.max(1, diff / minute));
        if (diff < day) return context.getString(R.string.partnerlist_active_hours, Math.max(1, diff / hour));
        if (diff < 3L * day) return context.getString(R.string.partnerlist_active_today);
        return context.getString(R.string.partnerlist_active_recently);
    }

    public static long normalizeMillis(long value) {
        if (value <= 0) return 0L;
        return value < 10_000_000_000L ? value * 1000L : value;
    }

    public static long nextDueAt(long rotateAt, long retryAt) {
        return Math.max(normalizeMillis(rotateAt), normalizeMillis(retryAt));
    }
    public static long nextDayBoundaryMillis() {
        ZonedDateTime now = ZonedDateTime.now(BUSINESS_ZONE);
        ZonedDateTime boundary = now.toLocalDate().atTime(4, 0).atZone(BUSINESS_ZONE);
        if (!boundary.isAfter(now)) boundary = boundary.plusDays(1);
        return boundary.toInstant().toEpochMilli();
    }

}
