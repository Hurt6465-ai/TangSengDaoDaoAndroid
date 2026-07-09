package com.chat.dating;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.List;

public class DatingSwipeDeckView extends FrameLayout {
    private static final int MAX_VISIBLE = 3;
    private final ArrayList<DatingProfile> profiles = new ArrayList<>();
    private OnDeckActionListener listener;
    private int currentIndex;
    private float downX;
    private float downY;
    private float startTranslationX;
    private float startTranslationY;
    private boolean dragging;
    private final int touchSlop;
    private VelocityTracker velocityTracker;

    public DatingSwipeDeckView(@NonNull Context context) {
        this(context, null);
    }

    public DatingSwipeDeckView(@NonNull Context context, @Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setOnDeckActionListener(OnDeckActionListener listener) {
        this.listener = listener;
    }

    public void setProfiles(List<DatingProfile> data) {
        profiles.clear();
        if (data != null) profiles.addAll(data);
        currentIndex = 0;
        rebuildDeck();
        notifyCurrentChanged();
    }

    public void appendProfiles(List<DatingProfile> data) {
        if (data == null || data.isEmpty()) return;
        profiles.addAll(data);
        if (getChildCount() == 0) {
            currentIndex = Math.min(currentIndex, Math.max(0, profiles.size() - 1));
            rebuildDeck();
            notifyCurrentChanged();
        }
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public DatingProfile getCurrentProfile() {
        if (currentIndex < 0 || currentIndex >= profiles.size()) return null;
        return profiles.get(currentIndex);
    }

    public int getCurrentPhotoIndex() {
        DatingCardView top = topCard();
        return top == null ? 0 : top.getPhotoIndex();
    }

    public int remainingCount() {
        return Math.max(0, profiles.size() - currentIndex);
    }

    public void swipeTop(String action) {
        DatingCardView top = topCard();
        if (top == null) return;
        int direction = DatingSwipeAction.LIKE.equals(action) ? 1 : -1;
        animateOut(top, direction, action, true);
    }

    private void rebuildDeck() {
        removeAllViews();
        int remain = profiles.size() - currentIndex;
        int count = Math.min(MAX_VISIBLE, Math.max(0, remain));
        for (int i = count - 1; i >= 0; i--) {
            DatingCardView card = new DatingCardView(getContext());
            card.bind(profiles.get(currentIndex + i));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(12), dp(118), dp(12), dp(102));
            addView(card, lp);
            applyStackTransform(card, i, 0f);
            if (i == 0) attachTouch(card);
            else card.setOnTouchListener(null);
        }
    }

    private void attachTouch(DatingCardView card) {
        card.setOnTouchListener((v, event) -> handleTouch(card, event));
    }

    private boolean handleTouch(DatingCardView card, MotionEvent event) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                startTranslationX = card.getTranslationX();
                startTranslationY = card.getTranslationY();
                dragging = false;
                card.animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) dragging = true;
                if (dragging) {
                    updateDrag(card, startTranslationX + dx, startTranslationY + dy);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.computeCurrentVelocity(1000);
                float vx = velocityTracker.getXVelocity();
                recycleVelocityTracker();
                float totalDx = event.getRawX() - downX;
                float totalDy = event.getRawY() - downY;
                if (!dragging && Math.hypot(totalDx, totalDy) < touchSlop * 1.5f) {
                    handleTap(card, event.getX());
                    resetCard(card);
                    return true;
                }
                float threshold = getWidth() * 0.28f;
                if (Math.abs(card.getTranslationX()) > threshold || Math.abs(vx) > 1100f) {
                    int direction = card.getTranslationX() >= 0 || vx > 1100f ? 1 : -1;
                    animateOut(card, direction, direction > 0 ? DatingSwipeAction.LIKE : DatingSwipeAction.PASS, false);
                } else {
                    resetCard(card);
                }
                return true;
            default:
                return true;
        }
    }

    private void updateDrag(DatingCardView card, float tx, float ty) {
        float width = Math.max(1f, getWidth());
        float progress = Math.min(1f, Math.abs(tx) / (width * 0.28f));
        card.setTranslationX(tx);
        card.setTranslationY(ty * 0.28f);
        card.setRotation(14f * tx / width);
        card.setSwipeProgress(tx);
        updateBackCards(progress);
    }

    private void handleTap(DatingCardView card, float x) {
        float width = Math.max(1f, getWidth());
        if (x < width * 0.42f) {
            card.showPreviousPhoto();
        } else if (x > width * 0.58f) {
            card.showNextPhoto();
        } else if (listener != null) {
            listener.onCardCenterTap(card.getProfile());
        }
    }

    private void animateOut(DatingCardView card, int direction, String action, boolean fromButton) {
        float targetX = direction * (getWidth() + dp(96));
        float targetY = card.getTranslationY() + dp(26);
        card.setSwipeProgress(direction * getWidth());
        card.animate()
                .translationX(targetX)
                .translationY(targetY)
                .rotation(direction * 18f)
                .setDuration(fromButton ? 230 : 190)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        DatingProfile swiped = card.getProfile();
                        int photoIndex = card.getPhotoIndex();
                        currentIndex++;
                        rebuildDeck();
                        if (listener != null) listener.onSwiped(swiped, action, photoIndex, currentIndex);
                        notifyCurrentChanged();
                        if (remainingCount() == 0 && listener != null) listener.onDeckEmpty();
                    }
                })
                .start();
    }

    private void resetCard(DatingCardView card) {
        card.animate()
                .translationX(0f)
                .translationY(0f)
                .rotation(0f)
                .setDuration(210)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        card.setSwipeProgress(0f);
                        updateBackCards(0f);
                    }
                })
                .start();
    }

    private DatingCardView topCard() {
        int count = getChildCount();
        if (count == 0) return null;
        View view = getChildAt(count - 1);
        return view instanceof DatingCardView ? (DatingCardView) view : null;
    }

    private void applyStackTransform(View view, int stackIndex, float progress) {
        float baseScale = 1f - 0.038f * stackIndex;
        float baseY = dp(14) * stackIndex;
        if (stackIndex > 0) {
            baseScale += 0.045f * progress;
            baseY -= dp(14) * progress;
        }
        view.setScaleX(baseScale);
        view.setScaleY(baseScale);
        view.setTranslationY(baseY);
        view.setAlpha(stackIndex >= 2 ? 0.82f : 1f);
    }

    private void updateBackCards(float progress) {
        int visibleCount = getChildCount();
        for (int childIndex = 0; childIndex < visibleCount; childIndex++) {
            View view = getChildAt(childIndex);
            int stackIndex = visibleCount - 1 - childIndex;
            if (stackIndex > 0) applyStackTransform(view, stackIndex, progress);
        }
    }

    private void notifyCurrentChanged() {
        if (listener != null) listener.onCurrentChanged(getCurrentProfile(), currentIndex);
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    public interface OnDeckActionListener {
        void onCurrentChanged(DatingProfile profile, int index);
        void onSwiped(DatingProfile profile, String action, int photoIndex, int nextIndex);
        void onDeckEmpty();
        void onCardCenterTap(DatingProfile profile);
    }
}
