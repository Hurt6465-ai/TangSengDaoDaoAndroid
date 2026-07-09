package com.chat.dating.model;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DatingProfile {
    public String uid;
    public String name;
    public String avatar;
    public String country_code;
    public String country;
    public String city;
    public int age;
    public int gender;
    public int sex;
    public String looking_for_gender;
    public String intent;
    public String relationship_goal;
    public String cross_border_preference;
    public String bio;
    public String intro;
    public String job;
    public String education;
    public String relationship_status;
    public String distance_label;
    public double distance_km;
    public int distance_meters;
    public List<String> photos;
    public List<String> native_languages;
    public List<String> learning_languages;
    public List<String> tags;
    public List<String> love_tags;
    public List<String> personality_tags;
    public List<String> lifestyle_tags;
    public List<String> interest_tags;
    public List<String> communication_tags;
    public int profile_score;
    public long last_active_at;
    public int enabled;

    public String safeUid() {
        return uid == null ? "" : uid;
    }

    public String safeName() {
        return TextUtils.isEmpty(name) ? safeUid() : name;
    }

    public int safeGender() {
        return gender != 0 ? gender : sex;
    }

    public String safeCountryCode() {
        return TextUtils.isEmpty(country_code) ? "" : country_code.trim().toUpperCase(Locale.US);
    }

    public String safeIntro() {
        if (!TextUtils.isEmpty(intro)) return intro;
        return bio == null ? "" : bio;
    }

    public String safeRelationshipGoal() {
        if (!TextUtils.isEmpty(relationship_goal)) return relationship_goal;
        return intent == null ? "" : intent;
    }

    public String safeCrossBorderPreference() {
        return cross_border_preference == null ? "" : cross_border_preference;
    }

    public boolean rejectsCrossBorder() {
        String value = safeCrossBorderPreference().trim().toLowerCase(Locale.US);
        return value.contains("same_country")
                || value.contains("same-country")
                || value.contains("local_only")
                || value.contains("nearby_only")
                || value.contains("no_foreign")
                || value.contains("refuse_foreign")
                || value.contains("拒绝异国")
                || value.contains("只接受本国")
                || value.contains("本国恋");
    }

    public String firstPhoto() {
        List<String> list = safePhotos();
        return list.isEmpty() ? "" : list.get(0);
    }

    public List<String> safePhotos() {
        ArrayList<String> list = new ArrayList<>();
        if (photos != null) {
            for (String item : photos) {
                if (!TextUtils.isEmpty(item)) list.add(item);
            }
        }
        if (list.isEmpty() && !TextUtils.isEmpty(avatar)) list.add(avatar);
        return list;
    }

    public List<String> safeTags() {
        ArrayList<String> list = new ArrayList<>();
        addAll(list, love_tags);
        addAll(list, personality_tags);
        addAll(list, lifestyle_tags);
        addAll(list, interest_tags);
        addAll(list, communication_tags);
        addAll(list, tags);
        return dedupe(list);
    }

    public List<String> safeCoreTags() {
        ArrayList<String> list = new ArrayList<>();
        addAll(list, love_tags);
        addAll(list, personality_tags);
        addAll(list, communication_tags);
        addAll(list, lifestyle_tags);
        addAll(list, interest_tags);
        addAll(list, tags);
        return dedupe(list);
    }

    public List<String> safeNativeLanguages() {
        return clean(native_languages);
    }

    public List<String> safeLearningLanguages() {
        return clean(learning_languages);
    }

    public String safeDistanceLabel() {
        if (!TextUtils.isEmpty(distance_label)) return distance_label;
        if (distance_meters > 0) {
            if (distance_meters < 1000) return distance_meters + "m";
            float km = distance_meters / 1000f;
            return String.format(Locale.getDefault(), "%.1fkm", km);
        }
        if (distance_km > 0) return String.format(Locale.getDefault(), "%.1fkm", distance_km);
        return "";
    }

    private void addAll(ArrayList<String> out, List<String> source) {
        if (source == null) return;
        for (String item : source) {
            if (!TextUtils.isEmpty(item)) out.add(item.trim());
        }
    }

    private List<String> clean(List<String> source) {
        ArrayList<String> list = new ArrayList<>();
        addAll(list, source);
        return dedupe(list);
    }

    private List<String> dedupe(List<String> source) {
        ArrayList<String> list = new ArrayList<>();
        if (source == null) return list;
        for (String item : source) {
            if (TextUtils.isEmpty(item)) continue;
            boolean exists = false;
            for (String old : list) {
                if (item.equalsIgnoreCase(old)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) list.add(item);
        }
        return list;
    }
}
