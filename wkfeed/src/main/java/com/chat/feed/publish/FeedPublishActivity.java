package com.chat.feed.publish;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.chat.feed.R;

/**
 * 第一版发布入口：先给 UI/上传压缩链路预留，不直接做复杂剪辑。
 * 视频压缩目标：540p，最高 720p；图片 WebP 约 100KB。
 */
public class FeedPublishActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_publish);
        TextView close = findViewById(R.id.feedPublishCloseTv);
        close.setOnClickListener(v -> finish());
    }
}
