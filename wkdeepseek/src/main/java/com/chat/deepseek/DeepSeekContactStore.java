package com.chat.deepseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

final class DeepSeekContactStore {
    private static final String PREF = "wk_deepseek_contacts";

    private DeepSeekContactStore() {}

    private static String key(DeepSeekRequest request, String field) {
        String channel = request == null || request.channelId == null ? "" : request.channelId;
        int type = request == null ? 0 : request.channelType;
        return type + "_" + channel + "_" + field;
    }

    static void apply(Context context, DeepSeekRequest request) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String value = sp.getString(key(request, "my_native"), "");
        if (!TextUtils.isEmpty(value)) request.myNativeLanguage = value;
        value = sp.getString(key(request, "peer_native"), "");
        if (!TextUtils.isEmpty(value)) request.peerNativeLanguage = value;
        value = sp.getString(key(request, "background"), "");
        if (!TextUtils.isEmpty(value)) request.background = value;
        value = sp.getString(key(request, "purpose"), "");
        if (!TextUtils.isEmpty(value)) request.purpose = value;
    }

    static void save(Context context, DeepSeekRequest request) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(key(request, "my_native"), safe(request.myNativeLanguage))
                .putString(key(request, "peer_native"), safe(request.peerNativeLanguage))
                .putString(key(request, "background"), safe(request.background))
                .putString(key(request, "purpose"), safe(request.purpose))
                .apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
