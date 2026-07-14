package com.chat.feedlist;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.feedlist.model.FeedListMedia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed 1-6 image layout. 3 = large left + 2 right; 5 = large left + 4 right. */
public class FeedListMediaGridLayout extends ViewGroup {
    public interface Listener { void onImageClick(int index, List<FeedListMedia> media); }

    private static final int MAX = 6;
    private final ImageView[] views = new ImageView[MAX];
    private final int gap;
    private List<FeedListMedia> media = Collections.emptyList();
    private Listener listener;
    private int measuredContentHeight;
    private String mediaSignature = "";
    private boolean pendingLoad;

    public FeedListMediaGridLayout(Context context) { this(context, null); }

    public FeedListMediaGridLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gap = dp(3);
        setClipChildren(true);
        setClipToPadding(true);
        for (int i = 0; i < MAX; i++) {
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(new ColorDrawable(context.getColor(R.color.feedlist_media_placeholder)));
            image.setVisibility(GONE);
            image.setContentDescription(context.getString(R.string.feedlist_image));
            final int index = i;
            image.setOnClickListener(v -> {
                if (listener != null && index < media.size()) listener.onImageClick(index, new ArrayList<>(media));
            });
            views[i] = image;
            addView(image);
        }
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void bind(List<FeedListMedia> input) {
        ArrayList<FeedListMedia> images = new ArrayList<>();
        if (input != null) {
            for (FeedListMedia item : input) {
                if (item != null && !item.isVideo() && !item.isTikTok()
                        && (!TextUtils.isEmpty(item.displayUrl()) || !TextUtils.isEmpty(item.thumbUrl()))) {
                    images.add(item);
                }
                if (images.size() == MAX) break;
            }
        }
        String nextSignature = signature(images);
        boolean changed = !TextUtils.equals(mediaSignature, nextSignature);
        if (changed) clearImageRequests();
        media = images;
        mediaSignature = nextSignature;
        setVisibility(media.isEmpty() ? GONE : VISIBLE);
        for (int i = 0; i < MAX; i++) views[i].setVisibility(i < media.size() ? VISIBLE : GONE);
        // RecyclerView may bind the same holder again before the queued Glide task runs.
        // Always keep a non-empty grid pending so the second bind cannot cancel first paint.
        pendingLoad = !media.isEmpty();
        requestLayout();
        if (isLaidOut()) postOnAnimation(this::loadImagesIfReady);
    }

    private String signature(List<FeedListMedia> items) {
        StringBuilder builder = new StringBuilder();
        for (FeedListMedia item : items) {
            if (item == null) continue;
            builder.append(item.displayUrl()).append('|').append(item.thumbUrl()).append('|')
                    .append(item.width).append('x').append(item.height).append(';');
        }
        return builder.toString();
    }

    private void loadImagesIfReady() {
        if (!pendingLoad || getWidth() <= 0 || media.isEmpty()) return;
        pendingLoad = false;
        for (int i = 0; i < media.size() && i < MAX; i++) {
            ImageView image = views[i];
            FeedListMedia item = media.get(i);
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) {
                pendingLoad = true;
                continue;
            }
            Glide.with(image)
                    .load(media.size() == 1 ? item.displayUrl() : item.thumbUrl())
                    .placeholder(R.color.feedlist_media_placeholder)
                    .error(R.color.feedlist_media_placeholder)
                    .override(width, height)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .into(image);
        }
        if (pendingLoad) postOnAnimation(this::loadImagesIfReady);
    }

    public void reloadImages() {
        if (media.isEmpty()) return;
        pendingLoad = true;
        postOnAnimation(this::loadImagesIfReady);
    }

    public void clearImages() {
        clearImageRequests();
        mediaSignature = "";
        pendingLoad = false;
    }

    private void clearImageRequests() {
        for (ImageView image : views) Glide.with(image).clear(image);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int count = media.size();
        if (count <= 0 || width <= 0) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0);
            return;
        }
        if (count == 1) {
            FeedListMedia first = media.get(0);
            float ratio = first == null ? 1f : first.ratio();
            ratio = Math.max(0.55f, Math.min(1.35f, ratio));
            measuredContentHeight = Math.max(dp(180), Math.min(dp(420), Math.round(width * ratio)));
        } else if (count == 2) {
            measuredContentHeight = (width - gap) / 2;
        } else if (count == 3) {
            measuredContentHeight = Math.round((width - gap) * 2f / 3f);
        } else if (count == 4) {
            measuredContentHeight = width;
        } else if (count == 5) {
            measuredContentHeight = (width - gap) / 2;
        } else {
            measuredContentHeight = Math.round((width - gap * 2) * 2f / 3f) + gap;
        }
        int totalHeight = measuredContentHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(width + getPaddingLeft() + getPaddingRight(), widthMeasureSpec), resolveSize(totalHeight, heightMeasureSpec));
        measureChildrenForPattern(width, measuredContentHeight, count);
    }

    private void measureChildrenForPattern(int width, int height, int count) {
        if (count == 1) {
            measureExact(views[0], width, height);
        } else if (count == 2) {
            int cell = (width - gap) / 2;
            measureExact(views[0], cell, cell);
            measureExact(views[1], width - gap - cell, cell);
        } else if (count == 3) {
            int right = (width - gap) / 3;
            int left = width - gap - right;
            int half = (height - gap) / 2;
            measureExact(views[0], left, height);
            measureExact(views[1], right, half);
            measureExact(views[2], right, height - gap - half);
        } else if (count == 4) {
            int cell = (width - gap) / 2;
            for (int i = 0; i < 4; i++) {
                measureExact(views[i], i % 2 == 0 ? cell : width - gap - cell, i < 2 ? cell : height - gap - cell);
            }
        } else if (count == 5) {
            int left = (width - gap) / 2;
            int right = width - gap - left;
            int cellW = (right - gap) / 2;
            int cellH = (height - gap) / 2;
            measureExact(views[0], left, height);
            for (int i = 1; i < 5; i++) {
                measureExact(views[i], i % 2 == 1 ? cellW : right - gap - cellW, i <= 2 ? cellH : height - gap - cellH);
            }
        } else {
            int cellW = (width - gap * 2) / 3;
            int cellH = (height - gap) / 2;
            for (int i = 0; i < 6; i++) {
                measureExact(views[i], i % 3 < 2 ? cellW : width - gap * 2 - cellW * 2, i < 3 ? cellH : height - gap - cellH);
            }
        }
    }

    private void measureExact(View view, int width, int height) {
        view.measure(MeasureSpec.makeMeasureSpec(Math.max(1, width), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(1, height), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int count = media.size();
        if (count == 1) layoutChild(views[0], x, y);
        else if (count == 2) {
            layoutChild(views[0], x, y);
            layoutChild(views[1], x + views[0].getMeasuredWidth() + gap, y);
        } else if (count == 3) {
            layoutChild(views[0], x, y);
            int rx = x + views[0].getMeasuredWidth() + gap;
            layoutChild(views[1], rx, y);
            layoutChild(views[2], rx, y + views[1].getMeasuredHeight() + gap);
        } else if (count == 4) {
            layoutChild(views[0], x, y);
            layoutChild(views[1], x + views[0].getMeasuredWidth() + gap, y);
            int rowY = y + views[0].getMeasuredHeight() + gap;
            layoutChild(views[2], x, rowY);
            layoutChild(views[3], x + views[2].getMeasuredWidth() + gap, rowY);
        } else if (count == 5) {
            layoutChild(views[0], x, y);
            int rx = x + views[0].getMeasuredWidth() + gap;
            layoutChild(views[1], rx, y);
            layoutChild(views[2], rx + views[1].getMeasuredWidth() + gap, y);
            int rowY = y + views[1].getMeasuredHeight() + gap;
            layoutChild(views[3], rx, rowY);
            layoutChild(views[4], rx + views[3].getMeasuredWidth() + gap, rowY);
        } else if (count >= 6) {
            for (int i = 0; i < 6; i++) {
                int col = i % 3, row = i / 3;
                int lx = x;
                for (int c = 0; c < col; c++) lx += views[row * 3 + c].getMeasuredWidth() + gap;
                int ly = y + (row == 0 ? 0 : views[0].getMeasuredHeight() + gap);
                layoutChild(views[i], lx, ly);
            }
        }
        if (pendingLoad) postOnAnimation(this::loadImagesIfReady);
    }

    private static void layoutChild(View view, int x, int y) {
        view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!media.isEmpty()) {
            pendingLoad = true;
            postOnAnimation(this::loadImagesIfReady);
        }
    }

    @Override protected void onDetachedFromWindow() {
        // RecyclerView may keep a detached ViewHolder in its item cache and reattach it
        // without rebinding. Clear Glide targets to release resources, but keep the media
        // signature and mark them pending so onAttachedToWindow restores the images.
        clearImageRequests();
        pendingLoad = !media.isEmpty();
        super.onDetachedFromWindow();
    }
}
