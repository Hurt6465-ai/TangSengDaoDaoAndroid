package com.chat.dating;

import android.content.Context;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.dating.model.DatingProfile;

import java.util.List;

public final class DatingImagePreloader {
    private DatingImagePreloader() {}

    public static void preloadAround(Context context, List<DatingProfile> profiles, int startIndex) {
        if (context == null || profiles == null || profiles.isEmpty()) return;
        int end = Math.min(profiles.size(), startIndex + 3);
        for (int i = Math.max(0, startIndex); i < end; i++) {
            DatingProfile profile = profiles.get(i);
            if (profile == null) continue;
            List<String> photos = profile.safePhotos();
            int photoEnd = Math.min(photos.size(), i == startIndex ? 2 : 1);
            for (int j = 0; j < photoEnd; j++) preload(context, photos.get(j));
        }
    }

    public static void preload(Context context, String url) {
        if (context == null || TextUtils.isEmpty(url)) return;
        try {
            Glide.with(context.getApplicationContext())
                    .load(DatingImageSource.resolve(context, url))
                    .override(900, 1400)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .preload();
        } catch (Throwable ignored) {
        }
    }
}
