package com.chat.feed.mock;

import com.chat.feed.model.CommentBean;
import com.chat.feed.model.CommentListResponse;
import com.chat.feed.model.FeedBean;
import com.chat.feed.model.FeedListResponse;
import com.chat.feed.model.FeedMedia;
import com.chat.feed.model.FeedRecommendationPolicy;
import com.chat.feed.model.FeedUser;

import java.util.ArrayList;
import java.util.List;

public class FeedMockData {
    private static final String[] IMAGE_URLS = new String[]{
            "https://picsum.photos/seed/wkfeed1/900/1400",
            "https://picsum.photos/seed/wkfeed2/900/1200",
            "https://picsum.photos/seed/wkfeed3/900/1500",
            "https://picsum.photos/seed/wkfeed4/900/1100",
            "https://picsum.photos/seed/wkfeed5/900/1350"
    };

    private static final String[] MOCK_VIDEO_URLS = new String[]{
            "https://www.w3schools.com/html/mov_bbb.mp4",
            "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
    };

    public static FeedListResponse feeds(String mode, String cursor, String uid, int limit) {
        int page = 0;
        try { page = cursor == null || cursor.length() == 0 ? 0 : Integer.parseInt(cursor); } catch (Exception ignored) {}
        List<FeedBean> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < limit; i++) {
            int index = page * limit + i;
            FeedBean bean = new FeedBean();
            bean.feed_id = (uid == null || uid.length() == 0 ? "discover" : uid) + "_mock_" + index;
            bean.uid = uid == null || uid.length() == 0 ? "user_" + (index % 8) : uid;
            bean.title = index % 4 == 0 ? "今天拍了一段练习视频" : (index % 3 == 0 ? "今天练中文，想找一个语伴一起聊天" : "分享一组日常照片");
            bean.text = bean.title;
            bean.created_at = now - index * 40L * 60L * 1000L;
            bean.updated_at = bean.created_at;
            bean.last_active_at = now - (index % 8) * 5L * 60L * 1000L;
            bean.like_count = 12 + index * 3;
            bean.comment_count = 2 + index % 9;
            bean.share_count = index % 4;
            bean.distance_meters = index % 5 == 0 ? 30000 : 0;
            bean.user = user(bean.uid, index);
            bean.media = media(index);
            list.add(bean);
        }
        FeedRecommendationPolicy.sortForDiscover(list);
        FeedListResponse resp = new FeedListResponse();
        resp.list = list;
        resp.cursor = String.valueOf(page + 1);
        resp.has_more = page < 4 ? 1 : 0;
        resp.server_time = now;
        return resp;
    }

    public static CommentListResponse comments(String feedId, String cursor, int limit) {
        int page = 0;
        try { page = cursor == null || cursor.length() == 0 ? 0 : Integer.parseInt(cursor); } catch (Exception ignored) {}
        CommentListResponse resp = new CommentListResponse();
        resp.list = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < limit; i++) {
            int index = page * limit + i;
            CommentBean c = new CommentBean();
            c.comment_id = feedId + "_c_" + index;
            c.uid = "comment_user_" + (index % 10);
            c.name = index % 2 == 0 ? "May" : "Aung";
            c.avatar = "";
            c.content = index % 2 == 0 ? "这个视频不错" : "可以一起练中文吗";
            c.created_at = now - index * 120000L;
            c.like_count = index % 7;
            c.reply_count = index % 3 == 0 ? 3 : 0;
            resp.list.add(c);
        }
        resp.cursor = String.valueOf(page + 1);
        resp.has_more = page < 2 ? 1 : 0;
        return resp;
    }

    private static FeedUser user(String uid, int index) {
        FeedUser u = new FeedUser();
        u.uid = uid;
        u.name = index % 2 == 0 ? "May" + index : "语伴" + index;
        u.age = 18 + index % 10;
        u.country_code = index % 2 == 0 ? "MM" : "CN";
        u.native_languages = index % 2 == 0 ? "MY" : "ZH";
        u.follow = index % 6 == 0 ? 1 : 0;
        return u;
    }

    private static List<FeedMedia> media(int index) {
        ArrayList<FeedMedia> out = new ArrayList<>();

        // 每 4 条插入一条视频 Mock。后端未完成前，必须能测 Media3 播放、切页、暂停/恢复、loading 和预缓存。
        if (index % 4 == 0) {
            FeedMedia m = new FeedMedia();
            m.type = FeedMedia.TYPE_VIDEO;
            m.cover_url = "https://picsum.photos/seed/wkfeed_video_" + index + "/720/1280";
            m.thumb_url = m.cover_url;
            m.display_url = m.cover_url;
            m.play_url_540p = MOCK_VIDEO_URLS[(index / 4) % MOCK_VIDEO_URLS.length];
            m.width = 720;
            m.height = 1280;
            m.duration_ms = 10000;
            out.add(m);
            return out;
        }

        int count = index % 3 == 0 ? 4 : (index % 3 == 1 ? 3 : 1);
        for (int i = 0; i < count; i++) {
            FeedMedia m = new FeedMedia();
            m.type = FeedMedia.TYPE_IMAGE;
            m.thumb_url = IMAGE_URLS[(index + i) % IMAGE_URLS.length];
            m.display_url = m.thumb_url;
            m.width = 900;
            m.height = 1200 + (i % 3) * 120;
            out.add(m);
        }
        return out;
    }
}
