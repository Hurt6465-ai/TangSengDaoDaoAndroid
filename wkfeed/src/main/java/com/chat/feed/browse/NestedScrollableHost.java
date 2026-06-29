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
 * Gesture gate for an outer vertical ViewPager2 and an inner horizontal media area.
 *
 * duoshine/douyin does not have this exact nested horizontal pager case. Its useful rule is:
 * vertical behavior is triggered only when the gesture is clearly vertical. Here we apply that
 * rule more strictly, because a single image still needs to absorb left/right drags instead of
 * letting the outer vertical pager switch pages.
 */
public class NestedScrollableHost extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;

    /** Horizontal is intentionally tolerant: left/right drags often contain small Y noise. */
    private static final float HORIZONTAL_TOLERANCE = 0.55f;
    /** Vertical must be clearly dominant before the outer vertical pager is released. */
    private static final float VERTICAL_DOMINANCE = 1.35f;

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
                // Keep the current item protected until MOVE proves the gesture is vertical.
                // This is what prevents a single-image left/right drag from becoming a page switch.
                requestParentDisallow(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - initialX;
                float dy = ev.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) {
                    requestParentDisallow(true);
                    return;
                }

                if (gestureDirection == GESTURE_UNDECIDED) {
                    if (absDx >= touchSlop && absDx >= absDy * HORIZONTAL_TOLERANCE) {
                        gestureDirection = GESTURE_HORIZONTAL;
                    } else if (absDy >= touchSlop && absDy >= absDx * VERTICAL_DOMINANCE) {
                        gestureDirection = GESTURE_VERTICAL;
                    } else {
                        // Ambiguous diagonal movement: do not release to the vertical pager yet.
                        requestParentDisallow(true);
                        return;
                    }
                }

                requestParentDisallow(gestureDirection != GESTURE_VERTICAL);
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
