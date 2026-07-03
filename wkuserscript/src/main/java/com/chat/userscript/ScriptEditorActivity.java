package com.chat.userscript;

import android.app.Activity;
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

        TextView cancel = button("取消");
        toolbar.addView(cancel, new LinearLayout.LayoutParams(dp(62), -1));
        cancel.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText(editingId == null ? "新增脚本" : "编辑脚本");
        title.setTextSize(19);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView save = button("保存");
        toolbar.addView(save, new LinearLayout.LayoutParams(dp(62), -1));
        save.setOnClickListener(v -> saveScript());

        TextView tip = new TextView(this);
        tip.setText("支持 @name、@match、@exclude、@run-at、@grant。测试阶段只在 DeepSeek / 千问域名注入。GM_xmlhttpRequest 当前禁用。");
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
    }

    private void bindData() {
        if (editingId != null) {
            UserScript script = store.getById(editingId);
            if (script != null) {
                editor.setText(script.code);
                editor.setSelection(editor.getText().length());
                return;
            }
        }
        editor.setText(UserScriptParser.defaultScriptTemplate());
        editor.setSelection(editor.getText().length());
    }

    private void saveScript() {
        String code = editor == null ? "" : editor.getText().toString();
        if (code.trim().length() == 0) {
            Toast.makeText(this, "脚本内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        UserScript script = UserScriptParser.parse(code);
        if (editingId != null) {
            UserScript old = store.getById(editingId);
            if (old != null) {
                script.id = old.id;
                script.enabled = old.enabled;
            }
        }
        store.upsert(script);
        Toast.makeText(this, "已保存：" + script.name, Toast.LENGTH_SHORT).show();
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
