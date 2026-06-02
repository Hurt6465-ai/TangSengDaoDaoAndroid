package com.chat.uikit.rtc;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.lang.reflect.Field;

/**
 * WuKong IM transport for WebRTC signaling.
 *
 * This class must balance two requirements:
 * 1) The peer must receive invite/offer/answer/ice in the normal online message listener.
 * 2) Signaling packets must not become chat bubbles, unread badges, or cache pollution.
 *
 * Important implementation detail for WuKongIM:
 * - Keep payload as WK_TEXT. Do not change content.type to WK_INSIDE_MSG; some SDK/server
 *   combinations will not deliver those packets through the RTC listener used by the app.
 * - Use WKSendOptions.header.noPersist = true so the packet is an online signaling packet,
 *   not a stored chat message.
 * - Use WKSendOptions.header.redDot = false. WuKongIM uses redDot to decide whether the
 *   peer should show unread/red-dot behavior; there is no noUnread field in the SDK header.
 */
public class RtcWukongSignalTransport implements RtcSignalTransport {
    private static final int SIGNAL_EXPIRE_SECONDS = 5 * 60;

    @Override
    public void sendSignal(String peerUid, String payload) throws Exception {
        if (TextUtils.isEmpty(peerUid) || TextUtils.isEmpty(payload)) return;

        WKTextContent content = new WKTextContent(payload);
        WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
        WKSendOptions options = new WKSendOptions();

        applyRealtimeSignalOptions(options);
        WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
    }

    private void applyRealtimeSignalOptions(WKSendOptions options) {
        if (options == null) return;
        options.expire = SIGNAL_EXPIRE_SECONDS;
        try {
            if (options.header != null) {
                // Online delivery, no local/remote chat cache pollution.
                options.header.noPersist = true;
                // This is the real unread/red-dot switch in WKMsgHeader.
                options.header.redDot = false;
                // Do not set syncOnce. Multi-device users should still be able to receive calls.
            }
        } catch (Exception ignored) {
        }
        try {
            if (options.setting != null) {
                options.setting.receipt = 0;
                options.setting.stream = 0;
            }
        } catch (Exception ignored) {
        }

        // Reflection fallback for older/newer SDK variants or obfuscated fields.
        markByReflection(options);
        markByReflection(getFieldValue(options, "header"));
        markByReflection(getFieldValue(options, "setting"));
    }

    private void markByReflection(Object object) {
        if (object == null) return;
        setBooleanField(object, "noPersist", true);
        setBooleanField(object, "no_persist", true);
        setBooleanField(object, "redDot", false);
        setBooleanField(object, "red_dot", false);
        setIntField(object, "receipt", 0);
        setIntField(object, "stream", 0);
        setIntField(object, "expire", SIGNAL_EXPIRE_SECONDS);
    }

    private Object getFieldValue(Object object, String name) {
        if (object == null) return null;
        try {
            Field f = object.getClass().getField(name);
            f.setAccessible(true);
            return f.get(object);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setBooleanField(Object object, String name, boolean value) {
        if (object == null) return;
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
        if (object == null) return;
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
