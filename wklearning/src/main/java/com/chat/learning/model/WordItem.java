package com.chat.learning.model;

import java.util.HashMap;
import java.util.Map;

/** 词库内容模型。内容来自 assets/远程 JSON，不进 Room。 */
public class WordItem {
    public String id;
    public String categoryId;
    public String word;
    public String pinyin;
    public String example;
    public String examplePinyin;
    public String audio;
    public String level;
    public final Map<String, String> translations = new HashMap<>();
    public final Map<String, String> exampleTranslations = new HashMap<>();

    public String translationFor(String lang) {
        if (lang != null && translations.containsKey(lang)) return translations.get(lang);
        if (translations.containsKey("my")) return translations.get("my");
        if (translations.containsKey("en")) return translations.get("en");
        return "";
    }
}
