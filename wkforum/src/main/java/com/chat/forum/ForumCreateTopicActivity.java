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
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native topic and Q&A composer. */
public class ForumCreateTopicActivity extends AppCompatActivity {
    private static final int MAX_IMAGES = 6;

    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final List<Uri> selectedImages = new ArrayList<>();
    private final List<ForumApiClient.Category> categories = new ArrayList<>();
    private EditText titleInput;
    private EditText contentInput;
    private EditText tagsInput;
    private Spinner categorySpinner;
    private LinearLayout imageContainer;
    private LinearLayout typePill;
    private EditText bountyInput;
    private int topicType;
    private TextView publishButton;
    private boolean publishing;

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null || uris.isEmpty()) return;
                for (Uri uri : uris) {
                    if (uri == null || selectedImages.contains(uri)) continue;
                    if (selectedImages.size() >= MAX_IMAGES) break;
                    selectedImages.add(uri);
                }
                if (uris.size() + selectedImages.size() > MAX_IMAGES) {
                    Toast.makeText(this, "每篇帖子最多选择6张图片", Toast.LENGTH_SHORT).show();
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
        authenticateAndLoadCategories();
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
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
            if (!publishing) finish();
        });
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
        TextView heading = text("发布帖子", 18, dark ? Color.WHITE : 0xFF1C1E21, true);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1f));
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
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(dp(170), dp(34));
        typeParams.topMargin = dp(7);
        form.addView(typePill, typeParams);
        renderTypePill();

        TextView categoryLabel = label("板块");
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

        tagsInput = input("标签，可选；多个标签用逗号分隔", false);
        LinearLayout.LayoutParams tagsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagsParams.topMargin = dp(12);
        form.addView(tagsInput, tagsParams);

        LinearLayout imageHeader = new LinearLayout(this);
        imageHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView imageLabel = label("图片（最多6张）");
        imageHeader.addView(imageLabel, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView choose = text("选择图片", 14, 0xFF1877F2, true);
        choose.setGravity(Gravity.CENTER);
        choose.setOnClickListener(v -> {
            if (publishing) return;
            if (selectedImages.size() >= MAX_IMAGES) {
                Toast.makeText(this, "最多6张图片", Toast.LENGTH_SHORT).show();
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
        setPublishing(true, "连接中…");
        ForumApiClient.getInstance().ensureSession(this, new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (isFinishing() || isDestroyed()) return;
                setPublishing(false, "发布");
                loadCategories();
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                setPublishing(false, "发布");
                Toast.makeText(ForumCreateTopicActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderTypePill() {
        if (typePill == null) return;
        typePill.removeAllViews();
        addTypeTab("讨论", 0);
        addTypeTab("问答", 2);
        if (bountyInput != null) bountyInput.setVisibility(topicType == 2 ? View.VISIBLE : View.GONE);
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
            renderTypePill();
            loadCategories();
            contentInput.setHint(topicType == 2
                    ? "请清楚描述问题、已经尝试的方法和期望得到的帮助…"
                    : "分享你的经验、观点或学习内容…");
        });
        typePill.addView(tab, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void loadCategories() {
        categories.clear();
        List<String> waiting = new ArrayList<>();
        waiting.add("加载中…");
        ArrayAdapter<String> waitingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, waiting);
        categorySpinner.setAdapter(waitingAdapter);
        ForumApiClient.getInstance().getCategoriesForType(topicType,
                new ForumApiClient.ResultCallback<List<ForumApiClient.Category>>() {
            @Override
            public void onSuccess(@Nullable List<ForumApiClient.Category> data) {
                if (isFinishing() || isDestroyed()) return;
                categories.clear();
                if (data != null) {
                    for (ForumApiClient.Category category : data) appendCategory(category);
                }
                List<String> names = new ArrayList<>();
                for (ForumApiClient.Category category : categories) names.add(category.name);
                if (names.isEmpty()) names.add(topicType == 2 ? "请先在网页后台创建问答板块" : "暂无可发布板块");
                ArrayAdapter<String> adapter = new ArrayAdapter<>(ForumCreateTopicActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                categorySpinner.setAdapter(adapter);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isFinishing()) Toast.makeText(ForumCreateTopicActivity.this, message, Toast.LENGTH_LONG).show();
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
        long categoryId = 0;
        int position = categorySpinner.getSelectedItemPosition();
        if (position >= 0 && position < categories.size()) categoryId = categories.get(position).id;
        if (categoryId <= 0) {
            Toast.makeText(this, "板块尚未加载，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        final long selectedCategoryId = categoryId;
        final int selectedType = topicType;
        final int bountyScore = parseBountyScore();
        final List<String> tags = parseTags(tagsInput.getText().toString());
        setPublishing(true, selectedImages.isEmpty() ? "发布中…" : "处理图片…");
        ForumApiClient.getInstance().ensureSession(this, new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                uploadImagesSequentially(0, new ArrayList<>(), uploaded ->
                        createTopic(selectedType, selectedCategoryId, title, content,
                                tags, uploaded, bountyScore));
            }

            @Override
            public void onError(@NonNull String message) {
                setPublishing(false, "发布");
                Toast.makeText(ForumCreateTopicActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void uploadImagesSequentially(int index, List<ForumApiClient.ImageInfo> uploaded,
                                          UploadsCallback callback) {
        if (index >= selectedImages.size()) {
            callback.onDone(uploaded);
            return;
        }
        publishButton.setText("图片 " + (index + 1) + "/" + selectedImages.size());
        Uri uri = selectedImages.get(index);
        imageExecutor.execute(() -> {
            File file;
            try {
                file = ForumImageCompressor.compress(getApplicationContext(), uri);
            } catch (Throwable error) {
                runOnUiThread(() -> failPublish("图片处理失败：" + safeMessage(error)));
                return;
            }
            runOnUiThread(() -> ForumApiClient.getInstance().uploadImage(file,
                    new ForumApiClient.ResultCallback<ForumApiClient.UploadResult>() {
                        @Override
                        public void onSuccess(@Nullable ForumApiClient.UploadResult result) {
                            file.delete();
                            if (result == null || TextUtils.isEmpty(result.url)) {
                                failPublish("图片上传返回数据不完整");
                                return;
                            }
                            uploaded.add(new ForumApiClient.ImageInfo(result.url));
                            uploadImagesSequentially(index + 1, uploaded, callback);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            file.delete();
                            failPublish(message);
                        }
                    }));
        });
    }

    private void createTopic(int type, long categoryId, String title, String content,
                             List<String> tags, List<ForumApiClient.ImageInfo> images,
                             int bountyScore) {
        publishButton.setText("发布中…");
        ForumApiClient.getInstance().createTopic(type, categoryId, title, content,
                tags, images, bountyScore,
                new ForumApiClient.ResultCallback<ForumApiClient.Topic>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Topic topic) {
                        if (isFinishing() || isDestroyed()) return;
                        setResult(Activity.RESULT_OK);
                        String id = topic == null ? "" : topic.id;
                        if (!TextUtils.isEmpty(id)) ForumTopicActivity.open(ForumCreateTopicActivity.this, id);
                        finish();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        failPublish(message);
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
        }
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

    private int parseBountyScore() {
        if (topicType != 2 || bountyInput == null) return 0;
        String raw = bountyInput.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) return 0;
        try { return Math.max(0, Integer.parseInt(raw)); }
        catch (Throwable ignored) { return 0; }
    }

    private List<String> parseTags(String raw) {
        if (TextUtils.isEmpty(raw)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String item : Arrays.asList(raw.split("[,，]"))) {
            String value = item.trim();
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
            if (result.size() >= 5) break;
        }
        return result;
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
