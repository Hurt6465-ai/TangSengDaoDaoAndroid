package com.chat.forum;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Compact native topic detail with flat comments and two-level replies. */
public class ForumTopicActivity extends AppCompatActivity {
    private static final String EXTRA_TOPIC_ID = "forum_topic_id";

    private final List<ForumApiClient.Comment> comments = new ArrayList<>();
    private final Set<Long> loadingReplyIds = new HashSet<>();
    private String topicId;
    private ForumApiClient.Topic currentTopic;
    private ScrollView scrollView;
    private LinearLayout articleContainer;
    private LinearLayout commentsContainer;
    private TextView commentHeading;
    private TextView stateView;
    private TextView loadMoreCommentsView;
    private EditText commentInput;
    private TextView replyHint;
    private TextView composerAction;
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

    private final ActivityResultLauncher<Intent> speechLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> results = result.getData().getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (results == null || results.isEmpty() || TextUtils.isEmpty(results.get(0))) return;
                String existing = commentInput == null ? "" : commentInput.getText().toString().trim();
                String recognized = results.get(0).trim();
                commentInput.setText(existing.isEmpty() ? recognized : existing + " " + recognized);
                commentInput.setSelection(commentInput.length());
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

    private void buildView() {
        boolean dark = isDark();
        getWindow().setStatusBarColor(dark ? 0xFF17181B : Color.WHITE);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF111214 : Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();

        FrameLayout body = new FrameLayout(this);
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(dark ? 0xFF111214 : Color.WHITE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(dark ? 0xFF111214 : Color.WHITE);

        articleContainer = new LinearLayout(this);
        articleContainer.setOrientation(LinearLayout.VERTICAL);
        articleContainer.setPadding(dp(18), dp(12), dp(18), dp(18));
        articleContainer.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        page.addView(articleContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        commentHeading = text("评论", 18, dark ? Color.WHITE : 0xFF202328, true);
        commentHeading.setPadding(dp(18), dp(15), dp(18), dp(9));
        commentHeading.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        page.addView(commentHeading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        commentsContainer = new LinearLayout(this);
        commentsContainer.setOrientation(LinearLayout.VERTICAL);
        commentsContainer.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        page.addView(commentsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loadMoreCommentsView = text("加载更多评论", 14, 0xFF1877F2, true);
        loadMoreCommentsView.setGravity(Gravity.CENTER);
        loadMoreCommentsView.setPadding(dp(16), dp(14), dp(16), dp(20));
        loadMoreCommentsView.setOnClickListener(v -> loadComments(false));
        loadMoreCommentsView.setVisibility(View.GONE);
        page.addView(loadMoreCommentsView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stateView = text("正在加载…", 14, dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
        stateView.setGravity(Gravity.CENTER);
        body.addView(stateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildComposer(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private View buildComposer() {
        boolean dark = isDark();
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(10), dp(5), dp(10), dp(7));
        wrapper.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        View topLine = divider();
        wrapper.addView(topLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f)));

        replyHint = text("", 12, dark ? 0xFFFFC46B : 0xFF8B6500, false);
        replyHint.setPadding(dp(10), dp(5), dp(10), dp(3));
        replyHint.setVisibility(View.GONE);
        replyHint.setOnClickListener(v -> clearReplyTarget());
        wrapper.addView(replyHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, 0);

        commentInput = new EditText(this);
        commentInput.setHint("友善交流，说点什么…");
        commentInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        commentInput.setTextColor(dark ? Color.WHITE : 0xFF202328);
        commentInput.setHintTextColor(dark ? 0xFF777B82 : 0xFF9A9FA6);
        commentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        commentInput.setMaxLines(4);
        commentInput.setPadding(dp(14), dp(8), dp(14), dp(8));
        commentInput.setBackground(roundRect(dark ? 0xFF24262B : 0xFFF1F3F5, 19));
        row.addView(commentInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        composerAction = text("🎙", 20, dark ? 0xFFE2E4E8 : 0xFF4D555E, false);
        composerAction.setGravity(Gravity.CENTER);
        composerAction.setContentDescription("语音输入");
        composerAction.setBackground(roundRect(dark ? 0xFF25272C : 0xFFF1F3F5, 19));
        composerAction.setOnClickListener(v -> {
            if (sending) return;
            if (TextUtils.isEmpty(commentInput.getText().toString().trim())) startSpeechInput();
            else sendComment();
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(58), dp(38));
        actionParams.leftMargin = dp(8);
        row.addView(composerAction, actionParams);
        wrapper.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        commentInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateComposerAction();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        updateComposerAction();
        return wrapper;
    }

    private void startSpeechInput() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出评论内容");
            speechLauncher.launch(intent);
        } catch (Throwable error) {
            Toast.makeText(this, "当前设备没有可用的语音识别服务", Toast.LENGTH_LONG).show();
        }
    }

    private void updateComposerAction() {
        if (composerAction == null || commentInput == null) return;
        boolean hasText = !TextUtils.isEmpty(commentInput.getText().toString().trim());
        composerAction.setText(hasText ? "发送" : "🎙");
        composerAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, hasText ? 14 : 20);
        composerAction.setTypeface(Typeface.DEFAULT, hasText ? Typeface.BOLD : Typeface.NORMAL);
        composerAction.setTextColor(hasText ? Color.WHITE : (isDark() ? 0xFFE2E4E8 : 0xFF4D555E));
        composerAction.setBackground(roundRect(hasText ? 0xFF1877F2
                : (isDark() ? 0xFF25272C : 0xFFF1F3F5), 19));
        composerAction.setContentDescription(hasText ? "发送评论" : "语音输入");
    }

    private void loadTopic() {
        ForumApiClient.getInstance().ensureSession(this, new ForumApiClient.ResultCallback<String>() {
            @Override public void onSuccess(@Nullable String data) { requestTopic(); }
            @Override public void onError(@NonNull String message) { requestTopic(); }
        });
    }

    private void requestTopic() {
        ForumApiClient.getInstance().getTopic(topicId,
                new ForumApiClient.ResultCallback<ForumApiClient.Topic>() {
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

                    @Override public void onError(@NonNull String message) {
                        if (!isDead()) showError(message);
                    }
                });
    }

    private void renderTopic(ForumApiClient.Topic topic) {
        currentTopic = topic;
        boolean dark = isDark();
        articleContainer.removeAllViews();

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
        titleParams.topMargin = topic.category == null || TextUtils.isEmpty(topic.category.name)
                ? 0 : dp(11);
        articleContainer.addView(title, titleParams);
        articleContainer.addView(buildAuthorRow(topic), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f));
        dividerParams.topMargin = dp(14);
        dividerParams.bottomMargin = dp(16);
        articleContainer.addView(divider(), dividerParams);

        TextView content = htmlText(TextUtils.isEmpty(topic.content) ? safe(topic.summary) : topic.content, 16);
        articleContainer.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addRemoteImages(articleContainer, topic.imageList, dp(12));

        articleContainer.addView(buildTopicActions(topic), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        commentHeading.setText(topic.commentCount > 0 ? "评论 " + topic.commentCount : "评论");
        stateView.setVisibility(View.GONE);
    }

    private View buildAuthorRow(ForumApiClient.Topic topic) {
        boolean dark = isDark();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, 0);

        AvatarView avatar = new AvatarView(this);
        avatar.setSize(39);
        String author = userName(topic.user);
        bindAvatar(avatar, topic.user, author);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(43), dp(43)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView authorView = text(author, 14, dark ? Color.WHITE : 0xFF272B31, true);
        nameRow.addView(authorView);

        String currentForumUserId = ForumApiClient.getInstance().getCurrentForumUserId();
        if (topic.user != null && !TextUtils.isEmpty(topic.user.id)
                && !TextUtils.equals(currentForumUserId, topic.user.id)) {
            String followLabel = authorFollowStateLoaded && authorFollowed ? " · 已关注" : " · 关注";
            TextView follow = text(followLabel, 13, 0xFFE53935, true);
            follow.setPadding(dp(2), 0, dp(8), 0);
            follow.setOnClickListener(v -> changeAuthorFollow(topic));
            nameRow.addView(follow);
        }
        copy.addView(nameRow);
        TextView meta = text(formatDate(topic.createTime), 12,
                dark ? 0xFF8F949C : 0xFF7A8088, false);
        copy.addView(meta);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);

        TextView more = text("⋮", 26, dark ? 0xFFD8DADE : 0xFF4D535B, false);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("帖子操作");
        more.setBackground(selectableBackground());
        more.setOnClickListener(v -> showTopicMenu());
        row.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return row;
    }

    private View buildTopicActions(ForumApiClient.Topic topic) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setPadding(0, dp(17), 0, 0);
        addTopicAction(row, topic.liked ? "♥ " + topic.likeCount : "♡ " + topic.likeCount,
                topic.liked, v -> changeLike(topic));
        addTopicAction(row, topic.favorited ? "★ 已收藏" : "☆ 收藏",
                topic.favorited, v -> changeFavorite(topic));
        addTopicAction(row, "↩ " + topic.commentCount, false, v -> focusCommentInput());
        return row;
    }

    private void addTopicAction(LinearLayout row, String label, boolean active,
                                View.OnClickListener listener) {
        TextView button = text(label, 13,
                active ? 0xFFE53935 : (isDark() ? 0xFFD1D4D9 : 0xFF535A63), active);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(11), 0, dp(11), 0);
        button.setBackground(selectableBackground());
        button.setOnClickListener(listener);
        row.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
    }

    private void focusCommentInput() {
        commentInput.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(commentInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void showTopicMenu() {
        ForumApiClient.Topic topic = currentTopic;
        if (topic == null || topicActionBusy) return;
        ForumApiClient client = ForumApiClient.getInstance();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        labels.add("分享");
        actions.add(() -> shareText(topic.title, topic.title + "\n" + safe(topic.summary)));
        labels.add("举报");
        actions.add(this::showReportDialog);

        if (client.hasPermission("dashboard.topic.recommend")) {
            labels.add(topic.recommend ? "取消精华" : "推荐到精华");
            actions.add(() -> changeTopicRecommended(topic));
        }
        if (client.hasPermission("dashboard.topic.sticky")) {
            labels.add(topic.sticky ? "取消置顶" : "置顶");
            actions.add(() -> changeTopicSticky(topic));
        }
        if (canDeleteTopic(topic)) {
            labels.add("删除帖子");
            actions.add(this::confirmDeleteTopic);
        }
        if (topic.user != null && !TextUtils.isEmpty(topic.user.id)
                && !TextUtils.equals(client.getCurrentForumUserId(), topic.user.id)) {
            if (client.hasPermission("dashboard.user.forbidden")) {
                labels.add("禁言 7 天");
                actions.add(() -> confirmForbidUser(topic.user, 7));
            }
            if (client.hasPermission("dashboard.user.forbiddenForever")) {
                labels.add("永久禁言");
                actions.add(() -> confirmForbidUser(topic.user, -1));
            }
        }
        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean canDeleteTopic(ForumApiClient.Topic topic) {
        if (topic == null) return false;
        ForumApiClient client = ForumApiClient.getInstance();
        return client.hasPermission("dashboard.topic.delete")
                || (topic.user != null && TextUtils.equals(
                client.getCurrentForumUserId(), topic.user.id));
    }

    private void changeTopicSticky(ForumApiClient.Topic topic) {
        boolean next = !topic.sticky;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicSticky(topic.id, next,
                new VoidCallback() {
                    @Override public void success() {
                        topic.sticky = next;
                        completeAction(next ? "已置顶" : "已取消置顶");
                        renderTopic(topic);
                    }
                }));
    }

    private void changeTopicRecommended(ForumApiClient.Topic topic) {
        boolean next = !topic.recommend;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicRecommended(topic.id, next,
                new VoidCallback() {
                    @Override public void success() {
                        topic.recommend = next;
                        completeAction(next ? "已推荐到精华" : "已取消精华");
                        renderTopic(topic);
                    }
                }));
    }

    private void changeLike(ForumApiClient.Topic topic) {
        boolean next = !topic.liked;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicLiked(topic.id, next,
                new VoidCallback() {
                    @Override public void success() {
                        topic.liked = next;
                        topic.likeCount = Math.max(0, topic.likeCount + (next ? 1 : -1));
                        completeAction("");
                        renderTopic(topic);
                    }
                }));
    }

    private void changeFavorite(ForumApiClient.Topic topic) {
        boolean next = !topic.favorited;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicFavorited(topic.id, next,
                new VoidCallback() {
                    @Override public void success() {
                        topic.favorited = next;
                        completeAction(next ? "已收藏" : "已取消收藏");
                        renderTopic(topic);
                    }
                }));
    }

    private void loadAuthorFollowState(ForumApiClient.Topic topic) {
        if (topic == null || topic.user == null || TextUtils.isEmpty(topic.user.id)
                || TextUtils.equals(ForumApiClient.getInstance().getCurrentForumUserId(), topic.user.id)) {
            authorFollowStateLoaded = true;
            authorFollowed = false;
            return;
        }
        ForumApiClient.getInstance().getUserFollowed(topic.user.id,
                new ForumApiClient.ResultCallback<Boolean>() {
                    @Override public void onSuccess(@Nullable Boolean followed) {
                        if (isDead() || currentTopic != topic) return;
                        authorFollowStateLoaded = true;
                        authorFollowed = Boolean.TRUE.equals(followed);
                        renderTopic(topic);
                    }
                    @Override public void onError(@NonNull String message) {
                        authorFollowStateLoaded = true;
                    }
                });
    }

    private void changeAuthorFollow(ForumApiClient.Topic topic) {
        if (topic == null || topic.user == null || TextUtils.isEmpty(topic.user.id)) return;
        boolean next = !authorFollowed;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setUserFollowed(
                topic.user.id, next, new VoidCallback() {
                    @Override public void success() {
                        authorFollowStateLoaded = true;
                        authorFollowed = next;
                        completeAction(next ? "已关注" : "已取消关注");
                        renderTopic(topic);
                    }
                }));
    }

    private void showReportDialog() {
        String[] reasons = {"广告或诈骗", "色情低俗", "辱骂骚扰", "违法违规", "其他"};
        new AlertDialog.Builder(this)
                .setTitle("举报帖子")
                .setItems(reasons, (dialog, which) -> reportTopic(reasons[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void reportTopic(String reason) {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().reportTopic(topicId, reason,
                new VoidCallback() {
                    @Override public void success() { completeAction("举报已提交"); }
                }));
    }

    private void confirmDeleteTopic() {
        if (currentTopic == null) return;
        new AlertDialog.Builder(this)
                .setTitle("删除帖子")
                .setMessage("删除后无法恢复，确定继续吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteTopic())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteTopic() {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().deleteTopic(topicId,
                new ForumApiClient.ResultCallback<Void>() {
                    @Override public void onSuccess(@Nullable Void data) {
                        topicActionBusy = false;
                        setResult(RESULT_OK);
                        Toast.makeText(ForumTopicActivity.this, "帖子已删除", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override public void onError(@NonNull String message) { finishTopicAction(message); }
                }));
    }

    private void confirmForbidUser(ForumApiClient.User user, int days) {
        if (user == null || TextUtils.isEmpty(user.id)) return;
        String duration = days == -1 ? "永久" : days + " 天";
        new AlertDialog.Builder(this)
                .setTitle(duration + "禁言")
                .setMessage("确定将“" + userName(user) + "”禁言" + duration + "吗？")
                .setPositiveButton("确定", (dialog, which) -> forbidUser(user, days))
                .setNegativeButton("取消", null)
                .show();
    }

    private void forbidUser(ForumApiClient.User user, int days) {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().forbidUser(user.id, days,
                "论坛帖子管理", new VoidCallback() {
                    @Override public void success() {
                        completeAction(days == -1 ? "已永久禁言" : "已禁言 7 天");
                    }
                }));
    }

    private void runAuthenticatedAction(Runnable action) {
        if (topicActionBusy) return;
        topicActionBusy = true;
        ForumApiClient.getInstance().ensureSession(this,
                new ForumApiClient.ResultCallback<String>() {
                    @Override public void onSuccess(@Nullable String data) { action.run(); }
                    @Override public void onError(@NonNull String message) { finishTopicAction(message); }
                });
    }

    private void completeAction(String message) {
        topicActionBusy = false;
        setResult(RESULT_OK);
        if (!TextUtils.isEmpty(message)) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void finishTopicAction(String message) {
        topicActionBusy = false;
        if (!isDead()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
        loadMoreCommentsView.setText("加载中…");
        ForumApiClient.getInstance().getComments(topicId, commentsCursor,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        loadingComments = false;
                        if (isDead()) return;
                        if (page != null && page.results != null) comments.addAll(page.results);
                        if (page != null && !TextUtils.isEmpty(page.cursor)) commentsCursor = page.cursor;
                        commentsHasMore = page != null && page.hasMore;
                        renderComments();
                        if (refreshCommentsPending) {
                            refreshCommentsPending = false;
                            loadComments(true);
                        }
                    }

                    @Override public void onError(@NonNull String message) {
                        loadingComments = false;
                        loadMoreCommentsView.setText("加载失败，点击重试");
                        loadMoreCommentsView.setVisibility(View.VISIBLE);
                        Toast.makeText(ForumTopicActivity.this, message, Toast.LENGTH_LONG).show();
                        if (refreshCommentsPending) {
                            refreshCommentsPending = false;
                            loadComments(true);
                        }
                    }
                });
    }

    private void renderComments() {
        commentsContainer.removeAllViews();
        if (comments.isEmpty()) {
            TextView empty = text("还没有评论，来发表第一条吧", 14,
                    isDark() ? 0xFF8F949C : 0xFF8A9098, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(30), dp(18), dp(34));
            commentsContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (ForumApiClient.Comment comment : comments) {
                commentsContainer.addView(createCommentView(comment, comment.id, false),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        loadMoreCommentsView.setText("加载更多评论");
        loadMoreCommentsView.setVisibility(commentsHasMore ? View.VISIBLE : View.GONE);
    }

    private View createCommentView(ForumApiClient.Comment comment, long parentId, boolean reply) {
        boolean dark = isDark();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int left = reply ? 62 : 18;
        row.setPadding(dp(left), dp(reply ? 8 : 12), dp(14), 0);
        row.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int avatarDp = reply ? 25 : 32;
        AvatarView avatar = new AvatarView(this);
        avatar.setSize(avatarDp);
        String author = userName(comment.user);
        bindAvatar(avatar, comment.user, author);
        header.addView(avatar, new LinearLayout.LayoutParams(dp(avatarDp + 4), dp(avatarDp + 4)));

        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.VERTICAL);
        String headerName = author;
        if (reply && comment.quote != null && comment.quote.user != null) {
            headerName += "  回复  " + userName(comment.quote.user);
        }
        TextView name = text(headerName, reply ? 12 : 13,
                dark ? 0xFFE4E6E9 : 0xFF30353B, true);
        TextView time = text(formatDate(comment.createTime), 10.5f,
                dark ? 0xFF858A92 : 0xFF8A9098, false);
        nameBox.addView(name);
        nameBox.addView(time);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(8);
        header.addView(nameBox, nameParams);
        row.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = htmlText(comment.content, reply ? 14 : 15);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(7);
        bodyParams.leftMargin = dp(avatarDp + 12);
        row.addView(body, bodyParams);
        addRemoteImages(row, comment.imageList, dp(7));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setPadding(dp(avatarDp + 4), dp(4), 0, dp(5));
        TextView like = commentAction(comment.liked ? "♥ " + comment.likeCount
                : "♡" + (comment.likeCount > 0 ? " " + comment.likeCount : ""));
        like.setTextColor(comment.liked ? 0xFFE53935 : (dark ? 0xFFB8BBC2 : 0xFF59616A));
        like.setOnClickListener(v -> changeCommentLike(comment));
        actions.addView(like, new LinearLayout.LayoutParams(dp(54), dp(34)));

        TextView replyButton = commentAction("↩");
        replyButton.setContentDescription("回复");
        replyButton.setOnClickListener(v -> setReplyTarget(parentId, comment.id, author));
        actions.addView(replyButton, new LinearLayout.LayoutParams(dp(46), dp(34)));

        TextView more = commentAction("⋮");
        more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        more.setContentDescription("评论操作");
        more.setOnClickListener(v -> showCommentMenu(comment));
        actions.addView(more, new LinearLayout.LayoutParams(dp(42), dp(34)));
        row.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!reply && comment.replies != null && comment.replies.results != null) {
            for (ForumApiClient.Comment item : comment.replies.results) {
                row.addView(createCommentView(item, comment.id, true),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            if (comment.replies.hasMore) {
                TextView moreReplies = text("展开更多回复", 12, 0xFF1877F2, true);
                moreReplies.setPadding(dp(45), dp(5), dp(8), dp(8));
                moreReplies.setOnClickListener(v -> loadMoreReplies(comment));
                row.addView(moreReplies, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        } else if (!reply && comment.commentCount > 0) {
            TextView moreReplies = text("查看 " + comment.commentCount + " 条回复", 12,
                    0xFF1877F2, true);
            moreReplies.setPadding(dp(45), dp(5), dp(8), dp(8));
            moreReplies.setOnClickListener(v -> loadMoreReplies(comment));
            row.addView(moreReplies, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        row.addView(divider(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f)));
        return row;
    }

    private TextView commentAction(String label) {
        TextView view = text(label, 15, isDark() ? 0xFFB8BBC2 : 0xFF59616A, false);
        view.setGravity(Gravity.CENTER);
        view.setBackground(selectableBackground());
        return view;
    }

    private void changeCommentLike(ForumApiClient.Comment comment) {
        if (comment == null || comment.id <= 0 || topicActionBusy) return;
        boolean next = !comment.liked;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setCommentLiked(comment.id, next,
                new VoidCallback() {
                    @Override public void success() {
                        comment.liked = next;
                        comment.likeCount = Math.max(0, comment.likeCount + (next ? 1 : -1));
                        completeAction("");
                        renderComments();
                    }
                }));
    }

    private boolean canDeleteComment(ForumApiClient.Comment comment) {
        if (comment == null) return false;
        ForumApiClient client = ForumApiClient.getInstance();
        return client.hasPermission("dashboard.comment.delete")
                || (comment.user != null && TextUtils.equals(
                client.getCurrentForumUserId(), comment.user.id));
    }

    private void showCommentMenu(ForumApiClient.Comment comment) {
        if (comment == null || topicActionBusy) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("举报");
        actions.add(() -> showCommentReportDialog(comment));
        labels.add("分享");
        actions.add(() -> shareText("评论", userName(comment.user) + "：" + safe(comment.content)));
        if (canDeleteComment(comment)) {
            labels.add("删除");
            actions.add(() -> confirmDeleteComment(comment));
        }
        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCommentReportDialog(ForumApiClient.Comment comment) {
        String[] reasons = {"广告或诈骗", "色情低俗", "辱骂骚扰", "违法违规", "其他"};
        new AlertDialog.Builder(this)
                .setTitle("举报评论")
                .setItems(reasons, (dialog, which) -> reportComment(comment, reasons[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void reportComment(ForumApiClient.Comment comment, String reason) {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().reportComment(comment.id, reason,
                new VoidCallback() {
                    @Override public void success() { completeAction("举报已提交"); }
                }));
    }

    private void confirmDeleteComment(ForumApiClient.Comment comment) {
        if (comment == null || comment.id <= 0) return;
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
                    @Override public void onSuccess(@Nullable Void data) {
                        completeAction("评论已删除");
                        loadTopic();
                    }
                    @Override public void onError(@NonNull String message) { finishTopicAction(message); }
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
                    @Override public void onError(@NonNull String message) {
                        loadingReplyIds.remove(parent.id);
                        Toast.makeText(ForumTopicActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setReplyTarget(long parentId, long quoteId, String name) {
        replyParentId = parentId;
        replyQuoteId = quoteId;
        replyHint.setText("回复 " + safe(name) + "（点击取消）");
        replyHint.setVisibility(View.VISIBLE);
        focusCommentInput();
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
            startSpeechInput();
            return;
        }
        setSending(true);
        ForumApiClient.getInstance().ensureSession(this,
                new ForumApiClient.ResultCallback<String>() {
                    @Override public void onSuccess(@Nullable String data) {
                        submitComment(content);
                    }
                    @Override public void onError(@NonNull String message) { failSend(message); }
                });
    }

    private void submitComment(String content) {
        String entityType = replyParentId > 0 ? "comment" : "topic";
        String entityId = replyParentId > 0 ? String.valueOf(replyParentId) : topicId;
        ForumApiClient.getInstance().createComment(entityType, entityId, content, replyQuoteId,
                new ArrayList<>(), new ForumApiClient.ResultCallback<ForumApiClient.Comment>() {
                    @Override public void onSuccess(@Nullable ForumApiClient.Comment data) {
                        if (isDead()) return;
                        commentInput.setText("");
                        clearReplyTarget();
                        setSending(false);
                        loadComments(true);
                    }
                    @Override public void onError(@NonNull String message) { failSend(message); }
                });
    }

    private void failSend(String message) {
        if (isDead()) return;
        setSending(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setSending(boolean value) {
        sending = value;
        commentInput.setEnabled(!value);
        composerAction.setEnabled(!value);
        composerAction.setAlpha(value ? 0.55f : 1f);
        if (value) composerAction.setText("发送中"); else updateComposerAction();
    }

    private void shareText(String title, String content) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, safe(title));
        share.putExtra(Intent.EXTRA_TEXT, safe(content));
        try {
            startActivity(Intent.createChooser(share, "分享"));
        } catch (Throwable error) {
            Toast.makeText(this, "当前设备无法分享", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView htmlText(String html, float sizeSp) {
        TextView view = text("", sizeSp, isDark() ? 0xFFE8E9EB : 0xFF272A2F, false);
        view.setLineSpacing(dp(3), 1.08f);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setText(Html.fromHtml(html == null ? "" : html, Html.FROM_HTML_MODE_LEGACY));
        return view;
    }

    private void addRemoteImages(LinearLayout container, List<ForumApiClient.ImageInfo> images,
                                 int topMargin) {
        if (images == null || images.isEmpty()) return;
        for (ForumApiClient.ImageInfo info : images) {
            if (info == null || TextUtils.isEmpty(info.url)) continue;
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(isDark() ? 0xFF24262B : 0xFFF0F1F3);
            String remote = TextUtils.isEmpty(info.preview) ? info.url : info.preview;
            Glide.with(this).load(ForumApiClient.getInstance().resolveUrl(remote)).into(image);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
            params.topMargin = topMargin;
            container.addView(image, params);
        }
    }

    private void bindAvatar(AvatarView avatar, ForumApiClient.User user, String fallbackName) {
        String avatarUrl = user == null ? "" : ForumApiClient.getInstance().resolveUrl(
                TextUtils.isEmpty(user.smallAvatar) ? user.avatar : user.smallAvatar);
        String seed = user == null ? fallbackName : safe(user.id);
        avatar.showAvatarUrl(avatarUrl, seed, fallbackName, seed);
        avatar.showFlag(user == null ? "" : (TextUtils.isEmpty(user.countryCode)
                ? user.country : user.countryCode));
    }

    private static String userName(ForumApiClient.User user) {
        return user == null || TextUtils.isEmpty(user.nickname) ? "用户" : user.nickname;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(isDark() ? 0xFF292B30 : 0xFFE9EBEF);
        return divider;
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
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true);
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

    private static String formatDate(long value) {
        if (value <= 0) return "刚刚";
        long millis = value < 10_000_000_000L ? value * 1000L : value;
        long diff = Math.max(0L, System.currentTimeMillis() - millis);
        if (diff < 60_000L) return "刚刚";
        if (diff < 3_600_000L) return diff / 60_000L + "分钟前";
        if (diff < 86_400_000L) return diff / 3_600_000L + "小时前";
        if (diff < 7 * 86_400_000L) return diff / 86_400_000L + "天前";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    /** Adapter so common void actions share identical error handling. */
    private abstract class VoidCallback implements ForumApiClient.ResultCallback<Void> {
        abstract void success();
        @Override public final void onSuccess(@Nullable Void data) { success(); }
        @Override public final void onError(@NonNull String message) { finishTopicAction(message); }
    }
}
