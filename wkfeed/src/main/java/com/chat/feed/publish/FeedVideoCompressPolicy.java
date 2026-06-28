package com.chat.feed.publish;

import com.chat.feed.config.FeedConfig;

/**
 * 视频发布压缩策略。
 * 目标不是追求最高清，而是发现流里“文件小、加载快、手机看足够清楚”。
 */
public class FeedVideoCompressPolicy {
    public int targetShortEdge = FeedConfig.VIDEO_TARGET_SHORT_EDGE;
    public int maxShortEdge = FeedConfig.VIDEO_MAX_SHORT_EDGE;
    public int compressTriggerMb = FeedConfig.VIDEO_COMPRESS_TRIGGER_MB;

    /**
     * Media3 Presentation 按输出 height 缩放，所以这里返回目标输出 height。
     * 竖屏 1080x1920 -> 540x960；横屏 1920x1080 -> 960x540。
     */
    public int chooseOutputHeight(int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 960;
        int shortEdge = Math.min(sourceWidth, sourceHeight);
        if (shortEdge <= 0) return Math.min(960, sourceHeight);
        int target = Math.min(maxShortEdge, targetShortEdge);
        if (sourceHeight >= sourceWidth) {
            return Math.max(target, Math.round(target * (sourceHeight / (float) sourceWidth)));
        }
        return target;
    }

    public boolean needCompress(int sourceWidth, int sourceHeight, long sizeBytes) {
        int shortEdge = Math.min(sourceWidth, sourceHeight);
        int longEdge = Math.max(sourceWidth, sourceHeight);
        return shortEdge > FeedConfig.VIDEO_MAX_SHORT_EDGE
                || longEdge > 1280
                || sizeBytes > compressTriggerMb * 1024L * 1024L;
    }
}
