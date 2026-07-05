package com.chat.translate.core

import android.content.Context
import com.chat.translate.prefs.TranslatePrefs
import java.util.Locale

object TranslateRouter {
    fun resolveProvider(context: Context, scene: TranslateScene): TranslateProvider {
        val hasAi = TranslatePrefs.hasUsableAi(context)
        val country = TranslatePrefs.getCurrentCountryCode(context).trim().uppercase(Locale.US)
        val isChinaUser = country == "CN" || country == "CHN" || country == "中国" || country == "CHINA"

        if (scene == TranslateScene.BEFORE_SEND) {
            return if (hasAi) TranslateProvider.AI else TranslateProvider.NEED_AI_CONFIG
        }

        return when (TranslatePrefs.getMode(context)) {
            TranslateMode.AI -> if (hasAi) TranslateProvider.AI else TranslateProvider.NEED_AI_CONFIG
            TranslateMode.MACHINE -> TranslateProvider.MACHINE
            TranslateMode.AUTO -> when {
                hasAi -> TranslateProvider.AI
                isChinaUser -> TranslateProvider.NEED_AI_CONFIG
                else -> TranslateProvider.MACHINE
            }
        }
    }
}
