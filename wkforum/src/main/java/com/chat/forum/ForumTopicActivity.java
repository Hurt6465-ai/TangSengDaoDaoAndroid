package com.chat.forum;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.chat.base.ui.components.AvatarView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native topic detail with comments, two-level replies and image comments. */
public class ForumTopicActivity extends AppCompatActivity {
    private static final String EXTRA_TOPIC_ID = "forum_topic_id";
    private static final int MAX_COMMENT_IMAGES = 3;

    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final List<ForumApiClient.Comment> comments = new ArrayList<>();
    private final List<Uri> selectedCommentImages = new ArrayList<>();
    private final Set<Long> loadingReplyIds = new HashSet<>();
    private String topicId;
    private ForumApiClient.Topic currentTopic;
    private ScrollView scrollView;
    private LinearLayout articleContainer;
    private LinearLayout commentsContainer;
    private TextView commentHeading;
    private TextView toolbarMoreButton;
    private TextView stateView;
    private TextView loadMoreCommentsView;
    private EditText commentInput;
    private TextView replyHint;
    private TextView imageCountView;
    private TextView sendButton;
    private String commentsCursor = "";
    private boolean commentsHasMore;
    private boolean loadingComments;
    private boolean refreshCommentsPending;
    private boolean sending;
    private boolean topicActionBusy;
    private boolean authorFollowed;
    private boolean authorFollowStateLoaded;
    private long replyParentId;
    private long replyQuoteId;

    private final ActivityResultLauncher<String> commentImagePicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null) return;
                for (Uri uri : uris) {
                    if (uri == null || selectedCommentImages.contains(uri)) continue;
                    if (selectedCommentImages.size() >= MAX_COMMENT_IMAGES) break;
                    selectedCommentImages.add(uri);
                }
                if (selectedCommentImages.size() >= MAX_COMMENT_IMAGES && uris.size() > MAX_COMMENT_IMAGES) {
                    Toast.makeText(this, "评论最多3张图片", Toast.LENGTH_SHORT).show();
                }
                updateImageCount();
            });

    public static Intent createIntent(Context context, String topicId) {
        Intent intent = new Intent(context, ForumTopicActivity.class);
        intent.putExtra(EXTRA_TOPIC_ID, topicId == null ? "" : topicId);
        return intent;
    }

    public static void open(Context context, String topicId) {
        if (context == null || TextUtils.isEmpty(topicId)) return;
        context.startActivity(createIntent(context, topicId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        topicId = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_TOPIC_ID);
        if (TextUtils.isEmpty(topicId)) {
            finish();
            return;
        }
        buildView();
        loadTopic();
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
        toolbar.setPadding(dp(4), 0, dp(4), 0);
        toolbar.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        TextView back = text("←", 25, dark ? Color.WHITE : 0xFF1C1E21, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(selectableBackground());
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
        TextView title = text("帖子", 18, dark ? Color.WHITE : 0xFF1C1E21, true);
        title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));
        toolbarMoreButton = text("•••", 18, dark ? 0xFFD8DADE : 0xFF454B53, true);
        toolbarMoreButton.setGravity(Gravity.CENTER);
        toolbarMoreButton.setBackground(selectableBackground());
        toolbarMoreButton.setOnClickListener(v -> showTopicMenu());
        toolbarMoreButton.setVisibility(View.INVISIBLE);
        toolbar.addView(toolbarMoreButton, new LinearLayout.LayoutParams(dp(48), dp(52)));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(this);
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(dark ? 0xFF111214 : 0xFFF6F7F9);

        articleContainer = new LinearLayout(this);
        articleContainer.setOrientation(LinearLayout.VERTICAL);
        articleContainer.setPadding(dp(18), dp(18), dp(18), dp(26));
        articleContainer.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        page.addView(articleContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        commentHeading = text("评论", 18, dark ? Color.WHITE : 0xFF202328, true);
        commentHeading.setPadding(dp(18), dp(18), dp(18), dp(10));
        commentHeading.setBackgroundColor(dark ? 0xFF111214 : 0xFFF6F7F9);
        page.addView(commentHeading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        commentsContainer = new LinearLayout(this);
        commentsContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(commentsContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loadMoreCommentsView = text("加载更多评论", 14, 0xFF1877F2, true);
        loadMoreCommentsView.setGravity(Gravity.CENTER);
        loadMoreCommentsView.setPadding(dp(16), dp(16), dp(16), dp(24));
        loadMoreCommentsView.setOnClickListener(v -> loadComments(false));
        loadMoreCommentsView.setVisibility(View.GONE);
        page.addView(loadMoreCommentsView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(scrollView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stateView = text("正在加载…", 14, dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
        stateView.setGravity(Gravity.CENTER);
        body.addView(stateView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildComposer(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private View buildComposer() {
        boolean dark = isDark();
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(10), dp(6), dp(10), dp(8));
        wrapper.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        View topLine = new View(this);
        topLine.setBackgroundColor(dark ? 0xFF2A2C31 : 0xFFE9EBEF);
        wrapper.addView(topLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        replyHint = text("", 12, dark ? 0xFFFFC46B : 0xFF986400, false);
        replyHint.setPadding(dp(10), dp(6), dp(10), dp(4));
        replyHint.setVisibility(View.GONE);
        replyHint.setOnClickListener(v -> clearReplyTarget());
        wrapper.addView(replyHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, 0);

        TextView image = text("＋", 24, dark ? 0xFFD7DADE : 0xFF50565E, false);
        image.setGravity(Gravity.CENTER);
        image.setBackground(roundRect(dark ? 0xFF25272C : 0xFFF1F3F5, 20));
        image.setOnClickListener(v -> {
            if (sending) return;
            if (selectedCommentImages.size() >= MAX_COMMENT_IMAGES) {
                selectedCommentImages.clear();
                updateImageCount();
                Toast.makeText(this, "已清空待发送图片", Toast.LENGTH_SHORT).show();
            } else {
                commentImagePicker.launch("image/*");
            }
        });
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        imageParams.rightMargin = dp(8);
        row.addView(image, imageParams);

        commentInput = new EditText(this);
        commentInput.setHint("友善交流，说点什么…");
        commentInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        commentInput.setTextColor(dark ? Color.WHITE : 0xFF202328);
        commentInput.setHintTextColor(dark ? 0xFF777B82 : 0xFF9A9FA6);
        commentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        commentInput.setMaxLines(5);
        commentInput.setPadding(dp(14), dp(9), dp(14), dp(9));
        commentInput.setBackground(roundRect(dark ? 0xFF24262B : 0xFFF1F3F5, 20));
        row.addView(commentInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = text("发送", 14, Color.WHITE, true);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setBackground(roundRect(0xFF1877F2, 18));
        sendButton.setOnClickListener(v -> sendComment());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(62), dp(38));
        sendParams.leftMargin = dp(8);
        row.addView(sendButton, sendParams);
        wrapper.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        imageCountView = text("", 11, dark ? 0xFF9EA2A9 : 0xFF737880, false);
        imageCountView.setPadding(dp(50), dp(4), dp(8), 0);
        imageCountView.setOnClickListener(v -> {
            if (sending) return;
            selectedCommentImages.clear();
            updateImageCount();
        });
        imageCountView.setVisibility(View.GONE);
        wrapper.addView(imageCountView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    private void loadTopic() {
        ForumApiClient.getInstance().ensureSession(this, new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                requestTopic();
            }

            @Override
            public void onError(@NonNull String message) {
                requestTopic();
            }
        });
    }

    private void requestTopic() {
        ForumApiClient.getInstance().getTopic(topicId, new ForumApiClient.ResultCallback<ForumApiClient.Topic>() {
            @Override
            public void onSuccess(@Nullable ForumApiClient.Topic topic) {
                if (isDead()) return;
                if (topic == null) {
                    showError("帖子不存在或已被删除");
                    return;
                }
                renderTopic(topic);
                loadAuthorFollowState(topic);
                loadComments(true);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isDead()) showError(message);
            }
        });
    }

    private void renderTopic(ForumApiClient.Topic topic) {
        currentTopic = topic;
        boolean dark = isDark();
        articleContainer.removeAllViews();
        if (toolbarMoreButton != null) toolbarMoreButton.setVisibility(View.VISIBLE);

        if (topic.category != null && !TextUtils.isEmpty(topic.category.name)) {
            TextView category = text(topic.category.name, 12, 0xFF1877F2, true);
            category.setGravity(Gravity.CENTER);
            category.setPadding(dp(10), 0, dp(10), 0);
            category.setBackground(roundRect(dark ? 0xFF243B59 : 0xFFEAF3FF, 12));
            articleContainer.addView(category, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)));
        }

        TextView title = text(safe(topic.title), 24, dark ? Color.WHITE : 0xFF17191C, true);
        title.setLineSpacing(0, 1.10f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = topic.category == null || TextUtils.isEmpty(topic.category.name) ? 0 : dp(12);
        articleContainer.addView(title, titleParams);

        articleContainer.addView(buildAuthorRow(topic), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(dark ? 0xFF292B30 : 0xFFEEF0F2);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = dp(16);
        dividerParams.bottomMargin = dp(18);
        articleContainer.addView(divider, dividerParams);

        TextView content = htmlText(TextUtils.isEmpty(topic.content) ? safe(topic.summary) : topic.content, 16);
        articleContainer.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addRemoteImages(articleContainer, topic.imageList, dp(12));

        TextView stats = text("浏览 " + topic.viewCount + "   ·   评论 " + topic.commentCount + "   ·   赞 " + topic.likeCount,
                12, dark ? 0xFF8F949C : 0xFF7A8088, false);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = dp(22);
        articleContainer.addView(stats, statsParams);
        articleContainer.addView(buildTopicActions(topic),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (commentHeading != null) {
            commentHeading.setText(topic.commentCount > 0 ? "评论  " + topic.commentCount : "评论");
        }
        stateView.setVisibility(View.GONE);
    }

    private View buildAuthorRow(ForumApiClient.Topic topic) {
        boolean dark = isDark();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(14);
        row.setLayoutParams(rowParams);

        AvatarView avatar = new AvatarView(this);
        avatar.setSize(40);
        String author = topic.user == null || TextUtils.isEmpty(topic.user.nickname)
                ? "用户" : topic.user.nickname;
        String avatarUrl = topic.user == null ? "" : ForumApiClient.getInstance().resolveUrl(
                TextUtils.isEmpty(topic.user.smallAvatar) ? topic.user.avatar : topic.user.smallAvatar);
        String avatarSeed = topic.user == null ? author : safe(topic.user.id);
        avatar.showAvatarUrl(avatarUrl, avatarSeed, author, avatarSeed);
        avatar.showFlag(topic.user == null ? "" : (TextUtils.isEmpty(topic.user.countryCode)
                ? topic.user.country : topic.user.countryCode));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView authorView = text(author, 14, dark ? Color.WHITE : 0xFF272B31, true);
        TextView meta = text(formatDate(topic.createTime), 12,
                dark ? 0xFF8F949C : 0xFF7A8088, false);
        copy.addView(authorView);
        copy.addView(meta);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);

        String currentForumUserId = ForumApiClient.getInstance().getCurrentForumUserId();
        if (topic.user != null && !TextUtils.isEmpty(topic.user.id)
                && !TextUtils.equals(currentForumUserId, topic.user.id)) {
            String followLabel = authorFollowStateLoaded
                    ? (authorFollowed ? "已关注" : "关注") : "关注";
            TextView follow = text(followLabel, 13,
                    authorFollowed ? (dark ? 0xFFA7ADB5 : 0xFF6F7780) : 0xFF1877F2, true);
            follow.setGravity(Gravity.CENTER);
            follow.setBackground(roundRect(authorFollowed
                    ? (dark ? 0xFF25272C : 0xFFF1F3F5)
                    : (dark ? 0xFF243B59 : 0xFFEAF3FF), 14));
            follow.setOnClickListener(v -> changeAuthorFollow(topic));
            row.addView(follow, new LinearLayout.LayoutParams(dp(64), dp(32)));
        }
        return row;
    }

    private View buildTopicActions(ForumApiClient.Topic topic) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, 0);

        TextView like = actionButton(topic.liked ? "已赞  " + topic.likeCount : "赞  " + topic.likeCount,
                topic.liked);
        like.setOnClickListener(v -> changeLike(topic));
        addActionButton(row, like);

        TextView favorite = actionButton(topic.favorited ? "已收藏" : "收藏", topic.favorited);
        favorite.setOnClickListener(v -> changeFavorite(topic));
        addActionButton(row, favorite);

        TextView comment = actionButton("评论  " + topic.commentCount, false);
        comment.setOnClickListener(v -> focusCommentInput());
        addActionButton(row, comment);
        return row;
    }

    private TextView actionButton(String label, boolean active) {
        TextView button = text(label, 13,
                active ? 0xFF1877F2 : (isDark() ? 0xFFD5D7DB : 0xFF50555D), true);
        button.setGravity(Gravity.CENTER);
        GradientDrawable background = roundRect(
                active ? (isDark() ? 0xFF243B59 : 0xFFEAF3FF)
                        : (isDark() ? 0xFF24262B : 0xFFF1F3F5), 16);
        if (!active) {
            background.setStroke(dp(1), isDark() ? 0xFF303239 : 0xFFE7E9ED);
        }
        button.setBackground(background);
        return button;
    }

    private void addActionButton(LinearLayout row, TextView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, params);
    }

    private void focusCommentInput() {
        if (commentInput == null) return;
        scrollView.post(() -> {
            scrollView.fullScroll(View.FOCUS_DOWN);
            commentInput.requestFocus();
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(commentInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void showTopicMenu() {
        ForumApiClient.Topic topic = currentTopic;
        if (topic == null || topicActionBusy) return;
        List<String> actions = new ArrayList<>();
        List<Runnable> callbacks = new ArrayList<>();
        ForumApiClient client = ForumApiClient.getInstance();

        if (client.hasPermission("dashboard.topic.sticky")) {
            actions.add(topic.sticky ? "取消置顶" : "置顶帖子");
            callbacks.add(() -> changeTopicSticky(topic));
        }
        if (client.hasPermission("dashboard.topic.recommend")) {
            actions.add(topic.recommend ? "取消精华" : "设为精华");
            callbacks.add(() -> changeTopicRecommended(topic));
        }

        String currentForumUserId = client.getCurrentForumUserId();
        if (topic.user != null && !TextUtils.isEmpty(topic.user.id)
                && !TextUtils.equals(currentForumUserId, topic.user.id)) {
            actions.add(authorFollowStateLoaded && authorFollowed ? "取消关注作者" : "关注作者");
            callbacks.add(() -> changeAuthorFollow(topic));
        }
        actions.add(topic.favorited ? "取消收藏" : "收藏帖子");
        callbacks.add(() -> changeFavorite(topic));
        actions.add("举报帖子");
        callbacks.add(this::showReportDialog);

        boolean ownTopic = topic.user != null && !TextUtils.isEmpty(currentForumUserId)
                && TextUtils.equals(currentForumUserId, topic.user.id);
        if (ownTopic || client.hasPermission("dashboard.topic.delete")) {
            actions.add("删除帖子");
            callbacks.add(this::confirmDeleteTopic);
        }

        new AlertDialog.Builder(this)
                .setTitle(client.isForumManager() ? "帖子与管理员工具" : "帖子操作")
                .setItems(actions.toArray(new String[0]), (dialog, which) -> callbacks.get(which).run())
                .setNegativeButton("取消", null)
                .show();
    }

    private void changeTopicSticky(ForumApiClient.Topic topic) {
        if (topic == null || topicActionBusy) return;
        final boolean target = !topic.sticky;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicSticky(topicId, target,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void data) {
                        if (isDead()) return;
                        topicActionBusy = false;
                        topic.sticky = target;
                        renderTopic(topic);
                        Toast.makeText(ForumTopicActivity.this,
                                target ? "已置顶" : "已取消置顶", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        finishTopicAction(message);
                    }
                }));
    }

    private void changeTopicRecommended(ForumApiClient.Topic topic) {
        if (topic == null || topicActionBusy) return;
        final boolean target = !topic.recommend;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicRecommended(topicId, target,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void data) {
                        if (isDead()) return;
                        topicActionBusy = false;
                        topic.recommend = target;
                        renderTopic(topic);
                        Toast.makeText(ForumTopicActivity.this,
                                target ? "已设为精华" : "已取消精华", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        finishTopicAction(message);
                    }
                }));
    }

    private void changeLike(ForumApiClient.Topic topic) {
        if (topicActionBusy || topic == null) return;
        runAuthenticatedAction(() -> {
            boolean target = !topic.liked;
            ForumApiClient.getInstance().setTopicLiked(topicId, target,
                    new ForumApiClient.ResultCallback<Void>() {
                        @Override
                        public void onSuccess(@Nullable Void data) {
                            if (isDead()) return;
                            topicActionBusy = false;
                            topic.liked = target;
                            topic.likeCount = Math.max(0, topic.likeCount + (target ? 1 : -1));
                            renderTopic(topic);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            finishTopicAction(message);
                        }
                    });
        });
    }

    private void changeFavorite(ForumApiClient.Topic topic) {
        if (topicActionBusy || topic == null) return;
        runAuthenticatedAction(() -> {
            boolean target = !topic.favorited;
            ForumApiClient.getInstance().setTopicFavorited(topicId, target,
                    new ForumApiClient.ResultCallback<Void>() {
                        @Override
                        public void onSuccess(@Nullable Void data) {
                            if (isDead()) return;
                            topicActionBusy = false;
                            topic.favorited = target;
                            renderTopic(topic);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            finishTopicAction(message);
                        }
                    });
        });
    }

    private void loadAuthorFollowState(ForumApiClient.Topic topic) {
        if (topic == null || topic.user == null || TextUtils.isEmpty(topic.user.id)) return;
        String currentForumUserId = ForumApiClient.getInstance().getCurrentForumUserId();
        if (TextUtils.isEmpty(currentForumUserId)
                || TextUtils.equals(currentForumUserId, topic.user.id)
                || !ForumApiClient.getInstance().hasValidSession()) {
            authorFollowStateLoaded = false;
            return;
        }
        final String authorId = topic.user.id;
        ForumApiClient.getInstance().getUserFollowed(authorId,
                new ForumApiClient.ResultCallback<Boolean>() {
                    @Override
                    public void onSuccess(@Nullable Boolean followed) {
                        if (isDead() || currentTopic == null || currentTopic.user == null
                                || !TextUtils.equals(authorId, currentTopic.user.id)) return;
                        authorFollowed = Boolean.TRUE.equals(followed);
                        authorFollowStateLoaded = true;
                        renderTopic(currentTopic);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (!isDead()) authorFollowStateLoaded = false;
                    }
                });
    }

    private void changeAuthorFollow(ForumApiClient.Topic topic) {
        if (topicActionBusy || topic == null || topic.user == null
                || TextUtils.isEmpty(topic.user.id)) return;
        final String authorId = topic.user.id;
        runAuthenticatedAction(() -> {
            boolean target = !authorFollowed;
            ForumApiClient.getInstance().setUserFollowed(authorId, target,
                    new ForumApiClient.ResultCallback<Void>() {
                        @Override
                        public void onSuccess(@Nullable Void data) {
                            if (isDead() || currentTopic == null || currentTopic.user == null
                                    || !TextUtils.equals(authorId, currentTopic.user.id)) return;
                            topicActionBusy = false;
                            authorFollowed = target;
                            authorFollowStateLoaded = true;
                            renderTopic(currentTopic);
                            Toast.makeText(ForumTopicActivity.this,
                                    target ? "已关注作者" : "已取消关注", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            finishTopicAction(message);
                        }
                    });
        });
    }

    private void showReportDialog() {
        if (topicActionBusy) return;
        String[] reasons = {"垃圾广告", "不友善或骚扰", "色情或违法内容", "虚假信息", "其他"};
        new AlertDialog.Builder(this)
                .setTitle("举报帖子")
                .setItems(reasons, (dialog, which) -> reportTopic(reasons[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void reportTopic(String reason) {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().reportTopic(topicId, reason,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void data) {
                        if (isDead()) return;
                        topicActionBusy = false;
                        Toast.makeText(ForumTopicActivity.this, "举报已提交", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        finishTopicAction(message);
                    }
                }));
    }

    private void confirmDeleteTopic() {
        if (topicActionBusy) return;
        new AlertDialog.Builder(this)
                .setTitle("删除帖子")
                .setMessage("删除后无法恢复，确定删除吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteTopic())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteTopic() {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().deleteTopic(topicId,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void data) {
                        if (isDead()) return;
                        topicActionBusy = false;
                        setResult(RESULT_OK);
                        Toast.makeText(ForumTopicActivity.this, "帖子已删除", Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        finishTopicAction(message);
                    }
                }));
    }

    private void runAuthenticatedAction(Runnable action) {
        if (topicActionBusy) return;
        topicActionBusy = true;
        ForumApiClient.getInstance().ensureSession(this, new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (isDead()) return;
                action.run();
            }

            @Override
            public void onError(@NonNull String message) {
                finishTopicAction(message);
            }
        });
    }

    private void finishTopicAction(String message) {
        if (isDead()) return;
        topicActionBusy = false;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void loadComments(boolean reset) {
        if (loadingComments) {
            if (reset) refreshCommentsPending = true;
            return;
        }
        if (reset) {
            commentsCursor = "";
            commentsHasMore = false;
            comments.clear();
            renderComments();
        }
        loadingComments = true;
        loadMoreCommentsView.setText("正在加载…");
        ForumApiClient.getInstance().getComments(topicId, commentsCursor,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        if (isDead()) return;
                        loadingComments = false;
                        if (page != null && page.results != null) comments.addAll(page.results);
                        if (page != null && !TextUtils.isEmpty(page.cursor)) commentsCursor = page.cursor;
                        commentsHasMore = page != null && page.hasMore;
                        renderComments();
                        if (refreshCommentsPending) {
                            refreshCommentsPending = false;
                            loadComments(true);
                        }
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (isDead()) return;
                        loadingComments = false;
                        if (refreshCommentsPending) {
                            refreshCommentsPending = false;
                            loadComments(true);
                            return;
                        }
                        loadMoreCommentsView.setText(comments.isEmpty() ? message : "加载失败，点击重试");
                        loadMoreCommentsView.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void renderComments() {
        commentsContainer.removeAllViews();
        if (commentHeading != null) {
            long count = currentTopic == null ? comments.size() : currentTopic.commentCount;
            commentHeading.setText(count > 0 ? "评论  " + count : "评论");
        }
        if (comments.isEmpty() && !loadingComments) {
            LinearLayout emptyBox = new LinearLayout(this);
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setGravity(Gravity.CENTER);
            emptyBox.setPadding(dp(16), dp(22), dp(16), dp(24));
            emptyBox.setBackground(roundRect(isDark() ? 0xFF17181B : Color.WHITE, 14));
            TextView title = text("还没有评论", 15,
                    isDark() ? 0xFFE1E3E6 : 0xFF30353B, true);
            TextView subtitle = text("说点有帮助的内容，成为第一个回复的人", 12,
                    isDark() ? 0xFF8F949C : 0xFF7B8189, false);
            subtitle.setPadding(0, dp(5), 0, 0);
            emptyBox.addView(title);
            emptyBox.addView(subtitle);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            emptyParams.setMargins(dp(10), 0, dp(10), dp(10));
            commentsContainer.addView(emptyBox, emptyParams);
        } else {
            for (ForumApiClient.Comment comment : comments) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(dp(10), 0, dp(10), dp(8));
                commentsContainer.addView(createCommentView(comment, comment.id, false), params);
            }
        }
        loadMoreCommentsView.setText("加载更多评论");
        loadMoreCommentsView.setVisibility(commentsHasMore ? View.VISIBLE : View.GONE);
    }

    private View createCommentView(ForumApiClient.Comment comment, long parentId, boolean reply) {
        boolean dark = isDark();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(reply ? 10 : 14), dp(reply ? 9 : 13),
                dp(reply ? 10 : 14), dp(reply ? 9 : 12));
        card.setBackground(roundRect(reply
                ? (dark ? 0xFF24262B : 0xFFF3F5F7)
                : (dark ? 0xFF17181B : Color.WHITE), reply ? 10 : 14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int avatarDp = reply ? 28 : 34;
        int avatarSize = dp(avatarDp + 4);
        AvatarView avatar = new AvatarView(this);
        avatar.setSize(avatarDp);
        String author = comment.user == null || TextUtils.isEmpty(comment.user.nickname)
                ? "用户" : comment.user.nickname;
        String avatarUrl = comment.user == null ? "" : ForumApiClient.getInstance().resolveUrl(
                TextUtils.isEmpty(comment.user.smallAvatar)
                        ? comment.user.avatar : comment.user.smallAvatar);
        String avatarSeed = comment.user == null ? author : safe(comment.user.id);
        avatar.showAvatarUrl(avatarUrl, avatarSeed, author, avatarSeed);
        avatar.showFlag(comment.user == null ? "" : (TextUtils.isEmpty(comment.user.countryCode)
                ? comment.user.country : comment.user.countryCode));
        header.addView(avatar, new LinearLayout.LayoutParams(avatarSize, avatarSize));

        LinearLayout authorBox = new LinearLayout(this);
        authorBox.setOrientation(LinearLayout.VERTICAL);
        TextView authorView = text(author, reply ? 12 : 13,
                dark ? 0xFFE4E6E9 : 0xFF30353B, true);
        TextView timeView = text(formatDate(comment.createTime), 11,
                dark ? 0xFF858A92 : 0xFF8A9098, false);
        authorBox.addView(authorView);
        authorBox.addView(timeView);
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        authorParams.leftMargin = dp(9);
        header.addView(authorBox, authorParams);

        if (canDeleteComment(comment)) {
            TextView more = text("•••", 15, dark ? 0xFFB8BBC2 : 0xFF606770, true);
            more.setGravity(Gravity.CENTER);
            more.setBackground(selectableBackground());
            more.setOnClickListener(v -> showCommentMenu(comment));
            header.addView(more, new LinearLayout.LayoutParams(dp(40), dp(36)));
        }
        card.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = htmlText(comment.content, reply ? 14 : 15);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(9);
        bodyParams.leftMargin = reply ? 0 : avatarSize + dp(9);
        card.addView(body, bodyParams);
        addRemoteImages(card, comment.imageList, dp(8));

        TextView action = text("回复" + (comment.commentCount > 0 && !reply
                        ? "   " + comment.commentCount + " 条回复" : ""),
                12, 0xFF1877F2, true);
        action.setPadding(reply ? 0 : avatarSize + dp(9), dp(8), dp(8), dp(2));
        action.setOnClickListener(v -> setReplyTarget(parentId, reply ? comment.id : 0, author));
        card.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!reply && comment.replies != null && comment.replies.results != null
                && !comment.replies.results.isEmpty()) {
            LinearLayout replyBox = new LinearLayout(this);
            replyBox.setOrientation(LinearLayout.VERTICAL);
            replyBox.setPadding(dp(8), dp(4), dp(8), dp(5));
            replyBox.setBackground(roundRect(dark ? 0xFF202227 : 0xFFF5F6F8, 12));
            for (ForumApiClient.Comment item : comment.replies.results) {
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                itemParams.topMargin = dp(4);
                replyBox.addView(createCommentView(item, comment.id, true), itemParams);
            }
            if (comment.replies.hasMore) {
                TextView more = text("展开更多回复", 12, 0xFF1877F2, true);
                more.setPadding(dp(10), dp(9), dp(10), dp(9));
                more.setOnClickListener(v -> loadMoreReplies(comment));
                replyBox.addView(more, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams replyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            replyParams.topMargin = dp(7);
            replyParams.leftMargin = avatarSize + dp(9);
            card.addView(replyBox, replyParams);
        } else if (!reply && comment.commentCount > 0) {
            TextView more = text("查看 " + comment.commentCount + " 条回复", 12, 0xFF1877F2, true);
            more.setPadding(avatarSize + dp(9), dp(9), dp(12), dp(8));
            more.setOnClickListener(v -> loadMoreReplies(comment));
            card.addView(more, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return card;
    }

    private boolean canDeleteComment(ForumApiClient.Comment comment) {
        if (comment == null) return false;
        ForumApiClient client = ForumApiClient.getInstance();
        String currentUserId = client.getCurrentForumUserId();
        boolean ownComment = comment.user != null && !TextUtils.isEmpty(currentUserId)
                && TextUtils.equals(currentUserId, comment.user.id);
        return ownComment || client.hasPermission("dashboard.comment.delete");
    }

    private void showCommentMenu(ForumApiClient.Comment comment) {
        if (!canDeleteComment(comment) || topicActionBusy) return;
        new AlertDialog.Builder(this)
                .setTitle(ForumApiClient.getInstance().hasPermission("dashboard.comment.delete")
                        ? "评论管理" : "评论操作")
                .setItems(new String[]{"删除评论"}, (dialog, which) -> confirmDeleteComment(comment))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteComment(ForumApiClient.Comment comment) {
        if (comment == null || comment.id <= 0 || topicActionBusy) return;
        new AlertDialog.Builder(this)
                .setTitle("删除评论")
                .setMessage("确定删除这条评论吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteComment(comment.id))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteComment(long commentId) {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().deleteComment(commentId,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void data) {
                        if (isDead()) return;
                        topicActionBusy = false;
                        Toast.makeText(ForumTopicActivity.this, "评论已删除", Toast.LENGTH_SHORT).show();
                        loadTopic();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        finishTopicAction(message);
                    }
                }));
    }

    private void loadMoreReplies(ForumApiClient.Comment parent) {
        if (parent == null || parent.id <= 0 || loadingReplyIds.contains(parent.id)) return;
        loadingReplyIds.add(parent.id);
        String cursor = parent.replies == null ? "" : parent.replies.cursor;
        ForumApiClient.getInstance().getReplies(parent.id, cursor,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        loadingReplyIds.remove(parent.id);
                        if (isDead()) return;
                        if (parent.replies == null) parent.replies = new ForumApiClient.Page<>();
                        if (parent.replies.results == null) parent.replies.results = new ArrayList<>();
                        if (page != null && page.results != null) parent.replies.results.addAll(page.results);
                        parent.replies.cursor = page == null ? parent.replies.cursor : page.cursor;
                        parent.replies.hasMore = page != null && page.hasMore;
                        renderComments();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        loadingReplyIds.remove(parent.id);
                        if (!isDead()) Toast.makeText(ForumTopicActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setReplyTarget(long parentId, long quoteId, String name) {
        replyParentId = parentId;
        replyQuoteId = quoteId;
        replyHint.setText("回复 " + safe(name) + "（点击取消）");
        replyHint.setVisibility(View.VISIBLE);
        commentInput.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(commentInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void clearReplyTarget() {
        replyParentId = 0;
        replyQuoteId = 0;
        replyHint.setVisibility(View.GONE);
    }

    private void sendComment() {
        if (sending) return;
        String content = commentInput.getText().toString().trim();
        if (content.isEmpty()) {
            commentInput.setError("请输入评论内容");
            return;
        }
        setSending(true, selectedCommentImages.isEmpty() ? "发送中" : "处理图片");
        ForumApiClient.getInstance().ensureSession(this, new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                uploadCommentImages(0, new ArrayList<>(), uploaded -> submitComment(content, uploaded));
            }

            @Override
            public void onError(@NonNull String message) {
                failSend(message);
            }
        });
    }

    private void uploadCommentImages(int index, List<ForumApiClient.ImageInfo> uploaded,
                                     UploadsCallback callback) {
        if (index >= selectedCommentImages.size()) {
            callback.onDone(uploaded);
            return;
        }
        sendButton.setText((index + 1) + "/" + selectedCommentImages.size());
        Uri uri = selectedCommentImages.get(index);
        imageExecutor.execute(() -> {
            File file;
            try {
                file = ForumImageCompressor.compress(getApplicationContext(), uri);
            } catch (Throwable error) {
                runOnUiThread(() -> failSend("图片处理失败：" + safeMessage(error)));
                return;
            }
            runOnUiThread(() -> ForumApiClient.getInstance().uploadImage(file,
                    new ForumApiClient.ResultCallback<ForumApiClient.UploadResult>() {
                        @Override
                        public void onSuccess(@Nullable ForumApiClient.UploadResult result) {
                            file.delete();
                            if (result == null || TextUtils.isEmpty(result.url)) {
                                failSend("图片上传返回数据不完整");
                                return;
                            }
                            uploaded.add(new ForumApiClient.ImageInfo(result.url));
                            uploadCommentImages(index + 1, uploaded, callback);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            file.delete();
                            failSend(message);
                        }
                    }));
        });
    }

    private void submitComment(String content, List<ForumApiClient.ImageInfo> images) {
        String entityType = replyParentId > 0 ? "comment" : "topic";
        String entityId = replyParentId > 0 ? String.valueOf(replyParentId) : topicId;
        ForumApiClient.getInstance().createComment(entityType, entityId, content, replyQuoteId, images,
                new ForumApiClient.ResultCallback<ForumApiClient.Comment>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Comment data) {
                        if (isDead()) return;
                        commentInput.setText("");
                        selectedCommentImages.clear();
                        updateImageCount();
                        clearReplyTarget();
                        setSending(false, "发送");
                        loadComments(true);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        failSend(message);
                    }
                });
    }

    private void failSend(String message) {
        if (isDead()) return;
        setSending(false, "发送");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setSending(boolean value, String label) {
        sending = value;
        sendButton.setText(label);
        sendButton.setAlpha(value ? 0.55f : 1f);
        commentInput.setEnabled(!value);
    }

    private void updateImageCount() {
        if (selectedCommentImages.isEmpty()) {
            imageCountView.setVisibility(View.GONE);
        } else {
            imageCountView.setText("已选择 " + selectedCommentImages.size() + " 张图片；点击这里可清空");
            imageCountView.setVisibility(View.VISIBLE);
        }
    }

    private TextView htmlText(String html, float sizeSp) {
        TextView view = text("", sizeSp, isDark() ? 0xFFE8E9EB : 0xFF272A2F, false);
        view.setLineSpacing(dp(3), 1.08f);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setText(Html.fromHtml(html == null ? "" : html, Html.FROM_HTML_MODE_LEGACY));
        return view;
    }

    private void addRemoteImages(LinearLayout container, List<ForumApiClient.ImageInfo> images, int topMargin) {
        if (images == null || images.isEmpty()) return;
        for (ForumApiClient.ImageInfo info : images) {
            if (info == null || TextUtils.isEmpty(info.url)) continue;
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(isDark() ? 0xFF24262B : 0xFFF0F1F3);
            String remote = TextUtils.isEmpty(info.preview) ? info.url : info.preview;
            Glide.with(this).load(ForumApiClient.getInstance().resolveUrl(remote)).into(image);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
            params.topMargin = topMargin;
            container.addView(image, params);
        }
    }

    private void showError(String message) {
        stateView.setText(message);
        stateView.setVisibility(View.VISIBLE);
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private android.graphics.drawable.Drawable selectableBackground() {
        TypedValue out = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        return getDrawable(out.resourceId);
    }

    private boolean isDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    private boolean isDead() {
        return isFinishing() || isDestroyed();
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "" : value;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return TextUtils.isEmpty(message) ? "未知错误" : message;
    }

    private static String formatDate(long value) {
        if (value <= 0) return "刚刚";
        long millis = value < 10_000_000_000L ? value * 1000L : value;
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private interface UploadsCallback {
        void onDone(List<ForumApiClient.ImageInfo> images);
    }
}
