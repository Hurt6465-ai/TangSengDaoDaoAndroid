package com.chat.translate.core

import android.content.Context
import com.chat.translate.prefs.TranslatePrefs

object TranslateRouter {
    fun resolveProvider(context: Context, scene: TranslateScene): TranslateProvider {
        val hasAi = TranslatePrefs.hasUsableAi(context)

        if (scene == TranslateScene.BEFORE_SEND) {
            // 发送前翻译也不要卡死在 AI 配置页。
            // 有验证过的 AI 就用 AI；没有 AI 时用内置 Google 机翻兜底，失败则阻止发送原文。
            return if (hasAi) TranslateProvider.AI else TranslateProvider.MACHINE
        }

        return when (TranslatePrefs.getMode(context)) {
            TranslateMode.AI -> if (hasAi) TranslateProvider.AI else TranslateProvider.NEED_AI_CONFIG
            TranslateMode.MACHINE -> TranslateProvider.MACHINE
            // Chat bubble translation should work out of the box. If AI has not
            // been verified, fall back to built-in Google machine translation.
            TranslateMode.AUTO -> if (hasAi) TranslateProvider.AI else TranslateProvider.MACHINE
        }
    }
}
