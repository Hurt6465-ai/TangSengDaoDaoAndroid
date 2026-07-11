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
        photoAdapter.setPhotos(profile.safePhotos());
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
        binding.nameTv.setText(DatingUi.nameAgeFlag(profile));
        String location = profile.displayLocation();
        binding.locationTv.setText(location);
        binding.locationTv.setVisibility(TextUtils.isEmpty(location) ? View.GONE : View.VISIBLE);
        bindSection(binding.aboutTitle, binding.aboutTv, "关于我", profile.safeIntro());
        bindSection(binding.loveTitle, binding.loveTv, "恋爱期待", DatingUi.loveExpectation(profile));
        bindSection(binding.idealTitle, binding.idealTv, "希望对方", profile.ideal_partner);
        bindSection(binding.dealbreakersTitle, binding.dealbreakersTv, "我反感", TextUtils.join("、", profile.safeDealbreakers()));
        bindSection(binding.basicTitle, binding.basicTv, "基本资料", basicLine());
        bindTags(binding.tagsLayout, profile.safeTags());
    }

    private String basicLine() {
        StringBuilder out = new StringBuilder();
        if (!TextUtils.isEmpty(profile.job)) out.append(profile.job);
        if (!TextUtils.isEmpty(profile.education)) {
            if (out.length() > 0) out.append(" · ");
            out.append(profile.education);
        }
        if (profile.height_cm > 0) {
            if (out.length() > 0) out.append(" · ");
            out.append(profile.height_cm).append("cm");
        }
        if (profile.weight_kg > 0) {
            if (out.length() > 0) out.append(" · ");
            out.append(profile.weight_kg).append("kg");
        }
        if (!TextUtils.isEmpty(profile.relationship_status)) {
            if (out.length() > 0) out.append(" · ");
            out.append(profile.relationship_status);
        }
        if (!TextUtils.isEmpty(profile.sexual_orientation)) {
            if (out.length() > 0) out.append(" · ");
            out.append(profile.sexual_orientation);
        }
        if (!TextUtils.isEmpty(profile.drinking)) {
            if (out.length() > 0) out.append(" · ");
            out.append("饮酒").append(profile.drinking);
        }
        if (!TextUtils.isEmpty(profile.smoking)) {
            if (out.length() > 0) out.append(" · ");
            out.append("吸烟").append(profile.smoking);
        }
        return out.toString();
    }

    private void bindSection(TextView title, TextView body, String titleText, String value) {
        boolean visible = !TextUtils.isEmpty(value);
        title.setText(titleText);
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
                .setTitle("屏蔽此人？")
                .setMessage("屏蔽后将不再向你推荐此人。")
                .setNegativeButton("取消", null)
                .setPositiveButton("屏蔽", (dialog, which) -> DatingModel.getInstance().block(profile.safeUid(), (code, msg, data) -> {
                    toast(code == HttpResponseCode.success ? "已屏蔽" : (TextUtils.isEmpty(msg) ? "屏蔽失败" : msg));
                    if (code == HttpResponseCode.success) returnAction(DatingSwipeAction.PASS);
                }))
                .show();
    }

    private void confirmReport() {
        String[] reasons = {"虚假资料", "色情或骚扰", "诈骗或引流", "冒用他人照片", "其他"};
        new AlertDialog.Builder(this)
                .setTitle("举报原因")
                .setItems(reasons, (dialog, which) -> DatingModel.getInstance().report(profile.safeUid(), reasons[which], "dating_profile", (code, msg, data) ->
                        toast(code == HttpResponseCode.success ? "已提交举报" : (TextUtils.isEmpty(msg) ? "举报失败" : msg))))
                .show();
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
