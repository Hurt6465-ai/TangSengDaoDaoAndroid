package com.chat.uikit.rtc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.chat.uikit.R;
import com.chat.uikit.rtc.model.RtcSignal;

import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.List;

public class RtcCallActivity extends Activity implements RtcPeerClient.Events, RtcCallManager.ActiveCallListener {
    private static final int REQ_PERMISSIONS = 7001;
    private static final int REQ_SCREEN_SHARE = 7002;
    private static final long CONTROLS_AUTO_HIDE_MS = 2000L;

    private String callId;
    private String peerUid;
    private String peerName;
    private int callType;
    private boolean incoming;
    private boolean accepted;
    private boolean connected;
    private boolean ending;
    private boolean micOn = true;
    private boolean cameraOn = true;
    private boolean localFullScreen;
    private boolean controlsVisible = true;
    private boolean screenSharing;
    private boolean weakMode;
    private boolean ultraHd;
    private boolean beautyOn = true;

    private EglBase eglBase;
    private RtcPeerClient peerClient;
    private RtcAudioManager audioManager;
    private RtcRingPlayer ringPlayer;
    private Runnable pendingPermissionAction;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<RtcSignal> localPending = new ArrayList<>();

    private FrameLayout root;
    private SurfaceViewRenderer remoteRenderer;
    private SurfaceViewRenderer localRenderer;
    private FrameLayout localContainer;
    private LinearLayout topInfo;
    private TextView avatarText;
    private TextView nameText;
    private TextView statusText;
    private LinearLayout controlsLayout;
    private LinearLayout sideToolsLayout;
    private LinearLayout incomingLayout;
    private TextView micBtn;
    private TextView speakerBtn;
    private TextView endBtn;
    private TextView camBtn;
    private TextView flipBtn;
    private TextView qualityBtn;
    private TextView beautyBtn;
    private TextView shareBtn;
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

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        readIntent();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        eglBase = EglBase.create();
        audioManager = new RtcAudioManager(this);
        ringPlayer = new RtcRingPlayer(this);
        setContentView(R.layout.act_rtc_call);
        bindViews();
        RtcCallManager.get().setActiveCallListener(this);
        if (incoming) showIncoming(); else { showOutgoing(); ensurePermissionsThen(this::startOutgoingFlow); }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        RtcCallManager.get().clearActiveCallListener(this);
        cleanup();
    }

    @Override public void onBackPressed() { endCall(false); }
    @Override public String getActiveCallId() { return callId; }
    @Override public void onSignalForActiveCall(RtcSignal signal) { runOnUiThread(() -> handleSignal(signal)); }

    @Override public void onLocalDescription(SessionDescription description) {
        try { RtcSignalManager.get().sendDescription(callId, peerUid, description); }
        catch (Exception e) { runOnUiThread(() -> toast(getString(R.string.rtc_send_sdp_failed))); }
    }
    @Override public void onIceCandidate(IceCandidate candidate) {
        try { RtcSignalManager.get().sendIce(callId, peerUid, candidate); }
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
    @Override public void onRemoteVideoTrack() { runOnUiThread(() -> { if (RtcConstants.isVideo(callType)) avatarText.setVisibility(View.GONE); }); }
    @Override public void onError(String message, Throwable error) { runOnUiThread(() -> { toast(message == null ? getString(R.string.rtc_call_error) : message); endCall(true); }); }

    private void readIntent() {
        callId = getIntent().getStringExtra(RtcConstants.EXTRA_CALL_ID);
        peerUid = getIntent().getStringExtra(RtcConstants.EXTRA_PEER_UID);
        peerName = getIntent().getStringExtra(RtcConstants.EXTRA_PEER_NAME);
        callType = getIntent().getIntExtra(RtcConstants.EXTRA_CALL_TYPE, RtcConstants.AUDIO);
        incoming = getIntent().getBooleanExtra(RtcConstants.EXTRA_INCOMING, false);
        if (TextUtils.isEmpty(callId)) callId = RtcCallManager.get().createCallId();
        if (TextUtils.isEmpty(peerName)) peerName = getString(R.string.rtc_friend);
    }

    private void bindViews() {
        root = findViewById(R.id.rtcRoot);
        remoteRenderer = findViewById(R.id.remoteRenderer);
        localRenderer = findViewById(R.id.localRenderer);
        localContainer = findViewById(R.id.localContainer);
        topInfo = findViewById(R.id.topInfo);
        avatarText = findViewById(R.id.avatarText);
        nameText = findViewById(R.id.nameText);
        statusText = findViewById(R.id.statusText);
        controlsLayout = findViewById(R.id.controlsLayout);
        sideToolsLayout = findViewById(R.id.sideToolsLayout);
        incomingLayout = findViewById(R.id.incomingLayout);
        micBtn = findViewById(R.id.micBtn);
        speakerBtn = findViewById(R.id.speakerBtn);
        endBtn = findViewById(R.id.endBtn);
        camBtn = findViewById(R.id.camBtn);
        flipBtn = findViewById(R.id.flipBtn);
        qualityBtn = findViewById(R.id.qualityBtn);
        beautyBtn = findViewById(R.id.beautyBtn);
        shareBtn = findViewById(R.id.shareBtn);

        avatarText.setText(initial(peerName));
        avatarText.setBackground(circle(0xff334155));
        nameText.setText(peerName);
        root.setBackground(gradient(0xff101827, 0xff162033, 0xff020617));
        localContainer.setBackground(round(0xee000000, dp(18)));
        findViewById(R.id.controlsCard).setBackground(round(0x50000000, dp(34)));
        sideToolsLayout.setBackground(round(0x50000000, dp(26)));

        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        remoteRenderer.setEnableHardwareScaler(true);
        remoteRenderer.setMirror(false);
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        localRenderer.setEnableHardwareScaler(true);
        localRenderer.setMirror(true);

        styleCallButton(micBtn, FontAwesome.Icon.faw_microphone, getString(R.string.rtc_mute), false);
        styleCallButton(speakerBtn, FontAwesome.Icon.faw_volume_up, getString(R.string.rtc_speaker), false);
        styleCallButton(endBtn, FontAwesome.Icon.faw_phone_slash, getString(R.string.rtc_hangup), true);
        styleCallButton(camBtn, FontAwesome.Icon.faw_video, getString(R.string.rtc_camera), false);
        styleCallButton(flipBtn, FontAwesome.Icon.faw_sync_alt, getString(R.string.rtc_flip), false);
        updateQualityButton();
        updateBeautyButton();
        styleCallButton(shareBtn, FontAwesome.Icon.faw_desktop, getString(R.string.rtc_screen_share), false);
        styleIncoming(findViewById(R.id.rejectBtn), 0x33ffffff);
        styleIncoming(findViewById(R.id.acceptBtn), 0xff22c55e);

        boolean video = RtcConstants.isVideo(callType);
        remoteRenderer.setVisibility(video ? View.VISIBLE : View.GONE);
        localContainer.setVisibility(video ? View.VISIBLE : View.GONE);
        sideToolsLayout.setVisibility(video ? View.VISIBLE : View.GONE);
        camBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        flipBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        qualityBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        beautyBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        shareBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        enableLocalDragAndSwap();
        root.setOnClickListener(v -> { if (connected && video) toggleControlsVisible(); });

        micBtn.setOnClickListener(v -> { toggleMic(); keepControlsVisible(); });
        speakerBtn.setOnClickListener(v -> { toggleSpeaker(); keepControlsVisible(); });
        endBtn.setOnClickListener(v -> endCall(false));
        camBtn.setOnClickListener(v -> { toggleCamera(); keepControlsVisible(); });
        flipBtn.setOnClickListener(v -> { if (peerClient != null) peerClient.switchCamera(); keepControlsVisible(); });
        qualityBtn.setOnClickListener(v -> { toggleQuality(); keepControlsVisible(); });
        beautyBtn.setOnClickListener(v -> { toggleBeauty(); keepControlsVisible(); });
        shareBtn.setOnClickListener(v -> { toggleScreenShare(); keepControlsVisible(); });
        findViewById(R.id.rejectBtn).setOnClickListener(v -> rejectIncoming());
        findViewById(R.id.acceptBtn).setOnClickListener(v -> acceptIncoming());
    }

    private void showIncoming() {
        controlsLayout.setVisibility(View.GONE);
        if (sideToolsLayout != null) sideToolsLayout.setVisibility(View.GONE);
        incomingLayout.setVisibility(View.VISIBLE);
        statusText.setText(RtcConstants.isVideo(callType) ? getString(R.string.rtc_invite_video) : getString(R.string.rtc_invite_audio));
        ringPlayer.playIncoming();
    }

    private void showOutgoing() {
        incomingLayout.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.VISIBLE);
        if (sideToolsLayout != null && RtcConstants.isVideo(callType)) sideToolsLayout.setVisibility(View.VISIBLE);
        statusText.setText(RtcConstants.isVideo(callType) ? getString(R.string.rtc_prepare_video) : getString(R.string.rtc_prepare_audio));
    }

    private void startOutgoingFlow() {
        accepted = true;
        startWebRtc();
        try {
            RtcSignalManager.get().sendInvite(callId, peerUid, peerName, getIntent().getStringExtra(RtcConstants.EXTRA_PEER_AVATAR), callType);
            statusText.setText(RtcConstants.isVideo(callType) ? getString(R.string.rtc_wait_accept_video) : getString(R.string.rtc_calling_audio));
            ringPlayer.playOutgoing();
            peerClient.createOffer();
        } catch (Exception e) {
            toast(getString(R.string.rtc_signal_not_ready));
            endCall(true);
        }
        handler.postDelayed(() -> { if (!connected && !ending) { toast(getString(R.string.rtc_no_answer)); endCall(false); } }, RtcConstants.CALL_TIMEOUT_MS);
    }

    private void acceptIncoming() {
        if (accepted) return;
        accepted = true;
        ringPlayer.stop();
        incomingLayout.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.VISIBLE);
        if (sideToolsLayout != null && RtcConstants.isVideo(callType)) sideToolsLayout.setVisibility(View.VISIBLE);
        ensurePermissionsThen(() -> {
            try { RtcSignalManager.get().sendSimple(RtcSignal.ACCEPT, callId, peerUid); } catch (Exception ignored) {}
            statusText.setText(getString(R.string.rtc_connecting));
            startWebRtc();
            consumePending();
        });
    }

    private void rejectIncoming() {
        ringPlayer.stop();
        try { RtcSignalManager.get().sendSimple(RtcSignal.REJECT, callId, peerUid); } catch (Exception ignored) {}
        RtcCallManager.get().markClosed(callId);
        finish();
    }

    private void startWebRtc() {
        audioManager.start(RtcConstants.isVideo(callType));
        updateSpeakerButton(audioManager.isSpeakerOn());
        peerClient = new RtcPeerClient(this, eglBase.getEglBaseContext(), this);
        peerClient.setBeautyEnabled(beautyOn);
        peerClient.setUltraHdEnabled(ultraHd);
        peerClient.start(RtcConstants.isVideo(callType), defaultIceServers(), localRenderer, remoteRenderer);
        peerClient.swapRenderers(false);
    }

    private void handleSignal(RtcSignal s) {
        if (s == null || ending || !TextUtils.equals(s.callId, callId)) return;
        if (RtcSignal.CANCEL.equals(s.type) || RtcSignal.END.equals(s.type)) { toast(getString(R.string.rtc_call_ended)); endCall(true); return; }
        if (RtcSignal.REJECT.equals(s.type)) { toast(getString(R.string.rtc_rejected)); endCall(true); return; }
        if (RtcSignal.BUSY.equals(s.type)) { toast(getString(R.string.rtc_busy)); endCall(true); return; }
        if (RtcSignal.ACCEPT.equals(s.type)) { ringPlayer.stop(); statusText.setText(getString(R.string.rtc_peer_accepted)); return; }
        if (peerClient == null || (incoming && !accepted)) { localPending.add(s); return; }
        handlePeerSignal(s);
    }

    private void handlePeerSignal(RtcSignal s) {
        if (RtcSignal.OFFER.equals(s.type)) peerClient.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, s.sdp));
        else if (RtcSignal.ANSWER.equals(s.type)) peerClient.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, s.sdp));
        else if (RtcSignal.ICE.equals(s.type)) peerClient.addRemoteIce(new IceCandidate(s.sdpMid, s.sdpMLineIndex, s.candidate));
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
        connected = true;
        weakMode = false;
        connectedAt = System.currentTimeMillis();
        statusText.setText("00:00");
        if (RtcConstants.isVideo(callType)) avatarText.setVisibility(View.GONE);
        handler.post(timerRunnable);
        scheduleControlsHide();
    }

    private void toggleMic() {
        micOn = !micOn;
        if (peerClient != null) peerClient.setMicEnabled(micOn);
        styleCallButton(micBtn, micOn ? FontAwesome.Icon.faw_microphone : FontAwesome.Icon.faw_microphone_slash, micOn ? getString(R.string.rtc_mute) : getString(R.string.rtc_muted), false);
    }

    private void toggleSpeaker() {
        updateSpeakerButton(audioManager.toggleSpeaker());
    }

    private void updateSpeakerButton(boolean on) {
        styleCallButton(speakerBtn, on ? FontAwesome.Icon.faw_volume_up : FontAwesome.Icon.faw_volume_down, on ? getString(R.string.rtc_speaker) : getString(R.string.rtc_earpiece), false);
    }

    private void toggleCamera() {
        cameraOn = !cameraOn;
        if (peerClient != null) peerClient.setCameraEnabled(cameraOn);
        styleCallButton(camBtn, cameraOn ? FontAwesome.Icon.faw_video : FontAwesome.Icon.faw_video_slash, cameraOn ? getString(R.string.rtc_camera) : getString(R.string.rtc_camera_off), false);
        localContainer.setVisibility(cameraOn ? View.VISIBLE : View.INVISIBLE);
    }

    private void toggleQuality() {
        ultraHd = !ultraHd;
        if (peerClient != null) peerClient.setUltraHdEnabled(ultraHd);
        updateQualityButton();
        toast(getString(ultraHd ? R.string.rtc_quality_fhd_on : R.string.rtc_quality_hd_on));
    }

    private void updateQualityButton() {
        styleCallButton(qualityBtn, ultraHd ? FontAwesome.Icon.faw_expand : FontAwesome.Icon.faw_compress, ultraHd ? getString(R.string.rtc_quality_fhd) : getString(R.string.rtc_quality_hd), false);
    }

    private void toggleBeauty() {
        beautyOn = !beautyOn;
        if (peerClient != null) peerClient.setBeautyEnabled(beautyOn);
        updateBeautyButton();
        toast(getString(beautyOn ? R.string.rtc_beauty_on : R.string.rtc_beauty_off));
    }

    private void updateBeautyButton() {
        styleCallButton(beautyBtn, FontAwesome.Icon.faw_magic, beautyOn ? getString(R.string.rtc_beauty_on) : getString(R.string.rtc_beauty_off), false);
    }

    private void toggleScreenShare() {
        if (!RtcConstants.isVideo(callType) || peerClient == null) return;
        if (screenSharing) {
            screenSharing = false;
            localRenderer.setMirror(true);
            peerClient.stopScreenShare();
            styleCallButton(shareBtn, FontAwesome.Icon.faw_desktop, getString(R.string.rtc_screen_share), false);
            toast(getString(R.string.rtc_screen_share_stopped));
            return;
        }
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (manager == null) { toast(getString(R.string.rtc_screen_share_failed)); return; }
            startActivityForResult(manager.createScreenCaptureIntent(), REQ_SCREEN_SHARE);
        } catch (Exception e) {
            toast(getString(R.string.rtc_screen_share_failed));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCREEN_SHARE) {
            if (resultCode == RESULT_OK && data != null && peerClient != null) {
                screenSharing = true;
                localRenderer.setMirror(false);
                peerClient.startScreenShare(data);
                styleCallButton(shareBtn, FontAwesome.Icon.faw_desktop, getString(R.string.rtc_screen_sharing), false);
                toast(getString(R.string.rtc_screen_share_started));
            } else {
                toast(getString(R.string.rtc_screen_share_cancelled));
            }
        }
    }

    private void endCall(boolean remoteEnded) {
        if (ending) return;
        ending = true;
        ringPlayer.stop();
        if (!remoteEnded) {
            try { RtcSignalManager.get().sendSimple(connected ? RtcSignal.END : RtcSignal.CANCEL, callId, peerUid); } catch (Exception ignored) {}
        }
        RtcCallManager.get().markClosed(callId);
        finish();
    }

    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
        try { if (ringPlayer != null) ringPlayer.stop(); } catch (Exception ignored) {}
        try { if (peerClient != null) peerClient.close(); } catch (Exception ignored) {}
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
        if (missing.isEmpty()) { action.run(); return; }
        pendingPermissionAction = action;
        requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSIONS) return;
        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { toast(getString(R.string.rtc_need_permissions)); endCall(false); return; }
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
                    if (!moved[0] && peerClient != null) { localFullScreen = !localFullScreen; peerClient.swapRenderers(localFullScreen); keepControlsVisible(); }
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
            if (RtcConstants.isVideo(callType)) sideToolsLayout.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }).start();
        if (RtcConstants.isVideo(callType)) sideToolsLayout.animate().alpha(target).setDuration(180).start();
        if (show) {
            topInfo.setVisibility(View.VISIBLE);
            controlsLayout.setVisibility(View.VISIBLE);
            if (RtcConstants.isVideo(callType)) sideToolsLayout.setVisibility(View.VISIBLE);
        }
    }

    private void styleCallButton(TextView v, FontAwesome.Icon icon, String label, boolean danger) {
        if (v == null) return;
        v.setText(label);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setBackground(circle(danger ? 0xffef4444 : 0x30ffffff));
        v.setCompoundDrawablePadding(dp(6));
        try {
            Drawable d = new IconicsDrawable(this, icon)
                    .colorInt(Color.WHITE)
                    .sizeDp(22);
            v.setCompoundDrawablesWithIntrinsicBounds(null, d, null, null);
        } catch (Throwable ignored) {
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
}
