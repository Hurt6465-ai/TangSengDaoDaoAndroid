package com.chat.dating;

import android.content.Context;
import android.text.TextUtils;

public final class DatingImageSource {
    private DatingImageSource() {}

    public static Object resolve(Context context, String raw) {
        if (context == null || TextUtils.isEmpty(raw)) return raw;
        if (raw.startsWith("res://")) {
            String name = raw.substring("res://".length()).trim();
            if (!TextUtils.isEmpty(name)) {
                int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
                if (id != 0) return id;
            }
        }
        return raw;
    }
}
