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
    public interface ActiveCallListener {
        String getActiveCallId();
        void onSignalForActiveCall(RtcSignal signal);
    }

    private static final RtcCallManager INSTANCE = new RtcCallManager();
    private static final long CLOSED_TTL_MS = 10 * 60 * 1000L;
    private static final long INCOMING_SEEN_TTL_MS = 2 * 60 * 1000L;
    private static final long STALE_CALL_GUARD_MS = 2 * 60 * 1000L;

    private Context appContext;
    private WeakReference<ActiveCallListener> activeListener = new WeakReference<>(null);
    private final Map<String, List<RtcSignal>> pending = new HashMap<>();
    private final Map<String, Long> closed = new HashMap<>();
    private final Map<String, Long> incomingSeen = new HashMap<>();
    private final Map<String, RtcSignal> incomingInviteMap = new HashMap<>();
    private String currentCallId = "";
    private long currentCallStartedAt = 0L;

    public static RtcCallManager get() { return INSTANCE; }

    public synchronized void configure(Context context, String myUid, RtcSignalTransport transport) {
        if (context != null) appContext = context.getApplicationContext();
        RtcDebugLogger.init(appContext);
        RtcDebugLogger.i("RtcCallManager", "configure my=" + RtcDebugLogger.shortUid(myUid)
                + " context=" + (appContext != null) + " transport=" + (transport != null));
        RtcSignalManager.get().configure(myUid, transport, this);
    }

    public void startOutgoing(Context context, String peerUid, String peerName, String peerAvatar, int callType) {
        RtcDebugLogger.i("RtcCallManager", "startOutgoing peer=" + RtcDebugLogger.shortUid(peerUid)
                + " name=" + peerName + " type=" + callType);
        if (context == null || TextUtils.isEmpty(peerUid)) {
            RtcDebugLogger.w("RtcCallManager", "startOutgoing blocked context/peer empty");
            return;
        }
        String myUid = RtcSignalManager.get().myUid();
        if (!TextUtils.isEmpty(myUid) && TextUtils.equals(peerUid, myUid)) {
            RtcDebugLogger.w("RtcCallManager", "startOutgoing blocked self-call my=" + RtcDebugLogger.shortUid(myUid));
            forceClearAllZombieCalls();
            return;
        }
        String nextCallId = createCallId();
        synchronized (this) {
            cleanupStateLocked();
            if (hasLiveCallLocked()) {
                ActiveCallListener listener = activeListener.get();
                if (listener != null && !TextUtils.isEmpty(listener.getActiveCallId())) {
                    RtcDebugLogger.w("RtcCallManager", "startOutgoing blocked liveCall current=" + currentCallId);
                    return;
                }
                RtcDebugLogger.w("RtcCallManager", "startOutgoing clear zombie current=" + currentCallId);
                forceClearAllZombieCallsLocked();
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
        try {
            context.startActivity(i);
            RtcDebugLogger.i("RtcCallManager", "startOutgoing activity launched callId=" + nextCallId
                    + " peer=" + RtcDebugLogger.shortUid(peerUid));
        } catch (Exception e) {
            RtcDebugLogger.e("RtcCallManager", "startOutgoing activity launch failed", e);
        }
    }

    public String createCallId() {
        return "call_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public synchronized void setActiveCallListener(ActiveCallListener listener) {
        activeListener = new WeakReference<>(listener);
        if (listener != null && !TextUtils.isEmpty(listener.getActiveCallId())) {
            currentCallId = listener.getActiveCallId();
            currentCallStartedAt = System.currentTimeMillis();
            RtcDebugLogger.i("RtcCallManager", "setActiveListener callId=" + currentCallId);
        }
    }

    public synchronized void markActivityVisible(String callId) {
        if (!TextUtils.isEmpty(callId)) {
            currentCallId = callId;
            currentCallStartedAt = System.currentTimeMillis();
        }
    }

    public synchronized boolean isCalling() {
        cleanupStateLocked();
        if (!TextUtils.isEmpty(currentCallId) && !closed.containsKey(currentCallId)) return true;
        ActiveCallListener listener = activeListener.get();
        return listener != null && !TextUtils.isEmpty(listener.getActiveCallId());
    }

    public synchronized void clearActiveCallListener(ActiveCallListener listener) {
        if (activeListener.get() == listener) activeListener.clear();
        cleanupStateLocked();
    }

    public synchronized List<RtcSignal> consumePending(String callId) {
        List<RtcSignal> list = pending.remove(callId);
        return list == null ? new ArrayList<>() : list;
    }

    public synchronized void markClosed(String callId) {
        if (!TextUtils.isEmpty(callId)) {
            RtcDebugLogger.i("RtcCallManager", "markClosed callId=" + callId + " current=" + currentCallId);
            closed.put(callId, System.currentTimeMillis());
            incomingSeen.remove(callId);
            incomingInviteMap.remove(callId);
            pending.remove(callId);
            if (TextUtils.equals(currentCallId, callId)) {
                currentCallId = "";
                currentCallStartedAt = 0L;
            }
        }
    }

    public synchronized boolean isClosed(String callId) {
        cleanupStateLocked();
        Long ts = closed.get(callId);
        return ts != null;
    }

    @Override
    public void onRtcSignal(RtcSignal signal) {
        RtcDebugLogger.i("RtcCallManager", "onRtcSignal " + RtcDebugLogger.signal(signal));
        if (signal == null || TextUtils.isEmpty(signal.callId)) return;
        ActiveCallListener listener;
        synchronized (this) {
            cleanupStateLocked();
            if (closed.containsKey(signal.callId)) {
                RtcDebugLogger.w("RtcCallManager", "ignore closed " + RtcDebugLogger.signal(signal));
                return;
            }
            listener = activeListener.get();
        }

        if (listener != null && TextUtils.equals(listener.getActiveCallId(), signal.callId)) {
            RtcDebugLogger.i("RtcCallManager", "deliver to active screen " + RtcDebugLogger.signal(signal));
            listener.onSignalForActiveCall(signal);
            return;
        }

        if (RtcSignal.INVITE.equals(signal.type)) {
            if (listener != null && !TextUtils.isEmpty(listener.getActiveCallId())) {
                RtcDebugLogger.w("RtcCallManager", "invite busy because activeListener exists active=" + listener.getActiveCallId());
                try { RtcSignalManager.get().sendSimple(RtcSignal.BUSY, signal.callId, signal.fromUid); } catch (Exception e) { RtcDebugLogger.e("RtcCallManager", "send BUSY failed", e); }
                return;
            }
            synchronized (this) {
                cleanupStateLocked();
                if (!TextUtils.isEmpty(currentCallId) && !TextUtils.equals(currentCallId, signal.callId)) {
                    ActiveCallListener active = activeListener.get();
                    if (active != null && !TextUtils.isEmpty(active.getActiveCallId())) {
                        try { RtcSignalManager.get().sendSimple(RtcSignal.BUSY, signal.callId, signal.fromUid); } catch (Exception ignored) {}
                        return;
                    }
                    // Zombie lock only, clear it and let the new invite ring.
                    RtcDebugLogger.w("RtcCallManager", "invite clears zombie current=" + currentCallId);
                    forceClearAllZombieCallsLocked();
                }
                if (incomingSeen.containsKey(signal.callId)) {
                    RtcDebugLogger.i("RtcCallManager", "invite duplicate, resend ringing " + signal.callId);
                    try { RtcSignalManager.get().sendSimple(RtcSignal.RINGING, signal.callId, signal.fromUid); } catch (Exception e) { RtcDebugLogger.e("RtcCallManager", "resend RINGING failed", e); }
                    return;
                }
                incomingSeen.put(signal.callId, System.currentTimeMillis());
                incomingInviteMap.put(signal.callId, signal);
                currentCallId = signal.callId;
                currentCallStartedAt = System.currentTimeMillis();
            }
            try { RtcSignalManager.get().sendSimple(RtcSignal.RINGING, signal.callId, signal.fromUid); } catch (Exception e) { RtcDebugLogger.e("RtcCallManager", "send RINGING failed", e); }
            RtcDebugLogger.i("RtcCallManager", "openIncoming for " + RtcDebugLogger.signal(signal));
            openIncoming(signal);
            return;
        }

        if (isTerminalSignal(signal)) {
            RtcSignal invite;
            boolean shouldRecord;
            synchronized (this) {
                invite = incomingInviteMap.remove(signal.callId);
                shouldRecord = invite != null || TextUtils.equals(currentCallId, signal.callId) || incomingSeen.containsKey(signal.callId);
                markClosed(signal.callId);
            }
            if (shouldRecord && invite != null && (RtcSignal.CANCEL.equals(signal.type) || RtcSignal.END.equals(signal.type) || RtcSignal.TIMEOUT.equals(signal.type))) {
                int type = RtcConstants.typeOf(invite.mode);
                String peerUid = TextUtils.isEmpty(invite.fromUid) ? signal.fromUid : invite.fromUid;
                String reason = RtcSignal.TIMEOUT.equals(signal.type) ? "missed" : "remote_cancelled";
                RtcCallRecordReporter.report(signal.callId, peerUid, getDisplayName(invite), type, true, reason, 0L);
            }
            return;
        }

        synchronized (this) {
            RtcDebugLogger.i("RtcCallManager", "pending signal " + RtcDebugLogger.signal(signal));
            List<RtcSignal> list = pending.get(signal.callId);
            if (list == null) {
                list = new ArrayList<>();
                pending.put(signal.callId, list);
            }
            list.add(signal);
        }
    }

    private boolean isTerminalSignal(RtcSignal signal) {
        if (signal == null) return false;
        return RtcSignal.CANCEL.equals(signal.type)
                || RtcSignal.END.equals(signal.type)
                || RtcSignal.REJECT.equals(signal.type)
                || RtcSignal.BUSY.equals(signal.type)
                || RtcSignal.TIMEOUT.equals(signal.type);
    }

    private synchronized void cleanupStateLocked() {
        cleanupOld(closed, CLOSED_TTL_MS);
        cleanupOld(incomingSeen, INCOMING_SEEN_TTL_MS);
        if (!TextUtils.isEmpty(currentCallId)) {
            boolean activeScreen = false;
            ActiveCallListener listener = activeListener.get();
            if (listener != null && TextUtils.equals(currentCallId, listener.getActiveCallId())) {
                activeScreen = true;
            }
            boolean closedCurrent = closed.containsKey(currentCallId);
            boolean stale = currentCallStartedAt > 0 && (System.currentTimeMillis() - currentCallStartedAt > STALE_CALL_GUARD_MS);
            if (closedCurrent || (!activeScreen && stale)) {
                currentCallId = "";
                currentCallStartedAt = 0L;
            }
        }
    }

    private boolean hasLiveCallLocked() {
        if (TextUtils.isEmpty(currentCallId)) return false;
        if (closed.containsKey(currentCallId)) return false;
        ActiveCallListener listener = activeListener.get();
        if (listener != null) return true;
        return currentCallStartedAt > 0 && (System.currentTimeMillis() - currentCallStartedAt) < STALE_CALL_GUARD_MS;
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
        if (appContext == null) {
            RtcDebugLogger.w("RtcCallManager", "openIncoming skipped appContext null " + RtcDebugLogger.signal(signal));
            return;
        }
        String name = getDisplayName(signal);
        String avatar = getDisplayAvatar(signal);
        int type = RtcConstants.typeOf(signal.mode);
        boolean notificationShown = false;
        try {
            notificationShown = RtcCallNotification.showIncoming(appContext, signal, name, avatar, type);
        } catch (Throwable e) {
            // Notification must never block the actual incoming call UI.
            RtcDebugLogger.e("RtcCallManager", "showIncoming crashed, fallback to activity " + RtcDebugLogger.signal(signal), e);
        }
        RtcDebugLogger.i("RtcCallManager", "showIncoming notification=" + notificationShown + " name=" + name
                + " type=" + type + " " + RtcDebugLogger.signal(signal));
        // After the invite is actually received, always try opening the call page directly.
        // The notification is only a supplement; it must not be the only incoming-call path.
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
        try {
            appContext.startActivity(i);
            RtcDebugLogger.i("RtcCallManager", "incoming activity launched " + RtcDebugLogger.signal(signal));
        } catch (Exception e) {
            RtcDebugLogger.e("RtcCallManager", "incoming activity launch failed " + RtcDebugLogger.signal(signal), e);
        }
    }

    public void rejectIncomingFromNotification(Context context, String callId, String peerUid, String peerName, int callType) {
        try { RtcSignalManager.get().sendSimple(RtcSignal.REJECT, callId, peerUid); } catch (Exception ignored) {}
        RtcCallRecordReporter.report(callId, peerUid, TextUtils.isEmpty(peerName) ? "好友" : peerName, callType, true, "rejected", 0L);
        markClosed(callId);
        RtcCallNotification.cancelIncoming(context);
    }


    public synchronized void forceClearAllZombieCalls() {
        RtcDebugLogger.w("RtcCallManager", "forceClearAllZombieCalls current=" + currentCallId);
        forceClearAllZombieCallsLocked();
        try { RtcCallNotification.cancelIncoming(appContext); } catch (Exception ignored) {}
    }

    private void forceClearAllZombieCallsLocked() {
        if (!TextUtils.isEmpty(currentCallId)) {
            closed.put(currentCallId, System.currentTimeMillis());
            pending.remove(currentCallId);
            incomingSeen.remove(currentCallId);
            incomingInviteMap.remove(currentCallId);
        }
        currentCallId = "";
        currentCallStartedAt = 0L;
        activeListener.clear();
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
