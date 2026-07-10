package com.chat.dating;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ud.WKUploader;
import com.chat.dating.model.DatingUploadUrl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 交友照片真实上传链路：content Uri -> 缓存 -> WebP 约 200KB -> common file/upload -> 返回可保存路径。
 */
public final class DatingPhotoUploadManager {
    public interface Callback {
        void onProgress(int progress, String message);
        void onSuccess(List<String> uploadedUrls);
        void onError(String message);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DatingPhotoUploadManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void upload(List<Uri> uris, Callback callback) {
        if (uris == null || uris.isEmpty()) {
            if (callback != null) callback.onSuccess(new ArrayList<>());
            return;
        }
        ArrayList<Uri> copy = new ArrayList<>(uris);
        executor.execute(() -> doUpload(copy, callback));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void doUpload(List<Uri> uris, Callback callback) {
        try {
            File cacheDir = new File(context.getCacheDir(), "dating_upload");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            ArrayList<String> uploaded = new ArrayList<>();
            for (int i = 0; i < uris.size(); i++) {
                File raw = null;
                File webp = null;
                try {
                    int base = Math.round(i * 100f / uris.size());
                    postProgress(callback, base, "正在压缩第 " + (i + 1) + " 张图片");
                    raw = copyUriToCache(uris.get(i), cacheDir);
                    webp = DatingPhotoCompressor.compressToWebp(raw, cacheDir);
                    int uploadBase = Math.round((i + 0.35f) * 100f / uris.size());
                    postProgress(callback, uploadBase, "正在上传第 " + (i + 1) + " 张图片");
                    uploaded.add(uploadOne(webp));
                } finally {
                    if (raw != null && raw.exists()) raw.delete();
                    if (webp != null && webp.exists()) webp.delete();
                }
            }
            mainHandler.post(() -> {
                if (callback != null) callback.onSuccess(uploaded);
            });
        } catch (Exception e) {
            String message = TextUtils.isEmpty(e.getMessage()) ? "图片上传失败" : e.getMessage();
            mainHandler.post(() -> {
                if (callback != null) callback.onError(message);
            });
        }
    }

    private String uploadOne(File file) throws Exception {
        DatingUploadUrl uploadUrl = awaitUploadUrl(file.getAbsolutePath());
        if (uploadUrl == null || TextUtils.isEmpty(uploadUrl.url)) throw new IllegalStateException("获取上传地址失败");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");
        AtomicReference<Exception> error = new AtomicReference<>();
        String tag = "dating_upload_" + UUID.randomUUID();
        WKUploader.getInstance().upload(uploadUrl.url, file.getAbsolutePath(), tag, new WKUploader.IUploadBack() {
            @Override
            public void onSuccess(String url) {
                result.set(TextUtils.isEmpty(url) ? uploadUrl.path : url);
                latch.countDown();
            }

            @Override
            public void onError() {
                error.set(new IllegalStateException("图片上传失败"));
                latch.countDown();
            }
        });
        if (!latch.await(120, TimeUnit.SECONDS)) throw new IllegalStateException("图片上传超时");
        if (error.get() != null) throw error.get();
        return normalizeUploadedPath(result.get());
    }

    private DatingUploadUrl awaitUploadUrl(String localPath) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DatingUploadUrl> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        DatingModel.getInstance().getUploadFileUrl(localPath, (code, msg, data) -> {
            if (code == HttpResponseCode.success && data != null) result.set(data);
            else error.set(new IllegalStateException(TextUtils.isEmpty(msg) ? "获取上传地址失败" : msg));
            latch.countDown();
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("获取上传地址超时");
        if (error.get() != null) throw error.get();
        return result.get();
    }

    private File copyUriToCache(Uri uri, File dir) throws Exception {
        if (uri == null) throw new IllegalArgumentException("图片地址无效");
        String name = displayName(uri);
        String ext = ".jpg";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) ext = name.substring(dot);
        File out = new File(dir, "dating_src_" + System.currentTimeMillis() + "_" + Math.abs(uri.hashCode()) + ext);
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(out)) {
            if (input == null) throw new IllegalStateException("无法读取图片");
            byte[] buffer = new byte[128 * 1024];
            int len;
            while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
            output.flush();
        }
        return out;
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return uri.getLastPathSegment() == null ? "photo.jpg" : uri.getLastPathSegment();
    }

    private String normalizeUploadedPath(String path) {
        if (TextUtils.isEmpty(path)) return "";
        String value = path.trim();
        String base = com.chat.base.config.WKApiConfig.baseUrl;
        if (!TextUtils.isEmpty(base) && value.startsWith(base)) value = value.substring(base.length());
        while (value.startsWith("/")) value = value.substring(1);
        if (value.startsWith("file/preview/")) return value;
        if (value.startsWith("common/")) return "file/preview/" + value;
        if (value.startsWith("dating/")) return "file/preview/common/" + value;
        return value;
    }

    private void postProgress(Callback callback, int progress, String message) {
        mainHandler.post(() -> {
            if (callback != null) callback.onProgress(Math.max(0, Math.min(100, progress)), message);
        });
    }
}
