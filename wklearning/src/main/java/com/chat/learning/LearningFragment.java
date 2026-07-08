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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;

/**
 * 学习首页：高端极简风格。
 * 顶部沉浸式 Banner，4个核心工具入口（带图标），底部学习分区卡片化去图标。
 */
public class LearningFragment extends Fragment {
    // 采用类似 Apple 的高级极简色彩体系
    private static final int COLOR_BG = 0xFFF5F5F7; // 高级浅灰背
    private static final int COLOR_TEXT = 0xFF1D1D1F; // 深邃黑
    private static final int COLOR_SUB = 0xFF86868B; // 高级灰副标题
    private static final int COLOR_ACCENT = 0xFF007AFF; // 纯净品牌蓝

    private DrawerLayout drawerLayout;
    private long lastCardClickTime = 0L;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        drawerLayout = new DrawerLayout(requireContext());
        drawerLayout.setBackgroundColor(COLOR_BG);
        drawerLayout.setScrimColor(0x80000000); // 加深侧边栏暗化效果
        drawerLayout.setDrawerElevation(dp(16));

        View main = createMainPage();
        drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));

        View drawer = createSideDrawer();
        DrawerLayout.LayoutParams drawerLp = new DrawerLayout.LayoutParams(getDrawerWidth(), -1);
        drawerLp.gravity = GravityCompat.START;
        drawerLayout.addView(drawer, drawerLp);
        return drawerLayout;
    }

    private View createMainPage() {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(40)); // 底部留白更多
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(createHero(), new LinearLayout.LayoutParams(-1, dp(280))); // Banner稍微加高

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), 0); // 增加两侧内边距，提升呼吸感
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));

        // 核心 4 个工具（带图标）
        content.addView(createToolsRow(), new LinearLayout.LayoutParams(-1, dp(100)));
        addSpace(content, 28);

        // 以下学习模块完全去除图标，改用极简卡片
        addSection(content, "拼音", null,
                new CardSpec[]{
                        new CardSpec("声母", "b p m f", "initials"),
                        new CardSpec("韵母", "a o e i u", "finals"),
                        new CardSpec("整体", "zhi chi shi", "whole"),
                        new CardSpec("声调", "一二三四声", "tone")
                }, 2);

        addSection(content, "单词", "更多",
                new CardSpec[]{
                        new CardSpec("HSK 1", "150 词", "hsk1"),
                        new CardSpec("HSK 2", "300 词", "hsk2"),
                        new CardSpec("HSK 3", "600 词", "hsk3")
                }, 3);

        addSection(content, "口语", "更多",
                new CardSpec[]{
                        new CardSpec("打招呼", "日常开场", "speak_hello"),
                        new CardSpec("点餐", "餐厅购物", "speak_food"),
                        new CardSpec("求职", "面试工作", "speak_job")
                }, 3);

        addSection(content, "句型", "更多",
                new CardSpec[]{
                        new CardSpec("我想…", "表达需求", "pattern_want"),
                        new CardSpec("可以吗", "请求帮助", "pattern_can"),
                        new CardSpec("怎么…", "询问方法", "pattern_how")
                }, 3);

        addSection(content, "语法", "更多",
                new CardSpec[]{
                        new CardSpec("了", "完成/变化", "grammar_le"),
                        new CardSpec("在", "正在进行", "grammar_zai"),
                        new CardSpec("吗 / 呢", "疑问语气", "grammar_ma")
                }, 3);

        return scrollView;
    }

    private View createHero() {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setClipToPadding(false);

        ImageView bg = new ImageView(requireContext());
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageResource(R.drawable.learning_home_banner_default);
        hero.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        // 重新设计沉浸式遮罩：从透明平滑过渡到纯黑，让文字像浮在上面一样清晰
        View overlay = new View(requireContext());
        overlay.setBackground(gradient(Color.TRANSPARENT, 0xCC000000, 0, Color.TRANSPARENT, 0));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM | Gravity.START);
        copy.setPadding(dp(24), dp(24), dp(24), dp(24));
        hero.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        // 高级感标签
        TextView badge = new TextView(requireContext());
        badge.setText("0 基础入门");
        badge.setTextSize(12);
        badge.setTextColor(0xFFFFFFFF);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(rounded(0x40FFFFFF, dp(6), Color.TRANSPARENT, 0)); // 极简磨砂块
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.setMargins(0, 0, 0, dp(12));
        copy.addView(badge, badgeLp);

        TextView title = new TextView(requireContext());
        title.setText("中文核心突破");
        title.setTextSize(32); // 放大标题增强视觉冲击
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("每天 10 分钟，与 AI 语伴沉浸式对练");
        sub.setTextSize(15);
        sub.setTextColor(0xD9FFFFFF); // 85% 白色
        sub.setPadding(0, dp(6), 0, dp(20));
        copy.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        // 按钮区域
        TextView start = new TextView(requireContext());
        start.setText("开始今日学习");
        start.setTextSize(15);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setTextColor(COLOR_TEXT);
        start.setGravity(Gravity.CENTER);
        start.setBackground(rounded(Color.WHITE, dp(24), Color.TRANSPARENT, 0)); // 高纯度白底大圆角按钮
        start.setOnClickListener(v -> Toast.makeText(requireContext(), "从拼音开始学习", Toast.LENGTH_SHORT).show());
        copy.addView(start, new LinearLayout.LayoutParams(dp(140), dp(48)));

        // 汉堡菜单按钮
        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END | Gravity.TOP);
        menuLp.setMargins(0, dp(32), dp(16), 0);
        hero.addView(createMenuHandle(), menuLp);

        return hero;
    }

    private View createMenuHandle() {
        FrameLayout box = new FrameLayout(requireContext());
        box.setOnClickListener(v -> openDrawer());

        LinearLayout bars = new LinearLayout(requireContext());
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.CENTER);
        box.addView(bars, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        bars.addView(menuBar(16), new LinearLayout.LayoutParams(dp(3), dp(16)));
        addHorizontalGap(bars, 5);
        bars.addView(menuBar(22), new LinearLayout.LayoutParams(dp(3), dp(22)));
        addHorizontalGap(bars, 5);
        bars.addView(menuBar(14), new LinearLayout.LayoutParams(dp(3), dp(14)));
        return box;
    }

    private View menuBar(int heightDp) {
        View view = new View(requireContext());
        view.setBackground(rounded(Color.WHITE, dp(1.5f), Color.TRANSPARENT, 0));
        return view;
    }

    // 顶部四个核心工具：保留图标，采用纯净悬浮卡片
    private View createToolsRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        
        row.addView(toolCard("🌐", "AI翻译", () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(row, 12);
        row.addView(toolCard("📚", "电子书", this::showBookPage), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(row, 12);
        row.addView(toolCard("🎙", "口语伴", this::showPromptScenes), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(row, 12);
        row.addView(toolCard("✍", "练习题", () -> Toast.makeText(requireContext(), "练习题后续接入", Toast.LENGTH_SHORT).show()), new LinearLayout.LayoutParams(0, -1, 1f));
        return row;
    }

    private View toolCard(String icon, String title, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        // 高级悬浮白卡
        card.setBackground(rounded(Color.WHITE, dp(16), 0xFFE5E5EA, 1));
        card.setElevation(dp(2)); // 微弱质感阴影
        card.setOnClickListener(v -> runCardClick(click));

        TextView iconView = new TextView(requireContext());
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setGravity(Gravity.CENTER);
        card.addView(iconView, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(COLOR_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private void addSection(LinearLayout parent, String title, String more, CardSpec[] cards, int columns) {
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.BOTTOM);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(0, dp(12), 0, dp(14));
        parent.addView(header, headerLp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (more != null) {
            TextView moreView = new TextView(requireContext());
            moreView.setText(more);
            moreView.setTextSize(14);
            moreView.setTextColor(COLOR_SUB);
            moreView.setOnClickListener(v -> openMorePage(title));
            header.addView(moreView, new LinearLayout.LayoutParams(-2, -2));
        }

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        parent.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        int index = 0;
        while (index < cards.length) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(80)); // 高度降低，更为精致
            rowLp.setMargins(0, 0, 0, dp(12));
            grid.addView(row, rowLp);

            for (int i = 0; i < columns; i++) {
                if (index < cards.length) {
                    row.addView(smallCard(cards[index]), new LinearLayout.LayoutParams(0, -1, 1f));
                    index++;
                } else {
                    View empty = new View(requireContext());
                    row.addView(empty, new LinearLayout.LayoutParams(0, -1, 1f));
                }
                if (i < columns - 1) addHorizontalGap(row, 12);
            }
        }
        addSpace(parent, 10);
    }

    // 学习小卡片：【完全去除图标】，采用高级文字排版
    private View smallCard(CardSpec spec) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        card.setPadding(dp(16), 0, dp(16), 0);
        // 纯净白底，极细描边，去除杂乱马卡龙色
        card.setBackground(rounded(Color.WHITE, dp(14), 0xFFEAEAEA, dp(1)));
        card.setOnClickListener(v -> runCardClick(() -> onSmallCardClick(spec)));

        TextView title = new TextView(requireContext());
        title.setText(spec.title);
        title.setTextSize(16);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(requireContext());
        desc.setText(spec.desc);
        desc.setTextSize(12);
        desc.setTextColor(COLOR_SUB);
        desc.setPadding(0, dp(2), 0, 0);
        card.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        return card;
    }

    private View createSideDrawer() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(40), dp(20), dp(20));
        panel.setBackgroundColor(Color.WHITE); // 抽屉采用极简纯白
        panel.setClickable(true);

        TextView title = new TextView(requireContext());
        title.setText("更多服务");
        title.setTextSize(24);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("在这里探索扩展工具与脚本");
        sub.setTextSize(14);
        sub.setTextColor(COLOR_SUB);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(4), 0, dp(24));
        panel.addView(sub, subLp);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        list.addView(drawerCard("DeepSeek 翻译", "中缅互译、语法解释", () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));
        list.addView(drawerCard("886.best", "国内学习 AI 入口", () -> AiScriptWebActivity.open(requireContext(), "886.best", "https://886.best")));
        list.addView(drawerCard("千问国内版", "qianwen.com", () -> AiScriptWebActivity.open(requireContext(), "千问国内版", "https://www.qianwen.com/")));
        list.addView(drawerCard("Qwen 国际版", "chat.qwen.ai", () -> AiScriptWebActivity.open(requireContext(), "Qwen 国际版", "https://chat.qwen.ai/")));
        list.addView(drawerCard("语音设置", "WKSpeech 引擎配置", this::openSpeechSettings));
        list.addView(drawerCard("口语 Prompt", "生活场景对话指令", this::showPromptScenes));
        list.addView(drawerCard("脚本中心", "用户自定义插件库", () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return panel;
    }

    private View drawerCard(String title, String desc, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(COLOR_BG, dp(12), Color.TRANSPARENT, 0)); // 浅灰卡片
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        card.setOnClickListener(v -> {
            closeDrawer();
            runCardClick(click);
        });

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(COLOR_TEXT);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView d = new TextView(requireContext());
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(COLOR_SUB);
        d.setPadding(0, dp(4), 0, 0);
        card.addView(d, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private void onSmallCardClick(CardSpec spec) {
        if (spec.id.startsWith("hsk")) {
            Intent intent = new Intent(requireContext(), WordFullscreenActivity.class);
            intent.putExtra(WordFullscreenActivity.EXTRA_LEVEL, spec.id);
            intent.putExtra(WordFullscreenActivity.EXTRA_TITLE, spec.title);
            startActivity(intent);
        } else {
            Toast.makeText(requireContext(), spec.title + " 正在开发中", Toast.LENGTH_SHORT).show();
        }
    }

    private void openMorePage(String section) {
        if ("单词".equals(section)) {
            final String[] items = new String[]{"HSK 1", "HSK 2", "HSK 3", "HSK 4", "HSK 5", "HSK 6", "生活高频", "工作求职", "恋爱聊天"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("更多单词")
                    .setItems(items, (dialog, which) -> {
                        Intent intent = new Intent(requireContext(), WordFullscreenActivity.class);
                        intent.putExtra(WordFullscreenActivity.EXTRA_LEVEL, "hsk" + (which + 1));
                        intent.putExtra(WordFullscreenActivity.EXTRA_TITLE, items[which]);
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            Toast.makeText(requireContext(), section + " 更多内容即将上线", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBookPage() {
        Toast.makeText(requireContext(), "电子书架加载中...", Toast.LENGTH_SHORT).show();
    }

    private void openSpeechSettings() {
        try {
            Class<?> clazz = Class.forName("com.chat.speech.ui.SpeechSettingsActivity");
            startActivity(new Intent(requireContext(), clazz));
        } catch (Throwable e) {
            Toast.makeText(requireContext(), "语音插件未安装", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPromptScenes() {
        final String[] names = new String[]{"日常打招呼", "点餐买东西", "求职面试", "医院看病", "机场过关", "租房沟通", "口语陪练", "中缅互译"};
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
                .setTitle("选择一个场景Prompt")
                .setItems(names, (dialog, which) -> copyPrompt(names[which], prompts[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void copyPrompt(String name, String prompt) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(name, prompt));
            Toast.makeText(requireContext(), "已复制：" + name, Toast.LENGTH_SHORT).show();
        }
    }

    private void openDrawer() {
        if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
    }

    private void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
    }

    public boolean closeSideMenuIfOpen() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    private void runCardClick(Runnable click) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastCardClickTime < 600) return;
        lastCardClickTime = now;
        if (click != null) click.run();
    }

    private GradientDrawable gradient(int start, int end, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{start, end});
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View v = new View(requireContext());
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        View v = new View(requireContext());
        parent.addView(v, new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private int getDrawerWidth() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.75f); // 缩小抽屉宽度，更显精致
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // 精简了 CardSpec，去掉了无用的强彩色配置参数
    private static class CardSpec {
        final String title;
        final String desc;
        final String id;

        CardSpec(String title, String desc, String id) {
            this.title = title;
            this.desc = desc;
            this.id = id;
        }
    }
}
