package com.chat.dating;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 首页滑卡完成后的强化动效。 */
public class DatingSwipeFxView extends FrameLayout {
    public DatingSwipeFxView(Context context) { super(context); init(); }
    public DatingSwipeFxView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public DatingSwipeFxView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
    }

    public void playLike() {
        playTextBurst("❤", Color.parseColor("#FF3B6D"), 86, true);
    }

    public void playFavorite() {
        playTextBurst("★", Color.parseColor("#5D95FF"), 74, true);
    }

    public void playPass() {
        removeAllViews();
        post(() -> {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER);
            ImageView center = brokenHeart();
            addView(center, lp);
            center.setScaleX(0.28f);
            center.setScaleY(0.28f);
            center.setAlpha(0f);

            List<Animator> animators = new ArrayList<>();
            AnimatorSet core = new AnimatorSet();
            core.playTogether(
                    ObjectAnimator.ofFloat(center, View.SCALE_X, 0.28f, 1.15f, 1.7f),
                    ObjectAnimator.ofFloat(center, View.SCALE_Y, 0.28f, 1.15f, 1.7f),
                    ObjectAnimator.ofFloat(center, View.ALPHA, 0f, 1f, 0f),
                    ObjectAnimator.ofFloat(center, View.ROTATION, 0f, -7f, 5f)
            );
            core.setDuration(520);
            core.setInterpolator(new DecelerateInterpolator());
            animators.add(core);

            int[][] points = new int[][]{
                    {-118, -74}, {-78, 104}, {-12, -148}, {68, 112}, {126, -62}, {-140, 24}
            };
            for (int i = 0; i < points.length; i++) {
                ImageView child = brokenHeart();
                FrameLayout.LayoutParams childLp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
                addView(child, childLp);
                child.setAlpha(0f);
                child.setScaleX(0.3f);
                child.setScaleY(0.3f);
                AnimatorSet item = new AnimatorSet();
                item.playTogether(
                        ObjectAnimator.ofFloat(child, View.TRANSLATION_X, 0f, dp(points[i][0])),
                        ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, 0f, dp(points[i][1])),
                        ObjectAnimator.ofFloat(child, View.SCALE_X, 0.3f, 1f, 0.72f),
                        ObjectAnimator.ofFloat(child, View.SCALE_Y, 0.3f, 1f, 0.72f),
                        ObjectAnimator.ofFloat(child, View.ALPHA, 0f, 0.9f, 0f),
                        ObjectAnimator.ofFloat(child, View.ROTATION, 0f, (i % 2 == 0 ? -20f : 20f))
                );
                item.setStartDelay(i * 42L);
                item.setDuration(430);
                item.setInterpolator(new DecelerateInterpolator());
                animators.add(item);
            }

            AnimatorSet all = new AnimatorSet();
            all.playTogether(animators);
            all.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { removeAllViews(); }
            });
            all.start();
        });
    }

    private void playTextBurst(String text, int color, int centerSp, boolean rise) {
        removeAllViews();
        post(() -> {
            TextView center = buildText(text, centerSp, color);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            addView(center, lp);
            center.setScaleX(0.35f);
            center.setScaleY(0.35f);
            center.setAlpha(0f);

            List<Animator> list = new ArrayList<>();
            AnimatorSet core = new AnimatorSet();
            core.playTogether(
                    ObjectAnimator.ofFloat(center, View.SCALE_X, 0.35f, 1.2f, 1.85f),
                    ObjectAnimator.ofFloat(center, View.SCALE_Y, 0.35f, 1.2f, 1.85f),
                    ObjectAnimator.ofFloat(center, View.ALPHA, 0f, 1f, 0f)
            );
            core.setDuration(520);
            core.setInterpolator(new DecelerateInterpolator());
            list.add(core);

            int[][] points = rise
                    ? new int[][]{{-120, -40}, {-70, -130}, {0, -170}, {76, -122}, {132, -52}, {-138, 62}, {122, 72}}
                    : new int[][]{{-118, -26}, {-86, 108}, {0, -150}, {94, 116}, {138, -18}, {-146, -92}, {144, -92}};

            for (int i = 0; i < points.length; i++) {
                TextView child = buildText(text, 25, color);
                addView(child, lp);
                child.setAlpha(0f);
                child.setScaleX(0.3f);
                child.setScaleY(0.3f);
                AnimatorSet item = new AnimatorSet();
                item.playTogether(
                        ObjectAnimator.ofFloat(child, View.TRANSLATION_X, 0f, dp(points[i][0])),
                        ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, 0f, dp(points[i][1])),
                        ObjectAnimator.ofFloat(child, View.SCALE_X, 0.3f, 1f, 0.8f),
                        ObjectAnimator.ofFloat(child, View.SCALE_Y, 0.3f, 1f, 0.8f),
                        ObjectAnimator.ofFloat(child, View.ALPHA, 0f, 1f, 0f)
                );
                item.setStartDelay(40L * i);
                item.setDuration(460);
                item.setInterpolator(new DecelerateInterpolator());
                list.add(item);
            }

            AnimatorSet all = new AnimatorSet();
            all.playTogether(list);
            all.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { removeAllViews(); }
            });
            all.start();
        });
    }

    private ImageView brokenHeart() {
        ImageView view = new ImageView(getContext());
        view.setImageResource(R.drawable.ic_dating_broken_heart_gray);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return view;
    }

    private TextView buildText(String text, int sp, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setShadowLayer(dp(8), 0f, dp(2), 0x66000000);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
