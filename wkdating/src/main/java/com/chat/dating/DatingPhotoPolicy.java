package com.chat.dating;

import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

/** 交友照片数量、尺寸与上传压缩策略。 */
public final class DatingPhotoPolicy {
    public static final int MAX_PHOTO_COUNT = 5;
    public static final int MIN_PHOTO_COUNT_TO_ENABLE = 1;

    /** 推荐卡派生图：真实上传一份 720x1280 边界内的 WebP。 */
    public static final int CARD_MAX_WIDTH = 720;
    public static final int CARD_MAX_HEIGHT = 1280;
    public static final int CARD_TARGET_MAX_BYTES = 200 * 1024;

    /** 详情主图：保留更高分辨率，最长边 1440。 */
    public static final int MASTER_MAX_EDGE = 1440;
    public static final int MASTER_TARGET_MAX_BYTES = 650 * 1024;

    public static final int UPLOAD_MIN_EDGE = 480;
    public static final int WEBP_START_QUALITY = 84;
    public static final int WEBP_MIN_QUALITY = 62;

    private DatingPhotoPolicy() {}

    public static boolean canAddMore(List<String> photos) {
        return photos == null || photos.size() < MAX_PHOTO_COUNT;
    }

    public static boolean canEnableDating(List<String> photos) {
        return photos != null && photos.size() >= MIN_PHOTO_COUNT_TO_ENABLE;
    }

    public static String validateBeforeUpload(String mimeType, long bytes, int width, int height) {
        if (TextUtils.isEmpty(mimeType)) return "图片格式不正确";
        String type = mimeType.toLowerCase(Locale.US);
        if (!type.contains("jpeg") && !type.contains("jpg") && !type.contains("png") && !type.contains("webp")) {
            return "只支持 JPG、PNG、WebP 图片";
        }
        if (width <= 0 || height <= 0) return "图片读取失败";
        int maxEdge = Math.max(width, height);
        int minEdge = Math.min(width, height);
        if (minEdge < UPLOAD_MIN_EDGE) return "图片太小，请上传清晰真人照片";
        if (maxEdge > 8000 || bytes > 20L * 1024L * 1024L) return "图片太大，请重新选择";
        return null;
    }

    public static boolean needsServerHumanReview() {
        return false;
    }
}
