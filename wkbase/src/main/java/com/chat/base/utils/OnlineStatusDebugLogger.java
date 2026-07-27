package com.chat.base.utils;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Temporary phone-readable diagnostics for stale conversation online state.
 * Files are stored in the existing wkCrash directory and can be forwarded from
 * Settings -> Error data without adb.
 */
public final class OnlineStatusDebugLogger {
    private static final String TAG = "TSOnlineStatus";
    private static final String FILE_NAME = "online_status_debug.log";
    private static final long MAX_BYTES = 512L * 1024L;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private OnlineStatusDebugLogger() {
    }

    public static void log(String event, String detail) {
        String safeDetail = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        String line;
        synchronized (FORMAT) {
            line = FORMAT.format(new Date()) + " [" + event + "] " + safeDetail;
        }
        Log.i(TAG, line);
        synchronized (LOCK) {
            try {
                String dirPath = WKFileUtils.getInstance().getNormalFileSavePath("wkCrash");
                File dir = new File(dirPath);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, FILE_NAME);
                if (file.exists() && file.length() >= MAX_BYTES) {
                    try (FileWriter clear = new FileWriter(file, false)) {
                        clear.write("");
                    }
                }
                try (FileWriter writer = new FileWriter(file, true)) {
                    writer.write(line);
                    writer.write('\n');
                }
            } catch (Throwable error) {
                Log.e(TAG, "write online status log failed", error);
            }
        }
    }
}
