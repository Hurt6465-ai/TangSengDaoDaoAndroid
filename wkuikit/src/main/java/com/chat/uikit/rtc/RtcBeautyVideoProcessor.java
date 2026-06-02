package com.chat.uikit.rtc;

import android.util.Log;

import org.webrtc.JavaI420Buffer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoProcessor;
import org.webrtc.VideoSink;

import java.nio.ByteBuffer;

/**
 * WebRTC VideoProcessor bridge.
 * It beautifies outgoing local frames. If anything goes wrong, it forwards the original frame.
 */
public class RtcBeautyVideoProcessor implements VideoProcessor {
    private static final String TAG = "RtcBeautyProcessor";

    private final RtcBeautyManager beautyManager;
    private VideoSink sink;
    private volatile boolean enabled = true;

    public RtcBeautyVideoProcessor(RtcBeautyManager beautyManager) {
        this.beautyManager = beautyManager;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (beautyManager != null) beautyManager.setEnabled(enabled);
    }

    @Override public void setSink(VideoSink sink) {
        this.sink = sink;
    }

    @Override public void onCapturerStarted(boolean success) {}

    @Override public void onCapturerStopped() {}

    @Override public void onFrameCaptured(VideoFrame frame) {
        VideoSink out = sink;
        if (out == null || frame == null) return;

        if (!enabled || beautyManager == null || !beautyManager.isEnabled()) {
            out.onFrame(frame);
            return;
        }

        VideoFrame.I420Buffer i420 = null;
        VideoFrame outFrame = null;
        try {
            i420 = frame.getBuffer().toI420();
            int width = i420.getWidth();
            int height = i420.getHeight();

            byte[] packed = packI420(i420);
            byte[] result = beautyManager.processI420(packed, width, height, width);
            if (result == null) {
                out.onFrame(frame);
                return;
            }

            JavaI420Buffer buffer = JavaI420Buffer.allocate(width, height);
            unpackI420(result, width, height, buffer);
            outFrame = new VideoFrame(buffer, frame.getRotation(), frame.getTimestampNs());
            out.onFrame(outFrame);
        } catch (Throwable t) {
            Log.w(TAG, "beauty frame failed", t);
            out.onFrame(frame);
        } finally {
            if (outFrame != null) outFrame.release();
            if (i420 != null) i420.release();
        }
    }

    private byte[] packI420(VideoFrame.I420Buffer src) {
        int width = src.getWidth();
        int height = src.getHeight();
        int ySize = width * height;
        int uvWidth = (width + 1) / 2;
        int uvHeight = (height + 1) / 2;
        int uvSize = uvWidth * uvHeight;
        byte[] out = new byte[ySize + uvSize * 2];

        copyToArray(src.getDataY(), src.getStrideY(), out, 0, width, height);
        copyToArray(src.getDataU(), src.getStrideU(), out, ySize, uvWidth, uvHeight);
        copyToArray(src.getDataV(), src.getStrideV(), out, ySize + uvSize, uvWidth, uvHeight);
        return out;
    }

    private void unpackI420(byte[] src, int width, int height, JavaI420Buffer dst) {
        int ySize = width * height;
        int uvWidth = (width + 1) / 2;
        int uvHeight = (height + 1) / 2;
        int uvSize = uvWidth * uvHeight;

        copyFromArray(src, 0, dst.getDataY(), dst.getStrideY(), width, height);
        copyFromArray(src, ySize, dst.getDataU(), dst.getStrideU(), uvWidth, uvHeight);
        copyFromArray(src, ySize + uvSize, dst.getDataV(), dst.getStrideV(), uvWidth, uvHeight);
    }

    private void copyToArray(ByteBuffer src, int srcStride, byte[] dst, int dstOffset, int width, int height) {
        ByteBuffer dup = src.duplicate();
        for (int row = 0; row < height; row++) {
            dup.position(row * srcStride);
            dup.get(dst, dstOffset + row * width, width);
        }
    }

    private void copyFromArray(byte[] src, int srcOffset, ByteBuffer dst, int dstStride, int width, int height) {
        ByteBuffer dup = dst.duplicate();
        for (int row = 0; row < height; row++) {
            dup.position(row * dstStride);
            dup.put(src, srcOffset + row * width, width);
        }
    }
}
