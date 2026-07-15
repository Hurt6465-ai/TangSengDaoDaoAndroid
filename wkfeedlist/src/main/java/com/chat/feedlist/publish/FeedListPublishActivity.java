package com.chat.feedlist.publish;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.ud.WKProgressManager;
import com.chat.base.net.ud.WKUploader;
import com.chat.feedlist.FeedListModel;
import com.chat.feedlist.R;

import com.chat.feedlist.model.FeedListTikTokPreview;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Image or TikTok publisher. Local video publishing remains in the old full-screen plugin only. */
public class FeedListPublishActivity extends AppCompatActivity {
    private static final int REQ_PICK_IMAGES = 101;

    public static void openForResult(Activity activity, int requestCode) {
        if (activity == null || activity.isFinishing()) return;
        Intent intent = new Intent(activity, FeedListPublishActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }

    private EditText textEt;
    private TextView pickImagesBtn;
    private TextView pickTikTokBtn;
    private TextView publishBtn;
    private TextView hintTv;
    private TextView progressTv;
    private ProgressBar progressBar;
    private LinearLayout previewRow;
    private LinearLayout tiktokBox;
    private EditText tiktokUrlEt;
    private TextView tiktokResolveBtn;
    private FrameLayout tiktokPreviewBox;
    private ImageView tiktokCoverIv;
    private TextView tiktokTitleTv;

    private final ArrayList<Uri> imageUris = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private FeedListTikTokPreview tiktokPreview;
    private boolean tiktokMode;
    private volatile boolean uploading;
    private volatile boolean resolvingTikTok;
    private boolean updatingTikTokUrl;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_feedlist_publish);
            bindViews();
            bindListeners();
            refreshState();
            worker.execute(this::cleanupOldUploadCache);
        } catch (Throwable error) {
            Toast.makeText(this, getString(R.string.feedlist_publish_open_failed), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void bindViews() {
        textEt = findViewById(R.id.feedlistPublishTextEt);
        pickImagesBtn = findViewById(R.id.feedlistPickImagesBtn);
        pickTikTokBtn = findViewById(R.id.feedlistPickTikTokBtn);
        publishBtn = findViewById(R.id.feedlistPublishSubmitBtn);
        hintTv = findViewById(R.id.feedlistPublishHintTv);
        progressTv = findViewById(R.id.feedlistUploadProgressTv);
        progressBar = findViewById(R.id.feedlistUploadProgressBar);
        previewRow = findViewById(R.id.feedlistPreviewRow);
        tiktokBox = findViewById(R.id.feedlistTikTokBox);
        tiktokUrlEt = findViewById(R.id.feedlistTikTokUrlEt);
        tiktokResolveBtn = findViewById(R.id.feedlistTikTokResolveBtn);
        tiktokPreviewBox = findViewById(R.id.feedlistTikTokPreview);
        tiktokCoverIv = findViewById(R.id.feedlistTikTokCoverIv);
        tiktokTitleTv = findViewById(R.id.feedlistTikTokTitleTv);
    }

    private void bindListeners() {
        findViewById(R.id.feedlistPublishCloseTv).setOnClickListener(v -> {
            if (uploading || resolvingTikTok) toast(getString(R.string.feedlist_publish_wait_upload));
            else finish();
        });
        pickImagesBtn.setOnClickListener(v -> {
            if (uploading || resolvingTikTok) return;
            tiktokMode = false;
            clearTikTok();
            openImagePicker();
        });
        pickTikTokBtn.setOnClickListener(v -> {
            if (uploading || resolvingTikTok) return;
            tiktokMode = true;
            imageUris.clear();
            renderImagePreviews();
            refreshState();
        });
        tiktokResolveBtn.setOnClickListener(v -> resolveTikTok());
        tiktokUrlEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (updatingTikTokUrl || tiktokPreview == null) return;
                String value = editable == null ? "" : editable.toString().trim();
                if (!TextUtils.equals(value, tiktokPreview.url)) {
                    clearTikTok();
                    refreshState();
                }
            }
        });
        publishBtn.setOnClickListener(v -> startPublish());
    }

    private void openImagePicker() {
        int remain = FeedListPublishConfig.IMAGE_MAX_SELECT_COUNT - imageUris.size();
        if (remain <= 0) {
            toast(getString(R.string.feedlist_publish_max_images, FeedListPublishConfig.IMAGE_MAX_SELECT_COUNT));
            return;
        }
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            if (remain > 1) {
                int max = Math.min(remain, MediaStore.getPickImagesMaxLimit());
                intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, max);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, remain > 1);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_IMAGES);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        tiktokMode = false;
        clearTikTok();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount() && imageUris.size() < FeedListPublishConfig.IMAGE_MAX_SELECT_COUNT; i++) {
                addImage(clip.getItemAt(i).getUri());
            }
        } else addImage(data.getData());
        renderImagePreviews();
        refreshState();
    }

    private void addImage(Uri uri) {
        if (uri == null || imageUris.size() >= FeedListPublishConfig.IMAGE_MAX_SELECT_COUNT) return;
        for (Uri old : imageUris) if (uri.equals(old)) return;
        imageUris.add(uri);
    }

    private void resolveTikTok() {
        String url = tiktokUrlEt.getText() == null ? "" : tiktokUrlEt.getText().toString().trim();
        if (TextUtils.isEmpty(url) || resolvingTikTok || uploading) {
            if (TextUtils.isEmpty(url)) toast(getString(R.string.feedlist_tiktok_publish_url_required));
            return;
        }
        resolvingTikTok = true;
        setInputsEnabled(false);
        tiktokResolveBtn.setText(R.string.feedlist_tiktok_publish_resolving);
        FeedListModel.getInstance().tiktokPreview(url, new IRequestResultListener<>() {
            @Override public void onSuccess(FeedListTikTokPreview result) {
                resolvingTikTok = false;
                if (isFinishing() || isDestroyed()) return;
                setInputsEnabled(true);
                tiktokResolveBtn.setText(R.string.feedlist_tiktok_publish_preview);
                if (result == null || TextUtils.isEmpty(result.video_id) || TextUtils.isEmpty(result.cover_url)) {
                    toast(getString(R.string.feedlist_tiktok_publish_resolve_failed));
                    return;
                }
                tiktokPreview = result;
                updatingTikTokUrl = true;
                tiktokUrlEt.setText(result.url);
                tiktokUrlEt.setSelection(tiktokUrlEt.length());
                updatingTikTokUrl = false;
                tiktokPreviewBox.setVisibility(View.VISIBLE);
                tiktokTitleTv.setText(TextUtils.isEmpty(result.title) ? "TikTok" : result.title);
                Glide.with(FeedListPublishActivity.this).load(result.cover_url).centerCrop().into(tiktokCoverIv);
                refreshState();
            }

            @Override public void onFail(int code, String msg) {
                resolvingTikTok = false;
                if (isFinishing() || isDestroyed()) return;
                setInputsEnabled(true);
                tiktokResolveBtn.setText(R.string.feedlist_tiktok_publish_preview);
                toast(TextUtils.isEmpty(msg) ? getString(R.string.feedlist_tiktok_publish_resolve_failed) : msg);
            }
        });
    }

    private void clearTikTok() {
        tiktokPreview = null;
        if (tiktokPreviewBox != null) tiktokPreviewBox.setVisibility(View.GONE);
        if (tiktokCoverIv != null) Glide.with(this).clear(tiktokCoverIv);
    }

    private void refreshState() {
        tiktokBox.setVisibility(tiktokMode ? View.VISIBLE : View.GONE);
        hintTv.setText(getString(R.string.feedlist_publish_storage_hint));
        boolean hasMedia = !imageUris.isEmpty() || (tiktokMode && tiktokPreview != null);
        publishBtn.setEnabled(hasMedia && !uploading && !resolvingTikTok);
        publishBtn.setAlpha(publishBtn.isEnabled() ? 1f : 0.45f);
    }

    private void renderImagePreviews() {
        previewRow.removeAllViews();
        for (int i = 0; i < imageUris.size(); i++) {
            ImageView image = new ImageView(this);
            int size = dp(72);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, dp(8), 0);
            image.setLayoutParams(params);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(imageUris.get(i)).centerCrop().into(image);
            final int index = i;
            image.setOnClickListener(v -> {
                if (!uploading && index < imageUris.size()) {
                    imageUris.remove(index);
                    renderImagePreviews();
                    refreshState();
                }
            });
            previewRow.addView(image);
        }
    }

    private void startPublish() {
        if (uploading) return;
        if (tiktokMode && tiktokPreview == null) {
            toast(getString(R.string.feedlist_tiktok_publish_preview_first));
            return;
        }
        if (!tiktokMode && imageUris.isEmpty()) {
            toast(getString(R.string.feedlist_publish_select_media));
            return;
        }
        FeedListTikTokPreview previewSnapshot = tiktokPreview;
        ArrayList<Uri> imageSnapshot = new ArrayList<>(imageUris);
        boolean publishTikTokMode = tiktokMode;
        uploading = true;
        setInputsEnabled(false);
        refreshState();
        String raw = textEt.getText() == null ? "" : textEt.getText().toString();
        int codePoints = raw.codePointCount(0, raw.length());
        String text = codePoints > 280 ? raw.substring(0, raw.offsetByCodePoints(0, 280)) : raw;
        if (publishTikTokMode) worker.execute(() -> publishTikTok(text, previewSnapshot));
        else worker.execute(() -> publishImages(text, imageSnapshot));
    }

    private void publishTikTok(String text, FeedListTikTokPreview preview) {
        try {
            if (preview == null || TextUtils.isEmpty(preview.video_id) || TextUtils.isEmpty(preview.url)) {
                throw new IllegalStateException(getString(R.string.feedlist_tiktok_publish_preview_first));
            }
            setProgressAsync(40, getString(R.string.feedlist_publish_saving));
            HashMap<String, Object> media = new HashMap<>();
            media.put("type", "tiktok");
            media.put("cover_url", preview.cover_url);
            media.put("external_provider", "tiktok");
            media.put("external_id", preview.video_id);
            media.put("external_url", preview.url);
            media.put("external_title", preview.title == null ? "" : preview.title);
            media.put("external_author", preview.author_name == null ? "" : preview.author_name);
            ArrayList<Map<String, Object>> list = new ArrayList<>();
            list.add(media);
            awaitPublish(text, list);
            finishSuccess();
        } catch (Exception e) {
            finishError(e);
        }
    }

    private void publishImages(String text, List<Uri> images) {
        try {
            ArrayList<Map<String, Object>> media = new ArrayList<>();
            File dir = new File(getCacheDir(), "feedlist_upload");
            if (!dir.exists()) dir.mkdirs();
            for (int i = 0; i < images.size(); i++) {
                File raw = null;
                File compressed = null;
                try {
                    int base = Math.round(i * 85f / Math.max(1, images.size()));
                    setProgressAsync(base, getString(R.string.feedlist_publish_compressing_image));
                    raw = copyUriToCache(images.get(i), dir);
                    compressed = FeedListImageCompressor.compressToWebp(raw, dir);
                    String path = uploadFile(compressed, i, images.size());
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(compressed.getAbsolutePath(), bounds);
                    HashMap<String, Object> item = new HashMap<>();
                    item.put("type", "image");
                    item.put("display_url", path);
                    item.put("width", Math.max(0, bounds.outWidth));
                    item.put("height", Math.max(0, bounds.outHeight));
                    item.put("size", compressed.length());
                    media.add(item);
                } finally {
                    deleteQuietly(compressed);
                    deleteQuietly(raw);
                }
            }
            setProgressAsync(92, getString(R.string.feedlist_publish_saving));
            awaitPublish(text, media);
            finishSuccess();
        } catch (Exception e) {
            finishError(e);
        }
    }

    private String uploadFile(File file, int index, int total) throws Exception {
        FeedListModel.FeedUploadUrl upload = awaitUploadUrl(file.getAbsolutePath());
        if (upload == null || TextUtils.isEmpty(upload.url)) throw new IllegalStateException(getString(R.string.feedlist_publish_upload_url_failed));
        String tag = "feedlist_upload_" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");
        AtomicReference<Exception> error = new AtomicReference<>();
        WKProgressManager.Companion.getInstance().registerProgress(tag, new WKProgressManager.IProgress() {
            @Override public void onProgress(Object progressTag, int progress) {
                int value = Math.min(90, Math.round((index + progress / 100f) * 85f / Math.max(1, total)));
                setProgressAsync(value, getString(R.string.feedlist_publish_uploading));
            }
            @Override public void onSuccess(Object progressTag, String path) {}
            @Override public void onFail(Object progressTag, String msg) {}
        });
        WKUploader.getInstance().upload(upload.url, file.getAbsolutePath(), tag, new WKUploader.IUploadBack() {
            @Override public void onSuccess(String path) {
                result.set(TextUtils.isEmpty(path) ? upload.path : path);
                latch.countDown();
            }
            @Override public void onError() {
                error.set(new IllegalStateException(getString(R.string.feedlist_publish_upload_failed)));
                latch.countDown();
            }
        });
        try {
            if (!latch.await(120, TimeUnit.SECONDS)) throw new IllegalStateException(getString(R.string.feedlist_publish_upload_timeout));
            if (error.get() != null) throw error.get();
            return normalizeUploadedPath(result.get());
        } finally {
            WKProgressManager.Companion.getInstance().unregisterProgress(tag);
        }
    }

    private FeedListModel.FeedUploadUrl awaitUploadUrl(String path) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FeedListModel.FeedUploadUrl> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        FeedListModel.getInstance().getUploadUrl(path, new IRequestResultListener<>() {
            @Override public void onSuccess(FeedListModel.FeedUploadUrl result) { value.set(result); latch.countDown(); }
            @Override public void onFail(int code, String msg) { error.set(new IllegalStateException(msg)); latch.countDown(); }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException(getString(R.string.feedlist_publish_upload_url_failed));
        if (error.get() != null) throw error.get();
        return value.get();
    }

    private void awaitPublish(String text, List<Map<String, Object>> media) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        FeedListModel.getInstance().publish(text, media, new IRequestResultListener<CommonResponse>() {
            @Override public void onSuccess(CommonResponse result) { latch.countDown(); }
            @Override public void onFail(int code, String msg) { error.set(new IllegalStateException(msg)); latch.countDown(); }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException(getString(R.string.feedlist_publish_failed));
        if (error.get() != null) throw error.get();
    }

    private File copyUriToCache(Uri uri, File dir) throws Exception {
        File out = new File(dir, "raw_" + System.nanoTime() + ".img");
        long maxBytes = FeedListPublishConfig.IMAGE_MAX_SOURCE_MB * 1024L * 1024L;
        long total = 0L;
        try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(out)) {
            if (input == null) throw new IllegalStateException(getString(R.string.feedlist_publish_file_missing));
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalStateException(getString(R.string.feedlist_publish_source_too_large, FeedListPublishConfig.IMAGE_MAX_SOURCE_MB));
                }
                output.write(buffer, 0, read);
            }
        } catch (Exception error) {
            deleteQuietly(out);
            throw error;
        }
        return out;
    }

    private void cleanupOldUploadCache() {
        File dir = new File(getCacheDir(), "feedlist_upload");
        File[] files = dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
        for (File file : files) {
            if (file != null && file.isFile() && file.lastModified() < cutoff) deleteQuietly(file);
        }
    }

    private void finishSuccess() {
        main.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            uploading = false;
            setInputsEnabled(true);
            setProgress(100, getString(R.string.feedlist_publish_success));
            toast(getString(R.string.feedlist_publish_success));
            setResult(RESULT_OK);
            finish();
        });
    }

    private void finishError(Exception error) {
        main.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            uploading = false;
            setInputsEnabled(true);
            refreshState();
            String message = error == null || TextUtils.isEmpty(error.getMessage()) ? getString(R.string.feedlist_publish_failed) : error.getMessage();
            setProgress(0, message);
            toast(message);
        });
    }


    private void setInputsEnabled(boolean enabled) {
        textEt.setEnabled(enabled);
        pickImagesBtn.setEnabled(enabled);
        pickTikTokBtn.setEnabled(enabled);
        tiktokUrlEt.setEnabled(enabled);
        tiktokResolveBtn.setEnabled(enabled && !resolvingTikTok);
    }

    private static void deleteQuietly(File file) {
        if (file == null) return;
        try { if (file.exists()) file.delete(); } catch (Throwable ignored) {}
    }

    private void setProgressAsync(int value, String text) {
        main.post(() -> {
            if (!isFinishing() && !isDestroyed()) setProgress(value, text);
        });
    }
    private void setProgress(int value, String text) {
        progressBar.setVisibility(View.VISIBLE);
        progressTv.setVisibility(View.VISIBLE);
        progressBar.setProgress(Math.max(0, Math.min(100, value)));
        progressTv.setText(text);
    }

    private String normalizeUploadedPath(String path) {
        if (TextUtils.isEmpty(path)) return "";
        String value = path.trim();
        String base = com.chat.base.config.WKApiConfig.baseUrl;
        if (!TextUtils.isEmpty(base) && value.startsWith(base)) value = value.substring(base.length());
        while (value.startsWith("/")) value = value.substring(1);
        if (value.startsWith("file/preview/")) return value;
        if (value.startsWith("common/")) return "file/preview/" + value;
        if (value.startsWith("feed/")) return "file/preview/common/" + value;
        return value;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() {
        if (uploading || resolvingTikTok) {
            toast(getString(R.string.feedlist_publish_wait_upload));
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
