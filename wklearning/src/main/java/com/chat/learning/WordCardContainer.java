package com.chat.learning;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Gesture-owning card container. It intercepts clear horizontal drags on both faces and downward
 * drags on the front only, leaving back-side vertical movement to the ScrollView.
 */
final class WordCardContainer extends FrameLayout {
    enum Direction { LEFT, RIGHT, DOWN }

    interface Listener {
        boolean isFrontFace();
        void onDrag(Direction direction, float progress, boolean thresholdCrossed);
        boolean onCommit(Direction direction);
        void onClickCard();
        void onReset();
    }

    private Listener listener;
    private final int touchSlop;
    private float downX;
    private float downY;
    private float lastDx;
    private float lastDy;
    private boolean dragging;
    private Direction direction;

    WordCardContainer(Context context) { this(context, null); }

    WordCardContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setClipChildren(false);
    }

    void setGestureListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (listener == null) return super.onInterceptTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                direction = null;
                lastDx = lastDy = 0f;
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.max(Math.abs(dx), Math.abs(dy)) < touchSlop) return false;
                if (Math.abs(dx) >= Math.abs(dy) * 0.78f) {
                    dragging = true;
                    direction = dx < 0 ? Direction.LEFT : Direction.RIGHT;
                    requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                if (listener.isFrontFace() && dy > 0 && Math.abs(dy) >= Math.abs(dx) * 0.78f) {
                    dragging = true;
                    direction = Direction.DOWN;
                    requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                direction = null;
                lastDx = lastDy = 0f;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                lastDx = dx;
                lastDy = dy;
                if (!dragging) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) < touchSlop) return true;
                    if (Math.abs(dx) >= Math.abs(dy) * 0.78f) {
                        dragging = true;
                        direction = dx < 0 ? Direction.LEFT : Direction.RIGHT;
                    } else if (listener.isFrontFace() && dy > 0 && Math.abs(dy) >= Math.abs(dx) * 0.78f) {
                        dragging = true;
                        direction = Direction.DOWN;
                    } else {
                        return false;
                    }
                }
                applyDrag(dx, dy);
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    if (Math.hypot(event.getX() - downX, event.getY() - downY) <= touchSlop * 1.5f) {
                        listener.onClickCard();
                    }
                    return true;
                }
                finishDrag();
                return true;
            case MotionEvent.ACTION_CANCEL:
                animateBack();
                return true;
            default:
                return true;
        }
    }

    private void applyDrag(float dx, float dy) {
        if (direction == Direction.DOWN) {
            float y = Math.max(0f, dy);
            setTranslationY(y);
            setTranslationX(0f);
            setRotation(0f);
            float threshold = Math.max(dp(46), getHeight() * 0.10f);
            float progress = Math.min(1.35f, y / threshold);
            listener.onDrag(direction, progress, progress >= 1f);
        } else {
            setTranslationX(dx);
            setTranslationY(0f);
            setRotation(5.5f * dx / Math.max(1f, getWidth()));
            float threshold = Math.max(dp(68), getWidth() * 0.26f);
            float progress = Math.min(1.35f, Math.abs(dx) / threshold);
            listener.onDrag(direction, progress, progress >= 1f);
        }
    }

    private void finishDrag() {
        float threshold = direction == Direction.DOWN
                ? Math.max(dp(46), getHeight() * 0.10f)
                : Math.max(dp(68), getWidth() * 0.26f);
        float distance = direction == Direction.DOWN ? Math.max(0f, lastDy) : Math.abs(lastDx);
        if (distance >= threshold && listener.onCommit(direction)) {
            flyOut(direction);
        } else {
            animateBack();
        }
    }

    private void flyOut(Direction direction) {
        float targetX = 0f;
        float targetY = 0f;
        if (direction == Direction.LEFT) targetX = -Math.max(getWidth() * 1.35f, dp(480));
        if (direction == Direction.RIGHT) targetX = Math.max(getWidth() * 1.35f, dp(480));
        if (direction == Direction.DOWN) targetY = Math.max(getHeight() * 0.38f, dp(180));
        animate().translationX(targetX).translationY(targetY)
                .rotation(direction == Direction.LEFT ? -10f : direction == Direction.RIGHT ? 10f : 0f)
                .alpha(direction == Direction.DOWN ? 0.92f : 0f)
                .setDuration(180)
                .withEndAction(this::resetImmediately)
                .start();
    }

    void resetImmediately() {
        animate().cancel();
        setTranslationX(0f);
        setTranslationY(0f);
        setRotation(0f);
        setAlpha(1f);
        dragging = false;
        direction = null;
        if (listener != null) listener.onReset();
    }

    private void animateBack() {
        animate().translationX(0f).translationY(0f).rotation(0f).alpha(1f)
                .setDuration(180)
                .withEndAction(() -> {
                    dragging = false;
                    direction = null;
                    if (listener != null) listener.onReset();
                }).start();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static boolean isTouchInside(View view, MotionEvent event) {
        if (view == null || event == null) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= location[0] && rawX <= location[0] + view.getWidth()
                && rawY >= location[1] && rawY <= location[1] + view.getHeight();
    }
}
