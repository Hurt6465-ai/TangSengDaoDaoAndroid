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
            TranslateMode.AUTO -> {
                // First release behavior: AUTO prefers verified AI. If AI is not verified,
                // open the translate settings instead of silently falling back to an unstable
                // free machine endpoint. Users who want free machine translation can explicitly
                // switch the mode to MACHINE in TranslateSettingsActivity.
                if (hasAi) TranslateProvider.AI else TranslateProvider.NEED_AI_CONFIG
            }
        }
    }
}
