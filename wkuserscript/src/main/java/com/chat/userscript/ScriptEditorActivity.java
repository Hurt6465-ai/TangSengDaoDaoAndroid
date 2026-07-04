package com.chat.userscript;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.userscript.model.UserScript;

public class ScriptEditorActivity extends Activity {
    private static final String EXTRA_SCRIPT_ID = "script_id";
    private EditText editor;
    private UserScriptStore store;
    private String editingId;
    private UserScript editingScript;

    public static void open(Context context, String scriptId) {
        Intent intent = new Intent(context, ScriptEditorActivity.class);
        intent.putExtra(EXTRA_SCRIPT_ID, scriptId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = UserScriptStore.get(this);
        editingId = getIntent().getStringExtra(EXTRA_SCRIPT_ID);
        if (editingId != null) editingScript = store.getById(editingId);
        buildLayout();
        bindData();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView cancel = button(getString(R.string.script_cancel));
        toolbar.addView(cancel, new LinearLayout.LayoutParams(dp(62), -1));
        cancel.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText(editingId == null ? getString(R.string.script_editor_new_title) : getString(R.string.script_editor_edit_title));
        title.setTextSize(19);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView save = button(getString(R.string.script_save));
        toolbar.addView(save, new LinearLayout.LayoutParams(dp(62), -1));
        save.setOnClickListener(v -> saveScript());
        if (editingScript != null && editingScript.official) save.setVisibility(View.GONE);

        TextView tip = new TextView(this);
        tip.setText(editingScript != null && editingScript.official ? getString(R.string.script_official_locked) : getString(R.string.script_tip));
        tip.setTextSize(12);
        tip.setTextColor(Color.rgb(107, 114, 128));
        tip.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(tip, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(238, 242, 247));
        root.addView(line, new LinearLayout.LayoutParams(-1, 1));

        editor = new EditText(this);
        editor.setTextSize(13);
        editor.setTextColor(Color.rgb(17, 24, 39));
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(Gravity.START | Gravity.TOP);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(true);
        editor.setSingleLine(false);
        editor.setPadding(dp(14), dp(14), dp(14), dp(14));
        editor.setBackgroundColor(Color.rgb(250, 251, 253));
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        if (editingScript != null && editingScript.official) editor.setEnabled(false);
    }

    private void bindData() {
        if (editingScript != null) {
            if (editingScript.official) {
                editor.setText(getString(R.string.script_official_locked) + "\n\n" + editingScript.name + "\n" + editingScript.description);
            } else {
                editor.setText(editingScript.code);
            }
            editor.setSelection(editor.getText().length());
            return;
        }
        editor.setText(UserScriptParser.defaultScriptTemplate());
        editor.setSelection(editor.getText().length());
    }

    private void saveScript() {
        if (editingScript != null && editingScript.official) return;
        String code = editor == null ? "" : editor.getText().toString();
        if (code.trim().length() == 0) {
            Toast.makeText(this, getString(R.string.script_empty_content), Toast.LENGTH_SHORT).show();
            return;
        }
        UserScript script = UserScriptParser.parse(code);
        if (editingScript != null) {
            script.id = editingScript.id;
            script.enabled = editingScript.enabled;
            script.networkAllowed = editingScript.networkAllowed;
        }
        if (script.wantsNetwork()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.script_network_risk_title))
                    .setMessage(getString(R.string.script_network_risk_message))
                    .setNegativeButton(getString(R.string.script_network_deny), (dialog, which) -> {
                        script.networkAllowed = false;
                        saveAndFinish(script);
                    })
                    .setPositiveButton(getString(R.string.script_network_allow), (dialog, which) -> {
                        script.networkAllowed = true;
                        saveAndFinish(script);
                    }).show();
        } else {
            script.networkAllowed = false;
            saveAndFinish(script);
        }
    }

    private void saveAndFinish(UserScript script) {
        store.upsert(script);
        Toast.makeText(this, getString(R.string.script_save_success, script.name), Toast.LENGTH_SHORT).show();
        finish();
    }

    private TextView button(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.rgb(24, 119, 242));
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
