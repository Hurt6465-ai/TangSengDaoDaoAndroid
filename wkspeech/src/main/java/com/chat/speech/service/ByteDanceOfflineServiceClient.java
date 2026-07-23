package com.chat.speech.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import com.chat.speech.debug.SpeechDebugLog;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Synchronous client used from SpeechManager's worker thread. */
public final class ByteDanceOfflineServiceClient {
    private static final long REMOTE_TIMEOUT_SECONDS = 55L;
    private final Context app;

    public ByteDanceOfflineServiceClient(Context context) {
        app = context.getApplicationContext();
    }

    public File synthesize(
            String text,
            String pinyin,
            String mode,
            String voice,
            int sampleRate,
            int ratePercent,
            int pitchPercent
    ) throws Exception {
        String requestId = UUID.randomUUID().toString();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !requestId.equals(intent.getStringExtra(
                        ByteDanceOfflineTtsService.EXTRA_REQUEST_ID))) return;
                if (intent.getBooleanExtra(ByteDanceOfflineTtsService.EXTRA_OK, false)) {
                    path.set(intent.getStringExtra(ByteDanceOfflineTtsService.EXTRA_FILE_PATH));
                } else {
                    error.set(intent.getStringExtra(ByteDanceOfflineTtsService.EXTRA_ERROR));
                }
                latch.countDown();
            }
        };

        IntentFilter filter = new IntentFilter(ByteDanceOfflineTtsService.ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            //noinspection UnspecifiedRegisterReceiverFlag
            app.registerReceiver(receiver, filter);
        }
        try {
            SpeechDebugLog.append(app, "main_client.start id=" + requestId);
            Intent request = new Intent(app, ByteDanceOfflineTtsService.class);
            request.setAction(ByteDanceOfflineTtsService.ACTION_SYNTHESIZE);
            request.putExtra(ByteDanceOfflineTtsService.EXTRA_REQUEST_ID, requestId);
            request.putExtra(ByteDanceOfflineTtsService.EXTRA_TEXT, text);
            request.putExtra(ByteDanceOfflineTtsService.EXTRA_PINYIN, pinyin);
            request.putExtra(ByteDanceOfflineTtsService.EXTRA_MODE, mode);
            request.putExtra(ByteDanceOfflineTtsService.EXTRA_VOICE, voice);
            request.putExtra(ByteDanceOfflineTtsService.EXTRA_SAMPLE_RATE, sampleRate);
            request.putExtra(ByteDanceOfflineTtsService.EXTRA_RATE_PERCENT, ratePercent);
            request.putExtra(ByteDanceOfflineTtsService.EXTRA_PITCH_PERCENT, pitchPercent);
            if (app.startService(request) == null) {
                throw new IllegalStateException("无法启动字节离线语音进程");
            }
            if (!latch.await(REMOTE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                String last = SpeechDebugLog.lastLine(app);
                throw new IllegalStateException("离线语音子进程无响应或已崩溃。最后阶段：" + last);
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("离线语音已取消");
            }
            if (error.get() != null && !error.get().isEmpty()) {
                throw new IllegalStateException(error.get());
            }
            String resultPath = path.get();
            if (resultPath == null || resultPath.isEmpty()) {
                throw new IllegalStateException("离线语音没有返回音频路径");
            }
            File file = new File(resultPath);
            if (!file.isFile() || file.length() <= 44L) {
                throw new IllegalStateException("离线语音文件无效：" + resultPath);
            }
            SpeechDebugLog.append(app, "main_client.success id=" + requestId
                    + " bytes=" + file.length());
            return file;
        } finally {
            try {
                app.unregisterReceiver(receiver);
            } catch (Throwable ignored) {
            }
        }
    }

    public void cancelActive() {
        try {
            Intent intent = new Intent(app, ByteDanceOfflineTtsService.class);
            intent.setAction(ByteDanceOfflineTtsService.ACTION_CANCEL);
            app.startService(intent);
        } catch (Throwable error) {
            SpeechDebugLog.append(app, "main_client.cancel_error " + error.getMessage());
        }
    }
}
