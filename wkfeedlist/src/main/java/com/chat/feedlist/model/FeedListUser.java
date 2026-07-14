package com.chat.feedlist.model;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FeedListUser implements Serializable {
    public String uid;
    public String name;
    public String username;
    public String avatar;
    public String avatar_cache_key;
    public String country_code;
    public int sex;
    public int age;
    /** Server versions may return either a string or a JSON array. */
    public Object native_languages;
    public Object learning_languages;
    public int follow;
    public int status;
    public String vercode;

    public List<String> nativeLanguageList() { return strings(native_languages); }
    public List<String> learningLanguageList() { return strings(learning_languages); }

    private static List<String> strings(Object value) {
        if (value == null) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                String text = item == null ? "" : String.valueOf(item).trim();
                if (!TextUtils.isEmpty(text)) out.add(text);
            }
        } else {
            String raw = String.valueOf(value).trim();
            if (!TextUtils.isEmpty(raw)) {
                raw = raw.replace("[", "").replace("]", "").replace("\"", "");
                for (String part : raw.split("[,|/]+")) {
                    String text = part.trim();
                    if (!TextUtils.isEmpty(text)) out.add(text);
                }
            }
        }
        return out;
    }
}
