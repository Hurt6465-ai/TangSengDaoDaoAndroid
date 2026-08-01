package com.chat.learning;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads bundled packs, remote caches, imported packs and saved-word collections. */
final class LearningWordRepository {
    private static final int MAX_PACK_BYTES = 12 * 1024 * 1024;

    interface Callback {
        void onLoaded(List<WordItem> words, boolean refreshed);
        void onError(Throwable error);
    }

    private LearningWordRepository() {}

    static List<WordItem> loadLocal(Context context, String packId) {
        return loadLocal(context, packId, 0);
    }

    static List<WordItem> loadLocal(Context context, String packId, int expectedCount) {
        String safePack = LearningRemoteContent.safeFileName(packId);
        File cachedFile = new File(context.getFilesDir(), "learning/words/" + safePack + ".json");

        String bundledJson = "";
        List<WordItem> bundledWords = Collections.emptyList();
        int bundledVersion = 0;
        try {
            bundledJson = LearningRemoteContent.readAsset(
                    context, "learning/words/" + safePack + ".json");
            bundledVersion = packVersion(bundledJson);
            bundledWords = parse(packId, bundledJson);
        } catch (Throwable ignored) { }

        String cachedJson = "";
        List<WordItem> cachedWords = Collections.emptyList();
        int cachedVersion = 0;
        try {
            cachedJson = LearningRemoteContent.readFile(cachedFile, MAX_PACK_BYTES);
            if (!cachedJson.isEmpty()) {
                cachedVersion = packVersion(cachedJson);
                cachedWords = parse(packId, cachedJson);
            }
        } catch (Throwable ignored) { }

        boolean bundledComplete = isComplete(bundledWords, expectedCount);
        boolean cachedComplete = isComplete(cachedWords, expectedCount);

        if (!bundledWords.isEmpty() && !cachedWords.isEmpty()) {
            if (bundledVersion > cachedVersion && bundledComplete) return bundledWords;
            if (cachedVersion > bundledVersion && cachedComplete) return cachedWords;

            if (bundledVersion == cachedVersion) {
                if (bundledComplete && (!cachedComplete || bundledWords.size() > cachedWords.size())) {
                    // Old demo caches may carry the same version number but contain only a few words.
                    LearningRemoteContent.deleteQuietly(cachedFile);
                    context.getSharedPreferences("tsdd_learning_content_versions", Context.MODE_PRIVATE)
                            .edit().remove("word." + safePack).apply();
                    return bundledWords;
                }
                if (cachedComplete) return cachedWords;
                if (bundledComplete) return bundledWords;
            }
        }

        if (cachedComplete) return cachedWords;
        if (bundledComplete) return bundledWords;

        // When a catalog promises more words than the incomplete cache contains, return empty so
        // the screen can download the complete remote pack instead of silently showing a demo pack.
        if (expectedCount > 0) return Collections.emptyList();
        if (!cachedWords.isEmpty()) return cachedWords;
        return bundledWords;
    }

    static List<WordItem> loadFavorites(Context context, WordProgressStore progressStore) {
        Map<String, Set<String>> favorites = progressStore.favoriteIds();
        Set<String> legacyIds = progressStore.legacyFavoriteWordIds();
        if (favorites.isEmpty() && legacyIds.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> packIds = new LinkedHashSet<>(favorites.keySet());
        if (!legacyIds.isEmpty()) {
            LearningCatalogRepository.Catalog catalog =
                    LearningCatalogRepository.load(context, "words");
            collectWordPackIds(catalog == null ? null : catalog.items, packIds);
        }

        ArrayList<WordItem> result = new ArrayList<>();
        for (String packId : packIds) {
            Set<String> ids = favorites.get(packId);
            List<WordItem> words = loadLocal(context, packId, 0);
            for (WordItem item : words) {
                boolean savedInDatabase = ids != null && ids.contains(item.id);
                boolean savedInLegacy = legacyIds.contains(item.id)
                        && progressStore.isFavorite(item.packId, item.id);
                if (savedInDatabase || savedInLegacy) result.add(item);
            }
        }
        result.sort(Comparator.comparing((WordItem item) -> item.packId)
                .thenComparing(item -> item.id));
        return result;
    }

    private static void collectWordPackIds(List<LearningCatalogRepository.Node> nodes,
                                           Set<String> output) {
        if (nodes == null || output == null) return;
        for (LearningCatalogRepository.Node node : nodes) {
            if (node == null) continue;
            if ("word".equals(node.target)) {
                output.add(first(node.level, node.id));
            }
            collectWordPackIds(node.children, output);
        }
    }

    static void refresh(Context context, String packId, String rawUrl, String sha256,
                        int version, int expectedCount, Callback callback) {
        String resolved = LearningRemoteContent.resolveUrl(context, rawUrl);
        if (resolved.isEmpty()) return;
        Context app = context.getApplicationContext();
        String safePack = LearningRemoteContent.safeFileName(packId);
        File target = new File(app.getFilesDir(), "learning/words/" + safePack + ".json");
        if (version > 0 && target.isFile()) {
            int stored = app.getSharedPreferences("tsdd_learning_content_versions", Context.MODE_PRIVATE)
                    .getInt("word." + safePack, 0);
            if (stored >= version) {
                try {
                    String cachedJson = LearningRemoteContent.readFile(target, MAX_PACK_BYTES);
                    List<WordItem> cached = parse(packId, cachedJson);
                    if (isComplete(cached, expectedCount) && packVersion(cachedJson) >= version) return;
                } catch (Throwable ignored) { }
            }
        }
        LearningRemoteContent.execute(() -> {
            try {
                byte[] bytes = LearningRemoteContent.download(app, resolved, MAX_PACK_BYTES);
                if (!LearningRemoteContent.verifySha256(bytes, sha256)) {
                    throw new SecurityException("Word pack checksum mismatch");
                }
                String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                int downloadedVersion = packVersion(json);
                if (version > 0 && downloadedVersion > 0 && downloadedVersion < version) {
                    throw new IllegalStateException("Downloaded word pack is older than catalog version");
                }
                List<WordItem> parsed = parse(packId, json);
                if (parsed.isEmpty()) throw new IllegalStateException("Empty word pack");
                if (!isComplete(parsed, expectedCount)) {
                    throw new IllegalStateException("Incomplete word pack: expected "
                            + expectedCount + ", got " + parsed.size());
                }
                LearningRemoteContent.atomicWrite(target, bytes);
                if (version > 0) {
                    app.getSharedPreferences("tsdd_learning_content_versions", Context.MODE_PRIVATE)
                            .edit().putInt("word." + safePack, version).apply();
                }
                if (callback != null) callback.onLoaded(parsed, true);
            } catch (Throwable error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    static void refresh(Context context, String packId, String rawUrl, String sha256,
                        int version, Callback callback) {
        refresh(context, packId, rawUrl, sha256, version, 0, callback);
    }

    static List<WordItem> parse(String fallbackPackId, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        String packId = root.optString("categoryId", root.optString("pack_id", fallbackPackId));
        JSONArray items = root.optJSONArray("items");
        ArrayList<WordItem> result = new ArrayList<>();
        if (items == null) return result;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String word = item.optString("word", "").trim();
            if (word.isEmpty()) continue;
            JSONObject translations = item.optJSONObject("translations");
            JSONObject exampleTranslations = item.optJSONObject("exampleTranslations");
            String meaningMy = first(item.optString("meaning_my", ""),
                    translations == null ? "" : translations.optString("my", ""));
            String exampleMy = first(item.optString("example_my", ""),
                    exampleTranslations == null ? "" : exampleTranslations.optString("my", ""));
            JSONObject memoryTipObject = item.optJSONObject("memory_tip");
            String memoryTipMy = first(item.optString("memory_tip_my", ""),
                    memoryTipObject == null ? "" : memoryTipObject.optString("my", ""));
            String memoryTipZh = first(item.optString("memory_tip_zh", ""),
                    memoryTipObject == null ? "" : memoryTipObject.optString("zh", ""));
            String memoryTip = first(memoryTipMy, memoryTipZh);
            String pinyin = PinyinUtils.resolve(word,
                    item.optString("pinyin_override", ""), item.optString("pinyin", ""));
            String ttsPinyin = PinyinUtils.resolveForSpeech(word,
                    item.optString("tts_pinyin_override", ""), pinyin);
            String exampleText = item.optString("example", "");
            String examplePinyin = PinyinUtils.resolve(exampleText,
                    item.optString("example_pinyin_override", ""), item.optString("example_pinyin", ""));
            result.add(new WordItem(
                    packId,
                    item.optString("id", packId + "_" + i),
                    word,
                    pinyin,
                    ttsPinyin,
                    item.optString("phonetic_my", ""),
                    first(item.optString("part_of_speech", ""), item.optString("part_of_speech_my", "")),
                    meaningMy,
                    first(item.optString("usage_scene_my", ""), item.optString("usage_scene", "")),
                    memoryTip,
                    exampleText,
                    examplePinyin,
                    exampleMy,
                    first(item.optString("notes_my", ""), item.optString("notes", "")),
                    strings(item.optJSONArray("synonyms")),
                    strings(item.optJSONArray("antonyms")),
                    strings(item.optJSONArray("collocations")),
                    item.optString("audio_override", item.optString("audio", "")),
                    item.optString("example_audio_override", "")
            ));
        }
        return result;
    }

    private static boolean isComplete(List<WordItem> words, int expectedCount) {
        if (words == null || words.isEmpty()) return false;
        return expectedCount <= 0 || words.size() >= expectedCount;
    }

    private static int packVersion(String json) {
        if (json == null || json.trim().isEmpty()) return 0;
        try {
            return new JSONObject(json).optInt("version", 0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static List<String> strings(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();
        ArrayList<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static String first(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first.trim()
                : second == null ? "" : second.trim();
    }
}
