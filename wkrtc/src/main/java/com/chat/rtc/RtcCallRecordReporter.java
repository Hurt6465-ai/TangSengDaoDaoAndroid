package com.chat.rtc;

import android.text.TextUtils;

import com.chat.base.endpoint.EndpointManager;
import com.chat.rtc.model.RtcCallRecordContent;

import java.util.HashMap;
import java.util.Map;

/**
 * Emits a lightweight call-result event for the host app or backend bridge.
 *
 * The plugin still does not force a persisted visible chat record because the final
 * message content type should be shared with the backend. This event is intentionally
 * stable and presentation-ready so wkuikit/server can directly turn it into records like:
 * video call 03:12, no answer, rejected, cancelled, busy, or connect failed.
 */
public final class RtcCallRecordReporter {
    public static final String ENDPOINT = "rtc_call_record_event";
    public static final int SCHEMA_VERSION = 2;

    private RtcCallRecordReporter() {}

    public static void report(String callId, String peerUid, String peerName, int callType,
                              boolean incoming, String reason, long connectedAt) {
        long duration = connectedAt > 0 ? Math.max(0L, (System.currentTimeMillis() - connectedAt) / 1000L) : 0L;
        String normalizedReason = TextUtils.isEmpty(reason) ? "ended" : reason;
        Map<String, Object> event = new HashMap<>();
        event.put("schema_version", SCHEMA_VERSION);
        event.put("call_id", nullToEmpty(callId));
        event.put("peer_uid", nullToEmpty(peerUid));
        event.put("peer_name", nullToEmpty(peerName));
        event.put("call_type", callType);
        event.put("mode", RtcConstants.modeOf(callType));
        event.put("direction", incoming ? "incoming" : "outgoing");
        event.put("incoming", incoming);
        event.put("reason", normalizedReason);
        event.put("result", resultOf(normalizedReason));
        event.put("duration_seconds", duration);
        event.put("display_text", RtcCallRecordContent.buildDisplayText(callType, normalizedReason, incoming, duration));
        event.put("timestamp", System.currentTimeMillis());
        try {
            EndpointManager.getInstance().invoke(ENDPOINT, event);
        } catch (Exception ignored) {
        }
    }

    private static String resultOf(String reason) {
        if ("ended".equals(reason) || "remote_ended".equals(reason)) return "completed";
        if ("missed".equals(reason) || "no_answer".equals(reason)) return "no_answer";
        if ("rejected".equals(reason)) return "rejected";
        if ("busy".equals(reason)) return "busy";
        if ("connect_failed".equals(reason)) return "connect_failed";
        if ("cancelled".equals(reason) || "remote_cancelled".equals(reason)) return "cancelled";
        return reason;
    }

    private static String buildDisplayText(int callType, String reason, long duration) {
        String prefix = RtcConstants.isVideo(callType) ? "视频通话" : "语音通话";
        if ("ended".equals(reason) || "remote_ended".equals(reason)) {
            return prefix + " " + formatDuration(duration);
        }
        if ("missed".equals(reason) || "no_answer".equals(reason)) return prefix + " 对方无应答";
        if ("rejected".equals(reason)) return prefix + " 已拒绝";
        if ("busy".equals(reason)) return prefix + " 对方忙线";
        if ("connect_failed".equals(reason)) return prefix + " 连接失败";
        if ("cancelled".equals(reason) || "remote_cancelled".equals(reason)) return prefix + " 已取消";
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
