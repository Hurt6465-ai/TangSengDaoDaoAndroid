package com.chat.speech.debug;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent breadcrumbs for failures that kill the vendor native process before Logcat is copied. */
public final class SpeechDebugLog {
    private static final String TAG = "ByteDanceOfflineTts";
    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 256L * 1024L;

    private SpeechDebugLog() {
    }

    public static void append(Context context, String message) {
        if (context == null) return;
        String safe = message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
        String line = timestamp() + " pid=" + Process.myPid() + " process=" + processName()
                + " | " + safe + "\n";
        Log.i(TAG, safe);
        synchronized (LOCK) {
            try {
                File file = file(context);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                if (file.length() > MAX_BYTES) rotate(file);
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    try {
                        out.getFD().sync();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable error) {
                Log.w(TAG, "Unable to persist debug log", error);
            }
        }
    }

    public static String read(Context context) {
        synchronized (LOCK) {
            File file = file(context);
            if (!file.isFile()) return "暂无离线语音日志。";
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) result.append(line).append('\n');
            } catch (Throwable error) {
                return "读取日志失败：" + error.getMessage();
            }
            return result.length() == 0 ? "暂无离线语音日志。" : result.toString();
        }
    }

    public static String lastLine(Context context) {
        String text = read(context).trim();
        if (text.isEmpty() || "暂无离线语音日志。".equals(text)) return "暂无测试记录";
        int index = text.lastIndexOf('\n');
        return index >= 0 ? text.substring(index + 1) : text;
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            File file = file(context);
            if (file.exists()) file.delete();
        }
    }

    public static File file(Context context) {
        return new File(new File(context.getFilesDir(), "wkspeech"), "bytedance_debug.log");
    }

    private static void rotate(File file) {
        File old = new File(file.getParentFile(), file.getName() + ".old");
        old.delete();
        file.renameTo(old);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String processName() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                String value = Application.getProcessName();
                if (value != null && !value.isEmpty()) return value;
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }
}
