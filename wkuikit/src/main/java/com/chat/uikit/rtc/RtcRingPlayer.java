package com.chat.uikit.rtc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.util.Log;

/**
 * Lightweight call ringtone helper.
 * Incoming ringtone file location:
 *   wkuikit/src/main/res/raw/newrtc.mp3
 * Outgoing dialing sound is intentionally disabled.
 */
public class RtcRingPlayer {
    private static final String TAG = "RtcRingPlayer";
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private boolean loopingTone;

    public RtcRingPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    /** No outgoing dialing sound. */
    public void playOutgoing() {
        stop();
    }

    public void playIncoming() {
        stop();
        startVibration();
        if (!playRawLoop("newrtc")) {
            playFallbackTone();
        }
    }

    public void stop() {
        loopingTone = false;
        handler.removeCallbacksAndMessages(null);
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
        } catch (Exception ignored) {}
        mediaPlayer = null;
        try {
            if (toneGenerator != null) toneGenerator.release();
        } catch (Exception ignored) {}
        toneGenerator = null;
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) {}
        vibrator = null;
    }

    private boolean playRawLoop(String name) {
        try {
            int resId = context.getResources().getIdentifier(name, "raw", context.getPackageName());
            if (resId == 0) return false;
            mediaPlayer = MediaPlayer.create(context, resId);
            if (mediaPlayer == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                //noinspection deprecation
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            }
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "play raw ring failed: " + name, e);
            stop();
            return false;
        }
    }

    private void playFallbackTone() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_RING, 80);
            loopingTone = true;
            Runnable r = new Runnable() {
                @Override public void run() {
                    if (!loopingTone || toneGenerator == null) return;
                    try {
                        toneGenerator.startTone(ToneGenerator.TONE_SUP_RINGTONE, 900);
                    } catch (Exception ignored) {}
                    handler.postDelayed(this, 1800);
                }
            };
            handler.post(r);
        } catch (Exception ignored) {}
    }

    private void startVibration() {
        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;
            long[] pattern = new long[]{0, 320, 120, 320, 900};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                //noinspection deprecation
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }
}
