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
    // status 是当前用户与对方的关系状态：1 正常，2 已拉黑；不是在线状态。
    public int status;
    public int online;
    public long last_offline;
    public int device_flag;
    public int is_upload_avatar;
    public int be_deleted;
    public int be_blacklist;
    public String last_online;
    public String last_online_time;
    public String last_seen;
    public String last_seen_at;
    public String last_active_at;
    public String last_active_time;
    public String last_login_at;
    public String last_login_time;
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
        return safePathList(profile_images);
    }


    @SuppressWarnings("unchecked")
    private List<String> safePathList(Object value) {
        ArrayList<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) addPathItem(result, item == null ? "" : String.valueOf(item));
            return result;
        }
        if (value == null) return result;
        String clean = String.valueOf(value).trim();
        if (clean.length() == 0 || "null".equalsIgnoreCase(clean)) return result;
        if (clean.startsWith("[")) {
            clean = clean.replace("[", "").replace("]", "");
            String[] parts = clean.split(",");
            for (String part : parts) addPathItem(result, part);
        } else {
            String[] parts = clean.split("[,，\\s]+");
            for (String part : parts) addPathItem(result, part);
        }
        return result;
    }

    private void addPathItem(ArrayList<String> out, String text) {
        if (text == null) return;
        String item = text.trim();
        while (item.startsWith("\"") || item.startsWith("'")) item = item.substring(1).trim();
        while (item.endsWith("\"") || item.endsWith("'")) item = item.substring(0, item.length() - 1).trim();
        if (item.length() == 0 || "null".equalsIgnoreCase(item)) return;
        if (!out.contains(item)) out.add(item);
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
