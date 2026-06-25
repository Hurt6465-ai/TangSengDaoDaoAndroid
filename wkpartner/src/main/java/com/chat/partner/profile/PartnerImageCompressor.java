package com.chat.partner.profile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * 语伴主页图片压缩工具：背景墙/照片墙优先 WebP，默认控制在 150KB 以内。
 * 下一步接上传时直接调用 compressToWebp150KB。
 */
public final class PartnerImageCompressor {
    private PartnerImageCompressor() {}

    public static File compressToWebp150KB(File input, File outputDir, String outputName) throws Exception {
        return compressToWebp(input, outputDir, outputName, 1080, 150 * 1024);
    }

    public static File compressToWebp(File input, File outputDir, String outputName, int maxSide, int maxBytes) throws Exception {
        if (input == null || !input.exists()) throw new IllegalArgumentException("input not exists");
        if (!outputDir.exists() && !outputDir.mkdirs()) throw new IllegalStateException("mkdir failed");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(input.getAbsolutePath(), bounds);
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / sample > maxSide) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        Bitmap bitmap = BitmapFactory.decodeFile(input.getAbsolutePath(), opts);
        if (bitmap == null) throw new IllegalStateException("decode failed");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int quality = 82;
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        do {
            out.reset();
            bitmap.compress(format, quality, out);
            quality -= 8;
        } while (out.size() > maxBytes && quality >= 50);

        File output = new File(outputDir, outputName.endsWith(".webp") ? outputName : outputName + ".webp");
        FileOutputStream fos = new FileOutputStream(output);
        fos.write(out.toByteArray());
        fos.flush();
        fos.close();
        bitmap.recycle();
        return output;
    }
}
