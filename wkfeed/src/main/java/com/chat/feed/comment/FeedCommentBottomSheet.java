package com.chat.feed.comment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.config.WKConfig;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.feed.FeedModel;
import com.chat.feed.R;
import com.chat.feed.model.CommentBean;
import com.chat.feed.model.CommentListResponse;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class FeedCommentBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_FEED_ID = "feed_id";
    private static final String ARG_COUNT = "count";
    private static final String ARG_AUTHOR = "author";
    private static final String ARG_CAPTION = "caption";
    private String feedId;
    private String authorName = "";
    private String caption = "";
    private String cursor = "";
    private boolean loading;
    private boolean hasMore = true;
    private int commentCount;
    private FeedCommentAdapter adapter;
    private EditText editText;
    private TextView titleTv;
    private OnCommentSentListener onCommentSentListener;

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
        FeedCommentBottomSheet sheet = new FeedCommentBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_FEED_ID, feedId);
        args.putInt(ARG_COUNT, count);
        args.putString(ARG_AUTHOR, author == null ? "" : author);
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
        feedId = getArguments() == null ? "" : getArguments().getString(ARG_FEED_ID, "");
        commentCount = getArguments() == null ? 0 : getArguments().getInt(ARG_COUNT, 0);
        authorName = getArguments() == null ? "" : getArguments().getString(ARG_AUTHOR, "");
        caption = getArguments() == null ? "" : getArguments().getString(ARG_CAPTION, "");
        adapter = new FeedCommentAdapter();
        RecyclerView recyclerView = view.findViewById(R.id.commentRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!hasMore || loading) return;
                int last = layoutManager.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 5) loadComments(false);
            }
        });
        titleTv = view.findViewById(R.id.commentTitleTv);
        TextView captionTv = view.findViewById(R.id.commentCaptionTv);
        if (captionTv != null) {
            if (TextUtils.isEmpty(caption)) {
                captionTv.setVisibility(View.GONE);
            } else {
                String prefix = TextUtils.isEmpty(authorName) ? "" : "@" + authorName + "  ";
                captionTv.setText(prefix + caption);
                captionTv.setVisibility(View.VISIBLE);
            }
        }
        editText = view.findViewById(R.id.commentEditText);
        ImageButton closeBtn = view.findViewById(R.id.commentCloseBtn);
        ImageButton sendBtn = view.findViewById(R.id.commentSendBtn);
        closeBtn.setOnClickListener(v -> dismissAllowingStateLoss());
        sendBtn.setOnClickListener(v -> sendCommentFromInput());
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
                // v2 先支持服务端随评论返回 children 的二级回复展示；独立 replies 分页等后端接口齐了再接。
            }

            @Override
            public void onRetryLocal(CommentBean item, int position) {
                if (item != null) retryLocalComment(item);
            }
        });
        updateTitle();
        loadComments(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.68f);
                bottomSheet.getLayoutParams().height = height;
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setPeekHeight(height);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
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
}
