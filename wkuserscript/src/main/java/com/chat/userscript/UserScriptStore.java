package com.chat.userscript;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.chat.userscript.model.UserScript;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserScriptStore {
    private static final String SP_NAME = "tsdd_user_script_store";
    private static final String KEY_SCRIPTS = "scripts_json";
    private static volatile UserScriptStore instance;
    private final SharedPreferences sp;

    private UserScriptStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public static UserScriptStore get(Context context) {
        if (instance == null) {
            synchronized (UserScriptStore.class) {
                if (instance == null) instance = new UserScriptStore(context);
            }
        }
        return instance;
    }

    public synchronized List<UserScript> getAll() {
        ArrayList<UserScript> list = new ArrayList<>();
        String json = sp.getString(KEY_SCRIPTS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) list.add(UserScript.fromJson(object));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public synchronized UserScript getById(String id) {
        if (TextUtils.isEmpty(id)) return null;
        for (UserScript script : getAll()) {
            if (id.equals(script.id)) return script;
        }
        return null;
    }

    public synchronized List<UserScript> getRunnableScripts(String url, String runAt) {
        ArrayList<UserScript> out = new ArrayList<>();
        for (UserScript script : getAll()) {
            if (!UserScriptMatcher.matches(script, url)) continue;
            String scriptRunAt = TextUtils.isEmpty(script.runAt) ? "document-end" : script.runAt;
            if ("document-idle".equals(runAt)) {
                if ("document-idle".equals(scriptRunAt)) out.add(script);
            } else if ("document-start".equals(runAt)) {
                if ("document-start".equals(scriptRunAt)) out.add(script);
            } else {
                if ("document-end".equals(scriptRunAt) || TextUtils.isEmpty(scriptRunAt)) out.add(script);
            }
        }
        return out;
    }

    public synchronized void upsert(UserScript script) {
        if (script == null || TextUtils.isEmpty(script.id)) return;
        List<UserScript> list = getAll();
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            if (script.id.equals(list.get(i).id)) {
                list.set(i, script);
                updated = true;
                break;
            }
        }
        if (!updated) list.add(script);
        saveAll(list);
    }

    public synchronized void delete(String id) {
        if (TextUtils.isEmpty(id)) return;
        List<UserScript> list = getAll();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (id.equals(list.get(i).id)) list.remove(i);
        }
        saveAll(list);
        sp.edit().remove(keysKey(id)).apply();
    }

    public synchronized void setEnabled(String id, boolean enabled) {
        UserScript script = getById(id);
        if (script == null) return;
        script.enabled = enabled;
        upsert(script);
    }

    private void saveAll(List<UserScript> list) {
        JSONArray array = new JSONArray();
        if (list != null) {
            for (UserScript script : list) array.put(script.toJson());
        }
        sp.edit().putString(KEY_SCRIPTS, array.toString()).apply();
    }

    public String getScriptValue(String scriptId, String key, String defaultJson) {
        if (TextUtils.isEmpty(scriptId) || TextUtils.isEmpty(key)) return defaultJsonOrNull(defaultJson);
        return sp.getString(valueKey(scriptId, key), defaultJsonOrNull(defaultJson));
    }

    public void setScriptValue(String scriptId, String key, String valueJson) {
        if (TextUtils.isEmpty(scriptId) || TextUtils.isEmpty(key)) return;
        Set<String> keys = new HashSet<>(sp.getStringSet(keysKey(scriptId), new HashSet<>()));
        keys.add(key);
        sp.edit()
                .putString(valueKey(scriptId, key), valueJson == null ? "null" : valueJson)
                .putStringSet(keysKey(scriptId), keys)
                .apply();
    }

    public void deleteScriptValue(String scriptId, String key) {
        if (TextUtils.isEmpty(scriptId) || TextUtils.isEmpty(key)) return;
        Set<String> keys = new HashSet<>(sp.getStringSet(keysKey(scriptId), new HashSet<>()));
        keys.remove(key);
        sp.edit().remove(valueKey(scriptId, key)).putStringSet(keysKey(scriptId), keys).apply();
    }

    public String listScriptValues(String scriptId) {
        JSONArray array = new JSONArray();
        if (!TextUtils.isEmpty(scriptId)) {
            Set<String> keys = sp.getStringSet(keysKey(scriptId), new HashSet<>());
            for (String key : keys) array.put(key);
        }
        return array.toString();
    }

    private static String valueKey(String scriptId, String key) {
        return "value_" + scriptId + "_" + Integer.toHexString(key.hashCode()) + "_" + key;
    }

    private static String keysKey(String scriptId) {
        return "keys_" + scriptId;
    }

    private static String defaultJsonOrNull(String value) {
        return value == null || value.length() == 0 ? "null" : value;
    }
}
