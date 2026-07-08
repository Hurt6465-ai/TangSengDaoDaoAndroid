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
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;

import java.lang.reflect.Field;

/**
 * 学习首页：高端极简课程风格。
 * 保留报名 Banner，恢复大手势 Drawer，主页只做学习入口和工具入口。
 */
public class LearningFragment extends Fragment {
    private static final int COLOR_BG = 0xFFF5F6F8;
    private static final int COLOR_TEXT = 0xFF1D1D1F;
    private static final int COLOR_SUB = 0xFF86868B;

    private DrawerLayout drawerLayout;
    private long lastCardClickTime = 0L;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        drawerLayout = new DrawerLayout(requireContext());
        drawerLayout.setBackgroundColor(COLOR_BG);
        drawerLayout.setScrimColor(0x80000000);
        drawerLayout.setDrawerElevation(dp(16));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);

        View main = createMainPage();
        drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));

        View drawer = createSideDrawer();
        DrawerLayout.LayoutParams drawerLp = new DrawerLayout.LayoutParams(getDrawerWidth(), -1);
        drawerLp.gravity = GravityCompat.START;
        drawerLayout.addView(drawer, drawerLp);

        widenDrawerGestureArea(dp(72));

        return drawerLayout;
    }

    private View createMainPage() {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(40));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(createHero(), new LinearLayout.LayoutParams(-1, dp(240)));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), 0);
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));

        content.addView(createToolsRow(), new LinearLayout.LayoutParams(-1, dp(100)));
        addSpace(content, 24);

        addSection(content, "拼音", null,
                new CardSpec[]{
                        new CardSpec("声母", "b p m f", "initials", new int[]{0xFFF6F9FF, 0xFFEBF1FF}),
                        new CardSpec("韵母", "a o e i u", "finals", new int[]{0xFFF6F9FF, 0xFFEBF1FF}),
                        new CardSpec("整体", "zhi chi shi", "whole", new int[]{0xFFF6F9FF, 0xFFEBF1FF}),
                        new CardSpec("声调", "一二三四声", "tone", new int[]{0xFFF6F9FF, 0xFFEBF1FF})
                });

        addSection(content, "单词", "更多",
                new CardSpec[]{
                        new CardSpec("HSK 1", "150 词", "hsk1", new int[]{0xFFFBF8FF, 0xFFF1E8FF}),
                        new CardSpec("HSK 2", "300 词", "hsk2", new int[]{0xFFFBF8FF, 0xFFF1E8FF}),
                        new CardSpec("HSK 3", "600 词", "hsk3", new int[]{0xFFFBF8FF, 0xFFF1E8FF})
                });

        addSection(content, "口语", "更多",
                new CardSpec[]{
                        new CardSpec("打招呼", "日常开场", "speak_hello", new int[]{0xFFFFF8F6, 0xFFFFEBE3}),
                        new CardSpec("点餐", "餐厅购物", "speak_food", new int[]{0xFFFFF8F6, 0xFFFFEBE3}),
                        new CardSpec("求职", "面试工作", "speak_job", new int[]{0xFFFFF8F6, 0xFFFFEBE3}),
                        new CardSpec("购物", "买单砍价", "speak_shop", new int[]{0xFFFFF8F6, 0xFFFFEBE3})
                });

        addSection(content, "句型", "更多",
                new CardSpec[]{
                        new CardSpec("我想…", "表达需求", "pattern_want", new int[]{0xFFF4FBF7, 0xFFE5F6EC}),
                        new CardSpec("可以吗", "请求帮助", "pattern_can", new int[]{0xFFF4FBF7, 0xFFE5F6EC}),
                        new CardSpec("怎么…", "询问方法", "pattern_how", new int[]{0xFFF4FBF7, 0xFFE5F6EC}),
                        new CardSpec("为什么", "询问原因", "pattern_why", new int[]{0xFFF4FBF7, 0xFFE5F6EC})
                });

        addSection(content, "语法", "更多",
                new CardSpec[]{
                        new CardSpec("了", "完成/变化", "grammar_le", new int[]{0xFFFDF9F2, 0xFFF7EBD8}),
                        new CardSpec("在", "正在进行", "grammar_zai", new int[]{0xFFFDF9F2, 0xFFF7EBD8}),
                        new CardSpec("吗/呢", "疑问语气", "grammar_ma", new int[]{0xFFFDF9F2, 0xFFF7EBD8}),
                        new CardSpec("的/得", "结构助词", "grammar_de", new int[]{0xFFFDF9F2, 0xFFF7EBD8})
                });

        return scrollView;
    }

    private View createHero() {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setClipToPadding(false);

        ImageView bg = new ImageView(requireContext());
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageResource(R.drawable.learning_home_banner_default);
        hero.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(requireContext());
        overlay.setBackground(verticalGradient(0x22000000, 0xD9000000, 0, Color.TRANSPARENT, 0));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM | Gravity.START);
        copy.setPadding(dp(22), dp(22), dp(22), dp(22));
        hero.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = new TextView(requireContext());
        badge.setText("AI 语伴课 · 零基础");
        badge.setTextSize(12);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(rounded(0x33FFFFFF, dp(12), 0x26FFFFFF, 1));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.setMargins(0, 0, 0, dp(10));
        copy.addView(badge, badgeLp);

        TextView title = new TextView(requireContext());
        title.setText("90天搞定汉语口语");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setPadding(0, 0, 0, dp(10));
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bottomRow = new LinearLayout(requireContext());
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(bottomRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout textBox = new LinearLayout(requireContext());
        textBox.setOrientation(LinearLayout.VERTICAL);
        bottomRow.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView sub = new TextView(requireContext());
        sub.setText("每天10分钟，与AI语伴沉浸对练");
        sub.setTextSize(14);
        sub.setTextColor(0xE6FFFFFF);
        sub.setIncludeFontPadding(false);
        textBox.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        TextView mini = new TextView(requireContext());
        mini.setText("拼音 · 单词 · 口语 · 句型");
        mini.setTextSize(12);
        mini.setTextColor(0xBFFFFFFF);
        mini.setPadding(0, dp(5), 0, 0);
        textBox.addView(mini, new LinearLayout.LayoutParams(-1, -2));

        TextView start = new TextView(requireContext());
        start.setText("报名");
        start.setTextSize(14);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setTextColor(COLOR_TEXT);
        start.setGravity(Gravity.CENTER);
        start.setBackground(rounded(Color.WHITE, dp(20), 0x33FFFFFF, 1));
        bindPressClick(start, () -> Toast.makeText(requireContext(), "进入报名页面", Toast.LENGTH_SHORT).show());

        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(dp(78), dp(40));
        startLp.setMargins(dp(12), 0, 0, 0);
        bottomRow.addView(start, startLp);

        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.END | Gravity.TOP);
        menuLp.setMargins(0, dp(22), dp(14), 0);
        hero.addView(createMenuHandle(), menuLp);

        return hero;
    }

    private View createMenuHandle() {
        FrameLayout box = new FrameLayout(requireContext());
        box.setOnClickListener(v -> openDrawer());

        LinearLayout bars = new LinearLayout(requireContext());
        bars.setOrientation(LinearLayout.VERTICAL);
        bars.setGravity(Gravity.CENTER);
        box.addView(bars, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        bars.addView(menuLine(), new LinearLayout.LayoutParams(dp(22), dp(3)));
        addSpace(bars, 5);
        bars.addView(menuLine(), new LinearLayout.LayoutParams(dp(18), dp(3)));
        addSpace(bars, 5);
        bars.addView(menuLine(), new LinearLayout.LayoutParams(dp(13), dp(3)));

        return box;
    }

    private View menuLine() {
        View view = new View(requireContext());
        view.setBackground(rounded(0xF2FFFFFF, dp(2), 0x22000000, 1));
        return view;
    }

    private View createToolsRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        row.addView(toolCard("译", new int[]{0xFF4FACFE, 0xFF00F2FE}, "AI翻译",
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 12);

        row.addView(toolCard("阅", new int[]{0xFFA18CD1, 0xFFFBC2EB}, "电子书",
                this::showBookPage),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 12);

        row.addView(toolCard("伴", new int[]{0xFF43E97B, 0xFF38F9D7}, "口语伴",
                this::showPromptScenes),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 12);

        row.addView(toolCard("练", new int[]{0xFFFFB75E, 0xFFED8F03}, "练习题",
                () -> Toast.makeText(requireContext(), "练习题后续接入", Toast.LENGTH_SHORT).show()),
                new LinearLayout.LayoutParams(0, -1, 1f));

        return row;
    }

    private View toolCard(String charIcon, int[] gradientColors, String title, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(rounded(Color.WHITE, dp(16), 0xFFE8E8ED, 1));
        card.setElevation(dp(2));
        bindPressClick(card, click);

        TextView iconView = new TextView(requireContext());
        iconView.setText(charIcon);
        iconView.setTextSize(18);
        iconView.setTextColor(Color.WHITE);
        iconView.setTypeface(Typeface.DEFAULT_BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(diagonalGradient(gradientColors[0], gradientColors[1], dp(14), Color.TRANSPARENT, 0));

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconLp.setMargins(0, dp(12), 0, dp(8));
        card.addView(iconView, iconLp);

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(COLOR_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(12));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        return card;
    }

    private void addSection(LinearLayout parent, String title, String more, CardSpec[] cards) {
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
            moreView.setText(more.contains("›") ? more : more + " ›");
            moreView.setTextSize(13);
            moreView.setTextColor(COLOR_SUB);
            moreView.setTypeface(Typeface.DEFAULT_BOLD);
            moreView.setGravity(Gravity.CENTER);
            moreView.setPadding(dp(10), dp(4), dp(10), dp(4));
            moreView.setBackground(rounded(Color.WHITE, dp(13), 0xFFE8E8ED, 1));
            bindPressClick(moreView, () -> openMorePage(title));
            header.addView(moreView, new LinearLayout.LayoutParams(-2, dp(28)));
        }

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        parent.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        int index = 0;
        int columns = 2;

        while (index < cards.length) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(84));
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

                if (i < columns - 1) {
                    addHorizontalGap(row, 12);
                }
            }
        }

        addSpace(parent, 10);
    }

    private View smallCard(CardSpec spec) {
        FrameLayout card = new FrameLayout(requireContext());
        card.setBackground(diagonalGradient(spec.bgColors[0], spec.bgColors[1], dp(16), 0xFFE8EAEE, 1));
        card.setElevation(dp(1.5f));
        bindPressClick(card, () -> onSmallCardClick(spec));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        content.setPadding(dp(16), 0, dp(34), 0);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(requireContext());
        title.setText(spec.title);
        title.setTextSize(16);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(requireContext());
        desc.setText(spec.desc);
        desc.setTextSize(12);
        desc.setTextColor(COLOR_SUB);
        desc.setSingleLine(true);
        desc.setPadding(0, dp(6), 0, 0);
        content.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        TextView arrow = new TextView(requireContext());
        arrow.setText("›");
        arrow.setTextSize(22);
        arrow.setTextColor(0x66000000);
        arrow.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(dp(24), dp(32), Gravity.END | Gravity.CENTER_VERTICAL);
        arrowLp.setMargins(0, 0, dp(8), 0);
        card.addView(arrow, arrowLp);

        return card;
    }

    private View createSideDrawer() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(40), dp(20), dp(20));
        panel.setBackgroundColor(Color.WHITE);
        panel.setClickable(true);

        TextView title = new TextView(requireContext());
        title.setText("更多服务");
        title.setTextSize(24);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("探索扩展工具与脚本");
        sub.setTextSize(14);
        sub.setTextColor(COLOR_SUB);

        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(24));
        panel.addView(sub, subLp);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        list.addView(drawerCard("DeepSeek 翻译", "中缅互译、语法解释", () ->
                AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));

        list.addView(drawerCard("886.best", "国内学习 AI 入口", () ->
                AiScriptWebActivity.open(requireContext(), "886.best", "https://886.best")));

        list.addView(drawerCard("千问国内版", "qianwen.com", () ->
                AiScriptWebActivity.open(requireContext(), "千问国内版", "https://www.qianwen.com/")));

        list.addView(drawerCard("Qwen 国际版", "chat.qwen.ai", () ->
                AiScriptWebActivity.open(requireContext(), "Qwen 国际版", "https://chat.qwen.ai/")));

        list.addView(drawerCard("语音设置", "WKSpeech 引擎配置", this::openSpeechSettings));

        list.addView(drawerCard("口语 Prompt", "生活场景对话指令", this::showPromptScenes));

        list.addView(drawerCard("脚本中心", "自定义扩展功能", () ->
                startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        return panel;
    }

    private View drawerCard(String title, String desc, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(10), dp(14));
        card.setBackground(rounded(COLOR_BG, dp(14), Color.TRANSPARENT, 0));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);

        bindPressClick(card, () -> {
            closeDrawer();
            if (click != null) click.run();
        });

        LinearLayout textBox = new LinearLayout(requireContext());
        textBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(COLOR_TEXT);
        t.setIncludeFontPadding(false);
        textBox.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView d = new TextView(requireContext());
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(COLOR_SUB);
        d.setPadding(0, dp(5), 0, 0);
        textBox.addView(d, new LinearLayout.LayoutParams(-1, -2));

        TextView arrow = new TextView(requireContext());
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(0xFFB0B0B5);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(40)));

        return card;
    }

    private void onSmallCardClick(CardSpec spec) {
        if (spec == null || spec.id == null) return;

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
            final String[] items = new String[]{
                    "HSK 1", "HSK 2", "HSK 3", "HSK 4", "HSK 5", "HSK 6",
                    "生活高频", "工作求职", "恋爱聊天"
            };

            final String[] ids = new String[]{
                    "hsk1", "hsk2", "hsk3", "hsk4", "hsk5", "hsk6",
                    "daily", "job", "love"
            };

            new AlertDialog.Builder(requireContext())
                    .setTitle("更多单词")
                    .setItems(items, (dialog, which) -> {
                        Intent intent = new Intent(requireContext(), WordFullscreenActivity.class);
                        intent.putExtra(WordFullscreenActivity.EXTRA_LEVEL, ids[which]);
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
        final String[] names = new String[]{
                "日常打招呼", "点餐买东西", "求职面试", "医院看病",
                "机场过关", "租房沟通", "口语陪练", "中缅互译"
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
                .setTitle("选择一个场景 Prompt")
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
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
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

        if (click != null) {
            click.run();
        }
    }

    private void bindPressClick(View view, Runnable click) {
        view.setClickable(true);
        view.setOnClickListener(v -> {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastCardClickTime < 450) return;
            lastCardClickTime = now;

            v.animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .alpha(0.92f)
                    .setDuration(70)
                    .withEndAction(() -> {
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(110)
                                .start();

                        if (click != null) {
                            click.run();
                        }
                    })
                    .start();
        });
    }

    private void widenDrawerGestureArea(final int edgeSizePx) {
        if (drawerLayout == null) return;

        drawerLayout.post(() -> {
            try {
                Field leftDraggerField = DrawerLayout.class.getDeclaredField("mLeftDragger");
                leftDraggerField.setAccessible(true);

                ViewDragHelper leftDragger = (ViewDragHelper) leftDraggerField.get(drawerLayout);
                if (leftDragger == null) return;

                Field edgeSizeField = ViewDragHelper.class.getDeclaredField("mEdgeSize");
                edgeSizeField.setAccessible(true);

                int oldSize = edgeSizeField.getInt(leftDragger);
                int newSize = Math.max(oldSize, edgeSizePx);
                edgeSizeField.setInt(leftDragger, newSize);
            } catch (Throwable ignored) {
                // AndroidX 内部字段变动时不崩溃，保持默认手势区域
            }
        });
    }

    private GradientDrawable verticalGradient(int start, int end, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{start, end}
        );
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private GradientDrawable diagonalGradient(int start, int end, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end}
        );
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
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
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.75f);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class CardSpec {
        final String title;
        final String desc;
        final String id;
        final int[] bgColors;

        CardSpec(String title, String desc, String id, int[] bgColors) {
            this.title = title;
            this.desc = desc;
            this.id = id;
            this.bgColors = bgColors;
        }
    }
}
