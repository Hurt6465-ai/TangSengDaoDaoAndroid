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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.SoundEffectConstants;
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

    private static final boolean SHOW_LESSON_TITLE = true;
    private static final int ROW_H_DP = 132;
    private static final int STICKY_HEADER_H_DP = 62;
    private static final int[] WARM_UNIT_COLORS = {
            0xFFFF7A59, // vivid coral
            0xFFFFB020, // golden amber
            0xFF58CC02, // duolingo green
            0xFF1CB0F6, // vivid sky
            0xFF9B51E0, // purple
            0xFFFF4B8B  // rose
    };

    private static int edgeOf(int color) {
        return LearningUiKit.blend(color, Color.BLACK, 0.17f);
    }

    private static int unitColor(int declaredColor, int unitIndex) {
        int color = declaredColor == 0
                ? WARM_UNIT_COLORS[Math.floorMod(unitIndex, WARM_UNIT_COLORS.length)]
                : declaredColor;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0.66f, hsv[1]);
        hsv[2] = Math.max(0.83f, hsv[2]);
        return Color.HSVToColor(255, hsv);
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
        
        // The map starts directly with the compact sticky unit card.
        // System back/gesture remains available; no duplicate back or refresh controls.
        FrameLayout content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        recyclerView = new RecyclerView(this);
        recyclerView.setBackgroundColor(COLOR_BG);
        recyclerView.setClipToPadding(false);
        recyclerView.setClipChildren(false);
        recyclerView.setPadding(0, dp(STICKY_HEADER_H_DP + 4), 0, dp(80));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(10);
        recyclerView.setHasFixedSize(false);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setInitialPrefetchItemCount(8);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new CourseMapAdapter();
        recyclerView.setAdapter(adapter);
        content.addView(recyclerView, new FrameLayout.LayoutParams(-1, -1));

        stickyHeader = new UnitHeaderView(this);
        stickyHeader.setVisibility(View.INVISIBLE);
        content.addView(stickyHeader,
                new FrameLayout.LayoutParams(-1, dp(STICKY_HEADER_H_DP)));

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateSticky();
            }
        });
        recyclerView.post(this::updateSticky);
    }

    private void updateSticky() {
        if (stickyHeader == null || adapter == null || layoutManager == null || course == null) {
            if (stickyHeader != null) stickyHeader.setVisibility(View.INVISIBLE);
            return;
        }

        MapItem item = null;
        float anchorY = Math.max(dp(STICKY_HEADER_H_DP), stickyHeader.getHeight()) + dp(2);
        View anchorChild = recyclerView.findChildViewUnder(recyclerView.getWidth() / 2f, anchorY);
        if (anchorChild != null) {
            int position = recyclerView.getChildAdapterPosition(anchorChild);
            item = position == RecyclerView.NO_POSITION ? null : adapter.itemAt(position);
        }
        if (item == null || item.unit == null) {
            int first = layoutManager.findFirstVisibleItemPosition();
            item = first == RecyclerView.NO_POSITION ? null : adapter.itemAt(first);
        }
        if (item == null || item.unit == null) {
            stickyHeader.setVisibility(View.INVISIBLE);
            return;
        }
        stickyHeader.setVisibility(View.VISIBLE);
        stickyHeader.setTranslationY(0f);
        stickyHeader.bind(item.unit, item.unitIndex,
                course == null ? 0 : course.units.size(), true);
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

        pageTitle = text("", 18, COLOR_TEXT, true);
        pageTitle.setVisibility(View.INVISIBLE);
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
            if (pageTitle != null) pageTitle.setText("");
            if (stickyHeader != null) stickyHeader.setVisibility(View.INVISIBLE);
            adapter.submitMessage(getString(R.string.learning_path_empty));
            return;
        }
        if (pageTitle != null) pageTitle.setText("");
        if (course.minAppVersion > currentVersionCode()) {
            if (stickyHeader != null) stickyHeader.setVisibility(View.INVISIBLE);
            adapter.submitMessage(getString(R.string.learning_path_app_update_required));
            return;
        }

        Parcelable oldState = !catalogChanged && layoutManager != null
                ? layoutManager.onSaveInstanceState() : null;
        String currentLesson = findCurrentLesson(LearningPathRepository.flatten(course));
        adapter.submitCourse(course, currentLesson);
        if (oldState != null) layoutManager.onRestoreInstanceState(oldState);
        if (!course.units.isEmpty()) {
            stickyHeader.setVisibility(View.VISIBLE);
            stickyHeader.bind(course.units.get(0), 0, course.units.size(), false);
        }

        if (!initialScrollDone) {
            initialScrollDone = true;
            recyclerView.post(() -> {
                int position = !anchorUnitId.isEmpty()
                        ? adapter.positionForUnit(anchorUnitId)
                        : adapter.positionForLesson(currentLesson);
                if (position < 0) position = 0;
                layoutManager.scrollToPositionWithOffset(position, dp(10));
                recyclerView.post(LearningPathActivity.this::updateSticky);
            });
        } else {
            recyclerView.post(LearningPathActivity.this::updateSticky);
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

    private Drawable gradientRaised(int startColor, int endColor, int edgeColor,
                                    float radius, int depth) {
        GradientDrawable bottom = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{edgeColor, LearningUiKit.blend(edgeColor, Color.WHITE, 0.12f)});
        bottom.setCornerRadius(radius);

        GradientDrawable top = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{startColor, endColor});
        top.setCornerRadius(radius);
        top.setStroke(dp(1), LearningUiKit.blend(startColor, Color.WHITE, 0.28f));

        LayerDrawable layers = new LayerDrawable(new Drawable[]{bottom, top});
        int inset = Math.max(0, depth);
        layers.setLayerInset(0, 0, inset, 0, 0);
        layers.setLayerInset(1, 0, 0, 0, inset);
        return layers;
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
        static final int UNIT = 1;
        static final int LESSON = 2;

        int type;
        String message = "";
        LearningPathRepository.Course course;
        LearningPathRepository.Unit unit;
        LearningPathRepository.Lesson lesson;
        int unitIndex;
        int lessonIndex;
        int globalLessonIndex;
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

            int globalLessonIndex = 0;
            for (int unitIndex = 0; unitIndex < value.units.size(); unitIndex++) {
                LearningPathRepository.Unit unit = value.units.get(unitIndex);
                unitPositions.put(unit.id, items.size());

                // The first unit starts directly below the sticky card. Later units use a
                // compact in-map marker; the large card itself stays fixed and pages its text.
                if (unitIndex > 0) {
                    MapItem header = new MapItem();
                    header.type = MapItem.UNIT;
                    header.course = value;
                    header.unit = unit;
                    header.unitIndex = unitIndex;
                    items.add(header);
                }

                for (int lessonIndex = 0; lessonIndex < unit.lessons.size(); lessonIndex++) {
                    LearningPathRepository.Lesson lesson = unit.lessons.get(lessonIndex);
                    MapItem node = new MapItem();
                    node.type = MapItem.LESSON;
                    node.course = value;
                    node.unit = unit;
                    node.lesson = lesson;
                    node.unitIndex = unitIndex;
                    node.lessonIndex = lessonIndex;
                    node.globalLessonIndex = globalLessonIndex++;
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
            if (viewType == MapItem.UNIT) return new UnitHolder(new UnitMarkerView(parent.getContext()));
            if (viewType == MapItem.LESSON) return new LessonHolder(new LessonRowView(parent.getContext()));
            return new MessageHolder(new MessageView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MapItem item = items.get(position);
            if (holder instanceof UnitHolder) {
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

    private static final class UnitHolder extends RecyclerView.ViewHolder {
        final UnitMarkerView view;
        UnitHolder(UnitMarkerView value) { super(value); view = value; }
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

    /** Compact second-level marker inside the virtualized map. */
    private final class UnitMarkerView extends FrameLayout {
        private final TextView dot;
        private final TextView title;
        private final TextView page;

        UnitMarkerView(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(-1, dp(58)));
            setPadding(dp(24), dp(8), dp(24), dp(8));

            View line = new View(context);
            line.setBackgroundColor(COLOR_BORDER);
            LayoutParams lineLp = new LayoutParams(-1, dp(1), Gravity.CENTER_VERTICAL);
            addView(line, lineLp);

            LinearLayout labelHost = new LinearLayout(context);
            labelHost.setOrientation(LinearLayout.HORIZONTAL);
            labelHost.setGravity(Gravity.CENTER_VERTICAL);
            labelHost.setPadding(dp(12), 0, dp(12), 0);
            labelHost.setBackgroundColor(COLOR_BG);
            LayoutParams hostLp = new LayoutParams(-2, dp(38), Gravity.CENTER);
            addView(labelHost, hostLp);

            dot = text("●", 12, COLOR_BLUE, true);
            dot.setGravity(Gravity.CENTER);
            labelHost.addView(dot, new LinearLayout.LayoutParams(dp(20), -1));

            title = text("", 15, COLOR_TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setMaxWidth(Math.max(dp(150),
                    getResources().getDisplayMetrics().widthPixels - dp(150)));
            labelHost.addView(title, new LinearLayout.LayoutParams(-2, -1));

            page = text("", 11, COLOR_SUBTEXT, true);
            page.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams pageLp = new LinearLayout.LayoutParams(-2, -1);
            pageLp.setMargins(dp(8), 0, 0, 0);
            labelHost.addView(page, pageLp);
        }

        void bind(LearningPathRepository.Unit unit, int unitIndex) {
            int accent = unitColor(unit.accent, unitIndex);
            dot.setTextColor(accent);
            title.setText(unit.title);
            int total = course == null ? 0 : course.units.size();
            page.setText(total > 0 ? (unitIndex + 1) + " / " + total : "");
            setContentDescription(unit.title);
        }
    }

    /** One fixed, short, page-like unit card. It changes as the map crosses a unit marker. */
    private final class UnitHeaderView extends FrameLayout {
        private final FrameLayout card;
        private final TextView title;
        private final TextView pageLabel;
        private final TextView progressLabel;
        private final LearningUiKit.ProgressView progressView;
        private final GuidebookIcon guide;
        private String boundUnitId = "";
        private int boundUnitIndex = -1;

        UnitHeaderView(Context context) {
            super(context);
            setPadding(dp(18), dp(4), dp(18), dp(4));

            card = new FrameLayout(context);
            addView(card, new LayoutParams(-1, dp(54)));

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            body.setPadding(dp(15), dp(4), dp(8), dp(6));
            card.addView(body, new LayoutParams(-1, -1));

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setGravity(Gravity.CENTER_VERTICAL);
            body.addView(textColumn, new LinearLayout.LayoutParams(0, -1, 1f));

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            textColumn.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(26)));

            title = text("", 16, COLOR_TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

            pageLabel = text("", 11, COLOR_SUBTEXT, true);
            pageLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams pageLp = new LinearLayout.LayoutParams(dp(38), -1);
            pageLp.setMargins(dp(8), 0, 0, 0);
            titleRow.addView(pageLabel, pageLp);

            LinearLayout progressRow = new LinearLayout(context);
            progressRow.setOrientation(LinearLayout.HORIZONTAL);
            progressRow.setGravity(Gravity.CENTER_VERTICAL);
            textColumn.addView(progressRow, new LinearLayout.LayoutParams(-1, dp(13)));

            progressView = new LearningUiKit.ProgressView(context);
            progressRow.addView(progressView, new LinearLayout.LayoutParams(0, dp(5), 1f));

            progressLabel = text("", 10, COLOR_SUBTEXT, true);
            progressLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(dp(36), -1);
            labelLp.setMargins(dp(7), 0, 0, 0);
            progressRow.addView(progressLabel, labelLp);

            guide = new GuidebookIcon(context);
            LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(dp(34), dp(34));
            guideLp.setMargins(dp(8), 0, 0, 0);
            body.addView(guide, guideLp);
        }

        void bind(LearningPathRepository.Unit unit, int unitIndex, int totalUnits,
                  boolean animatePage) {
            if (unit == null) return;
            boolean changed = !unit.id.equals(boundUnitId);
            int direction = boundUnitIndex < 0 || unitIndex >= boundUnitIndex ? 1 : -1;
            boundUnitId = unit.id;
            boundUnitIndex = unitIndex;

            int accent = unitColor(unit.accent, unitIndex);
            int start = LearningUiKit.blend(accent, Color.WHITE, 0.05f);
            int end = LearningUiKit.blend(accent, Color.BLACK, 0.08f);
            int edge = LearningUiKit.blend(accent, Color.BLACK, 0.19f);
            int ink = Color.WHITE;
            int softInk = 0xE8FFFFFF;
            int track = 0x54FFFFFF;

            card.setBackground(gradientRaised(start, end, edge, dp(16), dp(4)));
            title.setText(unit.title);
            title.setTextColor(ink);
            pageLabel.setText(totalUnits > 0 ? (unitIndex + 1) + " / " + totalUnits : "");
            pageLabel.setTextColor(softInk);
            guide.setColor(Color.WHITE);

            int completed = 0;
            for (LearningPathRepository.Lesson lesson : unit.lessons) {
                LearningPathProgressStore.Progress value = progress.get(lesson.id);
                if (value != null && value.completed()) completed++;
            }
            progressView.setColors(track, ink);
            progressView.setProgress(completed, Math.max(1, unit.lessons.size()));
            progressLabel.setText(completed + "/" + unit.lessons.size());
            progressLabel.setTextColor(softInk);
            setContentDescription(unit.title + ", " + progressLabel.getText());

            if (changed && animatePage && isLaidOut()) {
                animate().cancel();
                setAlpha(0.72f);
                setTranslationX(direction * dp(14));
                animate().alpha(1f).translationX(0f).setDuration(180L).start();
            } else {
                setAlpha(1f);
                setTranslationX(0f);
            }
        }
    }

    private final class GuidebookIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int color = Color.WHITE;
        GuidebookIcon(Context context) { super(context); }
        void setColor(int value) { color = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setColor(color);
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
        private final MapDecorationView decoration;
        private MapItem boundItem;
        private NodeState boundState = NodeState.LOCKED;

        LessonRowView(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
            setBackgroundColor(COLOR_BG);
            setLayoutParams(new RecyclerView.LayoutParams(-1, dp(ROW_H_DP)));

            decoration = new MapDecorationView(context);
            addView(decoration, new LayoutParams(dp(96), dp(96),
                    Gravity.BOTTOM | Gravity.START));

            node = new NodeGroup(context);
            addView(node, new LayoutParams(dp(156), dp(ROW_H_DP),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            setOnClickListener(v -> {
                if (boundItem == null || boundItem.lesson == null) return;
                v.playSoundEffect(SoundEffectConstants.CLICK);
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
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
            if (lp != null && lp.height != wanted) {
                lp.height = wanted;
                setLayoutParams(lp);
            }

            int width = Math.min(dp(680), getResources().getDisplayMetrics().widthPixels);
            int max = Math.max(0, width / 2 - dp(66));
            int offset = dp(PATH_OFFSET[Math.floorMod(item.lessonIndex, PATH_OFFSET.length)]);
            offset = Math.max(-max, Math.min(max, offset));
            node.setTranslationX(offset);

            int accent = unitColor(item.unit.accent, item.unitIndex);
            node.bind(item.lesson, state, value, download, accent);

            decoration.bind(item.globalLessonIndex, item.lesson.type, accent, state);
            LayoutParams decorLp = (LayoutParams) decoration.getLayoutParams();
            decorLp.gravity = (offset >= 0 ? Gravity.START : Gravity.END) | Gravity.BOTTOM;
            decorLp.setMargins(offset >= 0 ? dp(12) : 0, 0,
                    offset >= 0 ? 0 : dp(12), dp(14));
            decoration.setLayoutParams(decorLp);

            String stateText;
            if (state == NodeState.COMPLETED) stateText = getString(R.string.learning_path_completed);
            else if (state == NodeState.CURRENT) stateText = getString(R.string.learning_path_current);
            else if (state == NodeState.DOWNLOADING) stateText = (download == null ? 0 : download.progress) + "%";
            else if (state == NodeState.AVAILABLE) stateText = getString(R.string.learning_path_available);
            else stateText = getString(R.string.learning_path_locked);
            setContentDescription(item.lesson.title + ", " + stateText);
            setAlpha(1f);
        }
    }

    /**
     * Canvas-only decoration layer: about 70% light ornaments, 20% scene props,
     * and 10% original mascots. It animates only while its RecyclerView row is attached.
     */
    private final class MapDecorationView extends View {
        private static final int KIND_LIGHT = 0;
        private static final int KIND_SCENE = 1;
        private static final int KIND_MASCOT = 2;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private android.animation.ValueAnimator animator;
        private float phase;
        private int kind;
        private int variant;
        private int accent = COLOR_BLUE;
        private NodeState state = NodeState.LOCKED;
        private long celebrationStarted;

        MapDecorationView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        void bind(int globalIndex, String lessonType, int color, NodeState newState) {
            int slot = Math.floorMod(globalIndex, 10);
            kind = slot < 7 ? KIND_LIGHT : (slot < 9 ? KIND_SCENE : KIND_MASCOT);
            variant = Math.floorMod(globalIndex + (lessonType == null ? 0 : lessonType.hashCode()),
                    kind == KIND_MASCOT ? 5 : (kind == KIND_SCENE ? 4 : 7));
            accent = color;
            if (state != NodeState.COMPLETED && newState == NodeState.COMPLETED) {
                celebrationStarted = android.os.SystemClock.uptimeMillis();
            }
            state = newState;
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            startAnimator();
        }

        @Override
        protected void onDetachedFromWindow() {
            stopAnimator();
            super.onDetachedFromWindow();
        }

        private void startAnimator() {
            if (animator != null && animator.isRunning()) return;
            animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(4200L + variant * 180L);
            animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            animator.setInterpolator(new android.view.animation.LinearInterpolator());
            animator.addUpdateListener(value -> {
                phase = (float) value.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        private void stopAnimator() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float floatY = (float) Math.sin(phase * Math.PI * 2d) * dp(kind == KIND_MASCOT ? 1.8f : 2.6f);
            long elapsed = android.os.SystemClock.uptimeMillis() - celebrationStarted;
            float jump = 0f;
            if (state == NodeState.COMPLETED && elapsed >= 0L && elapsed < 620L) {
                float p = elapsed / 620f;
                jump = (float) -Math.sin(p * Math.PI) * dp(9);
            }

            canvas.save();
            canvas.translate(0f, floatY + jump);
            if (kind == KIND_LIGHT) drawLight(canvas, w, h);
            else if (kind == KIND_SCENE) drawScene(canvas, w, h);
            else drawMascot(canvas, w, h);
            if (state == NodeState.COMPLETED) drawCompletionSparkles(canvas, w, h);
            canvas.restore();
        }

        private void drawLight(Canvas canvas, float w, float h) {
            switch (variant) {
                case 0:
                    drawCloud(canvas, w * 0.52f, h * 0.55f, Math.min(w, h) * 0.25f);
                    break;
                case 1:
                    drawSparkle(canvas, w * 0.35f, h * 0.46f, dp(9), 0xFFFFC800);
                    drawSparkle(canvas, w * 0.64f, h * 0.62f, dp(6),
                            LearningUiKit.blend(accent, Color.WHITE, 0.25f));
                    drawDot(canvas, w * 0.64f, h * 0.33f, dp(3), 0x66FFC800);
                    break;
                case 2:
                    drawLeaves(canvas, w, h, false);
                    break;
                case 3:
                    drawFlower(canvas, w * 0.5f, h * 0.57f, dp(7));
                    break;
                case 4:
                    drawPaperPlane(canvas, w, h);
                    break;
                case 5:
                    drawLeaves(canvas, w, h, true);
                    break;
                default:
                    drawDot(canvas, w * 0.32f, h * 0.46f, dp(4), 0x6658CC02);
                    drawSparkle(canvas, w * 0.56f, h * 0.42f, dp(7),
                            LearningUiKit.blend(accent, Color.WHITE, 0.18f));
                    drawDot(canvas, w * 0.68f, h * 0.68f, dp(3), 0x66FF4B8B);
                    break;
            }
        }

        private void drawScene(Canvas canvas, float w, float h) {
            switch (variant) {
                case 0:
                    drawWhiteBook(canvas, w, h);
                    break;
                case 1:
                    drawHeadphones(canvas, w, h);
                    break;
                case 2:
                    drawFlag(canvas, w, h);
                    break;
                default:
                    drawLantern(canvas, w, h);
                    break;
            }
        }

        private void drawCloud(Canvas canvas, float cx, float cy, float r) {
            paint.setColor(0xDDF2FAFF);
            paint.setShadowLayer(dp(4), 0f, dp(2), 0x18000000);
            canvas.drawCircle(cx - r * 0.55f, cy, r * 0.55f, paint);
            canvas.drawCircle(cx, cy - r * 0.25f, r * 0.72f, paint);
            canvas.drawCircle(cx + r * 0.62f, cy, r * 0.48f, paint);
            rect.set(cx - r, cy, cx + r * 1.08f, cy + r * 0.65f);
            canvas.drawRoundRect(rect, r * 0.32f, r * 0.32f, paint);
            paint.clearShadowLayer();
        }

        private void drawSparkle(Canvas canvas, float cx, float cy, float r, int color) {
            paint.setColor(color);
            path.reset();
            path.moveTo(cx, cy - r);
            path.quadTo(cx + r * 0.2f, cy - r * 0.2f, cx + r, cy);
            path.quadTo(cx + r * 0.2f, cy + r * 0.2f, cx, cy + r);
            path.quadTo(cx - r * 0.2f, cy + r * 0.2f, cx - r, cy);
            path.quadTo(cx - r * 0.2f, cy - r * 0.2f, cx, cy - r);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void drawDot(Canvas canvas, float cx, float cy, float r, int color) {
            paint.setColor(color);
            canvas.drawCircle(cx, cy, r, paint);
        }

        private void drawLeaves(Canvas canvas, float w, float h, boolean bamboo) {
            float sway = (float) Math.sin(phase * Math.PI * 2d) * dp(2);
            stroke.setColor(LearningUiKit.blend(accent, Color.BLACK, 0.24f));
            stroke.setStrokeWidth(dp(2));
            canvas.drawLine(w * 0.32f, h * 0.72f, w * 0.68f + sway, h * 0.3f, stroke);
            paint.setColor(LearningUiKit.blend(accent, Color.WHITE, bamboo ? 0.34f : 0.52f));
            for (int i = 0; i < 4; i++) {
                float x = w * (0.37f + i * 0.08f) + sway * i * 0.15f;
                float y = h * (0.62f - i * 0.09f);
                canvas.save();
                canvas.rotate((i % 2 == 0 ? -32f : 32f) + sway, x, y);
                rect.set(x - dp(4), y - dp(11), x + dp(4), y + dp(11));
                canvas.drawOval(rect, paint);
                canvas.restore();
            }
        }

        private void drawFlower(Canvas canvas, float cx, float cy, float r) {
            int[] colors = {0xFFFFD6E5, 0xFFFFE49B, 0xFFD8F5C8, 0xFFDCCBFF};
            paint.setColor(colors[Math.floorMod(variant, colors.length)]);
            for (int i = 0; i < 5; i++) {
                double a = Math.PI * 2d * i / 5d + phase * 0.08d;
                canvas.drawCircle(cx + (float) Math.cos(a) * r,
                        cy + (float) Math.sin(a) * r, r * 0.72f, paint);
            }
            paint.setColor(0xFFFFC800);
            canvas.drawCircle(cx, cy, r * 0.64f, paint);
        }

        private void drawPaperPlane(Canvas canvas, float w, float h) {
            float x = w * 0.5f + (float) Math.sin(phase * Math.PI * 2d) * dp(4);
            float y = h * 0.52f;
            paint.setColor(LearningUiKit.blend(accent, Color.WHITE, 0.28f));
            path.reset();
            path.moveTo(x - dp(23), y - dp(10));
            path.lineTo(x + dp(24), y - dp(20));
            path.lineTo(x + dp(6), y + dp(22));
            path.lineTo(x - dp(2), y + dp(4));
            path.close();
            canvas.drawPath(path, paint);
            stroke.setColor(LearningUiKit.blend(accent, Color.BLACK, 0.22f));
            stroke.setStrokeWidth(dp(2));
            canvas.drawLine(x - dp(2), y + dp(4), x + dp(24), y - dp(20), stroke);
        }

        private void drawWhiteBook(Canvas canvas, float w, float h) {
            float cx = w * 0.5f, cy = h * 0.58f;
            paint.setColor(0xFFFFFFFF);
            paint.setShadowLayer(dp(3), 0f, dp(2), 0x1C000000);
            path.reset();
            path.moveTo(cx, cy - dp(17));
            path.quadTo(cx - dp(13), cy - dp(25), cx - dp(28), cy - dp(16));
            path.lineTo(cx - dp(28), cy + dp(20));
            path.quadTo(cx - dp(12), cy + dp(13), cx, cy + dp(22));
            path.quadTo(cx + dp(12), cy + dp(13), cx + dp(28), cy + dp(20));
            path.lineTo(cx + dp(28), cy - dp(16));
            path.quadTo(cx + dp(13), cy - dp(25), cx, cy - dp(17));
            path.close();
            canvas.drawPath(path, paint);
            paint.clearShadowLayer();
            stroke.setColor(LearningUiKit.blend(accent, Color.BLACK, 0.1f));
            stroke.setStrokeWidth(dp(2));
            canvas.drawLine(cx, cy - dp(17), cx, cy + dp(21), stroke);
            stroke.setStrokeWidth(dp(1.4f));
            for (int i = 0; i < 2; i++) {
                float yy = cy - dp(6) + i * dp(8);
                canvas.drawLine(cx - dp(22), yy, cx - dp(6), yy - dp(2), stroke);
                canvas.drawLine(cx + dp(6), yy - dp(2), cx + dp(22), yy, stroke);
            }
        }

        private void drawHeadphones(Canvas canvas, float w, float h) {
            float cx = w * 0.5f, cy = h * 0.55f;
            stroke.setColor(accent);
            stroke.setStrokeWidth(dp(7));
            rect.set(cx - dp(24), cy - dp(26), cx + dp(24), cy + dp(20));
            canvas.drawArc(rect, 198, 144, false, stroke);
            paint.setColor(LearningUiKit.blend(accent, Color.WHITE, 0.1f));
            rect.set(cx - dp(30), cy - dp(5), cx - dp(17), cy + dp(22));
            canvas.drawRoundRect(rect, dp(6), dp(6), paint);
            rect.set(cx + dp(17), cy - dp(5), cx + dp(30), cy + dp(22));
            canvas.drawRoundRect(rect, dp(6), dp(6), paint);
            drawSparkle(canvas, cx + dp(30), cy - dp(22), dp(5), 0xFFFFC800);
        }

        private void drawFlag(Canvas canvas, float w, float h) {
            float x = w * 0.36f, top = h * 0.28f, bottom = h * 0.78f;
            stroke.setColor(LearningUiKit.blend(accent, Color.BLACK, 0.2f));
            stroke.setStrokeWidth(dp(3));
            canvas.drawLine(x, top, x, bottom, stroke);
            paint.setColor(accent);
            path.reset();
            path.moveTo(x + dp(1), top);
            path.quadTo(x + dp(18), top + dp(4), x + dp(34), top + dp(1));
            path.lineTo(x + dp(30), top + dp(24));
            path.quadTo(x + dp(15), top + dp(27), x + dp(1), top + dp(22));
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(0xFFFFC800);
            canvas.drawCircle(x, bottom, dp(5), paint);
        }

        private void drawLantern(Canvas canvas, float w, float h) {
            float cx = w * 0.5f, cy = h * 0.52f;
            stroke.setColor(0xFFFFB020);
            stroke.setStrokeWidth(dp(2));
            canvas.drawLine(cx, cy - dp(30), cx, cy - dp(21), stroke);
            paint.setColor(0xFFFF6B5E);
            rect.set(cx - dp(20), cy - dp(21), cx + dp(20), cy + dp(19));
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);
            paint.setColor(0x44FFFFFF);
            rect.set(cx - dp(11), cy - dp(16), cx - dp(5), cy + dp(14));
            canvas.drawRoundRect(rect, dp(3), dp(3), paint);
            stroke.setColor(0xFFFFB020);
            stroke.setStrokeWidth(dp(3));
            canvas.drawLine(cx - dp(18), cy - dp(21), cx + dp(18), cy - dp(21), stroke);
            canvas.drawLine(cx - dp(18), cy + dp(19), cx + dp(18), cy + dp(19), stroke);
            canvas.drawLine(cx, cy + dp(19), cx, cy + dp(31), stroke);
        }

        private void drawMascot(Canvas canvas, float w, float h) {
            float breathe = 1f + (float) Math.sin(phase * Math.PI * 2d) * 0.025f;
            float cx = w * 0.5f, cy = h * 0.58f;
            canvas.save();
            canvas.scale(breathe, breathe, cx, cy);
            switch (variant) {
                case 0: drawPanda(canvas, cx, cy); break;
                case 1: drawRabbit(canvas, cx, cy); break;
                case 2: drawCat(canvas, cx, cy); break;
                case 3: drawBird(canvas, cx, cy); break;
                default: drawDragon(canvas, cx, cy); break;
            }
            canvas.restore();
        }

        private boolean blink() {
            return phase > 0.42f && phase < 0.47f;
        }

        private void drawFace(Canvas canvas, float cx, float cy, float eyeGap, int faceColor) {
            paint.setColor(faceColor);
            canvas.drawCircle(cx, cy, dp(24), paint);
            paint.setColor(0xFFFFA8A8);
            canvas.drawCircle(cx - dp(15), cy + dp(6), dp(4), paint);
            canvas.drawCircle(cx + dp(15), cy + dp(6), dp(4), paint);
            paint.setColor(0xFF3E3540);
            if (blink()) {
                stroke.setColor(0xFF3E3540);
                stroke.setStrokeWidth(dp(2));
                canvas.drawLine(cx - eyeGap - dp(3), cy, cx - eyeGap + dp(3), cy, stroke);
                canvas.drawLine(cx + eyeGap - dp(3), cy, cx + eyeGap + dp(3), cy, stroke);
            } else {
                canvas.drawCircle(cx - eyeGap, cy, dp(3.2f), paint);
                canvas.drawCircle(cx + eyeGap, cy, dp(3.2f), paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx - eyeGap - dp(1), cy - dp(1), dp(1), paint);
                canvas.drawCircle(cx + eyeGap - dp(1), cy - dp(1), dp(1), paint);
            }
            stroke.setColor(0xFF6A3C45);
            stroke.setStrokeWidth(dp(1.7f));
            rect.set(cx - dp(5), cy + dp(5), cx + dp(5), cy + dp(13));
            canvas.drawArc(rect, 10, 160, false, stroke);
        }

        private void drawPanda(Canvas canvas, float cx, float cy) {
            paint.setColor(0xFF2F3036);
            canvas.drawCircle(cx - dp(18), cy - dp(23), dp(10), paint);
            canvas.drawCircle(cx + dp(18), cy - dp(23), dp(10), paint);
            paint.setColor(0xFFFDFCF7);
            canvas.drawOval(cx - dp(26), cy - dp(27), cx + dp(26), cy + dp(26), paint);
            paint.setColor(0xFF2F3036);
            canvas.drawOval(cx - dp(17), cy - dp(10), cx - dp(5), cy + dp(5), paint);
            canvas.drawOval(cx + dp(5), cy - dp(10), cx + dp(17), cy + dp(5), paint);
            drawFace(canvas, cx, cy - dp(1), dp(11), 0x00FFFFFF);
            drawWhiteBook(canvas, getWidth(), getHeight() + dp(20));
        }

        private void drawRabbit(Canvas canvas, float cx, float cy) {
            float wiggle = (float) Math.sin(phase * Math.PI * 4d) * dp(2);
            paint.setColor(0xFFFFF7EE);
            rect.set(cx - dp(19) + wiggle, cy - dp(46), cx - dp(5) + wiggle, cy - dp(9));
            canvas.drawOval(rect, paint);
            rect.set(cx + dp(5) - wiggle, cy - dp(46), cx + dp(19) - wiggle, cy - dp(9));
            canvas.drawOval(rect, paint);
            paint.setColor(0xFFFFB4C6);
            rect.set(cx - dp(15) + wiggle, cy - dp(41), cx - dp(9) + wiggle, cy - dp(14));
            canvas.drawOval(rect, paint);
            rect.set(cx + dp(9) - wiggle, cy - dp(41), cx + dp(15) - wiggle, cy - dp(14));
            canvas.drawOval(rect, paint);
            drawFace(canvas, cx, cy, dp(9), 0xFFFFF7EE);
        }

        private void drawCat(Canvas canvas, float cx, float cy) {
            int fur = 0xFFFFB44A;
            paint.setColor(fur);
            path.reset();
            path.moveTo(cx - dp(24), cy - dp(10));
            path.lineTo(cx - dp(18), cy - dp(34));
            path.lineTo(cx - dp(4), cy - dp(24));
            path.lineTo(cx + dp(18), cy - dp(34));
            path.lineTo(cx + dp(24), cy - dp(10));
            path.close();
            canvas.drawPath(path, paint);
            drawFace(canvas, cx, cy, dp(9), fur);
            stroke.setColor(0xFF8D5A2D);
            stroke.setStrokeWidth(dp(1.6f));
            canvas.drawLine(cx - dp(22), cy + dp(4), cx - dp(34), cy, stroke);
            canvas.drawLine(cx + dp(22), cy + dp(4), cx + dp(34), cy, stroke);
        }

        private void drawBird(Canvas canvas, float cx, float cy) {
            int body = 0xFFFFD84D;
            paint.setColor(body);
            canvas.drawOval(cx - dp(25), cy - dp(27), cx + dp(25), cy + dp(26), paint);
            paint.setColor(0xFFFF9E35);
            path.reset();
            path.moveTo(cx - dp(3), cy + dp(2));
            path.lineTo(cx + dp(8), cy + dp(7));
            path.lineTo(cx - dp(3), cy + dp(11));
            path.close();
            canvas.drawPath(path, paint);
            drawFace(canvas, cx - dp(3), cy - dp(4), dp(10), 0x00FFFFFF);
            stroke.setColor(accent);
            stroke.setStrokeWidth(dp(4));
            rect.set(cx - dp(23), cy - dp(33), cx + dp(23), cy + dp(4));
            canvas.drawArc(rect, 200, 140, false, stroke);
        }

        private void drawDragon(Canvas canvas, float cx, float cy) {
            int body = 0xFF74D8A4;
            paint.setColor(0xFFFFD76A);
            path.reset();
            path.moveTo(cx - dp(13), cy - dp(24));
            path.lineTo(cx - dp(5), cy - dp(40));
            path.lineTo(cx + dp(1), cy - dp(23));
            path.lineTo(cx + dp(13), cy - dp(24));
            path.lineTo(cx + dp(5), cy - dp(40));
            path.lineTo(cx - dp(1), cy - dp(23));
            path.close();
            canvas.drawPath(path, paint);
            drawFace(canvas, cx, cy, dp(9), body);
            paint.setColor(0xFFFF4B4B);
            rect.set(cx - dp(20), cy + dp(18), cx + dp(20), cy + dp(27));
            canvas.drawRoundRect(rect, dp(5), dp(5), paint);
        }

        private void drawCompletionSparkles(Canvas canvas, float w, float h) {
            drawSparkle(canvas, w * 0.22f, h * 0.24f, dp(4), 0xFFFFC800);
            drawSparkle(canvas, w * 0.78f, h * 0.31f, dp(5), 0xFFFFE08A);
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
            addView(circle, new LayoutParams(dp(102), dp(102), Gravity.TOP | Gravity.CENTER_HORIZONTAL));

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

            title = text("", 13, COLOR_TEXT, true);
            title.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            title.setMaxLines(2);
            title.setLineSpacing(dp(1), 1f);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setVisibility(SHOW_LESSON_TITLE ? VISIBLE : GONE);
            LayoutParams tLp = new LayoutParams(dp(154), dp(38), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            tLp.topMargin = dp(94);
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
            float radius = dp(34);
            float depth = dp(7);
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
            paint.setStrokeWidth(dp(3.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            // Two clean sound-wave rings: easier to read than a single thick arc.
            arc.set(cx - dp(3), cy - dp(13), cx + dp(17), cy + dp(13));
            canvas.drawArc(arc, -52, 104, false, paint);
            arc.set(cx - dp(1), cy - dp(20), cx + dp(27), cy + dp(20));
            canvas.drawArc(arc, -47, 94, false, paint);
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
