package com.chat.learning;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;

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
        sub.setText("选择 AI 网页、口语场景、语音朗读或脚本管理。官方场景先内置在 App，后续可热更新。");
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(107, 114, 128));
        sub.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, dp(18));
        root.addView(sub, subLp);

        root.addView(card("DeepSeek", "打开 DeepSeek 网页，支持脚本和场景提示词。", "进入", () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));
        root.addView(card("886.best", "打开 https://886.best，作为学习页独立 AI 入口。", "进入", () -> AiScriptWebActivity.open(requireContext(), "886.best", "https://886.best")));
        root.addView(card("千问国内版", "打开 qianwen.com，适合国内用户和全能助手场景。", "进入", () -> AiScriptWebActivity.open(requireContext(), "千问国内版", "https://www.qianwen.com/")));
        root.addView(card("Qwen 国际版", "打开 chat.qwen.ai，适合纯聊天和语音练习。", "进入", () -> AiScriptWebActivity.open(requireContext(), "Qwen 国际版", "https://chat.qwen.ai/")));
        root.addView(card("语音朗读", "打开 wkspeech：导入 TTS 源、选择发音人、设置语速音调、开启混读。", "设置", this::openSpeechSettings));
        root.addView(card("高频生活场景", "点餐、面试、打招呼、医院、机场等场景 prompt。", "选择", this::showPromptScenes));
        root.addView(card("脚本管理", "新增、导入、在线安装、启用官方推荐脚本。", "管理", () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        TextView warn = new TextView(requireContext());
        warn.setText("网页服务由对应平台提供。唐僧叨叨仅提供学习入口、场景提示词、本地脚本管理和语音朗读设置，不保存网页登录密码。");
        warn.setTextSize(12);
        warn.setTextColor(Color.rgb(107, 114, 128));
        warn.setPadding(dp(4), dp(14), dp(4), 0);
        root.addView(warn, new LinearLayout.LayoutParams(-1, -2));
        return scrollView;
    }

    private void openSpeechSettings() {
        try {
            Class<?> clazz = Class.forName("com.chat.speech.ui.SpeechSettingsActivity");
            startActivity(new Intent(requireContext(), clazz));
        } catch (Throwable e) {
            Toast.makeText(requireContext(), "语音插件未安装或 wkspeech 模块未打包", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPromptScenes() {
        final String[] names = new String[]{
                "日常打招呼",
                "点餐买东西",
                "求职面试",
                "医院看病",
                "机场过关",
                "租房沟通",
                "中文老师口语陪练",
                "中缅互译练习"
        };
        final String[] prompts = new String[]{
                "你是多语言口语陪练老师。请用中文、缅语和英文各给我 10 句自然的日常打招呼表达，每句附使用场景，并带慢速跟读版本。",
                "请生成点餐和买东西场景的实用口语，包含中文、缅语、英文三列。句子要短、自然、适合手机朗读练习。",
                "请模拟求职面试口语场景。先给常见问题，再给简短自然回答，最后给我可直接背诵的中文/缅语/英文版本。",
                "请生成医院看病常用表达，包含挂号、描述症状、买药、复诊。中文、缅语、英文对照，句子要简单。",
                "请生成机场过关常用口语，包含入境目的、住址、停留时间、行李说明。中文、缅语、英文对照。",
                "请生成租房沟通常用表达，包含看房、价格、押金、水电、维修。中文、缅语、英文对照。",
                "你是中文老师。请用适合初学者的方式教我这个场景的中文口语：先给短句，再给拼音，再给缅语解释，最后给练习对话。",
                "请把我接下来输入的内容做中缅互译。要求忠实原意、语气自然、不解释、不加内容，只输出译文。"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("高频生活场景")
                .setItems(names, (dialog, which) -> copyPrompt(names[which], prompts[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void copyPrompt(String name, String prompt) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(name, prompt));
            Toast.makeText(requireContext(), "已复制场景 prompt，可粘贴到 DeepSeek / 886.best / 千问", Toast.LENGTH_LONG).show();
        }
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
