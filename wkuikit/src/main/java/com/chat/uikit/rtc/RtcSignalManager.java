package com.chat.uikit.rtc;

import android.text.TextUtils;
import android.util.Log;

import com.chat.base.msgitem.WKContentType;
import com.chat.uikit.rtc.model.RtcSignal;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RtcSignalManager {
    private static final String TAG = "RtcSignalManager";
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
    }

    public String myUid() { return myUid; }

    public boolean tryHandleIncomingMsg(WKMsg msg) {
        String text = extractSignalText(msg);
        boolean handled = tryHandleIncomingText(text);
        if (handled && msg != null) {
            Log.i(TAG, "handled rtc signal msg type=" + msg.type
                    + ", from=" + msg.fromUID
                    + ", channel=" + msg.channelID
                    + ", clientMsgNO=" + msg.clientMsgNO);
        }
        return handled;
    }

    public static boolean isSignalMsg(WKMsg msg) {
        // RTC signals are sent as WK_TEXT. Do not inspect image/video/voice payloads,
        // otherwise a malformed media payload could be filtered by mistake.
        if (msg == null || msg.type != WKContentType.WK_TEXT) return false;
        String text = extractSignalText(msg);
        if (TextUtils.isEmpty(text)) return false;
        try {
            return RtcSignal.fromTransportText(text) != null;
        } catch (Exception e) {
            Log.w(TAG, "ignore malformed rtc-looking text, clientMsgNO=" + msg.clientMsgNO, e);
            return false;
        }
    }

    public static String extractSignalText(WKMsg msg) {
        if (msg == null) return "";
        if (msg.type != WKContentType.WK_TEXT) return "";
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
                if (!TextUtils.isEmpty(content) && content.trim().startsWith(RtcConstants.SIGNAL_PREFIX)) {
                    return content.trim();
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
            if (!TextUtils.isEmpty(s.toUid) && !TextUtils.equals(s.toUid, myUid)) {
                Log.i(TAG, "skip rtc signal for other uid, type=" + s.type + ", callId=" + s.callId + ", to=" + s.toUid + ", me=" + myUid);
                return true;
            }
            if (!TextUtils.isEmpty(s.fromUid) && TextUtils.equals(s.fromUid, myUid)) {
                Log.i(TAG, "skip self rtc signal, type=" + s.type + ", callId=" + s.callId);
                return true;
            }
            if (RtcSignal.INVITE.equals(s.type) && s.isExpired()) {
                Log.i(TAG, "skip expired invite, callId=" + s.callId);
                return true;
            }
            if (rememberSignal(s)) return true;
            Log.i(TAG, "dispatch rtc signal type=" + s.type + ", callId=" + s.callId + ", from=" + s.fromUid + ", to=" + s.toUid);
            if (delegate != null) delegate.onRtcSignal(s);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "bad rtc signal", e);
            return false;
        }
    }

    private synchronized boolean rememberSignal(RtcSignal s) {
        cleanupSeenSignals();
        String key = signalKey(s);
        if (TextUtils.isEmpty(key)) return false;
        if (seenSignals.containsKey(key)) {
            Log.w(TAG, "ignore duplicated rtc signal: " + s.type + ", callId=" + s.callId);
            return true;
        }
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
