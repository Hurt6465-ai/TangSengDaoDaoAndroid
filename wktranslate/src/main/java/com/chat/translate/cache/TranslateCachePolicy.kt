package com.chat.translate.cache

import com.chat.translate.lang.TranslateLanguageMapper
import com.chat.translate.util.TranslateTextNormalizer

object TranslateCachePolicy {
    const val MAX_CACHE_COUNT = 50_000
    const val CLEAN_TRIGGER_COUNT = 55_000
    const val CLEAN_INTERVAL_MS = 24L * 60L * 60L * 1000L

    fun isRoomCacheable(text: String): Boolean {
        val normalized = TranslateTextNormalizer.normalizeForCache(text)
        if (normalized.isBlank()) return false
        if (containsPrivatePattern(normalized)) return false
        val compact = TranslateTextNormalizer.compactLengthText(normalized)
        if (compact.isBlank()) return false
        if (TranslateLanguageMapper.isLikelyCjk(compact)) return compact.length <= 15
        if (TranslateLanguageMapper.isLikelySouthEastAsianScript(compact)) return compact.length <= 80
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size in 1..12 && normalized.length <= 80) return true
        return normalized.length <= 80
    }

    fun isMemoryCacheable(text: String): Boolean {
        val normalized = TranslateTextNormalizer.normalizeForCache(text)
        return normalized.isNotBlank() && normalized.length <= 2000
    }

    private fun containsPrivatePattern(text: String): Boolean {
        val normalized = text.replace(" ", "")
        val hasLongDigits = Regex("\\d{8,}").containsMatchIn(normalized)
        val hasEmail = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").containsMatchIn(text)
        val hasBankLike = Regex("(?:\\d[ -]?){13,19}").containsMatchIn(text)
        return hasLongDigits || hasEmail || hasBankLike
    }
}
