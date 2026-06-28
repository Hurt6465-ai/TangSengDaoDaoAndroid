package com.chat.feed.publish;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import com.chat.feed.config.FeedConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 手机端视频压缩：Media3 Transformer + 系统硬件 MediaCodec + OpenGL。
 *
 * 不走服务器，不引入 FFmpeg，APK 体积和授权风险都更低。
 * 第一版策略：
 * 1. 检测时长/尺寸/大小；
 * 2. 生成 WebP 封面；
 * 3. 需要时转成 MP4(H.264 + AAC)，竖屏约 540x960，横屏约 960x540；
 * 4. 压缩后仍超过上限则拒绝上传。
 */
public class FeedVideoCompressor {
    public interface ProgressListener {
        void onProgress(int progress, String message);
    }

    public static PreparedVideo prepare(Context context, File input, File outputDir) throws Exception {
        return prepare(context, input, outputDir, null);
    }

    public static PreparedVideo prepare(Context context, File input, File outputDir, ProgressListener listener) throws Exception {
        if (input == null || !input.exists()) throw new IllegalArgumentException("video not found");
        if (outputDir != null && !outputDir.exists()) outputDir.mkdirs();

        PreparedVideo out = readMetadataAndCover(input, outputDir == null ? input.getParentFile() : outputDir);
        out.originalFile = input;
        out.videoFile = input;
        out.sizeBytes = input.length();
        out.mime = isLikelyMp4(input) ? "video/mp4" : "video/*";

        if (out.durationMs > FeedConfig.VIDEO_MAX_DURATION_SECONDS * 1000L) {
            throw new IllegalStateException("视频最长 " + FeedConfig.VIDEO_MAX_DURATION_SECONDS + " 秒，请选择更短的视频");
        }

        FeedVideoCompressPolicy policy = new FeedVideoCompressPolicy();
        boolean needCompress = policy.needCompress(out.width, out.height, input.length());
        long maxBytes = FeedConfig.VIDEO_MAX_UPLOAD_MB * 1024L * 1024L;

        if (needCompress) {
            if (!FeedConfig.ENABLE_CLIENT_VIDEO_TRANSCODE) {
                throw new IllegalStateException("视频过大，请开启手机端压缩或选择更短的视频");
            }
            if (context == null) throw new IllegalStateException("视频压缩初始化失败");
            notifyProgress(listener, 0, "正在压缩视频");
            File compressed = transcodeWithMedia3(context.getApplicationContext(), input,
                    outputDir == null ? input.getParentFile() : outputDir, out, policy, listener);
            if (compressed != null && compressed.exists() && compressed.length() > 0) {
                // 如果压缩后反而更大，且原文件也不超上限，则保留原文件，避免浪费清晰度。
                if (compressed.length() < input.length() || input.length() > maxBytes) {
                    out.videoFile = compressed;
                    out.sizeBytes = compressed.length();
                    out.mime = "video/mp4";
                    int targetHeight = policy.chooseOutputHeight(out.width, out.height);
                    if (targetHeight > 0 && out.width > 0 && out.height > 0) {
                        float ratio = targetHeight / (float) out.height;
                        out.width = Math.max(1, Math.round(out.width * ratio));
                        out.height = targetHeight;
                    }
                }
            }
            notifyProgress(listener, 100, "视频压缩完成");
        }

        if (out.videoFile.length() > maxBytes) {
            throw new IllegalStateException("压缩后仍超过 " + FeedConfig.VIDEO_MAX_UPLOAD_MB + "MB，请选择更短的视频");
        }
        return out;
    }

    private static PreparedVideo readMetadataAndCover(File input, File coverDir) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        PreparedVideo out = new PreparedVideo();
        try {
            retriever.setDataSource(input.getAbsolutePath());
            out.width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            out.height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            out.durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (("90".equals(rotation) || "270".equals(rotation)) && out.width > 0 && out.height > 0) {
                int tmp = out.width;
                out.width = out.height;
                out.height = tmp;
            }
            Bitmap frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame != null) {
                out.coverFile = writeCover(frame, coverDir == null ? input.getParentFile() : coverDir);
                frame.recycle();
            }
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    @OptIn(markerClass = UnstableApi.class)
    private static File transcodeWithMedia3(Context context,
                                            File input,
                                            File outputDir,
                                            PreparedVideo meta,
                                            FeedVideoCompressPolicy policy,
                                            ProgressListener progressListener) throws Exception {
        if (outputDir != null && !outputDir.exists()) outputDir.mkdirs();
        File output = new File(outputDir, "feed_video_" + System.currentTimeMillis() + ".mp4");
        if (output.exists()) output.delete();

        int targetHeight = policy.chooseOutputHeight(meta.width, meta.height);
        ArrayList<Effect> videoEffects = new ArrayList<>();
        if (targetHeight > 0 && meta.height > 0 && targetHeight < meta.height) {
            videoEffects.add(Presentation.createForHeight(targetHeight));
        }

        MediaItem inputMediaItem = new MediaItem.Builder().setUri(Uri.fromFile(input)).build();
        EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(inputMediaItem)
                .setEffects(new Effects(Collections.emptyList(), videoEffects))
                .build();

        Handler mainHandler = new Handler(Looper.getMainLooper());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        AtomicReference<Transformer> transformerRef = new AtomicReference<>();

        Runnable startRunnable = () -> {
            Transformer transformer = new Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(@NonNull Composition composition, @NonNull ExportResult exportResult) {
                            notifyProgress(progressListener, 100, "视频压缩完成");
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull Composition composition,
                                            @NonNull ExportResult exportResult,
                                            @NonNull ExportException exception) {
                            errorRef.set(exception);
                            latch.countDown();
                        }
                    })
                    .build();
            transformerRef.set(transformer);
            try {
                transformer.start(editedMediaItem, output.getAbsolutePath());
                ProgressHolder holder = new ProgressHolder();
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Transformer t = transformerRef.get();
                        if (t == null || latch.getCount() == 0) return;
                        int state = t.getProgress(holder);
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            notifyProgress(progressListener, Math.max(1, Math.min(99, holder.progress)), "正在压缩视频");
                        }
                        mainHandler.postDelayed(this, 350L);
                    }
                }, 350L);
            } catch (Exception e) {
                errorRef.set(e);
                latch.countDown();
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            startRunnable.run();
        } else {
            mainHandler.post(startRunnable);
        }

        if (!latch.await(10, TimeUnit.MINUTES)) {
            Transformer transformer = transformerRef.get();
            if (transformer != null) {
                mainHandler.post(transformer::cancel);
            }
            throw new IllegalStateException("视频压缩超时，请选择更短的视频");
        }
        if (errorRef.get() != null) {
            throw new IllegalStateException("视频压缩失败：" + errorRef.get().getMessage(), errorRef.get());
        }
        if (!output.exists() || output.length() <= 0) {
            throw new IllegalStateException("视频压缩失败，输出文件为空");
        }
        return output;
    }

    private static void notifyProgress(ProgressListener listener, int progress, String message) {
        if (listener != null) listener.onProgress(progress, message);
    }

    private static File writeCover(Bitmap bitmap, File dir) throws Exception {
        if (dir != null && !dir.exists()) dir.mkdirs();
        Bitmap scaled = bitmap;
        int maxEdge = 720;
        int longEdge = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longEdge > maxEdge) {
            float ratio = maxEdge / (float) longEdge;
            scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * ratio), Math.round(bitmap.getHeight() * ratio), true);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int quality = 78;
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        scaled.compress(format, quality, bos);
        while (bos.size() > 180 * 1024 && quality > 45) {
            bos.reset();
            quality -= 6;
            scaled.compress(format, quality, bos);
        }
        if (scaled != bitmap) scaled.recycle();
        File out = new File(dir, "feed_video_cover_" + System.currentTimeMillis() + ".webp");
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(bos.toByteArray());
        fos.flush();
        fos.close();
        return out;
    }

    private static boolean isLikelyMp4(File file) {
        String name = file == null ? "" : file.getName().toLowerCase(Locale.US);
        return name.endsWith(".mp4") || name.endsWith(".m4v");
    }

    private static int parseInt(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        if (TextUtils.isEmpty(value)) return 0L;
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    public static class PreparedVideo {
        public File originalFile;
        public File videoFile;
        public File coverFile;
        public int width;
        public int height;
        public long durationMs;
        public long sizeBytes;
        public String mime;
    }
}
