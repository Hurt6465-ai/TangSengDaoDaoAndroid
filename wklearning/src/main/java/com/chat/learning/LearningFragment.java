package com.chat.learning;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.customview.widget.ViewDragHelper;
import androidx.fragment.app.Fragment;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 学习主页：固定 Hero 背景 + 全宽上浮内容层。
 *
 * 设计原则：
 * 1. 具体场景使用统一线性图标，抽象知识继续使用幽灵文字。
 * 2. Android 9+ 使用原生彩色环境阴影，不开启大量软件模糊，保证滚动性能。
 * 3. 所有可点击卡片同时具备 Ripple 和 Z 轴按压反馈。
 * 4. 卡片使用最小高度而非固定高度，兼容系统大字体。
 */
public class LearningFragment extends Fragment {

    // ---------- Design tokens ----------
    private static final int COLOR_PAGE = 0xFFF2F6FC;
    private static final int COLOR_GLASS_TOP = 0xFFFFFFFF;
    private static final int COLOR_GLASS_BOTTOM = 0xFFF8FAFE;
    private static final int COLOR_TEXT = 0xFF182033;
    private static final int COLOR_SUB = 0xFF7A8498;
    private static final int COLOR_BRAND = 0xFF635BFF;
    private static final int COLOR_BRAND_END = 0xFF4D8DFF;

    private static final float RADIUS_SHEET = 32f;
    private static final float RADIUS_PANEL = 24f;
    private static final float RADIUS_CARD = 20f;
    private static final float RADIUS_PILL = 16f;

    private WideEdgeDrawerLayout drawerLayout;
    private View sideDrawerView;
    private final Map<String, HskProgressBinding> hskProgressViews = new HashMap<>();
    private int progressLoadToken;
    private long lastCardClickTime;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        hskProgressViews.clear();
        drawerLayout = new WideEdgeDrawerLayout(requireContext());
        drawerLayout.setBackgroundColor(COLOR_PAGE);
        drawerLayout.setScrimColor(0x4010182B);
        drawerLayout.setDrawerElevation(dp(24));

        View main = createMainPage();
        drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));

        sideDrawerView = createSideDrawer();
        DrawerLayout.LayoutParams drawerLp = new DrawerLayout.LayoutParams(getDrawerWidth(), -1);
        drawerLp.gravity = GravityCompat.START;
        drawerLayout.addView(sideDrawerView, drawerLp);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, sideDrawerView);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int edgeWidth = Math.min(dp(180), Math.max(dp(120), (int) (screenWidth * 0.30f)));
        drawerLayout.setEdgeSwipeWidth(edgeWidth);
        return drawerLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLocalWordProgress();
    }

    @Override
    public void onDestroyView() {
        progressLoadToken++;
        hskProgressViews.clear();
        sideDrawerView = null;
        drawerLayout = null;
        super.onDestroyView();
    }

    private View createMainPage() {
        FrameLayout page = new FrameLayout(requireContext());
        page.setBackgroundColor(COLOR_PAGE);

        LearningBackdropView backdrop = new LearningBackdropView(requireContext());
        page.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        // Hero 是固定背景层，内容滚动时它不移动。
        View hero = createHero();
        page.addView(hero, new FrameLayout.LayoutParams(-1, dp(336)));

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        page.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(0, 0, 0, dp(112));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        // 留出 Hero 可见区域，内容层提前覆盖约 50dp。
        root.addView(new View(requireContext()), new LinearLayout.LayoutParams(1, dp(286)));
        root.addView(createContentSheet(), new LinearLayout.LayoutParams(-1, -2));

        // 菜单独立放在最上层，不会再被 ScrollView 截走点击事件。
        View menuButton = createMenuHandle();
        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(
                dp(56), dp(56), Gravity.TOP | Gravity.END
        );
        menuLp.setMargins(0, getTopInset() + dp(4), dp(8), 0);
        page.addView(menuButton, menuLp);
        return page;
    }

    private View createHero() {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setClipChildren(false);

        ImageView image = new ImageView(requireContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageResource(getDrawableId(
                "learning_home_banner_cinematic",
                R.drawable.learning_home_banner_default
        ));
        hero.addView(image, new FrameLayout.LayoutParams(-1, -1));

        HeroImageScrimView scrim = new HeroImageScrimView(requireContext());
        hero.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM | Gravity.START);
        // 文案整体上移，避免贴近内容层顶部。
        copy.setPadding(dp(20), getTopInset() + dp(56), dp(20), dp(104));
        hero.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        TextView tag = text("AI 语伴课 · 零基础", 11, 0xEFFFFFFF, true);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(dp(12), dp(6), dp(12), dp(6));
        tag.setBackground(ripple(
                rounded(0x2EFFFFFF, dp(RADIUS_PILL), 0x55FFFFFF, dp(1)),
                0x44FFFFFF,
                RADIUS_PILL
        ));
        copy.addView(tag, new LinearLayout.LayoutParams(-2, -2));

        TextView title = text("90天搞定汉语口语", 27, Color.WHITE, true);
        title.setLetterSpacing(-0.01f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(12), 0, dp(7));
        copy.addView(title, titleLp);

        LinearLayout bottomRow = new LinearLayout(requireContext());
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(bottomRow, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text("每天10分钟，与 AI 语伴沉浸对练", 13, 0xE6FFFFFF, false);
        bottomRow.addView(sub, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView start = text("开始  ›", 13, COLOR_BRAND, true);
        start.setGravity(Gravity.CENTER);
        start.setBackground(ripple(
                rounded(0xF7FFFFFF, dp(18), 0xCCFFFFFF, dp(1)),
                withAlpha(COLOR_BRAND, 36),
                18
        ));
        bindClick(start, () -> openDirectory("pinyin", "拼音", ""));
        applyColoredShadow(start, COLOR_BRAND, 4f);
        attachNativePressAnimator(start, 4f, -2f);
        bottomRow.addView(start, new LinearLayout.LayoutParams(dp(82), dp(38)));
        return hero;
    }

    private LinearLayout createContentSheet() {
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(24), dp(16), dp(36));
        sheet.setBackground(topSheetDrawable());
        sheet.setClipToOutline(true);
        applyColoredShadow(sheet, COLOR_BRAND, 12f);

        TextView quickTitle = text("快捷工具", 22, COLOR_TEXT, true);
        sheet.addView(quickTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView quickSub = text("翻译、阅读与口语练习", 12, COLOR_SUB, false);
        LinearLayout.LayoutParams quickSubLp = new LinearLayout.LayoutParams(-1, -2);
        quickSubLp.setMargins(0, dp(6), 0, dp(16));
        sheet.addView(quickSub, quickSubLp);

        sheet.addView(createToolsPanel(), new LinearLayout.LayoutParams(-1, dp(104)));
        addSpace(sheet, 24);

        addSection(sheet, "拼音", "从发音开始", "更多",
                new CardSpec[]{
                        new CardSpec("声母", "b p m f", "initials"),
                        new CardSpec("韵母", "a o e i u", "finals"),
                        new CardSpec("整体", "zhi chi shi", "whole"),
                        new CardSpec("声调", "一二三四声", "tone")
                });

        addSection(sheet, "单词", "按等级稳步积累", "更多",
                new CardSpec[]{
                        CardSpec.hsk("HSK 1", "150 词", "hsk1", 1, 150),
                        CardSpec.hsk("HSK 2", "300 词", "hsk2", 2, 300),
                        CardSpec.hsk("HSK 3", "600 词", "hsk3", 3, 600),
                        CardSpec.hsk("HSK 4", "1200 词", "hsk4", 4, 1200)
                });

        addSection(sheet, "口语", "把中文真正说出来", "更多",
                new CardSpec[]{
                        CardSpec.icon("打招呼", "日常开场", "speak_hello", R.drawable.ic_learning_scene_hello),
                        CardSpec.icon("点餐", "餐厅购物", "speak_food", R.drawable.ic_learning_scene_food),
                        CardSpec.icon("求职", "面试工作", "speak_job", R.drawable.ic_learning_scene_job),
                        CardSpec.icon("购物", "买单砍价", "speak_shop", R.drawable.ic_learning_scene_shop)
                });

        addSection(sheet, "句型", "快速组织完整表达", "更多",
                new CardSpec[]{
                        new CardSpec("我想…", "表达需求", "pattern_want"),
                        new CardSpec("可以吗", "请求帮助", "pattern_can"),
                        new CardSpec("怎么…", "询问方法", "pattern_how"),
                        new CardSpec("为什么", "询问原因", "pattern_why")
                });

        addSection(sheet, "语法", "理解中文的结构", "更多",
                new CardSpec[]{
                        new CardSpec("了", "完成 / 变化", "grammar_le"),
                        new CardSpec("在", "正在进行", "grammar_zai"),
                        new CardSpec("吗 / 呢", "疑问语气", "grammar_ma"),
                        new CardSpec("的 / 得", "结构助词", "grammar_de")
                });
        return sheet;
    }

    private View createMenuHandle() {
        MenuHandleView button = new MenuHandleView(requireContext());
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("打开学习侧边栏");
        button.setOnClickListener(v -> openDrawer());
        return button;
    }

    private View createToolsPanel() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackground(gradientRounded(
                0xFFFFFFFF,
                0xFFF7F8FF,
                dp(RADIUS_PANEL),
                0x806B70F7,
                dp(1)
        ));
        applyColoredShadow(panel, 0xFF6E76F5, 6f);

        panel.addView(toolItem(
                R.drawable.ic_learning_translate,
                0xFF4D7CFE,
                "AI翻译",
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")
        ), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(panel, 6);

        panel.addView(toolItem(
                R.drawable.ic_learning_book,
                0xFF7D5CE8,
                "电子书",
                () -> openDirectory("books", "电子书", "")
        ), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(panel, 6);

        panel.addView(toolItem(
                R.drawable.ic_learning_mic,
                0xFF18A18A,
                "口语伴",
                () -> openDirectory("prompts", "口语 Prompt", "")
        ), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(panel, 6);

        panel.addView(toolItem(
                R.drawable.ic_learning_practice,
                0xFFED8A4A,
                "练习题",
                () -> openDirectory("quiz", "练习题", "")
        ), new LinearLayout.LayoutParams(0, -1, 1f));
        return panel;
    }

    private View toolItem(int iconRes, int accent, String title, Runnable click) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(6), dp(4), dp(6));
        item.setBackground(ripple(
                rounded(withAlpha(accent, 18), dp(16), withAlpha(accent, 92), dp(1)),
                withAlpha(accent, 38),
                16
        ));
        bindClick(item, click);
        attachNativePressAnimator(item, 0f, -1f);

        FrameLayout iconBox = new FrameLayout(requireContext());
        iconBox.setBackground(radialGlow(accent));
        item.addView(iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));

        TextView label = text(title, 12, COLOR_TEXT, true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.setMargins(0, dp(7), 0, 0);
        item.addView(label, labelLp);
        return item;
    }

    private View toolDivider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(0x247A8498);
        return divider;
    }

    private void addSection(LinearLayout parent, String title, String subtitle,
                            String more, CardSpec[] cards) {
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(dp(2), dp(8), dp(2), dp(14));
        parent.addView(header, headerLp);

        LinearLayout titles = new LinearLayout(requireContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView titleView = text(title, 22, COLOR_TEXT, true);
        titles.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitleView = text(subtitle, 12, COLOR_SUB, false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.setMargins(0, dp(5), 0, 0);
        titles.addView(subtitleView, subtitleLp);

        TextView moreView = text(more + "  ›", 13, 0xFF7C8496, true);
        moreView.setGravity(Gravity.CENTER);
        moreView.setPadding(dp(12), dp(8), dp(10), dp(8));
        moreView.setBackground(ripple(
                rounded(0x8FFFFFFF, dp(16), 0xB3FFFFFF, dp(1)),
                withAlpha(COLOR_BRAND, 26),
                16
        ));
        bindClick(moreView, () -> openMorePage(title));
        header.addView(moreView, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        parent.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        int index = 0;
        while (index < cards.length) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.setMargins(0, 0, 0, dp(12));
            grid.addView(row, rowLp);

            for (int column = 0; column < 2; column++) {
                if (index < cards.length) {
                    CardSpec cardSpec = cards[index++];
                    View card = smallCard(cardSpec);
                    card.setMinimumHeight(dp(cardSpec.level > 0 ? 120 : 96));
                    row.addView(card, new LinearLayout.LayoutParams(0, -2, 1f));
                } else {
                    row.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, dp(1), 1f));
                }
                if (column == 0) addHorizontalGap(row, 12);
            }
        }
        addSpace(parent, 12);
    }

    private View smallCard(CardSpec spec) {
        int accent = accentForCard(spec);
        int start = tintForCard(spec, true);
        int end = tintForCard(spec, false);

        FrameLayout card = new FrameLayout(requireContext());
        card.setPadding(dp(16), dp(14), dp(14), dp(14));
        card.setClipToOutline(true);
        card.setBackground(ripple(
                gradientRounded(start, end, dp(RADIUS_CARD), withAlpha(accent, 72), dp(1)),
                withAlpha(accent, 32),
                RADIUS_CARD
        ));
        bindClick(card, () -> onSmallCardClick(spec));
        applyColoredShadow(card, accent, 4f);
        attachNativePressAnimator(card, 4f, -2f);

        if (spec.iconRes != 0) {
            addSceneCardContent(card, spec, accent);
        } else if (spec.level > 0) {
            addHskCardContent(card, spec, accent);
        } else {
            addTextCardContent(card, spec, accent);
        }
        return card;
    }

    private void addTextCardContent(FrameLayout card, CardSpec spec, int accent) {
        TextView ghost = text(symbolForCard(spec), ghostTextSize(spec), withAlpha(accent, 44), true);
        ghost.setGravity(Gravity.CENTER);
        ghost.setIncludeFontPadding(false);
        ghost.setTranslationX(dp(13));
        ghost.setTranslationY(dp(10));
        ghost.setRotation(-10f);

        FrameLayout.LayoutParams ghostLp = new FrameLayout.LayoutParams(
                dp(82), dp(82), Gravity.END | Gravity.BOTTOM
        );
        ghostLp.setMargins(0, 0, -dp(5), -dp(7));
        card.addView(ghost, ghostLp);

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        View marker = new View(requireContext());
        marker.setBackground(rounded(accent, dp(3), Color.TRANSPARENT, 0));
        copy.addView(marker, new LinearLayout.LayoutParams(dp(20), dp(5)));

        TextView title = text(spec.title, 15, COLOR_TEXT, true);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(9), dp(44), 0);
        copy.addView(title, titleLp);

        TextView desc = text(spec.desc, 12, COLOR_SUB, false);
        desc.setMaxLines(2);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.setMargins(0, dp(5), dp(44), 0);
        copy.addView(desc, descLp);
    }

    private void addHskCardContent(FrameLayout card, CardSpec spec, int accent) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView eyebrow = text("LEVEL " + spec.level, 10, withAlpha(accent, 210), true);
        eyebrow.setLetterSpacing(0.08f);
        copy.addView(eyebrow, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text(spec.title, 16, COLOR_TEXT, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(7), 0, 0);
        copy.addView(title, titleLp);

        TextView progressText = text("已学 0 / " + spec.totalWords, 11, withAlpha(accent, 225), true);
        LinearLayout.LayoutParams progressTextLp = new LinearLayout.LayoutParams(-1, -2);
        progressTextLp.setMargins(0, dp(7), 0, 0);
        copy.addView(progressText, progressTextLp);

        LocalProgressBarView progressBar = new LocalProgressBarView(requireContext(), accent);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(5));
        barLp.setMargins(0, dp(7), dp(2), 0);
        copy.addView(progressBar, barLp);

        hskProgressViews.put(spec.id,
                new HskProgressBinding(progressText, progressBar, spec.totalWords));

        LevelBadgeView badge = new LevelBadgeView(requireContext(), spec.level, accent);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        badgeLp.setMargins(dp(10), 0, 0, 0);
        row.addView(badge, badgeLp);
    }

    private void addSceneCardContent(FrameLayout card, CardSpec spec, int accent) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout iconBox = new FrameLayout(requireContext());
        iconBox.setBackground(radialGlow(accent));
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(spec.iconRes);
        icon.setColorFilter(accent);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1f);
        copyLp.setMargins(dp(12), 0, 0, 0);
        row.addView(copy, copyLp);

        TextView title = text(spec.title, 15, COLOR_TEXT, true);
        title.setMaxLines(2);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = text(spec.desc, 12, COLOR_SUB, false);
        desc.setMaxLines(2);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.setMargins(0, dp(6), 0, 0);
        copy.addView(desc, descLp);
    }

    private View createSideDrawer() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), getTopInset() + dp(24), dp(18), dp(20));
        panel.setBackground(drawerBackground());
        panel.setClickable(true);

        TextView kicker = text("LEARNING SPACE", 10, 0xFF7D72E6, true);
        kicker.setLetterSpacing(0.12f);
        panel.addView(kicker, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("更多服务", 25, COLOR_TEXT, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(8), 0, 0);
        panel.addView(title, titleLp);

        TextView sub = text("AI、语音与扩展工具", 13, COLOR_SUB, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(20));
        panel.addView(sub, subLp);

        LinearLayout focusCard = new LinearLayout(requireContext());
        focusCard.setOrientation(LinearLayout.VERTICAL);
        focusCard.setGravity(Gravity.CENTER_VERTICAL);
        focusCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        focusCard.setBackground(gradientRounded(
                0x6DDED9FF,
                0x45D6EEFF,
                dp(20),
                0xB3FFFFFF,
                dp(1)
        ));
        TextView focusTitle = text("沉浸学习空间", 13, COLOR_TEXT, true);
        focusCard.addView(focusTitle, new LinearLayout.LayoutParams(-1, -2));
        TextView focusSub = text("常用学习能力集中在这里", 11, 0xFF7E879A, false);
        LinearLayout.LayoutParams focusSubLp = new LinearLayout.LayoutParams(-1, -2);
        focusSubLp.setMargins(0, dp(4), 0, 0);
        focusCard.addView(focusSub, focusSubLp);
        LinearLayout.LayoutParams focusLp = new LinearLayout.LayoutParams(-1, dp(58));
        focusLp.setMargins(0, 0, 0, dp(18));
        panel.addView(focusCard, focusLp);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        drawerGroupTitle(list, "AI 助手");
        list.addView(drawerCard("DeepSeek 翻译", "中缅互译、语法解释", 0xFF4D7CFE,
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));
        list.addView(drawerCard("886.best", "国内学习 AI 入口", 0xFF18AFC5,
                () -> AiScriptWebActivity.open(requireContext(), "886.best", "https://886.best")));
        list.addView(drawerCard("千问国内版", "qianwen.com", 0xFF8A5AF4,
                () -> AiScriptWebActivity.open(requireContext(), "千问国内版", "https://www.qianwen.com/")));
        list.addView(drawerCard("Qwen 国际版", "chat.qwen.ai", 0xFF6366F1,
                () -> AiScriptWebActivity.open(requireContext(), "Qwen 国际版", "https://chat.qwen.ai/")));

        drawerGroupTitle(list, "学习工具");
        list.addView(drawerCard("语音设置", "WKSpeech 引擎配置", 0xFF12A78E,
                this::openSpeechSettings));
        list.addView(drawerCard("口语 Prompt", "生活场景对话指令", 0xFFF39A4E,
                () -> openDirectory("prompts", "口语 Prompt", "")));

        drawerGroupTitle(list, "扩展功能");
        list.addView(drawerCard("脚本中心", "自定义扩展功能", 0xFFE45B85,
                () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return panel;
    }

    private void drawerGroupTitle(LinearLayout list, String title) {
        TextView view = text(title, 11, 0xFF9AA2B3, true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(32));
        lp.setMargins(dp(2), dp(4), 0, dp(6));
        list.addView(view, lp);
    }

    private View drawerCard(String title, String desc, int accent, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(12), dp(13));
        card.setBackground(ripple(
                gradientRounded(0xDFFFFFFF, 0xBFFFFFFF, dp(20), 0xD9FFFFFF, dp(1)),
                withAlpha(accent, 28),
                20
        ));
        bindClick(card, () -> {
            closeDrawer();
            if (click != null) click.run();
        });
        applyColoredShadow(card, accent, 2f);
        attachNativePressAnimator(card, 2f, -1f);

        FrameLayout dotBox = new FrameLayout(requireContext());
        dotBox.setBackground(radialGlow(accent));
        LinearLayout.LayoutParams dotBoxLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        dotBoxLp.setMargins(0, 0, dp(12), 0);
        card.addView(dotBox, dotBoxLp);

        View dot = new View(requireContext());
        dot.setBackground(rounded(accent, dp(5), Color.TRANSPARENT, 0));
        dotBox.addView(dot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));

        LinearLayout textBox = new LinearLayout(requireContext());
        textBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView titleView = text(title, 14, COLOR_TEXT, true);
        textBox.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView descView = text(desc, 12, COLOR_SUB, false);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.setMargins(0, dp(4), 0, 0);
        textBox.addView(descView, descLp);

        TextView arrow = text("›", 24, 0xFFB0B6C4, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(34)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    private void onSmallCardClick(CardSpec spec) {
        if (spec == null || spec.id == null) return;
        String type = typeForCardId(spec.id);
        if (type == null) {
            Toast.makeText(requireContext(), spec.title + " 正在开发中", Toast.LENGTH_SHORT).show();
            return;
        }
        openDirectory(type, spec.title, spec.id);
    }

    private String typeForCardId(String id) {
        if (id == null) return null;
        if (id.startsWith("hsk") || "daily".equals(id) || "job".equals(id)
                || "love".equals(id) || "greeting".equals(id)) return "words";
        if (id.startsWith("speak")) return "speaking";
        if (id.startsWith("pattern")) return "patterns";
        if (id.startsWith("grammar")) return "grammar";
        if ("initials".equals(id) || "finals".equals(id)
                || "whole".equals(id) || "tone".equals(id)) return "pinyin";
        return null;
    }

    private void openMorePage(String section) {
        if ("拼音".equals(section)) {
            openDirectory("pinyin", "拼音", "");
        } else if ("单词".equals(section)) {
            openDirectory("words", "单词", "");
        } else if ("口语".equals(section)) {
            openDirectory("speaking", "口语", "");
        } else if ("句型".equals(section)) {
            openDirectory("patterns", "句型", "");
        } else if ("语法".equals(section)) {
            openDirectory("grammar", "语法", "");
        } else {
            Toast.makeText(requireContext(), section + " 更多内容即将上线", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDirectory(String type, String title, String parentId) {
        LearningDirectoryActivity.open(
                requireContext(),
                type,
                title,
                parentId == null ? "" : parentId
        );
    }

    private void openSpeechSettings() {
        try {
            Class<?> clazz = Class.forName("com.chat.speech.ui.SpeechSettingsActivity");
            startActivity(new Intent(requireContext(), clazz));
        } catch (Throwable e) {
            Toast.makeText(requireContext(), "语音插件未安装", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 单词学习记录已经由 WordProgressStore(SQLite) 保存在本机。
     * 首页只读取 reviewCount>0 的唯一单词数，不因为打开卡片而虚增进度。
     */
    private void refreshLocalWordProgress() {
        if (drawerLayout == null || hskProgressViews.isEmpty() || !isAdded()) return;
        final int token = ++progressLoadToken;
        final Context app = requireContext().getApplicationContext();

        Thread worker = new Thread(() -> {
            Map<String, Integer> counts = new HashMap<>();
            WordProgressStore store = new WordProgressStore(app);
            try {
                String[] packIds = new String[]{"hsk1", "hsk2", "hsk3", "hsk4"};
                for (String packId : packIds) {
                    int learned = 0;
                    Map<String, WordFsrsScheduler.CardState> states = store.loadPack(packId);
                    for (WordFsrsScheduler.CardState state : states.values()) {
                        if (state != null && state.reviewCount > 0) learned++;
                    }
                    counts.put(packId, learned);
                }
            } finally {
                store.close();
            }

            WideEdgeDrawerLayout root = drawerLayout;
            if (root == null) return;
            root.post(() -> {
                if (!isAdded() || token != progressLoadToken) return;
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    HskProgressBinding binding = hskProgressViews.get(entry.getKey());
                    if (binding != null) binding.update(entry.getValue());
                }
            });
        }, "learning-home-progress");
        worker.setDaemon(true);
        worker.start();
    }

    private void openDrawer() {
        if (drawerLayout == null || sideDrawerView == null) return;
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, sideDrawerView);
        sideDrawerView.setVisibility(View.VISIBLE);
        drawerLayout.openDrawer(sideDrawerView, true);
    }

    private void closeDrawer() {
        if (drawerLayout != null && sideDrawerView != null
                && drawerLayout.isDrawerOpen(sideDrawerView)) {
            drawerLayout.closeDrawer(sideDrawerView, true);
        }
    }

    public boolean closeSideMenuIfOpen() {
        if (drawerLayout != null && sideDrawerView != null
                && drawerLayout.isDrawerOpen(sideDrawerView)) {
            drawerLayout.closeDrawer(sideDrawerView, true);
            return true;
        }
        return false;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int accentForCard(CardSpec spec) {
        if (spec == null || spec.id == null) return COLOR_BRAND;
        switch (spec.id) {
            case "hsk1": return 0xFF25A989;
            case "hsk2": return 0xFF159B83;
            case "hsk3": return 0xFF078B7B;
            case "hsk4": return 0xFF08756C;
            case "speak_hello": return 0xFFF17369;
            case "speak_food": return 0xFFEE8A46;
            case "speak_job": return 0xFF5C72E8;
            case "speak_shop": return 0xFFE15B91;
            default:
                if (spec.id.startsWith("pattern")) return 0xFF4285E8;
                if (spec.id.startsWith("grammar")) return 0xFF865AE8;
                return 0xFF6659E8;
        }
    }

    private int tintForCard(CardSpec spec, boolean start) {
        if (spec == null || spec.id == null) return start ? 0xFFE6E0FF : 0xFFF3F0FF;
        switch (spec.id) {
            case "hsk1": return start ? 0xFFD7F5EA : 0xFFEAFBF5;
            case "hsk2": return start ? 0xFFCCEFE4 : 0xFFE4F8F1;
            case "hsk3": return start ? 0xFFC0E7DD : 0xFFDCF3EC;
            case "hsk4": return start ? 0xFFB3DDD5 : 0xFFD2EAE5;
            case "speak_hello": return start ? 0xFFFFDCD8 : 0xFFFFEDEA;
            case "speak_food": return start ? 0xFFFFE2CC : 0xFFFFF1E4;
            case "speak_job": return start ? 0xFFDCE3FF : 0xFFEDF0FF;
            case "speak_shop": return start ? 0xFFFFD8E8 : 0xFFFFEBF3;
            default:
                if (spec.id.startsWith("pattern")) return start ? 0xFFDCEBFF : 0xFFEDF5FF;
                if (spec.id.startsWith("grammar")) return start ? 0xFFE6DFFF : 0xFFF2EEFF;
                return start ? 0xFFE4DEFF : 0xFFF2EFFF;
        }
    }

    private String symbolForCard(CardSpec spec) {
        if (spec == null || spec.id == null) return "·";
        switch (spec.id) {
            case "initials": return "b";
            case "finals": return "a";
            case "whole": return "zhi";
            case "tone": return "ˇ";
            case "pattern_want": return "想";
            case "pattern_can": return "可";
            case "pattern_how": return "怎";
            case "pattern_why": return "为";
            case "grammar_le": return "了";
            case "grammar_zai": return "在";
            case "grammar_ma": return "吗";
            case "grammar_de": return "的";
            default: return "·";
        }
    }

    private float ghostTextSize(CardSpec spec) {
        if (spec != null && "whole".equals(spec.id)) return 33f;
        return 48f;
    }

    private void bindClick(View view, Runnable click) {
        view.setClickable(true);
        view.setOnClickListener(v -> {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastCardClickTime < 360) return;
            lastCardClickTime = now;
            v.animate().cancel();
            if (click != null) click.run();
        });
    }

    private void applyColoredShadow(View view, int accent, float elevationDp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        view.setElevation(dp(elevationDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setOutlineAmbientShadowColor(withAlpha(accent, 38));
            view.setOutlineSpotShadowColor(withAlpha(accent, 58));
        }
    }

    private void attachNativePressAnimator(View view, float normalElevationDp, float pressedOffsetDp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        view.setElevation(dp(normalElevationDp));

        StateListAnimator animator = new StateListAnimator();
        ObjectAnimator pressed = ObjectAnimator.ofFloat(
                view,
                "translationZ",
                dp(pressedOffsetDp)
        );
        pressed.setDuration(80);
        ObjectAnimator normal = ObjectAnimator.ofFloat(view, "translationZ", 0f);
        normal.setDuration(140);
        animator.addState(new int[]{android.R.attr.state_pressed}, pressed);
        animator.addState(new int[]{}, normal);
        view.setStateListAnimator(animator);
    }

    private Drawable ripple(Drawable content, int rippleColor, float radiusDp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return content;
        GradientDrawable mask = rounded(Color.WHITE, dp(radiusDp), Color.TRANSPARENT, 0);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private GradientDrawable topSheetDrawable() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_GLASS_TOP, COLOR_GLASS_BOTTOM}
        );
        float radius = dp(RADIUS_SHEET);
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        drawable.setStroke(dp(1), 0xEFFFFFFF);
        return drawable;
    }

    private GradientDrawable drawerBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFF8F7FF, 0xFFF1F8FF, 0xFFFBFCFF}
        );
        drawable.setCornerRadius(0f);
        drawable.setStroke(dp(1), 0xFFFFFFFF);
        return drawable;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable gradientRounded(int startColor, int endColor, float radius,
                                              int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor}
        );
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable radialGlow(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setGradientRadius(dp(28));
        drawable.setColors(new int[]{withAlpha(color, 58), withAlpha(color, 8)});
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int getDrawableId(String name, int fallback) {
        int id = getResources().getIdentifier(name, "drawable", requireContext().getPackageName());
        return id == 0 ? fallback : id;
    }

    private int getTopInset() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) return getResources().getDimensionPixelSize(resId);
        return dp(24);
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        parent.addView(new View(requireContext()), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        parent.addView(new View(requireContext()), new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private int getDrawerWidth() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class CardSpec {
        final String title;
        final String desc;
        final String id;
        final int iconRes;
        final int level;
        final int totalWords;

        CardSpec(String title, String desc, String id) {
            this(title, desc, id, 0, 0, 0);
        }

        private CardSpec(String title, String desc, String id, int iconRes,
                         int level, int totalWords) {
            this.title = title;
            this.desc = desc;
            this.id = id;
            this.iconRes = iconRes;
            this.level = level;
            this.totalWords = totalWords;
        }

        static CardSpec icon(String title, String desc, String id, int iconRes) {
            return new CardSpec(title, desc, id, iconRes, 0, 0);
        }

        static CardSpec hsk(String title, String desc, String id, int level, int totalWords) {
            return new CardSpec(title, desc, id, 0, level, totalWords);
        }
    }

    private static class HskProgressBinding {
        final TextView label;
        final LocalProgressBarView bar;
        final int total;

        HskProgressBinding(TextView label, LocalProgressBarView bar, int total) {
            this.label = label;
            this.bar = bar;
            this.total = Math.max(1, total);
        }

        void update(int value) {
            int learned = Math.max(0, Math.min(total, value));
            label.setText("已学 " + learned + " / " + total);
            bar.setProgress(learned / (float) total);
        }
    }

    /** 读取本地 SQLite 后显示的真实进度条；不使用静态装饰数值。 */
    private static class LocalProgressBarView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;

        LocalProgressBarView(Context context, int accent) {
            super(context);
            track.setColor((accent & 0x00FFFFFF) | 0x24000000);
            fill.setColor(accent);
        }

        void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = getHeight() / 2f;
            RectF full = new RectF(0f, 0f, getWidth(), getHeight());
            canvas.drawRoundRect(full, radius, radius, track);
            if (progress <= 0f) return;
            float width = Math.max(getHeight(), getWidth() * progress);
            RectF done = new RectF(0f, 0f, Math.min(getWidth(), width), getHeight());
            canvas.drawRoundRect(done, radius, radius, fill);
        }
    }

    /** 无底托三横线：顶部最长，中间较短，底部最短，右侧对齐。 */
    private static class MenuHandleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        MenuHandleView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            paint.setColor(0xFFF9FBFF);
            paint.setStrokeWidth(2.15f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setShadowLayer(2.8f * density, 0f, 1.1f * density, 0x66071222);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float right = getWidth() * 0.72f;
            float centerY = getHeight() * 0.50f;
            float gap = 6f * density;
            drawLine(canvas, right, centerY - gap, 22f * density);
            drawLine(canvas, right, centerY, 16f * density);
            drawLine(canvas, right, centerY + gap, 10f * density);
        }

        private void drawLine(Canvas canvas, float right, float y, float width) {
            canvas.drawLine(right - width, y, right, y, paint);
        }
    }

    /** HSK 静态等级徽章。这里只表达级别，不显示任何虚假的学习进度。 */
    private static class LevelBadgeView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int level;
        private final float density;

        LevelBadgeView(Context context, int level, int color) {
            super(context);
            this.level = Math.max(1, Math.min(4, level));
            density = context.getResources().getDisplayMetrics().density;

            fill.setStyle(Paint.Style.FILL);
            fill.setColor((color & 0x00FFFFFF) | 0x24000000);

            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(1.4f * density);
            border.setColor((color & 0x00FFFFFF) | 0x8A000000);

            label.setColor(color);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextSize(15f * density);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = 3.5f * density;
            float radius = 15f * density;
            RectF box = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawRoundRect(box, radius, radius, fill);
            canvas.drawRoundRect(box, radius, radius, border);

            Paint.FontMetrics fm = label.getFontMetrics();
            float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(String.valueOf(level), getWidth() / 2f, y, label);
        }
    }

    /**
     * 优先扩大 DrawerLayout 自带拖拽边缘，让抽屉真正跟手移动。
     * 反射失败时只旁观手势并触发打开，绝不抢走 ScrollView 的事件序列。
     */
    private static class WideEdgeDrawerLayout extends DrawerLayout {
        private final int touchSlop;
        private int edgeSwipeWidth;
        private float downX;
        private float downY;
        private boolean fallbackTracking;
        private boolean fallbackTriggered;
        private boolean nativeEdgeExpanded;

        WideEdgeDrawerLayout(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            edgeSwipeWidth = touchSlop * 3;
        }

        void setEdgeSwipeWidth(int widthPx) {
            edgeSwipeWidth = Math.max(widthPx, touchSlop * 3);
            post(this::expandNativeLeftEdge);
        }

        private void expandNativeLeftEdge() {
            try {
                Field draggerField = DrawerLayout.class.getDeclaredField("mLeftDragger");
                draggerField.setAccessible(true);
                Object draggerObject = draggerField.get(this);
                if (!(draggerObject instanceof ViewDragHelper)) return;

                ViewDragHelper dragger = (ViewDragHelper) draggerObject;
                Field edgeField = ViewDragHelper.class.getDeclaredField("mEdgeSize");
                edgeField.setAccessible(true);
                int current = edgeField.getInt(dragger);
                edgeField.setInt(dragger, Math.max(current, edgeSwipeWidth));
                nativeEdgeExpanded = true;
            } catch (Throwable ignored) {
                nativeEdgeExpanded = false;
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            observeFallbackGesture(event);
            return super.dispatchTouchEvent(event);
        }

        private void observeFallbackGesture(MotionEvent event) {
            if (nativeEdgeExpanded) return;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                fallbackTriggered = false;
                fallbackTracking = downX <= edgeSwipeWidth
                        && !isDrawerOpen(GravityCompat.START);
                return;
            }
            if (action == MotionEvent.ACTION_MOVE && fallbackTracking && !fallbackTriggered) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (dx > touchSlop * 2f && dx > Math.abs(dy) * 1.25f) {
                    fallbackTriggered = true;
                    fallbackTracking = false;
                    openDrawer(GravityCompat.START, true);
                } else if (Math.abs(dy) > touchSlop * 1.5f
                        && Math.abs(dy) > Math.abs(dx)) {
                    fallbackTracking = false;
                }
                return;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                fallbackTracking = false;
                fallbackTriggered = false;
            }
        }
    }

    /** 页面底层弥散光，仅服务于顶部背景区域；内容层保持不透明。 */
    private static class LearningBackdropView extends View {
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint violet = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LearningBackdropView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            base.setShader(new LinearGradient(
                    0, 0, w, h,
                    new int[]{0xFFF3F0FF, 0xFFF0F8FF, 0xFFFAFCFF},
                    new float[]{0f, 0.48f, 1f},
                    Shader.TileMode.CLAMP
            ));
            violet.setShader(new RadialGradient(
                    w * 0.88f, h * 0.14f, w * 0.50f,
                    0x3A8A78FF, Color.TRANSPARENT, Shader.TileMode.CLAMP
            ));
            blue.setShader(new RadialGradient(
                    w * 0.08f, h * 0.42f, w * 0.45f,
                    0x2E6BB7FF, Color.TRANSPARENT, Shader.TileMode.CLAMP
            ));
            mint.setShader(new RadialGradient(
                    w * 0.82f, h * 0.72f, w * 0.52f,
                    0x2475D9C7, Color.TRANSPARENT, Shader.TileMode.CLAMP
            ));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), base);
            canvas.drawCircle(getWidth() * 0.88f, getHeight() * 0.14f,
                    getWidth() * 0.50f, violet);
            canvas.drawCircle(getWidth() * 0.08f, getHeight() * 0.42f,
                    getWidth() * 0.45f, blue);
            canvas.drawCircle(getWidth() * 0.82f, getHeight() * 0.72f,
                    getWidth() * 0.52f, mint);
        }
    }

    /** Hero 图片暗角与底部沉浸色，背景图和内容层之间不会出现生硬断层。 */
    private static class HeroImageScrimView extends View {
        private final Paint topScrim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomScrim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ring = new RectF();

        HeroImageScrimView(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(context.getResources().getDisplayMetrics().density);
            ringPaint.setColor(0x20FFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            topScrim.setShader(new LinearGradient(
                    0, 0, 0, h * 0.58f,
                    new int[]{0x6D071222, 0x12071222},
                    null,
                    Shader.TileMode.CLAMP
            ));
            bottomScrim.setShader(new LinearGradient(
                    0, h * 0.36f, 0, h,
                    new int[]{0x00000000, 0x54203956, COLOR_PAGE},
                    new float[]{0f, 0.62f, 1f},
                    Shader.TileMode.CLAMP
            ));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), topScrim);
            canvas.drawRect(0, 0, getWidth(), getHeight(), bottomScrim);

            float size = Math.min(getWidth(), getHeight()) * 0.30f;
            ring.set(getWidth() - size * 1.05f, -size * 0.28f,
                    getWidth() + size * 0.16f, size * 0.93f);
            canvas.drawOval(ring, ringPaint);
        }
    }
}
