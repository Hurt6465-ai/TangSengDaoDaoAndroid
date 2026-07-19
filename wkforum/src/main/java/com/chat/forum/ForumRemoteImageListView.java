package com.chat.forum;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Small reusable vertical image list for topic/comment media.
 *
 * RecyclerView rows are rebound frequently. Reusing the existing ImageViews avoids allocating a
 * new view tree and restarting every Glide request whenever only likes or reply counts change.
 */
final class ForumRemoteImageListView extends LinearLayout {
    private final List<String> boundUrls = new ArrayList<>();
    private int imageHeightPx;
    private int imageTopMarginPx;
    private int placeholderColor = Color.TRANSPARENT;

    ForumRemoteImageListView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setVisibility(GONE);
    }

    void bind(List<ForumApiClient.ImageInfo> source, int imageHeightPx,
              int imageTopMarginPx, int placeholderColor) {
        this.imageHeightPx = Math.max(1, imageHeightPx);
        this.imageTopMarginPx = Math.max(0, imageTopMarginPx);
        this.placeholderColor = placeholderColor;

        List<String> urls = new ArrayList<>();
        if (source != null) {
            for (ForumApiClient.ImageInfo info : source) {
                if (info == null || TextUtils.isEmpty(info.url)) continue;
                String remote = TextUtils.isEmpty(info.preview) ? info.url : info.preview;
                String resolved = ForumApiClient.getInstance().resolveUrl(remote);
                if (!TextUtils.isEmpty(resolved)) urls.add(resolved);
            }
        }

        trimChildren(urls.size());
        ensureChildren(urls.size());
        for (int i = 0; i < urls.size(); i++) {
            ImageView image = (ImageView) getChildAt(i);
            String url = urls.get(i);
            applyLayout(image, i);
            image.setBackgroundColor(placeholderColor);
            String oldUrl = i < boundUrls.size() ? boundUrls.get(i) : null;
            if (!TextUtils.equals(oldUrl, url)) {
                Glide.with(image).clear(image);
                Glide.with(image)
                        .load(url)
                        .centerCrop()
                        .into(image);
            }
        }
        boundUrls.clear();
        boundUrls.addAll(urls);
        setVisibility(urls.isEmpty() ? GONE : VISIBLE);
    }

    void recycle() {
        for (int i = 0; i < getChildCount(); i++) {
            ImageView image = (ImageView) getChildAt(i);
            Glide.with(image).clear(image);
            image.setImageDrawable(null);
        }
        boundUrls.clear();
    }

    private void trimChildren(int desired) {
        while (getChildCount() > desired) {
            int index = getChildCount() - 1;
            ImageView image = (ImageView) getChildAt(index);
            Glide.with(image).clear(image);
            removeViewAt(index);
        }
    }

    private void ensureChildren(int desired) {
        while (getChildCount() < desired) {
            ImageView image = new ImageView(getContext());
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(image);
        }
    }

    private void applyLayout(ImageView image, int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, imageHeightPx);
        params.topMargin = index == 0 ? imageTopMarginPx : imageTopMarginPx;
        image.setLayoutParams(params);
    }
}
