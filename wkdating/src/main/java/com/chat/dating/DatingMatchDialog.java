package com.chat.dating;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

import com.bumptech.glide.Glide;
import com.chat.dating.databinding.DialogWkDatingMatchBinding;
import com.chat.dating.model.DatingProfile;

public final class DatingMatchDialog {
    private DatingMatchDialog() {}

    public static void show(Activity activity, DatingProfile me, DatingProfile target, Runnable openChat) {
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

        Glide.with(activity).load(DatingImageSource.resolve(activity, me == null ? "" : me.firstPhoto())).centerCrop().into(binding.myPhoto);
        Glide.with(activity).load(DatingImageSource.resolve(activity, target.firstPhoto())).centerCrop().into(binding.targetPhoto);
        binding.titleTv.setText(R.string.dating_match_title_full);
        binding.subtitleTv.setText(activity.getString(R.string.dating_match_subtitle, target.safeName()));
        binding.keepBtn.setOnClickListener(v -> dialog.dismiss());
        binding.chatBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (openChat != null) openChat.run();
        });

        dialog.setOnShowListener(ignored -> {
            binding.heartTv.setScaleX(0.2f);
            binding.heartTv.setScaleY(0.2f);
            binding.heartTv.setAlpha(0f);
            binding.myPhoto.setTranslationX(-activity.getResources().getDisplayMetrics().density * 80f);
            binding.targetPhoto.setTranslationX(activity.getResources().getDisplayMetrics().density * 80f);
            binding.myPhoto.animate().translationX(0f).setDuration(360).start();
            binding.targetPhoto.animate().translationX(0f).setDuration(360).start();
            binding.heartTv.animate().alpha(1f).scaleX(1.15f).scaleY(1.15f).setDuration(260).withEndAction(() ->
                    binding.heartTv.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            ).start();
        });
        dialog.show();
        if (window != null) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
    }
}
