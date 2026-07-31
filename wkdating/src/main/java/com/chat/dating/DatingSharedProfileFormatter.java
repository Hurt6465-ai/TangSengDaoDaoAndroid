package com.chat.dating;

import android.content.Context;
import android.text.TextUtils;

import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 将主账号资料中的稳定标签转换成交友页的本地化可读字段。 */
public final class DatingSharedProfileFormatter {
    private static final String[] RELATIONSHIP_KEYS = {
            "relationship_private", "relationship_single", "relationship_dating",
            "relationship_married", "relationship_divorced"
    };
    private static final String[] RELATIONSHIP_LEGACY = {"保密", "单身", "在交往", "已婚", "离异"};

    private static final String[] PERSONALITY_KEYS = {
            "personality_patient", "personality_outgoing", "personality_quiet", "personality_introvert",
            "personality_funny", "personality_gentle", "personality_serious", "personality_slow_warm",
            "personality_eq", "personality_easygoing"
    };
    private static final String[] PERSONALITY_LEGACY = {
            "有耐心", "外向", "安静", "内向", "幽默", "温柔", "认真", "慢热", "高情商", "好相处"
    };

    private static final String[] PET_KEYS = {
            "pet_dog", "pet_cat", "pet_rabbit", "pet_bird", "pet_fish", "pet_hamster",
            "pet_reptile", "pet_love_animals"
    };
    private static final String[] PET_LEGACY = {"狗", "猫", "兔子", "鸟", "鱼", "仓鼠", "爬宠", "喜欢动物"};

    private static final String[] SPORT_KEYS = {
            "sport_running", "sport_basketball", "sport_football", "sport_badminton", "sport_fitness",
            "sport_yoga", "sport_swimming", "sport_cycling", "sport_hiking", "sport_skateboard"
    };
    private static final String[] SPORT_LEGACY = {
            "跑步", "篮球", "足球", "羽毛球", "健身", "瑜伽", "游泳", "骑行", "徒步", "滑板"
    };

    private static final String[] MOVIE_KEYS = {
            "movie_film", "movie_comedy", "movie_romance", "movie_action", "movie_mystery",
            "movie_documentary", "movie_tv", "movie_anime", "movie_variety", "movie_short_video"
    };
    private static final String[] MOVIE_LEGACY = {
            "电影", "喜剧片", "爱情片", "动作片", "悬疑片", "纪录片", "电视剧", "动漫", "综艺", "短视频"
    };

    private static final String[] JOB_KEYS = {
            "job_private", "job_student", "job_worker", "job_waiter", "job_teacher", "job_police",
            "job_driver", "job_sales", "job_boss", "job_freelance", "job_unemployed", "job_other"
    };
    private static final String[] JOB_LEGACY = {
            "保密", "在校生", "普通职工", "服务员", "老师", "警察", "司机", "销售", "老板", "自由职业", "待业中", "其他"
    };

    private static final String[] EDUCATION_KEYS = {
            "education_private", "education_middle", "education_high", "education_bachelor",
            "education_master", "education_other"
    };
    private static final String[] EDUCATION_LEGACY = {"保密", "初中及以下", "高中", "本科", "硕士及以上", "其他"};

    private DatingSharedProfileFormatter() {}

    /** 合并时保留稳定 code，不把当前界面语言的展示文案写回资料。 */
    public static void mergeSharedFields(DatingProfile target, DatingProfile source) {
        if (target == null || source == null) return;
        if (!TextUtils.isEmpty(source.name)) target.name = source.name;
        if (!TextUtils.isEmpty(source.username)) target.username = source.username;
        if (!TextUtils.isEmpty(source.avatar)) target.avatar = source.avatar;
        if (source.native_languages != null) target.native_languages = new ArrayList<>(source.native_languages);
        if (source.learning_languages != null) target.learning_languages = new ArrayList<>(source.learning_languages);
        if (source.age > 0) target.age = source.age;
        if (source.sex >= 0) target.sex = source.sex;
        if (source.gender >= 0) target.gender = source.gender;
        if (!TextUtils.isEmpty(source.birthday)) target.birthday = source.birthday;
        if (!TextUtils.isEmpty(source.country)) target.country = source.country;
        if (!TextUtils.isEmpty(source.country_code)) target.country_code = source.country_code;
        if (!TextUtils.isEmpty(source.relationship_status)) {
            target.relationship_status = canonicalFromGroupOrRaw(source.relationship_status, RELATIONSHIP_KEYS, RELATIONSHIP_LEGACY);
        }
        if (!TextUtils.isEmpty(source.job_status)) {
            target.job_status = canonicalFromGroupOrRaw(source.job_status, JOB_KEYS, JOB_LEGACY);
        }
        if (!TextUtils.isEmpty(source.job)) {
            target.job = canonicalFromGroupOrRaw(source.job, JOB_KEYS, JOB_LEGACY);
        }
        if (!TextUtils.isEmpty(source.education)) {
            target.education = canonicalFromGroupOrRaw(source.education, EDUCATION_KEYS, EDUCATION_LEGACY);
        }

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

    public static String basicLine(Context context, DatingProfile profile) {
        if (context == null || profile == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        if (profile.age > 0) parts.add(context.getString(R.string.dating_shared_age_value, profile.age));
        if (profile.isFemale()) parts.add(context.getString(R.string.dating_shared_gender_female));
        else if (profile.isMale()) parts.add(context.getString(R.string.dating_shared_gender_male));
        if (!TextUtils.isEmpty(profile.country)) parts.add(profile.country);
        return TextUtils.join(context.getString(R.string.dating_meta_separator), parts);
    }

    public static String relationshipLine(Context context, DatingProfile profile) {
        return profile == null ? "" : display(context, profile.relationship_status);
    }

    public static String personalityLine(Context context, DatingProfile profile) {
        return profile == null ? "" : joinDisplay(context, profile.safePersonalityTags());
    }

    public static String interestsLine(Context context, DatingProfile profile) {
        if (context == null || profile == null) return "";
        ArrayList<String> lines = new ArrayList<>();
        String pets = joinDisplay(context, profile.safePetTags());
        String sports = joinDisplay(context, profile.safeSportTags());
        String movies = joinDisplay(context, profile.safeMovieTags());
        if (!TextUtils.isEmpty(pets)) lines.add(context.getString(R.string.dating_shared_pets_value, pets));
        if (!TextUtils.isEmpty(sports)) lines.add(context.getString(R.string.dating_shared_sports_value, sports));
        if (!TextUtils.isEmpty(movies)) lines.add(context.getString(R.string.dating_shared_movies_value, movies));
        return TextUtils.join("\n", lines);
    }

    public static String careerLine(Context context, DatingProfile profile) {
        if (context == null || profile == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        String job = !TextUtils.isEmpty(profile.job_status) ? profile.job_status : profile.job;
        if (!TextUtils.isEmpty(job)) parts.add(display(context, job));
        if (!TextUtils.isEmpty(profile.education)) parts.add(display(context, profile.education));
        return TextUtils.join(context.getString(R.string.dating_meta_separator), parts);
    }

    /** 将旧中文值或稳定 code 统一成可保存的共享资料 code。 */
    public static String canonicalCode(String raw) {
        return canonical(raw);
    }

    public static String display(Context context, String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String clean = canonical(raw);
        if (context == null) return clean;
        String value = labelFromGroup(context, clean, RELATIONSHIP_KEYS, R.array.dating_shared_relationship_values);
        if (value != null) return value;
        value = labelFromGroup(context, clean, PERSONALITY_KEYS, R.array.dating_shared_personality_values);
        if (value != null) return value;
        value = labelFromGroup(context, clean, PET_KEYS, R.array.dating_shared_pet_values);
        if (value != null) return value;
        value = labelFromGroup(context, clean, SPORT_KEYS, R.array.dating_shared_sport_values);
        if (value != null) return value;
        value = labelFromGroup(context, clean, MOVIE_KEYS, R.array.dating_shared_movie_values);
        if (value != null) return value;
        value = labelFromGroup(context, clean, JOB_KEYS, R.array.dating_shared_job_values);
        if (value != null) return value;
        value = labelFromGroup(context, clean, EDUCATION_KEYS, R.array.dating_shared_education_values);
        return value == null ? clean : value;
    }

    public static String joinDisplay(Context context, List<String> values) {
        ArrayList<String> out = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                String label = display(context, value);
                if (!TextUtils.isEmpty(label) && !out.contains(label)) out.add(label);
            }
        }
        String separator = context == null ? ", " : context.getString(R.string.dating_list_separator);
        return TextUtils.join(separator, out);
    }

    private static String labelFromGroup(Context context, String code, String[] keys, int arrayRes) {
        int index = indexOf(keys, code);
        if (index < 0) return null;
        String[] labels = context.getResources().getStringArray(arrayRes);
        return index < labels.length ? labels[index] : code;
    }

    private static String canonical(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String clean = raw.trim();
        String code = canonicalFromGroup(clean, RELATIONSHIP_KEYS, RELATIONSHIP_LEGACY);
        if (code != null) return code;
        code = canonicalFromGroup(clean, PERSONALITY_KEYS, PERSONALITY_LEGACY);
        if (code != null) return code;
        code = canonicalFromGroup(clean, PET_KEYS, PET_LEGACY);
        if (code != null) return code;
        code = canonicalFromGroup(clean, SPORT_KEYS, SPORT_LEGACY);
        if (code != null) return code;
        code = canonicalFromGroup(clean, MOVIE_KEYS, MOVIE_LEGACY);
        if (code != null) return code;
        code = canonicalFromGroup(clean, JOB_KEYS, JOB_LEGACY);
        if (code != null) return code;
        code = canonicalFromGroup(clean, EDUCATION_KEYS, EDUCATION_LEGACY);
        return code == null ? clean : code;
    }


    private static String canonicalFromGroupOrRaw(String raw, String[] keys, String[] legacy) {
        if (TextUtils.isEmpty(raw)) return "";
        String clean = raw.trim();
        String code = canonicalFromGroup(clean, keys, legacy);
        return code == null ? clean : code;
    }

    private static String canonicalForPrefix(String raw, String prefix) {
        if ("relationship_".equals(prefix)) return canonicalFromGroupOrRaw(raw, RELATIONSHIP_KEYS, RELATIONSHIP_LEGACY);
        if ("personality_".equals(prefix)) return canonicalFromGroupOrRaw(raw, PERSONALITY_KEYS, PERSONALITY_LEGACY);
        if ("pet_".equals(prefix)) return canonicalFromGroupOrRaw(raw, PET_KEYS, PET_LEGACY);
        if ("sport_".equals(prefix)) return canonicalFromGroupOrRaw(raw, SPORT_KEYS, SPORT_LEGACY);
        if ("movie_".equals(prefix)) return canonicalFromGroupOrRaw(raw, MOVIE_KEYS, MOVIE_LEGACY);
        if ("job_".equals(prefix)) return canonicalFromGroupOrRaw(raw, JOB_KEYS, JOB_LEGACY);
        if ("education_".equals(prefix)) return canonicalFromGroupOrRaw(raw, EDUCATION_KEYS, EDUCATION_LEGACY);
        return canonical(raw);
    }

    private static String canonicalFromGroup(String raw, String[] keys, String[] legacy) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equalsIgnoreCase(raw)) return keys[i];
            if (i < legacy.length && legacy[i].equals(raw)) return keys[i];
        }
        return null;
    }

    private static List<String> choose(List<String> direct, List<String> fallback) {
        if (direct == null || direct.isEmpty()) return fallback;
        ArrayList<String> out = new ArrayList<>();
        for (String item : direct) {
            String code = canonical(item);
            if (!TextUtils.isEmpty(code) && !out.contains(code)) out.add(code);
        }
        return out;
    }

    private static ArrayList<String> category(List<String> tags, String prefix) {
        ArrayList<String> out = new ArrayList<>();
        if (tags == null) return out;
        for (String raw : tags) {
            if (TextUtils.isEmpty(raw)) continue;
            String code = canonicalForPrefix(raw, prefix);
            if (code.toLowerCase(Locale.US).startsWith(prefix) && !out.contains(code)) out.add(code);
        }
        return out;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return -1;
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? "" : values.get(0);
    }
}
