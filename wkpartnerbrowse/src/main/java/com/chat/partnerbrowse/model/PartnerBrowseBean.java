package com.chat.partnerbrowse.model;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chat.partnerbrowse.R;

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
    public long last_active_at;
    public long last_online;
    public long last_offline;
    public String role;
    public String category;
    public double server_score;
    public double score;
    public String recommend_reason;
    public boolean hello_sent;
    public int apply_status;
    public int greeting_status;
    public int requester_msg_count;
    public int max_greeting_count;
    public long next_allowed_at;

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

    public String getAvatarPathSafe() {
        // 语伴图片已改为账号头像：优先使用 uid 的稳定头像接口，
        // 不再让历史 profile_images 或旧服务器绝对地址覆盖它。
        if (!TextUtils.isEmpty(uid)) return "users/" + uid + "/avatar";
        if (!TextUtils.isEmpty(id)) return "users/" + id + "/avatar";
        return avatar == null ? "" : avatar.trim();
    }

    public boolean hasPartnerPhoto() {
        return !TextUtils.isEmpty(getAvatarPathSafe());
    }

    public boolean hasPartnerLanguages() {
        return !getNativeLanguagesSafe().isEmpty() && !getLearningLanguagesSafe().isEmpty();
    }

    public long getLastActiveMillisSafe() {
        if (last_active_millis > 0) return normalizeTime(last_active_millis);
        if (last_active_at > 0) return normalizeTime(last_active_at);
        if (last_online_millis > 0) return normalizeTime(last_online_millis);
        if (last_online > 0) return normalizeTime(last_online);
        if (last_offline > 0) return normalizeTime(last_offline);
        return 0L;
    }

    private long normalizeTime(long value) {
        if (value <= 0) return 0L;
        return value < 100000000000L ? value * 1000L : value;
    }

    public boolean isHelloSent() {
        return hello_sent || apply_status == 1 || greeting_status == 1;
    }

    public void markHelloSent() {
        hello_sent = true;
        if (apply_status == 0) apply_status = 1;
        if (greeting_status == 0) greeting_status = 1;
        if (requester_msg_count <= 0) requester_msg_count = 1;
        if (max_greeting_count <= 0) max_greeting_count = 3;
    }

    public int getMaxGreetingCountSafe() {
        return max_greeting_count > 0 ? max_greeting_count : 3;
    }

    public boolean canSendMoreGreeting() {
        if (follow == 1) return true;
        if (!isHelloSent()) return true;
        return requester_msg_count > 0 && requester_msg_count < getMaxGreetingCountSafe();
    }

    public void updateGreetingState(int count, int maxCount, long nextAllowedAt) {
        hello_sent = true;
        if (apply_status == 0) apply_status = 1;
        if (greeting_status == 0) greeting_status = 1;
        if (count > 0) requester_msg_count = count;
        if (maxCount > 0) max_greeting_count = maxCount;
        next_allowed_at = nextAllowedAt;
    }

    public int getDistanceMetersSafe() {
        if (distance_meters > 0) return distance_meters;
        if (distance_km > 0) return (int) Math.round(distance_km * 1000d);
        return 0;
    }

    public String getNearbyLabel(Context context) {
        int meters = getDistanceMetersSafe();
        if (context == null || meters <= 0 || meters > 70000) return "";
        if (meters < 1000) return context.getString(R.string.partnerbrowse_nearby_very_close);
        if (meters < 5000) return context.getString(R.string.partnerbrowse_nearby_5km);
        if (meters < 10000) return context.getString(R.string.partnerbrowse_nearby_10km);
        if (meters < 30000) return context.getString(R.string.partnerbrowse_nearby_30km);
        return context.getString(R.string.partnerbrowse_nearby_70km);
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
        List<String> images = safeImageList(profile_images);
        if (images.size() > 9) return new ArrayList<>(images.subList(0, 9));
        return images;
    }

    public List<String> getDisplayImagesSafe() {
        ArrayList<String> out = new ArrayList<>();
        String avatarPath = getAvatarPathSafe();
        if (!TextUtils.isEmpty(avatarPath)) out.add(avatarPath);
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
        args.putInt("requester_msg_count", requester_msg_count);
        args.putInt("max_greeting_count", max_greeting_count);
        args.putLong("next_allowed_at", next_allowed_at);
        args.putLong("last_active_millis", getLastActiveMillisSafe());
        args.putLong("last_online_millis", last_online_millis);
        args.putInt("online", online);
        args.putString("role", safe(role));
        args.putString("category", safe(category));
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
        bean.requester_msg_count = args.getInt("requester_msg_count", 0);
        bean.max_greeting_count = args.getInt("max_greeting_count", 0);
        bean.next_allowed_at = args.getLong("next_allowed_at", 0L);
        bean.last_active_millis = args.getLong("last_active_millis", 0L);
        bean.last_online_millis = args.getLong("last_online_millis", 0L);
        bean.online = args.getInt("online", 0);
        bean.role = args.getString("role", "");
        bean.category = args.getString("category", "");
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
