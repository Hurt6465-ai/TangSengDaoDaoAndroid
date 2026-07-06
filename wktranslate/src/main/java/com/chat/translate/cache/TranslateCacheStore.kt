package com.chat.translate.cache

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Lightweight local cache store. This replaces Room to avoid kapt/annotation-processing failures.
 * The table stays compatible with the first-stage Room schema name and columns.
 */
class TranslateCacheStore private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cacheKey TEXT NOT NULL UNIQUE,
                provider TEXT NOT NULL,
                engine TEXT NOT NULL,
                promptVersion TEXT NOT NULL,
                sourceLang TEXT NOT NULL,
                targetLang TEXT NOT NULL,
                textHash TEXT NOT NULL,
                originalText TEXT NOT NULL,
                normalizedText TEXT NOT NULL,
                translatedText TEXT NOT NULL,
                textLength INTEGER NOT NULL,
                hitCount INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                lastAccessAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_translation_cache_key ON $TABLE_NAME(cacheKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_translation_cache_cold ON $TABLE_NAME(hitCount ASC, lastAccessAt ASC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Reserved for future migrations. Keep existing cache rows whenever possible.
        onCreate(db)
    }

    @Synchronized
    fun findByKey(cacheKey: String): TranslateCacheEntity? {
        readableDatabase.query(
            TABLE_NAME,
            null,
            "cacheKey = ?",
            arrayOf(cacheKey),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toEntity() else null
        }
    }

    @Synchronized
    fun insertOrReplace(entity: TranslateCacheEntity) {
        val db = writableDatabase
        db.insertWithOnConflict(TABLE_NAME, null, entity.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun markHit(cacheKey: String, now: Long) {
        writableDatabase.execSQL(
            "UPDATE $TABLE_NAME SET hitCount = hitCount + 1, lastAccessAt = ? WHERE cacheKey = ?",
            arrayOf(now, cacheKey)
        )
    }

    @Synchronized
    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    @Synchronized
    fun clearAll() {
        writableDatabase.delete(TABLE_NAME, null, null)
    }

    @Synchronized
    fun deleteColdest(deleteCount: Int) {
        if (deleteCount <= 0) return
        writableDatabase.execSQL(
            """
            DELETE FROM $TABLE_NAME
            WHERE id IN (
                SELECT id FROM $TABLE_NAME
                ORDER BY hitCount ASC, lastAccessAt ASC
                LIMIT $deleteCount
            )
            """.trimIndent()
        )
    }

    private fun TranslateCacheEntity.toValues(): ContentValues {
        return ContentValues().apply {
            if (id > 0) put("id", id)
            put("cacheKey", cacheKey)
            put("provider", provider)
            put("engine", engine)
            put("promptVersion", promptVersion)
            put("sourceLang", sourceLang)
            put("targetLang", targetLang)
            put("textHash", textHash)
            put("originalText", originalText)
            put("normalizedText", normalizedText)
            put("translatedText", translatedText)
            put("textLength", textLength)
            put("hitCount", hitCount)
            put("createdAt", createdAt)
            put("lastAccessAt", lastAccessAt)
        }
    }

    private fun Cursor.toEntity(): TranslateCacheEntity {
        return TranslateCacheEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            cacheKey = getString(getColumnIndexOrThrow("cacheKey")),
            provider = getString(getColumnIndexOrThrow("provider")),
            engine = getString(getColumnIndexOrThrow("engine")),
            promptVersion = getString(getColumnIndexOrThrow("promptVersion")),
            sourceLang = getString(getColumnIndexOrThrow("sourceLang")),
            targetLang = getString(getColumnIndexOrThrow("targetLang")),
            textHash = getString(getColumnIndexOrThrow("textHash")),
            originalText = getString(getColumnIndexOrThrow("originalText")),
            normalizedText = getString(getColumnIndexOrThrow("normalizedText")),
            translatedText = getString(getColumnIndexOrThrow("translatedText")),
            textLength = getInt(getColumnIndexOrThrow("textLength")),
            hitCount = getInt(getColumnIndexOrThrow("hitCount")),
            createdAt = getLong(getColumnIndexOrThrow("createdAt")),
            lastAccessAt = getLong(getColumnIndexOrThrow("lastAccessAt"))
        )
    }

    companion object {
        private const val DB_NAME = "wktranslate_cache.db"
        private const val DB_VERSION = 1
        private const val TABLE_NAME = "translation_cache"

        @Volatile
        private var INSTANCE: TranslateCacheStore? = null

        fun get(context: Context): TranslateCacheStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TranslateCacheStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
