package com.chat.feedlist.publish;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
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
import androidx.core.content.ContextCompat;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Publishes images or a TikTok link detected directly from the normal content input. */
public class FeedListPublishActivity extends AppCompatActivity {
    private static final int REQ_PICK_IMAGES = 101;
    private static final long TIKTOK_DETECT_DELAY_MS = 550L;
    private static final String PUBLISH_STATE_PREFS = "feedlist_publish_state";
    private static final String KEY_PUBLISH_SUCCESS_PENDING = "publish_success_pending";
    private static final Pattern TIKTOK_URL_PATTERN = Pattern.compile(
            "https?://(?:[a-z0-9-]+\\.)*tiktok\\.com/[^\\s<]+",
            Pattern.CASE_INSENSITIVE
    );

    public static void open(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.startActivity(new Intent(activity, FeedListPublishActivity.class));
    }

    /** Kept for source compatibility; result delivery is intentionally no longer used. */
    public static void openForResult(Activity activity, int requestCode) {
        open(activity);
    }

    public static boolean consumePublishSuccess(Context context) {
        if (context == null) return false;
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                PUBLISH_STATE_PREFS,
                Context.MODE_PRIVATE
        );
        boolean pending = prefs.getBoolean(KEY_PUBLISH_SUCCESS_PENDING, false);
        if (pending) prefs.edit().remove(KEY_PUBLISH_SUCCESS_PENDING).commit();
        return pending;
    }

    private static void markPublishSuccess(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PUBLISH_STATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PUBLISH_SUCCESS_PENDING, true)
                .commit();
    }

    private EditText textEt;
    private TextView pickImagesBtn;
    private TextView publishBtn;
    private TextView hintTv;
    private TextView progressTv;
    private ProgressBar progressBar;
    private LinearLayout previewRow;
    private FrameLayout tiktokPreviewBox;
    private ImageView tiktokCoverIv;
    private TextView tiktokTitleTv;
    private TextView tiktokAuthorTv;

    private final ArrayList<Uri> imageUris = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private FeedListTikTokPreview tiktokPreview;
    private String resolvedTikTokInputUrl = "";
    private String lastConflictUrl = "";
    private Runnable pendingTikTokDetection;
    private int tiktokResolveGeneration;
    private volatile boolean uploading;
    private volatile boolean resolvingTikTok;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedlist_publish);
        bindViews();
        bindListeners();
        refreshState();
        worker.execute(this::cleanupOldUploadCache);
    }

    private void bindViews() {
        textEt = findViewById(R.id.feedlistPublishTextEt);
        pickImagesBtn = findViewById(R.id.feedlistPickImagesBtn);
        publishBtn = findViewById(R.id.feedlistPublishSubmitBtn);
        hintTv = findViewById(R.id.feedlistPublishHintTv);
        progressTv = findViewById(R.id.feedlistUploadProgressTv);
        progressBar = findViewById(R.id.feedlistUploadProgressBar);
        previewRow = findViewById(R.id.feedlistPreviewRow);
        tiktokPreviewBox = findViewById(R.id.feedlistTikTokPreview);
        tiktokCoverIv = findViewById(R.id.feedlistTikTokCoverIv);
        tiktokTitleTv = findViewById(R.id.feedlistTikTokTitleTv);
        tiktokAuthorTv = findViewById(R.id.feedlistTikTokAuthorTv);
    }

    private void bindListeners() {
        findViewById(R.id.feedlistPublishCloseTv).setOnClickListener(v -> {
            if (uploading || resolvingTikTok) toast(getString(R.string.feedlist_publish_wait_upload));
            else finish();
        });

        pickImagesBtn.setOnClickListener(v -> {
            if (uploading || resolvingTikTok) return;
            String link = extractTikTokUrl(currentText());
            if (!TextUtils.isEmpty(link) || tiktokPreview != null) {
                toast(getString(R.string.feedlist_tiktok_images_conflict));
                return;
            }
            openImagePicker();
        });

        textEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleTikTokDetection(String.valueOf(s));
                refreshState();
            }
            @Override public void afterTextChanged(Editable s) {}
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        if (!TextUtils.isEmpty(extractTikTokUrl(currentText()))) {
            toast(getString(R.string.feedlist_tiktok_images_conflict));
            return;
        }
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount()
                    && imageUris.size() < FeedListPublishConfig.IMAGE_MAX_SELECT_COUNT; i++) {
                addImage(clip.getItemAt(i).getUri());
            }
        } else {
            addImage(data.getData());
        }
        renderImagePreviews();
        refreshState();
    }

    private void addImage(Uri uri) {
        if (uri == null || imageUris.size() >= FeedListPublishConfig.IMAGE_MAX_SELECT_COUNT) return;
        for (Uri old : imageUris) if (uri.equals(old)) return;
        imageUris.add(uri);
    }

    private void scheduleTikTokDetection(String text) {
        if (pendingTikTokDetection != null) main.removeCallbacks(pendingTikTokDetection);
        pendingTikTokDetection = () -> detectTikTokUrl(text);
        main.postDelayed(pendingTikTokDetection, TIKTOK_DETECT_DELAY_MS);
    }

    private void detectTikTokUrl(String text) {
        if (isFinishing() || isDestroyed() || uploading) return;
        String url = extractTikTokUrl(text);
        if (TextUtils.isEmpty(url)) {
            lastConflictUrl = "";
            tiktokResolveGeneration++;
            resolvingTikTok = false;
            clearTikTokPreview();
            refreshState();
            return;
        }

        if (!imageUris.isEmpty()) {
            tiktokResolveGeneration++;
            resolvingTikTok = false;
            clearTikTokPreview();
            if (!TextUtils.equals(lastConflictUrl, url)) {
                lastConflictUrl = url;
                toast(getString(R.string.feedlist_tiktok_images_conflict));
            }
            refreshState();
            return;
        }

        lastConflictUrl = "";
        if (tiktokPreview != null && TextUtils.equals(url, resolvedTikTokInputUrl)) {
            refreshState();
            return;
        }
        resolveTikTok(url);
    }

    private void resolveTikTok(String url) {
        int generation = ++tiktokResolveGeneration;
        resolvingTikTok = true;
        clearTikTokPreview();
        refreshState();

        FeedListModel.getInstance().tiktokPreview(url, new IRequestResultListener<>() {
            @Override
            public void onSuccess(FeedListTikTokPreview result) {
                if (isFinishing() || isDestroyed() || generation != tiktokResolveGeneration) return;
                resolvingTikTok = false;
                String currentUrl = extractTikTokUrl(currentText());
                if (!TextUtils.equals(url, currentUrl)) return;
                if (result == null || TextUtils.isEmpty(result.video_id) || TextUtils.isEmpty(result.cover_url)) {
                    toast(getString(R.string.feedlist_tiktok_publish_resolve_failed));
                    refreshState();
                    return;
                }
                tiktokPreview = result;
                resolvedTikTokInputUrl = url;
                tiktokPreviewBox.setVisibility(View.VISIBLE);
                tiktokTitleTv.setText(TextUtils.isEmpty(result.title) ? "TikTok" : result.title);
                String author = result.author_name == null ? "" : result.author_name.trim();
                if (!TextUtils.isEmpty(author) && !author.startsWith("@")) author = "@" + author;
                tiktokAuthorTv.setText(TextUtils.isEmpty(author) ? "TikTok" : author);
                Glide.with(FeedListPublishActivity.this)
                        .load(result.cover_url)
                        .centerCrop()
                        .into(tiktokCoverIv);
                refreshState();
            }

            @Override
            public void onFail(int code, String msg) {
                if (isFinishing() || isDestroyed() || generation != tiktokResolveGeneration) return;
                resolvingTikTok = false;
                toast(TextUtils.isEmpty(msg)
                        ? getString(R.string.feedlist_tiktok_publish_resolve_failed)
                        : msg);
                refreshState();
            }
        });
    }

    private void clearTikTokPreview() {
        tiktokPreview = null;
        resolvedTikTokInputUrl = "";
        if (tiktokPreviewBox != null) tiktokPreviewBox.setVisibility(View.GONE);
        if (tiktokCoverIv != null) Glide.with(this).clear(tiktokCoverIv);
        if (tiktokTitleTv != null) tiktokTitleTv.setText("");
        if (tiktokAuthorTv != null) tiktokAuthorTv.setText("");
    }

    private void refreshState() {
        boolean hasTikTokUrl = !TextUtils.isEmpty(extractTikTokUrl(currentText()));
        boolean conflict = hasTikTokUrl && !imageUris.isEmpty();
        if (conflict) {
            hintTv.setText(R.string.feedlist_tiktok_images_conflict);
            hintTv.setTextColor(ContextCompat.getColor(this, R.color.feedlist_danger));
        } else if (resolvingTikTok) {
            hintTv.setText(R.string.feedlist_tiktok_publish_resolving);
            hintTv.setTextColor(ContextCompat.getColor(this, R.color.feedlist_secondary));
        } else {
            hintTv.setText(R.string.feedlist_publish_storage_hint);
            hintTv.setTextColor(ContextCompat.getColor(this, R.color.feedlist_secondary));
        }

        boolean previewMatches = tiktokPreview != null && TextUtils.equals(hasTikTokUrl ? extractTikTokUrl(currentText()) : "", resolvedTikTokInputUrl);
        boolean hasMedia = !imageUris.isEmpty() || previewMatches;
        publishBtn.setEnabled(hasMedia && !conflict && !uploading && !resolvingTikTok);
        publishBtn.setAlpha(publishBtn.isEnabled() ? 1f : 0.45f);
        pickImagesBtn.setEnabled(!uploading && !resolvingTikTok && !hasTikTokUrl && tiktokPreview == null);
        pickImagesBtn.setAlpha(pickImagesBtn.isEnabled() ? 1f : 0.45f);
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
                    scheduleTikTokDetection(currentText());
                    refreshState();
                }
            });
            previewRow.addView(image);
        }
    }

    private void startPublish() {
        if (uploading) return;
        if (resolvingTikTok) {
            toast(getString(R.string.feedlist_tiktok_publish_resolving));
            return;
        }

        String currentUrl = extractTikTokUrl(currentText());
        if (!TextUtils.isEmpty(currentUrl) && !imageUris.isEmpty()) {
            toast(getString(R.string.feedlist_tiktok_images_conflict));
            return;
        }
        if (!TextUtils.isEmpty(currentUrl)
                && (tiktokPreview == null || !TextUtils.equals(currentUrl, resolvedTikTokInputUrl))) {
            toast(getString(R.string.feedlist_tiktok_publish_preview_first));
            scheduleTikTokDetection(currentText());
            return;
        }
        if (TextUtils.isEmpty(currentUrl) && imageUris.isEmpty()) {
            toast(getString(R.string.feedlist_publish_select_media));
            return;
        }

        FeedListTikTokPreview previewSnapshot = tiktokPreview;
        ArrayList<Uri> imageSnapshot = new ArrayList<>(imageUris);
        boolean publishTikTok = previewSnapshot != null && TextUtils.equals(currentUrl, resolvedTikTokInputUrl);
        uploading = true;
        setInputsEnabled(false);
        refreshState();

        String raw = stripTikTokUrl(currentText(), currentUrl);
        int codePoints = raw.codePointCount(0, raw.length());
        String text = codePoints > 280 ? raw.substring(0, raw.offsetByCodePoints(0, 280)) : raw;
        if (publishTikTok) worker.execute(() -> publishTikTok(text, previewSnapshot));
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
        if (upload == null || TextUtils.isEmpty(upload.url)) {
            throw new IllegalStateException(getString(R.string.feedlist_publish_upload_url_failed));
        }
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
            if (!latch.await(120, TimeUnit.SECONDS)) {
                throw new IllegalStateException(getString(R.string.feedlist_publish_upload_timeout));
            }
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
            @Override public void onSuccess(FeedListModel.FeedUploadUrl result) {
                value.set(result);
                latch.countDown();
            }
            @Override public void onFail(int code, String msg) {
                error.set(new IllegalStateException(msg));
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException(getString(R.string.feedlist_publish_upload_url_failed));
        }
        if (error.get() != null) throw error.get();
        return value.get();
    }

    private void awaitPublish(String text, List<Map<String, Object>> media) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        FeedListModel.getInstance().publish(text, media, new IRequestResultListener<CommonResponse>() {
            @Override public void onSuccess(CommonResponse result) { latch.countDown(); }
            @Override public void onFail(int code, String msg) {
                error.set(new IllegalStateException(msg));
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException(getString(R.string.feedlist_publish_failed));
        }
        if (error.get() != null) throw error.get();
    }

    private File copyUriToCache(Uri uri, File dir) throws Exception {
        File out = new File(dir, "raw_" + System.nanoTime() + ".img");
        long maxBytes = FeedListPublishConfig.IMAGE_MAX_SOURCE_MB * 1024L * 1024L;
        long total = 0L;
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(out)) {
            if (input == null) throw new IllegalStateException(getString(R.string.feedlist_publish_file_missing));
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalStateException(getString(
                            R.string.feedlist_publish_source_too_large,
                            FeedListPublishConfig.IMAGE_MAX_SOURCE_MB
                    ));
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
        // Persist success before closing. The feed consumes this flag from onResume, avoiding
        // the old Activity-result/RecyclerView lifecycle race that could crash after a valid post.
        markPublishSuccess(getApplicationContext());
        main.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            uploading = false;
            setProgress(100, getString(R.string.feedlist_publish_success));
            toast(getString(R.string.feedlist_publish_success));
            finish();
        });
    }

    private void finishError(Exception error) {
        main.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            uploading = false;
            setInputsEnabled(true);
            refreshState();
            String message = error == null || TextUtils.isEmpty(error.getMessage())
                    ? getString(R.string.feedlist_publish_failed)
                    : error.getMessage();
            setProgress(0, message);
            toast(message);
        });
    }

    private void setInputsEnabled(boolean enabled) {
        textEt.setEnabled(enabled);
        pickImagesBtn.setEnabled(enabled && TextUtils.isEmpty(extractTikTokUrl(currentText())) && tiktokPreview == null);
    }

    private static void deleteQuietly(File file) {
        if (file == null) return;
        try {
            if (file.exists()) file.delete();
        } catch (Throwable ignored) {
        }
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

    private String currentText() {
        return textEt == null || textEt.getText() == null ? "" : textEt.getText().toString();
    }

    private String extractTikTokUrl(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher matcher = TIKTOK_URL_PATTERN.matcher(text);
        if (!matcher.find()) return "";
        String url = matcher.group();
        while (!TextUtils.isEmpty(url)) {
            char last = url.charAt(url.length() - 1);
            if (last == ')' || last == ']' || last == '}' || last == ',' || last == '.'
                    || last == '，' || last == '。' || last == ';' || last == '；') {
                url = url.substring(0, url.length() - 1);
            } else {
                break;
            }
        }
        return url.trim();
    }

    private String stripTikTokUrl(String text, String url) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(url)) return text == null ? "" : text.trim();
        return text.replace(url, "")
                .replaceAll("[ \\t]+\\n", "\\n")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (uploading || resolvingTikTok) {
            toast(getString(R.string.feedlist_publish_wait_upload));
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        tiktokResolveGeneration++;
        if (pendingTikTokDetection != null) main.removeCallbacks(pendingTikTokDetection);
        if (tiktokCoverIv != null) Glide.with(this).clear(tiktokCoverIv);
        worker.shutdown();
        super.onDestroy();
    }
}
