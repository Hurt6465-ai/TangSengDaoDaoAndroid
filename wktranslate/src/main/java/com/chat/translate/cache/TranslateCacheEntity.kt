package com.chat.translate.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translation_cache",
    indices = [
        Index(value = ["cacheKey"], unique = true),
        Index(value = ["hitCount", "lastAccessAt"])
    ]
)
data class TranslateCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cacheKey: String,
    val provider: String,
    val engine: String,
    val promptVersion: String,
    val sourceLang: String,
    val targetLang: String,
    val textHash: String,
    val originalText: String,
    val normalizedText: String,
    val translatedText: String,
    val textLength: Int,
    val hitCount: Int = 0,
    val createdAt: Long,
    val lastAccessAt: Long
)
