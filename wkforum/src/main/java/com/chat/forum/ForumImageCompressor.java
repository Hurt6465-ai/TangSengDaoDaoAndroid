package com.chat.forum;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Compresses a selected image for forum upload without retaining EXIF metadata. */
public final class ForumImageCompressor {
    private static final int MAX_LONG_EDGE = 1280;
    private static final int DECODE_LONG_EDGE = 2048;
    private static final int TARGET_BYTES = 100 * 1024;
    private static final int MAX_OUTPUT_BYTES = 110 * 1024;
    private static final int MIN_LONG_EDGE = 480;
    private static final int MAX_DECODE_ATTEMPTS = 3;
    private static final long STALE_FILE_AGE_MS = 24L * 60L * 60L * 1000L;

    private ForumImageCompressor() {
    }

    @NonNull
    public static File compress(@NonNull Context context, @NonNull Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options bounds = readBounds(resolver, uri);
        int sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap decoded = decodeWithRetry(resolver, uri, sampleSize);
        Bitmap working = decoded;
        File output = null;
        boolean success = false;
        try {
            int orientation = readOrientation(resolver, uri);
            Matrix matrix = orientationMatrix(orientation);
            if (!matrix.isIdentity()) {
                Bitmap rotated = Bitmap.createBitmap(working, 0, 0,
                        working.getWidth(), working.getHeight(), matrix, true);
                if (rotated != working) {
                    working.recycle();
                    working = rotated;
                }
            }

            Bitmap scaled = scaleDown(working, MAX_LONG_EDGE);
            if (scaled != working) {
                working.recycle();
                working = scaled;
            }

            File directory = new File(context.getCacheDir(), "forum_uploads");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("无法创建图片缓存目录");
            }
            pruneStaleFiles(directory);
            output = new File(directory, "forum_" + UUID.randomUUID() + ".webp");
            encodeToFile(working, output);
            success = true;
            return output;
        } catch (OutOfMemoryError error) {
            throw new IOException("图片尺寸过大，请选择较小图片", error);
        } finally {
            if (!working.isRecycled()) working.recycle();
            if (!success && output != null && output.exists()) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
            }
        }
    }

    @NonNull
    private static BitmapFactory.Options readBounds(ContentResolver resolver, Uri uri)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("无法读取图片");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("图片格式无效");
        }
        return bounds;
    }

    @NonNull
    private static Bitmap decodeWithRetry(ContentResolver resolver, Uri uri, int initialSample)
            throws IOException {
        int sample = Math.max(1, initialSample);
        OutOfMemoryError lastOom = null;
        for (int attempt = 0; attempt < MAX_DECODE_ATTEMPTS; attempt++) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inDither = false;
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) throw new IOException("无法读取图片");
                Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
                if (bitmap != null) return bitmap;
            } catch (OutOfMemoryError error) {
                lastOom = error;
            }
            sample = safeDouble(sample);
        }
        if (lastOom != null) throw new IOException("图片尺寸过大，无法解码", lastOom);
        throw new IOException("图片解码失败");
    }

    private static int calculateSampleSize(int width, int height) {
        int sample = 1;
        while (Math.max(ceilDiv(width, sample), ceilDiv(height, sample)) > DECODE_LONG_EDGE) {
            sample = safeDouble(sample);
        }
        return sample;
    }

    private static int safeDouble(int value) {
        return value >= 1 << 29 ? 1 << 30 : value * 2;
    }

    private static int ceilDiv(int value, int divisor) {
        return (int) ((value + (long) divisor - 1L) / divisor);
    }

    @NonNull
    private static Bitmap scaleDown(@NonNull Bitmap source, int maxLongEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longEdge = Math.max(width, height);
        if (longEdge <= maxLongEdge) return source;
        float ratio = maxLongEdge / (float) longEdge;
        return Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(width * ratio)),
                Math.max(1, Math.round(height * ratio)), true);
    }

    private static void encodeToFile(@NonNull Bitmap bitmap, @NonNull File output)
            throws IOException {
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        Bitmap current = bitmap;
        boolean ownsCurrent = false;
        boolean encoded = false;
        try {
            for (int pass = 0; pass < 8; pass++) {
                for (int quality = 82; quality >= 18; quality -= 4) {
                    writeBitmap(current, format, quality, output);
                    encoded = output.length() > 0;
                    if (encoded && output.length() <= TARGET_BYTES) return;
                }
                int longEdge = Math.max(current.getWidth(), current.getHeight());
                if (longEdge <= MIN_LONG_EDGE) break;
                int nextLongEdge = Math.max(MIN_LONG_EDGE, Math.round(longEdge * 0.80f));
                Bitmap smaller = scaleDown(current, nextLongEdge);
                if (smaller == current) break;
                if (ownsCurrent && !current.isRecycled()) current.recycle();
                current = smaller;
                ownsCurrent = true;
            }
        } finally {
            if (ownsCurrent && current != bitmap && !current.isRecycled()) current.recycle();
        }
        if (!encoded) throw new IOException("图片压缩失败");
        if (output.length() > MAX_OUTPUT_BYTES) {
            throw new IOException("图片压缩后仍超过110KB，请选择尺寸较小的图片");
        }
    }

    private static void writeBitmap(@NonNull Bitmap bitmap,
                                    @NonNull Bitmap.CompressFormat format,
                                    int quality, @NonNull File output) throws IOException {
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(output))) {
            if (!bitmap.compress(format, quality, stream)) {
                throw new IOException("图片压缩失败");
            }
            stream.flush();
        }
    }

    private static void pruneStaleFiles(@NonNull File directory) {
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) return;
        long threshold = System.currentTimeMillis() - STALE_FILE_AGE_MS;
        for (File file : files) {
            if (file == null || !file.isFile() || !file.getName().startsWith("forum_")) continue;
            if (file.lastModified() > 0L && file.lastModified() < threshold) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static int readOrientation(ContentResolver resolver, Uri uri) {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return ExifInterface.ORIENTATION_NORMAL;
            return new ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Throwable ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    @NonNull
    private static Matrix orientationMatrix(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                break;
        }
        return matrix;
    }
}
