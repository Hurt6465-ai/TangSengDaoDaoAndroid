package com.chat.learning;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;

/**
 * 全屏背单词：不依赖 RecyclerView / ViewPager2 / Room。
 * 上下滑换词，左滑忘记，右滑记得，点击翻面，底部三档写入 SM-2 本地记录。
 */
public class WordFullscreenActivity extends AppCompatActivity {
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_TITLE = "title";

    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_SUB = 0xFF64748B;
    private static final int COLOR_BLUE = 0xFF1877F2;
    private static final int COLOR_RED = 0xFFE11D48;
    private static final int COLOR_ORANGE = 0xFFEA580C;
    private static final int COLOR_GREEN = 0xFF059669;

    private final ArrayList<WordItem> words = new ArrayList<>();
    private int index = 0;
    private boolean flipped = false;
    private boolean judging = false;
    private Axis axis = Axis.NONE;
    private float downX;
    private float downY;
    private int touchSlop;
    private String level;
    private String title;

    private FrameLayout root;
    private LinearLayout card;
    private TextView progress;
    private TextView word;
    private TextView pinyin;
    private TextView meaning;
    private TextView example;
    private TextView hint;
    private TextView leftMark;
    private TextView rightMark;
    private GestureDetector gestureDetector;
    private ReviewStore reviewStore;

    private enum Axis { NONE, HORIZONTAL, VERTICAL }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(0xFFEAF4FF);
        window.setNavigationBarColor(0xFFF8FBFF);

        level = getIntent().getStringExtra(EXTRA_LEVEL);
        if (level == null || level.length() == 0) level = "hsk1";
        title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.length() == 0) title = level.toUpperCase();

        reviewStore = new ReviewStore(this);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                flip();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                playTtsHint();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Toast.makeText(WordFullscreenActivity.this, "收藏/更多后续接入", Toast.LENGTH_SHORT).show();
                vibrate(12);
            }
        });

        seedWords();
        sortWordsByReview();
        buildLayout();
        bind();
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
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView close = circle("×");
        close.setTextSize(22);
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, dp(12), 0);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView titleView = text(title + " 背单词", 18, COLOR_TEXT, true);
        titleBox.addView(titleView, new LinearLayout.LayoutParams(-1, 0, 1f));
        progress = text("", 12, COLOR_SUB, false);
        titleBox.addView(progress, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView more = circle("⋯");
        more.setTextSize(26);
        more.setOnClickListener(v -> Toast.makeText(this, "更多设置后续接入", Toast.LENGTH_SHORT).show());
        top.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));

        FrameLayout cardHost = new FrameLayout(this);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        hostLp.setMargins(0, dp(18), 0, dp(18));
        page.addView(cardHost, hostLp);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(26), dp(26), dp(26), dp(26));
        card.setBackground(cardBg());
        card.setOnTouchListener(this::handleCardTouch);
        cardHost.addView(card, new FrameLayout.LayoutParams(-1, -1));

        word = text("", 50, COLOR_TEXT, true);
        word.setGravity(Gravity.CENTER);
        card.addView(word, new LinearLayout.LayoutParams(-1, -2));

        pinyin = text("", 22, COLOR_BLUE, true);
        pinyin.setGravity(Gravity.CENTER);
        pinyin.setPadding(0, dp(10), 0, 0);
        card.addView(pinyin, new LinearLayout.LayoutParams(-1, -2));

        meaning = text("点击查看意思", 24, COLOR_TEXT, true);
        meaning.setGravity(Gravity.CENTER);
        meaning.setPadding(0, dp(34), 0, 0);
        card.addView(meaning, new LinearLayout.LayoutParams(-1, -2));

        example = text("左滑忘记 · 右滑记得 · 模糊点按钮", 16, COLOR_SUB, false);
        example.setGravity(Gravity.CENTER);
        example.setLineSpacing(dp(3), 1f);
        example.setPadding(0, dp(18), 0, 0);
        card.addView(example, new LinearLayout.LayoutParams(-1, -2));

        hint = text("点击翻面，双击发音，上下滑切词", 12, COLOR_SUB, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(28), 0, 0);
        card.addView(hint, new LinearLayout.LayoutParams(-1, -2));

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
        LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(0, -1, 1f);
        mid.setMargins(dp(10), 0, dp(10), 0);
        actions.addView(vague, mid);
        actions.addView(known, new LinearLayout.LayoutParams(0, -1, 1f));
        forgot.setOnClickListener(v -> judge(Sm2.QUALITY_FORGOT));
        vague.setOnClickListener(v -> judge(Sm2.QUALITY_VAGUE));
        known.setOnClickListener(v -> judge(Sm2.QUALITY_KNOWN));
    }

    private boolean handleCardTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        if (words.isEmpty() || judging) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                axis = Axis.NONE;
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
                    card.setRotation(dx / 28f);
                    float a = Math.min(1f, Math.abs(dx) / dp(128));
                    leftMark.setAlpha(dx < 0 ? a : 0f);
                    rightMark.setAlpha(dx > 0 ? a : 0f);
                } else if (axis == Axis.VERTICAL) {
                    card.setTranslationY(dy * 0.28f);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float upDx = event.getRawX() - downX;
                float upDy = event.getRawY() - downY;
                if (axis == Axis.HORIZONTAL && Math.abs(upDx) > dp(118)) {
                    judge(upDx > 0 ? Sm2.QUALITY_KNOWN : Sm2.QUALITY_FORGOT);
                } else if (axis == Axis.VERTICAL && Math.abs(upDy) > dp(118)) {
                    if (upDy < 0) next(); else prev();
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
        progress.setText((index + 1) + " / " + words.size() + " · " + formatReview(item.id));
        word.setText(item.word);
        pinyin.setText(item.pinyin);
        meaning.setText("点击查看意思");
        example.setText("左滑忘记 · 右滑记得 · 模糊点按钮");
        hint.setText("点击翻面，双击发音，上下滑切词");
        resetMotion();
    }

    private void flip() {
        if (words.isEmpty()) return;
        WordItem item = words.get(index);
        flipped = !flipped;
        if (flipped) {
            meaning.setText(item.meaning);
            example.setText("例句：" + item.example);
            hint.setText("忘记=10分钟后 · 模糊=8小时后 · 记得=按 SM-2");
        } else {
            meaning.setText("点击查看意思");
            example.setText("左滑忘记 · 右滑记得 · 模糊点按钮");
            hint.setText("点击翻面，双击发音，上下滑切词");
        }
    }

    private void judge(int quality) {
        if (words.isEmpty() || judging) return;
        judging = true;
        WordItem item = words.get(index);
        ReviewState old = reviewStore.get(item.id);
        ReviewState next = Sm2.schedule(old, item.id, quality, System.currentTimeMillis());
        reviewStore.save(next);
        vibrate(20);
        int dir = quality == Sm2.QUALITY_FORGOT ? -1 : 1;
        if (quality == Sm2.QUALITY_VAGUE) dir = 0;
        if (quality == Sm2.QUALITY_FORGOT) leftMark.setAlpha(1f);
        if (quality == Sm2.QUALITY_KNOWN) rightMark.setAlpha(1f);
        Toast.makeText(this, quality == Sm2.QUALITY_VAGUE ? "模糊：8小时后复习" : quality == Sm2.QUALITY_FORGOT ? "忘记：10分钟后复习" : "记得：已安排复习", Toast.LENGTH_SHORT).show();
        card.animate()
                .translationX(dir == 0 ? 0 : dir * dp(460))
                .translationY(quality == Sm2.QUALITY_VAGUE ? dp(460) : 0)
                .rotation(dir * 10f)
                .alpha(0f)
                .setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        card.animate().setListener(null);
                        card.setAlpha(1f);
                        judging = false;
                        next();
                    }
                })
                .start();
    }

    private void next() {
        if (index < words.size() - 1) index++; else index = 0;
        bind();
    }

    private void prev() {
        if (index > 0) index--; else index = words.size() - 1;
        bind();
    }

    private void resetMotion() {
        card.animate().translationX(0f).translationY(0f).rotation(0f).alpha(1f).setDuration(130).start();
        leftMark.animate().alpha(0f).setDuration(100).start();
        rightMark.animate().alpha(0f).setDuration(100).start();
    }

    private void playTtsHint() {
        if (!words.isEmpty()) Toast.makeText(this, "发音后续接 wkspeech：" + words.get(index).word, Toast.LENGTH_SHORT).show();
        vibrate(10);
    }

    private void seedWords() {
        words.clear();
        words.add(new WordItem(level + "_001", "你好", "nǐ hǎo", "မင်္ဂလာပါ / Hello", "你好，很高兴认识你。"));
        words.add(new WordItem(level + "_002", "谢谢", "xiè xie", "ကျေးဇူးတင်ပါတယ် / Thank you", "谢谢你的帮助。"));
        words.add(new WordItem(level + "_003", "再见", "zài jiàn", "နောက်မှတွေ့မယ် / Goodbye", "明天再见。"));
        words.add(new WordItem(level + "_004", "可以", "kě yǐ", "ရပါတယ် / OK", "这样可以吗？"));
        words.add(new WordItem(level + "_005", "朋友", "péng you", "သူငယ်ချင်း / Friend", "他是我的朋友。"));
        words.add(new WordItem(level + "_006", "学习", "xué xí", "သင်ယူသည် / Study", "我每天学习中文。"));
        words.add(new WordItem(level + "_007", "工作", "gōng zuò", "အလုပ် / Work", "我想找工作。"));
        words.add(new WordItem(level + "_008", "吃饭", "chī fàn", "ထမင်းစားသည် / Eat", "我们一起吃饭吧。"));
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
        if (s.reviewCount <= 0) return "新词";
        long diff = s.nextReviewAt - System.currentTimeMillis();
        if (diff <= 0) return "待复习";
        long h = diff / (60L * 60L * 1000L);
        if (h < 1) return "稍后复习";
        if (h < 24) return h + "小时后";
        return (h / 24) + "天后";
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

    private TextView circle(String v) {
        TextView t = text(v, 18, COLOR_TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(0xAAFFFFFF, dp(21), 0xFFE5E7EB, 1));
        return t;
    }

    private TextView actionButton(String v, int bg, int fg) {
        TextView t = text(v, 15, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(bg, dp(18), 0, 0));
        return t;
    }

    private TextView mark(String v, int bg, int fg) {
        TextView t = text(v, 16, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(bg, dp(18), 0, 0));
        return t;
    }

    private GradientDrawable pageBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFEAF4FF, 0xFFF8FBFF});
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

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class WordItem {
        final String id, word, pinyin, meaning, example;
        WordItem(String id, String word, String pinyin, String meaning, String example) {
            this.id = id; this.word = word; this.pinyin = pinyin; this.meaning = meaning; this.example = example;
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
        static final int QUALITY_VAGUE = 2;
        static final int QUALITY_KNOWN = 5;
        private static final double MIN_EASE = 1.3d;
        private static final long MINUTE = 60L * 1000L;
        private static final long HOUR = 60L * MINUTE;
        private static final long DAY = 24L * HOUR;

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
                nextAt = now + (quality <= QUALITY_FORGOT ? 10L * MINUTE : 8L * HOUR);
            } else {
                if (rep == 0) interval = 1;
                else if (rep == 1) interval = 6;
                else interval = Math.max(1, (int) Math.round(interval * ef));
                rep++;
                nextAt = now + interval * DAY;
            }
            // EF 无论成功/失败都更新，别挪进 else。
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
