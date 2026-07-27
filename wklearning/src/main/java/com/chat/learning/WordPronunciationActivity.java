package com.chat.learning;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Locale;

/** One-tap sherpa-onnx/system speech recognition and recording popup. */
public class WordPronunciationActivity extends AppCompatActivity {
    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_PINYIN = "pinyin";
    public static final String EXTRA_SPELLING_TEXT = "spelling_text";

    private static final int REQ_RECORD_AUDIO = 3021;
    private static final int COLOR_TEXT = 0xFF151922;
    private static final int COLOR_SUB = 0xFF6B7280;
    private static final int COLOR_ACCENT = 0xFF6761D7;
    private static final int COLOR_SOFT = 0xFFF2F3F5;
    private static final int COLOR_SUCCESS = 0xFF138A63;

    private String word;
    private String pinyin;
    private File recordingFile;
    private MediaPlayer player;
    private PronunciationCaptureSession captureSession;

    private LinearLayout panel;
    private TextView statusView;
    private TextView partialView;
    private TextView micButton;
    private TextView micCaption;
    private LinearLayout resultGroup;
    private TextView recognizedView;
    private TextView matchView;
    private TextView playMineButton;
    private boolean practicing;
    private SherpaOnnxRecognizer.ModelListener sherpaModelListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        word = safe(getIntent().getStringExtra(EXTRA_WORD), getString(R.string.word_unknown));
        pinyin = safe(getIntent().getStringExtra(EXTRA_PINYIN), "");
        configureWindow();
        buildLayout();
        prepareOfflineRecognizer();
        if (savedInstanceState == null) {
            panel.postDelayed(this::ensurePermissionAndStart, 180L);
        }
    }

    @Override
    protected void onDestroy() {
        SherpaOnnxRecognizer.removeModelListener(sherpaModelListener);
        sherpaModelListener = null;
        releaseCapture();
        releasePlayer();
        super.onDestroy();
    }


    private void prepareOfflineRecognizer() {
        sherpaModelListener = (type, state) -> {
            if (isFinishing() || isDestroyed() || practicing
                    || resultGroup.getVisibility() == View.VISIBLE) return;
            if (state == SherpaOnnxRecognizer.ModelState.DOWNLOADING) {
                statusView.setText(R.string.pronunciation_sherpa_downloading);
            } else if (state == SherpaOnnxRecognizer.ModelState.IMPORTING) {
                statusView.setText(R.string.pronunciation_sherpa_importing);
            } else if (state == SherpaOnnxRecognizer.ModelState.PREPARING) {
                statusView.setText(R.string.pronunciation_sherpa_loading);
            } else if (state == SherpaOnnxRecognizer.ModelState.READY) {
                statusView.setText(type == SherpaOnnxRecognizer.ModelType.SENSE_VOICE
                        ? R.string.pronunciation_sensevoice_ready
                        : R.string.pronunciation_sherpa_ready);
            } else if (state == SherpaOnnxRecognizer.ModelState.NOT_INSTALLED) {
                statusView.setText(R.string.pronunciation_sherpa_not_installed);
            } else {
                statusView.setText(R.string.pronunciation_one_tap_hint);
            }
        };
        SherpaOnnxRecognizer.prepare(this, sherpaModelListener);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = Build.VERSION.SDK_INT >= 31 ? 0.08f : 0.18f;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                attributes.getClass().getMethod("setBlurBehindRadius", int.class)
                        .invoke(attributes, dp(24));
            } catch (Throwable ignored) { }
        }
        window.setAttributes(attributes);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClickable(true);
        root.setOnClickListener(v -> finish());
        setContentView(root);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(20));
        panel.setBackground(rounded(0xF7FFFFFF, dp(26), 0xCCFFFFFF, dp(1)));
        panel.setElevation(dp(16));
        panel.setClickable(true);
        panel.setOnClickListener(v -> { });

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        panelLp.setMargins(dp(22), dp(24), dp(22), dp(24));
        root.addView(panel, panelLp);

        FrameLayout header = new FrameLayout(this);
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView title = text(getString(R.string.pronunciation_title), 17, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        TextView close = text("×", 25, COLOR_SUB, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(getString(android.R.string.cancel));
        close.setOnClickListener(v -> finish());
        header.addView(close, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.END));

        TextView wordView = text(word, 48, COLOR_TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wordLp = new LinearLayout.LayoutParams(-1, -2);
        wordLp.setMargins(0, dp(12), 0, 0);
        panel.addView(wordView, wordLp);

        if (!pinyin.isEmpty()) {
            TextView pinyinView = text(pinyin, 20, COLOR_ACCENT, false);
            pinyinView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pinyinLp = new LinearLayout.LayoutParams(-1, -2);
            pinyinLp.setMargins(0, dp(3), 0, 0);
            panel.addView(pinyinView, pinyinLp);
        }

        TextView original = compactButton("◖))  " + getString(R.string.pronunciation_play_standard),
                v -> playStandard());
        LinearLayout.LayoutParams originalLp = new LinearLayout.LayoutParams(-2, dp(38));
        originalLp.setMargins(0, dp(14), 0, 0);
        panel.addView(original, originalLp);

        statusView = text(getString(R.string.pronunciation_one_tap_hint), 14, COLOR_SUB, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setLineSpacing(dp(3), 1.05f);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(16), 0, 0);
        panel.addView(statusView, statusLp);

        partialView = text("", 15, COLOR_TEXT, true);
        partialView.setGravity(Gravity.CENTER);
        partialView.setVisibility(View.GONE);
        LinearLayout.LayoutParams partialLp = new LinearLayout.LayoutParams(-1, -2);
        partialLp.setMargins(0, dp(6), 0, 0);
        panel.addView(partialView, partialLp);

        micButton = text("●", 28, Color.WHITE, true);
        micButton.setGravity(Gravity.CENTER);
        micButton.setBackground(rounded(COLOR_ACCENT, dp(34), 0, 0));
        micButton.setContentDescription(getString(R.string.pronunciation_start_once));
        micButton.setOnClickListener(v -> {
            if (!practicing) ensurePermissionAndStart();
        });
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(68), dp(68));
        micLp.gravity = Gravity.CENTER_HORIZONTAL;
        micLp.setMargins(0, dp(16), 0, 0);
        panel.addView(micButton, micLp);

        micCaption = text(getString(R.string.pronunciation_start_once), 13, COLOR_SUB, true);
        micCaption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(-1, -2);
        captionLp.setMargins(0, dp(7), 0, 0);
        panel.addView(micCaption, captionLp);

        resultGroup = new LinearLayout(this);
        resultGroup.setOrientation(LinearLayout.VERTICAL);
        resultGroup.setGravity(Gravity.CENTER_HORIZONTAL);
        resultGroup.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(-1, -2);
        resultLp.setMargins(0, dp(16), 0, 0);
        panel.addView(resultGroup, resultLp);

        TextView resultTitle = text(getString(R.string.pronunciation_result_title), 13, COLOR_SUB, true);
        resultTitle.setGravity(Gravity.CENTER);
        resultGroup.addView(resultTitle, new LinearLayout.LayoutParams(-1, -2));

        recognizedView = text("", 22, COLOR_TEXT, true);
        recognizedView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams recognizedLp = new LinearLayout.LayoutParams(-1, -2);
        recognizedLp.setMargins(0, dp(5), 0, 0);
        resultGroup.addView(recognizedView, recognizedLp);

        matchView = text("", 29, COLOR_SUCCESS, true);
        matchView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams matchLp = new LinearLayout.LayoutParams(-1, -2);
        matchLp.setMargins(0, dp(5), 0, 0);
        resultGroup.addView(matchView, matchLp);

        TextView compareHint = text(getString(R.string.pronunciation_compare_hint), 12, COLOR_SUB, false);
        compareHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams compareLp = new LinearLayout.LayoutParams(-1, -2);
        compareLp.setMargins(0, dp(3), 0, dp(10));
        resultGroup.addView(compareHint, compareLp);

        LinearLayout compareRow = new LinearLayout(this);
        compareRow.setOrientation(LinearLayout.HORIZONTAL);
        compareRow.setGravity(Gravity.CENTER);
        resultGroup.addView(compareRow, new LinearLayout.LayoutParams(-1, dp(40)));

        TextView playOriginal = compactButton("◖))  " + getString(R.string.pronunciation_original_short),
                v -> playStandard());
        compareRow.addView(playOriginal, new LinearLayout.LayoutParams(0, dp(40), 1f));
        addHorizontalSpace(compareRow, 10);
        playMineButton = compactButton("▶  " + getString(R.string.pronunciation_mine_short),
                v -> playMine());
        compareRow.addView(playMineButton, new LinearLayout.LayoutParams(0, dp(40), 1f));

        TextView retry = text(getString(R.string.pronunciation_try_again), 14, COLOR_ACCENT, true);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(rounded(0xFFF4F3FF, dp(18), 0, 0));
        retry.setOnClickListener(v -> ensurePermissionAndStart());
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-1, dp(40));
        retryLp.setMargins(0, dp(10), 0, 0);
        resultGroup.addView(retry, retryLp);
    }

    private void ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        startPractice();
    }

    private void startPractice() {
        releaseCapture();
        releasePlayer();
        recordingFile = null;
        practicing = true;
        resultGroup.setVisibility(View.GONE);
        partialView.setText("");
        partialView.setVisibility(View.GONE);
        statusView.setText(R.string.pronunciation_status_preparing);
        micButton.setText("■");
        micButton.setScaleX(1f);
        micButton.setScaleY(1f);
        micButton.setAlpha(1f);
        micCaption.setText(R.string.pronunciation_status_preparing);

        captureSession = new PronunciationCaptureSession(this, word,
                new PronunciationCaptureSession.Listener() {
                    @Override public void onStateChanged(PronunciationCaptureSession.State state) {
                        runOnUiThread(() -> {
                            if (isFinishing()) return;
                            if (state == PronunciationCaptureSession.State.LISTENING) {
                                statusView.setText(R.string.pronunciation_status_recording_and_recognizing);
                                micCaption.setText(R.string.pronunciation_status_recording_and_recognizing);
                            } else if (state == PronunciationCaptureSession.State.PROCESSING) {
                                statusView.setText(R.string.pronunciation_status_processing);
                                micCaption.setText(R.string.pronunciation_status_processing);
                            }
                        });
                    }

                    @Override public void onRms(float rmsDb) {
                        runOnUiThread(() -> {
                            if (!practicing || isFinishing()) return;
                            float normalized = Math.max(0f, Math.min(1f, (rmsDb + 48f) / 42f));
                            float scale = 1f + normalized * 0.11f;
                            micButton.animate().scaleX(scale).scaleY(scale).setDuration(70).start();
                            micButton.setAlpha(0.78f + normalized * 0.22f);
                        });
                    }

                    @Override public void onPartialResult(String text) {
                        runOnUiThread(() -> {
                            if (text == null || text.isEmpty() || isFinishing()) return;
                            partialView.setText(text);
                            partialView.setVisibility(View.VISIBLE);
                        });
                    }

                    @Override public void onFinished(PronunciationCaptureSession.Result result) {
                        runOnUiThread(() -> showResult(result));
                    }
                });
        captureSession.start();
    }

    private void showResult(PronunciationCaptureSession.Result result) {
        practicing = false;
        micButton.setText("●");
        micButton.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
        micCaption.setText(R.string.pronunciation_start_once);
        recordingFile = result == null ? null : result.recordingFile;
        String recognized = result == null ? "" : safe(result.recognizedText, "");
        int match = recognized.isEmpty() ? 0 : textMatchPercent(word, recognized);

        partialView.setVisibility(View.GONE);
        resultGroup.setVisibility(View.VISIBLE);
        recognizedView.setText(recognized.isEmpty()
                ? getString(R.string.pronunciation_unrecognized)
                : recognized);
        matchView.setText(getString(R.string.pronunciation_match_value, match));
        matchView.setTextColor(match >= 80 ? COLOR_SUCCESS : match >= 50 ? 0xFFD97706 : 0xFFCA3854);
        playMineButton.setEnabled(recordingFile != null);
        playMineButton.setAlpha(recordingFile == null ? 0.42f : 1f);
        if (recognized.isEmpty()) {
            statusView.setText(R.string.pronunciation_recognition_empty);
        } else if (result != null && "sherpa-onnx".equals(result.recognitionEngine)) {
            statusView.setText(R.string.pronunciation_result_ready_local);
        } else {
            statusView.setText(R.string.pronunciation_result_ready);
        }
    }

    private void playStandard() {
        LearningTtsBridge.speak(this, word,
                LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_WORD);
        statusView.setText(R.string.pronunciation_status_standard);
    }

    private void playMine() {
        if (recordingFile == null || !recordingFile.isFile() || recordingFile.length() <= 44) {
            Toast.makeText(this, R.string.pronunciation_no_record, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            releasePlayer();
            player = new MediaPlayer();
            player.setDataSource(recordingFile.getAbsolutePath());
            player.setOnCompletionListener(mp -> releasePlayer());
            player.prepare();
            player.start();
            statusView.setText(R.string.pronunciation_status_play_mine);
        } catch (Throwable error) {
            releasePlayer();
            Toast.makeText(this, R.string.pronunciation_play_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void releaseCapture() {
        if (captureSession != null) captureSession.release();
        captureSession = null;
        practicing = false;
    }

    private void releasePlayer() {
        try { if (player != null) player.release(); } catch (Throwable ignored) { }
        player = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startPractice();
        } else {
            Toast.makeText(this, R.string.pronunciation_need_permission, Toast.LENGTH_SHORT).show();
        }
    }

    private int textMatchPercent(String expected, String actual) {
        int[] left = toCodePoints(normalize(expected));
        int[] right = toCodePoints(normalize(actual));
        int max = Math.max(left.length, right.length);
        if (max == 0) return 100;
        int distance = levenshtein(left, right);
        return Math.max(0, Math.min(100,
                Math.round((1f - distance / (float) max) * 100f)));
    }

    private String normalize(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < lower.length(); ) {
            int codePoint = lower.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) out.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        return out.toString();
    }

    private int[] toCodePoints(String value) {
        int count = value.codePointCount(0, value.length());
        int[] result = new int[count];
        int index = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            result[index++] = codePoint;
            offset += Character.charCount(codePoint);
        }
        return result;
    }

    private int levenshtein(int[] left, int[] right) {
        int[] previous = new int[right.length + 1];
        int[] current = new int[right.length + 1];
        for (int j = 0; j <= right.length; j++) previous[j] = j;
        for (int i = 1; i <= left.length; i++) {
            current[0] = i;
            for (int j = 1; j <= right.length; j++) {
                int cost = left[i - 1] == right[j - 1] ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length];
    }

    private TextView compactButton(String label, View.OnClickListener listener) {
        TextView view = text(label, 13, COLOR_TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(rounded(COLOR_SOFT, dp(19), 0, 0));
        view.setOnClickListener(listener);
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private void addHorizontalSpace(LinearLayout parent, int dp) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(dp(dp), 1));
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
