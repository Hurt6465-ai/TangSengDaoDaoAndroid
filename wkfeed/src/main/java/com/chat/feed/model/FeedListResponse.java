package com.chat.feed.model;

import java.util.ArrayList;
import java.util.List;

public class FeedListResponse {
    public List<FeedBean> list;
    public List<FeedBean> feeds;
    public List<FeedBean> items;
    public String cursor;
    public int has_more;
    public long server_time;

    public List<FeedBean> safeList() {
        if (list != null) return list;
        if (feeds != null) return feeds;
        if (items != null) return items;
        list = new ArrayList<>();
        return list;
    }
}
