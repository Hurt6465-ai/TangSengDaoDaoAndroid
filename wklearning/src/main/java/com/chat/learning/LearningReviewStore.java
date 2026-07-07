package com.chat.learning;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Local review-state store for the learning plugin.
 *
 * Word data can be static/offline, but review state must be user-specific and stored locally.
 * This first version uses SharedPreferences to avoid introducing database dependencies.
 * Later, if words exceed tens of thousands, move the same fields to Room/SQLite.
 */
public final class LearningReviewStore {
    private static final String PREF_NAME = "tsdd_learning_sm2";
    private static final String KEY_IDS = "word_ids";
    private static final double DEFAULT_EASE = 2.5d;

    private final SharedPreferences sp;

    public LearningReviewStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public ReviewState get(String wordId) {
        String key = key(wordId);
        ReviewState state = new ReviewState();
        state.wordId = wordId;
        state.easeFactor = Double.longBitsToDouble(sp.getLong(key + ".ease", Double.doubleToLongBits(DEFAULT_EASE)));
        state.repetitions = sp.getInt(key + ".repetitions", 0);
        state.intervalDays = sp.getInt(key + ".intervalDays", 0);
        state.lastQuality = sp.getInt(key + ".lastQuality", -1);
        state.lastReviewAt = sp.getLong(key + ".lastReviewAt", 0L);
        state.nextReviewAt = sp.getLong(key + ".nextReviewAt", 0L);
        state.reviewCount = sp.getInt(key + ".reviewCount", 0);
        return state;
    }

    public void save(ReviewState state) {
        if (state == null || state.wordId == null || state.wordId.length() == 0) return;
        String key = key(state.wordId);
        Set<String> ids = new HashSet<>(sp.getStringSet(KEY_IDS, new HashSet<>()));
        ids.add(state.wordId);
        sp.edit()
                .putStringSet(KEY_IDS, ids)
                .putLong(key + ".ease", Double.doubleToLongBits(state.easeFactor))
                .putInt(key + ".repetitions", state.repetitions)
                .putInt(key + ".intervalDays", state.intervalDays)
                .putInt(key + ".lastQuality", state.lastQuality)
                .putLong(key + ".lastReviewAt", state.lastReviewAt)
                .putLong(key + ".nextReviewAt", state.nextReviewAt)
                .putInt(key + ".reviewCount", state.reviewCount)
                .apply();
    }

    private String key(String wordId) {
        return "word." + wordId;
    }

    public static final class ReviewState {
        public String wordId;
        public double easeFactor = DEFAULT_EASE;
        public int repetitions = 0;
        public int intervalDays = 0;
        public int lastQuality = -1;
        public long lastReviewAt = 0L;
        public long nextReviewAt = 0L;
        public int reviewCount = 0;

        public boolean isDue(long now) {
            return nextReviewAt <= 0L || nextReviewAt <= now;
        }

        public ReviewState copy() {
            ReviewState copy = new ReviewState();
            copy.wordId = wordId;
            copy.easeFactor = easeFactor;
            copy.repetitions = repetitions;
            copy.intervalDays = intervalDays;
            copy.lastQuality = lastQuality;
            copy.lastReviewAt = lastReviewAt;
            copy.nextReviewAt = nextReviewAt;
            copy.reviewCount = reviewCount;
            return copy;
        }
    }
}
