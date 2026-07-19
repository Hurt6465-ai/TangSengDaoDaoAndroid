package com.chat.forum;

import android.text.Html;
import android.text.Spanned;
import android.text.SpannedString;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Small immutable cache for forum HTML so RecyclerView rebinding does not reparse unchanged text. */
final class ForumHtmlCache {
    private static final int MAX_CACHED_CHARS = 240_000;
    private static final LruCache<String, Spanned> CACHE =
            new LruCache<String, Spanned>(MAX_CACHED_CHARS) {
                @Override
                protected int sizeOf(@NonNull String key, @NonNull Spanned value) {
                    return Math.max(1, key.length() + value.length());
                }
            };

    private ForumHtmlCache() {
    }

    @NonNull
    static CharSequence parse(@Nullable String html) {
        String normalized = html == null ? "" : html.replace("\n", "<br>");
        synchronized (CACHE) {
            Spanned cached = CACHE.get(normalized);
            if (cached != null) return cached;
        }
        Spanned parsed = new SpannedString(Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY));
        synchronized (CACHE) {
            CACHE.put(normalized, parsed);
        }
        return parsed;
    }
}
