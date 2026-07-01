package com.chat.rtc;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * ICE server config for WebRTC.
 *
 * Bundled Google STUN is only a development fallback. Production should call
 * RtcConfigManager.refreshAsync(), backed by /v1/rtc/config, and provide TURN.
 */
public final class RtcIceServers {
    private static final String TAG = "RtcIceServers";
    private static final List<PeerConnection.IceServer> CONFIGURED = new ArrayList<>();

    private RtcIceServers() {}

    public static synchronized void setTurnServer(String url, String username, String credential) {
        CONFIGURED.clear();
        if (TextUtils.isEmpty(url)) return;
        CONFIGURED.add(PeerConnection.IceServer.builder(url.trim())
                .setUsername(username == null ? "" : username)
                .setPassword(credential == null ? "" : credential)
                .createIceServer());
    }

    public static synchronized void setConfiguredServers(JSONArray array) {
        List<PeerConnection.IceServer> parsed = parseServers(array);
        if (parsed.isEmpty()) return;
        CONFIGURED.clear();
        CONFIGURED.addAll(parsed);
    }

    public static synchronized void clearTurnServer() {
        CONFIGURED.clear();
    }

    public static synchronized List<PeerConnection.IceServer> getDefault() {
        if (!CONFIGURED.isEmpty()) return new ArrayList<>(CONFIGURED);

        List<PeerConnection.IceServer> list = new ArrayList<>();
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        return list;
    }

    private static List<PeerConnection.IceServer> parseServers(JSONArray array) {
        List<PeerConnection.IceServer> list = new ArrayList<>();
        if (array == null) return list;

        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject server = array.optJSONObject(i);
                if (server == null) continue;

                List<String> urls = parseUrls(server.opt("urls"));
                if (urls.isEmpty()) continue;

                PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(urls);
                String username = server.optString("username", "");
                String credential = server.optString("credential", server.optString("password", ""));
                if (!TextUtils.isEmpty(username)) builder.setUsername(username);
                if (!TextUtils.isEmpty(credential)) builder.setPassword(credential);
                list.add(builder.createIceServer());
            } catch (Exception e) {
                Log.w(TAG, "skip invalid ice server config", e);
            }
        }
        return list;
    }

    private static List<String> parseUrls(Object raw) {
        List<String> urls = new ArrayList<>();
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                String url = array.optString(i, "").trim();
                if (!TextUtils.isEmpty(url)) urls.add(url);
            }
        } else if (raw instanceof String) {
            String url = ((String) raw).trim();
            if (!TextUtils.isEmpty(url)) urls.add(url);
        }
        return urls;
    }
}
