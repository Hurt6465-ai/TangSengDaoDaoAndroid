package com.chat.uikit.rtc;

public interface RtcSignalTransport {
    /** Send payload to peerUid by WuKong IM. Payload already contains RtcConstants.SIGNAL_PREFIX. */
    void sendSignal(String peerUid, String payload) throws Exception;
}
