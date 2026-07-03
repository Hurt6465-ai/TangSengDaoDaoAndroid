package com.chat.rtc;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/** Lightweight sink switcher. No frame logging in the final build. */
public class RtcVideoSinkProxy implements VideoSink {
    private VideoSink target;

    public RtcVideoSinkProxy() {}

    public RtcVideoSinkProxy(String ignoredName) {}

    public synchronized void setTarget(VideoSink target) {
        this.target = target;
    }

    @Override public synchronized void onFrame(VideoFrame frame) {
        if (target != null) target.onFrame(frame);
    }
}
