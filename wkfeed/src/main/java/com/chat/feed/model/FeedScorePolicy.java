package com.chat.feed.model;

public class FeedScorePolicy {
    public static final int NEARBY_RADIUS_METERS = 70000;
    public static final int NEARBY_PENALTY = -25;

    // 活跃分降低版：在线重要，但不压过语言匹配、资料完整度和图片质量。
    public static int activeScore(long lastActiveAt, boolean online) {
        if (online) return 30;
        if (lastActiveAt <= 0) return -20;
        long min = Math.max(0, System.currentTimeMillis() - lastActiveAt) / 60000L;
        if (min <= 5) return 25;
        if (min <= 10) return 22;
        if (min <= 20) return 18;
        if (min <= 30) return 14;
        if (min <= 60) return 10;
        if (min <= 180) return 4;
        if (min <= 1440) return 1;
        return -20;
    }

    public static int distanceBucketKm(int distanceMeters) {
        if (distanceMeters <= 0 || distanceMeters > NEARBY_RADIUS_METERS) return 0;
        int km = Math.max(1, (int) Math.ceil(distanceMeters / 1000.0));
        if (km <= 5) return 5;
        if (km <= 10) return 10;
        if (km <= 30) return 30;
        return 70;
    }
}
