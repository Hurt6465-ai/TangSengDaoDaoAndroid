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
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 右滑/左滑强化动效。 */
public class DatingSwipeFxView extends FrameLayout {
    public DatingSwipeFxView(Context context) { super(context); init(); }
    public DatingSwipeFxView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public DatingSwipeFxView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
    }

    public void playLike() { playBurst("❤", Color.parseColor("#FF3B6D"), true); }
    public void playPass() { playBurst("✕", Color.parseColor("#FF566B"), false); }
    public void playFavorite() { playBurst("★", Color.parseColor("#5D95FF"), true); }

    private void playBurst(String text, int color, boolean rise) {
        removeAllViews();
        post(() -> {
            TextView center = build(text, 84, color, 0.98f);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            addView(center, lp);
            center.setScaleX(0.35f);
            center.setScaleY(0.35f);
            center.setAlpha(0f);

            List<Animator> list = new ArrayList<>();
            AnimatorSet core = new AnimatorSet();
            core.playTogether(
                    ObjectAnimator.ofFloat(center, SCALE_X, 0.35f, 1.2f, 1.85f),
                    ObjectAnimator.ofFloat(center, SCALE_Y, 0.35f, 1.2f, 1.85f),
                    ObjectAnimator.ofFloat(center, ALPHA, 0f, 1f, 0f)
            );
            core.setDuration(520);
            core.setInterpolator(new DecelerateInterpolator());
            list.add(core);

            int[][] points = rise
                    ? new int[][]{{-120, -40}, {-70, -130}, {0, -170}, {76, -122}, {132, -52}, {-138, 62}, {122, 72}}
                    : new int[][]{{-118, -26}, {-86, 108}, {0, -150}, {94, 116}, {138, -18}, {-146, -92}, {144, -92}};

            for (int i = 0; i < points.length; i++) {
                TextView child = build(text, rise ? 26 : 24, color, 0.90f);
                addView(child, lp);
                child.setAlpha(0f);
                child.setScaleX(0.3f);
                child.setScaleY(0.3f);
                float tx = dp(points[i][0]);
                float ty = dp(points[i][1]);
                AnimatorSet item = new AnimatorSet();
                item.playTogether(
                        ObjectAnimator.ofFloat(child, TRANSLATION_X, 0f, tx),
                        ObjectAnimator.ofFloat(child, TRANSLATION_Y, 0f, ty),
                        ObjectAnimator.ofFloat(child, SCALE_X, 0.3f, 1.0f, 0.8f),
                        ObjectAnimator.ofFloat(child, SCALE_Y, 0.3f, 1.0f, 0.8f),
                        ObjectAnimator.ofFloat(child, ALPHA, 0f, 1f, 0f)
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

    private TextView build(String text, int sp, int color, float alpha) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setAlpha(alpha);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setShadowLayer(dp(8), 0f, dp(2), 0x66000000);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
