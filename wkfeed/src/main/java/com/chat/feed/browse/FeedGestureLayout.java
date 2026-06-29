package com.chat.feed.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Full-screen media gesture layer, inspired by the Douyin demo's LikeView:
 * - the media root listens to single tap / double tap instead of putting a small
 *   transparent shield only above PlayerView;
 * - vertical ViewPager2 keeps control only when the gesture is clearly vertical;
 * - horizontal / diagonal-horizontal movement stays on the current item, so a
 *   video page will not accidentally turn into an up/down page swipe.
 */
public class FeedGestureLayout extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;

    public interface GestureListener {
        boolean isGestureEnabled();
        boolean shouldIgnoreGesture(float x, float y);
        void onSingleTap();
        void onDoubleTap(float x, float y);
    }

    private final int touchSlop;
    private final GestureDetector gestureDetector;
    private GestureListener gestureListener;
    private float downX;
    private float downY;
    private int gestureDirection = GESTURE_UNDECIDED;
    private boolean ignoreThisGesture;

    public FeedGestureLayout(@NonNull Context context) {
        this(context, null);
    }

    public FeedGestureLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                GestureListener listener = gestureListener;
                if (listener != null && listener.isGestureEnabled() && !ignoreThisGesture) {
                    listener.onSingleTap();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                GestureListener listener = gestureListener;
                if (listener != null && listener.isGestureEnabled() && !ignoreThisGesture) {
                    listener.onDoubleTap(e.getX(), e.getY());
                    return true;
                }
                return false;
            }
        });
    }

    public void setGestureListener(@Nullable GestureListener listener) {
        this.gestureListener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        GestureListener listener = gestureListener;
        if (listener != null && listener.isGestureEnabled()) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                ignoreThisGesture = listener.shouldIgnoreGesture(ev.getX(), ev.getY());
            }
            if (!ignoreThisGesture) {
                handleParentGestureGate(ev);
                try {
                    gestureDetector.onTouchEvent(ev);
                } catch (Throwable ignored) {
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void handleParentGestureGate(MotionEvent ev) {
        ViewPager2 parentPager = findParentVerticalPager();
        if (parentPager == null) return;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                gestureDirection = GESTURE_UNDECIDED;
                // Douyin-style: media layer receives the first touch, parent vertical pager
                // is protected until the direction is clear. Do not disable user input here;
                // just use requestDisallowInterceptTouchEvent so vertical swipes can still
                // be released to the parent once confirmed.
                parentPager.requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) {
                    parentPager.requestDisallowInterceptTouchEvent(true);
                    return;
                }
                if (gestureDirection == GESTURE_UNDECIDED) {
                    // Horizontal is intentionally easier to lock than vertical. Real short-video
                    // apps do not let a slight vertical component in a sideways drag flip pages.
                    if (absDx >= touchSlop && absDx >= absDy * 0.55f) {
                        gestureDirection = GESTURE_HORIZONTAL;
                    } else if (absDy >= touchSlop && absDy >= absDx * 1.35f) {
                        gestureDirection = GESTURE_VERTICAL;
                    } else {
                        parentPager.requestDisallowInterceptTouchEvent(true);
                        return;
                    }
                }
                parentPager.requestDisallowInterceptTouchEvent(gestureDirection != GESTURE_VERTICAL);
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
    private ViewPager2 findParentVerticalPager() {
        ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent instanceof ViewPager2) {
                ViewPager2 pager = (ViewPager2) parent;
                return pager.getOrientation() == ViewPager2.ORIENTATION_VERTICAL ? pager : null;
            }
            parent = parent.getParent();
        }
        return null;
    }
}
