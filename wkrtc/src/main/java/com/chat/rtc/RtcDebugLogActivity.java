package com.chat.rtc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class RtcDebugLogActivity extends Activity {
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RtcDebugLogger.init(this);
        setTitle("RTC 诊断日志");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("RTC 诊断日志");
        title.setTextSize(18);
        title.setTextColor(Color.rgb(20, 28, 40));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), dp(12), dp(16), dp(8));
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(4), dp(8), dp(8));
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addButton(bar, "刷新", v -> refresh());
        addButton(bar, "复制", v -> copyLog());
        addButton(bar, "分享", v -> shareLog());
        addButton(bar, "清空", v -> clearLog());
        addButton(bar, "关闭", v -> finish());

        ScrollView scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextColor(Color.rgb(31, 41, 55));
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(12), dp(8), dp(12), dp(24));
        scrollView.addView(logView, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refresh();
    }

    private void addButton(LinearLayout bar, String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        bar.addView(button, lp);
    }

    private void refresh() {
        String log = RtcDebugLogger.read(this);
        logView.setText(log);
    }

    private void copyLog() {
        String log = RtcDebugLogger.read(this);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("wkrtc.log", log));
        Toast.makeText(this, "RTC 日志已复制", Toast.LENGTH_SHORT).show();
    }

    private void shareLog() {
        String log = RtcDebugLogger.read(this);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "wkrtc.log");
        intent.putExtra(Intent.EXTRA_TEXT, log);
        startActivity(Intent.createChooser(intent, "分享 RTC 日志"));
    }

    private void clearLog() {
        RtcDebugLogger.clear(this);
        refresh();
        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
