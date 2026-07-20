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
 * Reusable vertical thumbnail list for topic/comment/article media.
 *
 * Thumbnails use FIT_CENTER so the complete image remains visible. Tapping opens the original
 * images in the forum full-screen viewer instead of trying to show a cropped full image inline.
 */
final class ForumRemoteImageListView extends LinearLayout {
    private final List<String> boundThumbUrls = new ArrayList<>();
    private final List<String> boundFullUrls = new ArrayList<>();
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

        List<String> thumbs = new ArrayList<>();
        List<String> full = new ArrayList<>();
        if (source != null) {
            for (ForumApiClient.ImageInfo info : source) {
                if (info == null || TextUtils.isEmpty(info.url)) continue;
                String fullUrl = ForumApiClient.getInstance().resolveUrl(info.url);
                String thumbnailSource = TextUtils.isEmpty(info.preview) ? info.url : info.preview;
                String thumbUrl = ForumApiClient.getInstance().resolveUrl(thumbnailSource);
                if (TextUtils.isEmpty(fullUrl)) fullUrl = thumbUrl;
                if (TextUtils.isEmpty(thumbUrl)) thumbUrl = fullUrl;
                if (TextUtils.isEmpty(fullUrl) || TextUtils.isEmpty(thumbUrl)) continue;
                thumbs.add(thumbUrl);
                full.add(fullUrl);
            }
        }

        trimChildren(thumbs.size());
        ensureChildren(thumbs.size());
        ArrayList<String> viewerUrls = new ArrayList<>(full);
        for (int i = 0; i < thumbs.size(); i++) {
            ImageView image = (ImageView) getChildAt(i);
            String thumbUrl = thumbs.get(i);
            applyLayout(image, i);
            image.setBackgroundColor(placeholderColor);
            image.setContentDescription(ForumText.get(R.string.forum_view_image, i + 1, thumbs.size()));
            final int openIndex = i;
            image.setOnClickListener(v -> ForumImageViewerActivity.open(
                    getContext(), new ArrayList<>(viewerUrls), openIndex));
            String oldUrl = i < boundThumbUrls.size() ? boundThumbUrls.get(i) : null;
            if (!TextUtils.equals(oldUrl, thumbUrl)) {
                Glide.with(image).clear(image);
                Glide.with(image)
                        .load(thumbUrl)
                        .fitCenter()
                        .into(image);
            }
        }
        boundThumbUrls.clear();
        boundThumbUrls.addAll(thumbs);
        boundFullUrls.clear();
        boundFullUrls.addAll(full);
        setVisibility(thumbs.isEmpty() ? GONE : VISIBLE);
    }

    void recycle() {
        for (int i = 0; i < getChildCount(); i++) {
            ImageView image = (ImageView) getChildAt(i);
            Glide.with(image).clear(image);
            image.setImageDrawable(null);
            image.setOnClickListener(null);
        }
        boundThumbUrls.clear();
        boundFullUrls.clear();
    }

    private void trimChildren(int desired) {
        while (getChildCount() > desired) {
            int index = getChildCount() - 1;
            ImageView image = (ImageView) getChildAt(index);
            Glide.with(image).clear(image);
            image.setOnClickListener(null);
            removeViewAt(index);
        }
    }

    private void ensureChildren(int desired) {
        while (getChildCount() < desired) {
            ImageView image = new ImageView(getContext());
            image.setAdjustViewBounds(false);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setClickable(true);
            addView(image);
        }
    }

    private void applyLayout(ImageView image, int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, imageHeightPx);
        params.topMargin = imageTopMarginPx;
        image.setLayoutParams(params);
    }
}
