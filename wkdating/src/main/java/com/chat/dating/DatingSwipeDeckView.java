package com.chat.dating;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 旧版手写滑卡容器的兼容占位类。
 *
 * 新版首页已经完全改用 CardStackView，此类不再承载任何滑卡逻辑。
 * 保留它只是为了覆盖旧仓库中残留的同名源码，避免增量上传文件时旧实现继续参与编译。
 * 后续确认仓库没有 XML/Java 引用后，可以直接删除本文件。
 */
@Deprecated
public final class DatingSwipeDeckView extends FrameLayout {
    public DatingSwipeDeckView(@NonNull Context context) {
        super(context);
    }

    public DatingSwipeDeckView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DatingSwipeDeckView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
