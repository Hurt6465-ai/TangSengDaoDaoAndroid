package com.chat.feed.browse;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.feed.model.FeedBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedPagerAdapter extends RecyclerView.Adapter<FeedPagerAdapter.VH> {
    private final List<FeedBean> items = new ArrayList<>();
    private final Map<String, Long> keyToId = new HashMap<>();
    private long nextId = 1L;

    public FeedPagerAdapter() {
        setHasStableIds(true);
    }

    public void append(List<FeedBean> list) {
        if (list == null || list.isEmpty()) return;
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public void add(FeedBean item) {
        if (item == null) return;
        int position = items.size();
        items.add(item);
        notifyItemInserted(position);
    }

    public void clear() {
        int count = items.size();
        items.clear();
        keyToId.clear();
        nextId = 1L;
        if (count > 0) notifyItemRangeRemoved(0, count);
    }

    public FeedBean getItem(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    public int indexOfFeedId(String feedId) {
        if (feedId == null || feedId.length() == 0) return -1;
        for (int i = 0; i < items.size(); i++) {
            FeedBean item = items.get(i);
            if (item != null && feedId.equals(item.stableKey())) return i;
        }
        return -1;
    }

    public List<FeedBean> getItems() {
        return items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FeedItemView view = new FeedItemView(parent.getContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.feedView.bind(items.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        holder.feedView.recycle();
        super.onViewRecycled(holder);
    }

    @Override
    public long getItemId(int position) {
        FeedBean item = getItem(position);
        if (item == null) return RecyclerView.NO_ID;
        String key = item.stableKey();
        if (key == null || key.length() == 0) return RecyclerView.NO_ID;
        Long existing = keyToId.get(key);
        if (existing != null) return existing;
        long id = nextId++;
        if (id == RecyclerView.NO_ID) id = nextId++;
        keyToId.put(key, id);
        return id;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setActivePosition(RecyclerView recyclerView, int position) {
        if (recyclerView == null) return;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (holder instanceof VH) {
                int adapterPosition = holder.getBindingAdapterPosition();
                ((VH) holder).feedView.setActive(adapterPosition != RecyclerView.NO_POSITION && adapterPosition == position);
            }
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        FeedItemView feedView;
        VH(@NonNull FeedItemView itemView) {
            super(itemView);
            feedView = itemView;
        }
    }
}
