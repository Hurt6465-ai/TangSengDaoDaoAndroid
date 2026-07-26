package com.chat.learning;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;

/** Plays bundled pinyin audio. Missing files transparently use the bundled placeholder. */
final class PinyinAudioPlayer {
    static final String PLACEHOLDER_ASSET = "learning/pinyin/audio/placeholder.mp3";

    interface Callback {
        void onStarted(boolean placeholder);
        void onCompleted();
        void onError();
    }

    private final Context context;
    private MediaPlayer player;

    PinyinAudioPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    void play(String assetPath, float speed, Callback callback) {
        stop();
        String requested = assetPath == null ? "" : assetPath.trim();
        boolean placeholder = requested.isEmpty() || !assetExists(requested);
        String resolved = placeholder ? PLACEHOLDER_ASSET : requested;
        try {
            AssetFileDescriptor descriptor = context.getAssets().openFd(resolved);
            MediaPlayer next = new MediaPlayer();
            next.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            next.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();
            next.setOnPreparedListener(mp -> {
                try {
                    if (Build.VERSION.SDK_INT >= 23 && Math.abs(speed - 1f) > 0.01f) {
                        PlaybackParams params = mp.getPlaybackParams();
                        params.setSpeed(Math.max(0.5f, Math.min(1.5f, speed)));
                        mp.setPlaybackParams(params);
                    }
                    mp.start();
                    if (callback != null) callback.onStarted(placeholder);
                } catch (Throwable error) {
                    stop();
                    if (callback != null) callback.onError();
                }
            });
            next.setOnCompletionListener(mp -> {
                stop();
                if (callback != null) callback.onCompleted();
            });
            next.setOnErrorListener((mp, what, extra) -> {
                stop();
                if (callback != null) callback.onError();
                return true;
            });
            player = next;
            next.prepareAsync();
        } catch (Throwable error) {
            stop();
            if (callback != null) callback.onError();
        }
    }

    void stop() {
        MediaPlayer old = player;
        player = null;
        if (old == null) return;
        try { old.setOnCompletionListener(null); } catch (Throwable ignored) { }
        try { old.setOnErrorListener(null); } catch (Throwable ignored) { }
        try { old.stop(); } catch (Throwable ignored) { }
        try { old.release(); } catch (Throwable ignored) { }
    }

    void release() {
        stop();
    }

    private boolean assetExists(String path) {
        try (AssetFileDescriptor ignored = context.getAssets().openFd(path)) {
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
