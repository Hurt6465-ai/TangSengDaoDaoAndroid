package com.chat.learning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A category-specific, Duolingo-inspired learning map without connector lines. */
public class LearningPathActivity extends AppCompatActivity {
    private static final String EXTRA_COURSE_ID = "course_id";
    private static final String EXTRA_UNIT_ID = "unit_id";
    private static final int COLOR_BG = 0xFFF4F5F7;
    private static final int COLOR_TEXT = 0xFF3C3C3C;
    private static final int COLOR_SUB = 0xFF777777;
    private static final int COLOR_BORDER = 0xFFE5E5E5;
    private static final int COLOR_GREEN = 0xFF58CC02;
    private static final int COLOR_GREEN_DARK = 0xFF46A302;
    private static final int COLOR_BLUE = 0xFF1CB0F6;
    private static final int COLOR_BLUE_DARK = 0xFF1899D6;
    private static final int COLOR_YELLOW = 0xFFFFC800;
    private static final int COLOR_YELLOW_DARK = 0xFFE5A500;
    private static final int COLOR_LOCKED = 0xFFE5E5E5;
    private static final int COLOR_LOCKED_DARK = 0xFFCFCFCF;

    private LinearLayout content;
    private TextView pageTitle;
    private TextView refreshButton;

    private LearningPathRepository.Catalog catalog;
    private LearningPathRepository.Course course;
    private LearningPathRepository.Unit selectedUnit;
    private Map<String, LearningPathProgressStore.Progress> progress = new HashMap<>();
    private final Map<String, NodeView> nodeViews = new HashMap<>();
    private final Map<String, DownloadUiState> downloads = new HashMap<>();
    private final Map<String, LearningPackageDownloader.Subscription> downloadSubscriptions = new HashMap<>();
    private String pendingOpenLessonId = "";
    private String selectedCourseId = "";
    private String selectedUnitId = "";
    private LearningPathProgressStore progressStore;
    private long lastClickAt;
    private int catalogGeneration;
    private boolean refreshInFlight;
    private boolean destroyed;
    private boolean resumed;

    public static void open(Context context) {
        LearningCategoryActivity.open(context);
    }

    public static void open(Context context, String courseId) {
        open(context, courseId, "");
    }

    public static void open(Context context, String courseId, String unitId) {
        if (context == null) return;
        Intent intent = new Intent(context, LearningPathActivity.class);
        if (courseId != null && !courseId.trim().isEmpty()) {
            intent.putExtra(EXTRA_COURSE_ID, courseId.trim());
        }
        if (unitId != null && !unitId.trim().isEmpty()) {
            intent.putExtra(EXTRA_UNIT_ID, unitId.trim());
        }
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
        selectedCourseId = safe(getIntent().getStringExtra(EXTRA_COURSE_ID));
        selectedUnitId = safe(getIntent().getStringExtra(EXTRA_UNIT_ID));
        buildLayout();
        loadCatalog(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (course != null && progressStore != null) {
            progress = progressStore.loadCourse(course.id);
            observeActiveDownloads();
            renderPath();
            maybeOpenPendingLesson();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        catalogGeneration++;
        cancelDownloadObservers();
        pendingOpenLessonId = "";
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
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(14), dp(8), dp(14), dp(84));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), 0);

        TextView back = circleButton("‹", 30);
        back.setContentDescription(getString(R.string.learning_path_back));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        pageTitle = text(getString(R.string.learning_path_title), 18, COLOR_TEXT, true);
        pageTitle.setGravity(Gravity.CENTER);
        pageTitle.setSingleLine(true);
        pageTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -1, 1f);
        titleLp.setMargins(dp(8), 0, dp(8), 0);
        bar.addView(pageTitle, titleLp);

        refreshButton = circleButton("↻", 21);
        refreshButton.setContentDescription(getString(R.string.learning_path_refresh));
        refreshButton.setOnClickListener(v -> loadCatalog(true));
        bar.addView(refreshButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private void loadCatalog(boolean manualRefresh) {
        if (destroyed || (manualRefresh && refreshInFlight)) return;
        final int generation = ++catalogGeneration;
        if (manualRefresh) {
            refreshInFlight = true;
            refreshButton.setEnabled(false);
            refreshButton.setAlpha(0.55f);
            refreshButton.animate().rotationBy(360f).setDuration(600).start();
        }
        if (catalog == null) showLoading();
        Context app = getApplicationContext();
        LearningRemoteContent.execute(() -> {
            LearningPathRepository.Catalog local = LearningPathRepository.load(app);
            runOnUiThread(() -> {
                if (!canApply(generation)) return;
                applyCatalog(local);
                refreshCatalog(app, local, generation, manualRefresh);
            });
        });
    }

    private void refreshCatalog(Context app, LearningPathRepository.Catalog local,
                                int generation, boolean manualRefresh) {
        LearningPathRepository.refresh(app, local, new LearningPathRepository.RefreshCallback() {
            @Override
            public void onUpdated(LearningPathRepository.Catalog updated) {
                runOnUiThread(() -> {
                    if (!canApply(generation)) return;
                    finishRefresh();
                    applyCatalog(updated);
                    if (manualRefresh) Toast.makeText(LearningPathActivity.this,
                            R.string.learning_path_updated, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onUnchanged() {
                runOnUiThread(() -> {
                    if (!canApply(generation)) return;
                    finishRefresh();
                    if (manualRefresh) Toast.makeText(LearningPathActivity.this,
                            R.string.learning_path_latest, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (!canApply(generation)) return;
                    finishRefresh();
                    if (manualRefresh) Toast.makeText(LearningPathActivity.this,
                            getString(R.string.learning_path_refresh_failed, message),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private boolean canApply(int generation) {
        return !destroyed && generation == catalogGeneration && !isFinishing()
                && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private void finishRefresh() {
        refreshInFlight = false;
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
            refreshButton.setAlpha(1f);
        }
    }

    private void applyCatalog(LearningPathRepository.Catalog updated) {
        cancelDownloadObservers();
        downloads.clear();
        catalog = updated;
        course = LearningPathRepository.findCourse(updated, selectedCourseId);
        if (course == null) course = LearningPathRepository.firstCourse(updated);
        selectedCourseId = course == null ? "" : course.id;
        selectedUnit = findUnit(course, selectedUnitId);
        if (selectedUnit == null && course != null && !course.units.isEmpty()) {
            selectedUnit = course.units.get(0);
            selectedUnitId = selectedUnit.id;
        }
        progress = course == null || progressStore == null
                ? new HashMap<>() : progressStore.loadCourse(course.id);
        observeActiveDownloads();
        renderPath();
    }

    private LearningPathRepository.Unit findUnit(LearningPathRepository.Course target, String unitId) {
        if (target == null || unitId == null) return null;
        for (LearningPathRepository.Unit unit : target.units) {
            if (unitId.equals(unit.id)) return unit;
        }
        return null;
    }

    private void showLoading() {
        if (content == null) return;
        content.removeAllViews();
        TextView loading = text(getString(R.string.learning_path_loading), 15, COLOR_SUB, false);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(80), 0, dp(80));
        content.addView(loading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void cancelDownloadObservers() {
        for (LearningPackageDownloader.Subscription subscription : downloadSubscriptions.values()) {
            if (subscription != null) subscription.cancel();
        }
        downloadSubscriptions.clear();
    }

    private void renderPath() {
        if (content == null) return;
        content.removeAllViews();
        nodeViews.clear();
        if (course == null || selectedUnit == null) {
            pageTitle.setText(R.string.learning_path_title);
            content.addView(emptyView(getString(R.string.learning_path_empty)),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        if (course.minAppVersion > currentVersionCode()) {
            pageTitle.setText(selectedUnit.title);
            content.addView(emptyView(getString(R.string.learning_path_app_update_required)),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        pageTitle.setText(selectedUnit.title);
        String currentLesson = findCurrentLesson(selectedUnit.lessons);

        int completed = 0;
        for (LearningPathRepository.Lesson lesson : selectedUnit.lessons) {
            LearningPathProgressStore.Progress item = progress.get(lesson.id);
            if (item != null && item.completed()) completed++;
        }
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(0, dp(5), 0, dp(14));
        content.addView(categoryHeader(selectedUnit, completed, selectedUnit.lessons.size()), headerLp);

        if (selectedUnit.lessons.isEmpty()) {
            content.addView(emptyView(getString(R.string.learning_path_empty)),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        PathLayout path = new PathLayout(this);
        for (LearningPathRepository.Lesson lesson : selectedUnit.lessons) {
            NodeState state = stateFor(lesson, currentLesson);
            NodeView node = new NodeView(this, course.accent);
            node.bind(lesson, state, progress.get(lesson.id), downloads.get(lesson.id));
            node.setOnClickListener(v -> throttled(() -> onLessonClick(lesson, node.nodeState())));
            nodeViews.put(lesson.id, node);
            path.addNode(node, lesson.position);
        }
        LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(-1, -2);
        pathLp.setMargins(0, dp(10), 0, dp(10));
        content.addView(path, pathLp);

        TextView tip = text(getString(R.string.learning_path_map_tip), 12, COLOR_SUB, false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(12), dp(10), dp(12), dp(10));
        content.addView(tip, new LinearLayout.LayoutParams(-1, -2));
    }

    private View categoryHeader(LearningPathRepository.Unit unit, int completed, int total) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(17));
        int accent = course == null ? COLOR_GREEN : course.accent;
        int end = blend(accent, COLOR_BLUE, 0.30f);
        card.setBackground(gradient(accent, end, dp(26)));

        TextView overline = text(getString(R.string.learning_category_section), 11,
                0xEFFFFFFF, true);
        overline.setAllCaps(true);
        card.addView(overline, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text(unit.title, 23, Color.WHITE, true);
        title.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(7), 0, dp(4));
        card.addView(title, titleLp);

        if (!unit.subtitle.isEmpty()) {
            TextView subtitle = text(unit.subtitle, 13, 0xEFFFFFFF, false);
            subtitle.setLineSpacing(dp(2), 1f);
            card.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(28));
        rowLp.setMargins(0, dp(15), 0, 0);
        card.addView(row, rowLp);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(1, total));
        bar.setProgress(completed);
        bar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x45FFFFFF));
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(9), 1f));

        TextView count = text(completed + " / " + total, 12, Color.WHITE, true);
        count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(68), -1);
        countLp.setMargins(dp(10), 0, 0, 0);
        row.addView(count, countLp);

        return card;
    }

    private String findCurrentLesson(List<LearningPathRepository.Lesson> lessons) {
        for (LearningPathRepository.Lesson lesson : lessons) {
            LearningPathProgressStore.Progress item = progress.get(lesson.id);
            if ((item == null || !item.completed()) && requirementsMet(lesson)) return lesson.id;
        }
        return "";
    }

    private NodeState stateFor(LearningPathRepository.Lesson lesson, String currentLesson) {
        DownloadUiState download = downloads.get(lesson.id);
        LearningPackageDownloader.Status status = LearningPackageDownloader.status(lesson);
        if ((download != null && download.active) || (status != null && status.active())) {
            return NodeState.DOWNLOADING;
        }
        LearningPathProgressStore.Progress item = progress.get(lesson.id);
        if (item != null && item.completed()) return NodeState.COMPLETED;
        if (!requirementsMet(lesson)) return NodeState.LOCKED;
        return lesson.id.equals(currentLesson) ? NodeState.CURRENT : NodeState.AVAILABLE;
    }

    private boolean requirementsMet(LearningPathRepository.Lesson lesson) {
        for (String required : lesson.requiredLessons) {
            LearningPathProgressStore.Progress item = progress.get(required);
            if (item == null || !item.completed()) return false;
        }
        return true;
    }

    private void onLessonClick(LearningPathRepository.Lesson lesson, NodeState state) {
        if (state == NodeState.LOCKED) {
            Toast.makeText(this, R.string.learning_path_locked_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!lesson.needsRemotePackage()) {
            openLesson(lesson);
            return;
        }
        if (LearningPackageDownloader.installedLessonFile(this, lesson) != null) {
            openLesson(lesson);
            return;
        }
        if (state == NodeState.DOWNLOADING) {
            pendingOpenLessonId = lesson.id;
            observeDownload(lesson);
            Toast.makeText(this, R.string.learning_path_downloading, Toast.LENGTH_SHORT).show();
            return;
        }
        showDownloadDialog(lesson);
    }

    private void showDownloadDialog(LearningPathRepository.Lesson lesson) {
        String size = lesson.packageSize > 0L ? formatBytes(lesson.packageSize)
                : getString(R.string.learning_path_size_unknown);
        new AlertDialog.Builder(this)
                .setTitle(lesson.title)
                .setMessage(getString(R.string.learning_path_download_message,
                        lesson.exerciseCount, lesson.minutes, size))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.learning_path_download_start, (dialog, which) ->
                        startDownload(lesson))
                .show();
    }

    private void startDownload(LearningPathRepository.Lesson lesson) {
        pendingOpenLessonId = lesson.id;
        DownloadUiState ui = new DownloadUiState();
        ui.active = true;
        ui.progress = 0;
        ui.message = getString(R.string.learning_path_downloading);
        downloads.put(lesson.id, ui);
        updateNodeDownload(lesson.id);
        observeDownload(lesson);
    }

    private void observeActiveDownloads() {
        if (course == null) return;
        for (LearningPathRepository.Lesson lesson : LearningPathRepository.flatten(course)) {
            LearningPackageDownloader.Status status = LearningPackageDownloader.status(lesson);
            if (status == null || !status.active()) continue;
            DownloadUiState ui = new DownloadUiState();
            ui.active = true;
            ui.progress = status.progress;
            ui.message = status.message;
            downloads.put(lesson.id, ui);
            observeDownload(lesson);
        }
    }

    private void observeDownload(LearningPathRepository.Lesson lesson) {
        LearningPackageDownloader.Subscription previous = downloadSubscriptions.remove(lesson.id);
        if (previous != null) previous.cancel();
        LearningPackageDownloader.Subscription subscription =
                LearningPackageDownloader.downloadAndInstall(this, lesson, (state, value, message) ->
                        runOnUiThread(() -> handleDownloadState(lesson, state, value, message)));
        downloadSubscriptions.put(lesson.id, subscription);
        LearningPackageDownloader.Status latest = LearningPackageDownloader.status(lesson);
        if (latest == null || !latest.active()) {
            LearningPackageDownloader.Subscription stored = downloadSubscriptions.remove(lesson.id);
            if (stored != null) stored.cancel();
        }
    }

    private void handleDownloadState(LearningPathRepository.Lesson lesson,
                                     LearningPackageDownloader.State state, int value, String message) {
        if (destroyed || isFinishing() || course == null || lesson == null
                || !course.id.equals(lesson.courseId)) return;
        LearningPathRepository.Lesson currentLesson = LearningPathRepository.findLesson(course, lesson.id);
        if (currentLesson == null || !currentLesson.packageKey().equals(lesson.packageKey())) return;
        DownloadUiState current = downloads.get(lesson.id);
        if (current == null) current = new DownloadUiState();
        current.message = message;
        current.progress = value;
        current.active = state == LearningPackageDownloader.State.DOWNLOADING
                || state == LearningPackageDownloader.State.VERIFYING
                || state == LearningPackageDownloader.State.INSTALLING;
        downloads.put(lesson.id, current);
        updateNodeDownload(lesson.id);
        if (state == LearningPackageDownloader.State.ERROR
                || state == LearningPackageDownloader.State.READY) {
            LearningPackageDownloader.Subscription subscription = downloadSubscriptions.remove(lesson.id);
            if (subscription != null) subscription.cancel();
            downloads.remove(lesson.id);
            updateNodeDownload(lesson.id);
        }
        if (state == LearningPackageDownloader.State.ERROR) {
            if (lesson.id.equals(pendingOpenLessonId)) pendingOpenLessonId = "";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else if (state == LearningPackageDownloader.State.READY
                && lesson.id.equals(pendingOpenLessonId) && resumed) {
            pendingOpenLessonId = "";
            openLesson(currentLesson);
        }
    }

    private void maybeOpenPendingLesson() {
        if (!resumed || course == null || pendingOpenLessonId.isEmpty()) return;
        LearningPathRepository.Lesson lesson = LearningPathRepository.findLesson(course, pendingOpenLessonId);
        if (lesson == null) {
            pendingOpenLessonId = "";
            return;
        }
        LearningPackageDownloader.Status status = LearningPackageDownloader.status(lesson);
        if (status != null && status.active()) return;
        if (LearningPackageDownloader.installedLessonFile(this, lesson) != null) {
            pendingOpenLessonId = "";
            openLesson(lesson);
        }
    }

    private void updateNodeDownload(String lessonId) {
        if (course == null) return;
        NodeView node = nodeViews.get(lessonId);
        LearningPathRepository.Lesson lesson = LearningPathRepository.findLesson(course, lessonId);
        if (node == null || lesson == null) return;
        NodeState state = stateFor(lesson,
                findCurrentLesson(LearningPathRepository.flatten(course)));
        node.bind(lesson, state, progress.get(lesson.id), downloads.get(lesson.id));
        node.invalidate();
    }

    private void openLesson(LearningPathRepository.Lesson lesson) {
        if (destroyed || course == null || progressStore == null) return;
        progressStore.markOpened(course.id, lesson.id);
        LearningLessonActivity.open(this, lesson);
    }

    private View emptyView(String message) {
        TextView empty = text(message, 14, COLOR_SUB, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(22), dp(38), dp(22), dp(38));
        empty.setBackground(round(Color.WHITE, dp(22), COLOR_BORDER, dp(2)));
        return empty;
    }

    private long currentVersionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return info.getLongVersionCode();
            return info.versionCode;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private void throttled(Runnable action) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastClickAt < 420L) return;
        lastClickAt = now;
        if (action != null) action.run();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }

    private TextView circleButton(String value, float size) {
        TextView view = text(value, size, COLOR_SUB, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(Color.WHITE, dp(22), COLOR_BORDER, dp(1)));
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

    private GradientDrawable round(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, float radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private enum NodeState { LOCKED, AVAILABLE, CURRENT, COMPLETED, DOWNLOADING }

    private static final class DownloadUiState {
        boolean active;
        int progress;
        String message = "";
    }

    private final class NodeView extends LinearLayout {
        private final TextView startBubble;
        private final NodeCircle circle;
        private final TextView label;
        private final TextView detail;
        private NodeState state = NodeState.LOCKED;

        NodeView(Context context, int accent) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            setClickable(true);
            setFocusable(true);
            setClipChildren(false);
            setClipToPadding(false);
            setPadding(dp(2), dp(1), dp(2), dp(1));

            startBubble = text(getString(R.string.learning_path_start), 11, COLOR_GREEN, true);
            startBubble.setGravity(Gravity.CENTER);
            startBubble.setPadding(dp(11), dp(5), dp(11), dp(5));
            startBubble.setBackground(round(Color.WHITE, dp(10), COLOR_BORDER, dp(2)));
            startBubble.setVisibility(INVISIBLE);
            addView(startBubble, new LinearLayout.LayoutParams(-2, dp(29)));

            circle = new NodeCircle(context, accent);
            LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(dp(84), dp(88));
            circleLp.setMargins(0, dp(2), 0, 0);
            addView(circle, circleLp);

            label = text("", 13, COLOR_TEXT, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setPadding(dp(8), dp(4), dp(8), dp(4));
            label.setBackground(round(Color.WHITE, dp(10), COLOR_BORDER, dp(2)));
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, dp(42));
            labelLp.setMargins(0, dp(4), 0, 0);
            addView(label, labelLp);

            detail = text("", 10, COLOR_SUB, true);
            detail.setGravity(Gravity.CENTER);
            detail.setMaxLines(1);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, dp(18));
            detailLp.setMargins(0, dp(3), 0, 0);
            addView(detail, detailLp);
        }

        void bind(LearningPathRepository.Lesson lesson, NodeState state,
                  LearningPathProgressStore.Progress item, DownloadUiState download) {
            this.state = state;
            label.setText(lesson.title);
            startBubble.setVisibility(state == NodeState.CURRENT ? VISIBLE : INVISIBLE);
            if (state == NodeState.DOWNLOADING) {
                int percent = download == null ? -1 : download.progress;
                detail.setText(percent >= 0 ? percent + "%" : getString(R.string.learning_path_downloading));
                circle.bind(lesson.icon, lesson.type, state, Math.max(0, percent));
            } else if (state == NodeState.COMPLETED) {
                int stars = item == null ? 0 : item.stars;
                detail.setText(stars <= 0 ? getString(R.string.learning_path_completed) : repeatStar(stars));
                circle.bind("✓", lesson.type, state, 100);
            } else if (state == NodeState.CURRENT) {
                detail.setText(getString(R.string.learning_path_current));
                circle.bind(lesson.icon, lesson.type, state, 0);
            } else if (state == NodeState.AVAILABLE) {
                detail.setText(getString(R.string.learning_path_available));
                circle.bind(lesson.icon, lesson.type, state, 0);
            } else {
                detail.setText(getString(R.string.learning_path_locked));
                circle.bind("◆", lesson.type, state, 0);
            }
            label.setTextColor(state == NodeState.LOCKED ? 0xFFAAAAAA : COLOR_TEXT);
            label.setBackground(round(state == NodeState.LOCKED ? 0xFFF2F2F2 : Color.WHITE,
                    dp(10), state == NodeState.LOCKED ? 0xFFE2E2E2 : COLOR_BORDER, dp(2)));
            setAlpha(state == NodeState.LOCKED ? 0.78f : 1f);
            setContentDescription(lesson.title + ", " + detail.getText());
        }

        NodeState nodeState() { return state; }

        @Override
        public boolean performClick() {
            super.performClick();
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            circle.animate().scaleX(0.90f).scaleY(0.90f).setDuration(65)
                    .withEndAction(() -> circle.animate().scaleX(1f).scaleY(1f)
                            .setDuration(120).start()).start();
            return true;
        }
    }

    private final class NodeCircle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private final int accent;
        private String icon = "✓";
        private String type = "normal";
        private NodeState state = NodeState.LOCKED;
        private int progressValue;

        NodeCircle(Context context, int accent) {
            super(context);
            this.accent = accent;
        }

        void bind(String icon, String type, NodeState state, int progress) {
            this.icon = icon == null || icon.isEmpty() ? "✓" : icon;
            this.type = type == null ? "normal" : type;
            this.state = state;
            this.progressValue = Math.max(0, Math.min(100, progress));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - dp(1);
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(7);
            int fill;
            int bottom;
            int iconColor = Color.WHITE;

            if (state == NodeState.LOCKED) {
                fill = COLOR_LOCKED;
                bottom = COLOR_LOCKED_DARK;
                iconColor = 0xFFAAAAAA;
            } else if (state == NodeState.COMPLETED) {
                fill = COLOR_YELLOW;
                bottom = COLOR_YELLOW_DARK;
            } else if (state == NodeState.CURRENT) {
                fill = COLOR_GREEN;
                bottom = COLOR_GREEN_DARK;
            } else if (state == NodeState.DOWNLOADING) {
                fill = 0xFF84D8FF;
                bottom = COLOR_BLUE_DARK;
            } else {
                fill = nodeAccent(type);
                bottom = darken(fill, 0.82f);
            }

            if (state == NodeState.CURRENT) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x3358CC02);
                canvas.drawCircle(cx, cy + dp(2), radius + dp(8), paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(bottom);
            canvas.drawCircle(cx, cy + dp(6), radius, paint);
            paint.setColor(fill);
            canvas.drawCircle(cx, cy, radius, paint);

            paint.setColor(0x36FFFFFF);
            canvas.drawOval(cx - radius * 0.60f, cy - radius * 0.66f,
                    cx + radius * 0.60f, cy - radius * 0.28f, paint);

            if (state == NodeState.DOWNLOADING) {
                arc.set(cx - radius + dp(5), cy - radius + dp(5),
                        cx + radius - dp(5), cy + radius - dp(5));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(5));
                paint.setColor(Color.WHITE);
                canvas.drawArc(arc, -90f, progressValue * 3.6f, false, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStyle(Paint.Style.FILL);
            }

            textPaint.setColor(iconColor);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(icon.length() > 2 ? 16 : 25));
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(icon, cx, baseline, textPaint);
        }

        private int nodeAccent(String type) {
            if ("test".equals(type) || "checkpoint".equals(type)) return 0xFFCE82FF;
            if ("review".equals(type)) return COLOR_YELLOW;
            if ("speaking".equals(type)) return 0xFFFF86D0;
            if ("listening".equals(type)) return COLOR_BLUE;
            return accent == 0 ? COLOR_BLUE : blend(accent, COLOR_BLUE, 0.34f);
        }
    }

    /** Staggered map container. Deliberately draws no connector line. */
    private final class PathLayout extends ViewGroup {
        private final Map<View, String> positions = new HashMap<>();
        private final int rowHeight = dp(166);
        private final int nodeWidth = dp(142);
        private final int nodeHeight = dp(160);

        PathLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
            setPadding(0, dp(4), 0, dp(4));
        }

        void addNode(NodeView node, String position) {
            positions.put(node, position);
            addView(node, new LayoutParams(nodeWidth, nodeHeight));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(nodeWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(nodeHeight, MeasureSpec.EXACTLY));
            }
            int height = getPaddingTop() + getPaddingBottom()
                    + Math.max(1, getChildCount()) * rowHeight;
            setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                    resolveSize(height, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int edge = dp(1);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                String position = positions.get(child);
                int x;
                if ("left".equals(position)) x = edge;
                else if ("right".equals(position)) x = width - nodeWidth - edge;
                else x = (width - nodeWidth) / 2;
                int y = getPaddingTop() + i * rowHeight;
                child.layout(x, y, x + nodeWidth, y + nodeHeight);
            }
        }
    }

    private static int blend(int first, int second, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = (int) (Color.red(first) * (1f - t) + Color.red(second) * t);
        int g = (int) (Color.green(first) * (1f - t) + Color.green(second) * t);
        int b = (int) (Color.blue(first) * (1f - t) + Color.blue(second) * t);
        return Color.rgb(r, g, b);
    }

    private static int darken(int color, float factor) {
        int r = Math.max(0, Math.min(255, (int) (Color.red(color) * factor)));
        int g = Math.max(0, Math.min(255, (int) (Color.green(color) * factor)));
        int b = Math.max(0, Math.min(255, (int) (Color.blue(color) * factor)));
        return Color.rgb(r, g, b);
    }

    private static String repeatStar(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(3, count); i++) builder.append('★');
        return builder.toString();
    }
}
