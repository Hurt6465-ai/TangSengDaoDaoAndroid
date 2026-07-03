package com.chat.userscript;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AiScriptWebActivity extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";

    private WebView webView;
    private UserScriptController controller;

    public static void open(Context context, String title, String url) {
        Intent intent = new Intent(context, AiScriptWebActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_URL, url);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (title == null || title.length() == 0) title = "AI 网页";
        if (url == null || url.length() == 0) url = "https://chat.qwen.ai/";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(8), dp(10), dp(8));
        toolbar.setBackgroundColor(Color.WHITE);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView close = toolbarButton("关闭");
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(56), -1));
        close.setOnClickListener(v -> finish());

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.rgb(17, 24, 39));
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        toolbar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1));

        TextView refresh = toolbarButton("刷新");
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(56), -1));
        refresh.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });

        TextView scripts = toolbarButton("脚本");
        toolbar.addView(scripts, new LinearLayout.LayoutParams(dp(56), -1));
        scripts.setOnClickListener(v -> startActivity(new Intent(this, ScriptManagerActivity.class)));

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(238, 242, 247));
        root.addView(line, new LinearLayout.LayoutParams(-1, 1));

        FrameLayout webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        webView = new WebView(this);
        webContainer.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        controller = new UserScriptController(this, webView);
        controller.attach();
        controller.loadUrl(url);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controller != null) controller.reinjectCurrentPage();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
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
