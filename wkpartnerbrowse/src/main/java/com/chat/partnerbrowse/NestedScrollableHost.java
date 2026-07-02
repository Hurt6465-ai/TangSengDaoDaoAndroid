package com.chat.partnerbrowse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Gesture gate for an outer vertical ViewPager2 and an inner horizontal image pager.
 *
 * Policy:
 * - Horizontal or ambiguous drags stay inside the current card.
 * - Vertical paging is released to the parent only when the drag is clearly vertical.
 * - ACTION_UP/CANCEL releases parent interception with a tiny delay, avoiding the common
 *   "horizontal swipe at image edge triggers vertical pager jitter" problem.
 */
public class NestedScrollableHost extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;

    private static final float HORIZONTAL_LOCK_RATIO = 0.62f;
    private static final float VERTICAL_DOMINANCE = 1.55f;
    private static final long RELEASE_PARENT_DELAY_MS = 180L;

    private float initialX;
    private float initialY;
    private final int touchSlop;
    private int gestureDirection = GESTURE_UNDECIDED;

    public NestedScrollableHost(Context context) {
        this(context, null);
    }

    public NestedScrollableHost(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NestedScrollableHost(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Nullable
    private ViewPager2 findParentViewPager() {
        ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent instanceof ViewPager2) return (ViewPager2) parent;
            parent = parent.getParent();
        }
        return null;
    }

    private void requestParentDisallow(boolean disallow) {
        ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        handleInterceptTouchEvent(e);
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        handleInterceptTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private void handleInterceptTouchEvent(MotionEvent e) {
        ViewPager2 parentPager = findParentViewPager();
        if (parentPager == null || parentPager.getOrientation() != ViewPager2.ORIENTATION_VERTICAL) return;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(releaseParentRunnable);
                initialX = e.getX();
                initialY = e.getY();
                gestureDirection = GESTURE_UNDECIDED;
                requestParentDisallow(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - initialX;
                float dy = e.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) {
                    requestParentDisallow(true);
                    return;
                }

                if (gestureDirection == GESTURE_UNDECIDED) {
                    if (absDx >= touchSlop && absDx >= absDy * HORIZONTAL_LOCK_RATIO) {
                        gestureDirection = GESTURE_HORIZONTAL;
                    } else if (absDy >= touchSlop && absDy >= absDx * VERTICAL_DOMINANCE) {
                        gestureDirection = GESTURE_VERTICAL;
                    } else {
                        // 斜滑不立刻交给外层，先锁当前卡片，等用户意图更明确。
                        requestParentDisallow(true);
                        return;
                    }
                }

                requestParentDisallow(gestureDirection != GESTURE_VERTICAL);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                gestureDirection = GESTURE_UNDECIDED;
                removeCallbacks(releaseParentRunnable);
                postDelayed(releaseParentRunnable, RELEASE_PARENT_DELAY_MS);
                break;
            default:
                break;
        }
    }

    private final Runnable releaseParentRunnable = new Runnable() {
        @Override
        public void run() {
            requestParentDisallow(false);
        }
    };
}
