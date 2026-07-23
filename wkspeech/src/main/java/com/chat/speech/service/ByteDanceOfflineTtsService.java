package com.chat.speech.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.chat.speech.debug.SpeechDebugLog;
import com.chat.speech.engine.ByteDanceOfflineTtsEngine;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs the extracted vendor native engine outside the main app process. */
public final class ByteDanceOfflineTtsService extends Service {
    public static final String ACTION_SYNTHESIZE = "com.chat.speech.action.BYTEDANCE_SYNTHESIZE";
    public static final String ACTION_CANCEL = "com.chat.speech.action.BYTEDANCE_CANCEL";
    public static final String ACTION_RESULT = "com.chat.speech.action.BYTEDANCE_RESULT";

    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_PINYIN = "pinyin";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_VOICE = "voice";
    public static final String EXTRA_SAMPLE_RATE = "sample_rate";
    public static final String EXTRA_RATE_PERCENT = "rate_percent";
    public static final String EXTRA_PITCH_PERCENT = "pitch_percent";
    public static final String EXTRA_OK = "ok";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_ERROR = "error";

    private static final ByteDanceOfflineTtsEngine ENGINE = new ByteDanceOfflineTtsEngine();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "bytedance-offline-worker");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onCreate() {
        super.onCreate();
        SpeechDebugLog.append(this, "remote_service.onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            SpeechDebugLog.append(this, "remote_service.cancel");
            ENGINE.cancelActive();
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!ACTION_SYNTHESIZE.equals(action)) return START_NOT_STICKY;

        String requestId = value(intent, EXTRA_REQUEST_ID);
        String text = value(intent, EXTRA_TEXT);
        String pinyin = value(intent, EXTRA_PINYIN);
        String mode = value(intent, EXTRA_MODE);
        String voice = value(intent, EXTRA_VOICE);
        int sampleRate = intent.getIntExtra(EXTRA_SAMPLE_RATE, 24000);
        int ratePercent = intent.getIntExtra(EXTRA_RATE_PERCENT, 0);
        int pitchPercent = intent.getIntExtra(EXTRA_PITCH_PERCENT, 0);
        SpeechDebugLog.append(this, "remote_service.request id=" + requestId
                + " mode=" + mode + " text=" + abbreviate(text)
                + " pinyin=" + abbreviate(pinyin));

        EXECUTOR.execute(() -> {
            try {
                File file = ENGINE.synthesize(
                        getApplicationContext(), text, pinyin, mode, voice, sampleRate,
                        ratePercent, pitchPercent
                );
                SpeechDebugLog.append(this, "remote_service.success id=" + requestId
                        + " bytes=" + file.length());
                sendResult(requestId, true, file.getAbsolutePath(), null);
            } catch (Throwable error) {
                String message = describeThrowable(error);
                SpeechDebugLog.append(this, "remote_service.error id=" + requestId + " " + message);
                sendResult(requestId, false, null, message);
            } finally {
                stopSelf(startId);
            }
        });
        return START_NOT_STICKY;
    }

    private void sendResult(String requestId, boolean ok, String path, String error) {
        Intent result = new Intent(ACTION_RESULT);
        result.setPackage(getPackageName());
        result.putExtra(EXTRA_REQUEST_ID, requestId);
        result.putExtra(EXTRA_OK, ok);
        if (path != null) result.putExtra(EXTRA_FILE_PATH, path);
        if (error != null) result.putExtra(EXTRA_ERROR, error);
        sendBroadcast(result);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private static String describeThrowable(Throwable error) {
        if (error == null) return "未知错误";
        StringBuilder result = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) result.append(" <- ");
            result.append(current.getClass().getName());
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                result.append(": ").append(message.trim());
            }
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
            depth++;
        }
        StackTraceElement[] stack = error.getStackTrace();
        if (stack != null && stack.length > 0) {
            result.append(" @ ").append(stack[0]);
        }
        return result.toString();
    }

    private static String value(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        return value == null ? "" : value;
    }

    private static String abbreviate(String text) {
        if (text == null) return "";
        String clean = text.replace('\n', ' ').replace('\r', ' ');
        return clean.length() <= 80 ? clean : clean.substring(0, 80) + "...";
    }
}
