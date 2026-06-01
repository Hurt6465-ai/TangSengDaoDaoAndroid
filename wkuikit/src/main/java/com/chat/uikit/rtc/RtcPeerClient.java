package com.chat.uikit.rtc;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
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

    private final RtcVideoSinkProxy localProxy = new RtcVideoSinkProxy();
    private final RtcVideoSinkProxy remoteProxy = new RtcVideoSinkProxy();
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
            if (pc == null) return;
            pc.createOffer(new SdpObserverAdapter() {
                @Override public void onCreateSuccess(SessionDescription sdp) { setLocalDescription(sdp); }
                @Override public void onCreateFailure(String error) { report("创建 offer 失败: " + error, null); }
            }, offerAnswerConstraints());
        });
    }

    public void createAnswer() {
        rtcHandler.post(() -> {
            if (pc == null) return;
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

            // 悟空 IM 可能会因为离线同步、刷新消息、历史同步等原因重复投递同一条
            // offer/answer。重复 answer 在 WebRTC 中会触发：
            // Failed to set remote answer sdp: Called in wrong state: stable
            // 这里按初始通话 MVP 处理：同一通话只接受一次远端 offer 或 answer，
            // 后续重复 SDP 直接忽略，不再弹错误 Toast。
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
                    if (sdp.type == SessionDescription.Type.OFFER) {
                        remoteOfferSet = true;
                    } else if (sdp.type == SessionDescription.Type.ANSWER) {
                        remoteAnswerSet = true;
                    }
                    drainCandidates();
                    if (sdp.type == SessionDescription.Type.OFFER) createAnswer();
                }
                @Override public void onSetFailure(String error) {
                    // 状态已经变成 stable 时，基本就是重复 answer，不应该打断通话。
                    PeerConnection.SignalingState currentState = pc == null ? null : pc.signalingState();
                    if (sdp.type == SessionDescription.Type.ANSWER
                            && currentState == PeerConnection.SignalingState.STABLE) {
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
            if (pc == null || candidate == null) return;
            if (!remoteSdpSet) queuedRemoteCandidates.add(candidate);
            else pc.addIceCandidate(candidate);
        });
    }

    public void setMicEnabled(boolean enabled) { rtcHandler.post(() -> { if (localAudioTrack != null) localAudioTrack.setEnabled(enabled); }); }
    public void setCameraEnabled(boolean enabled) { rtcHandler.post(() -> { if (localVideoTrack != null) localVideoTrack.setEnabled(enabled); }); }

    public void switchCamera() {
        rtcHandler.post(() -> {
            if (videoCapturer instanceof CameraVideoCapturer) {
                ((CameraVideoCapturer) videoCapturer).switchCamera(null);
            }
        });
    }

    public void swapRenderers(boolean localFullScreen) {
        if (localFullScreen) {
            localProxy.setTarget(remoteRenderer);
            remoteProxy.setTarget(localRenderer);
        } else {
            localProxy.setTarget(localRenderer);
            remoteProxy.setTarget(remoteRenderer);
        }
    }

    public void close() { rtcHandler.post(this::closeInternal); }

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
        videoCapturer = createCameraCapturer();
        if (videoCapturer == null) throw new IllegalStateException("没有可用摄像头");
        surfaceTextureHelper = SurfaceTextureHelper.create("cp-camera-thread", eglContext);
        videoSource = factory.createVideoSource(false);
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(RtcConstants.VIDEO_WIDTH, RtcConstants.VIDEO_HEIGHT, RtcConstants.VIDEO_FPS);
        localVideoTrack = factory.createVideoTrack("CP_VIDEO", videoSource);
        localVideoTrack.setEnabled(true);
        localProxy.setTarget(localRenderer);
        localVideoTrack.addSink(localProxy);
        videoSender = pc.addTrack(localVideoTrack, Collections.singletonList(STREAM_ID));
        setVideoMaxBitrate(RtcConstants.VIDEO_MAX_BITRATE_KBPS);
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

    private void setVideoMaxBitrate(int kbps) {
        try {
            if (videoSender == null) return;
            org.webrtc.RtpParameters p = videoSender.getParameters();
            if (p == null || p.encodings == null || p.encodings.isEmpty()) return;
            p.encodings.get(0).maxBitrateBps = kbps * 1000;
            videoSender.setParameters(p);
        } catch (Exception e) { Log.w(TAG, "bitrate", e); }
    }

    private List<PeerConnection.IceServer> defaultIceServers() {
        return RtcIceServers.getDefault();
    }

    private void closeInternal() {
        if (closed) return;
        closed = true;
        try { localProxy.setTarget(null); remoteProxy.setTarget(null); } catch (Exception ignored) {}
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
            if (events == null) return;
            if (s == PeerConnection.IceConnectionState.CONNECTED || s == PeerConnection.IceConnectionState.COMPLETED) events.onIceConnected();
            if (s == PeerConnection.IceConnectionState.DISCONNECTED || s == PeerConnection.IceConnectionState.FAILED) events.onIceDisconnected();
        }
        @Override public void onIceConnectionReceivingChange(boolean b) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
        @Override public void onIceCandidate(IceCandidate c) { if (events != null) events.onIceCandidate(c); }
        @Override public void onIceCandidatesRemoved(IceCandidate[] c) {}
        @Override public void onAddStream(MediaStream s) {}
        @Override public void onRemoveStream(MediaStream s) {}
        @Override public void onDataChannel(DataChannel d) {}
        @Override public void onRenegotiationNeeded() {}
        @Override public void onAddTrack(RtpReceiver r, MediaStream[] m) {}
        @Override public void onTrack(RtpTransceiver t) {
            if (t == null || t.getReceiver() == null) return;
            MediaStreamTrack track = t.getReceiver().track();
            if (track instanceof VideoTrack) {
                remoteProxy.setTarget(remoteRenderer);
                ((VideoTrack) track).addSink(remoteProxy);
                if (events != null) events.onRemoteVideoTrack();
            }
        }
    }
}
