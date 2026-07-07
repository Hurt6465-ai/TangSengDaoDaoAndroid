package com.chat.learning;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 背单词全屏页：
 * - 上下滑：ViewPager2 竖向切词；
 * - 左滑：忘记，10 分钟后再复习；
 * - 右滑：记得，按 SM-2 拉长复习间隔；
 * - 点击：正反面翻转；
 * - 底部按钮：忘记 / 模糊 / 记得，三档写入 SM-2。
 */
public class WordFullscreenActivity extends AppCompatActivity {
    private static final int COLOR_BLUE = 0xFF1877F2;
    private static final int COLOR_TEXT_DARK = 0xFF111827;
    private static final int COLOR_TEXT_GRAY = 0xFF6B7280;
    private static final int COLOR_LINE = 0xFFE8EDF6;

    private ViewPager2 wordPager;
    private TextView progressView;
    private LearningReviewStore reviewStore;
    private final ArrayList<WordItem> words = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(0xFFF7F9FC);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        seedWords();
        reviewStore = new LearningReviewStore(this);
        sortWordsByReviewTime();

        FrameLayout root = new FrameLayout(this);
        root.setBackground(createPageBg());
        setContentView(root);

        wordPager = new ViewPager2(this);
        wordPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        wordPager.setOffscreenPageLimit(1);
        wordPager.setAdapter(new WordPagerAdapter(words));
        root.addView(wordPager, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(14), dp(14), dp(14), dp(8));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, dp(72), Gravity.TOP);
        root.addView(topBar, topLp);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(34);
        back.setGravity(Gravity.CENTER);
        back.setTextColor(COLOR_TEXT_DARK);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setBackground(rounded(0xCCFFFFFF, dp(18), COLOR_LINE, 1));
        topBar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("全屏背单词");
        title.setTextColor(COLOR_TEXT_DARK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -1, 1f);
        titleLp.setMargins(dp(12), 0, 0, 0);
        topBar.addView(title, titleLp);

        progressView = new TextView(this);
        progressView.setTextColor(COLOR_TEXT_GRAY);
        progressView.setTextSize(13);
        progressView.setGravity(Gravity.CENTER);
        progressView.setBackground(rounded(0xCCFFFFFF, dp(17), COLOR_LINE, 1));
        topBar.addView(progressView, new LinearLayout.LayoutParams(dp(86), dp(34)));

        TextView hint = new TextView(this);
        hint.setText("上/下切词 · 左滑忘记 · 右滑记得 · 点击翻面 · 模糊点按钮");
        hint.setTextColor(0xFF64748B);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setBackground(rounded(0xCCFFFFFF, dp(17), COLOR_LINE, 1));
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(-2, dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintLp.setMargins(dp(16), 0, dp(16), dp(18));
        root.addView(hint, hintLp);

        wordPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateProgress(position);
            }
        });
        updateProgress(0);
    }

    private void updateProgress(int position) {
        if (progressView != null) {
            progressView.setText((position + 1) + "/" + words.size() + " · 待" + countDueWords());
        }
    }

    private void seedWords() {
        words.clear();
        words.add(new WordItem("zh_basic_001", "你好", "nǐ hǎo", "မင်္ဂလာပါ / Hello", "你好，你吃饭了吗？"));
        words.add(new WordItem("zh_basic_002", "谢谢", "xiè xie", "ကျေးဇူးတင်ပါတယ် / Thank you", "谢谢你帮我。"));
        words.add(new WordItem("zh_basic_003", "学习", "xué xí", "သင်ယူသည် / Study", "我正在学习中文。"));
        words.add(new WordItem("zh_basic_004", "工作", "gōng zuò", "အလုပ် / Work", "你在哪里工作？"));
        words.add(new WordItem("zh_basic_005", "朋友", "péng you", "သူငယ်ချင်း / Friend", "他是我的朋友。"));
        words.add(new WordItem("zh_basic_006", "吃饭", "chī fàn", "ထမင်းစားသည် / Eat", "我们一起吃饭吧。"));
        words.add(new WordItem("zh_basic_007", "多少钱", "duō shǎo qián", "ဘယ်လောက်လဲ / How much", "这个多少钱？"));
        words.add(new WordItem("zh_basic_008", "慢一点", "màn yì diǎn", "နည်းနည်းဖြေးဖြေး / Slower", "请你说慢一点。"));
    }

    private void sortWordsByReviewTime() {
        if (reviewStore == null) return;
        final long now = System.currentTimeMillis();
        Collections.sort(words, (a, b) -> {
            LearningReviewStore.ReviewState ra = reviewStore.get(a.id);
            LearningReviewStore.ReviewState rb = reviewStore.get(b.id);
            boolean da = ra.isDue(now);
            boolean db = rb.isDue(now);
            if (da != db) return da ? -1 : 1;
            long na = ra.nextReviewAt <= 0 ? 0 : ra.nextReviewAt;
            long nb = rb.nextReviewAt <= 0 ? 0 : rb.nextReviewAt;
            return Long.compare(na, nb);
        });
    }

    private int countDueWords() {
        if (reviewStore == null) return words.size();
        long now = System.currentTimeMillis();
        int count = 0;
        for (WordItem item : words) {
            if (reviewStore.get(item.id).isDue(now)) count++;
        }
        return count;
    }

    private class WordPagerAdapter extends RecyclerView.Adapter<WordPagerAdapter.Holder> {
        private final List<WordItem> data;

        WordPagerAdapter(List<WordItem> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout page = new FrameLayout(parent.getContext());
            page.setPadding(dp(20), dp(86), dp(20), dp(68));
            page.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            return new Holder(page);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            private final FrameLayout page;
            private final LinearLayout card;
            private final TextView word;
            private final TextView pinyin;
            private final TextView meaning;
            private final TextView example;
            private final TextView reviewInfo;
            private final TextView markLeft;
            private final TextView markRight;
            private final int slop;
            private float downX;
            private float downY;
            private boolean horizontal;
            private boolean flipped;

            @SuppressLint("ClickableViewAccessibility")
            Holder(@NonNull View itemView) {
                super(itemView);
                page = (FrameLayout) itemView;
                slop = ViewConfiguration.get(itemView.getContext()).getScaledTouchSlop();

                card = new LinearLayout(itemView.getContext());
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(24), dp(28), dp(24), dp(28));
                card.setBackground(rounded(Color.WHITE, dp(32), COLOR_LINE, 1));
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    card.setElevation(dp(6));
                }
                page.addView(card, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

                word = new TextView(itemView.getContext());
                word.setTextColor(COLOR_TEXT_DARK);
                word.setTypeface(Typeface.DEFAULT_BOLD);
                word.setTextSize(52);
                word.setGravity(Gravity.CENTER);
                card.addView(word, new LinearLayout.LayoutParams(-1, -2));

                pinyin = new TextView(itemView.getContext());
                pinyin.setTextColor(COLOR_BLUE);
                pinyin.setTextSize(24);
                pinyin.setGravity(Gravity.CENTER);
                pinyin.setPadding(0, dp(10), 0, 0);
                card.addView(pinyin, new LinearLayout.LayoutParams(-1, -2));

                reviewInfo = new TextView(itemView.getContext());
                reviewInfo.setTextColor(0xFF64748B);
                reviewInfo.setTextSize(13);
                reviewInfo.setGravity(Gravity.CENTER);
                reviewInfo.setPadding(0, dp(12), 0, 0);
                card.addView(reviewInfo, new LinearLayout.LayoutParams(-1, -2));

                meaning = new TextView(itemView.getContext());
                meaning.setTextColor(0xFF334155);
                meaning.setTextSize(21);
                meaning.setGravity(Gravity.CENTER);
                meaning.setLineSpacing(dp(3), 1f);
                meaning.setPadding(0, dp(26), 0, 0);
                card.addView(meaning, new LinearLayout.LayoutParams(-1, -2));

                example = new TextView(itemView.getContext());
                example.setTextColor(COLOR_TEXT_GRAY);
                example.setTextSize(17);
                example.setGravity(Gravity.CENTER);
                example.setLineSpacing(dp(3), 1f);
                example.setPadding(0, dp(18), 0, 0);
                card.addView(example, new LinearLayout.LayoutParams(-1, -2));

                LinearLayout actions = new LinearLayout(itemView.getContext());
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setGravity(Gravity.CENTER);
                actions.setPadding(0, dp(24), 0, 0);
                TextView forgot = actionButton("忘记", 0xFFFFEEF2, 0xFFE11D48);
                TextView blurry = actionButton("模糊", 0xFFFFF7ED, 0xFFEA580C);
                TextView remember = actionButton("记得", 0xFFECFDF5, 0xFF059669);
                actions.addView(forgot, new LinearLayout.LayoutParams(0, dp(44), 1f));
                LinearLayout.LayoutParams blurryLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
                blurryLp.setMargins(dp(10), 0, dp(10), 0);
                actions.addView(blurry, blurryLp);
                actions.addView(remember, new LinearLayout.LayoutParams(0, dp(44), 1f));
                card.addView(actions, new LinearLayout.LayoutParams(-1, -2));
                forgot.setOnClickListener(v -> commitJudge(Sm2Scheduler.QUALITY_FORGOT));
                blurry.setOnClickListener(v -> commitJudge(Sm2Scheduler.QUALITY_BLURRY));
                remember.setOnClickListener(v -> commitJudge(Sm2Scheduler.QUALITY_REMEMBER));

                markLeft = mark("忘记", 0xFFFFEEF2, 0xFFE11D48);
                FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(dp(86), dp(42), Gravity.START | Gravity.TOP);
                leftLp.setMargins(dp(22), dp(102), 0, 0);
                page.addView(markLeft, leftLp);

                markRight = mark("记得", 0xFFECFDF5, 0xFF059669);
                FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(dp(86), dp(42), Gravity.END | Gravity.TOP);
                rightLp.setMargins(0, dp(102), dp(22), 0);
                page.addView(markRight, rightLp);

                page.setOnTouchListener((v, event) -> handleCardTouch(event));
            }

            void bind(WordItem item) {
                flipped = false;
                horizontal = false;
                page.setTranslationX(0f);
                page.setRotation(0f);
                markLeft.setAlpha(0f);
                markRight.setAlpha(0f);
                word.setText(item.word);
                pinyin.setText(item.pinyin);
                meaning.setText("点击查看意思");
                reviewInfo.setText(formatReviewInfo(item));
                example.setText("左滑忘记 · 右滑记得 · 模糊点按钮");
                itemView.setTag(item);
            }

            private boolean handleCardTouch(MotionEvent event) {
                WordItem item = (WordItem) itemView.getTag();
                if (item == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        horizontal = false;
                        itemView.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (!horizontal && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                            horizontal = true;
                            itemView.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        if (horizontal) {
                            float limited = Math.max(-dp(130), Math.min(dp(130), dx));
                            page.setTranslationX(limited);
                            page.setRotation(limited / dp(28));
                            float alpha = Math.min(1f, Math.abs(limited) / dp(96));
                            markLeft.setAlpha(limited < 0 ? alpha : 0f);
                            markRight.setAlpha(limited > 0 ? alpha : 0f);
                            return true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float totalDx = event.getRawX() - downX;
                        float totalDy = event.getRawY() - downY;
                        if (horizontal && Math.abs(totalDx) > dp(92)) {
                            int quality = totalDx > 0 ? Sm2Scheduler.QUALITY_REMEMBER : Sm2Scheduler.QUALITY_FORGOT;
                            commitJudge(quality);
                        } else if (Math.abs(totalDx) < slop && Math.abs(totalDy) < slop) {
                            toggleFlip(item);
                            resetCard();
                        } else {
                            resetCard();
                        }
                        itemView.getParent().requestDisallowInterceptTouchEvent(false);
                        horizontal = false;
                        return true;
                    default:
                        return false;
                }
            }

            private void toggleFlip(WordItem item) {
                flipped = !flipped;
                if (flipped) {
                    meaning.setText(item.meaning);
                    example.setText("例句：" + item.example);
                } else {
                    meaning.setText("点击查看意思");
                    example.setText("左滑忘记 · 右滑记得 · 模糊点按钮");
                }
            }

            private void commitJudge(int quality) {
                WordItem item = (WordItem) itemView.getTag();
                if (item == null || reviewStore == null) return;
                int current = getBindingAdapterPosition();
                LearningReviewStore.ReviewState next = Sm2Scheduler.review(reviewStore.get(item.id), quality, System.currentTimeMillis());
                next.wordId = item.id;
                reviewStore.save(next);
                reviewInfo.setText(formatReviewInfo(item));

                String label = quality == Sm2Scheduler.QUALITY_REMEMBER ? "记得"
                        : quality == Sm2Scheduler.QUALITY_BLURRY ? "模糊" : "忘记";
                Toast.makeText(WordFullscreenActivity.this, label + "：下次复习 " + formatRelative(next.nextReviewAt), Toast.LENGTH_SHORT).show();

                int direction = quality == Sm2Scheduler.QUALITY_FORGOT ? -1 : 1;
                page.animate()
                        .translationX(direction > 0 ? dp(360) : -dp(360))
                        .rotation(direction > 0 ? 12f : -12f)
                        .alpha(0f)
                        .setDuration(170)
                        .withEndAction(() -> {
                            resetCard();
                            page.setAlpha(1f);
                            updateProgress(Math.max(0, current));
                            if (wordPager != null && current >= 0 && current < words.size() - 1) {
                                wordPager.setCurrentItem(current + 1, true);
                            }
                        })
                        .start();
            }

            private void resetCard() {
                page.animate().translationX(0f).rotation(0f).setDuration(120).start();
                markLeft.animate().alpha(0f).setDuration(120).start();
                markRight.animate().alpha(0f).setDuration(120).start();
            }

            private TextView actionButton(String text, int bg, int color) {
                TextView view = new TextView(WordFullscreenActivity.this);
                view.setText(text);
                view.setTextSize(15);
                view.setTypeface(Typeface.DEFAULT_BOLD);
                view.setTextColor(color);
                view.setGravity(Gravity.CENTER);
                view.setBackground(rounded(bg, dp(22), color, 1));
                return view;
            }

            private TextView mark(String text, int bg, int color) {
                TextView view = new TextView(WordFullscreenActivity.this);
                view.setText(text);
                view.setTextSize(16);
                view.setTypeface(Typeface.DEFAULT_BOLD);
                view.setTextColor(color);
                view.setGravity(Gravity.CENTER);
                view.setBackground(rounded(bg, dp(21), color, 1));
                view.setAlpha(0f);
                return view;
            }
        }
    }

    private GradientDrawable createPageBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFEAF2FF, 0xFFF7F9FC, 0xFFFFF7FA});
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

    private String formatReviewInfo(WordItem item) {
        if (reviewStore == null) return "新词";
        LearningReviewStore.ReviewState state = reviewStore.get(item.id);
        if (state.reviewCount <= 0) return "新词 · 未复习";
        String level = state.lastQuality == Sm2Scheduler.QUALITY_REMEMBER ? "记得"
                : state.lastQuality == Sm2Scheduler.QUALITY_BLURRY ? "模糊" : "忘记";
        return level + " · 第 " + state.reviewCount + " 次 · 下次 " + formatRelative(state.nextReviewAt);
    }

    private String formatRelative(long timeMillis) {
        long now = System.currentTimeMillis();
        long diff = timeMillis - now;
        if (diff <= 0L) return "现在";
        long minute = 60L * 1000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (diff < hour) return Math.max(1, Math.round((float) diff / minute)) + "分钟后";
        if (diff < day) return Math.max(1, Math.round((float) diff / hour)) + "小时后";
        return Math.max(1, Math.round((float) diff / day)) + "天后";
    }

    private static class WordItem {
        final String id;
        final String word;
        final String pinyin;
        final String meaning;
        final String example;

        WordItem(String id, String word, String pinyin, String meaning, String example) {
            this.id = id;
            this.word = word;
            this.pinyin = pinyin;
            this.meaning = meaning;
            this.example = example;
        }
    }
}
