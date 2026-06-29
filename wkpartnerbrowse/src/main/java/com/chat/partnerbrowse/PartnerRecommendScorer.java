package com.chat.partnerbrowse;

import android.text.TextUtils;

import com.chat.partnerbrowse.model.PartnerBrowseBean;

import java.util.Calendar;
import java.util.List;
import java.util.Random;

public final class PartnerRecommendScorer {
    private PartnerRecommendScorer() {}

    public static double score(PartnerBrowseBean item, String viewerUid, int round) {
        if (item == null) return -9999;
        double score = item.server_score;
        score += activeScore(item);
        score += profileScore(item);
        score += imageScore(item);
        // v25: distance does not affect score. Nearby is only for candidate mixing and display.
        score += stableRandomScore(viewerUid, item.getStableKey(), round);
        item.score = score;
        return score;
    }

    /**
     * Active score aligned with backend v25. Always use getLastActiveMillisSafe()
     * so second-level timestamps are converted to milliseconds before comparison.
     */
    public static int activeScore(PartnerBrowseBean item) {
        if (item == null) return 0;
        if (item.online == 1) return 35;
        long lastActive = item.getLastActiveMillisSafe();
        if (lastActive <= 0) return -20;
        long diffMin = Math.max(0, (System.currentTimeMillis() - lastActive) / 60000L);
        if (diffMin <= 5) return 30;
        if (diffMin <= 10) return 25;
        if (diffMin <= 20) return 20;
        if (diffMin <= 30) return 15;
        if (diffMin <= 60) return 10;
        if (diffMin <= 180) return 5;
        if (diffMin <= 24 * 60) return 1;
        if (diffMin <= 7 * 24 * 60) return -20;
        return -999;
    }

    private static int profileScore(PartnerBrowseBean item) {
        int score = 0;
        if (!TextUtils.isEmpty(item.name)) score += 3;
        if (!TextUtils.isEmpty(item.intro)) score += 4;
        if (!item.getNativeLanguagesSafe().isEmpty()) score += 5;
        if (!item.getLearningLanguagesSafe().isEmpty()) score += 5;
        if (!item.getTagsSafe().isEmpty()) score += 3;
        if (!TextUtils.isEmpty(item.country_code)) score += 2;
        return Math.min(score, 22);
    }

    private static int imageScore(PartnerBrowseBean item) {
        List<String> images = item.getProfileImagesSafe();
        int count = 0;
        for (String image : images) if (!TextUtils.isEmpty(image)) count++;
        if (count <= 0) return -999;
        int score = 8;
        if (count >= 2) score += 4;
        if (count >= 3) score += 4;
        if (count >= 5) score += 4;
        return Math.min(score, 20);
    }

    private static int stableRandomScore(String viewerUid, String candidateUid, int round) {
        Calendar c = Calendar.getInstance();
        String day = c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
        int seed = (String.valueOf(viewerUid) + "|" + candidateUid + "|" + day + "|" + round).hashCode();
        return new Random(seed).nextInt(17) - 8; // -8~+8, only for tie breaking/exploration
    }
}
