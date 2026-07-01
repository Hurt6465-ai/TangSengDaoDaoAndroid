package com.chat.rtc;

import android.text.TextUtils;
import android.util.Log;

import com.chat.base.config.WKApiConfig;
import com.chat.base.net.OkHttpUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Runtime RTC configuration.
 *
 * The plugin can run with bundled STUN for development, but production calls need the
 * backend to provide TURN/STUN and timeout values through /v1/rtc/config. This loader is
 * intentionally fail-safe: if the endpoint is absent or returns invalid data, existing
 * defaults remain in use and calls still attempt to connect.
 */
public final class RtcConfigManager {
    private static final String TAG = "RtcConfigManager";
    private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L;

    private static volatile boolean loading;
    private static volatile long lastLoadedAt;
    private static volatile int callTimeoutMs = RtcConstants.CALL_TIMEOUT_MS;
    private static volatile int inviteTimeoutMs = RtcConstants.CALL_TIMEOUT_MS;
    private static volatile int connectTimeoutMs = RtcConstants.CONNECT_TIMEOUT_MS;

    private RtcConfigManager() {}

    public static int getCallTimeoutMs() {
        return Math.max(10_000, callTimeoutMs);
    }

    /** Time to wait for the callee to accept the invite. */
    public static int getInviteTimeoutMs() {
        return Math.max(10_000, inviteTimeoutMs);
    }

    /** Time to wait for ICE to connect after the callee accepts. */
    public static int getConnectTimeoutMs() {
        return Math.max(10_000, connectTimeoutMs);
    }

    public static void refreshAsync() {
        String baseUrl = WKApiConfig.baseUrl;
        if (TextUtils.isEmpty(baseUrl)) return;

        long now = System.currentTimeMillis();
        if (loading || (lastLoadedAt > 0 && now - lastLoadedAt < REFRESH_INTERVAL_MS)) return;

        loading = true;
        new Thread(() -> {
            try {
                String url = baseUrl.endsWith("/") ? baseUrl + "rtc/config" : baseUrl + "/rtc/config";
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();
                try (Response response = OkHttpUtils.getInstance().getOkHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        Log.w(TAG, "rtc config unavailable: code=" + response.code());
                        return;
                    }
                    String body = response.body().string();
                    if (TextUtils.isEmpty(body)) return;
                    apply(new JSONObject(body));
                    lastLoadedAt = System.currentTimeMillis();
                }
            } catch (Exception e) {
                Log.w(TAG, "load rtc config failed, keep defaults", e);
            } finally {
                loading = false;
            }
        }, "wkrtc-config-loader").start();
    }

    private static void apply(JSONObject root) {
        if (root == null) return;
        JSONArray ice = optArray(root, "ice_servers", "iceServers");
        if (ice != null && ice.length() > 0) {
            RtcIceServers.setConfiguredServers(ice);
        }

        int timeout = root.optInt("call_timeout", root.optInt("callTimeout", 0));
        if (timeout > 0) {
            // Backend convention: seconds. Accept milliseconds too if a larger value is returned.
            callTimeoutMs = normalizeTimeout(timeout);
            inviteTimeoutMs = callTimeoutMs;
        }

        int inviteTimeout = root.optInt("invite_timeout", root.optInt("inviteTimeout", 0));
        if (inviteTimeout > 0) {
            inviteTimeoutMs = normalizeTimeout(inviteTimeout);
        }

        int connectTimeout = root.optInt("connect_timeout", root.optInt("connectTimeout", 0));
        if (connectTimeout > 0) {
            connectTimeoutMs = normalizeTimeout(connectTimeout);
        }
    }

    private static int normalizeTimeout(int value) {
        return value < 1000 ? value * 1000 : value;
    }

    private static JSONArray optArray(JSONObject object, String first, String second) {
        JSONArray array = object.optJSONArray(first);
        if (array != null) return array;
        return object.optJSONArray(second);
    }
}
