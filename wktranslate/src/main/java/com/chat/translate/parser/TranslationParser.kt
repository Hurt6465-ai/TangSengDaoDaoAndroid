package com.chat.translate.parser

interface TranslationParser {
    fun parse(body: String): String
}
