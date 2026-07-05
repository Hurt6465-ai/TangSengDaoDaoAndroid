package com.chat.translate.core

data class ProviderTranslateResult(
    val text: String,
    val provider: TranslateProvider,
    val engine: String
)
