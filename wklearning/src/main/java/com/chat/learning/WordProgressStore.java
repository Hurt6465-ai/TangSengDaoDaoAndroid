package com.chat.learning;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

/** SQLite progress storage. The composite key prevents collisions between packs and languages. */
final class WordProgressStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "tsdd_learning.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "word_progress";
    private final Context app;

    WordProgressStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        app = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "pack_id TEXT NOT NULL,"
                + "source_lang TEXT NOT NULL,"
                + "target_lang TEXT NOT NULL,"
                + "word_id TEXT NOT NULL,"
                + "state INTEGER NOT NULL DEFAULT 0,"
                + "step_index INTEGER NOT NULL DEFAULT 0,"
                + "stability REAL,"
                + "difficulty REAL,"
                + "due_at INTEGER NOT NULL DEFAULT 0,"
                + "last_review_at INTEGER NOT NULL DEFAULT 0,"
                + "review_count INTEGER NOT NULL DEFAULT 0,"
                + "lapse_count INTEGER NOT NULL DEFAULT 0,"
                + "favorite INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(pack_id, source_lang, target_lang, word_id)"
                + ")");
        db.execSQL("CREATE INDEX idx_word_progress_due ON " + TABLE + "(target_lang, due_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first SQLite-backed schema. Future versions must use explicit migrations.
    }

    WordFsrsScheduler.CardState load(String packId, String wordId) {
        WordFsrsScheduler.CardState state = new WordFsrsScheduler.CardState();
        Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"state", "step_index", "stability", "difficulty", "due_at",
                        "last_review_at", "review_count", "lapse_count"},
                "pack_id=? AND source_lang=? AND target_lang=? AND word_id=?",
                new String[]{safe(packId), "zh", "my", safe(wordId)}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) return migrateLegacy(packId, wordId, state);
            int stateValue = cursor.getInt(0);
            state.state = stateValue == 1 ? WordFsrsScheduler.State.REVIEW
                    : stateValue == 2 ? WordFsrsScheduler.State.RELEARNING
                    : WordFsrsScheduler.State.LEARNING;
            state.step = cursor.getInt(1);
            state.stability = cursor.isNull(2) ? Double.NaN : cursor.getDouble(2);
            state.difficulty = cursor.isNull(3) ? Double.NaN : cursor.getDouble(3);
            state.dueAt = cursor.getLong(4);
            state.lastReviewAt = cursor.getLong(5);
            state.reviewCount = cursor.getInt(6);
            state.lapseCount = cursor.getInt(7);
            return state;
        } finally {
            cursor.close();
        }
    }

    void save(String packId, String wordId, WordFsrsScheduler.CardState state) {
        ContentValues values = keyValues(packId, wordId);
        values.put("state", state.state == WordFsrsScheduler.State.REVIEW ? 1
                : state.state == WordFsrsScheduler.State.RELEARNING ? 2 : 0);
        values.put("step_index", state.step);
        if (Double.isNaN(state.stability)) values.putNull("stability"); else values.put("stability", state.stability);
        if (Double.isNaN(state.difficulty)) values.putNull("difficulty"); else values.put("difficulty", state.difficulty);
        values.put("due_at", state.dueAt);
        values.put("last_review_at", state.lastReviewAt);
        values.put("review_count", state.reviewCount);
        values.put("lapse_count", state.lapseCount);
        values.put("favorite", isFavorite(packId, wordId) ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    boolean isFavorite(String packId, String wordId) {
        Cursor cursor = getReadableDatabase().query(TABLE, new String[]{"favorite"},
                "pack_id=? AND source_lang=? AND target_lang=? AND word_id=?",
                new String[]{safe(packId), "zh", "my", safe(wordId)}, null, null, null, "1");
        try {
            if (cursor.moveToFirst()) return cursor.getInt(0) == 1;
            return app.getSharedPreferences("tsdd_word_study", Context.MODE_PRIVATE)
                    .getBoolean("fav." + safe(wordId), false);
        } finally {
            cursor.close();
        }
    }

    boolean toggleFavorite(String packId, String wordId) {
        boolean next = !isFavorite(packId, wordId);
        WordFsrsScheduler.CardState state = load(packId, wordId);
        ContentValues values = keyValues(packId, wordId);
        values.put("favorite", next ? 1 : 0);
        values.put("state", state.state == WordFsrsScheduler.State.REVIEW ? 1
                : state.state == WordFsrsScheduler.State.RELEARNING ? 2 : 0);
        values.put("step_index", state.step);
        if (Double.isNaN(state.stability)) values.putNull("stability"); else values.put("stability", state.stability);
        if (Double.isNaN(state.difficulty)) values.putNull("difficulty"); else values.put("difficulty", state.difficulty);
        values.put("due_at", state.dueAt);
        values.put("last_review_at", state.lastReviewAt);
        values.put("review_count", state.reviewCount);
        values.put("lapse_count", state.lapseCount);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return next;
    }

    Map<String, WordFsrsScheduler.CardState> loadPack(String packId) {
        HashMap<String, WordFsrsScheduler.CardState> result = new HashMap<>();
        Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"word_id", "state", "step_index", "stability", "difficulty", "due_at",
                        "last_review_at", "review_count", "lapse_count"},
                "pack_id=? AND source_lang=? AND target_lang=?",
                new String[]{safe(packId), "zh", "my"}, null, null, null);
        try {
            while (cursor.moveToNext()) {
                WordFsrsScheduler.CardState state = new WordFsrsScheduler.CardState();
                int stateValue = cursor.getInt(1);
                state.state = stateValue == 1 ? WordFsrsScheduler.State.REVIEW
                        : stateValue == 2 ? WordFsrsScheduler.State.RELEARNING
                        : WordFsrsScheduler.State.LEARNING;
                state.step = cursor.getInt(2);
                state.stability = cursor.isNull(3) ? Double.NaN : cursor.getDouble(3);
                state.difficulty = cursor.isNull(4) ? Double.NaN : cursor.getDouble(4);
                state.dueAt = cursor.getLong(5);
                state.lastReviewAt = cursor.getLong(6);
                state.reviewCount = cursor.getInt(7);
                state.lapseCount = cursor.getInt(8);
                result.put(cursor.getString(0), state);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    int countDue(String packId, long now) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE pack_id=? AND source_lang='zh' AND target_lang='my' AND review_count>0 AND due_at<=?",
                new String[]{safe(packId), Long.toString(now)});
        try { return cursor.moveToFirst() ? cursor.getInt(0) : 0; }
        finally { cursor.close(); }
    }


    private WordFsrsScheduler.CardState migrateLegacy(String packId, String wordId,
                                                       WordFsrsScheduler.CardState fallback) {
        android.content.SharedPreferences legacy = app.getSharedPreferences(
                "tsdd_learning_sm2", Context.MODE_PRIVATE);
        String key = "w." + safe(wordId);
        int count = legacy.getInt(key + ".count", 0);
        if (count <= 0) return fallback;
        WordFsrsScheduler.CardState state = new WordFsrsScheduler.CardState();
        state.reviewCount = count;
        state.lapseCount = legacy.getInt(key + ".lapse", 0);
        state.lastReviewAt = legacy.getLong(key + ".last", 0L);
        state.dueAt = legacy.getLong(key + ".next", 0L);
        int intervalDays = Math.max(1, legacy.getInt(key + ".int", 1));
        int quality = legacy.getInt(key + ".q", 3);
        state.stability = Math.max(0.1, intervalDays);
        state.difficulty = quality <= 1 ? 8.0 : quality == 2 ? 6.0 : 4.5;
        state.state = state.dueAt > 0 ? WordFsrsScheduler.State.REVIEW
                : WordFsrsScheduler.State.LEARNING;
        state.step = state.state == WordFsrsScheduler.State.REVIEW ? -1 : 0;
        save(packId, wordId, state);
        return state;
    }

    private ContentValues keyValues(String packId, String wordId) {
        ContentValues values = new ContentValues();
        values.put("pack_id", safe(packId));
        values.put("source_lang", "zh");
        values.put("target_lang", "my");
        values.put("word_id", safe(wordId));
        return values;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
