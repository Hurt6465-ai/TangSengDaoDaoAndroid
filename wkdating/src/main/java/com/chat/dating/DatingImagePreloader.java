package com.chat.dating;

import android.content.Context;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.dating.model.DatingProfile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 推荐页唯一的预加载入口：当前人最多两张，后两人各一张。 */
public final class DatingImagePreloader {
    private static final int CARD_WIDTH = 720;
    private static final int CARD_HEIGHT = 1280;

    private DatingImagePreloader() {}

    public static void preloadAround(Context context, List<DatingProfile> profiles, int startIndex) {
        if (context == null || profiles == null || profiles.isEmpty()) return;
        Set<String> queued = new HashSet<>();
        int start = Math.max(0, startIndex);
        int end = Math.min(profiles.size(), start + 3);
        for (int i = start; i < end; i++) {
            DatingProfile profile = profiles.get(i);
            if (profile == null) continue;
            List<String> photos = profile.safeCardPhotos();
            int count = Math.min(photos.size(), i == start ? 2 : 1);
            for (int j = 0; j < count; j++) {
                String url = photos.get(j);
                if (!TextUtils.isEmpty(url) && queued.add(url.trim())) preload(context, url);
            }
        }
    }

    public static void preload(Context context, String url) {
        if (context == null || TextUtils.isEmpty(url)) return;
        try {
            Glide.with(context)
                    .load(DatingImageSource.resolve(context, url))
                    .override(CARD_WIDTH, CARD_HEIGHT)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .preload();
        } catch (Throwable ignored) {
        }
    }
}
