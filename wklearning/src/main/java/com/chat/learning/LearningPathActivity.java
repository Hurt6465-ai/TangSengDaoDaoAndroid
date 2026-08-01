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
import android.os.Bundle;
import android.view.Gravity;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Duolingo-style vertical learning path with remote catalog and package updates. */
public class LearningPathActivity extends AppCompatActivity {
    private static final String EXTRA_COURSE_ID = "course_id";
    private static final String PREFS = "wk_learning_path";
    private static final String PREF_COURSE_ID = "course_id";
    private static final int COLOR_BG = 0xFFF4F7FC;
    private static final int COLOR_TEXT = 0xFF182033;
    private static final int COLOR_SUB = 0xFF758096;
    private static final int COLOR_BORDER = 0xFFE2E8F2;
    private static final int COLOR_LOCKED = 0xFFD5DBE6;

    private LinearLayout content;
    private TextView courseTitle;
    private TextView courseSubtitle;
    private TextView progressText;
    private ProgressBar progressBar;
    private TextView refreshButton;

    private LearningPathRepository.Catalog catalog;
    private LearningPathRepository.Course course;
    private Map<String, LearningPathProgressStore.Progress> progress = new HashMap<>();
    private final Map<String, NodeView> nodeViews = new HashMap<>();
    private final Map<String, DownloadUiState> downloads = new HashMap<>();
    private final Map<String, LearningPackageDownloader.Subscription> downloadSubscriptions = new HashMap<>();
    private String pendingOpenLessonId = "";
    private String selectedCourseId = "";
    private LearningPathProgressStore progressStore;
    private long lastClickAt;
    private int catalogGeneration;
    private boolean refreshInFlight;
    private boolean destroyed;
    private boolean resumed;

    public static void open(Context context) {
        open(context, "");
    }

    public static void open(Context context, String courseId) {
        if (context == null) return;
        Intent intent = new Intent(context, LearningPathActivity.class);
        if (courseId != null && !courseId.trim().isEmpty()) {
            intent.putExtra(EXTRA_COURSE_ID, courseId.trim());
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
        progressStore = new LearningPathProgressStore(this);
        selectedCourseId = getIntent().getStringExtra(EXTRA_COURSE_ID);
        if (selectedCourseId == null || selectedCourseId.trim().isEmpty()) {
            selectedCourseId = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(PREF_COURSE_ID, "");
        }
        if (selectedCourseId == null) selectedCourseId = "";
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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(createTopBar(), new LinearLayout.LayoutParams(-1, dp(62)));
        page.addView(createCourseHeader(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(80));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(14), 0);

        TextView back = circleButton("‹");
        back.setTextSize(29);
        back.setContentDescription(getString(R.string.learning_path_back));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text(getString(R.string.learning_path_title), 18, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

        refreshButton = circleButton("↻");
        refreshButton.setTextSize(21);
        refreshButton.setContentDescription(getString(R.string.learning_path_refresh));
        refreshButton.setOnClickListener(v -> loadCatalog(true));
        bar.addView(refreshButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private View createCourseHeader() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(14), dp(10), dp(14), dp(4));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(gradient(0xFFFFFFFF, 0xFFF5F3FF, dp(24), COLOR_BORDER, dp(1)));
        outer.addView(card, new LinearLayout.LayoutParams(-1, -2));

        courseTitle = text(getString(R.string.learning_path_loading), 23, COLOR_TEXT, true);
        courseTitle.setOnClickListener(v -> showCourseChooser());
        card.addView(courseTitle, new LinearLayout.LayoutParams(-1, -2));

        courseSubtitle = text("", 13, COLOR_SUB, false);
        courseSubtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.setMargins(0, dp(5), 0, dp(12));
        card.addView(courseSubtitle, subtitleLp);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(progressRow, new LinearLayout.LayoutParams(-1, -2));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF635BFF));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE7E9F3));
        progressRow.addView(progressBar, new LinearLayout.LayoutParams(0, dp(10), 1f));

        progressText = text("0 / 0", 12, COLOR_SUB, true);
        progressText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams progressTextLp = new LinearLayout.LayoutParams(dp(86), dp(28));
        progressTextLp.setMargins(dp(10), 0, 0, 0);
        progressRow.addView(progressText, progressTextLp);
        return outer;
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
        return !destroyed && generation == catalogGeneration && !isFinishing();
    }

    private void finishRefresh() {
        refreshInFlight = false;
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
            refreshButton.setAlpha(1f);
        }
    }

    private void applyCatalog(LearningPathRepository.Catalog updated) {
        String previousCourseId = course == null ? "" : course.id;
        cancelDownloadObservers();
        downloads.clear();
        catalog = updated;
        course = LearningPathRepository.findCourse(updated, selectedCourseId);
        if (course == null) course = LearningPathRepository.firstCourse(updated);
        selectedCourseId = course == null ? "" : course.id;
        if (!selectedCourseId.isEmpty()) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(PREF_COURSE_ID, selectedCourseId).apply();
        }
        if (!previousCourseId.equals(selectedCourseId)) pendingOpenLessonId = "";
        progress = course == null || progressStore == null
                ? new HashMap<>() : progressStore.loadCourse(course.id);
        observeActiveDownloads();
        renderPath();
    }

    private void showCourseChooser() {
        if (catalog == null || catalog.courses.size() <= 1) return;
        String[] titles = new String[catalog.courses.size()];
        int selected = 0;
        for (int i = 0; i < catalog.courses.size(); i++) {
            LearningPathRepository.Course item = catalog.courses.get(i);
            titles[i] = item.title;
            if (item.id.equals(selectedCourseId)) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.learning_path_choose_course)
                .setSingleChoiceItems(titles, selected, (dialog, which) -> {
                    if (which < 0 || which >= catalog.courses.size()) return;
                    selectedCourseId = catalog.courses.get(which).id;
                    dialog.dismiss();
                    applyCatalog(catalog);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        if (course != null) bindCourseTitle();
        if (course == null || course.units.isEmpty()) {
            if (course == null) {
                courseTitle.setText(R.string.learning_path_title);
                courseTitle.setClickable(false);
            }
            courseSubtitle.setText(R.string.learning_path_empty);
            progressText.setText("0 / 0");
            progressBar.setProgress(0);
            content.addView(emptyView(getString(R.string.learning_path_empty)),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        if (course.minAppVersion > currentVersionCode()) {
            courseTitle.setText(course.title);
            courseSubtitle.setText(R.string.learning_path_app_update_required);
            progressText.setText("—");
            progressBar.setProgress(0);
            content.addView(emptyView(getString(R.string.learning_path_app_update_required)),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        courseSubtitle.setText(course.subtitle);
        List<LearningPathRepository.Lesson> all = LearningPathRepository.flatten(course);
        int completed = 0;
        for (LearningPathRepository.Lesson lesson : all) {
            LearningPathProgressStore.Progress item = progress.get(lesson.id);
            if (item != null && item.completed()) completed++;
        }
        progressText.setText(getString(R.string.learning_path_progress, completed, all.size()));
        progressBar.setProgress(all.isEmpty() ? 0 : completed * 1000 / all.size());

        String currentLesson = findCurrentLesson(all);
        for (int unitIndex = 0; unitIndex < course.units.size(); unitIndex++) {
            LearningPathRepository.Unit unit = course.units.get(unitIndex);
            content.addView(unitHeader(unit, unitIndex), new LinearLayout.LayoutParams(-1, -2));

            PathLayout path = new PathLayout(this, course.accent);
            for (LearningPathRepository.Lesson lesson : unit.lessons) {
                NodeState state = stateFor(lesson, currentLesson);
                NodeView node = new NodeView(this, course.accent);
                node.bind(lesson, state, progress.get(lesson.id), downloads.get(lesson.id));
                node.setOnClickListener(v -> throttled(() ->
                        onLessonClick(lesson, node.nodeState())));
                nodeViews.put(lesson.id, node);
                path.addNode(node, lesson.position);
            }
            LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(-1, -2);
            pathLp.setMargins(0, dp(4), 0, dp(22));
            content.addView(path, pathLp);
        }
    }

    private void bindCourseTitle() {
        if (course == null || courseTitle == null) return;
        boolean multipleCourses = catalog != null && catalog.courses.size() > 1;
        courseTitle.setText(multipleCourses ? course.title + "  ▾" : course.title);
        courseTitle.setContentDescription(multipleCourses
                ? getString(R.string.learning_path_choose_course) : course.title);
        courseTitle.setClickable(multipleCourses);
        courseTitle.setAlpha(multipleCourses ? 1f : 0.98f);
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

    private View unitHeader(LearningPathRepository.Unit unit, int index) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(10), dp(4), dp(7));

        TextView number = text(String.valueOf(index + 1), 14, Color.WHITE, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(rounded(course.accent, dp(18), 0, 0));
        header.addView(number, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1f);
        textLp.setMargins(dp(11), 0, 0, 0);
        header.addView(textBox, textLp);

        TextView title = text(unit.title, 18, COLOR_TEXT, true);
        textBox.addView(title, new LinearLayout.LayoutParams(-1, -2));
        if (unit.subtitle != null && unit.subtitle.length() > 0) {
            TextView subtitle = text(unit.subtitle, 12, COLOR_SUB, false);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.setMargins(0, dp(3), 0, 0);
            textBox.addView(subtitle, subLp);
        }
        return header;
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
            LearningPackageDownloader.Subscription stored =
                    downloadSubscriptions.remove(lesson.id);
            if (stored != null) stored.cancel();
        }
    }

    private void handleDownloadState(LearningPathRepository.Lesson lesson,
                                     LearningPackageDownloader.State state, int value, String message) {
        if (destroyed || isFinishing() || course == null || lesson == null
                || !course.id.equals(lesson.courseId)) return;
        LearningPathRepository.Lesson currentLesson =
                LearningPathRepository.findLesson(course, lesson.id);
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
            LearningPackageDownloader.Subscription subscription =
                    downloadSubscriptions.remove(lesson.id);
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
        LearningPathRepository.Lesson lesson =
                LearningPathRepository.findLesson(course, pendingOpenLessonId);
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
        NodeState state = stateFor(lesson, findCurrentLesson(LearningPathRepository.flatten(course)));
        node.bind(lesson, state, progress.get(lesson.id), downloads.get(lesson.id));
        ViewParentInvalidator.invalidateParents(node);
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
        empty.setBackground(rounded(Color.WHITE, dp(22), COLOR_BORDER, dp(1)));
        return empty;
    }

    private long currentVersionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
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
        if (bytes < 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f KB",
                bytes / 1024f);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }

    private TextView circleButton(String value) {
        TextView view = text(value, 19, COLOR_TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(Color.WHITE, dp(22), COLOR_BORDER, dp(1)));
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

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, float radius, int stroke, int width) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(radius);
        if (width > 0) drawable.setStroke(width, stroke);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private enum NodeState {
        LOCKED,
        AVAILABLE,
        CURRENT,
        COMPLETED,
        DOWNLOADING
    }

    private static final class DownloadUiState {
        boolean active;
        int progress;
        String message = "";
    }

    private final class NodeView extends LinearLayout {
        private final NodeCircle circle;
        private final TextView label;
        private final TextView detail;
        private LearningPathRepository.Lesson lesson;
        private NodeState state = NodeState.LOCKED;

        NodeView(Context context, int accent) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            setClickable(true);
            setFocusable(true);
            setPadding(dp(3), dp(2), dp(3), dp(2));

            circle = new NodeCircle(context, accent);
            addView(circle, new LinearLayout.LayoutParams(dp(74), dp(74)));

            label = text("", 13, COLOR_TEXT, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, dp(22));
            labelLp.setMargins(0, dp(5), 0, 0);
            addView(label, labelLp);

            detail = text("", 10, COLOR_SUB, false);
            detail.setGravity(Gravity.CENTER);
            detail.setMaxLines(1);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(detail, new LinearLayout.LayoutParams(-1, dp(18)));
        }

        void bind(LearningPathRepository.Lesson lesson, NodeState state,
                  LearningPathProgressStore.Progress progress, DownloadUiState download) {
            this.lesson = lesson;
            this.state = state;
            label.setText(lesson.title);
            if (state == NodeState.DOWNLOADING) {
                int percent = download == null ? -1 : download.progress;
                detail.setText(percent >= 0 ? percent + "%" : getString(R.string.learning_path_downloading));
                circle.bind(lesson.icon, state, Math.max(0, percent));
            } else if (state == NodeState.COMPLETED) {
                int stars = progress == null ? 0 : progress.stars;
                detail.setText(stars <= 0 ? getString(R.string.learning_path_completed)
                        : repeatStar(stars));
                circle.bind(lesson.icon, state, 100);
            } else if (state == NodeState.CURRENT) {
                detail.setText(getString(R.string.learning_path_start));
                circle.bind(lesson.icon, state, 0);
            } else if (state == NodeState.AVAILABLE) {
                detail.setText(getString(R.string.learning_path_available));
                circle.bind(lesson.icon, state, 0);
            } else {
                detail.setText(getString(R.string.learning_path_locked));
                circle.bind("◆", state, 0);
            }
            setAlpha(state == NodeState.LOCKED ? 0.72f : 1f);
            setContentDescription(lesson.title + ", " + detail.getText());
        }

        float anchorX() { return getLeft() + getWidth() / 2f; }
        float anchorY() { return getTop() + dp(39); }
        NodeState nodeState() { return state; }
    }

    private final class NodeCircle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int accent;
        private String icon = "✓";
        private NodeState state = NodeState.LOCKED;
        private int progress;

        NodeCircle(Context context, int accent) {
            super(context);
            this.accent = accent;
            setElevation(dp(3));
        }

        void bind(String icon, NodeState state, int progress) {
            this.icon = icon == null || icon.length() == 0 ? "✓" : icon;
            this.state = state;
            this.progress = Math.max(0, Math.min(100, progress));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(6);
            int fill;
            int stroke;
            int iconColor;
            if (state == NodeState.LOCKED) {
                fill = 0xFFF0F2F6;
                stroke = COLOR_LOCKED;
                iconColor = 0xFF9BA5B7;
            } else if (state == NodeState.COMPLETED) {
                fill = accent;
                stroke = darken(accent, 0.82f);
                iconColor = Color.WHITE;
            } else if (state == NodeState.CURRENT) {
                fill = Color.WHITE;
                stroke = accent;
                iconColor = accent;
            } else if (state == NodeState.DOWNLOADING) {
                fill = 0xFFF0EFFF;
                stroke = accent;
                iconColor = accent;
            } else {
                fill = 0xFFF9F9FF;
                stroke = withAlpha(accent, 150);
                iconColor = accent;
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(state == NodeState.CURRENT ? dp(4) : dp(3));
            paint.setColor(stroke);
            canvas.drawCircle(cx, cy, radius - dp(1.5f), paint);

            if (state == NodeState.DOWNLOADING) {
                rect.set(cx - radius + dp(4), cy - radius + dp(4),
                        cx + radius - dp(4), cy + radius - dp(4));
                paint.setColor(accent);
                paint.setStrokeWidth(dp(5));
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawArc(rect, -90, progress * 3.6f, false, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
            }

            textPaint.setColor(iconColor);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(icon.length() > 2 ? dp(16) : dp(24));
            textPaint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(icon, cx, baseline, textPaint);
        }
    }

    private final class PathLayout extends ViewGroup {
        private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path curve = new Path();
        private final int accent;
        private final Map<View, String> positions = new HashMap<>();
        private final int rowHeight = dp(132);
        private final int nodeWidth = dp(130);
        private final int nodeHeight = dp(122);

        PathLayout(Context context, int accent) {
            super(context);
            this.accent = accent;
            setWillNotDraw(false);
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
            int edge = dp(5);
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

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getChildCount() < 2) return;
            pathPaint.setStyle(Paint.Style.STROKE);
            pathPaint.setStrokeCap(Paint.Cap.ROUND);
            pathPaint.setStrokeWidth(dp(7));
            for (int i = 0; i < getChildCount() - 1; i++) {
                NodeView start = (NodeView) getChildAt(i);
                NodeView end = (NodeView) getChildAt(i + 1);
                float x1 = start.anchorX();
                float y1 = start.anchorY();
                float x2 = end.anchorX();
                float y2 = end.anchorY();
                float midY = (y1 + y2) / 2f;
                curve.reset();
                curve.moveTo(x1, y1);
                curve.cubicTo(x1, midY, x2, midY, x2, y2);
                boolean completedSegment = start.nodeState() == NodeState.COMPLETED;
                pathPaint.setColor(completedSegment ? withAlpha(accent, 175) : 0xFFDCE2EC);
                canvas.drawPath(curve, pathPaint);
            }
        }
    }

    private static final class ViewParentInvalidator {
        static void invalidateParents(View view) {
            View current = view;
            for (int i = 0; i < 3 && current != null; i++) {
                current.invalidate();
                android.view.ViewParent parent = current.getParent();
                current = parent instanceof View ? (View) parent : null;
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
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
