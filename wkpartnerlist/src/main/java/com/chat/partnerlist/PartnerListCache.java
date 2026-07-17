package com.chat.partnerlist;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.partnerlist.model.PartnerListResponse;
import com.chat.partnerlist.model.PartnerListUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 按登录账号 + 推荐日隔离的轻量缓存。JSON 序列化全部放到单线程后台执行。 */
public final class PartnerListCache {
    public interface Callback {
        void onLoaded(PartnerListResponse response);
    }

    private static final String PREF = "partner_list_cache_v2";
    private static final String KEY_PREFIX = "recommendation:";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "partner-list-cache");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PartnerListCache() {}

    public static void loadAsync(Context context, Callback callback) {
        if (context == null) {
            if (callback != null) callback.onLoaded(null);
            return;
        }
        Context app = context.getApplicationContext();
        String uid = WKConfig.getInstance().getUid();
        String dayKey = PartnerListTime.currentDayKey();
        if (TextUtils.isEmpty(uid)) {
            if (callback != null) callback.onLoaded(null);
            return;
        }
        IO.execute(() -> {
            PartnerListResponse response = parse(app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getString(key(uid, dayKey), ""), dayKey);
            MAIN.post(() -> {
                if (callback != null) callback.onLoaded(response);
            });
        });
    }

    public static void saveAsync(Context context, PartnerListResponse response) {
        if (context == null || response == null) return;
        Context app = context.getApplicationContext();
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) return;
        String dayKey = TextUtils.isEmpty(response.day_key) ? PartnerListTime.currentDayKey() : response.day_key;
        PartnerListResponse snapshot = copyResponse(response);
        IO.execute(() -> {
            try {
                SharedPreferences prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit().putString(key(uid, dayKey), toJson(snapshot).toString());
                // 同一账号只保留当前推荐日，避免算法/日期切换后旧名单重新出现。
                String accountPrefix = KEY_PREFIX + uid + ":";
                for (String existing : prefs.getAll().keySet()) {
                    if (existing.startsWith(accountPrefix) && !existing.equals(key(uid, dayKey))) {
                        editor.remove(existing);
                    }
                }
                editor.apply();
            } catch (Throwable ignored) {
            }
        });
    }

    public static void clearCurrentAccount(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) return;
        IO.execute(() -> {
            SharedPreferences prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            String prefix = KEY_PREFIX + uid + ":";
            for (String existing : prefs.getAll().keySet()) {
                if (existing.startsWith(prefix)) editor.remove(existing);
            }
            editor.apply();
        });
    }

    public static void clearAll(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply());
    }

    private static String key(String uid, String dayKey) {
        return KEY_PREFIX + uid + ":" + dayKey;
    }

    private static PartnerListResponse parse(String raw, String expectedDay) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            JSONObject root = new JSONObject(raw);
            if (!TextUtils.equals(expectedDay, root.optString("day_key"))) return null;
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
            response.added_user_ids = strings(root.optJSONArray("added_user_ids"));
            response.removed_user_ids = strings(root.optJSONArray("removed_user_ids"));
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
                    user.created_at = valueString(item, "created_at");
                    user.joined_at = valueString(item, "joined_at");
                    user.registered_at = valueString(item, "registered_at");
                    user.join_time = valueString(item, "join_time");
                    user.profile_version = item.optLong("profile_version");
                    if (!TextUtils.isEmpty(user.stableId())) response.users.add(user);
                }
            }
            // 空名单同样是合法缓存，不能回退显示上一份旧名单。
            return response;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JSONObject toJson(PartnerListResponse response) throws Exception {
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
        root.put("added_user_ids", json(response.added_user_ids));
        root.put("removed_user_ids", json(response.removed_user_ids));
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
            item.put("created_at", user.created_at);
            item.put("joined_at", user.joined_at);
            item.put("registered_at", user.registered_at);
            item.put("join_time", user.join_time);
            item.put("profile_version", user.profile_version);
            users.put(item);
        }
        root.put("users", users);
        return root;
    }

    private static PartnerListResponse copyResponse(PartnerListResponse src) {
        PartnerListResponse out = new PartnerListResponse();
        out.day_key = src.day_key;
        out.algorithm_version = src.algorithm_version;
        out.list_version = src.list_version;
        out.first_served_at = src.first_served_at;
        out.rotate_at = src.rotate_at;
        out.rotation_done = src.rotation_done;
        out.rotation_retry_at = src.rotation_retry_at;
        out.updated_count = src.updated_count;
        out.unique_assigned_count = src.unique_assigned_count;
        out.daily_candidate_limit = src.daily_candidate_limit;
        out.greeting_limit = src.greeting_limit;
        out.greeting_used = src.greeting_used;
        out.greeting_remaining = src.greeting_remaining;
        out.server_time = src.server_time;
        out.added_user_ids = src.added_user_ids == null ? new ArrayList<>() : new ArrayList<>(src.added_user_ids);
        out.removed_user_ids = src.removed_user_ids == null ? new ArrayList<>() : new ArrayList<>(src.removed_user_ids);
        out.users = new ArrayList<>();
        for (PartnerListUser user : src.usersSafe()) if (user != null) out.users.add(user.copy());
        return out;
    }


    private static String valueString(JSONObject object, String key) {
        if (object == null || TextUtils.isEmpty(key) || !object.has(key) || object.isNull(key)) return "";
        Object value = object.opt(key);
        return value == null ? "" : String.valueOf(value);
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
