package com.chat.learning;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 全屏背单词：左右滑明显换词、点击翻面、下拉收藏、拼读音频、跟读练习。
 *
 * 说明：
 * - 单词分类卡片由 LearningDirectoryActivity 负责。
 * - 具体词库数据来自 assets/learning/words/{level}.json，一个分类一个大 JSON。
 * - TTS / ASR 不在本文件写死在线地址，通过 LearningTtsBridge / LearningAsrBridge 接插件。
 */
public class WordFullscreenActivity extends AppCompatActivity {
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_TITLE = "title";

    private static final int COLOR_BG_TOP = 0xFFEAF4FF;
    private static final int COLOR_BG_BOTTOM = 0xFFF8FBFF;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_SUB = 0xFF64748B;
    private static final int COLOR_BLUE = 0xFF2563EB;
    private static final int COLOR_PHONETIC_MY = 0xFFB45309;
    private static final int COLOR_RED = 0xFFE11D48;
    private static final int COLOR_GREEN = 0xFF059669;
    private static final int COLOR_FAV = 0xFFF59E0B;

    private static final String SP_NAME = "tsdd_word_study";
    private static final String SP_SHOW_GUIDE = "show_word_guide";
    private static final String SP_SHOW_PINYIN = "show_pinyin";
    private static final String SP_SHOW_MY_PHONETIC = "show_my_phonetic";

    private final ArrayList<WordItem> words = new ArrayList<>();
    private int index = 0;
    private boolean flipped = false;
    private boolean judging = false;
    private boolean pullReady = false;
    private Axis axis = Axis.NONE;
    private float downX;
    private float downY;
    private int touchSlop;
    private String level;
    private String title;

    private FrameLayout root;
    private FrameLayout cardHost;
    private FrameLayout card;
    private LinearLayout cardContent;
    private TextView progress;
    private TextView pinyinSwitch;
    private TextView moreMenu;
    private TextView favoriteButton;
    private TextView word;
    private TextView pinyin;
    private TextView phoneticMy;
    private TextView meaning;
    private TextView example;
    private TextView extraInfo;
    private TextView hint;
    private TextView leftMark;
    private TextView rightMark;
    private TextView pullMark;
    private GestureDetector gestureDetector;
    private ReviewStore reviewStore;
    private SharedPreferences settings;

    private enum Axis { NONE, HORIZONTAL, VERTICAL }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG_TOP);
        window.setNavigationBarColor(COLOR_BG_BOTTOM);

        level = getIntent().getStringExtra(EXTRA_LEVEL);
        if (level == null || level.length() == 0) level = "hsk1";
        title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.length() == 0) title = level.toUpperCase();

        settings = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        reviewStore = new ReviewStore(this);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                flip();
                return true;
            }
        });

        seedWords();
        sortWordsByReview();
        buildLayout();
        bind();
        maybeShowGuide();
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackground(pageBg());
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(16), dp(18), dp(18));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(54)));

        pinyinSwitch = text("", 14, COLOR_BLUE, true);
        pinyinSwitch.setGravity(Gravity.CENTER_VERTICAL);
        pinyinSwitch.setPadding(dp(2), 0, dp(10), 0);
        pinyinSwitch.setOnClickListener(v -> togglePinyin());
        top.addView(pinyinSwitch, new LinearLayout.LayoutParams(dp(110), -1));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView titleView = text(title, 17, COLOR_TEXT, true);
        titleView.setGravity(Gravity.CENTER);
        titleBox.addView(titleView, new LinearLayout.LayoutParams(-1, 0, 1f));
        progress = text("", 12, COLOR_SUB, false);
        progress.setGravity(Gravity.CENTER);
        titleBox.addView(progress, new LinearLayout.LayoutParams(-1, 0, 1f));

        moreMenu = text("⋮", 30, COLOR_TEXT, true);
        moreMenu.setGravity(Gravity.CENTER);
        moreMenu.setOnClickListener(v -> toggleMyPhonetic());
        top.addView(moreMenu, new LinearLayout.LayoutParams(dp(54), -1));

        cardHost = new FrameLayout(this);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        hostLp.setMargins(0, dp(16), 0, dp(16));
        page.addView(cardHost, hostLp);

        leftMark = mark(getString(R.string.word_mark_unknown), 0xFFFFEEF2, COLOR_RED);
        rightMark = mark(getString(R.string.word_mark_known), 0xFFECFDF5, COLOR_GREEN);
        pullMark = mark(getString(R.string.word_pull_favorite_hint), 0xFFFFF7ED, COLOR_FAV);
        FrameLayout.LayoutParams lLp = new FrameLayout.LayoutParams(dp(128), dp(52), Gravity.START | Gravity.CENTER_VERTICAL);
        lLp.setMargins(dp(12), 0, 0, 0);
        cardHost.addView(leftMark, lLp);
        FrameLayout.LayoutParams rLp = new FrameLayout.LayoutParams(dp(128), dp(52), Gravity.END | Gravity.CENTER_VERTICAL);
        rLp.setMargins(0, 0, dp(12), 0);
        cardHost.addView(rightMark, rLp);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(190), dp(48), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pLp.setMargins(0, dp(16), 0, 0);
        cardHost.addView(pullMark, pLp);
        leftMark.setAlpha(0f);
        rightMark.setAlpha(0f);
        pullMark.setAlpha(0f);

        card = new FrameLayout(this);
        card.setBackground(cardBg());
        card.setOnTouchListener(this::handleCardTouch);
        cardHost.addView(card, new FrameLayout.LayoutParams(-1, -1));

        favoriteButton = text("♡", 28, COLOR_FAV, true);
        favoriteButton.setGravity(Gravity.CENTER);
        favoriteButton.setOnClickListener(v -> toggleFavoriteWithToast());
        FrameLayout.LayoutParams favLp = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.END | Gravity.TOP);
        favLp.setMargins(0, dp(8), dp(10), 0);
        card.addView(favoriteButton, favLp);

        cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setGravity(Gravity.CENTER);
        cardContent.setPadding(dp(26), dp(70), dp(26), dp(26));
        cardContent.setOnTouchListener(this::handleCardTouch);
        card.addView(cardContent, new FrameLayout.LayoutParams(-1, -1));

        word = text("", 52, COLOR_TEXT, true);
        word.setGravity(Gravity.CENTER);
        cardContent.addView(word, new LinearLayout.LayoutParams(-1, -2));

        pinyin = text("", 22, COLOR_BLUE, true);
        pinyin.setGravity(Gravity.CENTER);
        pinyin.setPadding(0, dp(10), 0, 0);
        cardContent.addView(pinyin, new LinearLayout.LayoutParams(-1, -2));

        phoneticMy = text("", 18, COLOR_PHONETIC_MY, true);
        phoneticMy.setGravity(Gravity.CENTER);
        phoneticMy.setPadding(0, dp(6), 0, 0);
        cardContent.addView(phoneticMy, new LinearLayout.LayoutParams(-1, -2));

        meaning = text("", 23, COLOR_TEXT, true);
        meaning.setGravity(Gravity.CENTER);
        meaning.setPadding(0, dp(30), 0, 0);
        meaning.setLineSpacing(dp(4), 1f);
        cardContent.addView(meaning, new LinearLayout.LayoutParams(-1, -2));

        example = text("", 15, COLOR_SUB, false);
        example.setGravity(Gravity.CENTER);
        example.setLineSpacing(dp(4), 1f);
        example.setPadding(0, dp(18), 0, 0);
        cardContent.addView(example, new LinearLayout.LayoutParams(-1, -2));

        extraInfo = text("", 13, COLOR_SUB, false);
        extraInfo.setGravity(Gravity.CENTER);
        extraInfo.setLineSpacing(dp(4), 1f);
        extraInfo.setPadding(0, dp(14), 0, 0);
        cardContent.addView(extraInfo, new LinearLayout.LayoutParams(-1, -2));

        hint = text("", 12, COLOR_SUB, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(20), 0, 0);
        cardContent.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams toolLp = new LinearLayout.LayoutParams(-1, dp(50));
        toolLp.setMargins(0, dp(22), 0, 0);
        cardContent.addView(toolRow, toolLp);

        addToolButton(toolRow, getString(R.string.word_action_tts), () -> speakCurrentWord());
        addHorizontalGap(toolRow, 8);
        addToolButton(toolRow, getString(R.string.word_action_spelling), () -> speakCurrentSpelling());
        addHorizontalGap(toolRow, 8);
        addToolButton(toolRow, getString(R.string.word_action_stroke), () -> openStrokePractice());
        addHorizontalGap(toolRow, 8);
        addToolButton(toolRow, getString(R.string.word_action_pronunciation), () -> openPronunciationPractice());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        page.addView(actions, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView forgot = actionButton(getString(R.string.word_action_unknown), 0xFFFFEEF2, COLOR_RED);
        TextView known = actionButton(getString(R.string.word_action_known), 0xFFECFDF5, COLOR_GREEN);
        actions.addView(forgot, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams knownLp = new LinearLayout.LayoutParams(0, -1, 1f);
        knownLp.setMargins(dp(12), 0, 0, 0);
        actions.addView(known, knownLp);
        forgot.setOnClickListener(v -> judge(Sm2.QUALITY_FORGOT));
        known.setOnClickListener(v -> judge(Sm2.QUALITY_KNOWN));
    }

    private void addToolButton(LinearLayout row, String text, Runnable action) {
        TextView button = text(text, 13, COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(0xFFF8FAFC, dp(16), 0xFFE5E7EB, 1));
        button.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        row.addView(button, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private boolean handleCardTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        if (words.isEmpty() || judging) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                axis = Axis.NONE;
                pullReady = false;
                card.animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (axis == Axis.NONE && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    axis = Math.abs(dx) > Math.abs(dy) ? Axis.HORIZONTAL : Axis.VERTICAL;
                }
                if (axis == Axis.HORIZONTAL) {
                    card.setTranslationX(dx);
                    card.setRotation(dx / 30f);
                    float alpha = Math.min(1f, Math.abs(dx) / dp(120));
                    leftMark.setAlpha(dx < 0 ? alpha : 0f);
                    rightMark.setAlpha(dx > 0 ? alpha : 0f);
                    pullMark.setAlpha(0f);
                } else if (axis == Axis.VERTICAL && dy > 0 && !flipped) {
                    float move = Math.min(dp(110), dy * 0.45f);
                    card.setTranslationY(move);
                    float alpha = Math.min(1f, dy / dp(92));
                    pullMark.setAlpha(alpha);
                    boolean nowReady = dy > dp(92);
                    if (nowReady && !pullReady) {
                        pullReady = true;
                        vibrate(12);
                    }
                    pullMark.setText(isFavorite(currentId()) ? getString(R.string.word_pull_unfavorite_release) : getString(R.string.word_pull_favorite_release));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float upDx = event.getRawX() - downX;
                float upDy = event.getRawY() - downY;
                if (axis == Axis.HORIZONTAL && Math.abs(upDx) > dp(116)) {
                    judge(upDx > 0 ? Sm2.QUALITY_KNOWN : Sm2.QUALITY_FORGOT);
                } else if (axis == Axis.VERTICAL && upDy > dp(92) && !flipped) {
                    toggleFavoriteWithToast();
                    resetMotion();
                } else {
                    resetMotion();
                }
                axis = Axis.NONE;
                return true;
        }
        return true;
    }

    private void bind() {
        if (words.isEmpty()) return;
        WordItem item = words.get(index);
        flipped = false;
        updatePinyinSwitch();
        progress.setText((index + 1) + " / " + words.size() + " · " + formatReview(item.id));
        word.setText(item.word);
        pinyin.setText(item.pinyin);
        pinyin.setVisibility(showPinyin() ? View.VISIBLE : View.GONE);
        phoneticMy.setText(item.phoneticMy);
        phoneticMy.setVisibility(showMyPhonetic() && item.phoneticMy.length() > 0 ? View.VISIBLE : View.GONE);
        meaning.setText(getString(R.string.word_front_tap));
        example.setText(getString(R.string.word_front_hint));
        extraInfo.setText(spellingText(item));
        hint.setText(getString(R.string.word_gesture_hint_short));
        favoriteButton.setText(isFavorite(item.id) ? "♥" : "♡");
        resetMotion();
        card.setAlpha(0f);
        card.setTranslationX(dp(80));
        card.animate().alpha(1f).translationX(0f).rotation(0f).setDuration(150).start();
        speakCurrentWordAuto();
    }

    private void flip() {
        if (words.isEmpty() || axis != Axis.NONE) return;
        WordItem item = words.get(index);
        flipped = !flipped;
        card.animate().rotationY(90f).setDuration(80).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                card.animate().setListener(null);
                if (flipped) bindBack(item); else bindFront(item);
                card.setRotationY(-90f);
                card.animate().rotationY(0f).setDuration(100).start();
            }
        }).start();
    }

    private void bindFront(WordItem item) {
        meaning.setText(getString(R.string.word_front_tap));
        example.setText(getString(R.string.word_front_hint));
        extraInfo.setText(spellingText(item));
        hint.setText(getString(R.string.word_gesture_hint_short));
        pinyin.setVisibility(showPinyin() ? View.VISIBLE : View.GONE);
        phoneticMy.setVisibility(showMyPhonetic() && item.phoneticMy.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void bindBack(WordItem item) {
        StringBuilder m = new StringBuilder();
        if (item.meaningMy.length() > 0) m.append(getString(R.string.word_label_meaning_my)).append("\n").append(item.meaningMy);
        if (item.meaningEn.length() > 0) {
            if (m.length() > 0) m.append("\n\n");
            m.append(getString(R.string.word_label_meaning_en)).append("\n").append(item.meaningEn);
        }
        meaning.setText(m.length() == 0 ? item.meaning : m.toString());

        StringBuilder ex = new StringBuilder();
        if (item.example.length() > 0) {
            ex.append(getString(R.string.word_label_example)).append(item.example);
            if (item.examplePinyin.length() > 0 && showPinyin()) ex.append("\n").append(item.examplePinyin);
            if (item.exampleMy.length() > 0) ex.append("\n").append(item.exampleMy);
        }
        example.setText(ex.length() == 0 ? getString(R.string.word_no_example) : ex.toString());

        StringBuilder more = new StringBuilder();
        addMoreLine(more, getString(R.string.word_label_usage), item.usageScene);
        addMoreLine(more, getString(R.string.word_label_notes), item.notes);
        addMoreLine(more, getString(R.string.word_label_synonyms), item.synonymsText);
        addMoreLine(more, getString(R.string.word_label_antonyms), item.antonymsText);
        extraInfo.setText(more.toString());
        hint.setText(getString(R.string.word_back_hint));
    }

    private void addMoreLine(StringBuilder builder, String label, String value) {
        if (value == null || value.length() == 0) return;
        if (builder.length() > 0) builder.append("\n");
        builder.append(label).append(value);
    }

    private void judge(int quality) {
        if (words.isEmpty() || judging) return;
        judging = true;
        WordItem item = words.get(index);
        ReviewState old = reviewStore.get(item.id);
        ReviewState next = Sm2.schedule(old, item.id, quality, System.currentTimeMillis());
        reviewStore.save(next);
        vibrate(18);
        int dir = quality == Sm2.QUALITY_FORGOT ? -1 : 1;
        if (quality == Sm2.QUALITY_FORGOT) leftMark.setAlpha(1f); else rightMark.setAlpha(1f);
        Toast.makeText(this, quality == Sm2.QUALITY_FORGOT ? getString(R.string.word_toast_unknown) : getString(R.string.word_toast_known), Toast.LENGTH_SHORT).show();
        card.animate()
                .translationX(dir * dp(520))
                .rotation(dir * 10f)
                .alpha(0f)
                .setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        card.animate().setListener(null);
                        card.setAlpha(1f);
                        card.setTranslationX(0f);
                        card.setRotation(0f);
                        judging = false;
                        nextWord();
                    }
                })
                .start();
    }

    private void nextWord() {
        if (index < words.size() - 1) index++; else index = 0;
        bind();
    }

    private void resetMotion() {
        card.animate().translationX(0f).translationY(0f).rotation(0f).alpha(1f).setDuration(130).start();
        leftMark.animate().alpha(0f).setDuration(100).start();
        rightMark.animate().alpha(0f).setDuration(100).start();
        pullMark.animate().alpha(0f).setDuration(100).start();
    }

    private void speakCurrentWordAuto() {
        // 第一版默认不强制自动朗读，避免打开列表时连续刷词太吵；后续可加设置开关。
    }

    private void speakCurrentWord() {
        if (words.isEmpty()) return;
        LearningTtsBridge.speak(this, words.get(index).word, LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_WORD);
        vibrate(6);
    }

    private void speakCurrentSpelling() {
        if (words.isEmpty()) return;
        WordItem item = words.get(index);
        LearningTtsBridge.speak(this, spellingText(item), LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_SPELLING);
        vibrate(6);
    }

    private void openPronunciationPractice() {
        if (words.isEmpty()) return;
        WordItem item = words.get(index);
        Intent intent = new Intent(this, WordPronunciationActivity.class);
        intent.putExtra(WordPronunciationActivity.EXTRA_WORD, item.word);
        intent.putExtra(WordPronunciationActivity.EXTRA_PINYIN, item.pinyin);
        intent.putExtra(WordPronunciationActivity.EXTRA_SPELLING_TEXT, spellingText(item));
        startActivity(intent);
    }

    private void openStrokePractice() {
        if (words.isEmpty()) return;
        Toast.makeText(this, getString(R.string.word_stroke_todo), Toast.LENGTH_SHORT).show();
    }

    private void togglePinyin() {
        boolean next = !showPinyin();
        settings.edit().putBoolean(SP_SHOW_PINYIN, next).apply();
        updatePinyinSwitch();
        if (!words.isEmpty()) {
            pinyin.setVisibility(next ? View.VISIBLE : View.GONE);
            if (flipped) bindBack(words.get(index)); else bindFront(words.get(index));
        }
    }

    private void toggleMyPhonetic() {
        boolean next = !showMyPhonetic();
        settings.edit().putBoolean(SP_SHOW_MY_PHONETIC, next).apply();
        Toast.makeText(this, next ? getString(R.string.word_toast_my_phonetic_on) : getString(R.string.word_toast_my_phonetic_off), Toast.LENGTH_SHORT).show();
        if (!words.isEmpty()) phoneticMy.setVisibility(next && words.get(index).phoneticMy.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private boolean showPinyin() {
        return settings.getBoolean(SP_SHOW_PINYIN, true);
    }

    private boolean showMyPhonetic() {
        return settings.getBoolean(SP_SHOW_MY_PHONETIC, true);
    }

    private void updatePinyinSwitch() {
        pinyinSwitch.setText(showPinyin() ? getString(R.string.word_pinyin_on) : getString(R.string.word_pinyin_off));
    }

    private void toggleFavoriteWithToast() {
        String id = currentId();
        if (id.length() == 0) return;
        boolean next = !isFavorite(id);
        settings.edit().putBoolean("fav." + id, next).apply();
        favoriteButton.setText(next ? "♥" : "♡");
        Toast.makeText(this, next ? getString(R.string.word_favorite_added) : getString(R.string.word_favorite_removed), Toast.LENGTH_SHORT).show();
        vibrate(10);
    }

    private boolean isFavorite(String id) {
        return id != null && id.length() > 0 && settings.getBoolean("fav." + id, false);
    }

    private String currentId() {
        return words.isEmpty() ? "" : words.get(index).id;
    }

    private void maybeShowGuide() {
        if (!settings.getBoolean(SP_SHOW_GUIDE, true)) return;
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x99000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(22));
        panel.setBackground(rounded(0xFFFFFFFF, dp(24), 0, 0));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        panelLp.setMargins(dp(24), 0, dp(24), 0);
        overlay.addView(panel, panelLp);

        TextView title = text(getString(R.string.word_guide_title), 22, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView body = text(getString(R.string.word_guide_body), 15, COLOR_SUB, false);
        body.setLineSpacing(dp(6), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.setMargins(0, dp(16), 0, dp(20));
        panel.addView(body, bodyLp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        panel.addView(buttons, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView never = actionButton(getString(R.string.word_guide_never), 0xFFF3F4F6, COLOR_SUB);
        TextView ok = actionButton(getString(R.string.word_guide_ok), 0xFFEFF6FF, COLOR_BLUE);
        buttons.addView(never, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(0, -1, 1f);
        okLp.setMargins(dp(12), 0, 0, 0);
        buttons.addView(ok, okLp);
        never.setOnClickListener(v -> {
            settings.edit().putBoolean(SP_SHOW_GUIDE, false).apply();
            root.removeView(overlay);
        });
        ok.setOnClickListener(v -> root.removeView(overlay));
    }

    private void seedWords() {
        words.clear();
        if (loadWordsFromAsset(level)) return;

        words.add(new WordItem(level + "_001", "你好", "nǐ hǎo", "နီ ဟောင်", "မင်္ဂလာပါ", "Hello", "မင်္ဂလာပါ / Hello", "你好，很高兴认识你。", "Nǐ hǎo, hěn gāoxìng rènshi nǐ.", "မင်္ဂလာပါ၊ တွေ့ရတာဝမ်းသာပါတယ်။", "见面、聊天开场", "你好偏日常，您好更礼貌。", "您好", ""));
        words.add(new WordItem(level + "_002", "谢谢", "xiè xie", "ရှဲ့ ရှဲ့", "ကျေးဇူးတင်ပါတယ်", "Thank you", "ကျေးဇူးတင်ပါတယ် / Thank you", "谢谢你的帮助。", "Xièxie nǐ de bāngzhù.", "ကူညီပေးတာ ကျေးဇူးတင်ပါတယ်။", "表达感谢", "熟人和陌生人都可以用。", "感谢", ""));
        words.add(new WordItem(level + "_003", "再见", "zài jiàn", "ဇိုင်း ကျန်", "နောက်မှတွေ့မယ်", "Goodbye", "နောက်မှတွေ့မယ် / Goodbye", "明天再见。", "Míngtiān zàijiàn.", "မနက်ဖြန် ပြန်တွေ့မယ်။", "告别", "也可以说拜拜。", "拜拜", ""));
    }

    private boolean loadWordsFromAsset(String levelId) {
        if (levelId == null || levelId.length() == 0) return false;
        try {
            String json = readAsset("learning/words/" + levelId + ".json");
            JSONObject root = new JSONObject(json);
            JSONArray items = root.optJSONArray("items");
            if (items == null || items.length() == 0) return false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", levelId + "_" + i);
                String wordText = firstNonEmpty(item.optString("word", ""), item.optString("chinese", ""));
                String pinyinText = item.optString("pinyin", "");
                String phonetic = firstNonEmpty(item.optString("phonetic_my", ""), item.optString("mnemonic", ""));
                JSONObject translations = item.optJSONObject("translations");
                String my = firstNonEmpty(item.optString("meaning_my", ""), translations != null ? translations.optString("my", "") : "", item.optString("burmese", ""));
                String en = firstNonEmpty(item.optString("meaning_en", ""), translations != null ? translations.optString("en", "") : "");
                String meaningText = my.length() > 0 && en.length() > 0 ? my + " / " + en : my.length() > 0 ? my : en;
                String exampleText = item.optString("example", "");
                String examplePinyin = item.optString("example_pinyin", "");
                JSONObject exampleTranslations = item.optJSONObject("exampleTranslations");
                String exampleMy = firstNonEmpty(item.optString("example_my", ""), exampleTranslations != null ? exampleTranslations.optString("my", "") : "");
                String usage = firstNonEmpty(item.optString("usage_scene", ""), item.optString("scene", ""));
                String notes = firstNonEmpty(item.optString("notes", ""), item.optString("explanation", ""));
                String synonyms = joinArray(item.optJSONArray("synonyms"));
                String antonyms = joinArray(item.optJSONArray("antonyms"));
                if (wordText.length() == 0) continue;
                words.add(new WordItem(id, wordText, pinyinText, phonetic, my, en, meaningText, exampleText, examplePinyin, exampleMy, usage, notes, synonyms, antonyms));
            }
            return !words.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && v.length() > 0) return v;
        return "";
    }

    private String joinArray(JSONArray array) {
        if (array == null || array.length() == 0) return "";
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String v = array.optString(i, "");
            if (v.length() > 0) out.add(v);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) builder.append(" / ");
            builder.append(out.get(i));
        }
        return builder.toString();
    }

    private String readAsset(String path) throws Exception {
        InputStream input = getAssets().open(path);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        } finally {
            try { input.close(); } catch (Throwable ignored) {}
        }
    }

    private void sortWordsByReview() {
        final long now = System.currentTimeMillis();
        Collections.sort(words, (a, b) -> {
            ReviewState ra = reviewStore.get(a.id);
            ReviewState rb = reviewStore.get(b.id);
            boolean da = ra.nextReviewAt <= 0 || ra.nextReviewAt <= now;
            boolean db = rb.nextReviewAt <= 0 || rb.nextReviewAt <= now;
            if (da != db) return da ? -1 : 1;
            return Long.compare(ra.nextReviewAt, rb.nextReviewAt);
        });
    }

    private String formatReview(String id) {
        ReviewState s = reviewStore.get(id);
        if (s.reviewCount <= 0) return getString(R.string.word_review_new);
        long diff = s.nextReviewAt - System.currentTimeMillis();
        if (diff <= 0) return getString(R.string.word_review_due);
        long h = diff / (60L * 60L * 1000L);
        if (h < 1) return getString(R.string.word_review_soon);
        if (h < 24) return getString(R.string.word_review_hours, h);
        return getString(R.string.word_review_days, h / 24);
    }

    private String spellingText(WordItem item) {
        if (item == null || item.pinyin == null || item.pinyin.length() == 0) return "";
        String[] parts = item.pinyin.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            PinyinSyllable s = parseSyllable(part);
            if (builder.length() > 0) builder.append("。 ");
            if (s.initial.length() > 0) builder.append(s.initial).append("，");
            if (s.finalPart.length() > 0) builder.append(s.finalPart).append("，");
            builder.append(toneName(s.tone)).append("，").append(part);
        }
        return builder.toString();
    }

    private PinyinSyllable parseSyllable(String raw) {
        String base = normalizePinyin(raw);
        int tone = toneFromMarked(raw);
        String[] initials = new String[]{"zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w"};
        String initial = "";
        for (String it : initials) {
            if (base.startsWith(it) && base.length() > it.length()) {
                initial = it;
                break;
            }
        }
        String finalPart = initial.length() > 0 ? base.substring(initial.length()) : base;
        return new PinyinSyllable(initial, finalPart, tone);
    }

    private String normalizePinyin(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase();
        String[][] map = new String[][]{
                {"āáǎàa", "a"}, {"ēéěèe", "e"}, {"īíǐìi", "i"}, {"ōóǒòo", "o"}, {"ūúǔùu", "u"}, {"ǖǘǚǜü", "u"}
        };
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String repl = String.valueOf(c);
            for (String[] m : map) {
                if (m[0].indexOf(c) >= 0) { repl = m[1]; break; }
            }
            if ((repl.charAt(0) >= 'a' && repl.charAt(0) <= 'z')) out.append(repl);
        }
        return out.toString();
    }

    private int toneFromMarked(String raw) {
        if (raw == null) return 0;
        String t1 = "āēīōūǖ";
        String t2 = "áéíóúǘ";
        String t3 = "ǎěǐǒǔǚ";
        String t4 = "àèìòùǜ";
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (t1.indexOf(c) >= 0) return 1;
            if (t2.indexOf(c) >= 0) return 2;
            if (t3.indexOf(c) >= 0) return 3;
            if (t4.indexOf(c) >= 0) return 4;
            if (c >= '1' && c <= '5') return c - '0';
        }
        return 0;
    }

    private String toneName(int tone) {
        if (tone == 1) return getString(R.string.word_tone_1);
        if (tone == 2) return getString(R.string.word_tone_2);
        if (tone == 3) return getString(R.string.word_tone_3);
        if (tone == 4) return getString(R.string.word_tone_4);
        return getString(R.string.word_tone_0);
    }

    private void vibrate(long ms) {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(ms);
        } catch (Throwable ignored) {}
    }

    private TextView text(String v, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(2), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView actionButton(String v, int bg, int fg) {
        TextView t = text(v, 15, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(bg, dp(18), 0, 0));
        return t;
    }

    private TextView mark(String v, int bg, int fg) {
        TextView t = text(v, 15, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(bg, dp(20), 0, 0));
        return t;
    }

    private GradientDrawable pageBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
    }

    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFFFFF, 0xFFF8FBFF});
        g.setCornerRadius(dp(28));
        g.setStroke(1, 0xFFE2E8F0);
        return g;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class PinyinSyllable {
        final String initial;
        final String finalPart;
        final int tone;
        PinyinSyllable(String initial, String finalPart, int tone) {
            this.initial = initial; this.finalPart = finalPart; this.tone = tone;
        }
    }

    private static class WordItem {
        final String id, word, pinyin, phoneticMy, meaningMy, meaningEn, meaning, example, examplePinyin, exampleMy, usageScene, notes, synonymsText, antonymsText;
        WordItem(String id, String word, String pinyin, String phoneticMy, String meaningMy, String meaningEn, String meaning, String example, String examplePinyin, String exampleMy, String usageScene, String notes, String synonymsText, String antonymsText) {
            this.id = id; this.word = word; this.pinyin = pinyin; this.phoneticMy = phoneticMy;
            this.meaningMy = meaningMy; this.meaningEn = meaningEn; this.meaning = meaning;
            this.example = example; this.examplePinyin = examplePinyin; this.exampleMy = exampleMy;
            this.usageScene = usageScene; this.notes = notes; this.synonymsText = synonymsText; this.antonymsText = antonymsText;
        }
    }

    private static class ReviewState {
        String wordId = "";
        double easeFactor = 2.5d;
        int repetitions = 0;
        int intervalDays = 0;
        int lastQuality = -1;
        long lastReviewAt = 0L;
        long nextReviewAt = 0L;
        int reviewCount = 0;
        int lapseCount = 0;
    }

    private static class Sm2 {
        static final int QUALITY_FORGOT = 0;
        static final int QUALITY_KNOWN = 5;
        private static final double MIN_EASE = 1.3d;
        private static final long MINUTE = 60L * 1000L;
        private static final long DAY = 24L * 60L * MINUTE;

        static ReviewState schedule(ReviewState old, String wordId, int quality, long now) {
            ReviewState n = new ReviewState();
            n.wordId = wordId;
            double ef = old.easeFactor > 0 ? old.easeFactor : 2.5d;
            int rep = Math.max(0, old.repetitions);
            int interval = Math.max(0, old.intervalDays);
            int lapse = Math.max(0, old.lapseCount);
            long nextAt;
            if (quality < 3) {
                rep = 0;
                interval = 0;
                lapse++;
                nextAt = now + 10L * MINUTE;
            } else {
                if (rep == 0) interval = 1;
                else if (rep == 1) interval = 6;
                else interval = Math.max(1, (int) Math.round(interval * ef));
                rep++;
                nextAt = now + interval * DAY;
            }
            ef = ef + (0.1d - (5 - quality) * (0.08d + (5 - quality) * 0.02d));
            if (ef < MIN_EASE) ef = MIN_EASE;
            n.easeFactor = ef;
            n.repetitions = rep;
            n.intervalDays = interval;
            n.lastQuality = quality;
            n.lastReviewAt = now;
            n.nextReviewAt = nextAt;
            n.reviewCount = old.reviewCount + 1;
            n.lapseCount = lapse;
            return n;
        }
    }

    private static class ReviewStore {
        private final SharedPreferences sp;
        ReviewStore(Context context) { sp = context.getApplicationContext().getSharedPreferences("tsdd_learning_sm2", Context.MODE_PRIVATE); }
        ReviewState get(String id) {
            ReviewState s = new ReviewState();
            s.wordId = id;
            String k = "w." + id;
            s.easeFactor = Double.longBitsToDouble(sp.getLong(k + ".ef", Double.doubleToLongBits(2.5d)));
            s.repetitions = sp.getInt(k + ".rep", 0);
            s.intervalDays = sp.getInt(k + ".int", 0);
            s.lastQuality = sp.getInt(k + ".q", -1);
            s.lastReviewAt = sp.getLong(k + ".last", 0L);
            s.nextReviewAt = sp.getLong(k + ".next", 0L);
            s.reviewCount = sp.getInt(k + ".count", 0);
            s.lapseCount = sp.getInt(k + ".lapse", 0);
            return s;
        }
        void save(ReviewState s) {
            String k = "w." + s.wordId;
            sp.edit()
                    .putLong(k + ".ef", Double.doubleToLongBits(s.easeFactor))
                    .putInt(k + ".rep", s.repetitions)
                    .putInt(k + ".int", s.intervalDays)
                    .putInt(k + ".q", s.lastQuality)
                    .putLong(k + ".last", s.lastReviewAt)
                    .putLong(k + ".next", s.nextReviewAt)
                    .putInt(k + ".count", s.reviewCount)
                    .putInt(k + ".lapse", s.lapseCount)
                    .apply();
        }
    }
}
