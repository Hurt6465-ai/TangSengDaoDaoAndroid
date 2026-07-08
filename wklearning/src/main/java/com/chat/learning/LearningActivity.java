package com.chat.learning;

import android.app.Activity;
import android.os.Bundle;

/**
 * Compatibility stub.
 * 当前学习主页在底部导航的 LearningFragment 中显示，不再使用独立 LearningActivity。
 * 保留这个空 Activity 是为了覆盖旧文件，避免旧文件中 public class WordFullscreenActivity
 * 与文件名 LearningActivity.java 不一致导致编译失败。
 */
public class LearningActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
