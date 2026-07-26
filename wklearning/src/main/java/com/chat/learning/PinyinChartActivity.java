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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Native four-column pinyin chart with bundled local audio paths and autoplay. */
public class PinyinChartActivity extends AppCompatActivity {
    public static final String EXTRA_TAB = "tab";

    private static final int COLOR_BG = 0xFFF6F7FB;
    private static final int COLOR_TEXT = 0xFF171A22;
    private static final int COLOR_SUB = 0xFF737784;
    private static final int COLOR_BRAND = 0xFF675FD7;
    private static final int COLOR_BRAND_SOFT = 0xFFF0EFFF;
    private static final int COLOR_STROKE = 0xFFE6E7EC;

    private final List<TextView> tabViews = new ArrayList<>();
    private PinyinChartRepository.Chart chart;
    private PinyinChartRepository.Section currentSection;
    private PinyinGridAdapter adapter;
    private PinyinAudioPlayer audioPlayer;

    private TextView sectionTitle;
    private TextView sectionSubtitle;
    private TextView selectedLetter;
    private TextView selectedHint;
    private TextView speedButton;
    private TextView autoButton;

    private int sectionIndex;
    private int selectedIndex = -1;
    private int autoIndex = -1;
    private boolean autoPlaying;
    private boolean placeholderNoticeShown;
    private float speed = 1f;

    public static void open(Context context, String tab) {
        Intent intent = new Intent(context, PinyinChartActivity.class);
        intent.putExtra(EXTRA_TAB, PinyinChartRepository.normalizeSectionId(tab));
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        chart = PinyinChartRepository.load(this);
        audioPlayer = new PinyinAudioPlayer(this);
        sectionIndex = PinyinChartRepository.findSectionIndex(chart, getIntent().getStringExtra(EXTRA_TAB));
        buildLayout();
        if (!chart.sections.isEmpty()) switchSection(sectionIndex, false);
    }

    @Override
    protected void onDestroy() {
        if (audioPlayer != null) audioPlayer.release();
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(8), dp(16), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));



        page.addView(createTabs(), new LinearLayout.LayoutParams(-1, dp(52)));

        RecyclerView list = new RecyclerView(this);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, dp(10), 0, dp(142));
        list.setLayoutManager(new GridLayoutManager(this, 4));
        adapter = new PinyinGridAdapter(item -> {
            if (item == null || currentSection == null) return;
            int index = currentSection.items.indexOf(item);
            if (index >= 0) selectAndPlay(index, false);
        });
        list.setAdapter(adapter);
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(createPlayerDock(), playerDockLayoutParams());

        if (chart.sections.isEmpty()) {
            TextView empty = text(getString(R.string.pinyin_chart_data_missing), 15, COLOR_SUB, false);
            empty.setGravity(Gravity.CENTER);
            page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
    }

    private View createTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 30, COLOR_TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(rounded(Color.WHITE, dp(21), COLOR_STROKE, dp(1)));
        back.setContentDescription(getString(R.string.pinyin_chart_back));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView crumb = text(getString(R.string.learning_home_pinyin_title), 14, COLOR_SUB, false);
        crumb.setGravity(Gravity.CENTER_VERTICAL);
        crumb.setPadding(dp(12), 0, 0, 0);
        top.addView(crumb, new LinearLayout.LayoutParams(0, -1, 1f));
        return top;
    }

    private View createTabs() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setPadding(dp(4), dp(4), dp(4), dp(4));
        shell.setBackground(rounded(0xFFECEEF3, dp(15), 0, 0));

        int[] labels = new int[]{
                R.string.learning_home_initials,
                R.string.learning_home_finals,
                R.string.learning_home_whole_syllables,
                R.string.learning_home_tones
        };
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = text(getString(labels[i]), 13, COLOR_SUB, true);
            tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> switchSection(index, true));
            tabViews.add(tab);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.setMargins(dp(2), 0, 0, 0);
            shell.addView(tab, lp);
        }
        return shell;
    }

    private View createPlayerDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(14), dp(12), dp(14), dp(12));
        dock.setBackground(rounded(0xFFF1EEFF, dp(24), 0xFFD8D0FF, dp(1)));
        dock.setElevation(dp(14));

        LinearLayout selectedRow = new LinearLayout(this);
        selectedRow.setOrientation(LinearLayout.HORIZONTAL);
        selectedRow.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(selectedRow, new LinearLayout.LayoutParams(-1, dp(42)));

        selectedLetter = text("—", 28, COLOR_TEXT, true);
        selectedLetter.setGravity(Gravity.CENTER_VERTICAL);
        selectedRow.addView(selectedLetter, new LinearLayout.LayoutParams(-2, -1));

        selectedHint = text(getString(R.string.pinyin_chart_tap_hint), 12, COLOR_SUB, false);
        selectedHint.setGravity(Gravity.CENTER_VERTICAL);
        selectedHint.setMaxLines(1);
        selectedHint.setPadding(dp(12), 0, dp(8), 0);
        selectedRow.addView(selectedHint, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, dp(40));
        controlsLp.setMargins(0, dp(7), 0, 0);
        dock.addView(controls, controlsLp);

        speedButton = dockButton(getString(R.string.pinyin_chart_speed, "1.0"), v -> cycleSpeed());
        controls.addView(speedButton, new LinearLayout.LayoutParams(0, -1, 1f));
        addGap(controls, 8);

        TextView followButton = dockButton(getString(R.string.pinyin_chart_follow), v ->
                Toast.makeText(this, R.string.pinyin_chart_follow_placeholder, Toast.LENGTH_SHORT).show());
        controls.addView(followButton, new LinearLayout.LayoutParams(0, -1, 1f));
        addGap(controls, 8);

        autoButton = dockButton(getString(R.string.pinyin_chart_auto_play), v -> toggleAutoPlay());
        controls.addView(autoButton, new LinearLayout.LayoutParams(0, -1, 1f));
        return dock;
    }

    private FrameLayout.LayoutParams playerDockLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(12), 0, dp(12), dp(12));
        return lp;
    }

    private void switchSection(int requestedIndex, boolean scrollReset) {
        if (chart.sections.isEmpty()) return;
        int resolved = Math.max(0, Math.min(requestedIndex, chart.sections.size() - 1));
        stopAutoPlay();
        sectionIndex = resolved;
        currentSection = chart.sections.get(sectionIndex);
        selectedIndex = -1;
        sectionTitle.setText(currentSection.title);
        sectionSubtitle.setText(currentSection.subtitle);
        selectedLetter.setText("—");
        selectedHint.setText(R.string.pinyin_chart_tap_hint);
        adapter.submit(currentSection.items);
        updateTabs();
    }

    private void updateTabs() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tab = tabViews.get(i);
            boolean selected = i == sectionIndex;
            tab.setTextColor(selected ? COLOR_TEXT : COLOR_SUB);
            tab.setBackground(selected
                    ? rounded(Color.WHITE, dp(12), 0, 0)
                    : rounded(Color.TRANSPARENT, dp(12), 0, 0));
            tab.setElevation(selected ? dp(1) : 0f);
        }
    }

    private void selectAndPlay(int index, boolean fromAuto) {
        if (currentSection == null || index < 0 || index >= currentSection.items.size()) {
            stopAutoPlay();
            return;
        }
        selectedIndex = index;
        getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        PinyinChartRepository.Item item = currentSection.items.get(index);
        selectedLetter.setText(item.letter);
        selectedHint.setText(item.hint == null || item.hint.isEmpty()
                ? getString(R.string.pinyin_chart_local_audio)
                : item.hint);
        adapter.setSelectedIndex(index);
        adapter.setPlayingIndex(index);
        audioPlayer.play(item.audioAsset, speed, new PinyinAudioPlayer.Callback() {
            @Override public void onStarted(boolean placeholder) {
                if (placeholder && !placeholderNoticeShown) {
                    placeholderNoticeShown = true;
                    Toast.makeText(PinyinChartActivity.this,
                            R.string.pinyin_chart_audio_placeholder, Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void onCompleted() {
                adapter.setPlayingIndex(-1);
                if (autoPlaying) playNextAuto();
            }

            @Override public void onError() {
                adapter.setPlayingIndex(-1);
                Toast.makeText(PinyinChartActivity.this,
                        R.string.pinyin_chart_play_failed, Toast.LENGTH_SHORT).show();
                if (autoPlaying) playNextAuto();
            }
        });
        if (fromAuto) autoIndex = index;
    }

    private void replaySelected() {
        if (currentSection == null || currentSection.items.isEmpty()) return;
        int index = selectedIndex >= 0 ? selectedIndex : 0;
        selectAndPlay(index, false);
    }

    private void cycleSpeed() {
        if (speed > 0.9f) speed = 0.7f;
        else if (speed < 0.8f) speed = 0.85f;
        else speed = 1f;
        String label = speed == 1f ? "1.0" : speed == 0.85f ? "0.85" : "0.7";
        speedButton.setText(getString(R.string.pinyin_chart_speed, label));
        if (selectedIndex >= 0) selectAndPlay(selectedIndex, autoPlaying);
    }

    private void toggleAutoPlay() {
        if (autoPlaying) {
            stopAutoPlay();
            return;
        }
        if (currentSection == null || currentSection.items.isEmpty()) return;
        autoPlaying = true;
        autoButton.setText(R.string.pinyin_chart_stop_auto);
        autoButton.setTextColor(Color.WHITE);
        autoButton.setBackground(rounded(COLOR_BRAND, dp(14), 0, 0));
        autoIndex = selectedIndex >= 0 ? selectedIndex : 0;
        selectAndPlay(autoIndex, true);
    }

    private void playNextAuto() {
        if (!autoPlaying || currentSection == null) return;
        int next = autoIndex + 1;
        if (next >= currentSection.items.size()) {
            stopAutoPlay();
            return;
        }
        selectAndPlay(next, true);
    }

    private void stopAutoPlay() {
        autoPlaying = false;
        autoIndex = -1;
        if (audioPlayer != null) audioPlayer.stop();
        if (adapter != null) adapter.setPlayingIndex(-1);
        if (autoButton != null) {
            autoButton.setText(R.string.pinyin_chart_auto_play);
            autoButton.setTextColor(COLOR_TEXT);
            autoButton.setBackground(rounded(0xFFF2F3F7, dp(14), 0, 0));
        }
    }

    private TextView dockButton(String value, View.OnClickListener listener) {
        TextView view = text(value, 12, COLOR_TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(rounded(0xFFF2F3F7, dp(14), 0, 0));
        view.setOnClickListener(listener);
        return view;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private void addGap(LinearLayout parent, int widthDp) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(dp(widthDp), 1));
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

    private final class PinyinGridAdapter extends RecyclerView.Adapter<PinyinHolder> {
        interface Listener { void onClick(PinyinChartRepository.Item item); }

        private final List<PinyinChartRepository.Item> items = new ArrayList<>();
        private final Listener listener;
        private int selected = -1;
        private int playing = -1;

        PinyinGridAdapter(Listener listener) {
            this.listener = listener;
            setHasStableIds(true);
        }

        void submit(List<PinyinChartRepository.Item> values) {
            items.clear();
            if (values != null) items.addAll(values);
            selected = -1;
            playing = -1;
            notifyDataSetChanged();
        }

        void setSelectedIndex(int index) {
            int old = selected;
            selected = index;
            if (old >= 0) notifyItemChanged(old);
            if (selected >= 0) notifyItemChanged(selected);
        }

        void setPlayingIndex(int index) {
            int old = playing;
            playing = index;
            if (old >= 0) notifyItemChanged(old);
            if (playing >= 0) notifyItemChanged(playing);
        }

        @Override public long getItemId(int position) {
            return items.get(position).letter.hashCode();
        }

        @NonNull
        @Override public PinyinHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(5), dp(8), dp(5), dp(7));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, dp(82));
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(lp);

            TextView letter = text("", 25, COLOR_TEXT, true);
            letter.setGravity(Gravity.CENTER);
            card.addView(letter, new LinearLayout.LayoutParams(-1, 0, 1f));

            TextView hint = text("", 10.5f, COLOR_SUB, false);
            hint.setGravity(Gravity.CENTER);
            hint.setMaxLines(1);
            card.addView(hint, new LinearLayout.LayoutParams(-1, dp(23)));
            return new PinyinHolder(card, letter, hint);
        }

        @Override public void onBindViewHolder(@NonNull PinyinHolder holder, int position) {
            PinyinChartRepository.Item item = items.get(position);
            holder.letter.setText(item.letter);
            holder.letter.setTextSize(item.letter.length() >= 4 ? 21 : item.letter.length() >= 3 ? 23 : 26);
            holder.hint.setText(item.hint == null ? "" : item.hint);
            boolean isPlaying = position == playing;
            boolean isSelected = position == selected;
            holder.card.setBackground(isPlaying
                    ? gradient(0xFF756DE4, 0xFF9A73E9, dp(17), 0, 0)
                    : isSelected
                    ? rounded(COLOR_BRAND_SOFT, dp(17), 0xFFBDB8F6, dp(1))
                    : rounded(Color.WHITE, dp(17), COLOR_STROKE, dp(1)));
            holder.letter.setTextColor(isPlaying ? Color.WHITE : COLOR_TEXT);
            holder.hint.setTextColor(isPlaying ? 0xE6FFFFFF : COLOR_SUB);
            holder.card.setScaleX(isPlaying ? 1.035f : 1f);
            holder.card.setScaleY(isPlaying ? 1.035f : 1f);
            holder.card.setElevation(isPlaying ? dp(4) : 0f);
            holder.card.setContentDescription(item.letter + " " + item.hint);
            holder.card.setOnClickListener(v -> listener.onClick(item));
        }

        @Override public int getItemCount() {
            return items.size();
        }
    }

    private static final class PinyinHolder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final TextView letter;
        final TextView hint;

        PinyinHolder(@NonNull LinearLayout card, TextView letter, TextView hint) {
            super(card);
            this.card = card;
            this.letter = letter;
            this.hint = hint;
        }
    }

    private GradientDrawable gradient(int start, int end, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }
}
