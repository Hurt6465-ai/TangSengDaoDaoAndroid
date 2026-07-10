package com.chat.dating;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.animation.OvershootInterpolator;
import android.view.View;

import com.chat.dating.model.DatingProfile;
import com.yuyakaido.android.cardstackview.Direction;

/** 统一管理滑卡触觉、短音效、按钮回弹和客户端额度提示。 */
public final class DatingActionController {
    private final Activity activity;
    private SoundPool soundPool;
    private int likeSoundId;
    private int passSoundId;
    private int favoriteSoundId;
    private boolean dragFeedbackTriggered;
    private DatingProfile myProfile;

    public DatingActionController(Activity activity) {
        this.activity = activity;
        initSound();
    }

    public void setMyProfile(DatingProfile myProfile) {
        this.myProfile = myProfile;
    }

    public void onDragging(View source, Direction direction, float ratio) {
        if (direction == null) return;
        if (ratio >= 0.56f && !dragFeedbackTriggered) {
            dragFeedbackTriggered = true;
            haptic(source);
            play(direction);
        } else if (ratio < 0.44f) {
            dragFeedbackTriggered = false;
        }
    }

    public void resetDragFeedback() {
        dragFeedbackTriggered = false;
    }

    public boolean canUse(String action) {
        return DatingQuotaManager.hasQuota(activity, myProfile, action);
    }

    public boolean consume(String action) {
        return DatingQuotaManager.consume(activity, myProfile, action);
    }

    public boolean consumeRewind() {
        return DatingQuotaManager.consumeRewind(activity);
    }

    public String quotaMessage(String action) {
        int limit = DatingQuotaManager.dailyLimit(myProfile, action);
        if (DatingSwipeAction.FAVORITE.equals(action)) return "今日收藏额度已用完（" + limit + " 次）";
        return "今日喜欢额度已用完（" + limit + " 次）";
    }

    public void buttonFeedback(View view, Direction direction) {
        animateButton(view);
        haptic(view);
        play(direction);
    }

    public void animateButton(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setScaleX(0.90f);
        view.setScaleY(0.90f);
        view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(170)
                        .setInterpolator(new OvershootInterpolator(1.35f)).start()
        ).start();
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    private void initSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build();
        } else {
            soundPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
        }
        likeSoundId = soundPool.load(activity, R.raw.dating_like, 1);
        passSoundId = soundPool.load(activity, R.raw.dating_pass, 1);
        favoriteSoundId = soundPool.load(activity, R.raw.dating_favorite, 1);
    }

    private void haptic(View view) {
        if (view == null || !DatingInteractionSettings.hapticEnabled(activity)) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignored) {
        }
    }

    private void play(Direction direction) {
        if (soundPool == null || !DatingInteractionSettings.soundEnabled(activity)) return;
        try {
            AudioManager manager = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
            if (manager != null) {
                if (manager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
                if (manager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return;
            }
            int soundId = passSoundId;
            if (direction == Direction.Right) soundId = likeSoundId;
            else if (direction == Direction.Top) soundId = favoriteSoundId;
            if (soundId != 0) soundPool.play(soundId, 0.36f, 0.36f, 1, 0, 1f);
        } catch (Throwable ignored) {
        }
    }
}
