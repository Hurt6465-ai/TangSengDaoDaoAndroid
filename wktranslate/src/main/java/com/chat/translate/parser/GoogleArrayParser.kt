package com.chat.translate.parser

import org.json.JSONArray
import org.json.JSONObject

object GoogleArrayParser : TranslationParser {
    override fun parse(body: String): String {
        val raw = body.trim()
        if (raw.isBlank()) return ""

        // Normal Google unofficial endpoint:
        // [[ ["translated", "source", ...], ... ], ...]
        if (raw.startsWith("[")) {
            val root = JSONArray(raw)
            val segments = root.optJSONArray(0) ?: return ""
            val builder = StringBuilder()
            for (i in 0 until segments.length()) {
                val segment = segments.optJSONArray(i) ?: continue
                val text = segment.optString(0, "")
                if (text.isNotEmpty()) builder.append(text)
            }
            return builder.toString().trim()
        }

        // Some mirrors/customized Google URLs may use dj=1 and return:
        // {"sentences":[{"trans":"translated"}]}
        if (raw.startsWith("{")) {
            val json = JSONObject(raw)
            val sentences = json.optJSONArray("sentences") ?: return ""
            val builder = StringBuilder()
            for (i in 0 until sentences.length()) {
                val item = sentences.optJSONObject(i) ?: continue
                val text = item.optString("trans", "")
                if (text.isNotEmpty()) builder.append(text)
            }
            return builder.toString().trim()
        }

        return ""
    }
}
