package com.chat.learning;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;


/**
 * 学习首页：贴吧式双层结构 + 固定背景层 + 全宽内容层。
 */
public class LearningFragment extends Fragment {
    private static final int COLOR_PAGE = 0xFFF3F6FB;
    private static final int COLOR_GLASS = 0xF4FFFFFF;
    private static final int COLOR_TEXT = 0xFF172033;
    private static final int COLOR_SUB = 0xFF778197;
    private static final int COLOR_BRAND = 0xFF635BFF;

    private WideEdgeDrawerLayout drawerLayout;
    private View sideDrawerView;
    private long lastCardClickTime = 0L;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        drawerLayout = new WideEdgeDrawerLayout(requireContext());
        drawerLayout.setBackgroundColor(COLOR_PAGE);
        drawerLayout.setScrimColor(0x3D12182A);
        drawerLayout.setDrawerElevation(dp(18));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);

        View main = createMainPage();
        drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));

        sideDrawerView = createSideDrawer();
        DrawerLayout.LayoutParams drawerLp = new DrawerLayout.LayoutParams(getDrawerWidth(), -1);
        drawerLp.gravity = GravityCompat.START;
        drawerLayout.addView(sideDrawerView, drawerLp);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, sideDrawerView);

        int edgeWidth = Math.max(dp(160),
                (int) (getResources().getDisplayMetrics().widthPixels * 0.38f));
        drawerLayout.setEdgeSwipeWidth(edgeWidth);
        return drawerLayout;
    }

    private View createMainPage() {
        FrameLayout page = new FrameLayout(requireContext());
        page.setBackgroundColor(COLOR_PAGE);

        LearningBackdropView backdrop = new LearningBackdropView(requireContext());
        page.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        int heroHeight = dp(302);
        View hero = createHero();
        page.addView(hero, new FrameLayout.LayoutParams(-1, heroHeight));

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        page.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(0, heroHeight - dp(44), 0, dp(116));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout sheet = createContentSheet();
        root.addView(sheet, new LinearLayout.LayoutParams(-1, -2));

        // 菜单按钮必须位于 ScrollView 之上，否则透明滚动层会截走点击事件。
        View menuButton = createMenuHandle();
        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(
                dp(56), dp(56), Gravity.TOP | Gravity.END
        );
        menuLp.setMargins(0, getTopInset() + dp(6), dp(10), 0);
        menuButton.setElevation(dp(32));
        page.addView(menuButton, menuLp);
        menuButton.bringToFront();

        // 背景层固定不动，只有整屏内容层随手势向上覆盖。
        return page;
    }

    private LinearLayout createContentSheet() {
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(20), dp(18), dp(30));
        sheet.setBackground(topSheetDrawable());
        sheet.setElevation(dp(12));
        sheet.setClipToOutline(true);


        LinearLayout quickHeader = new LinearLayout(requireContext());
        quickHeader.setOrientation(LinearLayout.VERTICAL);
        quickHeader.setPadding(dp(2), 0, dp(2), 0);
        sheet.addView(quickHeader, new LinearLayout.LayoutParams(-1, -2));

        TextView quickTitle = text("快捷工具", 20, COLOR_TEXT, true);
        quickHeader.addView(quickTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView quickSub = text("翻译、阅读与口语练习", 12, COLOR_SUB, false);
        LinearLayout.LayoutParams quickSubLp = new LinearLayout.LayoutParams(-1, -2);
        quickSubLp.setMargins(0, dp(5), 0, dp(14));
        quickHeader.addView(quickSub, quickSubLp);

        sheet.addView(createToolsPanel(), new LinearLayout.LayoutParams(-1, dp(100)));
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
                        new CardSpec("HSK 1", "150 词", "hsk1"),
                        new CardSpec("HSK 2", "300 词", "hsk2"),
                        new CardSpec("HSK 3", "600 词", "hsk3"),
                        new CardSpec("HSK 4", "1200 词", "hsk4")
                });

        addSection(sheet, "口语", "把中文真正说出来", "更多",
                new CardSpec[]{
                        new CardSpec("打招呼", "日常开场", "speak_hello"),
                        new CardSpec("点餐", "餐厅购物", "speak_food"),
                        new CardSpec("求职", "面试工作", "speak_job"),
                        new CardSpec("购物", "买单砍价", "speak_shop")
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

    private View createHero() {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setClipChildren(false);
        hero.setClipToPadding(false);

        ImageView image = new ImageView(requireContext());
        image.setImageResource(R.drawable.learning_home_banner_default);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(image, new FrameLayout.LayoutParams(-1, -1));

        HeroImageScrimView scrim = new HeroImageScrimView(requireContext());
        hero.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        // 底部说明文字上移，避免贴近上浮内容层。
        content.setPadding(dp(20), getTopInset() + dp(16), dp(20), dp(116));
        hero.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topRow = new LinearLayout(requireContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout heading = new LinearLayout(requireContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView pageTitle = text("学习", 28, Color.WHITE, true);
        heading.addView(pageTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView greeting = text("沉浸式中文学习空间", 12, 0xE6F5F8FF, false);
        LinearLayout.LayoutParams greetingLp = new LinearLayout.LayoutParams(-1, -2);
        greetingLp.setMargins(0, dp(5), 0, 0);
        heading.addView(greeting, greetingLp);

        View spacer = new View(requireContext());
        content.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView subtitle = text("从拼音、单词到口语，按自己的节奏慢慢提升。", 14, 0xE9F8FAFF, false);
        subtitle.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, 0, dp(48), 0);
        content.addView(subtitle, subLp);

        return hero;
    }

    private View createMenuHandle() {
        MenuHandleView button = new MenuHandleView(requireContext());
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("打开学习侧边栏");
        button.setOnClickListener(v -> openDrawer());
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // 顶部常被 ViewPager、ScrollView 或宿主页抢事件，按下后立即锁定给按钮。
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (event.getX() >= 0 && event.getX() <= v.getWidth()
                            && event.getY() >= 0 && event.getY() <= v.getHeight()) {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        });
        return button;
    }

    private View createToolsPanel() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(7), dp(8), dp(7), dp(8));
        panel.setBackground(gradientRounded(0xEFFFFFFF, 0xDFFFFFFF, dp(22), 0xFFFFFFFF, dp(1)));
        panel.setElevation(dp(5));

        panel.addView(toolItem(R.drawable.ic_learning_translate, 0xFF4D7CFE, "AI翻译",
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")),
                new LinearLayout.LayoutParams(0, -1, 1f));
        panel.addView(toolDivider(), new LinearLayout.LayoutParams(dp(1), dp(36)));

        panel.addView(toolItem(R.drawable.ic_learning_book, 0xFF8659F5, "电子书",
                () -> openDirectory("books", "电子书", "")),
                new LinearLayout.LayoutParams(0, -1, 1f));
        panel.addView(toolDivider(), new LinearLayout.LayoutParams(dp(1), dp(36)));

        panel.addView(toolItem(R.drawable.ic_learning_mic, 0xFF11A78E, "口语伴",
                () -> openDirectory("prompts", "口语 Prompt", "")),
                new LinearLayout.LayoutParams(0, -1, 1f));
        panel.addView(toolDivider(), new LinearLayout.LayoutParams(dp(1), dp(36)));

        panel.addView(toolItem(R.drawable.ic_learning_practice, 0xFFF08A4B, "练习题",
                () -> openDirectory("quiz", "练习题", "")),
                new LinearLayout.LayoutParams(0, -1, 1f));
        return panel;
    }

    private View toolDivider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(0x247A8498);
        return divider;
    }

    private View toolItem(int iconRes, int accentColor, String title, Runnable click) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(3), dp(5), dp(3), dp(5));
        item.setBackground(rounded(Color.TRANSPARENT, dp(16), Color.TRANSPARENT, 0));
        bindClick(item, click);
        attachNativePressAnimator(item, 0, 1.5f);

        FrameLayout iconBox = new FrameLayout(requireContext());
        iconBox.setBackground(radialGlow(accentColor));
        item.addView(iconBox, new LinearLayout.LayoutParams(dp(42), dp(42)));

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(accentColor);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));

        TextView label = text(title, 12, COLOR_TEXT, true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.setMargins(0, dp(6), 0, 0);
        item.addView(label, labelLp);
        return item;
    }

    private void addSection(LinearLayout parent, String title, String subtitle, String more, CardSpec[] cards) {
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(dp(2), dp(8), dp(2), dp(13));
        parent.addView(header, headerLp);

        LinearLayout titles = new LinearLayout(requireContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView titleView = text(title, 20, COLOR_TEXT, true);
        titles.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitleView = text(subtitle, 12, COLOR_SUB, false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.setMargins(0, dp(4), 0, 0);
        titles.addView(subtitleView, subtitleLp);

        if (more != null) {
            TextView moreView = text(more + "  ›", 13, 0xFF7A8498, true);
            moreView.setGravity(Gravity.CENTER);
            moreView.setPadding(dp(10), dp(7), dp(8), dp(7));
            moreView.setBackground(rounded(0x8FFFFFFF, dp(15), 0xB3FFFFFF, dp(1)));
            bindClick(moreView, () -> openMorePage(title));
            header.addView(moreView, new LinearLayout.LayoutParams(-2, -2));
        }

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        parent.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        int index = 0;
        while (index < cards.length) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.setMargins(0, 0, 0, dp(12));
            grid.addView(row, rowLp);

            for (int column = 0; column < 2; column++) {
                if (index < cards.length) {
                    row.addView(smallCard(cards[index]), new LinearLayout.LayoutParams(0, dp(88), 1f));
                    index++;
                } else {
                    row.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, dp(1), 1f));
                }
                if (column == 0) addHorizontalGap(row, 12);
            }
        }
        addSpace(parent, 10);
    }

    private View smallCard(CardSpec spec) {
        int accent = accentForCard(spec.id);
        int tintStart = tintForCard(spec.id, true);
        int tintEnd = tintForCard(spec.id, false);

        FrameLayout card = new FrameLayout(requireContext());
        card.setPadding(dp(16), dp(13), dp(13), dp(13));
        card.setBackground(gradientRounded(tintStart, tintEnd, dp(20), 0xD9FFFFFF, dp(1)));
        card.setElevation(dp(3));
        card.setClipToOutline(true);
        bindClick(card, () -> onSmallCardClick(spec));
        attachNativePressAnimator(card, 3, 2.5f);

        TextView ghost = text(symbolForCard(spec), 38, withAlpha(accent, 34), true);
        ghost.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ghost.setIncludeFontPadding(false);
        FrameLayout.LayoutParams ghostLp = new FrameLayout.LayoutParams(dp(68), -1, Gravity.END | Gravity.CENTER_VERTICAL);
        ghostLp.setMargins(0, 0, dp(1), 0);
        card.addView(ghost, ghostLp);

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        View accentDot = new View(requireContext());
        accentDot.setBackground(rounded(accent, dp(3), Color.TRANSPARENT, 0));
        copy.addView(accentDot, new LinearLayout.LayoutParams(dp(18), dp(5)));

        TextView title = text(spec.title, 15, COLOR_TEXT, true);
        title.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(8), dp(42), 0);
        copy.addView(title, titleLp);

        TextView desc = text(spec.desc, 12, COLOR_SUB, false);
        desc.setSingleLine(true);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.setMargins(0, dp(5), dp(42), 0);
        copy.addView(desc, descLp);
        return card;
    }

    private View createSideDrawer() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), getTopInset() + dp(18), dp(18), dp(20));
        panel.setBackground(drawerBackground());
        panel.setClickable(true);
        panel.setElevation(dp(18));

        TextView kicker = text("LEARNING SPACE", 10, 0xFF8177E7, true);
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
        focusCard.setBackground(gradientRounded(0x6DDED9FF, 0x45D6EEFF, dp(16), 0xB3FFFFFF, dp(1)));

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
        list.addView(drawerCard("语音设置", "WKSpeech 引擎配置", 0xFF12A78E, this::openSpeechSettings));
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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(30));
        lp.setMargins(dp(2), dp(4), 0, dp(7));
        list.addView(view, lp);
    }

    private View drawerCard(String title, String desc, int accentColor, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(13), dp(12), dp(13));
        card.setBackground(gradientRounded(0xDFFFFFFF, 0xBFFFFFFF, dp(14), 0xD9FFFFFF, dp(1)));
        bindClick(card, () -> {
            closeDrawer();
            if (click != null) click.run();
        });
        attachNativePressAnimator(card, 1, 2);

        FrameLayout dotBox = new FrameLayout(requireContext());
        dotBox.setBackground(radialGlow(accentColor));
        LinearLayout.LayoutParams dotBoxLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        dotBoxLp.setMargins(0, 0, dp(12), 0);
        card.addView(dotBox, dotBoxLp);

        View dot = new View(requireContext());
        dot.setBackground(rounded(accentColor, dp(5), Color.TRANSPARENT, 0));
        dotBox.addView(dot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));

        LinearLayout textBox = new LinearLayout(requireContext());
        textBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView t = text(title, 14, COLOR_TEXT, true);
        textBox.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView d = text(desc, 12, COLOR_SUB, false);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, -2);
        dLp.setMargins(0, dp(4), 0, 0);
        textBox.addView(d, dLp);

        TextView arrow = text("›", 24, 0xFFB0B6C4, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(34)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        return card;
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

    private int accentForCard(String id) {
        if (id == null) return COLOR_BRAND;
        if (id.startsWith("hsk")) return 0xFF12A78E;
        if (id.startsWith("speak")) return 0xFFF07A55;
        if (id.startsWith("pattern")) return 0xFF4285E8;
        if (id.startsWith("grammar")) return 0xFF8A5AF4;
        return 0xFF6659E8;
    }

    private int tintForCard(String id, boolean start) {
        if (id == null) return start ? 0xFFF4F2FF : 0xFFFAFAFF;
        if (id.startsWith("hsk")) return start ? 0xFFE9FAF7 : 0xFFF7FFFD;
        if (id.startsWith("speak")) return start ? 0xFFFFEFE8 : 0xFFFFFAF7;
        if (id.startsWith("pattern")) return start ? 0xFFEAF3FF : 0xFFF8FBFF;
        if (id.startsWith("grammar")) return start ? 0xFFF2ECFF : 0xFFFBF9FF;
        return start ? 0xFFF0EDFF : 0xFFFAF9FF;
    }

    private String symbolForCard(CardSpec spec) {
        if (spec == null || spec.id == null) return "·";
        switch (spec.id) {
            case "initials": return "b";
            case "finals": return "a";
            case "whole": return "zhi";
            case "tone": return "ˇ";
            case "hsk1": return "1";
            case "hsk2": return "2";
            case "hsk3": return "3";
            case "hsk4": return "4";
            case "speak_hello": return "嗨";
            case "speak_food": return "吃";
            case "speak_job": return "职";
            case "speak_shop": return "买";
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
        if ("initials".equals(id) || "finals".equals(id) || "whole".equals(id)
                || "tone".equals(id)) return "pinyin";
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
        LearningDirectoryActivity.open(requireContext(), type, title, parentId == null ? "" : parentId);
    }

    private void openSpeechSettings() {
        try {
            Class<?> clazz = Class.forName("com.chat.speech.ui.SpeechSettingsActivity");
            startActivity(new Intent(requireContext(), clazz));
        } catch (Throwable e) {
            Toast.makeText(requireContext(), "语音插件未安装", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDrawer() {
        if (drawerLayout == null || sideDrawerView == null) return;

        // 直接使用抽屉 View，避免部分 RTL/宿主布局下 START 重力匹配失败。
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, sideDrawerView);
        sideDrawerView.setVisibility(View.VISIBLE);
        sideDrawerView.bringToFront();
        drawerLayout.post(() -> {
            if (!isAdded() || drawerLayout == null || sideDrawerView == null) return;
            drawerLayout.openDrawer(sideDrawerView, true);
        });
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
        StateListAnimator animator = new StateListAnimator();

        ObjectAnimator pressed = ObjectAnimator.ofFloat(view, "translationZ", dp(pressedTranslationZDp));
        pressed.setDuration(90);
        ObjectAnimator normal = ObjectAnimator.ofFloat(view, "translationZ", 0f);
        normal.setDuration(130);

        animator.addState(new int[]{android.R.attr.state_pressed}, pressed);
        animator.addState(new int[]{}, normal);
        view.setStateListAnimator(animator);
    }

    private GradientDrawable topSheetDrawable() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF7FFFFFF, COLOR_GLASS}
        );
        float r = dp(30);
        drawable.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        drawable.setStroke(dp(1), 0xEBFFFFFF);
        return drawable;
    }

    private GradientDrawable drawerBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFF8F7FF, 0xFFF2F7FF, 0xFFFBFCFF}
        );
        drawable.setCornerRadius(0);
        drawable.setStroke(dp(1), 0xF2FFFFFF);
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
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setGradientRadius(dp(26));
        drawable.setColors(new int[]{withAlpha(color, 54), withAlpha(color, 10)});
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View spacer = new View(requireContext());
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        View spacer = new View(requireContext());
        parent.addView(spacer, new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private int getDrawerWidth() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
    }

    private int getTopInset() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) return getResources().getDimensionPixelSize(resId);
        return dp(24);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * DrawerLayout 默认只允许从很窄的屏幕边缘开始滑动，且依赖内部字段反射的做法
     * 在不同 AndroidX 版本上容易失效。这里直接识别左侧宽区域的横向手势。
     */
    private static class WideEdgeDrawerLayout extends DrawerLayout {
        private final int touchSlop;
        private int edgeSwipeWidth;
        private float downX;
        private float downY;
        private boolean trackingEdgeGesture;

        WideEdgeDrawerLayout(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            edgeSwipeWidth = touchSlop * 3;
        }

        void setEdgeSwipeWidth(int widthPx) {
            edgeSwipeWidth = Math.max(widthPx, touchSlop * 3);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            final int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                trackingEdgeGesture = downX <= edgeSwipeWidth
                        && !isDrawerOpen(GravityCompat.START);
            } else if (action == MotionEvent.ACTION_MOVE && trackingEdgeGesture) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (dx > touchSlop && dx > Math.abs(dy) * 1.15f) {
                    trackingEdgeGesture = false;
                    openDrawer(GravityCompat.START, true);
                    return true;
                }
                if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    trackingEdgeGesture = false;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                trackingEdgeGesture = false;
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            // 横向手势从左侧触发区开始时，允许父布局继续判断是否打开抽屉。
            if (trackingEdgeGesture && disallowIntercept) return;
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                trackingEdgeGesture = false;
            }
            return super.onTouchEvent(event);
        }
    }

    /**
     * 无圆形底托的三横线菜单。三条线右侧对齐，顶部最长、底部最短。
     * 整个 View 都是 56dp 触摸区，绘制区域仍保持轻巧。
     */
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
            float right = getWidth() * 0.68f;
            float centerY = getHeight() * 0.50f;
            float gap = 6.2f * density;

            drawLine(canvas, right, centerY - gap, 22f * density);
            drawLine(canvas, right, centerY, 16f * density);
            drawLine(canvas, right, centerY + gap, 10f * density);
        }

        private void drawLine(Canvas canvas, float right, float y, float width) {
            canvas.drawLine(right - width, y, right, y, paint);
        }
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

    private static class LearningBackdropView extends View {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blobOne = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blobTwo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blobThree = new Paint(Paint.ANTI_ALIAS_FLAG);

        LearningBackdropView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            basePaint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{0xFFF4F1FF, 0xFFF0F8FF, 0xFFFAFCFF},
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            blobOne.setShader(new RadialGradient(w * 0.88f, h * 0.14f, w * 0.50f,
                    0x328AA8FF, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            blobTwo.setShader(new RadialGradient(w * 0.10f, h * 0.38f, w * 0.43f,
                    0x22CB9CFF, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            blobThree.setShader(new RadialGradient(w * 0.80f, h * 0.68f, w * 0.52f,
                    0x1CA8E6D5, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, getWidth(), getHeight(), basePaint);
            canvas.drawCircle(getWidth() * 0.88f, getHeight() * 0.14f, getWidth() * 0.50f, blobOne);
            canvas.drawCircle(getWidth() * 0.10f, getHeight() * 0.38f, getWidth() * 0.43f, blobTwo);
            canvas.drawCircle(getWidth() * 0.80f, getHeight() * 0.68f, getWidth() * 0.52f, blobThree);
        }
    }

    private static class HeroImageScrimView extends View {
        private final Paint topShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint violetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bluePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ring = new RectF();

        HeroImageScrimView(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(context.getResources().getDisplayMetrics().density);
            ringPaint.setColor(0x2CFFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            topShadePaint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0x2E0B1528, 0x120D1B2C, 0x00000000},
                    new float[]{0f, 0.36f, 0.60f}, Shader.TileMode.CLAMP));
            bottomShadePaint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0x00000000, 0x552A4660, COLOR_PAGE},
                    new float[]{0.34f, 0.76f, 1f}, Shader.TileMode.CLAMP));
            violetPaint.setShader(new RadialGradient(w * 0.80f, h * 0.20f, w * 0.38f,
                    0x2B9B8DFF, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            bluePaint.setShader(new RadialGradient(w * 0.10f, h * 0.68f, w * 0.45f,
                    0x215FD1FF, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, getWidth(), getHeight(), topShadePaint);
            canvas.drawCircle(getWidth() * 0.80f, getHeight() * 0.20f, getWidth() * 0.38f, violetPaint);
            canvas.drawCircle(getWidth() * 0.10f, getHeight() * 0.68f, getWidth() * 0.45f, bluePaint);
            canvas.drawRect(0, 0, getWidth(), getHeight(), bottomShadePaint);

            float size = Math.min(getWidth(), getHeight()) * 0.32f;
            ring.set(getWidth() - size * 1.1f, size * 0.02f,
                    getWidth() + size * 0.10f, size * 1.22f);
            canvas.drawOval(ring, ringPaint);
            ring.inset(size * 0.16f, size * 0.16f);
            canvas.drawOval(ring, ringPaint);
        }
    }
}
