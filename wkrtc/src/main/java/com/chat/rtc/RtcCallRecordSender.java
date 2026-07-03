package com.chat.rtc;

import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Save a call record only in the local chat database.
 *
 * Do not send this record to the peer through IM. Each side should create its own call record
 * from its own call lifecycle. Sending the record to the peer was the reason one video call
 * could show multiple records on the other device, and terminal signals without mode could
 * be rendered as an extra audio call record.
 */
public final class RtcCallRecordSender {
    private RtcCallRecordSender() {}

    public static void send(String peerUid, String callId, String peerName, int callType,
                            boolean incoming, String reason, long durationSeconds, String displayText) {
        if (TextUtils.isEmpty(peerUid)) return;
        try {
            RtcCallRecordContent content = new RtcCallRecordContent();
            content.callId = callId;
            content.peerName = peerName;
            content.reason = reason;
            content.incoming = incoming;
            content.callType = callType;
            content.durationSeconds = durationSeconds;
            content.timestamp = System.currentTimeMillis();
            content.displayText = displayText;

            JSONObject json = content.encodeMsg();
            json.put("type", content.type);

            WKMsg msg = new WKMsg();
            msg.channelID = peerUid;
            msg.channelType = WKChannelType.PERSONAL;
            msg.type = content.type;
            msg.content = json.toString();
            msg.baseContentMsgModel = content;
            msg.fromUID = WKConfig.getInstance().getUid();
            msg.clientMsgNO = "rtc_record_" + (TextUtils.isEmpty(callId) ? UUID.randomUUID().toString() : callId + "_" + reason);
            msg.timestamp = System.currentTimeMillis() / 1000L;
            msg.status = WKSendMsgResult.send_success;
            try {
                long maxOrderSeq = WKIM.getInstance().getMsgManager().getMaxOrderSeqWithChannel(peerUid, WKChannelType.PERSONAL);
                msg.orderSeq = maxOrderSeq + 1;
            } catch (Exception ignored) {
            }

            WKIM.getInstance().getMsgManager().saveAndUpdateConversationMsg(msg, false);
        } catch (Exception ignored) {
        }
    }
}
