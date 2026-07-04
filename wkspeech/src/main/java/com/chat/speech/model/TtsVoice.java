package com.chat.speech.model;

import org.json.JSONException;
import org.json.JSONObject;

public class TtsVoice {
    public static final int GENDER_FEMALE = 0;
    public static final int GENDER_MALE = 1;
    public static final int GENDER_UNKNOWN = 2;

    public final String code;
    public final String name;
    public final String locale;
    public final int gender;
    public final String sourceId;
    public final String sourceName;

    public TtsVoice(String code, String name, String locale, int gender) {
        this(code, name, locale, gender, "builtin_ms_translator", "微软翻译兼容源");
    }

    public TtsVoice(String code, String name, String locale, int gender, String sourceId, String sourceName) {
        this.code = safe(code);
        this.name = safe(name);
        this.locale = safe(locale);
        this.gender = gender;
        this.sourceId = safe(sourceId);
        this.sourceName = safe(sourceName);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("code", code);
        object.put("name", name);
        object.put("locale", locale);
        object.put("gender", gender);
        object.put("sourceId", sourceId);
        object.put("sourceName", sourceName);
        return object;
    }

    public static TtsVoice fromJson(JSONObject object) {
        if (object == null) return null;
        return new TtsVoice(
                object.optString("code"),
                object.optString("name"),
                object.optString("locale"),
                object.optInt("gender", GENDER_UNKNOWN),
                object.optString("sourceId", "imported"),
                object.optString("sourceName", "用户导入语音包")
        );
    }

    public String displayName() {
        StringBuilder builder = new StringBuilder();
        if (!name.isEmpty()) builder.append(name);
        if (!locale.isEmpty()) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(locale);
        }
        String genderName = genderName();
        if (!genderName.isEmpty()) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(genderName);
        }
        if (builder.length() == 0) builder.append(code);
        return builder.toString();
    }

    public String genderName() {
        if (gender == GENDER_FEMALE) return "女声";
        if (gender == GENDER_MALE) return "男声";
        return "";
    }

    public boolean isChinese() {
        return locale.startsWith("zh") || code.startsWith("zh-");
    }

    public boolean isMyanmar() {
        return locale.startsWith("my") || code.startsWith("my-");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
