package com.chat.translate.provider

import android.content.Context
import com.chat.translate.ai.AiAdapterFactory
import com.chat.translate.core.ProviderTranslateResult
import com.chat.translate.core.TranslateErrorCode
import com.chat.translate.core.TranslateProvider
import com.chat.translate.core.TranslateResult
import com.chat.translate.lang.TranslateLanguageMapper
import com.chat.translate.prefs.TranslatePrefs
import com.chat.translate.util.TranslateResultValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiTranslator {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun translate(context: Context, text: String, targetLang: String): Result<ProviderTranslateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val config = TranslatePrefs.getAiConfig(context)
            if (config.endpoint.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
                throw TranslateException(TranslateErrorCode.INVALID_CONFIG, "AI config is incomplete")
            }
            val body = AiAdapterFactory.get(config.adapter).buildRequestBody(
                config = config,
                targetLangName = TranslateLanguageMapper.toAiName(targetLang),
                text = text
            )
            val request = Request.Builder()
                .url(normalizeEndpoint(config.endpoint))
                .post(body)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (!response.isSuccessful) {
                    throw TranslateException(TranslateErrorCode.HTTP_ERROR, "HTTP ${response.code}")
                }
                if (TranslateResultValidator.looksLikeHtml(responseBody) || TranslateResultValidator.looksLikeErrorJson(responseBody)) {
                    throw TranslateException(TranslateErrorCode.HTTP_ERROR, "Invalid AI response")
                }
                val parsed = parseOpenAiCompatible(responseBody)
                val cleaned = TranslateResultValidator.clean(parsed)
                if (cleaned.isBlank()) throw TranslateException(TranslateErrorCode.EMPTY_RESULT, "Empty AI translation")
                if (!TranslateResultValidator.isValidForCache(text, cleaned)) {
                    throw TranslateException(TranslateErrorCode.UNSAFE_RESULT, "Unsafe AI translation")
                }
                ProviderTranslateResult(cleaned, TranslateProvider.AI, config.adapter.ifBlank { "ai" })
            }
        }
    }

    suspend fun test(context: Context, text: String, targetLang: String): TranslateResult {
        return translate(context, text, targetLang).fold(
            onSuccess = {
                TranslatePrefs.setAiVerified(context, true)
                TranslateResult.success(it.text, it.provider, it.engine)
            },
            onFailure = { err ->
                TranslatePrefs.setAiVerified(context, false)
                if (err is TranslateException) TranslateResult.failure(err.code, err.message ?: "")
                else TranslateResult.failure(TranslateErrorCode.UNKNOWN, err.message ?: "")
            }
        )
    }

    private fun normalizeEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim()
        if (trimmed.endsWith("/chat/completions")) return trimmed
        return trimmed.trimEnd('/') + "/chat/completions"
    }

    private fun parseOpenAiCompatible(body: String): String {
        val json = JSONObject(body)
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val first = choices.optJSONObject(0) ?: return ""
        val message = first.optJSONObject("message")
        val content = message?.optString("content", "") ?: first.optString("text", "")
        return content
    }
}

class TranslateException(val code: TranslateErrorCode, override val message: String) : RuntimeException(message)
