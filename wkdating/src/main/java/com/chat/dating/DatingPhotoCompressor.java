package com.chat.dating;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * 一张原图生成两份文件：
 * 1. master：最长边 1440，详情页使用；
 * 2. card：720x1280 边界内，推荐页与预加载使用。
 */
public final class DatingPhotoCompressor {
    private DatingPhotoCompressor() {}

    public static final class PhotoException extends Exception {
        public final int messageRes;

        PhotoException(int messageRes) {
            super("photo_error_" + messageRes);
            this.messageRes = messageRes;
        }

        PhotoException(int messageRes, Throwable cause) {
            super("photo_error_" + messageRes, cause);
            this.messageRes = messageRes;
        }
    }

    public static final class Result {
        public final File master;
        public final File card;

        Result(File master, File card) {
            this.master = master;
            this.card = card;
        }
    }

    public static Result compress(File input, File outputDir) throws Exception {
        validateInput(input, outputDir);
        File master = null;
        File card = null;
        try {
            master = compressVariant(input, outputDir, "master",
                    DatingPhotoPolicy.MASTER_MAX_EDGE, DatingPhotoPolicy.MASTER_MAX_EDGE,
                    DatingPhotoPolicy.MASTER_TARGET_MAX_BYTES);
            card = compressVariant(input, outputDir, "card",
                    DatingPhotoPolicy.CARD_MAX_WIDTH, DatingPhotoPolicy.CARD_MAX_HEIGHT,
                    DatingPhotoPolicy.CARD_TARGET_MAX_BYTES);
            return new Result(master, card);
        } catch (Throwable error) {
            deleteQuietly(master);
            deleteQuietly(card);
            if (error instanceof PhotoException) throw (PhotoException) error;
            if (error instanceof Exception) throw (Exception) error;
            throw new PhotoException(R.string.dating_photo_compress_failed, error);
        }
    }

    /** 兼容旧调用：返回推荐卡派生图。 */
    public static File compressToWebp(File input, File outputDir) throws Exception {
        validateInput(input, outputDir);
        return compressVariant(input, outputDir, "card",
                DatingPhotoPolicy.CARD_MAX_WIDTH, DatingPhotoPolicy.CARD_MAX_HEIGHT,
                DatingPhotoPolicy.CARD_TARGET_MAX_BYTES);
    }

    private static void validateInput(File input, File outputDir) throws Exception {
        if (input == null || !input.isFile()) throw new PhotoException(R.string.dating_read_image_failed);
        if (input.length() > 20L * 1024L * 1024L) throw new PhotoException(R.string.dating_photo_too_large);
        if (outputDir == null) throw new PhotoException(R.string.dating_photo_cache_unavailable);
        if (!outputDir.exists() && !outputDir.mkdirs()) throw new PhotoException(R.string.dating_photo_cache_failed);
        if (!outputDir.isDirectory()) throw new PhotoException(R.string.dating_photo_cache_unavailable);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(input.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new PhotoException(R.string.dating_read_image_failed);
        if (Math.min(bounds.outWidth, bounds.outHeight) < DatingPhotoPolicy.UPLOAD_MIN_EDGE) {
            throw new PhotoException(R.string.dating_photo_too_small);
        }
    }

    private static File compressVariant(File input, File outputDir, String suffix,
                                        int maxWidth, int maxHeight, int targetBytes) throws Exception {
        Bitmap bitmap = null;
        File out = null;
        boolean completed = false;
        try {
            bitmap = decodeSampled(input, maxWidth, maxHeight);
            bitmap = applyExifOrientation(input, bitmap);
            bitmap = scaleInside(bitmap, maxWidth, maxHeight);
            if (bitmap == null) throw new PhotoException(R.string.dating_photo_process_failed);

            int quality = DatingPhotoPolicy.WEBP_START_QUALITY;
            byte[] bytes = encode(bitmap, quality);
            while (bytes.length > targetBytes && quality > DatingPhotoPolicy.WEBP_MIN_QUALITY) {
                quality -= 4;
                bytes = encode(bitmap, quality);
            }
            // 极复杂照片在最低质量仍超限时小步缩放，最多循环 6 次，避免无限重编码。
            int resizeCount = 0;
            while (bytes.length > targetBytes && resizeCount < 6
                    && Math.min(bitmap.getWidth(), bitmap.getHeight()) > 480) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, Math.round(bitmap.getWidth() * 0.90f)),
                        Math.max(1, Math.round(bitmap.getHeight() * 0.90f)), true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
                bytes = encode(bitmap, DatingPhotoPolicy.WEBP_MIN_QUALITY);
                resizeCount++;
            }

            out = new File(outputDir, "dating_" + suffix + "_"
                    + System.currentTimeMillis() + "_" + Math.abs(input.getName().hashCode()) + ".webp");
            try (FileOutputStream output = new FileOutputStream(out)) {
                output.write(bytes);
                output.flush();
            }
            completed = true;
            return out;
        } finally {
            if (!completed) deleteQuietly(out);
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    @NonNull
    private static Bitmap decodeSampled(File input, int maxWidth, int maxHeight) throws PhotoException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(input.getAbsolutePath(), bounds);
        int sample = 1;
        // 解码结果控制在目标约 2 倍内，之后再精确缩放，降低大图瞬时内存。
        while (bounds.outWidth / sample > maxWidth * 2 || bounds.outHeight / sample > maxHeight * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        Bitmap bitmap = BitmapFactory.decodeFile(input.getAbsolutePath(), options);
        if (bitmap == null) throw new PhotoException(R.string.dating_photo_decode_failed);
        return bitmap;
    }

    private static Bitmap scaleInside(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) return bitmap;
        float scale = Math.min(maxWidth / (float) width, maxHeight / (float) height);
        if (scale >= 1f) return bitmap;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    private static Bitmap applyExifOrientation(File input, Bitmap bitmap) {
        if (input == null || bitmap == null) return bitmap;
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
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.postRotate(90f); matrix.postScale(-1f, 1f); break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.postRotate(270f); matrix.postScale(-1f, 1f); break;
                default: return bitmap;
            }
            Bitmap transformed = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (transformed != bitmap) bitmap.recycle();
            return transformed;
        } catch (Throwable ignored) {
            return bitmap;
        }
    }

    private static byte[] encode(Bitmap bitmap, int quality) throws PhotoException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(256 * 1024)) {
            Bitmap.CompressFormat format;
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    format = Bitmap.CompressFormat.valueOf("WEBP_LOSSY");
                } catch (Throwable ignored) {
                    format = Bitmap.CompressFormat.WEBP;
                }
            } else {
                format = Bitmap.CompressFormat.WEBP;
            }
            if (!bitmap.compress(format, quality, output)) {
                throw new PhotoException(R.string.dating_photo_encode_failed);
            }
            return output.toByteArray();
        } catch (java.io.IOException impossible) {
            throw new PhotoException(R.string.dating_photo_encode_failed, impossible);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) file.delete();
    }
}
