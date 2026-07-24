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
 * Persists the DeepSeek web conversation used by one Talkami chat.
 *
 * The mapping is scoped by the logged-in Talkami uid, channel type and channel id, so two
 * Talkami accounts on the same device never share a DeepSeek conversation accidentally.
 * Only the opaque id from /a/chat/s/{id} is stored; cookies and DeepSeek credentials remain
 * owned by Android WebView's CookieManager.
 *
 * To avoid resending the same Talkami history on every request, the store also keeps only
 * SHA-256 hashes of the last successfully submitted snapshot. Message text is not duplicated
 * in SharedPreferences. A reused DeepSeek conversation receives only new lines, with a small
 * recent-history checkpoint every few requests or when the local sequence can no longer be
 * aligned safely.
 */
final class DeepSeekConversationStore {
    private static final String PREF = "wk_deepseek_conversation_map_v1";
    private static final String KEY_PREFIX = "conversation:";
    private static final String USED_SUFFIX = ":used_at";
    private static final String CONTEXT_HASHES_SUFFIX = ":context_hashes";
    private static final String CONTEXT_SUBMIT_COUNT_SUFFIX = ":context_submit_count";
    private static final String CONTEXT_LIMIT_SUFFIX = ":context_limit";
    private static final int MAX_MAPPINGS = 300;
    private static final int MAX_CONTEXT_LINES = 120;
    private static final int RECENT_CHECKPOINT_LINES = 20;
    private static final int CHECKPOINT_INTERVAL = 12;
    private static final Pattern CONVERSATION_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private DeepSeekConversationStore() {
    }

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
        if (!TextUtils.isEmpty(previousId) && !TextUtils.equals(previousId, id)) {
            // A different DeepSeek conversation must never inherit the previous one's sync state.
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
     * Returns the context that should be sent with this request.
     *
     * A fresh DeepSeek conversation receives the complete Talkami snapshot. A mapped conversation
     * receives only lines not present in the last successfully submitted snapshot. If this app was
     * upgraded from an older build, the hash baseline is missing; in that one migration request we
     * send only the most recent checkpoint instead of duplicating the whole history.
     */
    static ContextPlan planContext(Context context, DeepSeekRequest request,
                                   boolean hasMappedConversation, boolean forceFull) {
        if (request == null || !request.contextEnabled) {
            return new ContextPlan("", 0, "disabled_or_empty");
        }
        int limit = normalizeLimit(request.contextLimit);
        List<String> currentLines = limitTail(snapshotLines(request.contextSnapshot), limit);
        if (currentLines.isEmpty()) {
            return new ContextPlan("", 0, "disabled_or_empty");
        }
        if (forceFull || !hasMappedConversation || context == null) {
            return fromLines(currentLines, "full");
        }

        String key = mappingKey(request);
        if (TextUtils.isEmpty(key)) {
            return fromLines(limitTail(currentLines, normalizeLimit(request.contextLimit)), "full_no_key");
        }
        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        List<String> previousHashes = decodeHashes(
                preferences.getString(key + CONTEXT_HASHES_SUFFIX, ""));
        int submittedCount = Math.max(0,
                preferences.getInt(key + CONTEXT_SUBMIT_COUNT_SUFFIX, 0));
        int previousLimit = Math.max(0, preferences.getInt(key + CONTEXT_LIMIT_SUFFIX, 0));

        if (!previousHashes.isEmpty() && previousLimit > 0 && limit > previousLimit) {
            return fromLines(currentLines, "context_limit_expanded");
        }
        if (previousHashes.isEmpty()) {
            return fromLines(limitTail(currentLines, Math.min(limit, RECENT_CHECKPOINT_LINES)),
                    "migration_checkpoint");
        }

        List<String> currentHashes = hashLines(currentLines);
        int overlap = longestSuffixPrefixOverlap(previousHashes, currentHashes);
        boolean periodicCheckpoint = submittedCount >= CHECKPOINT_INTERVAL - 1
                && (submittedCount + 1) % CHECKPOINT_INTERVAL == 0;
        if (periodicCheckpoint) {
            return fromLines(limitTail(currentLines, Math.min(limit, RECENT_CHECKPOINT_LINES)),
                    "periodic_checkpoint");
        }
        if (overlap > 0) {
            List<String> delta = new ArrayList<>(currentLines.subList(overlap, currentLines.size()));
            return fromLines(limitTail(delta, limit), delta.isEmpty() ? "already_synced" : "delta");
        }

        // Edits, deletions, a long period without using the assistant, or a rolling 120-message
        // window can remove the common prefix. A recent checkpoint is safer than assuming that
        // every current line is new and duplicating the entire conversation.
        return fromLines(limitTail(currentLines, Math.min(limit, RECENT_CHECKPOINT_LINES)),
                "realign_checkpoint");
    }

    /** Records the full snapshot only after WebView confirmed that the prompt was submitted. */
    static void markContextSubmitted(Context context, DeepSeekRequest request, String fullSnapshot) {
        String key = mappingKey(request);
        if (context == null || request == null || !request.contextEnabled || TextUtils.isEmpty(key)) {
            return;
        }
        List<String> lines = limitTail(snapshotLines(fullSnapshot),
                normalizeLimit(request.contextLimit));
        if (lines.isEmpty()) return;
        List<String> hashes = hashLines(lines);
        if (hashes.isEmpty()) return;
        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        int count = Math.max(0, preferences.getInt(key + CONTEXT_SUBMIT_COUNT_SUFFIX, 0));
        preferences.edit()
                .putString(key + CONTEXT_HASHES_SUFFIX, TextUtils.join(",", hashes))
                .putInt(key + CONTEXT_SUBMIT_COUNT_SUFFIX, count == Integer.MAX_VALUE ? 1 : count + 1)
                .putInt(key + CONTEXT_LIMIT_SUFFIX, normalizeLimit(request.contextLimit))
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

    private static ContextPlan fromLines(List<String> lines, String mode) {
        if (lines == null || lines.isEmpty()) return new ContextPlan("", 0, mode);
        return new ContextPlan(TextUtils.join("\n", lines), lines.size(), mode);
    }

    private static List<String> snapshotLines(String raw) {
        if (TextUtils.isEmpty(raw)) return Collections.emptyList();
        String[] source = raw.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        int start = Math.max(0, source.length - MAX_CONTEXT_LINES);
        for (int i = start; i < source.length; i++) {
            String line = safe(source[i]);
            if (!TextUtils.isEmpty(line)) lines.add(line);
        }
        return lines;
    }

    private static List<String> limitTail(List<String> lines, int maxLines) {
        if (lines == null || lines.isEmpty() || maxLines <= 0) return Collections.emptyList();
        int start = Math.max(0, lines.size() - maxLines);
        return new ArrayList<>(lines.subList(start, lines.size()));
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

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) out.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
            return out.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static List<String> decodeHashes(String encoded) {
        if (TextUtils.isEmpty(encoded)) return Collections.emptyList();
        String[] values = encoded.split(",");
        List<String> hashes = new ArrayList<>();
        int start = Math.max(0, values.length - MAX_CONTEXT_LINES);
        for (int i = start; i < values.length; i++) {
            String value = safe(values[i]).toLowerCase(java.util.Locale.US);
            if (HASH_PATTERN.matcher(value).matches()) hashes.add(value);
        }
        return hashes;
    }

    private static int longestSuffixPrefixOverlap(List<String> previous, List<String> current) {
        if (previous == null || current == null || previous.isEmpty() || current.isEmpty()) return 0;
        int max = Math.min(previous.size(), current.size());
        for (int length = max; length > 0; length--) {
            int previousStart = previous.size() - length;
            boolean equal = true;
            for (int i = 0; i < length; i++) {
                if (!TextUtils.equals(previous.get(previousStart + i), current.get(i))) {
                    equal = false;
                    break;
                }
            }
            if (equal) return length;
        }
        return 0;
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

    private static int normalizeLimit(int value) {
        if (value <= 0) return 100;
        return Math.max(20, Math.min(MAX_CONTEXT_LINES, value));
    }

    private static void removeEntry(SharedPreferences preferences, String key) {
        preferences.edit()
                .remove(key)
                .remove(key + USED_SUFFIX)
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
                    || key.endsWith(CONTEXT_HASHES_SUFFIX)
                    || key.endsWith(CONTEXT_SUBMIT_COUNT_SUFFIX)
                    || key.endsWith(CONTEXT_LIMIT_SUFFIX)
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
            editor.remove(key)
                    .remove(key + USED_SUFFIX)
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
