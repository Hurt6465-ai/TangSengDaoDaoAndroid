package com.chat.feed.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CommentBean implements Serializable {
    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_REPLY = 2;

    public String comment_id;
    public String parent_id;
    public String uid;
    public String name;
    public String avatar;
    public String avatar_cache_key;
    public String country_code;
    public String content;
    public long created_at;
    public int like_count;
    public int liked;
    public int reply_count;
    public int item_type = TYPE_COMMENT;
    public boolean local_sending;
    public boolean local_failed;
    public List<CommentBean> children;

    public long stableId() {
        String key = comment_id == null ? (uid + content + created_at) : comment_id;
        return key.hashCode();
    }

    public boolean isReply() {
        return item_type == TYPE_REPLY || (parent_id != null && parent_id.length() > 0);
    }

    public List<CommentBean> safeChildren() {
        if (children == null) children = new ArrayList<>();
        return children;
    }
}
