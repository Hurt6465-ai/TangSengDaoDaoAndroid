package com.chat.translate.parser

import org.json.JSONArray

object GoogleArrayParser : TranslationParser {
    override fun parse(body: String): String {
        val root = JSONArray(body)
        val segments = root.optJSONArray(0) ?: return ""
        val builder = StringBuilder()
        for (i in 0 until segments.length()) {
            val segment = segments.optJSONArray(i) ?: continue
            builder.append(segment.optString(0, ""))
        }
        return builder.toString()
    }
}
