package com.chat.learning;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Full-screen Chinese word cards for Burmese learners with FSRS scheduling. */
public class WordFullscreenActivity extends AppCompatActivity {
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_DATA_URL = "data_url";
    public static final String EXTRA_DATA_SHA256 = "data_sha256";
    public static final String EXTRA_DATA_VERSION = "data_version";

    private static final int COLOR_TEXT = 0xFF152033;
    private static final int COLOR_SUB = 0xFF64748B;
    private static final int COLOR_BRAND = 0xFF4F46E5;
    private static final int COLOR_BLUE = 0xFF2563EB;
    private static final int COLOR_PURPLE = 0xFF7C3AED;
    private static final int COLOR_ORANGE = 0xFFEA580C;
    private static final int COLOR_GREEN = 0xFF059669;
    private static final int COLOR_RED = 0xFFE11D48;
    private static final int COLOR_GOLD = 0xFFF59E0B;
    private static final int COLOR_DIVIDER = 0xFFE5E7EB;
    private static final String SP = "tsdd_word_study_v2";

    private String packId;
    private String title;
    private String dataUrl;
    private String dataSha256;
    private int dataVersion;
    private SharedPreferences settings;
    private WordProgressStore progressStore;
    private final WordFsrsScheduler scheduler = new WordFsrsScheduler();
    private final ArrayList<WordItem> queue = new ArrayList<>();
    private final Map<String, Integer> againRepeats = new HashMap<>();
    private final Map<String, Integer> hardRepeats = new HashMap<>();
    private Map<WordFsrsScheduler.Rating, WordFsrsScheduler.Result> previews = new EnumMap<>(WordFsrsScheduler.Rating.class);

    private WordCardContainer card;
    private FrameLayout cardHost;
    private View leftBackdrop;
    private View rightBackdrop;
    private View downBackdrop;
    private TextView leftMark;
    private TextView rightMark;
    private TextView downMark;
    private LinearLayout front;
    private ScrollView backScroll;
    private LinearLayout backContent;
    private TextView wordView;
    private TextView pinyinView;
    private TextView phoneticView;
    private TextView favoriteView;
    private TextView progressView;
    private TextView planView;
    private TextView autoReadView;
    private TextView pinyinToggleView;
    private final EnumMap<WordFsrsScheduler.Rating, TextView> ratingButtons = new EnumMap<>(WordFsrsScheduler.Rating.class);

    private boolean frontFace = true;
    private boolean thresholdFeedbackSent;
    private boolean pendingGestureRender;
    private boolean sessionFinished;
    private int totalInitial;
    private int countAgain;
    private int countHard;
    private int countGood;
    private int countEasy;
    private long sessionStartedAt;
    private ToneGenerator toneGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(0xFFEAF2FF);
        window.setNavigationBarColor(0xFFF6F8FC);

        packId = safe(getIntent().getStringExtra(EXTRA_LEVEL), "hsk1");
        title = safe(getIntent().getStringExtra(EXTRA_TITLE), packId.toUpperCase(Locale.ROOT));
        dataUrl = safe(getIntent().getStringExtra(EXTRA_DATA_URL), "");
        dataSha256 = safe(getIntent().getStringExtra(EXTRA_DATA_SHA256), "");
        dataVersion = Math.max(0, getIntent().getIntExtra(EXTRA_DATA_VERSION, 0));
        settings = getSharedPreferences(SP, Context.MODE_PRIVATE);
        progressStore = new WordProgressStore(this);
        try { toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 28); } catch (Throwable ignored) {}
        sessionStartedAt = System.currentTimeMillis();

        buildLayout();
        loadWords();
        maybeShowGuide();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (toneGenerator != null) toneGenerator.release(); } catch (Throwable ignored) {}
        try { progressStore.close(); } catch (Throwable ignored) {}
    }

    private void loadWords() {
        List<WordItem> local = LearningWordRepository.loadLocal(this, packId);
        boolean hasRemote = LearningRemoteContent.resolveUrl(this, dataUrl).length() > 0;
        if (local != null && !local.isEmpty()) {
            setWords(local);
        } else if (hasRemote) {
            showLoading();
        } else {
            showUnavailable();
        }
        LearningWordRepository.refresh(this, packId, dataUrl, dataSha256, dataVersion,
                new LearningWordRepository.Callback() {
                    @Override public void onLoaded(List<WordItem> words, boolean refreshed) {
                        runOnUiThread(() -> {
                            if (totalInitial == 0 && !sessionFinished) setWords(words);
                        });
                    }
                    @Override public void onError(Throwable error) {
                        runOnUiThread(() -> {
                            if (totalInitial == 0 && !sessionFinished) showLoadError();
                        });
                    }
                });
    }



    private void showUnavailable() {
        sessionFinished = true;
        frontFace = true;
        front.setVisibility(View.VISIBLE);
        backScroll.setVisibility(View.GONE);
        wordView.setText("—");
        pinyinView.setText(R.string.word_pack_unavailable);
        pinyinView.setVisibility(View.VISIBLE);
        phoneticView.setVisibility(View.GONE);
        progressView.setText(title);
        favoriteView.setVisibility(View.GONE);
        for (TextView button : ratingButtons.values()) button.setEnabled(false);
    }

    private void showLoading() {
        frontFace = true;
        front.setVisibility(View.VISIBLE);
        backScroll.setVisibility(View.GONE);
        wordView.setText("…");
        pinyinView.setText(R.string.word_loading_remote);
        pinyinView.setVisibility(View.VISIBLE);
        phoneticView.setVisibility(View.GONE);
        progressView.setText(title);
        favoriteView.setVisibility(View.GONE);
        for (TextView button : ratingButtons.values()) button.setEnabled(false);
    }

    private void showLoadError() {
        wordView.setText("!");
        pinyinView.setText(R.string.word_remote_load_failed);
        pinyinView.setVisibility(View.VISIBLE);
        phoneticView.setVisibility(View.GONE);
    }

    private void setWords(List<WordItem> source) {
        queue.clear();
        if (source != null) queue.addAll(source);
        Map<String, WordFsrsScheduler.CardState> states = progressStore.loadPack(packId);
        long now = System.currentTimeMillis();
        Collections.sort(queue, Comparator.comparingLong(item -> {
            WordFsrsScheduler.CardState state = states.get(item.id);
            if (state == null || state.reviewCount == 0) return Long.MAX_VALUE - 1;
            return state.dueAt <= now ? state.dueAt : Long.MAX_VALUE;
        }));
        totalInitial = queue.size();
        sessionFinished = false;
        renderCurrent();
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(pageBackground());
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), dp(14));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(52)));

        cardHost = new FrameLayout(this);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        hostParams.setMargins(0, dp(8), 0, 0);
        page.addView(cardHost, hostParams);

        leftBackdrop = backdrop(0xFFFFDCE5);
        rightBackdrop = backdrop(0xFFD4F8E7);
        downBackdrop = backdrop(0xFFFFE8A3);
        cardHost.addView(leftBackdrop, new FrameLayout.LayoutParams(-1, -1));
        cardHost.addView(rightBackdrop, new FrameLayout.LayoutParams(-1, -1));
        cardHost.addView(downBackdrop, new FrameLayout.LayoutParams(-1, -1));

        leftMark = dragMark(getString(R.string.word_mark_unknown), COLOR_RED);
        rightMark = dragMark(getString(R.string.word_mark_known), COLOR_GREEN);
        downMark = dragMark(getString(R.string.word_pull_favorite_hint), 0xFFB45309);
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(dp(132), dp(54), Gravity.START | Gravity.CENTER_VERTICAL);
        leftLp.setMargins(dp(12), 0, 0, 0);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(dp(132), dp(54), Gravity.END | Gravity.CENTER_VERTICAL);
        rightLp.setMargins(0, 0, dp(12), 0);
        FrameLayout.LayoutParams downLp = new FrameLayout.LayoutParams(dp(190), dp(50), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        downLp.setMargins(0, dp(14), 0, 0);
        cardHost.addView(leftMark, leftLp);
        cardHost.addView(rightMark, rightLp);
        cardHost.addView(downMark, downLp);
        resetBackdrop();

        card = new WordCardContainer(this);
        card.setBackground(cardBackground());
        card.setElevation(dp(5));
        card.setGestureListener(new WordCardContainer.Listener() {
            @Override public boolean isFrontFace() { return frontFace; }
            @Override public void onDrag(WordCardContainer.Direction direction, float progress, boolean crossed) {
                showDrag(direction, progress, crossed);
            }
            @Override public boolean onCommit(WordCardContainer.Direction direction) {
                return commitGesture(direction);
            }
            @Override public void onClickCard() { flipCard(); }
            @Override public void onReset() {
                resetBackdrop();
                if (pendingGestureRender) {
                    pendingGestureRender = false;
                    renderCurrent();
                } else {
                    bindFavorite();
                }
            }
        });
        cardHost.addView(card, new FrameLayout.LayoutParams(-1, -1));

        favoriteView = label("☆", 29, COLOR_GOLD, true);
        favoriteView.setGravity(Gravity.CENTER);
        favoriteView.setOnClickListener(v -> toggleFavorite());
        FrameLayout.LayoutParams favoriteLp = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.END | Gravity.TOP);
        favoriteLp.setMargins(0, dp(4), dp(6), 0);
        card.addView(favoriteView, favoriteLp);

        front = buildFront();
        FrameLayout.LayoutParams faceLp = new FrameLayout.LayoutParams(-1, -1);
        faceLp.setMargins(0, dp(52), 0, dp(90));
        card.addView(front, faceLp);

        backScroll = new ScrollView(this);
        backScroll.setFillViewport(true);
        backScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        backScroll.setVerticalScrollBarEnabled(false);
        backContent = new LinearLayout(this);
        backContent.setOrientation(LinearLayout.VERTICAL);
        backContent.setPadding(dp(24), dp(8), dp(24), dp(28));
        backScroll.addView(backContent, new ScrollView.LayoutParams(-1, -2));
        card.addView(backScroll, faceLp);

        LinearLayout ratingRow = buildRatingRow();
        FrameLayout.LayoutParams ratingLp = new FrameLayout.LayoutParams(-1, dp(82), Gravity.BOTTOM);
        ratingLp.setMargins(dp(12), 0, dp(12), dp(8));
        card.addView(ratingRow, ratingLp);
    }

    private View buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        planView = chip(getString(R.string.word_today_plan), 0xFFFFFFFF, COLOR_TEXT);
        planView.setOnClickListener(v -> showPlan());
        top.addView(planView, new LinearLayout.LayoutParams(0, dp(38), 1f));

        autoReadView = chip("", 0xFFE9F8F1, COLOR_GREEN);
        autoReadView.setOnClickListener(v -> {
            settings.edit().putBoolean("auto_read", !autoRead()).apply();
            bindTopControls();
            if (autoRead()) speakWord();
        });
        LinearLayout.LayoutParams autoLp = new LinearLayout.LayoutParams(dp(92), dp(38));
        autoLp.setMargins(dp(8), 0, 0, 0);
        top.addView(autoReadView, autoLp);

        pinyinToggleView = chip("", 0xFFEAF1FF, COLOR_BLUE);
        pinyinToggleView.setOnClickListener(v -> {
            settings.edit().putBoolean("show_pinyin", !showPinyin()).apply();
            bindTopControls();
            bindFront();
        });
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(dp(76), dp(38));
        pinLp.setMargins(dp(8), 0, 0, 0);
        top.addView(pinyinToggleView, pinLp);

        TextView more = label("⋮", 28, COLOR_TEXT, true);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(this::showMoreMenu);
        top.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));
        return top;
    }

    private LinearLayout buildFront() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(20), dp(12), dp(20), dp(12));

        progressView = label("", 12, COLOR_SUB, false);
        progressView.setGravity(Gravity.CENTER);
        box.addView(progressView, new LinearLayout.LayoutParams(-1, -2));

        box.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.55f));
        wordView = label("", 54, COLOR_TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        box.addView(wordView, new LinearLayout.LayoutParams(-1, -2));

        pinyinView = label("", 21, COLOR_BLUE, true);
        pinyinView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pyLp = new LinearLayout.LayoutParams(-1, -2);
        pyLp.setMargins(0, dp(8), 0, 0);
        box.addView(pinyinView, pyLp);

        phoneticView = label("", 18, 0xFFB45309, true);
        phoneticView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams phLp = new LinearLayout.LayoutParams(-1, -2);
        phLp.setMargins(0, dp(7), 0, 0);
        box.addView(phoneticView, phLp);

        TextView tap = label(getString(R.string.word_front_tap), 12, 0xFF94A3B8, false);
        tap.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tapLp = new LinearLayout.LayoutParams(-1, -2);
        tapLp.setMargins(0, dp(14), 0, 0);
        box.addView(tap, tapLp);

        box.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.55f));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        tools.addView(toolChip("🔊", R.string.word_action_tts, 0xFFE7F0FF, COLOR_BLUE, v -> speakWord()), weighted());
        addGap(tools, 7);
        tools.addView(toolChip("拼", R.string.word_action_spelling, 0xFFF0E9FF, COLOR_PURPLE, v -> speakSpelling()), weighted());
        addGap(tools, 7);
        tools.addView(toolChip("笔", R.string.word_action_stroke, 0xFFFFEEDC, COLOR_ORANGE, v -> openStroke()), weighted());
        addGap(tools, 7);
        tools.addView(toolChip("录", R.string.word_action_pronunciation, 0xFFE1F7EC, COLOR_GREEN, v -> openPronunciation()), weighted());
        box.addView(tools, new LinearLayout.LayoutParams(-1, dp(54)));
        return box;
    }

    private LinearLayout buildRatingRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        addRating(row, WordFsrsScheduler.Rating.AGAIN, R.string.word_action_unknown, 0xFFFFE7EC, COLOR_RED);
        addGap(row, 6);
        addRating(row, WordFsrsScheduler.Rating.HARD, R.string.word_action_hard, 0xFFFFF0D9, 0xFFD97706);
        addGap(row, 6);
        addRating(row, WordFsrsScheduler.Rating.GOOD, R.string.word_action_known, 0xFFE2F7EC, COLOR_GREEN);
        addGap(row, 6);
        addRating(row, WordFsrsScheduler.Rating.EASY, R.string.word_action_easy, 0xFFE7EDFF, COLOR_BRAND);
        return row;
    }

    private void addRating(LinearLayout row, WordFsrsScheduler.Rating rating, int titleRes, int bg, int color) {
        TextView button = label("", 12, color, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(bg, dp(15), 0, 0));
        button.setTag(titleRes);
        button.setOnClickListener(v -> onRatingClick(rating));
        row.addView(button, new LinearLayout.LayoutParams(0, dp(64), 1f));
        ratingButtons.put(rating, button);
    }

    private void renderCurrent() {
        if (queue.isEmpty()) {
            showCompletion();
            return;
        }
        sessionFinished = false;
        favoriteView.setVisibility(View.VISIBLE);
        for (TextView button : ratingButtons.values()) button.setEnabled(true);
        frontFace = true;
        front.setVisibility(View.VISIBLE);
        backScroll.setVisibility(View.GONE);
        backScroll.scrollTo(0, 0);
        bindTopControls();
        bindFront();
        bindBack();
        bindFavorite();
        bindRatings();
        resetBackdrop();
        card.resetImmediately();
        if (autoRead()) card.postDelayed(this::speakWord, 160);
    }

    private void bindTopControls() {
        int due = progressStore.countDue(packId, System.currentTimeMillis());
        planView.setText(getString(R.string.word_today_plan_count, due, queue.size()));
        autoReadView.setText(autoRead() ? getString(R.string.word_auto_read_on) : getString(R.string.word_auto_read_off));
        pinyinToggleView.setText(showPinyin() ? getString(R.string.word_pinyin_on) : getString(R.string.word_pinyin_off));
    }

    private void bindFront() {
        WordItem item = current();
        if (item == null) return;
        wordView.setText(item.word);
        pinyinView.setText(item.pinyin);
        pinyinView.setVisibility(showPinyin() && item.pinyin.length() > 0 ? View.VISIBLE : View.GONE);
        phoneticView.setText(item.phoneticMy);
        phoneticView.setVisibility(showPhonetic() && item.phoneticMy.length() > 0 ? View.VISIBLE : View.GONE);
        int done = Math.max(0, totalInitial - Math.min(totalInitial, queue.size()));
        progressView.setText(title + "  ·  " + Math.min(totalInitial, done + 1) + "/" + Math.max(1, totalInitial));
    }

    private void bindBack() {
        WordItem item = current();
        backContent.removeAllViews();
        if (item == null) return;

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        TextView backWord = label(item.word, 31, COLOR_TEXT, true);
        header.addView(backWord, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView backPinyin = label(showPinyin() ? item.pinyin : "", 16, COLOR_BLUE, true);
        backPinyin.setGravity(Gravity.END);
        header.addView(backPinyin, new LinearLayout.LayoutParams(0, -2, 1f));
        backContent.addView(header, new LinearLayout.LayoutParams(-1, -2));

        if (item.partOfSpeech.length() > 0) {
            TextView pos = label(getString(R.string.word_part_of_speech, item.partOfSpeech), 12, COLOR_BRAND, true);
            pos.setBackground(rounded(0xFFECEBFF, dp(10), 0, 0));
            pos.setPadding(dp(9), dp(4), dp(9), dp(4));
            LinearLayout.LayoutParams posLp = new LinearLayout.LayoutParams(-2, -2);
            posLp.setMargins(0, dp(8), 0, 0);
            backContent.addView(pos, posLp);
        }
        addDivider(backContent, 14, 16);
        addSection(backContent, R.string.word_core_meaning, item.meaningMy, COLOR_BRAND);
        addSection(backContent, R.string.word_label_usage, item.usageSceneMy, COLOR_ORANGE);
        addExampleSection(backContent, item);
        addSection(backContent, R.string.word_label_collocations, join(item.collocations), COLOR_PURPLE);
        addSection(backContent, R.string.word_label_synonyms, join(item.synonyms), COLOR_GREEN);
        addSection(backContent, R.string.word_label_antonyms, join(item.antonyms), COLOR_RED);
        addSection(backContent, R.string.word_label_notes, item.notesMy, 0xFFB45309);

        TextView hint = label(getString(R.string.word_back_hint), 11, 0xFF94A3B8, false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(16), 0, dp(6));
        backContent.addView(hint, hintLp);
    }

    private void addExampleSection(LinearLayout parent, WordItem item) {
        if (item.example.length() == 0) return;
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = sectionTitle(getString(R.string.word_label_example), COLOR_BLUE);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView speaker = chip("🔊", 0xFFE7F0FF, COLOR_BLUE);
        speaker.setOnClickListener(v -> LearningTtsBridge.speak(this, item.example,
                LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_EXAMPLE));
        heading.addView(speaker, new LinearLayout.LayoutParams(dp(46), dp(34)));
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.setMargins(0, dp(13), 0, 0);
        parent.addView(heading, headingLp);

        TextView chinese = label("", 20, COLOR_TEXT, false);
        chinese.setText(highlightWord(item.example, item.word));
        chinese.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams chineseLp = new LinearLayout.LayoutParams(-1, -2);
        chineseLp.setMargins(0, dp(7), 0, 0);
        parent.addView(chinese, chineseLp);

        if (showPinyin() && item.examplePinyin.length() > 0) {
            TextView py = label(item.examplePinyin, 14, COLOR_BLUE, false);
            py.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams pyLp = new LinearLayout.LayoutParams(-1, -2);
            pyLp.setMargins(0, dp(6), 0, 0);
            parent.addView(py, pyLp);
        }
        if (item.exampleMy.length() > 0) {
            TextView my = label(item.exampleMy, 16, COLOR_SUB, false);
            my.setLineSpacing(dp(5), 1.08f);
            LinearLayout.LayoutParams myLp = new LinearLayout.LayoutParams(-1, -2);
            myLp.setMargins(0, dp(8), 0, 0);
            parent.addView(my, myLp);
        }
    }

    private CharSequence highlightWord(String sentence, String target) {
        SpannableString text = new SpannableString(sentence);
        if (target == null || target.length() == 0) return text;
        int from = 0;
        while (from < sentence.length()) {
            int start = sentence.indexOf(target, from);
            if (start < 0) break;
            int end = start + target.length();
            text.setSpan(new ForegroundColorSpan(COLOR_BRAND), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(1.12f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = end;
        }
        return text;
    }

    private void addSection(LinearLayout parent, int titleRes, String value, int color) {
        if (value == null || value.trim().length() == 0) return;
        TextView heading = sectionTitle(getString(titleRes), color);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.setMargins(0, dp(13), 0, 0);
        parent.addView(heading, headingLp);
        TextView content = label(value.trim(), 16, COLOR_TEXT, false);
        content.setLineSpacing(dp(5), 1.08f);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, -2);
        contentLp.setMargins(0, dp(7), 0, 0);
        parent.addView(content, contentLp);
    }

    private void bindRatings() {
        WordItem item = current();
        if (item == null) return;
        WordFsrsScheduler.CardState state = progressStore.load(item.packId, item.id);
        previews = scheduler.preview(state, System.currentTimeMillis());
        for (WordFsrsScheduler.Rating rating : WordFsrsScheduler.Rating.values()) {
            TextView button = ratingButtons.get(rating);
            if (button == null) continue;
            int titleRes = (Integer) button.getTag();
            WordFsrsScheduler.Result result = previews.get(rating);
            button.setText(getString(titleRes) + "\n" + formatInterval(result == null ? 0 : result.intervalMillis));
        }
    }

    private void onRatingClick(WordFsrsScheduler.Rating rating) {
        if (queue.isEmpty()) return;
        if (frontFace && rating != WordFsrsScheduler.Rating.AGAIN) {
            flipCard();
            Toast.makeText(this, R.string.word_check_answer_first, Toast.LENGTH_SHORT).show();
            return;
        }
        rateCurrent(rating, false);
    }

    private void rateCurrent(WordFsrsScheduler.Rating rating, boolean fromGesture) {
        WordItem item = current();
        if (item == null) return;
        WordFsrsScheduler.CardState state = progressStore.load(item.packId, item.id);
        WordFsrsScheduler.Result result = scheduler.review(state, rating, System.currentTimeMillis());
        progressStore.save(item.packId, item.id, result.card);
        queue.remove(0);
        if (rating == WordFsrsScheduler.Rating.AGAIN) {
            countAgain++;
            int count = againRepeats.getOrDefault(item.progressKey(), 0);
            if (count < 2) {
                againRepeats.put(item.progressKey(), count + 1);
                queue.add(item);
            }
        } else if (rating == WordFsrsScheduler.Rating.HARD) {
            countHard++;
            int count = hardRepeats.getOrDefault(item.progressKey(), 0);
            if (count < 1) {
                hardRepeats.put(item.progressKey(), count + 1);
                queue.add(item);
            }
        } else if (rating == WordFsrsScheduler.Rating.GOOD) {
            countGood++;
        } else {
            countEasy++;
        }
        if (fromGesture) {
            pendingGestureRender = true;
        } else {
            card.animate().alpha(0.45f).scaleX(0.97f).scaleY(0.97f).setDuration(90)
                    .withEndAction(() -> {
                        card.setAlpha(1f);
                        card.setScaleX(1f);
                        card.setScaleY(1f);
                        renderCurrent();
                    }).start();
        }
    }

    private boolean commitGesture(WordCardContainer.Direction direction) {
        if (direction == WordCardContainer.Direction.LEFT) {
            rateCurrent(WordFsrsScheduler.Rating.AGAIN, true);
            return true;
        }
        if (direction == WordCardContainer.Direction.RIGHT) {
            if (frontFace) {
                flipCard();
                Toast.makeText(this, R.string.word_check_answer_first, Toast.LENGTH_SHORT).show();
                return false;
            }
            rateCurrent(WordFsrsScheduler.Rating.GOOD, true);
            return true;
        }
        if (direction == WordCardContainer.Direction.DOWN && frontFace) {
            toggleFavorite();
            return true;
        }
        return false;
    }

    private void showDrag(WordCardContainer.Direction direction, float progress, boolean crossed) {
        float alpha = Math.min(1f, Math.max(0.18f, progress));
        leftBackdrop.setAlpha(direction == WordCardContainer.Direction.LEFT ? alpha : 0f);
        rightBackdrop.setAlpha(direction == WordCardContainer.Direction.RIGHT ? alpha : 0f);
        downBackdrop.setAlpha(direction == WordCardContainer.Direction.DOWN ? alpha : 0f);
        leftMark.setAlpha(direction == WordCardContainer.Direction.LEFT ? alpha : 0f);
        rightMark.setAlpha(direction == WordCardContainer.Direction.RIGHT ? alpha : 0f);
        downMark.setAlpha(direction == WordCardContainer.Direction.DOWN ? alpha : 0f);
        if (direction == WordCardContainer.Direction.DOWN) {
            downMark.setText(crossed
                    ? getString(progressStore.isFavorite(current().packId, current().id)
                    ? R.string.word_pull_unfavorite_release : R.string.word_pull_favorite_release)
                    : getString(R.string.word_pull_favorite_hint));
        } else if (direction == WordCardContainer.Direction.RIGHT && frontFace) {
            rightMark.setText(crossed ? getString(R.string.word_check_answer_first) : getString(R.string.word_mark_known));
        }
        if (crossed && !thresholdFeedbackSent) {
            thresholdFeedbackSent = true;
            haptic();
            try { if (toneGenerator != null) toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 45); } catch (Throwable ignored) {}
        } else if (!crossed) {
            thresholdFeedbackSent = false;
        }
    }

    private void resetBackdrop() {
        thresholdFeedbackSent = false;
        leftBackdrop.setAlpha(0f);
        rightBackdrop.setAlpha(0f);
        downBackdrop.setAlpha(0f);
        leftMark.setAlpha(0f);
        rightMark.setAlpha(0f);
        downMark.setAlpha(0f);
        leftMark.setText(R.string.word_mark_unknown);
        rightMark.setText(R.string.word_mark_known);
        downMark.setText(R.string.word_pull_favorite_hint);
    }

    private void flipCard() {
        if (queue.isEmpty()) return;
        frontFace = !frontFace;
        View hide = frontFace ? backScroll : front;
        View show = frontFace ? front : backScroll;
        card.animate().rotationY(86f).setDuration(100).withEndAction(() -> {
            hide.setVisibility(View.GONE);
            show.setVisibility(View.VISIBLE);
            card.setRotationY(-86f);
            card.animate().rotationY(0f).setDuration(110).start();
        }).start();
    }

    private void toggleFavorite() {
        WordItem item = current();
        if (item == null) return;
        boolean favorite = progressStore.toggleFavorite(item.packId, item.id);
        bindFavorite();
        Toast.makeText(this, favorite ? R.string.word_favorite_added : R.string.word_favorite_removed,
                Toast.LENGTH_SHORT).show();
    }

    private void bindFavorite() {
        WordItem item = current();
        if (item == null) return;
        boolean favorite = progressStore.isFavorite(item.packId, item.id);
        favoriteView.setText(favorite ? "★" : "☆");
        favoriteView.setContentDescription(getString(favorite ? R.string.word_favorite_added : R.string.word_favorite_removed));
    }

    private void speakWord() {
        WordItem item = current();
        if (item != null) LearningTtsBridge.speak(this, item.word,
                LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_WORD);
    }

    private void speakSpelling() {
        WordItem item = current();
        if (item != null) LearningTtsBridge.speak(this, item.word,
                LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_SPELLING);
    }

    private void openStroke() {
        WordItem item = current();
        if (item == null) return;
        Intent intent = new Intent(this, WordStrokeActivity.class);
        intent.putExtra("word", item.word);
        intent.putExtra("pinyin", item.pinyin);
        startActivity(intent);
    }

    private void openPronunciation() {
        WordItem item = current();
        if (item == null) return;
        Intent intent = new Intent(this, WordPronunciationActivity.class);
        intent.putExtra("word", item.word);
        intent.putExtra("pinyin", item.pinyin);
        startActivity(intent);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(showPhonetic() ? R.string.word_menu_hide_my_phonetic : R.string.word_menu_show_my_phonetic);
        menu.getMenu().add(R.string.word_menu_show_guide);
        menu.getMenu().add(R.string.word_menu_tts_settings);
        menu.setOnMenuItemClickListener(item -> handleMenu(item));
        menu.show();
    }

    private boolean handleMenu(MenuItem item) {
        String titleText = String.valueOf(item.getTitle());
        if (titleText.equals(getString(R.string.word_menu_show_my_phonetic))
                || titleText.equals(getString(R.string.word_menu_hide_my_phonetic))) {
            settings.edit().putBoolean("show_phonetic", !showPhonetic()).apply();
            bindFront();
            return true;
        }
        if (titleText.equals(getString(R.string.word_menu_show_guide))) {
            showGuide(false);
            return true;
        }
        if (titleText.equals(getString(R.string.word_menu_tts_settings))) {
            Toast.makeText(this, R.string.word_tts_settings_hint, Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void showPlan() {
        int due = progressStore.countDue(packId, System.currentTimeMillis());
        new AlertDialog.Builder(this)
                .setTitle(R.string.word_today_plan)
                .setMessage(getString(R.string.word_plan_message, due, queue.size(), totalInitial))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void maybeShowGuide() {
        if (settings.getBoolean("guide_never", false)) return;
        showGuide(true);
    }

    private void showGuide(boolean automatic) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.word_guide_title)
                .setMessage(R.string.word_guide_body)
                .setPositiveButton(R.string.word_guide_ok, null)
                .setNegativeButton(R.string.word_guide_never, (d, which) -> settings.edit().putBoolean("guide_never", true).apply())
                .create();
        dialog.show();
    }

    private void showCompletion() {
        sessionFinished = true;
        cardHost.removeAllViews();
        LinearLayout done = new LinearLayout(this);
        done.setOrientation(LinearLayout.VERTICAL);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(28), dp(28), dp(28), dp(28));
        done.setBackground(cardBackground());
        TextView icon = label("✓", 52, COLOR_GREEN, true);
        icon.setGravity(Gravity.CENTER);
        done.addView(icon, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = label(getString(R.string.word_session_complete), 25, COLOR_TEXT, true);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.setMargins(0, dp(10), 0, 0);
        done.addView(heading, headingLp);
        long minutes = Math.max(1, (System.currentTimeMillis() - sessionStartedAt) / 60_000L);
        TextView stats = label(getString(R.string.word_session_stats,
                countAgain, countHard, countGood, countEasy, minutes), 16, COLOR_SUB, false);
        stats.setGravity(Gravity.CENTER);
        stats.setLineSpacing(dp(8), 1f);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(-1, -2);
        statsLp.setMargins(0, dp(18), 0, 0);
        done.addView(stats, statsLp);
        TextView finish = chip(getString(R.string.word_finish), 0xFF4F46E5, Color.WHITE);
        finish.setGravity(Gravity.CENTER);
        finish.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams finishLp = new LinearLayout.LayoutParams(-1, dp(50));
        finishLp.setMargins(0, dp(24), 0, 0);
        done.addView(finish, finishLp);
        FrameLayout.LayoutParams doneLp = new FrameLayout.LayoutParams(-1, -1);
        doneLp.setMargins(0, dp(18), 0, dp(18));
        cardHost.addView(done, doneLp);
    }

    private boolean autoRead() { return settings.getBoolean("auto_read", true); }
    private boolean showPinyin() { return settings.getBoolean("show_pinyin", true); }
    private boolean showPhonetic() { return settings.getBoolean("show_phonetic", true); }
    private WordItem current() { return queue.isEmpty() ? null : queue.get(0); }

    private String formatInterval(long millis) {
        if (millis < 60_000L) return getString(R.string.word_review_soon);
        long minutes = Math.max(1, Math.round(millis / 60_000.0));
        if (minutes < 60) return getString(R.string.word_review_minutes, minutes);
        long hours = Math.max(1, Math.round(minutes / 60.0));
        if (hours < 24) return getString(R.string.word_review_hours, hours);
        long days = Math.max(1, Math.round(hours / 24.0));
        if (days < 30) return getString(R.string.word_review_days, days);
        long months = Math.max(1, Math.round(days / 30.0));
        if (months < 12) return getString(R.string.word_review_months, months);
        return getString(R.string.word_review_years, Math.max(1, Math.round(months / 12.0)));
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().length() == 0) continue;
            if (out.length() > 0) out.append("  ·  ");
            out.append(value.trim());
        }
        return out.toString();
    }

    private void haptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                card.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                return;
            }
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(24, 75));
            else vibrator.vibrate(24);
        } catch (Throwable ignored) {}
    }

    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -1, 1f); }
    private void addGap(LinearLayout row, int width) { row.addView(new View(this), new LinearLayout.LayoutParams(dp(width), 1)); }

    private TextView toolChip(String icon, int titleRes, int bg, int color, View.OnClickListener listener) {
        TextView view = label(icon + "  " + getString(titleRes), 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(bg, dp(15), 0, 0));
        view.setOnClickListener(listener);
        return view;
    }

    private TextView chip(String text, int bg, int color) {
        TextView view = label(text, 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(4), dp(9), dp(4));
        view.setBackground(rounded(bg, dp(14), 0x16000000, dp(1)));
        return view;
    }

    private TextView sectionTitle(String text, int color) {
        TextView view = label(text, 13, color, true);
        view.setAllCaps(false);
        return view;
    }

    private TextView dragMark(String text, int color) {
        TextView view = label(text, 16, color, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(0xEFFFFFFF, dp(16), 0x24000000, dp(1)));
        return view;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private View backdrop(int color) {
        View view = new View(this);
        view.setBackground(rounded(color, dp(28), 0, 0));
        return view;
    }

    private void addDivider(LinearLayout parent, int top, int bottom) {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(0, dp(top), 0, dp(bottom));
        parent.addView(divider, lp);
    }

    private GradientDrawable pageBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFEAF2FF, 0xFFF7F9FD, 0xFFF4F6FA});
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = rounded(Color.WHITE, dp(28), 0x1F94A3B8, dp(1));
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

    private String safe(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }
}
