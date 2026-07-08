package com.chat.learning.fullscreen;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** 旧版全屏背单词残留兼容类。后续再接新的固定卡片入口。 */
public class WordFullscreenActivity extends Activity {
    public static final String EXTRA_CATEGORY_ID = "category_id";
    public static final String EXTRA_CATEGORY_TITLE = "category_title";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("背单词全屏页后续接入 HSK 词库和 SM-2。");
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTextSize(16);
        setContentView(tv);
    }
}
