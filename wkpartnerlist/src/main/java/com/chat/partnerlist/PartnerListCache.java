package com.chat.partnerlist;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.chat.partnerlist.model.PartnerListResponse;
import com.chat.partnerlist.model.PartnerListUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class PartnerListCache {
    private static final String PREF = "partner_list_cache_v1";
    private static final String KEY_JSON = "recommendation";

    private PartnerListCache() {}

    public static PartnerListResponse load(Context context) {
        if (context == null) return null;
        try {
            String raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_JSON, "");
            if (TextUtils.isEmpty(raw)) return null;
            JSONObject root = new JSONObject(raw);
            if (!PartnerListTime.currentDayKey().equals(root.optString("day_key"))) return null;
            PartnerListResponse response = new PartnerListResponse();
            response.day_key = root.optString("day_key");
            response.algorithm_version = root.optInt("algorithm_version");
            response.list_version = root.optInt("list_version");
            response.first_served_at = root.optLong("first_served_at");
            response.rotate_at = root.optLong("rotate_at");
            response.rotation_done = root.optBoolean("rotation_done");
            response.rotation_retry_at = root.optLong("rotation_retry_at");
            response.updated_count = 0;
            response.unique_assigned_count = root.optInt("unique_assigned_count");
            response.daily_candidate_limit = root.optInt("daily_candidate_limit", 100);
            response.greeting_limit = root.optInt("greeting_limit", 10);
            response.greeting_used = root.optInt("greeting_used");
            response.greeting_remaining = root.optInt("greeting_remaining", 10);
            response.server_time = root.optLong("server_time", System.currentTimeMillis());
            response.users = new ArrayList<>();
            JSONArray users = root.optJSONArray("users");
            if (users != null) {
                for (int i = 0; i < users.length(); i++) {
                    JSONObject item = users.optJSONObject(i);
                    if (item == null) continue;
                    PartnerListUser user = new PartnerListUser();
                    user.uid = item.optString("uid");
                    user.id = item.optString("id");
                    user.name = item.optString("name");
                    user.username = item.optString("username");
                    user.avatar = item.optString("avatar");
                    user.sex = item.optInt("sex");
                    user.birthday = item.optString("birthday");
                    user.intro = item.optString("intro");
                    user.country_code = item.optString("country_code");
                    user.country = item.optString("country");
                    user.native_languages = strings(item.optJSONArray("native_languages"));
                    user.learning_languages = strings(item.optJSONArray("learning_languages"));
                    user.tags = strings(item.optJSONArray("tags"));
                    user.profile_cover = item.optString("profile_cover");
                    user.profile_images = strings(item.optJSONArray("profile_images"));
                    user.vercode = item.optString("vercode");
                    user.online = item.optInt("online");
                    user.last_offline = item.optInt("last_offline");
                    user.last_active_at = item.optLong("last_active_at");
                    user.is_new = item.optInt("is_new");
                    if (!TextUtils.isEmpty(user.stableId())) response.users.add(user);
                }
            }
            return response.users.isEmpty() ? null : response;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void save(Context context, PartnerListResponse response) {
        if (context == null || response == null || response.usersSafe().isEmpty()) return;
        try {
            JSONObject root = new JSONObject();
            root.put("day_key", response.day_key);
            root.put("algorithm_version", response.algorithm_version);
            root.put("list_version", response.list_version);
            root.put("first_served_at", response.first_served_at);
            root.put("rotate_at", response.rotate_at);
            root.put("rotation_done", response.rotation_done);
            root.put("rotation_retry_at", response.rotation_retry_at);
            root.put("unique_assigned_count", response.unique_assigned_count);
            root.put("daily_candidate_limit", response.daily_candidate_limit);
            root.put("greeting_limit", response.greeting_limit);
            root.put("greeting_used", response.greeting_used);
            root.put("greeting_remaining", response.greeting_remaining);
            root.put("server_time", response.server_time);
            JSONArray users = new JSONArray();
            for (PartnerListUser user : response.usersSafe()) {
                if (user == null) continue;
                JSONObject item = new JSONObject();
                item.put("uid", user.uid);
                item.put("id", user.id);
                item.put("name", user.name);
                item.put("username", user.username);
                item.put("avatar", user.avatar);
                item.put("sex", user.sex);
                item.put("birthday", user.birthday);
                item.put("intro", user.intro);
                item.put("country_code", user.country_code);
                item.put("country", user.country);
                item.put("native_languages", json(user.nativeLanguages()));
                item.put("learning_languages", json(user.learningLanguages()));
                item.put("tags", json(user.tags()));
                item.put("profile_cover", user.profile_cover);
                item.put("profile_images", json(user.profile_images));
                item.put("vercode", user.vercode);
                item.put("online", user.online);
                item.put("last_offline", user.last_offline);
                item.put("last_active_at", user.last_active_at);
                item.put("is_new", user.is_new);
                users.put(item);
            }
            root.put("users", users);
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_JSON, root.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static JSONArray json(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) for (String value : values) if (!TextUtils.isEmpty(value)) array.put(value);
        return array;
    }

    private static List<String> strings(JSONArray array) {
        ArrayList<String> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (!TextUtils.isEmpty(value)) out.add(value);
        }
        return out;
    }
}
