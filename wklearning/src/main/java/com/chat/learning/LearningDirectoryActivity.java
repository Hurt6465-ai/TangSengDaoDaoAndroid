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
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * 全屏多级学习目录页。
 * 首页点“更多”或点击某个分类后，统一进入这里；不再使用 AlertDialog 弹窗。
 */
public class LearningDirectoryActivity extends AppCompatActivity {
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_PARENT_ID = "parent_id";
    public static final String EXTRA_TITLE = "title";

    private static final int COLOR_BG = 0xFFF2F2F7;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_SUB = 0xFF6B7280;
    private static final int COLOR_STROKE = 0xFFE5E7EB;
    private static final int COLOR_BRAND = 0xFF4F46E5;

    private String type;
    private String parentId;
    private LearningCatalogRepository.Catalog catalog;
    private long lastClickTime;

    public static void open(Context context, String type, String title, String parentId) {
        if ("speaking".equals(type)) {
            SpeakingDirectoryActivity.open(context, title, parentId);
            return;
        }
        Intent intent = new Intent(context, LearningDirectoryActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_PARENT_ID, parentId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);

        type = getIntent().getStringExtra(EXTRA_TYPE);
        if (type == null || type.length() == 0) type = "words";
        if ("speaking".equals(type)) {
            SpeakingDirectoryActivity.open(this,
                    getIntent().getStringExtra(EXTRA_TITLE),
                    getIntent().getStringExtra(EXTRA_PARENT_ID));
            finish();
            return;
        }
        parentId = getIntent().getStringExtra(EXTRA_PARENT_ID);
        if (parentId == null) parentId = "";
        catalog = LearningCatalogRepository.load(this, type);

        if (parentId.length() > 0) {
            LearningCatalogRepository.Node direct = LearningCatalogRepository.find(catalog, parentId);
            if (direct != null && !direct.hasChildren()) {
                handleNode(direct);
                finish();
                return;
            }
        }

        buildLayout();
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // 返回按钮固定；标题和介绍放进 ScrollView，向上滚动时自然隐藏。
        page.addView(createTopBar(), new LinearLayout.LayoutParams(-1, dp(52)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(30));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        addScrollableHeader(list);

        List<LearningCatalogRepository.Node> children = LearningCatalogRepository.childrenOf(catalog, parentId);
        if (children == null || children.isEmpty()) {
            list.addView(emptyCard(), new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        if ("words".equals(type)) {
            addWordCardGrid(list, children);
        } else {
            for (LearningCatalogRepository.Node node : children) {
                list.addView(directoryCard(node), new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    private void addScrollableHeader(LinearLayout parent) {
        TextView title = new TextView(this);
        title.setText(resolveTitle());
        title.setTextSize(28);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(8), 0, dp(6));
        parent.addView(title, titleLp);

        TextView subtitle = new TextView(this);
        subtitle.setText(resolveSubtitle());
        subtitle.setTextSize(14);
        subtitle.setTextColor(COLOR_SUB);
        subtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, 0, 0, dp(18));
        parent.addView(subtitle, subLp);
    }

    /**
     * 单词目录专用：一排 2 个竖屏卡片。
     * 卡片数据来自 assets/learning/words/catalog.json 的 title / badge / preview 字段。
     * 点击卡片后：有 children 就进入下一级目录；target=word 就进入 WordFullscreenActivity。
     */
    private void addWordCardGrid(LinearLayout parent, List<LearningCatalogRepository.Node> nodes) {
        int index = 0;
        while (index < nodes.size()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.setMargins(0, 0, 0, dp(12));
            parent.addView(row, rowLp);

            for (int i = 0; i < 2; i++) {
                if (index < nodes.size()) {
                    row.addView(wordVerticalCard(nodes.get(index)), new LinearLayout.LayoutParams(0, dp(184), 1f));
                    index++;
                } else {
                    View empty = new View(this);
                    row.addView(empty, new LinearLayout.LayoutParams(0, dp(1), 1f));
                }
                if (i == 0) addHorizontalGap(row, 12);
            }
        }
    }

    private View wordVerticalCard(LearningCatalogRepository.Node node) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(gradientRounded(cardStartFor(node), 0xFFFFFFFF, dp(18), 0x22FFFFFF, dp(1)));
        card.setClipToOutline(true);
        card.setOnClickListener(v -> throttledClick(() -> handleNode(node)));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(cardStartFor(node));
        card.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        LearningCoverLoader.load(cover, node.coverUrl, node.coverVersion);

        View shade = new View(this);
        GradientDrawable shadeDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x10000000, 0x22000000, 0xCC111827});
        shade.setBackground(shadeDrawable);
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.START);
        content.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = new TextView(this);
        badge.setText(node.badge != null && node.badge.length() > 0 ? node.badge : targetLabel(node));
        badge.setTextSize(10);
        badge.setTextColor(0xFF111827);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setPadding(dp(7), dp(4), dp(7), dp(4));
        badge.setBackground(rounded(0xEFFFFFFF, dp(12), 0x33FFFFFF, dp(1)));
        content.addView(badge, new LinearLayout.LayoutParams(-2, -2));

        content.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView title = new TextView(this);
        title.setText(node.title);
        title.setTextSize(16);
        title.setTextColor(Color.WHITE);
        title.setShadowLayer(dp(2), 0, dp(1), 0x77000000);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setMaxLines(1);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView preview = new TextView(this);
        String previewText = node.preview != null && node.preview.length() > 0 ? node.preview : node.subtitle;
        preview.setText(previewText);
        preview.setTextSize(10.5f);
        preview.setTextColor(0xE6FFFFFF);
        preview.setIncludeFontPadding(false);
        preview.setMaxLines(2);
        preview.setLineSpacing(dp(1), 1f);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
        previewLp.setMargins(0, dp(5), 0, 0);
        content.addView(preview, previewLp);
        return card;
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private int cardStartFor(LearningCatalogRepository.Node node) {
        String seed = node != null ? node.id : "word";
        int h = Math.abs(seed == null ? 0 : seed.hashCode());
        int[] colors = new int[]{0xFFEFF6FF, 0xFFF5F3FF, 0xFFECFDF5, 0xFFFFF7ED, 0xFFFFF1F2, 0xFFE0F2FE};
        return colors[h % colors.length];
    }

    private View createTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = circle(parentId.length() == 0 ? "×" : "‹");
        back.setTextSize(parentId.length() == 0 ? 22 : 28);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        // 不再显示“学习首页”面包屑，减少顶部重复信息。
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        return top;
    }

    private View directoryCard(LearningCatalogRepository.Node node) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(14), dp(16));
        card.setBackground(gradientRounded(0xFFFFFFFF, 0xFFF8F9FD, dp(18), COLOR_STROKE, dp(1)));
        card.setMinimumHeight(dp(82));
        card.setOnClickListener(v -> throttledClick(() -> handleNode(node)));

        View dot = new View(this);
        dot.setBackground(rounded(accentFor(node), dp(5), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(42));
        dotLp.setMargins(0, 0, dp(14), 0);
        card.addView(dot, dotLp);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        textBox.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText(node.title);
        title.setTextSize(17);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        if (node.badge != null && node.badge.length() > 0) {
            TextView badge = new TextView(this);
            badge.setText(node.badge);
            badge.setTextSize(11);
            badge.setTextColor(COLOR_BRAND);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded(0xFFEFF0FF, dp(10), 0, 0));
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            row.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        }

        TextView subtitle = new TextView(this);
        subtitle.setText(node.subtitle);
        subtitle.setTextSize(13);
        subtitle.setTextColor(COLOR_SUB);
        subtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(7), 0, 0);
        textBox.addView(subtitle, subLp);

        TextView arrow = new TextView(this);
        arrow.setText(node.hasChildren() ? "›" : targetLabel(node));
        arrow.setTextSize(node.hasChildren() ? 28 : 12);
        arrow.setTextColor(node.hasChildren() ? 0xFF9CA3AF : COLOR_BRAND);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(node.hasChildren() ? dp(34) : dp(54), -1);
        arrowLp.setMargins(dp(10), 0, 0, 0);
        card.addView(arrow, arrowLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private void handleNode(LearningCatalogRepository.Node node) {
        if (node == null) return;
        if (node.hasChildren()) {
            open(this, type, node.title, node.id);
            return;
        }

        if ("pinyin_chart".equals(node.target)) {
            PinyinChartActivity.open(this, node.id);
            return;
        }

        if ("word".equals(node.target)) {
            Intent intent = new Intent(this, WordFullscreenActivity.class);
            intent.putExtra(WordFullscreenActivity.EXTRA_LEVEL, node.level != null && node.level.length() > 0 ? node.level : node.id);
            intent.putExtra(WordFullscreenActivity.EXTRA_TITLE, node.title);
            intent.putExtra(WordFullscreenActivity.EXTRA_DATA_URL, node.dataUrl);
            intent.putExtra(WordFullscreenActivity.EXTRA_DATA_SHA256, node.dataSha256);
            intent.putExtra(WordFullscreenActivity.EXTRA_DATA_VERSION, node.dataVersion);
            startActivity(intent);
            return;
        }

        if ("prompt".equals(node.target)) {
            Intent intent = new Intent(this, LearningStudyActivity.class);
            intent.putExtra(LearningStudyActivity.EXTRA_TYPE, type);
            intent.putExtra(LearningStudyActivity.EXTRA_TITLE, node.title);
            intent.putExtra(LearningStudyActivity.EXTRA_SUBTITLE, node.subtitle);
            intent.putExtra(LearningStudyActivity.EXTRA_PROMPT, node.prompt);
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(this, LearningStudyActivity.class);
        intent.putExtra(LearningStudyActivity.EXTRA_TYPE, type);
        intent.putExtra(LearningStudyActivity.EXTRA_ID, node.id);
        intent.putExtra(LearningStudyActivity.EXTRA_TITLE, node.title);
        intent.putExtra(LearningStudyActivity.EXTRA_SUBTITLE, node.subtitle);
        intent.putExtra(LearningStudyActivity.EXTRA_ASSET, node.asset);
        startActivity(intent);
    }

    private String targetLabel(LearningCatalogRepository.Node node) {
        if ("word".equals(node.target)) return "背词";
        if ("prompt".equals(node.target)) return "复制";
        if ("pinyin_chart".equals(node.target)) return "点读";
        return "学习";
    }

    private View emptyCard() {
        TextView view = new TextView(this);
        view.setText("这个目录还没有内容，后续接入对应 JSON 数据。\nassets/learning/" + type + "/catalog.json");
        view.setTextSize(14);
        view.setTextColor(COLOR_SUB);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(dp(4), 1f);
        view.setPadding(dp(18), dp(30), dp(18), dp(30));
        view.setBackground(rounded(0xFFFFFFFF, dp(18), COLOR_STROKE, dp(1)));
        return view;
    }

    private String resolveTitle() {
        String provided = getIntent().getStringExtra(EXTRA_TITLE);
        if (parentId.length() == 0 && provided != null && provided.length() > 0) return provided;
        return LearningCatalogRepository.titleFor(catalog, parentId);
    }

    private String resolveSubtitle() {
        String subtitle = LearningCatalogRepository.subtitleFor(catalog, parentId);
        if (subtitle == null || subtitle.length() == 0) {
            return parentId.length() == 0 ? "选择一个分类，进入二级目录后再学习具体内容。" : "继续选择小类，进入具体学习内容。";
        }
        return subtitle;
    }

    private void throttledClick(Runnable runnable) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastClickTime < 420) return;
        lastClickTime = now;
        if (runnable != null) runnable.run();
    }

    private int accentFor(LearningCatalogRepository.Node node) {
        String seed = node != null ? node.id : type;
        int h = Math.abs(seed == null ? 0 : seed.hashCode());
        int[] colors = new int[]{0xFF2563EB, 0xFF7C3AED, 0xFF059669, 0xFFEA580C, 0xFFE11D48, 0xFF0891B2};
        return colors[h % colors.length];
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

    private GradientDrawable gradientRounded(int startColor, int endColor, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
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

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
