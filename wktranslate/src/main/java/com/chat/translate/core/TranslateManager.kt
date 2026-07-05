package com.chat.translate.core

import android.content.Context
import com.chat.translate.cache.TranslateCachePolicy
import com.chat.translate.cache.TranslateCacheRepository
import com.chat.translate.cache.TranslateMemoryCache
import com.chat.translate.lang.TranslateLanguageMapper
import com.chat.translate.prefs.TranslatePrefs
import com.chat.translate.provider.AiTranslator
import com.chat.translate.provider.MachineTranslator
import com.chat.translate.prompt.TranslatePrompt
import com.chat.translate.util.HashUtil
import com.chat.translate.util.TranslateResultValidator
import com.chat.translate.util.TranslateTextNormalizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object TranslateManager {
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<TranslateResult>>()

    suspend fun translate(
        context: Context,
        scene: TranslateScene,
        text: String,
        sourceLang: String = "auto",
        targetLang: String,
        bypassCache: Boolean = false
    ): TranslateResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val normalized = TranslateTextNormalizer.normalizeForCache(text)
        if (normalized.isBlank()) return@withContext TranslateResult.failure(TranslateErrorCode.EMPTY_RESULT, "Empty text")

        val provider = TranslateRouter.resolveProvider(appContext, scene)
        if (provider == TranslateProvider.NEED_AI_CONFIG) {
            return@withContext TranslateResult.failure(TranslateErrorCode.NEED_AI_CONFIG, "Need AI config")
        }
        if (provider == TranslateProvider.UNSUPPORTED) {
            return@withContext TranslateResult.failure(TranslateErrorCode.UNSUPPORTED, "Unsupported translation")
        }

        val engine = resolveEngine(appContext, provider)
        val promptVersion = TranslatePrompt.VERSION
        val normalizedSourceLang = normalizeSourceLang(provider, sourceLang)
        val normalizedTargetLang = TranslateLanguageMapper.toMachineCode(targetLang)
        val textHash = HashUtil.sha256(normalized)
        val cacheKey = buildCacheKey(provider, engine, promptVersion, normalizedSourceLang, normalizedTargetLang, textHash)
        val memoryKey = "mem:$cacheKey"

        if (!bypassCache) {
            if (TranslateCachePolicy.isRoomCacheable(normalized)) {
                val entity = TranslateCacheRepository.find(appContext, cacheKey)
                if (entity != null) {
                    TranslateCacheRepository.markHitAsync(appContext, cacheKey)
                    return@withContext TranslateCacheRepository.toResult(entity)
                }
            } else if (TranslateCachePolicy.isMemoryCacheable(normalized)) {
                TranslateMemoryCache.get(memoryKey)?.let { return@withContext it.copy(fromCache = true) }
            }
        }

        val flightKey = if (bypassCache) "refresh:$cacheKey" else cacheKey
        val mine = CompletableDeferred<TranslateResult>()
        val existing = inFlight.putIfAbsent(flightKey, mine)
        if (existing != null) return@withContext existing.await()

        try {
            val providerResult = when (provider) {
                TranslateProvider.AI -> AiTranslator.translate(appContext, normalized, targetLang).getOrThrow()
                TranslateProvider.MACHINE -> MachineTranslator.translate(appContext, normalized, normalizedSourceLang, targetLang).getOrThrow()
                else -> throw IllegalStateException("Unsupported provider $provider")
            }
            val cleaned = TranslateResultValidator.clean(providerResult.text)
            if (!TranslateResultValidator.isValidForCache(normalized, cleaned)) {
                val failure = TranslateResult.failure(TranslateErrorCode.UNSAFE_RESULT, "Unsafe translation result")
                mine.complete(failure)
                return@withContext failure
            }
            val result = TranslateResult.success(cleaned, providerResult.provider, providerResult.engine)
            if (TranslateCachePolicy.isRoomCacheable(normalized)) {
                TranslateCacheRepository.save(
                    context = appContext,
                    cacheKey = cacheKey,
                    provider = providerResult.provider,
                    engine = providerResult.engine,
                    promptVersion = promptVersion,
                    sourceLang = normalizedSourceLang,
                    targetLang = normalizedTargetLang,
                    textHash = textHash,
                    originalText = text,
                    normalizedText = normalized,
                    translatedText = cleaned
                )
            } else if (TranslateCachePolicy.isMemoryCacheable(normalized)) {
                TranslateMemoryCache.put(memoryKey, result)
            }
            mine.complete(result)
            return@withContext result
        } catch (t: Throwable) {
            val failure = TranslateResult.failure(
                if (t is com.chat.translate.provider.TranslateException) t.code else TranslateErrorCode.UNKNOWN,
                t.message ?: "Translation failed"
            )
            mine.complete(failure)
            return@withContext failure
        } finally {
            inFlight.remove(flightKey)
        }
    }

    suspend fun translateBeforeSend(
        context: Context,
        text: String,
        sourceLang: String = "auto",
        targetLang: String
    ): TranslateResult {
        return translate(
            context = context,
            scene = TranslateScene.BEFORE_SEND,
            text = text,
            sourceLang = sourceLang,
            targetLang = targetLang,
            bypassCache = false
        )
    }

    suspend fun testAi(context: Context, text: String, targetLang: String): TranslateResult {
        return AiTranslator.test(context.applicationContext, text, targetLang)
    }

    suspend fun testMachine(context: Context, text: String, targetLang: String): TranslateResult {
        return MachineTranslator.test(context.applicationContext, text, targetLang)
    }

    suspend fun clearCache(context: Context) {
        TranslateCacheRepository.clear(context.applicationContext)
    }

    fun buildCacheKey(provider: TranslateProvider, engine: String, promptVersion: String, sourceLang: String, targetLang: String, textHash: String): String {
        val providerRaw = when (provider) {
            TranslateProvider.AI -> "ai"
            TranslateProvider.MACHINE -> "machine"
            else -> provider.name.lowercase()
        }
        return "$providerRaw:$engine:$promptVersion:$sourceLang:$targetLang:$textHash"
    }

    private fun resolveEngine(context: Context, provider: TranslateProvider): String {
        return when (provider) {
            TranslateProvider.AI -> TranslatePrefs.getAiConfig(context).adapter.ifBlank { "ai" }
            TranslateProvider.MACHINE -> TranslatePrefs.getMachineConfig(context).engine.ifBlank { "machine" }
            else -> "unknown"
        }
    }

    private fun normalizeSourceLang(provider: TranslateProvider, sourceLang: String): String {
        return if (provider == TranslateProvider.AI) "auto" else TranslateLanguageMapper.toMachineCode(sourceLang)
    }
}
