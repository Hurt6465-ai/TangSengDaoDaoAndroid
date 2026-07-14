package com.chat.feedlist.model;

import com.chat.base.net.entity.CommonResponse;

/** Server-authoritative counters returned by like/share writes. */
public class FeedListInteractionResponse extends CommonResponse {
    public int liked;
    public int like_count;
    public int share_count;
}
