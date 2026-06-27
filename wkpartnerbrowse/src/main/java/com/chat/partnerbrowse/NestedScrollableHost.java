package com.chat.partnerbrowse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

public class NestedScrollableHost extends FrameLayout {
    private float initialX;
    private float initialY;
    private final int touchSlop;

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
    private ViewPager2 getInnerViewPager() {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        return child instanceof ViewPager2 ? (ViewPager2) child : null;
    }

    private boolean canChildScroll(ViewPager2 inner, int direction) {
        if (inner == null) return false;
        View child = inner.getChildCount() > 0 ? inner.getChildAt(0) : null;
        if (child instanceof RecyclerView) return child.canScrollHorizontally(direction);
        return inner.canScrollHorizontally(direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        handleInterceptTouchEvent(e);
        return super.onInterceptTouchEvent(e);
    }

    private void handleInterceptTouchEvent(MotionEvent e) {
        ViewPager2 parentPager = findParentViewPager();
        ViewPager2 inner = getInnerViewPager();
        if (parentPager == null || inner == null) return;
        if (parentPager.getOrientation() != ViewPager2.ORIENTATION_VERTICAL) return;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = e.getX();
                initialY = e.getY();
                parentPager.requestDisallowInterceptTouchEvent(false);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - initialX;
                float dy = e.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) return;

                boolean horizontalSwipe = absDx > absDy;
                if (horizontalSwipe) {
                    int dir = dx < 0 ? 1 : -1;
                    parentPager.requestDisallowInterceptTouchEvent(canChildScroll(inner, dir));
                } else {
                    parentPager.requestDisallowInterceptTouchEvent(false);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                parentPager.requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
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
}
