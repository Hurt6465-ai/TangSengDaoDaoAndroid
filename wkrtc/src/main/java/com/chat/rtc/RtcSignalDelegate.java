package com.chat.rtc;

import com.chat.rtc.model.RtcSignal;

public interface RtcSignalDelegate {
    void onRtcSignal(RtcSignal signal);
}
