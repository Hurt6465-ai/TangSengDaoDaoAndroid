package com.chat.feed.publish;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.ud.WKProgressManager;
import com.chat.base.net.ud.WKUploader;
import com.chat.feed.FeedModel;
import com.chat.feed.R;
import com.chat.feed.config.FeedConfig;

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

/**
 * 第一版发布：图片可一次选 5 张，手机端压缩成 WebP；视频用手机端 Media3 Transformer 压缩后上传。
 *
 * 说明：不做复杂剪辑/滤镜；上传在后台线程串行执行，UI 显示压缩和上传总进度。
 */
public class FeedPublishActivity extends Activity {
    private static final int REQ_PICK_IMAGES = 101;
    private static final int REQ_PICK_VIDEO = 102;

    private TextView closeTv;
    private EditText textEt;
    private TextView pickImagesBtn;
    private TextView pickVideoBtn;
    private TextView publishBtn;
    private TextView hintTv;
    private TextView progressTv;
    private ProgressBar progressBar;
    private LinearLayout previewRow;
    private TextView videoInfoTv;

    private final ArrayList<Uri> imageUris = new ArrayList<>();
    private Uri videoUri;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean uploading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_publish);
        bindViews();
        bindListeners();
        refreshState();
    }

    private void bindViews() {
        closeTv = findViewById(R.id.feedPublishCloseTv);
        textEt = findViewById(R.id.feedPublishTextEt);
        pickImagesBtn = findViewById(R.id.feedPickImagesBtn);
        pickVideoBtn = findViewById(R.id.feedPickVideoBtn);
        publishBtn = findViewById(R.id.feedPublishSubmitBtn);
        hintTv = findViewById(R.id.feedPublishHintTv);
        progressTv = findViewById(R.id.feedUploadProgressTv);
        progressBar = findViewById(R.id.feedUploadProgressBar);
        previewRow = findViewById(R.id.feedPreviewRow);
        videoInfoTv = findViewById(R.id.feedVideoInfoTv);
    }

    private void bindListeners() {
        if (closeTv != null) {
            closeTv.setOnClickListener(v -> {
                if (uploading) {
                    toast(getString(R.string.feed_publish_wait_upload));
                    return;
                }
                finish();
            });
        }
        pickImagesBtn.setOnClickListener(v -> openImagePicker());
        pickVideoBtn.setOnClickListener(v -> openVideoPicker());
        publishBtn.setOnClickListener(v -> startPublish());
    }

    private void openImagePicker() {
        if (uploading) return;
        if (!FeedConfig.ENABLE_IMAGE_PUBLISH) {
            toast(getString(R.string.feed_publish_image_disabled));
            return;
        }
        int remain = FeedConfig.IMAGE_MAX_SELECT_COUNT - imageUris.size();
        if (remain <= 0) {
            toast(getString(R.string.feed_publish_max_images, FeedConfig.IMAGE_MAX_SELECT_COUNT));
            return;
        }
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ 直接走系统 Photo Picker，避免弹到文件管理器；支持一次多选。
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, remain);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_PICK_IMAGES);
            return;
        }
        // 低版本优先给相册类 App 处理，同时保留多选能力；不同 ROM 可能回退到系统选择器。
        intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_IMAGES);
    }

    private void openVideoPicker() {
        if (uploading) return;
        if (!FeedConfig.ENABLE_VIDEO_PUBLISH) {
            toast(getString(R.string.feed_publish_video_disabled));
            return;
        }
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("video/*");
            startActivityForResult(intent, REQ_PICK_VIDEO);
            return;
        }
        intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_PICK_IMAGES) {
            addPickedImages(data);
        } else if (requestCode == REQ_PICK_VIDEO) {
            videoUri = data.getData();
            if (videoUri != null) {
                imageUris.clear();
                String name = getDisplayName(videoUri);
                videoInfoTv.setText(getString(R.string.feed_publish_video_selected, TextUtils.isEmpty(name) ? "video" : name));
                videoInfoTv.setVisibility(View.VISIBLE);
            }
        }
        refreshState();
    }

    private void addPickedImages(Intent data) {
        videoUri = null;
        if (videoInfoTv != null) {
            videoInfoTv.setText("");
            videoInfoTv.setVisibility(View.GONE);
        }
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                if (imageUris.size() >= FeedConfig.IMAGE_MAX_SELECT_COUNT) break;
                Uri uri = clip.getItemAt(i).getUri();
                addImageUriIfNeeded(uri);
            }
        } else if (data.getData() != null && imageUris.size() < FeedConfig.IMAGE_MAX_SELECT_COUNT) {
            addImageUriIfNeeded(data.getData());
        }
        if (imageUris.size() >= FeedConfig.IMAGE_MAX_SELECT_COUNT) {
            toast(getString(R.string.feed_publish_max_images, FeedConfig.IMAGE_MAX_SELECT_COUNT));
        }
    }

    private void addImageUriIfNeeded(Uri uri) {
        if (uri == null || imageUris.size() >= FeedConfig.IMAGE_MAX_SELECT_COUNT) return;
        for (Uri old : imageUris) {
            if (old != null && old.equals(uri)) return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        imageUris.add(uri);
    }

    private void refreshState() {
        pickVideoBtn.setVisibility(FeedConfig.ENABLE_VIDEO_PUBLISH ? View.VISIBLE : View.GONE);
        renderImagePreviews();
        hintTv.setText(getString(R.string.feed_publish_storage_hint));
        boolean hasMedia = !imageUris.isEmpty() || videoUri != null;
        publishBtn.setEnabled(hasMedia && !uploading);
        publishBtn.setAlpha(hasMedia && !uploading ? 1f : 0.45f);
    }

    private void renderImagePreviews() {
        previewRow.removeAllViews();
        for (int i = 0; i < imageUris.size(); i++) {
            Uri uri = imageUris.get(i);
            ImageView iv = new ImageView(this);
            int size = dp(68);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, dp(8), 0);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFF242424);
            iv.setImageURI(uri);
            final int index = i;
            iv.setOnClickListener(v -> {
                if (!uploading && index >= 0 && index < imageUris.size()) {
                    imageUris.remove(index);
                    refreshState();
                }
            });
            previewRow.addView(iv);
        }
    }

    private void startPublish() {
        if (uploading) return;
        if (imageUris.isEmpty() && videoUri == null) {
            toast(getString(R.string.feed_publish_select_media));
            return;
        }
        uploading = true;
        setProgress(0, getString(R.string.feed_publish_preparing));
        refreshState();
        List<Uri> images = new ArrayList<>(imageUris);
        Uri video = videoUri;
        String rawText = textEt.getText() == null ? "" : textEt.getText().toString();
        final String publishText = rawText.length() > 280 ? rawText.substring(0, 280) : rawText;
        uploadExecutor.execute(() -> doPublish(publishText, images, video));
    }

    private void doPublish(String text, List<Uri> images, Uri video) {
        try {
            ArrayList<Map<String, Object>> mediaList = new ArrayList<>();
            int calculatedTotalUnits = images.size() + (video == null ? 0 : 3); // 视频压缩 + 封面上传 + 视频上传
            if (calculatedTotalUnits <= 0) calculatedTotalUnits = 1;
            final int totalUnits = calculatedTotalUnits;
            final int[] finished = new int[]{0};

            File cacheDir = new File(getCacheDir(), "feed_upload");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            for (Uri uri : images) {
                updateStageProgress(finished[0], totalUnits, 0, getString(R.string.feed_publish_compressing_image));
                File raw = copyUriToCache(uri, cacheDir, "img", ".jpg");
                File webp = FeedImageCompressor.compressToWebp(raw, cacheDir);
                Map<String, Object> imageMedia = uploadImage(webp, finished[0], totalUnits);
                mediaList.add(imageMedia);
                finished[0]++;
                updateStageProgress(finished[0], totalUnits, 100, getString(R.string.feed_publish_uploading));
            }

            if (video != null) {
                if (!FeedConfig.ENABLE_VIDEO_PUBLISH) throw new IllegalStateException(getString(R.string.feed_publish_video_disabled));
                updateStageProgress(finished[0], totalUnits, 0, getString(R.string.feed_publish_preparing_video));
                File rawVideo = copyUriToCache(video, cacheDir, "video", ".mp4");
                FeedVideoCompressor.PreparedVideo prepared = FeedVideoCompressor.prepare(this, rawVideo, cacheDir,
                        (progress, message) -> updateStageProgress(finished[0], totalUnits, progress,
                                TextUtils.isEmpty(message) ? getString(R.string.feed_publish_compressing_video) : message));
                finished[0]++;
                updateStageProgress(finished[0], totalUnits, 0, getString(R.string.feed_publish_uploading));

                String coverPath = "";
                if (prepared.coverFile != null && prepared.coverFile.exists()) {
                    Map<String, Object> coverMedia = uploadImage(prepared.coverFile, finished[0], totalUnits);
                    Object path = coverMedia.get("display_url");
                    coverPath = path == null ? "" : String.valueOf(path);
                }
                finished[0]++;
                Map<String, Object> videoMedia = uploadVideo(prepared, coverPath, finished[0], totalUnits);
                mediaList.add(videoMedia);
                finished[0]++;
            }

            updateStageProgress(totalUnits, totalUnits, 100, getString(R.string.feed_publish_saving));
            awaitPublish(text, mediaList);
            mainHandler.post(() -> {
                uploading = false;
                setProgress(100, getString(R.string.feed_publish_success));
                toast(getString(R.string.feed_publish_success));
                setResult(RESULT_OK);
                finish();
            });
        } catch (Exception e) {
            mainHandler.post(() -> {
                uploading = false;
                refreshState();
                setProgress(0, e.getMessage() == null ? getString(R.string.feed_publish_failed) : e.getMessage());
                toast(e.getMessage() == null ? getString(R.string.feed_publish_failed) : e.getMessage());
            });
        }
    }

    private Map<String, Object> uploadImage(File file, int index, int total) throws Exception {
        String path = uploadFile(file, "image", index, total);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        HashMap<String, Object> media = new HashMap<>();
        media.put("type", "image");
        media.put("thumb_url", path);
        media.put("display_url", path);
        media.put("origin_url", path);
        media.put("width", Math.max(0, bounds.outWidth));
        media.put("height", Math.max(0, bounds.outHeight));
        media.put("size", file.length());
        return media;
    }

    private Map<String, Object> uploadVideo(FeedVideoCompressor.PreparedVideo video, String coverPath, int index, int total) throws Exception {
        String path = uploadFile(video.videoFile, "video", index, total);
        HashMap<String, Object> media = new HashMap<>();
        media.put("type", "video");
        media.put("cover_url", coverPath);
        media.put("thumb_url", coverPath);
        media.put("display_url", path);
        media.put("origin_url", path);
        media.put("play_url_540p", path);
        media.put("width", video.width);
        media.put("height", video.height);
        media.put("duration_ms", video.durationMs);
        media.put("size", video.sizeBytes);
        return media;
    }

    private String uploadFile(File file, String mediaType, int index, int total) throws Exception {
        if (file == null || !file.exists()) throw new IllegalStateException(getString(R.string.feed_publish_file_missing));
        FeedModel.FeedUploadUrl uploadUrl = awaitUploadUrl(file.getAbsolutePath(), mediaType);
        if (uploadUrl == null || TextUtils.isEmpty(uploadUrl.url)) throw new IllegalStateException(getString(R.string.feed_publish_upload_url_failed));
        String tag = "feed_upload_" + UUID.randomUUID();
        WKProgressManager.Companion.getInstance().registerProgress(tag, new WKProgressManager.IProgress() {
            @Override
            public void onProgress(Object progressTag, int progress) {
                updateStageProgress(index, total, progress, getString(R.string.feed_publish_uploading));
            }

            @Override
            public void onSuccess(Object progressTag, String path) {
            }

            @Override
            public void onFail(Object progressTag, String msg) {
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultPath = new AtomicReference<>("");
        AtomicReference<Exception> error = new AtomicReference<>();
        WKUploader.getInstance().upload(uploadUrl.url, file.getAbsolutePath(), tag, new WKUploader.IUploadBack() {
            @Override
            public void onSuccess(String uploadedPath) {
                WKProgressManager.Companion.getInstance().unregisterProgress(tag);
                resultPath.set(TextUtils.isEmpty(uploadedPath) ? uploadUrl.path : uploadedPath);
                latch.countDown();
            }

            @Override
            public void onError() {
                WKProgressManager.Companion.getInstance().unregisterProgress(tag);
                error.set(new IllegalStateException(getString(R.string.feed_publish_upload_failed)));
                latch.countDown();
            }
        });
        if (!latch.await(120, TimeUnit.SECONDS)) {
            WKProgressManager.Companion.getInstance().unregisterProgress(tag);
            throw new IllegalStateException(getString(R.string.feed_publish_upload_timeout));
        }
        if (error.get() != null) throw error.get();
        return normalizeUploadedPath(resultPath.get());
    }

    private FeedModel.FeedUploadUrl awaitUploadUrl(String localPath, String mediaType) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FeedModel.FeedUploadUrl> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        FeedModel.getInstance().getFeedUploadFileUrl(localPath, mediaType, new IRequestResultListener<FeedModel.FeedUploadUrl>() {
            @Override
            public void onSuccess(FeedModel.FeedUploadUrl data) {
                result.set(data);
                latch.countDown();
            }

            @Override
            public void onFail(int code, String msg) {
                error.set(new IllegalStateException(TextUtils.isEmpty(msg) ? getString(R.string.feed_publish_upload_url_failed) : msg));
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException(getString(R.string.feed_publish_upload_url_failed));
        if (error.get() != null) throw error.get();
        return result.get();
    }

    private void awaitPublish(String text, List<Map<String, Object>> mediaList) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        FeedModel.getInstance().publish(text, mediaList, new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                latch.countDown();
            }

            @Override
            public void onFail(int code, String msg) {
                error.set(new IllegalStateException(TextUtils.isEmpty(msg) ? getString(R.string.feed_publish_failed) : msg));
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException(getString(R.string.feed_publish_failed));
        if (error.get() != null) throw error.get();
    }

    private File copyUriToCache(Uri uri, File dir, String prefix, String defaultExt) throws Exception {
        if (uri == null) throw new IllegalArgumentException("uri null");
        if (dir != null && !dir.exists()) dir.mkdirs();
        String name = getDisplayName(uri);
        String ext = extensionOf(name, defaultExt);
        File out = new File(dir, prefix + "_" + System.currentTimeMillis() + "_" + Math.abs(uri.hashCode()) + ext);
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IllegalStateException(getString(R.string.feed_publish_file_missing));
        FileOutputStream output = new FileOutputStream(out);
        byte[] buffer = new byte[128 * 1024];
        int len;
        while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
        output.flush();
        output.close();
        input.close();
        return out;
    }

    private String extensionOf(String name, String fallback) {
        if (!TextUtils.isEmpty(name)) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) return name.substring(dot);
        }
        return fallback;
    }

    private String getDisplayName(Uri uri) {
        if (uri == null) return "";
        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String path = uri.getPath();
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void updateStageProgress(int finished, int total, int currentProgress, String text) {
        int safeTotal = Math.max(1, total);
        int value = Math.min(100, Math.max(0, Math.round(((finished * 100f) + currentProgress) / safeTotal)));
        mainHandler.post(() -> setProgress(value, text));
    }

    private void setProgress(int progress, String text) {
        progressBar.setVisibility(View.VISIBLE);
        progressTv.setVisibility(View.VISIBLE);
        progressBar.setProgress(progress);
        progressTv.setText(TextUtils.isEmpty(text) ? getString(R.string.feed_publish_uploading_percent, progress) : text + " " + progress + "%");
    }

    private String normalizeUploadedPath(String path) {
        if (TextUtils.isEmpty(path)) return "";
        String v = path.trim();
        if (v.startsWith(com.chat.base.config.WKApiConfig.baseUrl)) {
            v = v.substring(com.chat.base.config.WKApiConfig.baseUrl.length());
        }
        if (v.startsWith("/")) v = v.substring(1);
        if (v.startsWith("file/preview/")) return v;
        if (v.startsWith("common/")) return "file/preview/" + v;
        if (v.startsWith("feed/")) return "file/preview/common/" + v;
        return v;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        uploadExecutor.shutdownNow();
        super.onDestroy();
    }
}
