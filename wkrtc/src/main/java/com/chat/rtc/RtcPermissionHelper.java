package com.chat.rtc;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Runtime permission and system-setting helpers for call notifications.
 *
 * Android 13 requires POST_NOTIFICATIONS at runtime. Android 14 may also block full-screen
 * call intents unless the user grants the app full-screen notification access. The plugin
 * exposes these checks through WKRTCApplication endpoints so the host app can show a prompt
 * from Settings/notification UI before users miss background calls.
 */
public final class RtcPermissionHelper {
    private RtcPermissionHelper() {}

    public static boolean hasPostNotificationPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < 33) return true;
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canUseFullScreenIntent(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < 34) return true;
        try {
            NotificationManager nm = context.getApplicationContext().getSystemService(NotificationManager.class);
            return nm == null || nm.canUseFullScreenIntent();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean isIncomingCallNotificationReady(Context context) {
        return hasPostNotificationPermission(context) && canUseFullScreenIntent(context);
    }

    public static void openNotificationSettings(Context context) {
        if (context == null) return;
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(fallback); } catch (Exception ignored2) {}
        }
    }

    public static void openFullScreenIntentSettings(Context context) {
        if (context == null) return;
        Intent intent = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT")
                .setData(Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
            openNotificationSettings(context);
        }
    }
}
