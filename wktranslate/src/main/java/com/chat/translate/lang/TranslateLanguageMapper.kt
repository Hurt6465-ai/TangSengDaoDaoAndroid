package com.chat.translate.lang

import java.util.Locale

object TranslateLanguageMapper {
    fun toMachineCode(language: String): String {
        val value = language.trim()
        if (value.isBlank()) return "auto"
        val lower = value.lowercase(Locale.US)
        return when (lower) {
            "auto", "自动", "自動" -> "auto"
            "中文", "汉语", "漢語", "တရုတ်", "တရုတ်စာ", "chinese", "zh", "zh-cn", "cn" -> "zh-CN"
            "英文", "英语", "အင်္ဂလိပ်", "အင်္ဂလိပ်စာ", "english", "en" -> "en"
            "缅语", "缅甸语", "ဗမာ", "ဗမာစာ", "မြန်မာ", "မြန်မာစာ", "myanmar", "burmese", "my" -> "my"
            "日语", "日本語", "japanese", "ja", "jp" -> "ja"
            "韩语", "한국어", "korean", "ko", "kr" -> "ko"
            "泰语", "ไทย", "thai", "th" -> "th"
            "越南语", "tiếng việt", "vietnamese", "vi", "vn" -> "vi"
            "印尼语", "indonesian", "id" -> "id"
            "老挝语", "lao", "lo" -> "lo"
            "高棉语", "柬埔寨语", "khmer", "km" -> "km"
            "法语", "french", "fr" -> "fr"
            "德语", "german", "de" -> "de"
            "西班牙语", "spanish", "es" -> "es"
            else -> value
        }
    }

    fun toAiName(language: String): String {
        val code = toMachineCode(language)
        return when (code.lowercase(Locale.US)) {
            "zh-cn", "zh" -> "中文"
            "en" -> "英文"
            "my" -> "缅语"
            "ja" -> "日语"
            "ko" -> "韩语"
            "th" -> "泰语"
            "vi" -> "越南语"
            "id" -> "印尼语"
            "lo" -> "老挝语"
            "km" -> "高棉语"
            "fr" -> "法语"
            "de" -> "德语"
            "es" -> "西班牙语"
            else -> language.ifBlank { "目标语言" }
        }
    }

    fun isLikelyCjk(text: String): Boolean = text.any { ch ->
        ch.code in 0x4E00..0x9FFF || ch.code in 0x3040..0x30FF || ch.code in 0xAC00..0xD7AF
    }

    fun isLikelySouthEastAsianScript(text: String): Boolean = text.any { ch ->
        ch.code in 0x1000..0x109F || // Myanmar
            ch.code in 0x0E00..0x0E7F || // Thai
            ch.code in 0x0E80..0x0EFF || // Lao
            ch.code in 0x1780..0x17FF // Khmer
    }
}
