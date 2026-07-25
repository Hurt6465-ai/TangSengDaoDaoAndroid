package com.chat.deepseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps one Talkami contact to one DeepSeek web conversation and performs incremental context sync.
 *
 * Normal operation does not impose a local message-count or character limit. A fresh DeepSeek
 * conversation receives every line currently supplied by the chat screen; a reused conversation
 * receives only lines added after the last successful submit. Only a bounded tail of SHA-256
 * hashes is stored locally as a sync anchor. This bounds SharedPreferences growth without limiting
 * the actual conversation sent to DeepSeek.
 */
final class DeepSeekConversationStore {
    private static final String PREF = "wk_deepseek_conversation_map_v1";
    private static final String KEY_PREFIX = "conversation:";
    private static final String USED_SUFFIX = ":used_at";
    private static final String CREATED_SUFFIX = ":created_at";
    private static final String CONTEXT_HASHES_SUFFIX = ":context_hashes";
    private static final String CONTEXT_SUBMIT_COUNT_SUFFIX = ":context_submit_count";
    // Kept only so old builds' preference entries can be removed cleanly.
    private static final String CONTEXT_LIMIT_SUFFIX = ":context_limit";
    private static final int MAX_MAPPINGS = 300;
    private static final int MAX_SYNC_HASHES = 512;
    private static final Pattern CONVERSATION_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private DeepSeekConversationStore() {}

    static final class ContextPlan {
        final String snapshot;
        final int count;
        final String mode;

        ContextPlan(String snapshot, int count, String mode) {
            this.snapshot = snapshot == null ? "" : snapshot;
            this.count = Math.max(0, count);
            this.mode = mode == null ? "unknown" : mode;
        }
    }

    static String getConversationId(Context context, DeepSeekRequest request) {
        String key = mappingKey(request);
        if (context == null || TextUtils.isEmpty(key)) return "";
        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String id = cleanId(preferences.getString(key, ""));
        if (TextUtils.isEmpty(id)) {
            if (preferences.contains(key)) removeEntry(preferences, key);
            return "";
        }
        // Do not rotate a healthy conversation by age or submit count. The web service already owns
        // its context budget. A new conversation is created only when the route is stale/deleted or
        // the webpage explicitly reports a context-length overflow.
        preferences.edit().putLong(key + USED_SUFFIX, System.currentTimeMillis()).apply();
        return id;
    }

    static void save(Context context, DeepSeekRequest request, String conversationId) {
        String key = mappingKey(request);
        String id = cleanId(conversationId);
        if (context == null || TextUtils.isEmpty(key) || TextUtils.isEmpty(id)) return;
        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String previousId = cleanId(preferences.getString(key, ""));
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(key, id)
                .putLong(key + USED_SUFFIX, now);
        if (TextUtils.isEmpty(previousId) || !TextUtils.equals(previousId, id)) {
            editor.putLong(key + CREATED_SUFFIX, now);
        }
        if (!TextUtils.isEmpty(previousId) && !TextUtils.equals(previousId, id)) {
            editor.remove(key + CONTEXT_HASHES_SUFFIX)
                    .remove(key + CONTEXT_SUBMIT_COUNT_SUFFIX)
                    .remove(key + CONTEXT_LIMIT_SUFFIX);
        }
        editor.apply();
        trimOldMappings(preferences);
    }

    static void clear(Context context, DeepSeekRequest request) {
        String key = mappingKey(request);
        if (context == null || TextUtils.isEmpty(key)) return;
        removeEntry(context.getSharedPreferences(PREF, Context.MODE_PRIVATE), key);
    }

    /**
     * Returns the context that should accompany the next prompt.
     *
     * Fresh conversation: all currently supplied lines.
     * Reused conversation: only lines after the stored sync anchor.
     * Missing/unalignable anchor: all currently supplied lines, because silently discarding context
     * is worse than a one-time duplicate. No local truncation is applied here.
     */
    static ContextPlan planContext(Context context, DeepSeekRequest request,
                                   boolean hasMappedConversation, boolean forceFull) {
        if (request == null || !request.contextEnabled) {
            return new ContextPlan("", 0, "disabled_or_empty");
        }
        List<String> currentLines = snapshotLines(request.contextSnapshot);
        if (currentLines.isEmpty()) {
            return new ContextPlan("", 0, "disabled_or_empty");
        }
        if (forceFull || !hasMappedConversation || context == null) {
            return fromLines(currentLines, "full");
        }

        String key = mappingKey(request);
        if (TextUtils.isEmpty(key)) return fromLines(currentLines, "full_no_key");

        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        List<String> previousTailHashes = decodeHashes(
                preferences.getString(key + CONTEXT_HASHES_SUFFIX, ""));
        if (previousTailHashes.isEmpty()) {
            return fromLines(currentLines, "full_migration");
        }

        List<String> currentHashes = hashLines(currentLines);
        int deltaStart = findDeltaStart(previousTailHashes, currentHashes);
        if (deltaStart >= 0) {
            List<String> delta = new ArrayList<>(currentLines.subList(
                    Math.min(deltaStart, currentLines.size()), currentLines.size()));
            return fromLines(delta, delta.isEmpty() ? "already_synced" : "delta");
        }

        return fromLines(currentLines, "realign_full");
    }

    /** Records the complete local snapshot represented by the successfully submitted prompt. */
    static void markContextSubmitted(Context context, DeepSeekRequest request, String fullSnapshot) {
        String key = mappingKey(request);
        if (context == null || request == null || !request.contextEnabled || TextUtils.isEmpty(key)) {
            return;
        }
        List<String> lines = snapshotLines(fullSnapshot);
        if (lines.isEmpty()) return;
        List<String> hashes = tail(hashLines(lines), MAX_SYNC_HASHES);
        if (hashes.isEmpty()) return;

        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        int count = Math.max(0, preferences.getInt(key + CONTEXT_SUBMIT_COUNT_SUFFIX, 0));
        preferences.edit()
                .putString(key + CONTEXT_HASHES_SUFFIX, TextUtils.join(",", hashes))
                .putInt(key + CONTEXT_SUBMIT_COUNT_SUFFIX,
                        count == Integer.MAX_VALUE ? 1 : count + 1)
                .remove(key + CONTEXT_LIMIT_SUFFIX)
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
                    || !"chat.deepseek.com".equalsIgnoreCase(uri.getHost())) return "";
            List<String> segments = uri.getPathSegments();
            for (int i = 0; i + 3 < segments.size(); i++) {
                if ("a".equals(segments.get(i)) && "chat".equals(segments.get(i + 1))
                        && "s".equals(segments.get(i + 2))) {
                    return cleanId(segments.get(i + 3));
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    static boolean isNewChatUrl(String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"chat.deepseek.com".equalsIgnoreCase(uri.getHost())) return false;
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

    private static ContextPlan fromLines(List<String> lines, String mode) {
        if (lines == null || lines.isEmpty()) return new ContextPlan("", 0, mode);
        return new ContextPlan(TextUtils.join("\n", lines), lines.size(), mode);
    }

    private static List<String> snapshotLines(String raw) {
        if (TextUtils.isEmpty(raw)) return Collections.emptyList();
        String[] source = raw.split("\\r?\\n");
        List<String> lines = new ArrayList<>(source.length);
        for (String item : source) {
            String line = safe(item);
            if (!TextUtils.isEmpty(line)) lines.add(line);
        }
        return lines;
    }

    private static List<String> hashLines(List<String> lines) {
        List<String> hashes = new ArrayList<>();
        if (lines == null) return hashes;
        for (String line : lines) {
            String hash = sha256(safe(line));
            if (!TextUtils.isEmpty(hash)) hashes.add(hash);
        }
        return hashes;
    }

    private static <T> List<T> tail(List<T> list, int maxItems) {
        if (list == null || list.isEmpty() || maxItems <= 0) return Collections.emptyList();
        int start = Math.max(0, list.size() - maxItems);
        return new ArrayList<>(list.subList(start, list.size()));
    }

    /**
     * Finds where new lines begin in the current snapshot.
     * First try an exact match for the stored tail anywhere in the current list. If the chat UI has
     * discarded older rows, fall back to a suffix(previous)-prefix(current) overlap.
     */
    private static int findDeltaStart(List<String> previousTail, List<String> current) {
        if (previousTail == null || current == null || previousTail.isEmpty() || current.isEmpty()) {
            return -1;
        }
        int maxStart = current.size() - previousTail.size();
        for (int start = maxStart; start >= 0; start--) {
            boolean equal = true;
            for (int i = 0; i < previousTail.size(); i++) {
                if (!TextUtils.equals(previousTail.get(i), current.get(start + i))) {
                    equal = false;
                    break;
                }
            }
            if (equal) return start + previousTail.size();
        }

        int maxOverlap = Math.min(previousTail.size(), current.size());
        for (int length = maxOverlap; length > 0; length--) {
            int previousStart = previousTail.size() - length;
            boolean equal = true;
            for (int i = 0; i < length; i++) {
                if (!TextUtils.equals(previousTail.get(previousStart + i), current.get(i))) {
                    equal = false;
                    break;
                }
            }
            if (equal) return length;
        }
        return -1;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                out.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
            }
            return out.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static List<String> decodeHashes(String encoded) {
        if (TextUtils.isEmpty(encoded)) return Collections.emptyList();
        String[] values = encoded.split(",");
        List<String> hashes = new ArrayList<>();
        int start = Math.max(0, values.length - MAX_SYNC_HASHES);
        for (int i = start; i < values.length; i++) {
            String value = safe(values[i]).toLowerCase(java.util.Locale.US);
            if (HASH_PATTERN.matcher(value).matches()) hashes.add(value);
        }
        return hashes;
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

    private static void removeEntry(SharedPreferences preferences, String key) {
        preferences.edit()
                .remove(key)
                .remove(key + USED_SUFFIX)
                .remove(key + CREATED_SUFFIX)
                .remove(key + CONTEXT_HASHES_SUFFIX)
                .remove(key + CONTEXT_SUBMIT_COUNT_SUFFIX)
                .remove(key + CONTEXT_LIMIT_SUFFIX)
                .apply();
    }

    private static void trimOldMappings(SharedPreferences preferences) {
        Map<String, ?> all = preferences.getAll();
        List<MappingEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(KEY_PREFIX) || key.endsWith(USED_SUFFIX)
                    || key.endsWith(CREATED_SUFFIX)
                    || key.endsWith(CONTEXT_HASHES_SUFFIX)
                    || key.endsWith(CONTEXT_SUBMIT_COUNT_SUFFIX)
                    || key.endsWith(CONTEXT_LIMIT_SUFFIX)
                    || !(entry.getValue() instanceof String)) continue;
            entries.add(new MappingEntry(key, preferences.getLong(key + USED_SUFFIX, 0L)));
        }
        if (entries.size() <= MAX_MAPPINGS) return;
        Collections.sort(entries, Comparator.comparingLong(item -> item.usedAt));
        SharedPreferences.Editor editor = preferences.edit();
        int removeCount = entries.size() - MAX_MAPPINGS;
        for (int i = 0; i < removeCount; i++) {
            String key = entries.get(i).key;
            editor.remove(key)
                    .remove(key + USED_SUFFIX)
                    .remove(key + CREATED_SUFFIX)
                    .remove(key + CONTEXT_HASHES_SUFFIX)
                    .remove(key + CONTEXT_SUBMIT_COUNT_SUFFIX)
                    .remove(key + CONTEXT_LIMIT_SUFFIX);
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
