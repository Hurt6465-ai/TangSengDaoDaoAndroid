package com.chat.deepseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Persists the DeepSeek web conversation used by one Talkami chat.
 *
 * The mapping is scoped by the logged-in Talkami uid, channel type and channel id, so two
 * Talkami accounts on the same device never share a DeepSeek conversation accidentally.
 * Only the opaque id from /a/chat/s/{id} is stored; cookies and DeepSeek credentials remain
 * owned by Android WebView's CookieManager.
 */
final class DeepSeekConversationStore {
    private static final String PREF = "wk_deepseek_conversation_map_v1";
    private static final String KEY_PREFIX = "conversation:";
    private static final String USED_SUFFIX = ":used_at";
    private static final int MAX_MAPPINGS = 300;
    private static final Pattern CONVERSATION_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{16,128}");

    private DeepSeekConversationStore() {
    }

    static String getConversationId(Context context, DeepSeekRequest request) {
        String key = mappingKey(request);
        if (context == null || TextUtils.isEmpty(key)) return "";
        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String id = cleanId(preferences.getString(key, ""));
        if (TextUtils.isEmpty(id)) {
            if (preferences.contains(key)) {
                preferences.edit().remove(key).remove(key + USED_SUFFIX).apply();
            }
            return "";
        }
        preferences.edit().putLong(key + USED_SUFFIX, System.currentTimeMillis()).apply();
        return id;
    }

    static void save(Context context, DeepSeekRequest request, String conversationId) {
        String key = mappingKey(request);
        String id = cleanId(conversationId);
        if (context == null || TextUtils.isEmpty(key) || TextUtils.isEmpty(id)) return;
        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        preferences.edit()
                .putString(key, id)
                .putLong(key + USED_SUFFIX, now)
                .apply();
        trimOldMappings(preferences);
    }

    static void clear(Context context, DeepSeekRequest request) {
        String key = mappingKey(request);
        if (context == null || TextUtils.isEmpty(key)) return;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .remove(key + USED_SUFFIX)
                .apply();
    }

    static String conversationUrl(String conversationId) {
        String id = cleanId(conversationId);
        return TextUtils.isEmpty(id)
                ? "https://chat.deepseek.com/a/chat/"
                : "https://chat.deepseek.com/a/chat/s/" + id;
    }

    static String extractConversationId(String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) return "";
        try {
            Uri uri = Uri.parse(rawUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"chat.deepseek.com".equalsIgnoreCase(uri.getHost())) {
                return "";
            }
            List<String> segments = uri.getPathSegments();
            for (int i = 0; i + 3 < segments.size(); i++) {
                if ("a".equals(segments.get(i))
                        && "chat".equals(segments.get(i + 1))
                        && "s".equals(segments.get(i + 2))) {
                    return cleanId(segments.get(i + 3));
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    static boolean isNewChatUrl(String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"chat.deepseek.com".equalsIgnoreCase(uri.getHost())) {
                return false;
            }
            String path = uri.getPath();
            if (path == null) return false;
            while (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return "/a/chat".equals(path);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String mappingKey(DeepSeekRequest request) {
        if (request == null) return "";
        String selfUid = safe(request.selfUid);
        String channelId = safe(request.channelId);
        if (TextUtils.isEmpty(selfUid) || TextUtils.isEmpty(channelId)) return "";
        String raw = selfUid + "\n" + request.channelType + "\n" + channelId;
        String encoded = Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP | Base64.URL_SAFE);
        return KEY_PREFIX + encoded;
    }

    private static String cleanId(String value) {
        String id = safe(value);
        return CONVERSATION_ID_PATTERN.matcher(id).matches() ? id : "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void trimOldMappings(SharedPreferences preferences) {
        Map<String, ?> all = preferences.getAll();
        List<MappingEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(KEY_PREFIX) || key.endsWith(USED_SUFFIX)
                    || !(entry.getValue() instanceof String)) {
                continue;
            }
            long usedAt = preferences.getLong(key + USED_SUFFIX, 0L);
            entries.add(new MappingEntry(key, usedAt));
        }
        if (entries.size() <= MAX_MAPPINGS) return;
        Collections.sort(entries, Comparator.comparingLong(item -> item.usedAt));
        SharedPreferences.Editor editor = preferences.edit();
        int removeCount = entries.size() - MAX_MAPPINGS;
        for (int i = 0; i < removeCount; i++) {
            String key = entries.get(i).key;
            editor.remove(key).remove(key + USED_SUFFIX);
        }
        editor.apply();
    }

    private static final class MappingEntry {
        final String key;
        final long usedAt;

        MappingEntry(String key, long usedAt) {
            this.key = key;
            this.usedAt = usedAt;
        }
    }
}
