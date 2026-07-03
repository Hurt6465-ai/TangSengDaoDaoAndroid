package com.chat.rtc;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RtcPeerClient {
    private static final String TAG = "RtcPeerClient";
    private static final String STREAM_ID = "CP_STREAM";
    private static boolean factoryInitialized;

    public interface Events {
        void onLocalDescription(SessionDescription description);
        void onIceCandidate(IceCandidate candidate);
        void onIceConnected();
        void onIceDisconnected();
        void onRemoteVideoTrack();
        void onRenegotiationNeeded();
        void onError(String message, Throwable error);
    }

    private final Context context;
    private final EglBase.Context eglContext;
    private final Events events;
    private final HandlerThread rtcThread = new HandlerThread("cp-rtc-thread");
    private Handler rtcHandler;

    private PeerConnectionFactory factory;
    private PeerConnection pc;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private RtpSender videoSender;
    private final List<IceCandidate> queuedRemoteCandidates = new LinkedList<>();
    private boolean remoteSdpSet;
    private boolean remoteOfferSet;
    private boolean remoteAnswerSet;
    private boolean closed;
    private boolean videoCall;
    private boolean screenSharing;
    private boolean lowQuality;
    private VideoCapturer screenVideoCapturer;
    private VideoSource screenVideoSource;
    private VideoTrack screenVideoTrack;
    private SurfaceTextureHelper screenTextureHelper;

    private final RtcVideoSinkProxy localProxy = new RtcVideoSinkProxy("local");
    private final RtcVideoSinkProxy remoteProxy = new RtcVideoSinkProxy("remote");
    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;

    public RtcPeerClient(Context context, EglBase.Context eglContext, Events events) {
        this.context = context.getApplicationContext();
        this.eglContext = eglContext;
        this.events = events;
        rtcThread.start();
        rtcHandler = new Handler(rtcThread.getLooper());
    }

    public void start(boolean videoCall, List<PeerConnection.IceServer> servers, SurfaceViewRenderer local, SurfaceViewRenderer remote) {
        this.videoCall = videoCall;
        this.localRenderer = local;
        this.remoteRenderer = remote;
        rtcHandler.post(() -> startInternal(servers));
    }

    public void createOffer() {
        rtcHandler.post(() -> {
            if (pc == null || closed) return;
            pc.createOffer(new SdpObserverAdapter() {
                @Override public void onCreateSuccess(SessionDescription sdp) { setLocalDescription(sdp); }
                @Override public void onCreateFailure(String error) { report("创建 offer 失败: " + error, null); }
            }, offerAnswerConstraints());
        });
    }

    public void createAnswer() {
        rtcHandler.post(() -> {
            if (pc == null || closed) return;
            pc.createAnswer(new SdpObserverAdapter() {
                @Override public void onCreateSuccess(SessionDescription sdp) { setLocalDescription(sdp); }
                @Override public void onCreateFailure(String error) { report("创建 answer 失败: " + error, null); }
            }, offerAnswerConstraints());
        });
    }

    public void setRemoteDescription(SessionDescription sdp) {
        rtcHandler.post(() -> {
            if (pc == null || sdp == null || closed) return;
            PeerConnection.SignalingState state = pc.signalingState();
            if (sdp.type == SessionDescription.Type.ANSWER) {
                if (remoteAnswerSet || state != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    Log.w(TAG, "ignore remote answer in state=" + state + ", duplicated=" + remoteAnswerSet);
                    return;
                }
            } else if (sdp.type == SessionDescription.Type.OFFER) {
                if (remoteOfferSet || state != PeerConnection.SignalingState.STABLE) {
                    Log.w(TAG, "ignore remote offer in state=" + state + ", duplicated=" + remoteOfferSet);
                    return;
                }
            }
            pc.setRemoteDescription(new SdpObserverAdapter() {
                @Override public void onSetSuccess() {
                    remoteSdpSet = true;
                    if (sdp.type == SessionDescription.Type.OFFER) remoteOfferSet = true;
                    else if (sdp.type == SessionDescription.Type.ANSWER) remoteAnswerSet = true;
                    drainCandidates();
                    if (sdp.type == SessionDescription.Type.OFFER) createAnswer();
                }
                @Override public void onSetFailure(String error) {
                    PeerConnection.SignalingState currentState = pc == null ? null : pc.signalingState();
                    if (sdp.type == SessionDescription.Type.ANSWER && currentState == PeerConnection.SignalingState.STABLE) {
                        Log.w(TAG, "ignore duplicated remote answer failure: " + error);
                        return;
                    }
                    report("设置远端 SDP 失败: " + error, null);
                }
            }, sdp);
        });
    }

    public void addRemoteIce(IceCandidate candidate) {
        rtcHandler.post(() -> {
            if (pc == null || candidate == null || closed) return;
            if (!remoteSdpSet) queuedRemoteCandidates.add(candidate);
            else pc.addIceCandidate(candidate);
        });
    }

    public void setMicEnabled(boolean enabled) { rtcHandler.post(() -> { if (localAudioTrack != null) localAudioTrack.setEnabled(enabled); }); }
    public void setCameraEnabled(boolean enabled) { rtcHandler.post(() -> { if (localVideoTrack != null) localVideoTrack.setEnabled(enabled); }); }

    public void switchCamera() {
        rtcHandler.post(() -> {
            if (screenSharing) return;
            if (videoCapturer instanceof CameraVideoCapturer) {
                ((CameraVideoCapturer) videoCapturer).switchCamera(null);
            }
        });
    }

    public void startScreenShare(Intent permissionData) {
        if (permissionData == null) return;
        rtcHandler.post(() -> {
            if (!videoCall || closed || videoSender == null || localVideoTrack == null) return;
            if (screenSharing) return;
            VideoCapturer nextCapturer = null;
            VideoSource nextSource = null;
            VideoTrack nextTrack = null;
            SurfaceTextureHelper nextHelper = null;
            boolean senderReplaced = false;
            try {
                nextCapturer = new ScreenCapturerAndroid(permissionData, new MediaProjection.Callback() {
                    @Override public void onStop() {
                        stopScreenShare();
                    }
                });

                // Real screen sharing must replace the sender's outbound video track.
                // Reusing the camera VideoSource can keep sending camera frames on some WebRTC builds.
                nextSource = factory.createVideoSource(true);
                nextTrack = factory.createVideoTrack("CP_SCREEN_" + System.currentTimeMillis(), nextSource);
                nextTrack.setEnabled(true);
                nextHelper = SurfaceTextureHelper.create("cp-screen-thread", eglContext);
                nextCapturer.initialize(nextHelper, context, nextSource.getCapturerObserver());

                try {
                    if (videoCapturer != null) videoCapturer.stopCapture();
                    RtcDebugLogger.i("RtcPeerClient", "camera capturer stopped for screen share");
                } catch (Exception e) {
                    Log.w(TAG, "stop camera before screen share", e);
                }

                senderReplaced = videoSender.setTrack(nextTrack, false);
                if (!senderReplaced) throw new IllegalStateException("replace video sender track failed");

                screenVideoCapturer = nextCapturer;
                screenVideoSource = nextSource;
                screenVideoTrack = nextTrack;
                screenTextureHelper = nextHelper;

                int[] screenSize = resolveScreenCaptureSize();
                adaptScreenSourceOutput(screenVideoSource, screenSize[0], screenSize[1], RtcConstants.SCREEN_FPS);
                screenVideoCapturer.startCapture(screenSize[0], screenSize[1], RtcConstants.SCREEN_FPS);
                screenSharing = true;
                swapRenderers(false);
                setVideoBitrate(
                        RtcConstants.SCREEN_MIN_BITRATE_KBPS,
                        RtcConstants.SCREEN_START_BITRATE_KBPS,
                        RtcConstants.SCREEN_MAX_BITRATE_KBPS,
                        RtcConstants.SCREEN_FPS,
                        true);
                RtcDebugLogger.i("RtcPeerClient", "screen share started width=" + screenSize[0]
                        + " height=" + screenSize[1] + " fps=" + RtcConstants.SCREEN_FPS
                        + " minKbps=" + RtcConstants.SCREEN_MIN_BITRATE_KBPS
                        + " startKbps=" + RtcConstants.SCREEN_START_BITRATE_KBPS
                        + " maxKbps=" + RtcConstants.SCREEN_MAX_BITRATE_KBPS
                        + " replaceTrack=" + senderReplaced + " track=" + screenVideoTrack.id());
            } catch (Exception e) {
                try { if (senderReplaced && videoSender != null && localVideoTrack != null) videoSender.setTrack(localVideoTrack, false); } catch (Exception ignored) {}
                disposeScreenObjects(nextCapturer, nextSource, nextTrack, nextHelper);
                tryRestartCameraCapture();
                report("屏幕共享失败", e);
            }
        });
    }

    public void stopScreenShare() {
        rtcHandler.post(() -> {
            if (!videoCall || closed || !screenSharing) return;
            try {
                screenSharing = false;
                boolean restored = videoSender == null || localVideoTrack == null || videoSender.setTrack(localVideoTrack, false);
                stopAndDisposeCurrentScreenObjects();
                tryRestartCameraCapture();
                swapRenderers(false);
                if (lowQuality) setVideoBitrate(0, 0, RtcConstants.VIDEO_LOW_BITRATE_KBPS, RtcConstants.VIDEO_LOW_FPS, false);
                else setVideoBitrate(RtcConstants.VIDEO_MIN_BITRATE_KBPS, RtcConstants.VIDEO_START_BITRATE_KBPS, RtcConstants.VIDEO_MAX_BITRATE_KBPS, RtcConstants.VIDEO_FPS, false);
                RtcDebugLogger.i("RtcPeerClient", "screen share stopped cameraRestored=" + restored);
            } catch (Exception e) {
                report("恢复摄像头失败", e);
            }
        });
    }

    public void degradeForWeakNetwork() {
        rtcHandler.post(() -> {
            if (!videoCall || closed || screenSharing) return;
            lowQuality = true;
            setVideoBitrate(0, 0, RtcConstants.VIDEO_LOW_BITRATE_KBPS, RtcConstants.VIDEO_LOW_FPS, false);
            try {
                if (videoCapturer instanceof CameraVideoCapturer) {
                    ((CameraVideoCapturer) videoCapturer).changeCaptureFormat(
                            RtcConstants.VIDEO_LOW_WIDTH,
                            RtcConstants.VIDEO_LOW_HEIGHT,
                            RtcConstants.VIDEO_LOW_FPS);
                }
            } catch (Exception e) {
                Log.w(TAG, "degrade video quality", e);
            }
        });
    }

    public void restoreVideoQuality() {
        rtcHandler.post(() -> {
            if (!videoCall || closed || screenSharing) return;
            lowQuality = false;
            setVideoBitrate(RtcConstants.VIDEO_MIN_BITRATE_KBPS, RtcConstants.VIDEO_START_BITRATE_KBPS, RtcConstants.VIDEO_MAX_BITRATE_KBPS, RtcConstants.VIDEO_FPS, false);
            try {
                if (videoCapturer instanceof CameraVideoCapturer) {
                    ((CameraVideoCapturer) videoCapturer).changeCaptureFormat(
                            RtcConstants.VIDEO_WIDTH,
                            RtcConstants.VIDEO_HEIGHT,
                            RtcConstants.VIDEO_FPS);
                }
            } catch (Exception e) {
                Log.w(TAG, "restore video quality", e);
            }
        });
    }

    public void swapRenderers(boolean localFullScreen) {
        // While this side is sharing the screen, never render the screen-capture track full screen.
        // Otherwise the phone captures its own preview and creates the infinite tunnel effect.
        if (screenSharing) localFullScreen = false;
        if (localFullScreen) {
            localProxy.setTarget(remoteRenderer);
            remoteProxy.setTarget(localRenderer);
        } else {
            localProxy.setTarget(localRenderer);
            remoteProxy.setTarget(remoteRenderer);
        }
        RtcDebugLogger.i("RtcPeerClient", "swapRenderers localFullScreen=" + localFullScreen + " screenSharing=" + screenSharing);
    }

    public void close() { rtcHandler.post(this::closeInternal); }

    public void closeBlocking(long timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            rtcHandler.post(() -> {
                try {
                    closeInternal();
                } finally {
                    latch.countDown();
                }
            });
            latch.await(Math.max(200L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }

    private void startInternal(List<PeerConnection.IceServer> servers) {
        try {
            initFactory();
            createPeerConnection(servers);
            createLocalMedia();
        } catch (Exception e) {
            report("WebRTC 初始化失败", e);
        }
    }

    private void initFactory() {
        synchronized (RtcPeerClient.class) {
            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(context).setEnableInternalTracer(false).createInitializationOptions()
                );
                factoryInitialized = true;
            }
        }
        JavaAudioDeviceModule adm = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();
        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglContext, true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglContext))
                .createPeerConnectionFactory();
        adm.release();
    }

    private void createPeerConnection(List<PeerConnection.IceServer> servers) {
        if (servers == null || servers.isEmpty()) servers = defaultIceServers();
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(servers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        config.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        config.keyType = PeerConnection.KeyType.ECDSA;
        pc = factory.createPeerConnection(config, new PcObserver());
        if (pc == null) throw new IllegalStateException("PeerConnection 创建失败");
    }

    private void createLocalMedia() throws Exception {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("CP_AUDIO", audioSource);
        localAudioTrack.setEnabled(true);
        pc.addTrack(localAudioTrack, Collections.singletonList(STREAM_ID));

        if (!videoCall) return;
        videoSource = factory.createVideoSource(false);
        localVideoTrack = factory.createVideoTrack("CP_VIDEO", videoSource);
        localVideoTrack.setEnabled(true);
        localProxy.setTarget(localRenderer);
        localVideoTrack.addSink(localProxy);
        videoSender = pc.addTrack(localVideoTrack, Collections.singletonList(STREAM_ID));

        videoCapturer = createCameraCapturer();
        if (videoCapturer == null) throw new IllegalStateException("没有可用摄像头");
        surfaceTextureHelper = SurfaceTextureHelper.create("cp-video-thread", eglContext);
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(RtcConstants.VIDEO_WIDTH, RtcConstants.VIDEO_HEIGHT, RtcConstants.VIDEO_FPS);
        setVideoBitrate(RtcConstants.VIDEO_MIN_BITRATE_KBPS, RtcConstants.VIDEO_START_BITRATE_KBPS, RtcConstants.VIDEO_MAX_BITRATE_KBPS, RtcConstants.VIDEO_FPS, false);
    }

    private void tryRestartCameraCapture() {
        try {
            if (videoCapturer != null) {
                videoCapturer.startCapture(
                        lowQuality ? RtcConstants.VIDEO_LOW_WIDTH : RtcConstants.VIDEO_WIDTH,
                        lowQuality ? RtcConstants.VIDEO_LOW_HEIGHT : RtcConstants.VIDEO_HEIGHT,
                        lowQuality ? RtcConstants.VIDEO_LOW_FPS : RtcConstants.VIDEO_FPS);
                RtcDebugLogger.i("RtcPeerClient", "camera capturer restarted after screen share");
            }
        } catch (Exception e) {
            Log.w(TAG, "restart camera after screen share", e);
        }
    }

    private void stopAndDisposeCurrentScreenObjects() {
        VideoCapturer c = screenVideoCapturer;
        VideoSource source = screenVideoSource;
        VideoTrack track = screenVideoTrack;
        SurfaceTextureHelper helper = screenTextureHelper;
        screenVideoCapturer = null;
        screenVideoSource = null;
        screenVideoTrack = null;
        screenTextureHelper = null;
        disposeScreenObjects(c, source, track, helper);
    }

    private void disposeScreenObjects(VideoCapturer c, VideoSource source, VideoTrack track, SurfaceTextureHelper helper) {
        try { if (c != null) c.stopCapture(); } catch (Exception ignored) {}
        try { if (c != null) c.dispose(); } catch (Exception ignored) {}
        try { if (track != null) track.dispose(); } catch (Exception ignored) {}
        try { if (source != null) source.dispose(); } catch (Exception ignored) {}
        try { if (helper != null) helper.dispose(); } catch (Exception ignored) {}
    }

    private void switchVideoCapturer(VideoCapturer next, int width, int height, int fps, boolean nextIsScreen) throws Exception {
        if (next == null || videoSource == null) return;
        try { if (videoCapturer != null) videoCapturer.stopCapture(); } catch (Exception ignored) {}
        try { if (videoCapturer != null) videoCapturer.dispose(); } catch (Exception ignored) {}
        try { if (surfaceTextureHelper != null) surfaceTextureHelper.dispose(); } catch (Exception ignored) {}
        videoCapturer = next;
        surfaceTextureHelper = SurfaceTextureHelper.create(nextIsScreen ? "cp-screen-thread" : "cp-video-thread", eglContext);
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(width, height, fps);
    }

    private VideoCapturer createCameraCapturer() {
        CameraEnumerator e = Camera2Enumerator.isSupported(context) ? new Camera2Enumerator(context) : new Camera1Enumerator(true);
        for (String name : e.getDeviceNames()) if (e.isFrontFacing(name)) { VideoCapturer c = e.createCapturer(name, null); if (c != null) return c; }
        for (String name : e.getDeviceNames()) { VideoCapturer c = e.createCapturer(name, null); if (c != null) return c; }
        return null;
    }

    private void setLocalDescription(SessionDescription sdp) {
        pc.setLocalDescription(new SdpObserverAdapter() {
            @Override public void onSetSuccess() { if (events != null) events.onLocalDescription(sdp); }
            @Override public void onSetFailure(String error) { report("设置本地 SDP 失败: " + error, null); }
        }, sdp);
    }

    private MediaConstraints offerAnswerConstraints() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", videoCall ? "true" : "false"));
        return c;
    }

    private void drainCandidates() {
        for (IceCandidate c : new ArrayList<>(queuedRemoteCandidates)) pc.addIceCandidate(c);
        queuedRemoteCandidates.clear();
    }

    private void setVideoBitrate(int minKbps, int startKbps, int maxKbps, Integer maxFps, boolean screenContent) {
        try {
            if (videoSender == null) return;
            org.webrtc.RtpParameters parameters = videoSender.getParameters();
            if (parameters == null || parameters.encodings == null || parameters.encodings.isEmpty()) return;
            for (org.webrtc.RtpParameters.Encoding encoding : parameters.encodings) {
                encoding.active = true;
                if (minKbps > 0) encoding.minBitrateBps = minKbps * 1000;
                if (maxKbps > 0) encoding.maxBitrateBps = maxKbps * 1000;
                if (maxFps != null && maxFps > 0) encoding.maxFramerate = maxFps;
                try { encoding.scaleResolutionDownBy = 1.0; } catch (Throwable ignored) {}
                try { encoding.bitratePriority = screenContent ? 4.0 : 2.0; } catch (Throwable ignored) {}
                try { encoding.networkPriority = screenContent ? org.webrtc.Priority.HIGH : org.webrtc.Priority.MEDIUM; } catch (Throwable ignored) {}
                try { if (screenContent) encoding.numTemporalLayers = 1; } catch (Throwable ignored) {}
            }
            try {
                parameters.degradationPreference = screenContent
                        ? org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
                        : org.webrtc.RtpParameters.DegradationPreference.BALANCED;
            } catch (Throwable ignored) {}
            boolean ok = videoSender.setParameters(parameters);
            RtcDebugLogger.i("RtcPeerClient", "set video bitrate ok=" + ok
                    + " min=" + minKbps + " start=" + startKbps + " max=" + maxKbps
                    + " fps=" + maxFps + " screen=" + screenContent);
        } catch (Exception e) { Log.w(TAG, "bitrate", e); }
    }

    private void adaptScreenSourceOutput(VideoSource source, int width, int height, int fps) {
        if (source == null) return;
        try {
            java.lang.reflect.Method method = source.getClass().getMethod(
                    "adaptOutputFormat", int.class, int.class, int.class);
            method.invoke(source, width, height, fps);
            RtcDebugLogger.i("RtcPeerClient", "screen source adapted width=" + width + " height=" + height + " fps=" + fps);
        } catch (Throwable ignored) {
            // Some WebRTC Android builds do not expose VideoSource.adaptOutputFormat.
            // ScreenCapturerAndroid.startCapture(width,height,fps) still applies the same target size.
        }
    }

    private int[] resolveScreenCaptureSize() {
        int width = RtcConstants.SCREEN_WIDTH;
        int height = RtcConstants.SCREEN_HEIGHT;
        try {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            if (metrics != null && metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                width = metrics.widthPixels;
                height = metrics.heightPixels;
            }
        } catch (Exception ignored) {}
        int longEdge = Math.max(width, height);
        int shortEdge = Math.min(width, height);
        float scale = 1.0f;
        if (longEdge > RtcConstants.SCREEN_MAX_LONG_EDGE) {
            scale = Math.min(scale, (float) RtcConstants.SCREEN_MAX_LONG_EDGE / (float) longEdge);
        }
        if (shortEdge > RtcConstants.SCREEN_MAX_SHORT_EDGE) {
            scale = Math.min(scale, (float) RtcConstants.SCREEN_MAX_SHORT_EDGE / (float) shortEdge);
        }
        int outW = makeEven(Math.round(width * scale));
        int outH = makeEven(Math.round(height * scale));
        outW = Math.max(2, outW);
        outH = Math.max(2, outH);
        return new int[]{outW, outH};
    }

    private int makeEven(int value) {
        return value % 2 == 0 ? value : value - 1;
    }

    private List<PeerConnection.IceServer> defaultIceServers() { return RtcIceServers.getDefault(); }

    private void closeInternal() {
        if (closed) return;
        closed = true;
        try { localProxy.setTarget(null); remoteProxy.setTarget(null); } catch (Exception ignored) {}
        try { stopAndDisposeCurrentScreenObjects(); } catch (Exception ignored) {}
        try { if (videoCapturer != null) videoCapturer.stopCapture(); } catch (Exception ignored) {}
        try { if (videoCapturer != null) videoCapturer.dispose(); } catch (Exception ignored) {}
        try { if (surfaceTextureHelper != null) surfaceTextureHelper.dispose(); } catch (Exception ignored) {}
        try { if (localVideoTrack != null) localVideoTrack.dispose(); } catch (Exception ignored) {}
        try { if (localAudioTrack != null) localAudioTrack.dispose(); } catch (Exception ignored) {}
        try { if (videoSource != null) videoSource.dispose(); } catch (Exception ignored) {}
        try { if (audioSource != null) audioSource.dispose(); } catch (Exception ignored) {}
        try { if (pc != null) { pc.close(); pc.dispose(); } } catch (Exception ignored) {}
        try { if (factory != null) factory.dispose(); } catch (Exception ignored) {}
        rtcThread.quitSafely();
    }

    private void report(String msg, Throwable t) { Log.e(TAG, msg, t); if (events != null) events.onError(msg, t); }

    private class PcObserver implements PeerConnection.Observer {
        @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
            if (s == PeerConnection.IceConnectionState.CONNECTED || s == PeerConnection.IceConnectionState.COMPLETED) {
                if (events != null) events.onIceConnected();
            }
            if (s == PeerConnection.IceConnectionState.DISCONNECTED || s == PeerConnection.IceConnectionState.FAILED) {
                try {
                    if (pc != null && s == PeerConnection.IceConnectionState.FAILED) {
                        pc.restartIce();
                        if (events != null) events.onRenegotiationNeeded();
                    }
                } catch (Exception ignored) {}
                if (events != null) events.onIceDisconnected();
            }
        }
        @Override public void onIceConnectionReceivingChange(boolean b) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
        @Override public void onIceCandidate(IceCandidate c) { if (events != null) events.onIceCandidate(c); }
        @Override public void onIceCandidatesRemoved(IceCandidate[] c) {}
        @Override public void onAddStream(MediaStream s) {}
        @Override public void onRemoveStream(MediaStream s) {}
        @Override public void onDataChannel(DataChannel d) {}
        @Override public void onRenegotiationNeeded() { if (events != null && remoteSdpSet) events.onRenegotiationNeeded(); }
        @Override public void onAddTrack(RtpReceiver r, MediaStream[] m) {}
        @Override public void onTrack(RtpTransceiver t) {
            if (t == null || t.getReceiver() == null) return;
            MediaStreamTrack track = t.getReceiver().track();
            if (track instanceof VideoTrack) {
                RtcDebugLogger.i("RtcPeerClient", "remote video track received id=" + track.id());
                remoteProxy.setTarget(remoteRenderer);
                ((VideoTrack) track).addSink(remoteProxy);
                if (events != null) events.onRemoteVideoTrack();
            }
        }
    }
}
