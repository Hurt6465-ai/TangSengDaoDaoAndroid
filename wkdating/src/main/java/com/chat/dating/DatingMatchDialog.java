package com.chat.dating;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

import com.chat.dating.databinding.DialogWkDatingMatchBinding;
import com.chat.dating.model.DatingProfile;

public final class DatingMatchDialog {
    private DatingMatchDialog() {
    }

    public static void show(Activity activity,
                            DatingProfile me,
                            DatingProfile target,
                            boolean friendCreated,
                            Runnable openChat) {
        if (activity == null || activity.isFinishing() || target == null) return;
        Dialog dialog = new Dialog(activity);
        DialogWkDatingMatchBinding binding = DialogWkDatingMatchBinding.inflate(activity.getLayoutInflater());
        dialog.setContentView(binding.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.72f;
            window.setAttributes(params);
        }

        bindAvatar(binding.myAvatar, me);
        bindAvatar(binding.targetAvatar, target);
        binding.titleTv.setText(R.string.dating_match_title_full);
        binding.subtitleTv.setText(friendCreated
                ? activity.getString(R.string.dating_match_friend_created, target.safeName())
                : activity.getString(R.string.dating_match_subtitle, target.safeName()));
        binding.keepBtn.setOnClickListener(v -> dialog.dismiss());
        binding.chatBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (openChat != null) openChat.run();
        });

        dialog.setOnShowListener(ignored -> {
            float offset = activity.getResources().getDisplayMetrics().density * 80f;
            binding.heartTv.setScaleX(0.2f);
            binding.heartTv.setScaleY(0.2f);
            binding.heartTv.setAlpha(0f);
            binding.myAvatar.setTranslationX(-offset);
            binding.targetAvatar.setTranslationX(offset);
            binding.myAvatar.animate().translationX(0f).setDuration(360).start();
            binding.targetAvatar.animate().translationX(0f).setDuration(360).start();
            binding.heartTv.animate()
                    .alpha(1f)
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(260)
                    .withEndAction(() -> binding.heartTv.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start())
                    .start();
        });
        dialog.show();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private static void bindAvatar(com.chat.base.ui.components.AvatarView avatarView, DatingProfile profile) {
        if (avatarView == null) return;
        avatarView.setSize(112f);
        if (profile == null) {
            avatarView.showAvatarUrl("", "", "", "");
            avatarView.showFlag("");
            return;
        }
        avatarView.showAvatarUrl(profile.safeAvatar(), profile.safeUid(), profile.safeName(), profile.safeUid());
        avatarView.showFlag(profile.safeCountryCode());
    }
}
