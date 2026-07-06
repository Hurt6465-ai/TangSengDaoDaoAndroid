package com.chat.translate.prefs

import android.content.Context
import com.chat.translate.core.TranslateMode
import com.chat.translate.prompt.TranslatePrompt
import com.chat.translate.util.HashUtil

object TranslatePrefs {
    private const val PREF_NAME = "wktranslate_prefs"

    private const val KEY_MODE = "mode"
    private const val KEY_AI_ENDPOINT = "ai_endpoint"
    private const val KEY_AI_KEY = "ai_key"
    private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_AI_ADAPTER = "ai_adapter"
    private const val KEY_AI_VERIFIED = "ai_verified"
    private const val KEY_AI_VERIFIED_HASH = "ai_verified_hash"
    private const val KEY_AI_VERIFIED_AT = "ai_verified_at"
    private const val KEY_MACHINE_ENGINE = "machine_engine"
    private const val KEY_MACHINE_URL = "machine_url"
    private const val KEY_MACHINE_PARSER = "machine_parser"
    private const val KEY_CURRENT_COUNTRY = "current_country"
    private const val KEY_LAST_CLEAN_TIME = "last_clean_time"

    const val MODE_AUTO = "auto"
    const val MODE_AI = "ai"
    const val MODE_MACHINE = "machine"

    const val AI_ADAPTER_DEEPSEEK = "deepseek"
    const val AI_ADAPTER_OPENAI = "openai"

    const val ENGINE_DEEPLX = "deeplx"
    const val ENGINE_GOOGLE = "google"
    const val ENGINE_CUSTOM = "custom"

    const val PARSER_DEEPLX = "deeplx_json"
    const val PARSER_GOOGLE = "google_array"
    const val PARSER_PLAIN = "plain_text"

    const val DEFAULT_DEEPLX_URL = "https://deeplx.vercel.app/translate"
    const val DEFAULT_GOOGLE_URL = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl={sl}&tl={tl}&q={q}"

    data class AiConfig(
        val endpoint: String,
        val apiKey: String,
        val model: String,
        val adapter: String,
        val temperature: Double = 0.2,
        val promptVersion: String = TranslatePrompt.VERSION
    )

    data class MachineConfig(
        val engine: String,
        val url: String,
        val parser: String,
        val promptVersion: String = TranslatePrompt.VERSION
    )

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getMode(context: Context): TranslateMode {
        return when (sp(context).getString(KEY_MODE, MODE_AUTO)) {
            MODE_AI -> TranslateMode.AI
            MODE_MACHINE -> TranslateMode.MACHINE
            else -> TranslateMode.AUTO
        }
    }

    fun setMode(context: Context, mode: TranslateMode) {
        val raw = when (mode) {
            TranslateMode.AI -> MODE_AI
            TranslateMode.MACHINE -> MODE_MACHINE
            TranslateMode.AUTO -> MODE_AUTO
        }
        sp(context).edit().putString(KEY_MODE, raw).apply()
    }

    fun getAiConfig(context: Context): AiConfig {
        val p = sp(context)
        val endpoint = p.getString(KEY_AI_ENDPOINT, "") ?: ""
        val rawAdapter = p.getString(KEY_AI_ADAPTER, "") ?: ""
        return AiConfig(
            endpoint = endpoint,
            apiKey = p.getString(KEY_AI_KEY, "") ?: "",
            model = p.getString(KEY_AI_MODEL, "deepseek-chat") ?: "deepseek-chat",
            adapter = resolveAiAdapter(endpoint, rawAdapter)
        )
    }

    fun saveAiConfig(context: Context, endpoint: String, apiKey: String, model: String, adapter: String = "") {
        val oldHash = currentAiConfigHash(context)
        val cleanEndpoint = endpoint.trim()
        val finalAdapter = resolveAiAdapter(cleanEndpoint, adapter.trim())
        val newHash = aiConfigHash(cleanEndpoint, apiKey.trim(), model.trim(), finalAdapter)
        sp(context).edit()
            .putString(KEY_AI_ENDPOINT, cleanEndpoint)
            .putString(KEY_AI_KEY, apiKey.trim())
            .putString(KEY_AI_MODEL, model.trim())
            .putString(KEY_AI_ADAPTER, finalAdapter)
            .apply()
        if (oldHash != newHash) setAiVerified(context, false)
    }


    private fun resolveAiAdapter(endpoint: String, adapter: String): String {
        val lowerEndpoint = endpoint.lowercase()
        val lowerAdapter = adapter.lowercase()
        if (lowerAdapter == AI_ADAPTER_OPENAI) return AI_ADAPTER_OPENAI
        if (lowerAdapter == AI_ADAPTER_DEEPSEEK && isDeepSeekOfficialEndpoint(lowerEndpoint)) return AI_ADAPTER_DEEPSEEK
        if (isDeepSeekOfficialEndpoint(lowerEndpoint)) return AI_ADAPTER_DEEPSEEK
        // Third-party gateways such as fuxingapi are OpenAI-compatible. Do not send
        // DeepSeek-only fields like thinking/tool_choice to these gateways.
        return AI_ADAPTER_OPENAI
    }

    private fun isDeepSeekOfficialEndpoint(lowerEndpoint: String): Boolean {
        return lowerEndpoint.contains("api.deepseek.com") || lowerEndpoint.contains("deepseek.com/v1")
    }

    fun hasUsableAi(context: Context): Boolean {
        val config = getAiConfig(context)
        if (config.endpoint.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) return false
        val p = sp(context)
        if (!p.getBoolean(KEY_AI_VERIFIED, false)) return false
        return p.getString(KEY_AI_VERIFIED_HASH, "") == currentAiConfigHash(context)
    }

    fun setAiVerified(context: Context, verified: Boolean) {
        val editor = sp(context).edit().putBoolean(KEY_AI_VERIFIED, verified)
        if (verified) {
            editor.putString(KEY_AI_VERIFIED_HASH, currentAiConfigHash(context))
                .putLong(KEY_AI_VERIFIED_AT, System.currentTimeMillis())
        }
        editor.apply()
    }

    fun isAiVerified(context: Context): Boolean = hasUsableAi(context)

    private fun currentAiConfigHash(context: Context): String {
        val config = getAiConfig(context)
        return aiConfigHash(config.endpoint, config.apiKey, config.model, config.adapter)
    }

    private fun aiConfigHash(endpoint: String, apiKey: String, model: String, adapter: String): String {
        return HashUtil.sha256(endpoint.trim() + "|" + model.trim() + "|" + adapter.trim() + "|" + apiKey.trim())
    }

    fun getMachineConfig(context: Context): MachineConfig {
        val p = sp(context)
        val engine = p.getString(KEY_MACHINE_ENGINE, ENGINE_DEEPLX) ?: ENGINE_DEEPLX
        val defaultUrl = when (engine) {
            ENGINE_GOOGLE -> DEFAULT_GOOGLE_URL
            else -> DEFAULT_DEEPLX_URL
        }
        val defaultParser = when (engine) {
            ENGINE_GOOGLE -> PARSER_GOOGLE
            ENGINE_CUSTOM -> PARSER_PLAIN
            else -> PARSER_DEEPLX
        }
        return MachineConfig(
            engine = engine,
            url = p.getString(KEY_MACHINE_URL, defaultUrl) ?: defaultUrl,
            parser = p.getString(KEY_MACHINE_PARSER, defaultParser) ?: defaultParser
        )
    }

    fun saveMachineConfig(context: Context, engine: String, url: String, parser: String) {
        sp(context).edit()
            .putString(KEY_MACHINE_ENGINE, engine)
            .putString(KEY_MACHINE_URL, url.trim())
            .putString(KEY_MACHINE_PARSER, parser)
            .apply()
    }

    fun getCurrentCountryCode(context: Context): String {
        val own = sp(context).getString(KEY_CURRENT_COUNTRY, "") ?: ""
        if (own.isNotBlank()) return own
        val appSp = context.applicationContext.getSharedPreferences("wk_user", Context.MODE_PRIVATE)
        val candidates = listOf(
            appSp.getString("current_country", ""),
            appSp.getString("country_code", ""),
            appSp.getString("country", ""),
            context.applicationContext.getSharedPreferences("wkSharedPreferences", Context.MODE_PRIVATE).getString("current_country", "")
        )
        return candidates.firstOrNull { !it.isNullOrBlank() } ?: ""
    }

    fun setCurrentCountryCode(context: Context, countryCode: String) {
        sp(context).edit().putString(KEY_CURRENT_COUNTRY, countryCode.trim()).apply()
    }

    fun getLastCleanTime(context: Context): Long = sp(context).getLong(KEY_LAST_CLEAN_TIME, 0L)

    fun setLastCleanTime(context: Context, time: Long) {
        sp(context).edit().putLong(KEY_LAST_CLEAN_TIME, time).apply()
    }
}
