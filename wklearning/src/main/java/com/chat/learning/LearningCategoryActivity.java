package com.chat.learning;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * Entry page for interactive practice.
 *
 * The user first chooses a scene/category (a learning unit), then enters that
 * category's path. Keeping this page separate also prevents a large course
 * catalog from being rendered as one endless map.
 */
public class LearningCategoryActivity extends AppCompatActivity {
    private static final int COLOR_BG = 0xFFF4F5F7;
    private static final int COLOR_TEXT = 0xFF3C3C3C;
    private static final int COLOR_SUB = 0xFF777777;

    private LinearLayout content;
    private TextView refreshButton;
    private LearningPathRepository.Catalog catalog;
    private LearningPathProgressStore progressStore;
    private int loadGeneration;
    private boolean destroyed;
    private boolean refreshInFlight;

    public static void open(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, LearningCategoryActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        progressStore = new LearningPathProgressStore(this);
        buildLayout();
        loadCatalog(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (catalog != null) renderCategories();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        loadGeneration++;
        if (progressStore != null) progressStore.close();
        progressStore = null;
        super.onDestroy();
    }

    private void buildLayout() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(COLOR_BG);
        setContentView(page);

        page.addView(createTopBar(), new LinearLayout.LayoutParams(-1, dp(62)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(56));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), 0);

        TextView back = roundIcon("‹", 30, 0xFF777777, Color.WHITE);
        back.setContentDescription(getString(R.string.learning_path_back));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text(getString(R.string.learning_category_title), 19, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

        refreshButton = roundIcon("↻", 21, 0xFF777777, Color.WHITE);
        refreshButton.setContentDescription(getString(R.string.learning_path_refresh));
        refreshButton.setOnClickListener(v -> loadCatalog(true));
        bar.addView(refreshButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private void loadCatalog(boolean manualRefresh) {
        if (refreshInFlight) return;
        int generation = ++loadGeneration;
        refreshInFlight = true;
        refreshButton.setEnabled(false);
        if (manualRefresh) refreshButton.animate().rotationBy(360f).setDuration(500).start();
        if (catalog == null) showLoading();
        LearningRemoteContent.execute(() -> {
            LearningPathRepository.Catalog local = LearningPathRepository.load(getApplicationContext());
            runOnUiThread(() -> {
                if (!canApply(generation)) return;
                catalog = local;
                renderCategories();
                refreshCatalog(generation, local, manualRefresh);
            });
        });
    }

    private void refreshCatalog(int generation, LearningPathRepository.Catalog local,
                                boolean manualRefresh) {
        LearningPathRepository.refresh(getApplicationContext(), local,
                new LearningPathRepository.RefreshCallback() {
                    @Override
                    public void onUpdated(LearningPathRepository.Catalog updated) {
                        runOnUiThread(() -> {
                            if (!canApply(generation)) return;
                            finishRefresh();
                            catalog = updated;
                            renderCategories();
                            if (manualRefresh) Toast.makeText(LearningCategoryActivity.this,
                                    R.string.learning_path_updated, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onUnchanged() {
                        runOnUiThread(() -> {
                            if (!canApply(generation)) return;
                            finishRefresh();
                            if (manualRefresh) Toast.makeText(LearningCategoryActivity.this,
                                    R.string.learning_path_latest, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (!canApply(generation)) return;
                            finishRefresh();
                            if (manualRefresh) Toast.makeText(LearningCategoryActivity.this,
                                    getString(R.string.learning_path_refresh_failed, message),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void finishRefresh() {
        refreshInFlight = false;
        if (refreshButton != null) refreshButton.setEnabled(true);
    }

    private boolean canApply(int generation) {
        return !destroyed && generation == loadGeneration && !isFinishing()
                && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private void showLoading() {
        content.removeAllViews();
        TextView loading = text(getString(R.string.learning_path_loading), 15, COLOR_SUB, false);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(70), 0, dp(70));
        content.addView(loading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderCategories() {
        if (content == null || catalog == null) return;
        content.removeAllViews();

        TextView heading = text(getString(R.string.learning_category_heading), 27, COLOR_TEXT, true);
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text(getString(R.string.learning_category_subtitle), 14, COLOR_SUB, false);
        sub.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(20));
        content.addView(sub, subLp);

        int index = 0;
        for (LearningPathRepository.Course course : catalog.courses) {
            Map<String, LearningPathProgressStore.Progress> courseProgress = progressStore == null
                    ? new HashMap<>() : progressStore.loadCourse(course.id);
            for (LearningPathRepository.Unit unit : course.units) {
                CategoryCard card = new CategoryCard(this, course, unit, courseProgress, index++);
                card.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    LearningPathActivity.open(this, course.id, unit.id);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(172));
                lp.setMargins(0, 0, 0, dp(16));
                content.addView(card, lp);
            }
        }

        if (index == 0) {
            TextView empty = text(getString(R.string.learning_path_empty), 15, COLOR_SUB, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(58), dp(20), dp(58));
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private final class CategoryCard extends FrameLayout {
        CategoryCard(Context context, LearningPathRepository.Course course,
                     LearningPathRepository.Unit unit,
                     Map<String, LearningPathProgressStore.Progress> progress, int index) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setClipToOutline(false);
            setElevation(dp(2));

            int accent = course.accent;
            int second = blend(accent, index % 2 == 0 ? 0xFF1CB0F6 : 0xFFFFB020, 0.34f);
            setBackground(gradient(accent, second, dp(27)));

            addView(new CategoryArtView(context, accent), new FrameLayout.LayoutParams(-1, -1));

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            body.setPadding(dp(20), dp(16), dp(94), dp(16));
            addView(body, new FrameLayout.LayoutParams(-1, -1));

            int total = unit.lessons.size();
            int completed = 0;
            for (LearningPathRepository.Lesson lesson : unit.lessons) {
                LearningPathProgressStore.Progress value = progress.get(lesson.id);
                if (value != null && value.completed()) completed++;
            }

            TextView badge = text(getString(R.string.learning_category_lessons, total),
                    11, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(10), dp(5), dp(10), dp(5));
            badge.setBackground(round(0x32FFFFFF, dp(14), 0x50FFFFFF, dp(1)));
            body.addView(badge, new LinearLayout.LayoutParams(-2, -2));

            TextView title = text(unit.title, 23, Color.WHITE, true);
            title.setMaxLines(2);
            title.setLineSpacing(dp(1), 1f);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.setMargins(0, dp(13), 0, dp(4));
            body.addView(title, titleLp);

            TextView desc = text(unit.subtitle.isEmpty() ? course.subtitle : unit.subtitle,
                    13, 0xEFFFFFFF, false);
            desc.setMaxLines(2);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            body.addView(desc, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout progressRow = new LinearLayout(context);
            progressRow.setOrientation(LinearLayout.HORIZONTAL);
            progressRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(22));
            rowLp.setMargins(0, dp(13), 0, 0);
            body.addView(progressRow, rowLp);

            ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(Math.max(1, total));
            bar.setProgress(completed);
            bar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x45FFFFFF));
            progressRow.addView(bar, new LinearLayout.LayoutParams(0, dp(8), 1f));

            TextView count = text(completed + "/" + total, 12, Color.WHITE, true);
            count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(52), -1);
            countLp.setMargins(dp(10), 0, 0, 0);
            progressRow.addView(count, countLp);

            String iconValue = unit.lessons.isEmpty() ? "★" : unit.lessons.get(0).icon;
            if (iconValue == null || iconValue.trim().isEmpty()) iconValue = "★";
            TextView artIcon = text(iconValue, iconValue.length() > 2 ? 20 : 30,
                    Color.WHITE, true);
            artIcon.setGravity(Gravity.CENTER);
            artIcon.setBackground(round(0x2FFFFFFF, dp(32), 0x55FFFFFF, dp(1)));
            FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(dp(64), dp(64),
                    Gravity.END | Gravity.CENTER_VERTICAL);
            artLp.setMargins(0, 0, dp(17), 0);
            addView(artIcon, artLp);

            TextView arrow = text("›", 20, Color.WHITE, true);
            arrow.setGravity(Gravity.CENTER);
            arrow.setBackground(round(0x42FFFFFF, dp(14), 0, 0));
            FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(dp(28), dp(28),
                    Gravity.END | Gravity.BOTTOM);
            arrowLp.setMargins(0, 0, dp(13), dp(12));
            addView(arrow, arrowLp);

            if (completed == total && total > 0) {
                TextView done = text("★", 20, 0xFFFFD75E, true);
                done.setGravity(Gravity.CENTER);
                done.setContentDescription(getString(R.string.learning_path_completed));
                FrameLayout.LayoutParams doneLp = new FrameLayout.LayoutParams(dp(38), dp(38),
                        Gravity.TOP | Gravity.END);
                doneLp.setMargins(0, dp(13), dp(13), 0);
                addView(done, doneLp);
            }

            setContentDescription(unit.title + ", "
                    + getString(R.string.learning_path_progress, completed, total));
        }

        @Override
        public boolean performClick() {
            super.performClick();
            animate().scaleX(0.985f).scaleY(0.985f).setDuration(60)
                    .withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                    .start();
            return true;
        }
    }

    private static final class CategoryArtView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;

        CategoryArtView(Context context, int accent) {
            super(context);
            this.accent = accent;
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x18FFFFFF);
            canvas.drawCircle(w - h * 0.12f, -h * 0.05f, h * 0.78f, paint);
            paint.setColor(0x10FFFFFF);
            canvas.drawCircle(w - h * 0.72f, h * 1.02f, h * 0.58f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, h * 0.012f));
            paint.setColor(0x20FFFFFF);
            canvas.drawCircle(w * 0.77f, h * 0.52f, h * 0.34f, paint);
        }
    }

    private TextView roundIcon(String value, float size, int color, int background) {
        TextView view = text(value, size, color, false);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(background, dp(22), 0xFFE5E5E5, dp(1)));
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable round(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, float radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int blend(int first, int second, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = (int) (Color.red(first) * (1f - t) + Color.red(second) * t);
        int g = (int) (Color.green(first) * (1f - t) + Color.green(second) * t);
        int b = (int) (Color.blue(first) * (1f - t) + Color.blue(second) * t);
        return Color.rgb(r, g, b);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
