package com.chat.rtc;

import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKContentType;
import com.chat.rtc.model.RtcCallRecordContent;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

/**
 * Saves one visible, local call-record message.
 *
 * This is intentionally local-only: each side writes its own result when the call ends.
 * Control signals are not used as chat history, so SDP/ICE/END never pollute the conversation list.
 */
public final class RtcCallRecordMessageSender {
    private RtcCallRecordMessageSender() {}

    public static void saveLocal(String callId, String peerUid, String peerName, int callType,
                                 boolean incoming, String reason, long connectedAt) {
        if (TextUtils.isEmpty(peerUid)) return;
        long duration = connectedAt > 0 ? Math.max(0L, (System.currentTimeMillis() - connectedAt) / 1000L) : 0L;
        RtcCallRecordContent content = RtcCallRecordContent.create(callId, peerUid, peerName, callType, incoming, reason, duration);

        WKMsg msg = new WKMsg();
        msg.channelID = peerUid;
        msg.channelType = WKChannelType.PERSONAL;
        msg.type = WKContentType.WK_RTC_CALL_RECORD;
        msg.baseContentMsgModel = content;
        msg.content = content.encodeMsg().toString();
        msg.timestamp = System.currentTimeMillis() / 1000L;
        msg.fromUID = incoming ? peerUid : WKConfig.getInstance().getUid();
        msg.status = WKSendMsgResult.send_success;
        try {
            long orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(0, peerUid, WKChannelType.PERSONAL);
            msg.orderSeq = orderSeq + 1;
        } catch (Exception ignored) {
        }
        try {
            WKIM.getInstance().getMsgManager().saveAndUpdateConversationMsg(msg, false);
        } catch (Exception ignored) {
        }
    }
}
