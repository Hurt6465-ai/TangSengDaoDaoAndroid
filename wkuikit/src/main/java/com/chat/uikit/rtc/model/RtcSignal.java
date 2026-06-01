package com.chat.uikit.rtc.model;

import android.text.TextUtils;

import com.chat.uikit.rtc.RtcConstants;

import org.json.JSONException;
import org.json.JSONObject;

public class RtcSignal {
    public static final String INVITE = "invite";
    public static final String ACCEPT = "accept";
    public static final String REJECT = "reject";
    public static final String BUSY = "busy";
    public static final String CANCEL = "cancel";
    public static final String END = "end";
    public static final String OFFER = "offer";
    public static final String ANSWER = "answer";
    public static final String ICE = "ice_candidate";

    public String protocol = RtcConstants.PROTOCOL;
    public String type;
    public String callId;
    public String fromUid;
    public String toUid;
    public String fromName;
    public String fromAvatar;
    public String mode = "audio";
    public long timestamp = System.currentTimeMillis();
    public long expiresAt;

    public String sdpType;
    public String sdp;
    public String candidate;
    public String sdpMid;
    public int sdpMLineIndex = -1;

    public static RtcSignal base(String type, String callId, String fromUid, String toUid) {
        RtcSignal s = new RtcSignal();
        s.type = type;
        s.callId = callId;
        s.fromUid = fromUid;
        s.toUid = toUid;
        s.timestamp = System.currentTimeMillis();
        return s;
    }

    public boolean isExpired() {
        long now = System.currentTimeMillis();
        if (expiresAt > 0 && now > expiresAt) return true;
        return timestamp > 0 && now - timestamp > 45_000;
    }

    public String toTransportText() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("protocol", protocol);
        o.put("type", type);
        o.put("call_id", callId);
        o.put("from_uid", fromUid);
        o.put("to_uid", toUid);
        o.put("mode", mode);
        o.put("timestamp", timestamp);
        if (expiresAt > 0) o.put("expires_at", expiresAt);
        if (!TextUtils.isEmpty(fromName)) o.put("from_name", fromName);
        if (!TextUtils.isEmpty(fromAvatar)) o.put("from_avatar", fromAvatar);
        if (!TextUtils.isEmpty(sdpType)) o.put("sdp_type", sdpType);
        if (!TextUtils.isEmpty(sdp)) o.put("sdp", sdp);
        if (!TextUtils.isEmpty(candidate)) o.put("candidate", candidate);
        if (!TextUtils.isEmpty(sdpMid)) o.put("sdp_mid", sdpMid);
        if (sdpMLineIndex >= 0) o.put("sdp_mline_index", sdpMLineIndex);
        return RtcConstants.SIGNAL_PREFIX + o.toString();
    }

    public static RtcSignal fromTransportText(String text) throws JSONException {
        if (TextUtils.isEmpty(text) || !text.startsWith(RtcConstants.SIGNAL_PREFIX)) return null;
        JSONObject o = new JSONObject(text.substring(RtcConstants.SIGNAL_PREFIX.length()));
        if (!RtcConstants.PROTOCOL.equals(o.optString("protocol"))) return null;
        RtcSignal s = new RtcSignal();
        s.type = o.optString("type");
        s.callId = o.optString("call_id");
        s.fromUid = o.optString("from_uid");
        s.toUid = o.optString("to_uid");
        s.fromName = o.optString("from_name");
        s.fromAvatar = o.optString("from_avatar");
        s.mode = o.optString("mode", "audio");
        s.timestamp = o.optLong("timestamp", 0);
        s.expiresAt = o.optLong("expires_at", 0);
        s.sdpType = o.optString("sdp_type");
        s.sdp = o.optString("sdp");
        s.candidate = o.optString("candidate");
        s.sdpMid = o.optString("sdp_mid");
        s.sdpMLineIndex = o.optInt("sdp_mline_index", -1);
        if (TextUtils.isEmpty(s.type) || TextUtils.isEmpty(s.callId)) return null;
        return s;
    }
}
