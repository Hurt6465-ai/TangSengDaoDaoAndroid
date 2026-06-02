package com.chat.uikit.rtc;

import android.Manifest;

public final class RtcConstants {
    private RtcConstants() {}

    public static final String PROTOCOL = "cp-harmony-rtc-v1";
    public static final String SIGNAL_PREFIX = "__cp_harmony_rtc__:";

    public static final int AUDIO = 0;
    public static final int VIDEO = 1;

    public static final String EXTRA_CALL_ID = "rtc_call_id";
    public static final String EXTRA_PEER_UID = "rtc_peer_uid";
    public static final String EXTRA_PEER_NAME = "rtc_peer_name";
    public static final String EXTRA_PEER_AVATAR = "rtc_peer_avatar";
    public static final String EXTRA_CALL_TYPE = "rtc_call_type";
    public static final String EXTRA_INCOMING = "rtc_incoming";

    public static final int CALL_TIMEOUT_MS = 30_000;
    public static final int CONNECT_TIMEOUT_MS = 35_000;

    // Default HD. Stable first, then users can switch to 1080P in call UI.
    public static final int VIDEO_WIDTH = 1280;
    public static final int VIDEO_HEIGHT = 720;
    public static final int VIDEO_FPS = 24;
    public static final int VIDEO_MAX_BITRATE_KBPS = 1800;

    // Ultra HD mode, enabled by user in the side toolbar.
    public static final int VIDEO_FHD_WIDTH = 1920;
    public static final int VIDEO_FHD_HEIGHT = 1080;
    public static final int VIDEO_FHD_FPS = 24;
    public static final int VIDEO_FHD_BITRATE_KBPS = 3200;

    // Weak network fallback.
    public static final int VIDEO_LOW_WIDTH = 640;
    public static final int VIDEO_LOW_HEIGHT = 360;
    public static final int VIDEO_LOW_FPS = 15;
    public static final int VIDEO_LOW_BITRATE_KBPS = 550;

    public static final int SCREEN_WIDTH = 1280;
    public static final int SCREEN_HEIGHT = 720;
    public static final int SCREEN_FPS = 15;
    public static final int SCREEN_MAX_BITRATE_KBPS = 1200;

    // GPUPixel default beauty values. Conservative defaults keep video natural and reduce GPU pressure.
    public static final float BEAUTY_SMOOTHING = 0.45f;
    public static final float BEAUTY_WHITEN = 0.32f;
    public static final float BEAUTY_FILTER = 0.20f;
    public static final float BEAUTY_THIN_FACE = 0.22f;
    public static final float BEAUTY_BIG_EYE = 0.16f;

    public static final String[] AUDIO_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO};
    public static final String[] VIDEO_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};

    public static boolean isVideo(int type) { return type == VIDEO; }
    public static String modeOf(int type) { return isVideo(type) ? "video" : "audio"; }
    public static int typeOf(String mode) { return "video".equalsIgnoreCase(mode) ? VIDEO : AUDIO; }
}
