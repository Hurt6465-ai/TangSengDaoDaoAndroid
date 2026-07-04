package com.chat.userscript;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.userscript.model.UserScript;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ScriptManagerActivity extends Activity {
    private static final int REQ_IMPORT = 9201;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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
        toolbar.setPadding(dp(10), dp(8), dp(10), dp(8));
        toolbar.setBackgroundColor(Color.WHITE);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView close = textButton(getString(R.string.script_close));
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(52), -1));
        close.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText(getString(R.string.script_manager_title));
        title.setTextSize(18);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView add = textButton(getString(R.string.script_add));
        toolbar.addView(add, new LinearLayout.LayoutParams(dp(48), -1));
        add.setOnClickListener(v -> startActivity(new Intent(this, ScriptEditorActivity.class)));

        TextView imp = textButton(getString(R.string.script_import));
        toolbar.addView(imp, new LinearLayout.LayoutParams(dp(48), -1));
        imp.setOnClickListener(v -> importScriptFile());

        TextView online = textButton(getString(R.string.script_online));
        toolbar.addView(online, new LinearLayout.LayoutParams(dp(48), -1));
        online.setOnClickListener(v -> showOnlineInstallDialog());

        TextView official = textButton(getString(R.string.script_official));
        toolbar.addView(official, new LinearLayout.LayoutParams(dp(48), -1));
        official.setOnClickListener(v -> showOfficialScriptsDialog());

        TextView tip = new TextView(this);
        tip.setText(getString(R.string.script_tip));
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
            empty.setText(getString(R.string.script_empty));
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
        String prefix = script.enabled ? "● " : "○ ";
        String suffix = script.official ? "  官方" : "";
        name.setText(prefix + script.name + suffix);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(script.enabled ? Color.rgb(17, 24, 39) : Color.rgb(107, 114, 128));
        card.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = new TextView(this);
        meta.setText("run-at: " + script.runAt + "\nnetwork: " + (script.networkAllowed ? "allowed" : "off") + "\nmatch: " + join(script.matches));
        meta.setTextSize(12);
        meta.setTextColor(Color.rgb(107, 114, 128));
        meta.setPadding(0, dp(7), 0, dp(10));
        card.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(actions, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView toggle = actionButton(script.enabled ? getString(R.string.script_disable) : getString(R.string.script_enable));
        actions.addView(toggle, new LinearLayout.LayoutParams(0, -1, 1));
        toggle.setOnClickListener(v -> {
            store.setEnabled(script.id, !script.enabled);
            renderScripts();
        });

        TextView edit = actionButton(script.official ? getString(R.string.script_detail) : getString(R.string.script_edit));
        actions.addView(edit, new LinearLayout.LayoutParams(0, -1, 1));
        edit.setOnClickListener(v -> {
            if (script.official) showOfficialDetail(script); else ScriptEditorActivity.open(this, script.id);
        });

        TextView delete = actionButton(getString(R.string.script_delete));
        delete.setTextColor(Color.rgb(239, 68, 68));
        actions.addView(delete, new LinearLayout.LayoutParams(0, -1, 1));
        delete.setOnClickListener(v -> confirmDelete(script));
    }

    private void showOfficialDetail(UserScript script) {
        new AlertDialog.Builder(this)
                .setTitle(script.name)
                .setMessage(getString(R.string.script_official_locked) + "\n\n" + script.description)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void confirmDelete(UserScript script) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.script_delete_title))
                .setMessage(getString(R.string.script_delete_message, script.name))
                .setNegativeButton(getString(R.string.script_cancel), null)
                .setPositiveButton(getString(R.string.script_delete), (dialog, which) -> {
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
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) importFromUri(data.getData());
    }

    private void importFromUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("empty");
            String code = readAll(in);
            UserScript script = UserScriptParser.parse(code);
            saveWithRiskPrompt(script, getString(R.string.script_import_success, script.name));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.script_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void showOnlineInstallDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(getString(R.string.script_url_hint));
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(dp(18), dp(8), dp(18), dp(8));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.script_url_title))
                .setView(input)
                .setNegativeButton(getString(R.string.script_cancel), null)
                .setPositiveButton(getString(R.string.script_install), (dialog, which) -> downloadScript(input.getText().toString().trim()))
                .show();
    }

    private void downloadScript(String url) {
        if (url == null || !url.startsWith("https://")) {
            Toast.makeText(this, getString(R.string.script_allowed_web_only), Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false);
                String code = readAll(connection.getInputStream());
                UserScript script = UserScriptParser.parse(code);
                mainHandler.post(() -> saveWithRiskPrompt(script, getString(R.string.script_import_success, script.name)));
            } catch (Exception e) {
                String msg = e.getMessage();
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.script_download_failed, msg), Toast.LENGTH_SHORT).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void showOfficialScriptsDialog() {
        List<OfficialScript> list = loadOfficialScripts();
        if (list.isEmpty()) return;
        String[] titles = new String[list.size()];
        for (int i = 0; i < list.size(); i++) titles[i] = list.get(i).title + "\n" + list.get(i).description;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.script_official_title))
                .setItems(titles, (dialog, which) -> installOfficial(list.get(which)))
                .setNegativeButton(getString(R.string.script_cancel), null)
                .show();
    }

    private List<OfficialScript> loadOfficialScripts() {
        ArrayList<OfficialScript> list = new ArrayList<>();
        try (InputStream in = getAssets().open("official_scripts/index.json")) {
            JSONArray array = new JSONArray(readAll(in));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                OfficialScript item = new OfficialScript();
                item.file = object.optString("file", "");
                item.title = object.optString("title", item.file);
                item.description = object.optString("description", "");
                if (item.file.length() > 0) list.add(item);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.script_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
        return list;
    }

    private void installOfficial(OfficialScript item) {
        try (InputStream in = getAssets().open("official_scripts/" + item.file)) {
            UserScript script = UserScriptParser.parse(readAll(in));
            script.official = true;
            script.officialAsset = item.file;
            saveWithRiskPrompt(script, getString(R.string.script_official_installed, script.name));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.script_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveWithRiskPrompt(UserScript script, String successMessage) {
        if (script == null) return;
        if (script.wantsNetwork()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.script_network_risk_title))
                    .setMessage(getString(R.string.script_network_risk_message))
                    .setNegativeButton(getString(R.string.script_network_deny), (dialog, which) -> {
                        script.networkAllowed = false;
                        store.upsert(script);
                        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                        renderScripts();
                    })
                    .setPositiveButton(getString(R.string.script_network_allow), (dialog, which) -> {
                        script.networkAllowed = true;
                        store.upsert(script);
                        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                        renderScripts();
                    }).show();
        } else {
            script.networkAllowed = false;
            store.upsert(script);
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
            renderScripts();
        }
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        return out.toString("UTF-8");
    }

    private TextView textButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
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
        if (values == null || values.isEmpty()) return "default";
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

    private static class OfficialScript {
        String file;
        String title;
        String description;
    }
}
