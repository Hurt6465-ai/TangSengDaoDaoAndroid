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

/**
 * Handles nested gesture conflict between outer vertical ViewPager2 and inner horizontal ViewPager2.
 *
 * Important detail:
 * Once this gesture is judged as horizontal, keep it inside the inner pager until UP/CANCEL.
 * Do not release to the outer vertical pager even if the inner pager reaches first/last image,
 * otherwise the screen will shake when swiping to the last image and the vertical feed may be triggered.
 */
public class NestedScrollableHost extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;

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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        handleInterceptTouch(event);
        return super.onTouchEvent(event);
    }

    private void handleInterceptTouch(MotionEvent ev) {
        ViewPager2 parentPager = parentViewPager();
        if (parentPager == null || child() == null) return;
        if (parentPager.getOrientation() != ViewPager2.ORIENTATION_VERTICAL) return;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getX();
                initialY = ev.getY();
                gestureDirection = GESTURE_UNDECIDED;
                // DOWN first gives the inner horizontal pager a chance to decide.
                parentPager.requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - initialX;
                float dy = ev.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) return;

                if (gestureDirection == GESTURE_UNDECIDED) {
                    gestureDirection = absDx >= absDy ? GESTURE_HORIZONTAL : GESTURE_VERTICAL;
                }
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
