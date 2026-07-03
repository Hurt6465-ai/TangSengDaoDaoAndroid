package com.chat.speech.model;

import org.json.JSONException;
import org.json.JSONObject;

public class TtsVoice {
    public final String code;
    public final String name;
    public final String locale;
    public final int gender;

    public TtsVoice(String code, String name, String locale, int gender) {
        this.code = code;
        this.name = name;
        this.locale = locale;
        this.gender = gender;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("code", code);
        object.put("name", name);
        object.put("locale", locale);
        object.put("gender", gender);
        return object;
    }
}
