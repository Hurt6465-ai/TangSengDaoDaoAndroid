package com.chat.rtc;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.chat.rtc.R;
import com.chat.rtc.model.RtcSignal;
import com.chat.base.config.WKApiConfig;
import com.chat.base.glide.GlideUtils;
import com.mikepenz.iconics.IconicsDrawable;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RtcCallActivity extends Activity implements RtcPeerClient.Events, RtcCallManager.ActiveCallListener {
    private static final int REQ_PERMISSIONS = 7001;
    private static final int REQ_SCREEN_CAPTURE = 7002;
    private static final long CONTROLS_AUTO_HIDE_MS = 2000L;
    private static final long INVITE_RETRY_INTERVAL_MS = 3500L;
    private static final int MAX_INVITE_RETRY_COUNT = 4;

    private String callId;
    private String peerUid;
    private String peerName;
    private String peerAvatar;
    private int callType;
    private boolean incoming;
    private boolean autoAccept;
    private boolean accepted;
    // For outgoing calls this means the callee has accepted (or answered SDP).
    // It is intentionally separate from local accepted to distinguish no-answer from connect-failed.
    private boolean peerAccepted;
    private boolean peerRinging;
    private int inviteRetryCount;
    private boolean connected;
    private boolean ending;
    private boolean micOn = true;
    private boolean cameraOn = true;
    private boolean localFullScreen;
    private boolean controlsVisible = true;
    private boolean weakMode;
    private boolean screenSharing;
    private boolean foregroundServiceStarted;
    private boolean recordReported;
    private String closeReason = "";

    private EglBase eglBase;
    private RtcPeerClient peerClient;
    private RtcAudioManager audioManager;
    private RtcRingPlayer ringPlayer;
    private Runnable pendingPermissionAction;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<RtcSignal> localPending = new ArrayList<>();
    private final ExecutorService avatarExecutor = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private SurfaceViewRenderer remoteRenderer;
    private SurfaceViewRenderer localRenderer;
    private FrameLayout localContainer;
    private LinearLayout topInfo;
    private ImageView avatarImage;
    private TextView avatarText;
    private TextView nameText;
    private TextView statusText;
    private LinearLayout controlsLayout;
    private LinearLayout sideControlsLayout;
    private LinearLayout incomingLayout;
    private TextView micBtn;
    private TextView speakerBtn;
    private TextView endBtn;
    private TextView camBtn;
    private TextView flipBtn;
    private TextView screenBtn;
    private long connectedAt;

    private final Runnable timerRunnable = new Runnable() {
        @Override public void run() {
            if (!connected || ending) return;
            long seconds = (System.currentTimeMillis() - connectedAt) / 1000L;
            statusText.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable autoHideRunnable = new Runnable() {
        @Override public void run() {
            if (connected && RtcConstants.isVideo(callType) && !ending) setControlsVisible(false);
        }
    };

    private final Runnable inviteRetryRunnable = new Runnable() {
        @Override public void run() {
            if (incoming || ending || connected || peerAccepted || peerRinging) return;
            if (inviteRetryCount >= MAX_INVITE_RETRY_COUNT) return;
            inviteRetryCount++;
            try {
                RtcSignalManager.get().sendInvite(callId, peerUid, peerName, peerAvatar, callType);
            } catch (Exception ignored) {
                return;
            }
            handler.postDelayed(this, INVITE_RETRY_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        RtcDebugLogger.init(this);
        RtcConfigManager.refreshAsync();
        readIntent();
        RtcDebugLogger.i("RtcCallActivity", "onCreate incoming=" + incoming + " autoAccept=" + autoAccept
                + " callId=" + callId + " peer=" + RtcDebugLogger.shortUid(peerUid) + " type=" + callType);
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        RtcCallNotification.cancelIncoming(this);
        eglBase = EglBase.create();
        audioManager = new RtcAudioManager(this);
        ringPlayer = new RtcRingPlayer(this);
        setContentView(R.layout.wkrtc_act_rtc_call);
        bindViews();
        RtcCallManager.get().setActiveCallListener(this);
        RtcCallManager.get().markActivityVisible(callId);
        if (incoming) {
            showIncoming();
            scheduleIncomingTimeout();
            if (autoAccept) {
                handler.postDelayed(this::acceptIncoming, 120);
            }
        } else {
            showOutgoing();
            ensurePermissionsThen(this::startOutgoingFlow);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(RtcConstants.EXTRA_AUTO_ACCEPT, false) && incoming && !accepted) {
            acceptIncoming();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        RtcCallManager.get().clearActiveCallListener(this);
        cleanup();
    }

    @Override public void onBackPressed() { endCall(false); }
    @Override public String getActiveCallId() { return callId; }
    @Override public void onSignalForActiveCall(RtcSignal signal) {
        RtcDebugLogger.i("RtcCallActivity", "onSignalForActiveCall " + RtcDebugLogger.signal(signal));
        runOnUiThread(() -> handleSignal(signal));
    }

    @Override public void onLocalDescription(SessionDescription description) {
        try { RtcSignalManager.get().sendDescription(callId, peerUid, description, callType); }
        catch (Exception e) { runOnUiThread(() -> toast(getString(R.string.rtc_send_sdp_failed))); }
    }

    @Override public void onIceCandidate(IceCandidate candidate) {
        try { RtcSignalManager.get().sendIce(callId, peerUid, candidate, callType); }
        catch (Exception e) { runOnUiThread(() -> toast(getString(R.string.rtc_send_ice_failed))); }
    }

    @Override public void onIceConnected() { runOnUiThread(this::markConnected); }

    @Override public void onIceDisconnected() {
        runOnUiThread(() -> {
            if (ending) return;
            weakMode = true;
            if (peerClient != null) peerClient.degradeForWeakNetwork();
            statusText.setText(getString(R.string.rtc_network_weak_recovering));
            showCallControls();
        });
    }

    @Override public void onRemoteVideoTrack() { runOnUiThread(() -> { if (RtcConstants.isVideo(callType)) hideAvatar(); }); }

    @Override public void onRenegotiationNeeded() {
        runOnUiThread(() -> {
            if (!ending && accepted && peerClient != null) {
                peerClient.createOffer();
            }
        });
    }

    @Override public void onError(String message, Throwable error) {
        runOnUiThread(() -> {
            if (ending) return;
            toast(message == null ? getString(R.string.rtc_call_error) : message);
            closeReason = "connect_failed";
            endCall(false);
        });
    }

    private void readIntent() {
        RtcDebugLogger.i("RtcCallActivity", "readIntent action=" + (getIntent() == null ? "" : getIntent().getAction()));
        callId = getIntent().getStringExtra(RtcConstants.EXTRA_CALL_ID);
        peerUid = getIntent().getStringExtra(RtcConstants.EXTRA_PEER_UID);
        peerName = getIntent().getStringExtra(RtcConstants.EXTRA_PEER_NAME);
        peerAvatar = getIntent().getStringExtra(RtcConstants.EXTRA_PEER_AVATAR);
        callType = getIntent().getIntExtra(RtcConstants.EXTRA_CALL_TYPE, RtcConstants.AUDIO);
        incoming = getIntent().getBooleanExtra(RtcConstants.EXTRA_INCOMING, false);
        autoAccept = getIntent().getBooleanExtra(RtcConstants.EXTRA_AUTO_ACCEPT, false);
        if (TextUtils.isEmpty(callId)) callId = RtcCallManager.get().createCallId();
        if (TextUtils.isEmpty(peerName)) peerName = getString(R.string.rtc_friend);
        RtcDebugLogger.i("RtcCallActivity", "intent parsed incoming=" + incoming + " autoAccept=" + autoAccept
                + " callId=" + callId + " peer=" + RtcDebugLogger.shortUid(peerUid) + " name=" + peerName);
    }

    private void bindViews() {
        root = findViewById(R.id.rtcRoot);
        remoteRenderer = findViewById(R.id.remoteRenderer);
        localRenderer = findViewById(R.id.localRenderer);
        localContainer = findViewById(R.id.localContainer);
        topInfo = findViewById(R.id.topInfo);
        avatarImage = findViewById(R.id.avatarImage);
        avatarText = findViewById(R.id.avatarText);
        nameText = findViewById(R.id.nameText);
        statusText = findViewById(R.id.statusText);
        controlsLayout = findViewById(R.id.controlsLayout);
        sideControlsLayout = findViewById(R.id.sideControlsLayout);
        incomingLayout = findViewById(R.id.incomingLayout);
        micBtn = findViewById(R.id.micBtn);
        speakerBtn = findViewById(R.id.speakerBtn);
        endBtn = findViewById(R.id.endBtn);
        camBtn = findViewById(R.id.camBtn);
        flipBtn = findViewById(R.id.flipBtn);
        screenBtn = findViewById(R.id.screenBtn);

        avatarText.setText(initial(peerName));
        avatarText.setBackground(circle(0xff334155));
        avatarImage.setBackground(circle(0xff334155));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) avatarImage.setClipToOutline(true);
        nameText.setText(peerName);
        root.setBackground(gradient(0xff101827, 0xff162033, 0xff020617));
        // SurfaceViewRenderer cannot be clipped reliably on all Android versions.
        // Keep the PiP container transparent to avoid the black outer rectangle.
        localContainer.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) localContainer.setClipToOutline(false);
        findViewById(R.id.controlsCard).setBackgroundColor(Color.TRANSPARENT);

        loadAvatar(peerAvatar);

        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        remoteRenderer.setEnableHardwareScaler(true);
        remoteRenderer.setMirror(false);
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        localRenderer.setEnableHardwareScaler(true);
        localRenderer.setMirror(true);

        styleCallButton(micBtn, "faw-microphone", getString(R.string.rtc_mute), false);
        styleCallButton(speakerBtn, "faw-volume-up", getString(R.string.rtc_speaker), false);
        styleCallButton(endBtn, "faw-phone", getString(R.string.rtc_hangup), true);
        styleCallButton(camBtn, "faw-video", getString(R.string.rtc_camera), false);
        styleCallButton(flipBtn, "faw-sync-alt", getString(R.string.rtc_flip), false);
        styleCallButton(screenBtn, "faw-desktop", getString(R.string.rtc_screen_share), false);
        styleIncoming(findViewById(R.id.rejectBtn), 0x33ffffff);
        styleIncoming(findViewById(R.id.acceptBtn), 0xff22c55e);

        boolean video = RtcConstants.isVideo(callType);
        remoteRenderer.setVisibility(video ? View.VISIBLE : View.GONE);
        localContainer.setVisibility(video ? View.VISIBLE : View.GONE);
        sideControlsLayout.setVisibility(View.GONE);
        camBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        flipBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        screenBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        enableLocalDragAndSwap();
        root.setOnClickListener(v -> { if (connected && video) toggleControlsVisible(); });

        micBtn.setOnClickListener(v -> { toggleMic(); keepControlsVisible(); });
        speakerBtn.setOnClickListener(v -> { toggleSpeaker(); keepControlsVisible(); });
        endBtn.setOnClickListener(v -> endCall(false));
        camBtn.setOnClickListener(v -> { toggleCamera(); keepControlsVisible(); });
        flipBtn.setOnClickListener(v -> { if (peerClient != null && !screenSharing) peerClient.switchCamera(); keepControlsVisible(); });
        screenBtn.setOnClickListener(v -> { toggleScreenShare(); keepControlsVisible(); });
        findViewById(R.id.rejectBtn).setOnClickListener(v -> rejectIncoming());
        findViewById(R.id.acceptBtn).setOnClickListener(v -> acceptIncoming());
    }

    private void showIncoming() {
        RtcDebugLogger.i("RtcCallActivity", "showIncoming callId=" + callId
                + " peer=" + RtcDebugLogger.shortUid(peerUid) + " type=" + callType);
        controlsLayout.setVisibility(View.GONE);
        sideControlsLayout.setVisibility(View.GONE);
        camBtn.setVisibility(View.GONE);
        flipBtn.setVisibility(View.GONE);
        screenBtn.setVisibility(View.GONE);
        incomingLayout.setVisibility(View.VISIBLE);
        statusText.setText(RtcConstants.isVideo(callType) ? getString(R.string.rtc_invite_video) : getString(R.string.rtc_invite_audio));
        ringPlayer.playIncoming();
    }

    private void showOutgoing() {
        incomingLayout.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.VISIBLE);
        sideControlsLayout.setVisibility(View.GONE);
        camBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
        flipBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
        screenBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
        statusText.setText(RtcConstants.isVideo(callType) ? getString(R.string.rtc_prepare_video) : getString(R.string.rtc_prepare_audio));
    }

    private void scheduleIncomingTimeout() {
        handler.postDelayed(() -> {
            if (incoming && !accepted && !ending) {
                timeoutIncoming();
            }
        }, RtcConfigManager.getCallTimeoutMs() + 5_000L);
    }

    private void scheduleOutgoingInviteTimeout() {
        handler.postDelayed(() -> {
            if (!incoming && !peerAccepted && !connected && !ending) {
                closeReason = "no_answer";
                toast(getString(R.string.rtc_no_answer));
                endCall(false);
            }
        }, RtcConfigManager.getInviteTimeoutMs());
    }

    private void scheduleConnectTimeout() {
        handler.postDelayed(() -> {
            if (!connected && !ending && (peerAccepted || accepted)) {
                closeReason = "connect_failed";
                toast(getString(R.string.rtc_connect_failed));
                endCall(false);
            }
        }, RtcConfigManager.getConnectTimeoutMs());
    }

    private void startOutgoingFlow() {
        accepted = true;
        RtcDebugLogger.i("RtcCallActivity", "startOutgoingFlow callId=" + callId
                + " peer=" + RtcDebugLogger.shortUid(peerUid) + " type=" + callType);
        startWebRtc();
        try {
            inviteRetryCount = 0;
            peerRinging = false;
            RtcSignalManager.get().sendInvite(callId, peerUid, peerName, peerAvatar, callType);
            RtcDebugLogger.i("RtcCallActivity", "send INVITE ok callId=" + callId
                    + " peer=" + RtcDebugLogger.shortUid(peerUid));
            statusText.setText(RtcConstants.isVideo(callType) ? getString(R.string.rtc_wait_accept_video) : getString(R.string.rtc_calling_audio));
            ringPlayer.playOutgoing();
            peerClient.createOffer();
            handler.postDelayed(inviteRetryRunnable, INVITE_RETRY_INTERVAL_MS);
            scheduleOutgoingInviteTimeout();
        } catch (Exception e) {
            RtcDebugLogger.e("RtcCallActivity", "send INVITE failed callId=" + callId
                    + " peer=" + RtcDebugLogger.shortUid(peerUid), e);
            closeReason = "connect_failed";
            toast(getString(R.string.rtc_signal_not_ready));
            endCall(false);
        }
    }

    private void acceptIncoming() {
        RtcDebugLogger.i("RtcCallActivity", "acceptIncoming callId=" + callId + " peer=" + RtcDebugLogger.shortUid(peerUid));
        if (accepted) return;
        accepted = true;
        peerAccepted = true;
        ringPlayer.stop();
        RtcCallNotification.cancelIncoming(this);
        incomingLayout.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.VISIBLE);
        sideControlsLayout.setVisibility(View.GONE);
        camBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
        flipBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
        screenBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
        ensurePermissionsThen(() -> {
            try { RtcSignalManager.get().sendSimple(RtcSignal.ACCEPT, callId, peerUid, callType); } catch (Exception ignored) {}
            statusText.setText(getString(R.string.rtc_connecting));
            scheduleConnectTimeout();
            startWebRtc();
            consumePending();
        });
    }

    private void timeoutIncoming() {
        RtcDebugLogger.w("RtcCallActivity", "timeoutIncoming callId=" + callId + " peer=" + RtcDebugLogger.shortUid(peerUid));
        ringPlayer.stop();
        RtcCallNotification.cancelIncoming(this);
        closeReason = "missed";
        try { RtcSignalManager.get().sendSimple(RtcSignal.TIMEOUT, callId, peerUid, callType); } catch (Exception ignored) {}
        reportCallRecordIfNeeded(closeReason);
        RtcCallManager.get().markClosed(callId);
        RtcCallForegroundService.stop(this);
        finish();
    }

    private void rejectIncoming() {
        RtcDebugLogger.i("RtcCallActivity", "rejectIncoming callId=" + callId + " peer=" + RtcDebugLogger.shortUid(peerUid));
        ringPlayer.stop();
        RtcCallNotification.cancelIncoming(this);
        if (TextUtils.isEmpty(closeReason)) closeReason = "rejected";
        try { RtcSignalManager.get().sendSimple(RtcSignal.REJECT, callId, peerUid, callType); } catch (Exception ignored) {}
        reportCallRecordIfNeeded(closeReason);
        RtcCallManager.get().markClosed(callId);
        RtcCallNotification.cancelIncoming(this);
        RtcCallForegroundService.stop(this);
        finish();
    }

    private void startWebRtc() {
        startForegroundServiceIfNeeded();
        audioManager.start(RtcConstants.isVideo(callType));
        updateSpeakerButton(audioManager.isSpeakerOn());
        peerClient = new RtcPeerClient(this, eglBase.getEglBaseContext(), this);
        peerClient.start(RtcConstants.isVideo(callType), defaultIceServers(), localRenderer, remoteRenderer);
        peerClient.swapRenderers(false);
        updateScreenShareButton(false);
    }

    private void startForegroundServiceIfNeeded() {
        if (foregroundServiceStarted) return;
        RtcCallForegroundService.start(this, callId, peerName, callType);
        foregroundServiceStarted = true;
    }

    private void handleSignal(RtcSignal s) {
        if (s == null || ending || !TextUtils.equals(s.callId, callId)) return;
        if (RtcSignal.CANCEL.equals(s.type) || RtcSignal.END.equals(s.type)) {
            closeReason = connected ? "remote_ended" : "remote_cancelled";
            toast(getString(R.string.rtc_call_ended));
            endCall(true);
            return;
        }
        if (RtcSignal.REJECT.equals(s.type)) {
            closeReason = "rejected";
            toast(getString(R.string.rtc_rejected));
            endCall(true);
            return;
        }
        if (RtcSignal.BUSY.equals(s.type)) {
            closeReason = "busy";
            toast(getString(R.string.rtc_busy));
            endCall(true);
            return;
        }
        if (RtcSignal.TIMEOUT.equals(s.type)) {
            closeReason = "no_answer";
            toast(getString(R.string.rtc_no_answer));
            endCall(true);
            return;
        }
        if (RtcSignal.RINGING.equals(s.type)) {
            peerRinging = true;
            handler.removeCallbacks(inviteRetryRunnable);
            statusText.setText(getString(R.string.rtc_ringing));
            return;
        }
        if (RtcSignal.ACCEPT.equals(s.type)) {
            peerAccepted = true;
            handler.removeCallbacks(inviteRetryRunnable);
            ringPlayer.stop();
            statusText.setText(getString(R.string.rtc_peer_accepted));
            scheduleConnectTimeout();
            return;
        }
        if (peerClient == null || (incoming && !accepted)) { localPending.add(s); return; }
        handlePeerSignal(s);
    }

    private void handlePeerSignal(RtcSignal s) {
        if (RtcSignal.OFFER.equals(s.type)) {
            peerAccepted = true;
            scheduleConnectTimeout();
            peerClient.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, s.sdp));
        } else if (RtcSignal.ANSWER.equals(s.type)) {
            peerAccepted = true;
            scheduleConnectTimeout();
            peerClient.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, s.sdp));
        } else if (RtcSignal.ICE.equals(s.type)) {
            peerClient.addRemoteIce(new IceCandidate(s.sdpMid, s.sdpMLineIndex, s.candidate));
        }
    }

    private void consumePending() {
        for (RtcSignal s : RtcCallManager.get().consumePending(callId)) handleSignal(s);
        for (RtcSignal s : new ArrayList<>(localPending)) handleSignal(s);
        localPending.clear();
    }

    private void markConnected() {
        if (connected) {
            if (weakMode) {
                weakMode = false;
                if (peerClient != null) peerClient.restoreVideoQuality();
            }
            return;
        }
        ringPlayer.stop();
        peerAccepted = true;
        connected = true;
        weakMode = false;
        connectedAt = System.currentTimeMillis();
        statusText.setText("00:00");
        if (RtcConstants.isVideo(callType)) {
            hideAvatar();
            screenBtn.setVisibility(View.VISIBLE);
        }
        handler.post(timerRunnable);
        scheduleControlsHide();
    }

    private void toggleMic() {
        micOn = !micOn;
        if (peerClient != null) peerClient.setMicEnabled(micOn);
        styleCallButton(micBtn, micOn ? "faw-microphone" : "faw-microphone-slash",
                micOn ? getString(R.string.rtc_mute) : getString(R.string.rtc_muted), !micOn);
    }

    private void toggleSpeaker() { updateSpeakerButton(audioManager.toggleSpeaker()); }

    private void updateSpeakerButton(boolean on) {
        styleCallButton(speakerBtn, on ? "faw-volume-up" : "faw-assistive-listening-systems",
                on ? getString(R.string.rtc_speaker) : getString(R.string.rtc_earpiece), false);
    }

    private void toggleCamera() {
        if (screenSharing) {
            toast(getString(R.string.rtc_screen_sharing));
            return;
        }
        cameraOn = !cameraOn;
        if (peerClient != null) peerClient.setCameraEnabled(cameraOn);
        styleCallButton(camBtn, cameraOn ? "faw-video" : "faw-video-slash",
                cameraOn ? getString(R.string.rtc_camera) : getString(R.string.rtc_camera_off), false);
        localContainer.setVisibility(cameraOn ? View.VISIBLE : View.INVISIBLE);
    }

    private void applyScreenShareUi(boolean sharing) {
        if (!RtcConstants.isVideo(callType)) return;
        if (sharing) {
            // Never put the local screen-capture preview full screen.
            // Capturing the call page while showing the capture preview creates the tunnel/mirror effect.
            localFullScreen = false;
            if (peerClient != null) peerClient.swapRenderers(false);
            localContainer.setEnabled(false);
            localContainer.setClickable(false);
            localContainer.setVisibility(View.INVISIBLE);
            localRenderer.setMirror(false);
            flipBtn.setVisibility(View.GONE);
            camBtn.setEnabled(false);
            statusText.setText(getString(R.string.rtc_screen_sharing));
            RtcDebugLogger.i("RtcCallActivity", "screen share ui sharing=true remoteMain=true localHidden=true callId=" + callId);
        } else {
            localFullScreen = false;
            if (peerClient != null) peerClient.swapRenderers(false);
            localContainer.setEnabled(true);
            localContainer.setClickable(true);
            localContainer.setVisibility(cameraOn ? View.VISIBLE : View.INVISIBLE);
            localRenderer.setMirror(true);
            flipBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
            camBtn.setEnabled(true);
            RtcDebugLogger.i("RtcCallActivity", "screen share ui sharing=false remoteMain=true localVisible=" + cameraOn + " callId=" + callId);
        }
        updateScreenShareButton(sharing);
    }

    private void toggleScreenShare() {
        if (!RtcConstants.isVideo(callType) || peerClient == null || !connected) {
            toast(getString(R.string.rtc_connecting));
            return;
        }
        if (screenSharing) {
            stopScreenShareInternal();
            return;
        }
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                toast(getString(R.string.rtc_screen_share_failed));
                return;
            }
            RtcDebugLogger.i("RtcCallActivity", "request screen share callId=" + callId);
            startActivityForResult(manager.createScreenCaptureIntent(), REQ_SCREEN_CAPTURE);
        } catch (Exception e) {
            RtcDebugLogger.e("RtcCallActivity", "request screen share failed", e);
            toast(getString(R.string.rtc_screen_share_failed));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SCREEN_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            RtcDebugLogger.w("RtcCallActivity", "screen share cancelled callId=" + callId);
            toast(getString(R.string.rtc_screen_share_cancelled));
            updateScreenShareButton(false);
            return;
        }
        startScreenShareInternal(data);
    }

    private void startScreenShareInternal(Intent data) {
        if (peerClient == null || data == null || ending) return;
        try {
            screenSharing = true;
            applyScreenShareUi(true);
            RtcCallForegroundService.startScreenShare(this, callId, peerName, callType);
            handler.postDelayed(() -> {
                if (!ending && peerClient != null && screenSharing) {
                    peerClient.startScreenShare(data);
                    toast(getString(R.string.rtc_screen_share_started));
                    RtcDebugLogger.i("RtcCallActivity", "screen share start requested callId=" + callId);
                    handler.postDelayed(this::goHomeForRealScreenShare, 650L);
                }
            }, 350L);
        } catch (Exception e) {
            screenSharing = false;
            applyScreenShareUi(false);
            RtcDebugLogger.e("RtcCallActivity", "start screen share failed", e);
            toast(getString(R.string.rtc_screen_share_failed));
        }
    }

    private void goHomeForRealScreenShare() {
        if (ending || !screenSharing) return;
        try {
            RtcDebugLogger.i("RtcCallActivity", "screen share move to launcher so captured content is real phone screen callId=" + callId);
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Exception e) {
            RtcDebugLogger.w("RtcCallActivity", "screen share move home failed, moveTaskToBack fallback callId=" + callId);
            try {
                moveTaskToBack(true);
            } catch (Exception ignored) {}
        }
    }

    private void stopScreenShareInternal() {
        if (peerClient == null) return;
        try {
            screenSharing = false;
            peerClient.stopScreenShare();
            RtcCallForegroundService.start(this, callId, peerName, callType);
            applyScreenShareUi(false);
            toast(getString(R.string.rtc_screen_share_stopped));
            RtcDebugLogger.i("RtcCallActivity", "screen share stop requested callId=" + callId);
        } catch (Exception e) {
            RtcDebugLogger.e("RtcCallActivity", "stop screen share failed", e);
        }
    }

    private void updateScreenShareButton(boolean sharing) {
        if (screenBtn == null) return;
        styleCallButton(screenBtn, "faw-desktop", sharing ? getString(R.string.rtc_screen_sharing) : getString(R.string.rtc_screen_share), sharing);
    }

    private void endCall(boolean remoteEnded) {
        RtcDebugLogger.i("RtcCallActivity", "endCall remote=" + remoteEnded + " ending=" + ending
                + " callId=" + callId + " peer=" + RtcDebugLogger.shortUid(peerUid) + " reason=" + closeReason);
        if (ending) return;
        ending = true;
        handler.removeCallbacks(inviteRetryRunnable);
        ringPlayer.stop();
        if (TextUtils.isEmpty(closeReason)) {
            closeReason = connected ? (remoteEnded ? "remote_ended" : "ended") : (incoming ? "missed" : "cancelled");
        }
        if (!remoteEnded) {
            try {
                RtcSignalManager.get().sendSimple(connected ? RtcSignal.END : RtcSignal.CANCEL, callId, peerUid, callType);
                RtcDebugLogger.i("RtcCallActivity", "send terminal ok type=" + (connected ? RtcSignal.END : RtcSignal.CANCEL)
                        + " callId=" + callId + " peer=" + RtcDebugLogger.shortUid(peerUid));
            } catch (Exception e) {
                RtcDebugLogger.e("RtcCallActivity", "send terminal failed", e);
            }
        }
        reportCallRecordIfNeeded(closeReason);
        RtcCallManager.get().markClosed(callId);
        RtcCallNotification.cancelIncoming(this);
        RtcCallForegroundService.stop(this);
        finish();
    }

    private void reportCallRecordIfNeeded(String reason) {
        if (recordReported) return;
        recordReported = true;
        RtcCallRecordReporter.report(callId, peerUid, peerName, callType, incoming, reason, connectedAt);
    }

    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
        try { avatarExecutor.shutdownNow(); } catch (Exception ignored) {}
        try { RtcCallNotification.cancelIncoming(this); } catch (Exception ignored) {}
        try { RtcCallForegroundService.stop(this); foregroundServiceStarted = false; } catch (Exception ignored) {}
        try { if (ringPlayer != null) ringPlayer.stop(); } catch (Exception ignored) {}
        try { if (screenSharing && peerClient != null) peerClient.stopScreenShare(); } catch (Exception ignored) {}
        try { if (peerClient != null) peerClient.closeBlocking(1200L); } catch (Exception ignored) {}
        try { if (audioManager != null) audioManager.stop(); } catch (Exception ignored) {}
        try { if (localRenderer != null) localRenderer.release(); } catch (Exception ignored) {}
        try { if (remoteRenderer != null) remoteRenderer.release(); } catch (Exception ignored) {}
        try { if (eglBase != null) eglBase.release(); } catch (Exception ignored) {}
    }

    private void ensurePermissionsThen(Runnable action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { action.run(); return; }
        String[] need = RtcConstants.isVideo(callType) ? RtcConstants.VIDEO_PERMISSIONS : RtcConstants.AUDIO_PERMISSIONS;
        List<String> missing = new ArrayList<>();
        for (String p : need) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (missing.isEmpty()) { action.run(); return; }
        pendingPermissionAction = action;
        requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSIONS) return;
        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) {
            closeReason = "permission_denied";
            toast(getString(R.string.rtc_need_permissions));
            endCall(false);
            return;
        }
        if (pendingPermissionAction != null) pendingPermissionAction.run();
        pendingPermissionAction = null;
    }

    private List<PeerConnection.IceServer> defaultIceServers() { return RtcIceServers.getDefault(); }

    private void enableLocalDragAndSwap() {
        final float[] down = new float[2]; final float[] start = new float[2]; final boolean[] moved = new boolean[1];
        localContainer.setOnTouchListener((v, e) -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (screenSharing) return true;
                    down[0] = e.getRawX(); down[1] = e.getRawY();
                    start[0] = lp.leftMargin > 0 ? lp.leftMargin : root.getWidth() - v.getWidth() - lp.rightMargin;
                    start[1] = lp.topMargin; lp.gravity = Gravity.TOP | Gravity.LEFT; lp.leftMargin = (int) start[0]; lp.rightMargin = 0; v.setLayoutParams(lp); moved[0] = false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved[0] = true;
                    lp.leftMargin = clamp((int) (start[0] + dx), dp(6), root.getWidth() - v.getWidth() - dp(6));
                    lp.topMargin = clamp((int) (start[1] + dy), dp(54), root.getHeight() - v.getHeight() - dp(120));
                    v.setLayoutParams(lp); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!screenSharing && !moved[0] && peerClient != null) { localFullScreen = !localFullScreen; peerClient.swapRenderers(localFullScreen); keepControlsVisible(); }
                    return true;
            }
            return false;
        });
    }

    private void keepControlsVisible() {
        if (RtcConstants.isVideo(callType) && connected) {
            setControlsVisible(true);
            scheduleControlsHide();
        }
    }

    private void toggleControlsVisible() {
        setControlsVisible(!controlsVisible);
        if (controlsVisible) scheduleControlsHide();
    }

    private void showCallControls() {
        setControlsVisible(true);
        scheduleControlsHide();
    }

    private void scheduleControlsHide() {
        handler.removeCallbacks(autoHideRunnable);
        if (connected && RtcConstants.isVideo(callType)) handler.postDelayed(autoHideRunnable, CONTROLS_AUTO_HIDE_MS);
    }

    private void setControlsVisible(boolean show) {
        controlsVisible = show;
        float target = show ? 1f : 0f;
        topInfo.animate().alpha(target).setDuration(180).start();
        controlsLayout.animate().alpha(target).setDuration(180).withEndAction(() -> {
            topInfo.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            controlsLayout.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }).start();
        sideControlsLayout.setVisibility(View.GONE);
        if (show) {
            topInfo.setVisibility(View.VISIBLE);
            controlsLayout.setVisibility(View.VISIBLE);
            camBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
            flipBtn.setVisibility(RtcConstants.isVideo(callType) && !screenSharing ? View.VISIBLE : View.GONE);
            screenBtn.setVisibility(RtcConstants.isVideo(callType) ? View.VISIBLE : View.GONE);
        }
    }

    private void styleCallButton(TextView v, String icon, String label, boolean danger) {
        v.setText(label);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(danger ? 0xffff4b55 : 0xeeffffff);
        v.setBackgroundColor(Color.TRANSPARENT);
        v.setCompoundDrawablePadding(dp(2));
        try {
            Drawable d = new IconicsDrawable(this, icon);
            d.setBounds(0, 0, dp(22), dp(22));
            d.setColorFilter(danger ? 0xffff4b55 : 0xffffffff, PorterDuff.Mode.SRC_IN);
            v.setCompoundDrawables(null, d, null, null);
        } catch (Exception ignored) {
            v.setCompoundDrawables(null, null, null, null);
        }
    }

    private void styleIncoming(View v, int color) { v.setBackground(round(color, dp(27))); }
    private GradientDrawable circle(int color) { GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); return d; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    private GradientDrawable gradient(int a, int b, int c) { return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b, c}); }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private String initial(String s) { return TextUtils.isEmpty(s) ? getString(R.string.rtc_friend_initial) : s.substring(0, 1).toUpperCase(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void hideAvatar() {
        avatarText.setVisibility(View.GONE);
        avatarImage.setVisibility(View.GONE);
    }

    private void loadAvatar(String url) {
        avatarText.setVisibility(View.VISIBLE);
        avatarImage.setVisibility(View.GONE);
        if (TextUtils.isEmpty(url)) return;
        try {
            String showUrl = url;
            if (!showUrl.startsWith("http://") && !showUrl.startsWith("https://") && !showUrl.startsWith("file://")) {
                showUrl = WKApiConfig.getShowUrl(showUrl);
            }
            GlideUtils.getInstance().showImg(this, showUrl, dp(108), dp(108), avatarImage);
            avatarImage.setVisibility(View.VISIBLE);
            avatarText.setVisibility(View.GONE);
        } catch (Exception ignored) {
            avatarImage.setVisibility(View.GONE);
            avatarText.setVisibility(View.VISIBLE);
        }
    }
}
