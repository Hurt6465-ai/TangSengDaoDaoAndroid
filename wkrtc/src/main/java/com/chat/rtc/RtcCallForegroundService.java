package com.chat.rtc;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;

public class RtcCallForegroundService extends Service {
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_SCREEN_SHARING = "screen_sharing";

    public static void start(Context context, String callId, String peerName, int callType) {
        startInternal(context, callId, peerName, callType, false);
    }

    public static void startScreenShare(Context context, String callId, String peerName, int callType) {
        startInternal(context, callId, peerName, callType, true);
    }

    private static void startInternal(Context context, String callId, String peerName, int callType, boolean screenSharing) {
        if (context == null || TextUtils.isEmpty(callId)) return;
        Intent intent = new Intent(context.getApplicationContext(), RtcCallForegroundService.class);
        intent.setAction(RtcConstants.ACTION_START_CALL);
        intent.putExtra(RtcConstants.EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_NAME, peerName == null ? "" : peerName);
        intent.putExtra(RtcConstants.EXTRA_CALL_TYPE, callType);
        intent.putExtra(EXTRA_SCREEN_SHARING, screenSharing);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getApplicationContext().startForegroundService(intent);
            } else {
                context.getApplicationContext().startService(intent);
            }
        } catch (Exception e) {
            RtcDebugLogger.e("RtcForeground", "start failed screen=" + screenSharing, e);
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
        boolean screenSharing = intent != null && intent.getBooleanExtra(EXTRA_SCREEN_SHARING, false);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                if (screenSharing) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                } else if (RtcConstants.isVideo(callType)) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                }
                startForeground(RtcConstants.NOTIFICATION_ID_ACTIVE, RtcCallNotification.buildActive(this, callId, peerName, callType), type);
            } else {
                startForeground(RtcConstants.NOTIFICATION_ID_ACTIVE, RtcCallNotification.buildActive(this, callId, peerName, callType));
            }
        } catch (Exception e) {
            RtcDebugLogger.e("RtcForeground", "startForeground failed screen=" + screenSharing, e);
            startForeground(RtcConstants.NOTIFICATION_ID_ACTIVE, RtcCallNotification.buildActive(this, callId, peerName, callType));
        }
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
