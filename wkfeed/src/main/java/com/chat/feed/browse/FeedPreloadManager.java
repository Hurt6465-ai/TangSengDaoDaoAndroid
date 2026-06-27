package com.chat.feed.browse;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.chat.feed.model.FeedBean;
import com.chat.feed.model.FeedMedia;
import com.chat.feed.player.FeedPlayerManager;

import java.util.List;

public class FeedPreloadManager {
    public void preloadAround(Context context, List<FeedBean> list, int position) {
        if (context == null || list == null || position < 0) return;
        preloadNextFeed(context, safeGet(list, position + 1));
        preloadNextImage(context, safeGet(list, position));
    }

    private FeedBean safeGet(List<FeedBean> list, int position) {
        return position >= 0 && position < list.size() ? list.get(position) : null;
    }

    private void preloadNextFeed(Context context, FeedBean bean) {
        if (bean == null) return;
        FeedMedia media = bean.firstMedia();
        if (media == null) return;
        Glide.with(context).load(media.isVideo() ? media.thumbUrl() : media.displayUrl()).preload();
        if (media.isVideo()) {
            FeedPlayerManager.getInstance().preloadVideo(context, media.playUrl());
        }
    }

    private void preloadNextImage(Context context, FeedBean bean) {
        if (bean == null || bean.isVideo() || bean.safeMedia().size() < 2) return;
        FeedMedia media = bean.safeMedia().get(1);
        if (media != null) Glide.with(context).load(media.displayUrl()).preload();
    }
}
