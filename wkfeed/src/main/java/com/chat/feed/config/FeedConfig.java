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

    /** 是否允许发布图片。 */
    public static boolean ENABLE_IMAGE_PUBLISH = true;

    /**
     * 是否允许发布视频。
     * 测试阶段可打开；上线服务器容量小时，把这里改成 false，会隐藏/禁用视频上传入口。
     */
    public static boolean ENABLE_VIDEO_PUBLISH = true;

    /**
     * 是否在手机端压缩视频。
     * 使用 Media3 Transformer + 设备硬件 MediaCodec，不走服务器，也不引入 FFmpeg。
     */
    public static boolean ENABLE_CLIENT_VIDEO_TRANSCODE = true;

    /** 图片一次最多选择数量。 */
    public static final int IMAGE_MAX_SELECT_COUNT = 5;

    /** 发布视频最长时长，控制前端耗电、流量和服务端存储。 */
    public static final int VIDEO_MAX_DURATION_SECONDS = 60;

    /** 上传最终文件大小上限。压缩后仍超过这个大小就拒绝上传。 */
    public static final int VIDEO_MAX_UPLOAD_MB = 20;

    /** 大于这个大小或分辨率过高，就触发手机端压缩。 */
    public static final int VIDEO_COMPRESS_TRIGGER_MB = 8;

    /** 视频前端压缩目标：竖屏短边 540，最高短边 720。 */
    public static final int VIDEO_TARGET_SHORT_EDGE = 540;
    public static final int VIDEO_MAX_SHORT_EDGE = 720;

    /** 码率策略说明：Media3 Transformer 默认用硬件编码器策略，当前不强行指定码率，避免机型兼容问题。 */
    public static final int VIDEO_TARGET_BITRATE_KBPS = 1000;
    public static final int VIDEO_MAX_BITRATE_KBPS = 1600;

    /** 图片前端压缩目标：WebP 单张约 100KB。 */
    public static final int IMAGE_TARGET_KB = 100;
    public static final int IMAGE_MAX_LONG_EDGE = 1600;

    /** 下一条视频轻量缓存大小。 */
    public static final long VIDEO_PRELOAD_BYTES = 768L * 1024L;

    /** 发现页发布按钮：滑动隐藏，停稳后延迟显示。 */
    public static final long PUBLISH_SHOW_DELAY_MS = 900L;
}
