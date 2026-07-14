package com.chat.feedlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.feedlist.databinding.ActivityFeedImageViewerBinding;

import java.util.ArrayList;

public class FeedImageViewerActivity extends Activity {
    private static final String EXTRA_URLS = "urls";
    private static final String EXTRA_INDEX = "index";
    private ActivityFeedImageViewerBinding binding;

    public static void open(Context context, ArrayList<String> urls, int index) {
        if (context == null || urls == null || urls.isEmpty()) return;
        Intent intent = new Intent(context, FeedImageViewerActivity.class);
        intent.putStringArrayListExtra(EXTRA_URLS, urls);
        intent.putExtra(EXTRA_INDEX, Math.max(0, Math.min(index, urls.size() - 1)));
        context.startActivity(intent);
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFeedImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null || urls.isEmpty()) { finish(); return; }
        binding.pager.setAdapter(new ImageAdapter(urls));
        int index = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_INDEX, 0), urls.size() - 1));
        binding.pager.setCurrentItem(index, false);
        updateIndicator(index, urls.size());
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateIndicator(position, urls.size()); }
        });
        binding.closeBtn.setOnClickListener(v -> finish());
    }

    private void updateIndicator(int position, int size) { binding.indicator.setText((position + 1) + "/" + size); }

    private static final class ImageAdapter extends RecyclerView.Adapter<ImageHolder> {
        private final ArrayList<String> urls;
        ImageAdapter(ArrayList<String> urls) { this.urls = urls; }
        @NonNull @Override public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView view = new ImageView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setBackgroundColor(0xFF000000);
            return new ImageHolder(view);
        }
        @Override public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
            Glide.with(holder.image).load(urls.get(position)).fitCenter().diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(holder.image);
        }
        @Override public void onViewRecycled(@NonNull ImageHolder holder) { Glide.with(holder.image).clear(holder.image); }
        @Override public int getItemCount() { return urls.size(); }
    }
    private static final class ImageHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        ImageHolder(ImageView image) { super(image); this.image = image; }
    }
}
