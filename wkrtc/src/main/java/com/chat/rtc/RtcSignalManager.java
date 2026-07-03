package com.chat.rtc;

import android.text.TextUtils;

import com.chat.base.msgitem.WKContentType;
import com.chat.rtc.model.RtcSignal;
import com.chat.rtc.model.RtcSignalContent;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RtcSignalManager {
    private static final RtcSignalManager INSTANCE = new RtcSignalManager();
    private String myUid = "";
    private RtcSignalTransport transport;
    private RtcSignalDelegate delegate;
    private final Map<String, Long> seenSignals = new HashMap<>();

    public static RtcSignalManager get() { return INSTANCE; }

    public void configure(String myUid, RtcSignalTransport transport, RtcSignalDelegate delegate) {
        this.myUid = myUid == null ? "" : myUid;
        this.transport = transport;
        this.delegate = delegate;
        RtcDebugLogger.i("RtcSignalManager", "configure my=" + RtcDebugLogger.shortUid(this.myUid)
                + " transport=" + (transport != null) + " delegate=" + (delegate != null));
    }

    public String myUid() { return myUid; }

    /**
     * Return true only when this message is a confirmed RTC signal.
     * Never consume normal cached text/image/video/voice messages.
     */
    public boolean tryHandleIncomingMsg(WKMsg msg) {
        String text = extractSignalText(msg);
        if (!TextUtils.isEmpty(text)) {
            RtcDebugLogger.i("RtcSignalManager", "msg has rtc text msgType=" + (msg == null ? -1 : msg.type)
                    + " from=" + RtcDebugLogger.shortUid(msg == null ? "" : msg.fromUID)
                    + " channel=" + RtcDebugLogger.shortUid(msg == null ? "" : msg.channelID));
        }
        return tryHandleIncomingText(text);
    }

    /**
     * Used by ChatActivity history/cache filtering. Keep it very strict.
     */
    public static boolean isSignalMsg(WKMsg msg) {
        return !TextUtils.isEmpty(extractSignalText(msg));
    }

    public static String extractSignalText(WKMsg msg) {
        if (msg == null) return "";

        // Dedicated RTC signal content. This is the preferred path and should never render as text.
        if (msg.type == WKContentType.WK_RTC_SIGNAL) {
            String text = pickValidSignalText(msg.content);
            if (!TextUtils.isEmpty(text)) return text;
            try {
                if (msg.baseContentMsgModel instanceof RtcSignalContent) {
                    text = pickValidSignalText(((RtcSignalContent) msg.baseContentMsgModel).payload);
                    if (!TextUtils.isEmpty(text)) return text;
                }
                if (msg.baseContentMsgModel != null) {
                    JSONObject jsonObject = msg.baseContentMsgModel.encodeMsg();
                    if (jsonObject != null) {
                        text = pickValidSignalText(jsonObject.optString("payload", ""));
                        if (!TextUtils.isEmpty(text)) return text;
                        text = pickValidSignalText(jsonObject.optString("content", ""));
                        if (!TextUtils.isEmpty(text)) return text;
                        text = pickValidSignalText(jsonObject.optString("text", ""));
                        if (!TextUtils.isEmpty(text)) return text;
                    }
                }
            } catch (Exception ignored) {
            }
            return "";
        }

        if (msg.type != WKContentType.WK_TEXT && msg.type != WKContentType.WK_INSIDE_MSG) return "";

        // 1) Prefer raw SDK content. Do not inspect media payloads or generic display text.
        String text = pickValidSignalText(msg.content);
        if (!TextUtils.isEmpty(text)) return text;

        // 2) Some WK SDK versions store text in encoded content/text fields.
        try {
            if (msg.baseContentMsgModel != null) {
                JSONObject jsonObject = msg.baseContentMsgModel.encodeMsg();
                if (jsonObject != null) {
                    text = pickValidSignalText(jsonObject.optString("payload", ""));
                    if (!TextUtils.isEmpty(text)) return text;
                    text = pickValidSignalText(jsonObject.optString("content", ""));
                    if (!TextUtils.isEmpty(text)) return text;
                    text = pickValidSignalText(jsonObject.optString("text", ""));
                    if (!TextUtils.isEmpty(text)) return text;
                }
            }
        } catch (Exception ignored) {
        }

        // 3) Last fallback for text messages only. Still requires exact prefix + valid protocol/type/callId.
        try {
            if (msg.baseContentMsgModel != null) {
                text = pickValidSignalText(msg.baseContentMsgModel.getDisplayContent());
                if (!TextUtils.isEmpty(text)) return text;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String pickValidSignalText(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String text = raw.trim();

        if (text.startsWith(RtcConstants.SIGNAL_PREFIX)) {
            return isValidSignalText(text) ? text : "";
        }

        // In some local DB/cache formats the text message content is JSON wrapped.
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                JSONObject object = new JSONObject(text);
                String content = object.optString("content", object.optString("text", ""));
                if (!TextUtils.isEmpty(content)) {
                    content = content.trim();
                    if (content.startsWith(RtcConstants.SIGNAL_PREFIX) && isValidSignalText(content)) {
                        return content;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static boolean isValidSignalText(String text) {
        try {
            RtcSignal s = RtcSignal.fromTransportText(text);
            return s != null
                    && RtcConstants.PROTOCOL.equals(s.protocol)
                    && !TextUtils.isEmpty(s.callId)
                    && isAllowedType(s.type);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isAllowedType(String type) {
        return RtcSignal.INVITE.equals(type)
                || RtcSignal.ACCEPT.equals(type)
                || RtcSignal.RINGING.equals(type)
                || RtcSignal.REJECT.equals(type)
                || RtcSignal.BUSY.equals(type)
                || RtcSignal.CANCEL.equals(type)
                || RtcSignal.END.equals(type)
                || RtcSignal.TIMEOUT.equals(type)
                || RtcSignal.OFFER.equals(type)
                || RtcSignal.ANSWER.equals(type)
                || RtcSignal.ICE.equals(type);
    }

    public boolean tryHandleIncomingText(String text) {
        if (!isValidSignalText(text)) return false;
        try {
            RtcSignal s = RtcSignal.fromTransportText(text);
            if (s == null) return false;
            RtcDebugLogger.i("RtcSignalManager", "valid " + RtcDebugLogger.signal(s)
                    + " my=" + RtcDebugLogger.shortUid(myUid));

            // Hide every valid RTC packet from chat, but dispatch only packets explicitly
            // addressed to this login uid. Empty to_uid, wrong target or self-loop packets
            // must not enter RtcCallManager, otherwise they create phantom calls and BUSY locks.
            if (TextUtils.isEmpty(myUid)) {
                RtcDebugLogger.w("RtcSignalManager", "drop-dispatch because myUid empty " + RtcDebugLogger.signal(s));
                return true;
            }
            if (TextUtils.isEmpty(s.toUid) || !TextUtils.equals(s.toUid, myUid)) {
                RtcDebugLogger.w("RtcSignalManager", "hide-only target mismatch " + RtcDebugLogger.signal(s)
                        + " my=" + RtcDebugLogger.shortUid(myUid));
                return true;
            }
            if (!TextUtils.isEmpty(s.fromUid) && TextUtils.equals(s.fromUid, myUid)) {
                RtcDebugLogger.w("RtcSignalManager", "hide-only self packet " + RtcDebugLogger.signal(s));
                return true;
            }
            if (RtcSignal.INVITE.equals(s.type) && s.isExpired()) {
                RtcDebugLogger.w("RtcSignalManager", "drop expired invite " + RtcDebugLogger.signal(s));
                return true;
            }
            if (rememberSignal(s)) {
                RtcDebugLogger.w("RtcSignalManager", "drop duplicate " + RtcDebugLogger.signal(s));
                return true;
            }

            if (delegate != null) {
                RtcDebugLogger.i("RtcSignalManager", "dispatch " + RtcDebugLogger.signal(s));
                delegate.onRtcSignal(s);
            } else {
                RtcDebugLogger.w("RtcSignalManager", "delegate null for " + RtcDebugLogger.signal(s));
            }
            return true;
        } catch (Exception e) {
            RtcDebugLogger.e("RtcSignalManager", "tryHandleIncomingText failed", e);
            return false;
        }
    }

    private synchronized boolean rememberSignal(RtcSignal s) {
        cleanupSeenSignals();
        String key = signalKey(s);
        if (TextUtils.isEmpty(key)) return false;
        if (seenSignals.containsKey(key)) return true;
        seenSignals.put(key, System.currentTimeMillis());
        return false;
    }

    private synchronized void cleanupSeenSignals() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = seenSignals.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > 5 * 60 * 1000L) iterator.remove();
        }
    }

    private String signalKey(RtcSignal s) {
        String body = !TextUtils.isEmpty(s.candidate) ? s.candidate : s.sdp;
        int bodyHash = TextUtils.isEmpty(body) ? 0 : body.hashCode();
        return String.valueOf(s.type) + '|'
                + String.valueOf(s.callId) + '|'
                + String.valueOf(s.fromUid) + '|'
                + String.valueOf(s.toUid) + '|'
                + s.timestamp + '|'
                + bodyHash;
    }

    public void send(RtcSignal signal) throws Exception {
        if (transport == null) throw new IllegalStateException("RtcSignalTransport not configured");
        if (signal == null) throw new IllegalArgumentException("RtcSignal is null");
        if (TextUtils.isEmpty(myUid)) throw new IllegalStateException("Rtc uid is empty");
        if (TextUtils.isEmpty(signal.fromUid)) signal.fromUid = myUid;
        if (TextUtils.isEmpty(signal.toUid)) throw new IllegalArgumentException("Rtc peer uid is empty");
        if (TextUtils.equals(signal.toUid, myUid)) throw new IllegalArgumentException("Rtc self-call blocked");
        if (TextUtils.equals(signal.fromUid, signal.toUid)) throw new IllegalArgumentException("Rtc from/to uid are same");
        RtcDebugLogger.i("RtcSignalManager", "send " + RtcDebugLogger.signal(signal));
        transport.sendSignal(signal.toUid, signal.toTransportText());
    }

    public void sendInvite(String callId, String toUid, String ignoredPeerName, String ignoredPeerAvatar, int callType) throws Exception {
        RtcSignal s = RtcSignal.base(RtcSignal.INVITE, callId, myUid, toUid);
        s.mode = RtcConstants.modeOf(callType);
        // INVITE must carry caller profile, not the callee profile shown in caller UI.
        s.fromName = getSelfName();
        s.fromAvatar = getSelfAvatar();
        s.expiresAt = System.currentTimeMillis() + RtcConfigManager.getInviteTimeoutMs() + 5_000L;
        send(s);
    }

    private String getSelfName() {
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(myUid, WKChannelType.PERSONAL);
            if (channel != null) {
                String name = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
                if (!TextUtils.isEmpty(name)) return name;
            }
        } catch (Exception ignored) {
        }
        return TextUtils.isEmpty(myUid) ? "好友" : myUid;
    }

    private String getSelfAvatar() {
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(myUid, WKChannelType.PERSONAL);
            if (channel != null && !TextUtils.isEmpty(channel.avatar)) return channel.avatar;
        } catch (Exception ignored) {
        }
        return "";
    }

    public void sendSimple(String type, String callId, String toUid) throws Exception {
        send(RtcSignal.base(type, callId, myUid, toUid));
    }

    public void sendDescription(String callId, String toUid, SessionDescription d) throws Exception {
        String type = d.type == SessionDescription.Type.OFFER ? RtcSignal.OFFER : RtcSignal.ANSWER;
        RtcSignal s = RtcSignal.base(type, callId, myUid, toUid);
        s.sdpType = d.type.canonicalForm();
        s.sdp = d.description;
        send(s);
    }

    public void sendIce(String callId, String toUid, IceCandidate c) throws Exception {
        if (c == null) return;
        RtcSignal s = RtcSignal.base(RtcSignal.ICE, callId, myUid, toUid);
        s.candidate = c.sdp;
        s.sdpMid = c.sdpMid;
        s.sdpMLineIndex = c.sdpMLineIndex;
        send(s);
    }
}
