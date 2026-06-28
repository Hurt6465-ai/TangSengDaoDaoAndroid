package com.chat.feed.publish;

import com.chat.feed.config.FeedConfig;

/**
 * 视频发布压缩策略。
 * 目标不是追求最高清，而是发现流里“文件小、加载快、手机看足够清楚”。
 */
public class FeedVideoCompressPolicy {
    public int targetShortEdge = FeedConfig.VIDEO_TARGET_SHORT_EDGE;
    public int compressTriggerMb = FeedConfig.VIDEO_COMPRESS_TRIGGER_MB;

    /**
     * Media3 Presentation 按输出 height 缩放，所以这里返回目标输出 height。
     * 竖屏显示尺寸 1080x1920 -> height=960 -> 540x960。
     * 横屏显示尺寸 1920x1080 -> height=540 -> 960x540。
     *
     * 注意：sourceWidth/sourceHeight 必须是“已按 rotation 修正后的显示尺寸”。
     */
    public int chooseOutputHeight(int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 960;
        int shortEdge = Math.min(sourceWidth, sourceHeight);
        if (shortEdge <= targetShortEdge) return sourceHeight;
        if (sourceHeight >= sourceWidth) {
            return Math.max(targetShortEdge, Math.round(targetShortEdge * (sourceHeight / (float) sourceWidth)));
        }
        return targetShortEdge;
    }

    /**
     * 满足以下任一条件即压缩：
     * 1. 视频显示尺寸短边 > 540；
     * 2. 文件大小 > 5MB。
     */
    public boolean needCompress(int sourceWidth, int sourceHeight, long sizeBytes) {
        int shortEdge = Math.min(sourceWidth, sourceHeight);
        return shortEdge > FeedConfig.VIDEO_TARGET_SHORT_EDGE
                || sizeBytes > compressTriggerMb * 1024L * 1024L;
    }

    public int clampVideoBitrateKbps(int requestedKbps) {
        if (requestedKbps < FeedConfig.VIDEO_MIN_BITRATE_KBPS) return FeedConfig.VIDEO_MIN_BITRATE_KBPS;
        if (requestedKbps > FeedConfig.VIDEO_MAX_BITRATE_KBPS) return FeedConfig.VIDEO_MAX_BITRATE_KBPS;
        return requestedKbps;
    }
}
