package com.chat.partner.profile;

import android.content.Context;
import android.text.TextUtils;

import com.chat.partner.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

final class PartnerTagLocalizer {
    private PartnerTagLocalizer() {
    }

    static final class Group {
        final int titleIndex;
        final boolean singleChoice;
        final String[] keys;

        Group(int titleIndex, boolean singleChoice, String... keys) {
            this.titleIndex = titleIndex;
            this.singleChoice = singleChoice;
            this.keys = keys;
        }

        String title(Context context) {
            String[] titles = context.getResources().getStringArray(R.array.partner_tag_group_titles);
            if (titleIndex >= 0 && titleIndex < titles.length) return titles[titleIndex];
            return "";
        }

        boolean containsKey(String key) {
            if (TextUtils.isEmpty(key)) return false;
            for (String item : keys) {
                if (TextUtils.equals(item, key)) return true;
            }
            return false;
        }
    }

    static final Group[] GROUPS = new Group[]{
            new Group(0, true, "tag_lang_native", "tag_lang_fluent", "tag_lang_intermediate", "tag_lang_beginner", "tag_lang_zero"),
            new Group(1, false, "tag_goal_partner", "tag_goal_work", "tag_goal_hobby", "tag_goal_friends", "tag_goal_culture", "tag_goal_abroad", "tag_goal_travel", "tag_goal_career", "tag_goal_daily", "tag_goal_dating"),
            new Group(2, true, "tag_rel_private", "tag_rel_single", "tag_rel_dating", "tag_rel_married", "tag_rel_divorced"),
            new Group(3, false, "tag_person_patient", "tag_person_outgoing", "tag_person_quiet", "tag_person_introvert", "tag_person_humor", "tag_person_gentle", "tag_person_serious", "tag_person_slow", "tag_person_eq", "tag_person_easy"),
            new Group(4, false, "tag_pet_dog", "tag_pet_cat", "tag_pet_rabbit", "tag_pet_bird", "tag_pet_fish", "tag_pet_hamster", "tag_pet_reptile", "tag_pet_animals"),
            new Group(5, false, "tag_sport_running", "tag_sport_basketball", "tag_sport_football", "tag_sport_badminton", "tag_sport_fitness", "tag_sport_yoga", "tag_sport_swimming", "tag_sport_cycling", "tag_sport_hiking", "tag_sport_skateboard"),
            new Group(6, false, "tag_media_movie", "tag_media_comedy", "tag_media_romance", "tag_media_action", "tag_media_mystery", "tag_media_documentary", "tag_media_tv", "tag_media_anime", "tag_media_variety", "tag_media_short_video"),
            new Group(7, true, "tag_work_private", "tag_work_student", "tag_work_employee", "tag_work_waiter", "tag_work_teacher", "tag_work_police", "tag_work_driver", "tag_work_sales", "tag_work_boss", "tag_work_freelance", "tag_work_unemployed", "tag_work_other"),
            new Group(8, true, "tag_edu_private", "tag_edu_junior", "tag_edu_high", "tag_edu_bachelor", "tag_edu_master", "tag_edu_other"),
            new Group(9, false, "tag_safe_polite", "tag_safe_no_harass", "tag_safe_no_contact", "tag_safe_in_app", "tag_safe_respect"),
            new Group(10, false, "tag_study_self", "tag_study_offline", "tag_study_online")
    };

    static Group[] groups() {
        return GROUPS;
    }

    static String label(Context context, String rawValue) {
        String key = normalizeKey(rawValue);
        int index = indexOfKey(key);
        if (index >= 0) {
            String[] labels = context.getResources().getStringArray(R.array.partner_tag_option_labels);
            if (index < labels.length) return labels[index];
        }
        return rawValue == null ? "" : rawValue.trim();
    }

    static ArrayList<String> normalizeKeys(Collection<String> values) {
        ArrayList<String> out = new ArrayList<>();
        if (values == null) return out;
        for (String value : values) {
            String key = normalizeKey(value);
            if (!TextUtils.isEmpty(key) && !out.contains(key)) out.add(key);
        }
        return out;
    }

    static String normalizeKey(String rawValue) {
        if (rawValue == null) return "";
        String value = rawValue.trim();
        if (TextUtils.isEmpty(value) || "null".equalsIgnoreCase(value)) return "";
        if (indexOfKey(value) >= 0) return value;
        String lower = value.toLowerCase(Locale.US).replace(" ", "_").replace("-", "_");
        if (indexOfKey(lower) >= 0) return lower;
        switch (value) {
            case "语言能力": return "tag_lang_fluent";
            case "母语者": return "tag_lang_native";
            case "流利交流": return "tag_lang_fluent";
            case "中高级": return "tag_lang_intermediate";
            case "初学者": return "tag_lang_beginner";
            case "零基础": return "tag_lang_zero";
            case "找语伴": return "tag_goal_partner";
            case "工作需要": return "tag_goal_work";
            case "兴趣爱好": return "tag_goal_hobby";
            case "交朋友": return "tag_goal_friends";
            case "文化交流": return "tag_goal_culture";
            case "准备留学": return "tag_goal_abroad";
            case "准备旅行": return "tag_goal_travel";
            case "职场提升": return "tag_goal_career";
            case "日常练习": return "tag_goal_daily";
            case "找对象": return "tag_goal_dating";
            case "保密": return "tag_rel_private";
            case "单身": return "tag_rel_single";
            case "在交往": return "tag_rel_dating";
            case "已婚": return "tag_rel_married";
            case "离异": return "tag_rel_divorced";
            case "有耐心": return "tag_person_patient";
            case "外向": return "tag_person_outgoing";
            case "安静": return "tag_person_quiet";
            case "内向": return "tag_person_introvert";
            case "幽默": return "tag_person_humor";
            case "温柔": return "tag_person_gentle";
            case "认真": return "tag_person_serious";
            case "慢热": return "tag_person_slow";
            case "高情商": return "tag_person_eq";
            case "好相处": return "tag_person_easy";
            case "狗": return "tag_pet_dog";
            case "猫": return "tag_pet_cat";
            case "兔子": return "tag_pet_rabbit";
            case "鸟": return "tag_pet_bird";
            case "鱼": return "tag_pet_fish";
            case "仓鼠": return "tag_pet_hamster";
            case "爬宠": return "tag_pet_reptile";
            case "喜欢动物": return "tag_pet_animals";
            case "跑步": return "tag_sport_running";
            case "篮球": return "tag_sport_basketball";
            case "足球": return "tag_sport_football";
            case "羽毛球": return "tag_sport_badminton";
            case "健身": return "tag_sport_fitness";
            case "瑜伽": return "tag_sport_yoga";
            case "游泳": return "tag_sport_swimming";
            case "骑行": return "tag_sport_cycling";
            case "徒步": return "tag_sport_hiking";
            case "滑板": return "tag_sport_skateboard";
            case "电影": return "tag_media_movie";
            case "喜剧片": return "tag_media_comedy";
            case "爱情片": return "tag_media_romance";
            case "动作片": return "tag_media_action";
            case "悬疑片": return "tag_media_mystery";
            case "纪录片": return "tag_media_documentary";
            case "电视剧": return "tag_media_tv";
            case "动漫": return "tag_media_anime";
            case "综艺": return "tag_media_variety";
            case "短视频": return "tag_media_short_video";
            case "在校生": return "tag_work_student";
            case "普通职工": return "tag_work_employee";
            case "服务员": return "tag_work_waiter";
            case "老师": return "tag_work_teacher";
            case "警察": return "tag_work_police";
            case "司机": return "tag_work_driver";
            case "销售": return "tag_work_sales";
            case "老板": return "tag_work_boss";
            case "自由职业": return "tag_work_freelance";
            case "待业中": return "tag_work_unemployed";
            case "其他": return "tag_work_other";
            case "初中及以下": return "tag_edu_junior";
            case "高中": return "tag_edu_high";
            case "本科": return "tag_edu_bachelor";
            case "硕士及以上": return "tag_edu_master";
            case "礼貌聊天": return "tag_safe_polite";
            case "拒绝骚扰": return "tag_safe_no_harass";
            case "不加联系方式": return "tag_safe_no_contact";
            case "平台内沟通": return "tag_safe_in_app";
            case "互相尊重": return "tag_safe_respect";
            case "自学": return "tag_study_self";
            case "线下学": return "tag_study_offline";
            case "线上学": return "tag_study_online";
            default: return value;
        }
    }

    private static int indexOfKey(String key) {
        if (TextUtils.isEmpty(key)) return -1;
        int index = 0;
        for (Group group : GROUPS) {
            for (String item : group.keys) {
                if (TextUtils.equals(item, key)) return index;
                index++;
            }
        }
        return -1;
    }
}
