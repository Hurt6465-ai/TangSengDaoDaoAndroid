package com.chat.deepseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

final class DeepSeekContactStore {
    private static final String PREF = "wk_deepseek_contacts";

    private DeepSeekContactStore() {}

    private static String key(DeepSeekRequest request, String field) {
        String self = request == null || request.selfUid == null ? "" : request.selfUid.trim();
        String channel = request == null || request.channelId == null ? "" : request.channelId.trim();
        int type = request == null ? 0 : request.channelType;
        return self + "_" + type + "_" + channel + "_" + field;
    }

    private static String legacyKey(DeepSeekRequest request, String field) {
        String channel = request == null || request.channelId == null ? "" : request.channelId.trim();
        int type = request == null ? 0 : request.channelType;
        return type + "_" + channel + "_" + field;
    }

    static void apply(Context context, DeepSeekRequest request) {
        if (context == null || request == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        request.myNativeLanguage = readString(sp, request, "my_native", request.myNativeLanguage);
        request.peerNativeLanguage = readString(sp, request, "peer_native", request.peerNativeLanguage);
        request.background = readString(sp, request, "background", request.background);
        request.purpose = readString(sp, request, "purpose", request.purpose);
        request.relationshipStage = readString(sp, request, "relationship_stage", request.relationshipStage);
        request.preferredStyle = readString(sp, request, "preferred_style", request.preferredStyle);
        request.flirtLevel = readInt(sp, request, "flirt_level", request.flirtLevel);
        request.contextEnabled = readBoolean(sp, request, "context_enabled", request.contextEnabled);
        request.contextLimit = 0; // old 30/60/80 settings are intentionally ignored
        request.contactProfile = DeepSeekContactProfile.fromJson(readString(sp, request, "profile", ""));
        if (TextUtils.isEmpty(request.contactProfile.relationshipStage)) {
            request.contactProfile.relationshipStage = request.relationshipStage;
        }
        request.contactProfile.relationshipStage = request.relationshipStage;
        request.contactProfile.preferredStyle = request.preferredStyle;
        request.contactProfile.flirtLevel = request.flirtLevel;
        request.contactProfile.purpose = request.purpose;
        request.contactProfile.background = request.background;
    }

    static void save(Context context, DeepSeekRequest request) {
        if (context == null || request == null) return;
        if (request.contactProfile == null) request.contactProfile = new DeepSeekContactProfile();
        request.contactProfile.relationshipStage = safe(request.relationshipStage);
        request.contactProfile.preferredStyle = safe(request.preferredStyle);
        request.contactProfile.flirtLevel = DeepSeekContactProfile.clamp(request.flirtLevel, 0, 2);
        request.contactProfile.purpose = DeepSeekContactProfile.limit(request.purpose, 200);
        request.contactProfile.background = DeepSeekContactProfile.limit(request.background, 1000);
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(key(request, "my_native"), safe(request.myNativeLanguage))
                .putString(key(request, "peer_native"), safe(request.peerNativeLanguage))
                .putString(key(request, "background"), DeepSeekContactProfile.limit(request.background, 1000))
                .putString(key(request, "purpose"), DeepSeekContactProfile.limit(request.purpose, 200))
                .putString(key(request, "relationship_stage"), safe(request.relationshipStage))
                .putString(key(request, "preferred_style"), safe(request.preferredStyle))
                .putInt(key(request, "flirt_level"), DeepSeekContactProfile.clamp(request.flirtLevel, 0, 2))
                .putBoolean(key(request, "context_enabled"), request.contextEnabled)
                .remove(key(request, "context_limit"))
                .putString(key(request, "profile"), request.contactProfile.toJson())
                .apply();
    }

    static void saveProfile(Context context, DeepSeekRequest request, DeepSeekContactProfile profile) {
        if (context == null || request == null || profile == null) return;
        request.contactProfile = profile;
        request.relationshipStage = profile.relationshipStage;
        request.preferredStyle = profile.preferredStyle;
        request.flirtLevel = profile.flirtLevel;
        request.purpose = profile.purpose;
        request.background = profile.background;
        save(context, request);
    }

    static void clearProfile(Context context, DeepSeekRequest request) {
        if (context == null || request == null) return;
        DeepSeekContactProfile empty = new DeepSeekContactProfile();
        empty.relationshipStage = request.relationshipStage;
        empty.preferredStyle = request.preferredStyle;
        empty.flirtLevel = request.flirtLevel;
        empty.purpose = request.purpose;
        empty.background = request.background;
        request.contactProfile = empty;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(key(request, "profile"), empty.toJson())
                .apply();
    }

    private static String readString(SharedPreferences sp, DeepSeekRequest request, String field, String fallback) {
        String currentKey = key(request, field);
        if (sp.contains(currentKey)) return sp.getString(currentKey, fallback);
        String oldKey = legacyKey(request, field);
        if (!sp.contains(oldKey)) return fallback;
        String value = sp.getString(oldKey, fallback);
        sp.edit().putString(currentKey, value).apply();
        return value;
    }

    private static int readInt(SharedPreferences sp, DeepSeekRequest request, String field, int fallback) {
        String currentKey = key(request, field);
        if (sp.contains(currentKey)) return sp.getInt(currentKey, fallback);
        String oldKey = legacyKey(request, field);
        if (!sp.contains(oldKey)) return fallback;
        int value = sp.getInt(oldKey, fallback);
        sp.edit().putInt(currentKey, value).apply();
        return value;
    }

    private static boolean readBoolean(SharedPreferences sp, DeepSeekRequest request, String field, boolean fallback) {
        String currentKey = key(request, field);
        if (sp.contains(currentKey)) return sp.getBoolean(currentKey, fallback);
        String oldKey = legacyKey(request, field);
        if (!sp.contains(oldKey)) return fallback;
        boolean value = sp.getBoolean(oldKey, fallback);
        sp.edit().putBoolean(currentKey, value).apply();
        return value;
    }


    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
