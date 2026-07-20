package com.chat.forum;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;

/** Full-screen forum image preview with horizontal paging and pinch/double-tap zoom. */
public class ForumImageViewerActivity extends AppCompatActivity {
    private static final String EXTRA_URLS = "forum_image_urls";
    private static final String EXTRA_INDEX = "forum_image_index";

    private TextView indicator;

    static void open(@NonNull Context context, @NonNull ArrayList<String> urls, int index) {
        if (urls.isEmpty()) return;
        Intent intent = new Intent(context, ForumImageViewerActivity.class);
        intent.putStringArrayListExtra(EXTRA_URLS, urls);
        intent.putExtra(EXTRA_INDEX, Math.max(0, Math.min(index, urls.size() - 1)));
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        ArrayList<String> urls = getIntent() == null
                ? null : getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null || urls.isEmpty()) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ViewPager2 pager = new ViewPager2(this);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setAdapter(new ImageAdapter(urls));
        root.addView(pager, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView close = new TextView(this);
        close.setText("‹");
        close.setTextColor(Color.WHITE);
        close.setTextSize(38);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundColor(0x33000000);
        close.setContentDescription(ForumText.get(R.string.forum_close_image_preview));
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(52), dp(52));
        closeParams.gravity = Gravity.START | Gravity.TOP;
        closeParams.leftMargin = dp(4);
        closeParams.topMargin = dp(8);
        root.addView(close, closeParams);

        indicator = new TextView(this);
        indicator.setTextColor(Color.WHITE);
        indicator.setTextSize(14);
        indicator.setGravity(Gravity.CENTER);
        indicator.setBackgroundColor(0x55000000);
        indicator.setPadding(dp(12), 0, dp(12), 0);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        indicatorParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        indicatorParams.topMargin = dp(16);
        root.addView(indicator, indicatorParams);

        int start = Math.max(0, Math.min(
                getIntent().getIntExtra(EXTRA_INDEX, 0), urls.size() - 1));
        pager.setCurrentItem(start, false);
        updateIndicator(start, urls.size());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator(position, urls.size());
            }
        });

        setContentView(root);
    }

    private void updateIndicator(int position, int size) {
        if (indicator != null) indicator.setText((position + 1) + " / " + size);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ImageAdapter extends RecyclerView.Adapter<ImageHolder> {
        private final ArrayList<String> urls;

        ImageAdapter(@NonNull ArrayList<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ZoomImageView image = new ZoomImageView(parent.getContext());
            image.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            image.setBackgroundColor(Color.BLACK);
            return new ImageHolder(image);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
            holder.image.resetZoom();
            Glide.with(holder.image)
                    .load(urls.get(position))
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(holder.image);
        }

        @Override
        public void onViewRecycled(@NonNull ImageHolder holder) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }
    }

    private static final class ImageHolder extends RecyclerView.ViewHolder {
        final ZoomImageView image;

        ImageHolder(@NonNull ZoomImageView image) {
            super(image);
            this.image = image;
        }
    }

    /** Small dependency-free zoom view so the forum module does not add another image library. */
    private static final class ZoomImageView extends ImageView {
        private static final float MAX_SCALE = 4f;
        private final Matrix drawMatrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private float currentScale = 1f;
        private float lastX;
        private float lastY;
        private boolean dragging;

        ZoomImageView(@NonNull Context context) {
            super(context);
            setScaleType(ScaleType.MATRIX);
            scaleDetector = new ScaleGestureDetector(context,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector detector) {
                            Drawable drawable = getDrawable();
                            if (drawable == null) return false;
                            float requested = currentScale * detector.getScaleFactor();
                            float target = Math.max(1f, Math.min(MAX_SCALE, requested));
                            float factor = target / currentScale;
                            currentScale = target;
                            drawMatrix.postScale(factor, factor,
                                    detector.getFocusX(), detector.getFocusY());
                            constrainMatrix();
                            setImageMatrix(drawMatrix);
                            getParent().requestDisallowInterceptTouchEvent(currentScale > 1.01f);
                            return true;
                        }
                    });
            gestureDetector = new GestureDetector(context,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(@NonNull MotionEvent event) {
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(@NonNull MotionEvent event) {
                            if (currentScale > 1.05f) {
                                resetZoom();
                            } else {
                                zoomTo(2.5f, event.getX(), event.getY());
                            }
                            return true;
                        }
                    });
        }

        @Override
        public void setImageDrawable(@Nullable Drawable drawable) {
            super.setImageDrawable(drawable);
            if (drawMatrix != null) post(this::resetZoom);
        }

        void resetZoom() {
            Drawable drawable = getDrawable();
            if (drawable == null || getWidth() <= 0 || getHeight() <= 0) {
                drawMatrix.reset();
                currentScale = 1f;
                setImageMatrix(drawMatrix);
                return;
            }
            float dw = Math.max(1, drawable.getIntrinsicWidth());
            float dh = Math.max(1, drawable.getIntrinsicHeight());
            float base = Math.min(getWidth() / dw, getHeight() / dh);
            float dx = (getWidth() - dw * base) * 0.5f;
            float dy = (getHeight() - dh * base) * 0.5f;
            drawMatrix.reset();
            drawMatrix.postScale(base, base);
            drawMatrix.postTranslate(dx, dy);
            currentScale = 1f;
            setImageMatrix(drawMatrix);
            ViewParentCompat.allowParentIntercept(this);
        }

        private void zoomTo(float targetScale, float x, float y) {
            float target = Math.max(1f, Math.min(MAX_SCALE, targetScale));
            float factor = target / currentScale;
            currentScale = target;
            drawMatrix.postScale(factor, factor, x, y);
            constrainMatrix();
            setImageMatrix(drawMatrix);
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            post(this::resetZoom);
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = currentScale > 1.01f;
                    if (dragging) getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (currentScale > 1.01f && !scaleDetector.isInProgress()) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        drawMatrix.postTranslate(dx, dy);
                        constrainMatrix();
                        setImageMatrix(drawMatrix);
                        dragging = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    if (currentScale <= 1.01f) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    break;
                default:
                    break;
            }
            return true;
        }

        private void constrainMatrix() {
            Drawable drawable = getDrawable();
            if (drawable == null) return;
            RectF rect = new RectF(0, 0,
                    drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            drawMatrix.mapRect(rect);
            float dx = 0f;
            float dy = 0f;
            if (rect.width() <= getWidth()) {
                dx = getWidth() * 0.5f - rect.centerX();
            } else if (rect.left > 0) {
                dx = -rect.left;
            } else if (rect.right < getWidth()) {
                dx = getWidth() - rect.right;
            }
            if (rect.height() <= getHeight()) {
                dy = getHeight() * 0.5f - rect.centerY();
            } else if (rect.top > 0) {
                dy = -rect.top;
            } else if (rect.bottom < getHeight()) {
                dy = getHeight() - rect.bottom;
            }
            drawMatrix.postTranslate(dx, dy);
        }
    }

    /** Keeps the nested class free from a direct ViewParent import in touch hot paths. */
    private static final class ViewParentCompat {
        static void allowParentIntercept(@NonNull View view) {
            if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
        }
    }
}
