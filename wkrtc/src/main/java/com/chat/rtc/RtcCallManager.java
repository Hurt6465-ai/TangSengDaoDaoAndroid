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
    private static final long STALE_CALL_LOCK_MS = 45_000L;
    private Context appContext;
    private WeakReference<ActiveCallListener> activeListener = new WeakReference<>(null);
    private final Map<String, List<RtcSignal>> pending = new HashMap<>();
    private final Map<String, Long> closed = new HashMap<>();
    private final Map<String, Long> incomingSeen = new HashMap<>();
    // Keep the original incoming INVITE until a terminal signal arrives.
    // This lets us write a missed/cancelled record even if Android blocks the Activity.
    private final Map<String, RtcSignal> incomingInviteMap = new HashMap<>();
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
            cleanupStaleCurrentLocked();
            if (hasLiveListenerLocked() || !TextUtils.isEmpty(currentCallId)) {
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
        return hasLiveListenerLocked() || (!TextUtils.isEmpty(currentCallId) && !closed.containsKey(currentCallId));
    }

    private boolean hasLiveListenerLocked() {
        ActiveCallListener listener = activeListener.get();
        return listener != null && !TextUtils.isEmpty(listener.getActiveCallId());
    }

    private boolean isCurrentCallStaleLocked() {
        if (TextUtils.isEmpty(currentCallId)) return false;
        if (closed.containsKey(currentCallId)) return true;
        if (hasLiveListenerLocked()) return false;
        return currentCallStartedAt <= 0 || System.currentTimeMillis() - currentCallStartedAt > STALE_CALL_LOCK_MS;
    }

    private void cleanupStaleCurrentLocked() {
        if (isCurrentCallStaleLocked()) {
            incomingSeen.remove(currentCallId);
            incomingInviteMap.remove(currentCallId);
            pending.remove(currentCallId);
            currentCallId = "";
            currentCallStartedAt = 0L;
        }
    }

    private boolean isTerminalSignal(RtcSignal signal) {
        return signal != null && (RtcSignal.CANCEL.equals(signal.type)
                || RtcSignal.END.equals(signal.type)
                || RtcSignal.REJECT.equals(signal.type)
                || RtcSignal.TIMEOUT.equals(signal.type)
                || RtcSignal.BUSY.equals(signal.type));
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
            incomingInviteMap.remove(callId);
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

        if (isTerminalSignal(signal)) {
            RtcSignal invite;
            synchronized (this) {
                invite = incomingInviteMap.remove(signal.callId);
                pending.remove(signal.callId);
                incomingSeen.remove(signal.callId);
                if (TextUtils.equals(currentCallId, signal.callId)) {
                    currentCallId = "";
                    currentCallStartedAt = 0L;
                }
                closed.put(signal.callId, System.currentTimeMillis());
            }
            saveIncomingTerminalRecordIfNeeded(invite, signal);
            return;
        }

        if (RtcSignal.INVITE.equals(signal.type)) {
            synchronized (this) {
                cleanupStaleCurrentLocked();
                boolean busy = false;
                if (hasLiveListenerLocked()) {
                    busy = true;
                } else if (!TextUtils.isEmpty(currentCallId) && !TextUtils.equals(currentCallId, signal.callId)) {
                    busy = true;
                }
                if (busy) {
                    try { RtcSignalManager.get().sendSimple(RtcSignal.BUSY, signal.callId, signal.fromUid); } catch (Exception ignored) {}
                    return;
                }
                if (incomingSeen.containsKey(signal.callId)) {
                    try { RtcSignalManager.get().sendSimple(RtcSignal.RINGING, signal.callId, signal.fromUid); } catch (Exception ignored) {}
                    return;
                }
                incomingSeen.put(signal.callId, System.currentTimeMillis());
                incomingInviteMap.put(signal.callId, signal);
                currentCallId = signal.callId;
                currentCallStartedAt = System.currentTimeMillis();
            }
            try { RtcSignalManager.get().sendSimple(RtcSignal.RINGING, signal.callId, signal.fromUid); } catch (Exception ignored) {}
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
        String name = getDisplayName(signal);
        String avatar = getDisplayAvatar(signal);
        int type = RtcConstants.typeOf(signal.mode);
        RtcCallNotification.showIncoming(appContext, signal, name, avatar, type);
        // Do not rely on notification full-screen alone. Some devices show only a heads-up card
        // even for call notifications. Start the call Activity too; Android may block it while
        // backgrounded, but the notification path remains as fallback.
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

    public void rejectIncomingFromNotification(Context context, String callId, String peerUid, String peerName, int callType) {
        RtcSignal invite;
        synchronized (this) {
            invite = incomingInviteMap.remove(callId);
        }
        String safePeerUid = TextUtils.isEmpty(peerUid) && invite != null ? invite.fromUid : peerUid;
        String safePeerName = TextUtils.isEmpty(peerName) && invite != null ? getDisplayName(invite) : peerName;
        int safeCallType = callType == RtcConstants.VIDEO || callType == RtcConstants.AUDIO
                ? callType
                : (invite == null ? RtcConstants.AUDIO : RtcConstants.typeOf(invite.mode));

        try { RtcSignalManager.get().sendSimple(RtcSignal.REJECT, callId, safePeerUid); } catch (Exception ignored) {}
        RtcCallRecordMessageSender.saveLocal(callId, safePeerUid, safePeerName, safeCallType, true, "rejected", 0L);
        RtcCallRecordReporter.report(callId, safePeerUid, safePeerName, safeCallType, true, "rejected", 0L);
        markClosed(callId);
        RtcCallNotification.cancelIncoming(context);
    }

    /**
     * Terminal signals can arrive while the Activity was never opened, for example when
     * background-start restrictions block the incoming UI and the caller cancels from their side.
     * In that case the Activity will not have a chance to write the local missed-call record.
     */
    private void saveIncomingTerminalRecordIfNeeded(RtcSignal invite, RtcSignal terminal) {
        if (invite == null || terminal == null || TextUtils.isEmpty(invite.fromUid)) return;
        String reason;
        if (RtcSignal.CANCEL.equals(terminal.type) || RtcSignal.END.equals(terminal.type)) {
            reason = "remote_cancelled";
        } else if (RtcSignal.TIMEOUT.equals(terminal.type)) {
            reason = "missed";
        } else if (RtcSignal.REJECT.equals(terminal.type)) {
            reason = "rejected";
        } else if (RtcSignal.BUSY.equals(terminal.type)) {
            reason = "busy";
        } else {
            return;
        }
        int callType = RtcConstants.typeOf(invite.mode);
        String name = getDisplayName(invite);
        RtcCallRecordMessageSender.saveLocal(terminal.callId, invite.fromUid, name, callType, true, reason, 0L);
        RtcCallRecordReporter.report(terminal.callId, invite.fromUid, name, callType, true, reason, 0L);
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
