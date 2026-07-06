package com.chat.translate.cache

/**
 * Plain SQLite cache row. Kept intentionally annotation-free so wktranslate does not need Room/KAPT.
 */
data class TranslateCacheEntity(
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
