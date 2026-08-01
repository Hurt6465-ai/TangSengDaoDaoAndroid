package com.chat.learning;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.ToneGenerator;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.SoundEffectConstants;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.File;
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
    private static final String STATE_MATCHING_HAD_MISTAKE = "matching_had_mistake";
    private static final String STATE_MATCHING_WRONG_ATTEMPTS = "matching_wrong_attempts";
    private static final String STATE_SESSION_SEED = "session_seed";
    private static final String PREFS_NAME = "learning_lesson_preferences";
    private static final String PREF_SHOW_PINYIN = "show_pinyin";

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
    private SwitchCompat pinyinSwitch;
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
    private boolean showPinyin = true;
    private String selectedChoice = "";
    private final Map<String, View> choiceViews = new HashMap<>();
    private EditText fillInput;
    private final ArrayList<WordToken> orderedTokens = new ArrayList<>();
    private final Map<Integer, WordChipView> orderChips = new HashMap<>();
    private LinearLayout orderAnswerRow;
    private LinearLayout orderBankRow;
    private final Set<Integer> matchedPairIndexes = new HashSet<>();
    private final Map<Integer, MatchCardView> matchLeftViews = new HashMap<>();
    private final Map<Integer, MatchCardView> matchRightViews = new HashMap<>();
    private MatchCardView selectedMatchLeft;
    private LearningLessonRepository.PairItem selectedMatchPair;
    private boolean matchingHadMistake;
    private int matchingWrongAttempts;
    private long sessionSeed;
    private MediaPlayer mediaPlayer;
    private ToneGenerator feedbackTone;
    private SoundPool feedbackSounds;
    private int correctSoundId;
    private int wrongSoundId;
    private final ArrayList<View> pinyinViews = new ArrayList<>();
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
        showPinyin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_SHOW_PINYIN, true);
        progressStore = new LearningPathProgressStore(this);
        initFeedbackSounds();
        buildLayout();
        loadLesson();
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
        outState.putLong(STATE_SESSION_SEED, sessionSeed);
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
            outState.putBoolean(STATE_MATCHING_HAD_MISTAKE, matchingHadMistake);
            outState.putInt(STATE_MATCHING_WRONG_ATTEMPTS, matchingWrongAttempts);
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
        releaseFeedbackSounds();
        releaseFeedbackTone();
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
        bar.setPadding(dp(8), dp(6), dp(10), dp(4));

        TextView close = text("‹", 32, COLOR_SUB, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(getString(R.string.learning_lesson_close));
        close.setOnClickListener(v -> confirmExit());
        bar.addView(close, new LinearLayout.LayoutParams(dp(44), dp(50)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, -1, 1f);
        centerLp.setMargins(dp(4), 0, dp(8), 0);
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

        pinyinSwitch = new SwitchCompat(this);
        pinyinSwitch.setText(R.string.learning_lesson_pinyin);
        pinyinSwitch.setTextSize(12);
        pinyinSwitch.setTextColor(COLOR_SUB);
        pinyinSwitch.setGravity(Gravity.CENTER);
        pinyinSwitch.setChecked(showPinyin);
        pinyinSwitch.setContentDescription(getString(R.string.learning_lesson_pinyin_switch));
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        pinyinSwitch.setThumbTintList(new ColorStateList(states,
                new int[]{COLOR_BLUE, 0xFFB8BEC8}));
        pinyinSwitch.setTrackTintList(new ColorStateList(states,
                new int[]{0x667BCDF4, 0x336F7782}));
        pinyinSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            showPinyin = checked;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_SHOW_PINYIN, checked).apply();
            updatePinyinVisibility();
            buttonView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            buttonView.playSoundEffect(SoundEffectConstants.CLICK);
        });
        bar.addView(pinyinSwitch, new LinearLayout.LayoutParams(dp(76), -1));

        progressText = text("0 / 0", 11, COLOR_SUB, true);
        progressText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        progressText.setSingleLine(true);
        bar.addView(progressText, new LinearLayout.LayoutParams(dp(86), -1));
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
        sessionSeed = state.getLong(STATE_SESSION_SEED, sessionSeed);
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
                WordChipView chip = orderChips.get(id);
                if (chip == null || !(chip.getTag() instanceof WordToken)) continue;
                moveOrderChip(chip, orderAnswerRow, orderAnswerRow.getChildCount(), false);
            }
        }
        int[] matchedIds = state.getIntArray(STATE_MATCHED_PAIR_IDS);
        if (matchedIds != null) {
            for (int id : matchedIds) restoreMatchedPair(id);
        }
        matchingHadMistake = state.getBoolean(STATE_MATCHING_HAD_MISTAKE, false);
        matchingWrongAttempts = Math.max(0, state.getInt(STATE_MATCHING_WRONG_ATTEMPTS, 0));
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
        sessionSeed = LessonSessionPolicy.newSessionSeed();
    }

    private void showCurrentQuestion() {
        int generation = ++questionGeneration;
        cancelAutoPlay();
        releasePlayer();
        hideKeyboard();
        resetFooter();
        answered = false;
        selectedChoice = "";
        choiceViews.clear();
        pinyinViews.clear();
        fillInput = null;
        orderedTokens.clear();
        orderChips.clear();
        matchedPairIndexes.clear();
        matchLeftViews.clear();
        matchRightViews.clear();
        selectedMatchLeft = null;
        selectedMatchPair = null;
        matchingHadMistake = false;
        matchingWrongAttempts = 0;
        questionHost.removeAllViews();
        if (questionScroll != null) questionScroll.scrollTo(0, 0);

        if (currentIndex >= queue.size()) {
            showCompletion();
            return;
        }
        LearningLessonRepository.Exercise exercise = queue.get(currentIndex);
        updateLessonProgress();

        TextView typeBadge = text(typeLabel(exercise.type), 12, COLOR_BLUE, true);
        typeBadge.setGravity(Gravity.CENTER);
        typeBadge.setAllCaps(true);
        typeBadge.setLetterSpacing(0.06f);
        typeBadge.setPadding(dp(10), dp(5), dp(10), dp(5));
        typeBadge.setBackground(rounded(LearningUiKit.BLUE_SOFT, dp(12), 0xFF84D8FF, dp(1)));
        questionHost.addView(typeBadge, new LinearLayout.LayoutParams(-2, -2));

        addQuestionHeader(exercise);

        switch (exercise.type) {
            case "listen_choice":
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
                renderChoice(exercise);
                break;
            case "word_order":
                renderWordOrder(exercise);
                break;
            case "fill_blank":
            case "dictation":
                renderFillBlank(exercise);
                break;
            case "matching":
                renderMatching(exercise);
                break;
            default:
                showLoadError(getString(R.string.learning_lesson_unsupported_type));
                break;
        }
        updateActionAvailability();
    }

    private void addQuestionHeader(LearningLessonRepository.Exercise exercise) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, dp(15), 0, 0);
        questionHost.addView(row, rowLp);

        TextView question = text(exercise.question, 26, COLOR_TEXT, true);
        question.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams questionLp = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(question, questionLp);

        if (hasAudio(exercise)) {
            View play = audioButton(exercise);
            LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            playLp.setMargins(dp(3), dp(1), 0, 0);
            row.addView(play, playLp);
        }

        String questionPinyin = pinyinFor(exercise.question, "");
        if (!questionPinyin.isEmpty()) {
            TextView pinyin = text(questionPinyin, 14, 0xFF7C8797, false);
            pinyin.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams pinyinLp = new LinearLayout.LayoutParams(-1, -2);
            pinyinLp.setMargins(0, dp(7), 0, 0);
            questionHost.addView(pinyin, pinyinLp);
            registerPinyinView(pinyin);
        }

        if (!exercise.hint.isEmpty()) {
            TextView hint = text(exercise.hint, 15, COLOR_SUB, false);
            hint.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
            hintLp.setMargins(0, dp(8), 0, dp(20));
            questionHost.addView(hint, hintLp);
        } else {
            addVerticalSpace(questionHost, 20);
        }
    }

    private boolean hasAudio(LearningLessonRepository.Exercise exercise) {
        return exercise != null && (!exercise.audio.isEmpty() || !exercise.audioText.isEmpty());
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
            LessonSessionPolicy.shuffle(choices, sessionSeed, exercise.id, "choices");
        }
        int contentWidth = Math.min(dp(620), getResources().getDisplayMetrics().widthPixels - dp(36));
        int gridWidth = Math.min(dp(250), Math.max(dp(132), (contentWidth - dp(12)) / 2));
        for (int index = 0; index < choices.size(); index++) {
            LearningLessonRepository.ChoiceOption choice = choices.get(index);
            ChoiceCard option = createChoiceView(choice);
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
                option.playSoundEffect(SoundEffectConstants.CLICK);
            });
            if (compactGrid) {
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = gridWidth;
                lp.height = imageGrid ? dp(214) : dp(82);
                lp.setMargins(index % 2 == 0 ? 0 : dp(6), 0,
                        index % 2 == 0 ? dp(6) : 0, dp(12));
                options.addView(option, lp);
            } else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(82));
                lp.setMargins(0, 0, 0, dp(12));
                options.addView(option, lp);
            }
        }
    }

    private ChoiceCard createChoiceView(LearningLessonRepository.ChoiceOption choice) {
        String optionPinyin = pinyinFor(choice.text, "");
        ChoiceCard card = new ChoiceCard(this, choice.text, optionPinyin,
                !choice.image.isEmpty());
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
        boolean explicitPinyin = !exercise.pinyin.isEmpty();
        boolean retryHint = retryCount.containsKey(exercise.id)
                && retryCount.get(exercise.id) != null && retryCount.get(exercise.id) > 0;
        String answerPinyin = explicitPinyin || retryHint
                ? pinyinFor(exercise.answer, exercise.pinyin) : "";
        if (!answerPinyin.isEmpty()) {
            TextView clue = text(getString(R.string.learning_lesson_fill_pinyin, answerPinyin),
                    15, COLOR_BLUE_DARK, true);
            clue.setLineSpacing(dp(2), 1f);
            clue.setPadding(dp(13), dp(10), dp(13), dp(10));
            clue.setBackground(rounded(LearningUiKit.BLUE_SOFT, dp(13), 0xFFB7E6FC, dp(1)));
            LinearLayout.LayoutParams clueLp = new LinearLayout.LayoutParams(-1, -2);
            clueLp.setMargins(0, 0, 0, dp(12));
            questionHost.addView(clue, clueLp);
            registerPinyinView(clue);
        }

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
        orderAnswerRow.setMinimumHeight(dp(72));
        orderAnswerRow.setPadding(dp(8), dp(8), dp(8), dp(8));
        orderAnswerRow.setBackground(raised(0xFFF8F8F8, 0xFFDADADA,
                dp(16), COLOR_BORDER, dp(2), dp(4)));
        LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(-1, -2);
        answerLp.setMargins(0, dp(7), 0, dp(16));
        questionHost.addView(orderAnswerRow, answerLp);

        TextView bankLabel = text(getString(R.string.learning_lesson_word_bank), 12,
                COLOR_SUB, true);
        questionHost.addView(bankLabel, new LinearLayout.LayoutParams(-1, -2));

        TextView dragHint = text(getString(R.string.learning_lesson_drag_hint), 12,
                0xFF8A94A3, false);
        LinearLayout.LayoutParams dragHintLp = new LinearLayout.LayoutParams(-1, -2);
        dragHintLp.setMargins(0, dp(4), 0, dp(8));
        questionHost.addView(dragHint, dragHintLp);

        orderBankRow = wrapRow();
        LinearLayout.LayoutParams bankLp = new LinearLayout.LayoutParams(-1, -2);
        questionHost.addView(orderBankRow, bankLp);
        configureOrderDragTarget(orderAnswerRow);
        configureOrderDragTarget(orderBankRow);

        List<WordToken> tokens = new ArrayList<>();
        for (int i = 0; i < exercise.words.size(); i++) {
            tokens.add(new WordToken(i, exercise.words.get(i)));
        }
        if (!exercise.keepOrder) shuffleWordTokens(tokens, exercise.id);
        for (WordToken token : tokens) addOrderChip(token, orderBankRow);
    }

    private void shuffleWordTokens(List<WordToken> tokens, String exerciseId) {
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
            LessonSessionPolicy.shuffle(tokens, sessionSeed + attempt, exerciseId, "word_order");
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

    private void addOrderChip(WordToken token, LinearLayout parent) {
        WordChipView chip = new WordChipView(this, token);
        chip.setTag(token);
        orderChips.put(token.id, chip);
        chip.setOnClickListener(v -> {
            if (answered) return;
            ViewGroup currentParent = (ViewGroup) chip.getParent();
            if (currentParent == orderBankRow) {
                moveOrderChip(chip, orderAnswerRow, orderAnswerRow.getChildCount(), true);
            } else {
                moveOrderChip(chip, orderBankRow, orderBankRow.getChildCount(), true);
            }
        });
        chip.setOnLongClickListener(v -> {
            if (answered) return false;
            ClipData data = ClipData.newPlainText("learning_word", token.value);
            chip.setAlpha(0.38f);
            chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            boolean started = chip.startDragAndDrop(data, new View.DragShadowBuilder(chip), chip, 0);
            if (!started) chip.setAlpha(1f);
            return started;
        });
        parent.addView(chip, chipLayoutParams());
    }

    private void configureOrderDragTarget(LinearLayout target) {
        target.setOnDragListener((view, event) -> {
            Object state = event.getLocalState();
            if (!(state instanceof WordChipView) || answered) return false;
            WordChipView chip = (WordChipView) state;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    view.setAlpha(0.82f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    view.setAlpha(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    view.setAlpha(1f);
                    int index = findDropIndex(target, chip, event.getX(), event.getY());
                    moveOrderChip(chip, target, index, true);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    chip.setAlpha(1f);
                    view.setAlpha(1f);
                    return true;
                default:
                    return true;
            }
        });
    }

    private int findDropIndex(ViewGroup target, View dragging, float x, float y) {
        int visibleIndex = 0;
        for (int i = 0; i < target.getChildCount(); i++) {
            View child = target.getChildAt(i);
            if (child == dragging) continue;
            float centerY = (child.getTop() + child.getBottom()) / 2f;
            float centerX = (child.getLeft() + child.getRight()) / 2f;
            if (y < centerY || (Math.abs(y - centerY) <= child.getHeight() / 2f && x < centerX)) {
                return visibleIndex;
            }
            visibleIndex++;
        }
        return visibleIndex;
    }

    private void moveOrderChip(WordChipView chip, ViewGroup target, int index,
                               boolean feedback) {
        if (chip == null || target == null || !(chip.getTag() instanceof WordToken)) return;
        WordToken token = (WordToken) chip.getTag();
        if (chip.getParent() instanceof ViewGroup) {
            ((ViewGroup) chip.getParent()).removeView(chip);
        }
        orderedTokens.remove(token);
        int targetIndex = Math.max(0, Math.min(index, target.getChildCount()));
        target.addView(chip, targetIndex, chipLayoutParams());
        if (target == orderAnswerRow) {
            int answerIndex = Math.max(0, Math.min(targetIndex, orderedTokens.size()));
            orderedTokens.add(answerIndex, token);
        }
        setActionEnabled(!orderedTokens.isEmpty());
        if (feedback) {
            chip.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            chip.playSoundEffect(SoundEffectConstants.CLICK);
        }
    }

    private void renderMatching(LearningLessonRepository.Exercise exercise) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        questionHost.addView(row, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        addHorizontalSpace(row, 10);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));

        List<LearningLessonRepository.PairItem> rightItems = new ArrayList<>(exercise.pairs);
        LessonSessionPolicy.shuffle(rightItems, sessionSeed, exercise.id, "matching");
        for (LearningLessonRepository.PairItem pair : exercise.pairs) {
            MatchCardView view = matchButton(pair.left);
            matchLeftViews.put(pair.index, view);
            view.setOnClickListener(v -> {
                if (answered || matchedPairIndexes.contains(pair.index)) return;
                if (selectedMatchLeft != null) selectedMatchLeft.setVisualState(MatchCardView.NORMAL);
                selectedMatchLeft = view;
                selectedMatchPair = pair;
                view.setVisualState(MatchCardView.SELECTED);
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                view.playSoundEffect(SoundEffectConstants.CLICK);
            });
            left.addView(view, matchLp());
        }
        for (LearningLessonRepository.PairItem rightPair : rightItems) {
            MatchCardView view = matchButton(rightPair.right);
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
                    v.playSoundEffect(SoundEffectConstants.CLICK);
                } else {
                    matchingHadMistake = true;
                    matchingWrongAttempts++;
                    view.setVisualState(MatchCardView.WRONG);
                    view.postDelayed(() -> {
                        if (view.isEnabled()) view.setVisualState(MatchCardView.NORMAL);
                    }, 350L);
                    v.performHapticFeedback(HapticFeedbackConstants.REJECT);
                    playFeedbackTone(false);
                }
            });
            right.addView(view, matchLp());
        }
    }

    private void restoreMatchedPair(int index) {
        MatchCardView left = matchLeftViews.get(index);
        MatchCardView right = matchRightViews.get(index);
        if (left == null || right == null) return;
        matchedPairIndexes.add(index);
        markPairMatched(left, right);
    }

    private void markPairMatched(MatchCardView left, MatchCardView right) {
        if (left != null) {
            left.setEnabled(false);
            left.setVisualState(MatchCardView.CORRECT);
        }
        if (right != null) {
            right.setEnabled(false);
            right.setVisualState(MatchCardView.CORRECT);
        }
    }

    private View audioButton(LearningLessonRepository.Exercise exercise) {
        AudioButtonView play = new AudioButtonView(this);
        play.setContentDescription(getString(R.string.learning_lesson_play_audio));
        play.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            v.playSoundEffect(SoundEffectConstants.CLICK);
            v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(65)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
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
            actionButton.performHapticFeedback(HapticFeedbackConstants.REJECT);
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
        updateLessonProgress();
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
            return AnswerResult.of(exercise.acceptsWordOrder(words));
        }
        if ("matching".equals(exercise.type)) {
            if (matchedPairIndexes.size() < exercise.pairs.size()) {
                return AnswerResult.notReady(getString(R.string.learning_lesson_finish_matching));
            }
            return AnswerResult.of(LessonSessionPolicy.matchingCorrect(matchingHadMistake));
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
        feedbackTitle.setText(getString(correct
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
        if (!correct && "matching".equals(exercise.type)) {
            body.append(getString(R.string.learning_lesson_matching_mistakes,
                    Math.max(1, matchingWrongAttempts)));
        } else if (!correct) {
            body.append(getString(R.string.learning_lesson_correct_answer,
                    displayAnswer(exercise)));
        }
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
        playFeedbackSignal(correct);
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

    private void updateLessonProgress() {
        int total = Math.max(1, originalExercises.size());
        int mastered = clamp(masteredExercises.size(), 0, total);
        int introduced = clamp(firstAttemptAnswered.size(), 0, total);
        progressBar.setProgress(introduced, total);
        if (currentIndex < originalExercises.size()) {
            progressText.setText(getString(R.string.learning_lesson_progress,
                    Math.min(currentIndex + 1, total), total));
        } else {
            progressText.setText(getString(R.string.learning_lesson_review_progress,
                    mastered, total));
        }
    }

    private boolean hasCurrentInput() {
        if (!selectedChoice.isEmpty() || !orderedTokens.isEmpty()
                || !matchedPairIndexes.isEmpty() || selectedMatchPair != null) return true;
        return fillInput != null && fillInput.getText() != null
                && !fillInput.getText().toString().trim().isEmpty();
    }

    private void confirmExit() {
        boolean inProgress = lessonData != null && !attemptRecorded
                && currentIndex < queue.size()
                && (!firstAttemptAnswered.isEmpty() || currentIndex > 0 || answered
                || hasCurrentInput());
        if (!inProgress) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.learning_lesson_exit_title)
                .setMessage(R.string.learning_lesson_exit_message)
                .setNegativeButton(R.string.learning_lesson_exit_continue, null)
                .setPositiveButton(R.string.learning_lesson_exit_leave,
                        (dialog, which) -> finish())
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    private void showCompletion() {
        questionHost.removeAllViews();
        feedbackHost.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);
        progressBar.setProgress(1, 1);
        progressText.setText(getString(R.string.learning_lesson_complete_progress));

        int total = Math.max(1, originalExercises.size());
        if (!attemptRecorded) {
            completionScore = LessonSessionPolicy.firstAttemptScore(firstAttemptCorrect, total);
            int passingScore = lessonData == null ? 0 : lessonData.passingScore;
            completionPassed = LessonSessionPolicy.passed(masteredExercises.size(),
                    originalExercises.size());
            completionStars = LessonSessionPolicy.stars(completionScore, completionPassed,
                    passingScore);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(58));
        lp.setMargins(0, 0, dp(8), dp(8));
        return lp;
    }

    private MatchCardView matchButton(String value) {
        return new MatchCardView(this, value, pinyinFor(value, ""));
    }

    private LinearLayout.LayoutParams matchLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68));
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
        }
    }

    private String pinyinFor(String value, String explicit) {
        String clean = safe(value);
        if (clean.isEmpty() || !containsHan(clean)) return "";
        String generated = PinyinUtils.resolve(clean, explicit, "");
        if (generated.isEmpty() || generated.equals(clean)) return "";
        return generated;
    }

    private boolean containsHan(String value) {
        if (value == null) return false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) return true;
        }
        return false;
    }

    private void registerPinyinView(View view) {
        if (view == null) return;
        pinyinViews.add(view);
        view.setVisibility(showPinyin ? View.VISIBLE : View.GONE);
    }

    private void updatePinyinVisibility() {
        for (View view : pinyinViews) {
            if (view != null) view.setVisibility(showPinyin ? View.VISIBLE : View.GONE);
        }
        if (questionHost != null) questionHost.requestLayout();
    }

    private void playFeedbackSignal(boolean correct) {
        if (actionButton != null) {
            actionButton.performHapticFeedback(correct ? HapticFeedbackConstants.CONFIRM
                    : HapticFeedbackConstants.REJECT);
        }
        playFeedbackTone(correct);
    }

    private void initFeedbackSounds() {
        try {
            feedbackSounds = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
            correctSoundId = feedbackSounds.load(this, R.raw.learning_correct, 1);
            wrongSoundId = feedbackSounds.load(this, R.raw.learning_wrong, 1);
        } catch (Throwable ignored) {
            feedbackSounds = null;
            correctSoundId = 0;
            wrongSoundId = 0;
        }
    }

    private void playFeedbackTone(boolean correct) {
        try {
            int id = correct ? correctSoundId : wrongSoundId;
            if (feedbackSounds != null && id != 0) {
                int stream = feedbackSounds.play(id, 0.92f, 0.92f, 1, 0, 1f);
                if (stream != 0) return;
            }
        } catch (Throwable ignored) { }
        try {
            if (feedbackTone == null) {
                feedbackTone = new ToneGenerator(AudioManager.STREAM_MUSIC, 68);
            }
            feedbackTone.startTone(correct ? ToneGenerator.TONE_PROP_ACK
                    : ToneGenerator.TONE_PROP_NACK, correct ? 120 : 190);
        } catch (Throwable ignored) { }
    }

    private void releaseFeedbackSounds() {
        SoundPool old = feedbackSounds;
        feedbackSounds = null;
        correctSoundId = 0;
        wrongSoundId = 0;
        if (old == null) return;
        try { old.release(); } catch (Throwable ignored) { }
    }

    private void releaseFeedbackTone() {
        ToneGenerator old = feedbackTone;
        feedbackTone = null;
        if (old == null) return;
        try { old.release(); } catch (Throwable ignored) { }
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
        final TextView pinyinLabel;
        final ImageView image;
        final boolean imageMode;

        ChoiceCard(Context context, String value, String pinyin, boolean imageMode) {
            super(context);
            this.imageMode = imageMode;
            setOrientation(imageMode ? VERTICAL : HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(imageMode ? dp(9) : dp(15), imageMode ? dp(9) : dp(8),
                    imageMode ? dp(9) : dp(15), imageMode ? dp(10) : dp(10));
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
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, dp(27));
                labelLp.setMargins(dp(4), dp(6), dp(4), 0);
                addView(label, labelLp);

                pinyinLabel = text(pinyin, 12, 0xFF7C8797, false);
                pinyinLabel.setGravity(Gravity.CENTER);
                pinyinLabel.setSingleLine(true);
                pinyinLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
                addView(pinyinLabel, new LinearLayout.LayoutParams(-1, dp(21)));
            } else {
                image = null;
                LinearLayout stack = new LinearLayout(context);
                stack.setOrientation(VERTICAL);
                stack.setGravity(Gravity.CENTER_VERTICAL);
                addView(stack, new LinearLayout.LayoutParams(0, -1, 1f));

                label = text(value, 17, COLOR_TEXT, true);
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                stack.addView(label, new LinearLayout.LayoutParams(-1, 0, 1f));

                pinyinLabel = text(pinyin, 12, 0xFF7C8797, false);
                pinyinLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                pinyinLabel.setSingleLine(true);
                pinyinLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
                stack.addView(pinyinLabel, new LinearLayout.LayoutParams(-1, dp(22)));
            }
            if (pinyin.isEmpty()) pinyinLabel.setVisibility(View.GONE);
            else registerPinyinView(pinyinLabel);
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
            pinyinLabel.setTextColor(state == STATE_NORMAL ? 0xFF7C8797 : textColor);
        }
    }

    private final class MatchCardView extends LinearLayout {
        static final int NORMAL = 0;
        static final int SELECTED = 1;
        static final int CORRECT = 2;
        static final int WRONG = 3;

        final TextView wordView;
        final TextView pinyinView;

        MatchCardView(Context context, String value, String pinyin) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(8), dp(5), dp(8), dp(7));
            setClickable(true);
            setFocusable(true);

            wordView = text(value, 15, COLOR_TEXT, true);
            wordView.setGravity(Gravity.CENTER);
            wordView.setMaxLines(2);
            wordView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(wordView, new LinearLayout.LayoutParams(-1, 0, 1f));

            pinyinView = text(pinyin, 11, 0xFF7C8797, false);
            pinyinView.setGravity(Gravity.CENTER);
            pinyinView.setSingleLine(true);
            pinyinView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(pinyinView, new LinearLayout.LayoutParams(-1, dp(18)));
            if (pinyin.isEmpty()) pinyinView.setVisibility(View.GONE);
            else registerPinyinView(pinyinView);
            setVisualState(NORMAL);
        }

        void setVisualState(int state) {
            int top = Color.WHITE;
            int bottom = 0xFFD5D5D5;
            int border = COLOR_BORDER;
            int textColor = COLOR_TEXT;
            if (state == SELECTED) {
                top = LearningUiKit.BLUE_SOFT;
                bottom = 0xFF84D8FF;
                border = COLOR_BLUE;
                textColor = COLOR_BLUE_DARK;
            } else if (state == CORRECT) {
                top = LearningUiKit.GREEN_SOFT;
                bottom = 0xFF9DD86B;
                border = COLOR_SUCCESS;
                textColor = 0xFF3F7D20;
            } else if (state == WRONG) {
                top = LearningUiKit.RED_SOFT;
                bottom = 0xFFF1A8AC;
                border = COLOR_ERROR;
                textColor = COLOR_ERROR_DARK;
            }
            setBackground(LearningUiKit.raisedSelector(top, bottom,
                    dp(14), border, dp(2), dp(4)));
            wordView.setTextColor(textColor);
            pinyinView.setTextColor(state == NORMAL ? 0xFF7C8797 : textColor);
        }
    }

    private final class WordChipView extends LinearLayout {
        final TextView wordView;
        final TextView pinyinView;

        WordChipView(Context context, WordToken token) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(15), dp(5), dp(15), dp(7));
            setClickable(true);
            setFocusable(true);
            setBackground(LearningUiKit.raisedSelector(Color.WHITE, 0xFFD5D5D5,
                    dp(13), COLOR_BORDER, dp(2), dp(4)));

            wordView = text(token.value, 17, COLOR_TEXT, true);
            wordView.setGravity(Gravity.CENTER);
            wordView.setSingleLine(true);
            addView(wordView, new LinearLayout.LayoutParams(-2, dp(27)));

            String pinyin = pinyinFor(token.value, "");
            pinyinView = text(pinyin, 11, 0xFF7C8797, false);
            pinyinView.setGravity(Gravity.CENTER);
            pinyinView.setSingleLine(true);
            addView(pinyinView, new LinearLayout.LayoutParams(-2, dp(18)));
            if (pinyin.isEmpty()) pinyinView.setVisibility(View.GONE);
            else registerPinyinView(pinyinView);
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
            setBackground(null);
            setPadding(0, 0, 0, 0);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.42f;
            float cy = getHeight() / 2f;
            float unit = Math.max(1f, Math.min(getWidth(), getHeight()) / 34f);
            int color = isPressed() ? COLOR_BLUE_DARK : COLOR_BLUE;

            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(cx - 9 * unit, cy - 4.5f * unit);
            path.lineTo(cx - 5 * unit, cy - 4.5f * unit);
            path.lineTo(cx + 1.2f * unit, cy - 10 * unit);
            path.lineTo(cx + 1.2f * unit, cy + 10 * unit);
            path.lineTo(cx - 5 * unit, cy + 4.5f * unit);
            path.lineTo(cx - 9 * unit, cy + 4.5f * unit);
            path.close();
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(dp(1.7f), 2.0f * unit));
            arc.set(cx - 2 * unit, cy - 8 * unit, cx + 10 * unit, cy + 8 * unit);
            canvas.drawArc(arc, -47, 94, false, paint);

            paint.setStrokeWidth(Math.max(dp(1.6f), 1.85f * unit));
            arc.set(cx, cy - 12 * unit, cx + 16 * unit, cy + 12 * unit);
            canvas.drawArc(arc, -45, 90, false, paint);

            paint.setStrokeWidth(Math.max(dp(1.4f), 1.65f * unit));
            arc.set(cx + 2 * unit, cy - 16 * unit, cx + 22 * unit, cy + 16 * unit);
            canvas.drawArc(arc, -43, 86, false, paint);
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
