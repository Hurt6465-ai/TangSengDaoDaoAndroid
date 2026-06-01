package com.chat.uikit.rtc;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
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

    private EglBase eglBase;
    private RtcPeerClient peerClient;
    private RtcAudioManager audioManager;
    private Runnable pendingPermissionAction;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<RtcSignal> localPending = new ArrayList<>();

    private FrameLayout root;
    private SurfaceViewRenderer remoteRenderer;
    private SurfaceViewRenderer localRenderer;
    private FrameLayout localContainer;
    private TextView avatarText;
    private TextView nameText;
    private TextView statusText;
    private LinearLayout controlsLayout;
    private LinearLayout incomingLayout;
    private TextView micBtn;
    private TextView speakerBtn;
    private TextView endBtn;
    private TextView camBtn;
    private TextView flipBtn;
    private long connectedAt;

    private final Runnable timerRunnable = new Runnable() {
        @Override public void run() {
            if (!connected || ending) return;
            long seconds = (System.currentTimeMillis() - connectedAt) / 1000L;
            statusText.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
            handler.postDelayed(this, 1000);
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
        catch (Exception e) { runOnUiThread(() -> toast("发送 SDP 失败")); }
    }
    @Override public void onIceCandidate(IceCandidate candidate) {
        try { RtcSignalManager.get().sendIce(callId, peerUid, candidate); }
        catch (Exception e) { runOnUiThread(() -> toast("发送 ICE 失败")); }
    }
    @Override public void onIceConnected() { runOnUiThread(this::markConnected); }
    @Override public void onIceDisconnected() { runOnUiThread(() -> { if (!ending) statusText.setText("网络不稳定，正在恢复…"); }); }
    @Override public void onRemoteVideoTrack() { runOnUiThread(() -> { if (RtcConstants.isVideo(callType)) avatarText.setVisibility(View.GONE); }); }
    @Override public void onError(String message, Throwable error) { runOnUiThread(() -> { toast(message == null ? "通话异常" : message); endCall(true); }); }

    private void readIntent() {
        callId = getIntent().getStringExtra(RtcConstants.EXTRA_CALL_ID);
        peerUid = getIntent().getStringExtra(RtcConstants.EXTRA_PEER_UID);
        peerName = getIntent().getStringExtra(RtcConstants.EXTRA_PEER_NAME);
        callType = getIntent().getIntExtra(RtcConstants.EXTRA_CALL_TYPE, RtcConstants.AUDIO);
        incoming = getIntent().getBooleanExtra(RtcConstants.EXTRA_INCOMING, false);
        if (TextUtils.isEmpty(callId)) callId = RtcCallManager.get().createCallId();
        if (TextUtils.isEmpty(peerName)) peerName = "好友";
    }

    private void bindViews() {
        root = findViewById(R.id.rtcRoot);
        remoteRenderer = findViewById(R.id.remoteRenderer);
        localRenderer = findViewById(R.id.localRenderer);
        localContainer = findViewById(R.id.localContainer);
        avatarText = findViewById(R.id.avatarText);
        nameText = findViewById(R.id.nameText);
        statusText = findViewById(R.id.statusText);
        controlsLayout = findViewById(R.id.controlsLayout);
        incomingLayout = findViewById(R.id.incomingLayout);
        micBtn = findViewById(R.id.micBtn);
        speakerBtn = findViewById(R.id.speakerBtn);
        endBtn = findViewById(R.id.endBtn);
        camBtn = findViewById(R.id.camBtn);
        flipBtn = findViewById(R.id.flipBtn);

        avatarText.setText(initial(peerName));
        avatarText.setBackground(circle(0xff334155));
        nameText.setText(peerName);
        root.setBackground(gradient(0xff0f172a, 0xff111827, 0xff020617));
        localContainer.setBackground(round(0xee000000, dp(18)));
        endBtn.setBackground(circle(0xffef4444));
        styleControl(micBtn, 0x24ffffff);
        styleControl(speakerBtn, 0x24ffffff);
        styleControl(camBtn, 0x24ffffff);
        styleControl(flipBtn, 0x24ffffff);
        styleIncoming(findViewById(R.id.rejectBtn), 0x33ffffff);
        styleIncoming(findViewById(R.id.acceptBtn), 0xff2563eb);

        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        remoteRenderer.setEnableHardwareScaler(true);
        remoteRenderer.setMirror(false);
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        localRenderer.setEnableHardwareScaler(true);
        localRenderer.setMirror(true);

        boolean video = RtcConstants.isVideo(callType);
        remoteRenderer.setVisibility(video ? View.VISIBLE : View.GONE);
        localContainer.setVisibility(video ? View.VISIBLE : View.GONE);
        camBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        flipBtn.setVisibility(video ? View.VISIBLE : View.GONE);
        enableLocalDragAndSwap();

        micBtn.setOnClickListener(v -> toggleMic());
        speakerBtn.setOnClickListener(v -> toggleSpeaker());
        endBtn.setOnClickListener(v -> endCall(false));
        camBtn.setOnClickListener(v -> toggleCamera());
        flipBtn.setOnClickListener(v -> { if (peerClient != null) peerClient.switchCamera(); });
        findViewById(R.id.rejectBtn).setOnClickListener(v -> rejectIncoming());
        findViewById(R.id.acceptBtn).setOnClickListener(v -> acceptIncoming());
    }

    private void showIncoming() {
        controlsLayout.setVisibility(View.GONE);
        incomingLayout.setVisibility(View.VISIBLE);
        statusText.setText(RtcConstants.isVideo(callType) ? "邀请你视频通话" : "邀请你语音通话");
    }

    private void showOutgoing() {
        incomingLayout.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.VISIBLE);
        statusText.setText(RtcConstants.isVideo(callType) ? "准备视频通话…" : "准备语音通话…");
    }

    private void startOutgoingFlow() {
        accepted = true;
        startWebRtc();
        try {
            RtcSignalManager.get().sendInvite(callId, peerUid, peerName, getIntent().getStringExtra(RtcConstants.EXTRA_PEER_AVATAR), callType);
            statusText.setText(RtcConstants.isVideo(callType) ? "等待对方接听…" : "正在呼叫…");
            peerClient.createOffer();
        } catch (Exception e) {
            toast("信令未接好，无法发起通话");
            endCall(true);
        }
        handler.postDelayed(() -> { if (!connected && !ending) { toast("对方无应答"); endCall(false); } }, RtcConstants.CALL_TIMEOUT_MS);
    }

    private void acceptIncoming() {
        if (accepted) return;
        accepted = true;
        incomingLayout.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.VISIBLE);
        ensurePermissionsThen(() -> {
            try { RtcSignalManager.get().sendSimple(RtcSignal.ACCEPT, callId, peerUid); } catch (Exception ignored) {}
            statusText.setText("连接中…");
            startWebRtc();
            consumePending();
        });
    }

    private void rejectIncoming() {
        try { RtcSignalManager.get().sendSimple(RtcSignal.REJECT, callId, peerUid); } catch (Exception ignored) {}
        RtcCallManager.get().markClosed(callId);
        finish();
    }

    private void startWebRtc() {
        audioManager.start(RtcConstants.isVideo(callType));
        peerClient = new RtcPeerClient(this, eglBase.getEglBaseContext(), this);
        peerClient.start(RtcConstants.isVideo(callType), defaultIceServers(), localRenderer, remoteRenderer);
        peerClient.swapRenderers(false);
    }

    private void handleSignal(RtcSignal s) {
        if (s == null || ending || !TextUtils.equals(s.callId, callId)) return;
        if (RtcSignal.CANCEL.equals(s.type) || RtcSignal.END.equals(s.type)) { toast("通话已结束"); endCall(true); return; }
        if (RtcSignal.REJECT.equals(s.type)) { toast("对方已拒绝"); endCall(true); return; }
        if (RtcSignal.BUSY.equals(s.type)) { toast("对方忙线中"); endCall(true); return; }
        if (RtcSignal.ACCEPT.equals(s.type)) { statusText.setText("对方已接听，连接中…"); return; }
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
        if (connected) return;
        connected = true;
        connectedAt = System.currentTimeMillis();
        statusText.setText("00:00");
        if (RtcConstants.isVideo(callType)) avatarText.setVisibility(View.GONE);
        handler.post(timerRunnable);
    }

    private void toggleMic() { micOn = !micOn; if (peerClient != null) peerClient.setMicEnabled(micOn); micBtn.setText(micOn ? "🎙\n静音" : "🔇\n已静音"); }
    private void toggleSpeaker() { boolean on = audioManager.toggleSpeaker(); speakerBtn.setText(on ? "🔊\n扬声器" : "👂\n听筒"); }
    private void toggleCamera() { cameraOn = !cameraOn; if (peerClient != null) peerClient.setCameraEnabled(cameraOn); camBtn.setText(cameraOn ? "📷\n摄像头" : "🚫\n已关闭"); localContainer.setVisibility(cameraOn ? View.VISIBLE : View.INVISIBLE); }

    private void endCall(boolean remoteEnded) {
        if (ending) return;
        ending = true;
        if (!remoteEnded) {
            try { RtcSignalManager.get().sendSimple(connected ? RtcSignal.END : RtcSignal.CANCEL, callId, peerUid); } catch (Exception ignored) {}
        }
        RtcCallManager.get().markClosed(callId);
        finish();
    }

    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
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
        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { toast("需要麦克风/摄像头权限"); endCall(false); return; }
        if (pendingPermissionAction != null) pendingPermissionAction.run();
        pendingPermissionAction = null;
    }

    private List<PeerConnection.IceServer> defaultIceServers() {
        return RtcIceServers.getDefault();
    }

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
                    lp.topMargin = clamp((int) (start[1] + dy), dp(54), root.getHeight() - v.getHeight() - dp(100));
                    v.setLayoutParams(lp); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!moved[0] && peerClient != null) { localFullScreen = !localFullScreen; peerClient.swapRenderers(localFullScreen); }
                    return true;
            }
            return false;
        });
    }

    private void styleControl(TextView v, int color) { v.setBackground(circle(color)); v.setTypeface(Typeface.DEFAULT_BOLD); }
    private void styleIncoming(View v, int color) { v.setBackground(round(color, dp(27))); }
    private GradientDrawable circle(int color) { GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); return d; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    private GradientDrawable gradient(int a, int b, int c) { return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b, c}); }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private String initial(String s) { return TextUtils.isEmpty(s) ? "友" : s.substring(0, 1).toUpperCase(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
