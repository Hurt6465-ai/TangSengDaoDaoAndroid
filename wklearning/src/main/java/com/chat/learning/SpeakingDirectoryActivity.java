package com.chat.learning;

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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Expandable speaking category browser: category -> Chinese/Myanmar phrase -> card. */
public class SpeakingDirectoryActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PARENT_ID = "parent_id";

    private static final int COLOR_BG = 0xFFF5F6FA;
    private static final int COLOR_TEXT = 0xFF151925;
    private static final int COLOR_SUB = 0xFF747C8E;
    private static final int COLOR_BRAND = 0xFF5E5CE6;
    private static final int COLOR_STROKE = 0xFFE7E9F0;
    private static final int COLOR_MY = 0xFF4D596F;

    private final Map<String, CategoryBinding> bindings = new HashMap<>();
    private LearningCatalogRepository.Catalog catalog;
    private SpeakingProgressStore progressStore;
    private String requestedParentId = "";

    public static void open(Context context, String title, String parentId) {
        Intent intent = new Intent(context, SpeakingDirectoryActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_PARENT_ID, parentId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestedParentId = safe(getIntent().getStringExtra(EXTRA_PARENT_ID));
        catalog = LearningCatalogRepository.load(this, "speaking");
        progressStore = new SpeakingProgressStore(this);

        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        buildLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProgress();
    }

    @Override
    protected void onDestroy() {
        if (progressStore != null) progressStore.close();
        super.onDestroy();
    }

    private void buildLayout() {
        bindings.clear();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(52)));

        TextView title = text(resolveTitle(), 29, COLOR_TEXT, true);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(8), 0, dp(6));
        page.addView(title, titleLp);

        TextView subtitle = text(getString(R.string.speaking_directory_subtitle), 14, COLOR_SUB, false);
        subtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, 0, 0, dp(16));
        page.addView(subtitle, subLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(34));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        if (catalog == null || catalog.items == null || catalog.items.isEmpty()) {
            content.addView(emptyView(), new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        boolean expandedOne = false;
        for (LearningCatalogRepository.Node rootNode : catalog.items) {
            if (rootNode == null) continue;
            if (rootNode.hasChildren()) {
                content.addView(groupTitle(rootNode), groupTitleLp());
                for (LearningCatalogRepository.Node child : rootNode.children) {
                    expandedOne = addNode(content, child,
                            !expandedOne && shouldExpand(rootNode, child)) || expandedOne;
                }
            } else {
                expandedOne = addNode(content, rootNode,
                        !expandedOne && shouldExpand(null, rootNode)) || expandedOne;
            }
        }
    }

    private boolean addNode(LinearLayout parent, LearningCatalogRepository.Node node,
                            boolean expand) {
        if (node == null) return false;
        if (node.hasChildren()) {
            parent.addView(groupTitle(node), groupTitleLp());
            boolean expanded = false;
            for (LearningCatalogRepository.Node child : node.children) {
                expanded = addNode(parent, child, !expanded && (expand || shouldExpand(node, child)))
                        || expanded;
            }
            return expanded;
        }

        SpeakingPhraseRepository.Pack pack = SpeakingPhraseRepository.load(
                this, node.asset, node.id, node.title);
        parent.addView(categoryCard(node, pack, expand), categoryCardLp());
        return expand;
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView close = text("‹", 29, COLOR_TEXT, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(rounded(Color.WHITE, dp(20), COLOR_STROKE, dp(1)));
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView crumb = text(getString(R.string.speaking_directory_breadcrumb), 14, COLOR_SUB, false);
        crumb.setGravity(Gravity.CENTER_VERTICAL);
        crumb.setPadding(dp(12), 0, 0, 0);
        bar.addView(crumb, new LinearLayout.LayoutParams(0, -1, 1f));
        return bar;
    }

    private View groupTitle(LearningCatalogRepository.Node node) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(node.title, 18, COLOR_TEXT, true);
        title.setIncludeFontPadding(false);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        if (safe(node.subtitle).length() > 0) {
            TextView sub = text(node.subtitle, 12.5f, COLOR_SUB, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(4), 0, 0);
            box.addView(sub, lp);
        }
        return box;
    }

    private LinearLayout.LayoutParams groupTitleLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(2), dp(8), dp(2), dp(10));
        return lp;
    }

    private View categoryCard(LearningCatalogRepository.Node node,
                              SpeakingPhraseRepository.Pack pack,
                              boolean expanded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(Color.WHITE, dp(22), COLOR_STROKE, dp(1)));
        card.setClipToOutline(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(15), dp(14), dp(15));
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView index = text(categoryMark(node), 17, Color.WHITE, true);
        index.setGravity(Gravity.CENTER);
        index.setBackground(rounded(accentFor(node), dp(19), 0, 0));
        header.addView(index, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textBoxLp = new LinearLayout.LayoutParams(0, -2, 1f);
        textBoxLp.setMargins(dp(12), 0, dp(8), 0);
        header.addView(textBox, textBoxLp);

        TextView title = text(node.title, 17, COLOR_TEXT, true);
        title.setIncludeFontPadding(false);
        textBox.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView progress = text("", 12.5f, COLOR_SUB, false);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, -2);
        progressLp.setMargins(0, dp(5), 0, 0);
        textBox.addView(progress, progressLp);

        TextView arrow = text(expanded ? "⌃" : "⌄", 20, COLOR_BRAND, true);
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(38)));

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_STROKE);
        card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout phrases = new LinearLayout(this);
        phrases.setOrientation(LinearLayout.VERTICAL);
        phrases.setVisibility(expanded ? View.VISIBLE : View.GONE);
        card.addView(phrases, new LinearLayout.LayoutParams(-1, -2));

        Map<String, WordFsrsScheduler.CardState> states = progressStore.loadPack(pack.id);
        ArrayList<PhraseBinding> phraseBindings = new ArrayList<>();
        if (pack.phrases.isEmpty()) {
            TextView empty = text(getString(R.string.speaking_category_empty), 14, COLOR_SUB, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            phrases.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (int i = 0; i < pack.phrases.size(); i++) {
                SpeakingPhrase phrase = pack.phrases.get(i);
                PhraseBinding phraseBinding = phraseRow(node, pack, phrase, states.get(phrase.progressKey()));
                phrases.addView(phraseBinding.row, new LinearLayout.LayoutParams(-1, -2));
                phraseBindings.add(phraseBinding);
                if (i < pack.phrases.size() - 1) {
                    View line = new View(this);
                    line.setBackgroundColor(0xFFF0F1F5);
                    LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, dp(1));
                    lineLp.setMargins(dp(66), 0, dp(14), 0);
                    phrases.addView(line, lineLp);
                }
            }
        }

        header.setOnClickListener(v -> {
            boolean show = phrases.getVisibility() != View.VISIBLE;
            phrases.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setText(show ? "⌃" : "⌄");
        });

        bindings.put(pack.id, new CategoryBinding(pack, progress, phraseBindings));
        updateCategory(bindings.get(pack.id));
        return card;
    }

    private PhraseBinding phraseRow(LearningCatalogRepository.Node node,
                                    SpeakingPhraseRepository.Pack pack,
                                    SpeakingPhrase phrase,
                                    WordFsrsScheduler.CardState state) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(13), dp(12), dp(13));
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setOnClickListener(v -> SpeakingFullscreenActivity.open(
                this, pack.id, pack.title, node.asset, phrase.progressKey()));

        TextView status = text(stateMark(state), 12, stateColor(state), true);
        status.setGravity(Gravity.CENTER);
        status.setBackground(rounded(0xFFF2F3F8, dp(18), 0, 0));
        row.addView(status, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1f);
        textLp.setMargins(dp(13), 0, dp(8), 0);
        row.addView(textBox, textLp);

        TextView zh = text(phrase.text, 16.5f, COLOR_TEXT, true);
        zh.setIncludeFontPadding(false);
        zh.setMaxLines(2);
        textBox.addView(zh, new LinearLayout.LayoutParams(-1, -2));

        TextView my = text(phrase.meaningMy, 13.5f, COLOR_MY, false);
        my.setIncludeFontPadding(true);
        my.setLineSpacing(dp(2), 1.08f);
        my.setMaxLines(2);
        LinearLayout.LayoutParams myLp = new LinearLayout.LayoutParams(-1, -2);
        myLp.setMargins(0, dp(5), 0, 0);
        textBox.addView(my, myLp);

        TextView arrow = text("›", 24, 0xFFADB2BF, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), -1));
        return new PhraseBinding(phrase, row, status);
    }

    private void refreshProgress() {
        if (progressStore == null) return;
        for (CategoryBinding binding : bindings.values()) updateCategory(binding);
    }

    private void updateCategory(CategoryBinding binding) {
        if (binding == null) return;
        long now = System.currentTimeMillis();
        SpeakingProgressStore.PackStats stats = progressStore.stats(
                binding.pack.id, binding.pack.phrases.size(), now);
        String value = getString(R.string.speaking_progress_learned, stats.learned, stats.total);
        if (stats.due > 0) {
            value += "  ·  " + getString(R.string.speaking_progress_due, stats.due);
        }
        binding.progress.setText(value);

        Map<String, WordFsrsScheduler.CardState> states = progressStore.loadPack(binding.pack.id);
        for (PhraseBinding phraseBinding : binding.phrases) {
            WordFsrsScheduler.CardState state = states.get(phraseBinding.phrase.progressKey());
            phraseBinding.status.setText(stateMark(state));
            phraseBinding.status.setTextColor(stateColor(state));
        }
    }

    private boolean shouldExpand(LearningCatalogRepository.Node parent,
                                 LearningCatalogRepository.Node node) {
        if (requestedParentId.length() == 0) return false;
        return requestedParentId.equals(safe(node == null ? "" : node.id))
                || requestedParentId.equals(safe(parent == null ? "" : parent.id));
    }

    private String stateMark(WordFsrsScheduler.CardState state) {
        if (state == null || state.reviewCount <= 0) {
            return getString(R.string.speaking_status_new_short);
        }
        if (state.dueAt <= System.currentTimeMillis()) {
            return getString(R.string.speaking_status_due_short);
        }
        return "✓";
    }

    private int stateColor(WordFsrsScheduler.CardState state) {
        if (state == null || state.reviewCount <= 0) return COLOR_BRAND;
        if (state.dueAt <= System.currentTimeMillis()) return 0xFFE07835;
        return 0xFF138A63;
    }

    private String categoryMark(LearningCatalogRepository.Node node) {
        String title = safe(node == null ? "" : node.title);
        return title.length() == 0
                ? getString(R.string.speaking_category_mark_default)
                : title.substring(0, 1);
    }

    private int accentFor(LearningCatalogRepository.Node node) {
        int[] colors = {0xFF6865E8, 0xFFEE8A51, 0xFF2D9C8B, 0xFFDF6380, 0xFF4B8CE8, 0xFF9162CC};
        String seed = safe(node == null ? "" : node.id);
        return colors[(seed.hashCode() & 0x7fffffff) % colors.length];
    }

    private String resolveTitle() {
        String title = safe(getIntent().getStringExtra(EXTRA_TITLE));
        if (title.length() == 0 || requestedParentId.length() > 0) {
            return getString(R.string.speaking_directory_title);
        }
        return title;
    }

    private View emptyView() {
        TextView view = text(getString(R.string.speaking_directory_empty), 14,
                COLOR_SUB, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(30), dp(20), dp(30));
        view.setBackground(rounded(Color.WHITE, dp(20), COLOR_STROKE, dp(1)));
        return view;
    }

    private LinearLayout.LayoutParams categoryCardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(13));
        return lp;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class CategoryBinding {
        final SpeakingPhraseRepository.Pack pack;
        final TextView progress;
        final List<PhraseBinding> phrases;

        CategoryBinding(SpeakingPhraseRepository.Pack pack, TextView progress,
                        List<PhraseBinding> phrases) {
            this.pack = pack;
            this.progress = progress;
            this.phrases = phrases;
        }
    }

    private static final class PhraseBinding {
        final SpeakingPhrase phrase;
        final View row;
        final TextView status;

        PhraseBinding(SpeakingPhrase phrase, View row, TextView status) {
            this.phrase = phrase;
            this.row = row;
            this.status = status;
        }
    }
}
