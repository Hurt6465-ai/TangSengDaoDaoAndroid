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
    public String job_status;
    public String ideal_partner;
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
    /** 后端可直接返回模糊距离档位；兼容 distance_bucket / distance_level 两种字段名。 */
    public int distance_bucket;
    public int distance_level;
    public double distance_km;
    public int distance_meters;
    public List<String> photos;
    public List<String> card_photos;
    public List<String> profile_images;
    public List<String> native_languages;
    public List<String> learning_languages;
    public List<String> tags;
    public List<String> love_tags;
    public List<String> personality_tags;
    public List<String> pet_tags;
    public List<String> sport_tags;
    public List<String> movie_tags;
    public List<String> dealbreakers;
    public List<String> lifestyle_tags;
    public List<String> interest_tags;
    public List<String> communication_tags;
    public int profile_score;
    public long last_active_at;
    public int enabled;
    public int user_paused;

    public String safeUid() {
        if (!TextUtils.isEmpty(uid)) return uid;
        return id == null ? "" : id;
    }

    public String safeName() {
        if (!TextUtils.isEmpty(name)) return name;
        if (!TextUtils.isEmpty(username)) return username;
        return safeUid();
    }

    /**
     * 统一为后端 sex 约定：0=女，1=男，-1=未知。
     * 未填写、脏数据或未来扩展值都不能再被默认归为男性。
     */
    public int normalizedSex() {
        if (gender == 2) return 0;
        if (gender == 1) return 1;
        if (sex == 0 || sex == 1) return sex;
        return -1;
    }

    public boolean isFemale() { return normalizedSex() == 0; }
    public boolean isMale() { return normalizedSex() == 1; }
    public boolean hasKnownSex() { return normalizedSex() >= 0; }

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

    public List<String> safeDatingPhotos() {
        ArrayList<String> list = new ArrayList<>();
        appendPhotos(list, photos);
        // 只兼容旧版交友卡片图；语伴照片墙 profile_images 不再混入交友照片。
        if (list.isEmpty()) appendPhotos(list, card_photos);
        if (list.size() > 5) return new ArrayList<>(list.subList(0, 5));
        return list;
    }

    /** 交友大图与详情图只使用 dating photos；账号头像不再冒充交友照片。 */
    public List<String> safePhotos() {
        return safeDatingPhotos();
    }

    /** 兼容旧调用，已停止使用 card_photos 派生图。 */
    public List<String> safeCardPhotos() {
        return safeDatingPhotos();
    }

    public String safeAvatar() {
        return TextUtils.isEmpty(avatar) ? "" : avatar.trim();
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
        addAll(list, pet_tags);
        addAll(list, sport_tags);
        addAll(list, movie_tags);
        addAll(list, lifestyle_tags);
        addAll(list, interest_tags);
        addAll(list, communication_tags);
        addAll(list, tags);
        return dedupe(list);
    }


    public List<String> safeDealbreakers() { return clean(dealbreakers); }
    public List<String> safePersonalityTags() { return clean(personality_tags); }
    public List<String> safePetTags() { return clean(pet_tags); }
    public List<String> safeSportTags() { return clean(sport_tags); }
    public List<String> safeMovieTags() { return clean(movie_tags); }

    public List<String> safeNativeLanguages() { return clean(native_languages); }
    public List<String> safeLearningLanguages() { return clean(learning_languages); }

    /** 返回模糊距离档位：0未知，1=<1km，2=1-5km，3=5-20km，4=20-50km，5=50-100km，6=>=100km。 */
    public int distanceBucket() {
        int serverBucket = distance_bucket > 0 ? distance_bucket : distance_level;
        if (serverBucket >= 1 && serverBucket <= 6) return serverBucket;
        double km = distance_km;
        if (km <= 0d && distance_meters > 0) km = distance_meters / 1000d;
        if (km <= 0d && !TextUtils.isEmpty(distance_label)) {
            String raw = distance_label.trim().toLowerCase(Locale.US)
                    .replace('–', '-').replace('—', '-').replace(" ", "");
            if (raw.contains("1-5")) return 2;
            if (raw.contains("5-20")) return 3;
            if (raw.contains("20-50")) return 4;
            if (raw.contains("50-100")) return 5;
            if (raw.contains("100") && (raw.contains("以上") || raw.contains("over") || raw.contains("+"))) return 6;
            if (raw.contains("1km") && (raw.contains("内") || raw.contains("以内") || raw.contains("under") || raw.contains("within"))) return 1;
            try {
                String number = raw.replaceAll("[^0-9.]", "");
                if (!TextUtils.isEmpty(number)) km = Double.parseDouble(number);
                if (raw.contains("m") && !raw.contains("km")) km /= 1000d;
            } catch (Throwable ignored) {
            }
        }
        if (km <= 0d) return 0;
        if (km < 1d) return 1;
        if (km < 5d) return 2;
        if (km < 20d) return 3;
        if (km < 50d) return 4;
        if (km < 100d) return 5;
        return 6;
    }

    /** 兼容旧调用；正式页面通过 DatingUi.displayLocation(Context, profile) 本地化显示。 */
    public String safeDistanceLabel() {
        switch (distanceBucket()) {
            case 1: return "<1km";
            case 2: return "1-5km";
            case 3: return "5-20km";
            case 4: return "20-50km";
            case 5: return "50-100km";
            case 6: return "100km+";
            default: return "";
        }
    }

    /** 兼容旧调用。 */
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
