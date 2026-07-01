package com.chat.rtc;

import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

/**
 * Sends a real, user-visible call record as a normal chat message.
 *
 * RTC packets themselves are transient control messages and must not appear in chat history,
 * unread counters or conversation cover text. Tinode keeps call controls separate from
 * visible history; this class is the lightweight TangSeng equivalent for the final record.
 */
public final class RtcCallRecordMessageSender {
    private RtcCallRecordMessageSender() {}

    public static void send(String peerUid, int callType, String reason, long connectedAt) {
        if (TextUtils.isEmpty(peerUid)) return;
        String myUid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(myUid) && TextUtils.equals(myUid, peerUid)) return;

        long duration = connectedAt > 0 ? Math.max(0L, (System.currentTimeMillis() - connectedAt) / 1000L) : 0L;
        String text = RtcCallRecordReporter.buildDisplayText(callType, reason, duration);
        if (TextUtils.isEmpty(text)) return;

        try {
            WKTextContent content = new WKTextContent(text);
            WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
            WKSendOptions options = new WKSendOptions();
            try {
                if (options.setting != null) {
                    options.setting.receipt = 0;
                    options.setting.stream = 0;
                }
            } catch (Exception ignored) {
            }
            WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
        } catch (Exception ignored) {
        }
    }
}
