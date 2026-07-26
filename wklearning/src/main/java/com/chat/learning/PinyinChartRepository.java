package com.chat.learning;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads the bundled pinyin chart and its local audio asset paths. */
final class PinyinChartRepository {
    private static final String ASSET_PATH = "learning/pinyin/chart.json";

    private PinyinChartRepository() { }

    static Chart load(Context context) {
        try {
            String json = LearningRemoteContent.readAsset(context, ASSET_PATH);
            JSONObject root = new JSONObject(json);
            Chart chart = new Chart();
            JSONArray sections = root.optJSONArray("sections");
            if (sections != null) {
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject source = sections.optJSONObject(i);
                    if (source == null) continue;
                    Section section = new Section();
                    section.id = source.optString("id", "section_" + i);
                    section.title = source.optString("title", "");
                    section.subtitle = source.optString("subtitle", "");
                    JSONArray items = source.optJSONArray("items");
                    if (items != null) {
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject value = items.optJSONObject(j);
                            if (value == null) continue;
                            Item item = new Item();
                            item.letter = value.optString("letter", "");
                            item.hint = value.optString("hint", "");
                            if ("tones".equals(section.id)) item.hint = stripToneNumber(item.hint);
                            item.audioAsset = value.optString("audio", "");
                            if (!item.letter.isEmpty()) section.items.add(item);
                        }
                    }
                    if (!section.items.isEmpty()) chart.sections.add(section);
                }
            }
            return chart;
        } catch (Throwable ignored) {
            return new Chart();
        }
    }

    private static String stripToneNumber(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replaceFirst("\\s*[·•]?\\s*[①②③④1-4１-４]\\s*$", "").trim();
    }

    static int findSectionIndex(Chart chart, String requested) {
        if (chart == null || chart.sections.isEmpty()) return 0;
        String normalized = normalizeSectionId(requested);
        for (int i = 0; i < chart.sections.size(); i++) {
            if (normalized.equals(chart.sections.get(i).id)) return i;
        }
        return 0;
    }

    static String normalizeSectionId(String value) {
        if (value == null) return "initials";
        if ("tone".equals(value) || "tones".equals(value)) return "tones";
        if ("whole".equals(value) || "whole_syllables".equals(value)) return "whole";
        if ("final".equals(value) || "finals".equals(value)) return "finals";
        return "initials";
    }

    static final class Chart {
        final List<Section> sections = new ArrayList<>();

        List<Section> sections() {
            return Collections.unmodifiableList(sections);
        }
    }

    static final class Section {
        String id;
        String title;
        String subtitle;
        final List<Item> items = new ArrayList<>();
    }

    static final class Item {
        String letter;
        String hint;
        String audioAsset;
    }
}
