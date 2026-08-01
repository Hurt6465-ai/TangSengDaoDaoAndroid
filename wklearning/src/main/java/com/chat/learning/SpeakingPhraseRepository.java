package com.chat.learning;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads a small bundled speaking pack. The schema is ready for remote replacement later. */
final class SpeakingPhraseRepository {
    private SpeakingPhraseRepository() {}

    static Pack load(Context context, String assetPath, String fallbackPackId, String fallbackTitle) {
        String normalized = SpeakingImportedPackStore.isImportedAsset(assetPath)
                ? safe(assetPath) : normalizeAsset(assetPath);
        try {
            String json = SpeakingImportedPackStore.isImportedAsset(normalized)
                    ? SpeakingImportedPackStore.read(context, normalized)
                    : LearningRemoteContent.readAsset(context, normalized);
            return parse(context, json, fallbackPackId, fallbackTitle, normalized);
        } catch (Throwable ignored) {
            return new Pack(safe(fallbackPackId), safe(fallbackTitle), "", normalized,
                    Collections.emptyList());
        }
    }

    static Pack parse(Context context, String json, String fallbackPackId, String fallbackTitle,
                      String assetPath) throws Exception {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        String packId = root.optString("pack_id", fallbackPackId);
        String title = localized(context, root, "title", fallbackTitle);
        String subtitle = localized(context, root, "subtitle", "");
        JSONArray array = root.optJSONArray("phrases");
        ArrayList<SpeakingPhrase> phrases = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String text = item.optString("text", "").trim();
                if (text.length() == 0) continue;
                phrases.add(new SpeakingPhrase(
                        packId,
                        item.optString("id", packId + "_" + (i + 1)),
                        text,
                        item.optString("pinyin", ""),
                        item.optString("tts_pinyin", ""),
                        item.optString("meaning_my", ""),
                        item.optString("scene", ""),
                        item.optString("scene_my", ""),
                        item.optString("scene_en", ""),
                        parseBreakdown(item, text, item.optString("pinyin", "")),
                        parseVariants(context, item.optJSONArray("replacements")),
                        parseVariants(context, item.optJSONArray("alternatives"))
                ));
            }
        }
        return new Pack(safe(packId), safe(title), safe(subtitle), assetPath,
                Collections.unmodifiableList(phrases));
    }


    private static List<SpeakingPhrase.Breakdown> parseBreakdown(
            JSONObject item, String sentence, String pinyin) {
        JSONArray array = item.optJSONArray("breakdown");
        ArrayList<SpeakingPhrase.Breakdown> result = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject part = array.optJSONObject(i);
                if (part == null) continue;
                String text = part.optString("text", "").trim();
                if (text.length() == 0) continue;
                result.add(new SpeakingPhrase.Breakdown(
                        text,
                        part.optString("pinyin", ""),
                        part.optString("meaning_my", "")
                ));
            }
        }
        if (!result.isEmpty()) return result;
        return fallbackBreakdown(sentence, pinyin);
    }

    private static final String[] BREAKDOWN_WORDS = new String[]{
            "有什么问题", "工作经验", "工作时间", "什么时候", "请告诉我", "很高兴",
            "便宜一点", "再说一遍", "听不懂", "已经", "做完了", "还需要", "一个月",
            "水电费", "看一下", "修一下", "慢一点", "开始工作", "请给我", "少放一点",
            "想租房", "想要", "多少钱", "多少", "一点", "这个", "那个", "一份", "一杯",
            "买单", "谢谢", "叫什么", "名字", "认识", "哪国人", "缅甸人", "中文", "请说",
            "应聘", "两年", "工作", "马上", "开始", "工资", "几点", "需要", "准备", "今天",
            "什么", "怎么", "明天", "请假", "押金", "怎么算", "房子", "这里", "网络", "入住",
            "坏了", "帮我", "试一下", "大一点", "别的", "颜色", "现金", "扫码", "不要",
            "少放", "菜单", "可以", "告诉我"
    };

    private static List<SpeakingPhrase.Breakdown> fallbackBreakdown(
            String sentence, String pinyin) {
        ArrayList<SpeakingPhrase.Breakdown> result = new ArrayList<>();
        String clean = safe(sentence).replaceAll("[\\p{Punct}，。！？、；：…]", "");
        String pyClean = safe(pinyin).replace('，', ' ').replace('。', ' ')
                .replace('？', ' ').replace('！', ' ').trim();
        String[] syllables = pyClean.length() == 0 ? new String[0] : pyClean.split("\\s+");
        int syllableIndex = 0;
        int offset = 0;
        while (offset < clean.length()) {
            String token = matchBreakdownWord(clean, offset);
            if (token.length() == 0) {
                int codePoint = clean.codePointAt(offset);
                token = new String(Character.toChars(codePoint));
            }
            int charCount = token.codePointCount(0, token.length());
            StringBuilder py = new StringBuilder();
            for (int i = 0; i < charCount && syllableIndex < syllables.length; i++) {
                if (py.length() > 0) py.append(' ');
                py.append(syllables[syllableIndex++]);
            }
            result.add(new SpeakingPhrase.Breakdown(token, py.toString(), ""));
            offset += token.length();
        }
        return result;
    }

    private static String matchBreakdownWord(String sentence, int offset) {
        for (String word : BREAKDOWN_WORDS) {
            if (sentence.startsWith(word, offset)) return word;
        }
        return "";
    }

    private static List<SpeakingPhrase.Variant> parseVariants(Context context, JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();
        ArrayList<SpeakingPhrase.Variant> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (raw instanceof String) {
                result.add(new SpeakingPhrase.Variant((String) raw, "", "", "", ""));
                continue;
            }
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String text = item.optString("text", "").trim();
            if (text.length() == 0) continue;
            result.add(new SpeakingPhrase.Variant(
                    text,
                    item.optString("pinyin", ""),
                    item.optString("tts_pinyin", ""),
                    item.optString("meaning_my", ""),
                    localized(context, item, "label", "")
            ));
        }
        return result;
    }


    private static String localized(Context context, JSONObject object, String key, String fallback) {
        String suffix = localeSuffix(context);
        String value = suffix.length() == 0 ? "" : object.optString(key + suffix, "").trim();
        if (value.length() == 0) value = object.optString(key, fallback).trim();
        return value.length() == 0 ? safe(fallback) : value;
    }

    private static String localeSuffix(Context context) {
        java.util.Locale locale;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            locale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = context.getResources().getConfiguration().locale;
        }
        String language = locale == null ? "" : locale.getLanguage();
        if ("my".equalsIgnoreCase(language)) return "_my";
        if ("en".equalsIgnoreCase(language)) return "_en";
        return "";
    }

    private static String normalizeAsset(String path) {
        String value = safe(path);
        while (value.startsWith("/")) value = value.substring(1);
        if (value.startsWith("assets/")) value = value.substring("assets/".length());
        if (value.length() == 0) value = "learning/speaking/hello.json";
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Pack {
        final String id;
        final String title;
        final String subtitle;
        final String assetPath;
        final List<SpeakingPhrase> phrases;

        Pack(String id, String title, String subtitle, String assetPath,
             List<SpeakingPhrase> phrases) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.assetPath = assetPath;
            this.phrases = phrases == null ? Collections.emptyList() : phrases;
        }
    }
}
