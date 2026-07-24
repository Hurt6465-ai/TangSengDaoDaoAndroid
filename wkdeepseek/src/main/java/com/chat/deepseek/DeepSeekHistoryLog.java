package com.chat.deepseek;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.utils.WKFileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DeepSeek 覆盖层与聊天历史列表的临时诊断日志。
 *
 * 日志写入现有“开发日志”页面读取的 wkCrash 目录，用户无需连接电脑即可复制。
 * 不记录聊天正文，只记录窗口、列表、适配器数量和生命周期状态。
 */
public final class DeepSeekHistoryLog {
    private static final String TAG = "DeepSeekHistory";
    private static final Object LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "deepseek-history-log");
        thread.setDaemon(true);
        return thread;
    });
    private static final long MAX_FILE_BYTES = 512 * 1024L;
    private static final int MAX_LOG_FILES = 5;

    private static File activeFile;
    private static long sessionStartedAt;
    private static String sessionChannel = "";

    private DeepSeekHistoryLog() {
    }

    public static void begin(Context context, String channelId, byte channelType) {
        if (context == null) return;
        synchronized (LOCK) {
            try {
                File dir = new File(WKFileUtils.getInstance().getNormalFileSavePath("wkCrash"));
                if (!dir.exists() && !dir.mkdirs()) return;
                cleanupOldFiles(dir);
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                        .format(new Date());
                activeFile = new File(dir, "deepseek_history_" + stamp + ".log");
                sessionStartedAt = System.currentTimeMillis();
                sessionChannel = shortId(channelId) + "/" + channelType;
                appendLocked("SESSION_BEGIN channel=" + sessionChannel
                        + " sdk=" + Build.VERSION.SDK_INT
                        + " device=" + safe(Build.MANUFACTURER) + "/" + safe(Build.MODEL)
                        + " pid=" + Process.myPid());
            } catch (Throwable error) {
                Log.e(TAG, "begin failed", error);
            }
        }
    }

    public static void log(String event, String detail) {
        synchronized (LOCK) {
            if (activeFile == null) return;
            appendLocked(safe(event) + (TextUtils.isEmpty(detail) ? "" : " | " + detail));
        }
    }

    public static void log(String event) {
        log(event, "");
    }

    public static void end(String reason) {
        synchronized (LOCK) {
            if (activeFile == null) return;
            appendLocked("SESSION_END reason=" + safe(reason)
                    + " elapsedMs=" + Math.max(0L, System.currentTimeMillis() - sessionStartedAt)
                    + " channel=" + sessionChannel);
            // 保留 activeFile，使关闭后延迟快照和 Activity 生命周期仍能写入同一文件。
        }
    }

    public static String getActivePath() {
        synchronized (LOCK) {
            return activeFile == null ? "" : activeFile.getAbsolutePath();
        }
    }

    private static void appendLocked(String message) {
        if (activeFile == null) return;
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String line = timestamp
                + " [" + Thread.currentThread().getName() + "] "
                + message + "\n";
        File target = activeFile;
        Log.i(TAG, line.trim());
        IO.execute(() -> appendToFile(target, line));
    }

    private static void appendToFile(File target, String line) {
        if (target == null) return;
        try {
            if (target.exists() && target.length() > MAX_FILE_BYTES) return;
            try (FileWriter writer = new FileWriter(target, true)) {
                writer.write(line);
                writer.flush();
            }
        } catch (IOException error) {
            Log.e(TAG, "append failed", error);
        }
    }

    private static void cleanupOldFiles(File dir) {
        File[] files = dir.listFiles((parent, name) ->
                name != null && name.startsWith("deepseek_history_") && name.endsWith(".log"));
        if (files == null || files.length < MAX_LOG_FILES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int index = MAX_LOG_FILES - 1; index < files.length; index++) {
            // 删除旧日志后，为本次新文件留出一个位置。
            if (index >= MAX_LOG_FILES - 1) {
                //noinspection ResultOfMethodCallIgnored
                files[index].delete();
            }
        }
    }

    private static String shortId(String value) {
        if (TextUtils.isEmpty(value)) return "empty";
        String normalized = value.trim();
        if (normalized.length() <= 8) return normalized;
        return normalized.substring(0, 3) + "..." + normalized.substring(normalized.length() - 4);
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
