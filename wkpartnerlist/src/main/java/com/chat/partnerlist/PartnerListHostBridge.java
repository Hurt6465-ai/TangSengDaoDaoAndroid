package com.chat.partnerlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.activity.ComponentActivity;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.UserDetailMenu;
import com.xinbida.wukongim.entity.WKChannelType;

import java.lang.reflect.Method;

public final class PartnerListHostBridge {
    private PartnerListHostBridge() {}

    public static void openProfile(Context context, String uid) {
        if (context == null || TextUtils.isEmpty(uid)) return;
        if (tryPartnerProfile(context, uid)) return;
        EndpointManager.getInstance().invoke(EndpointSID.userDetailView, new UserDetailMenu(context, uid));
    }

    public static void openChat(Context context, String uid) {
        if (context == null || TextUtils.isEmpty(uid)) return;
        if (context instanceof ComponentActivity) {
            EndpointManager.getInstance().invoke(
                    EndpointSID.chatView,
                    new ChatViewMenu((ComponentActivity) context, uid, WKChannelType.PERSONAL, 0, false)
            );
            return;
        }
        try {
            Class<?> clazz = Class.forName("com.chat.uikit.chat.ChatActivity");
            Intent intent = new Intent(context, clazz);
            intent.putExtra("channelId", uid);
            intent.putExtra("channelType", WKChannelType.PERSONAL);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    public static void openProfileEdit(Context context) {
        openProfileEdit(context, -1);
    }

    public static void openProfileEdit(Context context, int requestCode) {
        if (context == null) return;
        try {
            Class<?> clazz = Class.forName("com.chat.partner.profile.PartnerProfileEditActivity");
            Intent intent = new Intent(context, clazz);
            if (context instanceof Activity && requestCode >= 0) {
                ((Activity) context).startActivityForResult(intent, requestCode);
            } else {
                if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Throwable ignored) {
            tryPartnerProfile(context, null);
        }
    }

    private static boolean tryPartnerProfile(Context context, String uid) {
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            Method method = TextUtils.isEmpty(uid)
                    ? route.getMethod("open", Context.class)
                    : route.getMethod("open", Context.class, String.class);
            if (TextUtils.isEmpty(uid)) method.invoke(null, context);
            else method.invoke(null, context, uid);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
