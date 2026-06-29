package com.chat.partnerbrowse.model;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Response for GET /v1/partners/profile/me.
 * This endpoint is used only as the partner-browse gate. Do not use /users/{uid}
 * for this check because /users/{uid} is a generic user profile and can treat
 * normal profile fields as complete partner data.
 */
public class PartnerBrowseProfileMe {
    public boolean has_partner_photo;
    public Object profile_images;
    public Object native_languages;
    public Object learning_languages;
    public Object tags;
    public String profile_cover;

    public boolean hasPartnerPhoto() {
        return has_partner_photo || !safeImageList(profile_images).isEmpty();
    }

    public boolean hasPartnerLanguages() {
        return !safeStringList(native_languages, 5).isEmpty() && !safeStringList(learning_languages, 5).isEmpty();
    }

    private List<String> safeStringList(Object value, int max) {
        ArrayList<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) addTextValue(result, item);
        } else if (value != null) {
            addTextValue(result, value);
        }
        ArrayList<String> out = dedupe(result);
        if (max > 0 && out.size() > max) return new ArrayList<>(out.subList(0, max));
        return out;
    }

    private List<String> safeImageList(Object value) {
        ArrayList<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) addImageValue(result, item);
        } else if (value != null) {
            addImageValue(result, value);
        }
        return dedupe(result);
    }

    private void addImageValue(ArrayList<String> result, Object value) {
        if (value == null) return;
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object display = firstMapValue(map, "display_url", "display", "url", "path", "thumb_url", "origin_url");
            if (display != null && !TextUtils.isEmpty(String.valueOf(display).trim())) {
                result.add(String.valueOf(display).trim());
            }
            return;
        }
        addTextValue(result, value);
    }

    private void addTextValue(ArrayList<String> result, Object value) {
        if (value == null) return;
        String text = String.valueOf(value).trim();
        if (TextUtils.isEmpty(text) || "null".equalsIgnoreCase(text)) return;
        String clean = text.replace("[", " ").replace("]", " ").replace("\"", " ");
        String[] parts = clean.split("[,，;；、\\s]+");
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) result.add(part.trim());
        }
    }

    private Object firstMapValue(Map<?, ?> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !TextUtils.isEmpty(String.valueOf(value).trim())) return value;
        }
        return null;
    }

    private ArrayList<String> dedupe(List<String> source) {
        ArrayList<String> out = new ArrayList<>();
        if (source == null) return out;
        for (String item : source) {
            if (TextUtils.isEmpty(item)) continue;
            if (!out.contains(item)) out.add(item);
        }
        return out;
    }
}
