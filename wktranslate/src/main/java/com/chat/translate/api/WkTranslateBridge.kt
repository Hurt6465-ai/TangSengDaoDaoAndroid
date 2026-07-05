package com.chat.translate.api

import android.content.Context
import com.chat.translate.core.TranslateManager
import com.chat.translate.core.TranslateResult
import com.chat.translate.ui.TranslateSettingsActivity

class WkTranslateBridge : ChatTranslateBridge {
    override suspend fun translate(request: ChatTranslateRequest): TranslateResult {
        return TranslateManager.translate(
            context = request.context,
            scene = request.scene,
            text = request.text,
            sourceLang = request.sourceLang,
            targetLang = request.targetLang,
            bypassCache = request.bypassCache
        )
    }

    override suspend fun translateBeforeSend(request: ChatBeforeSendRequest): TranslateResult {
        return TranslateManager.translateBeforeSend(
            context = request.context,
            text = request.text,
            sourceLang = request.sourceLang,
            targetLang = request.targetLang
        )
    }

    override fun openSettings(context: Context, from: String) {
        TranslateSettingsActivity.start(context, from)
    }
}
