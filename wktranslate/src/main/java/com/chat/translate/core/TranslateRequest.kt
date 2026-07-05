package com.chat.translate.core

import android.content.Context

data class TranslateRequest(
    val context: Context,
    val text: String,
    val sourceLang: String = "auto",
    val targetLang: String,
    val scene: TranslateScene,
    val bypassCache: Boolean = false
)
