package com.chat.feed.comment;

import androidx.recyclerview.widget.DiffUtil;

import com.chat.feed.model.CommentBean;

import java.util.List;

public class FeedCommentDiffCallback extends DiffUtil.Callback {
    private final List<CommentBean> oldList;
    private final List<CommentBean> newList;

    public FeedCommentDiffCallback(List<CommentBean> oldList, List<CommentBean> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList == null ? 0 : oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList == null ? 0 : newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).stableId() == newList.get(newItemPosition).stableId();
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        CommentBean oldItem = oldList.get(oldItemPosition);
        CommentBean newItem = newList.get(newItemPosition);
        return safe(oldItem.content).equals(safe(newItem.content))
                && oldItem.like_count == newItem.like_count
                && oldItem.liked == newItem.liked
                && oldItem.reply_count == newItem.reply_count
                && oldItem.local_sending == newItem.local_sending
                && oldItem.local_failed == newItem.local_failed
                && oldItem.item_type == newItem.item_type;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
