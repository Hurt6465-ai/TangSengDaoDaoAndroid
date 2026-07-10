package com.chat.learning;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

/** Small dependency-free cover loader with disk caching for directory cards. */
final class LearningCoverLoader {
    private static final int MAX_COVER_BYTES = 2 * 1024 * 1024;

    private LearningCoverLoader() {}

    static void load(ImageView target, String rawUrl, int version) {
        if (target == null || rawUrl == null || rawUrl.trim().length() == 0) return;
        Context app = target.getContext().getApplicationContext();
        String resolved = LearningRemoteContent.resolveUrl(app, rawUrl);
        if (resolved.length() == 0) return;
        String tag = resolved + "#" + version;
        target.setTag(tag);
        File cache = cacheFile(app, tag);
        if (cache.isFile()) {
            Bitmap bitmap = decode(cache.getAbsolutePath(), target.getResources().getDisplayMetrics().densityDpi);
            if (bitmap != null) {
                if (tag.equals(target.getTag())) target.setImageBitmap(bitmap);
                return;
            }
            try { cache.delete(); } catch (Throwable ignored) {}
        }
        LearningRemoteContent.execute(() -> {
            try {
                byte[] bytes = LearningRemoteContent.download(app, resolved, MAX_COVER_BYTES);
                LearningRemoteContent.atomicWrite(cache, bytes);
                Bitmap bitmap = decode(cache.getAbsolutePath(), target.getResources().getDisplayMetrics().densityDpi);
                if (bitmap == null) {
                    try { cache.delete(); } catch (Throwable ignored) {}
                    return;
                }
                target.post(() -> {
                    if (tag.equals(target.getTag())) target.setImageBitmap(bitmap);
                });
            } catch (Throwable ignored) {}
        });
    }

    private static File cacheFile(Context context, String key) {
        File dir = new File(context.getCacheDir(), "learning/covers");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, sha256(key) + ".img");
    }

    private static Bitmap decode(String path, int densityDpi) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > 1200) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inDensity = densityDpi;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte b : result) out.append(String.format(Locale.US, "%02x", b & 0xff));
            return out.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
