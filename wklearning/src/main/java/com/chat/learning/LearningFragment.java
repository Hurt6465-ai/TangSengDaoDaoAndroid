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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;
import com.chat.speech.ui.SpeechSettingsActivity;

public class LearningFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 249, 252));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(requireContext());
        title.setText("学习");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("第三阶段测试版：先接 DeepSeek、千问和用户脚本入口。后面再继续加课程、词汇、翻译、AI 学习工具。");
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(107, 114, 128));
        sub.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, dp(18));
        root.addView(sub, subLp);

        root.addView(card("DeepSeek", "打开 chat.deepseek.com，支持已安装脚本注入。", "进入", () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));
        root.addView(card("千问 / Qwen", "打开 https://chat.qwen.ai/，支持已安装脚本注入。", "进入", () -> AiScriptWebActivity.open(requireContext(), "千问", "https://chat.qwen.ai/")));
        root.addView(card("添加脚本", "粘贴或导入 .user.js。脚本只允许在 DeepSeek / 千问相关域名运行。", "管理", () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));
        root.addView(card("语音朗读", "独立 wkspeech 插件：系统 TTS 兜底，支持导入 MultiTTS 微软包，支持中文/缅语一句话双发音人测试。", "设置", () -> startActivity(new Intent(requireContext(), SpeechSettingsActivity.class))));

        TextView warn = new TextView(requireContext());
        warn.setText("安全说明：当前不会把脚本接入唐僧原生聊天、语伴、发现、通话，也不会开放登录 token、联系人、相册、定位、支付、IM 发消息能力。");
        warn.setTextSize(12);
        warn.setTextColor(Color.rgb(107, 114, 128));
        warn.setPadding(dp(4), dp(14), dp(4), 0);
        root.addView(warn, new LinearLayout.LayoutParams(-1, -2));
        return scrollView;
    }

    private View card(String title, String desc, String action, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(rounded(Color.WHITE, dp(22), Color.rgb(232, 237, 246), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(lp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(19);
        titleView.setTextColor(Color.rgb(17, 24, 39));
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView descView = new TextView(requireContext());
        descView.setText(desc);
        descView.setTextSize(14);
        descView.setTextColor(Color.rgb(107, 114, 128));
        descView.setPadding(0, dp(7), 0, dp(13));
        card.addView(descView, new LinearLayout.LayoutParams(-1, -2));

        TextView actionView = new TextView(requireContext());
        actionView.setText(action);
        actionView.setTextSize(15);
        actionView.setTypeface(Typeface.DEFAULT_BOLD);
        actionView.setTextColor(Color.WHITE);
        actionView.setGravity(Gravity.CENTER);
        actionView.setBackground(rounded(Color.rgb(24, 119, 242), dp(16), Color.TRANSPARENT, 0));
        card.addView(actionView, new LinearLayout.LayoutParams(-1, dp(46)));
        card.setOnClickListener(v -> click.run());
        actionView.setOnClickListener(v -> click.run());
        return card;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
