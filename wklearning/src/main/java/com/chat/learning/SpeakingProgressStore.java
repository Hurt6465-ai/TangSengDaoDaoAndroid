package com.chat.learning;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

/** Independent FSRS and practice statistics for speaking phrases. */
final class SpeakingProgressStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "tsdd_speaking.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "speaking_phrase_progress";

    SpeakingProgressStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "pack_id TEXT NOT NULL,"
                + "phrase_id TEXT NOT NULL,"
                + "state INTEGER NOT NULL DEFAULT 0,"
                + "step_index INTEGER NOT NULL DEFAULT 0,"
                + "stability REAL,"
                + "difficulty REAL,"
                + "due_at INTEGER NOT NULL DEFAULT 0,"
                + "last_review_at INTEGER NOT NULL DEFAULT 0,"
                + "review_count INTEGER NOT NULL DEFAULT 0,"
                + "lapse_count INTEGER NOT NULL DEFAULT 0,"
                + "listen_count INTEGER NOT NULL DEFAULT 0,"
                + "spelling_count INTEGER NOT NULL DEFAULT 0,"
                + "pronunciation_count INTEGER NOT NULL DEFAULT 0,"
                + "ai_practice_count INTEGER NOT NULL DEFAULT 0,"
                + "favorite INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(pack_id, phrase_id)"
                + ")");
        db.execSQL("CREATE INDEX idx_speaking_due ON " + TABLE + "(due_at)");
        db.execSQL("CREATE INDEX idx_speaking_pack ON " + TABLE + "(pack_id, review_count)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // First version. Add explicit migrations when the schema changes.
    }

    WordFsrsScheduler.CardState load(String packId, String phraseId) {
        WordFsrsScheduler.CardState state = new WordFsrsScheduler.CardState();
        Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"state", "step_index", "stability", "difficulty", "due_at",
                        "last_review_at", "review_count", "lapse_count"},
                "pack_id=? AND phrase_id=?", new String[]{safe(packId), safe(phraseId)},
                null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) return state;
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

    void save(String packId, String phraseId, WordFsrsScheduler.CardState state) {
        ContentValues values = loadAllValues(packId, phraseId);
        putState(values, state);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    void markViewed(String packId, String phraseId) {
        ContentValues values = loadAllValues(packId, phraseId);
        Integer reviews = values.getAsInteger("review_count");
        if (reviews != null && reviews > 0) return;
        long now = System.currentTimeMillis();
        values.put("state", 1);
        values.put("review_count", 1);
        values.put("last_review_at", now);
        values.put("due_at", Long.MAX_VALUE);
        values.put("updated_at", now);
        getWritableDatabase().insertWithOnConflict(TABLE, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    void increment(String packId, String phraseId, String column) {
        if (!("listen_count".equals(column) || "spelling_count".equals(column)
                || "pronunciation_count".equals(column) || "ai_practice_count".equals(column))) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = loadAllValues(packId, phraseId);
            int current = values.getAsInteger(column) == null ? 0 : values.getAsInteger(column);
            values.put(column, current + 1);
            values.put("updated_at", System.currentTimeMillis());
            db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    boolean toggleFavorite(String packId, String phraseId) {
        ContentValues values = loadAllValues(packId, phraseId);
        boolean next = values.getAsInteger("favorite") == null || values.getAsInteger("favorite") == 0;
        values.put("favorite", next ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        return next;
    }

    boolean isFavorite(String packId, String phraseId) {
        Cursor cursor = getReadableDatabase().query(TABLE, new String[]{"favorite"},
                "pack_id=? AND phrase_id=?", new String[]{safe(packId), safe(phraseId)},
                null, null, null, "1");
        try {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        } finally {
            cursor.close();
        }
    }

    PackStats stats(String packId, int total, long now) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*),"
                        + "SUM(CASE WHEN review_count>0 THEN 1 ELSE 0 END),"
                        + "SUM(CASE WHEN review_count>0 AND due_at<=? THEN 1 ELSE 0 END) "
                        + "FROM " + TABLE + " WHERE pack_id=?",
                new String[]{Long.toString(now), safe(packId)});
        try {
            if (!cursor.moveToFirst()) return new PackStats(total, 0, 0);
            int learned = cursor.isNull(1) ? 0 : cursor.getInt(1);
            int due = cursor.isNull(2) ? 0 : cursor.getInt(2);
            return new PackStats(total, learned, due);
        } finally {
            cursor.close();
        }
    }

    Map<String, WordFsrsScheduler.CardState> loadPack(String packId) {
        HashMap<String, WordFsrsScheduler.CardState> result = new HashMap<>();
        Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"phrase_id", "state", "step_index", "stability", "difficulty",
                        "due_at", "last_review_at", "review_count", "lapse_count"},
                "pack_id=?", new String[]{safe(packId)}, null, null, null);
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

    private ContentValues loadAllValues(String packId, String phraseId) {
        ContentValues values = new ContentValues();
        values.put("pack_id", safe(packId));
        values.put("phrase_id", safe(phraseId));
        values.put("state", 0);
        values.put("step_index", 0);
        values.putNull("stability");
        values.putNull("difficulty");
        values.put("due_at", 0L);
        values.put("last_review_at", 0L);
        values.put("review_count", 0);
        values.put("lapse_count", 0);
        values.put("listen_count", 0);
        values.put("spelling_count", 0);
        values.put("pronunciation_count", 0);
        values.put("ai_practice_count", 0);
        values.put("favorite", 0);

        Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"state", "step_index", "stability", "difficulty", "due_at",
                        "last_review_at", "review_count", "lapse_count", "listen_count",
                        "spelling_count", "pronunciation_count", "ai_practice_count", "favorite"},
                "pack_id=? AND phrase_id=?", new String[]{safe(packId), safe(phraseId)},
                null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) return values;
            String[] names = {"state", "step_index", "stability", "difficulty", "due_at",
                    "last_review_at", "review_count", "lapse_count", "listen_count",
                    "spelling_count", "pronunciation_count", "ai_practice_count", "favorite"};
            for (int i = 0; i < names.length; i++) {
                if (cursor.isNull(i)) values.putNull(names[i]);
                else if (i == 2 || i == 3) values.put(names[i], cursor.getDouble(i));
                else if (i == 4 || i == 5) values.put(names[i], cursor.getLong(i));
                else values.put(names[i], cursor.getInt(i));
            }
            return values;
        } finally {
            cursor.close();
        }
    }

    private void putState(ContentValues values, WordFsrsScheduler.CardState state) {
        values.put("state", state.state == WordFsrsScheduler.State.REVIEW ? 1
                : state.state == WordFsrsScheduler.State.RELEARNING ? 2 : 0);
        values.put("step_index", state.step);
        if (Double.isNaN(state.stability)) values.putNull("stability");
        else values.put("stability", state.stability);
        if (Double.isNaN(state.difficulty)) values.putNull("difficulty");
        else values.put("difficulty", state.difficulty);
        values.put("due_at", state.dueAt);
        values.put("last_review_at", state.lastReviewAt);
        values.put("review_count", state.reviewCount);
        values.put("lapse_count", state.lapseCount);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static final class PackStats {
        final int total;
        final int learned;
        final int due;

        PackStats(int total, int learned, int due) {
            this.total = Math.max(0, total);
            this.learned = Math.max(0, learned);
            this.due = Math.max(0, due);
        }
    }
}
