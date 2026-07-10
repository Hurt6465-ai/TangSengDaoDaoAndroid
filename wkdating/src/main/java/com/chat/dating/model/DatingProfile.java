package com.chat.dating.model;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 交友资料模型。 */
public class DatingProfile implements Serializable {
    private static final long serialVersionUID = 3L;

    public String uid;
    public String id;
    public String name;
    public String username;
    public String avatar;
    public String country_code;
    public String country;
    public String city;
    public int age;
    public String birthday;
    public int gender = -1;
    public int sex = -1;
    public int gender_preference = -1;
    public int min_age = 18;
    public int max_age = 99;
    public String looking_for_gender;
    public String intent;
    public String relationship_goal;
    public String cross_border_preference;
    public String bio;
    public String intro;
    public String job;
    public String education;
    public String relationship_status;
    public String sexual_orientation;
    public String drinking;
    public String smoking;
    public int height_cm;
    public int weight_kg;
    public int show_distance = 1;
    public int allow_voice = 1;
    public int allow_video;
    public int online;
    public boolean complete;
    public boolean can_recommend;
    public String distance_label;
    public double distance_km;
    public int distance_meters;
    public List<String> photos;
    public List<String> profile_images;
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
        if (!TextUtils.isEmpty(uid)) return uid;
        return id == null ? "" : id;
    }

    public String safeName() {
        if (!TextUtils.isEmpty(name)) return name;
        if (!TextUtils.isEmpty(username)) return username;
        return safeUid();
    }

    public boolean isFemale() {
        if (gender == 2) return true;
        if (gender == 1) return false;
        if (sex == 0) return true;
        if (sex == 1) return false;
        return false;
    }

    public boolean isMale() { return !isFemale(); }
    public int normalizedSex() { return isFemale() ? 0 : 1; }

    public String safeCountryCode() {
        return TextUtils.isEmpty(country_code) ? "" : country_code.trim().toUpperCase(Locale.US);
    }

    public String safeIntro() {
        if (!TextUtils.isEmpty(intro)) return intro.trim();
        return bio == null ? "" : bio.trim();
    }

    public String safeRelationshipGoal() {
        if (!TextUtils.isEmpty(relationship_goal)) return relationship_goal.trim();
        return intent == null ? "" : intent.trim();
    }

    public String safeCrossBorderPreference() {
        if (!TextUtils.isEmpty(cross_border_preference)) return cross_border_preference.trim();
        if (tags != null) {
            for (String tag : tags) {
                if (!TextUtils.isEmpty(tag) && tag.trim().toLowerCase(Locale.US).startsWith("cross:")) {
                    return tag.trim().substring("cross:".length());
                }
            }
        }
        return "";
    }

    /**
     * 是否拒绝异国恋。兼容新字段和旧版保存在 tags 中的 cross:* 值。
     */
    public boolean rejectsCrossBorder() {
        String value = safeCrossBorderPreference().toLowerCase(Locale.US);
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
        appendPhotos(list, photos);
        appendPhotos(list, profile_images);
        if (list.isEmpty() && !TextUtils.isEmpty(avatar)) list.add(avatar.trim());
        if (list.size() > 5) return new ArrayList<>(list.subList(0, 5));
        return list;
    }

    private void appendPhotos(ArrayList<String> out, List<String> source) {
        if (source == null) return;
        for (String item : source) {
            if (TextUtils.isEmpty(item)) continue;
            String value = item.trim();
            boolean exists = false;
            for (String old : out) {
                if (old.equals(value)) { exists = true; break; }
            }
            if (!exists && out.size() < 5) out.add(value);
        }
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

    public List<String> safeNativeLanguages() { return clean(native_languages); }
    public List<String> safeLearningLanguages() { return clean(learning_languages); }

    public String safeDistanceLabel() {
        if (!TextUtils.isEmpty(distance_label)) return distance_label;
        if (distance_meters > 0) {
            if (distance_meters < 1000) return distance_meters + "m";
            return String.format(Locale.getDefault(), "%.1fkm", distance_meters / 1000f);
        }
        if (distance_km > 0) return String.format(Locale.getDefault(), "%.1fkm", distance_km);
        return "";
    }

    public String displayLocation() {
        StringBuilder out = new StringBuilder();
        if (!TextUtils.isEmpty(city)) out.append(city.trim());
        else if (!TextUtils.isEmpty(country)) out.append(country.trim());
        String distance = show_distance == 1 ? safeDistanceLabel() : "";
        if (!TextUtils.isEmpty(distance)) {
            if (out.length() > 0) out.append(" · ");
            out.append(distance);
        }
        return out.toString();
    }

    private void addAll(ArrayList<String> out, List<String> source) {
        if (source == null) return;
        for (String item : source) if (!TextUtils.isEmpty(item)) out.add(item.trim());
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
                if (item.equalsIgnoreCase(old)) { exists = true; break; }
            }
            if (!exists) list.add(item);
        }
        return list;
    }
}
