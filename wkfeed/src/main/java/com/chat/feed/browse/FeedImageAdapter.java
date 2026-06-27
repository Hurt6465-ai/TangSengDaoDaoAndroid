package com.chat.feed.browse;

import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.chat.feed.R;
import com.chat.feed.model.FeedMedia;

import java.util.ArrayList;
import java.util.List;

import jp.wasabeef.glide.transformations.BlurTransformation;

public class FeedImageAdapter extends RecyclerView.Adapter<FeedImageAdapter.VH> {
    private static final int PLACEHOLDER = 0xFF111111;
    private final List<FeedMedia> images = new ArrayList<>();

    public FeedImageAdapter(List<FeedMedia> data) {
        if (data != null) images.addAll(data);
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_image, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FeedMedia media = images.get(position);
        int width = holder.itemView.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = holder.itemView.getResources().getDisplayMetrics().heightPixels;
        int height = Math.min(screenHeight, Math.max(width, Math.round(width * 1.8f)));
        ColorDrawable placeholder = new ColorDrawable(PLACEHOLDER);
        Glide.with(holder.blurBgIv)
                .load(media == null ? null : media.thumbUrl())
                .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3)))
                .override(120, 240)
                .placeholder(placeholder)
                .error(new ColorDrawable(PLACEHOLDER))
                .dontAnimate()
                .into(holder.blurBgIv);
        Glide.with(holder.mainIv)
                .load(media == null ? null : media.displayUrl())
                .fitCenter()
                .override(width, height)
                .placeholder(new ColorDrawable(PLACEHOLDER))
                .error(new ColorDrawable(PLACEHOLDER))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .into(holder.mainIv);
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        Glide.with(holder.blurBgIv).clear(holder.blurBgIv);
        Glide.with(holder.mainIv).clear(holder.mainIv);
        holder.blurBgIv.setImageDrawable(null);
        holder.mainIv.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    @Override
    public long getItemId(int position) {
        FeedMedia m = images.get(position);
        String key = (m == null ? "" : m.displayUrl()) + "_" + position;
        long h = 1469598103934665603L;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 1099511628211L;
        }
        return h == RecyclerView.NO_ID ? h ^ 0x5555555555555555L : h;
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView blurBgIv;
        ImageView mainIv;
        VH(@NonNull View itemView) {
            super(itemView);
            blurBgIv = itemView.findViewById(R.id.blurBgIv);
            mainIv = itemView.findViewById(R.id.mainIv);
        }
    }
}
