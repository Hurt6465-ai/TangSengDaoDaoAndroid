package com.chat.translate.core

enum class TranslateErrorCode {
    NONE,
    NEED_AI_CONFIG,
    INVALID_CONFIG,
    NETWORK_ERROR,
    HTTP_ERROR,
    PARSE_ERROR,
    EMPTY_RESULT,
    UNSAFE_RESULT,
    UNSUPPORTED,
    UNKNOWN
}
