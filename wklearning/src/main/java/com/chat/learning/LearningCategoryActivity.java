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

/** Course-first entry for interactive practice. */
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
        if (catalog != null) renderCourses();
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
        int available = Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(32));
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                Math.min(dp(680), available), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
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
                renderCourses();
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
                            renderCourses();
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

    private void renderCourses() {
        if (content == null || catalog == null) return;
        content.removeAllViews();

        TextView heading = text(getString(R.string.learning_category_heading), 29,
                LearningUiKit.TEXT, true);
        heading.setLetterSpacing(-0.018f);
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
            CourseCard card = new CourseCard(this, course, courseProgress, index++);
            card.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                v.animate().translationY(dp(3)).scaleX(0.988f).scaleY(0.988f).setDuration(65)
                        .withEndAction(() -> {
                            v.animate().translationY(0).scaleX(1f).scaleY(1f)
                                    .setDuration(110).start();
                            LearningPathActivity.open(this, course.id);
                        }).start();
            });
            int cardHeight = getResources().getDisplayMetrics().widthPixels >= dp(700)
                    ? dp(264) : dp(232);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, cardHeight);
            lp.setMargins(0, 0, 0, dp(20));
            content.addView(card, lp);
        }

        if (index == 0) {
            TextView empty = text(getString(R.string.learning_path_empty), 15,
                    LearningUiKit.SUBTEXT, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(72), dp(20), dp(72));
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private final class CourseCard extends FrameLayout {
        CourseCard(Context context, LearningPathRepository.Course course,
                   Map<String, LearningPathProgressStore.Progress> progress, int index) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setClipToOutline(true);
            setBackground(LearningUiKit.rounded(Color.WHITE, dp(28), 0, 0));
            setElevation(dp(4));

            LearningUiKit.CategoryArtworkView cover = new LearningUiKit.CategoryArtworkView(
                    context, course.accent, index, "中文");
            addView(cover, new FrameLayout.LayoutParams(-1, -1));
            addView(new LearningUiKit.ScrimView(context, course.accent),
                    new FrameLayout.LayoutParams(-1, -1));

            LearningUiKit.CharacterView first = new LearningUiKit.CharacterView(
                    context, course.accent, index * 2, "book");
            FrameLayout.LayoutParams firstLp = new FrameLayout.LayoutParams(dp(116), dp(152),
                    Gravity.END | Gravity.BOTTOM);
            firstLp.setMargins(0, 0, dp(54), dp(4));
            addView(first, firstLp);

            LearningUiKit.CharacterView second = new LearningUiKit.CharacterView(
                    context, LearningUiKit.blend(course.accent, LearningUiKit.PURPLE, 0.36f),
                    index * 2 + 1, "wave");
            FrameLayout.LayoutParams secondLp = new FrameLayout.LayoutParams(dp(95), dp(128),
                    Gravity.END | Gravity.BOTTOM);
            secondLp.setMargins(0, 0, dp(2), dp(2));
            addView(second, secondLp);

            int total = 0;
            int completed = 0;
            for (LearningPathRepository.Unit unit : course.units) {
                for (LearningPathRepository.Lesson lesson : unit.lessons) {
                    total++;
                    LearningPathProgressStore.Progress value = progress.get(lesson.id);
                    if (value != null && value.completed()) completed++;
                }
            }

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.BOTTOM);
            body.setPadding(dp(21), dp(17), dp(150), dp(19));
            addView(body, new FrameLayout.LayoutParams(-1, -1));

            TextView badge = text(getString(R.string.learning_course_stats,
                    course.units.size(), total), 12, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(11), dp(6), dp(11), dp(6));
            badge.setBackground(LearningUiKit.rounded(0x2FFFFFFF, dp(15),
                    0x46FFFFFF, dp(1)));
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.START);
            badgeLp.setMargins(dp(19), dp(17), 0, 0);
            addView(badge, badgeLp);

            TextView title = text(course.title, 28, Color.WHITE, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(1), 1f);
            body.addView(title, new LinearLayout.LayoutParams(-1, -2));

            TextView desc = text(course.subtitle, 14, 0xE8FFFFFF, true);
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
            descLp.setMargins(0, dp(5), 0, 0);
            body.addView(desc, descLp);

            LinearLayout chipRow = new LinearLayout(context);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(-1, dp(29));
            chipsLp.setMargins(0, dp(11), 0, 0);
            body.addView(chipRow, chipsLp);
            int visibleUnits = Math.min(3, course.units.size());
            for (int i = 0; i < visibleUnits; i++) {
                LearningPathRepository.Unit unit = course.units.get(i);
                String name = unit.title;
                int split = name.indexOf('·');
                if (split >= 0 && split + 1 < name.length()) name = name.substring(split + 1).trim();
                TextView chip = text(name, 11, Color.WHITE, true);
                chip.setSingleLine(true);
                chip.setEllipsize(TextUtils.TruncateAt.END);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(9), 0, dp(9), 0);
                chip.setBackground(LearningUiKit.rounded(0x26FFFFFF, dp(14),
                        0x32FFFFFF, dp(1)));
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (i > 0) chipLp.setMargins(dp(6), 0, 0, 0);
                chipRow.addView(chip, chipLp);
            }

            LinearLayout progressRow = new LinearLayout(context);
            progressRow.setOrientation(LinearLayout.HORIZONTAL);
            progressRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(24));
            rowLp.setMargins(0, dp(10), 0, 0);
            body.addView(progressRow, rowLp);

            LearningUiKit.ProgressView progressView = new LearningUiKit.ProgressView(context);
            progressView.setColors(0x4AFFFFFF, Color.WHITE);
            progressView.setProgress(completed, Math.max(1, total));
            progressRow.addView(progressView, new LinearLayout.LayoutParams(0, dp(8), 1f));

            TextView count = text(completed + "/" + total, 12, Color.WHITE, true);
            count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(50), -1);
            countLp.setMargins(dp(9), 0, 0, 0);
            progressRow.addView(count, countLp);

            setContentDescription(course.title + ", "
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
        return LearningUiKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return LearningUiKit.dp(this, value);
    }
}
