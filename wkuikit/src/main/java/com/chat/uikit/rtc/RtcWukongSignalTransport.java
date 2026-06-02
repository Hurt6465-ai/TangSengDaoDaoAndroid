package com.chat.uikit.rtc;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WuKong IM transport for WebRTC signaling.
 *
 * 关键约束：
 * 1) 对端必须能通过正常在线消息监听收到 invite/offer/answer/ice。
 * 2) 信令包绝不能变成聊天气泡、未读红点或缓存污染。
 *
 * 实现要点：
 * - payload 保持 WK_TEXT，不改成 WK_INSIDE_MSG（某些 SDK/服务端组合不会通过 RTC 监听投递）。
 * - header.noPersist = true：在线信令包，不落库为聊天消息。
 * - header.redDot = false：避免对端产生未读红点。
 * - 反射兜底使用 getDeclaredField 遍历继承链（getField 只能拿 public 字段，
 *   会导致混淆或非 public 字段设置失败，从而信令被当普通消息持久化）。
 */
public class RtcWukongSignalTransport implements RtcSignalTransport {
    private static final int SIGNAL_EXPIRE_SECONDS = 5 * 60;

    // 反射字段缓存：避免每次发信令都全量反射，保证性能。key = "className#fieldName"
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Field FIELD_NOT_FOUND;

    static {
        Field placeholder = null;
        try {
            placeholder = RtcWukongSignalTransport.class.getDeclaredField("SIGNAL_EXPIRE_SECONDS");
        } catch (NoSuchFieldException ignored) {
        }
        FIELD_NOT_FOUND = placeholder;
    }

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
                // 在线投递，不污染本地/远端聊天缓存。
                options.header.noPersist = true;
                // WKMsgHeader 中真正的未读/红点开关。
                options.header.redDot = false;
                // 不设置 syncOnce，多端用户仍应能收到通话。
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

        // 反射兜底（适配旧/新 SDK 变体或混淆字段）
        markByReflection(options);
        markByReflection(getFieldValue(options, "header"));
        markByReflection(getFieldValue(options, "setting"));
    }

    private void markByReflection(Object object
