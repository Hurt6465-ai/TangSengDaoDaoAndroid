package com.chat.learning.fullscreen;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.chat.learning.data.WordRepository;
import com.chat.learning.model.WordItem;
import com.chat.learning.review.LearningReviewStore;
import com.chat.learning.review.ReviewQueueBuilder;
import com.chat.learning.review.Sm2Scheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏背单词。
 * 上下滑换词，左滑忘记，右滑记得，点击翻面，双击播放发音占位。
 * 手势不用 ViewPager2，避免横纵方向和点击/双击/长按冲突。
 */
public class WordFullscreenActivity extends AppCompatActivity {
    public static final String EXTRA_CATEGORY_ID = "category_id";
    public static final String EXTRA_CATEGORY_TITLE = "category_title";

    private static final int COLOR_BG_TOP = 0xFFF7FBFF;
    private static final int COLOR_BG_BOTTOM = 0xFFEFF6FF;
    private static final int COLOR_BLUE = 0xFF1877F2;
    private static final int COLOR_TEXT_DARK = 0xFF111827;
    private static final int COLOR_TEXT_GRAY = 0xFF6B7280;
    private static final int COLOR_GREEN = 0xFF059669;
    private static final int COLOR_RED = 0xFFE11D48;
    private static final int COLOR_ORANGE = 0xFFEA580C;

    private FrameLayout root;
    private LinearLayout card;
    private TextView topTitle;
    private TextView progressText;
    private TextView wordText;
    private TextView pinyinText;
    private TextView meaningText;
    private TextView exampleText;
    private TextView hintText;
    private TextView leftMark;
    private TextView rightMark;
    private ProgressBar loading;

    private final ArrayList<WordItem> queue = new ArrayList<>();
    private int index = 0;
    private boolean flipped = false;
    private boolean judging = false;
    private String categoryId;
    private String categoryTitle;

    private LearningReviewStore reviewStore;
    private GestureDetector gestureDetector;
    private float downX;
    private float downY;
    private int touchSlop;
    private Axis activeAxis = Axis.NONE;

    private enum Axis { NONE, HORIZONTAL, VERTICAL }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG_TOP);
        window.setNavigationBarColor(COLOR_BG_BOTTOM);

        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        if (categoryId == null || categoryId.length() == 0) categoryId = "greeting";
        categoryTitle = getIntent().getStringExtra(EXTRA_CATEGORY_TITLE);
        if (categoryTitle == null || categoryTitle.length() == 0) categoryTitle = "背单词";

        reviewStore = new LearningReviewStore(this);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                flipCard();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                playPronunciation();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Toast.makeText(WordFullscreenActivity.this, "已收藏/更多操作后续接入", Toast.LENGTH_SHORT).show();
                vibrate(15);
            }
        });

        buildLayout();
        loadQueue();
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackground(createPageBackground());
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView close = circleButton("‹");
        close.setTextSize(30);
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, dp(10), 0);
        top.addView(titles, new LinearLayout.LayoutParams(0, -1, 1f));

        topTitle = text(categoryTitle, 18, COLOR_TEXT_DARK, true);
        titles.addView(topTitle, new LinearLayout.LayoutParams(-1, 0, 1f));
        progressText = text("加载中", 12, COLOR_TEXT_GRAY, false);
        titles.addView(progressText, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView more = circleButton("⋯");
        more.setTextSize(26);
        more.setOnClickListener(v -> Toast.makeText(this, "更多：收藏、生词本、举报错误后续接入", Toast.LENGTH_SHORT).show());
        top.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));

        FrameLayout cardHost = new FrameLayout(this);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        hostLp.setMargins(0, dp(18), 0, dp(18));
        page.addView(cardHost, hostLp);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(26), dp(26), dp(26), dp(26));
        card.setBackground(createCardBackground());
        card.setOnTouchListener(this::handleCardTouch);
        cardHost.addView(card, new FrameLayout.LayoutParams(-1, -1));

        wordText = text("", 46, COLOR_TEXT_DARK, true);
        wordText.setGravity(Gravity.CENTER);
        card.addView(wordText, new LinearLayout.LayoutParams(-1, -2));

        pinyinText = text("", 20, COLOR_BLUE, true);
        pinyinText.setGravity(Gravity.CENTER);
        pinyinText.setPadding(0, dp(12), 0, 0);
        card.addView(pinyinText, new LinearLayout.LayoutParams(-1, -2));

        meaningText = text("点击查看意思", 24, COLOR_TEXT_DARK, true);
        meaningText.setGravity(Gravity.CENTER);
        meaningText.setPadding(0, dp(34), 0, 0);
        card.addView(meaningText, new LinearLayout.LayoutParams(-1, -2));

        exampleText = text("左滑忘记 · 右滑记得 · 模糊点按钮", 16, COLOR_TEXT_GRAY, false);
        exampleText.setGravity(Gravity.CENTER);
        exampleText.setLineSpacing(dp(3), 1f);
        exampleText.setPadding(0, dp(18), 0, 0);
        card.addView(exampleText, new LinearLayout.LayoutParams(-1, -2));

        hintText = text("点击翻面，双击发音，上下滑切词", 12, COLOR_TEXT_GRAY, false);
        hintText.setGravity(Gravity.CENTER);
        hintText.setPadding(0, dp(28), 0, 0);
        card.addView(hintText, new LinearLayout.LayoutParams(-1, -2));

        leftMark = mark("忘记", 0xFFFFEEF2, COLOR_RED);
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(dp(92), dp(44), Gravity.START | Gravity.TOP);
        leftLp.setMargins(dp(22), dp(76), 0, 0);
        cardHost.addView(leftMark, leftLp);
        leftMark.setAlpha(0f);

        rightMark = mark("记得", 0xFFECFDF5, COLOR_GREEN);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(dp(92), dp(44), Gravity.END | Gravity.TOP);
        rightLp.setMargins(0, dp(76), dp(22), 0);
        cardHost.addView(rightMark, rightLp);
        rightMark.setAlpha(0f);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        page.addView(actions, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView forgot = actionButton("忘记", 0xFFFFEEF2, COLOR_RED);
        TextView vague = actionButton("模糊", 0xFFFFF7ED, COLOR_ORANGE);
        TextView known = actionButton("记得", 0xFFECFDF5, COLOR_GREEN);
        actions.addView(forgot, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0, -1, 1f);
        midLp.setMargins(dp(10), 0, dp(10), 0);
        actions.addView(vague, midLp);
        actions.addView(known, new LinearLayout.LayoutParams(0, -1, 1f));
        forgot.setOnClickListener(v -> commitJudge(Sm2Scheduler.QUALITY_FORGOT));
        vague.setOnClickListener(v -> commitJudge(Sm2Scheduler.QUALITY_VAGUE));
        known.setOnClickListener(v -> commitJudge(Sm2Scheduler.QUALITY_KNOWN));

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingLp = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER);
        root.addView(loading, loadingLp);
    }

    private boolean handleCardTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        if (queue.isEmpty() || judging) return true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                activeAxis = Axis.NONE;
                card.animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (activeAxis == Axis.NONE && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    activeAxis = Math.abs(dx) > Math.abs(dy) ? Axis.HORIZONTAL : Axis.VERTICAL;
                }
                if (activeAxis == Axis.HORIZONTAL) {
                    card.setTranslationX(dx);
                    card.setRotation(dx / 28f);
                    float progress = Math.min(1f, Math.abs(dx) / dp(130));
                    leftMark.setAlpha(dx < 0 ? progress : 0f);
                    rightMark.setAlpha(dx > 0 ? progress : 0f);
                } else if (activeAxis == Axis.VERTICAL) {
                    card.setTranslationY(dy * 0.28f);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float upDx = event.getRawX() - downX;
                float upDy = event.getRawY() - downY;
                if (activeAxis == Axis.HORIZONTAL && Math.abs(upDx) > dp(118)) {
                    commitJudge(upDx > 0 ? Sm2Scheduler.QUALITY_KNOWN : Sm2Scheduler.QUALITY_FORGOT);
                } else if (activeAxis == Axis.VERTICAL && Math.abs(upDy) > dp(118)) {
                    if (upDy < 0) goNext(true); else goPrev();
                    resetCardMotion();
                } else {
                    resetCardMotion();
                }
                activeAxis = Axis.NONE;
                return true;
        }
        return true;
    }

    private void loadQueue() {
        loading.setVisibility(View.VISIBLE);
        reviewStore.runIo(() -> {
            WordRepository repo = new WordRepository(this);
            List<WordItem> all = repo.loadWords(categoryId);
            List<WordItem> built = new ReviewQueueBuilder(reviewStore).build(all);
            if (built.isEmpty()) built = all;
            List<WordItem> finalBuilt = built;
            reviewStore.postMain(() -> {
                loading.setVisibility(View.GONE);
                queue.clear();
                queue.addAll(finalBuilt);
                index = 0;
                bindCurrent();
            });
        });
    }

    private void bindCurrent() {
        if (queue.isEmpty()) {
            progressText.setText("暂无单词");
            wordText.setText("暂无内容");
            pinyinText.setText("");
            meaningText.setText("请先添加词库 JSON");
            exampleText.setText("");
            return;
        }
        WordItem item = queue.get(index);
        flipped = false;
        progressText.setText((index + 1) + " / " + queue.size());
        wordText.setText(item.word == null ? "" : item.word);
        pinyinText.setText(item.pinyin == null ? "" : item.pinyin);
        meaningText.setText("点击查看意思");
        exampleText.setText("左滑忘记 · 右滑记得 · 模糊点按钮");
        hintText.setText("点击翻面，双击发音，上下滑切词");
        resetCardMotion();
    }

    private void flipCard() {
        if (queue.isEmpty()) return;
        WordItem item = queue.get(index);
        flipped = !flipped;
        if (flipped) {
            String translation = item.translationFor("my");
            meaningText.setText(translation.length() == 0 ? "暂无翻译" : translation);
            String example = item.example == null || item.example.length() == 0 ? "" : "例句：" + item.example;
            exampleText.setText(example.length() == 0 ? "左滑忘记 · 右滑记得 · 模糊点按钮" : example);
            hintText.setText("忘记=10分钟后 · 模糊=8小时后 · 记得=按 SM-2");
        } else {
            meaningText.setText("点击查看意思");
            exampleText.setText("左滑忘记 · 右滑记得 · 模糊点按钮");
            hintText.setText("点击翻面，双击发音，上下滑切词");
        }
    }

    private void commitJudge(int quality) {
        if (queue.isEmpty() || judging) return;
        judging = true;
        WordItem item = queue.get(index);
        int normalized = Sm2Scheduler.normalizeQuality(quality);
        showJudgePreview(normalized);
        vibrate(22);
        reviewStore.submitReview(item.id, normalized, state -> card.animate()
                .translationX(normalized == Sm2Scheduler.QUALITY_KNOWN ? dp(460) : normalized == Sm2Scheduler.QUALITY_FORGOT ? -dp(460) : 0)
                .translationY(normalized == Sm2Scheduler.QUALITY_VAGUE ? dp(460) : 0)
                .rotation(normalized == Sm2Scheduler.QUALITY_KNOWN ? 10f : normalized == Sm2Scheduler.QUALITY_FORGOT ? -10f : 0f)
                .alpha(0f)
                .setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        card.setAlpha(1f);
                        card.animate().setListener(null);
                        goNext(true);
                        judging = false;
                    }
                })
                .start());
    }

    private void showJudgePreview(int quality) {
        if (quality == Sm2Scheduler.QUALITY_FORGOT) {
            leftMark.setAlpha(1f);
            rightMark.setAlpha(0f);
        } else if (quality == Sm2Scheduler.QUALITY_KNOWN) {
            rightMark.setAlpha(1f);
            leftMark.setAlpha(0f);
        } else {
            Toast.makeText(this, "模糊：8 小时后再复习", Toast.LENGTH_SHORT).show();
        }
    }

    private void goNext(boolean loop) {
        if (queue.isEmpty()) return;
        if (index < queue.size() - 1) index++; else if (loop) index = 0;
        bindCurrent();
    }

    private void goPrev() {
        if (queue.isEmpty()) return;
        if (index > 0) index--; else index = queue.size() - 1;
        bindCurrent();
    }

    private void resetCardMotion() {
        if (card == null) return;
        card.animate().translationX(0f).translationY(0f).rotation(0f).alpha(1f).setDuration(160).start();
        if (leftMark != null) leftMark.animate().alpha(0f).setDuration(120).start();
        if (rightMark != null) rightMark.animate().alpha(0f).setDuration(120).start();
    }

    private void playPronunciation() {
        if (queue.isEmpty()) return;
        Toast.makeText(this, "发音后续接 wkspeech / 系统 TTS：" + queue.get(index).word, Toast.LENGTH_SHORT).show();
        vibrate(10);
    }

    private void vibrate(long ms) {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(2), 1f);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView circleButton(String value) {
        TextView tv = text(value, 18, COLOR_TEXT_DARK, true);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(roundRect(0xAAFFFFFF, dp(21), 0xFFE5E7EB, 1));
        return tv;
    }

    private TextView actionButton(String text, int bg, int fg) {
        TextView tv = text(text, 15, fg, true);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(roundRect(bg, dp(18), 0, 0));
        return tv;
    }

    private TextView mark(String text, int bg, int fg) {
        TextView tv = text(text, 16, fg, true);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(roundRect(bg, dp(18), 0, 0));
        return tv;
    }

    private GradientDrawable createPageBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFFFFF, 0xFFF8FBFF});
        drawable.setCornerRadius(dp(28));
        drawable.setStroke(1, 0xFFE2E8F0);
        return drawable;
    }

    private GradientDrawable roundRect(int color, float radius, int strokeColor, int strokeWidth) {
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
