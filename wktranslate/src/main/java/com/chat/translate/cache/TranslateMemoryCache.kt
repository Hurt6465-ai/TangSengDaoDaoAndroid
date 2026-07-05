package com.chat.translate.cache

import android.util.LruCache
import com.chat.translate.core.TranslateResult

object TranslateMemoryCache {
    private val cache = object : LruCache<String, TranslateResult>(200) {}

    fun get(key: String): TranslateResult? = synchronized(cache) { cache.get(key) }

    fun put(key: String, result: TranslateResult) {
        synchronized(cache) { cache.put(key, result) }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }
}
