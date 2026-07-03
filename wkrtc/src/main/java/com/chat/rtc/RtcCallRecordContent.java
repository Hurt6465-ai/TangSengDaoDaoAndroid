package com.chat.rtc;

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

public class RtcCallRecordContent extends WKMessageContent {
    public String callId;
    public String peerName;
    public String reason;
    public boolean incoming;
    public int callType;
    public long durationSeconds;
    public long timestamp;
    public String displayText;

    public RtcCallRecordContent() {
        type = RtcConstants.CONTENT_TYPE_CALL_RECORD;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        callId = jsonObject.optString("call_id");
        peerName = jsonObject.optString("peer_name");
        reason = jsonObject.optString("reason");
        incoming = jsonObject.optBoolean("incoming", false);
        callType = jsonObject.optInt("call_type", RtcConstants.AUDIO);
        durationSeconds = jsonObject.optLong("duration_seconds", 0L);
        timestamp = jsonObject.optLong("timestamp", 0L);
        displayText = jsonObject.optString("display_text");
        if (TextUtils.isEmpty(displayText)) {
            displayText = RtcConstants.isVideo(callType) ? "视频通话" : "语音通话";
        }
        return this;
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("call_id", callId);
            jsonObject.put("peer_name", peerName);
            jsonObject.put("reason", reason);
            jsonObject.put("incoming", incoming);
            jsonObject.put("call_type", callType);
            jsonObject.put("duration_seconds", durationSeconds);
            jsonObject.put("timestamp", timestamp);
            jsonObject.put("display_text", displayText);
        } catch (JSONException ignored) {
        }
        return jsonObject;
    }

    @Override
    public String getDisplayContent() {
        return TextUtils.isEmpty(displayText)
                ? (RtcConstants.isVideo(callType) ? "[视频通话]" : "[语音通话]")
                : displayText;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    protected RtcCallRecordContent(Parcel in) {
        super(in);
        callId = in.readString();
        peerName = in.readString();
        reason = in.readString();
        incoming = in.readByte() != 0;
        callType = in.readInt();
        durationSeconds = in.readLong();
        timestamp = in.readLong();
        displayText = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(callId);
        dest.writeString(peerName);
        dest.writeString(reason);
        dest.writeByte((byte) (incoming ? 1 : 0));
        dest.writeInt(callType);
        dest.writeLong(durationSeconds);
        dest.writeLong(timestamp);
        dest.writeString(displayText);
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
}
