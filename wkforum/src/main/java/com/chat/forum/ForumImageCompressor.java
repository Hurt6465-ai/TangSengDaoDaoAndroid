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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Compresses a selected image for forum upload without retaining EXIF metadata. */
public final class ForumImageCompressor {
    private static final int MAX_LONG_EDGE = 1440;
    private static final int TARGET_BYTES = 420 * 1024;
    private static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;

    private ForumImageCompressor() {
    }

    @NonNull
    public static File compress(@NonNull Context context, @NonNull Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("无法读取图片");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("图片格式无效");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("无法读取图片");
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        if (decoded == null) throw new IOException("图片解码失败");

        Bitmap oriented = decoded;
        try {
            int orientation = readOrientation(resolver, uri);
            Matrix matrix = orientationMatrix(orientation);
            if (!matrix.isIdentity()) {
                oriented = Bitmap.createBitmap(decoded, 0, 0,
                        decoded.getWidth(), decoded.getHeight(), matrix, true);
                if (oriented != decoded) decoded.recycle();
            }

            Bitmap scaled = scaleDown(oriented, MAX_LONG_EDGE);
            if (scaled != oriented) {
                oriented.recycle();
                oriented = scaled;
            }

            byte[] data = encode(oriented);
            File directory = new File(context.getCacheDir(), "forum_uploads");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("无法创建图片缓存目录");
            }
            File output = new File(directory, "forum_" + UUID.randomUUID() + ".webp");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                stream.write(data);
                stream.flush();
            }
            return output;
        } finally {
            if (!oriented.isRecycled()) oriented.recycle();
        }
    }

    private static int calculateSampleSize(int width, int height) {
        int sample = 1;
        while (Math.max(width / sample, height / sample) > MAX_LONG_EDGE * 2) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleDown(Bitmap source, int maxLongEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longEdge = Math.max(width, height);
        if (longEdge <= maxLongEdge) return source;
        float ratio = maxLongEdge / (float) longEdge;
        return Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(width * ratio)),
                Math.max(1, Math.round(height * ratio)), true);
    }

    private static byte[] encode(Bitmap bitmap) throws IOException {
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        byte[] last = null;
        for (int quality = 82; quality >= 56; quality -= 6) {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (!bitmap.compress(format, quality, output)) {
                    throw new IOException("图片压缩失败");
                }
                last = output.toByteArray();
            }
            if (last.length <= TARGET_BYTES) return last;
        }
        if (last == null || last.length == 0) throw new IOException("图片压缩失败");
        if (last.length > MAX_OUTPUT_BYTES) throw new IOException("图片压缩后仍然过大");
        return last;
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
