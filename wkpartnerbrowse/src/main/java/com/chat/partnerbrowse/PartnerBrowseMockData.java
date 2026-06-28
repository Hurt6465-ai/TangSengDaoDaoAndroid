package com.chat.partnerbrowse;

import com.chat.partnerbrowse.model.PartnerBrowseBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class PartnerBrowseMockData {
    private PartnerBrowseMockData() {}

    static List<PartnerBrowseBean> create() {
        ArrayList<PartnerBrowseBean> list = new ArrayList<>();
        list.add(bean("mock_partner_001", "May", "喜欢中文歌，想找人每天练 10 分钟口语。", "MM", 22,
                Arrays.asList("MY"), Arrays.asList("ZH"), Arrays.asList("聊天", "口语", "音乐"), 8000,
                "https://picsum.photos/seed/partner001/720/1280"));
        list.add(bean("mock_partner_002", "Yuki", "正在学中文，也可以帮你练日语。", "JP", 24,
                Arrays.asList("JA"), Arrays.asList("ZH", "EN"), Arrays.asList("学习", "电影"), 0,
                "https://picsum.photos/seed/partner002/720/1280"));
        list.add(bean("mock_partner_003", "Nora", "想认识认真学习语言的人。", "TH", 21,
                Arrays.asList("TH"), Arrays.asList("ZH"), Arrays.asList("旅游", "美食"), 26000,
                "https://picsum.photos/seed/partner003/720/1280"));
        list.add(bean("mock_partner_004", "Hana", "晚上在线，适合互相纠正发音。", "KR", 25,
                Arrays.asList("KO"), Arrays.asList("ZH"), Arrays.asList("发音", "日常"), 0,
                "https://picsum.photos/seed/partner004/720/1280"));
        list.add(bean("mock_partner_005", "Aye", "想练中文聊天，不太会开头。", "MM", 20,
                Arrays.asList("MY"), Arrays.asList("ZH"), Arrays.asList("新手", "聊天"), 52000,
                "https://picsum.photos/seed/partner005/720/1280"));
        return list;
    }

    private static PartnerBrowseBean bean(String uid, String name, String intro, String country, int age,
                                          List<String> nativeLangs, List<String> learningLangs,
                                          List<String> tags, int distanceMeters, String image) {
        PartnerBrowseBean b = new PartnerBrowseBean();
        b.uid = uid;
        b.id = uid;
        b.name = name;
        b.username = name;
        b.intro = intro;
        b.country_code = country;
        b.age = age;
        b.follow = 0;
        b.vercode = "";
        b.profile_images = Arrays.asList(image);
        b.images = Arrays.asList(image);
        b.native_languages = nativeLangs;
        b.learning_languages = learningLangs;
        b.tags = tags;
        b.distance_meters = distanceMeters;
        b.online = 1;
        b.last_active_millis = System.currentTimeMillis();
        return b;
    }
}
