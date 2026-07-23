package com.chat.speech.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.speech.SpeechManager;
import com.chat.speech.SpeechPrefs;
import com.chat.speech.debug.SpeechDebugLog;
import com.chat.speech.importer.ByteDanceOfflinePackageImporter;
import com.chat.speech.model.TtsSource;
import com.chat.speech.model.TtsVoice;

import java.util.List;
import java.util.Locale;

/** Simple user-facing TTS settings. */
public final class SpeechSettingsActivity extends Activity {
    private static final int REQ_IMPORT_MODEL = 8201;

    private SpeechPrefs prefs;
    private LinearLayout root;
    private EditText rateInput;
    private EditText pitchInput;
    private EditText testInput;

    public static void open(Context context) {
        context.startActivity(new Intent(context, SpeechSettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new SpeechPrefs(this);
        prefs.setMixedReadEnabled(true);
        normalizeSimpleSelection();
        render();
    }

    private void normalizeSimpleSelection() {
        TtsSource active = prefs.getActiveSource();
        if (active == null || (!TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(active.type)
                && !TtsSource.TYPE_EDGE_WEBSOCKET.equals(active.type))) {
            prefs.setActiveSourceId(TtsSource.edgeWebSocketTemplate().id);
        }
        if (!prefs.isSimpleMicrosoftVoice(prefs.getZhVoice())) {
            prefs.setZhVoice(SpeechPrefs.DEFAULT_ZH_VOICE);
        }
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 249, 252));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        addTitle();
        addEngineSection();
        addVoiceSection();
        addParameterSection();
        addTestSection();
    }

    private void addTitle() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 38, Color.rgb(17, 24, 39), false);
        back.setGravity(Gravity.CENTER);
        line.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        back.setOnClickListener(v -> finish());

        TextView title = text("语音设置", 24, Color.rgb(17, 24, 39), true);
        line.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        title.setOnLongClickListener(v -> {
            showDebugLog();
            return true;
        });

        root.addView(line);
        addSpace(12);
    }

    private void addEngineSection() {
        LinearLayout card = baseCard();
        card.addView(label("TTS 引擎"));

        TtsSource active = prefs.getActiveSource();
        boolean byteDance = active != null
                && TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(active.type);

        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setPadding(dp(4), dp(4), dp(4), dp(4));
        selector.setBackground(rounded(Color.rgb(239, 242, 247), dp(14), Color.TRANSPARENT, 0));

        TextView microsoft = engineButton("微软", !byteDance);
        TextView byteButton = engineButton("字节", byteDance);
        selector.addView(microsoft, new LinearLayout.LayoutParams(0, dp(44), 1f));
        selector.addView(byteButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        card.addView(selector);

        microsoft.setOnClickListener(v -> {
            saveParameters();
            prefs.setActiveSourceId(TtsSource.edgeWebSocketTemplate().id);
            if (!prefs.isSimpleMicrosoftVoice(prefs.getZhVoice())) {
                prefs.setZhVoice(SpeechPrefs.DEFAULT_ZH_VOICE);
            }
            render();
        });
        byteButton.setOnClickListener(v -> {
            saveParameters();
            prefs.setActiveSourceId(TtsSource.byteDanceOffline().id);
            render();
        });

        if (byteDance) {
            addDivider(card);
            card.addView(selectorRow(
                    "导入模型",
                    prefs.isByteDancePackageReady() ? "已导入" : "未导入",
                    "导入",
                    this::openImportPicker
            ));
        }
        root.addView(card);
    }

    private void addVoiceSection() {
        LinearLayout card = baseCard();
        TtsSource active = prefs.getActiveSource();
        boolean byteDance = active != null
                && TtsSource.TYPE_BYTEDANCE_OFFLINE.equals(active.type);
        String voiceCode = byteDance ? prefs.getByteDanceVoice() : prefs.getZhVoice();
        String voiceName = simpleVoiceName(voiceCode);
        card.addView(selectorRow("发音人", voiceName, "选择", byteDance
                ? this::chooseByteDanceVoice
                : this::chooseMicrosoftVoice));
        root.addView(card);
    }

    private void addParameterSection() {
        LinearLayout card = baseCard();
        card.addView(label("朗读参数"));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.TOP);

        LinearLayout rateBox = parameterBox("语速", formatValue(prefs.getRateValue()), "0.50–1.80");
        rateInput = (EditText) rateBox.getTag();
        LinearLayout.LayoutParams rateLp = new LinearLayout.LayoutParams(0, -2, 1f);
        rateLp.setMargins(0, 0, dp(6), 0);
        line.addView(rateBox, rateLp);

        LinearLayout pitchBox = parameterBox("音调", formatValue(prefs.getPitchValue()), "0.50–1.50");
        pitchInput = (EditText) pitchBox.getTag();
        LinearLayout.LayoutParams pitchLp = new LinearLayout.LayoutParams(0, -2, 1f);
        pitchLp.setMargins(dp(6), 0, 0, 0);
        line.addView(pitchBox, pitchLp);

        card.addView(line);
        root.addView(card);
    }

    private void addTestSection() {
        LinearLayout card = baseCard();
        card.addView(label("试听"));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        testInput = new EditText(this);
        testInput.setTextSize(16);
        testInput.setTextColor(Color.rgb(17, 24, 39));
        testInput.setHintTextColor(Color.rgb(156, 163, 175));
        testInput.setHint("输入中文、缅文、英文或带调拼音");
        testInput.setMinLines(1);
        testInput.setMaxLines(4);
        testInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        testInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        testInput.setPadding(dp(13), dp(9), dp(13), dp(9));
        testInput.setBackground(rounded(
                Color.rgb(249, 250, 251),
                dp(13),
                Color.rgb(209, 213, 219),
                1
        ));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, -2, 1f);
        inputLp.setMargins(0, 0, dp(10), 0);
        line.addView(testInput, inputLp);

        TextView play = text("试听", 15, Color.WHITE, true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(rounded(Color.rgb(24, 119, 242), dp(13), Color.TRANSPARENT, 0));
        line.addView(play, new LinearLayout.LayoutParams(dp(72), dp(48)));
        play.setOnClickListener(v -> playInput());

        card.addView(line);
        root.addView(card);
    }

    private void playInput() {
        saveParameters();
        String value = testInput == null ? "" : testInput.getText().toString().trim();
        if (value.isEmpty()) {
            toast("请输入试听内容");
            return;
        }
        hideKeyboard();
        SpeechManager.speak(this, value);
    }

    private void saveParameters() {
        if (rateInput != null) {
            prefs.setRateValue(parseValue(rateInput.getText().toString(), prefs.getRateValue()));
            rateInput.setText(formatValue(prefs.getRateValue()));
        }
        if (pitchInput != null) {
            prefs.setPitchValue(parseValue(pitchInput.getText().toString(), prefs.getPitchValue()));
            pitchInput.setText(formatValue(prefs.getPitchValue()));
        }
    }

    private LinearLayout parameterBox(String title, String value, String range) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView name = text(title, 14, Color.rgb(55, 65, 81), true);
        box.addView(name);

        EditText input = new EditText(this);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(18);
        input.setTextColor(Color.rgb(17, 24, 39));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setPadding(dp(8), dp(8), dp(8), dp(8));
        input.setBackground(rounded(
                Color.rgb(249, 250, 251),
                dp(12),
                Color.rgb(209, 213, 219),
                1
        ));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(48));
        inputLp.setMargins(0, dp(7), 0, dp(4));
        box.addView(input, inputLp);

        TextView rangeView = text(range, 11, Color.rgb(156, 163, 175), false);
        rangeView.setGravity(Gravity.CENTER);
        box.addView(rangeView);
        box.setTag(input);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveParameters();
        });
        return box;
    }

    private void chooseMicrosoftVoice() {
        List<TtsVoice> voices = prefs.getSimpleMicrosoftVoices();
        CharSequence[] items = new CharSequence[voices.size()];
        for (int i = 0; i < voices.size(); i++) items[i] = voices.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("发音人")
                .setItems(items, (dialog, which) -> {
                    prefs.setZhVoice(voices.get(which).code);
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void chooseByteDanceVoice() {
        List<TtsVoice> voices = prefs.getByteDanceVoices();
        if (voices.isEmpty()) {
            openImportPicker();
            return;
        }
        CharSequence[] items = new CharSequence[voices.size()];
        for (int i = 0; i < voices.size(); i++) items[i] = voices.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("发音人")
                .setItems(items, (dialog, which) -> {
                    prefs.setByteDanceVoice(voices.get(which).code);
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String simpleVoiceName(String code) {
        TtsVoice voice = prefs.findVoice(code);
        if (voice != null && voice.name != null && !voice.name.trim().isEmpty()) return voice.name;
        return code == null ? "" : code;
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_MODEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_MODEL || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        toast("正在导入");
        new Thread(() -> {
            try {
                ByteDanceOfflinePackageImporter.importFromUri(this, uri);
                runOnUiThread(() -> {
                    prefs.setActiveSourceId(TtsSource.byteDanceOffline().id);
                    toast("导入成功");
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast("导入失败：" + safeMessage(error)));
            }
        }, "bytedance-model-import").start();
    }

    private View selectorRow(String title, String value, String action, Runnable click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView titleView = text(title, 16, Color.rgb(17, 24, 39), true);
        row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView valueView = text(value, 14, Color.rgb(75, 85, 99), false);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(0, -2, 1.2f);
        valueLp.setMargins(dp(8), 0, dp(10), 0);
        row.addView(valueView, valueLp);

        TextView actionView = text(action, 14, Color.rgb(24, 119, 242), true);
        actionView.setGravity(Gravity.CENTER);
        actionView.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.addView(actionView);

        row.setOnClickListener(v -> click.run());
        actionView.setOnClickListener(v -> click.run());
        return row;
    }

    private TextView engineButton(String label, boolean selected) {
        TextView button = text(
                label,
                16,
                selected ? Color.rgb(17, 24, 39) : Color.rgb(107, 114, 128),
                selected
        );
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(
                selected ? Color.WHITE : Color.TRANSPARENT,
                dp(11),
                selected ? Color.rgb(225, 229, 236) : Color.TRANSPARENT,
                selected ? 1 : 0
        ));
        return button;
    }

    private TextView label(String value) {
        TextView label = text(value, 14, Color.rgb(75, 85, 99), true);
        label.setPadding(0, 0, 0, dp(10));
        return label;
    }

    private LinearLayout baseCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(Color.WHITE, dp(18), Color.rgb(232, 237, 246), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(238, 241, 246));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.setMargins(0, dp(13), 0, dp(11));
        parent.addView(divider, lp);
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

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private void addSpace(int value) {
        root.addView(new View(this), new LinearLayout.LayoutParams(1, dp(value)));
    }

    private float parseValue(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim().replace(',', '.'));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String formatValue(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    private void showDebugLog() {
        EditText view = new EditText(this);
        view.setText(SpeechDebugLog.read(this));
        view.setTextSize(11);
        view.setMinLines(16);
        view.setGravity(Gravity.TOP | Gravity.START);
        view.setTextIsSelectable(true);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this)
                .setTitle("诊断日志")
                .setView(view)
                .setPositiveButton("复制", (dialog, which) -> {
                    ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (manager != null) {
                        manager.setPrimaryClip(ClipData.newPlainText(
                                "speech_debug.log",
                                SpeechDebugLog.read(this)
                        ));
                    }
                })
                .setNeutralButton("清空", (dialog, which) -> SpeechDebugLog.clear(this))
                .setNegativeButton("关闭", null)
                .show();
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.trim().isEmpty() ? "未知错误" : message.trim();
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
