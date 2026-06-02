package com.chat.uikit.rtc;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.chat.base.msgitem.WKContentType;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.lang.reflect.Field;

/**
 * WuKong IM transport for WebRTC signaling.
 *
 * Important:
 * These packets are not user messages. They should not create chat bubbles or unread counts.
 * Different WuKong SDK versions expose the no-persist/no-unread flags in different places,
 * so this class sets all known flags by reflection and still keeps a short expire time.
 */
public class RtcWukongSignalTransport implements RtcSignalTransport {
    private static final int SIGNAL_EXPIRE_SECONDS = 5 * 60;

    @Override
    public void sendSignal(String peerUid, String payload) throws Exception {
        if (TextUtils.isEmpty(peerUid) || TextUtils.isEmpty(payload)) return;
        WKTextContent content = new WKTextContent(payload);
        // RTC packets are not chat messages. Mark them as inside/no-persist as early
        // as possible so they do not enter conversation cache or unread counters.
        try {
            content.type = WKContentType.WK_INSIDE_MSG;
        } catch (Exception ignored) {
        }
        WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
        WKSendOptions options = new WKSendOptions();
        options.expire = SIGNAL_EXPIRE_SECONDS;

        // Best-effort compatibility across WuKong SDK versions.
        // If a field does not exist, reflection just ignores it.
        markNoPersistAndNoUnread(content);
        markNoPersistAndNoUnread(options);

        WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
    }

    private void markNoPersistAndNoUnread(Object object) {
        if (object == null) return;
        setBooleanField(object, "noPersist", true);
        setBooleanField(object, "no_persist", true);
        setBooleanField(object, "noUnread", true);
        setBooleanField(object, "no_unread", true);
        setIntField(object, "expire", SIGNAL_EXPIRE_SECONDS);

        Object header = getFieldValue(object, "header");
        if (header != null && header != object) {
            setBooleanField(header, "noPersist", true);
            setBooleanField(header, "no_persist", true);
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
