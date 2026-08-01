package com.chat.learning;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.app.Activity;
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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.customview.widget.ViewDragHelper;
import androidx.fragment.app.Fragment;

import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.ui.components.AvatarView;
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

    private WideEdgeDrawerLayout drawerLayout;
    private View sideDrawerView;
    private AvatarView drawerAvatarView;
    private TextView drawerNameTv;
    private TextView drawerAccountTv;
    private ViewFlipper priceFlipper;
    private final Map<String, HskProgressBinding> hskProgressViews = new HashMap<>();
    private int progressLoadToken;
    private long lastCardClickTime;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        hskProgressViews.clear();
        drawerLayout = new WideEdgeDrawerLayout(requireContext());
        drawerLayout.setBackgroundColor(COLOR_PAGE);
        drawerLayout.setScrimColor(0x2E10182B);
        drawerLayout.setDrawerElevation(dp(12));

        View main = createMainPage();
        drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));

        sideDrawerView = createSideDrawer();
        DrawerLayout.LayoutParams drawerLp = new DrawerLayout.LayoutParams(getDrawerWidth(), -1);
        drawerLp.gravity = GravityCompat.START;
        drawerLayout.addView(sideDrawerView, drawerLp);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, sideDrawerView);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int edgeWidth = Math.min(dp(96), Math.max(dp(64), (int) (screenWidth * 0.18f)));
        drawerLayout.setEdgeSwipeWidth(edgeWidth);
        return drawerLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLocalWordProgress();
        refreshDrawerProfile();
        if (priceFlipper != null && priceFlipper.getChildCount() > 1) {
            priceFlipper.startFlipping();
        }
    }

    @Override
    public void onPause() {
        if (priceFlipper != null) priceFlipper.stopFlipping();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        progressLoadToken++;
        hskProgressViews.clear();
        sideDrawerView = null;
        drawerAvatarView = null;
        drawerNameTv = null;
        drawerAccountTv = null;
        priceFlipper = null;
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

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.BOTTOM | Gravity.START);
        content.setPadding(dp(20), getTopInset() + dp(48), dp(20), dp(92));
        hero.addView(content, new FrameLayout.LayoutParams(-1, -1));

        priceFlipper = new ViewFlipper(requireContext());
        priceFlipper.setFlipInterval(4800);
        priceFlipper.setAutoStart(false);
        priceFlipper.setMeasureAllChildren(false);

        AlphaAnimation in = new AlphaAnimation(0f, 1f);
        in.setDuration(360);
        AlphaAnimation out = new AlphaAnimation(1f, 0f);
        out.setDuration(260);
        priceFlipper.setInAnimation(in);
        priceFlipper.setOutAnimation(out);

        String[] titles = getResources().getStringArray(R.array.learning_home_plan_titles);
        String[] subtitles = getResources().getStringArray(R.array.learning_home_plan_subtitles);
        String[] prices = getResources().getStringArray(R.array.learning_home_plan_prices);
        String[] notes = getResources().getStringArray(R.array.learning_home_plan_notes);
        int count = Math.min(Math.min(titles.length, subtitles.length),
                Math.min(prices.length, notes.length));
        for (int i = 0; i < count; i++) {
            priceFlipper.addView(createPriceSlide(
                    titles[i], subtitles[i], prices[i], notes[i], i
            ), new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout.LayoutParams flipperLp = new LinearLayout.LayoutParams(-1, dp(142));
        flipperLp.setMargins(0, 0, 0, 0);
        content.addView(priceFlipper, flipperLp);
        return hero;
    }

    private View createPriceSlide(String title, String subtitle, String price,
                                  String note, int index) {
        LinearLayout slide = new LinearLayout(requireContext());
        slide.setOrientation(LinearLayout.VERTICAL);
        slide.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        slide.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView titleView = text(title, 25, Color.WHITE, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView subtitleView = text(subtitle, 13, 0xE8FFFFFF, false);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.setMargins(0, dp(8), 0, dp(13));
        slide.addView(subtitleView, subtitleLp);

        LinearLayout bottom = new LinearLayout(requireContext());
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        slide.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout priceBox = new LinearLayout(requireContext());
        priceBox.setOrientation(LinearLayout.VERTICAL);
        bottom.addView(priceBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView priceView = text(price, 23, Color.WHITE, true);
        priceBox.addView(priceView, new LinearLayout.LayoutParams(-1, -2));

        TextView noteView = text(note, 11, 0xD9FFFFFF, false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(0, dp(4), 0, 0);
        priceBox.addView(noteView, noteLp);

        TextView enroll = text(getString(R.string.learning_home_enroll), 13, COLOR_BRAND, true);
        enroll.setGravity(Gravity.CENTER);
        enroll.setBackground(ripple(
                rounded(0xF8FFFFFF, dp(19), 0xD9FFFFFF, dp(1)),
                withAlpha(COLOR_BRAND, 36), 19
        ));
        bindClick(enroll, () -> onEnrollClick(index));
        applyColoredShadow(enroll, COLOR_BRAND, 4f);
        attachNativePressAnimator(enroll, 4f, -2f);
        bottom.addView(enroll, new LinearLayout.LayoutParams(dp(88), dp(40)));
        return slide;
    }

    private void onEnrollClick(int planIndex) {
        Toast.makeText(requireContext(),
                getString(R.string.learning_home_enroll_pending), Toast.LENGTH_SHORT).show();
    }

    private LinearLayout createContentSheet() {
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(24), dp(16), dp(36));
        sheet.setBackground(topSheetDrawable());
        sheet.setClipToOutline(true);
        applyColoredShadow(sheet, COLOR_BRAND, 12f);

        TextView quickTitle = text(getString(R.string.learning_home_quick_tools),
                22, COLOR_TEXT, true);
        sheet.addView(quickTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView quickSub = text(getString(R.string.learning_home_quick_tools_subtitle),
                12, COLOR_SUB, false);
        LinearLayout.LayoutParams quickSubLp = new LinearLayout.LayoutParams(-1, -2);
        quickSubLp.setMargins(0, dp(6), 0, dp(16));
        sheet.addView(quickSub, quickSubLp);

        sheet.addView(createToolsPanel(), new LinearLayout.LayoutParams(-1, dp(104)));
        addSpace(sheet, 24);

        addSection(sheet,
                getString(R.string.learning_home_pinyin_title),
                getString(R.string.learning_home_pinyin_subtitle),
                "pinyin",
                new CardSpec[]{
                        new CardSpec(getString(R.string.learning_home_initials),
                                "b p m f", "initials"),
                        new CardSpec(getString(R.string.learning_home_finals),
                                "a o e i u", "finals"),
                        new CardSpec(getString(R.string.learning_home_whole_syllables),
                                "zhi chi shi", "whole"),
                        new CardSpec(getString(R.string.learning_home_tones),
                                getString(R.string.learning_home_tones_desc), "tone")
                });

        addSection(sheet,
                getString(R.string.learning_home_words_title),
                getString(R.string.learning_home_words_subtitle),
                "words",
                new CardSpec[]{
                        CardSpec.hsk("HSK 1", getString(R.string.learning_home_words_count, 150),
                                "hsk1", 1, 150),
                        CardSpec.hsk("HSK 2", getString(R.string.learning_home_words_count, 300),
                                "hsk2", 2, 300),
                        CardSpec.hsk("HSK 3", getString(R.string.learning_home_words_count, 600),
                                "hsk3", 3, 600),
                        CardSpec.hsk("HSK 4", getString(R.string.learning_home_words_count, 1200),
                                "hsk4", 4, 1200)
                });

        addSection(sheet,
                getString(R.string.learning_home_speaking_title),
                getString(R.string.learning_home_speaking_subtitle),
                "speaking",
                new CardSpec[]{
                        CardSpec.icon(getString(R.string.learning_home_speak_hello),
                                getString(R.string.learning_home_speak_hello_desc),
                                "speak_hello", R.drawable.ic_learning_scene_hello),
                        CardSpec.icon(getString(R.string.learning_home_speak_food),
                                getString(R.string.learning_home_speak_food_desc),
                                "speak_food", R.drawable.ic_learning_scene_food),
                        CardSpec.icon(getString(R.string.learning_home_speak_job),
                                getString(R.string.learning_home_speak_job_desc),
                                "speak_job", R.drawable.ic_learning_scene_job),
                        CardSpec.icon(getString(R.string.learning_home_speak_shop),
                                getString(R.string.learning_home_speak_shop_desc),
                                "speak_shop", R.drawable.ic_learning_scene_shop)
                });

        addSection(sheet,
                getString(R.string.learning_home_patterns_title),
                getString(R.string.learning_home_patterns_subtitle),
                "patterns",
                new CardSpec[]{
                        new CardSpec(getString(R.string.learning_home_pattern_want),
                                getString(R.string.learning_home_pattern_want_desc), "pattern_want"),
                        new CardSpec(getString(R.string.learning_home_pattern_can),
                                getString(R.string.learning_home_pattern_can_desc), "pattern_can"),
                        new CardSpec(getString(R.string.learning_home_pattern_how),
                                getString(R.string.learning_home_pattern_how_desc), "pattern_how"),
                        new CardSpec(getString(R.string.learning_home_pattern_why),
                                getString(R.string.learning_home_pattern_why_desc), "pattern_why")
                });

        addSection(sheet,
                getString(R.string.learning_home_grammar_title),
                getString(R.string.learning_home_grammar_subtitle),
                "grammar",
                new CardSpec[]{
                        new CardSpec("了", getString(R.string.learning_home_grammar_le_desc), "grammar_le"),
                        new CardSpec("在", getString(R.string.learning_home_grammar_zai_desc), "grammar_zai"),
                        new CardSpec("吗 / 呢", getString(R.string.learning_home_grammar_ma_desc), "grammar_ma"),
                        new CardSpec("的 / 得", getString(R.string.learning_home_grammar_de_desc), "grammar_de")
                });
        return sheet;
    }

    private View createMenuHandle() {
        MenuHandleView button = new MenuHandleView(requireContext());
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(getString(R.string.learning_home_open_drawer));
        button.setOnClickListener(v -> openDrawer());
        return button;
    }

    private View createToolsPanel() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(6), dp(8), dp(6), dp(8));
        panel.setBackground(gradientRounded(
                0xFFFFFFFF,
                0xFFF9FAFF,
                dp(RADIUS_PANEL),
                0x526B70F7,
                dp(1)
        ));
        applyColoredShadow(panel, 0xFF6E76F5, 5f);

        panel.addView(toolItem(
                R.drawable.ic_learning_translate,
                0xFF4D7CFE,
                getString(R.string.learning_home_tool_translate),
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")
        ), new LinearLayout.LayoutParams(0, -1, 1f));
        panel.addView(toolDivider(0xFF4D7CFE), new LinearLayout.LayoutParams(dp(1), dp(42)));

        panel.addView(toolItem(
                R.drawable.ic_learning_book,
                0xFF7D5CE8,
                getString(R.string.learning_home_tool_books),
                () -> AiScriptWebActivity.open(
                        requireContext(),
                        getString(R.string.learning_home_tool_books),
                        "https://z-library.im/booklist/3798447/a73105/%E5%AD%A6%E4%B8%AD%E6%96%87%E4%B9%A6%E7%B1%8D.html"
                )
        ), new LinearLayout.LayoutParams(0, -1, 1f));
        panel.addView(toolDivider(0xFF7D5CE8), new LinearLayout.LayoutParams(dp(1), dp(42)));

        panel.addView(toolItem(
                R.drawable.ic_learning_mic,
                0xFF18A18A,
                getString(R.string.learning_home_tool_partner),
                () -> openDirectory("prompts", getString(R.string.learning_home_tool_partner), "")
        ), new LinearLayout.LayoutParams(0, -1, 1f));
        panel.addView(toolDivider(0xFFED8A4A), new LinearLayout.LayoutParams(dp(1), dp(42)));

        panel.addView(toolItem(
                R.drawable.ic_learning_practice,
                0xFFED8A4A,
                getString(R.string.learning_home_tool_practice),
                () -> LearningCategoryActivity.open(requireContext())
        ), new LinearLayout.LayoutParams(0, -1, 1f));
        return panel;
    }

    private View toolItem(int iconRes, int accent, String title, Runnable click) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(6), dp(4), dp(6));
        item.setBackground(ripple(
                rounded(Color.TRANSPARENT, dp(16), Color.TRANSPARENT, 0),
                withAlpha(accent, 28),
                16
        ));
        bindClick(item, click);
        attachNativePressAnimator(item, 0f, -1f);

        FrameLayout iconBox = new FrameLayout(requireContext());
        iconBox.setBackground(rounded(withAlpha(accent, 28), dp(15),
                withAlpha(accent, 72), dp(1)));
        item.addView(iconBox, new LinearLayout.LayoutParams(dp(46), dp(46)));

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

    private View toolDivider(int accent) {
        View divider = new View(requireContext());
        divider.setBackgroundColor(withAlpha(accent, 30));
        return divider;
    }

    private void addSection(LinearLayout parent, String title, String subtitle,
                            String sectionType, CardSpec[] cards) {
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

        TextView moreView = text(getString(R.string.learning_home_more) + "  ›",
                13, 0xFF7C8496, true);
        moreView.setGravity(Gravity.CENTER);
        moreView.setPadding(dp(12), dp(8), dp(10), dp(8));
        moreView.setBackground(ripple(
                rounded(0x8FFFFFFF, dp(16), 0xB3FFFFFF, dp(1)),
                withAlpha(COLOR_BRAND, 26),
                16
        ));
        bindClick(moreView, () -> openDirectory(sectionType, title, ""));
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
                    card.setMinimumHeight(dp(cardSpec.level > 0 ? 108 : 96));
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
                gradientRounded(start, end, dp(RADIUS_CARD), Color.TRANSPARENT, 0),
                withAlpha(accent, 30),
                RADIUS_CARD
        ));
        bindClick(card, () -> onSmallCardClick(spec));
        applyColoredShadow(card, accent, 3f);
        attachNativePressAnimator(card, 3f, -2f);

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

        TextView title = text(spec.title, 15, COLOR_TEXT, true);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, 0, dp(44), 0);
        copy.addView(title, titleLp);

        TextView desc = text(spec.desc, 12, COLOR_SUB, false);
        desc.setMaxLines(2);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.setMargins(0, dp(5), dp(44), 0);
        copy.addView(desc, descLp);
    }

    private void addHskCardContent(FrameLayout card, CardSpec spec, int accent) {
        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleRowLp = new LinearLayout.LayoutParams(-1, -2);
        titleRowLp.setMargins(0, 0, 0, 0);
        copy.addView(titleRow, titleRowLp);

        TextView title = text(spec.title, 16, COLOR_TEXT, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView count = text(spec.desc, 11, withAlpha(accent, 220), true);
        titleRow.addView(count, new LinearLayout.LayoutParams(-2, -2));

        TextView progressText = text(
                getString(R.string.learning_home_learned_format, 0, spec.totalWords),
                11, COLOR_SUB, false);
        LinearLayout.LayoutParams progressTextLp = new LinearLayout.LayoutParams(-1, -2);
        progressTextLp.setMargins(0, dp(7), 0, 0);
        copy.addView(progressText, progressTextLp);

        LocalProgressBarView progressBar = new LocalProgressBarView(requireContext(), accent);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(4));
        barLp.setMargins(0, dp(7), 0, 0);
        copy.addView(progressBar, barLp);

        hskProgressViews.put(spec.id,
                new HskProgressBinding(progressText, progressBar, spec.totalWords));
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
        panel.setPadding(dp(16), getTopInset() + dp(14), dp(16), dp(16));
        panel.setBackground(drawerBackground());
        panel.setClickable(true);

        FrameLayout profileCard = new FrameLayout(requireContext());
        profileCard.setMinimumHeight(dp(106));
        profileCard.setPadding(dp(16), dp(16), dp(14), dp(16));
        profileCard.setContentDescription(getString(R.string.learning_home_drawer_profile_hint));
        profileCard.setBackground(ripple(
                rounded(0xFAFFFFFF, dp(24), 0xFFE8EBF3, dp(1)),
                0x14635BFF,
                24
        ));
        bindClick(profileCard, () -> {
            closeDrawer(false);
            openOwnProfile();
        });
        attachNativePressAnimator(profileCard, 1f, -0.35f);

        LinearLayout identityRow = new LinearLayout(requireContext());
        identityRow.setOrientation(LinearLayout.HORIZONTAL);
        identityRow.setGravity(Gravity.CENTER_VERTICAL);
        identityRow.setPadding(0, 0, dp(48), 0);

        drawerAvatarView = new AvatarView(requireContext());
        drawerAvatarView.setSize(62);
        identityRow.addView(drawerAvatarView, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout identityCopy = new LinearLayout(requireContext());
        identityCopy.setOrientation(LinearLayout.VERTICAL);
        identityCopy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams identityCopyLp = new LinearLayout.LayoutParams(0, -2, 1f);
        identityCopyLp.setMargins(dp(13), 0, 0, 0);
        identityRow.addView(identityCopy, identityCopyLp);

        drawerNameTv = text("", 17, COLOR_TEXT, true);
        drawerNameTv.setSingleLine(true);
        identityCopy.addView(drawerNameTv, new LinearLayout.LayoutParams(-1, -2));

        drawerAccountTv = text("", 12, COLOR_SUB, false);
        drawerAccountTv.setSingleLine(true);
        LinearLayout.LayoutParams accountLp = new LinearLayout.LayoutParams(-1, -2);
        accountLp.setMargins(0, dp(6), 0, 0);
        identityCopy.addView(drawerAccountTv, accountLp);

        TextView profileHint = text(getString(R.string.learning_home_drawer_profile_hint), 11, COLOR_BRAND, true);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(7), 0, 0);
        identityCopy.addView(profileHint, hintLp);

        profileCard.addView(identityRow, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));

        ImageView settings = new ImageView(requireContext());
        settings.setImageResource(R.drawable.ic_learning_drawer_settings);
        settings.setColorFilter(0xFF596174);
        settings.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setContentDescription(getString(R.string.learning_home_drawer_system_settings));
        settings.setBackground(ripple(
                rounded(0xFFF1F3F9, dp(14), Color.TRANSPARENT, 0),
                0x1F635BFF,
                14
        ));
        bindClick(settings, () -> {
            closeDrawer(false);
            openOptionalActivity("com.chat.uikit.setting.SettingActivity",
                    getString(R.string.learning_home_drawer_system_settings));
        });
        FrameLayout.LayoutParams settingsLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.END | Gravity.TOP);
        profileCard.addView(settings, settingsLp);

        LinearLayout.LayoutParams profileLp = new LinearLayout.LayoutParams(-1, -2);
        profileLp.setMargins(0, 0, 0, dp(16));
        panel.addView(profileCard, profileLp);

        LinearLayout menuGrid = new LinearLayout(requireContext());
        menuGrid.setOrientation(LinearLayout.VERTICAL);

        menuGrid.addView(drawerTileRow(
                drawerTile(R.drawable.ic_learning_drawer_contacts,
                        getString(R.string.learning_home_drawer_contacts), 0xFF4779E8,
                        this::openContactsPage),
                drawerTile(R.drawable.ic_learning_drawer_new_friends,
                        getString(R.string.learning_home_drawer_new_friends), 0xFF1A9B83,
                        () -> openOptionalActivity("com.chat.uikit.contacts.NewFriendsActivity",
                                getString(R.string.learning_home_drawer_new_friends)))
        ));

        menuGrid.addView(drawerTileRow(
                drawerTile(R.drawable.ic_learning_drawer_add_friend,
                        getString(R.string.learning_home_drawer_add_friend), 0xFFE98A37,
                        () -> openOptionalActivity("com.chat.uikit.search.AddFriendsActivity",
                                getString(R.string.learning_home_drawer_add_friend))),
                drawerTile(R.drawable.ic_learning_drawer_translate,
                        getString(R.string.learning_home_drawer_chat_translate), 0xFF6A5CE4,
                        this::openChatTranslateSettings)
        ));

        menuGrid.addView(drawerTileRow(
                drawerTile(R.drawable.ic_learning_drawer_speech,
                        getString(R.string.learning_home_drawer_speech_settings), 0xFF8B5AC8,
                        this::openSpeechSettings),
                drawerTile(R.drawable.ic_learning_drawer_script,
                        getString(R.string.learning_home_drawer_scripts), 0xFFD85682,
                        () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class)))
        ));

        ScrollView menuScroll = new ScrollView(requireContext());
        menuScroll.setFillViewport(true);
        menuScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        menuScroll.setVerticalScrollBarEnabled(false);
        menuScroll.addView(menuGrid, new ScrollView.LayoutParams(-1, -2));
        panel.addView(menuScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        refreshDrawerProfile();
        return panel;
    }

    private View drawerTileRow(View left, View right) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1f);
        leftLp.setMargins(0, 0, dp(5), dp(10));
        row.addView(left, leftLp);

        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -2, 1f);
        rightLp.setMargins(dp(5), 0, 0, dp(10));
        row.addView(right, rightLp);
        return row;
    }

    private View drawerTile(int iconRes, String label, int accent, Runnable click) {
        LinearLayout tile = new LinearLayout(requireContext());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.START);
        tile.setMinimumHeight(dp(101));
        tile.setPadding(dp(14), dp(13), dp(12), dp(12));
        tile.setContentDescription(label);
        tile.setBackground(ripple(
                rounded(0xF9FFFFFF, dp(20), 0xFFE9ECF3, dp(1)),
                withAlpha(accent, 24),
                20
        ));
        bindClick(tile, () -> {
            closeDrawer(false);
            if (click != null) click.run();
        });
        attachNativePressAnimator(tile, 0.6f, -0.25f);

        FrameLayout iconBox = new FrameLayout(requireContext());
        iconBox.setBackground(rounded(withAlpha(accent, 20), dp(14), Color.TRANSPARENT, 0));
        tile.addView(iconBox, new LinearLayout.LayoutParams(dp(42), dp(42)));

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        iconBox.addView(icon, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        TextView title = text(label, 13, COLOR_TEXT, true);
        title.setMaxLines(2);
        title.setGravity(Gravity.START);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(11), 0, 0);
        tile.addView(title, titleLp);
        return tile;
    }

    private void refreshDrawerProfile() {
        if (!isAdded() || drawerAvatarView == null || drawerNameTv == null || drawerAccountTv == null) {
            return;
        }
        UserInfoEntity user = WKConfig.getInstance().getUserInfo();
        String uid = firstNonEmpty(user.uid, WKConfig.getInstance().getUid());
        String displayName = firstNonEmpty(user.name, user.username,
                WKConfig.getInstance().getUserName(),
                getString(R.string.learning_home_drawer_default_name));
        drawerNameTv.setText(displayName);

        String shortNo = user.short_no == null ? "" : user.short_no.trim();
        if (TextUtils.isEmpty(shortNo)) {
            drawerAccountTv.setVisibility(View.GONE);
            drawerAccountTv.setText("");
        } else {
            drawerAccountTv.setVisibility(View.VISIBLE);
            drawerAccountTv.setText(getString(R.string.learning_home_drawer_account_format,
                    getAppLabel(), shortNo));
        }

        String avatar = user.avatar;
        if (TextUtils.isEmpty(avatar) && !TextUtils.isEmpty(uid)) {
            avatar = "users/" + uid + "/avatar";
        }
        drawerAvatarView.showAvatarUrl(avatar, "", displayName, uid);
    }

    private String getAppLabel() {
        try {
            CharSequence label = requireContext().getApplicationInfo()
                    .loadLabel(requireContext().getPackageManager());
            if (label != null && !TextUtils.isEmpty(label.toString().trim())) {
                return label.toString().trim();
            }
        } catch (Throwable ignored) {
        }
        return "Talkami";
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private void openOwnProfile() {
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            route.getMethod("open", Context.class).invoke(null, requireContext());
            return;
        } catch (Throwable ignored) {
        }
        openOptionalActivity("com.chat.uikit.user.MyInfoActivity",
                getString(R.string.learning_home_drawer_profile_hint));
    }

    private void openChatTranslateSettings() {
        try {
            Class<?> clazz = Class.forName("com.chat.translate.ui.TranslateSettingsActivity");
            Intent intent = new Intent(requireContext(), clazz);
            intent.putExtra("from", "learning_drawer");
            startActivity(intent);
        } catch (Throwable ignored) {
            Toast.makeText(requireContext(),
                    getString(R.string.learning_home_feature_unavailable,
                            getString(R.string.learning_home_drawer_chat_translate)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openContactsPage() {
        Activity activity = getActivity();
        if (activity == null) return;
        try {
            activity.getClass().getMethod("openContactsFromLearning").invoke(activity);
        } catch (Throwable ignored) {
            Toast.makeText(requireContext(),
                    getString(R.string.learning_home_contacts_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openOptionalActivity(String className, String label) {
        try {
            Class<?> clazz = Class.forName(className);
            startActivity(new Intent(requireContext(), clazz));
        } catch (Throwable ignored) {
            Toast.makeText(requireContext(),
                    getString(R.string.learning_home_feature_unavailable, label),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void onSmallCardClick(CardSpec spec) {
        if (spec == null || spec.id == null) return;
        String type = typeForCardId(spec.id);
        if (type == null) {
            Toast.makeText(requireContext(), getString(R.string.learning_home_coming_soon, spec.title), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), getString(R.string.learning_home_speech_plugin_missing), Toast.LENGTH_SHORT).show();
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
        if (drawerLayout == null || sideDrawerView == null
                || drawerLayout.isDrawerVisible(sideDrawerView)) return;
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, sideDrawerView);
        // 侧栏已随页面完成测量，立即显示，避免遮罩先出现、侧栏随后才滑出的延迟感。
        drawerLayout.openDrawer(sideDrawerView, false);
    }

    private void closeDrawer() {
        closeDrawer(true);
    }

    private void closeDrawer(boolean animate) {
        if (drawerLayout != null && sideDrawerView != null
                && drawerLayout.isDrawerVisible(sideDrawerView)) {
            drawerLayout.closeDrawer(sideDrawerView, animate);
        }
    }

    public boolean closeSideMenuIfOpen() {
        if (drawerLayout != null && sideDrawerView != null
                && drawerLayout.isDrawerVisible(sideDrawerView)) {
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
            case "tone": return "ài";
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
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFAFBFE, 0xFFF7F8FC}
        );
        drawable.setCornerRadius(0f);
        drawable.setStroke(dp(1), 0xFFE9ECF3);
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
        int screen = getResources().getDisplayMetrics().widthPixels;
        return Math.min(dp(336), (int) (screen * 0.86f));
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
            label.setText(label.getContext().getString(R.string.learning_home_learned_format, learned, total));
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
                    openDrawer(GravityCompat.START, false);
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
        HeroImageScrimView(Context context) {
            super(context);
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
        }
    }
}
