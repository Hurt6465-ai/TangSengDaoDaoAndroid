package com.chat.translate.ai

import com.chat.translate.prefs.TranslatePrefs
import okhttp3.RequestBody

interface AiRequestAdapter {
    fun buildRequestBody(config: TranslatePrefs.AiConfig, targetLangName: String, text: String): RequestBody
}
