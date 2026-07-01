package com.chat.rtc;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
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

/**
 * RTC call state manager.
 *
 * This class intentionally keeps the busy check close to Tinode's model:
 * - Only a live RtcCallActivity/listener is treated as a real active call.
 * - A leftover callId without a live Activity is treated as stale and is cleared.
 * - Incoming INVITE immediately replies RINGING, so the caller knows the callee received it.
 * - CANCEL/END/REJECT/TIMEOUT always release local state, even if the call UI is not alive.
 */
public class RtcCallManager implements RtcSignalDelegate {
    public interface ActiveCallListener {
        String getActiveCallId();
        void onSignalForActiveCall(RtcSignal signal);
    }

    private static final long CLOSED_TTL_MS = 10 * 60 * 1000L;
    private static final long INCOMING_SEEN_TTL_MS = 2 * 60 * 1000L;
    private static final long STALE_LOCK_MS = 45 * 1000L;

    private static final RtcCallManager INSTANCE = new RtcCallManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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
        if (context == null || TextUtils.isEmpty(peerUid)) return;
        String myUid = RtcSignalManager.get().myUid();
        if (!TextUtils.isEmpty(myUid) && TextUtils.equals(myUid, peerUid)) {
            // Never start a self call. It creates local busy locks and the peer never rings.
            return;
        }

        ActiveCallListener listener;
        synchronized (this) {
            cleanupLocked();
            listener = activeListener.get();
            if (isLiveListener(listener)) {
                return;
            }
            // A previous activity died without a clean END/CANCEL. Do not let this block a new call.
            clearCurrentLocked();
        }

        String nextCallId = createCallId();
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
        if (isLiveListener(listener)) {
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
        cleanupLocked();
        ActiveCallListener listener = activeListener.get();
        return isLiveListener(listener);
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
            if (TextUtils.equals(currentCallId, callId)) {
                clearCurrentLocked();
            }
        }
    }

    public synchronized void forceClearCurrentCall() {
        clearCurrentLocked();
    }

    public synchronized boolean isClosed(String callId) {
        cleanupOld(closed, CLOSED_TTL_MS);
        Long ts = closed.get(callId);
        return ts != null;
    }

    @Override
    public void onRtcSignal(RtcSignal signal) {
        if (signal == null || TextUtils.isEmpty(signal.callId)) return;

        String myUid = RtcSignalManager.get().myUid();
        if (!TextUtils.isEmpty(signal.fromUid) && TextUtils.equals(signal.fromUid, myUid)) {
            // Local echo of our own RTC packet. Hide from chat, but never dispatch to the call manager.
            return;
        }

        ActiveCallListener listener;
        synchronized (this) {
            cleanupLocked();
            if (closed.containsKey(signal.callId)) return;
            listener = activeListener.get();
        }

        if (listener != null && TextUtils.equals(listener.getActiveCallId(), signal.callId)) {
            listener.onSignalForActiveCall(signal);
            return;
        }

        if (isTerminalSignal(signal)) {
            // The UI may not be alive yet. Still release pending/current lock immediately.
            markClosed(signal.callId);
            return;
        }

        if (RtcSignal.INVITE.equals(signal.type)) {
            handleInvite(signal, listener);
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

    private void handleInvite(RtcSignal signal, ActiveCallListener listener) {
        if (signal.isExpired()) {
            markClosed(signal.callId);
            return;
        }

        if (isLiveListener(listener)) {
            // Same call would have been handled above. A different live call means real busy.
            try { RtcSignalManager.get().sendSimple(RtcSignal.BUSY, signal.callId, signal.fromUid); } catch (Exception ignored) {}
            return;
        }

        synchronized (this) {
            cleanupOld(incomingSeen, INCOMING_SEEN_TTL_MS);
            if (incomingSeen.containsKey(signal.callId)) {
                try { RtcSignalManager.get().sendSimple(RtcSignal.RINGING, signal.callId, signal.fromUid); } catch (Exception ignored) {}
                return;
            }
            incomingSeen.put(signal.callId, System.currentTimeMillis());
            currentCallId = signal.callId;
            currentCallStartedAt = System.currentTimeMillis();
        }

        // Tinode-like ack: tell caller that callee actually received the invite and is ringing.
        try { RtcSignalManager.get().sendSimple(RtcSignal.RINGING, signal.callId, signal.fromUid); } catch (Exception ignored) {}

        openIncoming(signal);
        scheduleStaleIncomingClear(signal.callId);
    }

    private void scheduleStaleIncomingClear(String callId) {
        long delay = Math.max(RtcConfigManager.getCallTimeoutMs() + 10_000L, STALE_LOCK_MS);
        mainHandler.postDelayed(() -> {
            synchronized (RtcCallManager.this) {
                ActiveCallListener listener = activeListener.get();
                if (!TextUtils.equals(currentCallId, callId)) return;
                if (isLiveListener(listener)) return;
                markClosed(callId);
            }
        }, delay);
    }

    private boolean isTerminalSignal(RtcSignal signal) {
        return RtcSignal.CANCEL.equals(signal.type)
                || RtcSignal.END.equals(signal.type)
                || RtcSignal.REJECT.equals(signal.type)
                || RtcSignal.TIMEOUT.equals(signal.type);
    }

    private synchronized void cleanupLocked() {
        cleanupOld(closed, CLOSED_TTL_MS);
        cleanupOld(incomingSeen, INCOMING_SEEN_TTL_MS);
        ActiveCallListener listener = activeListener.get();
        if (!isLiveListener(listener) && !TextUtils.isEmpty(currentCallId)
                && System.currentTimeMillis() - currentCallStartedAt > STALE_LOCK_MS) {
            clearCurrentLocked();
        }
    }

    private boolean isLiveListener(ActiveCallListener listener) {
        return listener != null && !TextUtils.isEmpty(listener.getActiveCallId());
    }

    private void clearCurrentLocked() {
        currentCallId = "";
        currentCallStartedAt = 0L;
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
        // Show notification for lock screen/background.
        try { RtcCallNotification.showIncoming(appContext, signal, name, avatar, type); } catch (Exception ignored) {}
        // Also try to bring up the call UI directly. This is what made the old in-app call feel reliable.
        tryOpenIncomingActivity(signal, name, avatar, type, false);
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
