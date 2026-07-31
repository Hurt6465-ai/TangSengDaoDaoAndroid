package com.chat.dating.model;

import java.util.List;

public class DatingRecommendResponse {
    public List<DatingProfile> items;
    public List<DatingProfile> list;
    public String cursor;
    public int has_more;

    public List<DatingProfile> getItems() {
        return items != null ? items : list;
    }

    public boolean hasMore() {
        return has_more == 1;
    }
}
