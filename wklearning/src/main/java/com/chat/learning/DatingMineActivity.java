package com.chat.dating;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingMineBinding;
import com.chat.dating.model.DatingProfile;

import java.util.List;

/** 交友设置页：保留资料、每日额度、发现开关和已接通的功能入口。 */
public class DatingMineActivity extends Activity {
    public static final String EXTRA_PROFILE = "dating_my_profile";
    private static final int REQ_EDIT = 501;

    private ActivityWkDatingMineBinding binding;
    private DatingProfile profile;
    private boolean changed;
    private boolean updatingDiscoverySwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(242, 242, 244));
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
        binding.editProfileRow.setOnClickListener(v -> openEditor());
        binding.previewProfileRow.setOnClickListener(v -> openPreview());

        binding.favoritesRow.setOnClickListener(v -> startActivity(new Intent(this, DatingFavoritesActivity.class)));
        binding.whoLikesRow.setOnClickListener(v -> startActivity(new Intent(this, DatingWhoLikesActivity.class)));
        binding.matchesRow.setOnClickListener(v -> startActivity(new Intent(this, DatingMatchesActivity.class)));
        binding.quotaRow.setOnClickListener(v -> showQuota());

        binding.discoverySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updatingDiscoverySwitch) changeDiscoveryState(isChecked);
        });
    }

    private void openEditor() {
        Intent intent = new Intent(this, DatingEditProfileActivity.class);
        intent.putExtra(DatingEditProfileActivity.EXTRA_PROFILE, profile);
        startActivityForResult(intent, REQ_EDIT);
    }

    private void openPreview() {
        if (profile == null) return;
        if (profile.safeDatingPhotos().isEmpty()) {
            toast(getString(R.string.dating_preview_need_photo));
            openEditor();
            return;
        }
        Intent intent = new Intent(this, DatingProfileDetailActivity.class);
        intent.putExtra(DatingProfileDetailActivity.EXTRA_PROFILE, profile);
        intent.putExtra(DatingProfileDetailActivity.EXTRA_PHOTO_INDEX, 0);
        intent.putExtra(DatingProfileDetailActivity.EXTRA_PREVIEW_ONLY, true);
        startActivity(intent);
    }

    private void loadProfile() {
        DatingModel.getInstance().getMyDatingProfile((code, msg, data) -> {
            if (isFinishing() || isDestroyed() || binding == null) return;
            if (data != null) {
                profile = data;
                bindProfile();
            } else {
                toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_profile_load_error) : msg);
            }
        });
    }

    private void bindProfile() {
        if (profile == null || binding == null) return;
        binding.nameTv.setText(DatingUi.nameAgeFlag(profile));
        String meta = DatingUi.displayLocation(this, profile);
        binding.metaTv.setText(TextUtils.isEmpty(meta) ? getString(R.string.dating_profile_complete_tip) : meta);
        binding.profileProgressTv.setText(getString(R.string.dating_profile_completion, profileCompletion(profile)));

        List<String> photos = profile.safeDatingPhotos();
        String cover = photos.isEmpty() ? profile.safeAvatar() : photos.get(0);
        boolean hasCover = !TextUtils.isEmpty(cover);
        binding.coverPlaceholderTv.setVisibility(hasCover ? View.GONE : View.VISIBLE);
        if (hasCover) {
            Glide.with(this)
                    .load(DatingImageSource.resolve(this, cover))
                    .centerCrop()
                    .into(binding.coverIv);
        } else {
            Glide.with(this).clear(binding.coverIv);
            binding.coverIv.setImageDrawable(null);
        }

        binding.likeQuotaTv.setText(getString(R.string.dating_like_quota_format,
                DatingQuotaManager.remaining(this, profile, DatingSwipeAction.LIKE),
                DatingQuotaManager.dailyLimit(profile, DatingSwipeAction.LIKE)));
        binding.favoriteQuotaTv.setText(getString(R.string.dating_favorite_quota_format,
                DatingQuotaManager.remaining(this, profile, DatingSwipeAction.FAVORITE),
                DatingQuotaManager.dailyLimit(profile, DatingSwipeAction.FAVORITE)));
        binding.rewindQuotaTv.setText(getString(R.string.dating_rewind_quota_format,
                DatingQuotaManager.rewindRemaining(this)));

        updatingDiscoverySwitch = true;
        binding.discoverySwitch.setChecked(profile.enabled == 1);
        binding.discoverySwitch.setEnabled(true);
        updatingDiscoverySwitch = false;
    }

    private int profileCompletion(DatingProfile value) {
        if (value.profile_score > 0) return Math.max(0, Math.min(100, value.profile_score));
        int score = 0;
        if (!value.safeDatingPhotos().isEmpty()) score += 35;
        if (value.safeDatingPhotos().size() >= 3) score += 10;
        if (value.age >= 18) score += 10;
        if (!TextUtils.isEmpty(value.safeRelationshipGoal())) score += 12;
        if (!TextUtils.isEmpty(value.safeIntro())) score += 12;
        if (!TextUtils.isEmpty(value.country_code)) score += 5;
        if (!value.safeTags().isEmpty()) score += 8;
        if (value.height_cm > 0 || value.weight_kg > 0) score += 4;
        if (!TextUtils.isEmpty(value.job_status) || !TextUtils.isEmpty(value.education)) score += 4;
        if (!TextUtils.isEmpty(value.relationship_status) || !TextUtils.isEmpty(value.sexual_orientation)) score += 5;
        if (!TextUtils.isEmpty(value.ideal_partner) || !value.safeDealbreakers().isEmpty()) score += 5;
        return Math.max(0, Math.min(100, score));
    }

    private void changeDiscoveryState(boolean enabled) {
        if (profile == null) {
            setDiscoveryChecked(false);
            return;
        }
        boolean current = profile.enabled == 1;
        if (enabled == current) return;
        if (enabled && !canEnableDiscovery()) {
            setDiscoveryChecked(false);
            toast(getString(R.string.dating_complete_before_open));
            openEditor();
            return;
        }

        binding.discoverySwitch.setEnabled(false);
        DatingModel.getInstance().enableProfile(enabled, (code, msg, data) -> {
            if (isFinishing() || isDestroyed() || binding == null) return;
            binding.discoverySwitch.setEnabled(true);
            if (code == HttpResponseCode.success && data != null) {
                profile = data;
                changed = true;
                bindProfile();
                toast(getString(profile.enabled == 1 ? R.string.dating_enabled : R.string.dating_disabled));
            } else {
                setDiscoveryChecked(current);
                toast(TextUtils.isEmpty(msg) ? getString(R.string.dating_action_failed) : msg);
            }
        });
    }

    private boolean canEnableDiscovery() {
        if (profile == null) return false;
        return profile.complete || (!profile.safeDatingPhotos().isEmpty()
                && profile.age >= 18
                && profile.hasKnownSex()
                && !TextUtils.isEmpty(profile.safeRelationshipGoal()));
    }

    private void setDiscoveryChecked(boolean checked) {
        updatingDiscoverySwitch = true;
        binding.discoverySwitch.setChecked(checked);
        updatingDiscoverySwitch = false;
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
