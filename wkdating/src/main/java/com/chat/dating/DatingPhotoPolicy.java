package com.chat.dating;

import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

/** 交友照片数量、尺寸与上传压缩策略。所有页面只复用同一份 WebP。 */
public final class DatingPhotoPolicy {
    public static final int MAX_PHOTO_COUNT = 5;
    public static final int MIN_PHOTO_COUNT_TO_ENABLE = 1;

    /** 单套交友图片：推荐卡、详情页和列表全部使用同一个 URL。 */
    public static final int PHOTO_MAX_WIDTH = 720;
    public static final int PHOTO_MAX_HEIGHT = 1280;
    /** 给上传协议和文件头预留余量，目标小于服务端 150KB 硬限制。 */
    public static final int PHOTO_TARGET_MAX_BYTES = 145 * 1024;
    public static final int PHOTO_HARD_MAX_BYTES = 150 * 1024;
    public static final long MAX_INPUT_BYTES = 20L * 1024L * 1024L;

    public static final int UPLOAD_MIN_EDGE = 480;
    public static final int WEBP_START_QUALITY = 82;
    public static final int WEBP_MIN_QUALITY = 54;

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
        if (maxEdge > 8000 || bytes > MAX_INPUT_BYTES) return "图片太大，请重新选择";
        return null;
    }

    public static boolean needsServerHumanReview() {
        return false;
    }
}
