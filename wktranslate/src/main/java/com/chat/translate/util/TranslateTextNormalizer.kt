package com.chat.translate.util

import java.text.Normalizer

object TranslateTextNormalizer {
    fun normalizeForCache(text: String): String {
        if (text.isBlank()) return ""
        val nfkc = try {
            Normalizer.normalize(text, Normalizer.Form.NFKC)
        } catch (_: Throwable) {
            text
        }
        return nfkc
            .replace('\u3000', ' ')
            .replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n+"), "\n")
            .replace(Regex("[ ]{2,}"), " ")
            .trim()
    }

    fun compactLengthText(text: String): String {
        return text.replace(Regex("\\s+"), "")
    }
}
