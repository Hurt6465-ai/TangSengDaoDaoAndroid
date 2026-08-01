package com.chat.learning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
    private static final int COLOR_BG = LearningUiKit.BG;
    private static final int COLOR_TEXT = LearningUiKit.TEXT;
    private static final int COLOR_SUB = LearningUiKit.SUBTEXT;
    private static final int COLOR_BORDER = LearningUiKit.BORDER;
    private static final int COLOR_GREEN = LearningUiKit.GREEN;
    private static final int COLOR_GREEN_DARK = LearningUiKit.GREEN_DARK;
    private static final int COLOR_BLUE = LearningUiKit.BLUE;
    private static final int COLOR_BLUE_DARK = LearningUiKit.BLUE_DARK;
    private static final int COLOR_YELLOW = LearningUiKit.YELLOW;
    private static final int COLOR_YELLOW_DARK = LearningUiKit.YELLOW_DARK;
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

        page.addView(createTopBar(), new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout widthHost = new FrameLayout(this);
        widthHost.setPadding(dp(14), dp(6), dp(14), dp(70));
        scroll.addView(widthHost, new ScrollView.LayoutParams(-1, -2));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        int available = Math.max(dp(300), getResources().getDisplayMetrics().widthPixels - dp(28));
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                Math.min(dp(560), available), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        widthHost.addView(content, contentLp);
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(4), dp(10), 0);

        TextView back = circleButton("‹", 32);
        back.setContentDescription(getString(R.string.learning_path_back));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));

        pageTitle = text(getString(R.string.learning_path_title), 18, COLOR_TEXT, true);
        pageTitle.setGravity(Gravity.CENTER);
        pageTitle.setSingleLine(true);
        pageTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -1, 1f);
        titleLp.setMargins(dp(8), 0, dp(8), 0);
        bar.addView(pageTitle, titleLp);

        refreshButton = circleButton("↻", 22);
        refreshButton.setContentDescription(getString(R.string.learning_path_refresh));
        refreshButton.setOnClickListener(v -> loadCatalog(true));
        bar.addView(refreshButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
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
        headerLp.setMargins(0, dp(8), 0, dp(24));
        content.addView(categoryHeader(selectedUnit, completed, selectedUnit.lessons.size()), headerLp);

        if (selectedUnit.lessons.isEmpty()) {
            content.addView(emptyView(getString(R.string.learning_path_empty)),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        PathLayout path = new PathLayout(this);
        int index = 0;
        for (LearningPathRepository.Lesson lesson : selectedUnit.lessons) {
            NodeState state = stateFor(lesson, currentLesson);
            NodeView node = new NodeView(this, course.accent);
            node.bind(lesson, state, progress.get(lesson.id), downloads.get(lesson.id));
            node.setOnClickListener(v -> throttled(() -> onLessonClick(lesson, node.nodeState())));
            nodeViews.put(lesson.id, node);
            path.addNode(node, index++);
        }
        LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(-1, -2);
        pathLp.setMargins(0, 0, 0, dp(18));
        content.addView(path, pathLp);
    }

    private View categoryHeader(LearningPathRepository.Unit unit, int completed, int total) {
        FrameLayout card = new FrameLayout(this);
        int accent = course == null || course.accent == 0 ? COLOR_GREEN : course.accent;
        card.setBackground(round(accent, dp(20), 0, 0));
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setElevation(dp(2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        card.addView(body, new FrameLayout.LayoutParams(-1, -2));

        TextView overline = text(getString(R.string.learning_category_section), 11,
                0xDFFFFFFF, true);
        overline.setAllCaps(true);
        overline.setLetterSpacing(0.08f);
        body.addView(overline, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text(unit.title, 23, Color.WHITE, true);
        title.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(7), dp(72), 0);
        body.addView(title, titleLp);

        if (!unit.subtitle.isEmpty()) {
            TextView subtitle = text(unit.subtitle, 14, 0xEFFFFFFF, false);
            subtitle.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
            subtitleLp.setMargins(0, dp(5), dp(64), 0);
            body.addView(subtitle, subtitleLp);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(25));
        rowLp.setMargins(0, dp(16), 0, 0);
        body.addView(row, rowLp);

        LearningUiKit.ProgressView bar = new LearningUiKit.ProgressView(this);
        bar.setColors(0x42FFFFFF, Color.WHITE);
        bar.setProgress(completed, Math.max(1, total));
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(9), 1f));

        TextView count = text(completed + " / " + total, 12, Color.WHITE, true);
        count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(64), -1);
        countLp.setMargins(dp(10), 0, 0, 0);
        row.addView(count, countLp);

        TextView unitIcon = text((total > 0 && "test".equals(unit.lessons.get(total - 1).type))
                ? "♛" : "★", 25, Color.WHITE, true);
        unitIcon.setGravity(Gravity.CENTER);
        unitIcon.setBackground(round(0x24FFFFFF, dp(24), 0x42FFFFFF, dp(1)));
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.TOP | Gravity.END);
        iconLp.setMargins(0, dp(2), dp(1), 0);
        card.addView(unitIcon, iconLp);
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
        TextView view = text(value, size, COLOR_SUB, false);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(Color.TRANSPARENT, dp(23), 0, 0));
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return LearningUiKit.text(this, value, size, color, bold);
    }

    private GradientDrawable round(int color, float radius, int strokeColor, int strokeWidth) {
        return LearningUiKit.rounded(color, radius, strokeColor, strokeWidth);
    }

    private GradientDrawable gradient(int start, int end, float radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return LearningUiKit.dp(this, value);
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
        private final FrameLayout bubbleHost;
        private final TextView startBubble;
        private final NodeCircle circle;
        private final TextView label;
        private final TextView stars;
        private NodeState state = NodeState.LOCKED;

        NodeView(Context context, int accent) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            setClickable(true);
            setFocusable(true);
            setClipChildren(false);
            setClipToPadding(false);

            bubbleHost = new FrameLayout(context);
            startBubble = text(getString(R.string.learning_path_start), 11, COLOR_GREEN, true);
            startBubble.setGravity(Gravity.CENTER);
            startBubble.setPadding(dp(13), dp(6), dp(13), dp(6));
            startBubble.setBackground(round(Color.WHITE, dp(11), COLOR_BORDER, dp(2)));
            FrameLayout.LayoutParams bubbleLp = new FrameLayout.LayoutParams(-2, dp(27),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            bubbleHost.addView(startBubble, bubbleLp);
            LearningUiKit.TriangleView triangle = new LearningUiKit.TriangleView(context);
            FrameLayout.LayoutParams triangleLp = new FrameLayout.LayoutParams(dp(12), dp(7),
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            triangleLp.setMargins(0, 0, 0, 0);
            bubbleHost.addView(triangle, triangleLp);
            bubbleHost.setVisibility(GONE);
            addView(bubbleHost, new LinearLayout.LayoutParams(-1, dp(31)));

            circle = new NodeCircle(context, accent);
            addView(circle, new LinearLayout.LayoutParams(dp(102), dp(108)));

            label = text("", 14, COLOR_TEXT, true);
            label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setLineSpacing(dp(1), 1f);
            label.setPadding(dp(4), dp(7), dp(4), 0);
            addView(label, new LinearLayout.LayoutParams(-1, dp(35)));

            stars = text("", 12, COLOR_YELLOW_DARK, true);
            stars.setGravity(Gravity.CENTER);
            addView(stars, new LinearLayout.LayoutParams(-1, dp(14)));
        }

        void bind(LearningPathRepository.Lesson lesson, NodeState state,
                  LearningPathProgressStore.Progress item, DownloadUiState download) {
            this.state = state;
            label.setText(lesson.title);
            bubbleHost.setVisibility(state == NodeState.CURRENT ? VISIBLE : GONE);
            int value = download == null ? 0 : Math.max(0, download.progress);
            String displayIcon = lesson.icon == null || lesson.icon.trim().isEmpty()
                    ? "★" : lesson.icon.trim();
            if (state == NodeState.COMPLETED) displayIcon = "✓";
            if (state == NodeState.LOCKED) displayIcon = "";
            int nodeProgress = state == NodeState.DOWNLOADING
                    ? value : item == null ? 0 : Math.max(item.bestScore, item.lastScore);
            circle.bind(displayIcon, lesson.type, state, nodeProgress);

            int starCount = item == null ? 0 : Math.max(0, Math.min(3, item.stars));
            stars.setText(state == NodeState.COMPLETED && starCount > 0
                    ? repeatStar(starCount) : "");
            label.setTextColor(state == NodeState.LOCKED ? 0xFFB4B4B4 : COLOR_TEXT);
            setAlpha(state == NodeState.LOCKED ? 0.82f : 1f);

            String stateText;
            if (state == NodeState.COMPLETED) stateText = getString(R.string.learning_path_completed);
            else if (state == NodeState.CURRENT) stateText = getString(R.string.learning_path_current);
            else if (state == NodeState.DOWNLOADING) stateText = value + "%";
            else if (state == NodeState.AVAILABLE) stateText = getString(R.string.learning_path_available);
            else stateText = getString(R.string.learning_path_locked);
            setContentDescription(lesson.title + ", " + stateText);
        }

        NodeState nodeState() { return state; }

        @Override
        public boolean performClick() {
            super.performClick();
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            circle.animate().translationY(dp(4)).scaleX(0.94f).scaleY(0.94f).setDuration(70)
                    .withEndAction(() -> circle.animate().translationY(0).scaleX(1f).scaleY(1f)
                            .setDuration(115).start()).start();
            return true;
        }
    }

    private final class NodeCircle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private final Path symbol = new Path();
        private final int accent;
        private String icon = "★";
        private String type = "normal";
        private NodeState state = NodeState.LOCKED;
        private int progressValue;

        NodeCircle(Context context, int accent) {
            super(context);
            this.accent = accent;
        }

        void bind(String icon, String type, NodeState state, int progress) {
            this.icon = icon == null ? "" : icon;
            this.type = type == null ? "normal" : type;
            this.state = state;
            this.progressValue = Math.max(0, Math.min(100, progress));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - dp(2);
            float radius = state == NodeState.CURRENT ? dp(35)
                    : Math.min(getWidth(), getHeight()) / 2f - dp(15);
            int fill;
            int bottom;
            int symbolColor = Color.WHITE;

            if (state == NodeState.LOCKED) {
                fill = COLOR_LOCKED;
                bottom = COLOR_LOCKED_DARK;
                symbolColor = 0xFFAAAAAA;
            } else if (state == NodeState.CURRENT) {
                fill = COLOR_GREEN;
                bottom = COLOR_GREEN_DARK;
            } else if (state == NodeState.COMPLETED) {
                fill = nodeAccent(type);
                bottom = darken(fill, 0.79f);
            } else if (state == NodeState.DOWNLOADING) {
                fill = COLOR_BLUE;
                bottom = COLOR_BLUE_DARK;
            } else {
                fill = nodeAccent(type);
                bottom = darken(fill, 0.79f);
            }

            if (state == NodeState.CURRENT) {
                float ringRadius = dp(46);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(8));
                paint.setColor(COLOR_BORDER);
                canvas.drawCircle(cx, cy + dp(1), ringRadius, paint);
                if (progressValue > 0) {
                    arc.set(cx - ringRadius, cy + dp(1) - ringRadius,
                            cx + ringRadius, cy + dp(1) + ringRadius);
                    paint.setColor(COLOR_GREEN);
                    canvas.drawArc(arc, -90f, Math.max(8f, progressValue * 3.6f),
                            false, paint);
                }
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStyle(Paint.Style.FILL);
            }

            paint.setColor(bottom);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy + dp(7), radius, paint);
            paint.setColor(fill);
            canvas.drawCircle(cx, cy, radius, paint);

            paint.setColor(0x28FFFFFF);
            canvas.drawOval(cx - radius * 0.53f, cy - radius * 0.65f,
                    cx + radius * 0.53f, cy - radius * 0.36f, paint);

            if (state == NodeState.DOWNLOADING) {
                arc.set(cx - radius + dp(7), cy - radius + dp(7),
                        cx + radius - dp(7), cy + radius - dp(7));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(5));
                paint.setColor(Color.WHITE);
                canvas.drawArc(arc, -90f, progressValue * 3.6f, false, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStyle(Paint.Style.FILL);
                drawArrow(canvas, cx, cy, Color.WHITE);
                return;
            }
            if (state == NodeState.LOCKED) {
                drawLock(canvas, cx, cy, symbolColor);
                return;
            }
            if (state == NodeState.COMPLETED) {
                drawCheck(canvas, cx, cy, symbolColor);
                return;
            }
            if ("test".equals(type) || "checkpoint".equals(type)) {
                drawCrown(canvas, cx, cy, symbolColor);
                return;
            }
            if ("review".equals(type)) {
                drawStar(canvas, cx, cy, symbolColor);
                return;
            }
            textPaint.setColor(symbolColor);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(icon.length() > 2 ? 16 : 25));
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(icon, cx, baseline, textPaint);
        }

        private void drawCheck(Canvas canvas, float cx, float cy, int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(6));
            paint.setColor(color);
            symbol.reset();
            symbol.moveTo(cx - dp(14), cy);
            symbol.lineTo(cx - dp(4), cy + dp(10));
            symbol.lineTo(cx + dp(16), cy - dp(12));
            canvas.drawPath(symbol, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawLock(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            arc.set(cx - dp(11), cy - dp(19), cx + dp(11), cy + dp(4));
            canvas.drawArc(arc, 190f, 160f, false, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(cx - dp(17), cy - dp(2), cx + dp(17), cy + dp(20),
                    dp(5), dp(5), paint);
            paint.setColor(0x66FFFFFF);
            canvas.drawCircle(cx, cy + dp(8), dp(3), paint);
        }

        private void drawArrow(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(5));
            canvas.drawLine(cx, cy - dp(12), cx, cy + dp(10), paint);
            symbol.reset();
            symbol.moveTo(cx - dp(9), cy + dp(2));
            symbol.lineTo(cx, cy + dp(11));
            symbol.lineTo(cx + dp(9), cy + dp(2));
            canvas.drawPath(symbol, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawCrown(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            symbol.reset();
            symbol.moveTo(cx - dp(18), cy - dp(10));
            symbol.lineTo(cx - dp(10), cy + dp(3));
            symbol.lineTo(cx, cy - dp(12));
            symbol.lineTo(cx + dp(10), cy + dp(3));
            symbol.lineTo(cx + dp(18), cy - dp(10));
            symbol.lineTo(cx + dp(14), cy + dp(15));
            symbol.lineTo(cx - dp(14), cy + dp(15));
            symbol.close();
            canvas.drawPath(symbol, paint);
            paint.setColor(0x45FFFFFF);
            canvas.drawRoundRect(cx - dp(12), cy + dp(7), cx + dp(12), cy + dp(11),
                    dp(2), dp(2), paint);
        }

        private void drawStar(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            symbol.reset();
            for (int i = 0; i < 10; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                float radius = i % 2 == 0 ? dp(19) : dp(9);
                float x = cx + (float) Math.cos(angle) * radius;
                float y = cy + (float) Math.sin(angle) * radius;
                if (i == 0) symbol.moveTo(x, y); else symbol.lineTo(x, y);
            }
            symbol.close();
            canvas.drawPath(symbol, paint);
        }

        private int nodeAccent(String type) {
            if ("test".equals(type) || "checkpoint".equals(type)) return LearningUiKit.PURPLE;
            if ("review".equals(type)) return COLOR_YELLOW;
            if ("speaking".equals(type)) return 0xFFFF86D0;
            if ("listening".equals(type)) return COLOR_BLUE;
            return accent == 0 ? COLOR_BLUE : blend(accent, COLOR_BLUE, 0.18f);
        }
    }

    /** Staggered map container. Deliberately draws no connector line. */
    private final class PathLayout extends ViewGroup {
        private final Map<View, Integer> indexes = new HashMap<>();
        private final int rowHeight = dp(174);
        private final int nodeWidth = dp(154);
        private final int nodeHeight = dp(192);
        private final int[] offsets = new int[]{0, 40, 80, 40, 0, -40, -80, -40};

        PathLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
            setPadding(0, dp(2), 0, dp(12));
        }

        void addNode(NodeView node, int index) {
            indexes.put(node, index);
            addView(node, new LayoutParams(nodeWidth, nodeHeight));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(nodeWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(nodeHeight, MeasureSpec.EXACTLY));
            }
            int height = getPaddingTop() + getPaddingBottom()
                    + Math.max(1, getChildCount()) * rowHeight + dp(10);
            setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                    resolveSize(height, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int center = width / 2;
            int maxOffset = Math.max(0, center - nodeWidth / 2 - dp(8));
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                Integer stored = indexes.get(child);
                int index = stored == null ? i : stored;
                int rawOffset = dp(offsets[Math.floorMod(index, offsets.length)]);
                int xOffset = Math.max(-maxOffset, Math.min(maxOffset, rawOffset));
                int childLeft = center - nodeWidth / 2 + xOffset;
                int childTop = getPaddingTop() + index * rowHeight;
                child.layout(childLeft, childTop, childLeft + nodeWidth, childTop + nodeHeight);
            }
        }
    }

    private static int blend(int first, int second, float amount) {
        return LearningUiKit.blend(first, second, amount);
    }

    private static int darken(int color, float factor) {
        return LearningUiKit.darken(color, factor);
    }

    private static String repeatStar(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(3, count); i++) builder.append('★');
        return builder.toString();
    }
}
