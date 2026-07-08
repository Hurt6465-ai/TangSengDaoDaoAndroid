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
import android.util.DisplayMetrics;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;

import java.lang.reflect.Field;

/**
 * 学习首页：高端专业重构版
 *
 * 优化要点：
 * 1. 设计规范化：统一的颜色 / 圆角 / 间距 / 阴影 token，视觉更协调。
 * 2. 更高端的视觉：渐变图标胶囊、卡片彩色装饰条、Hero 双层渐变 + 标题投影。
 * 3. 性能：缓存 density，减少 getResources() 重复调用；点击防抖按 View 独立管理。
 * 4. 稳定性：小卡片使用 minHeight + WRAP_CONTENT，避免低端机 MATCH_PARENT 测量异常。
 */
public class LearningFragment extends Fragment {

    // ===== 颜色 Token =====
    private static final int COLOR_BG = 0xFFF4F5F9;
    private static final int COLOR_CARD_BG = 0xFFFFFFFF;
    private static final int COLOR_CARD_BG_2 = 0xFFF8F9FE;
    private static final int COLOR_STROKE = 0xFFEBECF2;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_SUB = 0xFF7A8194;
    private static final int COLOR_HINT = 0xFFA6ADBB;
    private static final int COLOR_BRAND = 0xFF5B54F0;
    private static final int SCRIM = 0x66000000;

    // ===== Font Awesome 图标（含中文 fallback）=====
    private static final String FA_LANGUAGE = "\uf1ab";
    private static final String FA_BOOK = "\uf02d";
    private static final String FA_MIC = "\uf130";
    private static final String FA_PEN = "\uf303";

    private DrawerLayout drawerLayout;
    private Typeface faTypeface;
    private float density = 1f;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        density = getResources().getDisplayMetrics().density;
        faTypeface = loadFontAwesome();

        drawerLayout = new DrawerLayout(requireContext());
        drawerLayout.setBackgroundColor(COLOR_BG);
        drawerLayout.setScrimColor(SCRIM);
        drawerLayout.setDrawerElevation(dp(20));
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

    // ===================== 主页面 =====================

    private View createMainPage() {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(42));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(createHero(), new LinearLayout.LayoutParams(-1, dp(252)));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(18), dp(16), 0);
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));

        content.addView(createToolsRow(), new LinearLayout.LayoutParams(-1, -2));
        addSpace(content, 22);

        addSection(content, "拼音", "更多", new CardSpec[]{
                new CardSpec("声母", "b p m f", "initials", 0xFF3B82F6),
                new CardSpec("韵母", "a o e i u", "finals", 0xFF06B6D4),
                new CardSpec("整体", "zhi chi shi", "whole", 0xFF10B981),
                new CardSpec("声调", "一二三四声", "tone", 0xFFF59E0B)
        });

        addSection(content, "单词", "更多", new CardSpec[]{
                new CardSpec("HSK 1", "150 词", "hsk1", 0xFF6366F1),
                new CardSpec("HSK 2", "300 词", "hsk2", 0xFF8B5CF6),
                new CardSpec("HSK 3", "600 词", "hsk3", 0xFFEC4899),
                new CardSpec("HSK 4", "1200 词", "hsk4", 0xFFF43F5E)
        });

        addSection(content, "口语", "更多", new CardSpec[]{
                new CardSpec("打招呼", "日常开场", "speak_hello", 0xFF0EA5E9),
                new CardSpec("点餐", "餐厅购物", "speak_food", 0xFFF97316),
                new CardSpec("求职", "面试工作", "speak_job", 0xFF14B8A6),
                new CardSpec("购物", "买单砍价", "speak_shop", 0xFFA855F7)
        });

        addSection(content, "句型", "更多", new CardSpec[]{
                new CardSpec("我想…", "表达需求", "pattern_want", 0xFF3B82F6),
                new CardSpec("可以吗", "请求帮助", "pattern_can", 0xFF22C55E),
                new CardSpec("怎么…", "询问方法", "pattern_how", 0xFFEAB308),
                new CardSpec("为什么", "询问原因", "pattern_why", 0xFFEF4444)
        });

        addSection(content, "语法", "更多", new CardSpec[]{
                new CardSpec("了", "完成/变化", "grammar_le", 0xFF6366F1),
                new CardSpec("在", "正在进行", "grammar_zai", 0xFF06B6D4),
                new CardSpec("吗/呢", "疑问语气", "grammar_ma", 0xFFF59E0B),
                new CardSpec("的/得", "结构助词", "grammar_de", 0xFF8B5CF6)
        });

        return scrollView;
    }

    // ===================== Hero =====================

    private View createHero() {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setClipToPadding(false);

        ImageView bg = new ImageView(requireContext());
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageResource(getDrawableId("learning_home_banner_cinematic",
                R.drawable.learning_home_banner_default));
        hero.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        // 顶部轻微暗化，保证右上角菜单可见
        View topShade = new View(requireContext());
        topShade.setBackground(vGradient(0x59000000, 0x00000000));
        hero.addView(topShade, new FrameLayout.LayoutParams(-1, dp(80)));

        // 底部深色渐变，突出文字
        View overlay = new View(requireContext());
        overlay.setBackground(vGradient(0x00000000, 0xDB000000));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM | Gravity.START);
        copy.setPadding(dp(20), dp(20), dp(20), dp(24));
        hero.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        TextView tag = new TextView(requireContext());
        tag.setText("AI 语伴课 · 零基础");
        tag.setTextSize(11);
        tag.setTextColor(Color.WHITE);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(dp(10), dp(4), dp(10), dp(4));
        tag.setBackground(rounded(0x33FFFFFF, dp(20), 0x4DFFFFFF, dp(1)));
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(-2, -2);
        tagLp.setMargins(0, 0, 0, dp(10));
        copy.addView(tag, tagLp);

        TextView title = new TextView(requireContext());
        title.setText("90天搞定汉语口语");
        title.setTextSize(29);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setShadowLayer(dp(8), 0, dp(2), 0x66000000);
        title.setPadding(0, 0, 0, dp(8));
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bottomRow = new LinearLayout(requireContext());
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(bottomRow, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("每天10分钟，与AI语伴沉浸对练");
        sub.setTextSize(13.5f);
        sub.setTextColor(0xE6FFFFFF);
        sub.setIncludeFontPadding(false);
        bottomRow.addView(sub, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView start = new TextView(requireContext());
        start.setText("报名");
        start.setTextSize(14);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setTextColor(COLOR_BRAND);
        start.setGravity(Gravity.CENTER);
        start.setBackground(rounded(Color.WHITE, dp(22), Color.TRANSPARENT, 0));
        bindClick(start, () -> toast("进入报名页面"));
        attachNativePressAnimator(start, 3, 5);
        bottomRow.addView(start, new LinearLayout.LayoutParams(dp(80), dp(40)));

        FrameLayout.LayoutParams menuLp =
                new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.END | Gravity.TOP);
        menuLp.setMargins(0, dp(16), dp(12), 0);
        hero.addView(createMenuHandle(), menuLp);

        return hero;
    }

    private View createMenuHandle() {
        FrameLayout box = new FrameLayout(requireContext());
        box.setBackground(rounded(0x33000000, dp(23), Color.TRANSPARENT, 0));
        bindClick(box, this::openDrawer);

        LinearLayout bars = new LinearLayout(requireContext());
        bars.setOrientation(LinearLayout.VERTICAL);
        bars.setGravity(Gravity.CENTER);
        box.addView(bars, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        bars.addView(menuLine(18), new LinearLayout.LayoutParams(dp(18), dp(2)));
        addSpace(bars, 5);
        bars.addView(menuLine(14), new LinearLayout.LayoutParams(dp(14), dp(2)));
        addSpace(bars, 5);
        bars.addView(menuLine(18), new LinearLayout.LayoutParams(dp(18), dp(2)));

        return box;
    }

    private View menuLine(int widthDp) {
        View view = new View(requireContext());
        view.setBackground(rounded(Color.WHITE, dp(1), Color.TRANSPARENT, 0));
        return view;
    }

    // ===================== 顶部工具行 =====================

    private View createToolsRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        addTool(row, FA_LANGUAGE, "译", 0xFF3B82F6, 0xFF6366F1, "AI翻译",
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/"));
        addHorizontalGap(row, 12);
        addTool(row, FA_BOOK, "阅", 0xFF8B5CF6, 0xFFA855F7, "电子书", this::showBookPage);
        addHorizontalGap(row, 12);
        addTool(row, FA_MIC, "伴", 0xFF10B981, 0xFF14B8A6, "口语伴", this::showPromptScenes);
        addHorizontalGap(row, 12);
        addTool(row, FA_PEN, "练", 0xFFF97316, 0xFFF59E0B, "练习题",
                () -> toast("练习题后续接入"));

        return row;
    }

    private void addTool(LinearLayout row, String faCode, String fallback,
                         int c1, int c2, String title, Runnable click) {
        row.addView(toolCard(faCode, fallback, c1, c2, title, click),
                new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private View toolCard(String faCode, String fallbackText, int c1, int c2,
                          String title, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(4), dp(15), dp(4), dp(15));
        card.setBackground(rounded(COLOR_CARD_BG, dp(18), COLOR_STROKE, dp(1)));
        card.setElevation(dp(2));
        bindClick(card, click);
        attachNativePressAnimator(card, 2, 5);

        // 渐变圆角图标背景
        FrameLayout iconWrap = new FrameLayout(requireContext());
        iconWrap.setBackground(diagGradient(c1, c2, dp(14), Color.TRANSPARENT, 0));

        TextView iconView = new TextView(requireContext());
        iconView.setText(faTypeface != null ? faCode : fallbackText);
        iconView.setTextSize(faTypeface != null ? 19 : 17);
        iconView.setTextColor(Color.WHITE);
        iconView.setTypeface(faTypeface != null ? faTypeface : Typeface.DEFAULT_BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconWrap.addView(iconView,
                new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconLp.setMargins(0, 0, 0, dp(9));
        card.addView(iconWrap, iconLp);

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(12.5f);
        t.setTextColor(COLOR_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        return card;
    }

    // ===================== 分区 =====================

    private void addSection(LinearLayout parent, String title, String more, CardSpec[] cards) {
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(0, dp(14), 0, dp(12));
        parent.addView(header, headerLp);

        // 品牌色竖条
        View bar = new View(requireContext());
        bar.setBackground(rounded(COLOR_BRAND, dp(2), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(4), dp(18));
        barLp.setMargins(0, 0, dp(9), 0);
        header.addView(bar, barLp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(18.5f);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setIncludeFontPadding(false);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (more != null) {
            TextView moreView = new TextView(requireContext());
            moreView.setText(more + " 〉");
            moreView.setTextSize(13);
            moreView.setTextColor(COLOR_SUB);
            moreView.setGravity(Gravity.CENTER);
            moreView.setPadding(dp(8), dp(4), 0, dp(4));
            bindClick(moreView, () -> openMorePage(title));
            header.addView(moreView, new LinearLayout.LayoutParams(-2, -2));
        }

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        parent.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        final int columns = 2;
        int index = 0;
        while (index < cards.length) {
            LinearLayout gridRow = new LinearLayout(requireContext());
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            gridRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.setMargins(0, 0, 0, dp(12));
            grid.addView(gridRow, rowLp);

            for (int i = 0; i < columns; i++) {
                if (index < cards.length) {
                    gridRow.addView(smallCard(cards[index]),
                            new LinearLayout.LayoutParams(0, -2, 1f));
                    index++;
                } else {
                    gridRow.addView(new View(requireContext()),
                            new LinearLayout.LayoutParams(0, dp(1), 1f));
                }
                if (i < columns - 1) addHorizontalGap(gridRow, 12);
            }
        }
    }

    private View smallCard(final CardSpec spec) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setMinimumHeight(dp(74));
        card.setBackground(diagGradient(COLOR_CARD_BG, COLOR_CARD_BG_2, dp(16), COLOR_STROKE, dp(1)));
        card.setElevation(dp(1.5f));
        bindClick(card, () -> onSmallCardClick(spec));
        attachNativePressAnimator(card, 1.5f, 4);

        // 左侧彩色装饰条
        View accent = new View(requireContext());
        accent.setBackground(rounded(spec.accent, dp(2), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(4), dp(36));
        accentLp.setMargins(0, 0, dp(12), 0);
        card.addView(accent, accentLp);

        LinearLayout textBox = new LinearLayout(requireContext());
        textBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(requireContext());
        title.setText(spec.title);
        title.setTextSize(15);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        textBox.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(requireContext());
        desc.setText(spec.desc);
        desc.setTextSize(12);
        desc.setTextColor(COLOR_SUB);
        desc.setSingleLine(true);
        desc.setPadding(0, dp(6), 0, 0);
        textBox.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        return card;
    }

    // ===================== 侧边抽屉 =====================

    private View createSideDrawer() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(40), dp(20), dp(20));
        panel.setBackgroundColor(COLOR_CARD_BG);
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
        subLp.setMargins(0, dp(6), 0, dp(20));
        panel.addView(sub, subLp);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);

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
        view.setTextColor(COLOR_HINT);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(28));
        lp.setMargins(dp(2), dp(6), 0, dp(6));
        list.addView(view, lp);
    }

    private View drawerCard(String title, String desc, int accentColor, final Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(0xFFF7F8FB, dp(14), COLOR_STROKE, dp(1)));
        bindClick(card, () -> {
            closeDrawer();
            if (click != null) click.run();
        });

        // 渐变圆点
        View dot = new View(requireContext());
        dot.setBackground(diagGradient(accentColor, blend(accentColor, Color.WHITE, 0.35f),
                dp(5), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
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
        d.setPadding(0, dp(4), 0, 0);
        textBox.addView(d, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    // ===================== 交互逻辑 =====================

    private void onSmallCardClick(CardSpec spec) {
        if (spec == null || spec.id == null) return;
        if (spec.id.startsWith("hsk")) {
            Intent intent = new Intent(requireContext(), WordFullscreenActivity.class);
            intent.putExtra(WordFullscreenActivity.EXTRA_LEVEL, spec.id);
            intent.putExtra(WordFullscreenActivity.EXTRA_TITLE, spec.title);
            startActivity(intent);
        } else {
            toast(spec.title + " 正在开发中");
        }
    }

    private void openMorePage(String section) {
        if ("单词".equals(section)) {
            final String[] items = {"HSK 1", "HSK 2", "HSK 3", "HSK 4", "HSK 5", "HSK 6",
                    "生活高频", "工作求职", "恋爱聊天"};
            final String[] ids = {"hsk1", "hsk2", "hsk3", "hsk4", "hsk5", "hsk6",
                    "daily", "job", "love"};
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
            toast(section + " 更多内容即将上线");
        }
    }

    private void showBookPage() {
        toast("电子书架加载中...");
    }

    private void openSpeechSettings() {
        try {
            Class<?> clazz = Class.forName("com.chat.speech.ui.SpeechSettingsActivity");
            startActivity(new Intent(requireContext(), clazz));
        } catch (Throwable e) {
            toast("语音插件未安装");
        }
    }

    private void showPromptScenes() {
        final String[] names = {"日常打招呼", "点餐买东西", "求职面试", "医院看病",
                "机场过关", "租房沟通", "口语陪练", "中缅互译"};
        final String[] prompts = {
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
        ClipboardManager manager =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(name, prompt));
            toast("已复制：" + name);
        }
    }

    // ===================== Drawer 控制 =====================

    private void openDrawer() {
        if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
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

    // ===================== 工具方法 =====================

    /** 点击防抖：按 View 独立管理，避免多个卡片相互影响 */
    private void bindClick(final View view, final Runnable click) {
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            long last = 0L;

            @Override
            public void onClick(View v) {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - last < 420) return;
                last = now;
                v.animate().cancel();
                if (click != null) click.run();
            }
        });
    }

    private void attachNativePressAnimator(View view, float normalElevationDp, float pressedTranslationZDp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        view.setElevation(dp(normalElevationDp));

        StateListAnimator sla = new StateListAnimator();
        ObjectAnimator pressed = ObjectAnimator.ofFloat(view, "translationZ", dp(pressedTranslationZDp));
        pressed.setDuration(90);
        ObjectAnimator normal = ObjectAnimator.ofFloat(view, "translationZ", 0f);
        normal.setDuration(120);
        sla.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sla.addState(new int[]{}, normal);
        view.setStateListAnimator(sla);
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
                edgeSizeField.setInt(leftDragger, Math.max(oldSize, edgeSizePx));
            } catch (Throwable ignored) {
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

    // 渐变 / 圆角工具
    private GradientDrawable vGradient(int start, int end) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{start, end});
        d.setCornerRadius(0);
        return d;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private GradientDrawable diagGradient(int start, int end, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    /** 颜色混合，用于生成渐变浅色端 */
    private int blend(int c1, int c2, float ratio) {
        float r = 1f - ratio;
        int a = (int) (Color.alpha(c1) * r + Color.alpha(c2) * ratio);
        int red = (int) (Color.red(c1) * r + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * r + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * r + Color.blue(c2) * ratio);
        return Color.argb(a, red, g, b);
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        parent.addView(new View(requireContext()), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        parent.addView(new View(requireContext()), new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private int getDrawerWidth() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
    }

    private int dp(float value) {
        return (int) (value * density + 0.5f);
    }

    // ===================== 数据结构 =====================

    private static class CardSpec {
        final String title;
        final String desc;
        final String id;
        final int accent;

        CardSpec(String title, String desc, String id, int accent) {
            this.title = title;
            this.desc = desc;
            this.id = id;
            this.accent = accent;
        }
    }
}
