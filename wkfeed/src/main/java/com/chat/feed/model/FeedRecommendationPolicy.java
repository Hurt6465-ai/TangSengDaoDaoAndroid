package com.chat.feed.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 简单发现页排序，不做千人千面。
 * 目标：新内容优先，质量/热度适当加权，随机扰动避免每次顺序完全一样。
 */
public class FeedRecommendationPolicy {
    private static final long DAY = 24L * 60L * 60L * 1000L;

    public static void sortForDiscover(List<FeedBean> list) {
        if (list == null || list.size() <= 1) return;
        final long now = System.currentTimeMillis();
        final long seed = now / DAY;
        Collections.sort(list, new Comparator<FeedBean>() {
            @Override
            public int compare(FeedBean a, FeedBean b) {
                double sb = score(b, now, seed);
                double sa = score(a, now, seed);
                return Double.compare(sb, sa);
            }
        });
    }

    private static double score(FeedBean item, long now, long seed) {
        if (item == null) return -99999;
        long created = item.created_at > 0 ? item.created_at : item.updated_at;
        long ageMs = created > 0 ? Math.max(0, now - created) : DAY * 30;
        double fresh = Math.max(0, 48 - ageMs / 3600000.0); // 48小时内更靠前
        double hot = Math.log(1 + Math.max(0, item.like_count) * 2 + Math.max(0, item.comment_count) * 4 + Math.max(0, item.share_count) * 3) * 3;
        double quality = 0;
        if (item.firstMedia() != null) quality += 8;
        if (item.safeMedia().size() >= 2) quality += 3;
        if (item.isVideo()) quality += 2;
        if (item.user != null && item.user.follow == 1) quality -= 8; // 发现页不是好友页
        double random = stableRandom(item.stableKey(), seed) * 10.0;
        return fresh + hot + quality + random;
    }

    private static double stableRandom(String key, long seed) {
        long value = (key == null ? 0 : key.hashCode()) * 1103515245L + seed * 2654435761L;
        return new Random(value).nextDouble();
    }
}
