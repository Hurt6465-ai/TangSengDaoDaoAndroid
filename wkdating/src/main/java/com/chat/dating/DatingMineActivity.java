package com.chat.dating;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
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
        binding.enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            buttonView.setEnabled(false);
            DatingModel.getInstance().enableProfile(isChecked, (code, msg, data) -> {
                buttonView.setEnabled(true);
                if (code == HttpResponseCode.success) {
                    if (profile != null) profile.enabled = isChecked ? 1 : 0;
                    changed = true;
                    toast(getString(isChecked ? R.string.dating_enabled : R.string.dating_disabled));
                } else {
                    buttonView.setChecked(!isChecked);
                    toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_action_failed) : msg);
                }
            });
        });
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
        Glide.with(this).load(DatingImageSource.resolve(this, profile.firstPhoto())).centerCrop().into(binding.avatarIv);
        binding.nameTv.setText(DatingUi.nameAgeFlag(profile));
        String meta = profile.displayLocation();
        binding.metaTv.setText(TextUtils.isEmpty(meta) ? getString(R.string.dating_profile_complete_tip) : meta);
        binding.enabledSwitch.setChecked(profile.enabled == 1);
        binding.likeQuotaTv.setText(getString(R.string.dating_like_quota_format,
                DatingQuotaManager.remaining(this, profile, DatingSwipeAction.LIKE),
                DatingQuotaManager.dailyLimit(profile, DatingSwipeAction.LIKE)));
        binding.favoriteQuotaTv.setText(getString(R.string.dating_favorite_quota_format,
                DatingQuotaManager.remaining(this, profile, DatingSwipeAction.FAVORITE),
                DatingQuotaManager.dailyLimit(profile, DatingSwipeAction.FAVORITE)));
        binding.rewindQuotaTv.setText(getString(R.string.dating_rewind_quota_format,
                DatingQuotaManager.rewindRemaining(this)));
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
