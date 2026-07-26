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
        String normalized = normalizeAsset(assetPath);
        try {
            String json = LearningRemoteContent.readAsset(context, normalized);
            return parse(context, json, fallbackPackId, fallbackTitle, normalized);
        } catch (Throwable ignored) {
            return new Pack(safe(fallbackPackId), safe(fallbackTitle), "", normalized,
                    Collections.emptyList());
        }
    }

    private static Pack parse(Context context, String json, String fallbackPackId, String fallbackTitle,
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
                        parseVariants(context, item.optJSONArray("replacements")),
                        parseVariants(context, item.optJSONArray("alternatives"))
                ));
            }
        }
        return new Pack(safe(packId), safe(title), safe(subtitle), assetPath,
                Collections.unmodifiableList(phrases));
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
