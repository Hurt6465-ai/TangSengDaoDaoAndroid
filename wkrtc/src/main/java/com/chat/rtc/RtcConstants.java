package com.chat.rtc;

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
    public static final String EXTRA_AUTO_ACCEPT = "rtc_auto_accept";

    public static final String ACTION_INCOMING_CALL = "com.chat.rtc.action.INCOMING_CALL";
    public static final String ACTION_START_CALL = "com.chat.rtc.action.START_CALL";
    public static final String ACTION_ANSWER_CALL = "com.chat.rtc.action.ANSWER_CALL";
    public static final String ACTION_REJECT_CALL = "com.chat.rtc.action.REJECT_CALL";
    public static final String ACTION_END_CALL = "com.chat.rtc.action.END_CALL";

    public static final int NOTIFICATION_ID_INCOMING = 7601;
    public static final int NOTIFICATION_ID_ACTIVE = 7602;

    public static final int CONTENT_TYPE_CALL_RECORD = 9302;

    public static final int CALL_TIMEOUT_MS = 30_000;
    public static final int CONNECT_TIMEOUT_MS = 35_000;

    // Camera call: keep stable. Screen sharing has its own much higher profile below.
    public static final int VIDEO_WIDTH = 1280;
    public static final int VIDEO_HEIGHT = 720;
    public static final int VIDEO_FPS = 24;
    public static final int VIDEO_MIN_BITRATE_KBPS = 600;
    public static final int VIDEO_START_BITRATE_KBPS = 1500;
    public static final int VIDEO_MAX_BITRATE_KBPS = 2200;

    public static final int VIDEO_LOW_WIDTH = 640;
    public static final int VIDEO_LOW_HEIGHT = 360;
    public static final int VIDEO_LOW_FPS = 15;
    public static final int VIDEO_LOW_BITRATE_KBPS = 550;

    // Screen sharing: borrowed from LiveKit's screenshare preset idea.
    // Do not force 720p. Capture real device resolution, cap only extreme devices.
    // For text/PPT/chat UI, resolution matters more than 30fps.
    public static final int SCREEN_FALLBACK_WIDTH = 1080;
    public static final int SCREEN_FALLBACK_HEIGHT = 1920;
    public static final int SCREEN_FPS = 20;
    public static final int SCREEN_MIN_BITRATE_KBPS = 2500;
    public static final int SCREEN_START_BITRATE_KBPS = 5000;
    public static final int SCREEN_MAX_BITRATE_KBPS = 7000;
    public static final int SCREEN_MAX_SHORT_EDGE = 1440;
    public static final int SCREEN_MAX_LONG_EDGE = 2560;

    // Keep old names for compatibility with already patched v9 code or external references.
    public static final int SCREEN_WIDTH = SCREEN_FALLBACK_WIDTH;
    public static final int SCREEN_HEIGHT = SCREEN_FALLBACK_HEIGHT;

    public static final String[] AUDIO_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO};
    public static final String[] VIDEO_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};

    public static boolean isVideo(int type) { return type == VIDEO; }
    public static String modeOf(int type) { return isVideo(type) ? "video" : "audio"; }
    public static int typeOf(String mode) { return "video".equalsIgnoreCase(mode) ? VIDEO : AUDIO; }
}
