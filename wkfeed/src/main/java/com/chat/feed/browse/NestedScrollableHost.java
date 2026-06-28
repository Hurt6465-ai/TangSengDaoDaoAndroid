package com.chat.feed.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

public class NestedScrollableHost extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;
    private static final float HORIZONTAL_LOCK_RATIO = 1.5f;

    private float initialX;
    private float initialY;
    private final int touchSlop;
    private int gestureDirection = GESTURE_UNDECIDED;

    public NestedScrollableHost(@NonNull Context context) {
        this(context, null);
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Nullable
    private ViewPager2 parentViewPager() {
        ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent instanceof ViewPager2) return (ViewPager2) parent;
            parent = parent.getParent();
        }
        return null;
    }

    private View child() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleInterceptTouch(ev);
        return super.onInterceptTouchEvent(ev);
    }

    private boolean canScrollHorizontally(View view, int direction) {
        if (view instanceof ViewPager2) {
            ViewPager2 pager = (ViewPager2) view;
            View rv = pager.getChildCount() > 0 ? pager.getChildAt(0) : null;
            return rv instanceof RecyclerView && ViewCompat.canScrollHorizontally(rv, direction);
        }
        return ViewCompat.canScrollHorizontally(view, direction);
    }

    private void handleInterceptTouch(MotionEvent ev) {
        ViewPager2 parentPager = parentViewPager();
        View childView = child();
        if (parentPager == null || childView == null) return;
        if (parentPager.getOrientation() != ViewPager2.ORIENTATION_VERTICAL) return;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getX();
                initialY = ev.getY();
                gestureDirection = GESTURE_UNDECIDED;
                // 单图/不可横滑时不要一开始拦住外层竖向 ViewPager2，竖滑切下一条才不会有延迟。
                boolean canScrollLeft = canScrollHorizontally(childView, -1);
                boolean canScrollRight = canScrollHorizontally(childView, 1);
                parentPager.requestDisallowInterceptTouchEvent(canScrollLeft || canScrollRight);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - initialX;
                float dy = ev.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) return;

                if (gestureDirection == GESTURE_UNDECIDED) {
                    gestureDirection = absDx > absDy * HORIZONTAL_LOCK_RATIO ? GESTURE_HORIZONTAL : GESTURE_VERTICAL;
                }

                // 一旦判定横滑，本次手势全程锁给内层。即使滑到最后一张，也不要交给外层竖滑，避免屏幕晃动。
                parentPager.requestDisallowInterceptTouchEvent(gestureDirection == GESTURE_HORIZONTAL);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                gestureDirection = GESTURE_UNDECIDED;
                parentPager.requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
    }
}
