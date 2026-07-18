package com.chat.forum;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.chat.base.config.WKApiConfig;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.ui.components.AvatarView;
import com.chat.uikit.view.voice.AudioRecordManager;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Native topic detail with a single RecyclerView for smooth long discussions. */
public class ForumTopicActivity extends AppCompatActivity {
    private static final String EXTRA_TOPIC_ID = "topic_id";
    private static final String SEEN_PREF = "forum_topic_seen";
    private static final long MIN_RECORD_MS = 700L;
    private static final long MAX_RECORD_MS = 60_000L;

    private String topicId = "";
    private RecyclerView recyclerView;
    private TextView stateView;
    private EditText commentInput;
    private TextView composerAction;
    private TextView replyHint;
    private TextView recordHint;
    private TopicDetailAdapter adapter;
    private ForumApiClient.Topic currentTopic;
    private final List<ForumApiClient.Comment> comments = new ArrayList<>();
    private final Set<Long> expandedReplies = new HashSet<>();
    private String commentsCursor = "";
    private boolean commentsHasMore;
    private boolean loadingComments;
    private boolean refreshCommentsPending;
    private boolean sending;
    private boolean topicActionBusy;
    private long replyParentId;
    private long replyQuoteId;
    private boolean authorFollowStateLoaded;
    private boolean authorFollowed;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean recording;
    private boolean recordCancel;
    private long recordStartTime;
    private float recordStartY;
    private String recordPath = "";
    private final Runnable recordTick = new Runnable() {
        @Override
        public void run() {
            if (!recording) return;
            long elapsed = Math.max(0L, System.currentTimeMillis() - recordStartTime);
            int seconds = (int) Math.ceil(elapsed / 1000.0);
            updateRecordHint(seconds);
            if (elapsed >= MAX_RECORD_MS) {
                finishRecord(false);
                return;
            }
            mainHandler.postDelayed(this, 100L);
        }
    };

    private MediaPlayer voicePlayer;
    private String playingVoiceKey = "";
    private TextView playingVoiceView;

    private final ActivityResultLauncher<String> audioPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    Toast.makeText(this, "按住麦克风录音，上滑取消", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "需要麦克风权限才能发送语音评论", Toast.LENGTH_LONG).show();
                }
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
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, 0, 0, dp(12));
        recyclerView.setItemViewCacheSize(12);
        adapter = new TopicDetailAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loadingComments || !commentsHasMore) return;
                RecyclerView.LayoutManager manager = rv.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
                    if (last >= adapter.getItemCount() - 4) loadComments(false);
                }
            }
        });
        body.addView(recyclerView, new FrameLayout.LayoutParams(
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
        wrapper.setPadding(dp(10), 0, dp(10), dp(7));
        wrapper.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        wrapper.addView(divider(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f)));

        replyHint = text("", 12, dark ? 0xFFFFC46B : 0xFF8B6500, false);
        replyHint.setPadding(dp(10), dp(5), dp(10), dp(3));
        replyHint.setVisibility(View.GONE);
        replyHint.setOnClickListener(v -> clearReplyTarget());
        wrapper.addView(replyHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        recordHint = text("", 13, dark ? Color.WHITE : 0xFF33383E, true);
        recordHint.setGravity(Gravity.CENTER);
        recordHint.setPadding(dp(12), dp(7), dp(12), dp(7));
        recordHint.setBackground(roundRect(dark ? 0xFF292C31 : 0xFFF0F2F5, 12));
        recordHint.setVisibility(View.GONE);
        LinearLayout.LayoutParams recordParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recordParams.setMargins(0, dp(5), 0, 0);
        wrapper.addView(recordHint, recordParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);

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

        composerAction = text("", 14, dark ? 0xFFE2E4E8 : 0xFF4D555E, true);
        composerAction.setGravity(Gravity.CENTER);
        composerAction.setBackground(roundRect(dark ? 0xFF25272C : 0xFFF1F3F5, 19));
        composerAction.setOnClickListener(v -> {
            if (sending) return;
            if (!TextUtils.isEmpty(commentInput.getText().toString().trim())) sendComment();
            else Toast.makeText(this, "按住麦克风录音，上滑取消", Toast.LENGTH_SHORT).show();
        });
        composerAction.setOnTouchListener(this::handleComposerTouch);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(58), dp(40));
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

    private boolean handleComposerTouch(View view, MotionEvent event) {
        if (sending || commentInput == null
                || !TextUtils.isEmpty(commentInput.getText().toString().trim())) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                    return true;
                }
                recordStartY = event.getRawY();
                startRecord();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (recording) {
                    recordCancel = recordStartY - event.getRawY() >= dp(76);
                    long elapsed = Math.max(0L, System.currentTimeMillis() - recordStartTime);
                    updateRecordHint((int) Math.ceil(elapsed / 1000.0));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (recording) finishRecord(recordCancel);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (recording) finishRecord(true);
                return true;
            default:
                return false;
        }
    }

    private void startRecord() {
        if (recording) return;
        stopVoicePlayback();
        try {
            File dir = new File(getCacheDir(), "forum_voice");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建录音目录");
            File file = new File(dir, "voice_" + System.currentTimeMillis() + ".amr");
            recordPath = file.getAbsolutePath();
            recordStartTime = System.currentTimeMillis();
            recordCancel = false;
            AudioRecordManager.getInstance().init(recordPath);
            AudioRecordManager.getInstance().startRecord();
            recording = true;
            composerAction.setSelected(true);
            composerAction.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            recordHint.setVisibility(View.VISIBLE);
            updateRecordHint(0);
            mainHandler.post(recordTick);
        } catch (Throwable error) {
            recording = false;
            recordHint.setVisibility(View.GONE);
            Toast.makeText(this, "录音启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRecordHint(int seconds) {
        if (!recording || recordHint == null) return;
        String time = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
        recordHint.setText((recordCancel ? "松开取消" : "松开发送，上滑取消") + "  " + time);
        recordHint.setTextColor(recordCancel ? 0xFFEF4444 : (isDark() ? Color.WHITE : 0xFF33383E));
    }

    private void finishRecord(boolean cancel) {
        if (!recording) return;
        recording = false;
        mainHandler.removeCallbacks(recordTick);
        long duration = Math.max(0L, System.currentTimeMillis() - recordStartTime);
        String localPath = recordPath;
        byte[] waveform = AudioRecordManager.getInstance().getDbs();
        if (cancel) AudioRecordManager.getInstance().cancelRecord();
        else AudioRecordManager.getInstance().stopRecord();
        composerAction.setSelected(false);
        recordHint.setVisibility(View.GONE);
        recordPath = "";
        if (cancel) {
            deleteQuietly(localPath);
            return;
        }
        if (duration < MIN_RECORD_MS) {
            deleteQuietly(localPath);
            Toast.makeText(this, "录音时间太短", Toast.LENGTH_SHORT).show();
            return;
        }
        int seconds = Math.max(1, (int) Math.ceil(duration / 1000.0));
        String waveformText = Base64.encodeToString(waveform == null ? new byte[0] : waveform,
                Base64.NO_WRAP);
        uploadAndSendVoice(localPath, seconds, waveformText);
    }

    private void uploadAndSendVoice(String localPath, int seconds, String waveform) {
        File file = new File(localPath);
        setSending(true);
        ForumApiClient.getInstance().getVoiceUploadTarget(file,
                new ForumApiClient.ResultCallback<ForumApiClient.VoiceUploadTarget>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.VoiceUploadTarget target) {
                        if (target == null || TextUtils.isEmpty(target.uploadUrl)) {
                            failVoice(localPath, "无法获取语音上传地址");
                            return;
                        }
                        String tag = "forum_voice_" + UUID.randomUUID();
                        WKUploader.getInstance().upload(target.uploadUrl, localPath, tag,
                                new WKUploader.IUploadBack() {
                                    @Override
                                    public void onSuccess(String uploadedPath) {
                                        runOnUiThread(() -> {
                                            String remote = !TextUtils.isEmpty(target.publicUrl)
                                                    ? target.publicUrl
                                                    : (!TextUtils.isEmpty(uploadedPath)
                                                    ? uploadedPath : target.path);
                                            String content = "voice:" + normalizeVoicePath(remote)
                                                    + "|" + seconds + "|" + waveform;
                                            sendCommentContent(content, localPath);
                                        });
                                    }

                                    @Override
                                    public void onError() {
                                        runOnUiThread(() -> failVoice(localPath, "语音上传失败"));
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        failVoice(localPath, message);
                    }
                });
    }

    private void failVoice(String localPath, String message) {
        if (isDead()) return;
        deleteQuietly(localPath);
        setSending(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String normalizeVoicePath(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String path = value.trim();
        if (path.startsWith(WKApiConfig.baseUrl)) path = path.substring(WKApiConfig.baseUrl.length());
        if (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("file/preview/")) return path;
        if (path.startsWith("common/")) return "file/preview/" + path;
        if (path.startsWith("forum/")) return "file/preview/common/" + path;
        return path;
    }

    private void updateComposerAction() {
        if (composerAction == null || commentInput == null) return;
        boolean hasText = !TextUtils.isEmpty(commentInput.getText().toString().trim());
        composerAction.setText(hasText ? "发送" : "");
        composerAction.setTextColor(hasText ? Color.WHITE : (isDark() ? 0xFFE2E4E8 : 0xFF4D555E));
        composerAction.setBackground(roundRect(hasText ? 0xFF1877F2
                : (isDark() ? 0xFF25272C : 0xFFF1F3F5), 19));
        composerAction.setContentDescription(hasText ? "发送评论" : "按住录音");
        if (hasText) {
            composerAction.setCompoundDrawables(null, null, null, null);
        } else {
            setCompoundIcon(composerAction, R.drawable.ic_forum_mic, 23,
                    isDark() ? 0xFFE2E4E8 : 0xFF4D555E);
        }
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
                        currentTopic = topic;
                        markTopicSeen(topic);
                        stateView.setVisibility(View.GONE);
                        adapter.rebuild();
                        loadAuthorFollowState(topic);
                        loadComments(true);
                    }

                    @Override public void onError(@NonNull String message) {
                        if (!isDead()) showError(message);
                    }
                });
    }

    private void markTopicSeen(ForumApiClient.Topic topic) {
        if (topic == null || TextUtils.isEmpty(topic.id)) return;
        long latest = Math.max(normalizeTime(topic.lastCommentTime), normalizeTime(topic.createTime));
        getSharedPreferences(SEEN_PREF, MODE_PRIVATE).edit()
                .putLong("time_" + topic.id, latest)
                .putLong("count_" + topic.id, Math.max(0L, topic.commentCount))
                .apply();
    }

    private void loadAuthorFollowState(ForumApiClient.Topic topic) {
        if (topic == null || topic.user == null || TextUtils.isEmpty(topic.user.id)
                || TextUtils.equals(ForumApiClient.getInstance().getCurrentForumUserId(), topic.user.id)) {
            authorFollowStateLoaded = true;
            authorFollowed = false;
            adapter.rebuild();
            return;
        }
        ForumApiClient.getInstance().getUserFollowed(topic.user.id,
                new ForumApiClient.ResultCallback<Boolean>() {
                    @Override public void onSuccess(@Nullable Boolean followed) {
                        if (isDead() || currentTopic != topic) return;
                        authorFollowStateLoaded = true;
                        authorFollowed = Boolean.TRUE.equals(followed);
                        adapter.rebuild();
                    }
                    @Override public void onError(@NonNull String message) {
                        authorFollowStateLoaded = true;
                        adapter.rebuild();
                    }
                });
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
            adapter.rebuild();
        }
        loadingComments = true;
        adapter.rebuild();
        ForumApiClient.getInstance().getComments(topicId, commentsCursor,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        loadingComments = false;
                        if (isDead()) return;
                        if (page != null && page.results != null) appendUnique(comments, page.results);
                        if (page != null && !TextUtils.isEmpty(page.cursor)) commentsCursor = page.cursor;
                        commentsHasMore = page != null && page.hasMore;
                        adapter.rebuild();
                        if (refreshCommentsPending) {
                            refreshCommentsPending = false;
                            loadComments(true);
                        }
                    }

                    @Override public void onError(@NonNull String message) {
                        loadingComments = false;
                        adapter.rebuild();
                        Toast.makeText(ForumTopicActivity.this, message, Toast.LENGTH_LONG).show();
                        if (refreshCommentsPending) {
                            refreshCommentsPending = false;
                            loadComments(true);
                        }
                    }
                });
    }

    private void loadMoreReplies(ForumApiClient.Comment parent) {
        if (parent == null || parent.id <= 0 || loadingComments) return;
        loadingComments = true;
        adapter.rebuild();
        String replyCursor = parent.replies == null ? "" : safe(parent.replies.cursor);
        ForumApiClient.getInstance().getReplies(parent.id, replyCursor,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        loadingComments = false;
                        if (isDead()) return;
                        if (parent.replies == null) parent.replies = new ForumApiClient.Page<>();
                        if (parent.replies.results == null) parent.replies.results = new ArrayList<>();
                        if (page != null && page.results != null) {
                            appendUnique(parent.replies.results, page.results);
                        }
                        parent.replies.cursor = page == null ? parent.replies.cursor : page.cursor;
                        parent.replies.hasMore = page != null && page.hasMore;
                        expandedReplies.add(parent.id);
                        adapter.rebuild();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        loadingComments = false;
                        adapter.rebuild();
                        Toast.makeText(ForumTopicActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private static void appendUnique(List<ForumApiClient.Comment> target,
                                     List<ForumApiClient.Comment> source) {
        Set<Long> ids = new HashSet<>();
        for (ForumApiClient.Comment item : target) if (item != null) ids.add(item.id);
        for (ForumApiClient.Comment item : source) {
            if (item != null && ids.add(item.id)) target.add(item);
        }
    }

    private void toggleReplies(ForumApiClient.Comment parent) {
        if (parent == null) return;
        if (expandedReplies.contains(parent.id)) {
            expandedReplies.remove(parent.id);
            adapter.rebuild();
            return;
        }
        expandedReplies.add(parent.id);
        if (parent.replies == null || parent.replies.results == null
                || parent.replies.results.isEmpty()) {
            loadMoreReplies(parent);
        } else {
            adapter.rebuild();
        }
    }

    private void showTopicMenu() {
        ForumApiClient.Topic topic = currentTopic;
        if (topic == null || topicActionBusy) return;
        ForumApiClient client = ForumApiClient.getInstance();
        boolean manager = client.isForumManager();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("分享");
        actions.add(() -> shareText(topic.title, safe(topic.title) + "\n" + safe(topic.content)));
        labels.add("举报");
        actions.add(this::showReportDialog);
        if (manager || client.hasPermission("dashboard.topic.recommend")) {
            labels.add(topic.recommend ? "取消推荐" : "推荐");
            actions.add(() -> changeTopicRecommended(topic));
        }
        if (manager || client.hasPermission("dashboard.topic.sticky")) {
            labels.add(topic.sticky ? "取消置顶" : "置顶");
            actions.add(() -> changeTopicSticky(topic));
        }
        if (canDeleteTopic(topic)) {
            labels.add("删除帖子");
            actions.add(this::confirmDeleteTopic);
        }
        if (topic.user != null && !TextUtils.isEmpty(topic.user.id)
                && !TextUtils.equals(client.getCurrentForumUserId(), topic.user.id)) {
            if (manager || client.hasPermission("dashboard.user.forbidden")) {
                labels.add("禁言 7 天");
                actions.add(() -> confirmForbidUser(topic.user, 7));
            }
            if (manager || client.hasPermission("dashboard.user.forbiddenForever")) {
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
        return client.isForumManager() || client.hasPermission("dashboard.topic.delete")
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
                        adapter.rebuild();
                    }
                }));
    }

    private void changeTopicRecommended(ForumApiClient.Topic topic) {
        boolean next = !topic.recommend;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicRecommended(topic.id, next,
                new VoidCallback() {
                    @Override public void success() {
                        topic.recommend = next;
                        completeAction(next ? "已推荐" : "已取消推荐");
                        adapter.rebuild();
                    }
                }));
    }

    private void changeTopicLike() {
        ForumApiClient.Topic topic = currentTopic;
        if (topic == null) return;
        boolean next = !topic.liked;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicLiked(topic.id, next,
                new VoidCallback() {
                    @Override public void success() {
                        topic.liked = next;
                        topic.likeCount = Math.max(0, topic.likeCount + (next ? 1 : -1));
                        completeAction("");
                        adapter.rebuild();
                    }
                }));
    }

    private void changeFavorite() {
        ForumApiClient.Topic topic = currentTopic;
        if (topic == null) return;
        boolean next = !topic.favorited;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setTopicFavorited(topic.id, next,
                new VoidCallback() {
                    @Override public void success() {
                        topic.favorited = next;
                        completeAction(next ? "已收藏" : "已取消收藏");
                        adapter.rebuild();
                    }
                }));
    }

    private void changeAuthorFollow() {
        ForumApiClient.Topic topic = currentTopic;
        if (topic == null || topic.user == null || TextUtils.isEmpty(topic.user.id)) return;
        boolean next = !authorFollowed;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().setUserFollowed(
                topic.user.id, next, new VoidCallback() {
                    @Override public void success() {
                        authorFollowStateLoaded = true;
                        authorFollowed = next;
                        completeAction(next ? "已关注" : "已取消关注");
                        adapter.rebuild();
                    }
                }));
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
                        adapter.rebuild();
                    }
                }));
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

    private boolean canDeleteComment(ForumApiClient.Comment comment) {
        if (comment == null) return false;
        ForumApiClient client = ForumApiClient.getInstance();
        return client.isForumManager() || client.hasPermission("dashboard.comment.delete")
                || (comment.user != null && TextUtils.equals(
                client.getCurrentForumUserId(), comment.user.id));
    }

    private void showReportDialog() {
        String[] reasons = {"广告或诈骗", "色情低俗", "辱骂骚扰", "违法违规", "其他"};
        new AlertDialog.Builder(this)
                .setTitle("举报帖子")
                .setItems(reasons, (dialog, which) -> reportTopic(reasons[which]))
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

    private void reportTopic(String reason) {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().reportTopic(topicId, reason,
                new VoidCallback() {
                    @Override public void success() { completeAction("举报已提交"); }
                }));
    }

    private void reportComment(ForumApiClient.Comment comment, String reason) {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().reportComment(comment.id, reason,
                new VoidCallback() {
                    @Override public void success() { completeAction("举报已提交"); }
                }));
    }

    private void confirmDeleteTopic() {
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

    private void confirmDeleteComment(ForumApiClient.Comment comment) {
        new AlertDialog.Builder(this)
                .setTitle("删除评论")
                .setMessage("删除后无法恢复，确定继续吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteComment(comment.id))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteComment(long commentId) {
        runAuthenticatedAction(() -> ForumApiClient.getInstance().deleteComment(commentId,
                new VoidCallback() {
                    @Override public void success() {
                        removeComment(commentId);
                        completeAction("评论已删除");
                        adapter.rebuild();
                    }
                }));
    }

    private void removeComment(long id) {
        for (int i = comments.size() - 1; i >= 0; i--) {
            ForumApiClient.Comment item = comments.get(i);
            if (item != null && item.id == id) {
                comments.remove(i);
                return;
            }
            if (item != null && item.replies != null && item.replies.results != null) {
                for (int j = item.replies.results.size() - 1; j >= 0; j--) {
                    ForumApiClient.Comment reply = item.replies.results.get(j);
                    if (reply != null && reply.id == id) {
                        item.replies.results.remove(j);
                        item.commentCount = Math.max(0, item.commentCount - 1);
                        return;
                    }
                }
            }
        }
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
                "论坛内容管理", new VoidCallback() {
                    @Override public void success() {
                        completeAction(days == -1 ? "已永久禁言" : "已禁言 7 天");
                    }
                }));
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
        String content = commentInput.getText().toString().trim();
        if (TextUtils.isEmpty(content) || sending) return;
        setSending(true);
        sendCommentContent(content, null);
    }

    private void sendCommentContent(String content, @Nullable String localVoicePath) {
        String entityType = replyParentId > 0 ? "comment" : "topic";
        String entityId = replyParentId > 0 ? String.valueOf(replyParentId) : topicId;
        ForumApiClient.getInstance().createComment(entityType, entityId, content, replyQuoteId,
                new ArrayList<>(), new ForumApiClient.ResultCallback<ForumApiClient.Comment>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Comment data) {
                        if (isDead()) return;
                        deleteQuietly(localVoicePath);
                        commentInput.setText("");
                        clearReplyTarget();
                        setSending(false);
                        setResult(RESULT_OK);
                        requestTopic();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        deleteQuietly(localVoicePath);
                        failSend(message);
                    }
                });
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
        if (value) {
            composerAction.setCompoundDrawables(null, null, null, null);
            composerAction.setText("发送中");
        } else {
            updateComposerAction();
        }
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

    private void focusCommentInput() {
        recyclerView.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
        commentInput.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(commentInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void playVoice(VoicePayload payload, String key, TextView view) {
        if (payload == null || TextUtils.isEmpty(payload.playPath())) return;
        if (voicePlayer != null && TextUtils.equals(playingVoiceKey, key)) {
            try {
                if (voicePlayer.isPlaying()) {
                    voicePlayer.pause();
                    setCompoundIcon(view, R.drawable.ic_forum_play, 20,
                            isDark() ? 0xFFDFE2E6 : 0xFF3E464F);
                } else {
                    voicePlayer.start();
                    setCompoundIcon(view, R.drawable.ic_forum_pause, 20, 0xFF1877F2);
                }
            } catch (Throwable ignored) { }
            return;
        }
        stopVoicePlayback();
        playingVoiceKey = key;
        playingVoiceView = view;
        voicePlayer = new MediaPlayer();
        try {
            voicePlayer.setDataSource(payload.playPath());
            voicePlayer.setOnPreparedListener(player -> {
                if (isDead()) return;
                player.start();
                setCompoundIcon(view, R.drawable.ic_forum_pause, 20, 0xFF1877F2);
            });
            voicePlayer.setOnCompletionListener(player -> stopVoicePlayback());
            voicePlayer.setOnErrorListener((player, what, extra) -> {
                stopVoicePlayback();
                Toast.makeText(this, "语音播放失败", Toast.LENGTH_SHORT).show();
                return true;
            });
            voicePlayer.prepareAsync();
        } catch (Throwable error) {
            stopVoicePlayback();
            Toast.makeText(this, "语音播放失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopVoicePlayback() {
        if (voicePlayer != null) {
            try { voicePlayer.stop(); } catch (Throwable ignored) { }
            try { voicePlayer.release(); } catch (Throwable ignored) { }
            voicePlayer = null;
        }
        if (playingVoiceView != null) {
            setCompoundIcon(playingVoiceView, R.drawable.ic_forum_play, 20,
                    isDark() ? 0xFFDFE2E6 : 0xFF3E464F);
        }
        playingVoiceKey = "";
        playingVoiceView = null;
    }

    @Override
    protected void onDestroy() {
        if (recording) finishRecord(true);
        mainHandler.removeCallbacks(recordTick);
        stopVoicePlayback();
        super.onDestroy();
    }

    private final class TopicDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ARTICLE = 1;
        private static final int TYPE_COMMENT = 2;
        private static final int TYPE_TOGGLE = 3;
        private static final int TYPE_EMPTY = 4;
        private static final int TYPE_LOAD_MORE = 5;
        private final List<Row> rows = new ArrayList<>();

        private TopicDetailAdapter() {
            setHasStableIds(true);
        }

        void rebuild() {
            rows.clear();
            if (currentTopic != null) rows.add(Row.article());
            if (currentTopic != null && comments.isEmpty() && !loadingComments) {
                rows.add(Row.empty());
            }
            for (ForumApiClient.Comment parent : comments) {
                if (parent == null) continue;
                rows.add(Row.comment(parent, parent.id, false));
                boolean expanded = expandedReplies.contains(parent.id);
                if (expanded && parent.replies != null && parent.replies.results != null) {
                    for (ForumApiClient.Comment reply : parent.replies.results) {
                        if (reply != null) rows.add(Row.comment(reply, parent.id, true));
                    }
                }
                if (parent.commentCount > 0 || (parent.replies != null
                        && parent.replies.results != null && !parent.replies.results.isEmpty())) {
                    rows.add(Row.toggle(parent));
                }
            }
            if (commentsHasMore || loadingComments) rows.add(Row.loadMore());
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return rows.get(position).stableId();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_ARTICLE) return new ArticleHolder(new LinearLayout(parent.getContext()));
            if (viewType == TYPE_COMMENT) return new CommentHolder(createCommentItem(parent.getContext()));
            if (viewType == TYPE_TOGGLE) return new ToggleHolder(createToggleItem(parent.getContext()));
            TextView text = ForumTopicActivity.this.text("", 14,
                    isDark() ? 0xFF8F949C : 0xFF7A818A, false);
            text.setGravity(Gravity.CENTER);
            text.setPadding(dp(18), dp(24), dp(18), dp(28));
            return new SimpleHolder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof ArticleHolder) bindArticle((ArticleHolder) holder);
            else if (holder instanceof CommentHolder) bindComment((CommentHolder) holder, row);
            else if (holder instanceof ToggleHolder) bindToggle((ToggleHolder) holder, row.parent);
            else if (holder instanceof SimpleHolder) {
                TextView text = (TextView) holder.itemView;
                if (row.type == TYPE_EMPTY) {
                    text.setText("还没有评论，来发表第一条吧");
                    text.setOnClickListener(v -> focusCommentInput());
                } else {
                    text.setText(loadingComments ? "加载中…" : "加载更多评论");
                    text.setOnClickListener(v -> {
                        if (!loadingComments) loadComments(false);
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private void bindArticle(ArticleHolder holder) {
            LinearLayout root = (LinearLayout) holder.itemView;
            root.removeAllViews();
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(18), dp(12), dp(18), 0);
            root.setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);
            ForumApiClient.Topic topic = currentTopic;
            if (topic == null) return;

            LinearLayout topMeta = new LinearLayout(ForumTopicActivity.this);
            topMeta.setGravity(Gravity.CENTER_VERTICAL);
            if (topic.category != null && !TextUtils.isEmpty(topic.category.name)) {
                TextView category = text(topic.category.name, 12, 0xFF1877F2, true);
                category.setGravity(Gravity.CENTER);
                category.setPadding(dp(10), 0, dp(10), 0);
                category.setBackground(roundRect(isDark() ? 0xFF243B59 : 0xFFEAF3FF, 12));
                topMeta.addView(category, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)));
            }
            if (topic.sticky) {
                TextView sticky = smallBadge("置顶", 0xFFB96800,
                        isDark() ? 0xFF40311D : 0xFFFFF0D6);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(23));
                lp.leftMargin = dp(6);
                topMeta.addView(sticky, lp);
            }
            if (topic.recommend) {
                TextView recommend = smallBadge("推荐", 0xFF1877F2,
                        isDark() ? 0xFF243B59 : 0xFFEAF3FF);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(23));
                lp.leftMargin = dp(6);
                topMeta.addView(recommend, lp);
            }
            root.addView(topMeta);

            TextView title = text(safe(topic.title), 24,
                    isDark() ? Color.WHITE : 0xFF17191C, true);
            title.setLineSpacing(0, 1.10f);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = dp(10);
            root.addView(title, titleParams);
            root.addView(buildAuthorRow(topic), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f));
            dividerParams.topMargin = dp(13);
            dividerParams.bottomMargin = dp(15);
            root.addView(divider(), dividerParams);

            TextView content = htmlText(TextUtils.isEmpty(topic.content) ? topic.summary : topic.content, 17);
            root.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addRemoteImages(root, topic.imageList, dp(10));

            LinearLayout actions = new LinearLayout(ForumTopicActivity.this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(0, dp(15), 0, dp(13));
            addTopicStat(actions, R.drawable.ic_forum_eye, String.valueOf(Math.max(0, topic.viewCount)), false, null);
            addTopicStat(actions, topic.liked ? R.drawable.ic_forum_heart_filled : R.drawable.ic_forum_heart_round,
                    String.valueOf(Math.max(0, topic.likeCount)), topic.liked, v -> changeTopicLike());
            addTopicStat(actions, topic.favorited ? R.drawable.ic_forum_bookmark_filled : R.drawable.ic_forum_bookmark,
                    topic.favorited ? "已收藏" : "收藏", topic.favorited, v -> changeFavorite());
            addTopicStat(actions, R.drawable.ic_forum_reply_round,
                    String.valueOf(Math.max(0, topic.commentCount)), false, v -> focusCommentInput());
            root.addView(actions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(divider(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(0.8f)));
            TextView commentsTitle = text(topic.commentCount > 0 ? "评论 " + topic.commentCount : "评论",
                    18, isDark() ? Color.WHITE : 0xFF202328, true);
            commentsTitle.setPadding(0, dp(15), 0, dp(9));
            root.addView(commentsTitle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private View buildAuthorRow(ForumApiClient.Topic topic) {
            LinearLayout row = new LinearLayout(ForumTopicActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(13), 0, 0);
            AvatarView avatar = new AvatarView(ForumTopicActivity.this);
            avatar.setSize(39);
            String author = userName(topic.user);
            bindAvatar(avatar, topic.user, author);
            row.addView(avatar, new LinearLayout.LayoutParams(dp(43), dp(43)));

            LinearLayout copy = new LinearLayout(ForumTopicActivity.this);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout nameRow = new LinearLayout(ForumTopicActivity.this);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView authorView = text(author, 14,
                    isDark() ? Color.WHITE : 0xFF272B31, true);
            nameRow.addView(authorView);
            String currentForumUserId = ForumApiClient.getInstance().getCurrentForumUserId();
            if (topic.user != null && !TextUtils.isEmpty(topic.user.id)
                    && !TextUtils.equals(currentForumUserId, topic.user.id)) {
                String followLabel = authorFollowStateLoaded && authorFollowed ? " · 已关注" : " · 关注";
                TextView follow = text(followLabel, 13, 0xFFE53935, true);
                follow.setPadding(dp(2), 0, dp(8), 0);
                follow.setOnClickListener(v -> changeAuthorFollow());
                nameRow.addView(follow);
            }
            copy.addView(nameRow);
            TextView meta = text(formatDate(topic.createTime), 12,
                    isDark() ? 0xFF8F949C : 0xFF7A8088, false);
            copy.addView(meta);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            copyParams.leftMargin = dp(10);
            row.addView(copy, copyParams);

            TextView more = text("⋮", 27, isDark() ? 0xFFD8DADE : 0xFF4D535B, false);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("帖子操作");
            more.setBackground(selectableBackground());
            more.setOnClickListener(v -> showTopicMenu());
            row.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));
            return row;
        }

        private void addTopicStat(LinearLayout row, int iconRes, String label,
                                  boolean active, @Nullable View.OnClickListener click) {
            TextView action = text(label, 13,
                    active ? 0xFFE34A55 : (isDark() ? 0xFFB8BBC2 : 0xFF59616A), false);
            action.setGravity(Gravity.CENTER);
            action.setCompoundDrawablePadding(dp(4));
            setCompoundIcon(action, iconRes, iconRes == R.drawable.ic_forum_eye ? 23 : 20,
                    active ? 0xFFE34A55 : (isDark() ? 0xFFB8BBC2 : 0xFF59616A));
            if (click != null) {
                action.setBackground(selectableBackground());
                action.setOnClickListener(click);
            }
            row.addView(action, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }

        private void bindComment(CommentHolder holder, Row row) {
            ForumApiClient.Comment comment = row.comment;
            boolean reply = row.reply;
            int left = reply ? 52 : 16;
            holder.root.setPadding(dp(left), dp(reply ? 7 : 10), dp(14), 0);
            holder.avatar.setSize(reply ? 25 : 32);
            LinearLayout.LayoutParams avatarParams = (LinearLayout.LayoutParams) holder.avatar.getLayoutParams();
            avatarParams.width = dp((reply ? 25 : 32) + 4);
            avatarParams.height = dp((reply ? 25 : 32) + 4);
            holder.avatar.setLayoutParams(avatarParams);
            String author = userName(comment.user);
            bindAvatar(holder.avatar, comment.user, author);
            String target = reply && comment.quote != null && comment.quote.user != null
                    ? userName(comment.quote.user) : "";
            String header = reply && !TextUtils.isEmpty(target)
                    ? author + " 回复 " + target + " · " + formatDate(comment.createTime)
                    : author + " · " + formatDate(comment.createTime);
            holder.meta.setText(header);
            holder.meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, reply ? 12 : 13);

            VoicePayload voice = VoicePayload.parse(comment.content);
            if (voice == null) {
                holder.body.setVisibility(View.VISIBLE);
                holder.voice.setVisibility(View.GONE);
                holder.body.setText(Html.fromHtml(safe(comment.content).replace("\n", "<br>"),
                        Html.FROM_HTML_MODE_LEGACY));
            } else {
                holder.body.setVisibility(View.GONE);
                holder.voice.setVisibility(View.VISIBLE);
                holder.voice.setText(formatVoiceTime(voice.durationSec));
                setCompoundIcon(holder.voice, R.drawable.ic_forum_play, 20,
                        isDark() ? 0xFFDFE2E6 : 0xFF3E464F);
                String key = String.valueOf(comment.id);
                holder.voice.setOnClickListener(v -> playVoice(voice, key, holder.voice));
            }

            holder.imageContainer.removeAllViews();
            addRemoteImages(holder.imageContainer, comment.imageList, dp(6));
            holder.imageContainer.setVisibility(holder.imageContainer.getChildCount() > 0
                    ? View.VISIBLE : View.GONE);

            holder.like.setText(comment.likeCount > 0 ? String.valueOf(comment.likeCount) : "");
            setCompoundIcon(holder.like, comment.liked ? R.drawable.ic_forum_heart_filled
                            : R.drawable.ic_forum_heart_round,
                    19, comment.liked ? 0xFFE34A55 : (isDark() ? 0xFFB8BBC2 : 0xFF59616A));
            holder.like.setOnClickListener(v -> changeCommentLike(comment));
            holder.reply.setOnClickListener(v -> setReplyTarget(row.parentId, comment.id, author));
            holder.root.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showCommentMenu(comment);
                return true;
            });
        }

        private void bindToggle(ToggleHolder holder, ForumApiClient.Comment parent) {
            boolean expanded = expandedReplies.contains(parent.id);
            boolean hasLoaded = parent.replies != null && parent.replies.results != null
                    && !parent.replies.results.isEmpty();
            boolean hasMore = parent.replies != null && parent.replies.hasMore;
            holder.more.setVisibility(View.GONE);
            holder.collapse.setVisibility(View.GONE);
            if (!expanded) {
                holder.more.setVisibility(View.VISIBLE);
                holder.more.setText("查看 " + Math.max(parent.commentCount,
                        hasLoaded ? parent.replies.results.size() : 0) + " 条回复");
                holder.more.setOnClickListener(v -> toggleReplies(parent));
            } else {
                if (hasMore) {
                    holder.more.setVisibility(View.VISIBLE);
                    holder.more.setText(loadingComments ? "加载中…" : "继续加载更多");
                    holder.more.setOnClickListener(v -> {
                        if (!loadingComments) loadMoreReplies(parent);
                    });
                }
                holder.collapse.setVisibility(View.VISIBLE);
                holder.collapse.setText("收起回复");
                holder.collapse.setOnClickListener(v -> toggleReplies(parent));
            }
        }
    }

    private View createCommentItem(Context context) {
        boolean dark = isDark();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        AvatarView avatar = new AvatarView(context);
        avatar.setSize(32);
        header.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView meta = text("", 13, dark ? 0xFFE4E6E9 : 0xFF30353B, true);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metaParams.leftMargin = dp(8);
        header.addView(meta, metaParams);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = htmlText("", 15);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(6);
        bodyParams.leftMargin = dp(40);
        root.addView(body, bodyParams);

        TextView voice = text("00:01", 13, dark ? 0xFFDFE2E6 : 0xFF3E464F, true);
        voice.setGravity(Gravity.CENTER_VERTICAL);
        voice.setCompoundDrawablePadding(dp(7));
        voice.setPadding(dp(12), 0, dp(12), 0);
        voice.setBackground(roundRect(dark ? 0xFF24272C : 0xFFF0F3F7, 17));
        voice.setVisibility(View.GONE);
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(dp(150), dp(38));
        voiceParams.topMargin = dp(6);
        voiceParams.leftMargin = dp(40);
        root.addView(voice, voiceParams);

        LinearLayout images = new LinearLayout(context);
        images.setOrientation(LinearLayout.VERTICAL);
        images.setVisibility(View.GONE);
        LinearLayout.LayoutParams imagesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imagesParams.leftMargin = dp(40);
        root.addView(images, imagesParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setPadding(dp(36), dp(2), 0, dp(4));
        TextView like = text("", 13, dark ? 0xFFB8BBC2 : 0xFF59616A, false);
        like.setGravity(Gravity.CENTER);
        like.setCompoundDrawablePadding(dp(3));
        like.setBackground(selectableBackground());
        actions.addView(like, new LinearLayout.LayoutParams(dp(58), dp(34)));
        TextView reply = text("»", 22, dark ? 0xFFB8BBC2 : 0xFF59616A, false);
        reply.setGravity(Gravity.CENTER);
        reply.setContentDescription("回复");
        reply.setBackground(selectableBackground());
        actions.addView(reply, new LinearLayout.LayoutParams(dp(48), dp(34)));
        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(divider(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f)));
        return root;
    }

    private View createToggleItem(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(52), dp(3), dp(14), dp(6));
        root.setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);
        TextView more = text("", 12, 0xFF1877F2, true);
        more.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(more, new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView collapse = text("", 12, 0xFF1877F2, true);
        collapse.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        root.addView(collapse, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        return root;
    }

    private static final class ArticleHolder extends RecyclerView.ViewHolder {
        ArticleHolder(@NonNull View itemView) { super(itemView); }
    }

    private static final class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(@NonNull View itemView) { super(itemView); }
    }

    private static final class CommentHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final AvatarView avatar;
        final TextView meta;
        final TextView body;
        final TextView voice;
        final LinearLayout imageContainer;
        final TextView like;
        final TextView reply;

        CommentHolder(@NonNull View itemView) {
            super(itemView);
            root = (LinearLayout) itemView;
            LinearLayout header = (LinearLayout) root.getChildAt(0);
            avatar = (AvatarView) header.getChildAt(0);
            meta = (TextView) header.getChildAt(1);
            body = (TextView) root.getChildAt(1);
            voice = (TextView) root.getChildAt(2);
            imageContainer = (LinearLayout) root.getChildAt(3);
            LinearLayout actions = (LinearLayout) root.getChildAt(4);
            like = (TextView) actions.getChildAt(0);
            reply = (TextView) actions.getChildAt(1);
        }
    }

    private static final class ToggleHolder extends RecyclerView.ViewHolder {
        final TextView more;
        final TextView collapse;

        ToggleHolder(@NonNull View itemView) {
            super(itemView);
            LinearLayout root = (LinearLayout) itemView;
            more = (TextView) root.getChildAt(0);
            collapse = (TextView) root.getChildAt(1);
        }
    }

    private static final class Row {
        final int type;
        final ForumApiClient.Comment comment;
        final ForumApiClient.Comment parent;
        final long parentId;
        final boolean reply;

        private Row(int type, ForumApiClient.Comment comment,
                    ForumApiClient.Comment parent, long parentId, boolean reply) {
            this.type = type;
            this.comment = comment;
            this.parent = parent;
            this.parentId = parentId;
            this.reply = reply;
        }

        static Row article() { return new Row(1, null, null, 0, false); }
        static Row comment(ForumApiClient.Comment comment, long parentId, boolean reply) {
            return new Row(2, comment, null, parentId, reply);
        }
        static Row toggle(ForumApiClient.Comment parent) {
            return new Row(3, null, parent, parent.id, false);
        }
        static Row empty() { return new Row(4, null, null, 0, false); }
        static Row loadMore() { return new Row(5, null, null, 0, false); }

        long stableId() {
            if (type == 1) return Long.MIN_VALUE + 1;
            if (type == 4) return Long.MIN_VALUE + 4;
            if (type == 5) return Long.MIN_VALUE + 5;
            if (type == 3) return -(parentId * 10L + 3L);
            long id = comment == null ? 0 : comment.id;
            return id * 10L + (reply ? 2L : 1L);
        }
    }

    private static final class VoicePayload {
        final String path;
        final int durationSec;
        final String waveform;

        private VoicePayload(String path, int durationSec, String waveform) {
            this.path = path;
            this.durationSec = durationSec;
            this.waveform = waveform;
        }

        @Nullable
        static VoicePayload parse(String content) {
            if (TextUtils.isEmpty(content) || !content.startsWith("voice:")) return null;
            String[] parts = content.substring("voice:".length()).split("\\|", -1);
            String path = parts.length > 0 ? parts[0] : "";
            int duration = 1;
            if (parts.length > 1) {
                try { duration = Math.max(1, Integer.parseInt(parts[1])); }
                catch (Throwable ignored) { }
            }
            String waveform = parts.length > 2 ? parts[2] : "";
            return new VoicePayload(path, duration, waveform);
        }

        String playPath() {
            if (TextUtils.isEmpty(path)) return "";
            if (path.startsWith("http://") || path.startsWith("https://")
                    || path.startsWith("file://") || path.startsWith("/")) return path;
            return WKApiConfig.getShowUrl(path);
        }
    }

    private TextView smallBadge(String label, int textColor, int backgroundColor) {
        TextView view = text(label, 10.5f, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(7), 0, dp(7), 0);
        view.setBackground(roundRect(backgroundColor, 9));
        return view;
    }

    private TextView htmlText(String html, float sizeSp) {
        TextView view = text("", sizeSp, isDark() ? 0xFFE8E9EB : 0xFF272A2F, false);
        view.setLineSpacing(dp(3), 1.08f);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setText(Html.fromHtml(safe(html).replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY));
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
        try {
            String uid = user == null ? "" : safe(user.uid);
            String forumId = user == null ? "" : safe(user.id);
            if (!TextUtils.isEmpty(uid)) {
                // Use the Talkami uid so this is the exact same AvatarView path as chat/feed.
                avatar.showAvatar(uid, WKChannelType.PERSONAL);
            } else if (user != null && (!TextUtils.isEmpty(user.smallAvatar)
                    || !TextUtils.isEmpty(user.avatar))) {
                String remote = TextUtils.isEmpty(user.smallAvatar) ? user.avatar : user.smallAvatar;
                avatar.showAvatarUrl(ForumApiClient.getInstance().resolveUrl(remote),
                        forumId, fallbackName, forumId);
            } else {
                avatar.showDefaultAvatar(fallbackName, TextUtils.isEmpty(uid) ? forumId : uid);
            }
            if (user != null && (!TextUtils.isEmpty(user.countryCode)
                    || !TextUtils.isEmpty(user.country))) {
                avatar.showFlag(TextUtils.isEmpty(user.countryCode) ? user.country : user.countryCode);
            }
        } catch (Throwable ignored) {
            String seed = user == null ? fallbackName
                    : (!TextUtils.isEmpty(user.uid) ? user.uid : safe(user.id));
            avatar.showDefaultAvatar(fallbackName, seed);
        }
    }

    private static String userName(ForumApiClient.User user) {
        return user == null || TextUtils.isEmpty(user.nickname) ? "用户" : user.nickname;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(isDark() ? 0xFF292B30 : 0xFFE7E9ED);
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

    private Drawable selectableBackground() {
        TypedValue out = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true);
        return AppCompatResources.getDrawable(this, out.resourceId);
    }

    private void setCompoundIcon(TextView view, int resId, int sizeDp, int color) {
        Drawable drawable = AppCompatResources.getDrawable(this, resId);
        if (drawable == null) return;
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, color);
        int size = dp(sizeDp);
        drawable.setBounds(0, 0, size, size);
        view.setCompoundDrawables(drawable, null, null, null);
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

    private static long normalizeTime(long value) {
        return value > 0 && value < 10_000_000_000L ? value * 1000L : value;
    }

    private static String formatDate(long value) {
        if (value <= 0) return "刚刚";
        long millis = normalizeTime(value);
        long diff = Math.max(0L, System.currentTimeMillis() - millis);
        if (diff < 60_000L) return "刚刚";
        if (diff < 3_600_000L) return diff / 60_000L + "分钟前";
        if (diff < 86_400_000L) return diff / 3_600_000L + "小时前";
        if (diff < 7 * 86_400_000L) return diff / 86_400_000L + "天前";
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(millis));
    }

    private static String formatVoiceTime(int sec) {
        int seconds = Math.max(1, sec);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static void deleteQuietly(@Nullable String path) {
        if (TextUtils.isEmpty(path)) return;
        try { new File(path).delete(); } catch (Throwable ignored) { }
    }

    private abstract class VoidCallback implements ForumApiClient.ResultCallback<Void> {
        abstract void success();
        @Override public final void onSuccess(@Nullable Void data) { success(); }
        @Override public final void onError(@NonNull String message) { finishTopicAction(message); }
    }
}
