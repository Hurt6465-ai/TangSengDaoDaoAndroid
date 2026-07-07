package com.chat.learning;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;

/**
 * 独立学习插件首页。
 *
 * 本版改为真正原生层：
 * 1. DrawerLayout 左侧 Telegram 风格侧边栏；
 * 2. ViewPager2 横向切换：拼音 / 单词 / 口语 / 句型 / 语法 / 互动题；
 * 3. 每个栏目里面用 RecyclerView 渲染内容；
 * 4. 背单词从“单词”页进入独立全屏 Activity，使用竖向 ViewPager2 + 横向会/不会手势。
 *
 * 注意：广告暂时不加。海报背景仍建议“本地兜底 + 远程 JSON 覆盖”。
 */
public class LearningFragment extends Fragment {
    private static final int COLOR_BG = 0xFFF7F9FC;
    private static final int COLOR_BLUE = 0xFF1877F2;
    private static final int COLOR_TEXT_DARK = 0xFF111827;
    private static final int COLOR_TEXT_GRAY = 0xFF6B7280;
    private static final int COLOR_LINE = 0xFFE8EDF6;

    private static final String[] PAGE_TITLES = new String[]{"拼音", "单词", "口语", "句型", "语法", "互动题"};

    private DrawerLayout drawerLayout;
    private ViewPager2 pagePager;
    private LinearLayout tabContainer;
    private final TextView[] tabViews = new TextView[PAGE_TITLES.length];
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private long lastMenuClickTime = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();

        drawerLayout = new DrawerLayout(context);
        drawerLayout.setBackgroundColor(COLOR_BG);
        drawerLayout.setScrimColor(0x66000000);
        drawerLayout.setDrawerElevation(dp(10));
        drawerLayout.setFocusableInTouchMode(true);

        FrameLayout mainLayer = new FrameLayout(context);
        mainLayer.setBackgroundColor(COLOR_BG);
        drawerLayout.addView(mainLayer, new DrawerLayout.LayoutParams(-1, -1));

        LinearLayout main = new LinearLayout(context);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(COLOR_BG);
        mainLayer.addView(main, new FrameLayout.LayoutParams(-1, -1));

        main.addView(createHeader(context), new LinearLayout.LayoutParams(-1, -2));
        main.addView(createTabs(context), new LinearLayout.LayoutParams(-1, dp(52)));

        pagePager = new ViewPager2(context);
        pagePager.setId(View.generateViewId());
        pagePager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pagePager.setOffscreenPageLimit(2);
        pagePager.setAdapter(new LearningPagerAdapter(this));
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTabs(position);
            }
        };
        pagePager.registerOnPageChangeCallback(pageChangeCallback);
        main.addView(pagePager, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 左边缘提示条：真实抽屉手势由 DrawerLayout 处理，只是加一个可见入口。
        TextView edgeHandle = createEdgeHandle(context);
        FrameLayout.LayoutParams edgeLp = new FrameLayout.LayoutParams(dp(8), dp(78), Gravity.START | Gravity.CENTER_VERTICAL);
        edgeLp.setMargins(0, 0, 0, dp(40));
        mainLayer.addView(edgeHandle, edgeLp);
        edgeHandle.setOnClickListener(v -> openSideMenu());

        LinearLayout drawerPanel = createSideMenuPanel(context);
        DrawerLayout.LayoutParams panelLp = new DrawerLayout.LayoutParams(getPanelWidth(), -1);
        panelLp.gravity = GravityCompat.START;
        drawerLayout.addView(drawerPanel, panelLp);

        updateTabs(0);
        return drawerLayout;
    }

    private View createHeader(Context context) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(16), dp(14), dp(16), dp(10));

        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setGravity(Gravity.BOTTOM | Gravity.START);
        banner.setPadding(dp(18), dp(18), dp(18), dp(16));
        banner.setBackground(createBannerBg());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            banner.setElevation(dp(2));
        }

        TextView badge = new TextView(context);
        badge.setText("新手推荐");
        badge.setTextSize(12);
        badge.setTextColor(COLOR_BLUE);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(0xEEFFFFFF, dp(14), Color.TRANSPARENT, 0));
        banner.addView(badge, new LinearLayout.LayoutParams(dp(74), dp(28)));

        TextView title = new TextView(context);
        title.setText("中文零基础入门");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(18), 0, 0);
        banner.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(context);
        sub.setText("每天 10 分钟，跟语伴一起学拼音、单词和口语");
        sub.setTextColor(0xEFFFFFFF);
        sub.setTextSize(13);
        sub.setPadding(0, dp(5), 0, dp(12));
        banner.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bottom = new LinearLayout(context);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setOrientation(LinearLayout.HORIZONTAL);

        TextView price = new TextView(context);
        price.setText("免费试看");
        price.setTextSize(17);
        price.setTextColor(Color.WHITE);
        price.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.addView(price, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView start = new TextView(context);
        start.setText("开始学习");
        start.setTextSize(14);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setTextColor(COLOR_BLUE);
        start.setGravity(Gravity.CENTER);
        start.setBackground(rounded(Color.WHITE, dp(18), Color.TRANSPARENT, 0));
        bottom.addView(start, new LinearLayout.LayoutParams(dp(104), dp(38)));
        start.setOnClickListener(v -> {
            if (pagePager != null) pagePager.setCurrentItem(1, true);
        });
        banner.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        wrap.addView(banner, new LinearLayout.LayoutParams(-1, dp(198)));

        LinearLayout quickRow = new LinearLayout(context);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(82));
        rowLp.setMargins(0, dp(12), 0, 0);
        wrap.addView(quickRow, rowLp);

        quickRow.addView(quickEntry("AI 翻译", "DeepSeek / 886.best", () -> openSideMenu()), new LinearLayout.LayoutParams(0, -1, 1f));
        View gap = new View(context);
        quickRow.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        quickRow.addView(quickEntry("学习书籍", "书籍目录 / 离线包", this::showBookPlan), new LinearLayout.LayoutParams(0, -1, 1f));

        TextView hint = new TextView(context);
        hint.setText("左边缘右滑打开学习工具；左右滑切换栏目；单词页进入全屏背单词。");
        hint.setTextSize(11);
        hint.setTextColor(COLOR_TEXT_GRAY);
        hint.setPadding(dp(2), dp(9), dp(2), 0);
        wrap.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private View quickEntry(String title, String desc, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), 0, dp(14), 0);
        card.setBackground(rounded(Color.WHITE, dp(18), COLOR_LINE, 1));
        card.setOnClickListener(v -> runMenuClick(click));

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(COLOR_TEXT_DARK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView d = new TextView(requireContext());
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(COLOR_TEXT_GRAY);
        d.setPadding(0, dp(5), 0, 0);
        card.addView(d, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private View createTabs(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(Color.WHITE);

        tabContainer = new LinearLayout(context);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setGravity(Gravity.CENTER_VERTICAL);
        tabContainer.setPadding(dp(12), dp(7), dp(12), dp(7));
        scroll.addView(tabContainer, new HorizontalScrollView.LayoutParams(-2, -1));

        for (int i = 0; i < PAGE_TITLES.length; i++) {
            final int index = i;
            TextView tab = new TextView(context);
            tab.setText(PAGE_TITLES[i]);
            tab.setTextSize(15);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setOnClickListener(v -> {
                if (pagePager != null) pagePager.setCurrentItem(index, true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), -1);
            lp.setMargins(0, 0, dp(7), 0);
            tabContainer.addView(tab, lp);
            tabViews[i] = tab;
        }
        return scroll;
    }

    private TextView createEdgeHandle(Context context) {
        TextView view = new TextView(context);
        view.setText("›");
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.WHITE);
        view.setTextSize(20);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x551877F2);
        bg.setCornerRadii(new float[]{0, 0, dp(10), dp(10), dp(10), dp(10), 0, 0});
        view.setBackground(bg);
        view.setAlpha(0.72f);
        return view;
    }

    private LinearLayout createSideMenuPanel(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setBackground(createSideMenuBackground());
        panel.setPadding(dp(18), dp(44), dp(18), dp(20));

        TextView title = new TextView(context);
        title.setText("学习工具");
        title.setTextSize(26);
        title.setTextColor(COLOR_TEXT_DARK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(context);
        sub.setText("DeepSeek、886.best、千问、书籍、场景 prompt、语音朗读和脚本管理放这里，主页面只做学习内容。");
        sub.setTextSize(13);
        sub.setTextColor(COLOR_TEXT_GRAY);
        sub.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, dp(18));
        panel.addView(sub, subLp);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list, new ScrollView.LayoutParams(-1, -2));

        list.addView(menuCard("DeepSeek 翻译", "打开 DeepSeek 网页，适合中缅互译、口语解释和场景练习。", "进入", () -> openAiPage("DeepSeek", "https://chat.deepseek.com/")));
        list.addView(menuCard("886.best", "打开 https://886.best，作为你的国内学习 AI 入口。", "进入", () -> openAiPage("886.best", "https://886.best")));
        list.addView(menuCard("千问国内版", "打开 qianwen.com，适合国内用户和全能助手场景。", "进入", () -> openAiPage("千问国内版", "https://www.qianwen.com/")));
        list.addView(menuCard("Qwen 国际版", "打开 chat.qwen.ai，适合纯聊天和语音练习。", "进入", () -> openAiPage("Qwen 国际版", "https://chat.qwen.ai/")));
        list.addView(menuCard("学习书籍", "第一版先做静态书籍目录；后续书籍 JSON 可以远程更新。", "查看", this::showBookPlan));
        list.addView(menuCard("语音朗读", "打开 wkspeech：导入 TTS 源、选择发音人、设置语速音调。", "设置", this::openSpeechSettings));
        list.addView(menuCard("高频生活场景", "点餐、面试、打招呼、医院、机场等场景 prompt。", "选择", this::showPromptScenes));
        list.addView(menuCard("脚本管理", "新增、导入、在线安装、启用官方推荐脚本。", "管理", () -> startActivity(new Intent(requireContext(), ScriptManagerActivity.class))));

        TextView warn = new TextView(context);
        warn.setText("背景图策略：本地内置默认图兜底；正式运营图、价格、按钮文案走远程 home.json。远程失败仍显示本地学习页。");
        warn.setTextSize(12);
        warn.setTextColor(COLOR_TEXT_GRAY);
        warn.setPadding(dp(2), dp(8), dp(2), dp(14));
        list.addView(warn, new LinearLayout.LayoutParams(-1, -2));

        panel.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));
        return panel;
    }

    private View menuCard(String title, String desc, String action, Runnable click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(rounded(Color.WHITE, dp(18), COLOR_LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(11));
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(COLOR_TEXT_DARK);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView actionView = new TextView(requireContext());
        actionView.setText(action);
        actionView.setTextSize(12);
        actionView.setTypeface(Typeface.DEFAULT_BOLD);
        actionView.setTextColor(COLOR_BLUE);
        actionView.setGravity(Gravity.CENTER);
        actionView.setBackground(rounded(0xFFEAF2FF, dp(13), Color.TRANSPARENT, 0));
        row.addView(actionView, new LinearLayout.LayoutParams(dp(52), dp(28)));
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView descView = new TextView(requireContext());
        descView.setText(desc);
        descView.setTextSize(12);
        descView.setTextColor(COLOR_TEXT_GRAY);
        descView.setLineSpacing(dp(2), 1f);
        descView.setPadding(0, dp(7), 0, 0);
        card.addView(descView, new LinearLayout.LayoutParams(-1, -2));

        View.OnClickListener guardedClick = v -> runMenuClick(click);
        card.setOnClickListener(guardedClick);
        actionView.setOnClickListener(guardedClick);
        return card;
    }

    private void updateTabs(int selected) {
        for (int i = 0; i < tabViews.length; i++) {
            TextView tab = tabViews[i];
            if (tab == null) continue;
            boolean checked = i == selected;
            tab.setTextColor(checked ? Color.WHITE : COLOR_TEXT_GRAY);
            tab.setTypeface(Typeface.DEFAULT, checked ? Typeface.BOLD : Typeface.NORMAL);
            tab.setBackground(rounded(checked ? COLOR_BLUE : 0xFFF1F5F9, dp(18), Color.TRANSPARENT, 0));
        }
        if (tabContainer != null && selected >= 0 && selected < tabViews.length) {
            tabViews[selected].post(() -> {
                View parent = (View) tabContainer.getParent();
                if (parent instanceof HorizontalScrollView) {
                    HorizontalScrollView scroll = (HorizontalScrollView) parent;
                    int target = tabViews[selected].getLeft() - dp(24);
                    scroll.smoothScrollTo(Math.max(0, target), 0);
                }
            });
        }
    }

    private void openAiPage(String title, String url) {
        closeSideMenuIfOpen();
        AiScriptWebActivity.open(requireContext(), title, url);
    }

    private void showBookPlan() {
        closeSideMenuIfOpen();
        new AlertDialog.Builder(requireContext())
                .setTitle("学习书籍")
                .setMessage("第一版书籍建议放在 learning/data/books.json；封面图本地兜底，远程 home.json / books.json 可覆盖。后续要加新书，只改 JSON 和图片，不用重装 App。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void openSpeechSettings() {
        closeSideMenuIfOpen();
        try {
            Class<?> clazz = Class.forName("com.chat.speech.ui.SpeechSettingsActivity");
            startActivity(new Intent(requireContext(), clazz));
        } catch (Throwable e) {
            Toast.makeText(requireContext(), "语音插件未安装或 wkspeech 模块未打包", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPromptScenes() {
        final String[] names = new String[]{
                "日常打招呼", "点餐买东西", "求职面试", "医院看病", "机场过关", "租房沟通", "中文老师口语陪练", "中缅互译练习"
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

    private void runMenuClick(Runnable click) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastMenuClickTime < 650) return;
        lastMenuClickTime = now;
        if (click != null) click.run();
    }

    private void openSideMenu() {
        if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
    }

    public boolean closeSideMenuIfOpen() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    public boolean canGoBack() {
        return false;
    }

    public void goBack() {
        // 原生学习页不维护 WebView 后退栈，保留方法给 TabActivity 调用。
    }

    @Override
    public void onDestroyView() {
        if (pagePager != null && pageChangeCallback != null) {
            pagePager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        pagePager = null;
        pageChangeCallback = null;
        tabContainer = null;
        drawerLayout = null;
        super.onDestroyView();
    }

    private GradientDrawable createBannerBg() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4F8DFF, 0xFF7C5CFF, 0xFFFF8AAE});
        drawable.setCornerRadius(dp(26));
        return drawable;
    }

    private GradientDrawable createSideMenuBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFF5F8FF});
        drawable.setCornerRadii(new float[]{0, 0, dp(28), dp(28), dp(28), dp(28), 0, 0});
        return drawable;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int getPanelWidth() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int max = dp(324);
        int min = dp(286);
        int target = (int) (screenWidth * 0.84f);
        return Math.max(min, Math.min(max, target));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class LearningPagerAdapter extends FragmentStateAdapter {
        LearningPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return LearningPageFragment.newInstance(position);
        }

        @Override
        public int getItemCount() {
            return PAGE_TITLES.length;
        }
    }
}
