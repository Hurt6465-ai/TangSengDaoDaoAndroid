package com.chat.rtc;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RtcDebugLogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.rtc_debug_disabled_title));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(22), dp(36), dp(22), dp(22));

        TextView title = new TextView(this);
        title.setText(getString(R.string.rtc_debug_disabled_title));
        title.setTextSize(20);
        title.setTextColor(Color.rgb(20, 28, 40));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView msg = new TextView(this);
        msg.setText(getString(R.string.rtc_debug_disabled_msg));
        msg.setTextSize(15);
        msg.setTextColor(Color.rgb(75, 85, 99));
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, dp(18), 0, dp(28));
        root.addView(msg, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button close = new Button(this);
        close.setAllCaps(false);
        close.setText(getString(R.string.rtc_close));
        close.setOnClickListener(v -> finish());
        root.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        setContentView(root);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
