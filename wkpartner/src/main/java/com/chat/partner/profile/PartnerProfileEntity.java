package com.chat.partner.profile;

import java.util.ArrayList;
import java.util.List;

/**
 * 语伴个人主页数据。
 * 字段和后端 /v1/users/{uid}、/v1/user/current 保持松耦合：
 * 当前后端没有返回的字段会自动为空，后端以后补字段后页面直接显示。
 */
public class PartnerProfileEntity {
    public String uid;
    public String name;
    public String username;
    public String avatar;
    public String avatar_cache_key;
    public String country_code;
    public String country;
    public int sex = -1; // 1 男，0 女，其他保密
    public int age;
    public String birthday;
    public String intro;
    public String profile_cover;
    public String category;
    public String role;
    public int follow;
    public int status;
    public String vercode;

    public List<String> native_languages;
    public List<String> learning_languages;
    public List<String> tags;
    public List<String> profile_images;

    // 兼容部分后端可能返回单字符串
    public String native_language;
    public String learning_language;

    public List<String> getNativeLanguagesSafe() {
        return safeList(native_languages, native_language);
    }

    public List<String> getLearningLanguagesSafe() {
        return safeList(learning_languages, learning_language);
    }

    public List<String> getTagsSafe() {
        return tags == null ? new ArrayList<>() : tags;
    }

    private List<String> safeList(List<String> list, String fallback) {
        if (list != null && !list.isEmpty()) return list;
        List<String> result = new ArrayList<>();
        if (fallback != null && fallback.trim().length() > 0) {
            String[] parts = fallback.split("[,，/\\s]+");
            for (String item : parts) {
                if (item != null && item.trim().length() > 0) result.add(item.trim());
            }
        }
        return result;
    }
}
