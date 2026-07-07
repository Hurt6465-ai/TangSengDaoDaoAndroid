package com.chat.learning;

/**
 * SM-2 spaced repetition scheduler for the word learning module.
 *
 * Three user-facing grades are used:
 * - FORGOT   = 0: 忘记，尽快重学；
 * - BLURRY   = 3: 模糊，算通过但间隔短；
 * - REMEMBER = 5: 记得，按 SM-2 正常拉长间隔。
 */
public final class Sm2Scheduler {
    public static final int QUALITY_FORGOT = 0;
    public static final int QUALITY_BLURRY = 3;
    public static final int QUALITY_REMEMBER = 5;

    private static final double DEFAULT_EASE = 2.5d;
    private static final double MIN_EASE = 1.3d;
    private static final long MINUTE = 60L * 1000L;
    private static final long DAY = 24L * 60L * MINUTE;

    private Sm2Scheduler() {
    }

    public static LearningReviewStore.ReviewState review(LearningReviewStore.ReviewState oldState, int quality, long now) {
        LearningReviewStore.ReviewState state = oldState == null
                ? new LearningReviewStore.ReviewState()
                : oldState.copy();

        quality = normalizeQuality(quality);
        double ease = state.easeFactor > 0 ? state.easeFactor : DEFAULT_EASE;
        int repetitions = Math.max(0, state.repetitions);
        int intervalDays = Math.max(0, state.intervalDays);
        long nextReviewAt;

        // Standard SM-2 ease update. Low confidence lowers future interval growth.
        ease = ease + (0.1d - (5 - quality) * (0.08d + (5 - quality) * 0.02d));
        if (ease < MIN_EASE) ease = MIN_EASE;

        if (quality < QUALITY_BLURRY) {
            // For mobile word learning, failed cards should come back soon instead of tomorrow.
            // This is an SM-2 inspired short retry step, then the card restarts its repetition count.
            repetitions = 0;
            intervalDays = 0;
            nextReviewAt = now + 10L * MINUTE;
        } else {
            repetitions += 1;
            if (repetitions == 1) {
                intervalDays = 1;
            } else if (repetitions == 2) {
                intervalDays = quality == QUALITY_BLURRY ? 2 : 6;
            } else {
                double multiplier = quality == QUALITY_BLURRY ? Math.max(1.2d, ease * 0.55d) : ease;
                intervalDays = Math.max(1, (int) Math.round(Math.max(1, intervalDays) * multiplier));
            }
            nextReviewAt = now + intervalDays * DAY;
        }

        state.easeFactor = ease;
        state.repetitions = repetitions;
        state.intervalDays = intervalDays;
        state.lastQuality = quality;
        state.lastReviewAt = now;
        state.nextReviewAt = nextReviewAt;
        state.reviewCount = Math.max(0, state.reviewCount) + 1;
        return state;
    }

    private static int normalizeQuality(int quality) {
        if (quality <= 1) return QUALITY_FORGOT;
        if (quality <= 3) return QUALITY_BLURRY;
        return QUALITY_REMEMBER;
    }
}
