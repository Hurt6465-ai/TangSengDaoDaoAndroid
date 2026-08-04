package com.chat.uikit.partner;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists messages that were delivered by a business REST endpoint instead of the
 * normal WuKongIM Android send pipeline.
 *
 * saveAndUpdateConversationMsg may emit either a new-message callback or a refresh
 * callback depending on whether the same client_msg_no is already in the SDK DB. A
 * refresh callback cannot insert a bubble that is absent from the currently open
 * ChatActivity, so this class also emits a small local UI notification. The chat
 * screen de-duplicates by client_msg_no/message_id before inserting.
 */
public final class PartnerLocalMessageStore {
    public interface Listener {
        void onOutgoingMessageSaved(WKMsg msg);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PartnerLocalMessageStore() {
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    public static void saveGreeting(String peerUid,
                                    String text,
                                    String clientMsgNo,
                                    String messageId,
                                    int messageSeq,
                                    long timestamp,
                                    long lastGreetAt) {
        if (TextUtils.isEmpty(peerUid) || TextUtils.isEmpty(text)) return;
        try {
            WKTextContent content = new WKTextContent(text);
            JSONObject json = content.encodeMsg();
            if (json == null) json = new JSONObject();
            json.put("type", content.type);

            WKMsg msg = new WKMsg();
            msg.channelID = peerUid;
            msg.channelType = WKChannelType.PERSONAL;
            msg.type = content.type;
            msg.baseContentMsgModel = content;
            msg.content = json.toString();
            msg.fromUID = WKConfig.getInstance().getUid();
            msg.clientMsgNO = resolveGreetingClientMsgNo(
                    msg.fromUID, peerUid, lastGreetAt, clientMsgNo);
            if (!TextUtils.isEmpty(messageId)) msg.messageID = messageId;
            msg.messageSeq = Math.max(0, messageSeq);
            msg.timestamp = normalizeTimestamp(timestamp, lastGreetAt);
            msg.status = WKSendMsgResult.send_success;
            saveAndNotify(msg);
        } catch (Throwable ignored) {
            // The REST request already delivered the message. Local persistence must
            // never trigger another network send or turn that success into a failure.
        }
    }

    public static void saveAndNotify(WKMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.channelID)
                || msg.channelType != WKChannelType.PERSONAL
                || TextUtils.isEmpty(msg.clientMsgNO)) {
            return;
        }
        try {
            long maxOrderSeq = WKIM.getInstance().getMsgManager()
                    .getMaxOrderSeqWithChannel(msg.channelID, msg.channelType);
            if (msg.orderSeq <= 0) msg.orderSeq = maxOrderSeq + 1L;
            WKIM.getInstance().getMsgManager().saveAndUpdateConversationMsg(msg, false);

            // WuKongIM 1.5.0 creates the conversation row first, but the immediate
            // WKUIConversationMsg callback does not attach its WKMsg. That makes the
            // first REST-delivered greeting appear as a blank preview until another
            // message or a full conversation reload arrives. Re-read the stored row,
            // attach it explicitly, and emit one content-complete refresh.
            WKMsg persisted = WKIM.getInstance().getMsgManager()
                    .getWithClientMsgNO(msg.clientMsgNO);
            if (persisted == null) persisted = msg;

            WKUIConversationMsg conversation = WKIM.getInstance()
                    .getConversationManager()
                    .getUIConversationMsg(msg.channelID, msg.channelType);
            if (conversation == null) {
                conversation = WKIM.getInstance().getConversationManager()
                        .updateWithWKMsg(persisted);
            }
            if (conversation != null) {
                conversation.setWkMsg(persisted);
                WKIM.getInstance().getConversationManager()
                        .setOnRefreshMsg(conversation, "PartnerLocalMessageStore");
            }
            notifySaved(persisted);
        } catch (Throwable ignored) {
            // Do not retry transmission: the peer has already received this message.
        }
    }

    private static String resolveGreetingClientMsgNo(String fromUid,
                                                     String peerUid,
                                                     long lastGreetAt,
                                                     String serverClientMsgNo) {
        if (!TextUtils.isEmpty(serverClientMsgNo)) return serverClientMsgNo;
        if (!TextUtils.isEmpty(fromUid) && !TextUtils.isEmpty(peerUid) && lastGreetAt > 0) {
            try {
                String source = fromUid + '\u0000' + peerUid + '\u0000' + lastGreetAt;
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(source.getBytes(StandardCharsets.UTF_8));
                final char[] alphabet = "0123456789abcdef".toCharArray();
                StringBuilder hex = new StringBuilder(digest.length * 2);
                for (byte value : digest) {
                    int unsigned = value & 0xff;
                    hex.append(alphabet[unsigned >>> 4]);
                    hex.append(alphabet[unsigned & 0x0f]);
                }
                return "partner-greeting:" + hex.substring(0, 52);
            } catch (Throwable ignored) {
            }
        }
        // Never invent a random identity here. If a legacy server returns neither
        // client_msg_no nor last_greet_at, waiting for IM sync is safer than creating
        // a local row that cannot be de-duplicated from the server-delivered greeting.
        return "";
    }

    private static long normalizeTimestamp(long timestamp, long lastGreetAt) {
        long value = timestamp;
        if (value <= 0) value = lastGreetAt;
        if (value > 100000000000L) value /= 1000L;
        return value > 0 ? value : System.currentTimeMillis() / 1000L;
    }

    private static void notifySaved(WKMsg msg) {
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) {
                try {
                    listener.onOutgoingMessageSaved(msg);
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
