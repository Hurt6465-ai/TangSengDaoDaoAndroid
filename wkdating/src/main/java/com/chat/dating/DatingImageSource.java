package com.chat.dating;

import android.content.Context;
import android.text.TextUtils;

import com.chat.base.config.WKApiConfig;

/** Resolves every dating image through the same URL path used by the old fullscreen partner page. */
public final class DatingImageSource {
    private DatingImageSource() {}

    public static Object resolve(Context context, String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String value = raw.trim();
        if (context != null && value.startsWith("res://")) {
            String name = value.substring("res://".length()).trim();
            if (!TextUtils.isEmpty(name)) {
                int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
                if (id != 0) return id;
            }
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")
                || value.startsWith("content://") || value.startsWith("file://")) {
            return value;
        }
        while (value.startsWith("/")) value = value.substring(1);
        return WKApiConfig.getShowUrl(value);
    }
}
