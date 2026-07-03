package com.chat.rtc;

import android.content.Context;
import android.text.TextUtils;

import com.chat.rtc.model.RtcSignal;

import java.io.File;

/**
 * Production no-op logger.
 *
 * The RTC module used to write verbose diagnostics to files while debugging signaling and
 * screenshare. The final build keeps this API so older host code still compiles, but it no
 * longer writes files, rotates logs, or performs any disk I/O during chat history loading or calls.
 */
public final class RtcDebugLogger {
    private RtcDebugLogger() {}

    public static void init(Context context) {}

    public static void i(String tag, String message) {}

    public static void w(String tag, String message) {}

    public static void e(String tag, String message, Throwable throwable) {}

    public static boolean isVerboseEnabled() { return false; }

    public static void setVerboseEnabled(Context context, boolean enabled) {}

    public static String read(Context context) {
        if (context == null) return "RTC diagnostics disabled";
        try {
            return context.getString(R.string.rtc_debug_disabled_msg);
        } catch (Exception ignored) {
            return "RTC diagnostics disabled";
        }
    }

    public static void clear(Context context) {}

    public static File getLogFile(Context context) { return null; }

    public static String signal(RtcSignal s) {
        if (s == null) return "signal=null";
        return "type=" + safe(s.type)
                + " callId=" + safe(s.callId)
                + " from=" + shortUid(s.fromUid)
                + " to=" + shortUid(s.toUid)
                + " mode=" + safe(s.mode)
                + " hasSdp=" + (!TextUtils.isEmpty(s.sdp))
                + " hasIce=" + (!TextUtils.isEmpty(s.candidate));
    }

    public static String shortUid(String uid) {
        if (TextUtils.isEmpty(uid)) return "";
        if (uid.length() <= 10) return uid;
        return uid.substring(0, 6) + "..." + uid.substring(uid.length() - 4);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
