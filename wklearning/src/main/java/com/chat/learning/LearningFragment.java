package com.chat.learning;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
 * 学习首页：高端轻玻璃拟态课程风格。
 *
 * 设计原则：
 * 1. 电影级 3D UI 图只做 Hero 背景，不把整个页面做成重 3D。
 * 2. 主页卡片统一为轻玻璃白卡 + 彩色点缀，长期使用更耐看。
 * 3. 侧边栏只放工具，学习内容走主页和二级目录。
 * 4. Font Awesome 可选：有 assets/fonts/fa-solid-900.ttf 就显示图标，没有就自动退回汉字。
 */
public class LearningFragment extends Fragment {
    private static final int COLOR_BG = 0xFFF5F6F8;
    private static final int COLOR_TEXT = 0xFF1D1D1F;
    private static final int COLOR_SUB = 0xFF7E8188;
    private static final int COLOR_CARD_STROKE = 0xB3FFFFFF;

    private static final String FA_LANGUAGE = "\uf1ab";
    private static final String FA_BOOK = "\uf02d";
    private static final String FA_MIC = "\uf130";
    private static final String FA_PEN = "\uf303";

    private DrawerLayout drawerLayout;
    private Typeface faTypeface;
    private long lastCardClickTime = 0L;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        faTypeface = loadFontAwesome();

        drawerLayout = new DrawerLayout(requireContext());
        drawerLayout.setBackgroundColor(COLOR_BG);
        drawerLayout.setScrimColor(0x88000000);
        drawerLayout.setDrawerElevation(dp(18));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);

        View main = createMainPage();
        drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));

        View drawer = createSideDrawer();
        DrawerLayout.LayoutParams drawerLp = new DrawerLayout.LayoutParams(getDrawerWidth(), -1);
        drawerLp.gravity = GravityCompat.START;
        drawerLayout.addView(drawer, drawerLp);

        widenDrawerGestureArea(dp(76));

        return drawerLayout;
    }

    private View createMainPage() {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(42));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(createHero(), new LinearLayout.LayoutParams(-1, dp(248)));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), 0);
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));

        content.addView(createToolsRow(), new LinearLayout.LayoutParams(-1, dp(98)));
        addSpace(content, 24);

        addSection(content, "拼音", null,
                new CardSpec[]{
                        new CardSpec("声母", "b p m f", "initials"),
                        new CardSpec("韵母", "a o e i u", "finals"),
                        new CardSpec("整体", "zhi chi shi", "whole"),
                        new CardSpec("声调", "一二三四声", "tone")
                });

        addSection(content, "单词", "更多",
                new CardSpec[]{
                        new CardSpec("HSK 1", "150 词", "hsk1"),
                        new CardSpec("HSK 2", "300 词", "hsk2"),
                        new CardSpec("HSK 3", "600 词", "hsk3")
                });

        addSection(content, "口语", "更多",
                new CardSpec[]{
                        new CardSpec("打招呼", "日常开场", "speak_hello"),
                        new CardSpec("点餐", "餐厅购物", "speak_food"),
                        new CardSpec("求职", "面试工作", "speak_job"),
                        new CardSpec("购物", "买单砍价", "speak_shop")
                });

        addSection(content, "句型", "更多",
                new CardSpec[]{
                        new CardSpec("我想…", "表达需求", "pattern_want"),
                        new CardSpec("可以吗", "请求帮助", "pattern_can"),
                        new CardSpec("怎么…", "询问方法", "pattern_how"),
                        new CardSpec("为什么", "询问原因", "pattern_why")
                });

        addSection(content, "语法", "更多",
                new CardSpec[]{
                        new CardSpec("了", "完成/变化", "grammar_le"),
                        new CardSpec("在", "正在进行", "grammar_zai"),
                        new CardSpec("吗/呢", "疑问语气", "grammar_ma"),
                        new CardSpec("的/得", "结构助词", "grammar_de")
                });

        return scrollView;
    }

    private View createHero() {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setClipToPadding(false);

        ImageView bg = new ImageView(requireContext());
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageResource(getDrawableId("learning_home_banner_cinematic", R.drawable.learning_home_banner_default));
        hero.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(requireContext());
        overlay.setBackground(verticalGradient(0x16000000, 0xDD000000, 0, Color.TRANSPARENT, 0));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        addHeroGlow(hero, Gravity.START | Gravity.TOP, 0x33A7D8FF, -36, 4, 170);
        addHeroGlow(hero, Gravity.END | Gravity.BOTTOM, 0x2EFFE7C2, 28, 12, 150);

        FrameLayout glassPanel = new FrameLayout(requireContext());
        glassPanel.setBackground(heroGlassBg(dp(24)));
        attachNativePressAnimator(glassPanel, 0, 2);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, dp(132), Gravity.BOTTOM);
        panelLp.setMargins(dp(16), 0, dp(16), dp(16));
        hero.addView(glassPanel, panelLp);

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM | Gravity.START);
        copy.setPadding(dp(18), dp(15), dp(18), dp(15));
        glassPanel.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = new TextView(requireContext());
        badge.setText("AI 语伴课 · 零基础");
        badge.setTextSize(12);
        badge.setTextColor(0xF2FFFFFF);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(rounded(0x2EFFFFFF, dp(12), 0x30FFFFFF, 1));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.setMargins(0, 0, 0, dp(9));
        copy.addView(badge, badgeLp);

        TextView title = new TextView(requireContext());
        title.setText("90天搞定汉语口语");
        title.setTextSize(27);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setPadding(0, 0, 0, dp(9));
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
        sub.setTextSize(13);
        sub.setTextColor(0xE6FFFFFF);
        sub.setIncludeFontPadding(false);
        textBox.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        TextView mini = new TextView(requireContext());
        mini.setText("拼音 · 单词 · 口语 · 句型");
        mini.setTextSize(11);
        mini.setTextColor(0xBFFFFFFF);
        mini.setPadding(0, dp(4), 0, 0);
        textBox.addView(mini, new LinearLayout.LayoutParams(-1, -2));

        TextView start = new TextView(requireContext());
        start.setText("报名");
        start.setTextSize(14);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setTextColor(COLOR_TEXT);
        start.setGravity(Gravity.CENTER);
        start.setBackground(rounded(0xF5FFFFFF, dp(20), 0x80FFFFFF, 1));
        bindClick(start, () -> Toast.makeText(requireContext(), "进入报名页面", Toast.LENGTH_SHORT).show());
        attachNativePressAnimator(start, 2, 5);

        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(dp(78), dp(40));
        startLp.setMargins(dp(12), 0, 0, 0);
        bottomRow.addView(start, startLp);

        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.END | Gravity.TOP);
        menuLp.setMargins(0, dp(20), dp(14), 0);
        hero.addView(createMenuHandle(), menuLp);

        return hero;
    }

    private void addHeroGlow(FrameLayout hero, int gravity, int color, int marginX, int marginY, int sizeDp) {
        View glow = new View(requireContext());
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        glow.setBackground(drawable);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), gravity);
        lp.setMargins(dp(Math.max(marginX, 0)), dp(Math.max(marginY, 0)),
                dp(Math.max(-marginX, 0)), dp(Math.max(-marginY, 0)));
        hero.addView(glow, lp);
    }

    private View createMenuHandle() {
        FrameLayout box = new FrameLayout(requireContext());
        box.setBackground(rounded(0x30FFFFFF, dp(16), 0x36FFFFFF, 1));
        bindClick(box, this::openDrawer);
        attachNativePressAnimator(box, 1, 4);

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
        view.setBackground(rounded(0xF2FFFFFF, dp(2), 0x26000000, 1));
        return view;
    }

    private View createToolsRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        row.addView(toolCard(FA_LANGUAGE, "译", 0xFF3B82F6, "AI翻译",
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 10);

        row.addView(toolCard(FA_BOOK, "阅", 0xFF8B5CF6, "电子书",
                this::showBookPage),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 10);

        row.addView(toolCard(FA_MIC, "伴", 0xFF10B981, "口语伴",
                this::showPromptScenes),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 10);

        row.addView(toolCard(FA_PEN, "练", 0xFFF59E0B, "练习题",
                () -> Toast.makeText(requireContext(), "练习题后续接入", Toast.LENGTH_SHORT).show()),
                new LinearLayout.LayoutParams(0, -1, 1f));

        return row;
    }

    private View toolCard(String faCode, String fallbackText, int accentColor, String title, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(10), dp(6), dp(10));
        card.setBackground(glassBg(accentColor, dp(18), 0.06f));
        bindClick(card, click);
        attachNativePressAnimator(card, 2, 5);

        TextView iconView = new TextView(requireContext());
        iconView.setText(faTypeface != null ? faCode : fallbackText);
        iconView.setTextSize(faTypeface != null ? 17 : 18);
        iconView.setTextColor(accentColor);
        iconView.setTypeface(faTypeface != null ? faTypeface : Typeface.DEFAULT_BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(glassBg(accentColor, dp(16), 0.14f));

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(43), dp(43));
        iconLp.setMargins(0, 0, 0, dp(8));
        card.addView(iconView, iconLp);

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(COLOR_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        return card;
    }

    private void addSection(LinearLayout parent, String title, String more, CardSpec[] cards) {
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.BOTTOM);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(0, dp(10), 0, dp(13));
        parent.addView(header, headerLp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setIncludeFontPadding(false);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (more != null) {
            TextView moreView = new TextView(requireContext());
            moreView.setText(more.contains("›") ? more : more + " ›");
            moreView.setTextSize(13);
            moreView.setTextColor(COLOR_SUB);
            moreView.setTypeface(Typeface.DEFAULT_BOLD);
            moreView.setGravity(Gravity.CENTER);
            moreView.setPadding(dp(10), 0, dp(10), 0);
            moreView.setBackground(glassBg(accentColorForTitle(title), dp(14), 0.05f));
            bindClick(moreView, () -> openMorePage(title));
            attachNativePressAnimator(moreView, 1, 3);
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
        int accent = accentColorFor(spec.id);

        FrameLayout card = new FrameLayout(requireContext());
        card.setBackground(glassBg(accent, dp(18), 0.055f));
        bindClick(card, () -> onSmallCardClick(spec));
        attachNativePressAnimator(card, 2, 5);

        View accentBar = new View(requireContext());
        accentBar.setBackground(rounded(accent, dp(3), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(dp(4), dp(42), Gravity.START | Gravity.CENTER_VERTICAL);
        barLp.setMargins(dp(12), 0, 0, 0);
        card.addView(accentBar, barLp);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        content.setPadding(dp(25), 0, dp(34), 0);
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
        arrow.setTextColor(0x52000000);
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
        panel.setBackground(verticalGradient(0xFFFFFFFF, 0xFFF7F8FB, 0, Color.TRANSPARENT, 0));
        panel.setClickable(true);

        TextView title = new TextView(requireContext());
        title.setText("更多服务");
        title.setTextSize(24);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("AI、语音与扩展工具");
        sub.setTextSize(14);
        sub.setTextColor(COLOR_SUB);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(22));
        panel.addView(sub, subLp);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        drawerGroupTitle(list, "AI 助手");
        list.addView(drawerCard("DeepSeek 翻译", "中缅互译、语法解释", 0xFF3B82F6,
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));
        list.addView(drawerCard("886.best", "国内学习 AI 入口", 0xFF06B6D4,
                () -> AiScriptWebActivity.open(requireContext(), "886.best", "https://886.best")));
        list.addView(drawerCard("千问国内版", "qianwen.com", 0xFF8B5CF6,
                () -> AiScriptWebActivity.open(requireContext(), "千问国内版", "https://www.qianwen.com/")));
        list.addView(drawerCard("Qwen 国际版", "chat.qwen.ai", 0xFF6366F1,
                () -> AiScriptWebActivity.open(requireContext(), "Qwen 国际版", "https://chat.qwen.ai/")));

        drawerGroupTitle(list, "学习工具");
        list.addView(drawerCard("语音设置", "WKSpeech 引擎配置", 0xFF10B981, this::openSpeechSettings));
        list.addView(drawerCard("口语 Prompt", "生活场景对话指令", 0xFFF59E0B, this::showPromptScenes));

        drawerGroupTitle(list, "扩展功能");
        list.addView(drawerCard("脚本中心", "自定义扩展功能", 0xFFE11D48,
                () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        return panel;
    }

    private void drawerGroupTitle(LinearLayout list, String title) {
        TextView view = new TextView(requireContext());
        view.setText(title);
        view.setTextSize(12);
        view.setTextColor(0xFF9CA3AF);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(28));
        lp.setMargins(dp(2), dp(4), 0, dp(6));
        list.addView(view, lp);
    }

    private View drawerCard(String title, String desc, int accentColor, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(10), dp(13));
        card.setBackground(glassBg(accentColor, dp(16), 0.045f));
        bindClick(card, () -> {
            closeDrawer();
            if (click != null) click.run();
        });
        attachNativePressAnimator(card, 1, 4);

        View dot = new View(requireContext());
        dot.setBackground(rounded(accentColor, dp(5), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotLp.setMargins(0, 0, dp(10), 0);
        card.addView(dot, dotLp);

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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
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

    private void bindClick(View view, Runnable click) {
        view.setClickable(true);
        view.setOnClickListener(v -> {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastCardClickTime < 420) return;
            lastCardClickTime = now;
            v.animate().cancel();
            if (click != null) click.run();
        });
    }

    private void attachNativePressAnimator(View view, float normalElevationDp, float pressedTranslationZDp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        view.setElevation(dp(normalElevationDp));

        StateListAnimator stateListAnimator = new StateListAnimator();
        ObjectAnimator pressed = ObjectAnimator.ofFloat(view, "translationZ", dp(pressedTranslationZDp));
        pressed.setDuration(90);

        ObjectAnimator normal = ObjectAnimator.ofFloat(view, "translationZ", 0f);
        normal.setDuration(120);

        stateListAnimator.addState(new int[]{android.R.attr.state_pressed}, pressed);
        stateListAnimator.addState(new int[]{}, normal);
        view.setStateListAnimator(stateListAnimator);
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

    private Typeface loadFontAwesome() {
        try {
            return Typeface.createFromAsset(requireContext().getAssets(), "fonts/fa-solid-900.ttf");
        } catch (Throwable e) {
            return null;
        }
    }

    private int getDrawableId(String name, int fallback) {
        int id = getResources().getIdentifier(name, "drawable", requireContext().getPackageName());
        return id != 0 ? id : fallback;
    }

    private int accentColorForTitle(String title) {
        if ("拼音".equals(title)) return 0xFF3B82F6;
        if ("单词".equals(title)) return 0xFF8B5CF6;
        if ("口语".equals(title)) return 0xFFF97316;
        if ("句型".equals(title)) return 0xFF10B981;
        if ("语法".equals(title)) return 0xFFB45309;
        return 0xFF64748B;
    }

    private int accentColorFor(String id) {
        if (id == null) return 0xFF64748B;
        if (id.startsWith("hsk")) return 0xFF8B5CF6;
        if (id.startsWith("speak")) return 0xFFF97316;
        if (id.startsWith("pattern")) return 0xFF10B981;
        if (id.startsWith("grammar")) return 0xFFB45309;
        if ("initials".equals(id) || "finals".equals(id) || "whole".equals(id) || "tone".equals(id)) {
            return 0xFF3B82F6;
        }
        return 0xFF64748B;
    }

    private GradientDrawable glassBg(int tintColor, float radius, float tintRatio) {
        int start = blendColor(0xF2FFFFFF, tintColor, tintRatio);
        int end = blendColor(0xD9FFFFFF, tintColor, tintRatio + 0.03f);

        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end}
        );
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), COLOR_CARD_STROKE);
        return drawable;
    }

    private GradientDrawable heroGlassBg(float radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x33FFFFFF, 0x1AFFFFFF}
        );
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), 0x40FFFFFF);
        return drawable;
    }

    private GradientDrawable verticalGradient(int start, int end, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{start, end}
        );
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

    private int blendColor(int base, int overlay, float ratio) {
        float safeRatio = Math.max(0f, Math.min(1f, ratio));
        int a = Color.alpha(base);
        int r = (int) (Color.red(base) * (1 - safeRatio) + Color.red(overlay) * safeRatio);
        int g = (int) (Color.green(base) * (1 - safeRatio) + Color.green(overlay) * safeRatio);
        int b = (int) (Color.blue(base) * (1 - safeRatio) + Color.blue(overlay) * safeRatio);
        return Color.argb(a, r, g, b);
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
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

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
