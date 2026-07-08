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
 * 学习首页：保留底部导航，固定内容分区，恢复 Drawer 手势。
 * UI 方向：图片 Banner + 清晰玻璃卡片 + 4 个工具入口 + 单词全屏入口。
 */
public class LearningFragment extends Fragment {
    private static final int COLOR_BG = 0xFFF3F7FD;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_SUB = 0xFF64748B;
    private static final int COLOR_LINE = 0x26FFFFFF;
    private static final int COLOR_BLUE = 0xFF1877F2;

    private DrawerLayout drawerLayout;
    private long lastCardClickTime = 0L;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        drawerLayout = new DrawerLayout(requireContext());
        drawerLayout.setBackgroundColor(COLOR_BG);
        drawerLayout.setScrimColor(0x70000000);
        drawerLayout.setDrawerElevation(dp(10));

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
        root.setPadding(0, 0, 0, dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(createHero(), new LinearLayout.LayoutParams(-1, dp(256)));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), 0);
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));

        addSectionTitle(content, "工具");
        content.addView(createToolsRow(), new LinearLayout.LayoutParams(-1, dp(96)));
        addSpace(content, 18);

        addSection(content, "拼音", null,
                new CardSpec[]{
                        new CardSpec("声母", "b p m f", "initials", 0xFFDBEAFE, 0xFFEFF6FF),
                        new CardSpec("韵母", "a o e i u", "finals", 0xFFFCE7F3, 0xFFFFF3F8),
                        new CardSpec("整体", "zhi chi shi", "whole", 0xFFDCFCE7, 0xFFF0FDF4),
                        new CardSpec("声调", "一二三四声", "tone", 0xFFFFEDD5, 0xFFFFF7ED)
                }, 2);

        addSection(content, "单词", "更多 ›",
                new CardSpec[]{
                        new CardSpec("HSK 1", "150 词", "hsk1", 0xFFDBEAFE, 0xFFEFF6FF),
                        new CardSpec("HSK 2", "300 词", "hsk2", 0xFFE9D5FF, 0xFFF5F3FF),
                        new CardSpec("HSK 3", "600 词", "hsk3", 0xFFDCFCE7, 0xFFF0FDF4)
                }, 3);

        addSection(content, "口语", "更多 ›",
                new CardSpec[]{
                        new CardSpec("打招呼", "日常开场", "speak_hello", 0xFFFCE7F3, 0xFFFFF3F8),
                        new CardSpec("点餐", "餐厅购物", "speak_food", 0xFFFFEDD5, 0xFFFFF7ED),
                        new CardSpec("求职", "面试工作", "speak_job", 0xFFDBEAFE, 0xFFEFF6FF)
                }, 3);

        addSection(content, "句型", "更多 ›",
                new CardSpec[]{
                        new CardSpec("我想…", "表达需求", "pattern_want", 0xFFDBEAFE, 0xFFEFF6FF),
                        new CardSpec("可以吗", "请求帮助", "pattern_can", 0xFFE9D5FF, 0xFFF5F3FF),
                        new CardSpec("怎么…", "询问方法", "pattern_how", 0xFFDCFCE7, 0xFFF0FDF4)
                }, 3);

        addSection(content, "语法", "更多 ›",
                new CardSpec[]{
                        new CardSpec("了", "完成/变化", "grammar_le", 0xFFFFEDD5, 0xFFFFF7ED),
                        new CardSpec("在", "正在进行", "grammar_zai", 0xFFDBEAFE, 0xFFEFF6FF),
                        new CardSpec("吗 / 呢", "疑问语气", "grammar_ma", 0xFFFCE7F3, 0xFFFFF3F8)
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

        View overlay = new View(requireContext());
        overlay.setBackground(gradient(0x14000000, 0xB0000000, 0, Color.TRANSPARENT, 0));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        View bottomGlass = new View(requireContext());
        bottomGlass.setBackground(gradient(0x08FFFFFF, 0x26FFFFFF, dp(26), 0x24FFFFFF, 1));
        FrameLayout.LayoutParams glassLp = new FrameLayout.LayoutParams(-1, dp(78), Gravity.BOTTOM);
        glassLp.setMargins(dp(14), 0, dp(14), dp(12));
        hero.addView(bottomGlass, glassLp);

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM | Gravity.START);
        copy.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = pillText("新手推荐", 12, Color.WHITE, 0x33FFFFFF, 0x40FFFFFF);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, dp(28));
        badgeLp.setMargins(0, 0, 0, dp(14));
        copy.addView(badge, badgeLp);

        TextView title = new TextView(requireContext());
        title.setText("中文零基础入门");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setShadowLayer(dp(8), 0, dp(2), 0x55000000);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("跟语伴一起学拼音、单词、口语和句型");
        sub.setTextSize(14);
        sub.setTextColor(0xF5FFFFFF);
        sub.setPadding(0, dp(8), 0, dp(14));
        sub.setShadowLayer(dp(6), 0, dp(1), 0x40000000);
        copy.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bottom = new LinearLayout(requireContext());
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        copy.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout priceBox = new LinearLayout(requireContext());
        priceBox.setOrientation(LinearLayout.VERTICAL);
        bottom.addView(priceBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView price = new TextView(requireContext());
        price.setText("免费试看");
        price.setTextSize(18);
        price.setTextColor(Color.WHITE);
        price.setTypeface(Typeface.DEFAULT_BOLD);
        priceBox.addView(price, new LinearLayout.LayoutParams(-1, -2));

        TextView tips = new TextView(requireContext());
        tips.setText("每天 10 分钟，适合零基础");
        tips.setTextSize(12);
        tips.setTextColor(0xD8FFFFFF);
        tips.setPadding(0, dp(4), 0, 0);
        priceBox.addView(tips, new LinearLayout.LayoutParams(-1, -2));

        TextView start = new TextView(requireContext());
        start.setText("开始学习");
        start.setTextSize(14);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setTextColor(COLOR_BLUE);
        start.setGravity(Gravity.CENTER);
        start.setBackground(rounded(Color.WHITE, dp(20), 0x40FFFFFF, 1));
        start.setOnClickListener(v -> Toast.makeText(requireContext(), "从拼音开始学习", Toast.LENGTH_SHORT).show());
        bottom.addView(start, new LinearLayout.LayoutParams(dp(110), dp(40)));

        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END | Gravity.TOP);
        menuLp.setMargins(0, dp(18), dp(12), 0);
        hero.addView(createMenuHandle(), menuLp);

        return hero;
    }

    private View createMenuHandle() {
        FrameLayout box = new FrameLayout(requireContext());
        box.setOnClickListener(v -> openDrawer());

        LinearLayout bars = new LinearLayout(requireContext());
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams barsLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        box.addView(bars, barsLp);

        bars.addView(menuBar(16), new LinearLayout.LayoutParams(dp(4), dp(16)));
        addHorizontalGap(bars, 4);
        bars.addView(menuBar(22), new LinearLayout.LayoutParams(dp(4), dp(22)));
        addHorizontalGap(bars, 4);
        bars.addView(menuBar(14), new LinearLayout.LayoutParams(dp(4), dp(14)));
        return box;
    }

    private View menuBar(int heightDp) {
        View view = new View(requireContext());
        view.setBackground(rounded(0xF0FFFFFF, dp(3), Color.TRANSPARENT, 0));
        return view;
    }

    private View createToolsRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(toolCard("🌐", "翻译", 0xFFDBEAFE, 0xFFEFF6FF,
                () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(row, 10);
        row.addView(toolCard("📚", "书籍", 0xFFFCE7F3, 0xFFFFF3F8,
                this::showBookPage), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(row, 10);
        row.addView(toolCard("🎙", "AI口语", 0xFFDCFCE7, 0xFFF0FDF4,
                this::showPromptScenes), new LinearLayout.LayoutParams(0, -1, 1f));
        addHorizontalGap(row, 10);
        row.addView(toolCard("✍", "练习题", 0xFFFFEDD5, 0xFFFFF7ED,
                () -> Toast.makeText(requireContext(), "练习题后续接入 quiz 数据", Toast.LENGTH_SHORT).show()), new LinearLayout.LayoutParams(0, -1, 1f));
        return row;
    }

    private View toolCard(String icon, String title, int startColor, int endColor, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(10), dp(6), dp(10));
        card.setBackground(glassCard(startColor, endColor, dp(22)));
        card.setElevation(dp(4));
        card.setOnClickListener(v -> runCardClick(click));

        TextView iconView = new TextView(requireContext());
        iconView.setText(icon);
        iconView.setTextSize(21);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(rounded(0xA6FFFFFF, dp(16), 0x2FFFFFFF, 1));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        card.addView(iconView, iconLp);

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(COLOR_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, 0);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView label = new TextView(requireContext());
        label.setText(title);
        label.setTextSize(21);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(COLOR_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        parent.addView(label, lp);
    }

    private void addSection(LinearLayout parent, String title, String more, CardSpec[] cards, int columns) {
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(0, dp(6), 0, dp(10));
        parent.addView(header, headerLp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(21);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (more != null) {
            TextView moreView = pillText(more, 13, COLOR_SUB, 0xAAFFFFFF, 0x16A0AEC0);
            moreView.setOnClickListener(v -> openMorePage(title));
            header.addView(moreView, new LinearLayout.LayoutParams(-2, dp(32)));
        }

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        parent.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        int index = 0;
        while (index < cards.length) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(columns == 3 ? 94 : 96));
            rowLp.setMargins(0, 0, 0, dp(10));
            grid.addView(row, rowLp);

            for (int i = 0; i < columns; i++) {
                if (index < cards.length) {
                    row.addView(smallCard(cards[index]), new LinearLayout.LayoutParams(0, -1, 1f));
                    index++;
                } else {
                    View empty = new View(requireContext());
                    row.addView(empty, new LinearLayout.LayoutParams(0, -1, 1f));
                }
                if (i < columns - 1) addHorizontalGap(row, 10);
            }
        }
        addSpace(parent, 8);
    }

    private View smallCard(CardSpec spec) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setBackground(glassCard(spec.startColor, spec.endColor, dp(22)));
        card.setElevation(dp(3));
        card.setOnClickListener(v -> runCardClick(() -> onSmallCardClick(spec)));

        LinearLayout left = new LinearLayout(requireContext());
        left.setOrientation(LinearLayout.VERTICAL);
        card.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView title = new TextView(requireContext());
        title.setText(spec.title);
        title.setTextSize(16);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(requireContext());
        desc.setText(spec.desc);
        desc.setTextSize(12);
        desc.setTextColor(COLOR_SUB);
        desc.setPadding(0, dp(5), 0, 0);
        left.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        TextView icon = new TextView(requireContext());
        icon.setText(iconFor(spec.id));
        icon.setTextSize(18);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(0xA6FFFFFF, dp(16), 0x30FFFFFF, 1));
        card.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        return card;
    }

    private View createSideDrawer() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(34), dp(18), dp(20));
        panel.setBackground(gradient(0xFFFFFFFF, 0xFFF4F8FF, 0, Color.TRANSPARENT, 0));
        panel.setClickable(true);

        TextView title = new TextView(requireContext());
        title.setText("学习工具");
        title.setTextSize(26);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("左边缘右滑可打开。这里放 AI、脚本、语音和扩展入口。主页只保留核心学习内容。\n");
        sub.setTextSize(13);
        sub.setTextColor(COLOR_SUB);
        sub.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, dp(10));
        panel.addView(sub, subLp);

        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        list.addView(drawerCard("DeepSeek 翻译", "中缅互译、语法解释、口语润色", () -> AiScriptWebActivity.open(requireContext(), "DeepSeek", "https://chat.deepseek.com/")));
        list.addView(drawerCard("886.best", "国内学习 AI 入口", () -> AiScriptWebActivity.open(requireContext(), "886.best", "https://886.best")));
        list.addView(drawerCard("千问国内版", "qianwen.com", () -> AiScriptWebActivity.open(requireContext(), "千问国内版", "https://www.qianwen.com/")));
        list.addView(drawerCard("Qwen 国际版", "chat.qwen.ai", () -> AiScriptWebActivity.open(requireContext(), "Qwen 国际版", "https://chat.qwen.ai/")));
        list.addView(drawerCard("语音朗读", "wkspeech 设置", this::openSpeechSettings));
        list.addView(drawerCard("高频生活场景", "复制场景 prompt", this::showPromptScenes));
        list.addView(drawerCard("脚本管理", "管理官方和用户脚本", () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return panel;
    }

    private View drawerCard(String title, String desc, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(rounded(Color.WHITE, dp(18), 0x10A0AEC0, 1));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        card.setOnClickListener(v -> {
            closeDrawer();
            runCardClick(click);
        });

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(COLOR_TEXT);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView d = new TextView(requireContext());
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(COLOR_SUB);
        d.setPadding(0, dp(6), 0, 0);
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
            Toast.makeText(requireContext(), spec.title + " 学习内容后续接 JSON", Toast.LENGTH_SHORT).show();
        }
    }

    private String iconFor(String id) {
        if (id == null) return "✦";
        if (id.startsWith("hsk")) return "词";
        if (id.startsWith("speak")) return "说";
        if (id.startsWith("pattern")) return "句";
        if (id.startsWith("grammar")) return "法";
        if ("initials".equals(id)) return "声";
        if ("finals".equals(id)) return "韵";
        if ("whole".equals(id)) return "拼";
        if ("tone".equals(id)) return "调";
        return "✦";
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
            Toast.makeText(requireContext(), section + " 更多内容后续接入", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBookPage() {
        Toast.makeText(requireContext(), "学习书籍后续接 books.json", Toast.LENGTH_SHORT).show();
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
        final String[] names = new String[]{"日常打招呼", "点餐买东西", "求职面试", "医院看病", "机场过关", "租房沟通", "中文老师口语陪练", "中缅互译练习"};
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
            Toast.makeText(requireContext(), "已复制场景 prompt", Toast.LENGTH_LONG).show();
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

    private GradientDrawable glassCard(int start, int end, float radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), 0x66FFFFFF);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
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

    private TextView pillText(String text, int textSizeSp, int textColor, int bgColor, int strokeColor) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(textSizeSp);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(rounded(bgColor, dp(16), strokeColor, 1));
        return view;
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
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (screenWidth * 0.82f);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class CardSpec {
        final String title;
        final String desc;
        final String id;
        final int startColor;
        final int endColor;

        CardSpec(String title, String desc, String id, int startColor, int endColor) {
            this.title = title;
            this.desc = desc;
            this.id = id;
            this.startColor = startColor;
            this.endColor = endColor;
        }
    }
}
