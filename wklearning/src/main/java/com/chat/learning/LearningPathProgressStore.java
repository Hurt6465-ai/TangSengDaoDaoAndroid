package com.chat.learning;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

/** Stores stable lesson progress separately from remotely replaceable course files. */
final class LearningPathProgressStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "learning_path_progress.db";
    private static final int DB_VERSION = 2;
    private static final String TABLE = "lesson_progress";

    LearningPathProgressStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + "("
                + "course_id TEXT NOT NULL,"
                + "lesson_id TEXT NOT NULL,"
                + "best_score INTEGER NOT NULL DEFAULT 0,"
                + "stars INTEGER NOT NULL DEFAULT 0,"
                + "attempts INTEGER NOT NULL DEFAULT 0,"
                + "last_score INTEGER NOT NULL DEFAULT 0,"
                + "last_passed INTEGER NOT NULL DEFAULT 0,"
                + "completed_at INTEGER NOT NULL DEFAULT 0,"
                + "last_opened_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(course_id, lesson_id)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_learning_path_completed ON " + TABLE
                + "(course_id, completed_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumn(db, "last_score", "INTEGER NOT NULL DEFAULT 0");
            addColumn(db, "last_passed", "INTEGER NOT NULL DEFAULT 0");
        }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_learning_path_completed ON " + TABLE
                    + "(course_id, completed_at)");
        } catch (Throwable ignored) { }
    }

    Map<String, Progress> loadCourse(String courseId) {
        HashMap<String, Progress> result = new HashMap<>();
        String safeCourse = safeId(courseId);
        if (safeCourse.isEmpty()) return result;
        try (Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"lesson_id", "best_score", "stars", "attempts", "last_score",
                        "last_passed", "completed_at", "last_opened_at"},
                "course_id=?", new String[]{safeCourse}, null, null, null)) {
            while (cursor.moveToNext()) {
                Progress value = new Progress();
                value.lessonId = cursor.getString(0);
                value.bestScore = cursor.getInt(1);
                value.stars = cursor.getInt(2);
                value.attempts = cursor.getInt(3);
                value.lastScore = cursor.getInt(4);
                value.lastPassed = cursor.getInt(5) == 1;
                value.completedAt = cursor.getLong(6);
                value.lastOpenedAt = cursor.getLong(7);
                result.put(value.lessonId, value);
            }
        } catch (Throwable ignored) {
            // A damaged/full database must not make the learning page crash.
        }
        return result;
    }

    void markOpened(String courseId, String lessonId) {
        mutate(courseId, lessonId, values ->
                values.put("last_opened_at", System.currentTimeMillis()));
    }

    void recordAttempt(String courseId, String lessonId, int score, int stars, boolean passed) {
        mutate(courseId, lessonId, values -> {
            int safeScore = clamp(score, 0, 100);
            int safeStars = clamp(stars, 0, 3);
            values.put("best_score", Math.max(intValue(values, "best_score"), safeScore));
            values.put("stars", Math.max(intValue(values, "stars"), passed ? safeStars : 0));
            values.put("attempts", intValue(values, "attempts") + 1);
            values.put("last_score", safeScore);
            values.put("last_passed", passed ? 1 : 0);
            if (passed && longValue(values, "completed_at") <= 0L) {
                values.put("completed_at", System.currentTimeMillis());
            }
            values.put("last_opened_at", System.currentTimeMillis());
        });
    }

    void markCompleted(String courseId, String lessonId, int score, int stars) {
        recordAttempt(courseId, lessonId, score, stars, true);
    }

    private void mutate(String courseId, String lessonId, Mutator mutator) {
        String safeCourse = safeId(courseId);
        String safeLesson = safeId(lessonId);
        if (safeCourse.isEmpty() || safeLesson.isEmpty()) return;
        SQLiteDatabase db = null;
        boolean transactionStarted = false;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            transactionStarted = true;
            ContentValues values = loadValues(db, safeCourse, safeLesson);
            if (mutator != null) mutator.apply(values);
            values.put("updated_at", System.currentTimeMillis());
            long row = db.insertWithOnConflict(TABLE, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
            if (row == -1L) return;
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
            // Progress can be retried later; never crash an exercise completion screen.
        } finally {
            if (transactionStarted && db != null) {
                try { db.endTransaction(); } catch (Throwable ignored) { }
            }
        }
    }

    private ContentValues loadValues(SQLiteDatabase db, String courseId, String lessonId) {
        ContentValues values = defaults(courseId, lessonId);
        try (Cursor cursor = db.query(TABLE,
                new String[]{"best_score", "stars", "attempts", "last_score", "last_passed",
                        "completed_at", "last_opened_at", "updated_at"},
                "course_id=? AND lesson_id=?", new String[]{courseId, lessonId},
                null, null, null, "1")) {
            if (!cursor.moveToFirst()) return values;
            values.put("best_score", cursor.getInt(0));
            values.put("stars", cursor.getInt(1));
            values.put("attempts", cursor.getInt(2));
            values.put("last_score", cursor.getInt(3));
            values.put("last_passed", cursor.getInt(4));
            values.put("completed_at", cursor.getLong(5));
            values.put("last_opened_at", cursor.getLong(6));
            values.put("updated_at", cursor.getLong(7));
            return values;
        }
    }

    private static ContentValues defaults(String courseId, String lessonId) {
        ContentValues values = new ContentValues();
        values.put("course_id", courseId);
        values.put("lesson_id", lessonId);
        values.put("best_score", 0);
        values.put("stars", 0);
        values.put("attempts", 0);
        values.put("last_score", 0);
        values.put("last_passed", 0);
        values.put("completed_at", 0L);
        values.put("last_opened_at", 0L);
        values.put("updated_at", 0L);
        return values;
    }

    private static void addColumn(SQLiteDatabase db, String name, String definition) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + name + " " + definition); }
        catch (Throwable ignored) { }
    }

    private static int intValue(ContentValues values, String key) {
        Integer value = values.getAsInteger(key);
        return value == null ? 0 : value;
    }

    private static long longValue(ContentValues values, String key) {
        Long value = values.getAsLong(key);
        return value == null ? 0L : value;
    }

    private static String safeId(String value) {
        String id = value == null ? "" : value.trim();
        if (id.length() < 1 || id.length() > 96
                || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*") || id.endsWith(".")) return "";
        return id;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface Mutator { void apply(ContentValues values); }

    static final class Progress {
        String lessonId = "";
        int bestScore;
        int stars;
        int attempts;
        int lastScore;
        boolean lastPassed;
        long completedAt;
        long lastOpenedAt;

        boolean completed() { return completedAt > 0L; }
    }
}
