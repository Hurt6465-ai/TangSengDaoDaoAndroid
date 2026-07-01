package com.chat.rtc.model;

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.chat.base.msgitem.WKContentType;
import com.chat.rtc.RtcConstants;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Dedicated RTC signaling message content.
 *
 * It intentionally does not render as a normal text message. The chat layer only uses it
 * as a transport packet and hides it from message lists/unread counters.
 */
public class RtcSignalContent extends WKMessageContent {
    public String payload;

    public RtcSignalContent() {
        type = WKContentType.WK_RTC_SIGNAL;
    }

    public RtcSignalContent(String payload) {
        this();
        this.payload = payload == null ? "" : payload;
    }

    protected RtcSignalContent(Parcel in) {
        super(in);
        payload = in.readString();
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject object = new JSONObject();
        try {
            object.put("payload", payload == null ? "" : payload);
            object.put("content", payload == null ? "" : payload);
            object.put("protocol", RtcConstants.PROTOCOL);
        } catch (JSONException ignored) {
        }
        return object;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject == null) return this;
        payload = jsonObject.optString("payload");
        if (TextUtils.isEmpty(payload)) payload = jsonObject.optString("content");
        if (TextUtils.isEmpty(payload)) payload = jsonObject.optString("text");
        return this;
    }

    @Override
    public String getDisplayContent() {
        return "";
    }

    @Override
    public String getSearchableWord() {
        return "";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(payload);
    }

    public static final Creator<RtcSignalContent> CREATOR = new Creator<RtcSignalContent>() {
        @Override
        public RtcSignalContent createFromParcel(Parcel in) {
            return new RtcSignalContent(in);
        }

        @Override
        public RtcSignalContent[] newArray(int size) {
            return new RtcSignalContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
