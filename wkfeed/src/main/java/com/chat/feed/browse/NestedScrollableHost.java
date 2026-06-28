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
import androidx.viewpager2.widget.ViewPager2;

public class NestedScrollableHost extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;
    private static final float HORIZONTAL_LOCK_RATIO = 1.35f;

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

    @Nullable
    private ViewPager2 innerViewPager() {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        return child instanceof ViewPager2 ? (ViewPager2) child : null;
    }

    private boolean hasMultipleInnerPages() {
        ViewPager2 pager = innerViewPager();
        return pager != null && pager.getAdapter() != null && pager.getAdapter().getItemCount() > 1;
    }

    private void requestParentDisallow(boolean disallow) {
        ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleInterceptTouch(ev);
        return super.onInterceptTouchEvent(ev);
    }

    private void handleInterceptTouch(MotionEvent ev) {
        ViewPager2 parentPager = parentViewPager();
        if (parentPager == null || parentPager.getOrientation() != ViewPager2.ORIENTATION_VERTICAL) return;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getX();
                initialY = ev.getY();
                gestureDirection = GESTURE_UNDECIDED;
                // 参考抖音项目：先看当前场景是否需要横向容器处理。
                // 单图/视频页没有多图横滑能力，DOWN 阶段不要抢外层竖向 ViewPager2。
                requestParentDisallow(hasMultipleInnerPages());
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - initialX;
                float dy = ev.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) return;

                if (gestureDirection == GESTURE_UNDECIDED) {
                    if (hasMultipleInnerPages() && absDx > absDy * HORIZONTAL_LOCK_RATIO) {
                        gestureDirection = GESTURE_HORIZONTAL;
                    } else if (absDy >= absDx) {
                        gestureDirection = GESTURE_VERTICAL;
                    } else {
                        // 角度不明确时继续交给内层一点点，避免 45 度附近反复抢事件。
                        requestParentDisallow(hasMultipleInnerPages());
                        return;
                    }
                }

                // 横向锁定后整次手势都不交给外层；最后一张/第一张继续横滑也不触发上下滑。
                requestParentDisallow(gestureDirection == GESTURE_HORIZONTAL);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                gestureDirection = GESTURE_UNDECIDED;
                requestParentDisallow(false);
                break;
            default:
                break;
        }
    }
}
