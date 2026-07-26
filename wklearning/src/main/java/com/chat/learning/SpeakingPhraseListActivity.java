package com.chat.learning;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Efficient phrase list for one speaking category. */
public class SpeakingPhraseListActivity extends AppCompatActivity {
    private static final String EXTRA_PACK_ID = "pack_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_SUBTITLE = "subtitle";
    private static final String EXTRA_ASSET = "asset";

    private static final int COLOR_BG = 0xFFF5F6FA;
    private static final int COLOR_TEXT = 0xFF151925;
    private static final int COLOR_SUB = 0xFF747C8E;
    private static final int COLOR_BRAND = 0xFF5E5CE6;
    private static final int COLOR_STROKE = 0xFFE7E9F0;
    private static final int COLOR_MY = 0xFF4D596F;

    private String packId = "";
    private String title = "";
    private String subtitle = "";
    private String asset = "";
    private SpeakingPhraseRepository.Pack pack;
    private SpeakingProgressStore progressStore;
    private PhraseAdapter adapter;
    private TextView summaryView;

    public static void open(Context context, String packId, String title,
                            String subtitle, String asset) {
        Intent intent = new Intent(context, SpeakingPhraseListActivity.class);
        intent.putExtra(EXTRA_PACK_ID, packId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SUBTITLE, subtitle);
        intent.putExtra(EXTRA_ASSET, asset);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packId = safe(getIntent().getStringExtra(EXTRA_PACK_ID));
        title = safe(getIntent().getStringExtra(EXTRA_TITLE));
        subtitle = safe(getIntent().getStringExtra(EXTRA_SUBTITLE));
        asset = safe(getIntent().getStringExtra(EXTRA_ASSET));
        progressStore = new SpeakingProgressStore(this);
        pack = SpeakingPhraseRepository.load(this, asset, packId, title);
        if (pack.id.length() > 0) packId = pack.id;
        if (pack.title.length() > 0) title = pack.title;
        if (pack.subtitle.length() > 0) subtitle = pack.subtitle;

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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(52)));

        TextView titleView = text(title.length() > 0
                ? title : getString(R.string.speaking_title_default), 28, COLOR_TEXT, true);
        titleView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(8), 0, dp(5));
        page.addView(titleView, titleLp);

        if (subtitle.length() > 0) {
            TextView subtitleView = text(subtitle, 13.5f, COLOR_SUB, false);
            subtitleView.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(7));
            page.addView(subtitleView, lp);
        }

        summaryView = text("", 12.5f, COLOR_SUB, false);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(-1, -2);
        summaryLp.setMargins(0, 0, 0, dp(12));
        page.addView(summaryView, summaryLp);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setHasFixedSize(true);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(28));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new PhraseAdapter(pack == null ? Collections.emptyList() : pack.phrases);
        list.setAdapter(adapter);
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
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

        TextView crumb = text(getString(R.string.speaking_phrase_list_breadcrumb),
                14, COLOR_SUB, false);
        crumb.setGravity(Gravity.CENTER_VERTICAL);
        crumb.setPadding(dp(12), 0, 0, 0);
        bar.addView(crumb, new LinearLayout.LayoutParams(0, -1, 1f));
        return bar;
    }

    private void refreshProgress() {
        if (pack == null || progressStore == null) return;
        SpeakingProgressStore.PackStats stats = progressStore.stats(
                packId, pack.phrases.size(), System.currentTimeMillis());
        String summary = getString(R.string.speaking_phrase_list_summary,
                stats.total, stats.learned);
        if (stats.due > 0) summary += "  ·  " + getString(R.string.speaking_progress_due, stats.due);
        summaryView.setText(summary);
        if (adapter != null) {
            adapter.states = progressStore.loadPack(packId);
            adapter.notifyDataSetChanged();
        }
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

    private final class PhraseAdapter extends RecyclerView.Adapter<PhraseHolder> {
        private final List<SpeakingPhrase> items;
        private Map<String, WordFsrsScheduler.CardState> states = Collections.emptyMap();

        PhraseAdapter(List<SpeakingPhrase> items) {
            this.items = items == null ? Collections.emptyList() : items;
        }

        @NonNull
        @Override
        public PhraseHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(SpeakingPhraseListActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(15), dp(14), dp(12), dp(14));
            row.setBackground(rounded(Color.WHITE, dp(20), COLOR_STROKE, dp(1)));

            TextView status = text("", 12, COLOR_BRAND, true);
            status.setGravity(Gravity.CENTER);
            status.setBackground(rounded(0xFFF2F3F8, dp(18), 0, 0));
            row.addView(status, new LinearLayout.LayoutParams(dp(36), dp(36)));

            LinearLayout textBox = new LinearLayout(SpeakingPhraseListActivity.this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1f);
            textLp.setMargins(dp(13), 0, dp(8), 0);
            row.addView(textBox, textLp);

            TextView zh = text("", 16.5f, COLOR_TEXT, true);
            zh.setIncludeFontPadding(false);
            zh.setMaxLines(2);
            textBox.addView(zh, new LinearLayout.LayoutParams(-1, -2));

            TextView my = text("", 13.5f, COLOR_MY, false);
            my.setIncludeFontPadding(true);
            my.setLineSpacing(dp(2), 1.08f);
            my.setMaxLines(2);
            LinearLayout.LayoutParams myLp = new LinearLayout.LayoutParams(-1, -2);
            myLp.setMargins(0, dp(5), 0, 0);
            textBox.addView(my, myLp);

            TextView arrow = text("›", 24, 0xFFADB2BF, false);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(28), -1));
            return new PhraseHolder(row, status, zh, my);
        }

        @Override
        public void onBindViewHolder(@NonNull PhraseHolder holder, int position) {
            SpeakingPhrase phrase = items.get(position);
            WordFsrsScheduler.CardState state = states.get(phrase.progressKey());
            holder.status.setText(stateMark(state));
            holder.status.setTextColor(stateColor(state));
            holder.zh.setText(phrase.text);
            holder.my.setText(phrase.meaningMy);
            holder.itemView.setOnClickListener(v -> SpeakingFullscreenActivity.open(
                    SpeakingPhraseListActivity.this, packId, title, asset, phrase.progressKey()));

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(10));
            holder.itemView.setLayoutParams(lp);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class PhraseHolder extends RecyclerView.ViewHolder {
        final TextView status;
        final TextView zh;
        final TextView my;

        PhraseHolder(@NonNull View itemView, TextView status, TextView zh, TextView my) {
            super(itemView);
            this.status = status;
            this.zh = zh;
            this.my = my;
        }
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
}
