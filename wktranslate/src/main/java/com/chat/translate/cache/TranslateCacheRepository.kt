package com.chat.translate.cache

import android.content.Context
import com.chat.translate.core.TranslateProvider
import com.chat.translate.core.TranslateResult
import com.chat.translate.prefs.TranslatePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TranslateCacheRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun find(context: Context, cacheKey: String): TranslateCacheEntity? {
        maybeClean(context)
        return TranslateCacheStore.get(context).findByKey(cacheKey)
    }

    fun markHitAsync(context: Context, cacheKey: String) {
        scope.launch {
            runCatching {
                TranslateCacheStore.get(context).markHit(cacheKey, System.currentTimeMillis())
            }
        }
    }

    suspend fun save(
        context: Context,
        cacheKey: String,
        provider: TranslateProvider,
        engine: String,
        promptVersion: String,
        sourceLang: String,
        targetLang: String,
        textHash: String,
        originalText: String,
        normalizedText: String,
        translatedText: String
    ) {
        if (!TranslateCachePolicy.isRoomCacheable(normalizedText)) return
        val now = System.currentTimeMillis()
        val old = TranslateCacheStore.get(context).findByKey(cacheKey)
        val hitCount = old?.hitCount ?: 1
        TranslateCacheStore.get(context).insertOrReplace(
            TranslateCacheEntity(
                id = old?.id ?: 0,
                cacheKey = cacheKey,
                provider = provider.name.lowercase(),
                engine = engine,
                promptVersion = promptVersion,
                sourceLang = sourceLang,
                targetLang = targetLang,
                textHash = textHash,
                originalText = originalText,
                normalizedText = normalizedText,
                translatedText = translatedText,
                textLength = normalizedText.length,
                hitCount = hitCount,
                createdAt = old?.createdAt ?: now,
                lastAccessAt = now
            )
        )
    }

    suspend fun clear(context: Context) {
        TranslateCacheStore.get(context).clearAll()
        TranslateMemoryCache.clear()
    }

    suspend fun maybeClean(context: Context) {
        val now = System.currentTimeMillis()
        val last = TranslatePrefs.getLastCleanTime(context)
        if (now - last < TranslateCachePolicy.CLEAN_INTERVAL_MS) return
        val store = TranslateCacheStore.get(context)
        val count = store.count()
        if (count > TranslateCachePolicy.CLEAN_TRIGGER_COUNT) {
            store.deleteColdest(count - TranslateCachePolicy.MAX_CACHE_COUNT)
        }
        TranslatePrefs.setLastCleanTime(context, now)
    }

    fun toResult(entity: TranslateCacheEntity): TranslateResult {
        val provider = if (entity.provider == "ai") TranslateProvider.AI else TranslateProvider.MACHINE
        return TranslateResult.success(entity.translatedText, provider, entity.engine, fromCache = true)
    }
}
