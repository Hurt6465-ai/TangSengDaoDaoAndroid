package com.chat.feed.comment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.ui.components.AvatarView;
import com.chat.feed.R;
import com.chat.feed.model.CommentBean;
import com.xinbida.wukongim.entity.WKChannelType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeedCommentAdapter extends RecyclerView.Adapter<FeedCommentAdapter.VH> {
    public interface CommentActionListener {
        void onReplyClick(CommentBean item, int position);
        void onLoadReplies(CommentBean item, int position);
        void onRetryLocal(CommentBean item, int position);
    }

    private final ArrayList<CommentBean> list = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private CommentActionListener actionListener;

    public FeedCommentAdapter() {
        setHasStableIds(true);
    }

    public void setActionListener(CommentActionListener listener) {
        this.actionListener = listener;
    }

    public void submitList(List<CommentBean> newList) {
        ArrayList<CommentBean> next = flatten(newList);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new FeedCommentDiffCallback(list, next));
        list.clear();
        list.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    public void appendList(List<CommentBean> moreList) {
        ArrayList<CommentBean> next = flatten(moreList);
        if (next.isEmpty()) return;
        int start = list.size();
        list.addAll(next);
        notifyItemRangeInserted(start, next.size());
    }

    private ArrayList<CommentBean> flatten(List<CommentBean> source) {
        ArrayList<CommentBean> out = new ArrayList<>();
        if (source == null) return out;
        for (CommentBean item : source) {
            if (item == null) continue;
            item.item_type = CommentBean.TYPE_COMMENT;
            out.add(item);
            List<CommentBean> children = item.safeChildren();
            for (CommentBean child : children) {
                if (child == null) continue;
                child.parent_id = item.comment_id;
                child.item_type = CommentBean.TYPE_REPLY;
                out.add(child);
            }
        }
        return out;
    }

    public void addFirst(CommentBean bean) {
        if (bean == null) return;
        list.add(0, bean);
        notifyItemInserted(0);
    }

    public void markLocalFailed(String commentId) {
        int index = indexOf(commentId);
        if (index < 0) return;
        CommentBean bean = list.get(index);
        bean.local_sending = false;
        bean.local_failed = true;
        notifyItemChanged(index);
    }

    public void markLocalSent(String commentId) {
        int index = indexOf(commentId);
        if (index < 0) return;
        CommentBean bean = list.get(index);
        bean.local_sending = false;
        bean.local_failed = false;
        notifyItemChanged(index);
    }

    public void markLocalSending(String commentId) {
        int index = indexOf(commentId);
        if (index < 0) return;
        CommentBean bean = list.get(index);
        bean.local_sending = true;
        bean.local_failed = false;
        notifyItemChanged(index);
    }

    private int indexOf(String commentId) {
        if (commentId == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (commentId.equals(list.get(i).comment_id)) return i;
        }
        return -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_comment, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CommentBean item = list.get(position);
        boolean reply = item.isReply();
        int start = dp(holder.itemView, reply ? 62 : 15);
        holder.root.setPadding(start, holder.root.getPaddingTop(), holder.root.getPaddingRight(), holder.root.getPaddingBottom());
        holder.avatar.setSize(reply ? 28 : 38);
        bindCommentAvatar(holder.avatar, item);
        holder.nameTv.setText(item.name == null ? "" : item.name);
        holder.contentTv.setText(item.content == null ? "" : item.content);
        holder.timeTv.setText(item.created_at > 0 ? sdf.format(new Date(item.created_at)) : "");
        holder.likeTv.setText(item.like_count > 0 ? String.valueOf(item.like_count) : "");

        if (item.local_sending) {
            holder.stateTv.setVisibility(View.VISIBLE);
            holder.stateTv.setText(R.string.feed_comment_sending);
            holder.stateTv.setTextColor(0xFF8A8F98);
        } else if (item.local_failed) {
            holder.stateTv.setVisibility(View.VISIBLE);
            holder.stateTv.setText(R.string.feed_comment_failed);
            holder.stateTv.setTextColor(0xFFEF4444);
        } else {
            holder.stateTv.setVisibility(View.GONE);
        }

        int missingReplyCount = Math.max(0, item.reply_count - item.safeChildren().size());
        holder.expandTv.setVisibility(!reply && missingReplyCount > 0 ? View.VISIBLE : View.GONE);
        holder.expandTv.setText(holder.itemView.getResources().getString(R.string.feed_comment_expand_replies, missingReplyCount));
        holder.expandTv.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onLoadReplies(item, holder.getBindingAdapterPosition());
        });
        holder.replyTv.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onReplyClick(item, holder.getBindingAdapterPosition());
        });
        holder.stateTv.setOnClickListener(v -> {
            if (item.local_failed && actionListener != null) actionListener.onRetryLocal(item, holder.getBindingAdapterPosition());
        });
    }

    @Override
    public long getItemId(int position) {
        return list.get(position).stableId();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }


    private void bindCommentAvatar(AvatarView avatar, CommentBean item) {
        if (avatar == null || item == null) return;
        try {
            if (item.uid != null && item.uid.length() > 0) {
                // 评论接口有时不带 avatar 字段；用唐僧原生头像接口按 uid 拉，避免评论区全是字母头像。
                avatar.showAvatar(item.uid, WKChannelType.PERSONAL, item.avatar_cache_key);
            } else if (item.avatar != null && item.avatar.length() > 0) {
                avatar.showAvatarUrl(item.avatar, item.avatar_cache_key, item.name, item.uid);
            } else {
                avatar.showDefaultAvatar(item.name, item.uid);
            }
        } catch (Throwable ignored) {
            avatar.showDefaultAvatar(item.name, item.uid);
        }
    }

    private int dp(View view, int value) {
        return (int) (view.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    static class VH extends RecyclerView.ViewHolder {
        View root;
        AvatarView avatar;
        TextView nameTv;
        TextView contentTv;
        TextView timeTv;
        TextView replyTv;
        TextView stateTv;
        TextView expandTv;
        TextView likeTv;
        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.commentRoot);
            avatar = itemView.findViewById(R.id.commentAvatar);
            nameTv = itemView.findViewById(R.id.commentNameTv);
            contentTv = itemView.findViewById(R.id.commentContentTv);
            timeTv = itemView.findViewById(R.id.commentTimeTv);
            replyTv = itemView.findViewById(R.id.commentReplyTv);
            stateTv = itemView.findViewById(R.id.commentStateTv);
            expandTv = itemView.findViewById(R.id.commentExpandTv);
            likeTv = itemView.findViewById(R.id.commentLikeTv);
        }
    }
}
