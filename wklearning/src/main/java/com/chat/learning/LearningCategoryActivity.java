package com.chat.learning;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

/** Compact image-first course chooser for interactive practice. */
public class LearningCategoryActivity extends AppCompatActivity {
    private static final int[] COURSE_PALETTE = {
            0xFF6C63FF, 0xFF1CB0F6, 0xFF58CC02,
            0xFFFF7A59, 0xFFFF4B8B, 0xFF9B51E0
    };

    private LinearLayout content;
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
        loadCatalog();
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

        TextView title = text(getString(R.string.learning_category_title), 21,
                LearningUiKit.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setPadding(dp(16), dp(4), dp(16), 0);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout widthHost = new FrameLayout(this);
        widthHost.setPadding(dp(18), dp(10), dp(18), dp(48));
        scroll.addView(widthHost, new ScrollView.LayoutParams(-1, -2));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipToPadding(false);
        int available = Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(36));
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                Math.min(dp(680), available), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        widthHost.addView(content, contentLp);
    }

    private void loadCatalog() {
        if (refreshInFlight || destroyed) return;
        int generation = ++loadGeneration;
        refreshInFlight = true;
        if (catalog == null) showLoading();
        LearningRemoteContent.execute(() -> {
            LearningPathRepository.Catalog local = LearningPathRepository.load(getApplicationContext());
            runOnUiThread(() -> {
                if (!canApply(generation)) return;
                catalog = local;
                renderCourses();
                refreshCatalog(generation, local);
            });
        });
    }

    private void refreshCatalog(int generation, LearningPathRepository.Catalog local) {
        LearningPathRepository.refresh(getApplicationContext(), local,
                new LearningPathRepository.RefreshCallback() {
                    @Override
                    public void onUpdated(LearningPathRepository.Catalog updated) {
                        runOnUiThread(() -> {
                            if (!canApply(generation)) return;
                            refreshInFlight = false;
                            catalog = updated;
                            renderCourses();
                        });
                    }

                    @Override
                    public void onUnchanged() {
                        runOnUiThread(() -> {
                            if (!canApply(generation)) return;
                            refreshInFlight = false;
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (!canApply(generation)) return;
                            refreshInFlight = false;
                        });
                    }
                });
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
        loading.setPadding(0, dp(76), 0, dp(76));
        content.addView(loading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderCourses() {
        if (content == null || catalog == null) return;
        content.removeAllViews();

        int index = 0;
        for (LearningPathRepository.Course course : catalog.courses) {
            Map<String, LearningPathProgressStore.Progress> courseProgress = progressStore == null
                    ? new HashMap<>() : progressStore.loadCourse(course.id);
            CourseCard card = new CourseCard(this, course, courseProgress, index);
            card.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(65L)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(105L)
                                .withEndAction(() -> LearningPathActivity.open(this, course.id))
                                .start()).start();
            });
            int height = getResources().getDisplayMetrics().widthPixels >= dp(700)
                    ? dp(196) : dp(176);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height);
            lp.setMargins(0, index == 0 ? dp(4) : 0, 0, dp(16));
            content.addView(card, lp);
            index++;
        }

        if (index == 0) {
            TextView empty = text(getString(R.string.learning_path_empty), 15,
                    LearningUiKit.SUBTEXT, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(72), dp(20), dp(72));
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private int courseColor(LearningPathRepository.Course course, int index) {
        int raw = course == null ? COURSE_PALETTE[Math.floorMod(index, COURSE_PALETTE.length)]
                : course.accent;
        float[] hsv = new float[3];
        Color.colorToHSV(raw, hsv);
        hsv[1] = Math.max(0.68f, hsv[1]);
        hsv[2] = Math.max(0.82f, hsv[2]);
        return Color.HSVToColor(255, hsv);
    }

    private GradientDrawable gradient(int start, int end, float radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private final class CourseCard extends FrameLayout {
        CourseCard(Context context, LearningPathRepository.Course course,
                   Map<String, LearningPathProgressStore.Progress> progress, int index) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setClipToOutline(true);
            setElevation(dp(4));

            int accent = courseColor(course, index);
            int end = LearningUiKit.blend(accent, Color.BLACK, 0.14f);
            setBackground(gradient(accent, end, dp(24)));

            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cover.setAdjustViewBounds(true);
            cover.setPadding(0, dp(2), dp(4), 0);
            int coverRes = resolveCover(course);
            if (coverRes != 0) cover.setImageResource(coverRes);
            FrameLayout.LayoutParams coverLp = new FrameLayout.LayoutParams(
                    dp(184), -1, Gravity.END | Gravity.CENTER_VERTICAL);
            coverLp.setMargins(0, 0, dp(2), 0);
            addView(cover, coverLp);

            View leftScrim = new View(context);
            GradientDrawable scrim = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x40000000, 0x12000000, 0x00000000});
            scrim.setCornerRadius(dp(24));
            leftScrim.setBackground(scrim);
            addView(leftScrim, new FrameLayout.LayoutParams(-1, -1));

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
            body.setGravity(Gravity.CENTER_VERTICAL);
            body.setPadding(dp(20), dp(15), dp(152), dp(15));
            addView(body, new FrameLayout.LayoutParams(-1, -1));

            TextView meta = text(getString(R.string.learning_course_stats,
                    course.units.size(), total), 12, 0xEFFFFFFF, true);
            meta.setSingleLine(true);
            body.addView(meta, new LinearLayout.LayoutParams(-1, dp(24)));

            TextView title = text(course.title, 26, Color.WHITE, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(1), 1f);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, 0, 1f);
            titleLp.setMargins(0, dp(2), 0, dp(5));
            body.addView(title, titleLp);

            LinearLayout progressRow = new LinearLayout(context);
            progressRow.setOrientation(LinearLayout.HORIZONTAL);
            progressRow.setGravity(Gravity.CENTER_VERTICAL);
            body.addView(progressRow, new LinearLayout.LayoutParams(-1, dp(24)));

            LearningUiKit.ProgressView progressView = new LearningUiKit.ProgressView(context);
            progressView.setColors(0x58FFFFFF, Color.WHITE);
            progressView.setProgress(completed, Math.max(1, total));
            progressRow.addView(progressView, new LinearLayout.LayoutParams(0, dp(8), 1f));

            TextView count = text(completed + "/" + total, 12, Color.WHITE, true);
            count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(48), -1);
            countLp.setMargins(dp(8), 0, 0, 0);
            progressRow.addView(count, countLp);

            setContentDescription(course.title + ", "
                    + getString(R.string.learning_path_progress, completed, total));
        }

        private int resolveCover(LearningPathRepository.Course course) {
            String name = course == null ? "" : course.coverDrawable;
            if (name.isEmpty() && course != null && "zh_beginner".equals(course.id)) {
                name = "learning_course_beginner";
            }
            if (name.isEmpty()) return 0;
            if ("learning_course_beginner".equals(name)) {
                return R.drawable.learning_course_beginner;
            }
            return getResources().getIdentifier(name, "drawable", getPackageName());
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return LearningUiKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return LearningUiKit.dp(this, value);
    }
}
