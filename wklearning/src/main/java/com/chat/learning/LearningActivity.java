package com.chat.learning;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 兼容旧版本残留入口。
 * 当前学习主页显示在底部导航里的 LearningFragment，不再使用独立 LearningActivity。
 */
public class LearningActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
