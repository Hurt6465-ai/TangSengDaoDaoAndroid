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

public class NestedScrollableHost extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;
    private static final float HORIZONTAL_LOCK_RATIO = 1.35f;

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
    private ViewPager2 innerViewPager() {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        return child instanceof ViewPager2 ? (ViewPager2) child : null;
    }

    private boolean hasMultipleInnerPages() {
        ViewPager2 pager = innerViewPager();
        return pager != null && pager.getAdapter() != null && pager.getAdapter().getItemCount() > 1;
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

    private void handleInterceptTouchEvent(MotionEvent e) {
        ViewPager2 parentPager = findParentViewPager();
        if (parentPager == null || parentPager.getOrientation() != ViewPager2.ORIENTATION_VERTICAL) return;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = e.getX();
                initialY = e.getY();
                gestureDirection = GESTURE_UNDECIDED;
                requestParentDisallow(hasMultipleInnerPages());
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - initialX;
                float dy = e.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) return;

                if (gestureDirection == GESTURE_UNDECIDED) {
                    if (hasMultipleInnerPages() && absDx > absDy * HORIZONTAL_LOCK_RATIO) {
                        gestureDirection = GESTURE_HORIZONTAL;
                    } else if (absDy >= absDx) {
                        gestureDirection = GESTURE_VERTICAL;
                    } else {
                        requestParentDisallow(hasMultipleInnerPages());
                        return;
                    }
                }

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
