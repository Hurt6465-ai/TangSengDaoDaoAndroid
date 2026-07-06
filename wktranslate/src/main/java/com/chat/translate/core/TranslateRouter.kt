package com.chat.translate.core

import android.content.Context
import com.chat.translate.prefs.TranslatePrefs

object TranslateRouter {
    fun resolveProvider(context: Context, scene: TranslateScene): TranslateProvider {
        val hasAi = TranslatePrefs.hasUsableAi(context)

        if (scene == TranslateScene.BEFORE_SEND) {
            return if (hasAi) TranslateProvider.AI else TranslateProvider.NEED_AI_CONFIG
        }

        return when (TranslatePrefs.getMode(context)) {
            TranslateMode.AI -> if (hasAi) TranslateProvider.AI else TranslateProvider.NEED_AI_CONFIG
            TranslateMode.MACHINE -> TranslateProvider.MACHINE
            TranslateMode.AUTO -> if (hasAi) TranslateProvider.AI else TranslateProvider.NEED_AI_CONFIG
        }
    }
}
