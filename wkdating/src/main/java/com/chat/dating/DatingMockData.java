package com.chat.dating;

import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DatingMockData {
    private DatingMockData() {}

    public static DatingProfile demoMyProfile() {
        DatingProfile p = new DatingProfile();
        p.uid = "demo_me";
        p.name = "我";
        p.age = 26;
        p.gender = 1;
        p.country_code = "CN";
        p.country = "China";
        p.city = "Guangzhou";
        p.relationship_goal = "认真恋爱";
        p.cross_border_preference = "open_foreign";
        p.tags = Arrays.asList("真诚", "稳定长期", "可以异国恋");
        p.enabled = 1;
        return p;
    }

    public static List<DatingProfile> demoProfiles() {
        ArrayList<DatingProfile> list = new ArrayList<>();
        list.add(profile("demo_mina", "Mina", 22, 2, "MM", "Myanmar", "Yangon", "认真恋爱", "open_foreign",
                "喜欢慢慢了解，认真聊天。希望遇到情绪稳定、愿意每天分享生活的人。",
                Arrays.asList("res://dating_demo_rose", "res://dating_demo_violet", "res://dating_demo_sun"),
                Arrays.asList("认真恋爱", "可以异国恋"), Arrays.asList("温柔", "慢热", "真诚"), Arrays.asList("咖啡", "电影", "旅行"), "3.2km", "护士"));
        list.add(profile("demo_yuki", "Yuki", 24, 2, "JP", "Japan", "Tokyo", "先聊天了解", "open_foreign",
                "不太喜欢尬聊，喜欢自然一点的相处。可以先文字，再语音。",
                Arrays.asList("res://dating_demo_blue", "res://dating_demo_peach"),
                Arrays.asList("先聊天了解", "可以异国恋"), Arrays.asList("安静", "独立", "幽默"), Arrays.asList("摄影", "散步", "音乐"), "附近", "设计师"));
        list.add(profile("demo_lina", "Lina", 25, 2, "CN", "China", "Shenzhen", "奔结婚", "same_country_only",
                "想找同国稳定恋爱，三观合适比热闹重要。喜欢干净、靠谱、有边界感的人。",
                Arrays.asList("res://dating_demo_gold", "res://dating_demo_rose"),
                Arrays.asList("奔结婚", "只接受本国"), Arrays.asList("情绪稳定", "真诚", "慢热"), Arrays.asList("做饭", "宠物", "健身"), "18km", "教师"));
        list.add(profile("demo_anna", "Anna", 23, 2, "TH", "Thailand", "Bangkok", "稳定长期", "prefer_foreign",
                "喜欢旅行、美食和拍照。希望对方主动一点，但不要油腻。",
                Arrays.asList("res://dating_demo_peach", "res://dating_demo_sun"),
                Arrays.asList("稳定长期", "喜欢异国恋"), Arrays.asList("开朗", "主动", "浪漫"), Arrays.asList("美食", "拍照", "跳舞"), "全球", "学生"));
        list.add(profile("demo_chen", "Chen", 27, 1, "CN", "China", "Shanghai", "认真恋爱", "same_country_only",
                "工作比较忙，但会认真回复。希望找一个同频、稳定、愿意沟通的人。",
                Arrays.asList("res://dating_demo_blue", "res://dating_demo_gold"),
                Arrays.asList("认真恋爱", "本国恋"), Arrays.asList("成熟", "稳定", "幽默"), Arrays.asList("运动", "读书", "咖啡"), "42km", "工程师"));
        return list;
    }

    private static DatingProfile profile(String uid, String name, int age, int gender, String countryCode, String country, String city,
                                         String goal, String crossBorder, String intro, List<String> photos,
                                         List<String> loveTags, List<String> personalityTags, List<String> interestTags,
                                         String distance, String job) {
        DatingProfile p = new DatingProfile();
        p.uid = uid;
        p.name = name;
        p.age = age;
        p.gender = gender;
        p.country_code = countryCode;
        p.country = country;
        p.city = city;
        p.intent = "love";
        p.relationship_goal = goal;
        p.cross_border_preference = crossBorder;
        p.intro = intro;
        p.bio = intro;
        p.photos = photos;
        p.love_tags = loveTags;
        p.personality_tags = personalityTags;
        p.interest_tags = interestTags;
        p.communication_tags = Arrays.asList("重视安全感", "认真回复");
        p.tags = Arrays.asList(goal, city, job);
        p.distance_label = distance;
        p.job = job;
        p.profile_score = 92;
        p.enabled = 1;
        return p;
    }
}
