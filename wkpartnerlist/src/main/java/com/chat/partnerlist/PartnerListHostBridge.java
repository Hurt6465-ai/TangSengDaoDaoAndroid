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
import com.chat.base.config.WKConfig;
import com.chat.partnerlist.model.PartnerGreetingResponse;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.UUID;

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
     * this device's normal IM send pipeline. Save one successful outgoing copy locally so the
     * sender sees the same greeting in chat history without transmitting it a second time.
     */
    public static void saveOutgoingGreeting(String uid, String text, PartnerGreetingResponse response) {
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(text)) return;
        try {
            WKTextContent content = new WKTextContent(text);
            JSONObject json = content.encodeMsg();
            if (json == null) json = new JSONObject();
            json.put("type", content.type);

            WKMsg msg = new WKMsg();
            msg.channelID = uid;
            msg.channelType = WKChannelType.PERSONAL;
            msg.type = content.type;
            msg.baseContentMsgModel = content;
            msg.content = json.toString();
            msg.fromUID = WKConfig.getInstance().getUid();
            msg.clientMsgNO = response != null && !TextUtils.isEmpty(response.client_msg_no)
                    ? response.client_msg_no : "partner_greeting_" + UUID.randomUUID().toString().replace("-", "");
            if (response != null && !TextUtils.isEmpty(response.message_id)) msg.messageID = response.message_id;
            msg.messageSeq = response == null ? 0 : Math.max(0, response.message_seq);
            long serverTimestamp = response == null ? 0L : response.timestamp;
            if (serverTimestamp > 100000000000L) serverTimestamp /= 1000L;
            msg.timestamp = serverTimestamp > 0 ? serverTimestamp : System.currentTimeMillis() / 1000L;
            msg.status = WKSendMsgResult.send_success;
            long maxOrderSeq = WKIM.getInstance().getMsgManager().getMaxOrderSeqWithChannel(uid, WKChannelType.PERSONAL);
            msg.orderSeq = maxOrderSeq + 1;
            WKIM.getInstance().getMsgManager().saveAndUpdateConversationMsg(msg, false);
        } catch (Throwable ignored) {
            // The REST greeting already succeeded; local persistence must never turn it into a failure.
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
