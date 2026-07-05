package com.chat.translate.api

import android.content.Context
import com.chat.translate.core.TranslateResult
import com.chat.translate.core.TranslateScene

data class ChatTranslateRequest(
    val context: Context,
    val text: String,
    val sourceLang: String = "auto",
    val targetLang: String,
    val scene: TranslateScene,
    val bypassCache: Boolean = false
)

data class ChatBeforeSendRequest(
    val context: Context,
    val text: String,
    val sourceLang: String = "auto",
    val targetLang: String
)

interface ChatTranslateBridge {
    suspend fun translate(request: ChatTranslateRequest): TranslateResult
    suspend fun translateBeforeSend(request: ChatBeforeSendRequest): TranslateResult
    fun openSettings(context: Context, from: String = "chat")
}
