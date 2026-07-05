package com.chat.translate.parser

import com.chat.translate.prefs.TranslatePrefs

object ParserFactory {
    fun forType(type: String): TranslationParser {
        return when (type) {
            TranslatePrefs.PARSER_GOOGLE -> GoogleArrayParser
            TranslatePrefs.PARSER_PLAIN -> PlainTextParser
            else -> DeeplxJsonParser
        }
    }
}
