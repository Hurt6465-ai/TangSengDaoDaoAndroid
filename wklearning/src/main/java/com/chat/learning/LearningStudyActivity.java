package com.chat.learning;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 非单词类的全屏学习页占位：拼音、口语、句型、语法、练习题、Prompt 都走这里。
 * 后续具体数据接 assets/learning/{type}/{id}.json 即可。
 */
public class LearningStudyActivity extends AppCompatActivity {
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBTITLE = "subtitle";
    public static final String EXTRA_ASSET = "asset";
    public static final String EXTRA_PROMPT = "prompt";

    private static final int COLOR_BG = 0xFFF8FAFF;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_SUB = 0xFF64748B;
    private static final int COLOR_BRAND = 0xFF4F46E5;
    private static final int COLOR_STROKE = 0xFFE5E7EB;

    private String title;
    private String subtitle;
    private String type;
    private String id;
    private String asset;
    private String prompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);

        type = getIntent().getStringExtra(EXTRA_TYPE);
        id = getIntent().getStringExtra(EXTRA_ID);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        subtitle = getIntent().getStringExtra(EXTRA_SUBTITLE);
        asset = getIntent().getStringExtra(EXTRA_ASSET);
        prompt = getIntent().getStringExtra(EXTRA_PROMPT);
        if (title == null || title.length() == 0) title = "学习内容";
        if (subtitle == null) subtitle = "";
        if (asset == null) asset = "";
        if (prompt == null) prompt = "";

        buildLayout();
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(pageBg());
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(14), dp(18), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView back = circle("‹");
        back.setTextSize(28);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView crumb = new TextView(this);
        crumb.setText(typeLabel(type));
        crumb.setTextSize(14);
        crumb.setTextColor(COLOR_SUB);
        crumb.setPadding(dp(12), 0, 0, 0);
        crumb.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(crumb, new LinearLayout.LayoutParams(0, -1, 1f));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(31);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setIncludeFontPadding(false);
        content.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView subView = new TextView(this);
        subView.setText(subtitle.length() > 0 ? subtitle : "全屏学习页，后续可接入对应 JSON 数据。素材和数据不用写死在首页。 ");
        subView.setTextSize(15);
        subView.setTextColor(COLOR_SUB);
        subView.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(10), 0, dp(22));
        content.addView(subView, subLp);

        if (prompt.length() > 0) {
            content.addView(promptCard(), new LinearLayout.LayoutParams(-1, -2));
        } else {
            content.addView(studyCard("学习内容", placeholderText()), new LinearLayout.LayoutParams(-1, -2));
            content.addView(studyCard("后续数据路径", asset.length() > 0 ? asset : "assets/learning/" + safe(type) + "/" + safe(id) + ".json"), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private View promptCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(Color.WHITE, dp(22), COLOR_STROKE, dp(1)));

        TextView label = new TextView(this);
        label.setText("场景 Prompt");
        label.setTextSize(13);
        label.setTextColor(COLOR_BRAND);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(label, new LinearLayout.LayoutParams(-1, -2));

        TextView text = new TextView(this);
        text.setText(prompt);
        text.setTextSize(16);
        text.setTextColor(COLOR_TEXT);
        text.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-1, -2);
        textLp.setMargins(0, dp(14), 0, dp(18));
        card.addView(text, textLp);

        TextView copy = new TextView(this);
        copy.setText("复制 Prompt");
        copy.setTextSize(15);
        copy.setTextColor(Color.WHITE);
        copy.setTypeface(Typeface.DEFAULT_BOLD);
        copy.setGravity(Gravity.CENTER);
        copy.setBackground(rounded(COLOR_BRAND, dp(18), 0, 0));
        copy.setOnClickListener(v -> copyPrompt());
        card.addView(copy, new LinearLayout.LayoutParams(-1, dp(48)));
        return card;
    }

    private View studyCard(String label, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(Color.WHITE, dp(22), COLOR_STROKE, dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(14);
        title.setTextColor(COLOR_BRAND);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView text = new TextView(this);
        text.setText(body);
        text.setTextSize(16);
        text.setTextColor(COLOR_TEXT);
        text.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-1, -2);
        textLp.setMargins(0, dp(12), 0, 0);
        card.addView(text, textLp);
        return card;
    }

    private String placeholderText() {
        if ("pinyin".equals(type)) return "这里展示拼音点读、发音口型、声调练习和跟读入口。";
        if ("speaking".equals(type)) return "这里展示场景短句、AI 对练、跟读、角色扮演和常见问答。";
        if ("patterns".equals(type)) return "这里展示句型结构、替换练习、例句和中缅对照。";
        if ("grammar".equals(type)) return "这里展示语法说明、常见错误、例句和练习题。";
        if ("quiz".equals(type)) return "这里展示选择题、听力题、口语题和错题复习。";
        if ("books".equals(type)) return "这里展示电子书章节、离线缓存、点读和朗读入口。";
        return "这里展示具体学习内容。";
    }

    private void copyPrompt() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(title, prompt));
            Toast.makeText(this, "已复制：" + title, Toast.LENGTH_SHORT).show();
        }
    }

    private String typeLabel(String type) {
        if ("words".equals(type)) return "单词";
        if ("speaking".equals(type)) return "口语";
        if ("patterns".equals(type)) return "句型";
        if ("grammar".equals(type)) return "语法";
        if ("pinyin".equals(type)) return "拼音";
        if ("quiz".equals(type)) return "练习题";
        if ("books".equals(type)) return "电子书";
        if ("prompts".equals(type)) return "口语 Prompt";
        return "学习";
    }

    private TextView circle(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(COLOR_TEXT);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setBackground(rounded(0xFFFFFFFF, dp(21), COLOR_STROKE, dp(1)));
        return view;
    }

    private GradientDrawable pageBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{COLOR_BG, 0xFFFFFFFF});
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
