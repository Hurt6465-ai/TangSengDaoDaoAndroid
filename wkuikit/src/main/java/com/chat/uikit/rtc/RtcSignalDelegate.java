package com.chat.uikit.rtc;

import com.chat.uikit.rtc.model.RtcSignal;

public interface RtcSignalDelegate {
    void onRtcSignal(RtcSignal signal);
}
