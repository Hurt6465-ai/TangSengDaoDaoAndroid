package com.chat.translate.ai

import com.chat.translate.prefs.TranslatePrefs
import com.chat.translate.prompt.TranslatePrompt
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI-compatible gateways used by third-party aggregators.
 *
 * We still send the common DeepSeek-style thinking disabled body because many
 * gateways expose DeepSeek reasoning models through the OpenAI-compatible path,
 * and clients such as Kelivo allow adding this custom body field.
 *
 * Do not add tool_choice here: some gateways reject it for models without tool
 * support. Keep the third-party request body small and focused on translation.
 */
class OpenAICompatibleAdapter : ChatCompletionsAdapter() {
    override fun buildRequestBody(config: TranslatePrefs.AiConfig, targetLangName: String, text: String): RequestBody {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", TranslatePrompt.SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", "目标语言：$targetLangName\n原文：$text"))
        val json = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
            .put("temperature", config.temperature)
            .put("stream", false)
            .put("max_tokens", 512)
            .put("thinking", JSONObject().put("type", "disabled"))
        return json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    }
}
