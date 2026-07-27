package com.chat.learning;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/** User-facing download/import manager for offline sherpa-onnx ASR models. */
public final class SherpaModelSettingsActivity extends AppCompatActivity {
    private static final int REQ_IMPORT_ZIPFORMER = 9101;
    private static final int REQ_IMPORT_SENSE_VOICE = 9102;

    private LinearLayout root;
    private SherpaOnnxRecognizer.ModelListener listener;
    private boolean operationRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listener = (type, state) -> {
            if (!isFinishing() && !isDestroyed()) render();
        };
        SherpaOnnxRecognizer.addModelListener(listener);
        render();
        SherpaOnnxRecognizer.prepare(this, listener);
    }

    @Override
    protected void onDestroy() {
        SherpaOnnxRecognizer.removeModelListener(listener);
        super.onDestroy();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF6F7FB);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        addHeader();
        TextView intro = text(getString(R.string.asr_model_intro), 14, 0xFF6B7280, false);
        intro.setLineSpacing(dp(3), 1.08f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2);
        introLp.setMargins(dp(2), 0, dp(2), dp(14));
        root.addView(intro, introLp);

        addModelCard(SherpaOnnxRecognizer.ModelType.ZIPFORMER,
                getString(R.string.asr_model_zipformer_title),
                getString(R.string.asr_model_zipformer_desc),
                getString(R.string.asr_model_zipformer_size));
        addModelCard(SherpaOnnxRecognizer.ModelType.SENSE_VOICE,
                getString(R.string.asr_model_sensevoice_title),
                getString(R.string.asr_model_sensevoice_desc),
                getString(R.string.asr_model_sensevoice_size));

        TextView note = text(getString(R.string.asr_model_import_note), 12, 0xFF8A93A3, false);
        note.setLineSpacing(dp(3), 1.08f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(dp(2), dp(2), dp(2), 0);
        root.addView(note, noteLp);
    }

    private void addHeader() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 38, 0xFF111827, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        line.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));

        TextView title = text(getString(R.string.asr_model_title), 24, 0xFF111827, true);
        line.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(line);
    }

    private void addModelCard(SherpaOnnxRecognizer.ModelType type, String title,
                              String description, String size) {
        boolean selected = SherpaOnnxRecognizer.getSelectedModel(this) == type;
        boolean installed = SherpaOnnxRecognizer.isInstalled(this, type);
        SherpaOnnxRecognizer.ModelState state = SherpaOnnxRecognizer.getState(this, type);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(selected ? 0xFFF3F2FF : Color.WHITE, dp(18),
                selected ? 0xFF716AE8 : 0xFFE5E9F0, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(12));
        root.addView(card, cardLp);

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(titleLine, new LinearLayout.LayoutParams(-1, -2));

        TextView titleView = text(title, 17, 0xFF111827, true);
        titleLine.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView badge = text(selected ? getString(R.string.asr_model_selected)
                : getString(R.string.asr_model_choose), 12,
                selected ? Color.WHITE : 0xFF655FD4, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));
        badge.setBackground(rounded(selected ? 0xFF6761D7 : 0xFFEDEBFF,
                dp(12), Color.TRANSPARENT, 0));
        titleLine.addView(badge);
        badge.setEnabled(!operationRunning);
        badge.setAlpha(operationRunning ? 0.5f : 1f);
        badge.setOnClickListener(v -> choose(type));

        TextView descView = text(description, 13, 0xFF687184, false);
        descView.setLineSpacing(dp(2), 1.06f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.setMargins(0, dp(6), 0, 0);
        card.addView(descView, descLp);

        String statusText = modelStatus(type, state, installed, size);
        TextView status = text(statusText, 13,
                state == SherpaOnnxRecognizer.ModelState.FAILED ? 0xFFCA3854 : 0xFF4B5563,
                true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(10), 0, 0);
        card.addView(status, statusLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.setMargins(0, dp(13), 0, 0);
        card.addView(actions, actionsLp);

        TextView download = actionButton(installed
                ? getString(R.string.asr_model_redownload)
                : getString(R.string.asr_model_download), true);
        TextView importButton = actionButton(getString(R.string.asr_model_import), false);
        actions.addView(download, weightedButtonLp(true));
        actions.addView(importButton, weightedButtonLp(false));
        download.setEnabled(!operationRunning);
        importButton.setEnabled(!operationRunning);
        download.setAlpha(operationRunning ? 0.5f : 1f);
        importButton.setAlpha(operationRunning ? 0.5f : 1f);
        download.setOnClickListener(v -> confirmDownload(type, title, size));
        importButton.setOnClickListener(v -> openImportPicker(type));

        if (installed) {
            TextView delete = text(getString(R.string.asr_model_delete), 13, 0xFFB83B4B, true);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(dp(10), dp(9), dp(10), dp(6));
            card.addView(delete, new LinearLayout.LayoutParams(-1, -2));
            delete.setEnabled(!operationRunning);
            delete.setAlpha(operationRunning ? 0.5f : 1f);
            delete.setOnClickListener(v -> confirmDelete(type, title));
        }
    }

    private void choose(SherpaOnnxRecognizer.ModelType type) {
        if (operationRunning) return;
        SherpaOnnxRecognizer.selectModel(this, type);
        render();
    }

    private void confirmDownload(SherpaOnnxRecognizer.ModelType type, String name, String size) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.asr_model_download_confirm_title))
                .setMessage(getString(R.string.asr_model_download_confirm_message, name, size))
                .setPositiveButton(getString(R.string.asr_model_download),
                        (dialog, which) -> startDownload(type))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startDownload(SherpaOnnxRecognizer.ModelType type) {
        operationRunning = true;
        render();
        SherpaOnnxRecognizer.download(this, type, listener,
                (success, message) -> finishOperation(message));
    }

    private void openImportPicker(SherpaOnnxRecognizer.ModelType type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed", "application/octet-stream"
        });
        int request = type == SherpaOnnxRecognizer.ModelType.SENSE_VOICE
                ? REQ_IMPORT_SENSE_VOICE : REQ_IMPORT_ZIPFORMER;
        startActivityForResult(intent, request);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        SherpaOnnxRecognizer.ModelType type;
        if (requestCode == REQ_IMPORT_SENSE_VOICE) {
            type = SherpaOnnxRecognizer.ModelType.SENSE_VOICE;
        } else if (requestCode == REQ_IMPORT_ZIPFORMER) {
            type = SherpaOnnxRecognizer.ModelType.ZIPFORMER;
        } else {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (Throwable ignored) { }
        operationRunning = true;
        render();
        SherpaOnnxRecognizer.importZip(this, type, uri, listener,
                (success, message) -> finishOperation(message));
    }

    private void confirmDelete(SherpaOnnxRecognizer.ModelType type, String name) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.asr_model_delete_confirm_title))
                .setMessage(getString(R.string.asr_model_delete_confirm_message, name))
                .setPositiveButton(getString(R.string.asr_model_delete), (dialog, which) -> {
                    operationRunning = true;
                    render();
                    SherpaOnnxRecognizer.deleteModel(this, type,
                            (success, message) -> finishOperation(message));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void finishOperation(String message) {
        operationRunning = false;
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        render();
    }

    private String modelStatus(SherpaOnnxRecognizer.ModelType type,
                               SherpaOnnxRecognizer.ModelState state,
                               boolean installed, String size) {
        if (state == SherpaOnnxRecognizer.ModelState.DOWNLOADING) {
            return getString(R.string.asr_model_status_downloading);
        }
        if (state == SherpaOnnxRecognizer.ModelState.IMPORTING) {
            return getString(R.string.asr_model_status_importing);
        }
        if (state == SherpaOnnxRecognizer.ModelState.PREPARING) {
            return getString(R.string.asr_model_status_loading);
        }
        if (state == SherpaOnnxRecognizer.ModelState.FAILED) {
            return getString(R.string.asr_model_status_failed);
        }
        if (installed) {
            long bytes = SherpaOnnxRecognizer.installedBytes(this, type);
            return getString(R.string.asr_model_status_installed,
                    bytes > 0 ? formatBytes(bytes) : size);
        }
        return getString(R.string.asr_model_status_not_installed, size);
    }

    private String formatBytes(long bytes) {
        return String.format(Locale.getDefault(), "%.0f MB", bytes / 1024f / 1024f);
    }

    private LinearLayout.LayoutParams weightedButtonLp(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        if (first) lp.setMargins(0, 0, dp(5), 0);
        else lp.setMargins(dp(5), 0, 0, 0);
        return lp;
    }

    private TextView actionButton(String value, boolean primary) {
        TextView button = text(value, 14, primary ? Color.WHITE : 0xFF5C56CB, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(primary ? 0xFF6761D7 : 0xFFEDEBFF,
                dp(13), Color.TRANSPARENT, 0));
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
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

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
