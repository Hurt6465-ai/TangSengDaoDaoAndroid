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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Continuous Course -> Unit -> Lesson map backed by RecyclerView virtualization. */
public class LearningPathActivity extends AppCompatActivity {
    private static final String EXTRA_COURSE_ID = "course_id";
    private static final String EXTRA_UNIT_ID = "unit_id";

    // 1. 配色（对齐多邻国官方色值）
    private static final int COLOR_BG        = 0xFFFFFFFF;
    private static final int COLOR_TEXT      = 0xFF4B4B4B;
    private static final int COLOR_SUBTEXT   = 0xFF777777;
    private static final int COLOR_BORDER    = 0xFFE5E5E5;
    private static final int COLOR_GREEN     = 0xFF58CC02;
    private static final int COLOR_BLUE      = 0xFF1CB0F6;
    private static final int COLOR_BLUE_DARK = 0xFF1899D6;
    private static final int COLOR_PURPLE    = 0xFFCE82FF;
    private static final int COLOR_GOLD      = 0xFFFFC800;
    private static final int COLOR_RED       = 0xFFFF4B4B;
    private static final int COLOR_LOCK_FILL = 0xFFE5E5E5;
    private static final int COLOR_LOCK_EDGE = 0xFFCFCFCF;
    private static final int COLOR_LOCK_ICON = 0xFFAFAFAF;

    private static final boolean SHOW_LESSON_TITLE = false; // 多邻国节点下没有文字
    private static final int ROW_H_DP = SHOW_LESSON_TITLE ? 118 : 94;

    private static int edgeOf(int color) {
        return LearningUiKit.blend(color, Color.BLACK, 0.17f);
    }

    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private CourseMapAdapter adapter;
    private UnitHeaderView stickyHeader;
    private TextView pageTitle;
    private TextView refreshButton;

    private LearningPathRepository.Catalog catalog;
    private LearningPathRepository.Course course;
    private Map<String, LearningPathProgressStore.Progress> progress = new HashMap<>();
    private final Map<String, DownloadUiState> downloads = new HashMap<>();
    private final Map<String, LearningPackageDownloader.Subscription> downloadSubscriptions = new HashMap<>();
    private String pendingOpenLessonId = "";
    private String selectedCourseId = "";
    private String anchorUnitId = "";
    private LearningPathProgressStore progressStore;
    private long lastClickAt;
    private int catalogGeneration;
    private boolean refreshInFlight;
    private boolean destroyed;
    private boolean resumed;
    private boolean initialScrollDone;

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
        anchorUnitId = safe(getIntent().getStringExtra(EXTRA_UNIT_ID));
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
            renderCourse(false);
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

        FrameLayout content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        recyclerView = new RecyclerView(this);
        recyclerView.setBackgroundColor(COLOR_BG);
        recyclerView.setClipToPadding(false);
        recyclerView.setClipChildren(false);
        recyclerView.setPadding(0, 0, 0, dp(80));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setItemAnimator(null);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new CourseMapAdapter();
        recyclerView.setAdapter(adapter);
        content.addView(recyclerView, new FrameLayout.LayoutParams(-1, -1));

        stickyHeader = new UnitHeaderView(this);
        stickyHeader.setVisibility(View.INVISIBLE);
        content.addView(stickyHeader, new FrameLayout.LayoutParams(-1, -2));

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateSticky();
            }
        });
        recyclerView.post(this::updateSticky);
    }

    private void updateSticky() {
        if (stickyHeader == null || adapter == null || layoutManager == null) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        MapItem item = first == RecyclerView.NO_POSITION ? null : adapter.itemAt(first);
        if (item == null || item.unit == null) {
            stickyHeader.setVisibility(View.INVISIBLE);
            return;
        }
        View firstView = layoutManager.findViewByPosition(first);
        if (item.type == MapItem.UNIT && firstView != null && firstView.getTop() >= 0) {
            stickyHeader.setVisibility(View.INVISIBLE); // 真实条还完整可见，不重复画
            return;
        }
        stickyHeader.setVisibility(View.VISIBLE);
        stickyHeader.bind(item.unit, item.unitIndex);

        float push = 0f;
        int h = stickyHeader.getHeight();
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int position = recyclerView.getChildAdapterPosition(child);
            MapItem next = position == RecyclerView.NO_POSITION ? null : adapter.itemAt(position);
            if (next == null || next.type != MapItem.UNIT || next.unit == item.unit) continue;
            int top = child.getTop();
            if (top > 0 && top < h) push = top - h;
            break;
        }
        stickyHeader.setTranslationY(push);
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

        pageTitle = text(getString(R.string.learning_path_title), 18, COLOR_TEXT, true);
        pageTitle.setGravity(Gravity.CENTER);
        pageTitle.setSingleLine(true);
        pageTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -1, 1f);
        titleLp.setMargins(dp(8), 0, dp(8), 0);
        bar.addView(pageTitle, titleLp);

        refreshButton = iconButton("↻", 22);
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
        if (catalog == null) showMessage(getString(R.string.learning_path_loading));
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
        progress = course == null || progressStore == null
                ? new HashMap<>() : progressStore.loadCourse(course.id);
        observeActiveDownloads();
        renderCourse(true);
    }

    private void showMessage(String message) {
        if (adapter != null) adapter.submitMessage(message);
    }

    private void renderCourse(boolean catalogChanged) {
        if (adapter == null) return;
        if (course == null) {
            pageTitle.setText(R.string.learning_path_title);
            adapter.submitMessage(getString(R.string.learning_path_empty));
            return;
        }
        pageTitle.setText(course.title);
        if (course.minAppVersion > currentVersionCode()) {
            adapter.submitMessage(getString(R.string.learning_path_app_update_required));
            return;
        }

        Parcelable oldState = !catalogChanged && layoutManager != null
                ? layoutManager.onSaveInstanceState() : null;
        String currentLesson = findCurrentLesson(LearningPathRepository.flatten(course));
        adapter.submitCourse(course, currentLesson);
        if (oldState != null) layoutManager.onRestoreInstanceState(oldState);

        if (!initialScrollDone) {
            initialScrollDone = true;
            recyclerView.post(() -> {
                int position = !anchorUnitId.isEmpty()
                        ? adapter.positionForUnit(anchorUnitId)
                        : adapter.positionForLesson(currentLesson);
                if (position < 0) position = 0;
                layoutManager.scrollToPositionWithOffset(position, dp(10));
            });
        }
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
        LearningPackageDownloader.Status status = lesson.isRewardNode()
                ? null : LearningPackageDownloader.status(lesson);
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
        if (lesson.isRewardNode()) {
            completeTrophy(lesson);
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

    private void completeTrophy(LearningPathRepository.Lesson lesson) {
        LearningPathProgressStore.Progress value = progress.get(lesson.id);
        if ((value == null || !value.completed()) && progressStore != null) {
            progressStore.markCompleted(course.id, lesson.id, 100, 3);
            progress = progressStore.loadCourse(course.id);
            renderCourse(false);
        }
        LearningPathRepository.Unit unit = findUnit(lesson.unitId);
        String title = unit == null ? lesson.title : unit.title;
        String nextLessonId = findCurrentLesson(LearningPathRepository.flatten(course));
        new AlertDialog.Builder(this)
                .setTitle(R.string.learning_unit_complete_title)
                .setMessage(getString(R.string.learning_unit_complete_message, title))
                .setPositiveButton(R.string.learning_lesson_continue, (dialog, which) -> {
                    int position = adapter == null ? -1 : adapter.positionForLesson(nextLessonId);
                    if (position >= 0 && layoutManager != null) {
                        recyclerView.post(() -> layoutManager.scrollToPositionWithOffset(position, dp(12)));
                    }
                })
                .show();
    }

    private LearningPathRepository.Unit findUnit(String unitId) {
        if (course == null || unitId == null) return null;
        for (LearningPathRepository.Unit unit : course.units) {
            if (unitId.equals(unit.id)) return unit;
        }
        return null;
    }

    private void showDownloadDialog(LearningPathRepository.Lesson lesson) {
        String size = lesson.packageSize > 0L ? formatBytes(lesson.packageSize)
                : getString(R.string.learning_path_size_unknown);
        new AlertDialog.Builder(this)
                .setTitle(lesson.title)
                .setMessage(getString(R.string.learning_path_download_message,
                        lesson.exerciseCount, lesson.minutes, size))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.learning_path_download_start,
                        (dialog, which) -> startDownload(lesson))
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
            if (lesson.isRewardNode()) continue;
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
                                     LearningPackageDownloader.State state, int value,
                                     String message) {
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
        if (adapter != null) adapter.notifyLessonChanged(lessonId);
    }

    private void openLesson(LearningPathRepository.Lesson lesson) {
        if (destroyed || course == null || progressStore == null) return;
        progressStore.markOpened(course.id, lesson.id);
        LearningLessonActivity.open(this, lesson);
    }

    private void cancelDownloadObservers() {
        for (LearningPackageDownloader.Subscription subscription : downloadSubscriptions.values()) {
            if (subscription != null) subscription.cancel();
        }
        downloadSubscriptions.clear();
    }

    private long currentVersionCode() {
        try {
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
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
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f);
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }

    private TextView iconButton(String value, float size) {
        TextView view = text(value, size, COLOR_SUBTEXT, false);
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private enum NodeState { LOCKED, AVAILABLE, CURRENT, COMPLETED, DOWNLOADING }

    private static final class DownloadUiState {
        boolean active;
        int progress;
        String message = "";
    }

    private static final class MapItem {
        static final int MESSAGE = 0;
        static final int COURSE = 1;
        static final int UNIT = 2;
        static final int LESSON = 3;

        int type;
        String message = "";
        LearningPathRepository.Course course;
        LearningPathRepository.Unit unit;
        LearningPathRepository.Lesson lesson;
        int unitIndex;
        int lessonIndex;
        boolean lastInUnit;
    }

    private final class CourseMapAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<MapItem> items = new ArrayList<>();
        private final Map<String, Integer> lessonPositions = new HashMap<>();
        private final Map<String, Integer> unitPositions = new HashMap<>();
        private String currentLessonId = "";

        CourseMapAdapter() {
            setHasStableIds(true);
        }

        MapItem itemAt(int position) {
            return position < 0 || position >= items.size() ? null : items.get(position);
        }

        void submitMessage(String message) {
            items.clear();
            lessonPositions.clear();
            unitPositions.clear();
            MapItem item = new MapItem();
            item.type = MapItem.MESSAGE;
            item.message = message == null ? "" : message;
            items.add(item);
            notifyDataSetChanged();
            if (recyclerView != null) recyclerView.post(LearningPathActivity.this::updateSticky);
        }

        void submitCourse(LearningPathRepository.Course value, String currentLesson) {
            items.clear();
            lessonPositions.clear();
            unitPositions.clear();
            currentLessonId = currentLesson == null ? "" : currentLesson;

            MapItem courseItem = new MapItem();
            courseItem.type = MapItem.COURSE;
            courseItem.course = value;
            items.add(courseItem);

            for (int unitIndex = 0; unitIndex < value.units.size(); unitIndex++) {
                LearningPathRepository.Unit unit = value.units.get(unitIndex);
                MapItem header = new MapItem();
                header.type = MapItem.UNIT;
                header.course = value;
                header.unit = unit;
                header.unitIndex = unitIndex;
                unitPositions.put(unit.id, items.size());
                items.add(header);

                for (int lessonIndex = 0; lessonIndex < unit.lessons.size(); lessonIndex++) {
                    LearningPathRepository.Lesson lesson = unit.lessons.get(lessonIndex);
                    MapItem node = new MapItem();
                    node.type = MapItem.LESSON;
                    node.course = value;
                    node.unit = unit;
                    node.lesson = lesson;
                    node.unitIndex = unitIndex;
                    node.lessonIndex = lessonIndex;
                    node.lastInUnit = lessonIndex == unit.lessons.size() - 1;
                    lessonPositions.put(lesson.id, items.size());
                    items.add(node);
                }
            }
            notifyDataSetChanged();
            if (recyclerView != null) recyclerView.post(LearningPathActivity.this::updateSticky);
        }

        int positionForLesson(String lessonId) {
            Integer position = lessonPositions.get(lessonId);
            return position == null ? -1 : position;
        }

        int positionForUnit(String unitId) {
            Integer position = unitPositions.get(unitId);
            return position == null ? -1 : position;
        }

        void notifyLessonChanged(String lessonId) {
            Integer position = lessonPositions.get(lessonId);
            if (position != null && position >= 0 && position < items.size()) {
                notifyItemChanged(position);
            }
        }

        @Override
        public long getItemId(int position) {
            MapItem item = items.get(position);
            if (item.type == MapItem.COURSE && item.course != null) {
                return stableId("course:" + item.course.id);
            }
            if (item.type == MapItem.UNIT && item.unit != null) {
                return stableId("unit:" + item.unit.id);
            }
            if (item.type == MapItem.LESSON && item.lesson != null) {
                return stableId("lesson:" + item.lesson.id);
            }
            return stableId("message:" + item.message);
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == MapItem.COURSE) return new CourseHolder(new CourseHeaderView(parent.getContext()));
            if (viewType == MapItem.UNIT) return new UnitHolder(new UnitHeaderView(parent.getContext()));
            if (viewType == MapItem.LESSON) return new LessonHolder(new LessonRowView(parent.getContext()));
            return new MessageHolder(new MessageView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MapItem item = items.get(position);
            if (holder instanceof CourseHolder) {
                ((CourseHolder) holder).view.bind(item.course);
            } else if (holder instanceof UnitHolder) {
                ((UnitHolder) holder).view.bind(item.unit, item.unitIndex);
            } else if (holder instanceof LessonHolder) {
                NodeState state = stateFor(item.lesson, currentLessonId);
                ((LessonHolder) holder).view.bind(item, state,
                        progress.get(item.lesson.id), downloads.get(item.lesson.id));
            } else if (holder instanceof MessageHolder) {
                ((MessageHolder) holder).view.bind(item.message);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static long stableId(String value) {
        long hash = 1125899906842597L;
        String text = value == null ? "" : value;
        for (int i = 0; i < text.length(); i++) hash = 31L * hash + text.charAt(i);
        return hash;
    }

    private static final class CourseHolder extends RecyclerView.ViewHolder {
        final CourseHeaderView view;
        CourseHolder(CourseHeaderView value) { super(value); view = value; }
    }

    private static final class UnitHolder extends RecyclerView.ViewHolder {
        final UnitHeaderView view;
        UnitHolder(UnitHeaderView value) { super(value); view = value; }
    }

    private static final class LessonHolder extends RecyclerView.ViewHolder {
        final LessonRowView view;
        LessonHolder(LessonRowView value) { super(value); view = value; }
    }

    private static final class MessageHolder extends RecyclerView.ViewHolder {
        final MessageView view;
        MessageHolder(MessageView value) { super(value); view = value; }
    }

    private final class MessageView extends FrameLayout {
        private final TextView label;

        MessageView(Context context) {
            super(context);
            setPadding(dp(16), dp(20), dp(16), dp(20));
            label = text("", 15, COLOR_SUBTEXT, false);
            label.setGravity(Gravity.CENTER);
            label.setPadding(dp(22), dp(48), dp(22), dp(48));
            label.setBackground(LearningUiKit.rounded(Color.WHITE, dp(22), COLOR_BORDER, dp(2)));
            LayoutParams lp = new LayoutParams(Math.min(dp(620),
                    getResources().getDisplayMetrics().widthPixels - dp(32)), -2,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addView(label, lp);
        }

        void bind(String value) { label.setText(value); }
    }

    // 精简后：白底蓝卡、只有主副标题和一只伴读熊
    private final class CourseHeaderView extends FrameLayout {
        private final TextView title;
        private final LearningUiKit.ProgressView progressView;
        private final TextView progressLabel;
        private final LearningUiKit.CharacterView character;

        CourseHeaderView(Context context) {
            super(context);
            setPadding(dp(16), dp(16), dp(16), dp(22));

            FrameLayout card = new FrameLayout(context);
            card.setClipToOutline(true);
            card.setElevation(dp(3));
            LayoutParams cardLp = new LayoutParams(Math.min(dp(680),
                    getResources().getDisplayMetrics().widthPixels - dp(32)), dp(150),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addView(card, cardLp);

            character = new LearningUiKit.CharacterView(context, COLOR_BLUE, 0, "book");
            LayoutParams charLp = new LayoutParams(dp(112), dp(148), Gravity.END | Gravity.BOTTOM);
            charLp.setMargins(0, 0, dp(16), dp(1));
            card.addView(character, charLp);

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            body.setPadding(dp(20), dp(16), dp(135), dp(16));
            card.addView(body, new LayoutParams(-1, -1));

            title = text("", 24, Color.WHITE, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(title, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(24));
            rowLp.setMargins(0, dp(16), 0, 0);
            body.addView(row, rowLp);

            progressView = new LearningUiKit.ProgressView(context);
            progressView.setColors(0x48FFFFFF, Color.WHITE);
            row.addView(progressView, new LinearLayout.LayoutParams(0, dp(8), 1f));

            progressLabel = text("", 12, Color.WHITE, true);
            progressLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(54), -1);
            countLp.setMargins(dp(9), 0, 0, 0);
            row.addView(progressLabel, countLp);
        }

        void bind(LearningPathRepository.Course value) {
            int total = 0;
            int completed = 0;
            for (LearningPathRepository.Unit unit : value.units) {
                for (LearningPathRepository.Lesson lesson : unit.lessons) {
                    total++;
                    LearningPathProgressStore.Progress item = progress.get(lesson.id);
                    if (item != null && item.completed()) completed++;
                }
            }
            title.setText(value.title);
            progressView.setProgress(completed, Math.max(1, total));
            progressLabel.setText(completed + "/" + total);
            
            int accent = value.accent == 0 ? COLOR_BLUE : value.accent;
            ((FrameLayout) getChildAt(0)).setBackground(LearningUiKit.rounded(accent, dp(16), 0, 0));
            character.setStyle(accent, 0, "book");
        }
    }

    // 经典吸顶窄条式 Unit Header
    private final class UnitHeaderView extends FrameLayout {
        private final FrameLayout card;
        private final TextView overline;
        private final TextView title;
        private final LearningUiKit.ProgressView progressView;
        private final GuidebookIcon guide;

        UnitHeaderView(Context context) {
            super(context);
            setPadding(dp(12), dp(10), dp(12), dp(10));
            card = new FrameLayout(context);
            card.setClipToOutline(true);
            card.setElevation(dp(2));
            addView(card, new LayoutParams(-1, dp(84)));

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            body.setPadding(dp(16), dp(12), dp(78), dp(12));
            card.addView(body, new LayoutParams(-1, -1));

            overline = text("", 12, 0xCCFFFFFF, true);
            overline.setAllCaps(true);
            overline.setLetterSpacing(0.06f);
            body.addView(overline, new LinearLayout.LayoutParams(-1, -2));

            title = text("", 19, Color.WHITE, true);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
            tLp.setMargins(0, dp(2), 0, 0);
            body.addView(title, tLp);

            progressView = new LearningUiKit.ProgressView(context);
            progressView.setColors(0x40FFFFFF, Color.WHITE);
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, dp(7));
            pLp.setMargins(0, dp(8), 0, 0);
            body.addView(progressView, pLp);

            View divider = new View(context);
            divider.setBackgroundColor(0x40FFFFFF);
            LayoutParams dLp = new LayoutParams(dp(2), dp(40), Gravity.END | Gravity.CENTER_VERTICAL);
            dLp.setMargins(0, 0, dp(62), 0);
            card.addView(divider, dLp);

            guide = new GuidebookIcon(context);
            LayoutParams gLp = new LayoutParams(dp(52), dp(52), Gravity.END | Gravity.CENTER_VERTICAL);
            gLp.setMargins(0, 0, dp(6), 0);
            card.addView(guide, gLp);
        }

        void bind(LearningPathRepository.Unit unit, int unitIndex) {
            int accent = unit.accent == 0 ? COLOR_BLUE : unit.accent;
            GradientDrawable bg = LearningUiKit.rounded(accent, dp(16), 0, 0);
            card.setBackground(bg);
            int completed = 0;
            for (LearningPathRepository.Lesson lesson : unit.lessons) {
                LearningPathProgressStore.Progress value = progress.get(lesson.id);
                if (value != null && value.completed()) completed++;
            }
            overline.setText(getString(R.string.learning_unit_number, unitIndex + 1));
            title.setText(unit.title);
            progressView.setProgress(completed, Math.max(1, unit.lessons.size()));
        }
    }

    private final class GuidebookIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        GuidebookIcon(Context context) { super(context); setClickable(true); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(cx - dp(12), cy - dp(9));
            path.quadTo(cx - dp(5), cy - dp(11), cx, cy - dp(5));
            path.quadTo(cx + dp(5), cy - dp(11), cx + dp(12), cy - dp(9));
            path.lineTo(cx + dp(12), cy + dp(10));
            path.quadTo(cx + dp(5), cy + dp(7), cx, cy + dp(12));
            path.quadTo(cx - dp(5), cy + dp(7), cx - dp(12), cy + dp(10));
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(0x33000000);
            canvas.drawRect(cx - dp(1), cy - dp(5), cx + dp(1), cy + dp(11), paint);
        }
    }

    // 12段平滑正弦波
    private static final int[] PATH_OFFSET = {0, -35, -60, -70, -60, -35, 0, 35, 60, 70, 60, 35};

    private final class LessonRowView extends FrameLayout {
        private final NodeGroup node;
        private final LearningUiKit.CharacterView character;
        private MapItem boundItem;
        private NodeState boundState = NodeState.LOCKED;

        LessonRowView(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
            setBackgroundColor(COLOR_BG);
            setLayoutParams(new RecyclerView.LayoutParams(-1, dp(ROW_H_DP)));

            character = new LearningUiKit.CharacterView(context);
            character.setVisibility(GONE);
            addView(character, new LayoutParams(dp(84), dp(112), Gravity.BOTTOM | Gravity.START));

            node = new NodeGroup(context);
            addView(node, new LayoutParams(dp(130), dp(ROW_H_DP), Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            setOnClickListener(v -> {
                if (boundItem == null || boundItem.lesson == null) return;
                throttled(() -> onLessonClick(boundItem.lesson, boundState));
            });
        }

        @Override 
        public boolean onTouchEvent(android.view.MotionEvent event) {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    node.setNodePressed(true); 
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    node.setNodePressed(false); 
                    break;
                default: 
                    break;
            }
            return super.onTouchEvent(event);
        }

        @Override 
        public boolean performClick() {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            return super.performClick();
        }

        void bind(MapItem item, NodeState state, LearningPathProgressStore.Progress value,
                  DownloadUiState download) {
            boundItem = item;
            boundState = state;

            ViewGroup.LayoutParams lp = getLayoutParams();
            int wanted = dp(ROW_H_DP) + (item.lastInUnit ? dp(18) : 0);
            if (lp != null && lp.height != wanted) { lp.height = wanted; setLayoutParams(lp); }

            int width = Math.min(dp(680), getResources().getDisplayMetrics().widthPixels);
            int max = Math.max(0, width / 2 - dp(66));
            int offset = dp(PATH_OFFSET[Math.floorMod(item.lessonIndex, PATH_OFFSET.length)]);
            offset = Math.max(-max, Math.min(max, offset));
            node.setTranslationX(offset);
            node.bind(item.lesson, state, value, download, item.unit.accent);

            boolean showCharacter = "story".equals(item.lesson.type)
                    || "trophy".equals(item.lesson.type)
                    || (item.lessonIndex == 4 && item.unit.lessons.size() >= 6); // 更稀疏的插画
            if (showCharacter) {
                character.setVisibility(VISIBLE);
                String pose = "trophy".equals(item.lesson.type) ? "trophy"
                        : "story".equals(item.lesson.type) ? "book" : "wave";
                character.setStyle(item.unit.accent,
                        characterVariant(item.unit.character, item.unitIndex * 2 + item.lessonIndex), pose);
                LayoutParams cLp = (LayoutParams) character.getLayoutParams();
                cLp.gravity = (offset >= 0 ? Gravity.START : Gravity.END) | Gravity.BOTTOM;
                cLp.setMargins(offset >= 0 ? dp(16) : 0, 0, offset >= 0 ? 0 : dp(16), 0);
                character.setLayoutParams(cLp);
            } else {
                character.setVisibility(GONE);
            }

            String stateText;
            if (state == NodeState.COMPLETED) stateText = getString(R.string.learning_path_completed);
            else if (state == NodeState.CURRENT) stateText = getString(R.string.learning_path_current);
            else if (state == NodeState.DOWNLOADING) stateText = (download == null ? 0 : download.progress) + "%";
            else if (state == NodeState.AVAILABLE) stateText = getString(R.string.learning_path_available);
            else stateText = getString(R.string.learning_path_locked);
            setContentDescription(item.lesson.title + ", " + stateText);
            setAlpha(1f);   // 锁定态靠灰色区分，不再降透明度
        }
    }

    private final class NodeGroup extends FrameLayout {
        private final NodeCircle circle;
        private final LinearLayout bubbleHost;
        private final TextView bubble;
        private final LearningUiKit.TriangleView tail;
        private final TextView title;
        private android.animation.ObjectAnimator bob;

        NodeGroup(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);

            circle = new NodeCircle(context);
            addView(circle, new LayoutParams(dp(104), dp(104), Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            bubbleHost = new LinearLayout(context);
            bubbleHost.setOrientation(LinearLayout.VERTICAL);
            bubbleHost.setGravity(Gravity.CENTER_HORIZONTAL);
            bubble = text("", 14, COLOR_GREEN, true);
            bubble.setGravity(Gravity.CENTER);
            bubble.setAllCaps(true);
            bubble.setLetterSpacing(0.04f);
            bubbleHost.addView(bubble, new LinearLayout.LayoutParams(-2, dp(42)));
            tail = new LearningUiKit.TriangleView(context);
            bubbleHost.addView(tail, new LinearLayout.LayoutParams(dp(16), dp(9)));
            LayoutParams bLp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addView(bubbleHost, bLp);
            bubbleHost.setTranslationY(-dp(44));

            title = text("", 14, COLOR_TEXT, true);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setVisibility(SHOW_LESSON_TITLE ? VISIBLE : GONE);
            LayoutParams tLp = new LayoutParams(dp(130), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            tLp.topMargin = dp(98);
            addView(title, tLp);
        }

        void setNodePressed(boolean pressed) { 
            circle.setNodePressed(pressed); 
        }

        void bind(LearningPathRepository.Lesson lesson, NodeState state,
                  LearningPathProgressStore.Progress value, DownloadUiState download, int accent) {
            int color = state == NodeState.LOCKED ? COLOR_LOCK_ICON
                    : circle.nodeColor(lesson.type, accent);
            boolean current = state == NodeState.CURRENT;
            bubbleHost.setVisibility(current ? VISIBLE : INVISIBLE);
            
            if (current) {
                bubble.setText(lesson.isRewardNode()
                        ? getString(R.string.learning_path_claim)
                        : getString(R.string.learning_path_start));
                bubble.setTextColor(color);
                bubble.setPadding(dp(16), 0, dp(16), 0);
                bubble.setBackground(LearningUiKit.rounded(Color.WHITE, dp(16), color, dp(2)));
                tail.setColor(color);
                startBob();
            } else {
                stopBob();
            }
            title.setText(lesson.title);
            title.setTextColor(state == NodeState.LOCKED ? COLOR_LOCK_ICON : COLOR_TEXT);

            int nodeProgress = state == NodeState.DOWNLOADING
                    ? (download == null ? 0 : Math.max(0, download.progress))
                    : value == null ? 0 : Math.max(value.bestScore, value.lastScore);
            circle.bind(lesson.type, state, nodeProgress, accent);
        }

        private void startBob() {
            if (bob != null && bob.isRunning()) return;
            bob = android.animation.ObjectAnimator.ofFloat(bubbleHost, View.TRANSLATION_Y,
                    -dp(44), -dp(44) - dp(7));
            bob.setDuration(680);
            bob.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            bob.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            bob.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            bob.start();
        }

        private void stopBob() {
            if (bob != null) { bob.cancel(); bob = null; }
            bubbleHost.setTranslationY(-dp(44));
        }

        @Override 
        protected void onDetachedFromWindow() { 
            stopBob(); 
            super.onDetachedFromWindow(); 
        }
    }

    private final class NodeCircle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private final Path path = new Path();
        private String type = "normal";
        private NodeState state = NodeState.LOCKED;
        private int progressValue;
        private int accent = COLOR_GREEN;
        private boolean nodePressed;

        NodeCircle(Context context) { 
            super(context); 
        }

        void bind(String type, NodeState state, int progress, int accent) {
            this.type = type == null ? "normal" : type;
            this.state = state;
            this.progressValue = Math.max(0, Math.min(100, progress));
            this.accent = accent == 0 ? COLOR_GREEN : accent;
            invalidate();
        }

        void setNodePressed(boolean value) {
            if (nodePressed != value) { 
                nodePressed = value; 
                invalidate(); 
            }
        }

        int nodeColor(String type, int unitAccent) {
            if ("trophy".equals(type) || "chest".equals(type)) return COLOR_GOLD;
            return unitAccent == 0 ? COLOR_GREEN : unitAccent;
        }

        @Override 
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = dp(35);
            float depth = dp(8);
            boolean locked = state == NodeState.LOCKED;

            int fill = locked ? COLOR_LOCK_FILL : nodeColor(type, accent);
            int symbol = locked ? COLOR_LOCK_ICON : Color.WHITE;
            if (state == NodeState.DOWNLOADING) fill = COLOR_BLUE;
            int edge = locked ? COLOR_LOCK_EDGE : edgeOf(fill);

            if (state == NodeState.CURRENT && progressValue > 0) {
                float ring = dp(45);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(8));
                paint.setColor(COLOR_BORDER);
                canvas.drawCircle(cx, cy, ring, paint);
                arc.set(cx - ring, cy - ring, cx + ring, cy + ring);
                paint.setColor(fill);
                canvas.drawArc(arc, -90f, Math.max(6f, progressValue * 3.6f), false, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeCap(Paint.Cap.BUTT);
            }

            // 完美的 3D 按钮交互：底层为深色阴影，表层往下沉
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(edge);
            canvas.drawCircle(cx, cy + depth, radius, paint);
            
            float faceY = nodePressed ? cy + depth : cy;
            paint.setColor(fill);
            canvas.drawCircle(cx, faceY, radius, paint);

            if (state == NodeState.DOWNLOADING) drawDownload(canvas, cx, faceY, radius, symbol);
            else if (state == NodeState.COMPLETED) drawCheck(canvas, cx, faceY, symbol);
            else if ("practice".equals(type) || "review".equals(type)) drawDumbbell(canvas, cx, faceY, symbol);
            else if ("story".equals(type)) drawBook(canvas, cx, faceY, symbol);
            else if ("checkpoint".equals(type) || "test".equals(type)) drawShield(canvas, cx, faceY, symbol);
            else if ("trophy".equals(type) || "chest".equals(type)) drawTrophy(canvas, cx, faceY, symbol);
            else if ("speaking".equals(type)) drawMic(canvas, cx, faceY, symbol);
            else if ("listening".equals(type)) drawSpeaker(canvas, cx, faceY, symbol);
            else drawStar(canvas, cx, faceY, symbol);
        }

        private void drawCheck(Canvas canvas, float cx, float cy, int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(6));
            paint.setColor(color);
            path.reset();
            path.moveTo(cx - dp(14), cy);
            path.lineTo(cx - dp(4), cy + dp(10));
            path.lineTo(cx + dp(16), cy - dp(12));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawStar(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            path.reset();
            for (int i = 0; i < 10; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                float radius = i % 2 == 0 ? dp(19) : dp(9);
                float x = cx + (float) Math.cos(angle) * radius;
                float y = cy + (float) Math.sin(angle) * radius;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            canvas.drawPath(path, paint);
        }

        private void drawDumbbell(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            paint.setStrokeWidth(dp(7));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx - dp(14), cy, cx + dp(14), cy, paint);
            canvas.drawRoundRect(cx - dp(22), cy - dp(13), cx - dp(14), cy + dp(13), dp(3), dp(3), paint);
            canvas.drawRoundRect(cx + dp(14), cy - dp(13), cx + dp(22), cy + dp(13), dp(3), dp(3), paint);
        }

        private void drawBook(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            path.reset();
            path.moveTo(cx - dp(21), cy - dp(15));
            path.quadTo(cx - dp(9), cy - dp(18), cx, cy - dp(8));
            path.quadTo(cx + dp(9), cy - dp(18), cx + dp(21), cy - dp(15));
            path.lineTo(cx + dp(21), cy + dp(17));
            path.quadTo(cx + dp(9), cy + dp(13), cx, cy + dp(20));
            path.quadTo(cx - dp(9), cy + dp(13), cx - dp(21), cy + dp(17));
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(0x66000000); // Inner page crease shadow
            canvas.drawRect(cx - dp(1), cy - dp(8), cx + dp(1), cy + dp(19), paint);
        }

        private void drawShield(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            path.reset();
            path.moveTo(cx, cy - dp(22));
            path.lineTo(cx + dp(19), cy - dp(14));
            path.lineTo(cx + dp(16), cy + dp(7));
            path.quadTo(cx + dp(11), cy + dp(18), cx, cy + dp(23));
            path.quadTo(cx - dp(11), cy + dp(18), cx - dp(16), cy + dp(7));
            path.lineTo(cx - dp(19), cy - dp(14));
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(0x44000000);
            canvas.drawCircle(cx, cy, dp(6), paint);
        }

        private void drawTrophy(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            canvas.drawRoundRect(cx - dp(15), cy - dp(20), cx + dp(15), cy + dp(5), dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            arc.set(cx - dp(26), cy - dp(17), cx - dp(9), cy + dp(2));
            canvas.drawArc(arc, 75, 210, false, paint);
            arc.set(cx + dp(9), cy - dp(17), cx + dp(26), cy + dp(2));
            canvas.drawArc(arc, -105, 210, false, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(cx - dp(4), cy + dp(3), cx + dp(4), cy + dp(17), dp(2), dp(2), paint);
            canvas.drawRoundRect(cx - dp(14), cy + dp(15), cx + dp(14), cy + dp(21), dp(3), dp(3), paint);
        }

        private void drawMic(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            canvas.drawRoundRect(cx - dp(8), cy - dp(21), cx + dp(8), cy + dp(6), dp(8), dp(8), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            arc.set(cx - dp(16), cy - dp(7), cx + dp(16), cy + dp(15));
            canvas.drawArc(arc, 0, 180, false, paint);
            canvas.drawLine(cx, cy + dp(15), cx, cy + dp(23), paint);
            canvas.drawLine(cx - dp(10), cy + dp(23), cx + dp(10), cy + dp(23), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawSpeaker(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            path.reset();
            path.moveTo(cx - dp(20), cy - dp(8));
            path.lineTo(cx - dp(10), cy - dp(8));
            path.lineTo(cx + dp(2), cy - dp(19));
            path.lineTo(cx + dp(2), cy + dp(19));
            path.lineTo(cx - dp(10), cy + dp(8));
            path.lineTo(cx - dp(20), cy + dp(8));
            path.close();
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            arc.set(cx - dp(3), cy - dp(16), cx + dp(22), cy + dp(16));
            canvas.drawArc(arc, -55, 110, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawDownload(Canvas canvas, float cx, float cy, float radius, int color) {
            arc.set(cx - radius + dp(7), cy - radius + dp(7), cx + radius - dp(7), cy + radius - dp(7));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(5));
            paint.setColor(color);
            canvas.drawArc(arc, -90f, progressValue * 3.6f, false, paint);
            canvas.drawLine(cx, cy - dp(12), cx, cy + dp(9), paint);
            path.reset();
            path.moveTo(cx - dp(8), cy + dp(1));
            path.lineTo(cx, cy + dp(10));
            path.lineTo(cx + dp(8), cy + dp(1));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static int characterVariant(String character, int fallback) {
        String value = character == null ? "" : character.trim().toLowerCase(java.util.Locale.US);
        if ("mei".equals(value)) return 0;
        if ("bo".equals(value)) return 1;
        if ("lin".equals(value)) return 2;
        if ("ya".equals(value)) return 3;
        if ("kai".equals(value)) return 4;
        if ("ning".equals(value)) return 5;
        return Math.floorMod(fallback, 6);
    }
}
