package com.chat.partnerlist;

import android.content.Context;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PartnerListTime {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
    private static final long MINUTE_MS = 60_000L;
    private static final long STATUS_VISIBLE_WINDOW_MS = 60L * MINUTE_MS;

    private PartnerListTime() {}

    public static String currentDayKey() {
        return ZonedDateTime.now(BUSINESS_ZONE).minusHours(4).format(DAY_FORMAT);
    }

    public static boolean isRecentlyActive(int online, long lastActiveAt, long serverTime) {
        if (online == 1) return true;
        long now = serverTime > 0 ? normalizeMillis(serverTime) : System.currentTimeMillis();
        long ts = normalizeMillis(lastActiveAt);
        return ts > 0 && Math.max(0L, now - ts) < STATUS_VISIBLE_WINDOW_MS;
    }

    /**
     * 列表只保留有用的短状态：当前在线，或一小时内“几分钟前在线”。
     * 不再显示“最近活跃 / 今天活跃 / 几小时前活跃”等模糊、冗长状态。
     */
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
