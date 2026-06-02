package com.chat.uikit.rtc;

import android.content.Context;
import android.util.Log;

import com.pixpark.gpupixel.FaceDetector;
import com.pixpark.gpupixel.GPUPixel;
import com.pixpark.gpupixel.GPUPixelFilter;
import com.pixpark.gpupixel.GPUPixelSinkRawData;
import com.pixpark.gpupixel.GPUPixelSourceRawData;

/**
 * GPUPixel beauty pipeline for local WebRTC frames.
 *
 * The AAR exposes a Source -> Filter -> Sink pipeline. We feed I420 frames from
 * WebRTC into GPUPixel, run beauty + face reshape filters, then read I420 back.
 * If GPUPixel fails on a device, the caller will safely fall back to the original frame.
 */
public final class RtcBeautyManager {
    private static final String TAG = "RtcBeautyManager";
    private static final RtcBeautyManager INSTANCE = new RtcBeautyManager();

    public static RtcBeautyManager get() { return INSTANCE; }

    private boolean initialized;
    private volatile boolean enabled = true;
    private float smooth = RtcConstants.BEAUTY_SMOOTHING;
    private float whiten = RtcConstants.BEAUTY_WHITEN;
    private float filter = RtcConstants.BEAUTY_FILTER;
    private float thinFace = RtcConstants.BEAUTY_THIN_FACE;
    private float bigEye = RtcConstants.BEAUTY_BIG_EYE;

    private GPUPixelSourceRawData source;
    private GPUPixelFilter beautyFilter;
    private GPUPixelFilter reshapeFilter;
    private GPUPixelSinkRawData sink;
    private FaceDetector faceDetector;

    private RtcBeautyManager() {}

    public synchronized void init(Context context) {
        if (initialized) return;
        try {
            GPUPixel.Init(context.getApplicationContext());
            source = GPUPixelSourceRawData.Create();
            beautyFilter = GPUPixelFilter.Create(GPUPixelFilter.BEAUTY_FACE_FILTER);
            reshapeFilter = GPUPixelFilter.Create(GPUPixelFilter.FACE_RESHAPE_FILTER);
            sink = GPUPixelSinkRawData.Create();
            faceDetector = FaceDetector.Create();

            if (source != null && beautyFilter != null && reshapeFilter != null && sink != null) {
                source.AddSink(beautyFilter);
                beautyFilter.AddSink(reshapeFilter);
                reshapeFilter.AddSink(sink);
                applyProperties();
                initialized = true;
            } else {
                releaseInternal();
                Log.w(TAG, "GPUPixel pipeline create failed");
            }
        } catch (Throwable t) {
            releaseInternal();
            Log.w(TAG, "GPUPixel init failed", t);
        }
    }

    public boolean isReady() { return initialized; }
    public boolean isEnabled() { return enabled && initialized; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public synchronized void setStrength(float smooth, float whiten, float filter, float thinFace, float bigEye) {
        this.smooth = clamp(smooth);
        this.whiten = clamp(whiten);
        this.filter = clamp(filter);
        this.thinFace = clamp(thinFace);
        this.bigEye = clamp(bigEye);
        applyProperties();
    }

    /**
     * Process I420 frame. Return null when processing is unavailable or failed.
     */
    public synchronized byte[] processI420(byte[] i420, int width, int height, int stride) {
        if (!isEnabled() || i420 == null || width <= 0 || height <= 0) return null;
        try {
            if (faceDetector != null && reshapeFilter != null) {
                float[] points = faceDetector.detect(
                        i420,
                        width,
                        height,
                        stride,
                        FaceDetector.GPUPIXEL_FRAME_TYPE_YUVI420,
                        FaceDetector.GPUPIXEL_MODE_FMT_VIDEO
                );
                if (points != null && points.length > 0) {
                    setProperty(reshapeFilter, "face_landmark", points);
                    // Some GPUPixel builds use this alias.
                    setProperty(reshapeFilter, "facePoints", points);
                }
            }

            applyProperties();
            source.ProcessData(i420, width, height, stride, GPUPixelSourceRawData.FRAME_TYPE_YUVI420);

            byte[] out = sink.GetI420Buffer();
            if (out == null || out.length < width * height * 3 / 2) return null;
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "process frame failed, fallback raw", t);
            return null;
        }
    }

    public synchronized void release() {
        releaseInternal();
        initialized = false;
    }

    private void applyProperties() {
        if (beautyFilter != null) {
            setProperty(beautyFilter, "skin_smoothing", smooth);
            setProperty(beautyFilter, "whiteness", whiten);
            setProperty(beautyFilter, "whiten", whiten);
            setProperty(beautyFilter, "brightness_factor", filter * 0.22f);
            setProperty(beautyFilter, "blend_level", filter);
        }
        if (reshapeFilter != null) {
            setProperty(reshapeFilter, "thin_face", thinFace);
            setProperty(reshapeFilter, "big_eye", bigEye);
            setProperty(reshapeFilter, "thinFaceDelta", thinFace);
            setProperty(reshapeFilter, "bigEyeDelta", bigEye);
        }
    }

    private void setProperty(GPUPixelFilter f, String name, float value) {
        try { if (f != null) f.SetProperty(name, value); } catch (Throwable ignored) {}
    }

    private void setProperty(GPUPixelFilter f, String name, float[] value) {
        try { if (f != null && value != null) f.SetProperty(name, value); } catch (Throwable ignored) {}
    }

    private float clamp(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private void releaseInternal() {
        try { if (source != null) source.Destroy(); } catch (Throwable ignored) {}
        try { if (beautyFilter != null) beautyFilter.Destroy(); } catch (Throwable ignored) {}
        try { if (reshapeFilter != null) reshapeFilter.Destroy(); } catch (Throwable ignored) {}
        try { if (sink != null) sink.Destroy(); } catch (Throwable ignored) {}
        try { if (faceDetector != null) faceDetector.destroy(); } catch (Throwable ignored) {}
        source = null;
        beautyFilter = null;
        reshapeFilter = null;
        sink = null;
        faceDetector = null;
    }
}
