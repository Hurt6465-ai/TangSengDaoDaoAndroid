package com.chat.translate.util

import org.json.JSONObject
import java.util.Locale

object TranslateResultValidator {
    private val exactErrorPhrases = listOf(
        "api rate limit exceeded",
        "too many requests",
        "unauthorized",
        "forbidden",
        "service unavailable",
        "bad gateway",
        "gateway timeout",
        "rate limit exceeded",
        "quota exceeded"
    )

    fun clean(text: String): String {
        var result = text.trim()
            .trim('"', '\'', '`')
            .trim()
        val prefixes = listOf("译文：", "翻译：", "Translation:", "translation:")
        prefixes.firstOrNull { result.startsWith(it) }?.let { result = result.removePrefix(it).trim() }
        return result
    }

    fun isValidForCache(originalText: String, translatedText: String): Boolean {
        val value = translatedText.trim()
        if (value.isBlank()) return false
        val lower = value.lowercase(Locale.US)
        if (looksLikeHtml(lower)) return false
        if (looksLikeErrorJson(value)) return false
        if (exactErrorPhrases.any { lower == it || lower.startsWith("$it:") }) return false
        if (lower.startsWith("sorry, i can't") || lower.startsWith("i am sorry") || lower.startsWith("抱歉") || lower.startsWith("以下是翻译")) return false
        val originalSize = originalText.trim().length.coerceAtLeast(1)
        if (value.length > 200 && value.length > originalSize * 5) return false
        return true
    }

    fun looksLikeHtml(text: String): Boolean {
        val lower = text.lowercase(Locale.US).trimStart()
        return lower.startsWith("<!doctype html") || lower.startsWith("<html") || lower.contains("<body") || lower.contains("</html>")
    }

    fun looksLikeErrorJson(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val json = JSONObject(trimmed)
            json.has("error") || json.has("errors") || (json.has("code") && json.has("message"))
        } catch (_: Throwable) {
            false
        }
    }
}
