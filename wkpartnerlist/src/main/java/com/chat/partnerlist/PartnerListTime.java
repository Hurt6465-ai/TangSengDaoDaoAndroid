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

    public static String activeLabel(Context context, int online, long lastActiveAt, long serverTime) {
        if (context == null) return "";
        if (online == 1) return context.getString(R.string.partnerlist_online_now);
        long now = serverTime > 0 ? serverTime : System.currentTimeMillis();
        long ts = normalizeMillis(lastActiveAt);
        if (ts <= 0) return context.getString(R.string.partnerlist_active_recently);
        long diff = Math.max(0L, now - ts);
        long minute = 60_000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (diff < minute) return context.getString(R.string.partnerlist_active_just_now);
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
}
