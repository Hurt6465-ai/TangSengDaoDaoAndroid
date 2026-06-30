package com.chat.feed.comment;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.ud.WKProgressManager;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.ui.components.AvatarView;
import com.chat.feed.FeedModel;
import com.chat.feed.R;
import com.chat.feed.model.CommentBean;
import com.chat.feed.model.CommentListResponse;
import com.chat.uikit.view.voice.AudioRecordManager;
import com.chat.uikit.view.voice.LineWaveVoiceView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FeedCommentBottomSheet extends BottomSheetDialogFragment {
    private static final int REQ_RECORD_AUDIO = 3201;
    private static final long MIN_RECORD_MS = 1000L;
    private static final long MAX_RECORD_MS = 60000L;

    private static final String ARG_FEED_ID = "feed_id";
    private static final String ARG_COUNT = "count";
    private static final String ARG_AUTHOR_UID = "author_uid";
    private static final String ARG_AUTHOR = "author";
    private static final String ARG_AUTHOR_AVATAR = "author_avatar";
    private static final String ARG_AUTHOR_AVATAR_CACHE = "author_avatar_cache";
    private static final String ARG_AUTHOR_COUNTRY = "author_country";
    private static final String ARG_AUTHOR_FOLLOWED = "author_followed";
    private static final String ARG_CAPTION = "caption";

    private String feedId;
    private String authorUid = "";
    private String authorName = "";
    private String authorAvatar = "";
    private String authorAvatarCache = "";
    private String authorCountry = "";
    private boolean authorFollowed;
    private String caption = "";
    private String cursor = "";
    private boolean loading;
    private boolean hasMore = true;
    private int commentCount;
    private FeedCommentAdapter adapter;
    private View rootView;
    private RecyclerView commentRecyclerView;
    private View inputBar;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardListener;
    private int recyclerBasePaddingBottom;
    private EditText editText;
    private TextView titleTv;
    private ImageButton actionBtn;
    private TextView recordHintTv;
    private View recordPanel;
    private LineWaveVoiceView recordWaveView;
    private TextView recordTimerTv;
    private TextView recordCancelTv;
    private TextView authorFollowTv;
    private OnCommentSentListener onCommentSentListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService voiceExecutor = Executors.newSingleThreadExecutor();
    private boolean recording;
    private boolean recordCancel;
    private float recordStartX;
    private float recordStartY;
    private long recordStartTime;
    private String recordPath;
    private String recordWaveform;
    private final Runnable maxRecordRunnable = () -> finishRecord(false);
    private final Runnable recordTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!recording) return;
            updateRecordTimer();
            mainHandler.postDelayed(this, 200L);
        }
    };

    public interface OnCommentSentListener {
        void onCommentSent(int delta);
    }

    public static FeedCommentBottomSheet newInstance(String feedId) {
        return newInstance(feedId, 0);
    }

    public static FeedCommentBottomSheet newInstance(String feedId, int count) {
        return newInstance(feedId, count, "", "");
    }

    public static FeedCommentBottomSheet newInstance(String feedId, int count, String author, String caption) {
        return newInstance(feedId, count, "", author, "", "", "", false, caption);
    }

    public static FeedCommentBottomSheet newInstance(String feedId, int count, String authorUid, String author, String avatar, String avatarCache, String country, boolean followed, String caption) {
        FeedCommentBottomSheet sheet = new FeedCommentBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_FEED_ID, feedId);
        args.putInt(ARG_COUNT, count);
        args.putString(ARG_AUTHOR_UID, authorUid == null ? "" : authorUid);
        args.putString(ARG_AUTHOR, author == null ? "" : author);
        args.putString(ARG_AUTHOR_AVATAR, avatar == null ? "" : avatar);
        args.putString(ARG_AUTHOR_AVATAR_CACHE, avatarCache == null ? "" : avatarCache);
        args.putString(ARG_AUTHOR_COUNTRY, country == null ? "" : country);
        args.putBoolean(ARG_AUTHOR_FOLLOWED, followed);
        args.putString(ARG_CAPTION, caption == null ? "" : caption);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnCommentSentListener(OnCommentSentListener listener) {
        this.onCommentSentListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_feed_comment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        feedId = args == null ? "" : args.getString(ARG_FEED_ID, "");
        commentCount = args == null ? 0 : args.getInt(ARG_COUNT, 0);
        authorUid = args == null ? "" : args.getString(ARG_AUTHOR_UID, "");
        authorName = args == null ? "" : args.getString(ARG_AUTHOR, "");
        authorAvatar = args == null ? "" : args.getString(ARG_AUTHOR_AVATAR, "");
        authorAvatarCache = args == null ? "" : args.getString(ARG_AUTHOR_AVATAR_CACHE, "");
        authorCountry = args == null ? "" : args.getString(ARG_AUTHOR_COUNTRY, "");
        authorFollowed = args != null && args.getBoolean(ARG_AUTHOR_FOLLOWED, false);
        caption = args == null ? "" : args.getString(ARG_CAPTION, "");

        rootView = view;
        bindHeader(view);
        adapter = new FeedCommentAdapter();
        commentRecyclerView = view.findViewById(R.id.commentRecyclerView);
        recyclerBasePaddingBottom = commentRecyclerView.getPaddingBottom();
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        commentRecyclerView.setLayoutManager(layoutManager);
        commentRecyclerView.setAdapter(adapter);
        commentRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!hasMore || loading) return;
                int last = layoutManager.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 5) loadComments(false);
            }
        });
        titleTv = view.findViewById(R.id.commentTitleTv);
        editText = view.findViewById(R.id.commentEditText);
        actionBtn = view.findViewById(R.id.commentSendBtn);
        inputBar = view.findViewById(R.id.commentInputBar);
        recordHintTv = view.findViewById(R.id.commentRecordHintTv);
        recordPanel = view.findViewById(R.id.commentRecordPanel);
        recordWaveView = view.findViewById(R.id.commentRecordWaveView);
        recordTimerTv = view.findViewById(R.id.commentRecordTimerTv);
        recordCancelTv = view.findViewById(R.id.commentRecordCancelTv);
        ImageButton closeBtn = view.findViewById(R.id.commentCloseBtn);
        closeBtn.setOnClickListener(v -> dismissAllowingStateLoss());
        bindInputBar();
        setupKeyboardAvoidance();
        adapter.setActionListener(new FeedCommentAdapter.CommentActionListener() {
            @Override
            public void onReplyClick(CommentBean item, int position) {
                if (item == null || editText == null) return;
                String name = TextUtils.isEmpty(item.name) ? "" : item.name;
                editText.setHint(TextUtils.isEmpty(name) ? getString(R.string.feed_comment_hint) : getString(R.string.feed_comment_reply_to, name));
                editText.requestFocus();
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }

            @Override
            public void onLoadReplies(CommentBean item, int position) {
            }

            @Override
            public void onRetryLocal(CommentBean item, int position) {
                if (item != null) retryLocalComment(item);
            }
        });
        updateTitle();
        loadComments(true);
    }

    private void bindHeader(View view) {
        AvatarView avatar = view.findViewById(R.id.commentAuthorAvatar);
        TextView nameTv = view.findViewById(R.id.commentAuthorNameTv);
        authorFollowTv = view.findViewById(R.id.commentAuthorFollowTv);
        TextView captionTv = view.findViewById(R.id.commentCaptionTv);
        View divider = view.findViewById(R.id.commentCaptionDivider);
        nameTv.setText(TextUtils.isEmpty(authorName) ? "@" : "@" + authorName);
        try {
            if (!TextUtils.isEmpty(authorUid)) avatar.showAvatar(authorUid, WKChannelType.PERSONAL, authorAvatarCache);
            else if (!TextUtils.isEmpty(authorAvatar)) avatar.showAvatarUrl(authorAvatar, authorAvatarCache, authorName, authorUid);
            else avatar.showDefaultAvatar(authorName, authorUid);
            if (!TextUtils.isEmpty(authorCountry)) avatar.showFlag(authorCountry);
        } catch (Throwable ignored) {
            avatar.showDefaultAvatar(authorName, authorUid);
        }
        updateAuthorFollowButton();
        authorFollowTv.setOnClickListener(v -> toggleAuthorFollow());
        if (TextUtils.isEmpty(caption)) {
            captionTv.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
        } else {
            captionTv.setText(caption);
            captionTv.setVisibility(View.VISIBLE);
            divider.setVisibility(View.VISIBLE);
        }
    }

    private void updateAuthorFollowButton() {
        if (authorFollowTv == null) return;
        boolean self = !TextUtils.isEmpty(authorUid) && authorUid.equals(WKConfig.getInstance().getUid());
        authorFollowTv.setVisibility(self || TextUtils.isEmpty(authorUid) ? View.GONE : View.VISIBLE);
        authorFollowTv.setText(authorFollowed ? R.string.feed_followed : R.string.feed_follow);
        authorFollowTv.setAlpha(authorFollowed ? 0.72f : 1f);
    }

    private void toggleAuthorFollow() {
        if (TextUtils.isEmpty(authorUid)) return;
        boolean next = !authorFollowed;
        authorFollowTv.setEnabled(false);
        FeedModel.getInstance().setFollow(authorUid, next, new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                authorFollowed = next;
                updateAuthorFollowButton();
                authorFollowTv.setEnabled(true);
            }

            @Override
            public void onFail(int code, String msg) {
                authorFollowTv.setEnabled(true);
                Toast.makeText(requireContext(), TextUtils.isEmpty(msg) ? getString(R.string.feed_action_unavailable) : msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupKeyboardAvoidance() {
        if (rootView == null || inputBar == null) return;
        keyboardListener = () -> {
            if (rootView == null || inputBar == null) return;
            Rect rect = new Rect();
            rootView.getWindowVisibleDisplayFrame(rect);
            int rootHeight = rootView.getRootView() == null ? rootView.getHeight() : rootView.getRootView().getHeight();
            int keyboardHeight = Math.max(0, rootHeight - rect.bottom);
            boolean keyboardVisible = keyboardHeight > dp(140);
            int offset = keyboardVisible ? keyboardHeight : 0;
            inputBar.setTranslationY(-offset);
            if (recordHintTv != null) recordHintTv.setTranslationY(-offset);
            if (commentRecyclerView != null) {
                int bottom = recyclerBasePaddingBottom + (keyboardVisible ? keyboardHeight + dp(58) : 0);
                if (commentRecyclerView.getPaddingBottom() != bottom) {
                    commentRecyclerView.setPadding(
                            commentRecyclerView.getPaddingLeft(),
                            commentRecyclerView.getPaddingTop(),
                            commentRecyclerView.getPaddingRight(),
                            bottom
                    );
                }
            }
        };
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);
    }

    private void bindInputBar() {
        updateActionButton();
        actionBtn.setOnClickListener(v -> {
            if (hasInputText()) sendCommentFromInput();
        });
        actionBtn.setOnTouchListener((v, event) -> {
            if (hasInputText()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return startRecord(event);
                case MotionEvent.ACTION_MOVE:
                    if (!recording) return true;
                    recordCancel = recordStartX - event.getRawX() > dp(72) || recordStartY - event.getRawY() > dp(96);
                    updateRecordHint();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (recording) finishRecord(recordCancel);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (recording) finishRecord(true);
                    return true;
                default:
                    return true;
            }
        });
        editText.setSingleLine(false);
        editText.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setOnEditorActionListener((v, actionId, event) -> {
            boolean imeSend = actionId == EditorInfo.IME_ACTION_SEND;
            boolean enterSend = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && !event.isShiftPressed();
            if ((imeSend || enterSend) && hasInputText()) {
                sendCommentFromInput();
                return true;
            }
            return false;
        });
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) expandCommentSheet(true);
        });
        editText.setOnClickListener(v -> expandCommentSheet(true));
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateActionButton(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private boolean hasInputText() {
        return editText != null && !TextUtils.isEmpty(editText.getText().toString().trim());
    }

    private void updateActionButton() {
        if (actionBtn == null) return;
        if (hasInputText()) {
            actionBtn.setImageResource(R.drawable.ic_feed_send_white);
            actionBtn.setBackgroundResource(R.drawable.bg_feed_comment_send_button);
            actionBtn.setContentDescription(getString(R.string.feed_comment_send));
        } else {
            actionBtn.setImageResource(R.drawable.ic_feed_mic_white);
            actionBtn.setBackgroundResource(R.drawable.bg_feed_comment_voice_button);
            actionBtn.setContentDescription(getString(R.string.feed_voice_hold_to_talk));
        }
    }

    private boolean startRecord(MotionEvent event) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            Toast.makeText(requireContext(), R.string.feed_voice_permission_required, Toast.LENGTH_SHORT).show();
            return true;
        }
        try {
            hideKeyboard();
            expandCommentSheet(true);
            File dir = new File(requireContext().getExternalCacheDir(), "feed_voice");
            if (!dir.exists()) dir.mkdirs();
            recordPath = new File(dir, "comment_" + System.currentTimeMillis() + ".amr").getAbsolutePath();
            recordStartX = event.getRawX();
            recordStartY = event.getRawY();
            recordStartTime = System.currentTimeMillis();
            recordCancel = false;
            recording = true;
            if (actionBtn != null) {
                actionBtn.setSelected(true);
                actionBtn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            AudioRecordManager.getInstance().init(recordPath);
            AudioRecordManager.getInstance().startRecord();
            if (recordWaveView != null) {
                recordWaveView.setText(getString(R.string.feed_voice_recording_title));
                recordWaveView.startRecord();
            }
            updateRecordHint();
            updateRecordTimer();
            mainHandler.post(recordTickRunnable);
            mainHandler.postDelayed(maxRecordRunnable, MAX_RECORD_MS);
            return true;
        } catch (Throwable e) {
            recording = false;
            hideRecordHint();
            Toast.makeText(requireContext(), R.string.feed_voice_record_failed, Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private void updateRecordHint() {
        if (recordPanel != null) recordPanel.setVisibility(View.VISIBLE);
        if (recordHintTv != null) {
            recordHintTv.setVisibility(View.VISIBLE);
            recordHintTv.setText(recordCancel ? R.string.feed_voice_release_cancel : R.string.feed_voice_slide_cancel);
            recordHintTv.setTextColor(recordCancel ? 0xFFEF4444 : 0xFF6B7280);
        }
        if (recordCancelTv != null) {
            recordCancelTv.setText(recordCancel ? R.string.feed_voice_release_cancel : R.string.feed_voice_drag_cancel);
            recordCancelTv.setTextColor(recordCancel ? 0xFFFFFFFF : 0xFFEF4444);
            recordCancelTv.setBackgroundResource(recordCancel ? R.drawable.bg_feed_comment_record_cancel_active : R.drawable.bg_feed_comment_record_cancel);
        }
        if (recordWaveView != null) {
            recordWaveView.setText(recordCancel ? getString(R.string.feed_voice_release_cancel) : getString(R.string.feed_voice_release_send));
        }
    }

    private void updateRecordTimer() {
        if (recordTimerTv == null || !recording) return;
        long durationMs = Math.max(0, System.currentTimeMillis() - recordStartTime);
        int sec = Math.min((int) Math.ceil(durationMs / 1000.0), (int) (MAX_RECORD_MS / 1000L));
        recordTimerTv.setText(formatRecordTime(sec) + " / 01:00");
    }

    private String formatRecordTime(int seconds) {
        int sec = Math.max(0, seconds);
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60);
    }


    private void finishRecord(boolean cancel) {
        if (!recording) return;
        recording = false;
        mainHandler.removeCallbacks(maxRecordRunnable);
        mainHandler.removeCallbacks(recordTickRunnable);
        long durationMs = Math.max(0, System.currentTimeMillis() - recordStartTime);
        String file = recordPath;
        byte[] waveform = AudioRecordManager.getInstance().getDbs();
        recordWaveform = Base64.encodeToString(waveform == null ? new byte[0] : waveform, Base64.NO_WRAP);
        if (cancel) {
            AudioRecordManager.getInstance().cancelRecord();
            hideRecordHint();
            if (!TextUtils.isEmpty(file)) new File(file).delete();
            return;
        }
        AudioRecordManager.getInstance().stopRecord();
        hideRecordHint();
        if (durationMs < MIN_RECORD_MS) {
            if (!TextUtils.isEmpty(file)) new File(file).delete();
            Toast.makeText(requireContext(), R.string.feed_voice_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        int seconds = Math.max(1, (int) Math.ceil(durationMs / 1000.0));
        sendVoiceComment(file, seconds, recordWaveform);
    }

    private void hideRecordHint() {
        mainHandler.removeCallbacks(recordTickRunnable);
        if (recordWaveView != null) recordWaveView.stopRecord();
        if (recordPanel != null) recordPanel.setVisibility(View.GONE);
        if (recordHintTv != null) recordHintTv.setVisibility(View.GONE);
        if (actionBtn != null) actionBtn.setSelected(false);
    }

    private void hideKeyboard() {
        try {
            if (editText != null) editText.clearFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getView() != null) imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    private void expandCommentSheet(boolean forceFull) {
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        }
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;
        int height = calculateSheetHeight(forceFull);
        ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            bottomSheet.setLayoutParams(lp);
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setSkipCollapsed(true);
        behavior.setPeekHeight(height);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private int calculateSheetHeight(boolean forceFull) {
        int screen = getResources().getDisplayMetrics().heightPixels;
        int full = Math.max(dp(560), screen - getStatusBarHeight());
        int loadedCount = adapter == null ? 0 : adapter.getItemCount();
        boolean contentNeedsFull = commentCount >= 4 || loadedCount >= 4 || (!TextUtils.isEmpty(caption) && caption.length() > 60);
        if (forceFull || contentNeedsFull) return full;
        int compact = (int) (screen * 0.84f);
        return Math.min(full, Math.max(compact, dp(560)));
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    @Override
    public void onStart() {
        super.onStart();
        expandCommentSheet(false);
    }

    private void updateTitle() {
        if (titleTv == null) return;
        if (commentCount > 0) titleTv.setText(getString(R.string.feed_comment_title_count, commentCount));
        else titleTv.setText(getString(R.string.feed_comment_title));
    }

    private void loadComments(boolean first) {
        if (TextUtils.isEmpty(feedId) || loading || (!hasMore && !first)) return;
        loading = true;
        FeedModel.getInstance().comments(feedId, first ? "" : cursor, new IRequestResultListener<CommentListResponse>() {
            @Override
            public void onSuccess(CommentListResponse result) {
                loading = false;
                if (result == null) return;
                cursor = result.cursor;
                hasMore = result.has_more == 1 && !TextUtils.isEmpty(cursor);
                if (first) adapter.submitList(result.safeList());
                else adapter.appendList(result.safeList());
                expandCommentSheet(false);
            }

            @Override
            public void onFail(int code, String msg) {
                loading = false;
            }
        });
    }

    private void sendCommentFromInput() {
        String content = editText == null ? "" : editText.getText().toString().trim();
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(feedId)) return;
        editText.setText("");
        editText.setHint(R.string.feed_comment_hint);
        CommentBean local = createLocalComment(content, null);
        adapter.addFirst(local);
        commentCount++;
        updateTitle();
        if (onCommentSentListener != null) onCommentSentListener.onCommentSent(1);
        sendLocalComment(local, true);
    }

    private void sendVoiceComment(String localPath, int seconds, String waveform) {
        if (TextUtils.isEmpty(localPath) || TextUtils.isEmpty(feedId)) return;
        String localContent = buildVoiceContent("voice_local", localPath, seconds, waveform);
        CommentBean local = createLocalComment(localContent, null);
        adapter.addFirst(local);
        commentCount++;
        updateTitle();
        if (onCommentSentListener != null) onCommentSentListener.onCommentSent(1);
        voiceExecutor.execute(() -> {
            try {
                String remotePath = uploadVoiceFile(new File(localPath));
                String remoteContent = buildVoiceContent("voice", remotePath, seconds, waveform);
                mainHandler.post(() -> {
                    adapter.updateLocalContent(local.comment_id, remoteContent);
                    local.content = remoteContent;
                    sendLocalComment(local, true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    adapter.markLocalFailed(local.comment_id);
                    commentCount = Math.max(0, commentCount - 1);
                    updateTitle();
                    if (onCommentSentListener != null) onCommentSentListener.onCommentSent(-1);
                    Toast.makeText(requireContext(), TextUtils.isEmpty(e.getMessage()) ? getString(R.string.feed_voice_upload_failed) : e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String buildVoiceContent(String scheme, String path, int seconds, String waveform) {
        return scheme + ":" + (path == null ? "" : path) + "|" + Math.max(1, seconds) + "|" + (waveform == null ? "" : waveform);
    }

    private void retryLocalComment(CommentBean local) {
        if (local == null || TextUtils.isEmpty(local.content)) return;
        local.local_sending = true;
        local.local_failed = false;
        adapter.markLocalSending(local.comment_id);
        sendLocalComment(local, false);
    }

    private CommentBean createLocalComment(String content, String parentId) {
        CommentBean local = new CommentBean();
        local.comment_id = "local_" + System.currentTimeMillis();
        local.parent_id = parentId;
        local.uid = WKConfig.getInstance().getUid();
        local.name = getString(R.string.feed_comment_me);
        local.content = content;
        local.created_at = System.currentTimeMillis();
        local.local_sending = true;
        return local;
    }

    private void sendLocalComment(CommentBean local, boolean countRollbackOnFail) {
        FeedModel.getInstance().sendComment(feedId, local.content, new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                adapter.markLocalSent(local.comment_id);
            }

            @Override
            public void onFail(int code, String msg) {
                adapter.markLocalFailed(local.comment_id);
                if (countRollbackOnFail) {
                    commentCount = Math.max(0, commentCount - 1);
                    updateTitle();
                    if (onCommentSentListener != null) onCommentSentListener.onCommentSent(-1);
                }
            }
        });
    }

    private String uploadVoiceFile(File file) throws Exception {
        if (file == null || !file.exists()) throw new IllegalStateException(getString(R.string.feed_publish_file_missing));
        FeedModel.FeedUploadUrl uploadUrl = awaitUploadUrl(file.getAbsolutePath());
        if (uploadUrl == null || TextUtils.isEmpty(uploadUrl.url)) throw new IllegalStateException(getString(R.string.feed_publish_upload_url_failed));
        String tag = "feed_voice_" + UUID.randomUUID();
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
                error.set(new IllegalStateException(getString(R.string.feed_voice_upload_failed)));
                latch.countDown();
            }
        });
        if (!latch.await(90, TimeUnit.SECONDS)) throw new IllegalStateException(getString(R.string.feed_publish_upload_timeout));
        if (error.get() != null) throw error.get();
        return normalizeUploadedPath(resultPath.get());
    }

    private FeedModel.FeedUploadUrl awaitUploadUrl(String localPath) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FeedModel.FeedUploadUrl> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        FeedModel.getInstance().getFeedUploadFileUrl(localPath, "audio", new IRequestResultListener<FeedModel.FeedUploadUrl>() {
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

    private String normalizeUploadedPath(String path) {
        if (TextUtils.isEmpty(path)) return "";
        String v = path.trim();
        if (v.startsWith(WKApiConfig.baseUrl)) v = v.substring(WKApiConfig.baseUrl.length());
        if (v.startsWith("/")) v = v.substring(1);
        if (v.startsWith("file/preview/")) return v;
        if (v.startsWith("common/")) return "file/preview/" + v;
        if (v.startsWith("feed/")) return "file/preview/common/" + v;
        return v;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroyView() {
        if (recording) finishRecord(true);
        mainHandler.removeCallbacks(maxRecordRunnable);
        mainHandler.removeCallbacks(recordTickRunnable);
        if (rootView != null && keyboardListener != null && rootView.getViewTreeObserver().isAlive()) {
            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardListener);
        }
        voiceExecutor.shutdownNow();
        super.onDestroyView();
    }
}
