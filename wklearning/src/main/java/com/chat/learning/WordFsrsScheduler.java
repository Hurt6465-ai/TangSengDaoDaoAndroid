package com.chat.learning;

import java.util.EnumMap;
import java.util.Map;

/**
 * FSRS-6 scheduler for Chinese word cards.
 *
 * <p>This implementation follows the current Open Spaced Repetition FSRS-6 formulas and
 * official default 21-parameter set. It intentionally disables interval fuzzing so the four
 * intervals previewed in the UI are exactly the intervals committed after a rating.</p>
 *
 * <p>Configuration:</p>
 * <ul>
 *     <li>desired retention: 0.90</li>
 *     <li>learning steps: 1 minute, 10 minutes</li>
 *     <li>relearning step: 10 minutes</li>
 *     <li>maximum interval: 36,500 days</li>
 * </ul>
 */
final class WordFsrsScheduler {
    static final String ALGORITHM_VERSION = "FSRS-6/py-fsrs-6.3.1";
    static final int PARAMETER_SET_VERSION = 620;

    private static final long MINUTE = 60_000L;
    private static final long DAY = 86_400_000L;
    private static final int NO_STEP = -1;
    private static final int MAX_INTERVAL_DAYS = 36_500;
    private static final double DESIRED_RETENTION = 0.90;
    private static final double STABILITY_MIN = 0.001;

    /** Official FSRS-6 default parameters shown in The Algorithm wiki. */
    private static final double[] W = {
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194,
            0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629,
            1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542
    };

    private static final long[] LEARNING_STEPS = {MINUTE, 10L * MINUTE};
    private static final long[] RELEARNING_STEPS = {10L * MINUTE};

    private static final double DECAY = -W[20];
    private static final double FACTOR = Math.pow(0.9, 1.0 / DECAY) - 1.0;

    enum Rating {
        AGAIN(1), HARD(2), GOOD(3), EASY(4);

        final int value;

        Rating(int value) {
            this.value = value;
        }
    }

    enum State {
        LEARNING, REVIEW, RELEARNING
    }

    static final class CardState {
        State state = State.LEARNING;
        int step = 0;
        double stability = Double.NaN;
        double difficulty = Double.NaN;
        long dueAt = 0L;
        long lastReviewAt = 0L;
        int reviewCount = 0;
        int lapseCount = 0;

        CardState copy() {
            CardState out = new CardState();
            out.state = state;
            out.step = step;
            out.stability = stability;
            out.difficulty = difficulty;
            out.dueAt = dueAt;
            out.lastReviewAt = lastReviewAt;
            out.reviewCount = reviewCount;
            out.lapseCount = lapseCount;
            return out;
        }
    }

    static final class Result {
        final Rating rating;
        final CardState card;
        final long intervalMillis;

        Result(Rating rating, CardState card, long intervalMillis) {
            this.rating = rating;
            this.card = card;
            this.intervalMillis = intervalMillis;
        }
    }

    /** Returns all four possible outcomes without mutating the supplied card. */
    Map<Rating, Result> preview(CardState source, long now) {
        EnumMap<Rating, Result> results = new EnumMap<>(Rating.class);
        for (Rating rating : Rating.values()) {
            results.put(rating, review(source, rating, now));
        }
        return results;
    }

    /** Applies one rating and returns a copied, updated card state. */
    Result review(CardState source, Rating rating, long now) {
        if (rating == null) {
            throw new IllegalArgumentException("rating == null");
        }

        CardState card = source == null ? new CardState() : source.copy();
        normalizeState(card);
        if (now <= 0L) {
            now = System.currentTimeMillis();
        }

        boolean hasPreviousReview = card.lastReviewAt > 0L;
        long daysSinceLastReview = hasPreviousReview
                ? Math.floorDiv(now - card.lastReviewAt, DAY)
                : 0L;

        updateMemory(card, rating, hasPreviousReview, daysSinceLastReview);

        long interval;
        switch (card.state) {
            case LEARNING:
                interval = scheduleLearning(card, rating);
                break;
            case REVIEW:
                interval = scheduleReview(card, rating);
                break;
            case RELEARNING:
            default:
                interval = scheduleRelearning(card, rating);
                break;
        }

        card.lastReviewAt = now;
        card.dueAt = safeAdd(now, interval);
        card.reviewCount += 1;
        return new Result(rating, card, interval);
    }

    /** Current recall probability, using full elapsed days as in the official reference. */
    double getRetrievability(CardState card, long now) {
        if (card == null || card.lastReviewAt <= 0L || !isFinite(card.stability)) {
            return 0.0;
        }
        long elapsedDays = Math.max(0L, Math.floorDiv(now - card.lastReviewAt, DAY));
        return retrievability(card.stability, elapsedDays);
    }

    private long scheduleLearning(CardState card, Rating rating) {
        if (LEARNING_STEPS.length == 0
                || (card.step >= LEARNING_STEPS.length && rating != Rating.AGAIN)) {
            graduateToReview(card);
            return days(nextInterval(card.stability));
        }

        switch (rating) {
            case AGAIN:
                card.step = 0;
                return LEARNING_STEPS.length == 0
                        ? days(nextInterval(card.stability))
                        : LEARNING_STEPS[0];
            case HARD:
                return hardStepInterval(LEARNING_STEPS, card.step);
            case GOOD:
                if (card.step + 1 >= LEARNING_STEPS.length) {
                    graduateToReview(card);
                    return days(nextInterval(card.stability));
                }
                card.step += 1;
                return LEARNING_STEPS[card.step];
            case EASY:
            default:
                graduateToReview(card);
                return days(nextInterval(card.stability));
        }
    }

    private long scheduleReview(CardState card, Rating rating) {
        if (rating == Rating.AGAIN && RELEARNING_STEPS.length > 0) {
            card.state = State.RELEARNING;
            card.step = 0;
            card.lapseCount += 1;
            return RELEARNING_STEPS[0];
        }
        if (rating == Rating.AGAIN) {
            card.lapseCount += 1;
        }
        return days(nextInterval(card.stability));
    }

    private long scheduleRelearning(CardState card, Rating rating) {
        if (RELEARNING_STEPS.length == 0
                || (card.step >= RELEARNING_STEPS.length && rating != Rating.AGAIN)) {
            graduateToReview(card);
            return days(nextInterval(card.stability));
        }

        switch (rating) {
            case AGAIN:
                card.step = 0;
                return RELEARNING_STEPS.length == 0
                        ? days(nextInterval(card.stability))
                        : RELEARNING_STEPS[0];
            case HARD:
                return hardStepInterval(RELEARNING_STEPS, card.step);
            case GOOD:
                if (card.step + 1 >= RELEARNING_STEPS.length) {
                    graduateToReview(card);
                    return days(nextInterval(card.stability));
                }
                card.step += 1;
                return RELEARNING_STEPS[card.step];
            case EASY:
            default:
                graduateToReview(card);
                return days(nextInterval(card.stability));
        }
    }

    private long hardStepInterval(long[] steps, int step) {
        if (steps.length == 0) {
            return DAY;
        }
        int safeStep = Math.max(0, Math.min(step, steps.length - 1));
        if (safeStep == 0 && steps.length == 1) {
            return Math.round(steps[0] * 1.5d);
        }
        if (safeStep == 0) {
            return Math.round((steps[0] + steps[1]) / 2.0d);
        }
        return steps[safeStep];
    }

    private void graduateToReview(CardState card) {
        card.state = State.REVIEW;
        card.step = NO_STEP;
    }

    private void updateMemory(CardState card, Rating rating, boolean hasPreviousReview,
                              long daysSinceLastReview) {
        if (!isFinite(card.stability) || !isFinite(card.difficulty)) {
            card.stability = initialStability(rating);
            card.difficulty = initialDifficulty(rating, true);
            return;
        }

        if (hasPreviousReview && daysSinceLastReview < 1L) {
            card.stability = shortTermStability(card.stability, rating);
            card.difficulty = nextDifficulty(card.difficulty, rating);
            return;
        }

        double retrievability = retrievability(
                card.stability, Math.max(0L, daysSinceLastReview));
        card.stability = nextStability(
                card.difficulty, card.stability, retrievability, rating);
        card.difficulty = nextDifficulty(card.difficulty, rating);
    }

    private double retrievability(double stability, long elapsedDays) {
        return Math.pow(
                1.0 + FACTOR * elapsedDays / clampStability(stability),
                DECAY);
    }

    private double initialStability(Rating rating) {
        return clampStability(W[rating.value - 1]);
    }

    private double initialDifficulty(Rating rating, boolean clamp) {
        double value = W[4] - Math.exp(W[5] * (rating.value - 1)) + 1.0;
        return clamp ? clampDifficulty(value) : value;
    }

    private double shortTermStability(double stability, Rating rating) {
        double increase = Math.exp(W[17] * (rating.value - 3 + W[18]))
                * Math.pow(stability, -W[19]);
        if (rating == Rating.GOOD || rating == Rating.EASY) {
            increase = Math.max(increase, 1.0);
        }
        return clampStability(stability * increase);
    }

    private double nextDifficulty(double difficulty, Rating rating) {
        double deltaDifficulty = -(W[6] * (rating.value - 3));
        double linearDamping = (10.0 - difficulty) * deltaDifficulty / 9.0;
        double dampedDifficulty = difficulty + linearDamping;

        // FSRS-5/6 mean reversion target is the un-clamped D0(Easy).
        double target = initialDifficulty(Rating.EASY, false);
        double next = W[7] * target + (1.0 - W[7]) * dampedDifficulty;
        return clampDifficulty(next);
    }

    private double nextStability(double difficulty, double stability,
                                 double retrievability, Rating rating) {
        double next;
        if (rating == Rating.AGAIN) {
            double longTerm = W[11]
                    * Math.pow(difficulty, -W[12])
                    * (Math.pow(stability + 1.0, W[13]) - 1.0)
                    * Math.exp((1.0 - retrievability) * W[14]);
            double shortTerm = stability / Math.exp(W[17] * W[18]);
            next = Math.min(longTerm, shortTerm);
        } else {
            double hardPenalty = rating == Rating.HARD ? W[15] : 1.0;
            double easyBonus = rating == Rating.EASY ? W[16] : 1.0;
            next = stability * (1.0
                    + Math.exp(W[8])
                    * (11.0 - difficulty)
                    * Math.pow(stability, -W[9])
                    * (Math.exp((1.0 - retrievability) * W[10]) - 1.0)
                    * hardPenalty
                    * easyBonus);
        }
        return clampStability(next);
    }

    private int nextInterval(double stability) {
        double raw = (stability / FACTOR)
                * (Math.pow(DESIRED_RETENTION, 1.0 / DECAY) - 1.0);

        // Python's official reference uses round(), which is ties-to-even.
        long rounded = (long) Math.rint(raw);
        return (int) Math.min(MAX_INTERVAL_DAYS, Math.max(1L, rounded));
    }

    private void normalizeState(CardState card) {
        if (card.state == null) {
            card.state = State.LEARNING;
        }
        if (card.state == State.REVIEW) {
            card.step = NO_STEP;
        } else if (card.step < 0) {
            card.step = 0;
        }
        if (isFinite(card.stability)) {
            card.stability = clampStability(card.stability);
        }
        if (isFinite(card.difficulty)) {
            card.difficulty = clampDifficulty(card.difficulty);
        }
    }

    private double clampStability(double value) {
        return Math.max(STABILITY_MIN, value);
    }

    private double clampDifficulty(double value) {
        return Math.min(10.0, Math.max(1.0, value));
    }

    private boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private long days(int value) {
        return value * DAY;
    }

    private long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
