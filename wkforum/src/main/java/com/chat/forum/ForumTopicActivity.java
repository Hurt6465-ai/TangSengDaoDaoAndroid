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
        toolbar.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        TextView back = text("‹", 35, dark ? Color.WHITE : 0xFF1C1E21, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView title = text("帖子", 18, dark ? Color.WHITE : 0xFF1C1E21, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));
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

        TextView commentHeading = text("评论", 18, dark ? Color.WHITE : 0xFF202328, true);
        commentHeading.setPadding(dp(18), dp(18), dp(18), dp(12));
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
        wrapper.setPadding(dp(10), dp(5), dp(10), dp(8));
        wrapper.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        replyHint = text("", 12, dark ? 0xFFFFC46B : 0xFF986400, false);
        replyHint.setPadding(dp(8), dp(3), dp(8), dp(3));
        replyHint.setVisibility(View.GONE);
        replyHint.setOnClickListener(v -> clearReplyTarget());
        wrapper.addView(replyHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        TextView image = text("图片", 13, 0xFF1877F2, true);
        image.setGravity(Gravity.CENTER);
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
        row.addView(image, new LinearLayout.LayoutParams(dp(50), dp(46)));

        commentInput = new EditText(this);
        commentInput.setHint("写评论…");
        commentInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        commentInput.setTextColor(dark ? Color.WHITE : 0xFF202328);
        commentInput.setHintTextColor(dark ? 0xFF777B82 : 0xFF9A9FA6);
        commentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        commentInput.setMaxLines(5);
        commentInput.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setCornerRadius(dp(20));
        inputBackground.setColor(dark ? 0xFF24262B : 0xFFF1F3F5);
        commentInput.setBackground(inputBackground);
        row.addView(commentInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = text("发送", 14, 0xFF1877F2, true);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setOnClickListener(v -> sendComment());
        row.addView(sendButton, new LinearLayout.LayoutParams(dp(58), dp(46)));
        wrapper.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        imageCountView = text("", 11, dark ? 0xFF9EA2A9 : 0xFF737880, false);
        imageCountView.setPadding(dp(58), 0, dp(8), 0);
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

        TextView title = text(safe(topic.title), 23, dark ? Color.WHITE : 0xFF17191C, true);
        title.setLineSpacing(0, 1.12f);
        articleContainer.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String author = topic.user == null ? "" : safe(topic.user.nickname);
        String category = topic.category == null ? "" : safe(topic.category.name);
        String metaText = author;
        if (!TextUtils.isEmpty(category)) metaText += (TextUtils.isEmpty(metaText) ? "" : " · ") + category;
        metaText += (TextUtils.isEmpty(metaText) ? "" : " · ") + formatDate(topic.createTime);
        TextView meta = text(metaText, 13, dark ? 0xFF9EA2A9 : 0xFF737880, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(10);
        articleContainer.addView(meta, metaParams);

        TextView content = htmlText(TextUtils.isEmpty(topic.content) ? safe(topic.summary) : topic.content, 16);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = dp(24);
        articleContainer.addView(content, contentParams);
        addRemoteImages(articleContainer, topic.imageList, dp(12));

        TextView stats = text("浏览 " + topic.viewCount + "   评论 " + topic.commentCount + "   赞 " + topic.likeCount,
                13, dark ? 0xFF90949B : 0xFF777C84, false);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = dp(24);
        articleContainer.addView(stats, statsParams);
        articleContainer.addView(buildTopicActions(topic),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stateView.setVisibility(View.GONE);
    }

    private View buildTopicActions(ForumApiClient.Topic topic) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, 0);

        TextView like = actionButton(topic.liked ? "已赞 " + topic.likeCount : "赞 " + topic.likeCount);
        like.setOnClickListener(v -> changeLike(topic));
        addActionButton(row, like);

        TextView favorite = actionButton(topic.favorited ? "已收藏" : "收藏");
        favorite.setOnClickListener(v -> changeFavorite(topic));
        addActionButton(row, favorite);

        String currentForumUserId = ForumApiClient.getInstance().getCurrentForumUserId();
        if (topic.user != null && !TextUtils.isEmpty(topic.user.id)
                && !TextUtils.equals(currentForumUserId, topic.user.id)) {
            String followLabel = authorFollowStateLoaded
                    ? (authorFollowed ? "已关注" : "关注作者") : "关注作者";
            TextView follow = actionButton(followLabel);
            follow.setOnClickListener(v -> changeAuthorFollow(topic));
            addActionButton(row, follow);
        }

        TextView report = actionButton("举报");
        report.setOnClickListener(v -> showReportDialog());
        addActionButton(row, report);

        if (topic.user != null && !TextUtils.isEmpty(currentForumUserId)
                && TextUtils.equals(currentForumUserId, topic.user.id)) {
            TextView delete = actionButton("删除");
            delete.setTextColor(0xFFE5484D);
            delete.setOnClickListener(v -> confirmDeleteTopic());
            addActionButton(row, delete);
        }
        return row;
    }

    private TextView actionButton(String label) {
        TextView button = text(label, 13, isDark() ? 0xFFD5D7DB : 0xFF50555D, true);
        button.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(18));
        background.setColor(isDark() ? 0xFF24262B : 0xFFF1F3F5);
        button.setBackground(background);
        return button;
    }

    private void addActionButton(LinearLayout row, TextView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, params);
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
        if (comments.isEmpty() && !loadingComments) {
            TextView empty = text("还没有评论，来发表第一条吧", 14,
                    isDark() ? 0xFF92969D : 0xFF7B8088, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(24), dp(16), dp(28));
            commentsContainer.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (ForumApiClient.Comment comment : comments) {
                commentsContainer.addView(createCommentView(comment, comment.id, false),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        loadMoreCommentsView.setText("加载更多评论");
        loadMoreCommentsView.setVisibility(commentsHasMore ? View.VISIBLE : View.GONE);
    }

    private View createCommentView(ForumApiClient.Comment comment, long parentId, boolean reply) {
        boolean dark = isDark();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int left = reply ? 12 : 18;
        card.setPadding(dp(left), dp(reply ? 8 : 14), dp(16), dp(reply ? 8 : 14));
        card.setBackgroundColor(reply ? (dark ? 0xFF222429 : 0xFFF4F5F7)
                : (dark ? 0xFF17181B : Color.WHITE));

        String author = comment.user == null ? "用户" : safe(comment.user.nickname);
        TextView meta = text(author + " · " + formatDate(comment.createTime),
                reply ? 12 : 13, dark ? 0xFFB9BDC4 : 0xFF656A72, true);
        card.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = htmlText(comment.content, reply ? 14 : 15);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(6);
        card.addView(body, bodyParams);
        addRemoteImages(card, comment.imageList, dp(8));

        TextView action = text("回复" + (comment.commentCount > 0 && !reply ? "  " + comment.commentCount + "条回复" : ""),
                12, 0xFF1877F2, true);
        action.setPadding(0, dp(7), dp(8), dp(4));
        action.setOnClickListener(v -> setReplyTarget(parentId, reply ? comment.id : 0, author));
        card.addView(action, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!reply && comment.replies != null && comment.replies.results != null
                && !comment.replies.results.isEmpty()) {
            LinearLayout replyBox = new LinearLayout(this);
            replyBox.setOrientation(LinearLayout.VERTICAL);
            for (ForumApiClient.Comment item : comment.replies.results) {
                replyBox.addView(createCommentView(item, comment.id, true),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            if (comment.replies.hasMore) {
                TextView more = text("展开更多回复", 12, 0xFF1877F2, true);
                more.setPadding(dp(12), dp(9), dp(12), dp(9));
                more.setOnClickListener(v -> loadMoreReplies(comment));
                replyBox.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams replyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            replyParams.topMargin = dp(6);
            card.addView(replyBox, replyParams);
        } else if (!reply && comment.commentCount > 0) {
            TextView more = text("查看 " + comment.commentCount + " 条回复", 12, 0xFF1877F2, true);
            more.setPadding(dp(12), dp(9), dp(12), dp(9));
            more.setOnClickListener(v -> loadMoreReplies(comment));
            card.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (!reply) {
            View divider = new View(this);
            divider.setBackgroundColor(dark ? 0xFF2B2D31 : 0xFFE8EAED);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            dividerParams.topMargin = dp(7);
            card.addView(divider, dividerParams);
        }
        return card;
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
