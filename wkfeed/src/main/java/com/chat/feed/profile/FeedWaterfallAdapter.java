package com.chat.feed.profile;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.chat.feed.R;
import com.chat.feed.model.FeedBean;
import com.chat.feed.model.FeedMedia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeedWaterfallAdapter extends RecyclerView.Adapter<FeedWaterfallAdapter.VH> {
    public interface OnItemClickListener { void onItemClick(FeedBean item, int position); }

    private final ArrayList<FeedBean> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public FeedWaterfallAdapter(OnItemClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<FeedBean> list) {
        ArrayList<FeedBean> next = dedupe(list, null);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).stableKey().equals(next.get(newItemPosition).stableKey());
            }
            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return sameContent(items.get(oldItemPosition), next.get(newItemPosition));
            }
        }, false);
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    public void append(List<FeedBean> list) {
        if (list == null || list.isEmpty()) return;
        Set<String> existing = new HashSet<>();
        for (FeedBean item : items) {
            if (item != null) existing.add(item.stableKey());
        }
        ArrayList<FeedBean> added = dedupe(list, existing);
        if (added.isEmpty()) return;
        int start = items.size();
        items.addAll(added);
        notifyItemRangeInserted(start, added.size());
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
        int parentWidth = 0;
        if (holder.itemView.getParent() instanceof View) {
            parentWidth = ((View) holder.itemView.getParent()).getWidth();
        }
        if (parentWidth <= 0) parentWidth = holder.itemView.getResources().getDisplayMetrics().widthPixels;
        int cardWidth = Math.max(dp(holder.itemView, 120), (parentWidth - dp(holder.itemView, 26)) / 2);
        int height = dp(holder.itemView, 230);
        String coverUrl = "";
        if (media != null) {
            float ratio = media.ratio();
            height = Math.max(dp(holder.itemView, 180),
                    Math.min(dp(holder.itemView, 330), Math.round(cardWidth * ratio)));
            coverUrl = media.thumbUrl();
        }

        if (TextUtils.isEmpty(coverUrl)) {
            clearImageSafely(holder.coverIv);
        } else {
            Glide.with(holder.coverIv)
                    .load(coverUrl)
                    .centerCrop()
                    .dontAnimate()
                    .into(holder.coverIv);
        }

        ViewGroup.LayoutParams coverParams = holder.coverIv.getLayoutParams();
        if (coverParams.height != height) {
            coverParams.height = height;
            holder.coverIv.setLayoutParams(coverParams);
        }

        holder.playIconIv.setVisibility(item.isVideo() || item.isTikTok() ? View.VISIBLE : View.GONE);

        int count = item.safeMedia().size();
        holder.imageCountTv.setVisibility(!item.isVideo() && !item.isTikTok() && count > 1
                ? View.VISIBLE : View.GONE);
        holder.imageCountTv.setText("1/" + count);
        holder.titleTv.setText(item.displayTitle());
        holder.likeTv.setText(item.like_count > 0 ? "♡ " + formatCount(item.like_count) : "♡");
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            FeedBean current = getItem(adapterPosition);
            if (listener != null && current != null) listener.onItemClick(current, adapterPosition);
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        // RecyclerView may recycle holders while the host Activity is already being destroyed.
        // Never use Glide.with(view/activity) here because Glide will reject a destroyed Activity.
        clearImageSafely(holder.coverIv);
        holder.itemView.animate().cancel();
        holder.itemView.setOnClickListener(null);
        super.onViewRecycled(holder);
    }

    /**
     * Clears a Glide request without binding the operation to the Activity lifecycle.
     *
     * <p>During Activity/Fragment teardown, RecyclerView#setAdapter(null) recycles every
     * ViewHolder. The ImageView context can still be the destroyed Activity at that moment,
     * so Glide.with(imageView) would throw "You cannot start a load for a destroyed activity".
     * Using the application context avoids that lifecycle lookup while still cancelling the
     * request associated with this ImageView.</p>
     */
    private void clearImageSafely(@NonNull ImageView imageView) {
        imageView.animate().cancel();

        Context context = imageView.getContext();
        Context appContext = context == null ? null : context.getApplicationContext();
        if (appContext != null) {
            Glide.with(appContext).clear(imageView);
        }

        // Also remove the currently displayed drawable. If an unusual Context does not expose
        // an application context, this still prevents a recycled card from showing stale media.
        imageView.setImageDrawable(null);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).stableId();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private ArrayList<FeedBean> dedupe(List<FeedBean> source, Set<String> existing) {
        ArrayList<FeedBean> result = new ArrayList<>();
        Set<String> keys = existing == null ? new HashSet<>() : new HashSet<>(existing);
        if (source == null) return result;
        for (FeedBean item : source) {
            if (item == null) continue;
            String key = item.stableKey();
            if (keys.add(key)) result.add(item);
        }
        return result;
    }

    private boolean sameContent(FeedBean oldItem, FeedBean newItem) {
        if (oldItem == newItem) return true;
        if (oldItem == null || newItem == null) return false;
        if (oldItem.like_count != newItem.like_count
                || oldItem.comment_count != newItem.comment_count
                || oldItem.liked != newItem.liked
                || oldItem.isTikTok() != newItem.isTikTok()
                || oldItem.isVideo() != newItem.isVideo()
                || !TextUtils.equals(oldItem.displayTitle(), newItem.displayTitle())) {
            return false;
        }
        List<FeedMedia> oldMedia = oldItem.safeMedia();
        List<FeedMedia> newMedia = newItem.safeMedia();
        if (oldMedia.size() != newMedia.size()) return false;
        FeedMedia oldFirst = oldItem.firstMedia();
        FeedMedia newFirst = newItem.firstMedia();
        if (oldFirst == newFirst) return true;
        if (oldFirst == null || newFirst == null) return false;
        return oldFirst.width == newFirst.width
                && oldFirst.height == newFirst.height
                && TextUtils.equals(oldFirst.thumbUrl(), newFirst.thumbUrl())
                && TextUtils.equals(oldFirst.tiktokVideoId(), newFirst.tiktokVideoId())
                && TextUtils.equals(oldFirst.tiktokSourceUrl(), newFirst.tiktokSourceUrl());
    }

    private String formatCount(int count) {
        if (count < 10000) return String.valueOf(count);
        return (count / 10000) + "w";
    }

    private int dp(View view, int value) {
        return (int) (view.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView coverIv;
        final ImageView playIconIv;
        final TextView imageCountTv;
        final TextView titleTv;
        final TextView likeTv;

        VH(@NonNull View itemView) {
            super(itemView);
            coverIv = itemView.findViewById(R.id.coverIv);
            playIconIv = itemView.findViewById(R.id.playIconIv);
            imageCountTv = itemView.findViewById(R.id.imageCountTv);
            titleTv = itemView.findViewById(R.id.titleTv);
            likeTv = itemView.findViewById(R.id.waterfallLikeTv);
        }
    }
}
