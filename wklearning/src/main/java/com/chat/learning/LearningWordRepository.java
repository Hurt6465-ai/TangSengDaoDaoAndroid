package com.chat.learning;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads bundled preview packs and refreshes official remote packs into persistent local cache. */
final class LearningWordRepository {
    private static final int MAX_PACK_BYTES = 12 * 1024 * 1024;

    interface Callback {
        void onLoaded(List<WordItem> words, boolean refreshed);
        void onError(Throwable error);
    }

    private LearningWordRepository() {}

    static List<WordItem> loadLocal(Context context, String packId) {
        String safePack = LearningRemoteContent.safeFileName(packId);
        File cached = new File(context.getFilesDir(), "learning/words/" + safePack + ".json");
        try {
            String cachedJson = LearningRemoteContent.readFile(cached, MAX_PACK_BYTES);
            if (cachedJson.length() > 0) {
                List<WordItem> parsed = parse(packId, cachedJson);
                if (!parsed.isEmpty()) return parsed;
            }
        } catch (Throwable ignored) {}
        try {
            return parse(packId, LearningRemoteContent.readAsset(context, "learning/words/" + safePack + ".json"));
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    static void refresh(Context context, String packId, String rawUrl, String sha256, int version, Callback callback) {
        String resolved = LearningRemoteContent.resolveUrl(context, rawUrl);
        if (resolved.length() == 0) return;
        Context app = context.getApplicationContext();
        String safePack = LearningRemoteContent.safeFileName(packId);
        File target = new File(app.getFilesDir(), "learning/words/" + safePack + ".json");
        if (version > 0 && target.isFile()) {
            int stored = app.getSharedPreferences("tsdd_learning_content_versions", Context.MODE_PRIVATE)
                    .getInt("word." + safePack, 0);
            if (stored >= version) {
                try {
                    List<WordItem> cached = parse(packId, LearningRemoteContent.readFile(target, MAX_PACK_BYTES));
                    if (!cached.isEmpty()) return;
                } catch (Throwable ignored) {}
            }
        }
        LearningRemoteContent.execute(() -> {
            try {
                byte[] bytes = LearningRemoteContent.download(app, resolved, MAX_PACK_BYTES);
                if (!LearningRemoteContent.verifySha256(bytes, sha256)) {
                    throw new SecurityException("Word pack checksum mismatch");
                }
                String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                List<WordItem> parsed = parse(packId, json);
                if (parsed.isEmpty()) throw new IllegalStateException("Empty word pack");
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
            if (word.length() == 0) continue;
            JSONObject translations = item.optJSONObject("translations");
            JSONObject exampleTranslations = item.optJSONObject("exampleTranslations");
            String meaningMy = first(item.optString("meaning_my", ""),
                    translations == null ? "" : translations.optString("my", ""));
            String exampleMy = first(item.optString("example_my", ""),
                    exampleTranslations == null ? "" : exampleTranslations.optString("my", ""));
            String pinyin = PinyinUtils.resolve(word,
                    item.optString("pinyin_override", ""), item.optString("pinyin", ""));
            String exampleText = item.optString("example", "");
            String examplePinyin = PinyinUtils.resolve(exampleText,
                    item.optString("example_pinyin_override", ""), item.optString("example_pinyin", ""));
            result.add(new WordItem(
                    packId,
                    item.optString("id", packId + "_" + i),
                    word,
                    pinyin,
                    item.optString("phonetic_my", ""),
                    first(item.optString("part_of_speech", ""), item.optString("part_of_speech_my", "")),
                    meaningMy,
                    first(item.optString("usage_scene_my", ""), item.optString("usage_scene", "")),
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

    private static List<String> strings(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();
        ArrayList<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (value.length() > 0) values.add(value);
        }
        return values;
    }

    private static String first(String first, String second) {
        return first != null && first.trim().length() > 0 ? first.trim()
                : second == null ? "" : second.trim();
    }
}
