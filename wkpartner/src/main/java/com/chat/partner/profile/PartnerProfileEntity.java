package com.chat.partner.profile;

import java.util.ArrayList;
import java.util.List;

public class PartnerProfileEntity {
    public String uid;
    public String name;
    public String username;
    public String avatar;
    public String avatar_cache_key;
    public String country_code;
    public String country;
    public int sex = -1;
    public int age;
    public String birthday;
    public String intro;
    public String category;
    public String role;
    public int follow;
    public int status;
    public String vercode;

    // 后端 user 表里这些字段可能是字符串，也可能后续改成数组。用 Object 兼容两种返回。
    public Object native_languages;
    public Object learning_languages;
    public Object tags;
    public String profile_cover;
    public Object profile_images;

    // 兼容旧字段/注册旧版本字段。
    public String native_language;
    public String learning_language;

    public List<String> getNativeLanguagesSafe() {
        return safeList(native_languages, native_language);
    }

    public List<String> getLearningLanguagesSafe() {
        return safeList(learning_languages, learning_language);
    }

    public List<String> getTagsSafe() {
        return safeList(tags, "");
    }

    public List<String> getProfileImagesSafe() {
        return safeList(profile_images, "");
    }

    @SuppressWarnings("unchecked")
    private List<String> safeList(Object value, String fallback) {
        ArrayList<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                addSplitItems(result, item == null ? "" : String.valueOf(item));
            }
            if (!result.isEmpty()) return result;
        } else if (value != null) {
            addSplitItems(result, String.valueOf(value));
            if (!result.isEmpty()) return result;
        }
        addSplitItems(result, fallback);
        return result;
    }

    private void addSplitItems(ArrayList<String> out, String text) {
        if (text == null) return;
        String clean = text.trim();
        if (clean.length() == 0 || "null".equalsIgnoreCase(clean)) return;
        clean = clean.replace("[", " ").replace("]", " ").replace("\"", " ");
        String[] parts = clean.split("[,，/\\s]+");
        for (String item : parts) {
            if (item != null && item.trim().length() > 0) out.add(item.trim());
        }
    }
}
