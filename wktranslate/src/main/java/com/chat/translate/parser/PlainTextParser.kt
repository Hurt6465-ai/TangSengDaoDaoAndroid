package com.chat.translate.parser

object PlainTextParser : TranslationParser {
    override fun parse(body: String): String = body.trim()
}
