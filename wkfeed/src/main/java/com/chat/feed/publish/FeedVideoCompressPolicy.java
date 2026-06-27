package com.chat.feed.publish;

import com.chat.feed.config.FeedConfig;

/**
 * 视频发布压缩策略。真正转码建议用 Media3 Transformer 或服务端兜底转码。
 * 首版客户端在选择/上传前按这个策略生成目标参数。
 */
public class FeedVideoCompressPolicy {
    public int targetHeight = FeedConfig.VIDEO_TARGET_HEIGHT;
    public int maxHeight = FeedConfig.VIDEO_MAX_HEIGHT;
    public int targetBitrateKbps = FeedConfig.VIDEO_TARGET_BITRATE_KBPS;
    public int maxBitrateKbps = FeedConfig.VIDEO_MAX_BITRATE_KBPS;

    public int chooseOutputHeight(int sourceWidth, int sourceHeight) {
        int sourceLongEdge = Math.max(sourceWidth, sourceHeight);
        if (sourceLongEdge <= FeedConfig.VIDEO_TARGET_HEIGHT) return sourceLongEdge;
        return Math.min(FeedConfig.VIDEO_MAX_HEIGHT, FeedConfig.VIDEO_TARGET_HEIGHT);
    }

    public boolean needCompress(int sourceWidth, int sourceHeight, long sizeBytes) {
        return Math.max(sourceWidth, sourceHeight) > FeedConfig.VIDEO_MAX_HEIGHT || sizeBytes > 8L * 1024L * 1024L;
    }
}
