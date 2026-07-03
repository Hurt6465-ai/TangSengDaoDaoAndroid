package com.chat.userscript;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.userscript.model.UserScript;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class ScriptManagerActivity extends Activity {
    private static final int REQ_IMPORT = 9201;
    private UserScriptStore store;
    private LinearLayout listLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = UserScriptStore.get(this);
        buildLayout();
        renderScripts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderScripts();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 252));
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(14), dp(8), dp(14), dp(8));
        toolbar.setBackgroundColor(Color.WHITE);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView close = textButton("关闭");
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(56), -1));
        close.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("脚本管理");
        title.setTextSize(19);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView add = textButton("新增");
        toolbar.addView(add, new LinearLayout.LayoutParams(dp(56), -1));
        add.setOnClickListener(v -> startActivity(new Intent(this, ScriptEditorActivity.class)));

        TextView imp = textButton("导入");
        toolbar.addView(imp, new LinearLayout.LayoutParams(dp(56), -1));
        imp.setOnClickListener(v -> importScriptFile());

        TextView tip = new TextView(this);
        tip.setText("第三阶段测试版：脚本只会在 DeepSeek / 千问 HTTPS 网页运行。默认禁用 GM_xmlhttpRequest，避免泄露 AI 对话内容。");
        tip.setTextSize(13);
        tip.setTextColor(Color.rgb(107, 114, 128));
        tip.setPadding(dp(16), dp(12), dp(16), dp(10));
        root.addView(tip, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dp(14), dp(6), dp(14), dp(24));
        scrollView.addView(listLayout, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void renderScripts() {
        if (listLayout == null || store == null) return;
        listLayout.removeAllViews();
        List<UserScript> scripts = store.getAll();
        if (scripts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有脚本。点右上角“新增”可以粘贴测试脚本，点“导入”可以选择 .user.js 文件。");
            empty.setTextSize(15);
            empty.setTextColor(Color.rgb(107, 114, 128));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(60), dp(18), dp(60));
            listLayout.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (UserScript script : scripts) addScriptCard(script);
    }

    private void addScriptCard(UserScript script) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(ShapeUtils.rounded(Color.WHITE, dp(18), Color.rgb(231, 236, 244), 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(12));
        listLayout.addView(card, cardLp);

        TextView name = new TextView(this);
        name.setText((script.enabled ? "● " : "○ ") + script.name);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(script.enabled ? Color.rgb(17, 24, 39) : Color.rgb(107, 114, 128));
        card.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = new TextView(this);
        meta.setText("run-at: " + script.runAt + "\nmatch: " + join(script.matches));
        meta.setTextSize(12);
        meta.setTextColor(Color.rgb(107, 114, 128));
        meta.setPadding(0, dp(7), 0, dp(10));
        card.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(actions, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView toggle = actionButton(script.enabled ? "停用" : "启用");
        actions.addView(toggle, new LinearLayout.LayoutParams(0, -1, 1));
        toggle.setOnClickListener(v -> {
            store.setEnabled(script.id, !script.enabled);
            renderScripts();
        });

        TextView edit = actionButton("编辑");
        actions.addView(edit, new LinearLayout.LayoutParams(0, -1, 1));
        edit.setOnClickListener(v -> ScriptEditorActivity.open(this, script.id));

        TextView delete = actionButton("删除");
        delete.setTextColor(Color.rgb(239, 68, 68));
        actions.addView(delete, new LinearLayout.LayoutParams(0, -1, 1));
        delete.setOnClickListener(v -> confirmDelete(script));
    }

    private void confirmDelete(UserScript script) {
        new AlertDialog.Builder(this)
                .setTitle("删除脚本")
                .setMessage("确定删除“" + script.name + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    store.delete(script.id);
                    renderScripts();
                }).show();
    }

    private void importScriptFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importFromUri(data.getData());
        }
    }

    private void importFromUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("文件为空");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            String code = out.toString("UTF-8");
            UserScript script = UserScriptParser.parse(code);
            store.upsert(script);
            Toast.makeText(this, "已导入：" + script.name, Toast.LENGTH_SHORT).show();
            renderScripts();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView textButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.rgb(24, 119, 242));
        return view;
    }

    private TextView actionButton(String text) {
        TextView view = textButton(text);
        view.setBackground(ShapeUtils.rounded(Color.rgb(243, 247, 255), dp(12), Color.TRANSPARENT, 0));
        return view;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) return "默认 DeepSeek / 千问";
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) sb.append("\n       ");
            sb.append(value);
        }
        return sb.toString();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
