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
        animateOut(top, action == null ? DatingSwipeAction.PASS : action, true);
    }

    private void rebuildDeck() {
        removeAllViews();
        int remain = profiles.size() - currentIndex;
        int count = Math.min(MAX_VISIBLE, Math.max(0, remain));
        for (int i = count - 1; i >= 0; i--) {
            DatingCardView card = new DatingCardView(getContext());
            card.bind(profiles.get(currentIndex + i));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            lp.setMargins(0, 0, 0, 0);
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
                float vy = velocityTracker.getYVelocity();
                recycleVelocityTracker();
                float totalDx = event.getRawX() - downX;
                float totalDy = event.getRawY() - downY;
                if (!dragging && Math.hypot(totalDx, totalDy) < touchSlop * 1.5f) {
                    handleTap(card, event.getX());
                    resetCard(card);
                    return true;
                }

                float horizontalThreshold = getWidth() * 0.26f;
                float downThreshold = getHeight() * 0.14f;
                boolean downFavorite = (totalDy > downThreshold || vy > 1250f)
                        && totalDy > Math.abs(totalDx) * 0.72f;
                if (downFavorite) {
                    animateOut(card, DatingSwipeAction.FAVORITE, false);
                } else if (Math.abs(card.getTranslationX()) > horizontalThreshold || Math.abs(vx) > 1100f) {
                    animateOut(card, card.getTranslationX() >= 0 || vx > 1100f ? DatingSwipeAction.LIKE : DatingSwipeAction.PASS, false);
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
        float downY = Math.max(0f, ty);
        card.setTranslationX(tx);
        card.setTranslationY(downY > Math.abs(tx) * 0.72f ? downY * 0.42f : ty * 0.26f);
        card.setRotation(12f * tx / width);
        card.setSwipeProgress(tx, downY);
        updateBackCards(Math.min(1f, Math.max(Math.abs(tx) / (width * 0.28f), downY / (Math.max(1f, getHeight()) * 0.18f))));
    }

    private void handleTap(DatingCardView card, float x) {
        float width = Math.max(1f, getWidth());
        if (x < width * 0.42f) {
            card.showPreviousPhoto();
        } else if (x > width * 0.58f) {
            card.showNextPhoto();
        }
    }

    private void animateOut(DatingCardView card, String action, boolean fromButton) {
        boolean favorite = DatingSwipeAction.FAVORITE.equals(action);
        boolean like = DatingSwipeAction.LIKE.equals(action);
        float targetX = favorite ? 0f : (like ? 1 : -1) * (getWidth() + dp(96));
        float targetY = favorite ? getHeight() + dp(96) : card.getTranslationY() + dp(18);
        card.setSwipeProgress(like ? getWidth() : (favorite ? 0f : -getWidth()), favorite ? getHeight() : 0f);
        card.animate()
                .translationX(targetX)
                .translationY(targetY)
                .rotation(favorite ? 0f : (like ? 1 : -1) * 16f)
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
                        card.setSwipeProgress(0f, 0f);
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
        float baseScale = 1f - 0.026f * stackIndex;
        float baseY = dp(10) * stackIndex;
        if (stackIndex > 0) {
            baseScale += 0.032f * progress;
            baseY -= dp(10) * progress;
        }
        view.setScaleX(baseScale);
        view.setScaleY(baseScale);
        view.setTranslationY(baseY);
        view.setAlpha(stackIndex >= 2 ? 0.92f : 1f);
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
