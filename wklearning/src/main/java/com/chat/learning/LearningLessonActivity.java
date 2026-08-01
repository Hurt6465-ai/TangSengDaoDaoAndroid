package com.chat.learning;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mixed interactive exercise player used by nodes on the learning path. */
public class LearningLessonActivity extends AppCompatActivity {
    private static final String EXTRA_COURSE_ID = "course_id";
    private static final String EXTRA_LESSON_ID = "lesson_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_SUBTITLE = "subtitle";
    private static final String EXTRA_BUNDLED_ASSET = "bundled_asset";
    private static final String EXTRA_FILE_PATH = "file_path";
    private static final String EXTRA_PACKAGE_ROOT = "package_root";
    private static final int REQ_PRONUNCIATION_COMPARE = 4107;

    private static final String STATE_QUEUE = "queue";
    private static final String STATE_INDEX = "index";
    private static final String STATE_FIRST_CORRECT = "first_correct";
    private static final String STATE_FIRST_ANSWERED = "first_answered";
    private static final String STATE_MASTERED = "mastered";
    private static final String STATE_RETRY_IDS = "retry_ids";
    private static final String STATE_RETRY_VALUES = "retry_values";
    private static final String STATE_ATTEMPT_RECORDED = "attempt_recorded";
    private static final String STATE_COMPLETION_SCORE = "completion_score";
    private static final String STATE_COMPLETION_STARS = "completion_stars";
    private static final String STATE_COMPLETION_PASSED = "completion_passed";
    private static final String STATE_CONTENT_HASH = "content_hash";
    private static final String STATE_SELECTED_CHOICE = "selected_choice";
    private static final String STATE_FILL_TEXT = "fill_text";
    private static final String STATE_ORDER_TOKEN_IDS = "order_token_ids";
    private static final String STATE_MATCHED_PAIR_IDS = "matched_pair_ids";
    private static final String STATE_PRONUNCIATION_OPENED = "pronunciation_opened";

    private static final int COLOR_BG = LearningUiKit.SURFACE;
    private static final int COLOR_TEXT = LearningUiKit.TEXT;
    private static final int COLOR_SUB = LearningUiKit.SUBTEXT;
    private static final int COLOR_ACCENT = LearningUiKit.GREEN;
    private static final int COLOR_ACCENT_DARK = LearningUiKit.GREEN_DARK;
    private static final int COLOR_BLUE = LearningUiKit.BLUE;
    private static final int COLOR_BLUE_DARK = LearningUiKit.BLUE_DARK;
    private static final int COLOR_BORDER = LearningUiKit.BORDER;
    private static final int COLOR_SUCCESS = LearningUiKit.GREEN;
    private static final int COLOR_ERROR = LearningUiKit.RED;
    private static final int COLOR_ERROR_DARK = LearningUiKit.RED_DARK;

    private String courseId = "";
    private String lessonId = "";
    private String title = "";
    private String subtitle = "";
    private String bundledAsset = "";
    private String filePath = "";
    private String packageRootPath = "";

    private LearningUiKit.ProgressView progressBar;
    private ScrollView questionScroll;
    private TextView progressText;
    private TextView lessonTitle;
    private LinearLayout questionHost;
    private FrameLayout feedbackHost;
    private LinearLayout feedbackPanel;
    private TextView feedbackTitle;
    private TextView feedbackBody;
    private TextView actionButton;

    private LearningLessonRepository.LessonData lessonData;
    private final ArrayList<LearningLessonRepository.Exercise> originalExercises = new ArrayList<>();
    private final ArrayList<LearningLessonRepository.Exercise> queue = new ArrayList<>();
    private final Map<String, Integer> retryCount = new HashMap<>();
    private final Set<String> firstAttemptAnswered = new HashSet<>();
    private final Set<String> masteredExercises = new HashSet<>();
    private int currentIndex;
    private int firstAttemptCorrect;
    private boolean answered;
    private boolean pronunciationOpened;
    private String selectedChoice = "";
    private final Map<String, View> choiceViews = new HashMap<>();
    private EditText fillInput;
    private final ArrayList<WordToken> orderedTokens = new ArrayList<>();
    private final Map<Integer, TextView> orderChips = new HashMap<>();
    private LinearLayout orderAnswerRow;
    private LinearLayout orderBankRow;
    private final Set<Integer> matchedPairIndexes = new HashSet<>();
    private final Map<Integer, TextView> matchLeftViews = new HashMap<>();
    private final Map<Integer, TextView> matchRightViews = new HashMap<>();
    private TextView selectedMatchLeft;
    private LearningLessonRepository.PairItem selectedMatchPair;
    private MediaPlayer mediaPlayer;
    private LearningPathProgressStore progressStore;
    private Bundle restoreState;
    private int loadGeneration;
    private int questionGeneration;
    private Runnable autoPlayRunnable;
    private boolean destroyed;
    private boolean attemptRecorded;
    private int completionScore;
    private int completionStars;
    private boolean completionPassed;

    public static void open(Context context, LearningPathRepository.Lesson lesson) {
        if (context == null || lesson == null) return;
        Intent intent = new Intent(context, LearningLessonActivity.class);
        intent.putExtra(EXTRA_COURSE_ID, lesson.courseId);
        intent.putExtra(EXTRA_LESSON_ID, lesson.id);
        intent.putExtra(EXTRA_TITLE, lesson.title);
        intent.putExtra(EXTRA_SUBTITLE, lesson.subtitle);
        intent.putExtra(EXTRA_BUNDLED_ASSET, lesson.bundledLessonAsset);
        File installed = LearningPackageDownloader.installedLessonFile(context, lesson);
        File packageRoot = LearningPackageDownloader.installedPackageDirectory(context, lesson);
        if (installed != null) intent.putExtra(EXTRA_FILE_PATH, installed.getAbsolutePath());
        if (packageRoot != null) intent.putExtra(EXTRA_PACKAGE_ROOT, packageRoot.getAbsolutePath());
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);

        courseId = safe(getIntent().getStringExtra(EXTRA_COURSE_ID));
        lessonId = safe(getIntent().getStringExtra(EXTRA_LESSON_ID));
        title = safe(getIntent().getStringExtra(EXTRA_TITLE));
        subtitle = safe(getIntent().getStringExtra(EXTRA_SUBTITLE));
        bundledAsset = safe(getIntent().getStringExtra(EXTRA_BUNDLED_ASSET));
        filePath = safe(getIntent().getStringExtra(EXTRA_FILE_PATH));
        packageRootPath = safe(getIntent().getStringExtra(EXTRA_PACKAGE_ROOT));
        restoreState = savedInstanceState;
        progressStore = new LearningPathProgressStore(this);
        buildLayout();
        loadLesson();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PRONUNCIATION_COMPARE) {
            pronunciationOpened = pronunciationOpened || resultCode == RESULT_OK;
            if (resultCode == RESULT_OK) {
                setActionEnabled(true);
                Toast.makeText(this, R.string.learning_lesson_record_done,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> queueIds = new ArrayList<>();
        for (LearningLessonRepository.Exercise exercise : queue) queueIds.add(exercise.id);
        outState.putStringArrayList(STATE_QUEUE, queueIds);
        outState.putInt(STATE_INDEX, answered ? currentIndex + 1 : currentIndex);
        outState.putInt(STATE_FIRST_CORRECT, firstAttemptCorrect);
        outState.putStringArrayList(STATE_FIRST_ANSWERED, new ArrayList<>(firstAttemptAnswered));
        outState.putStringArrayList(STATE_MASTERED, new ArrayList<>(masteredExercises));
        ArrayList<String> retryIds = new ArrayList<>();
        int[] retryValues = new int[retryCount.size()];
        int position = 0;
        for (Map.Entry<String, Integer> entry : retryCount.entrySet()) {
            retryIds.add(entry.getKey());
            retryValues[position++] = entry.getValue() == null ? 0 : entry.getValue();
        }
        outState.putStringArrayList(STATE_RETRY_IDS, retryIds);
        outState.putIntArray(STATE_RETRY_VALUES, retryValues);
        outState.putBoolean(STATE_ATTEMPT_RECORDED, attemptRecorded);
        outState.putInt(STATE_COMPLETION_SCORE, completionScore);
        outState.putInt(STATE_COMPLETION_STARS, completionStars);
        outState.putBoolean(STATE_COMPLETION_PASSED, completionPassed);
        if (!answered && currentIndex < queue.size()) {
            outState.putString(STATE_SELECTED_CHOICE, selectedChoice);
            if (fillInput != null) outState.putString(STATE_FILL_TEXT,
                    fillInput.getText().toString());
            int[] orderedIds = new int[orderedTokens.size()];
            for (int i = 0; i < orderedTokens.size(); i++) orderedIds[i] = orderedTokens.get(i).id;
            outState.putIntArray(STATE_ORDER_TOKEN_IDS, orderedIds);
            int[] matchedIds = new int[matchedPairIndexes.size()];
            int matchedPosition = 0;
            for (Integer value : matchedPairIndexes) {
                matchedIds[matchedPosition++] = value == null ? -1 : value;
            }
            outState.putIntArray(STATE_MATCHED_PAIR_IDS, matchedIds);
            outState.putBoolean(STATE_PRONUNCIATION_OPENED, pronunciationOpened);
        }
        if (lessonData != null) outState.putString(STATE_CONTENT_HASH, lessonData.contentHash);
    }

    @Override
    protected void onStop() {
        cancelAutoPlay();
        releasePlayer();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        loadGeneration++;
        questionGeneration++;
        cancelAutoPlay();
        releasePlayer();
        if (progressStore != null) progressStore.close();
        progressStore = null;
        super.onDestroy();
    }

    private void buildLayout() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(COLOR_BG);
        setContentView(page);

        page.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(72)));

        questionScroll = new ScrollView(this);
        questionScroll.setFillViewport(true);
        questionScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        questionScroll.setVerticalScrollBarEnabled(false);
        questionScroll.setClipToPadding(false);
        page.addView(questionScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout widthHost = new FrameLayout(this);
        widthHost.setPadding(dp(18), dp(10), dp(18), dp(30));
        widthHost.setMinimumHeight(Math.max(dp(420),
                getResources().getDisplayMetrics().heightPixels - dp(230)));
        questionScroll.addView(widthHost, new ScrollView.LayoutParams(-1, -2));

        questionHost = new LinearLayout(this);
        questionHost.setOrientation(LinearLayout.VERTICAL);
        questionHost.setClipChildren(false);
        questionHost.setClipToPadding(false);
        int available = Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(36));
        FrameLayout.LayoutParams questionLp = new FrameLayout.LayoutParams(
                Math.min(dp(620), available), -2, Gravity.CENTER);
        widthHost.addView(questionHost, questionLp);

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        page.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        feedbackHost = new FrameLayout(this);
        feedbackHost.setBackgroundColor(Color.WHITE);
        page.addView(feedbackHost, new LinearLayout.LayoutParams(-1, -2));

        feedbackPanel = new LinearLayout(this);
        feedbackPanel.setOrientation(LinearLayout.VERTICAL);
        feedbackPanel.setPadding(dp(18), dp(13), dp(18), dp(14));
        feedbackPanel.setBackgroundColor(Color.WHITE);
        int footerAvailable = Math.max(dp(280),
                getResources().getDisplayMetrics().widthPixels - dp(24));
        FrameLayout.LayoutParams footerLp = new FrameLayout.LayoutParams(
                Math.min(dp(720), footerAvailable), -2, Gravity.CENTER_HORIZONTAL);
        feedbackHost.addView(feedbackPanel, footerLp);

        feedbackTitle = text("", 20, COLOR_TEXT, true);
        feedbackTitle.setVisibility(View.GONE);
        feedbackPanel.addView(feedbackTitle, new LinearLayout.LayoutParams(-1, -2));

        feedbackBody = text("", 14, COLOR_SUB, false);
        feedbackBody.setLineSpacing(dp(3), 1f);
        feedbackBody.setVisibility(View.GONE);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.setMargins(0, dp(5), 0, dp(12));
        feedbackPanel.addView(feedbackBody, bodyLp);

        actionButton = text(getString(R.string.learning_lesson_check), 17, Color.WHITE, true);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setAllCaps(false);
        actionButton.setBackground(LearningUiKit.raisedSelector(COLOR_ACCENT,
                COLOR_ACCENT_DARK, dp(16), 0, 0, dp(5)));
        actionButton.setOnClickListener(v -> onAction());
        feedbackPanel.addView(actionButton, new LinearLayout.LayoutParams(-1, dp(58)));
        setActionEnabled(false);
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(14), dp(4));

        TextView close = text("×", 29, COLOR_SUB, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(getString(R.string.learning_lesson_close));
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(dp(46), dp(50)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, -1, 1f);
        centerLp.setMargins(dp(5), 0, dp(10), 0);
        bar.addView(center, centerLp);

        lessonTitle = text(title.isEmpty() ? getString(R.string.learning_lesson_title) : title,
                14, COLOR_SUB, true);
        lessonTitle.setSingleLine(true);
        lessonTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        center.addView(lessonTitle, new LinearLayout.LayoutParams(-1, dp(24)));

        progressBar = new LearningUiKit.ProgressView(this);
        progressBar.setColors(COLOR_BORDER, COLOR_ACCENT);
        progressBar.setProgress(0, 1000);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(12));
        progressLp.setMargins(0, dp(4), 0, 0);
        center.addView(progressBar, progressLp);

        progressText = text("0 / 0", 12, COLOR_SUB, true);
        progressText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bar.addView(progressText, new LinearLayout.LayoutParams(dp(58), -1));
        return bar;
    }

    private void loadLesson() {
        int generation = ++loadGeneration;
        setActionEnabled(false);
        progressText.setText("…");
        questionHost.removeAllViews();
        TextView loading = text(getString(R.string.learning_path_loading), 15, COLOR_SUB, false);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(44), 0, dp(44));
        questionHost.addView(loading, new LinearLayout.LayoutParams(-1, -2));

        LearningRemoteContent.execute(() -> {
            try {
                LearningLessonRepository.LessonData loaded = LearningLessonRepository.load(
                        getApplicationContext(), lessonId, bundledAsset, filePath);
                runOnUiThread(() -> {
                    if (!canApply(generation)) return;
                    applyLesson(loaded);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    if (!canApply(generation)) return;
                    showLoadError(error.getMessage());
                });
            }
        });
    }

    private boolean canApply(int generation) {
        return !destroyed && generation == loadGeneration && !isFinishing()
                && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private void applyLesson(LearningLessonRepository.LessonData loaded) {
        lessonData = loaded;
        if (!loaded.installedSource) {
            filePath = "";
            packageRootPath = "";
        }
        originalExercises.clear();
        originalExercises.addAll(loaded.exercises);
        if (!loaded.title.isEmpty()) title = loaded.title;
        if (!loaded.subtitle.isEmpty()) subtitle = loaded.subtitle;
        lessonTitle.setText(title.isEmpty() ? getString(R.string.learning_lesson_title) : title);
        setActionEnabled(true);
        restoreOrStartSession();
    }

    private void restoreOrStartSession() {
        resetSession();
        Bundle state = restoreState;
        restoreState = null;
        if (state == null || lessonData == null
                || !lessonData.contentHash.equals(state.getString(STATE_CONTENT_HASH, ""))) {
            queue.addAll(originalExercises);
            showCurrentQuestion();
            return;
        }

        Map<String, LearningLessonRepository.Exercise> byId = new HashMap<>();
        for (LearningLessonRepository.Exercise exercise : originalExercises) byId.put(exercise.id, exercise);
        ArrayList<String> savedQueue = state.getStringArrayList(STATE_QUEUE);
        if (savedQueue != null) {
            for (String id : savedQueue) {
                LearningLessonRepository.Exercise exercise = byId.get(id);
                if (exercise != null) queue.add(exercise);
            }
        }
        if (queue.isEmpty()) queue.addAll(originalExercises);
        currentIndex = clamp(state.getInt(STATE_INDEX, 0), 0, queue.size());
        addValidIds(firstAttemptAnswered, state.getStringArrayList(STATE_FIRST_ANSWERED), byId);
        addValidIds(masteredExercises, state.getStringArrayList(STATE_MASTERED), byId);
        firstAttemptCorrect = clamp(state.getInt(STATE_FIRST_CORRECT, 0), 0,
                Math.min(originalExercises.size(), firstAttemptAnswered.size()));
        ArrayList<String> retryIds = state.getStringArrayList(STATE_RETRY_IDS);
        int[] retryValues = state.getIntArray(STATE_RETRY_VALUES);
        if (retryIds != null && retryValues != null) {
            for (int i = 0; i < Math.min(retryIds.size(), retryValues.length); i++) {
                if (byId.containsKey(retryIds.get(i))) {
                    retryCount.put(retryIds.get(i), Math.max(0, retryValues[i]));
                }
            }
        }
        attemptRecorded = state.getBoolean(STATE_ATTEMPT_RECORDED, false);
        completionScore = state.getInt(STATE_COMPLETION_SCORE, 0);
        completionStars = state.getInt(STATE_COMPLETION_STARS, 0);
        completionPassed = state.getBoolean(STATE_COMPLETION_PASSED, false);
        showCurrentQuestion();
        restoreQuestionInput(state);
    }

    private void restoreQuestionInput(Bundle state) {
        if (state == null || answered || currentIndex >= queue.size()) return;
        String savedChoice = safe(state.getString(STATE_SELECTED_CHOICE, ""));
        if (!savedChoice.isEmpty()) {
            selectedChoice = savedChoice;
            View selected = choiceViews.get(LearningLessonRepository.normalize(savedChoice));
            for (View child : choiceViews.values()) {
                boolean active = child == selected;
                child.setSelected(active);
                setChoiceVisual(child, active ? ChoiceCard.STATE_SELECTED : ChoiceCard.STATE_NORMAL);
            }
        }
        if (fillInput != null) {
            String text = state.getString(STATE_FILL_TEXT, "");
            fillInput.setText(text == null ? "" : text);
            fillInput.setSelection(fillInput.length());
        }
        int[] orderedIds = state.getIntArray(STATE_ORDER_TOKEN_IDS);
        if (orderedIds != null && orderAnswerRow != null && orderBankRow != null) {
            for (int id : orderedIds) {
                TextView chip = orderChips.get(id);
                if (chip == null || !(chip.getTag() instanceof WordToken)) continue;
                WordToken token = (WordToken) chip.getTag();
                if (chip.getParent() instanceof ViewGroup) {
                    ((ViewGroup) chip.getParent()).removeView(chip);
                }
                if (!orderedTokens.contains(token)) orderedTokens.add(token);
                orderAnswerRow.addView(chip, chipLayoutParams());
            }
        }
        int[] matchedIds = state.getIntArray(STATE_MATCHED_PAIR_IDS);
        if (matchedIds != null) {
            for (int id : matchedIds) restoreMatchedPair(id);
        }
        pronunciationOpened = state.getBoolean(STATE_PRONUNCIATION_OPENED, false);
        updateActionAvailability();
    }

    private void resetSession() {
        queue.clear();
        retryCount.clear();
        firstAttemptAnswered.clear();
        masteredExercises.clear();
        currentIndex = 0;
        firstAttemptCorrect = 0;
        attemptRecorded = false;
        completionScore = 0;
        completionStars = 0;
        completionPassed = false;
    }

    private void showCurrentQuestion() {
        int generation = ++questionGeneration;
        cancelAutoPlay();
        releasePlayer();
        hideKeyboard();
        resetFooter();
        answered = false;
        pronunciationOpened = false;
        selectedChoice = "";
        choiceViews.clear();
        fillInput = null;
        orderedTokens.clear();
        orderChips.clear();
        matchedPairIndexes.clear();
        matchLeftViews.clear();
        matchRightViews.clear();
        selectedMatchLeft = null;
        selectedMatchPair = null;
        questionHost.removeAllViews();
        if (questionScroll != null) questionScroll.scrollTo(0, 0);

        if (currentIndex >= queue.size()) {
            showCompletion();
            return;
        }
        LearningLessonRepository.Exercise exercise = queue.get(currentIndex);
        int displayPosition = currentIndex + 1;
        progressText.setText(displayPosition + " / " + queue.size());
        progressBar.setProgress(currentIndex, Math.max(1, queue.size()));

        TextView typeBadge = text(typeLabel(exercise.type), 12, COLOR_BLUE, true);
        typeBadge.setGravity(Gravity.CENTER);
        typeBadge.setAllCaps(true);
        typeBadge.setLetterSpacing(0.06f);
        typeBadge.setPadding(dp(10), dp(5), dp(10), dp(5));
        typeBadge.setBackground(rounded(LearningUiKit.BLUE_SOFT, dp(12), 0xFF84D8FF, dp(1)));
        questionHost.addView(typeBadge, new LinearLayout.LayoutParams(-2, -2));

        TextView question = text(exercise.question, 27, COLOR_TEXT, true);
        question.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams questionLp = new LinearLayout.LayoutParams(-1, -2);
        questionLp.setMargins(0, dp(18), 0, exercise.hint.isEmpty() ? dp(25) : dp(9));
        questionHost.addView(question, questionLp);

        if (!exercise.hint.isEmpty()) {
            TextView hint = text(exercise.hint, 15, COLOR_SUB, false);
            hint.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
            hintLp.setMargins(0, 0, 0, dp(25));
            questionHost.addView(hint, hintLp);
        }

        switch (exercise.type) {
            case "listen_choice":
                addCenteredAudio(exercise);
                addVerticalSpace(questionHost, 20);
                renderChoice(exercise);
                autoPlayRunnable = () -> {
                    if (generation == questionGeneration && currentIndex < queue.size()
                            && queue.get(currentIndex).id.equals(exercise.id)) {
                        playExerciseAudio(exercise);
                    }
                };
                questionHost.postDelayed(autoPlayRunnable, 260L);
                break;
            case "true_false":
            case "single_choice":
            case "image_choice":
                if (!exercise.audio.isEmpty() || !exercise.audioText.isEmpty()) {
                    addCenteredAudio(exercise);
                    addVerticalSpace(questionHost, 18);
                }
                renderChoice(exercise);
                break;
            case "word_order":
                renderWordOrder(exercise);
                break;
            case "fill_blank":
            case "dictation":
                if ("dictation".equals(exercise.type)) {
                    addCenteredAudio(exercise);
                    addVerticalSpace(questionHost, 20);
                }
                renderFillBlank(exercise);
                break;
            case "matching":
                renderMatching(exercise);
                break;
            case "pronunciation":
                renderPronunciation(exercise);
                break;
            default:
                showLoadError(getString(R.string.learning_lesson_unsupported_type));
                break;
        }
        updateActionAvailability();
    }

    private void renderChoice(LearningLessonRepository.Exercise exercise) {
        boolean imageGrid = "image_choice".equals(exercise.type);
        boolean compactGrid = imageGrid || "true_false".equals(exercise.type);
        ViewGroup options;
        View optionsRoot;
        if (compactGrid) {
            FrameLayout gridHost = new FrameLayout(this);
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(2);
            grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            grid.setUseDefaultMargins(false);
            FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL);
            gridHost.addView(grid, gridLp);
            options = grid;
            optionsRoot = gridHost;
        } else {
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            options = list;
            optionsRoot = list;
        }
        questionHost.addView(optionsRoot, new LinearLayout.LayoutParams(-1, -2));

        ArrayList<View> optionViews = new ArrayList<>();
        ArrayList<LearningLessonRepository.ChoiceOption> choices =
                new ArrayList<>(exercise.options);
        if (!exercise.keepOrder && !"true_false".equals(exercise.type)) {
            Collections.shuffle(choices);
        }
        int contentWidth = Math.min(dp(620), getResources().getDisplayMetrics().widthPixels - dp(36));
        int gridWidth = Math.min(dp(250), Math.max(dp(132), (contentWidth - dp(12)) / 2));
        for (int index = 0; index < choices.size(); index++) {
            LearningLessonRepository.ChoiceOption choice = choices.get(index);
            ChoiceCard option = createChoiceView(exercise, choice, index + 1);
            optionViews.add(option);
            choiceViews.put(LearningLessonRepository.normalize(choice.value), option);
            option.setOnClickListener(v -> {
                if (answered) return;
                selectedChoice = choice.value;
                for (View child : optionViews) {
                    setChoiceVisual(child, child == option
                            ? ChoiceCard.STATE_SELECTED : ChoiceCard.STATE_NORMAL);
                }
                setActionEnabled(true);
                option.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            });
            if (compactGrid) {
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = gridWidth;
                lp.height = imageGrid ? dp(196) : dp(68);
                lp.setMargins(index % 2 == 0 ? 0 : dp(6), 0,
                        index % 2 == 0 ? dp(6) : 0, dp(12));
                options.addView(option, lp);
            } else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68));
                lp.setMargins(0, 0, 0, dp(12));
                options.addView(option, lp);
            }
        }
    }

    private ChoiceCard createChoiceView(LearningLessonRepository.Exercise exercise,
                                      LearningLessonRepository.ChoiceOption choice,
                                      int shortcut) {
        ChoiceCard card = new ChoiceCard(this, choice.text, shortcut, !choice.image.isEmpty());
        card.setContentDescription(choice.contentDescription);
        if (!choice.image.isEmpty()) {
            loadChoiceImage(card.image, choice.image, questionGeneration);
        }
        return card;
    }

    private void loadChoiceImage(ImageView view, String relative, int generation) {
        view.setTag(relative);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.min(dp(420), Math.max(dp(220), screenWidth / 2));
        int targetHeight = dp(220);
        LearningRemoteContent.execute(() -> {
            Bitmap bitmap = null;
            try { bitmap = decodeLessonBitmap(relative, targetWidth, targetHeight); }
            catch (Throwable ignored) { }
            Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (destroyed || generation != questionGeneration
                        || !relative.equals(view.getTag())) {
                    if (result != null) result.recycle();
                    return;
                }
                if (result == null) {
                    view.setVisibility(View.GONE);
                } else {
                    view.setImageBitmap(result);
                    view.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private Bitmap decodeLessonBitmap(String relative, int maxWidth, int maxHeight) throws Exception {
        String clean = LearningLessonRepository.cleanRelative(relative, true);
        if (clean.isEmpty()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        if (!filePath.isEmpty()) {
            File image = resolveInstalledMedia(clean);
            BitmapFactory.decodeFile(image.getAbsolutePath(), bounds);
            BitmapFactory.Options options = sampledOptions(bounds, maxWidth, maxHeight);
            return BitmapFactory.decodeFile(image.getAbsolutePath(), options);
        }
        String asset = resolveBundledAudioAsset(clean);
        try (InputStream input = getAssets().open(asset)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        BitmapFactory.Options options = sampledOptions(bounds, maxWidth, maxHeight);
        try (InputStream input = getAssets().open(asset)) {
            return BitmapFactory.decodeStream(input, null, options);
        }
    }

    private BitmapFactory.Options sampledOptions(BitmapFactory.Options bounds, int maxWidth,
                                                 int maxHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        int width = Math.max(1, bounds.outWidth);
        int height = Math.max(1, bounds.outHeight);
        int sample = 1;
        while (width / sample > maxWidth * 2 || height / sample > maxHeight * 2) sample *= 2;
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    private void renderFillBlank(LearningLessonRepository.Exercise exercise) {
        fillInput = new EditText(this);
        fillInput.setTextSize(19);
        fillInput.setTextColor(COLOR_TEXT);
        fillInput.setHintTextColor(0xFFADB5C4);
        fillInput.setHint(exercise.placeholder.isEmpty()
                ? getString(R.string.learning_lesson_input_hint) : exercise.placeholder);
        fillInput.setSingleLine(true);
        fillInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        fillInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        fillInput.setPadding(dp(18), 0, dp(18), dp(4));
        fillInput.setBackground(LearningUiKit.raisedSelector(Color.WHITE, 0xFFD5D5D5,
                dp(16), COLOR_BORDER, dp(2), dp(4)));
        fillInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!answered) setActionEnabled(s != null && s.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        questionHost.addView(fillInput, new LinearLayout.LayoutParams(-1, dp(68)));
    }

    private void renderWordOrder(LearningLessonRepository.Exercise exercise) {
        TextView answerLabel = text(getString(R.string.learning_lesson_your_answer), 12,
                COLOR_SUB, true);
        questionHost.addView(answerLabel, new LinearLayout.LayoutParams(-1, -2));

        orderAnswerRow = wrapRow();
        orderAnswerRow.setMinimumHeight(dp(64));
        orderAnswerRow.setPadding(dp(8), dp(8), dp(8), dp(8));
        orderAnswerRow.setBackground(raised(0xFFF7F7F7, 0xFFDADADA,
                dp(16), COLOR_BORDER, dp(2), dp(4)));
        LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(-1, -2);
        answerLp.setMargins(0, dp(7), 0, dp(18));
        questionHost.addView(orderAnswerRow, answerLp);

        TextView bankLabel = text(getString(R.string.learning_lesson_word_bank), 12,
                COLOR_SUB, true);
        questionHost.addView(bankLabel, new LinearLayout.LayoutParams(-1, -2));
        orderBankRow = wrapRow();
        LinearLayout.LayoutParams bankLp = new LinearLayout.LayoutParams(-1, -2);
        bankLp.setMargins(0, dp(7), 0, 0);
        questionHost.addView(orderBankRow, bankLp);

        List<WordToken> tokens = new ArrayList<>();
        for (int i = 0; i < exercise.words.size(); i++) {
            tokens.add(new WordToken(i, exercise.words.get(i)));
        }
        if (!exercise.keepOrder) shuffleWordTokens(tokens);
        for (WordToken token : tokens) addOrderChip(token, orderBankRow, true);
    }

    private void shuffleWordTokens(List<WordToken> tokens) {
        if (tokens == null || tokens.size() < 2) return;
        boolean hasDifferentValues = false;
        String first = LearningLessonRepository.normalize(tokens.get(0).value);
        for (int i = 1; i < tokens.size(); i++) {
            if (!first.equals(LearningLessonRepository.normalize(tokens.get(i).value))) {
                hasDifferentValues = true;
                break;
            }
        }
        if (!hasDifferentValues) return;
        for (int attempt = 0; attempt < 6; attempt++) {
            Collections.shuffle(tokens);
            boolean originalOrder = true;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).id != i) {
                    originalOrder = false;
                    break;
                }
            }
            if (!originalOrder) return;
        }
        Collections.rotate(tokens, 1);
    }

    private void addOrderChip(WordToken token, LinearLayout parent, boolean inBank) {
        TextView chip = text(token.value, 17, COLOR_TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(15), dp(8), dp(15), dp(8));
        chip.setBackground(LearningUiKit.raisedSelector(Color.WHITE, 0xFFD5D5D5,
                dp(13), COLOR_BORDER, dp(2), dp(4)));
        chip.setTag(token);
        orderChips.put(token.id, chip);
        chip.setOnClickListener(v -> {
            if (answered) return;
            ViewGroup currentParent = (ViewGroup) chip.getParent();
            if (currentParent != null) currentParent.removeView(chip);
            if (currentParent == orderBankRow) {
                orderedTokens.add(token);
                orderAnswerRow.addView(chip, chipLayoutParams());
            } else {
                orderedTokens.remove(token);
                orderBankRow.addView(chip, chipLayoutParams());
            }
            setActionEnabled(!orderedTokens.isEmpty());
            chip.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        });
        parent.addView(chip, chipLayoutParams());
    }

    private void renderMatching(LearningLessonRepository.Exercise exercise) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        questionHost.addView(row, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        addHorizontalSpace(row, 10);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));

        List<LearningLessonRepository.PairItem> rightItems = new ArrayList<>(exercise.pairs);
        Collections.shuffle(rightItems);
        for (LearningLessonRepository.PairItem pair : exercise.pairs) {
            TextView view = matchButton(pair.left);
            matchLeftViews.put(pair.index, view);
            view.setOnClickListener(v -> {
                if (answered || matchedPairIndexes.contains(pair.index)) return;
                if (selectedMatchLeft != null) {
                    selectedMatchLeft.setBackground(LearningUiKit.raisedSelector(Color.WHITE,
                            0xFFD5D5D5, dp(14), COLOR_BORDER, dp(2), dp(4)));
                }
                selectedMatchLeft = view;
                selectedMatchPair = pair;
                view.setBackground(LearningUiKit.raisedSelector(LearningUiKit.BLUE_SOFT,
                        0xFF84D8FF, dp(14), COLOR_BLUE, dp(2), dp(4)));
            });
            left.addView(view, matchLp());
        }
        for (LearningLessonRepository.PairItem rightPair : rightItems) {
            TextView view = matchButton(rightPair.right);
            matchRightViews.put(rightPair.index, view);
            view.setOnClickListener(v -> {
                if (answered || selectedMatchPair == null || !view.isEnabled()) return;
                if (selectedMatchPair.index == rightPair.index) {
                    matchedPairIndexes.add(selectedMatchPair.index);
                    markPairMatched(selectedMatchLeft, view);
                    selectedMatchLeft = null;
                    selectedMatchPair = null;
                    setActionEnabled(matchedPairIndexes.size() >= exercise.pairs.size());
                    v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                } else {
                    view.setBackground(raised(0xFFFFDFE0, 0xFFF1A8AC,
                            dp(14), COLOR_ERROR, dp(2), dp(4)));
                    view.postDelayed(() -> {
                        if (view.isEnabled()) view.setBackground(raised(Color.WHITE, 0xFFD5D5D5,
                                dp(14), COLOR_BORDER, dp(2), dp(4)));
                    }, 350L);
                    v.performHapticFeedback(HapticFeedbackConstants.REJECT);
                }
            });
            right.addView(view, matchLp());
        }
    }

    private void restoreMatchedPair(int index) {
        TextView left = matchLeftViews.get(index);
        TextView right = matchRightViews.get(index);
        if (left == null || right == null) return;
        matchedPairIndexes.add(index);
        markPairMatched(left, right);
    }

    private void markPairMatched(TextView left, TextView right) {
        if (left != null) {
            left.setEnabled(false);
            left.setTextColor(COLOR_SUCCESS);
            left.setBackground(raised(0xFFD7FFB8, 0xFF9DD86B,
                    dp(14), COLOR_SUCCESS, dp(2), dp(4)));
        }
        if (right != null) {
            right.setEnabled(false);
            right.setTextColor(COLOR_SUCCESS);
            right.setBackground(raised(0xFFD7FFB8, 0xFF9DD86B,
                    dp(14), COLOR_SUCCESS, dp(2), dp(4)));
        }
    }

    private void renderPronunciation(LearningLessonRepository.Exercise exercise) {
        TextView target = text(!exercise.text.isEmpty() ? exercise.text : exercise.answer,
                36, COLOR_TEXT, true);
        target.setGravity(Gravity.CENTER);
        questionHost.addView(target, new LinearLayout.LayoutParams(-1, -2));

        if (!exercise.pinyin.isEmpty()) {
            TextView pinyin = text(exercise.pinyin, 18, COLOR_ACCENT, true);
            pinyin.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pinyinLp = new LinearLayout.LayoutParams(-1, -2);
            pinyinLp.setMargins(0, dp(8), 0, dp(20));
            questionHost.addView(pinyin, pinyinLp);
        } else {
            addVerticalSpace(questionHost, 18);
        }

        TextView original = text(getString(R.string.learning_lesson_play_original), 15,
                COLOR_ACCENT, true);
        original.setGravity(Gravity.CENTER);
        original.setTextColor(COLOR_BLUE);
        original.setBackground(raised(0xFFDDF4FF, 0xFF84D8FF,
                dp(16), COLOR_BLUE, dp(2), dp(4)));
        original.setOnClickListener(v -> playExerciseAudio(exercise));
        questionHost.addView(original, new LinearLayout.LayoutParams(-1, dp(52)));
        addVerticalSpace(questionHost, 10);

        TextView record = text(getString(R.string.learning_lesson_record_compare), 16,
                Color.WHITE, true);
        record.setGravity(Gravity.CENTER);
        record.setBackground(raised(COLOR_ACCENT, COLOR_ACCENT_DARK,
                dp(16), 0, 0, dp(5)));
        record.setOnClickListener(v -> openPronunciationComparison(exercise));
        questionHost.addView(record, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView hint = text(getString(R.string.learning_lesson_pronunciation_hint), 13,
                COLOR_SUB, false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(12), 0, 0);
        questionHost.addView(hint, hintLp);
    }

    private void openPronunciationComparison(LearningLessonRepository.Exercise exercise) {
        Intent intent = new Intent(this, WordPronunciationActivity.class);
        intent.putExtra(WordPronunciationActivity.EXTRA_WORD,
                !exercise.text.isEmpty() ? exercise.text : exercise.answer);
        intent.putExtra(WordPronunciationActivity.EXTRA_PINYIN, exercise.pinyin);
        if (!filePath.isEmpty() && !exercise.audio.isEmpty()) {
            try {
                File audioFile = resolveInstalledMedia(exercise.audio);
                if (audioFile.isFile()) {
                    intent.putExtra(WordPronunciationActivity.EXTRA_STANDARD_AUDIO_FILE,
                            audioFile.getAbsolutePath());
                }
            } catch (Throwable ignored) { }
        } else {
            String audioAsset = resolveBundledAudioAsset(exercise.audio);
            if (!audioAsset.isEmpty()) {
                intent.putExtra(WordPronunciationActivity.EXTRA_STANDARD_AUDIO_ASSET, audioAsset);
            }
        }
        intent.putExtra(WordPronunciationActivity.EXTRA_STANDARD_AUDIO_SPEED, 0.5f);
        intent.putExtra(WordPronunciationActivity.EXTRA_COMPARISON_ONLY, true);
        intent.putExtra(WordPronunciationActivity.EXTRA_AUTO_START, true);
        startActivityForResult(intent, REQ_PRONUNCIATION_COMPARE);
    }

    private View audioButton(LearningLessonRepository.Exercise exercise) {
        AudioButtonView play = new AudioButtonView(this);
        play.setContentDescription(getString(R.string.learning_lesson_play_audio));
        play.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().translationY(dp(4)).scaleX(0.95f).scaleY(0.95f).setDuration(65)
                    .withEndAction(() -> v.animate().translationY(0).scaleX(1f).scaleY(1f)
                            .setDuration(110).start()).start();
            playExerciseAudio(exercise);
        });
        return play;
    }

    private void onAction() {
        if (currentIndex >= queue.size()) return;
        if (answered) {
            currentIndex++;
            showCurrentQuestion();
            return;
        }
        LearningLessonRepository.Exercise exercise = queue.get(currentIndex);
        AnswerResult result = evaluate(exercise);
        if (!result.ready) {
            Toast.makeText(this, result.message.isEmpty()
                    ? getString(R.string.learning_lesson_answer_first) : result.message,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        answered = true;
        hideKeyboard();
        boolean firstAttempt = firstAttemptAnswered.add(exercise.id);
        if (firstAttempt && result.correct) firstAttemptCorrect++;
        if (result.correct) masteredExercises.add(exercise.id);

        boolean willRetry = false;
        if (!result.correct) {
            int retries = retryCount.containsKey(exercise.id) ? retryCount.get(exercise.id) : 0;
            int maxRetries = lessonData == null ? 2 : lessonData.maxRetries;
            if (retries < maxRetries) {
                retryCount.put(exercise.id, retries + 1);
                queue.add(exercise);
                willRetry = true;
            }
        }
        showFeedback(exercise, result.correct, willRetry);
    }

    private AnswerResult evaluate(LearningLessonRepository.Exercise exercise) {
        if ("single_choice".equals(exercise.type) || "listen_choice".equals(exercise.type)
                || "true_false".equals(exercise.type) || "image_choice".equals(exercise.type)) {
            if (selectedChoice.isEmpty()) return AnswerResult.notReady("");
            return AnswerResult.of(LearningLessonRepository.normalize(selectedChoice).equals(
                    LearningLessonRepository.normalize(exercise.answer)));
        }
        if ("fill_blank".equals(exercise.type) || "dictation".equals(exercise.type)) {
            String value = fillInput == null ? "" : fillInput.getText().toString().trim();
            if (value.isEmpty()) return AnswerResult.notReady("");
            return AnswerResult.of(exercise.accepts(value));
        }
        if ("word_order".equals(exercise.type)) {
            if (orderedTokens.isEmpty()) return AnswerResult.notReady("");
            ArrayList<String> words = new ArrayList<>();
            for (WordToken token : orderedTokens) words.add(token.value);
            String value = LearningLessonRepository.join(words);
            return AnswerResult.of(LearningLessonRepository.normalize(value).equals(
                    LearningLessonRepository.normalize(exercise.answer)));
        }
        if ("matching".equals(exercise.type)) {
            if (matchedPairIndexes.size() < exercise.pairs.size()) {
                return AnswerResult.notReady(getString(R.string.learning_lesson_finish_matching));
            }
            return AnswerResult.of(true);
        }
        if ("pronunciation".equals(exercise.type)) {
            if (!pronunciationOpened) {
                return AnswerResult.notReady(getString(R.string.learning_lesson_record_first));
            }
            return AnswerResult.of(true);
        }
        return AnswerResult.of(false);
    }

    private void showFeedback(LearningLessonRepository.Exercise exercise, boolean correct,
                              boolean willRetry) {
        int feedbackColor = correct ? LearningUiKit.GREEN_SOFT : LearningUiKit.RED_SOFT;
        feedbackHost.setBackgroundColor(feedbackColor);
        feedbackPanel.setBackgroundColor(feedbackColor);
        feedbackTitle.setVisibility(View.VISIBLE);
        feedbackBody.setVisibility(View.VISIBLE);
        feedbackTitle.setText((correct ? "✓  " : "✕  ") + getString(correct
                ? R.string.learning_lesson_correct : R.string.learning_lesson_incorrect));
        feedbackTitle.setTextColor(correct ? 0xFF3F7D20 : COLOR_ERROR_DARK);

        if (!exercise.options.isEmpty()) {
            View correctView = choiceViews.get(
                    LearningLessonRepository.normalize(exercise.answer));
            View selectedView = choiceViews.get(
                    LearningLessonRepository.normalize(selectedChoice));
            if (correctView != null) setChoiceVisual(correctView, ChoiceCard.STATE_CORRECT);
            if (!correct && selectedView != null && selectedView != correctView) {
                setChoiceVisual(selectedView, ChoiceCard.STATE_WRONG);
            }
        }

        StringBuilder body = new StringBuilder();
        if (!correct) body.append(getString(R.string.learning_lesson_correct_answer,
                displayAnswer(exercise)));
        if (!exercise.explanation.isEmpty()) {
            if (body.length() > 0) body.append('\n');
            body.append(exercise.explanation);
        }
        if (!correct) {
            if (body.length() > 0) body.append('\n');
            body.append(getString(willRetry ? R.string.learning_lesson_retry_hint
                    : R.string.learning_lesson_retry_exhausted));
        } else if (body.length() == 0) {
            body.append(getString(R.string.learning_lesson_correct_hint));
        }
        feedbackBody.setText(body.toString());
        feedbackBody.setTextColor(correct ? 0xFF4F6F3C : 0xFF8D3A41);
        actionButton.setText(currentIndex + 1 >= queue.size()
                ? R.string.learning_lesson_finish : R.string.learning_lesson_continue);
        actionButton.setBackground(LearningUiKit.raisedSelector(
                correct ? COLOR_SUCCESS : COLOR_ERROR,
                correct ? COLOR_ACCENT_DARK : COLOR_ERROR_DARK,
                dp(16), 0, 0, dp(5)));
        setActionEnabled(true);
        actionButton.performHapticFeedback(correct ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.REJECT);
    }

    private String displayAnswer(LearningLessonRepository.Exercise exercise) {
        if (exercise == null) return "";
        for (LearningLessonRepository.ChoiceOption option : exercise.options) {
            if (LearningLessonRepository.normalize(option.value).equals(
                    LearningLessonRepository.normalize(exercise.answer))) {
                return option.text;
            }
        }
        if ("word_order".equals(exercise.type) && !exercise.answerWords.isEmpty()) {
            return LearningLessonRepository.join(exercise.answerWords);
        }
        return exercise.answer;
    }

    private void showCompletion() {
        questionHost.removeAllViews();
        feedbackHost.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);
        progressBar.setProgress(1, 1);
        progressText.setText(getString(R.string.learning_lesson_complete_progress));

        int total = Math.max(1, originalExercises.size());
        if (!attemptRecorded) {
            completionScore = clamp(firstAttemptCorrect * 100 / total, 0, 100);
            boolean masteredAll = masteredExercises.size() >= originalExercises.size();
            int passingScore = lessonData == null ? 0 : lessonData.passingScore;
            completionPassed = masteredAll && completionScore >= passingScore;
            completionStars = completionPassed
                    ? completionScore >= 90 ? 3 : completionScore >= 75 ? 2 : 1 : 0;
            if (progressStore != null) {
                progressStore.recordAttempt(courseId, lessonId, completionScore,
                        completionStars, completionPassed);
            }
            attemptRecorded = true;
        }

        int resultColor = completionPassed ? COLOR_SUCCESS : COLOR_ERROR;
        TextView icon = text(completionPassed ? "✓" : "!", 42, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(raised(resultColor,
                completionPassed ? COLOR_ACCENT_DARK : COLOR_ERROR_DARK,
                dp(42), 0, 0, dp(6)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(84), dp(84));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconLp.setMargins(0, dp(32), 0, dp(18));
        questionHost.addView(icon, iconLp);

        TextView done = text(getString(completionPassed
                        ? R.string.learning_lesson_complete_title
                        : R.string.learning_lesson_not_passed_title),
                28, COLOR_TEXT, true);
        done.setGravity(Gravity.CENTER);
        questionHost.addView(done, new LinearLayout.LayoutParams(-1, -2));

        TextView starsView = text(repeat("★", completionStars) + repeat("☆", 3 - completionStars),
                27, completionPassed ? 0xFFFFB020 : 0xFFB7BECA, true);
        starsView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams starsLp = new LinearLayout.LayoutParams(-1, -2);
        starsLp.setMargins(0, dp(12), 0, dp(8));
        questionHost.addView(starsView, starsLp);

        TextView scoreView = text(getString(R.string.learning_lesson_score, completionScore), 17,
                COLOR_SUB, true);
        scoreView.setGravity(Gravity.CENTER);
        questionHost.addView(scoreView, new LinearLayout.LayoutParams(-1, -2));

        if (!completionPassed) {
            int missing = Math.max(0, originalExercises.size() - masteredExercises.size());
            int passing = lessonData == null ? 0 : lessonData.passingScore;
            String reason = missing > 0
                    ? getString(R.string.learning_lesson_not_mastered, missing)
                    : getString(R.string.learning_lesson_score_required, passing);
            TextView reasonView = text(reason, 14, COLOR_ERROR, false);
            reasonView.setGravity(Gravity.CENTER);
            reasonView.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams reasonLp = new LinearLayout.LayoutParams(-1, -2);
            reasonLp.setMargins(dp(12), dp(12), dp(12), 0);
            questionHost.addView(reasonView, reasonLp);
        }

        TextView primary = text(getString(completionPassed
                        ? R.string.learning_lesson_back_to_map : R.string.learning_lesson_again),
                16, Color.WHITE, true);
        primary.setGravity(Gravity.CENTER);
        primary.setBackground(raised(completionPassed ? COLOR_ACCENT : COLOR_ERROR,
                completionPassed ? COLOR_ACCENT_DARK : COLOR_ERROR_DARK,
                dp(18), 0, 0, dp(5)));
        primary.setOnClickListener(v -> {
            if (completionPassed) finish();
            else restartSession();
        });
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(-1, dp(56));
        primaryLp.setMargins(0, dp(28), 0, dp(10));
        questionHost.addView(primary, primaryLp);

        TextView secondary = text(getString(completionPassed
                        ? R.string.learning_lesson_again : R.string.learning_lesson_back_to_map),
                15, COLOR_ACCENT, true);
        secondary.setGravity(Gravity.CENTER);
        secondary.setTextColor(completionPassed ? COLOR_BLUE : COLOR_SUB);
        secondary.setBackground(raised(Color.WHITE, 0xFFD5D5D5,
                dp(17), completionPassed ? COLOR_BLUE : COLOR_BORDER, dp(2), dp(4)));
        secondary.setOnClickListener(v -> {
            if (completionPassed) restartSession();
            else finish();
        });
        questionHost.addView(secondary, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private void restartSession() {
        resetSession();
        queue.addAll(originalExercises);
        showCurrentQuestion();
    }

    private void showLoadError(String message) {
        questionHost.removeAllViews();
        feedbackHost.setVisibility(View.VISIBLE);
        feedbackPanel.setVisibility(View.VISIBLE);
        feedbackHost.setBackgroundColor(Color.WHITE);
        feedbackPanel.setBackgroundColor(Color.WHITE);
        feedbackTitle.setVisibility(View.GONE);
        feedbackBody.setVisibility(View.GONE);
        actionButton.setVisibility(View.VISIBLE);
        progressText.setText("—");
        setActionEnabled(true);
        TextView error = text(getString(R.string.learning_lesson_load_failed,
                message == null ? "" : message), 15, COLOR_ERROR, false);
        error.setGravity(Gravity.CENTER);
        error.setLineSpacing(dp(3), 1f);
        error.setPadding(dp(20), dp(32), dp(20), dp(32));
        error.setBackground(rounded(0xFFFFEEF1, dp(22), 0xFFF4C5CE, dp(1)));
        questionHost.addView(error, new LinearLayout.LayoutParams(-1, -2));
        actionButton.setText(R.string.learning_lesson_close);
        actionButton.setOnClickListener(v -> finish());
    }

    private void playExerciseAudio(LearningLessonRepository.Exercise exercise) {
        releasePlayer();
        if (!exercise.audio.isEmpty()) {
            try {
                MediaPlayer player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                if (!filePath.isEmpty()) {
                    File audio = resolveInstalledMedia(exercise.audio);
                    if (!audio.isFile()) throw new IllegalStateException("Audio not found");
                    player.setDataSource(audio.getAbsolutePath());
                } else {
                    String asset = resolveBundledAudioAsset(exercise.audio);
                    try (AssetFileDescriptor descriptor = getAssets().openFd(asset)) {
                        player.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(),
                                descriptor.getLength());
                    }
                }
                player.setOnPreparedListener(mp -> {
                    if (mediaPlayer != mp || destroyed) {
                        try { mp.release(); } catch (Throwable ignored) { }
                        return;
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= 23
                                && Math.abs(exercise.originalSpeed - 1f) > 0.01f) {
                            PlaybackParams params = mp.getPlaybackParams();
                            params.setSpeed(Math.max(0.5f, Math.min(1.5f, exercise.originalSpeed)));
                            mp.setPlaybackParams(params);
                        }
                        mp.start();
                    } catch (Throwable ignored) {
                        if (mediaPlayer == mp) {
                            releasePlayer();
                            speakFallback(exercise);
                        } else {
                            try { mp.release(); } catch (Throwable ignoredRelease) { }
                        }
                    }
                });
                player.setOnCompletionListener(mp -> {
                    if (mediaPlayer == mp) releasePlayer();
                    else try { mp.release(); } catch (Throwable ignored) { }
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    if (mediaPlayer == mp) {
                        releasePlayer();
                        speakFallback(exercise);
                    } else {
                        try { mp.release(); } catch (Throwable ignored) { }
                    }
                    return true;
                });
                mediaPlayer = player;
                player.prepareAsync();
                return;
            } catch (Throwable ignored) {
                releasePlayer();
            }
        }
        speakFallback(exercise);
    }

    private void speakFallback(LearningLessonRepository.Exercise exercise) {
        String value = !exercise.audioText.isEmpty() ? exercise.audioText
                : !exercise.text.isEmpty() ? exercise.text : exercise.answer;
        if (!LearningTtsBridge.speak(this, value, LearningTtsBridge.LANG_ZH_CN,
                LearningTtsBridge.MODE_EXAMPLE)) {
            Toast.makeText(this, R.string.learning_lesson_audio_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String resolveBundledAudioAsset(String relative) {
        String clean = LearningLessonRepository.cleanRelative(relative, true);
        if (clean.isEmpty()) return "";
        if (clean.startsWith("learning/")) return clean;
        int slash = bundledAsset.lastIndexOf('/');
        String parent = slash >= 0 ? bundledAsset.substring(0, slash + 1) : "";
        return parent + clean;
    }

    private File resolveInstalledMedia(String relative) throws Exception {
        String clean = LearningLessonRepository.cleanRelative(relative, true);
        if (clean.isEmpty()) throw new SecurityException("Invalid media path");
        if (!packageRootPath.isEmpty()) {
            File rootFile = safeChild(new File(packageRootPath), clean);
            if (rootFile.isFile()) return rootFile;
        }
        // Compatibility with early packages that stored media beside the lesson JSON.
        File lessonParent = new File(filePath).getParentFile();
        File legacy = safeChild(lessonParent, clean);
        if (legacy.isFile()) return legacy;
        throw new IllegalStateException("Media not found");
    }

    private File safeChild(File base, String relative) throws Exception {
        if (base == null) throw new SecurityException("Invalid package directory");
        String clean = LearningLessonRepository.cleanRelative(relative, true);
        if (clean.isEmpty()) throw new SecurityException("Invalid media path");
        File child = new File(base, clean).getCanonicalFile();
        String basePath = base.getCanonicalPath() + File.separator;
        if (!child.getPath().startsWith(basePath)) {
            throw new SecurityException("Media path leaves package directory");
        }
        return child;
    }

    private void cancelAutoPlay() {
        if (autoPlayRunnable != null && questionHost != null) questionHost.removeCallbacks(autoPlayRunnable);
        autoPlayRunnable = null;
    }

    private void releasePlayer() {
        MediaPlayer old = mediaPlayer;
        mediaPlayer = null;
        if (old == null) return;
        try { old.stop(); } catch (Throwable ignored) { }
        try { old.reset(); } catch (Throwable ignored) { }
        try { old.release(); } catch (Throwable ignored) { }
    }

    private LinearLayout wrapRow() {
        FlowLayout flow = new FlowLayout(this);
        flow.setPadding(0, 0, 0, 0);
        return flow;
    }

    private LinearLayout.LayoutParams chipLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(46));
        lp.setMargins(0, 0, dp(8), dp(8));
        return lp;
    }

    private TextView matchButton(String value) {
        TextView view = text(value, 15, COLOR_TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(5), dp(8), dp(5));
        view.setBackground(raised(Color.WHITE, 0xFFD5D5D5,
                dp(14), COLOR_BORDER, dp(2), dp(4)));
        return view;
    }

    private LinearLayout.LayoutParams matchLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, 0, 0, dp(9));
        return lp;
    }

    private void addVerticalSpace(LinearLayout parent, int value) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(value)));
    }

    private void addHorizontalSpace(LinearLayout parent, int value) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(dp(value), 1));
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    private void resetFooter() {
        feedbackHost.setVisibility(View.VISIBLE);
        feedbackPanel.setVisibility(View.VISIBLE);
        feedbackHost.setBackgroundColor(Color.WHITE);
        feedbackPanel.setBackgroundColor(Color.WHITE);
        feedbackTitle.setVisibility(View.GONE);
        feedbackBody.setVisibility(View.GONE);
        actionButton.setVisibility(View.VISIBLE);
        actionButton.setOnClickListener(v -> onAction());
        actionButton.setText(R.string.learning_lesson_check);
        actionButton.setBackground(LearningUiKit.raisedSelector(COLOR_ACCENT,
                COLOR_ACCENT_DARK, dp(16), 0, 0, dp(5)));
        setActionEnabled(false);
    }

    private void setActionEnabled(boolean enabled) {
        if (actionButton == null) return;
        actionButton.setEnabled(enabled);
        actionButton.setAlpha(enabled ? 1f : 0.48f);
    }

    private void updateActionAvailability() {
        if (answered || currentIndex >= queue.size()) {
            setActionEnabled(true);
            return;
        }
        LearningLessonRepository.Exercise exercise = queue.get(currentIndex);
        if ("single_choice".equals(exercise.type) || "listen_choice".equals(exercise.type)
                || "true_false".equals(exercise.type) || "image_choice".equals(exercise.type)) {
            setActionEnabled(!selectedChoice.isEmpty());
        } else if ("fill_blank".equals(exercise.type) || "dictation".equals(exercise.type)) {
            setActionEnabled(fillInput != null && fillInput.getText().toString().trim().length() > 0);
        } else if ("word_order".equals(exercise.type)) {
            setActionEnabled(!orderedTokens.isEmpty());
        } else if ("matching".equals(exercise.type)) {
            setActionEnabled(matchedPairIndexes.size() >= exercise.pairs.size());
        } else if ("pronunciation".equals(exercise.type)) {
            setActionEnabled(pronunciationOpened);
        }
    }

    private void addCenteredAudio(LearningLessonRepository.Exercise exercise) {
        View play = audioButton(exercise);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(88), dp(94));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        questionHost.addView(play, lp);
    }

    private void setChoiceVisual(View view, int state) {
        if (view instanceof ChoiceCard) ((ChoiceCard) view).setVisualState(state);
    }

    private String typeLabel(String type) {
        if ("listen_choice".equals(type) || "dictation".equals(type)) {
            return getString(R.string.learning_lesson_type_listening);
        }
        if ("word_order".equals(type)) return getString(R.string.learning_lesson_type_order);
        if ("fill_blank".equals(type)) return getString(R.string.learning_lesson_type_fill);
        if ("matching".equals(type)) return getString(R.string.learning_lesson_type_matching);
        if ("pronunciation".equals(type)) return getString(R.string.learning_lesson_type_speaking);
        if ("true_false".equals(type)) return getString(R.string.learning_lesson_type_judgement);
        if ("image_choice".equals(type)) return getString(R.string.learning_lesson_type_image);
        return getString(R.string.learning_lesson_type_choice);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return LearningUiKit.text(this, value, size, color, bold);
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        return LearningUiKit.rounded(color, radius, strokeColor, strokeWidth);
    }

    private Drawable raised(int topColor, int bottomColor, float radius,
                            int strokeColor, int strokeWidth, int depth) {
        return LearningUiKit.raised(topColor, bottomColor, radius, strokeColor, strokeWidth, depth);
    }

    private int dp(float value) {
        return LearningUiKit.dp(this, value);
    }

    private static void addValidIds(Set<String> target, ArrayList<String> values,
                                    Map<String, LearningLessonRepository.Exercise> valid) {
        if (values == null || valid == null) return;
        for (String value : values) if (valid.containsKey(value)) target.add(value);
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.max(0, count); i++) builder.append(value);
        return builder.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class ChoiceCard extends LinearLayout {
        static final int STATE_NORMAL = 0;
        static final int STATE_SELECTED = 1;
        static final int STATE_CORRECT = 2;
        static final int STATE_WRONG = 3;

        final TextView label;
        final TextView shortcut;
        final ImageView image;
        final boolean imageMode;

        ChoiceCard(Context context, String value, int number, boolean imageMode) {
            super(context);
            this.imageMode = imageMode;
            setOrientation(imageMode ? VERTICAL : HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(imageMode ? dp(9) : dp(14), imageMode ? dp(9) : dp(8),
                    imageMode ? dp(9) : dp(12), imageMode ? dp(12) : dp(12));
            setClickable(true);
            setFocusable(true);

            if (imageMode) {
                image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(rounded(0xFFF1F3F7, dp(12), 0, 0));
                image.setClipToOutline(true);
                addView(image, new LinearLayout.LayoutParams(-1, 0, 1f));
                label = text(value, 16, COLOR_TEXT, true);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, dp(48));
                labelLp.setMargins(dp(4), dp(7), dp(4), 0);
                addView(label, labelLp);
                shortcut = text(String.valueOf(number), 12, COLOR_SUB, true);
                shortcut.setVisibility(View.GONE);
            } else {
                image = null;
                shortcut = text(String.valueOf(number), 12, COLOR_SUB, true);
                shortcut.setGravity(Gravity.CENTER);
                shortcut.setBackground(rounded(Color.WHITE, dp(9), COLOR_BORDER, dp(2)));
                addView(shortcut, new LinearLayout.LayoutParams(dp(30), dp(30)));
                label = text(value, 17, COLOR_TEXT, true);
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -1, 1f);
                labelLp.setMargins(dp(13), 0, dp(6), 0);
                addView(label, labelLp);
            }
            setVisualState(STATE_NORMAL);
        }

        void setVisualState(int state) {
            int top = Color.WHITE;
            int bottom = 0xFFD5D5D5;
            int border = COLOR_BORDER;
            int textColor = COLOR_TEXT;
            if (state == STATE_SELECTED) {
                top = LearningUiKit.BLUE_SOFT;
                bottom = 0xFF84D8FF;
                border = COLOR_BLUE;
                textColor = COLOR_BLUE_DARK;
            } else if (state == STATE_CORRECT) {
                top = LearningUiKit.GREEN_SOFT;
                bottom = 0xFF9DD86B;
                border = COLOR_SUCCESS;
                textColor = 0xFF3F7D20;
            } else if (state == STATE_WRONG) {
                top = LearningUiKit.RED_SOFT;
                bottom = 0xFFF1A8AC;
                border = COLOR_ERROR;
                textColor = COLOR_ERROR_DARK;
            }
            setBackground(LearningUiKit.raisedSelector(top, bottom, dp(16), border, dp(2), dp(4)));
            label.setTextColor(textColor);
            if (shortcut != null && !imageMode) {
                shortcut.setTextColor(state == STATE_NORMAL ? COLOR_SUB : textColor);
                shortcut.setBackground(rounded(state == STATE_NORMAL ? Color.WHITE : top,
                        dp(9), border, dp(2)));
            }
        }
    }

    private final class AudioButtonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF arc = new RectF();

        AudioButtonView(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - dp(2);
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(8);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_BLUE_DARK);
            canvas.drawCircle(cx, cy + dp(7), radius, paint);
            paint.setColor(COLOR_BLUE);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setColor(0x28FFFFFF);
            canvas.drawOval(cx - radius * 0.52f, cy - radius * 0.63f,
                    cx + radius * 0.52f, cy - radius * 0.38f, paint);

            paint.setColor(Color.WHITE);
            path.reset();
            path.moveTo(cx - dp(20), cy - dp(8));
            path.lineTo(cx - dp(10), cy - dp(8));
            path.lineTo(cx + dp(2), cy - dp(18));
            path.lineTo(cx + dp(2), cy + dp(18));
            path.lineTo(cx - dp(10), cy + dp(8));
            path.lineTo(cx - dp(20), cy + dp(8));
            path.close();
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            arc.set(cx - dp(5), cy - dp(16), cx + dp(22), cy + dp(16));
            canvas.drawArc(arc, -53, 106, false, paint);
            arc.set(cx - dp(3), cy - dp(22), cx + dp(33), cy + dp(22));
            canvas.drawArc(arc, -48, 96, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static final class WordToken {
        final int id;
        final String value;

        WordToken(int id, String value) {
            this.id = id;
            this.value = value == null ? "" : value;
        }
    }

    private static final class AnswerResult {
        final boolean ready;
        final boolean correct;
        final String message;

        private AnswerResult(boolean ready, boolean correct, String message) {
            this.ready = ready;
            this.correct = correct;
            this.message = message == null ? "" : message;
        }

        static AnswerResult of(boolean correct) {
            return new AnswerResult(true, correct, "");
        }

        static AnswerResult notReady(String message) {
            return new AnswerResult(false, false, message);
        }
    }

    /** Small wrapping layout implemented as a LinearLayout-compatible container. */
    private final class FlowLayout extends LinearLayout {
        FlowLayout(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.START);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int available = Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
                    - getPaddingLeft() - getPaddingRight());
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, y);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                if (x > 0 && x + childWidth > available) {
                    x = 0;
                    y += rowHeight;
                    rowHeight = 0;
                }
                x += childWidth;
                rowHeight = Math.max(rowHeight, childHeight);
            }
            y += rowHeight;
            int width = resolveSize(available + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
            int height = resolveSize(y + getPaddingTop() + getPaddingBottom(), heightMeasureSpec);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int maxWidth = right - left - getPaddingLeft() - getPaddingRight();
            int x = getPaddingLeft();
            int y = getPaddingTop();
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int occupied = childWidth + lp.leftMargin + lp.rightMargin;
                if (x > getPaddingLeft() && x - getPaddingLeft() + occupied > maxWidth) {
                    x = getPaddingLeft();
                    y += rowHeight;
                    rowHeight = 0;
                }
                int childLeft = x + lp.leftMargin;
                int childTop = y + lp.topMargin;
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                x += occupied;
                rowHeight = Math.max(rowHeight, childHeight + lp.topMargin + lp.bottomMargin);
            }
        }
    }
}
