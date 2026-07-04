package com.chat.userscript;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AiScriptWebActivity extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_START_PROMPT = "start_prompt";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private UserScriptController controller;
    private View toolbar;
    private boolean toolbarVisible = true;

    public static void open(Context context, String title, String url) {
        open(context, title, url, null);
    }

    public static void open(Context context, String title, String url, String startPrompt) {
        Intent intent = new Intent(context, AiScriptWebActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_START_PROMPT, startPrompt);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String url = getIntent().getStringExtra(EXTRA_URL);
        String startPrompt = getIntent().getStringExtra(EXTRA_START_PROMPT);
        if (title == null || title.length() == 0) title = "AI 网页";
        if (url == null || url.length() == 0) url = "https://chat.qwen.ai/";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        toolbar = buildToolbar(title);
        root.addView(toolbar, new FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP));

        View topHit = new View(this);
        topHit.setBackgroundColor(Color.TRANSPARENT);
        topHit.setOnClickListener(v -> showToolbarTemporarily());
        root.addView(topHit, new FrameLayout.LayoutParams(-1, dp(28), Gravity.TOP));

        controller = new UserScriptController(this, webView);
        controller.setStartupPrompt(startPrompt);
        controller.attach();
        controller.loadUrl(url);
        handler.postDelayed(this::hideToolbar, 1800L);
    }

    private View buildToolbar(String title) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(0xF9FFFFFF);
        bar.setElevation(dp(2));

        TextView close = toolbarButton(getString(R.string.script_close));
        bar.addView(close, new LinearLayout.LayoutParams(dp(56), -1));
        close.setOnClickListener(v -> finish());

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.rgb(17, 24, 39));
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1));

        TextView refresh = toolbarButton(getString(R.string.script_refresh));
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(56), -1));
        refresh.setOnClickListener(v -> {
            if (webView != null) webView.reload();
            showToolbarTemporarily();
        });

        TextView scripts = toolbarButton(getString(R.string.script_scripts));
        bar.addView(scripts, new LinearLayout.LayoutParams(dp(56), -1));
        scripts.setOnClickListener(v -> {
            startActivity(new Intent(this, ScriptManagerActivity.class));
            showToolbarTemporarily();
        });
        return bar;
    }

    private void showToolbarTemporarily() {
        showToolbar();
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::hideToolbar, 2200L);
    }

    private void showToolbar() {
        if (toolbar == null || toolbarVisible) return;
        toolbarVisible = true;
        toolbar.animate().translationY(0).alpha(1f).setDuration(180).start();
    }

    private void hideToolbar() {
        if (toolbar == null || !toolbarVisible) return;
        toolbarVisible = false;
        toolbar.animate().translationY(-dp(56)).alpha(0f).setDuration(220).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (controller != null) controller.handleRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controller != null) controller.reinjectCurrentPage();
    }

    @Override
    public void onBackPressed() {
        if (toolbar != null && !toolbarVisible) {
            showToolbarTemporarily();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
        super.onDestroy();
    }

    private TextView toolbarButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(24, 119, 242));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
