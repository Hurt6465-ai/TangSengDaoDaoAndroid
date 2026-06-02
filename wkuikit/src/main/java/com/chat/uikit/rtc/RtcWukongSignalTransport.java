package com.chat.uikit.rtc;

import android.text.TextUtils;

import com.chat.base.msgitem.WKContentType;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.lang.reflect.Field;

/**
 * WuKong IM transport for WebRTC signaling.
 *
 * Reliability first:
 * - Send RTC signaling as normal WK_TEXT so the other device receives invite/offer/answer/ice
 *   through the same path as normal chat messages.
 * - Do NOT force WK_INSIDE_MSG and do NOT force noPersist here. Some WuKong SDK/server
 *   combinations will not deliver those packets to the peer listener, which makes the callee
 *   unable to receive incoming calls.
 * - The chat UI must hide these packets with RtcSignalManager's strict prefix/protocol/type check.
 * - We still best-effort set noUnread/receipt=0/expire to reduce unread pollution.
 */
public class RtcWukongSignalTransport implements RtcSignalTransport {
    private static final int SIGNAL_EXPIRE_SECONDS = 5 * 60;

    @Override
    public void sendSignal(String peerUid, String payload) throws Exception {
        if (TextUtils.isEmpty(peerUid) || TextUtils.isEmpty(payload)) return;

        WKTextContent content = new WKTextContent(payload);
        try {
            content.type = WKContentType.WK_TEXT;
        } catch (Exception ignored) {
        }

        WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
        WKSendOptions options = new WKSendOptions();
        options.expire = SIGNAL_EXPIRE_SECONDS;

        markSignalButKeepDeliverable(content);
        markSignalButKeepDeliverable(options);

        WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
    }

    /**
     * Do not set noPersist here. noPersist/inside can break incoming-call delivery on some SDKs.
     */
    private void markSignalButKeepDeliverable(Object object) {
        if (object == null) return;
        setBooleanField(object, "noUnread", true);
        setBooleanField(object, "no_unread", true);
        setIntField(object, "expire", SIGNAL_EXPIRE_SECONDS);

        Object header = getFieldValue(object, "header");
        if (header != null && header != object) {
            setBooleanField(header, "noUnread", true);
            setBooleanField(header, "no_unread", true);
        }

        Object setting = getFieldValue(object, "setting");
        if (setting != null && setting != object) {
            setIntField(setting, "receipt", 0);
            setIntField(setting, "stream", 0);
        }
    }

    private Object getFieldValue(Object object, String name) {
        try {
            Field f = object.getClass().getField(name);
            f.setAccessible(true);
            return f.get(object);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setBooleanField(Object object, String name, boolean value) {
        try {
            Field f = object.getClass().getField(name);
            f.setAccessible(true);
            if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                f.set(object, value);
            } else if (f.getType() == int.class || f.getType() == Integer.class) {
                f.set(object, value ? 1 : 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void setIntField(Object object, String name, int value) {
        try {
            Field f = object.getClass().getField(name);
            f.setAccessible(true);
            if (f.getType() == int.class || f.getType() == Integer.class) {
                f.set(object, value);
            }
        } catch (Exception ignored) {
        }
    }
}
