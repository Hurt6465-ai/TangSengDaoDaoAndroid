package com.chat.partnerbrowse;

import android.text.TextUtils;

import com.chat.partnerbrowse.model.PartnerBrowseBean;

import java.util.Calendar;
import java.util.List;
import java.util.Random;

public final class PartnerRecommendScorer {
    public static final int NEARBY_RADIUS_METERS = 70 * 1000;
    public static final int NEARBY_SCORE_PENALTY = -25;

    private PartnerRecommendScorer() {}

    public static double score(PartnerBrowseBean item, String viewerUid, int round) {
        if (item == null) return -9999;
        double score = item.server_score;
        score += activeScore(item);
        score += profileScore(item);
        score += imageScore(item);
        score += nearbyPenalty(item);
        score += stableRandomScore(viewerUid, item.getStableKey(), round);
        item.score = score;
        return score;
    }

    /**
     * 活跃权重调低版：最高 +35，不再 +60。
     * 在线重要，但不能压过语言匹配、资料完整和随机探索。
     */
    public static int activeScore(PartnerBrowseBean item) {
        if (item == null) return 0;
        if (item.online == 1 || item.status == 1) return 35;
        long lastActive = item.last_active_millis > 0 ? item.last_active_millis : item.last_online_millis;
        if (lastActive <= 0) return 0;
        long diffMin = Math.max(0, (System.currentTimeMillis() - lastActive) / 60000L);
        if (diffMin <= 5) return 30;
        if (diffMin <= 10) return 26;
        if (diffMin <= 20) return 22;
        if (diffMin <= 30) return 18;
        if (diffMin <= 60) return 12;
        if (diffMin <= 180) return 5;
        if (diffMin <= 24 * 60) return 0;
        if (diffMin <= 7 * 24 * 60) return -12;
        return -999;
    }

    private static int profileScore(PartnerBrowseBean item) {
        int score = 0;
        if (!TextUtils.isEmpty(item.name)) score += 3;
        if (!TextUtils.isEmpty(item.intro)) score += 4;
        if (!item.getNativeLanguagesSafe().isEmpty()) score += 5;
        if (!item.getLearningLanguagesSafe().isEmpty()) score += 5;
        if (!item.getTagsSafe().isEmpty()) score += 3;
        if (item.age > 0 || !TextUtils.isEmpty(item.birthday)) score += 3;
        if (!TextUtils.isEmpty(item.country_code)) score += 2;
        return Math.min(score, 25);
    }

    private static int imageScore(PartnerBrowseBean item) {
        List<String> images = item.getDisplayImagesSafe();
        int count = 0;
        for (String image : images) if (!TextUtils.isEmpty(image)) count++;
        if (count <= 0) return -999;
        int score = 8;
        if (count >= 2) score += 4;
        if (count >= 3) score += 4;
        if (count >= 5) score += 4;
        return Math.min(score, 20);
    }

    private static int nearbyPenalty(PartnerBrowseBean item) {
        int meters = item.getDistanceMetersSafe();
        if (meters > 0 && meters <= NEARBY_RADIUS_METERS) return NEARBY_SCORE_PENALTY;
        return 0;
    }

    private static int stableRandomScore(String viewerUid, String candidateUid, int round) {
        Calendar c = Calendar.getInstance();
        String day = c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
        int seed = (String.valueOf(viewerUid) + "|" + candidateUid + "|" + day + "|" + round).hashCode();
        return new Random(seed).nextInt(13); // 0~12，降低乱跳，避免随机压过质量分
    }
}
