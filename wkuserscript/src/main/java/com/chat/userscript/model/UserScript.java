package com.chat.userscript.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserScript {
    public String id = UUID.randomUUID().toString().replace("-", "");
    public String name = "未命名脚本";
    public String description = "";
    public String version = "1.0.0";
    public String author = "";
    public String namespace = "";
    public String runAt = "document-end";
    public boolean enabled = true;
    public boolean noFrames = false;
    public String code = "";
    public final List<String> matches = new ArrayList<>();
    public final List<String> includes = new ArrayList<>();
    public final List<String> excludes = new ArrayList<>();
    public final List<String> grants = new ArrayList<>();

    public boolean hasGrant(String grant) {
        if (grant == null) return false;
        if (grants.isEmpty()) return false;
        for (String value : grants) {
            if ("none".equalsIgnoreCase(value)) return false;
            if (grant.equals(value) || grant.equalsIgnoreCase(value)) return true;
            if (("GM." + grant.replace("GM_", "")).equals(value)) return true;
        }
        return false;
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("name", name);
            object.put("description", description);
            object.put("version", version);
            object.put("author", author);
            object.put("namespace", namespace);
            object.put("runAt", runAt);
            object.put("enabled", enabled);
            object.put("noFrames", noFrames);
            object.put("code", code);
            object.put("matches", toArray(matches));
            object.put("includes", toArray(includes));
            object.put("excludes", toArray(excludes));
            object.put("grants", toArray(grants));
        } catch (Exception ignored) {
        }
        return object;
    }

    public static UserScript fromJson(JSONObject object) {
        UserScript script = new UserScript();
        if (object == null) return script;
        script.id = object.optString("id", script.id);
        script.name = object.optString("name", script.name);
        script.description = object.optString("description", "");
        script.version = object.optString("version", "1.0.0");
        script.author = object.optString("author", "");
        script.namespace = object.optString("namespace", "");
        script.runAt = object.optString("runAt", "document-end");
        script.enabled = object.optBoolean("enabled", true);
        script.noFrames = object.optBoolean("noFrames", false);
        script.code = object.optString("code", "");
        fill(script.matches, object.optJSONArray("matches"));
        fill(script.includes, object.optJSONArray("includes"));
        fill(script.excludes, object.optJSONArray("excludes"));
        fill(script.grants, object.optJSONArray("grants"));
        return script;
    }

    private static JSONArray toArray(List<String> list) {
        JSONArray array = new JSONArray();
        if (list == null) return array;
        for (String item : list) {
            if (item != null && item.trim().length() > 0) array.put(item.trim());
        }
        return array;
    }

    private static void fill(List<String> out, JSONArray array) {
        if (out == null || array == null) return;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (value.length() > 0) out.add(value);
        }
    }
}
