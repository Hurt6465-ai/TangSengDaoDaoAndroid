package com.chat.rtc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import com.chat.base.config.WKConstants;
import com.chat.rtc.model.RtcSignal;

public final class RtcCallNotification {
    private RtcCallNotification() {}

    public static boolean showIncoming(Context context, RtcSignal signal, String peerName, String peerAvatar, int callType) {
        if (context == null || signal == null) return false;
        Context app = context.getApplicationContext();
        ensureChannel(app);
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return false;
        if (!RtcPermissionHelper.hasPostNotificationPermission(app)) return false;
        boolean allowFullScreen = RtcPermissionHelper.canUseFullScreenIntent(app);

        String title = TextUtils.isEmpty(peerName) ? app.getString(R.string.rtc_friend) : peerName;
        String text = RtcConstants.isVideo(callType) ? app.getString(R.string.rtc_invite_video) : app.getString(R.string.rtc_invite_audio);
        PendingIntent fullScreen = callActivityIntent(app, signal, title, peerAvatar, callType, false);
        PendingIntent answer = callActivityIntent(app, signal, title, peerAvatar, callType, true);
        PendingIntent reject = rejectIntent(app, signal, title, callType);

        Notification.Builder builder = new Notification.Builder(app)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreen, allowFullScreen)
                .setContentIntent(fullScreen)
                .setUsesChronometer(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(WKConstants.newRTCChannelID);
        }

        Bitmap largeIcon = decodeCallIcon(app);
        if (largeIcon != null) builder.setLargeIcon(largeIcon);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person.Builder personBuilder = new Person.Builder()
                    .setName(title)
                    .setKey(signal.fromUid == null ? signal.callId : signal.fromUid);
            if (largeIcon != null) personBuilder.setIcon(Icon.createWithBitmap(largeIcon));
            builder.setStyle(Notification.CallStyle.forIncomingCall(personBuilder.build(), reject, answer));
        } else {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, app.getString(R.string.rtc_reject), reject);
            builder.addAction(android.R.drawable.stat_sys_phone_call, app.getString(R.string.rtc_accept), answer);
        }

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_INSISTENT;
        nm.notify(RtcConstants.NOTIFICATION_ID_INCOMING, notification);
        return true;
    }

    public static Notification buildActive(Context context, String callId, String peerName, int callType) {
        Context app = context.getApplicationContext();
        ensureChannel(app);
        String title = TextUtils.isEmpty(peerName) ? app.getString(R.string.rtc_friend) : peerName;
        String text = RtcConstants.isVideo(callType) ? app.getString(R.string.rtc_invite_video) : app.getString(R.string.rtc_invite_audio);
        PendingIntent content = openCurrentCallIntent(app, callId);
        Notification.Builder builder = new Notification.Builder(app)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(content)
                .setUsesChronometer(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(WKConstants.newRTCChannelID);
        }
        Bitmap icon = decodeCallIcon(app);
        if (icon != null) builder.setLargeIcon(icon);
        return builder.build();
    }

    public static void cancelIncoming(Context context) {
        if (context == null) return;
        NotificationManager nm = context.getApplicationContext().getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(RtcConstants.NOTIFICATION_ID_INCOMING);
    }

    private static PendingIntent callActivityIntent(Context context, RtcSignal signal, String peerName, String peerAvatar, int callType, boolean autoAccept) {
        Intent intent = new Intent(context, RtcCallActivity.class);
        intent.setAction(autoAccept ? RtcConstants.ACTION_ANSWER_CALL : RtcConstants.ACTION_INCOMING_CALL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        intent.putExtra(RtcConstants.EXTRA_CALL_ID, signal.callId);
        intent.putExtra(RtcConstants.EXTRA_PEER_UID, signal.fromUid);
        intent.putExtra(RtcConstants.EXTRA_PEER_NAME, peerName);
        intent.putExtra(RtcConstants.EXTRA_PEER_AVATAR, peerAvatar == null ? "" : peerAvatar);
        intent.putExtra(RtcConstants.EXTRA_CALL_TYPE, callType);
        intent.putExtra(RtcConstants.EXTRA_INCOMING, true);
        intent.putExtra(RtcConstants.EXTRA_AUTO_ACCEPT, autoAccept);
        int requestCode = autoAccept ? 7612 : 7611;
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent rejectIntent(Context context, RtcSignal signal, String peerName, int callType) {
        Intent intent = new Intent(context, RtcCallActionReceiver.class);
        intent.setAction(RtcConstants.ACTION_REJECT_CALL);
        intent.putExtra(RtcConstants.EXTRA_CALL_ID, signal.callId);
        intent.putExtra(RtcConstants.EXTRA_PEER_UID, signal.fromUid);
        intent.putExtra(RtcConstants.EXTRA_PEER_NAME, peerName);
        intent.putExtra(RtcConstants.EXTRA_CALL_TYPE, callType);
        return PendingIntent.getBroadcast(context, 7613, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openCurrentCallIntent(Context context, String callId) {
        Intent intent = new Intent(context, RtcCallActivity.class);
        intent.setAction(RtcConstants.ACTION_START_CALL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(RtcConstants.EXTRA_CALL_ID, callId == null ? "" : callId);
        return PendingIntent.getActivity(context, 7614, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context == null) return;

        Context app = context.getApplicationContext();
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel existing = nm.getNotificationChannel(WKConstants.newRTCChannelID);
        if (existing != null) return;

        CharSequence name = app.getString(R.string.new_rtc_notification);
        String description = app.getString(R.string.new_rtc_notification_desc);
        NotificationChannel channel = new NotificationChannel(
                WKConstants.newRTCChannelID,
                name,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(description);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 320, 120, 320, 900});

        try {
            Uri sound = Uri.parse("android.resource://" + app.getPackageName() + "/" + R.raw.wkrtc_newrtc);
            channel.setSound(sound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } catch (Exception ignored) {
        }

        nm.createNotificationChannel(channel);
    }

    private static Bitmap decodeCallIcon(Context context) {
        try {
            return BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_call);
        } catch (Exception ignored) {
            return null;
        }
    }
}
