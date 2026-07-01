package com.chat.rtc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

public class RtcCallActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (RtcConstants.ACTION_REJECT_CALL.equals(action)) {
            String callId = intent.getStringExtra(RtcConstants.EXTRA_CALL_ID);
            String peerUid = intent.getStringExtra(RtcConstants.EXTRA_PEER_UID);
            String peerName = intent.getStringExtra(RtcConstants.EXTRA_PEER_NAME);
            int callType = intent.getIntExtra(RtcConstants.EXTRA_CALL_TYPE, RtcConstants.AUDIO);
            if (!TextUtils.isEmpty(callId)) {
                RtcCallManager.get().rejectIncomingFromNotification(context, callId, peerUid, peerName, callType);
            }
        }
    }
}
