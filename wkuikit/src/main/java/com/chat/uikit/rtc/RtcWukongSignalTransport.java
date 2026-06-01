package com.chat.uikit.rtc;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

/**
 * WuKong IM transport for WebRTC signaling.
 *
 * 信令通过悟空 IM 的个人频道发送，ChatActivity 会在渲染前过滤掉这些文本。
 * 这里给信令设置 5 分钟过期，避免数据库里长期堆积 offer/answer/ice 文本。
 */
public class RtcWukongSignalTransport implements RtcSignalTransport {
    private static final int SIGNAL_EXPIRE_SECONDS = 5 * 60;

    @Override
    public void sendSignal(String peerUid, String payload) throws Exception {
        if (TextUtils.isEmpty(peerUid) || TextUtils.isEmpty(payload)) return;
        WKTextContent content = new WKTextContent(payload);
        WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
        WKSendOptions options = new WKSendOptions();
        options.expire = SIGNAL_EXPIRE_SECONDS;
        WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
    }
}
