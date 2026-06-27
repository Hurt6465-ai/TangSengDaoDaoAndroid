package com.chat.feed.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.chat.base.ui.components.AvatarView;
import com.chat.feed.R;
import com.chat.feed.model.FeedBean;
import com.chat.feed.model.FeedMedia;
import com.chat.feed.model.FeedUser;

import java.util.ArrayList;
import java.util.List;

public class FeedWaterfallAdapter extends RecyclerView.Adapter<FeedWaterfallAdapter.VH> {
    public interface OnItemClickListener { void onItemClick(FeedBean item, int position); }
    private final ArrayList<FeedBean> items = new ArrayList<>();
    private OnItemClickListener listener;

    public FeedWaterfallAdapter(OnItemClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<FeedBean> list) {
        ArrayList<FeedBean> next = new ArrayList<>();
        if (list != null) next.addAll(list);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).stableKey().equals(next.get(newItemPosition).stableKey());
            }
            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FeedBean oldItem = items.get(oldItemPosition);
                FeedBean newItem = next.get(newItemPosition);
                return oldItem.like_count == newItem.like_count
                        && oldItem.comment_count == newItem.comment_count
                        && oldItem.liked == newItem.liked
                        && String.valueOf(oldItem.displayTitle()).equals(String.valueOf(newItem.displayTitle()));
            }
        });
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    public void append(List<FeedBean> list) {
        if (list == null || list.isEmpty()) return;
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public FeedBean getItem(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_waterfall, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FeedBean item = items.get(position);
        FeedMedia media = item.firstMedia();
        int screenWidth = holder.itemView.getResources().getDisplayMetrics().widthPixels;
        int cardWidth = (screenWidth - dp(holder.itemView, 26)) / 2;
        int height = dp(holder.itemView, 230);
        if (media != null) {
            float ratio = media.ratio();
            height = Math.max(dp(holder.itemView, 180), Math.min(dp(holder.itemView, 330), (int) (cardWidth * ratio)));
            Glide.with(holder.coverIv).load(media.thumbUrl()).centerCrop().dontAnimate().into(holder.coverIv);
        }
        holder.coverIv.getLayoutParams().height = height;
        holder.coverIv.requestLayout();
        holder.playIconIv.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        int count = item.safeMedia().size();
        holder.imageCountTv.setVisibility(!item.isVideo() && count > 1 ? View.VISIBLE : View.GONE);
        holder.imageCountTv.setText("1/" + count);
        holder.titleTv.setText(item.displayTitle());
        FeedUser user = item.user;
        if (user != null) {
            holder.authorAvatar.showAvatarUrl(user.avatar, user.avatar_cache_key, user.name, user.uid);
            holder.authorNameTv.setText(user.name == null ? "" : user.name);
        } else {
            holder.authorAvatar.showDefaultAvatar(item.userName());
            holder.authorNameTv.setText(item.userName());
        }
        holder.likeTv.setText(item.like_count > 0 ? "♡ " + formatCount(item.like_count) : "♡");
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item, holder.getBindingAdapterPosition());
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        Glide.with(holder.coverIv).clear(holder.coverIv);
        super.onViewRecycled(holder);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).stableId();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatCount(int count) {
        if (count < 10000) return String.valueOf(count);
        return (count / 10000) + "w";
    }

    private int dp(View view, int value) {
        return (int) (view.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView coverIv;
        ImageView playIconIv;
        TextView imageCountTv;
        TextView titleTv;
        AvatarView authorAvatar;
        TextView authorNameTv;
        TextView likeTv;
        VH(@NonNull View itemView) {
            super(itemView);
            coverIv = itemView.findViewById(R.id.coverIv);
            playIconIv = itemView.findViewById(R.id.playIconIv);
            imageCountTv = itemView.findViewById(R.id.imageCountTv);
            titleTv = itemView.findViewById(R.id.titleTv);
            authorAvatar = itemView.findViewById(R.id.authorAvatar);
            authorNameTv = itemView.findViewById(R.id.authorNameTv);
            likeTv = itemView.findViewById(R.id.waterfallLikeTv);
        }
    }
}
