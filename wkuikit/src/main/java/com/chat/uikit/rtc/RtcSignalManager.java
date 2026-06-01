package com.chat.uikit.rtc;

import android.text.TextUtils;
import android.util.Log;

import com.chat.uikit.rtc.model.RtcSignal;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public class RtcSignalManager {
    private static final String TAG = "RtcSignalManager";
    private static final RtcSignalManager INSTANCE = new RtcSignalManager();
    private String myUid = "";
    private RtcSignalTransport transport;
    private RtcSignalDelegate delegate;

    public static RtcSignalManager get() { return INSTANCE; }

    public void configure(String myUid, RtcSignalTransport transport, RtcSignalDelegate delegate) {
        this.myUid = myUid == null ? "" : myUid;
        this.transport = transport;
        this.delegate = delegate;
    }

    public String myUid() { return myUid; }

    public boolean tryHandleIncomingMsg(WKMsg msg) {
        return tryHandleIncomingText(extractSignalText(msg));
    }

    public static boolean isSignalMsg(WKMsg msg) {
        String text = extractSignalText(msg);
        return !TextUtils.isEmpty(text) && text.startsWith(RtcConstants.SIGNAL_PREFIX);
    }

    public static String extractSignalText(WKMsg msg) {
        if (msg == null) return "";
        try {
            if (msg.baseContentMsgModel != null) {
                String text = safePickSignalText(msg.baseContentMsgModel.getDisplayContent());
                if (!TextUtils.isEmpty(text)) return text;

                JSONObject jsonObject = msg.baseContentMsgModel.encodeMsg();
                if (jsonObject != null) {
                    text = safePickSignalText(jsonObject.optString("content", jsonObject.optString("text", "")));
                    if (!TextUtils.isEmpty(text)) return text;
                }
            }
            return safePickSignalText(msg.content);
        } catch (Exception ignored) {
            return safePickSignalText(msg.content);
        }
    }

    private static String safePickSignalText(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String text = raw.trim();
        if (text.startsWith(RtcConstants.SIGNAL_PREFIX)) return text;
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                JSONObject object = new JSONObject(text);
                String content = object.optString("content", object.optString("text", ""));
                if (!TextUtils.isEmpty(content) && content.startsWith(RtcConstants.SIGNAL_PREFIX)) {
                    return content;
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public boolean tryHandleIncomingText(String text) {
        try {
            RtcSignal s = RtcSignal.fromTransportText(text);
            if (s == null) return false;
            if (!TextUtils.isEmpty(s.toUid) && !TextUtils.equals(s.toUid, myUid)) return true;
            if (!TextUtils.isEmpty(s.fromUid) && TextUtils.equals(s.fromUid, myUid)) return true;
            if (RtcSignal.INVITE.equals(s.type) && s.isExpired()) return true;
            if (delegate != null) delegate.onRtcSignal(s);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "bad rtc signal", e);
            return false;
        }
    }

    public void send(RtcSignal signal) throws Exception {
        if (transport == null) throw new IllegalStateException("RtcSignalTransport not configured");
        if (TextUtils.isEmpty(signal.fromUid)) signal.fromUid = myUid;
        transport.sendSignal(signal.toUid, signal.toTransportText());
    }

    public void sendInvite(String callId, String toUid, String name, String avatar, int callType) throws Exception {
        RtcSignal s = RtcSignal.base(RtcSignal.INVITE, callId, myUid, toUid);
        s.mode = RtcConstants.modeOf(callType);
        s.fromName = name;
        s.fromAvatar = avatar;
        s.expiresAt = System.currentTimeMillis() + 45_000;
        send(s);
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
        RtcSignal s = RtcSignal.base(RtcSignal.ICE, callId, myUid, toUid);
        s.candidate = c.sdp;
        s.sdpMid = c.sdpMid;
        s.sdpMLineIndex = c.sdpMLineIndex;
        send(s);
    }
}
