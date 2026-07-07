package com.chat.learning;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 底部导航里的轻量入口。真正学习系统走独立 LearningActivity，避免侧边栏和底部导航层级冲突。
 */
public class LearningFragment extends Fragment {
    private boolean openedOnce = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(0xFFF6F8FC);

        TextView title = new TextView(requireContext());
        title.setText("学习");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF111827);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(requireContext());
        desc.setText("学习系统已改为独立全屏插件：沉浸式海报、无圆角侧边栏、原生 ViewPager2、SM-2 背单词。点击进入。 ");
        desc.setTextSize(14);
        desc.setTextColor(0xFF6B7280);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.setMargins(0, dp(10), 0, dp(22));
        root.addView(desc, descLp);

        TextView button = new TextView(requireContext());
        button.setText("进入学习系统");
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundGradient());
        button.setOnClickListener(v -> openLearning());
        root.addView(button, new LinearLayout.LayoutParams(-1, dp(52)));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!openedOnce) {
            openedOnce = true;
            // 延迟一帧，避免 Fragment 尚未 attach 完成时启动 Activity。
            requireView().postDelayed(this::openLearning, 120);
        }
    }

    private void openLearning() {
        if (!isAdded()) return;
        startActivity(new Intent(requireContext(), LearningActivity.class));
    }

    private GradientDrawable roundGradient() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF1877F2, 0xFF5B8CFF});
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
