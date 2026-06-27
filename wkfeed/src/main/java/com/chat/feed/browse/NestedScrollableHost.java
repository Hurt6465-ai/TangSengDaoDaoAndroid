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
    private float initialX;
    private float initialY;
    private final int touchSlop;

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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        handleInterceptTouch(event);
        return super.onTouchEvent(event);
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
                // DOWN 先把事件交给内部可滑动区域，避免多图左右滑一开始就被外层竖向 ViewPager2 抢走。
                // MOVE 再根据方向和边界释放给外层。
                parentPager.requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - initialX;
                float dy = ev.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) return;
                boolean horizontalSwipe = absDx > absDy;
                if (horizontalSwipe) {
                    int direction = dx < 0 ? 1 : -1;
                    parentPager.requestDisallowInterceptTouchEvent(canScrollHorizontally(childView, direction));
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
}
