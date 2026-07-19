package com.chat.forum;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native post, Q&A, and article composer. */
public class ForumCreateTopicActivity extends AppCompatActivity {
    private static final int MAX_IMAGES = 6;
    private static final int TYPE_TOPIC = 0;
    private static final int TYPE_QA = 2;
    private static final int TYPE_ARTICLE = 3;
    private static final String STATE_TITLE = "forum_title";
    private static final String STATE_CONTENT = "forum_content";
    private static final String STATE_TAGS = "forum_tags";
    private static final String STATE_BOUNTY = "forum_bounty";
    private static final String STATE_TYPE = "forum_type";
    private static final String STATE_CATEGORY = "forum_category";
    private static final String STATE_IMAGES = "forum_images";

    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final List<Uri> selectedImages = new ArrayList<>();
    private final List<ForumApiClient.Category> categories = new ArrayList<>();
    private final List<ForumApiClient.Tag> recommendedTags = new ArrayList<>();
    private final LinkedHashSet<String> selectedTags = new LinkedHashSet<>();
    private final Set<File> pendingUploadFiles = Collections.synchronizedSet(new HashSet<>());
    private final ForumApiClient.RequestScope readScope = new ForumApiClient.RequestScope();
    private final ForumApiClient.RequestScope publishScope = new ForumApiClient.RequestScope();
    private EditText titleInput;
    private EditText contentInput;
    private LinearLayout tagContainer;
    private TextView tagHint;
    private Spinner categorySpinner;
    private TextView categoryLabel;
    private TextView imageLabel;
    private LinearLayout imageContainer;
    private LinearLayout typePill;
    private EditText bountyInput;
    private int topicType;
    private TextView publishButton;
    private TextView headingView;
    private TextView relationButton;
    private boolean publishing;
    private boolean authenticating;
    private boolean discardDraft;
    private volatile boolean destroyed;
    private int categoryGeneration;
    private int publishGeneration;
    private boolean categoriesLoaded;
    private boolean tagsLoaded;
    private long pendingCategoryId;

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null || uris.isEmpty() || publishing) return;
                int maxImages = topicType == TYPE_ARTICLE ? 1 : MAX_IMAGES;
                int added = 0;
                for (Uri uri : uris) {
                    if (uri == null || selectedImages.contains(uri)) continue;
                    if (selectedImages.size() >= maxImages) break;
                    selectedImages.add(uri);
                    added++;
                }
                if (added < uris.size()) {
                    Toast.makeText(this, topicType == TYPE_ARTICLE
                            ? "文章只使用第一张图片作为封面"
                            : "每篇帖子最多选择6张图片", Toast.LENGTH_SHORT).show();
                }
                renderSelectedImages();
            });

    public static Intent createIntent(Context context) {
        return new Intent(context, ForumCreateTopicActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildView();
        restoreComposerState(savedInstanceState);
        authenticateAndLoadCategories();
    }

    @Override
    protected void onStop() {
        if ((!publishing || authenticating) && !discardDraft) saveDraft();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TITLE, valueOf(titleInput));
        outState.putString(STATE_CONTENT, valueOf(contentInput));
        outState.putString(STATE_TAGS, TextUtils.join(",", selectedTags));
        outState.putString(STATE_BOUNTY, valueOf(bountyInput));
        outState.putInt(STATE_TYPE, topicType);
        outState.putLong(STATE_CATEGORY, categoryIdForState());
        ArrayList<String> images = new ArrayList<>();
        for (Uri uri : selectedImages) if (uri != null) images.add(uri.toString());
        outState.putStringArrayList(STATE_IMAGES, images);
    }

    @Override
    public void onBackPressed() {
        if (publishing && !authenticating) {
            Toast.makeText(this, "正在发布，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        readScope.cancelAll();
        publishScope.cancelAll();
        imageExecutor.shutdownNow();
        cleanupPendingFiles();
        super.onDestroy();
    }

    private void buildView() {
        boolean dark = isDark();
        getWindow().setStatusBarColor(dark ? 0xFF17181B : Color.WHITE);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF111214 : 0xFFF6F7F9);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(6), 0, dp(8), 0);
        toolbar.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        TextView back = text("‹", 35, dark ? Color.WHITE : 0xFF1C1E21, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> {
            if (!publishing || authenticating) finish();
        });
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
        headingView = text("发布帖子", 18, dark ? Color.WHITE : 0xFF1C1E21, true);
        toolbar.addView(headingView, new LinearLayout.LayoutParams(0, dp(52), 1f));
        publishButton = text("发布", 15, 0xFF1877F2, true);
        publishButton.setGravity(Gravity.CENTER);
        publishButton.setOnClickListener(v -> publish());
        toolbar.addView(publishButton, new LinearLayout.LayoutParams(dp(64), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(16), dp(16), dp(40));
        form.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        form.addView(label("类型"));
        typePill = new LinearLayout(this);
        typePill.setGravity(Gravity.CENTER_VERTICAL);
        typePill.setPadding(dp(2), dp(2), dp(2), dp(2));
        typePill.setBackground(roundRect(dark ? 0xFF25272C : 0xFFF1F3F5, 16));
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(dp(258), dp(34));
        typeParams.topMargin = dp(7);
        form.addView(typePill, typeParams);
        renderTypePill();

        categoryLabel = label("板块");
        LinearLayout.LayoutParams categoryLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        categoryLabelParams.topMargin = dp(14);
        form.addView(categoryLabel, categoryLabelParams);
        categorySpinner = new Spinner(this);
        form.addView(categorySpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        bountyInput = input("悬赏积分，可选", false);
        bountyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        bountyInput.setVisibility(View.GONE);
        LinearLayout.LayoutParams bountyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bountyParams.topMargin = dp(8);
        form.addView(bountyInput, bountyParams);

        titleInput = input("标题（最多128字）", false);
        titleInput.setMaxLines(2);
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(12);
        form.addView(titleInput, titleParams);

        contentInput = input("分享你的问题、经验或学习内容…", true);
        contentInput.setGravity(Gravity.TOP | Gravity.START);
        contentInput.setMinHeight(dp(220));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = dp(12);
        form.addView(contentInput, contentParams);

        relationButton = text("＋ 插入文章/帖子关联", 13, 0xFF1877F2, true);
        relationButton.setGravity(Gravity.CENTER_VERTICAL);
        relationButton.setPadding(dp(4), 0, dp(4), 0);
        relationButton.setOnClickListener(v -> showInsertReferenceDialog());
        relationButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams relationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        relationParams.topMargin = dp(4);
        form.addView(relationButton, relationParams);

        TextView tagsLabel = label("推荐标签（最多选择5个）");
        LinearLayout.LayoutParams tagsLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagsLabelParams.topMargin = dp(14);
        form.addView(tagsLabel, tagsLabelParams);

        tagHint = text("加载推荐标签…", 12, dark ? 0xFF8E9299 : 0xFF8A8F96, false);
        LinearLayout.LayoutParams tagHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        tagHintParams.topMargin = dp(4);
        form.addView(tagHint, tagHintParams);

        HorizontalScrollView tagScroll = new HorizontalScrollView(this);
        tagScroll.setHorizontalScrollBarEnabled(false);
        tagScroll.setFillViewport(false);
        tagContainer = new LinearLayout(this);
        tagContainer.setOrientation(LinearLayout.HORIZONTAL);
        tagContainer.setGravity(Gravity.CENTER_VERTICAL);
        tagScroll.addView(tagContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        form.addView(tagScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        LinearLayout imageHeader = new LinearLayout(this);
        imageHeader.setGravity(Gravity.CENTER_VERTICAL);
        imageLabel = label("图片（最多6张）");
        imageHeader.addView(imageLabel, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView choose = text("选择图片", 14, 0xFF1877F2, true);
        choose.setGravity(Gravity.CENTER);
        choose.setOnClickListener(v -> {
            if (publishing) return;
            int maxImages = topicType == TYPE_ARTICLE ? 1 : MAX_IMAGES;
            if (selectedImages.size() >= maxImages) {
                Toast.makeText(this, topicType == TYPE_ARTICLE
                        ? "文章最多选择1张封面图" : "最多6张图片", Toast.LENGTH_SHORT).show();
                return;
            }
            imagePicker.launch("image/*");
        });
        imageHeader.addView(choose, new LinearLayout.LayoutParams(dp(90), dp(48)));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = dp(8);
        form.addView(imageHeader, headerParams);

        HorizontalScrollView imageScroll = new HorizontalScrollView(this);
        imageScroll.setHorizontalScrollBarEnabled(false);
        imageContainer = new LinearLayout(this);
        imageContainer.setOrientation(LinearLayout.HORIZONTAL);
        imageScroll.addView(imageContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(96)));
        form.addView(imageScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));

        scroll.addView(form, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        renderSelectedImages();
    }

    private void authenticateAndLoadCategories() {
        authenticating = true;
        setPublishing(true, "连接中…");
        ForumApiClient.getInstance().ensureSession(this, readScope, new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (isFinishing() || isDestroyed()) return;
                authenticating = false;
                setPublishing(false, "发布");
                loadCategories();
                loadRecommendedTags();
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                authenticating = false;
                setPublishing(false, "发布");
                Toast.makeText(ForumCreateTopicActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderTypePill() {
        if (typePill == null) return;
        typePill.removeAllViews();
        addTypeTab("帖子", TYPE_TOPIC);
        addTypeTab("问答", TYPE_QA);
        addTypeTab("文章", TYPE_ARTICLE);
        updateTypeDependentViews();
    }

    private void addTypeTab(String label, int value) {
        boolean selected = topicType == value;
        TextView tab = text(label, 12.5f, selected ? 0xFF1877F2
                : (isDark() ? 0xFFAEB3BB : 0xFF66707A), selected);
        tab.setGravity(Gravity.CENTER);
        tab.setBackground(selected ? roundRect(isDark() ? 0xFF34465F : Color.WHITE, 14) : null);
        tab.setOnClickListener(v -> {
            if (publishing || topicType == value) return;
            topicType = value;
            pendingCategoryId = 0L;
            renderTypePill();
            updateContentHint();
            if (topicType != TYPE_ARTICLE) loadCategories();
        });
        typePill.addView(tab, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void loadCategories() {
        if (topicType == TYPE_ARTICLE) {
            categoriesLoaded = true;
            categories.clear();
            return;
        }
        final int generation = ++categoryGeneration;
        final int requestType = topicType;
        categoriesLoaded = false;
        categories.clear();
        List<String> waiting = new ArrayList<>();
        waiting.add("加载中…");
        ArrayAdapter<String> waitingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, waiting);
        categorySpinner.setAdapter(waitingAdapter);
        ForumApiClient.getInstance().getCategoriesForType(requestType, readScope,
                new ForumApiClient.ResultCallback<List<ForumApiClient.Category>>() {
            @Override
            public void onSuccess(@Nullable List<ForumApiClient.Category> data) {
                if (!isCategoryRequestCurrent(generation, requestType)) return;
                categories.clear();
                categoriesLoaded = true;
                if (data != null) {
                    for (ForumApiClient.Category category : data) appendCategory(category);
                }
                List<String> names = new ArrayList<>();
                for (ForumApiClient.Category category : categories) names.add(category.name);
                if (names.isEmpty()) names.add("暂无可发布板块");
                ArrayAdapter<String> adapter = new ArrayAdapter<>(ForumCreateTopicActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                categorySpinner.setAdapter(adapter);
                restoreCategorySelection();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isCategoryRequestCurrent(generation, requestType)) return;
                Toast.makeText(ForumCreateTopicActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void appendCategory(ForumApiClient.Category category) {
        if (category == null) return;
        if (category.id > 0 && (!category.adminOnlyPost || category.canPost)
                && !TextUtils.isEmpty(category.name)) {
            categories.add(category);
        }
        if (category.children != null) {
            for (ForumApiClient.Category child : category.children) appendCategory(child);
        }
    }

    private void publish() {
        if (publishing) return;
        String title = titleInput.getText().toString().trim();
        String content = contentInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("请输入标题");
            return;
        }
        if (title.codePointCount(0, title.length()) > 128) {
            titleInput.setError("标题不能超过128字");
            return;
        }
        if (content.isEmpty()) {
            contentInput.setError("请输入内容");
            return;
        }
        long categoryId = topicType == TYPE_ARTICLE ? 0L : selectedCategoryId();
        if (topicType != TYPE_ARTICLE && categoryId <= 0) {
            Toast.makeText(this, "板块尚未加载，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        final long selectedCategoryId = categoryId;
        final int selectedType = topicType;
        final int bountyScore = parseBountyScore();
        final List<String> tags = new ArrayList<>(selectedTags);
        final List<Uri> images = new ArrayList<>(selectedImages);
        final int generation = ++publishGeneration;
        saveDraft();
        setPublishing(true, images.isEmpty() ? "发布中…" : "处理图片…");
        ForumApiClient.getInstance().ensureSession(this, publishScope, new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (!isPublishActive(generation)) return;
                uploadImagesSequentially(generation, images, 0, new ArrayList<>(), uploaded -> {
                    if (selectedType == TYPE_ARTICLE) {
                        createArticle(generation, title, content, tags, uploaded);
                    } else {
                        createTopic(generation, selectedType, selectedCategoryId, title, content,
                                tags, uploaded, bountyScore);
                    }
                });
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isPublishActive(generation)) return;
                failPublish(message);
            }
        });
    }

    private void uploadImagesSequentially(int generation, List<Uri> images, int index,
                                          List<ForumApiClient.ImageInfo> uploaded,
                                          UploadsCallback callback) {
        if (!isPublishActive(generation)) return;
        if (index >= images.size()) {
            callback.onDone(uploaded);
            return;
        }
        publishButton.setText("图片 " + (index + 1) + "/" + images.size());
        Uri uri = images.get(index);
        try {
            imageExecutor.execute(() -> {
                File file = null;
                try {
                    file = ForumImageCompressor.compress(getApplicationContext(), uri);
                    if (!isPublishActive(generation)) {
                        deleteFile(file);
                        return;
                    }
                    pendingUploadFiles.add(file);
                    File uploadFile = file;
                    runOnUiThread(() -> {
                        if (!isPublishActive(generation)) {
                            cleanupUploadFile(uploadFile);
                            return;
                        }
                        ForumApiClient.getInstance().uploadImage(uploadFile, publishScope,
                                new ForumApiClient.ResultCallback<ForumApiClient.UploadResult>() {
                                    @Override
                                    public void onSuccess(@Nullable ForumApiClient.UploadResult result) {
                                        cleanupUploadFile(uploadFile);
                                        if (!isPublishActive(generation)) return;
                                        if (result == null || TextUtils.isEmpty(result.url)) {
                                            failPublish("图片上传返回数据不完整");
                                            return;
                                        }
                                        uploaded.add(new ForumApiClient.ImageInfo(result.url));
                                        uploadImagesSequentially(generation, images, index + 1,
                                                uploaded, callback);
                                    }

                                    @Override
                                    public void onError(@NonNull String message) {
                                        cleanupUploadFile(uploadFile);
                                        if (isPublishActive(generation)) failPublish(message);
                                    }
                                });
                    });
                } catch (Throwable error) {
                    deleteFile(file);
                    runOnUiThread(() -> {
                        if (isPublishActive(generation)) {
                            failPublish("图片处理失败：" + safeMessage(error));
                        }
                    });
                }
            });
        } catch (RuntimeException error) {
            if (isPublishActive(generation)) failPublish("图片处理任务无法启动");
        }
    }

    private void createTopic(int generation, int type, long categoryId, String title,
                             String content, List<String> tags,
                             List<ForumApiClient.ImageInfo> images, int bountyScore) {
        if (!isPublishActive(generation)) return;
        publishButton.setText("发布中…");
        ForumApiClient.getInstance().createTopic(type, categoryId, title, content,
                tags, images, bountyScore, publishScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Topic>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Topic topic) {
                        if (!isPublishActive(generation)) return;
                        discardDraft = true;
                        ForumDraftStore.clear(ForumCreateTopicActivity.this);
                        setResult(Activity.RESULT_OK);
                        String id = topic == null ? "" : topic.id;
                        if (!TextUtils.isEmpty(id)) {
                            ForumTopicActivity.open(ForumCreateTopicActivity.this, id);
                        }
                        finish();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (isPublishActive(generation)) failPublish(message);
                    }
                });
    }

    private void createArticle(int generation, String title, String content,
                               List<String> tags, List<ForumApiClient.ImageInfo> images) {
        if (!isPublishActive(generation)) return;
        publishButton.setText("发布中…");
        ForumApiClient.ImageInfo cover = images == null || images.isEmpty() ? null : images.get(0);
        ForumApiClient.getInstance().createArticle(title, buildArticleSummary(content), content,
                tags, cover, publishScope, new ForumApiClient.ResultCallback<ForumApiClient.Article>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Article article) {
                        if (!isPublishActive(generation)) return;
                        discardDraft = true;
                        ForumDraftStore.clear(ForumCreateTopicActivity.this);
                        setResult(Activity.RESULT_OK);
                        long id = article == null ? 0L : article.id;
                        if (id > 0L) {
                            startActivity(ForumArticleActivity.createIntent(
                                    ForumCreateTopicActivity.this, id));
                        }
                        finish();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (isPublishActive(generation)) failPublish(message);
                    }
                });
    }

    private void failPublish(String message) {
        if (isFinishing() || isDestroyed()) return;
        setPublishing(false, "发布");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setPublishing(boolean value, String label) {
        publishing = value;
        if (publishButton != null) {
            publishButton.setText(label);
            publishButton.setAlpha(value ? 0.55f : 1f);
            publishButton.setEnabled(!value);
        }
        if (titleInput != null) titleInput.setEnabled(!value);
        if (contentInput != null) contentInput.setEnabled(!value);
        setTagSelectionEnabled(!value);
        if (bountyInput != null) bountyInput.setEnabled(!value);
        if (categorySpinner != null) categorySpinner.setEnabled(!value);
        if (typePill != null) typePill.setEnabled(!value);
        if (relationButton != null) relationButton.setEnabled(!value);
    }

    private void showInsertReferenceDialog() {
        if (publishing || contentInput == null) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(8), dp(18), 0);

        Spinner typeSpinner = new Spinner(this);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("文章", "帖子"));
        typeSpinner.setAdapter(typeAdapter);
        panel.addView(typeSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        EditText labelInput = input("显示文字，例如：缅语声调基础", false);
        int selectionStart = Math.max(0, contentInput.getSelectionStart());
        int selectionEnd = Math.max(selectionStart, contentInput.getSelectionEnd());
        if (selectionEnd > selectionStart) {
            labelInput.setText(contentInput.getText().subSequence(selectionStart, selectionEnd));
            labelInput.setSelection(labelInput.length());
        }
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(6);
        panel.addView(labelInput, labelParams);

        EditText targetInput = input("文章/帖子 ID 或复制的链接", false);
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        targetParams.topMargin = dp(8);
        panel.addView(targetInput, targetParams);

        TextView hint = text("可在目标文章或帖子右上角菜单复制链接；插入后点击会在 App 内打开。",
                12, isDark() ? 0xFF92979F : 0xFF7B818A, false);
        hint.setPadding(0, dp(8), 0, 0);
        panel.addView(hint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("插入关联内容")
                .setView(panel)
                .setNegativeButton("取消", null)
                .setPositiveButton("插入", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String label = labelInput.getText().toString().trim();
                    String target = targetInput.getText().toString().trim();
                    boolean articleTarget = typeSpinner.getSelectedItemPosition() == 0;
                    String url = ForumLinkRouter.normalizeReference(target, articleTarget);
                    if (TextUtils.isEmpty(label)) {
                        labelInput.setError("请输入显示文字");
                        return;
                    }
                    if (TextUtils.isEmpty(url)) {
                        targetInput.setError("请输入有效的文章/帖子 ID 或论坛链接");
                        return;
                    }
                    insertReferenceAtSelection(label, url);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void insertReferenceAtSelection(String label, String url) {
        if (contentInput == null) return;
        int start = Math.max(0, contentInput.getSelectionStart());
        int end = Math.max(start, contentInput.getSelectionEnd());
        String markdown = ForumLinkRouter.markdownReference(label, url);
        contentInput.getText().replace(start, end, markdown);
        int cursor = Math.min(contentInput.length(), start + markdown.length());
        contentInput.setSelection(cursor);
        contentInput.requestFocus();
    }

    private void renderSelectedImages() {
        if (imageContainer == null) return;
        imageContainer.removeAllViews();
        if (selectedImages.isEmpty()) {
            TextView empty = text("未选择图片", 13, isDark() ? 0xFF8E9299 : 0xFF8A8F96, false);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            imageContainer.addView(empty, new LinearLayout.LayoutParams(dp(120), dp(88)));
            return;
        }
        for (Uri uri : new ArrayList<>(selectedImages)) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(uri).into(image);
            box.addView(image, new LinearLayout.LayoutParams(dp(78), dp(70)));
            TextView remove = text("移除", 12, 0xFFE14A4A, false);
            remove.setGravity(Gravity.CENTER);
            remove.setOnClickListener(v -> {
                if (publishing) return;
                selectedImages.remove(uri);
                renderSelectedImages();
            });
            box.addView(remove, new LinearLayout.LayoutParams(dp(78), dp(24)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(78), dp(94));
            params.rightMargin = dp(8);
            imageContainer.addView(box, params);
        }
    }

    private void restoreComposerState(@Nullable Bundle state) {
        if (state != null) {
            titleInput.setText(state.getString(STATE_TITLE, ""));
            contentInput.setText(state.getString(STATE_CONTENT, ""));
            restoreSelectedTags(state.getString(STATE_TAGS, ""));
            bountyInput.setText(state.getString(STATE_BOUNTY, ""));
            topicType = state.getInt(STATE_TYPE, 0);
            pendingCategoryId = state.getLong(STATE_CATEGORY, 0L);
            ArrayList<String> images = state.getStringArrayList(STATE_IMAGES);
            if (images != null) {
                for (String value : images) {
                    if (!TextUtils.isEmpty(value) && selectedImages.size() < MAX_IMAGES) {
                        selectedImages.add(Uri.parse(value));
                    }
                }
            }
            renderTypePill();
            updateContentHint();
            renderSelectedImages();
            return;
        }
        ForumDraftStore.Draft draft = ForumDraftStore.load(this);
        if (draft == null) return;
        titleInput.setText(draft.title);
        contentInput.setText(draft.content);
        restoreSelectedTags(draft.tags);
        bountyInput.setText(draft.bounty);
        topicType = draft.topicType;
        pendingCategoryId = draft.categoryId;
        renderTypePill();
        updateContentHint();
        Toast.makeText(this, "已恢复上次未发布的草稿", Toast.LENGTH_SHORT).show();
    }

    private void saveDraft() {
        if (titleInput == null || contentInput == null) return;
        ForumDraftStore.Draft draft = new ForumDraftStore.Draft();
        draft.title = valueOf(titleInput);
        draft.content = valueOf(contentInput);
        draft.tags = TextUtils.join(",", selectedTags);
        draft.bounty = valueOf(bountyInput);
        draft.topicType = topicType;
        draft.categoryId = categoryIdForState();
        ForumDraftStore.save(this, draft);
    }

    private void updateContentHint() {
        if (contentInput == null) return;
        if (topicType == TYPE_QA) {
            contentInput.setHint("请清楚描述问题、已经尝试的方法和期望得到的帮助…");
        } else if (topicType == TYPE_ARTICLE) {
            contentInput.setHint("撰写完整文章内容，建议分段表达…");
        } else {
            contentInput.setHint("分享你的经验、观点或学习内容…");
        }
    }

    private void updateTypeDependentViews() {
        boolean article = topicType == TYPE_ARTICLE;
        if (headingView != null) {
            headingView.setText(article ? "发布文章" : (topicType == TYPE_QA ? "发布问答" : "发布帖子"));
        }
        if (categoryLabel != null) categoryLabel.setVisibility(article ? View.GONE : View.VISIBLE);
        if (categorySpinner != null) categorySpinner.setVisibility(article ? View.GONE : View.VISIBLE);
        if (bountyInput != null) bountyInput.setVisibility(topicType == TYPE_QA ? View.VISIBLE : View.GONE);
        if (relationButton != null) relationButton.setVisibility(article ? View.VISIBLE : View.GONE);
        if (imageLabel != null) {
            imageLabel.setText(article ? "封面图（最多1张）" : "图片（最多6张）");
        }
        if (article && selectedImages.size() > 1) {
            Uri cover = selectedImages.get(0);
            selectedImages.clear();
            selectedImages.add(cover);
            renderSelectedImages();
        }
    }

    private static String buildArticleSummary(String content) {
        if (TextUtils.isEmpty(content)) return "";
        String compact = content.replaceAll("\\s+", " ").trim();
        int end = compact.offsetByCodePoints(0, Math.min(140,
                compact.codePointCount(0, compact.length())));
        return compact.substring(0, end);
    }

    private long selectedCategoryId() {
        if (!categoriesLoaded || categorySpinner == null) return 0L;
        int position = categorySpinner.getSelectedItemPosition();
        if (position >= 0 && position < categories.size()) return categories.get(position).id;
        return 0L;
    }

    private long categoryIdForState() {
        long selected = selectedCategoryId();
        return selected > 0L ? selected : pendingCategoryId;
    }

    private void restoreCategorySelection() {
        if (pendingCategoryId <= 0L || categorySpinner == null) return;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == pendingCategoryId) {
                categorySpinner.setSelection(i);
                return;
            }
        }
    }

    private boolean isCategoryRequestCurrent(int generation, int requestType) {
        return !destroyed && !isFinishing() && generation == categoryGeneration
                && requestType == topicType;
    }

    private boolean isPublishActive(int generation) {
        return !destroyed && !isFinishing() && publishing && generation == publishGeneration;
    }

    private void cleanupUploadFile(@Nullable File file) {
        if (file != null) pendingUploadFiles.remove(file);
        deleteFile(file);
    }

    private void cleanupPendingFiles() {
        List<File> files;
        synchronized (pendingUploadFiles) {
            files = new ArrayList<>(pendingUploadFiles);
            pendingUploadFiles.clear();
        }
        for (File file : files) deleteFile(file);
    }

    private static void deleteFile(@Nullable File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @NonNull
    private static String valueOf(@Nullable EditText input) {
        return input == null ? "" : input.getText().toString();
    }

    private int parseBountyScore() {
        if (topicType != TYPE_QA || bountyInput == null) return 0;
        String raw = bountyInput.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) return 0;
        try { return Math.max(0, Integer.parseInt(raw)); }
        catch (Throwable ignored) { return 0; }
    }

    private void loadRecommendedTags() {
        tagsLoaded = false;
        renderRecommendedTags();
        ForumApiClient.getInstance().getRecommendedTags(readScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Tag>>() {
            @Override
            public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Tag> data) {
                if (destroyed || isFinishing()) return;
                recommendedTags.clear();
                if (data != null && data.results != null) {
                    for (ForumApiClient.Tag tag : data.results) {
                        if (tag == null || TextUtils.isEmpty(tag.name)) continue;
                        recommendedTags.add(tag);
                        if (recommendedTags.size() >= 20) break;
                    }
                }
                tagsLoaded = true;
                renderRecommendedTags();
            }

            @Override
            public void onError(@NonNull String message) {
                if (destroyed || isFinishing()) return;
                tagsLoaded = true;
                renderRecommendedTags();
                Toast.makeText(ForumCreateTopicActivity.this,
                        "推荐标签加载失败，可稍后重试", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void restoreSelectedTags(@Nullable String raw) {
        selectedTags.clear();
        if (!TextUtils.isEmpty(raw)) {
            for (String item : raw.split("[,，]")) {
                String value = item == null ? "" : item.trim();
                if (!value.isEmpty()) selectedTags.add(value);
                if (selectedTags.size() >= 5) break;
            }
        }
        renderRecommendedTags();
    }

    private void renderRecommendedTags() {
        if (tagContainer == null || tagHint == null) return;
        tagContainer.removeAllViews();
        if (!tagsLoaded && recommendedTags.isEmpty()) {
            tagHint.setText("加载推荐标签…");
            tagHint.setVisibility(View.VISIBLE);
            return;
        }

        List<String> names = new ArrayList<>();
        names.addAll(selectedTags);
        for (ForumApiClient.Tag tag : recommendedTags) {
            String name = tag == null ? "" : safeTag(tag.name);
            if (!name.isEmpty() && !names.contains(name)) names.add(name);
        }
        if (names.isEmpty()) {
            tagHint.setText("暂无推荐标签，可以不选择");
            tagHint.setVisibility(View.VISIBLE);
            return;
        }

        tagHint.setText(selectedTags.isEmpty()
                ? "选择与你内容最相关的标签"
                : "已选择 " + selectedTags.size() + "/5");
        tagHint.setVisibility(View.VISIBLE);
        for (String name : names) {
            boolean selected = selectedTags.contains(name);
            TextView chip = text(name, 12.5f, selected ? 0xFFFFFFFF
                    : (isDark() ? 0xFFD2D6DC : 0xFF56606B), selected);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setBackground(roundRect(selected ? 0xFF1877F2
                    : (isDark() ? 0xFF292C31 : 0xFFF0F2F5), 16));
            chip.setEnabled(!publishing);
            chip.setAlpha(publishing ? 0.58f : 1f);
            chip.setOnClickListener(v -> toggleTag(name));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            params.rightMargin = dp(8);
            tagContainer.addView(chip, params);
        }
    }

    private void toggleTag(@NonNull String name) {
        if (publishing) return;
        if (selectedTags.contains(name)) {
            selectedTags.remove(name);
        } else {
            if (selectedTags.size() >= 5) {
                Toast.makeText(this, "最多选择5个标签", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedTags.add(name);
        }
        renderRecommendedTags();
    }

    private void setTagSelectionEnabled(boolean enabled) {
        if (tagContainer == null) return;
        for (int i = 0; i < tagContainer.getChildCount(); i++) {
            View child = tagContainer.getChildAt(i);
            child.setEnabled(enabled);
            child.setAlpha(enabled ? 1f : 0.58f);
        }
    }

    @NonNull
    private static String safeTag(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private TextView label(String value) {
        return text(value, 14, isDark() ? 0xFFD4D6DA : 0xFF454A51, true);
    }

    private EditText input(String hint, boolean multiline) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTextColor(isDark() ? Color.WHITE : 0xFF202328);
        view.setHintTextColor(isDark() ? 0xFF777B82 : 0xFF9A9FA6);
        view.setBackgroundColor(isDark() ? 0xFF222429 : 0xFFF4F5F7);
        view.setPadding(dp(12), dp(11), dp(12), dp(11));
        if (multiline) {
            view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        return view;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, float radiusDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private boolean isDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return TextUtils.isEmpty(message) ? "未知错误" : message;
    }

    private interface UploadsCallback {
        void onDone(List<ForumApiClient.ImageInfo> images);
    }
}
