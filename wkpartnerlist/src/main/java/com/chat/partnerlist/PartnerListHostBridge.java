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
import com.chat.partnerlist.model.PartnerGreetingResponse;
import com.chat.uikit.partner.PartnerLocalMessageStore;
import com.xinbida.wukongim.entity.WKChannelType;

import java.lang.reflect.Method;

public final class PartnerListHostBridge {
    private PartnerListHostBridge() {}

    public static void openProfile(Context context, String uid) {
        openProfile(context, uid, "");
    }

    public static void openProfile(Context context, String uid, String vercode) {
        if (context == null || TextUtils.isEmpty(uid)) return;
        if (tryPartnerProfile(context, uid, vercode)) return;
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

    /**
     * The greeting REST endpoint delivers the message to the peer, but it does not pass through
     * this device's normal IM send pipeline. Save the exact server-normalized greeting locally
     * using the same stable client_msg_no, so a later IM sync updates instead of duplicating it.
     */
    public static void saveOutgoingGreeting(String uid, String text, PartnerGreetingResponse response) {
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(text) || response == null) return;
        PartnerLocalMessageStore.saveGreeting(
                uid,
                text,
                response.client_msg_no,
                response.message_id,
                response.message_seq,
                response.timestamp,
                response.last_greet_at);
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
            tryPartnerProfile(context, null, null);
        }
    }

    private static boolean tryPartnerProfile(Context context, String uid, String vercode) {
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            if (TextUtils.isEmpty(uid)) {
                route.getMethod("open", Context.class).invoke(null, context);
            } else {
                try {
                    route.getMethod("open", Context.class, String.class, String.class)
                            .invoke(null, context, uid, vercode);
                } catch (NoSuchMethodException ignored) {
                    route.getMethod("open", Context.class, String.class).invoke(null, context, uid);
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
