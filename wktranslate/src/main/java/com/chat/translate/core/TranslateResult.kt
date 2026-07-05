package com.chat.translate.core

data class TranslateResult(
    val success: Boolean,
    val translatedText: String = "",
    val provider: TranslateProvider? = null,
    val engine: String = "",
    val fromCache: Boolean = false,
    val errorCode: TranslateErrorCode = TranslateErrorCode.NONE,
    val message: String = ""
) {
    companion object {
        fun success(text: String, provider: TranslateProvider, engine: String, fromCache: Boolean = false): TranslateResult {
            return TranslateResult(true, text, provider, engine, fromCache)
        }

        fun failure(code: TranslateErrorCode, message: String = ""): TranslateResult {
            return TranslateResult(false, errorCode = code, message = message)
        }
    }
}
