package com.chat.dating;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingMineBinding;
import com.chat.dating.model.DatingProfile;

public class DatingMineActivity extends Activity {
    public static final String EXTRA_PROFILE = "dating_my_profile";
    private static final int REQ_EDIT = 501;

    private ActivityWkDatingMineBinding binding;
    private DatingProfile profile;
    private boolean changed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(247, 247, 249));
        binding = ActivityWkDatingMineBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        DatingUi.applyPageInsets(this, binding.getRoot());
        Object value = getIntent().getSerializableExtra(EXTRA_PROFILE);
        if (value instanceof DatingProfile) profile = (DatingProfile) value;
        initListeners();
        if (profile == null) loadProfile();
        else bindProfile();
    }

    private void initListeners() {
        binding.backBtn.setOnClickListener(v -> finishWithResult());
        binding.editProfileRow.setOnClickListener(v -> {
            Intent intent = new Intent(this, DatingEditProfileActivity.class);
            intent.putExtra(DatingEditProfileActivity.EXTRA_PROFILE, profile);
            startActivityForResult(intent, REQ_EDIT);
        });
        binding.favoritesRow.setOnClickListener(v -> startActivity(new Intent(this, DatingFavoritesActivity.class)));
        binding.whoLikesRow.setOnClickListener(v -> startActivity(new Intent(this, DatingWhoLikesActivity.class)));
        binding.matchesRow.setOnClickListener(v -> startActivity(new Intent(this, DatingMatchesActivity.class)));
        binding.quotaRow.setOnClickListener(v -> showQuota());
        binding.statusRow.setOnClickListener(v -> showStatusDialog());
    }

    private void loadProfile() {
        DatingModel.getInstance().getMyDatingProfile((code, msg, data) -> {
            if (data != null) {
                profile = data;
                bindProfile();
            } else {
                toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_profile_load_error) : msg);
            }
        });
    }

    private void bindProfile() {
        if (profile == null) return;
        binding.avatarView.setSize(74f);
        binding.avatarView.showAvatarUrl(profile.safeAvatar(), profile.safeUid(), profile.safeName(), profile.safeUid());
        binding.avatarView.showFlag(profile.safeCountryCode());
        binding.nameTv.setText(DatingUi.nameAgeFlag(profile));
        String meta = DatingUi.displayLocation(this, profile);
        binding.metaTv.setText(TextUtils.isEmpty(meta) ? getString(R.string.dating_profile_complete_tip) : meta);
        binding.statusValueTv.setText(profile.enabled == 1 ? R.string.dating_close_dating : R.string.dating_open_dating);
        binding.likeQuotaTv.setText(getString(R.string.dating_like_quota_format,
                DatingQuotaManager.remaining(this, profile, DatingSwipeAction.LIKE),
                DatingQuotaManager.dailyLimit(profile, DatingSwipeAction.LIKE)));
        binding.favoriteQuotaTv.setText(getString(R.string.dating_favorite_quota_format,
                DatingQuotaManager.remaining(this, profile, DatingSwipeAction.FAVORITE),
                DatingQuotaManager.dailyLimit(profile, DatingSwipeAction.FAVORITE)));
        binding.rewindQuotaTv.setText(getString(R.string.dating_rewind_quota_format,
                DatingQuotaManager.rewindRemaining(this)));
    }

    private void showStatusDialog() {
        if (profile == null) return;
        boolean enabled = profile.enabled == 1;
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.dating_status_title)
                .setMessage(enabled ? R.string.dating_pause_message : R.string.dating_resume_message)
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(enabled ? R.string.dating_pause : R.string.dating_resume, (dialog, which) -> {
                    if (!enabled && profile.safeDatingPhotos().isEmpty()) {
                        toast(getString(R.string.dating_min_photo_enable));
                        binding.editProfileRow.performClick();
                        return;
                    }
                    binding.statusRow.setEnabled(false);
                    DatingModel.getInstance().enableProfile(!enabled, (code, msg, data) -> {
                        if (isFinishing() || isDestroyed() || binding == null) return;
                        binding.statusRow.setEnabled(true);
                        if (code == HttpResponseCode.success && data != null) {
                            profile = data;
                            changed = true;
                            bindProfile();
                            toast(getString(profile.enabled == 1 ? R.string.dating_enabled : R.string.dating_disabled));
                        } else {
                            toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_action_failed) : msg);
                        }
                    });
                })
                .show();
    }

    private void showQuota() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.dating_daily_quota)
                .setMessage(getString(R.string.dating_quota_message,
                        binding.likeQuotaTv.getText(), binding.favoriteQuotaTv.getText(), binding.rewindQuotaTv.getText()))
                .setPositiveButton(R.string.dating_ok, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT && resultCode == RESULT_OK) {
            changed = true;
            loadProfile();
        }
    }

    @Override
    public void onBackPressed() {
        finishWithResult();
    }

    private void finishWithResult() {
        setResult(changed ? RESULT_OK : RESULT_CANCELED);
        finish();
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
