package com.chat.rtc.model;

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.chat.base.msgitem.WKContentType;
import com.chat.rtc.RtcConstants;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/** Visible chat-history item for a completed/missed RTC call. */
public class RtcCallRecordContent extends WKMessageContent {
    public String callId;
    public String peerUid;
    public String peerName;
    public String mode = "audio";
    public int callType = RtcConstants.AUDIO;
    public String direction = "outgoing";
    public String reason = "ended";
    public String result = "completed";
    public long durationSeconds;
    public String displayText;
    public long timestamp;

    public RtcCallRecordContent() {
        type = WKContentType.WK_RTC_CALL_RECORD;
    }

    public static RtcCallRecordContent create(String callId, String peerUid, String peerName, int callType,
                                              boolean incoming, String reason, long durationSeconds) {
        RtcCallRecordContent c = new RtcCallRecordContent();
        c.callId = nullToEmpty(callId);
        c.peerUid = nullToEmpty(peerUid);
        c.peerName = nullToEmpty(peerName);
        c.callType = callType;
        c.mode = RtcConstants.modeOf(callType);
        c.direction = incoming ? "incoming" : "outgoing";
        c.reason = TextUtils.isEmpty(reason) ? "ended" : reason;
        c.result = resultOf(c.reason);
        c.durationSeconds = Math.max(0L, durationSeconds);
        c.displayText = buildDisplayText(callType, c.reason, incoming, c.durationSeconds);
        c.timestamp = System.currentTimeMillis();
        return c;
    }

    protected RtcCallRecordContent(Parcel in) {
        super(in);
        callId = in.readString();
        peerUid = in.readString();
        peerName = in.readString();
        mode = in.readString();
        callType = in.readInt();
        direction = in.readString();
        reason = in.readString();
        result = in.readString();
        durationSeconds = in.readLong();
        displayText = in.readString();
        timestamp = in.readLong();
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject object = new JSONObject();
        try {
            object.put("call_id", nullToEmpty(callId));
            object.put("peer_uid", nullToEmpty(peerUid));
            object.put("peer_name", nullToEmpty(peerName));
            object.put("mode", TextUtils.isEmpty(mode) ? RtcConstants.modeOf(callType) : mode);
            object.put("call_type", callType);
            object.put("direction", nullToEmpty(direction));
            object.put("reason", nullToEmpty(reason));
            object.put("result", nullToEmpty(result));
            object.put("duration_seconds", durationSeconds);
            object.put("display_text", getDisplayContent());
            object.put("timestamp", timestamp <= 0 ? System.currentTimeMillis() : timestamp);
        } catch (JSONException ignored) {
        }
        return object;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject == null) return this;
        callId = jsonObject.optString("call_id");
        peerUid = jsonObject.optString("peer_uid");
        peerName = jsonObject.optString("peer_name");
        mode = jsonObject.optString("mode", "audio");
        callType = jsonObject.optInt("call_type", RtcConstants.typeOf(mode));
        direction = jsonObject.optString("direction", "outgoing");
        reason = jsonObject.optString("reason", "ended");
        result = jsonObject.optString("result", resultOf(reason));
        durationSeconds = jsonObject.optLong("duration_seconds", 0L);
        displayText = jsonObject.optString("display_text");
        timestamp = jsonObject.optLong("timestamp", 0L);
        if (TextUtils.isEmpty(displayText)) {
            displayText = buildDisplayText(callType, reason, "incoming".equals(direction), durationSeconds);
        }
        return this;
    }

    @Override
    public String getDisplayContent() {
        if (!TextUtils.isEmpty(displayText)) return displayText;
        return buildDisplayText(callType, reason, "incoming".equals(direction), durationSeconds);
    }

    @Override
    public String getSearchableWord() {
        return getDisplayContent();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(callId);
        dest.writeString(peerUid);
        dest.writeString(peerName);
        dest.writeString(mode);
        dest.writeInt(callType);
        dest.writeString(direction);
        dest.writeString(reason);
        dest.writeString(result);
        dest.writeLong(durationSeconds);
        dest.writeString(displayText);
        dest.writeLong(timestamp);
    }

    public static final Creator<RtcCallRecordContent> CREATOR = new Creator<RtcCallRecordContent>() {
        @Override
        public RtcCallRecordContent createFromParcel(Parcel in) {
            return new RtcCallRecordContent(in);
        }

        @Override
        public RtcCallRecordContent[] newArray(int size) {
            return new RtcCallRecordContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    public static String resultOf(String reason) {
        if ("ended".equals(reason) || "remote_ended".equals(reason)) return "completed";
        if ("missed".equals(reason) || "no_answer".equals(reason)) return "no_answer";
        if ("rejected".equals(reason)) return "rejected";
        if ("busy".equals(reason)) return "busy";
        if ("connect_failed".equals(reason)) return "connect_failed";
        if ("cancelled".equals(reason) || "remote_cancelled".equals(reason)) return "cancelled";
        if ("permission_denied".equals(reason)) return "permission_denied";
        return TextUtils.isEmpty(reason) ? "ended" : reason;
    }

    public static String buildDisplayText(int callType, String reason, boolean incoming, long duration) {
        String prefix = RtcConstants.isVideo(callType) ? "视频通话" : "语音通话";
        if ("ended".equals(reason) || "remote_ended".equals(reason)) return prefix + " " + formatDuration(duration);
        if ("missed".equals(reason)) return incoming ? prefix + " 未接来电" : prefix + " 对方无应答";
        if ("no_answer".equals(reason)) return prefix + " 对方无应答";
        if ("rejected".equals(reason)) return prefix + " 已拒绝";
        if ("busy".equals(reason)) return prefix + " 对方忙线";
        if ("connect_failed".equals(reason)) return prefix + " 连接失败";
        if ("cancelled".equals(reason)) return prefix + " 已取消";
        if ("remote_cancelled".equals(reason)) return incoming ? prefix + " 未接来电" : prefix + " 已取消";
        if ("permission_denied".equals(reason)) return prefix + " 权限不足";
        return prefix + " 已结束";
    }

    private static String formatDuration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long minutes = seconds / 60L;
        long remain = seconds % 60L;
        return String.format("%02d:%02d", minutes, remain);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
