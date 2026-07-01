package com.chat.rtc;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.chat.rtc.model.RtcSignal;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RtcCallManager implements RtcSignalDelegate {
    private static final long MIN_STALE_CALL_MS = 90_000L;
    private static final long MAX_STALE_CALL_MS = 3 * 60_000L;
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
    private String currentCallId = "";
    private long currentCallStartedAt = 0L;

    public static RtcCallManager get() { return INSTANCE; }

    public synchronized void configure(Context context, String myUid, RtcSignalTransport transport) {
        if (context != null) appContext = context.getApplicationContext();
        RtcSignalManager.get().configure(myUid, transport, this);
    }

    public void startOutgoing(Context context, String peerUid, String peerName, String peerAvatar, int callType) {
        if (TextUtils.isEmpty(peerUid)) return;
        String nextCallId = createCallId();
        synchronized (this) {
            if (!TextUtils.isEmpty(currentCallId)) {
                return;
            }
            currentCallId = nextCallId;
            currentCallStartedAt = System.currentTimeMillis();
        }
        Intent i = new Intent(context, RtcCallActivity.class);
        i.putExtra(RtcConstants.EXTRA_CALL_ID, nextCallId);
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
        if (listener != null && !TextUtils.isEmpty(listener.getActiveCallId())) {
            currentCallId = listener.getActiveCallId();
            currentCallStartedAt = System.currentTimeMillis();
        }
    }

    public synchronized void markActivityVisible(String callId) {
        if (!TextUtils.isEmpty(callId)) {
            currentCallId = callId;
            currentCallStartedAt = System.currentTimeMillis();
        }
    }

    public synchronized boolean isCalling() {
        cleanupOld(closed, 10 * 60 * 1000L);
        cleanupStaleCurrentLocked();
        if (!TextUtils.isEmpty(currentCallId) && !closed.containsKey(currentCallId)) return true;
        ActiveCallListener listener = activeListener.get();
        return listener != null && !TextUtils.isEmpty(listener.getActiveCallId());
    }

    public synchronized void clearActiveCallListener(ActiveCallListener listener) {
        if (activeListener.get() == listener) activeListener.clear();
        cleanupStaleCurrentLocked();
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
            if (TextUtils.equals(currentCallId, callId)) {
                currentCallId = "";
                currentCallStartedAt = 0L;
            }
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
            cleanupStaleCurrentLocked();
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
                if (!TextUtils.isEmpty(currentCallId) && !TextUtils.equals(currentCallId, signal.callId)) {
                    try { RtcSignalManager.get().sendSimple(RtcSignal.BUSY, signal.callId, signal.fromUid); } catch (Exception ignored) {}
                    return;
                }
                if (incomingSeen.containsKey(signal.callId)) return;
                incomingSeen.put(signal.callId, System.currentTimeMillis());
                currentCallId = signal.callId;
                currentCallStartedAt = System.currentTimeMillis();
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

    private synchronized void cleanupStaleCurrentLocked() {
        if (TextUtils.isEmpty(currentCallId) || currentCallStartedAt <= 0L) return;
        ActiveCallListener listener = activeListener.get();
        if (listener != null && TextUtils.equals(listener.getActiveCallId(), currentCallId)) return;

        long staleMs = Math.max(MIN_STALE_CALL_MS,
                RtcConfigManager.getInviteTimeoutMs() + RtcConfigManager.getConnectTimeoutMs() + 15_000L);
        staleMs = Math.min(staleMs, MAX_STALE_CALL_MS);
        if (System.currentTimeMillis() - currentCallStartedAt > staleMs) {
            closed.put(currentCallId, System.currentTimeMillis());
            pending.remove(currentCallId);
            incomingSeen.remove(currentCallId);
            currentCallId = "";
            currentCallStartedAt = 0L;
        }
    }

    public synchronized void forceClearCurrentCall(String callId) {
        if (TextUtils.isEmpty(callId)) return;
        markClosed(callId);
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
        String name = getDisplayName(signal);
        String avatar = getDisplayAvatar(signal);
        int type = RtcConstants.typeOf(signal.mode);
        boolean notified = RtcCallNotification.showIncoming(appContext, signal, name, avatar, type);
        if (!notified) {
            // If notification permission is missing, still try to open the call screen.
            // Android may block this while fully backgrounded, so the host app should also
            // prompt users to enable notification/full-screen call permission through endpoints.
            tryOpenIncomingActivity(signal, name, avatar, type, false);
        }
    }

    private void tryOpenIncomingActivity(RtcSignal signal, String name, String avatar, int type, boolean autoAccept) {
        if (appContext == null || signal == null) return;
        Intent i = new Intent(appContext, RtcCallActivity.class);
        i.setAction(autoAccept ? RtcConstants.ACTION_ANSWER_CALL : RtcConstants.ACTION_INCOMING_CALL);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        i.putExtra(RtcConstants.EXTRA_CALL_ID, signal.callId);
        i.putExtra(RtcConstants.EXTRA_PEER_UID, signal.fromUid);
        i.putExtra(RtcConstants.EXTRA_PEER_NAME, TextUtils.isEmpty(name) ? "好友" : name);
        i.putExtra(RtcConstants.EXTRA_PEER_AVATAR, avatar == null ? "" : avatar);
        i.putExtra(RtcConstants.EXTRA_CALL_TYPE, type);
        i.putExtra(RtcConstants.EXTRA_INCOMING, true);
        i.putExtra(RtcConstants.EXTRA_AUTO_ACCEPT, autoAccept);
        try { appContext.startActivity(i); } catch (Exception ignored) {}
    }

    public void rejectIncomingFromNotification(Context context, String callId, String peerUid) {
        try { RtcSignalManager.get().sendSimple(RtcSignal.REJECT, callId, peerUid); } catch (Exception ignored) {}
        markClosed(callId);
        RtcCallNotification.cancelIncoming(context);
    }

    private String getDisplayName(RtcSignal signal) {
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(signal.fromUid, WKChannelType.PERSONAL);
            if (channel != null) {
                String name = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
                if (!TextUtils.isEmpty(name)) return name;
            }
        } catch (Exception ignored) {
        }
        return TextUtils.isEmpty(signal.fromName) ? "好友" : signal.fromName;
    }

    private String getDisplayAvatar(RtcSignal signal) {
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(signal.fromUid, WKChannelType.PERSONAL);
            if (channel != null && !TextUtils.isEmpty(channel.avatar)) return channel.avatar;
        } catch (Exception ignored) {
        }
        return signal.fromAvatar == null ? "" : signal.fromAvatar;
    }
}
