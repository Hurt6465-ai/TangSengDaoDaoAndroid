package com.chat.feed.config;

/**
 * wkfeed 全局策略。
 * 第一版发现页：后端可用时走真实接口；后端未接好时可自动回退 Mock，保证前端入口和播放器可测试。
 */
public class FeedConfig {
    /** 强制使用 Mock。正式上线前改 false。 */
    public static boolean DEBUG_MOCK = false;

    /** 后端接口未完成/失败时，首版可回退 Mock，避免发现页一片空白。上线前建议改 false。 */
    public static boolean FALLBACK_MOCK_ON_ERROR = true;

    public static final int PAGE_SIZE = 16;

    /** 视频前端压缩目标：优先 540p，最高不超过 720p。 */
    public static final int VIDEO_TARGET_HEIGHT = 540;
    public static final int VIDEO_MAX_HEIGHT = 720;
    public static final int VIDEO_TARGET_BITRATE_KBPS = 1200;
    public static final int VIDEO_MAX_BITRATE_KBPS = 1800;

    /** 图片前端压缩目标：WebP 单张约 100KB。 */
    public static final int IMAGE_TARGET_KB = 100;
    public static final int IMAGE_MAX_LONG_EDGE = 1600;

    /** 下一条视频轻量缓存大小。 */
    public static final long VIDEO_PRELOAD_BYTES = 768L * 1024L;

    /** 发现页发布按钮：滑动隐藏，停稳后延迟显示。 */
    public static final long PUBLISH_SHOW_DELAY_MS = 900L;
}
