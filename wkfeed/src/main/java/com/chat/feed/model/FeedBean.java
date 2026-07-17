package com.chat.feed.model;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FeedBean implements Serializable {
    public String feed_id;
    public String uid;
    public String text;
    public String title;
    public long created_at;
    public long updated_at;
    public long last_active_at;
    public int like_count;
    public int comment_count;
    public int share_count;
    public int liked;
    public int distance_meters;
    public double score;
    public FeedUser user;
    public List<FeedMedia> media;

    public String stableKey() {
        if (!TextUtils.isEmpty(feed_id)) return feed_id;
        return uid + "_" + created_at;
    }

    public long stableId() {
        String key = stableKey();
        long h = 1125899906842597L;
        for (int i = 0; i < key.length(); i++) {
            h = 31L * h + key.charAt(i);
        }
        return h == RecyclerViewNoId.NO_ID ? 1L : h;
    }

    /**
     * Avoid importing RecyclerView into model just for NO_ID. RecyclerView.NO_ID = -1L.
     */
    private static final class RecyclerViewNoId {
        private static final long NO_ID = -1L;
    }

    public List<FeedMedia> safeMedia() {
        if (media == null) media = new ArrayList<>();
        return media;
    }

    public boolean isVideo() {
        List<FeedMedia> list = safeMedia();
        return list.size() > 0 && list.get(0) != null && list.get(0).isVideo();
    }

    public boolean isTikTok() {
        FeedMedia first = firstMedia();
        return first != null && first.isTikTok();
    }

    public FeedMedia firstMedia() {
        List<FeedMedia> list = safeMedia();
        return list.isEmpty() ? null : list.get(0);
    }

    public String displayTitle() {
        if (!TextUtils.isEmpty(title)) return title;
        return TextUtils.isEmpty(text) ? "" : text;
    }

    public String userName() {
        if (user != null && !TextUtils.isEmpty(user.name)) return user.name;
        return TextUtils.isEmpty(uid) ? "" : uid;
    }
}
