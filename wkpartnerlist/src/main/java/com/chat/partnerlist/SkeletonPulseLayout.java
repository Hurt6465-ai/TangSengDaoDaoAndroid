package com.chat.partnerlist;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

public class SkeletonPulseLayout extends LinearLayout {
    private ObjectAnimator animator;

    public SkeletonPulseLayout(Context context) { this(context, null); }
    public SkeletonPulseLayout(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startPulse();
    }

    @Override protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    public void startPulse() {
        if (animator != null && animator.isRunning()) return;
        animator = ObjectAnimator.ofFloat(this, ALPHA, 0.72f, 0.98f);
        animator.setDuration(1200L);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    public void stopPulse() {
        if (animator != null) animator.cancel();
        animator = null;
        setAlpha(1f);
    }
}
