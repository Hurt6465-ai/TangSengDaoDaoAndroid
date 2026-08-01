package com.chat.learning;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Imports user speaking packs and exposes them as normal speaking catalog nodes. */
final class SpeakingImportedPackStore {
    static final String ASSET_PREFIX = "imported:";

    private static final int MAX_IMPORT_BYTES = 12 * 1024 * 1024;
    private static final int MAX_INDEX_BYTES = 1024 * 1024;
    private static final String INDEX_PATH = "learning/imported_speaking/catalog.json";
    private static final String DATA_DIR = "learning/speaking/imported";

    private SpeakingImportedPackStore() { }

    static ImportResult importFromUri(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) throw new IllegalArgumentException("No file selected");
        byte[] bytes;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException("Cannot open selected file");
            bytes = readLimited(input, MAX_IMPORT_BYTES);
        }

        String raw = new String(bytes, StandardCharsets.UTF_8).trim();
        if (raw.startsWith("\uFEFF")) raw = raw.substring(1).trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("The selected file is empty");

        JSONObject root;
        if (raw.startsWith("[")) {
            root = new JSONObject();
            root.put("phrases", new JSONArray(raw));
        } else {
            root = new JSONObject(raw);
        }

        JSONArray phrases = root.optJSONArray("phrases");
        if (phrases == null) phrases = root.optJSONArray("items");
        if (phrases == null || phrases.length() == 0) {
            throw new IllegalArgumentException("The speaking pack has no phrases");
        }
        root.put("phrases", phrases);

        String sourceId = first(root.optString("pack_id", ""),
                root.optString("id", ""), root.optString("categoryId", ""));
        if (sourceId.isEmpty()) sourceId = "speaking_" + System.currentTimeMillis();
        String safeSource = LearningRemoteContent.safeFileName(sourceId);
        String packId = safeSource.startsWith("user_speaking_")
                ? safeSource : "user_speaking_" + safeSource;
        String title = first(root.optString("title", ""), sourceId);
        String subtitle = first(root.optString("subtitle", ""), "Imported speaking phrases");
        int version = Math.max(1, root.optInt("version", 1));

        root.put("pack_id", packId);
        root.put("title", title);
        root.put("subtitle", subtitle);
        root.put("version", version);

        Set<String> usedIds = new HashSet<>();
        for (int i = 0; i < phrases.length(); i++) {
            JSONObject item = phrases.optJSONObject(i);
            if (item == null) continue;
            String text = first(item.optString("text", ""), item.optString("word", ""));
            if (text.isEmpty()) continue;
            item.put("text", text);
            if (!item.has("meaning_my")) {
                item.put("meaning_my", first(item.optString("translation", ""),
                        item.optString("meaning", "")));
            }
            String rawItemId = item.optString("id", "").trim();
            String itemId = rawItemId.isEmpty()
                    ? String.format(Locale.US, "%s_%04d", packId, i + 1)
                    : LearningRemoteContent.safeFileName(rawItemId);
            String uniqueId = itemId;
            int duplicateIndex = 2;
            while (!usedIds.add(uniqueId)) uniqueId = itemId + "_" + duplicateIndex++;
            item.put("id", uniqueId);
        }

        String normalized = root.toString(2);
        SpeakingPhraseRepository.Pack parsed = SpeakingPhraseRepository.parse(
                context, normalized, packId, title, ASSET_PREFIX + packId);
        if (parsed.phrases.isEmpty()) throw new IllegalArgumentException("No valid phrases were found");

        File target = dataFile(context, packId);
        LearningRemoteContent.atomicWrite(target, normalized.getBytes(StandardCharsets.UTF_8));

        JSONObject meta = new JSONObject();
        meta.put("id", packId);
        meta.put("title", title);
        meta.put("subtitle", subtitle);
        meta.put("version", version);
        meta.put("item_count", parsed.phrases.size());
        upsert(context, meta);

        return new ImportResult(packId, title, subtitle, ASSET_PREFIX + packId,
                parsed.phrases.size());
    }

    static String read(Context context, String assetPath) throws Exception {
        String packId = packIdFromAsset(assetPath);
        if (packId.isEmpty()) throw new IllegalArgumentException("Invalid imported speaking pack");
        return LearningRemoteContent.readFile(dataFile(context, packId), MAX_IMPORT_BYTES);
    }

    static boolean isImportedAsset(String assetPath) {
        return assetPath != null && assetPath.trim().startsWith(ASSET_PREFIX);
    }

    static String packIdFromAsset(String assetPath) {
        if (!isImportedAsset(assetPath)) return "";
        return LearningRemoteContent.safeFileName(
                assetPath.trim().substring(ASSET_PREFIX.length()));
    }

    static List<LearningCatalogRepository.Node> nodes(Context context) {
        ArrayList<LearningCatalogRepository.Node> result = new ArrayList<>();
        JSONObject root = readIndex(context);
        JSONArray items = root.optJSONArray("items");
        if (items == null) return result;
        for (int i = 0; i < items.length(); i++) {
            JSONObject meta = items.optJSONObject(i);
            if (meta == null) continue;
            String id = meta.optString("id", "").trim();
            if (id.isEmpty() || !dataFile(context, id).isFile()) continue;
            LearningCatalogRepository.Node node = new LearningCatalogRepository.Node();
            node.id = id;
            node.level = id;
            node.title = meta.optString("title", id);
            node.subtitle = meta.optString("subtitle", "");
            node.badge = meta.optInt("item_count", 0) + " phrases";
            node.target = "study";
            node.asset = ASSET_PREFIX + id;
            node.dataVersion = Math.max(1, meta.optInt("version", 1));
            node.itemCount = Math.max(0, meta.optInt("item_count", 0));
            node.imported = true;
            result.add(node);
        }
        return result;
    }

    static boolean delete(Context context, String packId) {
        String safe = LearningRemoteContent.safeFileName(packId);
        File data = dataFile(context, safe);
        boolean deleted = !data.exists() || data.delete();

        JSONObject root = readIndex(context);
        JSONArray source = root.optJSONArray("items");
        JSONArray next = new JSONArray();
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item == null || safe.equals(item.optString("id", ""))) continue;
                next.put(item);
            }
        }
        try {
            root.put("items", next);
            writeIndex(context, root);
        } catch (Throwable ignored) {
            return false;
        }
        return deleted;
    }

    private static File dataFile(Context context, String packId) {
        String safe = LearningRemoteContent.safeFileName(packId);
        return new File(context.getFilesDir(), DATA_DIR + "/" + safe + ".json");
    }

    private static void upsert(Context context, JSONObject meta) throws Exception {
        JSONObject root = readIndex(context);
        JSONArray source = root.optJSONArray("items");
        JSONArray next = new JSONArray();
        String id = meta.optString("id", "");
        boolean replaced = false;
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject old = source.optJSONObject(i);
                if (old == null) continue;
                if (id.equals(old.optString("id", ""))) {
                    next.put(meta);
                    replaced = true;
                } else {
                    next.put(old);
                }
            }
        }
        if (!replaced) next.put(meta);
        root.put("items", next);
        writeIndex(context, root);
    }

    private static JSONObject readIndex(Context context) {
        File file = new File(context.getFilesDir(), INDEX_PATH);
        try {
            String json = LearningRemoteContent.readFile(file, MAX_INDEX_BYTES);
            if (!json.isEmpty()) return new JSONObject(json);
        } catch (Throwable ignored) { }
        JSONObject root = new JSONObject();
        try { root.put("items", new JSONArray()); } catch (Throwable ignored) { }
        return root;
    }

    private static void writeIndex(Context context, JSONObject root) throws Exception {
        File file = new File(context.getFilesDir(), INDEX_PATH);
        LearningRemoteContent.atomicWrite(file, root.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) throw new IllegalArgumentException("The file is too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String first(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    static final class ImportResult {
        final String packId;
        final String title;
        final String subtitle;
        final String asset;
        final int count;

        ImportResult(String packId, String title, String subtitle, String asset, int count) {
            this.packId = packId;
            this.title = title;
            this.subtitle = subtitle;
            this.asset = asset;
            this.count = count;
        }
    }
}
