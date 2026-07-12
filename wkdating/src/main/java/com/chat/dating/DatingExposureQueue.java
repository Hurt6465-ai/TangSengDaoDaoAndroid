package com.chat.dating;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 小型持久化曝光队列，网络失败或页面销毁后仍可在下次进入时重试。 */
final class DatingExposureQueue {
    private static final String PREF = "wk_dating_exposure_queue";
    private static final String KEY = "items";
    private static final int MAX_ITEMS = 100;

    private DatingExposureQueue() {}

    static ArrayList<Map<String, Object>> load(Context context) {
        ArrayList<Map<String, Object>> out = new ArrayList<>();
        if (context == null) return out;
        String raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            int start = Math.max(0, array.length() - MAX_ITEMS);
            for (int i = start; i < array.length(); i++) {
                JSONObject value = array.optJSONObject(i);
                if (value == null) continue;
                HashMap<String, Object> item = new HashMap<>();
                item.put("to_uid", value.optString("to_uid", ""));
                item.put("event_type", value.optString("event_type", "expose"));
                item.put("source", value.optString("source", "wkdating"));
                item.put("duration_ms", value.optLong("duration_ms", 0L));
                item.put("photo_index", value.optInt("photo_index", 0));
                if (!String.valueOf(item.get("to_uid")).isEmpty()) out.add(item);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static void save(Context context, List<Map<String, Object>> values) {
        if (context == null) return;
        JSONArray array = new JSONArray();
        int size = values == null ? 0 : values.size();
        int start = Math.max(0, size - MAX_ITEMS);
        for (int i = start; i < size; i++) {
            Map<String, Object> value = values.get(i);
            if (value == null) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("to_uid", stringValue(value.get("to_uid")));
                item.put("event_type", stringValue(value.get("event_type")));
                item.put("source", stringValue(value.get("source")));
                item.put("duration_ms", longValue(value.get("duration_ms")));
                item.put("photo_index", intValue(value.get("photo_index")));
                array.put(item);
            } catch (Throwable ignored) {
            }
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit();
        if (array.length() == 0) editor.remove(KEY); else editor.putString(KEY, array.toString());
        editor.apply();
    }

    private static String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private static long longValue(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(stringValue(value)); } catch (Throwable ignored) { return 0L; }
    }
    private static int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(stringValue(value)); } catch (Throwable ignored) { return 0; }
    }
}
