package com.chat.translate.parser

import org.json.JSONObject

object DeeplxJsonParser : TranslationParser {
    override fun parse(body: String): String {
        val json = JSONObject(body)
        if (json.has("error")) return ""
        val directKeys = listOf("data", "translation", "translatedText", "result", "text")
        for (key in directKeys) {
            val value = json.optString(key, "")
            if (value.isNotBlank()) return value
        }
        val translations = json.optJSONArray("translations")
        if (translations != null && translations.length() > 0) {
            val first = translations.optJSONObject(0)
            val text = first?.optString("text", "") ?: ""
            if (text.isNotBlank()) return text
        }
        return ""
    }
}
