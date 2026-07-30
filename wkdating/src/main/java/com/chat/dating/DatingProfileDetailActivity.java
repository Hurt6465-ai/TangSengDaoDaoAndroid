package com.chat.dating;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingProfileDetailBinding;
import com.chat.dating.model.DatingProfile;

import java.util.List;

/** 别人的完整资料页：可滚动白色资料卡，底部固定 3 个动作按钮。 */
public class DatingProfileDetailActivity extends Activity {
    public static final String EXTRA_PROFILE = "dating_profile";
    public static final String EXTRA_PHOTO_INDEX = "dating_photo_index";
    public static final String EXTRA_ACTION = "dating_action";

    private ActivityWkDatingProfileDetailBinding binding;
    private DatingProfile profile;
    private DatingPhotoPagerAdapter photoAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatingUi.applyDarkSystemBars(this, Color.rgb(242, 242, 245));
        binding = ActivityWkDatingProfileDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        DatingUi.applyPageInsets(this, binding.getRoot());
        Object value = getIntent().getSerializableExtra(EXTRA_PROFILE);
        if (!(value instanceof DatingProfile)) {
            finish();
            return;
        }
        profile = (DatingProfile) value;
        initView();
        bindProfile();
    }

    private void initView() {
        binding.passBtn.setOnClickListener(v -> returnAction(DatingSwipeAction.PASS));
        binding.favoriteBtn.setOnClickListener(v -> returnAction(DatingSwipeAction.FAVORITE));
        binding.likeBtn.setOnClickListener(v -> returnAction(DatingSwipeAction.LIKE));
        binding.blockBtn.setOnClickListener(v -> confirmBlock());
        binding.reportBtn.setOnClickListener(v -> confirmReport());

        photoAdapter = new DatingPhotoPagerAdapter();
        photoAdapter.setPhotos(profile.safeDatingPhotos());
        binding.photoPager.setAdapter(photoAdapter);
        binding.photoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.photoCountTv.setText((position + 1) + "/" + Math.max(1, photoAdapter.getItemCount()));
            }
        });
        int index = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_PHOTO_INDEX, 0), photoAdapter.getItemCount() - 1));
        binding.photoPager.setCurrentItem(index, false);
    }

    private void bindProfile() {
        binding.avatarView.setSize(64f);
        binding.avatarView.showAvatarUrl(profile.safeAvatar(), profile.safeUid(), profile.safeName(), profile.safeUid());
        binding.avatarView.showFlag(profile.safeCountryCode());
        binding.nameTv.setText(DatingUi.nameAgeFlag(profile));
        String location = DatingUi.displayLocation(this, profile);
        binding.locationTv.setText(location);
        binding.locationRow.setVisibility(TextUtils.isEmpty(location) ? View.GONE : View.VISIBLE);
        bindSection(binding.aboutTitle, binding.aboutTv, R.string.dating_section_about, profile.safeIntro());
        bindSection(binding.loveTitle, binding.loveTv, R.string.dating_section_relationship, DatingUi.loveExpectation(this, profile));
        bindSection(binding.idealTitle, binding.idealTv, R.string.dating_section_ideal_partner, profile.ideal_partner);
        bindSection(binding.dealbreakersTitle, binding.dealbreakersTv,
                R.string.dating_section_dealbreakers_title,
                TextUtils.join(getString(R.string.dating_list_separator),
                        DatingValueFormatter.dealbreakerLabels(this, profile.safeDealbreakers())));
        bindSection(binding.basicTitle, binding.basicTv, R.string.dating_section_basic, basicLine());
        bindTags(binding.tagsLayout, DatingValueFormatter.displayList(this, profile.safeTags()));
    }

    private String basicLine() {
        return DatingValueFormatter.basicLine(this, profile);
    }

    private void bindSection(TextView title, TextView body, int titleRes, String value) {
        boolean visible = !TextUtils.isEmpty(value);
        title.setText(titleRes);
        title.setVisibility(visible ? View.VISIBLE : View.GONE);
        body.setText(value);
        body.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void bindTags(DatingFlowLayout layout, List<String> tags) {
        layout.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            binding.tagsTitle.setVisibility(View.GONE);
            layout.setVisibility(View.GONE);
            return;
        }
        binding.tagsTitle.setVisibility(View.VISIBLE);
        layout.setVisibility(View.VISIBLE);
        int count = 0;
        for (String item : tags) {
            if (TextUtils.isEmpty(item) || count >= 12) continue;
            TextView tag = new TextView(this);
            tag.setText(item.trim());
            tag.setTextSize(14);
            tag.setTextColor(Color.rgb(65, 65, 72));
            tag.setBackgroundResource(R.drawable.bg_dating_detail_tag);
            tag.setPadding(dp(13), dp(8), dp(13), dp(8));
            android.view.ViewGroup.MarginLayoutParams lp = new android.view.ViewGroup.MarginLayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), dp(8));
            layout.addView(tag, lp);
            count++;
        }
    }

    private void returnAction(String action) {
        Intent result = new Intent();
        result.putExtra(EXTRA_ACTION, action);
        setResult(RESULT_OK, result);
        finish();
    }

    private void confirmBlock() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_block_title)
                .setMessage(R.string.dating_block_message)
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(R.string.dating_block, (dialog, which) -> DatingModel.getInstance().block(profile.safeUid(), (code, msg, data) -> {
                    toast(code == HttpResponseCode.success ? getString(R.string.dating_blocked) : (TextUtils.isEmpty(msg) ? getString(R.string.dating_block_failed) : msg));
                    if (code == HttpResponseCode.success) returnAction(DatingSwipeAction.PASS);
                }))
                .show();
    }

    private void confirmReport() {
        String[] reasons = {getString(R.string.dating_report_fake), getString(R.string.dating_report_harassment), getString(R.string.dating_report_scam), getString(R.string.dating_report_impersonation), getString(R.string.dating_report_other)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_report_reason)
                .setItems(reasons, (dialog, which) -> DatingModel.getInstance().report(profile.safeUid(), reasons[which], "dating_profile", (code, msg, data) ->
                        toast(code == HttpResponseCode.success ? getString(R.string.dating_reported) : (TextUtils.isEmpty(msg) ? getString(R.string.dating_report_failed) : msg))))
                .show();
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
