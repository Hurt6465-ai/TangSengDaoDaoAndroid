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

    public List<String> native_languages;
    public List<String> learning_languages;

    // 兼容旧字段/后端字符串字段。真正资料仍复用注册完善资料字段。
    public String native_language;
    public String learning_language;

    public List<String> getNativeLanguagesSafe() {
        return safeList(native_languages, native_language);
    }

    public List<String> getLearningLanguagesSafe() {
        return safeList(learning_languages, learning_language);
    }

    private List<String> safeList(List<String> list, String fallback) {
        if (list != null && !list.isEmpty()) return list;
        ArrayList<String> result = new ArrayList<>();
        if (fallback == null || fallback.trim().length() == 0) return result;
        String[] parts = fallback.split("[,，/\\s]+");
        for (String item : parts) {
            if (item != null && item.trim().length() > 0) result.add(item.trim());
        }
        return result;
    }
}
