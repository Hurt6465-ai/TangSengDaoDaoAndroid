package com.chat.learning;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** Pure lesson-session rules, intentionally independent from Android UI for unit testing. */
final class LessonSessionPolicy {
    private static final AtomicLong SEED_COUNTER = new AtomicLong(System.nanoTime());

    private LessonSessionPolicy() { }

    static long newSessionSeed() {
        return mix64(System.nanoTime() ^ SEED_COUNTER.incrementAndGet());
    }

    static <T> void shuffle(List<T> values, long sessionSeed, String exerciseId, String purpose) {
        if (values == null || values.size() < 2) return;
        long seed = sessionSeed;
        seed ^= ((long) safeHash(exerciseId) << 32);
        seed ^= safeHash(purpose);
        Collections.shuffle(values, new Random(mix64(seed)));
    }

    static int firstAttemptScore(int correct, int total) {
        if (total <= 0) return 0;
        int safeCorrect = Math.max(0, Math.min(correct, total));
        return Math.max(0, Math.min(100, safeCorrect * 100 / total));
    }

    /** Finishing every exercise is the pass condition; first-attempt accuracy controls stars. */
    static boolean passed(int mastered, int total) {
        return total > 0 && mastered >= total;
    }

    static int stars(int score, boolean passed, int configuredPassingScore) {
        if (!passed) return 0;
        int safeScore = Math.max(0, Math.min(100, score));
        int twoStarThreshold = Math.max(75, Math.min(89, configuredPassingScore));
        if (safeScore >= 90) return 3;
        if (safeScore >= twoStarThreshold) return 2;
        return 1;
    }

    static boolean matchingCorrect(boolean hadWrongAttempt) {
        return !hadWrongAttempt;
    }

    private static int safeHash(String value) {
        return value == null ? 0 : value.hashCode();
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
