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

    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private CourseMapAdapter adapter;
    private TextView pageTitle;
    private TextView refreshButton;

    private LearningPathRepository.Catalog catalog;
    private LearningPathRepository.Course course;
    private Map<String, LearningPathProgressStore.Progress> progress = new HashMap<>();
    private final Map<String, DownloadUiState> downloads = new HashMap<>();
    private final Map<String, LearningPackageDownloader.Subscription> downloadSubscriptions =
            new HashMap<>();
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
        window.setStatusBarColor(LearningUiKit.BG);
        window.setNavigationBarColor(LearningUiKit.BG);
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
        page.setBackgroundColor(LearningUiKit.BG);
        setContentView(page);

        page.addView(createTopBar(), new LinearLayout.LayoutParams(-1, dp(58)));

        recyclerView = new RecyclerView(this);
        recyclerView.setBackgroundColor(LearningUiKit.BG);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, dp(6), 0, dp(70));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setItemAnimator(null);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new CourseMapAdapter();
        recyclerView.setAdapter(adapter);
        page.addView(recyclerView, new LinearLayout.LayoutParams(-1, 0, 1f));
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

        pageTitle = text(getString(R.string.learning_path_title), 18,
                LearningUiKit.TEXT, true);
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
                        recyclerView.post(() -> layoutManager.scrollToPositionWithOffset(
                                position, dp(12)));
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

        void submitMessage(String message) {
            items.clear();
            lessonPositions.clear();
            unitPositions.clear();
            MapItem item = new MapItem();
            item.type = MapItem.MESSAGE;
            item.message = message == null ? "" : message;
            items.add(item);
            notifyDataSetChanged();
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
            label = text("", 15, LearningUiKit.SUBTEXT, false);
            label.setGravity(Gravity.CENTER);
            label.setPadding(dp(22), dp(48), dp(22), dp(48));
            label.setBackground(LearningUiKit.rounded(Color.WHITE, dp(22),
                    LearningUiKit.BORDER, dp(2)));
            LayoutParams lp = new LayoutParams(Math.min(dp(620),
                    getResources().getDisplayMetrics().widthPixels - dp(32)), -2,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addView(label, lp);
        }

        void bind(String value) { label.setText(value); }
    }

    private final class CourseHeaderView extends FrameLayout {
        private final TextView title;
        private final TextView subtitle;
        private final TextView stats;
        private final LearningUiKit.ProgressView progressView;
        private final TextView progressLabel;
        private final LearningUiKit.CategoryArtworkView artwork;
        private final LearningUiKit.ScrimView scrim;
        private final LearningUiKit.CharacterView first;
        private final LearningUiKit.CharacterView second;

        CourseHeaderView(Context context) {
            super(context);
            setPadding(dp(16), dp(8), dp(16), dp(22));

            FrameLayout card = new FrameLayout(context);
            card.setBackground(LearningUiKit.rounded(Color.WHITE, dp(26), 0, 0));
            card.setClipToOutline(true);
            card.setElevation(dp(3));
            LayoutParams cardLp = new LayoutParams(Math.min(dp(680),
                    getResources().getDisplayMetrics().widthPixels - dp(32)), dp(205),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addView(card, cardLp);

            artwork = new LearningUiKit.CategoryArtworkView(
                    context, LearningUiKit.BLUE, 0, "中");
            card.addView(artwork, new LayoutParams(-1, -1));
            scrim = new LearningUiKit.ScrimView(context, LearningUiKit.BLUE);
            card.addView(scrim, new LayoutParams(-1, -1));

            first = new LearningUiKit.CharacterView(context, LearningUiKit.BLUE, 0, "book");
            LayoutParams firstLp = new LayoutParams(dp(112), dp(148), Gravity.END | Gravity.BOTTOM);
            firstLp.setMargins(0, 0, dp(49), dp(1));
            card.addView(first, firstLp);
            second = new LearningUiKit.CharacterView(context, LearningUiKit.PURPLE, 1, "wave");
            LayoutParams secondLp = new LayoutParams(dp(91), dp(124), Gravity.END | Gravity.BOTTOM);
            secondLp.setMargins(0, 0, dp(3), 0);
            card.addView(second, secondLp);

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            body.setPadding(dp(20), dp(16), dp(145), dp(16));
            card.addView(body, new LayoutParams(-1, -1));

            stats = text("", 11, Color.WHITE, true);
            stats.setGravity(Gravity.CENTER);
            stats.setPadding(dp(10), dp(5), dp(10), dp(5));
            stats.setBackground(LearningUiKit.rounded(0x2FFFFFFF, dp(14),
                    0x42FFFFFF, dp(1)));
            body.addView(stats, new LinearLayout.LayoutParams(-2, -2));

            title = text("", 26, Color.WHITE, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.setMargins(0, dp(9), 0, 0);
            body.addView(title, titleLp);

            subtitle = text("", 14, 0xE8FFFFFF, true);
            subtitle.setMaxLines(2);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.setMargins(0, dp(5), 0, 0);
            body.addView(subtitle, subLp);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(24));
            rowLp.setMargins(0, dp(13), 0, 0);
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
            subtitle.setText(value.subtitle);
            stats.setText(getString(R.string.learning_course_stats, value.units.size(), total));
            progressView.setProgress(completed, Math.max(1, total));
            progressLabel.setText(completed + "/" + total);
            first.setStyle(value.accent, 0, "book");
            second.setStyle(LearningUiKit.blend(value.accent, LearningUiKit.PURPLE, 0.38f),
                    1, "wave");
            artwork.setAccent(value.accent);
            scrim.setAccent(value.accent);
        }
    }

    private final class UnitHeaderView extends FrameLayout {
        private final FrameLayout card;
        private final TextView overline;
        private final TextView title;
        private final TextView subtitle;
        private final TextView count;
        private final LearningUiKit.ProgressView progressView;
        private final LearningUiKit.CharacterView character;

        UnitHeaderView(Context context) {
            super(context);
            setPadding(dp(16), dp(9), dp(16), 0);
            card = new FrameLayout(context);
            card.setClipToOutline(true);
            card.setElevation(dp(2));
            LayoutParams cardLp = new LayoutParams(Math.min(dp(680),
                    getResources().getDisplayMetrics().widthPixels - dp(32)), dp(154),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addView(card, cardLp);

            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            body.setPadding(dp(20), dp(15), dp(125), dp(15));
            card.addView(body, new LayoutParams(-1, -1));

            overline = text("", 11, 0xE8FFFFFF, true);
            overline.setLetterSpacing(0.08f);
            body.addView(overline, new LinearLayout.LayoutParams(-1, -2));
            title = text("", 23, Color.WHITE, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.setMargins(0, dp(5), 0, 0);
            body.addView(title, titleLp);
            subtitle = text("", 13, 0xE8FFFFFF, true);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.setMargins(0, dp(4), 0, 0);
            body.addView(subtitle, subLp);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(22));
            rowLp.setMargins(0, dp(10), 0, 0);
            body.addView(row, rowLp);
            progressView = new LearningUiKit.ProgressView(context);
            progressView.setColors(0x46FFFFFF, Color.WHITE);
            row.addView(progressView, new LinearLayout.LayoutParams(0, dp(8), 1f));
            count = text("", 12, Color.WHITE, true);
            count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(54), -1);
            countLp.setMargins(dp(9), 0, 0, 0);
            row.addView(count, countLp);

            character = new LearningUiKit.CharacterView(context);
            LayoutParams characterLp = new LayoutParams(dp(116), dp(145),
                    Gravity.END | Gravity.BOTTOM);
            characterLp.setMargins(0, 0, dp(3), 0);
            card.addView(character, characterLp);
        }

        void bind(LearningPathRepository.Unit unit, int unitIndex) {
            card.setBackground(unitHeaderBackground(unit.accent));
            int completed = 0;
            for (LearningPathRepository.Lesson lesson : unit.lessons) {
                LearningPathProgressStore.Progress value = progress.get(lesson.id);
                if (value != null && value.completed()) completed++;
            }
            overline.setText(getString(R.string.learning_unit_number, unitIndex + 1));
            title.setText(unit.title);
            subtitle.setText(unit.subtitle);
            progressView.setProgress(completed, Math.max(1, unit.lessons.size()));
            count.setText(completed + "/" + unit.lessons.size());
            character.setStyle(unit.accent, characterVariant(unit.character, unitIndex),
                    unitIndex % 2 == 0 ? "wave" : "point");
        }
    }

    private GradientDrawable unitHeaderBackground(int accent) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{LearningUiKit.blend(accent, Color.WHITE, 0.08f),
                        accent, LearningUiKit.darken(accent, 0.76f)});
        drawable.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        return drawable;
    }

    private final class LessonRowView extends FrameLayout {
        private final FrameLayout section;
        private final NodeColumn node;
        private final LearningUiKit.CharacterView character;
        private MapItem boundItem;
        private NodeState boundState = NodeState.LOCKED;

        LessonRowView(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
            setMinimumHeight(dp(174));

            section = new FrameLayout(context);
            section.setClipChildren(false);
            section.setClipToPadding(false);
            LayoutParams sectionLp = new LayoutParams(Math.min(dp(680),
                    getResources().getDisplayMetrics().widthPixels - dp(32)), dp(174),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addView(section, sectionLp);

            character = new LearningUiKit.CharacterView(context);
            character.setVisibility(GONE);
            section.addView(character, new FrameLayout.LayoutParams(
                    dp(92), dp(122), Gravity.BOTTOM | Gravity.START));

            node = new NodeColumn(context);
            FrameLayout.LayoutParams nodeLp = new FrameLayout.LayoutParams(dp(170), dp(174),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            section.addView(node, nodeLp);

            setOnClickListener(v -> {
                if (boundItem == null || boundItem.lesson == null) return;
                throttled(() -> onLessonClick(boundItem.lesson, boundState));
            });
        }

        void bind(MapItem item, NodeState state, LearningPathProgressStore.Progress value,
                  DownloadUiState download) {
            boundItem = item;
            boundState = state;
            int pale = LearningUiKit.blend(item.unit.accent, Color.WHITE, 0.91f);
            GradientDrawable background = LearningUiKit.rounded(pale, 0, 0, 0);
            if (item.lastInUnit) {
                background.setCornerRadii(new float[]{0, 0, 0, 0,
                        dp(24), dp(24), dp(24), dp(24)});
            }
            section.setBackground(background);

            int[] offsets = new int[]{0, 40, 80, 40, 0, -40, -80, -40};
            int offset = dp(offsets[Math.floorMod(item.lessonIndex, offsets.length)]);
            int sectionWidth = Math.min(dp(680),
                    getResources().getDisplayMetrics().widthPixels - dp(32));
            int max = Math.max(0, sectionWidth / 2 - dp(125));
            offset = Math.max(-max, Math.min(max, offset));
            node.setTranslationX(offset);
            node.bind(item.lesson, state, value, download, item.unit.accent);

            boolean showCharacter = "story".equals(item.lesson.type)
                    || "trophy".equals(item.lesson.type)
                    || (item.lessonIndex == 1 && item.unit.lessons.size() >= 5);
            if (showCharacter) {
                character.setVisibility(VISIBLE);
                String pose = "story".equals(item.lesson.type) ? "book"
                        : "trophy".equals(item.lesson.type) ? "trophy" : "wave";
                character.setStyle(item.unit.accent,
                        characterVariant(item.unit.character, item.unitIndex * 2 + item.lessonIndex), pose);
                FrameLayout.LayoutParams characterLp =
                        (FrameLayout.LayoutParams) character.getLayoutParams();
                if (offset >= 0) {
                    characterLp.gravity = Gravity.BOTTOM | Gravity.START;
                    characterLp.setMargins(dp(12), 0, 0, dp(3));
                } else {
                    characterLp.gravity = Gravity.BOTTOM | Gravity.END;
                    characterLp.setMargins(0, 0, dp(12), dp(3));
                }
                character.setLayoutParams(characterLp);
            } else {
                character.setVisibility(GONE);
            }

            String stateText;
            if (state == NodeState.COMPLETED) stateText = getString(R.string.learning_path_completed);
            else if (state == NodeState.CURRENT) stateText = getString(R.string.learning_path_current);
            else if (state == NodeState.DOWNLOADING) {
                stateText = (download == null ? 0 : download.progress) + "%";
            } else if (state == NodeState.AVAILABLE) {
                stateText = getString(R.string.learning_path_available);
            } else stateText = getString(R.string.learning_path_locked);
            setContentDescription(item.lesson.title + ", " + stateText);
            setAlpha(state == NodeState.LOCKED ? 0.88f : 1f);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            node.press();
            return true;
        }
    }

    private final class NodeColumn extends LinearLayout {
        private final LinearLayout bubbleHost;
        private final TextView bubble;
        private final LearningUiKit.TriangleView triangle;
        private final NodeCircle circle;
        private final TextView title;
        private final TextView stars;

        NodeColumn(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setClipChildren(false);
            setClipToPadding(false);

            bubbleHost = new LinearLayout(context);
            bubbleHost.setOrientation(VERTICAL);
            bubbleHost.setGravity(Gravity.CENTER_HORIZONTAL);
            bubble = text(getString(R.string.learning_path_start), 11,
                    LearningUiKit.GREEN, true);
            bubble.setGravity(Gravity.CENTER);
            bubble.setAllCaps(true);
            bubble.setLetterSpacing(0.06f);
            bubble.setBackground(LearningUiKit.rounded(Color.WHITE, dp(12),
                    LearningUiKit.GREEN, dp(2)));
            bubbleHost.addView(bubble, new LinearLayout.LayoutParams(dp(70), dp(27)));
            triangle = new LearningUiKit.TriangleView(context);
            triangle.setColor(LearningUiKit.GREEN);
            bubbleHost.addView(triangle, new LinearLayout.LayoutParams(dp(13), dp(7)));
            addView(bubbleHost, new LinearLayout.LayoutParams(-1, dp(35)));

            circle = new NodeCircle(context);
            addView(circle, new LinearLayout.LayoutParams(dp(104), dp(106)));

            title = text("", 14, LearningUiKit.TEXT, true);
            title.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(3), dp(4), dp(3), 0);
            addView(title, new LinearLayout.LayoutParams(-1, dp(31)));

            stars = text("", 11, LearningUiKit.YELLOW_DARK, true);
            stars.setGravity(Gravity.CENTER);
            addView(stars, new LinearLayout.LayoutParams(-1, dp(12)));
        }

        void bind(LearningPathRepository.Lesson lesson, NodeState state,
                  LearningPathProgressStore.Progress value, DownloadUiState download, int accent) {
            bubbleHost.setVisibility(state == NodeState.CURRENT ? VISIBLE : INVISIBLE);
            bubble.setText(lesson.isRewardNode()
                    ? getString(R.string.learning_path_claim) : getString(R.string.learning_path_start));
            title.setText(lesson.title);
            title.setTextColor(state == NodeState.LOCKED ? 0xFFAAAAAA : LearningUiKit.TEXT);
            int downloadProgress = download == null ? 0 : Math.max(0, download.progress);
            int nodeProgress = state == NodeState.DOWNLOADING ? downloadProgress
                    : value == null ? 0 : Math.max(value.bestScore, value.lastScore);
            circle.bind(lesson.type, state, nodeProgress, accent);
            int starCount = value == null ? 0 : Math.max(0, Math.min(3, value.stars));
            stars.setText(state == NodeState.COMPLETED && starCount > 0
                    ? repeatStar(starCount) : "");
        }

        void press() {
            circle.animate().translationY(dp(4)).scaleX(0.94f).scaleY(0.94f).setDuration(70)
                    .withEndAction(() -> circle.animate().translationY(0).scaleX(1f).scaleY(1f)
                            .setDuration(115).start()).start();
        }
    }

    private final class NodeCircle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private final Path path = new Path();
        private String type = "normal";
        private NodeState state = NodeState.LOCKED;
        private int progressValue;
        private int accent = LearningUiKit.GREEN;

        NodeCircle(Context context) {
            super(context);
        }

        void bind(String type, NodeState state, int progress, int accent) {
            this.type = type == null ? "normal" : type;
            this.state = state;
            this.progressValue = Math.max(0, Math.min(100, progress));
            this.accent = accent == 0 ? LearningUiKit.GREEN : accent;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - dp(1);
            float radius = dp(34);
            int fill = nodeColor(type, accent);
            int bottom = LearningUiKit.darken(fill, 0.76f);
            int symbol = Color.WHITE;

            if (state == NodeState.LOCKED) {
                fill = 0xFFE5E5E5;
                bottom = 0xFFCACACA;
                symbol = 0xFFA8A8A8;
            } else if (state == NodeState.CURRENT) {
                fill = nodeColor(type, accent);
                bottom = LearningUiKit.darken(fill, 0.74f);
            } else if (state == NodeState.DOWNLOADING) {
                fill = LearningUiKit.BLUE;
                bottom = LearningUiKit.BLUE_DARK;
            }

            if (state == NodeState.CURRENT) {
                float ringRadius = dp(47);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(8));
                paint.setColor(0xFFE2E2E2);
                canvas.drawCircle(cx, cy + dp(1), ringRadius, paint);
                if (progressValue > 0) {
                    arc.set(cx - ringRadius, cy + dp(1) - ringRadius,
                            cx + ringRadius, cy + dp(1) + ringRadius);
                    paint.setColor(fill);
                    canvas.drawArc(arc, -90f, Math.max(8f, progressValue * 3.6f), false, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeCap(Paint.Cap.BUTT);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(bottom);
            canvas.drawCircle(cx, cy + dp(7), radius, paint);
            paint.setColor(fill);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setColor(0x25FFFFFF);
            canvas.drawOval(cx - radius * 0.52f, cy - radius * 0.65f,
                    cx + radius * 0.52f, cy - radius * 0.40f, paint);

            if (state == NodeState.DOWNLOADING) {
                drawDownload(canvas, cx, cy, radius, symbol);
            } else if (state == NodeState.LOCKED) {
                drawLock(canvas, cx, cy, symbol);
            } else if (state == NodeState.COMPLETED) {
                drawCheck(canvas, cx, cy, symbol);
            } else if ("practice".equals(type) || "review".equals(type)) {
                drawDumbbell(canvas, cx, cy, symbol);
            } else if ("story".equals(type)) {
                drawBook(canvas, cx, cy, symbol);
            } else if ("checkpoint".equals(type) || "test".equals(type)) {
                drawShield(canvas, cx, cy, symbol);
            } else if ("trophy".equals(type) || "chest".equals(type)) {
                drawTrophy(canvas, cx, cy, symbol);
            } else if ("speaking".equals(type)) {
                drawMic(canvas, cx, cy, symbol);
            } else if ("listening".equals(type)) {
                drawSpeaker(canvas, cx, cy, symbol);
            } else {
                drawStar(canvas, cx, cy, symbol);
            }
        }

        private int nodeColor(String type, int unitAccent) {
            if ("practice".equals(type) || "review".equals(type)) return LearningUiKit.YELLOW;
            if ("story".equals(type)) return LearningUiKit.PURPLE;
            if ("checkpoint".equals(type) || "test".equals(type)) return LearningUiKit.BLUE;
            if ("trophy".equals(type) || "chest".equals(type)) return 0xFFFFA800;
            if ("speaking".equals(type)) return 0xFFFF78C8;
            if ("listening".equals(type)) return 0xFF2B9FF3;
            return unitAccent;
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
            canvas.drawRoundRect(cx - dp(22), cy - dp(13), cx - dp(14), cy + dp(13),
                    dp(3), dp(3), paint);
            canvas.drawRoundRect(cx + dp(14), cy - dp(13), cx + dp(22), cy + dp(13),
                    dp(3), dp(3), paint);
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
            paint.setColor(0x66000000);
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
            canvas.drawRoundRect(cx - dp(15), cy - dp(20), cx + dp(15), cy + dp(5),
                    dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            arc.set(cx - dp(26), cy - dp(17), cx - dp(9), cy + dp(2));
            canvas.drawArc(arc, 75, 210, false, paint);
            arc.set(cx + dp(9), cy - dp(17), cx + dp(26), cy + dp(2));
            canvas.drawArc(arc, -105, 210, false, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(cx - dp(4), cy + dp(3), cx + dp(4), cy + dp(17),
                    dp(2), dp(2), paint);
            canvas.drawRoundRect(cx - dp(14), cy + dp(15), cx + dp(14), cy + dp(21),
                    dp(3), dp(3), paint);
        }

        private void drawMic(Canvas canvas, float cx, float cy, int color) {
            paint.setColor(color);
            canvas.drawRoundRect(cx - dp(8), cy - dp(21), cx + dp(8), cy + dp(6),
                    dp(8), dp(8), paint);
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
            arc.set(cx - radius + dp(7), cy - radius + dp(7),
                    cx + radius - dp(7), cy + radius - dp(7));
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

    private static String repeatStar(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(3, count); i++) builder.append('★');
        return builder.toString();
    }
}
