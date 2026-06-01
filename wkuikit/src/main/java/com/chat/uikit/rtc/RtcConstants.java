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
    public static final int VIDEO_WIDTH = 1280;
    public static final int VIDEO_HEIGHT = 720;
    public static final int VIDEO_FPS = 24;
    public static final int VIDEO_MAX_BITRATE_KBPS = 1800;

    public static final String[] AUDIO_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO};
    public static final String[] VIDEO_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};

    public static boolean isVideo(int type) { return type == VIDEO; }
    public static String modeOf(int type) { return isVideo(type) ? "video" : "audio"; }
    public static int typeOf(String mode) { return "video".equalsIgnoreCase(mode) ? VIDEO : AUDIO; }
}
