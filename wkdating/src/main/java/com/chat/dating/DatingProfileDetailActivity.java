package com.chat.dating;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingProfileDetailBinding;
import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.List;

/** 别人的完整资料页：照片优先，资料分区展示，避免把所有字段挤在一行。 */
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
        binding.backBtn.setOnClickListener(v -> finish());
        binding.moreBtn.setOnClickListener(this::showMoreMenu);
        binding.passBtn.setOnClickListener(v -> returnAction(DatingSwipeAction.PASS));
        binding.favoriteBtn.setOnClickListener(v -> returnAction(DatingSwipeAction.FAVORITE));
        binding.likeBtn.setOnClickListener(v -> returnAction(DatingSwipeAction.LIKE));

        photoAdapter = new DatingPhotoPagerAdapter();
        photoAdapter.setPhotos(profile.safeDatingPhotos());
        binding.photoPager.setAdapter(photoAdapter);
        binding.photoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.photoCountTv.setText((position + 1) + "/" + Math.max(1, photoAdapter.getItemCount()));
            }
        });
        int max = Math.max(0, photoAdapter.getItemCount() - 1);
        int index = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_PHOTO_INDEX, 0), max));
        binding.photoPager.setCurrentItem(index, false);
    }

    private void bindProfile() {
        binding.nameTv.setText(profile.safeName());
        binding.ageTv.setText(profile.age > 0 ? String.valueOf(profile.age) : "");
        binding.ageTv.setVisibility(profile.age > 0 ? View.VISIBLE : View.GONE);
        bindOptionalAvatarView();

        String location = DatingUi.displayLocation(this, profile);
        binding.locationTv.setText(location);
        binding.locationRow.setVisibility(TextUtils.isEmpty(location) ? View.GONE : View.VISIBLE);

        bindSection(binding.aboutTitle, binding.aboutTv, R.string.dating_section_about, profile.safeIntro());
        bindSection(binding.loveTitle, binding.loveTv, R.string.dating_section_relationship,
                DatingUi.loveExpectation(this, profile));
        bindSection(binding.idealTitle, binding.idealTv, R.string.dating_section_ideal_partner,
                profile.ideal_partner);
        bindSection(binding.dealbreakersTitle, binding.dealbreakersTv,
                R.string.dating_section_dealbreakers_title,
                TextUtils.join(getString(R.string.dating_list_separator),
                        DatingValueFormatter.dealbreakerLabels(this, profile.safeDealbreakers())));

        bindBasicRows();
        bindLanguages();
        bindInterests();
    }

    /**
     * 兼容新旧布局：部分版本的详情页已移除 avatar_view。
     * 使用反射避免 ActivityWkDatingProfileDetailBinding 在没有该字段时编译失败；
     * 若后续布局恢复 avatar_view，本方法仍会自动完成头像和国旗绑定。
     */
    private void bindOptionalAvatarView() {
        try {
            java.lang.reflect.Field field = binding.getClass().getField("avatarView");
            Object avatarView = field.get(binding);
            if (avatarView == null) return;

            avatarView.getClass().getMethod("setSize", float.class).invoke(avatarView, 64f);
            avatarView.getClass().getMethod("showAvatarUrl",
                            String.class, String.class, String.class, String.class)
                    .invoke(avatarView, profile.safeAvatar(), profile.safeUid(),
                            profile.safeName(), profile.safeUid());
            avatarView.getClass().getMethod("showFlag", String.class)
                    .invoke(avatarView, profile.safeCountryCode());
        } catch (NoSuchFieldException ignored) {
            // 当前布局没有 avatar_view，照片轮播已承担主要头像展示，直接跳过。
        } catch (ReflectiveOperationException ignored) {
            // 控件版本方法签名不一致时不影响资料页其余内容。
        }
    }

    private void bindBasicRows() {
        binding.basicRows.removeAllViews();
        int count = 0;
        count += addInfoRow(binding.basicRows, R.string.dating_basic_relationship,
                DatingSharedProfileFormatter.display(this, profile.relationship_status));
        count += addInfoRow(binding.basicRows, R.string.dating_basic_orientation,
                DatingValueFormatter.orientation(this, profile.sexual_orientation));

        ArrayList<String> body = new ArrayList<>();
        if (profile.height_cm > 0) body.add(profile.height_cm + " cm");
        if (profile.weight_kg > 0) body.add(profile.weight_kg + " kg");
        count += addInfoRow(binding.basicRows, R.string.dating_basic_body,
                TextUtils.join(getString(R.string.dating_meta_separator), body));

        ArrayList<String> habits = new ArrayList<>();
        if (!TextUtils.isEmpty(profile.drinking)) {
            habits.add(getString(R.string.dating_drinking_value,
                    DatingValueFormatter.drinking(this, profile.drinking)));
        }
        if (!TextUtils.isEmpty(profile.smoking)) {
            habits.add(getString(R.string.dating_smoking_value,
                    DatingValueFormatter.smoking(this, profile.smoking)));
        }
        count += addInfoRow(binding.basicRows, R.string.dating_basic_lifestyle,
                TextUtils.join(getString(R.string.dating_meta_separator), habits));

        String job = TextUtils.isEmpty(profile.job_status) ? profile.job : profile.job_status;
        count += addInfoRow(binding.basicRows, R.string.dating_basic_job,
                DatingSharedProfileFormatter.display(this, job));
        count += addInfoRow(binding.basicRows, R.string.dating_basic_education,
                DatingSharedProfileFormatter.display(this, profile.education));

        boolean visible = count > 0;
        binding.basicTitle.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.basicRows.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private int addInfoRow(LinearLayout parent, int labelRes, String value) {
        if (TextUtils.isEmpty(value)) return 0;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_dating_detail_info_row);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextColor(Color.rgb(132, 132, 143));
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(dp(86),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(label, labelLp);

        TextView content = new TextView(this);
        content.setText(value);
        content.setTextColor(Color.rgb(45, 45, 52));
        content.setTextSize(14);
        content.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(content, contentLp);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) rowLp.topMargin = dp(8);
        parent.addView(row, rowLp);
        return 1;
    }

    private void bindLanguages() {
        ArrayList<String> lines = new ArrayList<>();
        String nativeLanguages = DatingSharedProfileFormatter.joinDisplay(this, profile.safeNativeLanguages());
        String learningLanguages = DatingSharedProfileFormatter.joinDisplay(this, profile.safeLearningLanguages());
        if (!TextUtils.isEmpty(nativeLanguages)) {
            lines.add(getString(R.string.dating_native_languages_format, nativeLanguages));
        }
        if (!TextUtils.isEmpty(learningLanguages)) {
            lines.add(getString(R.string.dating_learning_languages_format, learningLanguages));
        }
        String text = TextUtils.join("\n", lines);
        binding.languagesTitle.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        binding.languagesTv.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        binding.languagesTv.setText(text);
    }

    private void bindSection(TextView title, TextView body, int titleRes, String value) {
        boolean visible = !TextUtils.isEmpty(value);
        title.setText(titleRes);
        title.setVisibility(visible ? View.VISIBLE : View.GONE);
        body.setText(value);
        body.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void bindInterests() {
        binding.interestsRows.removeAllViews();
        int count = 0;
        count += addInfoRow(binding.interestsRows, R.string.dating_shared_personality_label,
                DatingSharedProfileFormatter.joinDisplay(this, profile.safePersonalityTags()));
        count += addInfoRow(binding.interestsRows, R.string.dating_shared_pets,
                DatingSharedProfileFormatter.joinDisplay(this, profile.safePetTags()));
        count += addInfoRow(binding.interestsRows, R.string.dating_shared_sports,
                DatingSharedProfileFormatter.joinDisplay(this, profile.safeSportTags()));
        count += addInfoRow(binding.interestsRows, R.string.dating_shared_movies,
                DatingSharedProfileFormatter.joinDisplay(this, profile.safeMovieTags()));

        ArrayList<String> other = new ArrayList<>();
        if (profile.tags != null) {
            for (String raw : profile.tags) {
                if (TextUtils.isEmpty(raw)) continue;
                String lower = raw.trim().toLowerCase(java.util.Locale.US);
                if (lower.startsWith("relationship_") || lower.startsWith("personality_")
                        || lower.startsWith("pet_") || lower.startsWith("sport_")
                        || lower.startsWith("movie_") || lower.startsWith("job_")
                        || lower.startsWith("education_") || lower.startsWith("cross:")) continue;
                String label = DatingSharedProfileFormatter.display(this, raw);
                if (!TextUtils.isEmpty(label) && !other.contains(label)) other.add(label);
            }
        }
        count += addInfoRow(binding.interestsRows, R.string.dating_shared_other_tags,
                TextUtils.join(getString(R.string.dating_list_separator), other));
        boolean visible = count > 0;
        binding.tagsTitle.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.interestsRows.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.dating_block);
        menu.getMenu().add(0, 2, 1, R.string.dating_report);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                confirmBlock();
                return true;
            }
            if (item.getItemId() == 2) {
                confirmReport();
                return true;
            }
            return false;
        });
        menu.show();
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
                .setPositiveButton(R.string.dating_block, (dialog, which) ->
                        DatingModel.getInstance().block(profile.safeUid(), (code, msg, data) -> {
                            toast(code == HttpResponseCode.success
                                    ? getString(R.string.dating_blocked)
                                    : (TextUtils.isEmpty(msg) ? getString(R.string.dating_block_failed) : msg));
                            if (code == HttpResponseCode.success) returnAction(DatingSwipeAction.PASS);
                        }))
                .show();
    }

    private void confirmReport() {
        String[] reasons = {
                getString(R.string.dating_report_fake),
                getString(R.string.dating_report_harassment),
                getString(R.string.dating_report_scam),
                getString(R.string.dating_report_impersonation),
                getString(R.string.dating_report_other)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_report_reason)
                .setItems(reasons, (dialog, which) ->
                        DatingModel.getInstance().report(profile.safeUid(), reasons[which],
                                "dating_profile", (code, msg, data) ->
                                        toast(code == HttpResponseCode.success
                                                ? getString(R.string.dating_reported)
                                                : (TextUtils.isEmpty(msg)
                                                ? getString(R.string.dating_report_failed) : msg))))
                .show();
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
