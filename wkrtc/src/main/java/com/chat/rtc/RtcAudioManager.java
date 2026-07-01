package com.chat.rtc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

/**
 * RTC audio route controller. Borrowed the important Tinode idea:
 * on Android 12+ prefer setCommunicationDevice()/clearCommunicationDevice()
 * instead of only using the legacy setSpeakerphoneOn().
 */
public class RtcAudioManager {
    private final AudioManager audioManager;
    private boolean started;
    private int savedMode;
    private boolean savedSpeaker;
    private boolean savedMute;
    private AudioFocusRequest focusRequest;
    private AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {};

    public RtcAudioManager(Context context) {
        audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    public void start(boolean videoCall) {
        if (started || audioManager == null) return;
        started = true;
        savedMode = audioManager.getMode();
        savedSpeaker = isSpeakerOn();
        savedMute = audioManager.isMicrophoneMute();
        requestFocus();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setMicrophoneMute(false);
        setSpeaker(videoCall);
    }

    public void stop() {
        if (!started || audioManager == null) return;
        started = false;
        setSpeaker(savedSpeaker);
        audioManager.setMicrophoneMute(savedMute);
        audioManager.setMode(savedMode);
        abandonFocus();
    }

    public boolean toggleSpeaker() {
        if (audioManager == null) return false;
        boolean next = !isSpeakerOn();
        setSpeaker(next);
        return isSpeakerOn();
    }

    public boolean isSpeakerOn() {
        if (audioManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo device = audioManager.getCommunicationDevice();
            return device != null && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        }
        return audioManager.isSpeakerphoneOn();
    }

    private void setSpeaker(boolean enable) {
        if (audioManager == null) return;
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enable) {
                AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                for (AudioDeviceInfo device : devices) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        audioManager.setCommunicationDevice(device);
                        return;
                    }
                }
            } else {
                audioManager.clearCommunicationDevice();
            }
        } else {
            //noinspection deprecation
            audioManager.setSpeakerphoneOn(enable);
        }
    }

    private void requestFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            //noinspection deprecation
            audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            //noinspection deprecation
            audioManager.abandonAudioFocus(focusChangeListener);
        }
        focusRequest = null;
    }
}
