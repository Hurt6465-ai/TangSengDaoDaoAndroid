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
 * Handles nested gesture conflict between outer vertical ViewPager2 and inner horizontal ViewPager2.
 * Horizontal gesture is locked for the whole gesture lifecycle to avoid vertical page jitter at image edge.
 */
public class NestedScrollableHost extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;

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
                initialX = e.getX();
                initialY = e.getY();
                gestureDirection = GESTURE_UNDECIDED;
                parentPager.requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - initialX;
                float dy = e.getY() - initialY;
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
