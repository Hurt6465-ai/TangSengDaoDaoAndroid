package com.chat.feedlist.model;

import java.util.ArrayList;
import java.util.List;

public class FeedListResponse {
    public List<FeedListItem> list;
    public List<FeedListItem> feeds;
    public List<FeedListItem> items;
    public String cursor;
    public int has_more;
    public long server_time;

    public List<FeedListItem> safeList() {
        if (list != null) return list;
        if (feeds != null) return feeds;
        if (items != null) return items;
        list = new ArrayList<>();
        return list;
    }
}
