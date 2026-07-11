package com.chat.dating;

import android.text.TextUtils;

import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 将语伴资料中的稳定标签同步成交友页可读字段。 */
public final class DatingSharedProfileFormatter {
    private static final Map<String, String> LABELS = new HashMap<>();

    static {
        put(new String[]{"relationship_private", "relationship_single", "relationship_dating", "relationship_married", "relationship_divorced"},
                new String[]{"保密", "单身", "在交往", "已婚", "离异"});
        put(new String[]{"personality_patient", "personality_outgoing", "personality_quiet", "personality_introvert", "personality_funny", "personality_gentle", "personality_serious", "personality_slow_warm", "personality_eq", "personality_easygoing"},
                new String[]{"有耐心", "外向", "安静", "内向", "幽默", "温柔", "认真", "慢热", "高情商", "好相处"});
        put(new String[]{"pet_dog", "pet_cat", "pet_rabbit", "pet_bird", "pet_fish", "pet_hamster", "pet_reptile", "pet_love_animals"},
                new String[]{"狗", "猫", "兔子", "鸟", "鱼", "仓鼠", "爬宠", "喜欢动物"});
        put(new String[]{"sport_running", "sport_basketball", "sport_football", "sport_badminton", "sport_fitness", "sport_yoga", "sport_swimming", "sport_cycling", "sport_hiking", "sport_skateboard"},
                new String[]{"跑步", "篮球", "足球", "羽毛球", "健身", "瑜伽", "游泳", "骑行", "徒步", "滑板"});
        put(new String[]{"movie_film", "movie_comedy", "movie_romance", "movie_action", "movie_mystery", "movie_documentary", "movie_tv", "movie_anime", "movie_variety", "movie_short_video"},
                new String[]{"电影", "喜剧片", "爱情片", "动作片", "悬疑片", "纪录片", "电视剧", "动漫", "综艺", "短视频"});
        put(new String[]{"job_private", "job_student", "job_worker", "job_waiter", "job_teacher", "job_police", "job_driver", "job_sales", "job_boss", "job_freelance", "job_unemployed", "job_other"},
                new String[]{"保密", "在校生", "普通职工", "服务员", "老师", "警察", "司机", "销售", "老板", "自由职业", "待业中", "其他"});
        put(new String[]{"education_private", "education_middle", "education_high", "education_bachelor", "education_master", "education_other"},
                new String[]{"保密", "初中及以下", "高中", "本科", "硕士及以上", "其他"});
    }

    private DatingSharedProfileFormatter() {}

    private static void put(String[] keys, String[] values) {
        for (int i = 0; i < Math.min(keys.length, values.length); i++) LABELS.put(keys[i], values[i]);
    }

    public static void mergeSharedFields(DatingProfile target, DatingProfile source) {
        if (target == null || source == null) return;
        if (source.age > 0) target.age = source.age;
        if (source.sex >= 0) target.sex = source.sex;
        if (source.gender >= 0) target.gender = source.gender;
        if (!TextUtils.isEmpty(source.birthday)) target.birthday = source.birthday;
        if (!TextUtils.isEmpty(source.country)) target.country = source.country;
        if (!TextUtils.isEmpty(source.country_code)) target.country_code = source.country_code;
        if (!TextUtils.isEmpty(source.relationship_status)) target.relationship_status = display(source.relationship_status);
        if (!TextUtils.isEmpty(source.job_status)) target.job_status = display(source.job_status);
        if (!TextUtils.isEmpty(source.job)) target.job = display(source.job);
        if (!TextUtils.isEmpty(source.education)) target.education = display(source.education);

        target.personality_tags = choose(source.personality_tags, category(source.safeTags(), "personality_"));
        target.pet_tags = choose(source.pet_tags, category(source.safeTags(), "pet_"));
        target.sport_tags = choose(source.sport_tags, category(source.safeTags(), "sport_"));
        target.movie_tags = choose(source.movie_tags, category(source.safeTags(), "movie_"));

        if (TextUtils.isEmpty(target.relationship_status)) {
            target.relationship_status = first(category(source.safeTags(), "relationship_"));
        }
        if (TextUtils.isEmpty(target.job_status)) {
            target.job_status = first(category(source.safeTags(), "job_"));
        }
        if (TextUtils.isEmpty(target.education)) {
            target.education = first(category(source.safeTags(), "education_"));
        }
    }

    public static String basicLine(DatingProfile profile) {
        if (profile == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        if (profile.age > 0) parts.add(profile.age + "岁");
        if (profile.sex == 0 || profile.gender == 2) parts.add("女");
        else if (profile.sex == 1 || profile.gender == 1) parts.add("男");
        if (!TextUtils.isEmpty(profile.country)) parts.add(profile.country);
        return TextUtils.join(" · ", parts);
    }

    public static String relationshipLine(DatingProfile profile) {
        return profile == null ? "" : display(profile.relationship_status);
    }

    public static String personalityLine(DatingProfile profile) {
        return profile == null ? "" : joinDisplay(profile.safePersonalityTags());
    }

    public static String interestsLine(DatingProfile profile) {
        if (profile == null) return "";
        ArrayList<String> lines = new ArrayList<>();
        String pets = joinDisplay(profile.safePetTags());
        String sports = joinDisplay(profile.safeSportTags());
        String movies = joinDisplay(profile.safeMovieTags());
        if (!TextUtils.isEmpty(pets)) lines.add("宠物：" + pets);
        if (!TextUtils.isEmpty(sports)) lines.add("运动：" + sports);
        if (!TextUtils.isEmpty(movies)) lines.add("影视：" + movies);
        return TextUtils.join("\n", lines);
    }

    public static String careerLine(DatingProfile profile) {
        if (profile == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        String job = !TextUtils.isEmpty(profile.job_status) ? profile.job_status : profile.job;
        if (!TextUtils.isEmpty(job)) parts.add(display(job));
        if (!TextUtils.isEmpty(profile.education)) parts.add(display(profile.education));
        return TextUtils.join(" · ", parts);
    }

    public static String display(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String clean = raw.trim();
        String mapped = LABELS.get(clean);
        return TextUtils.isEmpty(mapped) ? clean : mapped;
    }

    public static String joinDisplay(List<String> values) {
        ArrayList<String> out = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                String display = display(value);
                if (!TextUtils.isEmpty(display) && !out.contains(display)) out.add(display);
            }
        }
        return TextUtils.join("、", out);
    }

    private static List<String> choose(List<String> direct, List<String> fallback) {
        if (direct != null && !direct.isEmpty()) {
            ArrayList<String> out = new ArrayList<>();
            for (String item : direct) {
                String display = display(item);
                if (!TextUtils.isEmpty(display) && !out.contains(display)) out.add(display);
            }
            return out;
        }
        return fallback;
    }

    private static ArrayList<String> category(List<String> tags, String prefix) {
        ArrayList<String> out = new ArrayList<>();
        if (tags == null) return out;
        for (String raw : tags) {
            if (TextUtils.isEmpty(raw)) continue;
            String clean = raw.trim();
            String lower = clean.toLowerCase(Locale.US);
            boolean matched = lower.startsWith(prefix);
            if (!matched) {
                for (Map.Entry<String, String> entry : LABELS.entrySet()) {
                    if (entry.getKey().startsWith(prefix) && TextUtils.equals(entry.getValue(), clean)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (matched) {
                String value = display(clean);
                if (!TextUtils.isEmpty(value) && !out.contains(value)) out.add(value);
            }
        }
        return out;
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? "" : values.get(0);
    }
}
