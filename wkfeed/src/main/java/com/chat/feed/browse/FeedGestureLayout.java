package com.chat.feed.browse;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
 * Full-screen media gesture layer, inspired by Douyin/TikTok item root gesture handling.
 *
 * Important detail for ViewPager2:
 * The view that actually intercepts page swipes is ViewPager2's internal RecyclerView,
 * not only the ViewPager2 wrapper. Therefore requestDisallowInterceptTouchEvent must be
 * sent through the whole parent chain. When a horizontal gesture is locked, we also
 * temporarily disable the outer vertical ViewPager2 user input until ACTION_UP/CANCEL.
 */
public class FeedGestureLayout extends FrameLayout {
    private static final int GESTURE_UNDECIDED = 0;
    private static final int GESTURE_HORIZONTAL = 1;
    private static final int GESTURE_VERTICAL = 2;
    private static final long RESTORE_PARENT_DELAY_MS = 180L;
    private static final long BLOCK_TAP_AFTER_SCROLL_MS = 220L;

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
    private boolean gestureMoved;
    private long blockTapUntilMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ViewPager2 lockedVerticalPager;
    private final Runnable restoreParentRunnable = this::restoreParentNow;

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
                if (listener != null && listener.isGestureEnabled() && canDispatchMediaTap()) {
                    listener.onSingleTap();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                GestureListener listener = gestureListener;
                if (listener != null && listener.isGestureEnabled() && canDispatchMediaTap()) {
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

    private boolean canDispatchMediaTap() {
        return !ignoreThisGesture
                && SystemClock.uptimeMillis() >= blockTapUntilMs
                && !gestureMoved
                && gestureDirection == GESTURE_UNDECIDED;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        GestureListener listener = gestureListener;
        if (listener != null && listener.isGestureEnabled()) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                ignoreThisGesture = listener.shouldIgnoreGesture(ev.getX(), ev.getY())
                        || SystemClock.uptimeMillis() < blockTapUntilMs;
            }
            handleParentGestureGate(ev, ignoreThisGesture);
            if (!ignoreThisGesture) {
                try {
                    gestureDetector.onTouchEvent(ev);
                } catch (Throwable ignored) {
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void handleParentGestureGate(MotionEvent ev, boolean ignoreMediaTap) {
        ViewPager2 parentPager = findParentVerticalPager();
        if (parentPager == null) return;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handler.removeCallbacks(restoreParentRunnable);
                lockedVerticalPager = parentPager;
                downX = ev.getX();
                downY = ev.getY();
                gestureDirection = GESTURE_UNDECIDED;
                gestureMoved = false;
                // The direct parent is usually ViewPager2's internal RecyclerView. Protect the
                // entire parent chain immediately, otherwise the outer vertical pager may steal
                // video horizontal drags before this item receives enough MOVE events.
                requestAllParentsDisallow(true);
                setOuterPagerInputEnabled(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx < touchSlop && absDy < touchSlop) {
                    requestAllParentsDisallow(true);
                    return;
                }
                gestureMoved = true;
                if (gestureDirection == GESTURE_UNDECIDED) {
                    // Media area: horizontal/diagonal-horizontal should stay on current item.
                    // Button/text area: same protection, but no single/double tap callback.
                    if (absDx >= touchSlop && absDx >= absDy * 0.50f) {
                        gestureDirection = GESTURE_HORIZONTAL;
                    } else if (absDy >= touchSlop && absDy >= absDx * 1.60f) {
                        gestureDirection = GESTURE_VERTICAL;
                    } else {
                        requestAllParentsDisallow(true);
                        return;
                    }
                }
                if (gestureDirection == GESTURE_HORIZONTAL) {
                    requestAllParentsDisallow(true);
                    setOuterPagerInputEnabled(false);
                } else {
                    setOuterPagerInputEnabled(true);
                    requestAllParentsDisallow(false);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetParentGestureGate();
                break;
            default:
                break;
        }
    }

    private void resetParentGestureGate() {
        if (gestureMoved || gestureDirection != GESTURE_UNDECIDED) {
            blockTapUntilMs = SystemClock.uptimeMillis() + BLOCK_TAP_AFTER_SCROLL_MS;
        }
        gestureDirection = GESTURE_UNDECIDED;
        gestureMoved = false;
        // Delayed restore avoids the classic ViewPager2 issue where ACTION_UP immediately
        // hands a diagonal tail to the outer vertical pager and causes a small page shake.
        handler.removeCallbacks(restoreParentRunnable);
        handler.postDelayed(restoreParentRunnable, RESTORE_PARENT_DELAY_MS);
    }

    private void restoreParentNow() {
        setOuterPagerInputEnabled(true);
        requestAllParentsDisallow(false);
        lockedVerticalPager = null;
    }

    private void requestAllParentsDisallow(boolean disallow) {
        ViewParent parent = getParent();
        while (parent != null) {
            try {
                parent.requestDisallowInterceptTouchEvent(disallow);
            } catch (Throwable ignored) {
            }
            if (parent instanceof View) {
                parent = ((View) parent).getParent();
            } else {
                break;
            }
        }
    }

    private void setOuterPagerInputEnabled(boolean enabled) {
        ViewPager2 pager = lockedVerticalPager != null ? lockedVerticalPager : findParentVerticalPager();
        if (pager == null) return;
        try {
            if (pager.isUserInputEnabled() != enabled) pager.setUserInputEnabled(enabled);
        } catch (Throwable ignored) {
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
            parent = ((View) parent).getParent();
        }
        return null;
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(restoreParentRunnable);
        restoreParentNow();
        super.onDetachedFromWindow();
    }
}
