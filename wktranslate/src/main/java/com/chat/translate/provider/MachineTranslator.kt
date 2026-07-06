package com.chat.translate.provider

import android.content.Context
import com.chat.translate.core.ProviderTranslateResult
import com.chat.translate.core.TranslateErrorCode
import com.chat.translate.core.TranslateProvider
import com.chat.translate.core.TranslateResult
import com.chat.translate.lang.TranslateLanguageMapper
import com.chat.translate.parser.ParserFactory
import com.chat.translate.prefs.TranslatePrefs
import com.chat.translate.util.TranslateResultValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object MachineTranslator {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun translate(context: Context, text: String, sourceLang: String, targetLang: String): Result<ProviderTranslateResult> = withContext(Dispatchers.IO) {
        val prefs = TranslatePrefs.getMachineConfig(context)
        val chain = buildFallbackChain(prefs)
        val errors = mutableListOf<String>()
        for (candidate in chain) {
            val result = runCatching { requestMachine(candidate, text, sourceLang, targetLang) }
            if (result.isSuccess) return@withContext Result.success(result.getOrThrow())
            errors.add(result.exceptionOrNull()?.message ?: candidate.engine)
        }
        Result.failure(TranslateException(TranslateErrorCode.NETWORK_ERROR, errors.joinToString("; ").ifBlank { "Machine translation failed" }))
    }

    suspend fun test(context: Context, text: String, targetLang: String): TranslateResult {
        return translate(context, text, "auto", targetLang).fold(
            onSuccess = { TranslateResult.success(it.text, it.provider, it.engine) },
            onFailure = { err ->
                if (err is TranslateException) TranslateResult.failure(err.code, err.message ?: "")
                else TranslateResult.failure(TranslateErrorCode.UNKNOWN, err.message ?: "")
            }
        )
    }

    private data class Candidate(val engine: String, val url: String, val parser: String)

    private fun buildFallbackChain(prefs: TranslatePrefs.MachineConfig): List<Candidate> {
        val selected = Candidate(prefs.engine, prefs.url, prefs.parser)
        val fallback = listOf(
            Candidate(TranslatePrefs.ENGINE_GOOGLE, TranslatePrefs.DEFAULT_GOOGLE_URL, TranslatePrefs.PARSER_GOOGLE),
            Candidate(TranslatePrefs.ENGINE_DEEPLX, TranslatePrefs.DEFAULT_DEEPLX_URL, TranslatePrefs.PARSER_DEEPLX)
        )
        return (listOf(selected) + fallback).distinctBy { it.engine + "|" + it.url + "|" + it.parser }
    }

    private fun requestMachine(candidate: Candidate, text: String, sourceLang: String, targetLang: String): ProviderTranslateResult {
        val sourceCode = TranslateLanguageMapper.toMachineCode(sourceLang)
        val targetCode = TranslateLanguageMapper.toMachineCode(targetLang)
        val request = if (candidate.engine == TranslatePrefs.ENGINE_DEEPLX && !candidate.url.contains("{q}")) {
            val bodyJson = JSONObject()
                .put("text", text)
                .put("source_lang", sourceCode.uppercase(Locale.US))
                .put("target_lang", targetCode.uppercase(Locale.US))
            Request.Builder()
                .url(candidate.url)
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 TangSengDaoDao-Translate/1.0")
                .build()
        } else {
            val url = fillTemplate(candidate.url, sourceCode, targetCode, text)
            Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 TangSengDaoDao-Translate/1.0")
                .build()
        }
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw TranslateException(TranslateErrorCode.HTTP_ERROR, "HTTP ${response.code}")
            if (TranslateResultValidator.looksLikeHtml(body) || TranslateResultValidator.looksLikeErrorJson(body)) {
                throw TranslateException(TranslateErrorCode.HTTP_ERROR, "Invalid machine response")
            }
            val parsed = ParserFactory.forType(candidate.parser).parse(body)
            val cleaned = TranslateResultValidator.clean(parsed)
            if (cleaned.isBlank()) throw TranslateException(TranslateErrorCode.EMPTY_RESULT, "Empty machine translation")
            if (!TranslateResultValidator.isValidForCache(text, cleaned)) {
                throw TranslateException(TranslateErrorCode.UNSAFE_RESULT, "Unsafe machine translation")
            }
            return ProviderTranslateResult(cleaned, TranslateProvider.MACHINE, candidate.engine)
        }
    }

    private fun fillTemplate(url: String, sourceLang: String, targetLang: String, text: String): String {
        val q = URLEncoder.encode(text, "UTF-8")
        return url
            .replace("{sl}", URLEncoder.encode(sourceLang, "UTF-8"))
            .replace("{tl}", URLEncoder.encode(targetLang, "UTF-8"))
            .replace("{q}", q)
    }
}
