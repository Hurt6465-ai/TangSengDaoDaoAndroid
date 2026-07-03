package com.chat.rtc;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.chat.rtc.model.RtcSignal;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Small file logger for RTC diagnostics. It is intentionally independent from Logcat so the
 * user can open/copy the log directly inside the Android app without a computer.
 */
public final class RtcDebugLogger {
    private static final String DIR_NAME = "logs";
    private static final String FILE_NAME = "wkrtc.log";
    private static final String OLD_FILE_NAME = "wkrtc.old.log";
    private static final String SP_NAME = "wkrtc_debug";
    private static final String KEY_VERBOSE = "verbose_enabled";
    private static final long MAX_BYTES = 768L * 1024L;
    private static final long READ_TAIL_BYTES = 512L * 1024L;

    private static Context appContext;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private RtcDebugLogger() {}

    public static void init(Context context) {
        if (context != null) appContext = context.getApplicationContext();
    }

    public static void i(String tag, String message) {
        if (!isVerboseEnabled()) return;
        write("I", tag, message, null);
    }

    public static void w(String tag, String message) {
        write("W", tag, message, null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        write("E", tag, message, throwable);
    }

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

    public static boolean isVerboseEnabled() {
        Context ctx = appContext;
        if (ctx == null) return false;
        try {
            return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(KEY_VERBOSE, false);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void setVerboseEnabled(Context context, boolean enabled) {
        Context ctx = context != null ? context.getApplicationContext() : appContext;
        if (ctx == null) return;
        appContext = ctx;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
            sp.edit().putBoolean(KEY_VERBOSE, enabled).apply();
        } catch (Exception ignored) {
        }
        write("W", "RtcDebugLogger", enabled ? "verbose log enabled" : "verbose log disabled", null);
    }

    public static String read(Context context) {
        Context ctx = context != null ? context.getApplicationContext() : appContext;
        if (ctx == null) return "RTC log context is null";
        synchronized (LOCK) {
            try {
                File file = logFile(ctx);
                if (!file.exists()) return "暂无 RTC 日志";
                long len = file.length();
                byte[] data;
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
                    if (len > READ_TAIL_BYTES) {
                        raf.seek(len - READ_TAIL_BYTES);
                        data = new byte[(int) READ_TAIL_BYTES];
                        raf.readFully(data);
                        return "只显示最后 " + (READ_TAIL_BYTES / 1024) + "KB 日志\n\n" + new String(data, StandardCharsets.UTF_8);
                    }
                    data = new byte[(int) len];
                    raf.readFully(data);
                }
                return new String(data, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "读取 RTC 日志失败: " + e.getMessage();
            }
        }
    }

    public static void clear(Context context) {
        Context ctx = context != null ? context.getApplicationContext() : appContext;
        if (ctx == null) return;
        synchronized (LOCK) {
            try { File file = logFile(ctx); if (file.exists()) file.delete(); } catch (Exception ignored) {}
            try { File old = oldLogFile(ctx); if (old.exists()) old.delete(); } catch (Exception ignored) {}
        }
        write("W", "RtcDebugLogger", "log cleared", null);
    }

    public static File getLogFile(Context context) {
        Context ctx = context != null ? context.getApplicationContext() : appContext;
        return ctx == null ? null : logFile(ctx);
    }

    private static void write(String level, String tag, String message, Throwable throwable) {
        Context ctx = appContext;
        if (ctx == null) return;
        synchronized (LOCK) {
            try {
                File file = logFile(ctx);
                rotateIfNeeded(file);
                StringBuilder sb = new StringBuilder(256);
                sb.append(FORMAT.format(new Date()))
                        .append(' ').append(level)
                        .append('/').append(TextUtils.isEmpty(tag) ? "RTC" : tag)
                        .append('[').append(Thread.currentThread().getName()).append("] ")
                        .append(message == null ? "" : message)
                        .append('\n');
                if (throwable != null) {
                    sb.append(throwable.getClass().getSimpleName()).append(": ")
                            .append(throwable.getMessage()).append('\n');
                }
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static File logDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File logFile(Context context) {
        return new File(logDir(context), FILE_NAME);
    }

    private static File oldLogFile(Context context) {
        return new File(logDir(context), OLD_FILE_NAME);
    }

    private static void rotateIfNeeded(File file) {
        try {
            if (file == null || !file.exists() || file.length() < MAX_BYTES) return;
            File old = oldLogFile(appContext);
            if (old.exists()) old.delete();
            file.renameTo(old);
        } catch (Exception ignored) {
        }
    }
}
