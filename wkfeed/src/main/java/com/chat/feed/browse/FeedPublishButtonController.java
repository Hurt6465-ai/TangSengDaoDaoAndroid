package com.chat.feed.browse;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.viewpager2.widget.ViewPager2;

import com.chat.feed.config.FeedConfig;

/**
 * 发现发布按钮控制。
 * 右下角按钮不能一直抢视觉：拖动/切页时隐藏，停稳一小会儿再显示。
 */
public class FeedPublishButtonController {
    private final View button;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable showRunnable = this::show;

    public FeedPublishButtonController(View button) {
        this.button = button;
    }

    public void onPageScrollStateChanged(int state) {
        if (state == ViewPager2.SCROLL_STATE_DRAGGING || state == ViewPager2.SCROLL_STATE_SETTLING) {
            hide();
        } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
            scheduleShow();
        }
    }

    public void onPageSelected() {
        hide();
        scheduleShow();
    }

    public void destroy() {
        handler.removeCallbacks(showRunnable);
    }

    private void scheduleShow() {
        handler.removeCallbacks(showRunnable);
        handler.postDelayed(showRunnable, FeedConfig.PUBLISH_SHOW_DELAY_MS);
    }

    private void hide() {
        handler.removeCallbacks(showRunnable);
        if (button == null) return;
        button.animate().cancel();
        button.animate().alpha(0f).scaleX(0.88f).scaleY(0.88f).setDuration(120).withEndAction(() -> button.setVisibility(View.GONE)).start();
    }

    private void show() {
        if (button == null) return;
        button.animate().cancel();
        button.setVisibility(View.VISIBLE);
        button.setAlpha(0f);
        button.setScaleX(0.88f);
        button.setScaleY(0.88f);
        button.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
    }
}
