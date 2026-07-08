package com.chat.learning;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
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
import android.view.animation.DecelerateInterpolator;
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
 * 学习首页：课程 Banner + 轻玻璃卡片 + 分组侧边栏。
 * 说明：
 * 1. 主页先保持静态数据，后续可再拆 Repository/JSON。
 * 2. Font Awesome 采用兼容模式：assets/fonts/fa-solid-900.ttf 存在则显示图标，否则自动回退汉字。
 * 3. 点击反馈使用 StateListAnimator，更接近原生按压动效。
 */
public class LearningFragment extends Fragment {
    private static final int COLOR_BG_TOP = 0xFFF8FAFD;
    private static final int COLOR_BG_BOTTOM = 0xFFEFF3F8;
    private static final int COLOR_TEXT = 0xFF1D1D1F;
    private static final int COLOR_SUB = 0xFF7A7F87;
    private static final int COLOR_CARD = 0xEFFFFFFF;
    private static final int COLOR_CARD_STROKE = 0xCCFFFFFF;
    private static final int COLOR_SOFT_LINE = 0xFFE7EAF0;

    private DrawerLayout drawerLayout;
    private long lastCardClickTime = 0L;
    private Typeface faTypeface;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        faTypeface = loadFontAwesomeTypeface();

        drawerLayout = new DrawerLayout(requireContext());
        drawerLayout.setBackground(verticalGradient(COLOR_BG_TOP, COLOR_BG_BOTTOM, 0, Color.TRANSPARENT, 0));
        drawerLayout.setScrimColor(0x82000000);
        drawerLayout.setDrawerElevation(dp(18));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);

        View main = createMainPage();
        drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));

        View drawer = createSideDrawer();
        DrawerLayout.LayoutParams drawerLp = new DrawerLayout.LayoutParams(getDrawerWidth(), -1);
        drawerLp.gravity = GravityCompat.START;
        drawerLayout.addView(drawer, drawerLp);

        // 默认 Drawer 边缘手势太窄，学习页需要更容易从左侧滑出。
        widenDrawerGestureArea(dp(76));

        return drawerLayout;
    }

    private View createMainPage() {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setBackground(verticalGradient(COLOR_BG_TOP, COLOR_BG_BOTTOM, 0, Color.TRANSPARENT, 0));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(42));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(createHero(), new LinearLayout.LayoutParams(-1, dp(244)));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), 0);
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));

        content.addView(createToolsRow(), new LinearLayout.LayoutParams(-1, dp(104)));
        addSpace(content, 24);

        addSection(content, "拼音", null,
                new CardSpec[]{
                        new CardSpec("声母", "b p m f", "initials", 0xFF3B82F6, 0xFFEAF2FF),
                        new CardSpec("韵母", "a o e i u", "finals", 0xFF3B82F6, 0xFFEAF2FF),
                        new CardSpec("整体", "zhi chi shi", "whole", 0xFF3B82F6, 0xFFEAF2FF),
                        new CardSpec("声调", "一二三四声", "tone", 0xFF3B82F6, 0xFFEAF2FF)
                });

        addSection(content, "单词", "更多",
                new CardSpec[]{
                        new CardSpec("HSK 1", "150 词", "hsk1", 0xFF8B5CF6, 0xFFF2ECFF),
                        new CardSpec("HSK 2", "300 词", "hsk2", 0xFF8B5CF6, 0xFFF2ECFF),
                        new CardSpec("HSK 3", "600 词", "hsk3", 0xFF8B5CF6, 0xFFF2ECFF)
                });

        addSection(content, "口语", "更多",
                new CardSpec[]{
                        new CardSpec("打招呼", "日常开场", "speak_hello", 0xFFF97316, 0xFFFFF0E8),
                        new CardSpec("点餐", "餐厅购物", "speak_food", 0xFFF97316, 0xFFFFF0E8),
                        new CardSpec("求职", "面试工作", "speak_job", 0xFFF97316, 0xFFFFF0E8),
                        new CardSpec("购物", "买单砍价", "speak_shop", 0xFFF97316, 0xFFFFF0E8)
                });

        addSection(content, "句型", "更多",
                new CardSpec[]{
                        new CardSpec("我想…", "表达需求", "pattern_want", 0xFF22A06B, 0xFFEAF8F1),
                        new CardSpec("可以吗", "请求帮助", "pattern_can", 0xFF22A06B, 0xFFEAF8F1),
                        new CardSpec("怎么…", "询问方法", "pattern_how", 0xFF22A06B, 0xFFEAF8F1),
                        new CardSpec("为什么", "询问原因", "pattern_why", 0xFF22A06B, 0xFFEAF8F1)
                });

        addSection(content, "语法", "更多",
                new CardSpec[]{
                        new CardSpec("了", "完成/变化", "grammar_le", 0xFFC9892B, 0xFFFFF4E2),
                        new CardSpec("在", "正在进行", "grammar_zai", 0xFFC9892B, 0xFFFFF4E2),
                        new CardSpec("吗/呢", "疑问语气", "grammar_ma", 0xFFC9892B, 0xFFFFF4E2),
                        new CardSpec("的/得", "结构助词", "grammar_de", 0xFFC9892B, 0xFFFFF4E2)
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
        overlay.setBackground(verticalGradient(0x18000000, 0xE0000000, 0, Color.TRANSPARENT, 0));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        // 轻微玻璃底板，解决文字压在图片上太硬的问题。
        View glassPanel = new View(requireContext());
        glassPanel.setBackground(verticalGradient(0x18FFFFFF, 0x08FFFFFF, dp(24), 0x2AFFFFFF, 1));
        FrameLayout.LayoutParams glassLp = new FrameLayout.LayoutParams(-1, dp(92), Gravity.BOTTOM);
        glassLp.setMargins(dp(14), 0, dp(14), dp(14));
        hero.addView(glassPanel, glassLp);

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
        badge.setBackground(rounded(0x30FFFFFF, dp(12), 0x30FFFFFF, 1));
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
        title.setShadowLayer(dp(6), 0, dp(2), 0x50000000);
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
        sub.setTextColor(0xE8FFFFFF);
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
        start.setBackground(rounded(Color.WHITE, dp(20), 0x50FFFFFF, 1));
        attachPressAnimator(start, dp(1), dp(4));
        bindClick(start, () -> Toast.makeText(requireContext(), "进入报名页面", Toast.LENGTH_SHORT).show());

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
        box.setBackground(rounded(0x24FFFFFF, dp(16), 0x28FFFFFF, 1));
        attachPressAnimator(box, dp(0), dp(4));
        bindClick(box, this::openDrawer);

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

        row.addView(toolCard("\uf1ab", "译", "AI翻译", 0xFF2F80ED, 0xFFEAF2FF,
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 12);

        row.addView(toolCard("\uf02d", "阅", "电子书", 0xFF8B5CF6, 0xFFF2ECFF,
                this::showBookPage),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 12);

        row.addView(toolCard("\uf130", "伴", "口语伴", 0xFF22A06B, 0xFFEAF8F1,
                this::showPromptScenes),
                new LinearLayout.LayoutParams(0, -1, 1f));

        addHorizontalGap(row, 12);

        row.addView(toolCard("\uf044", "练", "练习题", 0xFFF97316, 0xFFFFF0E8,
                () -> Toast.makeText(requireContext(), "练习题后续接入", Toast.LENGTH_SHORT).show()),
                new LinearLayout.LayoutParams(0, -1, 1f));

        return row;
    }

    private View toolCard(String faIcon, String fallbackText, String title, int accentColor, int softBgColor, Runnable click) {
        FrameLayout card = new FrameLayout(requireContext());
        card.setBackground(glassBackground(dp(18)));
        card.setElevation(dp(2));
        attachPressAnimator(card, dp(1), dp(5));
        bindClick(card, click);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(6), dp(10), dp(6), dp(10));
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView iconView = new TextView(requireContext());
        iconView.setText(getIconText(faIcon, fallbackText));
        iconView.setTextSize(faTypeface != null ? 18 : 17);
        iconView.setTextColor(accentColor);
        iconView.setTypeface(faTypeface != null ? faTypeface : Typeface.DEFAULT_BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(rounded(softBgColor, dp(15), 0xAAFFFFFF, 1));

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconLp.setMargins(0, 0, 0, dp(8));
        content.addView(iconView, iconLp);

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(COLOR_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        content.addView(t, new LinearLayout.LayoutParams(-1, -2));

        addGlassHighlight(card);
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
        titleView.setIncludeFontPadding(false);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (more != null) {
            TextView moreView = new TextView(requireContext());
            moreView.setText(more.contains("›") ? more : more + " ›");
            moreView.setTextSize(13);
            moreView.setTextColor(COLOR_SUB);
            moreView.setTypeface(Typeface.DEFAULT_BOLD);
            moreView.setGravity(Gravity.CENTER);
            moreView.setPadding(dp(10), dp(4), dp(10), dp(4));
            moreView.setBackground(glassBackground(dp(14)));
            attachPressAnimator(moreView, dp(0), dp(3));
            bindClick(moreView, () -> openMorePage(title));
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

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(86));
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
        card.setBackground(glassBackground(dp(18)));
        card.setElevation(dp(1.5f));
        attachPressAnimator(card, dp(1), dp(4));
        bindClick(card, () -> onSmallCardClick(spec));

        View tint = new View(requireContext());
        tint.setBackground(diagonalGradient(spec.softBgColor, 0x00FFFFFF, dp(18), Color.TRANSPARENT, 0));
        card.addView(tint, new FrameLayout.LayoutParams(-1, -1));

        View accent = new View(requireContext());
        accent.setBackground(rounded(spec.accentColor, dp(2), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams accentLp = new FrameLayout.LayoutParams(dp(4), dp(42), Gravity.START | Gravity.CENTER_VERTICAL);
        accentLp.setMargins(dp(12), 0, 0, 0);
        card.addView(accent, accentLp);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        content.setPadding(dp(26), 0, dp(34), 0);
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

        addGlassHighlight(card);
        return card;
    }

    private View createSideDrawer() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(40), dp(20), dp(20));
        panel.setBackground(verticalGradient(0xFFFFFFFF, 0xFFF6F8FB, 0, Color.TRANSPARENT, 0));
        panel.setClickable(true);

        TextView title = new TextView(requireContext());
        title.setText("更多服务");
        title.setTextSize(24);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("探索 AI、语音和扩展工具");
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

        addDrawerGroupTitle(list, "AI 助手");
        list.addView(drawerCard("DeepSeek 翻译", "中缅互译、语法解释", 0xFF2F80ED, () ->
                AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));
        list.addView(drawerCard("886.best", "国内学习 AI 入口", 0xFF22A06B, () ->
                AiScriptWebActivity.open(requireContext(), "886.best", "https://886.best")));
        list.addView(drawerCard("千问国内版", "qianwen.com", 0xFFF97316, () ->
                AiScriptWebActivity.open(requireContext(), "千问国内版", "https://www.qianwen.com/")));
        list.addView(drawerCard("Qwen 国际版", "chat.qwen.ai", 0xFF8B5CF6, () ->
                AiScriptWebActivity.open(requireContext(), "Qwen 国际版", "https://chat.qwen.ai/")));

        addDrawerGroupTitle(list, "学习工具");
        list.addView(drawerCard("语音设置", "WKSpeech 引擎配置", 0xFF0891B2, this::openSpeechSettings));
        list.addView(drawerCard("口语 Prompt", "生活场景对话指令", 0xFF22A06B, this::showPromptScenes));

        addDrawerGroupTitle(list, "扩展功能");
        list.addView(drawerCard("脚本中心", "自定义扩展功能", 0xFF64748B, () ->
                startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        return panel;
    }

    private void addDrawerGroupTitle(LinearLayout parent, String text) {
        TextView title = new TextView(requireContext());
        title.setText(text);
        title.setTextSize(12);
        title.setTextColor(0xFF9AA0A8);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(false);
        title.setPadding(dp(2), dp(10), 0, dp(8));
        parent.addView(title, new LinearLayout.LayoutParams(-1, -2));
    }

    private View drawerCard(String title, String desc, int accentColor, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(10), dp(13));
        card.setBackground(glassBackground(dp(15)));
        card.setElevation(dp(1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);

        attachPressAnimator(card, dp(0), dp(3));
        bindClick(card, () -> {
            closeDrawer();
            if (click != null) click.run();
        });

        View dot = new View(requireContext());
        dot.setBackground(rounded(accentColor, dp(4), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.setMargins(0, 0, dp(12), 0);
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
        arrow.setTextColor(0xFFB0B5BD);
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

    private void bindClick(View view, Runnable click) {
        view.setClickable(true);
        view.setOnClickListener(v -> {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastCardClickTime < 420) return;
            lastCardClickTime = now;
            if (click != null) click.run();
        });
    }

    private void attachPressAnimator(View view, float normalElevation, float pressedElevation) {
        StateListAnimator animator = new StateListAnimator();

        AnimatorSet pressed = new AnimatorSet();
        pressed.playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 0.985f),
                ObjectAnimator.ofFloat(view, "scaleY", 0.985f),
                ObjectAnimator.ofFloat(view, "translationZ", pressedElevation)
        );
        pressed.setDuration(90);
        pressed.setInterpolator(new DecelerateInterpolator());

        AnimatorSet normal = new AnimatorSet();
        normal.playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f),
                ObjectAnimator.ofFloat(view, "translationZ", normalElevation)
        );
        normal.setDuration(120);
        normal.setInterpolator(new DecelerateInterpolator());

        animator.addState(new int[]{android.R.attr.state_pressed}, pressed);
        animator.addState(new int[]{}, normal);
        view.setStateListAnimator(animator);
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
                // AndroidX 内部字段变动时不崩溃，保持默认 Drawer 手势区域。
            }
        });
    }

    private Typeface loadFontAwesomeTypeface() {
        try {
            return Typeface.createFromAsset(requireContext().getAssets(), "fonts/fa-solid-900.ttf");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String getIconText(String faIcon, String fallbackText) {
        return faTypeface != null ? faIcon : fallbackText;
    }

    private GradientDrawable glassBackground(float radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF4FFFFFF, COLOR_CARD}
        );
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, COLOR_CARD_STROKE);
        return drawable;
    }

    private void addGlassHighlight(FrameLayout parent) {
        View highlight = new View(requireContext());
        highlight.setBackground(verticalGradient(0x2BFFFFFF, 0x00FFFFFF, dp(18), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(30), Gravity.TOP);
        lp.setMargins(dp(1), dp(1), dp(1), 0);
        parent.addView(highlight, lp);
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

    private GradientDrawable diagonalGradient(int start, int end, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
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
        final int accentColor;
        final int softBgColor;

        CardSpec(String title, String desc, String id, int accentColor, int softBgColor) {
            this.title = title;
            this.desc = desc;
            this.id = id;
            this.accentColor = accentColor;
            this.softBgColor = softBgColor;
        }
    }
}
