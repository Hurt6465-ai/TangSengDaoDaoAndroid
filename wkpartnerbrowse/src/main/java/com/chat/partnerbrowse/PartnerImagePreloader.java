package com.chat.partnerbrowse;

import android.content.Context;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.base.config.WKApiConfig;
import com.chat.partnerbrowse.model.PartnerBrowseBean;

import java.util.List;

public final class PartnerImagePreloader {
    private PartnerImagePreloader() {}

    public static void preloadNextUser(Context context, List<PartnerBrowseBean> partners, int currentPosition) {
        if (context == null || partners == null) return;
        PartnerBrowseBean next = currentPosition + 1 < partners.size() ? partners.get(currentPosition + 1) : null;
        if (next == null) return;
        String image = firstImage(next);
        preload(context, image);
    }

    public static void preloadNextImage(Context context, List<String> images, int currentPosition) {
        if (context == null || images == null) return;
        if (currentPosition + 1 >= images.size()) return;
        preload(context, images.get(currentPosition + 1));
    }

    private static String firstImage(PartnerBrowseBean bean) {
        List<String> images = bean == null ? null : bean.getDisplayImagesSafe();
        if (images == null || images.isEmpty()) return "";
        return images.get(0);
    }

    private static void preload(Context context, String url) {
        if (context == null || TextUtils.isEmpty(url)) return;
        try {
            Context safe = context.getApplicationContext() == null ? context : context.getApplicationContext();
            Glide.with(safe)
                    .load(showUrl(url))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .preload();
        } catch (Throwable ignored) {
        }
    }

    private static String showUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        return WKApiConfig.getShowUrl(value);
    }
}
