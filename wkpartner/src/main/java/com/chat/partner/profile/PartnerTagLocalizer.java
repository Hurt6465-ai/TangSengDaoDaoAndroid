package com.chat.partner.profile;

import android.content.Context;
import android.text.TextUtils;

import com.chat.partner.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * 语伴标签本地化。
 * 后端保存稳定 key，前端按当前语言显示；同时兼容旧版本已保存的中文标签。
 */
public final class PartnerTagLocalizer {
    private PartnerTagLocalizer() {
    }

    public static final int MAX_TAGS = 20;

    public static final String[][] TAG_KEYS = new String[][]{
            {"lang_native", "lang_fluent", "lang_upper", "lang_beginner", "lang_zero"},
            {"goal_partner", "goal_work", "goal_interest", "goal_friend", "goal_culture", "goal_study_abroad", "goal_travel", "goal_career", "goal_daily", "goal_dating"},
            {"relationship_private", "relationship_single", "relationship_dating", "relationship_married", "relationship_divorced"},
            {"personality_patient", "personality_outgoing", "personality_quiet", "personality_introvert", "personality_funny", "personality_gentle", "personality_serious", "personality_slow_warm", "personality_eq", "personality_easygoing"},
            {"pet_dog", "pet_cat", "pet_rabbit", "pet_bird", "pet_fish", "pet_hamster", "pet_reptile", "pet_love_animals"},
            {"sport_running", "sport_basketball", "sport_football", "sport_badminton", "sport_fitness", "sport_yoga", "sport_swimming", "sport_cycling", "sport_hiking", "sport_skateboard"},
            {"movie_film", "movie_comedy", "movie_romance", "movie_action", "movie_mystery", "movie_documentary", "movie_tv", "movie_anime", "movie_variety", "movie_short_video"},
            {"job_private", "job_student", "job_worker", "job_waiter", "job_teacher", "job_police", "job_driver", "job_sales", "job_boss", "job_freelance", "job_unemployed", "job_other"},
            {"education_private", "education_middle", "education_high", "education_bachelor", "education_master", "education_other"},
            {"safe_polite", "safe_no_harass", "safe_no_contact", "safe_in_app", "safe_respect"},
            {"study_self", "study_offline", "study_online"}
    };

    private static final String[][] OLD_CN = new String[][]{
            {"母语者", "流利交流", "中高级", "初学者", "零基础"},
            {"找语伴", "工作需要", "兴趣爱好", "交朋友", "文化交流", "准备留学", "准备旅行", "职场提升", "日常练习", "找对象"},
            {"保密", "单身", "在交往", "已婚", "离异"},
            {"有耐心", "外向", "安静", "内向", "幽默", "温柔", "认真", "慢热", "高情商", "好相处"},
            {"狗", "猫", "兔子", "鸟", "鱼", "仓鼠", "爬宠", "喜欢动物"},
            {"跑步", "篮球", "足球", "羽毛球", "健身", "瑜伽", "游泳", "骑行", "徒步", "滑板"},
            {"电影", "喜剧片", "爱情片", "动作片", "悬疑片", "纪录片", "电视剧", "动漫", "综艺", "短视频"},
            {"保密", "在校生", "普通职工", "服务员", "老师", "警察", "司机", "销售", "老板", "自由职业", "待业中", "其他"},
            {"保密", "初中及以下", "高中", "本科", "硕士及以上", "其他"},
            {"礼貌聊天", "拒绝骚扰", "不加联系方式", "平台内沟通", "互相尊重"},
            {"自学", "线下学", "线上学"}
    };

    private static final HashMap<String, String> OLD_TO_KEY = new HashMap<>();

    static {
        for (int g = 0; g < TAG_KEYS.length; g++) {
            for (int i = 0; i < TAG_KEYS[g].length; i++) {
                OLD_TO_KEY.put(TAG_KEYS[g][i], TAG_KEYS[g][i]);
                OLD_TO_KEY.put(OLD_CN[g][i], TAG_KEYS[g][i]);
            }
        }
    }

    public static boolean isSingleGroup(int groupIndex) {
        return groupIndex == 0 || groupIndex == 2 || groupIndex == 7 || groupIndex == 8;
    }

    public static int groupIndexOf(String key) {
        if (TextUtils.isEmpty(key)) return -1;
        for (int g = 0; g < TAG_KEYS.length; g++) {
            for (String item : TAG_KEYS[g]) {
                if (TextUtils.equals(item, key)) return g;
            }
        }
        return -1;
    }

    public static String groupTitle(Context context, int groupIndex) {
        String[] groups = context.getResources().getStringArray(R.array.partner_tag_group_titles);
        if (groupIndex >= 0 && groupIndex < groups.length) return groups[groupIndex];
        return "";
    }

    public static String tagText(Context context, String key) {
        int groupIndex = groupIndexOf(key);
        if (groupIndex < 0) return key == null ? "" : key;
        int tagIndex = tagIndexOf(groupIndex, key);
        if (tagIndex < 0) return key;
        String[] values = context.getResources().getStringArray(tagArrayRes(groupIndex));
        if (tagIndex < values.length) return values[tagIndex];
        return key;
    }

    public static ArrayList<String> toKeyList(Collection<String> rawTags) {
        ArrayList<String> list = new ArrayList<>();
        if (rawTags == null) return list;
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String raw : rawTags) {
            String key = toKey(raw);
            if (!TextUtils.isEmpty(key) && set.size() < MAX_TAGS) set.add(key);
        }
        list.addAll(set);
        return list;
    }

    public static ArrayList<String> toDisplayList(Context context, Collection<String> rawTags) {
        ArrayList<String> list = new ArrayList<>();
        for (String key : toKeyList(rawTags)) {
            list.add(tagText(context, key));
        }
        return list;
    }

    public static String toKey(String raw) {
        if (raw == null) return "";
        String clean = raw.trim();
        if (TextUtils.isEmpty(clean)) return "";
        String direct = OLD_TO_KEY.get(clean);
        if (!TextUtils.isEmpty(direct)) return direct;
        String lower = clean.toLowerCase(Locale.US);
        direct = OLD_TO_KEY.get(lower);
        if (!TextUtils.isEmpty(direct)) return direct;
        return clean;
    }

    private static int tagIndexOf(int groupIndex, String key) {
        if (groupIndex < 0 || groupIndex >= TAG_KEYS.length) return -1;
        for (int i = 0; i < TAG_KEYS[groupIndex].length; i++) {
            if (TextUtils.equals(TAG_KEYS[groupIndex][i], key)) return i;
        }
        return -1;
    }

    private static int tagArrayRes(int groupIndex) {
        switch (groupIndex) {
            case 0:
                return R.array.partner_tags_language_level;
            case 1:
                return R.array.partner_tags_learning_goal;
            case 2:
                return R.array.partner_tags_relationship;
            case 3:
                return R.array.partner_tags_personality;
            case 4:
                return R.array.partner_tags_pets;
            case 5:
                return R.array.partner_tags_sports;
            case 6:
                return R.array.partner_tags_movies;
            case 7:
                return R.array.partner_tags_job;
            case 8:
                return R.array.partner_tags_education;
            case 9:
                return R.array.partner_tags_safety;
            case 10:
                return R.array.partner_tags_study_way;
            default:
                return R.array.partner_tags_learning_goal;
        }
    }
}
