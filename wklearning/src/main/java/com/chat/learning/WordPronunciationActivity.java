package com.chat.learning;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/** Google speech recognition plus local recording playback for pronunciation comparison. */
public class WordPronunciationActivity extends AppCompatActivity {
    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_PINYIN = "pinyin";
    public static final String EXTRA_SPELLING_TEXT = "spelling_text";

    private static final int REQ_RECORD_AUDIO = 3021;
    private static final int REQ_GOOGLE_RECOGNITION = 3022;
    private static final int COLOR_BG = 0xFFF5F7FB;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_SUB = 0xFF64748B;
    private static final int COLOR_BLUE = 0xFF2563EB;
    private static final int COLOR_GREEN = 0xFF059669;
    private static final int COLOR_RED = 0xFFE11D48;

    private String word;
    private String pinyin;
    private String spellingText;
    private File recordFile;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private boolean recording;
    private TextView status;
    private TextView recordButton;
    private TextView playbackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);

        word = getIntent().getStringExtra(EXTRA_WORD);
        pinyin = getIntent().getStringExtra(EXTRA_PINYIN);
        spellingText = getIntent().getStringExtra(EXTRA_SPELLING_TEXT);
        if (word == null || word.length() == 0) word = getString(R.string.word_unknown);
        if (pinyin == null) pinyin = "";
        if (spellingText == null || spellingText.length() == 0) spellingText = word;
        recordFile = new File(getCacheDir(), "word_pronunciation_" + System.currentTimeMillis() + ".m4a");
        buildLayout();
    }

    @Override
    protected void onDestroy() {
        stopRecording(false);
        releasePlayer();
        super.onDestroy();
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(20), dp(22), dp(22));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView back = text("‹", 32, COLOR_TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), -1));

        TextView title = text(getString(R.string.pronunciation_title), 18, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(20), dp(24), dp(20), dp(24));
        hero.setBackground(rounded(0xFFFFFFFF, dp(26), 0xFFE5E7EB, 1));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.setMargins(0, dp(24), 0, dp(16));
        page.addView(hero, heroLp);

        TextView wordView = text(word, 54, COLOR_TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        hero.addView(wordView, new LinearLayout.LayoutParams(-1, -2));

        TextView pinyinView = text(pinyin, 22, COLOR_BLUE, true);
        pinyinView.setGravity(Gravity.CENTER);
        pinyinView.setPadding(0, dp(8), 0, 0);
        hero.addView(pinyinView, new LinearLayout.LayoutParams(-1, -2));

        status = text(getString(R.string.pronunciation_tip), 14, COLOR_SUB, false);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(4), 1.05f);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, 0, 0, dp(16));
        page.addView(status, statusLp);

        page.addView(button(getString(R.string.pronunciation_play_standard), COLOR_BLUE, 0xFFEFF6FF,
                v -> playStandard()), new LinearLayout.LayoutParams(-1, dp(52)));
        addSpace(page, 10);
        page.addView(button(getString(R.string.pronunciation_play_spelling), 0xFF7C3AED, 0xFFF5F3FF,
                v -> playSpelling()), new LinearLayout.LayoutParams(-1, dp(52)));
        addSpace(page, 10);
        page.addView(button(getString(R.string.pronunciation_google_recognize), COLOR_TEXT, 0xFFFFFFFF,
                v -> startGoogleRecognition()), new LinearLayout.LayoutParams(-1, dp(52)));
        addSpace(page, 10);

        LinearLayout recordRow = new LinearLayout(this);
        recordRow.setOrientation(LinearLayout.HORIZONTAL);
        recordRow.setGravity(Gravity.CENTER);
        recordButton = button(getString(R.string.pronunciation_start_record), COLOR_RED, 0xFFFFEEF2,
                v -> toggleRecord());
        recordRow.addView(recordButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        addHorizontalSpace(recordRow, 10);
        playbackButton = button(getString(R.string.pronunciation_play_mine), COLOR_GREEN, 0xFFECFDF5,
                v -> playMine());
        playbackButton.setAlpha(0.45f);
        recordRow.addView(playbackButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        page.addView(recordRow, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private TextView button(String label, int fg, int bg, View.OnClickListener listener) {
        TextView view = text(label, 15, fg, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(bg, dp(17), 0xFFE5E7EB, 1));
        view.setOnClickListener(listener);
        return view;
    }

    private void playStandard() {
        LearningTtsBridge.speak(this, word, LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_WORD);
        status.setText(getString(R.string.pronunciation_status_standard));
    }

    private void playSpelling() {
        LearningTtsBridge.speak(this, spellingText, LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_SPELLING);
        status.setText(getString(R.string.pronunciation_status_spelling));
    }

    private void startGoogleRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.pronunciation_recognition_prompt, word));
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        try {
            startActivityForResult(intent, REQ_GOOGLE_RECOGNITION);
        } catch (Throwable error) {
            Toast.makeText(this, R.string.pronunciation_google_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_GOOGLE_RECOGNITION) return;
        if (resultCode != RESULT_OK || data == null) {
            status.setText(R.string.pronunciation_recognition_cancelled);
            return;
        }
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            status.setText(R.string.pronunciation_recognition_empty);
            return;
        }
        String recognized = results.get(0) == null ? "" : results.get(0).trim();
        int match = textMatchPercent(word, recognized);
        status.setText(getString(R.string.pronunciation_recognition_result, recognized, match));
    }

    private int textMatchPercent(String expected, String actual) {
        int[] left = toCodePoints(normalize(expected));
        int[] right = toCodePoints(normalize(actual));
        int max = Math.max(left.length, right.length);
        if (max == 0) return 100;
        int distance = levenshtein(left, right);
        return Math.max(0, Math.min(100, Math.round((1f - distance / (float) max) * 100f)));
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
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length];
    }

    private void toggleRecord() {
        if (recording) stopRecording(true);
        else startRecording();
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        try {
            releasePlayer();
            recordFile = new File(getCacheDir(), "word_pronunciation_" + System.currentTimeMillis() + ".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(recordFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            recordButton.setText(getString(R.string.pronunciation_stop_record));
            status.setText(getString(R.string.pronunciation_status_recording));
        } catch (Throwable error) {
            recording = false;
            cleanupRecorder();
            Toast.makeText(this, R.string.pronunciation_record_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording(boolean notify) {
        if (!recording) return;
        try { recorder.stop(); } catch (Throwable ignored) { }
        cleanupRecorder();
        recording = false;
        recordButton.setText(getString(R.string.pronunciation_start_record));
        playbackButton.setAlpha(1f);
        if (notify) status.setText(getString(R.string.pronunciation_status_record_done));
    }

    private void cleanupRecorder() {
        try { if (recorder != null) recorder.release(); } catch (Throwable ignored) { }
        recorder = null;
    }

    private void playMine() {
        if (recordFile == null || !recordFile.exists() || recordFile.length() <= 0) {
            Toast.makeText(this, R.string.pronunciation_no_record, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            releasePlayer();
            player = new MediaPlayer();
            player.setDataSource(recordFile.getAbsolutePath());
            player.setOnCompletionListener(mp -> releasePlayer());
            player.prepare();
            player.start();
            status.setText(getString(R.string.pronunciation_status_play_mine));
        } catch (Throwable error) {
            releasePlayer();
            Toast.makeText(this, R.string.pronunciation_play_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void releasePlayer() {
        try { if (player != null) player.release(); } catch (Throwable ignored) { }
        player = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startRecording();
            else Toast.makeText(this, R.string.pronunciation_need_permission, Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private void addSpace(LinearLayout parent, int value) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(value)));
    }

    private void addHorizontalSpace(LinearLayout parent, int value) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(dp(value), 1));
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
}
