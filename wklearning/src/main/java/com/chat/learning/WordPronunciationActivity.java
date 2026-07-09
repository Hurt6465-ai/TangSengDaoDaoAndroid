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

/**
 * 发音练习页：标准音、拼读音、录音、回放、ASR/评分插件入口合并到一个页面。
 * 不在学习模块内置收费评测；后续 sherpa-onnx/字节离线识别等做成独立插件后，通过 LearningAsrBridge 接入。
 */
public class WordPronunciationActivity extends AppCompatActivity {
    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_PINYIN = "pinyin";
    public static final String EXTRA_SPELLING_TEXT = "spelling_text";

    private static final int REQ_RECORD_AUDIO = 3021;
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
    private TextView checkButton;

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
        hero.setPadding(dp(20), dp(28), dp(20), dp(28));
        hero.setBackground(rounded(0xFFFFFFFF, dp(26), 0xFFE5E7EB, 1));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.setMargins(0, dp(28), 0, dp(18));
        page.addView(hero, heroLp);

        TextView wordView = text(word, 54, COLOR_TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        hero.addView(wordView, new LinearLayout.LayoutParams(-1, -2));

        TextView pinyinView = text(pinyin, 22, COLOR_BLUE, true);
        pinyinView.setGravity(Gravity.CENTER);
        pinyinView.setPadding(0, dp(8), 0, 0);
        hero.addView(pinyinView, new LinearLayout.LayoutParams(-1, -2));

        TextView spelling = text(spellingText, 14, COLOR_SUB, false);
        spelling.setGravity(Gravity.CENTER);
        spelling.setLineSpacing(dp(3), 1f);
        spelling.setPadding(0, dp(16), 0, 0);
        hero.addView(spelling, new LinearLayout.LayoutParams(-1, -2));

        status = text(getString(R.string.pronunciation_tip), 14, COLOR_SUB, false);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, 0, 0, dp(18));
        page.addView(status, statusLp);

        page.addView(button(getString(R.string.pronunciation_play_standard), COLOR_BLUE, 0xFFEFF6FF, v -> playStandard()), new LinearLayout.LayoutParams(-1, dp(54)));
        addSpace(page, 12);
        page.addView(button(getString(R.string.pronunciation_play_spelling), 0xFF7C3AED, 0xFFF5F3FF, v -> playSpelling()), new LinearLayout.LayoutParams(-1, dp(54)));
        addSpace(page, 12);
        recordButton = button(getString(R.string.pronunciation_start_record), COLOR_RED, 0xFFFFEEF2, v -> toggleRecord());
        page.addView(recordButton, new LinearLayout.LayoutParams(-1, dp(54)));
        addSpace(page, 12);
        playbackButton = button(getString(R.string.pronunciation_play_mine), COLOR_GREEN, 0xFFECFDF5, v -> playMine());
        playbackButton.setAlpha(0.45f);
        page.addView(playbackButton, new LinearLayout.LayoutParams(-1, dp(54)));
        addSpace(page, 12);
        checkButton = button(getString(R.string.pronunciation_local_check), COLOR_TEXT, 0xFFFFFFFF, v -> startLocalCheck());
        checkButton.setAlpha(0.45f);
        page.addView(checkButton, new LinearLayout.LayoutParams(-1, dp(54)));
    }

    private TextView button(String label, int fg, int bg, View.OnClickListener listener) {
        TextView view = text(label, 16, fg, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(bg, dp(18), 0xFFE5E7EB, 1));
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

    private void toggleRecord() {
        if (recording) {
            stopRecording(true);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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
        } catch (Throwable e) {
            recording = false;
            cleanupRecorder();
            Toast.makeText(this, getString(R.string.pronunciation_record_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording(boolean notify) {
        if (!recording) return;
        try {
            recorder.stop();
        } catch (Throwable ignored) {}
        cleanupRecorder();
        recording = false;
        recordButton.setText(getString(R.string.pronunciation_start_record));
        playbackButton.setAlpha(1f);
        checkButton.setAlpha(1f);
        if (notify) status.setText(getString(R.string.pronunciation_status_record_done));
    }

    private void cleanupRecorder() {
        try { if (recorder != null) recorder.release(); } catch (Throwable ignored) {}
        recorder = null;
    }

    private void playMine() {
        if (recordFile == null || !recordFile.exists() || recordFile.length() <= 0) {
            Toast.makeText(this, getString(R.string.pronunciation_no_record), Toast.LENGTH_SHORT).show();
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
        } catch (Throwable e) {
            releasePlayer();
            Toast.makeText(this, getString(R.string.pronunciation_play_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void startLocalCheck() {
        if (recordFile == null || !recordFile.exists() || recordFile.length() <= 0) {
            Toast.makeText(this, getString(R.string.pronunciation_no_record), Toast.LENGTH_SHORT).show();
            return;
        }
        boolean started = LearningAsrBridge.startPronunciationCheck(this, word, pinyin, recordFile.getAbsolutePath());
        if (started) status.setText(getString(R.string.pronunciation_status_checking));
    }

    private void releasePlayer() {
        try { if (player != null) player.release(); } catch (Throwable ignored) {}
        player = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startRecording();
            else Toast.makeText(this, getString(R.string.pronunciation_need_permission), Toast.LENGTH_SHORT).show();
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

    private void addSpace(LinearLayout parent, int dp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(dp)));
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
