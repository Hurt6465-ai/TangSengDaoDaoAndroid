package com.chat.learning;

import java.util.EnumMap;
import java.util.Map;

/**
 * Compact FSRS v6 scheduler for Android word cards.
 *
 * Uses the official default 21 parameters, 90% desired retention, 1/10 minute learning steps,
 * a 10 minute relearning step and no interval fuzzing so previewed intervals equal committed ones.
 */
final class WordFsrsScheduler {
    private static final long MINUTE = 60_000L;
    private static final long DAY = 86_400_000L;
    private static final int MAX_INTERVAL_DAYS = 36_500;
    private static final double DESIRED_RETENTION = 0.90;
    private static final double STABILITY_MIN = 0.001;
    private static final double[] W = {
            0.2172, 1.1771, 3.2602, 16.1507, 7.0114, 0.57, 2.0966, 0.0069,
            1.5261, 0.112, 1.0178, 1.849, 0.1133, 0.3127, 2.2934, 0.2191,
            3.0004, 0.7536, 0.3332, 0.1437, 0.2
    };
    private static final double DECAY = -W[20];
    private static final double FACTOR = Math.pow(0.9, 1.0 / DECAY) - 1.0;

    enum Rating {
        AGAIN(1), HARD(2), GOOD(3), EASY(4);
        final int value;
        Rating(int value) { this.value = value; }
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

    Map<Rating, Result> preview(CardState source, long now) {
        EnumMap<Rating, Result> results = new EnumMap<>(Rating.class);
        for (Rating rating : Rating.values()) results.put(rating, review(source, rating, now));
        return results;
    }

    Result review(CardState source, Rating rating, long now) {
        CardState card = source == null ? new CardState() : source.copy();
        if (now <= 0) now = System.currentTimeMillis();
        long interval;
        long elapsedDays = card.lastReviewAt <= 0 ? 0 : Math.max(0L, (now - card.lastReviewAt) / DAY);

        switch (card.state) {
            case LEARNING:
                updateMemory(card, rating, elapsedDays, now);
                interval = scheduleLearning(card, rating);
                break;
            case REVIEW:
                updateMemory(card, rating, elapsedDays, now);
                if (rating == Rating.AGAIN) {
                    card.state = State.RELEARNING;
                    card.step = 0;
                    card.lapseCount += 1;
                    interval = 10L * MINUTE;
                } else {
                    interval = days(nextInterval(card.stability));
                }
                break;
            case RELEARNING:
            default:
                updateMemory(card, rating, elapsedDays, now);
                interval = scheduleRelearning(card, rating);
                break;
        }

        card.lastReviewAt = now;
        card.dueAt = now + interval;
        card.reviewCount += 1;
        return new Result(rating, card, interval);
    }

    private long scheduleLearning(CardState card, Rating rating) {
        switch (rating) {
            case AGAIN:
                card.step = 0;
                return MINUTE;
            case HARD:
                if (card.step <= 0) return Math.round((MINUTE + 10L * MINUTE) / 2.0);
                return 10L * MINUTE;
            case GOOD:
                if (card.step <= 0) {
                    card.step = 1;
                    return 10L * MINUTE;
                }
                card.state = State.REVIEW;
                card.step = -1;
                return days(nextInterval(card.stability));
            case EASY:
            default:
                card.state = State.REVIEW;
                card.step = -1;
                return days(nextInterval(card.stability));
        }
    }

    private long scheduleRelearning(CardState card, Rating rating) {
        switch (rating) {
            case AGAIN:
                card.step = 0;
                return 10L * MINUTE;
            case HARD:
                return 15L * MINUTE;
            case GOOD:
            case EASY:
            default:
                card.state = State.REVIEW;
                card.step = -1;
                return days(nextInterval(card.stability));
        }
    }

    private void updateMemory(CardState card, Rating rating, long elapsedDays, long now) {
        if (Double.isNaN(card.stability) || Double.isNaN(card.difficulty)) {
            card.stability = initialStability(rating);
            card.difficulty = initialDifficulty(rating);
            return;
        }
        if (card.lastReviewAt > 0 && now - card.lastReviewAt < DAY) {
            card.stability = shortTermStability(card.stability, rating);
            card.difficulty = nextDifficulty(card.difficulty, rating);
            return;
        }
        double retrievability = retrievability(card.stability, elapsedDays);
        card.stability = nextStability(card.difficulty, card.stability, retrievability, rating);
        card.difficulty = nextDifficulty(card.difficulty, rating);
    }

    private double retrievability(double stability, long elapsedDays) {
        return Math.pow(1.0 + FACTOR * elapsedDays / clampStability(stability), DECAY);
    }

    private double initialStability(Rating rating) {
        return clampStability(W[rating.value - 1]);
    }

    private double initialDifficulty(Rating rating) {
        return clampDifficulty(W[4] - Math.exp(W[5] * (rating.value - 1)) + 1.0);
    }

    private double shortTermStability(double stability, Rating rating) {
        double increase = Math.exp(W[17] * (rating.value - 3 + W[18]))
                * Math.pow(stability, -W[19]);
        if (rating == Rating.GOOD || rating == Rating.EASY) increase = Math.max(increase, 1.0);
        return clampStability(stability * increase);
    }

    private double nextDifficulty(double difficulty, Rating rating) {
        double delta = -(W[6] * (rating.value - 3));
        double damped = difficulty + (10.0 - difficulty) * delta / 9.0;
        return clampDifficulty(W[7] * initialDifficulty(Rating.EASY) + (1.0 - W[7]) * damped);
    }

    private double nextStability(double difficulty, double stability, double retrievability, Rating rating) {
        double next;
        if (rating == Rating.AGAIN) {
            double longTerm = W[11] * Math.pow(difficulty, -W[12])
                    * (Math.pow(stability + 1.0, W[13]) - 1.0)
                    * Math.exp((1.0 - retrievability) * W[14]);
            double shortTerm = stability / Math.exp(W[17] * W[18]);
            next = Math.min(longTerm, shortTerm);
        } else {
            double hardPenalty = rating == Rating.HARD ? W[15] : 1.0;
            double easyBonus = rating == Rating.EASY ? W[16] : 1.0;
            next = stability * (1.0 + Math.exp(W[8]) * (11.0 - difficulty)
                    * Math.pow(stability, -W[9])
                    * (Math.exp((1.0 - retrievability) * W[10]) - 1.0)
                    * hardPenalty * easyBonus);
        }
        return clampStability(next);
    }

    private int nextInterval(double stability) {
        int interval = (int) Math.round((stability / FACTOR)
                * (Math.pow(DESIRED_RETENTION, 1.0 / DECAY) - 1.0));
        return Math.min(MAX_INTERVAL_DAYS, Math.max(1, interval));
    }

    private double clampStability(double value) {
        return Math.max(STABILITY_MIN, value);
    }

    private double clampDifficulty(double value) {
        return Math.min(10.0, Math.max(1.0, value));
    }

    private long days(int value) {
        return value * DAY;
    }
}
