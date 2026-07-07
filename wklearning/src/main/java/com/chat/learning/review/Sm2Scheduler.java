package com.chat.learning.review;

import com.chat.learning.model.ReviewState;

/**
 * SM-2 间隔重复调度器。
 *
 * 三档评分映射：
 *   忘记 FORGOT = quality 0
 *   模糊 VAGUE  = quality 2
 *   记得 KNOWN  = quality 5
 *
 * 关键点（二开务必注意）：
 *   1. easeFactor 更新无论成功失败都要执行，不要挪进 else 分支。
 *   2. quality < 3 视为失败：repetitions 清零。
 *      - 忘记：10 分钟后再出现，适合同一轮快速重刷。
 *      - 模糊：8 小时后再出现，介于忘记和记得之间。
 *   3. 本类是纯函数：只算状态，不读写数据库，方便单测和后续替换 FSRS。
 */
public final class Sm2Scheduler {
    public static final int QUALITY_FORGOT = 0;
    public static final int QUALITY_VAGUE = 2;
    public static final int QUALITY_KNOWN = 5;

    private static final double DEFAULT_EASE = 2.5d;
    private static final double MIN_EASE = 1.3d;
    private static final long MINUTE_MS = 60L * 1000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;
    private static final long RELEARN_FORGOT_MS = 10L * MINUTE_MS;
    private static final long RELEARN_VAGUE_MS = 8L * HOUR_MS;

    private Sm2Scheduler() {}

    public static ReviewState schedule(ReviewState old, String wordId, int rawQuality, long now) {
        int quality = normalizeQuality(rawQuality);

        ReviewState next = new ReviewState();
        next.wordId = wordId == null ? "" : wordId;

        double easeFactor = old != null && old.easeFactor > 0 ? old.easeFactor : DEFAULT_EASE;
        int repetitions = old != null ? Math.max(0, old.repetitions) : 0;
        int intervalDays = old != null ? Math.max(0, old.intervalDays) : 0;
        int reviewCount = old != null ? Math.max(0, old.reviewCount) : 0;
        int lapseCount = old != null ? Math.max(0, old.lapseCount) : 0;

        long nextReviewAt;

        if (quality < 3) {
            repetitions = 0;
            intervalDays = 0;
            lapseCount += 1;
            nextReviewAt = now + (quality <= QUALITY_FORGOT ? RELEARN_FORGOT_MS : RELEARN_VAGUE_MS);
        } else {
            if (repetitions == 0) {
                intervalDays = 1;
            } else if (repetitions == 1) {
                intervalDays = 6;
            } else {
                intervalDays = Math.max(1, (int) Math.round(intervalDays * easeFactor));
            }
            repetitions += 1;
            nextReviewAt = now + (long) intervalDays * DAY_MS;
        }

        // EF 更新：无论 quality 是否通过都要执行。失败词会降低 EF，让它以后更频繁出现。
        easeFactor = easeFactor + (0.1d - (5 - quality) * (0.08d + (5 - quality) * 0.02d));
        if (easeFactor < MIN_EASE) easeFactor = MIN_EASE;

        next.easeFactor = easeFactor;
        next.repetitions = repetitions;
        next.intervalDays = intervalDays;
        next.lastQuality = quality;
        next.lastReviewAt = now;
        next.nextReviewAt = nextReviewAt;
        next.reviewCount = reviewCount + 1;
        next.lapseCount = lapseCount;
        return next;
    }

    public static int normalizeQuality(int quality) {
        if (quality <= 1) return QUALITY_FORGOT;
        if (quality <= 3) return QUALITY_VAGUE;
        return QUALITY_KNOWN;
    }
}
