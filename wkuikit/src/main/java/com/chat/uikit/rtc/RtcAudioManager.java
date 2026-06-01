package com.chat.uikit.rtc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

public class RtcAudioManager {
    private final AudioManager audioManager;
    private boolean started;
    private int savedMode;
    private boolean savedSpeaker;
    private boolean savedMute;
    private AudioFocusRequest focusRequest;

    public RtcAudioManager(Context context) {
        audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    public void start(boolean videoCall) {
        if (started || audioManager == null) return;
        started = true;
        savedMode = audioManager.getMode();
        savedSpeaker = audioManager.isSpeakerphoneOn();
        savedMute = audioManager.isMicrophoneMute();
        requestFocus();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setMicrophoneMute(false);
        audioManager.setSpeakerphoneOn(videoCall);
    }

    public void stop() {
        if (!started || audioManager == null) return;
        started = false;
        audioManager.setSpeakerphoneOn(savedSpeaker);
        audioManager.setMicrophoneMute(savedMute);
        audioManager.setMode(savedMode);
        abandonFocus();
    }

    public boolean toggleSpeaker() {
        if (audioManager == null) return false;
        boolean next = !audioManager.isSpeakerphoneOn();
        audioManager.setSpeakerphoneOn(next);
        return next;
    }

    private void requestFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChange -> {})
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(focusChange -> {}, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void abandonFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        else audioManager.abandonAudioFocus(null);
        focusRequest = null;
    }
}
