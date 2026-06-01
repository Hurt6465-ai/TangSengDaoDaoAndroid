package com.chat.uikit.rtc;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * ICE server config for WebRTC. STUN is enough for lab testing, TURN is needed
 * for reliable production calls across restrictive NAT/firewall networks.
 */
public final class RtcIceServers {
    private static final List<PeerConnection.IceServer> EXTRA = new ArrayList<>();

    private RtcIceServers() {}

    public static synchronized void setTurnServer(String url, String username, String credential) {
        EXTRA.clear();
        if (url == null || url.trim().isEmpty()) return;
        EXTRA.add(PeerConnection.IceServer.builder(url)
                .setUsername(username == null ? "" : username)
                .setPassword(credential == null ? "" : credential)
                .createIceServer());
    }

    public static synchronized void clearTurnServer() {
        EXTRA.clear();
    }

    public static synchronized List<PeerConnection.IceServer> getDefault() {
        List<PeerConnection.IceServer> list = new ArrayList<>();
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        list.addAll(EXTRA);
        return list;
    }
}
