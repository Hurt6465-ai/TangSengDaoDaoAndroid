package com.chat.partnerlist.model;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class PartnerListUser {
    public String uid;
    public String id;
    public String name;
    public String username;
    public String avatar;
    public int sex;
    public String birthday;
    public String intro;
    public String country_code;
    public String country;
    public List<String> native_languages;
    public List<String> learning_languages;
    public List<String> tags;
    public String profile_cover;
    public List<String> profile_images;
    public String vercode;
    public int online;
    public int last_offline;
    public long last_active_at;
    public int is_new;
    public long profile_version;

    public String stableId() {
        if (!TextUtils.isEmpty(uid)) return uid;
        return id == null ? "" : id;
    }

    public String displayName() {
        if (!TextUtils.isEmpty(name)) return name;
        if (!TextUtils.isEmpty(username)) return username;
        return stableId();
    }

    public String displayAvatar() {
        if (!TextUtils.isEmpty(avatar)) return avatar;
        if (profile_images != null) {
            for (String item : profile_images) {
                if (!TextUtils.isEmpty(item)) return item;
            }
        }
        if (!TextUtils.isEmpty(profile_cover)) return profile_cover;
        return "";
    }

    public int age() {
        if (TextUtils.isEmpty(birthday) || birthday.length() < 4) return 0;
        try {
            int year = Integer.parseInt(birthday.substring(0, 4));
            int current = Calendar.getInstance().get(Calendar.YEAR);
            int age = current - year;
            return age > 0 && age < 120 ? age : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }


    public PartnerListUser copy() {
        PartnerListUser out = new PartnerListUser();
        out.uid = uid;
        out.id = id;
        out.name = name;
        out.username = username;
        out.avatar = avatar;
        out.sex = sex;
        out.birthday = birthday;
        out.intro = intro;
        out.country_code = country_code;
        out.country = country;
        out.native_languages = native_languages == null ? new ArrayList<>() : new ArrayList<>(native_languages);
        out.learning_languages = learning_languages == null ? new ArrayList<>() : new ArrayList<>(learning_languages);
        out.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        out.profile_cover = profile_cover;
        out.profile_images = profile_images == null ? new ArrayList<>() : new ArrayList<>(profile_images);
        out.vercode = vercode;
        out.online = online;
        out.last_offline = last_offline;
        out.last_active_at = last_active_at;
        out.is_new = is_new;
        out.profile_version = profile_version;
        return out;
    }

    public List<String> nativeLanguages() {
        return native_languages == null ? new ArrayList<>() : native_languages;
    }

    public List<String> learningLanguages() {
        return learning_languages == null ? new ArrayList<>() : learning_languages;
    }

    public List<String> tags() {
        return tags == null ? new ArrayList<>() : tags;
    }
}
