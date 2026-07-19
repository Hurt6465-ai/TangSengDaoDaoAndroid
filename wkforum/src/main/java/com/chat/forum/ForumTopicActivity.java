package com.chat.forum;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.config.WKApiConfig;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.ui.components.AvatarView;
import com.chat.uikit.view.voice.AudioRecordManager;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Native topic detail with a single RecyclerView for smooth long discussions. */
public class ForumTopicActivity extends AppCompatActivity {
    private static final String EXTRA_TOPIC_ID = "topic_id";
    private static final String SEEN_PREF = "forum_topic_seen";
    private static final long MIN_RECORD_MS = 700L;
    private static final long MAX_RECORD_MS = 60_000L;
    private static final String COMMENT_SORT_HOT = "hot";
    private static final String COMMENT_SORT_ASC = "asc";
    private static final String COMMENT_SORT_DESC = "desc";
    private static final OkHttpClient VOICE_HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private String topicId = "";
    private RecyclerView recyclerView;
    private TextView stateView;
    private AppCompatImageView fastScrollButton;
    private boolean fastScrollToTop;
    private EditText commentInput;
    private AppCompatImageView composerAction;
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
    private boolean tailCommentsPending;
    private boolean commentsTailWindow;
    private String commentSort = COMMENT_SORT_ASC;
    private boolean sending;
    private boolean topicActionBusy;
    private long replyParentId;
    private long replyQuoteId;
    private final ForumApiClient.RequestScope requestScope = new ForumApiClient.RequestScope();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideFastScrollButton = () -> {
        if (fastScrollButton == null) return;
        fastScrollButton.animate().cancel();
        fastScrollButton.animate().alpha(0f).setDuration(140L).withEndAction(() -> {
            if (fastScrollButton != null && fastScrollButton.getAlpha() == 0f) {
                fastScrollButton.setVisibility(View.GONE);
            }
        }).start();
    };
    private boolean recording;
    private boolean recordCancel;
    private long recordStartTime;
    private float recordStartX;
    private float recordStartY;
    private long recordReplyParentId;
    private long recordReplyQuoteId;
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
    private Call voiceDownloadCall;
    private String playingVoiceKey = "";
    private VoicePayload playingVoicePayload;
    private VoiceBubbleView playingVoiceView;
    private AudioManager audioManager;
    private boolean voiceAudioFocusHeld;
    private boolean resumeVoiceAfterFocusGain;
    private long voiceUploadGeneration;
    private AlertDialog voiceRetryDialog;
    private AlertDialog voiceConfirmDialog;
    private String confirmVoicePath = "";
    private int confirmVoiceSeconds;
    private String confirmVoiceWaveform = "";
    private long confirmVoiceReplyParentId;
    private long confirmVoiceReplyQuoteId;
    private String activeVoiceLocalPath = "";
    private String pendingVoicePath = "";
    private int pendingVoiceSeconds;
    private String pendingVoiceWaveform = "";
    private String pendingVoiceCommentContent = "";
    private long pendingVoiceReplyParentId;
    private long pendingVoiceReplyQuoteId;
    private final Runnable voiceProgressTick = new Runnable() {
        @Override
        public void run() {
            updatePlayingVoiceUi();
            if (voicePlayer != null && isVoicePlaying()) {
                mainHandler.postDelayed(this, 250L);
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener voiceFocusListener = focusChange -> {
        MediaPlayer player = voicePlayer;
        if (player == null) return;
        try {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                resumeVoiceAfterFocusGain = false;
                stopVoicePlayback();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                resumeVoiceAfterFocusGain = player.isPlaying();
                if (player.isPlaying()) player.pause();
                updatePlayingVoiceUi();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                player.setVolume(0.25f, 0.25f);
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                player.setVolume(1f, 1f);
                if (resumeVoiceAfterFocusGain && !player.isPlaying()) {
                    resumeVoiceAfterFocusGain = false;
                    player.start();
                    mainHandler.removeCallbacks(voiceProgressTick);
                    mainHandler.post(voiceProgressTick);
                }
                updatePlayingVoiceUi();
            }
        } catch (Throwable ignored) { }
    };

    private final ActivityResultLauncher<String> audioPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    Toast.makeText(this, "按住录音，上滑取消，松开后确认发送", Toast.LENGTH_SHORT).show();
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
        body.setClickable(true);
        body.setOnClickListener(v -> {
            if (replyParentId > 0 || replyQuoteId > 0) clearReplyTarget();
        });
        recyclerView = new RecyclerView(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, 0, 0, dp(14));
        recyclerView.setItemViewCacheSize(14);
        recyclerView.setHasFixedSize(false);
        adapter = new TopicDetailAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && !loadingComments && commentsHasMore) {
                    int last = layoutManager.findLastVisibleItemPosition();
                    if (last >= adapter.getItemCount() - 4) loadComments(false);
                }
                updateFastScrollButton();
            }
        });
        body.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stateView = text("正在加载…", 14, dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
        stateView.setGravity(Gravity.CENTER);
        stateView.setOnClickListener(v -> clearReplyTarget());
        body.addView(stateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fastScrollButton = new AppCompatImageView(this);
        fastScrollButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        fastScrollButton.setContentDescription("快速到底部");
        fastScrollButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        fastScrollButton.setBackground(roundRect(dark ? 0xFF2A2D32 : 0xFFF4F5F7, 16));
        setImageIcon(fastScrollButton, R.drawable.ic_forum_arrow_down, 17,
                dark ? 0xFFD3D6DA : 0xFF5D646C);
        fastScrollButton.setElevation(dp(2));
        fastScrollButton.setVisibility(View.GONE);
        fastScrollButton.setOnClickListener(v -> {
            if (adapter == null || adapter.getItemCount() == 0) return;
            if (fastScrollToTop) {
                if (commentsTailWindow && COMMENT_SORT_ASC.equals(commentSort)) {
                    commentsTailWindow = false;
                    loadComments(true);
                } else {
                    recyclerView.stopScroll();
                    recyclerView.scrollToPosition(0);
                }
            } else if (COMMENT_SORT_ASC.equals(commentSort) && commentsHasMore) {
                loadTailComments();
            } else {
                recyclerView.stopScroll();
                recyclerView.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
            }
            recyclerView.post(this::updateFastScrollButton);
        });
        FrameLayout.LayoutParams fastParams = new FrameLayout.LayoutParams(dp(34), dp(34),
                Gravity.END | Gravity.BOTTOM);
        fastParams.setMargins(0, 0, dp(10), dp(10));
        body.addView(fastScrollButton, fastParams);

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildComposer(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        cleanupVoiceCache();
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
        recordHint.setPadding(dp(12), dp(8), dp(12), dp(8));
        recordHint.setBackground(roundRect(dark ? 0xFF292C31 : 0xFFF0F2F5, 14));
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
        commentInput.setPadding(dp(14), dp(9), dp(14), dp(9));
        commentInput.setBackground(roundRect(dark ? 0xFF24262B : 0xFFF1F3F5, 21));
        row.addView(commentInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        composerAction = new AppCompatImageView(this);
        composerAction.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        composerAction.setPadding(dp(11), dp(11), dp(11), dp(11));
        composerAction.setMinimumWidth(0);
        composerAction.setMinimumHeight(0);
        composerAction.setOnClickListener(v -> {
            if (sending) return;
            if (!TextUtils.isEmpty(commentInput.getText().toString().trim())) sendComment();
            else Toast.makeText(this, "按住录音，上滑取消，松开后确认发送", Toast.LENGTH_SHORT).show();
        });
        composerAction.setOnTouchListener(this::handleComposerTouch);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(48), dp(48));
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
                recordStartX = event.getRawX();
                recordStartY = event.getRawY();
                startRecord();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (recording) {
                    if (recordStartY - event.getRawY() >= dp(58)
                            || Math.abs(event.getRawX() - recordStartX) >= dp(96)) {
                        recordCancel = true;
                    }
                    long elapsed = Math.max(0L, System.currentTimeMillis() - recordStartTime);
                    updateRecordHint((int) Math.ceil(elapsed / 1000.0));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (recording) {
                    boolean cancel = recordCancel
                            || recordStartY - event.getRawY() >= dp(58)
                            || Math.abs(event.getRawX() - recordStartX) >= dp(96);
                    finishRecord(cancel);
                }
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
            recordReplyParentId = replyParentId;
            recordReplyQuoteId = replyQuoteId;
            AudioRecordManager.getInstance().init(recordPath);
            AudioRecordManager.getInstance().startRecord();
            recording = true;
            composerAction.setSelected(true);
            composerAction.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            recordHint.setVisibility(View.VISIBLE);
            updateRecordHint(0);
            mainHandler.post(recordTick);
        } catch (Throwable error) {
            try { AudioRecordManager.getInstance().cancelRecord(); } catch (Throwable ignored) { }
            deleteQuietly(recordPath);
            recordPath = "";
            recording = false;
            composerAction.setSelected(false);
            recordHint.setVisibility(View.GONE);
            Toast.makeText(this, "录音启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRecordHint(int seconds) {
        if (!recording || recordHint == null) return;
        String time = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
        recordHint.setText((recordCancel ? "松开取消" : "松开完成，上滑取消") + "  " + time);
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
        showVoiceSendConfirmation(localPath, seconds, waveformText,
                recordReplyParentId, recordReplyQuoteId);
    }

    private void showVoiceSendConfirmation(String localPath, int seconds, String waveform,
                                               long targetParentId, long targetQuoteId) {
        if (TextUtils.isEmpty(localPath) || !new File(localPath).exists()) {
            deleteQuietly(localPath);
            Toast.makeText(this, "录音文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        dismissVoiceConfirmDialog(true);
        confirmVoicePath = localPath;
        confirmVoiceSeconds = seconds;
        confirmVoiceWaveform = waveform == null ? "" : waveform;
        confirmVoiceReplyParentId = targetParentId;
        confirmVoiceReplyQuoteId = targetQuoteId;
        voiceConfirmDialog = new AlertDialog.Builder(this)
                .setTitle("发送语音评论？")
                .setMessage(seconds + " 秒录音。确认后才会发送，取消将直接删除。")
                .setNegativeButton("取消", (dialog, which) -> discardVoiceConfirmation())
                .setPositiveButton("发送", (dialog, which) -> sendConfirmedVoice())
                .setOnCancelListener(dialog -> discardVoiceConfirmation())
                .create();
        voiceConfirmDialog.setOnDismissListener(dialog -> {
            if (voiceConfirmDialog == dialog) voiceConfirmDialog = null;
        });
        voiceConfirmDialog.show();
    }

    private void sendConfirmedVoice() {
        String path = confirmVoicePath;
        int seconds = confirmVoiceSeconds;
        String waveform = confirmVoiceWaveform;
        long targetParentId = confirmVoiceReplyParentId;
        long targetQuoteId = confirmVoiceReplyQuoteId;
        clearVoiceConfirmation(false);
        if (TextUtils.isEmpty(path) || !new File(path).exists()) {
            Toast.makeText(this, "录音文件已失效，请重新录制", Toast.LENGTH_SHORT).show();
            return;
        }
        uploadAndSendVoice(path, seconds, waveform, targetParentId, targetQuoteId);
    }

    private void discardVoiceConfirmation() {
        clearVoiceConfirmation(true);
    }

    private void clearVoiceConfirmation(boolean deleteFile) {
        String path = confirmVoicePath;
        confirmVoicePath = "";
        confirmVoiceSeconds = 0;
        confirmVoiceWaveform = "";
        confirmVoiceReplyParentId = 0L;
        confirmVoiceReplyQuoteId = 0L;
        if (deleteFile) deleteQuietly(path);
    }

    private void dismissVoiceConfirmDialog(boolean discard) {
        AlertDialog dialog = voiceConfirmDialog;
        voiceConfirmDialog = null;
        if (dialog != null) {
            dialog.setOnCancelListener(null);
            dialog.setOnDismissListener(null);
            if (dialog.isShowing()) dialog.dismiss();
        }
        if (discard) discardVoiceConfirmation();
    }

    private void uploadAndSendVoice(String localPath, int seconds, String waveform,
                                    long targetParentId, long targetQuoteId) {
        File file = new File(localPath);
        if (!file.exists() || file.length() <= 0) {
            failVoiceUpload(localPath, seconds, waveform, targetParentId, targetQuoteId,
                    "录音文件不存在");
            return;
        }
        dismissVoiceRetryDialog(false);
        activeVoiceLocalPath = localPath;
        final long generation = ++voiceUploadGeneration;
        setSending(true);
        ForumApiClient.getInstance().getVoiceUploadTarget(file, requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.VoiceUploadTarget>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.VoiceUploadTarget target) {
                        if (!isVoiceUploadActive(generation, localPath)) return;
                        if (target == null || TextUtils.isEmpty(target.uploadUrl)) {
                            failVoiceUpload(localPath, seconds, waveform, targetParentId,
                                    targetQuoteId, "无法获取语音上传地址");
                            return;
                        }
                        String tag = "forum_voice_" + UUID.randomUUID();
                        WKUploader.getInstance().upload(target.uploadUrl, localPath, tag,
                                new WKUploader.IUploadBack() {
                                    @Override
                                    public void onSuccess(String uploadedPath) {
                                        runOnUiThread(() -> {
                                            if (!isVoiceUploadActive(generation, localPath)) {
                                                deleteQuietly(localPath);
                                                clearActiveVoicePath(localPath);
                                                return;
                                            }
                                            String remote = !TextUtils.isEmpty(target.publicUrl)
                                                    ? target.publicUrl
                                                    : (!TextUtils.isEmpty(uploadedPath)
                                                    ? uploadedPath : target.path);
                                            String content = "voice:" + normalizeVoicePath(remote)
                                                    + "|" + seconds + "|" + waveform;
                                            sendCommentContent(content, localPath, targetParentId,
                                                    targetQuoteId);
                                        });
                                    }

                                    @Override
                                    public void onError() {
                                        runOnUiThread(() -> {
                                            if (isVoiceUploadActive(generation, localPath)) {
                                                failVoiceUpload(localPath, seconds, waveform,
                                                        targetParentId, targetQuoteId, "语音上传失败");
                                            } else {
                                                deleteQuietly(localPath);
                                                clearActiveVoicePath(localPath);
                                            }
                                        });
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (isVoiceUploadActive(generation, localPath)) {
                            failVoiceUpload(localPath, seconds, waveform, targetParentId,
                                    targetQuoteId, message);
                        }
                    }
                });
    }

    private void clearActiveVoicePath(@Nullable String path) {
        if (!TextUtils.isEmpty(path) && TextUtils.equals(activeVoiceLocalPath, path)) {
            activeVoiceLocalPath = "";
        }
    }

    private boolean isVoiceUploadActive(long generation, String localPath) {
        return !isDead() && generation == voiceUploadGeneration
                && !TextUtils.isEmpty(localPath) && new File(localPath).exists();
    }

    private void failVoiceUpload(String localPath, int seconds, String waveform,
                                 long targetParentId, long targetQuoteId, String message) {
        if (isDead()) {
            deleteQuietly(localPath);
            return;
        }
        setSending(false);
        clearActiveVoicePath(localPath);
        dismissVoiceRetryDialog(true);
        pendingVoicePath = localPath == null ? "" : localPath;
        pendingVoiceSeconds = seconds;
        pendingVoiceWaveform = waveform == null ? "" : waveform;
        pendingVoiceCommentContent = "";
        pendingVoiceReplyParentId = targetParentId;
        pendingVoiceReplyQuoteId = targetQuoteId;
        voiceRetryDialog = new AlertDialog.Builder(this)
                .setTitle("语音发送失败")
                .setMessage(TextUtils.isEmpty(message) ? "请检查网络后重试" : message)
                .setNegativeButton("删除", (dialog, which) -> discardPendingVoice())
                .setPositiveButton("重试", (dialog, which) -> retryPendingVoice())
                .setOnCancelListener(dialog -> discardPendingVoice())
                .create();
        voiceRetryDialog.setOnDismissListener(dialog -> voiceRetryDialog = null);
        voiceRetryDialog.show();
    }

    private void retryPendingVoice() {
        String path = pendingVoicePath;
        int seconds = pendingVoiceSeconds;
        String waveform = pendingVoiceWaveform;
        long targetParentId = pendingVoiceReplyParentId;
        long targetQuoteId = pendingVoiceReplyQuoteId;
        pendingVoicePath = "";
        pendingVoiceSeconds = 0;
        pendingVoiceWaveform = "";
        pendingVoiceCommentContent = "";
        pendingVoiceReplyParentId = 0L;
        pendingVoiceReplyQuoteId = 0L;
        if (TextUtils.isEmpty(path) || !new File(path).exists()) {
            Toast.makeText(this, "录音文件已失效，请重新录制", Toast.LENGTH_SHORT).show();
            return;
        }
        uploadAndSendVoice(path, seconds, waveform, targetParentId, targetQuoteId);
    }

    private void discardPendingVoice() {
        String path = pendingVoicePath;
        pendingVoicePath = "";
        pendingVoiceSeconds = 0;
        pendingVoiceWaveform = "";
        pendingVoiceCommentContent = "";
        pendingVoiceReplyParentId = 0L;
        pendingVoiceReplyQuoteId = 0L;
        deleteQuietly(path);
    }

    private void dismissVoiceRetryDialog(boolean discard) {
        AlertDialog dialog = voiceRetryDialog;
        voiceRetryDialog = null;
        if (dialog != null) {
            dialog.setOnCancelListener(null);
            dialog.setOnDismissListener(null);
            if (dialog.isShowing()) dialog.dismiss();
        }
        if (discard) discardPendingVoice();
    }

    private void showVoiceCommentRetry(String content, long targetParentId, long targetQuoteId,
                                       String message) {
        if (isDead()) return;
        setSending(false);
        dismissVoiceRetryDialog(true);
        pendingVoiceCommentContent = content == null ? "" : content;
        pendingVoiceReplyParentId = targetParentId;
        pendingVoiceReplyQuoteId = targetQuoteId;
        voiceRetryDialog = new AlertDialog.Builder(this)
                .setTitle("语音评论发送失败")
                .setMessage(TextUtils.isEmpty(message) ? "请检查网络后重试" : message)
                .setNegativeButton("取消", (dialog, which) -> discardPendingVoice())
                .setPositiveButton("重试", (dialog, which) -> retryPendingVoiceComment())
                .setOnCancelListener(dialog -> discardPendingVoice())
                .create();
        voiceRetryDialog.setOnDismissListener(dialog -> voiceRetryDialog = null);
        voiceRetryDialog.show();
    }

    private void retryPendingVoiceComment() {
        String content = pendingVoiceCommentContent;
        long targetParentId = pendingVoiceReplyParentId;
        long targetQuoteId = pendingVoiceReplyQuoteId;
        pendingVoiceCommentContent = "";
        pendingVoiceReplyParentId = 0L;
        pendingVoiceReplyQuoteId = 0L;
        if (TextUtils.isEmpty(content)) return;
        setSending(true);
        sendCommentContent(content, null, targetParentId, targetQuoteId);
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
        composerAction.setBackground(roundRect(hasText ? 0xFF1877F2
                : (isDark() ? 0xFF2A2D33 : 0xFFF0F2F5), 24));
        composerAction.setContentDescription(hasText ? "发送评论" : "按住录音，松开后确认发送");
        setImageIcon(composerAction, hasText ? R.drawable.ic_forum_send
                        : R.drawable.ic_forum_mic, hasText ? 20 : 25,
                hasText ? Color.WHITE : (isDark() ? 0xFFE4E6EA : 0xFF4D555E));
    }

    private void loadTopic() {
        ForumApiClient.getInstance().ensureSession(this, requestScope,
                new ForumApiClient.ResultCallback<String>() {
            @Override public void onSuccess(@Nullable String data) { requestTopic(); }
            @Override public void onError(@NonNull String message) { requestTopic(); }
        });
    }

    private void requestTopic() {
        ForumApiClient.getInstance().getTopic(topicId, requestScope,
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

    private void loadComments(boolean reset) {
        if (loadingComments) {
            if (reset) refreshCommentsPending = true;
            return;
        }
        if (reset) {
            refreshCommentsPending = false;
            tailCommentsPending = false;
            commentsTailWindow = false;
            commentsCursor = "";
            commentsHasMore = false;
            comments.clear();
            adapter.rebuild();
        }
        loadingComments = true;
        adapter.rebuild();
        ForumApiClient.getInstance().getComments(topicId, commentsCursor, commentSort, requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        loadingComments = false;
                        if (isDead()) return;
                        if (page != null && page.results != null) appendUnique(comments, page.results);
                        String nextCursor = page == null ? "" : safe(page.cursor);
                        if (!TextUtils.isEmpty(nextCursor)) commentsCursor = nextCursor;
                        commentsHasMore = page != null && page.hasMore
                                && !TextUtils.isEmpty(nextCursor);
                        adapter.rebuild();
                        recyclerView.post(ForumTopicActivity.this::updateFastScrollButton);
                        drainPendingCommentLoad();
                    }

                    @Override public void onError(@NonNull String message) {
                        loadingComments = false;
                        adapter.rebuild();
                        Toast.makeText(ForumTopicActivity.this, message, Toast.LENGTH_LONG).show();
                        drainPendingCommentLoad();
                    }
                });
    }

    private void setCommentSort(@NonNull String sort) {
        if (loadingComments) return;
        if (TextUtils.equals(commentSort, sort) && !commentsTailWindow) return;
        commentSort = sort;
        commentsTailWindow = false;
        expandedReplies.clear();
        stopVoicePlayback();
        loadComments(true);
        recyclerView.post(() -> recyclerView.scrollToPosition(0));
    }

    private void loadTailComments() {
        if (loadingComments) {
            tailCommentsPending = true;
            return;
        }
        tailCommentsPending = false;
        loadingComments = true;
        ForumApiClient.getInstance().getComments(topicId, "", COMMENT_SORT_DESC, requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Comment>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Comment> page) {
                        loadingComments = false;
                        if (isDead()) return;
                        List<ForumApiClient.Comment> latest = page == null || page.results == null
                                ? new ArrayList<>() : new ArrayList<>(page.results);
                        java.util.Collections.reverse(latest);
                        comments.clear();
                        comments.addAll(latest);
                        commentsCursor = "";
                        commentsHasMore = false;
                        commentsTailWindow = true;
                        expandedReplies.clear();
                        adapter.rebuild();
                        recyclerView.post(() -> {
                            recyclerView.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                            updateFastScrollButton();
                        });
                        drainPendingCommentLoad();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        loadingComments = false;
                        Toast.makeText(ForumTopicActivity.this, message, Toast.LENGTH_LONG).show();
                        drainPendingCommentLoad();
                    }
                });
    }

    private void drainPendingCommentLoad() {
        if (isDead() || loadingComments) return;
        if (tailCommentsPending) {
            tailCommentsPending = false;
            refreshCommentsPending = false;
            loadTailComments();
        } else if (refreshCommentsPending) {
            refreshCommentsPending = false;
            loadComments(true);
        }
    }

    private void updateFastScrollButton() {
        if (fastScrollButton == null || recyclerView == null || adapter == null
                || adapter.getItemCount() <= 1) {
            hideFastScrollImmediately();
            return;
        }
        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        LinearLayoutManager linear = (LinearLayoutManager) manager;
        int first = linear.findFirstVisibleItemPosition();
        boolean canUp = recyclerView.canScrollVertically(-1);
        boolean canDown = recyclerView.canScrollVertically(1);
        if (!canUp && !canDown) {
            hideFastScrollImmediately();
            return;
        }
        fastScrollToTop = first > 3;
        setImageIcon(fastScrollButton,
                fastScrollToTop ? R.drawable.ic_forum_arrow_up : R.drawable.ic_forum_arrow_down,
                17, isDark() ? 0xFFD3D6DA : 0xFF5D646C);
        fastScrollButton.setContentDescription(fastScrollToTop ? "快速回到顶部" : "快速回到底部");
        mainHandler.removeCallbacks(hideFastScrollButton);
        fastScrollButton.animate().cancel();
        fastScrollButton.setAlpha(1f);
        fastScrollButton.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(hideFastScrollButton, 1100L);
    }

    private void hideFastScrollImmediately() {
        mainHandler.removeCallbacks(hideFastScrollButton);
        if (fastScrollButton == null) return;
        fastScrollButton.animate().cancel();
        fastScrollButton.setAlpha(0f);
        fastScrollButton.setVisibility(View.GONE);
    }

    private void loadMoreReplies(ForumApiClient.Comment parent) {
        if (parent == null || parent.id <= 0 || loadingComments) return;
        loadingComments = true;
        adapter.rebuild();
        String replyCursor = parent.replies == null ? "" : safe(parent.replies.cursor);
        ForumApiClient.getInstance().getReplies(parent.id, replyCursor, requestScope,
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
                        String nextCursor = page == null ? "" : safe(page.cursor);
                        if (!TextUtils.isEmpty(nextCursor)) parent.replies.cursor = nextCursor;
                        parent.replies.hasMore = page != null && page.hasMore
                                && !TextUtils.isEmpty(nextCursor);
                        expandedReplies.add(parent.id);
                        adapter.rebuild();
                        drainPendingCommentLoad();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        loadingComments = false;
                        adapter.rebuild();
                        Toast.makeText(ForumTopicActivity.this, message, Toast.LENGTH_LONG).show();
                        drainPendingCommentLoad();
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
        String topicUrl = ForumLinkRouter.topicWebUrl(topic.id);
        labels.add("分享");
        actions.add(() -> shareText(topic.title,
                safe(topic.title) + "\n" + safe(topic.content) + "\n" + topicUrl));
        labels.add("复制帖子链接");
        actions.add(() -> copyTopicText("帖子链接", topicUrl));
        labels.add("复制引用格式");
        actions.add(() -> copyTopicText("帖子引用",
                ForumLinkRouter.markdownReference(topic.title, topicUrl)));
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
        showCompactActionMenu(labels, actions);
    }

    private void copyTopicText(String label, String value) {
        boolean copied = ForumLinkRouter.copyToClipboard(this, label, value);
        Toast.makeText(this, copied ? "已复制" : "复制失败", Toast.LENGTH_SHORT).show();
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

    private void showCommentMenu(ForumApiClient.Comment comment, long parentId, String author) {
        if (comment == null || topicActionBusy) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(comment.liked ? "取消点赞" : "点赞");
        actions.add(() -> changeCommentLike(comment));
        if (!isOwnComment(comment)) {
            labels.add("回复");
            actions.add(() -> setReplyTarget(parentId, comment.id, author));
        }
        if (canAcceptAnswer(comment, parentId)) {
            labels.add("采纳为答案");
            actions.add(() -> confirmAcceptAnswer(comment));
        }
        labels.add("举报");
        actions.add(() -> showCommentReportDialog(comment));
        labels.add("分享");
        actions.add(() -> shareText("评论", userName(comment.user) + "：" + safe(comment.content)));
        if (canDeleteComment(comment)) {
            labels.add("删除");
            actions.add(() -> confirmDeleteComment(comment));
        }
        showCompactActionMenu(labels, actions);
    }

    private boolean isOwnComment(@Nullable ForumApiClient.Comment comment) {
        return comment != null && comment.user != null
                && TextUtils.equals(ForumApiClient.getInstance().getCurrentForumUserId(), comment.user.id);
    }

    private boolean canAcceptAnswer(@Nullable ForumApiClient.Comment comment, long parentId) {
        ForumApiClient.Topic topic = currentTopic;
        if (topic == null || comment == null || topic.type != 2 || comment.id <= 0) return false;
        if (parentId != comment.id) return false;
        if (!TextUtils.isEmpty(comment.entityType)
                && !"topic".equalsIgnoreCase(safe(comment.entityType))) return false;
        if (topic.acceptedCommentId > 0 || "solved".equalsIgnoreCase(safe(topic.qaStatus))) return false;
        ForumApiClient client = ForumApiClient.getInstance();
        boolean owner = topic.user != null && TextUtils.equals(
                client.getCurrentForumUserId(), topic.user.id);
        return owner || client.isForumManager();
    }

    private void confirmAcceptAnswer(@NonNull ForumApiClient.Comment comment) {
        new AlertDialog.Builder(this)
                .setTitle("采纳答案")
                .setMessage("采纳后该问答会标记为已解决。确定选择这条回答吗？")
                .setPositiveButton("采纳", (dialog, which) -> acceptAnswer(comment))
                .setNegativeButton("取消", null)
                .show();
    }

    private void acceptAnswer(@NonNull ForumApiClient.Comment comment) {
        ForumApiClient.Topic topic = currentTopic;
        if (topic == null || topicActionBusy) return;
        runAuthenticatedAction(() -> ForumApiClient.getInstance().acceptAnswer(topic.id, comment.id,
                new VoidCallback() {
                    @Override public void success() {
                        topic.acceptedCommentId = comment.id;
                        topic.qaStatus = "solved";
                        completeAction("已采纳答案");
                        adapter.rebuild();
                    }
                }));
    }

    private void showCompactActionMenu(List<String> labels, List<Runnable> actions) {
        if (labels == null || labels.isEmpty() || labels.size() != actions.size()) return;
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(6), dp(5), dp(6), dp(5));
        GradientDrawable background = roundRect(isDark() ? 0xFF292B30 : Color.WHITE, 14);
        background.setStroke(dp(0.7f), isDark() ? 0xFF3A3D43 : 0xFFE4E6E9);
        panel.setBackground(background);
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            TextView item = text(labels.get(i), 14,
                    isDark() ? 0xFFE6E8EB : 0xFF30343A, false);
            item.setGravity(Gravity.CENTER);
            item.setBackground(selectableBackground());
            item.setOnClickListener(v -> {
                dialog.dismiss();
                actions.get(index).run();
            });
            panel.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        }
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = dp(126);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(dp(126), WindowManager.LayoutParams.WRAP_CONTENT);
        }
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
                        if (currentTopic != null) {
                            currentTopic.commentCount = Math.max(0, currentTopic.commentCount - 1);
                        }
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
        replyHint.setText("正在回复 " + safe(name) + " · 点击空白处取消");
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
        sendCommentContent(content, null, replyParentId, replyQuoteId);
    }

    private void sendCommentContent(String content, @Nullable String localVoicePath,
                                    long targetParentId, long targetQuoteId) {
        String entityType = targetParentId > 0 ? "comment" : "topic";
        String entityId = targetParentId > 0 ? String.valueOf(targetParentId) : topicId;
        ForumApiClient.getInstance().createComment(entityType, entityId, content, targetQuoteId,
                new ArrayList<>(), new ForumApiClient.ResultCallback<ForumApiClient.Comment>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Comment data) {
                        deleteQuietly(localVoicePath);
                        clearActiveVoicePath(localVoicePath);
                        if (isDead()) return;
                        commentInput.setText("");
                        if (replyParentId == targetParentId && replyQuoteId == targetQuoteId) {
                            clearReplyTarget();
                        }
                        setSending(false);
                        setResult(RESULT_OK);
                        if (currentTopic != null) currentTopic.commentCount = Math.max(0, currentTopic.commentCount + 1);
                        if (COMMENT_SORT_ASC.equals(commentSort)) {
                            loadTailComments();
                        } else {
                            requestTopic();
                        }
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        deleteQuietly(localVoicePath);
                        clearActiveVoicePath(localVoicePath);
                        if (content.startsWith("voice:")) {
                            showVoiceCommentRetry(content, targetParentId, targetQuoteId, message);
                        } else {
                            failSend(message);
                        }
                    }
                });
    }

    private void runAuthenticatedAction(Runnable action) {
        if (topicActionBusy) return;
        topicActionBusy = true;
        ForumApiClient.getInstance().ensureSession(this, requestScope,
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
            composerAction.setImageDrawable(null);
            composerAction.setBackground(roundRect(0xFF9ABEF0, 24));
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
        if (COMMENT_SORT_ASC.equals(commentSort)) {
            recyclerView.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
        }
        commentInput.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(commentInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void playVoice(VoicePayload payload, String key, VoiceBubbleView view) {
        if (payload == null || TextUtils.isEmpty(payload.playPath())) return;
        view.setTag(key);
        if (voicePlayer != null && TextUtils.equals(playingVoiceKey, key)) {
            try {
                if (voicePlayer.isPlaying()) {
                    voicePlayer.pause();
                    resumeVoiceAfterFocusGain = false;
                    abandonVoiceAudioFocus();
                } else if (requestVoiceAudioFocus()) {
                    voicePlayer.setVolume(1f, 1f);
                    voicePlayer.start();
                    mainHandler.removeCallbacks(voiceProgressTick);
                    mainHandler.post(voiceProgressTick);
                }
                playingVoiceView = view;
                updatePlayingVoiceUi();
            } catch (Throwable ignored) {
                stopVoicePlayback();
            }
            return;
        }
        stopVoicePlayback();
        playingVoiceKey = key;
        playingVoicePayload = payload;
        playingVoiceView = view;
        setVoiceBubbleUi(view, payload, payload.durationSec, true, 0f);

        String source = payload.playPath();
        if (source.startsWith("file://")) {
            prepareVoiceFile(new File(Uri.parse(source).getPath()));
            return;
        }
        if (source.startsWith("/")) {
            prepareVoiceFile(new File(source));
            return;
        }
        File cacheFile = voiceCacheFile(source);
        if (cacheFile.exists() && cacheFile.length() > 0) {
            prepareVoiceFile(cacheFile);
            return;
        }
        downloadVoice(source, cacheFile);
    }

    private void stopVoicePlayback() {
        mainHandler.removeCallbacks(voiceProgressTick);
        if (voiceDownloadCall != null) {
            voiceDownloadCall.cancel();
            voiceDownloadCall = null;
        }
        if (voicePlayer != null) {
            try { voicePlayer.stop(); } catch (Throwable ignored) { }
            try { voicePlayer.release(); } catch (Throwable ignored) { }
            voicePlayer = null;
        }
        abandonVoiceAudioFocus();
        if (playingVoiceView != null && playingVoicePayload != null
                && TextUtils.equals(String.valueOf(playingVoiceView.getTag()), playingVoiceKey)) {
            setVoiceBubbleUi(playingVoiceView, playingVoicePayload, playingVoicePayload.durationSec, false, 0f);
        }
        playingVoiceKey = "";
        playingVoicePayload = null;
        playingVoiceView = null;
    }


    private void prepareVoiceFile(@Nullable File file) {
        if (file == null || !file.exists() || file.length() <= 0 || TextUtils.isEmpty(playingVoiceKey)) {
            voicePlaybackError();
            return;
        }
        final String expectedKey = playingVoiceKey;
        MediaPlayer player = new MediaPlayer();
        voicePlayer = player;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            } else {
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                if (isDead() || !TextUtils.equals(expectedKey, playingVoiceKey)) {
                    try { mp.release(); } catch (Throwable ignored) { }
                    return;
                }
                if (!requestVoiceAudioFocus()) {
                    voicePlaybackError("其他应用正在占用音频");
                    return;
                }
                mp.setVolume(1f, 1f);
                mp.start();
                mainHandler.removeCallbacks(voiceProgressTick);
                mainHandler.post(voiceProgressTick);
            });
            player.setOnCompletionListener(mp -> stopVoicePlayback());
            player.setOnErrorListener((mp, what, extra) -> {
                voicePlaybackError();
                return true;
            });
            player.prepareAsync();
        } catch (Throwable error) {
            voicePlaybackError();
        }
    }

    private void downloadVoice(String url, File target) {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        final String expectedKey = playingVoiceKey;
        Request request;
        try {
            request = new Request.Builder().url(url).get().build();
        } catch (Throwable error) {
            voicePlaybackError();
            return;
        }
        Call call = VOICE_HTTP.newCall(request);
        voiceDownloadCall = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call failedCall, @NonNull IOException error) {
                runOnUiThread(() -> {
                    if (voiceDownloadCall == failedCall) voiceDownloadCall = null;
                    if (!failedCall.isCanceled() && TextUtils.equals(expectedKey, playingVoiceKey)) {
                        voicePlaybackError();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call responseCall, @NonNull Response response) {
                File part = new File(target.getAbsolutePath() + ".part");
                boolean ok = false;
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("HTTP " + response.code());
                    }
                    try (InputStream input = response.body().byteStream();
                         FileOutputStream output = new FileOutputStream(part)) {
                        byte[] buffer = new byte[16 * 1024];
                        int read;
                        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                        output.flush();
                    }
                    if (part.length() <= 0) throw new IOException("empty voice");
                    if (target.exists() && !target.delete()) {
                        throw new IOException("cannot replace voice cache");
                    }
                    ok = part.renameTo(target);
                    if (!ok) {
                        try (InputStream input = new java.io.FileInputStream(part);
                             FileOutputStream output = new FileOutputStream(target)) {
                            byte[] buffer = new byte[16 * 1024];
                            int read;
                            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                            output.flush();
                        }
                        ok = target.length() > 0;
                    }
                } catch (Throwable ignored) {
                    ok = false;
                    if (target.exists() && target.length() <= 0) target.delete();
                } finally {
                    response.close();
                    if (part.exists()) part.delete();
                }
                final boolean success = ok;
                runOnUiThread(() -> {
                    if (voiceDownloadCall == responseCall) voiceDownloadCall = null;
                    if (!TextUtils.equals(expectedKey, playingVoiceKey)) return;
                    if (success) prepareVoiceFile(target);
                    else voicePlaybackError();
                });
            }
        });
    }

    private void updatePlayingVoiceUi() {
        if (playingVoiceView == null || playingVoicePayload == null
                || !TextUtils.equals(String.valueOf(playingVoiceView.getTag()), playingVoiceKey)) return;
        int remain = playingVoicePayload.durationSec;
        boolean active = false;
        float progress = 0f;
        if (voicePlayer != null) {
            try {
                active = voicePlayer.isPlaying();
                int duration = voicePlayer.getDuration();
                int position = voicePlayer.getCurrentPosition();
                if (duration > 0) {
                    remain = Math.max(0, (int) Math.ceil((duration - position) / 1000.0));
                    progress = Math.max(0f, Math.min(1f, position / (float) duration));
                }
            } catch (Throwable ignored) { }
        }
        setVoiceBubbleUi(playingVoiceView, playingVoicePayload, remain, active, progress);
    }

    private void setVoiceBubbleUi(VoiceBubbleView view, VoicePayload payload, int seconds,
                                  boolean active, float progress) {
        if (view == null || payload == null) return;
        view.bind(payload, Math.max(0, seconds), active, progress);
    }

    private boolean requestVoiceAudioFocus() {
        if (voiceAudioFocusHeld) return true;
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) return true;
        int result;
        try {
            result = audioManager.requestAudioFocus(voiceFocusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        } catch (Throwable ignored) {
            return true;
        }
        voiceAudioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return voiceAudioFocusHeld;
    }

    private void abandonVoiceAudioFocus() {
        resumeVoiceAfterFocusGain = false;
        if (!voiceAudioFocusHeld || audioManager == null) return;
        try { audioManager.abandonAudioFocus(voiceFocusListener); } catch (Throwable ignored) { }
        voiceAudioFocusHeld = false;
    }

    private void voicePlaybackError() {
        voicePlaybackError("语音播放失败，请重试");
    }

    private void voicePlaybackError(String message) {
        stopVoicePlayback();
        if (!isDead()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private File voiceCacheFile(String url) {
        File dir = new File(getCacheDir(), "forum_voice_play");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length() + ".amr");
    }

    private void cleanupVoiceCache() {
        File dir = new File(getCacheDir(), "forum_voice_play");
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return;
        long now = System.currentTimeMillis();
        long maxAge = TimeUnit.DAYS.toMillis(7);
        for (File file : files) {
            if (file == null) continue;
            if (file.getName().endsWith(".part") || now - file.lastModified() > maxAge) {
                deleteQuietly(file.getAbsolutePath());
            }
        }
        files = dir.listFiles(file -> file != null && file.isFile()
                && !file.getName().endsWith(".part"));
        if (files == null || files.length == 0) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        long keptBytes = 0L;
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            keptBytes += Math.max(0L, file.length());
            if (i >= 30 || keptBytes > 40L * 1024L * 1024L) {
                deleteQuietly(file.getAbsolutePath());
            }
        }
    }

    @Override
    protected void onStop() {
        if (recording) finishRecord(true);
        stopVoicePlayback();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        requestScope.cancelAll();
        voiceUploadGeneration++;
        deleteQuietly(activeVoiceLocalPath);
        activeVoiceLocalPath = "";
        if (recording) finishRecord(true);
        mainHandler.removeCallbacks(recordTick);
        mainHandler.removeCallbacks(hideFastScrollButton);
        if (fastScrollButton != null) fastScrollButton.animate().cancel();
        stopVoicePlayback();
        dismissVoiceConfirmDialog(true);
        dismissVoiceRetryDialog(true);
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
            List<Row> next = new ArrayList<>();
            if (currentTopic != null) next.add(Row.article(articleContentKey()));
            if (currentTopic != null && comments.isEmpty() && !loadingComments) {
                next.add(Row.empty());
            }
            for (ForumApiClient.Comment parent : comments) {
                if (parent == null) continue;
                next.add(Row.comment(parent, parent.id, false, commentContentKey(parent, false)));
                boolean expanded = expandedReplies.contains(parent.id);
                if (expanded && parent.replies != null && parent.replies.results != null) {
                    for (ForumApiClient.Comment reply : parent.replies.results) {
                        if (reply != null) {
                            next.add(Row.comment(reply, parent.id, true,
                                    commentContentKey(reply, true)));
                        }
                    }
                }
                if (parent.commentCount > 0 || (parent.replies != null
                        && parent.replies.results != null && !parent.replies.results.isEmpty())) {
                    next.add(Row.toggle(parent, toggleContentKey(parent, expanded)));
                }
            }
            if (commentsHasMore || loadingComments) {
                next.add(Row.loadMore((loadingComments ? "loading" : "more") + commentsCursor));
            }

            List<Row> old = new ArrayList<>(rows);
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    return old.get(oldPosition).stableId() == next.get(newPosition).stableId();
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    return TextUtils.equals(old.get(oldPosition).contentKey,
                            next.get(newPosition).contentKey);
                }
            }, false);
            rows.clear();
            rows.addAll(next);
            diff.dispatchUpdatesTo(this);
        }

        private String articleContentKey() {
            ForumApiClient.Topic topic = currentTopic;
            if (topic == null) return "";
            ForumApiClient.User user = topic.user;
            ForumApiClient.Category category = topic.category;
            return safe(topic.id) + '|' + textKey(topic.title) + '|' + textKey(topic.content) + '|'
                    + textKey(topic.summary) + '|' + topic.createTime + '|' + topic.viewCount + '|'
                    + topic.commentCount + '|' + topic.likeCount + '|' + topic.liked + '|'
                    + topic.favorited + '|' + topic.sticky + '|' + topic.recommend + '|'
                    + topic.type + '|' + safe(topic.qaStatus) + '|' + topic.acceptedCommentId + '|'
                    + userKey(user) + '|' + safe(category == null ? null : category.name) + '|'
                    + commentSort + '|' + imageListKey(topic.imageList);
        }

        private String commentContentKey(ForumApiClient.Comment comment, boolean reply) {
            if (comment == null) return "";
            ForumApiClient.User quoteUser = comment.quote == null ? null : comment.quote.user;
            return comment.id + "|" + reply + '|' + textKey(comment.content) + '|'
                    + comment.likeCount + '|' + comment.liked + '|' + comment.commentCount + '|'
                    + comment.status + '|' + comment.createTime + '|' + userKey(comment.user) + '|'
                    + userKey(quoteUser) + '|' + textKey(comment.quote == null
                    ? null : comment.quote.content) + '|' + imageListKey(comment.imageList) + '|'
                    + (currentTopic == null ? 0L : currentTopic.acceptedCommentId);
        }

        private String userKey(@Nullable ForumApiClient.User user) {
            if (user == null) return "";
            return safe(user.id) + ':' + safe(user.uid) + ':' + textKey(user.nickname) + ':'
                    + textKey(user.smallAvatar) + ':' + textKey(user.avatar) + ':'
                    + safe(user.countryCode) + ':' + safe(user.country);
        }

        private String textKey(@Nullable String value) {
            if (value == null) return "0:0";
            return value.length() + ":" + value.hashCode();
        }

        private String toggleContentKey(ForumApiClient.Comment parent, boolean expanded) {
            int loaded = parent.replies == null || parent.replies.results == null
                    ? 0 : parent.replies.results.size();
            boolean more = parent.replies != null && parent.replies.hasMore;
            String replyCursor = parent.replies == null ? "" : safe(parent.replies.cursor);
            return parent.id + "|" + parent.commentCount + '|' + loaded + '|' + more + '|'
                    + expanded + '|' + loadingComments + '|' + replyCursor;
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
            if (viewType == TYPE_ARTICLE) return new ArticleHolder(new ArticleView(parent.getContext()));
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
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            if (holder instanceof ArticleHolder) {
                ((ArticleHolder) holder).view.recycle();
            } else if (holder instanceof CommentHolder) {
                CommentHolder commentHolder = (CommentHolder) holder;
                commentHolder.imageContainer.recycle();
                if (playingVoiceView == commentHolder.voice) playingVoiceView = null;
                commentHolder.voice.setOnClickListener(null);
                commentHolder.voice.setOnLongClickListener(null);
            }
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private void bindArticle(ArticleHolder holder) {
            holder.view.bind(currentTopic);
        }

        private void bindComment(CommentHolder holder, Row row) {
            ForumApiClient.Comment comment = row.comment;
            boolean reply = row.reply;
            VoicePayload voice = VoicePayload.parse(comment.content);
            int left = reply ? 48 : 16;
            int right = voice == null ? 14 : 2;
            holder.root.setPadding(dp(left), dp(reply ? 7 : 10), dp(right), dp(reply ? 6 : 8));
            holder.root.setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);
            holder.avatar.setSize(reply ? 25 : 32);
            LinearLayout.LayoutParams avatarParams = (LinearLayout.LayoutParams) holder.avatar.getLayoutParams();
            avatarParams.width = dp((reply ? 25 : 32) + 4);
            avatarParams.height = dp((reply ? 25 : 32) + 4);
            holder.avatar.setLayoutParams(avatarParams);

            String author = userName(comment.user);
            bindAvatar(holder.avatar, comment.user, author);
            View.OnClickListener openProfile = v -> ForumProfileRouter.open(
                    ForumTopicActivity.this, comment.user);
            holder.avatar.setOnClickListener(openProfile);
            holder.name.setOnClickListener(openProfile);
            String target = reply && comment.quote != null && comment.quote.user != null
                    ? userName(comment.quote.user) : "";
            setCommentName(holder.name, author, target, reply, isTopicAuthor(comment.user));
            holder.time.setText(formatDate(comment.createTime));

            boolean accepted = currentTopic != null && currentTopic.type == 2
                    && currentTopic.acceptedCommentId == comment.id;
            boolean canAccept = !reply && canAcceptAnswer(comment, row.parentId);
            holder.answer.setVisibility(accepted || canAccept ? View.VISIBLE : View.GONE);
            if (accepted) {
                holder.answer.setText("已采纳");
                holder.answer.setTextColor(isDark() ? 0xFF79D4A8 : 0xFF21875B);
                holder.answer.setBackground(roundRect(isDark() ? 0xFF203D32 : 0xFFE9F7F0, 11));
                holder.answer.setOnClickListener(null);
                holder.answer.setClickable(false);
            } else if (canAccept) {
                holder.answer.setText("采纳");
                holder.answer.setTextColor(0xFF1877F2);
                holder.answer.setBackground(roundRect(isDark() ? 0xFF243B59 : 0xFFEAF3FF, 11));
                holder.answer.setClickable(true);
                holder.answer.setOnClickListener(v -> confirmAcceptAnswer(comment));
            } else {
                holder.answer.setOnClickListener(null);
                holder.answer.setBackground(null);
            }

            holder.like.setText(comment.likeCount > 0 ? String.valueOf(comment.likeCount) : "");
            int likeColor = comment.liked ? 0xFF1877F2
                    : (isDark() ? 0xFF858B93 : 0xFF9AA0A7);
            setCompoundIcon(holder.like, R.drawable.ic_forum_thumb_up_outline, 16, likeColor);
            holder.like.setTextColor(likeColor);
            holder.like.setBackground(voice == null ? selectableBackground() : null);
            holder.like.setOnClickListener(v -> changeCommentLike(comment));

            holder.more.setText("⋮");
            holder.more.setTextColor(isDark() ? 0xFF858B93 : 0xFF9AA0A7);
            holder.more.setBackground(voice == null ? selectableBackground() : null);
            holder.more.setTranslationX(voice == null ? 0f : dp(4));
            holder.more.setOnClickListener(v -> showCommentMenu(comment, row.parentId, author));

            if (voice == null) {
                if (playingVoiceView == holder.voice) playingVoiceView = null;
                holder.voice.setOnClickListener(null);
                holder.voice.setOnLongClickListener(null);
                holder.body.setVisibility(View.VISIBLE);
                holder.voice.setVisibility(View.GONE);
                ForumLinkRouter.setLinkedText(holder.body, ForumHtmlCache.parse(comment.content));
            } else {
                holder.body.setVisibility(View.GONE);
                holder.voice.setVisibility(View.VISIBLE);
                String key = String.valueOf(comment.id);
                holder.voice.setTag(key);
                int width = Math.min(250, 176 + Math.max(1, voice.durationSec) * 2);
                LinearLayout.LayoutParams voiceParams = (LinearLayout.LayoutParams) holder.voice.getLayoutParams();
                voiceParams.width = dp(width);
                holder.voice.setLayoutParams(voiceParams);
                boolean playing = TextUtils.equals(playingVoiceKey, key);
                if (playing) playingVoiceView = holder.voice;
                float progress = playing ? currentVoiceProgress() : 0f;
                setVoiceBubbleUi(holder.voice, voice,
                        playing ? voiceRemainingSeconds(voice) : voice.durationSec,
                        playing && voicePlayer != null && isVoicePlaying(), progress);
                holder.voice.setOnClickListener(v -> playVoice(voice, key, holder.voice));
                holder.voice.setOnLongClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showCommentMenu(comment, row.parentId, author);
                    return true;
                });
            }

            holder.imageContainer.bind(comment.imageList, dp(220), dp(6),
                    isDark() ? 0xFF24262B : 0xFFF0F1F3);

            View.OnLongClickListener longClick = v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showCommentMenu(comment, row.parentId, author);
                return true;
            };
            holder.root.setOnLongClickListener(longClick);
            holder.body.setOnLongClickListener(longClick);

            View.OnClickListener replyClick = v -> {
                if (!isOwnComment(comment)) {
                    setReplyTarget(row.parentId, comment.id, author);
                } else if (replyParentId > 0 || replyQuoteId > 0) {
                    clearReplyTarget();
                }
            };
            holder.root.setOnClickListener(replyClick);
            holder.body.setOnClickListener(replyClick);
        }

        private void setCommentName(TextView view, String author, String target,
                                    boolean reply, boolean owner) {
            String safeAuthor = TextUtils.isEmpty(author) ? "用户" : author;
            String safeTarget = TextUtils.isEmpty(target) ? "" : target;
            String connector = reply && !TextUtils.isEmpty(safeTarget) ? " 回复 " : "";
            String namePart = safeAuthor + connector + safeTarget;
            String ownerPart = owner ? "  楼主" : "";
            SpannableString value = new SpannableString(namePart + ownerPart);
            int normalName = isDark() ? 0xFFE4E6E9 : 0xFF30353B;
            int replyName = isDark() ? 0xFFA3A9B1 : 0xFF7E858D;
            int connectorColor = isDark() ? 0xFF767D86 : 0xFFA1A7AE;
            value.setSpan(new ForegroundColorSpan(reply ? replyName : normalName),
                    0, safeAuthor.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setSpan(new StyleSpan(reply ? Typeface.NORMAL : Typeface.BOLD),
                    0, safeAuthor.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!TextUtils.isEmpty(connector)) {
                int connectorStart = safeAuthor.length();
                int targetStart = connectorStart + connector.length();
                value.setSpan(new ForegroundColorSpan(connectorColor), connectorStart, targetStart,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                value.setSpan(new ForegroundColorSpan(replyName), targetStart, namePart.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (owner) {
                int ownerStart = namePart.length();
                int ownerEnd = ownerStart + ownerPart.length();
                value.setSpan(new ForegroundColorSpan(0xFF1877F2), ownerStart, ownerEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                value.setSpan(new RelativeSizeSpan(0.82f), ownerStart, ownerEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            view.setText(value);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, reply ? 12 : 13);
        }

        private boolean isTopicAuthor(@Nullable ForumApiClient.User user) {
            return currentTopic != null && currentTopic.user != null && user != null
                    && !TextUtils.isEmpty(currentTopic.user.id)
                    && TextUtils.equals(currentTopic.user.id, user.id);
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

    private float currentVoiceProgress() {
        if (voicePlayer == null) return 0f;
        try {
            int duration = voicePlayer.getDuration();
            int position = voicePlayer.getCurrentPosition();
            if (duration > 0) return Math.max(0f, Math.min(1f, position / (float) duration));
        } catch (Throwable ignored) { }
        return 0f;
    }

    private int voiceRemainingSeconds(VoicePayload payload) {
        if (payload == null || voicePlayer == null) return payload == null ? 0 : payload.durationSec;
        try {
            int duration = voicePlayer.getDuration();
            int position = voicePlayer.getCurrentPosition();
            if (duration > 0) return Math.max(0, (int) Math.ceil((duration - position) / 1000.0));
        } catch (Throwable ignored) { }
        return payload.durationSec;
    }

    private boolean isVoicePlaying() {
        try { return voicePlayer != null && voicePlayer.isPlaying(); }
        catch (Throwable ignored) { return false; }
    }

    private View createCommentItem(Context context) {
        boolean dark = isDark();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setClickable(true);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        AvatarView avatar = new AvatarView(context);
        avatar.setSize(32);
        header.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView name = text("", 13, dark ? 0xFFE4E6E9 : 0xFF30353B, false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(8);
        header.addView(name, nameParams);

        TextView answer = text("", 10.5f, 0xFF1877F2, true);
        answer.setGravity(Gravity.CENTER);
        answer.setSingleLine(true);
        answer.setPadding(dp(8), 0, dp(8), 0);
        answer.setVisibility(View.GONE);
        LinearLayout.LayoutParams answerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(26));
        answerParams.rightMargin = dp(2);
        header.addView(answer, answerParams);

        TextView like = text("", 11, dark ? 0xFF858B93 : 0xFF9AA0A7, false);
        like.setGravity(Gravity.CENTER);
        like.setCompoundDrawablePadding(dp(1));
        like.setBackground(selectableBackground());
        header.addView(like, new LinearLayout.LayoutParams(dp(40), dp(32)));

        TextView more = text("⋮", 20, dark ? 0xFF858B93 : 0xFF9AA0A7, false);
        more.setGravity(Gravity.CENTER);
        more.setIncludeFontPadding(false);
        more.setContentDescription("评论操作");
        more.setBackground(selectableBackground());
        header.addView(more, new LinearLayout.LayoutParams(dp(30), dp(32)));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = htmlText("", 15);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(5);
        bodyParams.leftMargin = dp(40);
        root.addView(body, bodyParams);

        VoiceBubbleView voice = new VoiceBubbleView(context);
        voice.setVisibility(View.GONE);
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(dp(176), dp(48));
        voiceParams.topMargin = dp(6);
        voiceParams.leftMargin = dp(40);
        root.addView(voice, voiceParams);

        ForumRemoteImageListView images = new ForumRemoteImageListView(context);
        LinearLayout.LayoutParams imagesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imagesParams.leftMargin = dp(40);
        root.addView(images, imagesParams);

        TextView time = text("", 10.5f, dark ? 0xFF6F757D : 0xFFA7ADB4, false);
        time.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        timeParams.leftMargin = dp(40);
        timeParams.topMargin = dp(2);
        root.addView(time, timeParams);

        return root;
    }

    private View createToggleItem(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(52), dp(3), dp(14), dp(6));
        root.setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView more = text("", 12, 0xFF1877F2, true);
        more.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(more, new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView collapse = text("", 12, 0xFF1877F2, true);
        collapse.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        root.addView(collapse, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        return root;
    }

    private final class ArticleView extends LinearLayout {
        private final LinearLayout topMeta;
        private final TextView category;
        private final TextView sticky;
        private final TextView recommend;
        private final TextView qaMark;
        private final TextView qaStatus;
        private final TextView title;
        private final AvatarView avatar;
        private final TextView author;
        private final TextView authorMeta;
        private final TextView more;
        private final TextView content;
        private final ForumRemoteImageListView images;
        private final TextView eyeAction;
        private final TextView likeAction;
        private final TextView favoriteAction;
        private final TextView commentAction;
        private final TextView commentTitle;
        private final TextView hotSort;
        private final TextView ascSort;
        private final TextView descSort;

        ArticleView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(18), dp(12), dp(18), 0);
            setBackgroundColor(isDark() ? 0xFF17181B : Color.WHITE);
            setOnClickListener(v -> {
                if (replyParentId > 0 || replyQuoteId > 0) clearReplyTarget();
            });

            topMeta = new LinearLayout(context);
            topMeta.setGravity(Gravity.CENTER_VERTICAL);
            category = text("", 12, 0xFF1877F2, true);
            category.setGravity(Gravity.CENTER);
            category.setPadding(dp(10), 0, dp(10), 0);
            category.setBackground(roundRect(isDark() ? 0xFF243B59 : 0xFFEAF3FF, 12));
            topMeta.addView(category, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)));
            sticky = smallBadge("置顶", 0xFFB96800,
                    isDark() ? 0xFF40311D : 0xFFFFF0D6);
            LinearLayout.LayoutParams stickyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(23));
            stickyParams.leftMargin = dp(6);
            topMeta.addView(sticky, stickyParams);
            recommend = smallBadge("推荐", 0xFF1877F2,
                    isDark() ? 0xFF243B59 : 0xFFEAF3FF);
            LinearLayout.LayoutParams recommendParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(23));
            recommendParams.leftMargin = dp(6);
            topMeta.addView(recommend, recommendParams);
            addView(topMeta);

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            qaMark = text("问", 12, isDark() ? 0xFF3D2C00 : 0xFF6E4B00, true);
            qaMark.setGravity(Gravity.CENTER);
            qaMark.setIncludeFontPadding(false);
            qaMark.setBackground(roundRect(isDark() ? 0xFFD6A52A : 0xFFFFD65A, 12));
            qaMark.setVisibility(GONE);
            LinearLayout.LayoutParams qaMarkParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            qaMarkParams.rightMargin = dp(8);
            titleRow.addView(qaMark, qaMarkParams);

            title = text("", 24, isDark() ? Color.WHITE : 0xFF17191C, true);
            title.setLineSpacing(0, 1.10f);
            titleRow.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            qaStatus = text("", 11, 0xFFB76E00, true);
            qaStatus.setGravity(Gravity.CENTER);
            qaStatus.setSingleLine(true);
            qaStatus.setPadding(dp(8), 0, dp(8), 0);
            qaStatus.setVisibility(GONE);
            LinearLayout.LayoutParams qaStatusParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(24));
            qaStatusParams.leftMargin = dp(8);
            titleRow.addView(qaStatus, qaStatusParams);

            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = dp(10);
            addView(titleRow, titleParams);

            LinearLayout authorRow = new LinearLayout(context);
            authorRow.setGravity(Gravity.CENTER_VERTICAL);
            authorRow.setPadding(0, dp(13), 0, 0);
            avatar = new AvatarView(context);
            avatar.setSize(39);
            authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(43), dp(43)));

            LinearLayout authorCopy = new LinearLayout(context);
            authorCopy.setOrientation(VERTICAL);
            LinearLayout nameRow = new LinearLayout(context);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            author = text("", 14, isDark() ? Color.WHITE : 0xFF272B31, true);
            nameRow.addView(author);
            TextView ownerBadge = smallBadge("楼主", 0xFF1877F2,
                    isDark() ? 0xFF243B59 : 0xFFEAF3FF);
            LinearLayout.LayoutParams ownerBadgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(20));
            ownerBadgeParams.leftMargin = dp(6);
            nameRow.addView(ownerBadge, ownerBadgeParams);
            authorCopy.addView(nameRow);
            authorMeta = text("", 12, isDark() ? 0xFF8F949C : 0xFF7A8088, false);
            authorCopy.addView(authorMeta);
            LinearLayout.LayoutParams authorCopyParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            authorCopyParams.leftMargin = dp(10);
            authorRow.addView(authorCopy, authorCopyParams);

            more = text("⋮", 27, isDark() ? 0xFFD8DADE : 0xFF4D535B, false);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("帖子操作");
            more.setBackground(selectableBackground());
            more.setOnClickListener(v -> showTopicMenu());
            more.setTranslationX(dp(10));
            authorRow.addView(more, new LinearLayout.LayoutParams(dp(38), dp(44)));
            addView(authorRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f));
            dividerParams.topMargin = dp(13);
            dividerParams.bottomMargin = dp(15);
            addView(divider(), dividerParams);

            content = htmlText("", 17);
            content.setMaxLines(Integer.MAX_VALUE);
            content.setEllipsize(null);
            content.setOnClickListener(v -> clearReplyTarget());
            addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            images = new ForumRemoteImageListView(context);
            addView(images, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout actions = new LinearLayout(context);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(0, dp(12), 0, dp(10));
            eyeAction = createTopicAction();
            likeAction = createTopicAction();
            favoriteAction = createTopicAction();
            commentAction = createTopicAction();
            actions.addView(eyeAction, actionParams());
            actions.addView(likeAction, actionParams());
            actions.addView(favoriteAction, actionParams());
            actions.addView(commentAction, actionParams());
            addView(actions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            addView(divider(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(0.8f)));

            LinearLayout commentsHeader = new LinearLayout(context);
            commentsHeader.setGravity(Gravity.CENTER_VERTICAL);
            commentTitle = text("评论", 17, isDark() ? Color.WHITE : 0xFF202328, true);
            commentsHeader.addView(commentTitle, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            LinearLayout pill = new LinearLayout(context);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(2), dp(2), dp(2), dp(2));
            pill.setBackground(roundRect(isDark() ? 0xFF25272C : 0xFFF1F3F5, 15));
            hotSort = createSortTab("热门", COMMENT_SORT_HOT);
            ascSort = createSortTab("正序", COMMENT_SORT_ASC);
            descSort = createSortTab("倒序", COMMENT_SORT_DESC);
            pill.addView(hotSort, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            pill.addView(ascSort, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            pill.addView(descSort, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            commentsHeader.addView(pill, new LinearLayout.LayoutParams(dp(150), dp(32)));
            addView(commentsHeader, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        }

        void bind(@Nullable ForumApiClient.Topic topic) {
            if (topic == null) {
                setVisibility(GONE);
                images.recycle();
                return;
            }
            setVisibility(VISIBLE);
            boolean hasCategory = topic.category != null
                    && !TextUtils.isEmpty(topic.category.name);
            category.setVisibility(hasCategory ? VISIBLE : GONE);
            category.setText(hasCategory ? topic.category.name : "");
            sticky.setVisibility(topic.sticky ? VISIBLE : GONE);
            recommend.setVisibility(topic.recommend ? VISIBLE : GONE);
            topMeta.setVisibility(hasCategory || topic.sticky || topic.recommend ? VISIBLE : GONE);

            boolean question = topic.type == 2;
            boolean solved = question && (topic.acceptedCommentId > 0
                    || "solved".equalsIgnoreCase(safe(topic.qaStatus)));
            qaMark.setVisibility(question ? VISIBLE : GONE);
            qaStatus.setVisibility(question ? VISIBLE : GONE);
            if (question) {
                qaStatus.setText(solved ? "已解决" : "未解决");
                qaStatus.setTextColor(solved ? 0xFF21875B : 0xFFB76E00);
                qaStatus.setBackground(roundRect(
                        solved ? (isDark() ? 0xFF203D32 : 0xFFE9F7F0)
                                : (isDark() ? 0xFF40311D : 0xFFFFF3D8), 12));
            } else {
                qaStatus.setBackground(null);
            }
            title.setText(safe(topic.title));
            String authorName = userName(topic.user);
            author.setText(authorName);
            authorMeta.setText(formatDate(topic.createTime));
            bindAvatar(avatar, topic.user, authorName);

            View.OnClickListener openProfile = v -> ForumProfileRouter.open(
                    ForumTopicActivity.this, topic.user);
            avatar.setOnClickListener(openProfile);
            author.setOnClickListener(openProfile);
            authorMeta.setOnClickListener(openProfile);

            String body = TextUtils.isEmpty(topic.content) ? topic.summary : topic.content;
            ForumLinkRouter.setLinkedText(content, ForumHtmlCache.parse(body));
            images.bind(topic.imageList, dp(220), dp(10),
                    isDark() ? 0xFF24262B : 0xFFF0F1F3);

            bindTopicAction(eyeAction, R.drawable.ic_forum_eye,
                    String.valueOf(Math.max(0, topic.viewCount)), false, null, 22);
            bindTopicAction(likeAction,
                    topic.liked ? R.drawable.ic_forum_heart_filled
                            : R.drawable.ic_forum_heart_round,
                    String.valueOf(Math.max(0, topic.likeCount)), topic.liked,
                    v -> changeTopicLike(), 19);
            bindTopicAction(favoriteAction,
                    topic.favorited ? R.drawable.ic_forum_bookmark_filled
                            : R.drawable.ic_forum_bookmark,
                    "", topic.favorited, v -> changeFavorite(), 19);
            bindTopicAction(commentAction, R.drawable.ic_forum_chat_bubble,
                    String.valueOf(Math.max(0, topic.commentCount)), false,
                    v -> focusCommentInput(), 19);

            commentTitle.setText(topic.commentCount > 0
                    ? "评论 " + topic.commentCount : "评论");
            bindSortTab(hotSort, COMMENT_SORT_HOT);
            bindSortTab(ascSort, COMMENT_SORT_ASC);
            bindSortTab(descSort, COMMENT_SORT_DESC);
        }

        void recycle() {
            images.recycle();
        }

        private TextView createTopicAction() {
            TextView action = text("", 12.5f,
                    isDark() ? 0xFFB8BBC2 : 0xFF59616A, false);
            action.setGravity(Gravity.CENTER);
            action.setCompoundDrawablePadding(dp(3));
            action.setPadding(dp(8), 0, dp(8), 0);
            return action;
        }

        private LinearLayout.LayoutParams actionParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            params.rightMargin = dp(5);
            return params;
        }

        private void bindTopicAction(TextView action, int iconRes, String label,
                                     boolean active, @Nullable View.OnClickListener listener,
                                     int iconSize) {
            int color = active ? 0xFFE34A55 : (isDark() ? 0xFFB8BBC2 : 0xFF59616A);
            action.setText(label);
            action.setTextColor(color);
            setCompoundIcon(action, iconRes, iconSize, color);
            action.setOnClickListener(listener);
            action.setClickable(listener != null);
            action.setBackground(listener == null ? null : selectableBackground());
        }

        private TextView createSortTab(String label, String value) {
            TextView tab = text(label, 11.5f,
                    isDark() ? 0xFFAEB3BB : 0xFF69717A, false);
            tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> setCommentSort(value));
            return tab;
        }

        private void bindSortTab(TextView tab, String value) {
            boolean selected = TextUtils.equals(commentSort, value);
            tab.setTextColor(selected ? 0xFF1877F2
                    : (isDark() ? 0xFFAEB3BB : 0xFF69717A));
            tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            tab.setBackground(selected
                    ? roundRect(isDark() ? 0xFF34465F : Color.WHITE, 13) : null);
        }
    }

    private final class VoiceBubbleView extends LinearLayout {
        private final AppCompatImageView playView;
        private final VoiceSignalView signalView;
        private final TextView durationView;

        VoiceBubbleView(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(7), 0, dp(10), 0);
            setClickable(true);

            playView = new AppCompatImageView(context);
            playView.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            playView.setPadding(dp(9), dp(9), dp(9), dp(9));
            addView(playView, new LinearLayout.LayoutParams(dp(34), dp(34)));

            signalView = new VoiceSignalView(context);
            LinearLayout.LayoutParams signalParams = new LinearLayout.LayoutParams(
                    0, dp(28), 1f);
            signalParams.leftMargin = dp(8);
            signalParams.rightMargin = dp(7);
            addView(signalView, signalParams);

            durationView = text("1″", 12,
                    isDark() ? 0xFFDDE0E5 : 0xFF4B525B, true);
            durationView.setGravity(Gravity.CENTER);
            durationView.setSingleLine(true);
            addView(durationView, new LinearLayout.LayoutParams(dp(36),
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        void bind(VoicePayload payload, int seconds, boolean active, float progress) {
            if (payload == null) return;
            int accent = active ? 0xFF1877F2 : (isDark() ? 0xFFDDE0E5 : 0xFF4F5862);
            signalView.setProgress(progress);
            signalView.setActive(active);
            durationView.setText(Math.max(0, seconds) + "″");
            durationView.setTextColor(accent);
            playView.setBackground(null);
            setImageIcon(playView, active ? R.drawable.ic_forum_pause : R.drawable.ic_forum_play,
                    15, active ? 0xFF1877F2 : (isDark() ? 0xFFE5E8EC : 0xFF4F5862));
            setBackground(roundRect(active
                    ? (isDark() ? 0xFF263B57 : 0xFFEAF3FF)
                    : (isDark() ? 0xFF25282E : 0xFFF0F3F7), 24));
        }
    }

    private final class VoiceSignalView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] bars = {0.34f, 0.58f, 0.82f, 0.46f, 0.72f, 0.92f, 0.56f,
                0.38f, 0.68f, 0.88f, 0.52f, 0.76f, 0.44f, 0.64f, 0.36f};
        private float progress;
        private boolean active;

        VoiceSignalView(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(2.0f));
        }

        void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        void setActive(boolean value) {
            active = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getWidth() <= 0 || getHeight() <= 0) return;
            float gap = getWidth() / (float) bars.length;
            float centerY = getHeight() / 2f;
            int progressed = Math.round(progress * bars.length);
            for (int i = 0; i < bars.length; i++) {
                boolean played = i < progressed;
                paint.setColor(played ? 0xFF1877F2
                        : (isDark() ? 0xFF8D949D : 0xFF9AA2AB));
                paint.setAlpha(played ? 255 : 205);
                float x = gap * i + gap / 2f;
                float half = Math.max(dp(2), getHeight() * bars[i] * 0.42f);
                canvas.drawLine(x, centerY - half, x, centerY + half, paint);
            }
            paint.setAlpha(255);
        }
    }

    private final class ArticleHolder extends RecyclerView.ViewHolder {
        final ArticleView view;
        ArticleHolder(@NonNull ArticleView itemView) {
            super(itemView);
            view = itemView;
        }
    }

    private static final class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(@NonNull View itemView) { super(itemView); }
    }

    private static final class CommentHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final AvatarView avatar;
        final TextView name;
        final TextView body;
        final VoiceBubbleView voice;
        final ForumRemoteImageListView imageContainer;
        final TextView time;
        final TextView answer;
        final TextView like;
        final TextView more;

        CommentHolder(@NonNull View itemView) {
            super(itemView);
            root = (LinearLayout) itemView;
            LinearLayout header = (LinearLayout) root.getChildAt(0);
            avatar = (AvatarView) header.getChildAt(0);
            name = (TextView) header.getChildAt(1);
            answer = (TextView) header.getChildAt(2);
            like = (TextView) header.getChildAt(3);
            more = (TextView) header.getChildAt(4);
            body = (TextView) root.getChildAt(1);
            voice = (VoiceBubbleView) root.getChildAt(2);
            imageContainer = (ForumRemoteImageListView) root.getChildAt(3);
            time = (TextView) root.getChildAt(4);
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
        final String contentKey;

        private Row(int type, ForumApiClient.Comment comment,
                    ForumApiClient.Comment parent, long parentId, boolean reply,
                    @NonNull String contentKey) {
            this.type = type;
            this.comment = comment;
            this.parent = parent;
            this.parentId = parentId;
            this.reply = reply;
            this.contentKey = contentKey;
        }

        static Row article(String key) { return new Row(1, null, null, 0, false, key); }
        static Row comment(ForumApiClient.Comment comment, long parentId, boolean reply, String key) {
            return new Row(2, comment, null, parentId, reply, key);
        }
        static Row toggle(ForumApiClient.Comment parent, String key) {
            return new Row(3, null, parent, parent.id, false, key);
        }
        static Row empty() { return new Row(4, null, null, 0, false, "empty"); }
        static Row loadMore(String key) { return new Row(5, null, null, 0, false, key); }

        long stableId() {
            if (type == 1) return Long.MIN_VALUE + 1;
            if (type == 4) return Long.MIN_VALUE + 4;
            if (type == 5) return Long.MIN_VALUE + 5;
            if (type == 3) return -(parentId * 10L + 3L);
            long id = comment == null ? 0 : comment.id;
            return id * 10L + (reply ? 2L : 1L);
        }
    }

    private static String imageListKey(@Nullable List<ForumApiClient.ImageInfo> images) {
        if (images == null || images.isEmpty()) return "";
        StringBuilder key = new StringBuilder();
        for (ForumApiClient.ImageInfo image : images) {
            if (image == null) continue;
            key.append(safe(image.url)).append(':').append(safe(image.preview)).append(';');
        }
        return key.toString();
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
        ForumLinkRouter.setLinkedText(view, ForumHtmlCache.parse(html));
        return view;
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

    private void setImageIcon(AppCompatImageView view, int resId, int sizeDp, int color) {
        Drawable drawable = AppCompatResources.getDrawable(this, resId);
        if (drawable == null) {
            view.setImageDrawable(null);
            return;
        }
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, color);
        int size = dp(sizeDp);
        drawable.setBounds(0, 0, size, size);
        view.setImageDrawable(drawable);
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
        @Override public final void onSuccess(@Nullable Void data) {
            if (!isDead()) success();
        }
        @Override public final void onError(@NonNull String message) {
            if (!isDead()) finishTopicAction(message);
        }
    }
}
