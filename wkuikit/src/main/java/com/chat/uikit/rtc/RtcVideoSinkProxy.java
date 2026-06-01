package com.chat.uikit.rtc;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

public class RtcVideoSinkProxy implements VideoSink {
    private VideoSink target;
    public synchronized void setTarget(VideoSink target) { this.target = target; }
    @Override public synchronized void onFrame(VideoFrame frame) { if (target != null) target.onFrame(frame); }
}
