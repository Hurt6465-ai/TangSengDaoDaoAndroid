package com.chat.feed.publish;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;

import androidx.exifinterface.media.ExifInterface;

import com.chat.feed.config.FeedConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/** 图片前端压缩：最长边限制 + EXIF 方向修正 + WebP，尽量接近 100KB。 */
public class FeedImageCompressor {
    public static File compressToWebp(File input, File outputDir) throws Exception {
        if (input == null || !input.exists()) throw new IllegalArgumentException("input not found");
        if (outputDir != null && !outputDir.exists()) outputDir.mkdirs();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(input.getAbsolutePath(), bounds);
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (max / sample > FeedConfig.IMAGE_MAX_LONG_EDGE) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(input.getAbsolutePath(), opts);
        if (bitmap == null) throw new IllegalStateException("decode failed");

        bitmap = applyExifOrientation(input, bitmap);

        File out = new File(outputDir == null ? input.getParentFile() : outputDir,
                "feed_img_" + System.currentTimeMillis() + ".webp");
        int quality = 86;
        byte[] bytes = encode(bitmap, quality);
        while (bytes.length > FeedConfig.IMAGE_TARGET_KB * 1024 && quality > 48) {
            quality -= 6;
            bytes = encode(bitmap, quality);
        }
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(bytes);
        fos.flush();
        fos.close();
        bitmap.recycle();
        return out;
    }

    private static Bitmap applyExifOrientation(File input, Bitmap bitmap) {
        if (input == null || bitmap == null) return bitmap;
        try {
            ExifInterface exif = new ExifInterface(input.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.postScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.postScale(1f, -1f);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.postRotate(90f);
                    matrix.postScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.postRotate(270f);
                    matrix.postScale(-1f, 1f);
                    break;
                default:
                    return bitmap;
            }
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Throwable ignored) {
            return bitmap;
        }
    }

    private static byte[] encode(Bitmap bitmap, int quality) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        bitmap.compress(format, quality, bos);
        return bos.toByteArray();
    }
}
