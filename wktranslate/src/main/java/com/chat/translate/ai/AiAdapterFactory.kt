package com.chat.translate.ai

import com.chat.translate.prefs.TranslatePrefs

object AiAdapterFactory {
    fun get(adapter: String): AiRequestAdapter {
        return when (adapter) {
            TranslatePrefs.AI_ADAPTER_OPENAI -> OpenAICompatibleAdapter()
            else -> DeepSeekAdapter()
        }
    }
}
