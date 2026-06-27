package com.chat.feed.model;

import java.util.ArrayList;
import java.util.List;

public class CommentListResponse {
    public List<CommentBean> list;
    public String cursor;
    public int has_more;

    public List<CommentBean> safeList() {
        if (list == null) list = new ArrayList<>();
        return list;
    }
}
