package com.chat.rtc;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

public class RtcVideoSinkProxy implements VideoSink {
    private final String name;
    private VideoSink target;
    private long lastLogAt;
    private int lastWidth;
    private int lastHeight;

    public RtcVideoSinkProxy() {
        this("video");
    }

    public RtcVideoSinkProxy(String name) {
        this.name = name == null ? "video" : name;
    }

    public synchronized void setTarget(VideoSink target) {
        this.target = target;
    }

    @Override public synchronized void onFrame(VideoFrame frame) {
        if (frame != null) {
            int w = frame.getRotatedWidth();
            int h = frame.getRotatedHeight();
            long now = System.currentTimeMillis();
            if (w != lastWidth || h != lastHeight || now - lastLogAt > 2500L) {
                lastWidth = w;
                lastHeight = h;
                lastLogAt = now;
                RtcDebugLogger.i("RtcVideoSink", name + " frame " + w + "x" + h + " rotation=" + frame.getRotation());
            }
        }
        if (target != null) target.onFrame(frame);
    }
}
