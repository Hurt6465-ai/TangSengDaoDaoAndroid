package com.chat.rtc;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;

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

            WKSendOptions options = new WKSendOptions();
            try { options.header.redDot = false; } catch (Exception ignored) {}
            try { options.header.noPersist = false; } catch (Exception ignored) {}
            try { options.setting.receipt = 0; } catch (Exception ignored) {}

            WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
            WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
        } catch (Exception ignored) {
        }
    }
}
