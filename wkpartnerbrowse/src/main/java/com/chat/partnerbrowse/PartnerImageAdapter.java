package com.chat.partnerbrowse;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.base.config.WKApiConfig;

import java.util.ArrayList;
import java.util.List;

public class PartnerImageAdapter extends RecyclerView.Adapter<PartnerImageAdapter.VH> {
    private static final int COLOR_PLACEHOLDER = 0xFF1A1A1A;
    private final ArrayList<String> images = new ArrayList<>();

    public PartnerImageAdapter(List<String> source) {
        if (source != null) images.addAll(source);
        if (images.isEmpty()) images.add("");
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView imageView = new ImageView(parent.getContext());
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundColor(Color.BLACK);
        return new VH(imageView);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String imageUrl = images.get(position);
        if (TextUtils.isEmpty(imageUrl)) {
            holder.imageView.setBackgroundColor(Color.BLACK);
            holder.imageView.setImageDrawable(new ColorDrawable(COLOR_PLACEHOLDER));
            return;
        }
        int width = holder.imageView.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = holder.imageView.getResources().getDisplayMetrics().heightPixels;
        int height = Math.min(screenHeight, Math.max(width, Math.round(width * 1.80f)));
        Glide.with(holder.imageView)
                .load(showUrl(imageUrl))
                .thumbnail(0.12f)
                .centerCrop()
                .override(width, height)
                .placeholder(new ColorDrawable(COLOR_PLACEHOLDER))
                .error(new ColorDrawable(COLOR_PLACEHOLDER))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .into(holder.imageView);
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        Glide.with(holder.imageView).clear(holder.imageView);
        holder.imageView.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    private String showUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        return WKApiConfig.getShowUrl(value);
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imageView;

        VH(@NonNull ImageView itemView) {
            super(itemView);
            imageView = itemView;
        }
    }
}
