package com.chat.learning;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

/** Category-first entry for the interactive learning path. */
public class LearningCategoryActivity extends AppCompatActivity {
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
        LearningUiKit.applySystemBars(this, LearningUiKit.BG);
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
        page.setBackgroundColor(LearningUiKit.BG);
        setContentView(page);

        page.addView(createTopBar(), new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout widthHost = new FrameLayout(this);
        widthHost.setPadding(dp(16), 0, dp(16), dp(56));
        scroll.addView(widthHost, new ScrollView.LayoutParams(-1, -2));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipToPadding(false);
        int maximum = dp(620);
        int available = Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(32));
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                Math.min(maximum, available), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        widthHost.addView(content, contentLp);
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(4), dp(10), 0);

        TextView back = iconButton("‹", 32);
        back.setContentDescription(getString(R.string.learning_path_back));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView title = text(getString(R.string.learning_category_title), 18,
                LearningUiKit.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

        refreshButton = iconButton("↻", 22);
        refreshButton.setContentDescription(getString(R.string.learning_path_refresh));
        refreshButton.setOnClickListener(v -> loadCatalog(true));
        bar.addView(refreshButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        return bar;
    }

    private void loadCatalog(boolean manualRefresh) {
        if (refreshInFlight) return;
        int generation = ++loadGeneration;
        refreshInFlight = true;
        refreshButton.setEnabled(false);
        refreshButton.setAlpha(0.45f);
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
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
            refreshButton.setAlpha(1f);
        }
    }

    private boolean canApply(int generation) {
        return !destroyed && generation == loadGeneration && !isFinishing()
                && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private void showLoading() {
        content.removeAllViews();
        TextView loading = text(getString(R.string.learning_path_loading), 15,
                LearningUiKit.SUBTEXT, false);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(90), 0, dp(90));
        content.addView(loading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderCategories() {
        if (content == null || catalog == null) return;
        content.removeAllViews();

        TextView heading = text(getString(R.string.learning_category_heading), 28,
                LearningUiKit.TEXT, true);
        heading.setLetterSpacing(-0.015f);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.setMargins(dp(2), dp(14), dp(2), 0);
        content.addView(heading, headingLp);

        TextView sub = text(getString(R.string.learning_category_subtitle), 15,
                LearningUiKit.SUBTEXT, false);
        sub.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(dp(2), dp(8), dp(2), dp(24));
        content.addView(sub, subLp);

        int index = 0;
        for (LearningPathRepository.Course course : catalog.courses) {
            Map<String, LearningPathProgressStore.Progress> courseProgress = progressStore == null
                    ? new HashMap<>() : progressStore.loadCourse(course.id);
            for (LearningPathRepository.Unit unit : course.units) {
                final int cardIndex = index++;
                CategoryCard card = new CategoryCard(this, course, unit, courseProgress, cardIndex);
                card.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(65)
                            .withEndAction(() -> {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                                LearningPathActivity.open(this, course.id, unit.id);
                            }).start();
                });
                int cardHeight = getResources().getDisplayMetrics().widthPixels >= dp(700)
                        ? dp(210) : dp(188);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, cardHeight);
                lp.setMargins(0, 0, 0, dp(18));
                content.addView(card, lp);
            }
        }

        if (index == 0) {
            TextView empty = text(getString(R.string.learning_path_empty), 15,
                    LearningUiKit.SUBTEXT, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(72), dp(20), dp(72));
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
            setClipToOutline(true);
            setBackground(LearningUiKit.rounded(Color.WHITE, dp(26), 0, 0));
            setElevation(dp(4));

            String artworkSymbol = unit.lessons.isEmpty() ? "学" : unit.lessons.get(0).icon;
            LearningUiKit.CategoryArtworkView cover = new LearningUiKit.CategoryArtworkView(
                    context, course.accent, index, artworkSymbol);
            addView(cover, new FrameLayout.LayoutParams(-1, -1));

            LearningUiKit.ScrimView scrim = new LearningUiKit.ScrimView(context, course.accent);
            addView(scrim, new FrameLayout.LayoutParams(-1, -1));

            int total = unit.lessons.size();
            int completed = 0;
            for (LearningPathRepository.Lesson lesson : unit.lessons) {
                LearningPathProgressStore.Progress value = progress.get(lesson.id);
                if (value != null && value.completed()) completed++;
            }

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.BOTTOM);
            body.setPadding(dp(20), dp(16), dp(20), dp(18));
            addView(body, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout topRow = new LinearLayout(context);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2,
                    Gravity.TOP | Gravity.START);
            topLp.setMargins(dp(18), dp(16), dp(18), 0);
            addView(topRow, topLp);

            TextView badge = text(getString(R.string.learning_category_lessons, total),
                    12, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(11), dp(6), dp(11), dp(6));
            badge.setBackground(LearningUiKit.rounded(0x2FFFFFFF, dp(15), 0x46FFFFFF, dp(1)));
            topRow.addView(badge, new LinearLayout.LayoutParams(-2, -2));

            View spacer = new View(context);
            topRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

            String iconValue = unit.lessons.isEmpty() ? "★" : unit.lessons.get(0).icon;
            if (TextUtils.isEmpty(iconValue)) iconValue = "★";
            TextView icon = text(iconValue, iconValue.length() > 2 ? 17 : 23,
                    Color.WHITE, true);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(LearningUiKit.rounded(0x2AFFFFFF, dp(22), 0x42FFFFFF, dp(1)));
            topRow.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

            TextView title = text(unit.title, 25, Color.WHITE, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(1), 1f);
            body.addView(title, new LinearLayout.LayoutParams(-1, -2));

            String description = unit.subtitle.isEmpty() ? course.subtitle : unit.subtitle;
            TextView desc = text(description, 14, 0xE8FFFFFF, true);
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
            descLp.setMargins(0, dp(5), dp(52), 0);
            body.addView(desc, descLp);

            LinearLayout progressRow = new LinearLayout(context);
            progressRow.setOrientation(LinearLayout.HORIZONTAL);
            progressRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(24));
            rowLp.setMargins(0, dp(12), 0, 0);
            body.addView(progressRow, rowLp);

            LearningUiKit.ProgressView progressView = new LearningUiKit.ProgressView(context);
            progressView.setColors(0x4AFFFFFF, Color.WHITE);
            progressView.setProgress(completed, Math.max(1, total));
            progressRow.addView(progressView, new LinearLayout.LayoutParams(0, dp(8), 1f));

            TextView count = text(completed + "/" + total, 12, Color.WHITE, true);
            count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(52), -1);
            countLp.setMargins(dp(10), 0, 0, 0);
            progressRow.addView(count, countLp);

            TextView arrow = text("›", 25, Color.WHITE, true);
            arrow.setGravity(Gravity.CENTER);
            arrow.setBackground(LearningUiKit.rounded(0x2EFFFFFF, dp(20), 0x46FFFFFF, dp(1)));
            FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(dp(40), dp(40),
                    Gravity.END | Gravity.BOTTOM);
            arrowLp.setMargins(0, 0, dp(16), dp(45));
            addView(arrow, arrowLp);

            if (completed == total && total > 0) {
                TextView done = text("✓", 16, 0xFF4B5A23, true);
                done.setGravity(Gravity.CENTER);
                done.setBackground(LearningUiKit.raisedSelector(LearningUiKit.YELLOW,
                        LearningUiKit.YELLOW_DARK, dp(17), 0, 0, dp(4)));
                done.setContentDescription(getString(R.string.learning_path_completed));
                FrameLayout.LayoutParams doneLp = new FrameLayout.LayoutParams(dp(34), dp(38),
                        Gravity.TOP | Gravity.END);
                doneLp.setMargins(0, dp(21), dp(68), 0);
                addView(done, doneLp);
            }

            setContentDescription(unit.title + ", "
                    + getString(R.string.learning_path_progress, completed, total));
        }
    }

    private TextView iconButton(String value, float size) {
        TextView view = text(value, size, LearningUiKit.SUBTEXT, false);
        view.setGravity(Gravity.CENTER);
        view.setBackground(LearningUiKit.rounded(Color.TRANSPARENT, dp(23), 0, 0));
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = LearningUiKit.text(this, value, size, color, bold);
        if (bold) view.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return view;
    }

    private int dp(float value) {
        return LearningUiKit.dp(this, value);
    }
}
