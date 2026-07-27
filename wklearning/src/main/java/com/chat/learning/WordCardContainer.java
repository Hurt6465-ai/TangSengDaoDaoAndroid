package com.chat.learning;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Stable gesture container for the word card.
 *
 * The gesture axis is locked once per touch sequence. Horizontal drags are owned by the card on
 * both faces. A downward drag is owned by the card only on the front face; vertical movement on
 * the back face is left to the inner ScrollView. Translation uses raw screen coordinates so the
 * moving view never changes the coordinate system used to calculate the drag distance.
 */
final class WordCardContainer extends FrameLayout {
    enum Direction { LEFT, RIGHT, DOWN }

    interface Listener {
        boolean isFrontFace();
        boolean isInteractionLocked();
        void onDrag(Direction direction, float progress, boolean thresholdCrossed);
        boolean onCommit(Direction direction);
        void onClickCard();
        void onReset();
    }

    private enum Axis { UNDECIDED, HORIZONTAL, DOWN, CHILD_VERTICAL, REJECTED }

    private Listener listener;
    private final int touchSlop;
    private final int minimumFlingVelocity;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private float downRawX;
    private float downRawY;
    private float dragX;
    private float dragY;
    private Axis axis = Axis.UNDECIDED;
    private Direction direction;
    private VelocityTracker velocityTracker;
    private boolean blockedTouchSequence;

    WordCardContainer(Context context) {
        this(context, null);
    }

    WordCardContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setGestureListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (listener == null) return super.onInterceptTouchEvent(event);
        if (consumeLockedTouch(event)) return true;

        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            beginGesture(event);
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            addMovement(event);
            if (axis == Axis.UNDECIDED) decideAxis(event);
            return axis == Axis.HORIZONTAL || axis == Axis.DOWN;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            recycleVelocityTracker();
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) return super.onTouchEvent(event);
        if (consumeLockedTouch(event)) return true;
        addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginGesture(event);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (axis == Axis.UNDECIDED) decideAxis(event);
                if (axis != Axis.HORIZONTAL && axis != Axis.DOWN) return false;
                updateDrag(event);
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                handlePointerUp(event);
                return true;

            case MotionEvent.ACTION_UP:
                if (axis == Axis.HORIZONTAL || axis == Axis.DOWN) {
                    finishDrag();
                } else if (axis == Axis.UNDECIDED && distanceFromDown(event) <= touchSlop * 1.5f) {
                    listener.onClickCard();
                    clearGesture(false);
                } else {
                    clearGesture(false);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                animateBack();
                return true;

            default:
                return true;
        }
    }

    /**
     * A running flip or card-transition animation owns the card until its end callback resets the
     * state. Starting another touch sequence used to call animate().cancel(), which skipped that
     * callback and left the activity permanently locked. Consume the whole sequence instead.
     */
    private boolean consumeLockedTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            blockedTouchSequence = listener != null && listener.isInteractionLocked();
            if (blockedTouchSequence) clearGesture(false);
        }
        if (!blockedTouchSequence) return false;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            blockedTouchSequence = false;
            clearGesture(false);
        }
        return true;
    }

    private void beginGesture(MotionEvent event) {
        animate().cancel();
        activePointerId = event.getPointerId(0);
        downRawX = rawX(event, 0);
        downRawY = rawY(event, 0);
        dragX = 0f;
        dragY = 0f;
        axis = Axis.UNDECIDED;
        direction = null;
        recycleVelocityTracker();
        velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);
    }

    private void decideAxis(MotionEvent event) {
        int index = pointerIndex(event);
        if (index < 0) return;
        float dx = rawX(event, index) - downRawX;
        float dy = rawY(event, index) - downRawY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        if (Math.max(absX, absY) < touchSlop) return;

        // A clear dominance ratio prevents diagonal movement from switching axes and shaking.
        final float dominance = 1.18f;
        if (absX >= absY * dominance) {
            axis = Axis.HORIZONTAL;
            direction = dx < 0f ? Direction.LEFT : Direction.RIGHT;
            getParent().requestDisallowInterceptTouchEvent(true);
            return;
        }

        if (absY >= absX * dominance) {
            if (listener.isFrontFace() && dy > 0f) {
                axis = Axis.DOWN;
                direction = Direction.DOWN;
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (!listener.isFrontFace()) {
                // The back-side ScrollView owns vertical gestures for the rest of this sequence.
                axis = Axis.CHILD_VERTICAL;
            } else {
                axis = Axis.REJECTED;
            }
        }
    }

    private void updateDrag(MotionEvent event) {
        int index = pointerIndex(event);
        if (index < 0) return;
        float dx = rawX(event, index) - downRawX;
        float dy = rawY(event, index) - downRawY;

        if (axis == Axis.HORIZONTAL) {
            // Remove the touch slop from the visual distance, avoiding a jump when interception begins.
            dragX = subtractSlop(dx);
            dragY = 0f;
            direction = dragX < 0f ? Direction.LEFT : Direction.RIGHT;
            setTranslationX(dragX);
            setTranslationY(0f);
            setRotation(0f);
            float threshold = horizontalThreshold();
            float progress = Math.min(1.35f, Math.abs(dragX) / threshold);
            listener.onDrag(direction, progress, progress >= 1f);
        } else if (axis == Axis.DOWN) {
            dragY = Math.max(0f, subtractSlop(dy));
            dragX = 0f;
            setTranslationX(0f);
            setTranslationY(dragY);
            setRotation(0f);
            float threshold = verticalThreshold();
            float progress = Math.min(1.35f, dragY / threshold);
            listener.onDrag(Direction.DOWN, progress, progress >= 1f);
        }
    }

    private void finishDrag() {
        float velocity = 0f;
        if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000);
            velocity = axis == Axis.HORIZONTAL
                    ? velocityTracker.getXVelocity(activePointerId)
                    : velocityTracker.getYVelocity(activePointerId);
        }

        float distance = axis == Axis.HORIZONTAL ? Math.abs(dragX) : dragY;
        float threshold = axis == Axis.HORIZONTAL ? horizontalThreshold() : verticalThreshold();
        boolean flingMatches = Math.abs(velocity) >= minimumFlingVelocity * 1.35f
                && (axis != Axis.DOWN || velocity > 0f)
                && (axis != Axis.HORIZONTAL || Math.signum(velocity) == Math.signum(dragX));
        boolean commit = distance >= threshold || (distance >= threshold * 0.58f && flingMatches);

        if (commit && direction != null && listener.onCommit(direction)) {
            flyOut(direction);
        } else {
            animateBack();
        }
        recycleVelocityTracker();
    }

    private void flyOut(Direction direction) {
        float targetX = 0f;
        float targetY = 0f;
        float rotation = 0f;
        if (direction == Direction.LEFT) {
            targetX = -Math.max(getWidth() * 1.28f, dp(460));
            rotation = -5f;
        } else if (direction == Direction.RIGHT) {
            targetX = Math.max(getWidth() * 1.28f, dp(460));
            rotation = 5f;
        } else {
            targetY = Math.max(getHeight() * 0.34f, dp(160));
        }
        animate()
                .translationX(targetX)
                .translationY(targetY)
                .rotation(rotation)
                .alpha(direction == Direction.DOWN ? 0.94f : 0f)
                .setDuration(170)
                .withEndAction(this::resetImmediately)
                .start();
    }

    void resetImmediately() {
        animate().cancel();
        setTranslationX(0f);
        setTranslationY(0f);
        setRotation(0f);
        setAlpha(1f);
        clearGesture(false);
        if (listener != null) listener.onReset();
    }

    private void animateBack() {
        animate()
                .translationX(0f)
                .translationY(0f)
                .rotation(0f)
                .alpha(1f)
                .setDuration(175)
                .withEndAction(() -> {
                    clearGesture(false);
                    if (listener != null) listener.onReset();
                })
                .start();
    }

    private void handlePointerUp(MotionEvent event) {
        int actionIndex = event.getActionIndex();
        if (event.getPointerId(actionIndex) != activePointerId) return;
        int replacement = actionIndex == 0 ? 1 : 0;
        if (replacement >= event.getPointerCount()) return;
        activePointerId = event.getPointerId(replacement);
        downRawX = rawX(event, replacement) - dragX;
        downRawY = rawY(event, replacement) - dragY;
    }

    private void clearGesture(boolean notify) {
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        dragX = 0f;
        dragY = 0f;
        axis = Axis.UNDECIDED;
        direction = null;
        recycleVelocityTracker();
        if (notify && listener != null) listener.onReset();
    }

    private void addMovement(MotionEvent event) {
        if (velocityTracker != null) velocityTracker.addMovement(event);
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private int pointerIndex(MotionEvent event) {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return 0;
        return event.findPointerIndex(activePointerId);
    }

    private float distanceFromDown(MotionEvent event) {
        int index = pointerIndex(event);
        if (index < 0) return Float.MAX_VALUE;
        return (float) Math.hypot(rawX(event, index) - downRawX, rawY(event, index) - downRawY);
    }

    private float rawX(MotionEvent event, int pointerIndex) {
        return event.getRawX() + event.getX(pointerIndex) - event.getX();
    }

    private float rawY(MotionEvent event, int pointerIndex) {
        return event.getRawY() + event.getY(pointerIndex) - event.getY();
    }

    private float subtractSlop(float value) {
        float magnitude = Math.max(0f, Math.abs(value) - touchSlop);
        return Math.copySign(magnitude, value);
    }

    private float horizontalThreshold() {
        return Math.max(dp(64), getWidth() * 0.245f);
    }

    private float verticalThreshold() {
        return Math.max(dp(44), getHeight() * 0.09f);
    }

    private int dp(float value) {
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
