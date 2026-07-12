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

/** content Uri -> 主图 + 720 卡片派生图 -> 分别上传。 */
public final class DatingPhotoUploadManager {
    public interface Callback {
        void onProgress(int progress, String message);
        void onSuccess(List<String> masterUrls, List<String> cardUrls);
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
            if (callback != null) callback.onSuccess(new ArrayList<>(), new ArrayList<>());
            return;
        }
        executor.execute(() -> doUpload(new ArrayList<>(uris), callback));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void doUpload(List<Uri> uris, Callback callback) {
        try {
            File cacheDir = new File(context.getCacheDir(), "dating_upload");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) throw new IllegalStateException(context.getString(R.string.dating_photo_cache_failed));
            ArrayList<String> masters = new ArrayList<>();
            ArrayList<String> cards = new ArrayList<>();
            for (int i = 0; i < uris.size(); i++) {
                File raw = null;
                DatingPhotoCompressor.Result compressed = null;
                try {
                    int base = Math.round(i * 100f / uris.size());
                    postProgress(callback, base, context.getString(R.string.dating_upload_processing, i + 1));
                    raw = copyUriToCache(uris.get(i), cacheDir);
                    compressed = DatingPhotoCompressor.compress(raw, cacheDir);

                    int masterProgress = Math.round((i + 0.35f) * 100f / uris.size());
                    postProgress(callback, masterProgress, context.getString(R.string.dating_upload_master, i + 1, uris.size()));
                    String masterUrl = uploadOne(compressed.master);

                    int cardProgress = Math.round((i + 0.68f) * 100f / uris.size());
                    postProgress(callback, cardProgress, context.getString(R.string.dating_upload_card, i + 1, uris.size()));
                    String cardUrl = uploadOne(compressed.card);
                    masters.add(masterUrl);
                    cards.add(TextUtils.isEmpty(cardUrl) ? masterUrl : cardUrl);
                } finally {
                    deleteQuietly(raw);
                    if (compressed != null) {
                        deleteQuietly(compressed.master);
                        deleteQuietly(compressed.card);
                    }
                }
            }
            mainHandler.post(() -> {
                if (callback != null) callback.onSuccess(masters, cards);
            });
        } catch (Exception e) {
            String message;
            if (e instanceof DatingPhotoCompressor.PhotoException) {
                message = context.getString(((DatingPhotoCompressor.PhotoException) e).messageRes);
            } else {
                message = TextUtils.isEmpty(e.getMessage()) ? context.getString(R.string.dating_upload_failed) : e.getMessage();
            }
            mainHandler.post(() -> {
                if (callback != null) callback.onError(message);
            });
        }
    }

    private String uploadOne(File file) throws Exception {
        DatingUploadUrl uploadUrl = awaitUploadUrl(file.getAbsolutePath());
        if (uploadUrl == null || TextUtils.isEmpty(uploadUrl.url)) throw new IllegalStateException(context.getString(R.string.dating_upload_url_failed));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");
        AtomicReference<Exception> error = new AtomicReference<>();
        String tag = "dating_upload_" + UUID.randomUUID();
        WKUploader.getInstance().upload(uploadUrl.url, file.getAbsolutePath(), tag, new WKUploader.IUploadBack() {
            @Override public void onSuccess(String url) {
                result.set(TextUtils.isEmpty(url) ? uploadUrl.path : url);
                latch.countDown();
            }
            @Override public void onError() {
                error.set(new IllegalStateException(context.getString(R.string.dating_upload_failed)));
                latch.countDown();
            }
        });
        if (!latch.await(120, TimeUnit.SECONDS)) throw new IllegalStateException(context.getString(R.string.dating_upload_timeout));
        if (error.get() != null) throw error.get();
        return normalizeUploadedPath(result.get());
    }

    private DatingUploadUrl awaitUploadUrl(String localPath) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DatingUploadUrl> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        DatingModel.getInstance().getUploadFileUrl(localPath, (code, msg, data) -> {
            if (code == HttpResponseCode.success && data != null) result.set(data);
            else error.set(new IllegalStateException(TextUtils.isEmpty(msg) ? context.getString(R.string.dating_upload_url_failed) : msg));
            latch.countDown();
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException(context.getString(R.string.dating_upload_url_timeout));
        if (error.get() != null) throw error.get();
        return result.get();
    }

    private File copyUriToCache(Uri uri, File dir) throws Exception {
        if (uri == null) throw new IllegalArgumentException(context.getString(R.string.dating_invalid_image_uri));
        String name = displayName(uri);
        String ext = ".jpg";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) ext = name.substring(dot);
        File out = new File(dir, "dating_src_" + System.currentTimeMillis() + "_" + Math.abs(uri.hashCode()) + ext);
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(out)) {
            if (input == null) throw new IllegalStateException(context.getString(R.string.dating_read_image_failed));
            byte[] buffer = new byte[128 * 1024];
            int len;
            while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
            output.flush();
        } catch (Throwable error) {
            deleteQuietly(out);
            if (error instanceof Exception) throw (Exception) error;
            throw new IllegalStateException(context.getString(R.string.dating_read_image_failed), error);
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
        if (value.startsWith("profile/")) return "file/preview/common/" + value;
        if (value.startsWith("dating/")) return "file/preview/common/" + value;
        return value;
    }

    private void postProgress(Callback callback, int progress, String message) {
        mainHandler.post(() -> {
            if (callback != null) callback.onProgress(Math.max(0, Math.min(100, progress)), message);
        });
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) file.delete();
    }
}
