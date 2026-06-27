package com.chat.partnerbrowse.model;

import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fullscreen partner-flow item. Kept inside wkpartnerbrowse so it does not collide with the
 * existing wkpartner personal-profile module.
 */
public class PartnerBrowseBean {
    public String id;
    public String uid;
    public String name;
    public String username;
    public String intro;
    public String avatar;
    public String avatar_cache_key;
    public String country_code;
    public String country;
    public String birthday;
    public int age;
    public int sex;
    public int follow;
    public String vercode;
    public String profile_cover;
    public Object profile_images;
    public Object native_languages;
    public Object learning_languages;
    public Object tags;
    public Object images;
    public double distance_km;
    public int distance_meters;
    public int online;
    public int status;
    public long last_active_millis;
    public long last_online_millis;
    public double server_score;
    public double score;
    public String recommend_reason;
    public boolean hello_sent;
    public int apply_status;
    public int greeting_status;

    public String getStableKey() {
        if (!TextUtils.isEmpty(uid)) return uid;
        if (!TextUtils.isEmpty(id)) return id;
        String fallback = getNameSafe() + "|" + avatar + "|" + profile_cover;
        return TextUtils.isEmpty(fallback.trim()) ? String.valueOf(hashCode()) : fallback;
    }

    public String getNameSafe() {
        if (!TextUtils.isEmpty(name)) return name;
        if (!TextUtils.isEmpty(username)) return username;
        if (!TextUtils.isEmpty(uid)) return uid;
        if (!TextUtils.isEmpty(id)) return id;
        return "";
    }

    public boolean isHelloSent() {
        return hello_sent || apply_status == 1 || greeting_status == 1;
    }

    public void markHelloSent() {
        hello_sent = true;
        if (apply_status == 0) apply_status = 1;
        if (greeting_status == 0) greeting_status = 1;
    }

    public int getDistanceMetersSafe() {
        if (distance_meters > 0) return distance_meters;
        if (distance_km > 0) return (int) Math.round(distance_km * 1000d);
        return 0;
    }

    public String getNearbyLabel() {
        int meters = getDistanceMetersSafe();
        if (meters <= 0 || meters > 70000) return "";
        if (meters <= 5000) return "5km内";
        if (meters <= 10000) return "10km内";
        if (meters <= 30000) return "30km内";
        return "70km内";
    }

    public List<String> getNativeLanguagesSafe() {
        return safeStringList(native_languages, 5);
    }

    public List<String> getLearningLanguagesSafe() {
        return safeStringList(learning_languages, 5);
    }

    public List<String> getTagsSafe() {
        return safeStringList(tags, 20);
    }

    public List<String> getProfileImagesSafe() {
        return safeStringList(profile_images, 9);
    }

    public List<String> getDisplayImagesSafe() {
        ArrayList<String> out = new ArrayList<>();
        addAll(out, safeImageList(images));
        addAll(out, getProfileImagesSafe());
        if (!TextUtils.isEmpty(profile_cover)) out.add(profile_cover);
        if (!TextUtils.isEmpty(avatar)) out.add(avatar);
        ArrayList<String> deduped = dedupe(out);
        if (deduped.isEmpty()) deduped.add("");
        return deduped;
    }

    public Bundle toBundle(String stableKey) {
        Bundle args = new Bundle();
        args.putString("stable_key", stableKey == null ? getStableKey() : stableKey);
        args.putString("uid", safe(uid));
        args.putString("id", safe(id));
        args.putString("name", safe(name));
        args.putString("username", safe(username));
        args.putString("intro", safe(intro));
        args.putString("avatar", safe(avatar));
        args.putString("avatar_cache_key", safe(avatar_cache_key));
        args.putString("country_code", safe(country_code));
        args.putString("country", safe(country));
        args.putString("birthday", safe(birthday));
        args.putInt("age", age);
        args.putInt("sex", sex);
        args.putInt("follow", follow);
        args.putString("vercode", safe(vercode));
        args.putString("profile_cover", safe(profile_cover));
        args.putInt("distance_meters", getDistanceMetersSafe());
        args.putBoolean("hello_sent", hello_sent);
        args.putInt("apply_status", apply_status);
        args.putInt("greeting_status", greeting_status);
        args.putStringArrayList("images", new ArrayList<>(getDisplayImagesSafe()));
        args.putStringArrayList("native_languages", new ArrayList<>(getNativeLanguagesSafe()));
        args.putStringArrayList("learning_languages", new ArrayList<>(getLearningLanguagesSafe()));
        args.putStringArrayList("tags", new ArrayList<>(getTagsSafe()));
        return args;
    }

    public static PartnerBrowseBean fromBundle(Bundle args) {
        if (args == null) return null;
        PartnerBrowseBean bean = new PartnerBrowseBean();
        bean.uid = args.getString("uid", "");
        bean.id = args.getString("id", "");
        bean.name = args.getString("name", "");
        bean.username = args.getString("username", "");
        bean.intro = args.getString("intro", "");
        bean.avatar = args.getString("avatar", "");
        bean.avatar_cache_key = args.getString("avatar_cache_key", "");
        bean.country_code = args.getString("country_code", "");
        bean.country = args.getString("country", "");
        bean.birthday = args.getString("birthday", "");
        bean.age = args.getInt("age", 0);
        bean.sex = args.getInt("sex", 0);
        bean.follow = args.getInt("follow", 0);
        bean.vercode = args.getString("vercode", "");
        bean.profile_cover = args.getString("profile_cover", "");
        bean.distance_meters = args.getInt("distance_meters", 0);
        bean.hello_sent = args.getBoolean("hello_sent", false);
        bean.apply_status = args.getInt("apply_status", 0);
        bean.greeting_status = args.getInt("greeting_status", 0);
        bean.images = args.getStringArrayList("images");
        bean.native_languages = args.getStringArrayList("native_languages");
        bean.learning_languages = args.getStringArrayList("learning_languages");
        bean.tags = args.getStringArrayList("tags");
        return TextUtils.isEmpty(bean.uid) && TextUtils.isEmpty(bean.id) && TextUtils.isEmpty(bean.name) ? null : bean;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
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
            if (display != null && !TextUtils.isEmpty(String.valueOf(display).trim())) result.add(String.valueOf(display).trim());
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
        for (String part : parts) if (!TextUtils.isEmpty(part)) result.add(part.trim());
    }

    private Object firstMapValue(Map<?, ?> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !TextUtils.isEmpty(String.valueOf(value).trim())) return value;
        }
        return null;
    }

    private void addAll(ArrayList<String> out, List<String> source) {
        if (source == null) return;
        for (String item : source) if (!TextUtils.isEmpty(item)) out.add(item);
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
