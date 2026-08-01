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
import java.util.Set;

/** Imports user word books and exposes them as normal word-library catalog nodes. */
final class LearningImportedWordStore {
    private static final int MAX_IMPORT_BYTES = 12 * 1024 * 1024;
    private static final int MAX_INDEX_BYTES = 1024 * 1024;
    private static final String INDEX_PATH = "learning/imported_words/catalog.json";

    private LearningImportedWordStore() { }

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

        JSONObject root = new JSONObject(raw);
        JSONArray items = root.optJSONArray("items");
        if (items == null || items.length() == 0) {
            throw new IllegalArgumentException("The word book has no items");
        }

        String sourceId = first(root.optString("categoryId", ""), root.optString("pack_id", ""));
        if (sourceId.isEmpty()) sourceId = "words_" + System.currentTimeMillis();
        String safeSource = LearningRemoteContent.safeFileName(sourceId);
        String packId = safeSource.startsWith("user_") ? safeSource : "user_" + safeSource;
        String title = first(root.optString("title", ""), sourceId);
        int version = Math.max(1, root.optInt("version", 1));

        root.put("categoryId", packId);
        root.put("title", title);
        root.put("version", version);

        Set<String> usedIds = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String word = item.optString("word", "").trim();
            if (word.isEmpty()) continue;
            String rawItemId = item.optString("id", "").trim();
            String itemId = rawItemId.isEmpty()
                    ? String.format(java.util.Locale.US, "%s_%04d", packId, i + 1)
                    : LearningRemoteContent.safeFileName(rawItemId);
            String uniqueId = itemId;
            int duplicateIndex = 2;
            while (!usedIds.add(uniqueId)) {
                uniqueId = itemId + "_" + duplicateIndex++;
            }
            item.put("id", uniqueId);
            if (!item.has("order")) item.put("order", i + 1);
        }

        String normalized = root.toString(2);
        List<WordItem> parsed = LearningWordRepository.parse(packId, normalized);
        if (parsed.isEmpty()) throw new IllegalArgumentException("No valid words were found");

        File target = new File(context.getFilesDir(), "learning/words/" + packId + ".json");
        LearningRemoteContent.atomicWrite(target, normalized.getBytes(StandardCharsets.UTF_8));

        JSONObject meta = new JSONObject();
        meta.put("id", packId);
        meta.put("title", title);
        meta.put("subtitle", first(root.optString("subtitle", ""), parsed.size() + " 个导入单词"));
        meta.put("badge", parsed.size() + "词");
        meta.put("preview", preview(parsed));
        meta.put("version", version);
        meta.put("item_count", parsed.size());
        upsert(context, meta);

        return new ImportResult(packId, title, parsed.size());
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
            if (id.isEmpty()) continue;
            File data = new File(context.getFilesDir(), "learning/words/" + id + ".json");
            if (!data.isFile()) continue;
            LearningCatalogRepository.Node node = new LearningCatalogRepository.Node();
            node.id = id;
            node.level = id;
            node.title = meta.optString("title", id);
            node.subtitle = meta.optString("subtitle", "");
            node.badge = meta.optString("badge", "导入");
            node.preview = meta.optString("preview", "");
            node.target = "word";
            node.dataVersion = Math.max(1, meta.optInt("version", 1));
            node.itemCount = Math.max(0, meta.optInt("item_count", 0));
            node.imported = true;
            result.add(node);
        }
        return result;
    }

    static boolean delete(Context context, String packId) {
        String safe = LearningRemoteContent.safeFileName(packId);
        File data = new File(context.getFilesDir(), "learning/words/" + safe + ".json");
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

    private static String preview(List<WordItem> items) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < items.size() && i < 3; i++) {
            if (out.length() > 0) out.append(" / ");
            out.append(items.get(i).word);
        }
        return out.toString();
    }

    private static String first(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        return second == null ? "" : second.trim();
    }

    static final class ImportResult {
        final String packId;
        final String title;
        final int count;

        ImportResult(String packId, String title, int count) {
            this.packId = packId;
            this.title = title;
            this.count = count;
        }
    }
}
