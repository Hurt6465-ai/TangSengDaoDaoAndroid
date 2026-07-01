package com.chat.rtc;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;

public class RtcCallForegroundService extends Service {
    private static final String EXTRA_NAME = "name";

    public static void start(Context context, String callId, String peerName, int callType) {
        if (context == null || TextUtils.isEmpty(callId)) return;
        Intent intent = new Intent(context.getApplicationContext(), RtcCallForegroundService.class);
        intent.setAction(RtcConstants.ACTION_START_CALL);
        intent.putExtra(RtcConstants.EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_NAME, peerName == null ? "" : peerName);
        intent.putExtra(RtcConstants.EXTRA_CALL_TYPE, callType);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getApplicationContext().startForegroundService(intent);
            } else {
                context.getApplicationContext().startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context.getApplicationContext(), RtcCallForegroundService.class);
            context.getApplicationContext().stopService(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String callId = intent == null ? "" : intent.getStringExtra(RtcConstants.EXTRA_CALL_ID);
        String peerName = intent == null ? "" : intent.getStringExtra(EXTRA_NAME);
        int callType = intent == null ? RtcConstants.AUDIO : intent.getIntExtra(RtcConstants.EXTRA_CALL_TYPE, RtcConstants.AUDIO);
        startForeground(RtcConstants.NOTIFICATION_ID_ACTIVE, RtcCallNotification.buildActive(this, callId, peerName, callType));
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
