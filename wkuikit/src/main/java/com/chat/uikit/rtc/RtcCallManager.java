package com.chat.uikit.rtc;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.chat.uikit.rtc.model.RtcSignal;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RtcCallManager implements RtcSignalDelegate {
    public interface ActiveCallListener {
        String getActiveCallId();
        void onSignalForActiveCall(RtcSignal signal);
    }

    private static final RtcCallManager INSTANCE = new RtcCallManager();
    private Context appContext;
    private WeakReference<ActiveCallListener> activeListener = new WeakReference<>(null);
    private final Map<String, List<RtcSignal>> pending = new HashMap<>();
    private final Map<String, Long> closed = new HashMap<>();
    private final Map<String, Long> incomingSeen = new HashMap<>();

    public static RtcCallManager get() { return INSTANCE; }

    public synchronized void configure(Context context, String myUid, RtcSignalTransport transport) {
        if (context != null) appContext = context.getApplicationContext();
        RtcSignalManager.get().configure(myUid, transport, this);
    }

    public void startOutgoing(Context context, String peerUid, String peerName, String peerAvatar, int callType) {
        if (TextUtils.isEmpty(peerUid)) return;
        Intent i = new Intent(context, RtcCallActivity.class);
        i.putExtra(RtcConstants.EXTRA_CALL_ID, createCallId());
        i.putExtra(RtcConstants.EXTRA_PEER_UID, peerUid);
        i.putExtra(RtcConstants.EXTRA_PEER_NAME, TextUtils.isEmpty(peerName) ? "好友" : peerName);
        i.putExtra(RtcConstants.EXTRA_PEER_AVATAR, peerAvatar == null ? "" : peerAvatar);
        i.putExtra(RtcConstants.EXTRA_CALL_TYPE, callType);
        i.putExtra(RtcConstants.EXTRA_INCOMING, false);
        context.startActivity(i);
    }

    public String createCallId() {
        return "call_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public synchronized void setActiveCallListener(ActiveCallListener listener) {
        activeListener = new WeakReference<>(listener);
    }

    public synchronized void clearActiveCallListener(ActiveCallListener listener) {
        if (activeListener.get() == listener) activeListener.clear();
    }

    public synchronized List<RtcSignal> consumePending(String callId) {
        List<RtcSignal> list = pending.remove(callId);
        return list == null ? new ArrayList<>() : list;
    }

    public synchronized void markClosed(String callId) {
        if (!TextUtils.isEmpty(callId)) {
            closed.put(callId, System.currentTimeMillis());
            incomingSeen.remove(callId);
            pending.remove(callId);
        }
    }

    public synchronized boolean isClosed(String callId) {
        cleanupOld(closed, 10 * 60 * 1000L);
        Long ts = closed.get(callId);
        return ts != null;
    }

    @Override
    public void onRtcSignal(RtcSignal signal) {
        if (signal == null || TextUtils.isEmpty(signal.callId)) return;
        ActiveCallListener listener;
        synchronized (this) {
            cleanupOld(closed, 10 * 60 * 1000L);
            cleanupOld(incomingSeen, 2 * 60 * 1000L);
            if (closed.containsKey(signal.callId)) return;
            listener = activeListener.get();
        }

        if (listener != null && TextUtils.equals(listener.getActiveCallId(), signal.callId)) {
            listener.onSignalForActiveCall(signal);
            return;
        }

        if (RtcSignal.INVITE.equals(signal.type)) {
            if (listener != null && !TextUtils.isEmpty(listener.getActiveCallId())) {
                try { RtcSignalManager.get().sendSimple(RtcSignal.BUSY, signal.callId, signal.fromUid); } catch (Exception ignored) {}
                return;
            }
            synchronized (this) {
                if (incomingSeen.containsKey(signal.callId)) return;
                incomingSeen.put(signal.callId, System.currentTimeMillis());
            }
            openIncoming(signal);
            return;
        }

        synchronized (this) {
            List<RtcSignal> list = pending.get(signal.callId);
            if (list == null) {
                list = new ArrayList<>();
                pending.put(signal.callId, list);
            }
            list.add(signal);
        }
    }

    private synchronized void cleanupOld(Map<String, Long> map, long ttlMs) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > ttlMs) iterator.remove();
        }
    }

    private void openIncoming(RtcSignal signal) {
        if (appContext == null) return;
        Intent i = new Intent(appContext, RtcCallActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(RtcConstants.EXTRA_CALL_ID, signal.callId);
        i.putExtra(RtcConstants.EXTRA_PEER_UID, signal.fromUid);
        i.putExtra(RtcConstants.EXTRA_PEER_NAME, TextUtils.isEmpty(signal.fromName) ? "好友" : signal.fromName);
        i.putExtra(RtcConstants.EXTRA_PEER_AVATAR, signal.fromAvatar == null ? "" : signal.fromAvatar);
        i.putExtra(RtcConstants.EXTRA_CALL_TYPE, RtcConstants.typeOf(signal.mode));
        i.putExtra(RtcConstants.EXTRA_INCOMING, true);
        appContext.startActivity(i);
    }
}
