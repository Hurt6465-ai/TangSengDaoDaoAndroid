package com.chat.learning;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.learning.ui.LearningPageFragment;
import com.chat.userscript.AiScriptWebActivity;
import com.chat.userscript.ScriptManagerActivity;

import java.io.File;

/**
 * 独立学习 Activity。
 *
 * 这版不再把学习首页塞在底部导航 Fragment 里：DrawerLayout 可以覆盖全屏，
 * 不会和底部导航产生层级/返回栈/重建问题。
 */
public class LearningActivity extends AppCompatActivity {
    private static final int COLOR_BG = 0xFFF6F8FC;
    private static final int COLOR_BLUE = 0xFF1877F2;
    private static final int COLOR_TEXT_DARK = 0xFF111827;
    private static final int COLOR_TEXT_GRAY = 0xFF6B7280;
    private static final int COLOR_LINE = 0xFFE8EDF6;
    private static final String[] PAGE_TITLES = new String[]{"拼音", "单词", "口语", "句型", "语法", "互动题"};

    private DrawerLayout drawerLayout;
    private ViewPager2 pagePager;
    private LinearLayout tabContainer;
    private final TextView[] tabViews = new TextView[PAGE_TITLES.length];
    private long lastMenuClickTime;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupImmersiveWindow();

        drawerLayout = new DrawerLayout(this);
        drawerLayout.setBackgroundColor(COLOR_BG);
        drawerLayout.setScrimColor(0x77000000);
        drawerLayout.setDrawerElevation(dp(10));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START);
            }
        });

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(COLOR_BG);
        drawerLayout.addView(main, new DrawerLayout.LayoutParams(-1, -1));

        main.addView(createHeader(), new LinearLayout.LayoutParams(-1, dp(278)));
        main.addView(createTabs(), new LinearLayout.LayoutParams(-1, dp(54)));

        pagePager = new ViewPager2(this);
        pagePager.setId(View.generateViewId());
        pagePager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pagePager.setOffscreenPageLimit(2);
        pagePager.setAdapter(new FragmentStateAdapter(this) {
            @Override
            public Fragment createFragment(int position) {
                return LearningPageFragment.newInstance(position);
            }

            @Override
            public int getItemCount() {
                return PAGE_TITLES.length;
            }
        });
        pagePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTabs(position);
            }
        });
        main.addView(pagePager, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout drawerPanel = createSideMenuPanel();
        DrawerLayout.LayoutParams panelLp = new DrawerLayout.LayoutParams(getPanelWidth(), -1);
        panelLp.gravity = GravityCompat.START;
        drawerLayout.addView(drawerPanel, panelLp);

        setContentView(drawerLayout);
        updateTabs(0);
    }

    private void setupImmersiveWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    private View createHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setBackgroundColor(0xFFDCEBFF);

        ImageView banner = new ImageView(this);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        File remoteBanner = getRemoteBannerFile();
        if (remoteBanner.exists() && remoteBanner.length() > 0) {
            banner.setImageURI(Uri.fromFile(remoteBanner));
        } else {
            banner.setImageResource(R.drawable.learning_home_banner_default);
        }
        header.addView(banner, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(createHeaderScrim());
        header.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        TextView close = circleButton("‹");
        close.setTextSize(32);
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.START | Gravity.TOP);
        closeLp.setMargins(dp(12), getStatusBarInset() + dp(8), 0, 0);
        header.addView(close, closeLp);

        TextView more = circleButton("⋯");
        more.setTextSize(28);
        more.setOnClickListener(v -> openDrawer());
        FrameLayout.LayoutParams moreLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.END | Gravity.TOP);
        moreLp.setMargins(0, getStatusBarInset() + dp(8), dp(12), 0);
        header.addView(more, moreLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM | Gravity.START);
        copy.setPadding(dp(20), 0, dp(20), dp(22));
        header.addView(copy, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = new TextView(this);
        badge.setText("新手推荐");
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(COLOR_BLUE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundRect(0xEEFFFFFF, dp(16), 0, 0));
        copy.addView(badge, new LinearLayout.LayoutParams(dp(78), dp(30)));

        TextView title = new TextView(this);
        title.setText("中文零基础入门");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, dp(16), 0, 0);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("每天 10 分钟，跟语伴一起学拼音、单词和口语");
        sub.setTextSize(14);
        sub.setTextColor(0xEEFFFFFF);
        sub.setPadding(0, dp(6), 0, dp(14));
        copy.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        copy.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        TextView price = new TextView(this);
        price.setText("免费试看");
        price.setTextColor(Color.WHITE);
        price.setTextSize(18);
        price.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.addView(price, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView start = new TextView(this);
        start.setText("开始学习");
        start.setTextColor(COLOR_BLUE);
        start.setTextSize(14);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setGravity(Gravity.CENTER);
        start.setBackground(roundRect(Color.WHITE, dp(18), 0, 0));
        start.setOnClickListener(v -> pagePager.setCurrentItem(1, true));
        bottom.addView(start, new LinearLayout.LayoutParams(dp(104), dp(38)));

        return header;
    }

    private TextView circleButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setBackground(roundRect(0x44000000, dp(21), 0x22FFFFFF, 1));
        return view;
    }

    private View createTabs() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(Color.WHITE);

        tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setGravity(Gravity.CENTER_VERTICAL);
        tabContainer.setPadding(dp(12), dp(7), dp(12), dp(7));
        scroll.addView(tabContainer, new HorizontalScrollView.LayoutParams(-2, -1));

        for (int i = 0; i < PAGE_TITLES.length; i++) {
            final int index = i;
            TextView tab = new TextView(this);
            tab.setText(PAGE_TITLES[i]);
            tab.setTextSize(15);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setOnClickListener(v -> pagePager.setCurrentItem(index, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), -1);
            lp.setMargins(0, 0, dp(7), 0);
            tabContainer.addView(tab, lp);
            tabViews[i] = tab;
        }
        return scroll;
    }

    private LinearLayout createSideMenuPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setPadding(dp(18), getStatusBarInset() + dp(20), dp(18), dp(18));
        panel.setBackground(createDrawerBackground()); // 无圆角，浅渐变

        TextView title = new TextView(this);
        title.setText("学习工具");
        title.setTextSize(28);
        title.setTextColor(COLOR_TEXT_DARK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("AI 翻译、学习书籍、语音朗读、脚本管理和生活场景放这里。主页面只负责学习内容。侧边栏无圆角并覆盖全屏。");
        sub.setTextSize(13);
        sub.setTextColor(COLOR_TEXT_GRAY);
        sub.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, dp(16));
        panel.addView(sub, subLp);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list, new ScrollView.LayoutParams(-1, -2));

        list.addView(menuCard("DeepSeek 翻译", "打开 DeepSeek 网页，适合中缅互译、口语解释和场景练习。", "进入", () -> openAiPage("DeepSeek", "https://chat.deepseek.com/")));
        list.addView(menuCard("886.best", "打开 886.best，作为你的国内学习 AI 入口。", "进入", () -> openAiPage("886.best", "https://886.best")));
        list.addView(menuCard("千问国内版", "打开 qianwen.com，适合国内用户和全能助手场景。", "进入", () -> openAiPage("千问国内版", "https://www.qianwen.com/")));
        list.addView(menuCard("Qwen 国际版", "打开 chat.qwen.ai，适合纯聊天和语音练习。", "进入", () -> openAiPage("Qwen 国际版", "https://chat.qwen.ai/")));
        list.addView(menuCard("学习书籍", "后续用 books.json 做书籍目录；封面本地兜底，远程可覆盖。", "查看", this::showBookPlan));
        list.addView(menuCard("语音朗读", "打开 wkspeech：导入 TTS 源、选择发音人、设置语速音调。", "设置", this::openSpeechSettings));
        list.addView(menuCard("高频生活场景", "点餐、面试、打招呼、医院、机场等场景 prompt。", "选择", this::showPromptScenes));
        list.addView(menuCard("脚本管理", "新增、导入、在线安装、启用官方推荐脚本。", "管理", () -> startActivity(new Intent(this, ScriptManagerActivity.class))));

        TextView warn = new TextView(this);
        warn.setText("说明：侧边栏默认不支持边缘右滑，只能点右上角三个点打开，避免和 ViewPager2 横滑冲突。背景图本地兜底，正式图远程覆盖。价格文字走 JSON，不写进图片。SM-2 记录存在 Room。音频第一版建议用 TTS 或远程缓存，不要全塞 APK。");
        warn.setTextSize(12);
        warn.setTextColor(COLOR_TEXT_GRAY);
        warn.setPadding(dp(2), dp(8), dp(2), dp(14));
        list.addView(warn, new LinearLayout.LayoutParams(-1, -2));

        panel.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));
        return panel;
    }

    private View menuCard(String title, String desc, String action, Runnable click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(roundRect(0xFFFFFFFF, dp(16), COLOR_LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(11));
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(COLOR_TEXT_DARK);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView actionView = new TextView(this);
        actionView.setText(action);
        actionView.setTextSize(12);
        actionView.setTypeface(Typeface.DEFAULT_BOLD);
        actionView.setTextColor(COLOR_BLUE);
        actionView.setGravity(Gravity.CENTER);
        actionView.setBackground(roundRect(0xFFEAF2FF, dp(13), 0, 0));
        row.addView(actionView, new LinearLayout.LayoutParams(dp(52), dp(28)));
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(12);
        descView.setTextColor(COLOR_TEXT_GRAY);
        descView.setLineSpacing(dp(2), 1f);
        descView.setPadding(0, dp(7), 0, 0);
        card.addView(descView, new LinearLayout.LayoutParams(-1, -2));

        View.OnClickListener guarded = v -> runMenuClick(click);
        card.setOnClickListener(guarded);
        actionView.setOnClickListener(guarded);
        return card;
    }

    private void updateTabs(int selected) {
        for (int i = 0; i < tabViews.length; i++) {
            TextView tab = tabViews[i];
            if (tab == null) continue;
            boolean checked = i == selected;
            tab.setTextColor(checked ? Color.WHITE : COLOR_TEXT_GRAY);
            tab.setTypeface(Typeface.DEFAULT, checked ? Typeface.BOLD : Typeface.NORMAL);
            tab.setBackground(roundRect(checked ? COLOR_BLUE : 0xFFF1F5F9, dp(18), 0, 0));
        }
        if (tabContainer != null && selected >= 0 && selected < tabViews.length) {
            tabViews[selected].post(() -> {
                View parent = (View) tabContainer.getParent();
                if (parent instanceof HorizontalScrollView) {
                    ((HorizontalScrollView) parent).smoothScrollTo(Math.max(0, tabViews[selected].getLeft() - dp(24)), 0);
                }
            });
        }
    }

    private void openDrawer() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);
        drawerLayout.openDrawer(GravityCompat.START);
    }

    private void closeDrawerIfOpen() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void openAiPage(String title, String url) {
        closeDrawerIfOpen();
        AiScriptWebActivity.open(this, title, url);
    }

    private void showBookPlan() {
        closeDrawerIfOpen();
        new AlertDialog.Builder(this)
                .setTitle("学习书籍")
                .setMessage("第一版书籍建议放在 learning/books 或 learning/config/books.json。书籍入口不抢主页，主页主线先打磨单词 + SM-2。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void openSpeechSettings() {
        closeDrawerIfOpen();
        try {
            Class<?> clazz = Class.forName("com.chat.speech.ui.SpeechSettingsActivity");
            startActivity(new Intent(this, clazz));
        } catch (Throwable e) {
            Toast.makeText(this, "语音插件未安装或 wkspeech 模块未打包", Toast.LENGTH_SHORT).show();
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
        closeDrawerIfOpen();
        new AlertDialog.Builder(this)
                .setTitle("高频生活场景")
                .setItems(names, (dialog, which) -> copyPrompt(names[which], prompts[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void copyPrompt(String name, String prompt) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(name, prompt));
            Toast.makeText(this, "已复制场景 prompt", Toast.LENGTH_LONG).show();
        }
    }

    private void runMenuClick(Runnable click) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastMenuClickTime < 700) return;
        lastMenuClickTime = now;
        if (click != null) click.run();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeDrawerIfOpen();
            return;
        }
        super.onBackPressed();
    }

    private File getRemoteBannerFile() {
        return new File(getFilesDir(), "learning/images/home_banner.webp");
    }

    private int getPanelWidth() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
    }

    private GradientDrawable createHeaderScrim() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x33000000, 0x11000000, 0xAA000000});
    }

    private GradientDrawable createDrawerBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFFFFF, 0xFFF3F8FF, 0xFFFFFFFF});
    }

    private GradientDrawable roundRect(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int getStatusBarInset() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
