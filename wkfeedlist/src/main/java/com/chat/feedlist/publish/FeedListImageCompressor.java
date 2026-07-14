package com.chat.feedlist.publish;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;

import androidx.exifinterface.media.ExifInterface;

import com.chat.feedlist.publish.FeedListPublishConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/** Strict client-side WebP compressor. Resolution is sacrificed before allowing a file over 200KB. */
public final class FeedListImageCompressor {
    private FeedListImageCompressor() {}

    public static File compressToWebp(File input, File outputDir) throws Exception {
        if (input == null || !input.exists()) throw new IllegalArgumentException("input not found");
        File dir = outputDir == null ? input.getParentFile() : outputDir;
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cache directory unavailable");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(input.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalStateException("decode bounds failed");

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > Math.round(FeedListPublishConfig.IMAGE_MAX_LONG_EDGE * 1.25f)) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeFile(input.getAbsolutePath(), options);
        if (decoded == null) throw new IllegalStateException("decode failed");

        Bitmap oriented = applyExifOrientation(input, decoded);
        Bitmap working = scaleLongestEdge(oriented, FeedListPublishConfig.IMAGE_MAX_LONG_EDGE);
        if (working != oriented && oriented != null && !oriented.isRecycled()) oriented.recycle();

        final int maxBytes = FeedListPublishConfig.IMAGE_TARGET_KB * 1024;
        byte[] result = null;
        try {
            for (int round = 0; round < 12; round++) {
                for (int quality = 86; quality >= 48; quality -= 6) {
                    result = encode(working, quality);
                    if (result.length <= maxBytes) break;
                }
                if (result != null && result.length <= maxBytes) break;
                int nextW = Math.max(1, Math.round(working.getWidth() * 0.86f));
                int nextH = Math.max(1, Math.round(working.getHeight() * 0.86f));
                if (nextW == working.getWidth() && nextH == working.getHeight()) break;
                Bitmap smaller = Bitmap.createScaledBitmap(working, nextW, nextH, true);
                if (smaller != working) working.recycle();
                working = smaller;
            }
            if (result == null || result.length > maxBytes) {
                // Last-resort dimension loop. Normal photos should never reach this branch.
                while (result != null && result.length > maxBytes && Math.max(working.getWidth(), working.getHeight()) > 240) {
                    Bitmap smaller = Bitmap.createScaledBitmap(working,
                            Math.max(1, Math.round(working.getWidth() * 0.78f)),
                            Math.max(1, Math.round(working.getHeight() * 0.78f)), true);
                    if (smaller != working) working.recycle();
                    working = smaller;
                    result = encode(working, 46);
                }
            }
            if (result == null || result.length > maxBytes) throw new IllegalStateException("图片压缩失败");

            File out = new File(dir, "feed_img_" + System.nanoTime() + ".webp");
            try (FileOutputStream stream = new FileOutputStream(out)) {
                stream.write(result);
                stream.flush();
            }
            return out;
        } finally {
            if (working != null && !working.isRecycled()) working.recycle();
        }
    }

    private static Bitmap scaleLongestEdge(Bitmap source, int maxLongEdge) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= maxLongEdge) return source;
        float scale = maxLongEdge * 1f / longest;
        return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * scale)),
                Math.max(1, Math.round(source.getHeight() * scale)), true);
    }

    private static Bitmap applyExifOrientation(File input, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(input.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90f); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180f); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270f); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.postScale(-1f, 1f); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.postScale(1f, -1f); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.postRotate(90f); matrix.postScale(-1f, 1f); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.postRotate(270f); matrix.postScale(-1f, 1f); break;
                default: return bitmap;
            }
            Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (result != bitmap) bitmap.recycle();
            return result;
        } catch (Throwable ignored) {
            return bitmap;
        }
    }

    private static byte[] encode(Bitmap bitmap, int quality) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30
                    ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
            if (!bitmap.compress(format, quality, output)) throw new IllegalStateException("webp encode failed");
            return output.toByteArray();
        }
    }
}
