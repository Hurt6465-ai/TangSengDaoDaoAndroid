package com.chat.partnerbrowse;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.AndroidUtilities;
import com.chat.partnerbrowse.databinding.FragmentWkPartnerDetailBinding;
import com.chat.partnerbrowse.model.PartnerBrowseBean;

import java.util.List;
import java.util.Locale;

public class PartnerDetailFragment extends Fragment {
    private static final String KEY_STABLE = "stable_key";

    private FragmentWkPartnerDetailBinding binding;
    private PartnerBrowseBean partner;
    private ViewPager2.OnPageChangeCallback imagePageCallback;
    private String stableKey;

    public PartnerDetailFragment() {
        super(R.layout.fragment_wk_partner_detail);
    }

    public static PartnerDetailFragment newInstance(String key, @Nullable PartnerBrowseBean bean) {
        PartnerDetailFragment fragment = new PartnerDetailFragment();
        Bundle args = bean == null ? new Bundle() : bean.toBundle(key);
        args.putString(KEY_STABLE, key == null ? "" : key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentWkPartnerDetailBinding.bind(view);
        stableKey = getArguments() == null ? "" : getArguments().getString(KEY_STABLE, "");
        partner = TextUtils.isEmpty(stableKey) ? null : PartnerRepository.getPartnerFromCache(stableKey);
        if (partner == null) partner = PartnerBrowseBean.fromBundle(getArguments());
        if (partner == null) {
            bindLoadingState();
            reloadProfileByKey();
            return;
        }
        PartnerRepository.putOne(partner);
        bindImages(partner.getDisplayImagesSafe());
        bindUserInfo();
    }

    @Override
    public void onDestroyView() {
        try {
            if (binding != null && imagePageCallback != null) {
                binding.viewPagerInner.unregisterOnPageChangeCallback(imagePageCallback);
            }
            if (binding != null) binding.viewPagerInner.setAdapter(null);
        } catch (Throwable ignored) {
        }
        imagePageCallback = null;
        binding = null;
        super.onDestroyView();
    }

    private boolean isViewAlive() {
        return isAdded() && binding != null && getView() != null;
    }

    private void bindLoadingState() {
        if (binding == null) return;
        binding.indicatorTv.setVisibility(View.GONE);
        binding.avatarView.setVisibility(View.GONE);
        binding.nameTv.setText(R.string.partnerbrowse_loading);
        binding.ageSexTv.setVisibility(View.GONE);
        binding.nearbyGroup.setVisibility(View.GONE);
        binding.langTv.setVisibility(View.GONE);
        binding.lastOnlineTv.setVisibility(View.GONE);
        binding.tagRow.removeAllViews();
        binding.tagRow.setVisibility(View.GONE);
        binding.introTv.setText(R.string.partnerbrowse_retry_tip);
        binding.actionBtn.setText(R.string.partnerbrowse_retry);
        binding.actionBtn.setEnabled(true);
        binding.actionBtn.setAlpha(1f);
        binding.actionBtn.setOnClickListener(v -> reloadProfileByKey());
    }

    private void bindEmptyState() {
        if (binding == null) return;
        binding.indicatorTv.setVisibility(View.GONE);
        binding.avatarView.setVisibility(View.GONE);
        binding.nameTv.setText(R.string.partnerbrowse_empty);
        binding.ageSexTv.setVisibility(View.GONE);
        binding.nearbyGroup.setVisibility(View.GONE);
        binding.langTv.setVisibility(View.GONE);
        binding.lastOnlineTv.setVisibility(View.GONE);
        binding.tagRow.removeAllViews();
        binding.tagRow.setVisibility(View.GONE);
        binding.introTv.setText(R.string.partnerbrowse_intro_empty);
        binding.actionBtn.setText(R.string.partnerbrowse_retry);
        binding.actionBtn.setEnabled(true);
        binding.actionBtn.setAlpha(1f);
        binding.actionBtn.setOnClickListener(v -> reloadProfileByKey());
    }

    private void reloadProfileByKey() {
        if (TextUtils.isEmpty(stableKey)) {
            bindEmptyState();
            return;
        }
        PartnerBrowseModel.getInstance().getPartnerProfile(stableKey, (code, msg, data) -> {
            if (!isViewAlive()) return;
            if (code == HttpResponseCode.success && data != null) {
                partner = data;
                PartnerRepository.putOne(data);
                bindImages(data.getDisplayImagesSafe());
                bindUserInfo();
            } else {
                bindEmptyState();
            }
        });
    }

    private void bindImages(List<String> images) {
        if (binding == null) return;
        PartnerImageAdapter imageAdapter = new PartnerImageAdapter(images);
        binding.viewPagerInner.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        binding.viewPagerInner.setOffscreenPageLimit(1);
        binding.viewPagerInner.setSaveEnabled(false);
        binding.viewPagerInner.setAdapter(imageAdapter);
        updateIndicator(0, imageAdapter.getItemCount());
        if (imagePageCallback != null) binding.viewPagerInner.unregisterOnPageChangeCallback(imagePageCallback);
        imagePageCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (!isViewAlive()) return;
                Context context = getContext();
                if (context != null) PartnerImagePreloader.preloadNextImage(context, images, position);
                updateIndicator(position, imageAdapter.getItemCount());
            }
        };
        binding.viewPagerInner.registerOnPageChangeCallback(imagePageCallback);
    }

    private void bindUserInfo() {
        if (binding == null || partner == null) return;
        binding.avatarView.setVisibility(View.VISIBLE);
        binding.avatarView.setSize(58);
        binding.avatarView.showAvatarUrl(partner.getAvatarPathSafe(), partner.avatar_cache_key, partner.getNameSafe(), partner.getStableKey());
        binding.avatarView.showFlag(partner.country_code);

        binding.nameTv.setText(partner.getNameSafe());
        String ageSex = buildAgeSex();
        binding.ageSexTv.setText(ageSex);
        binding.ageSexTv.setVisibility(TextUtils.isEmpty(ageSex) ? View.GONE : View.VISIBLE);

        String lang = buildLanguages();
        binding.langTv.setText(lang);
        binding.langTv.setVisibility(TextUtils.isEmpty(lang) ? View.GONE : View.VISIBLE);

        String lastOnline = buildLastOnline();
        binding.lastOnlineTv.setText(lastOnline);
        binding.lastOnlineTv.setVisibility(TextUtils.isEmpty(lastOnline) ? View.GONE : View.VISIBLE);

        String nearby = partner.getNearbyLabel();
        binding.nearbyGroup.setVisibility(TextUtils.isEmpty(nearby) ? View.GONE : View.VISIBLE);
        binding.nearbyTv.setText(nearby);

        bindTags(partner.getTagsSafe());
        binding.introTv.setText(TextUtils.isEmpty(partner.intro) ? getString(R.string.partnerbrowse_intro_empty) : partner.intro);
        binding.actionBtn.setAlpha(partner.isHelloSent() ? 0.55f : 1f);
        binding.actionBtn.setText(partner.follow == 1 ? R.string.partnerbrowse_send_message : (partner.isHelloSent() ? R.string.partnerbrowse_hello_sent : R.string.partnerbrowse_say_hello));
        binding.actionBtn.setEnabled(!partner.isHelloSent() || partner.follow == 1);
        binding.actionBtn.setOnClickListener(v -> onActionClick());

        View.OnClickListener openProfile = v -> openProfilePage();
        binding.avatarView.setOnClickListener(openProfile);
        binding.nameTv.setOnClickListener(openProfile);
        binding.ageSexTv.setOnClickListener(openProfile);
        binding.langTv.setOnClickListener(openProfile);
        binding.lastOnlineTv.setOnClickListener(openProfile);
        binding.introTv.setOnClickListener(openProfile);
    }

    private void bindTags(List<String> tags) {
        if (binding == null) return;
        binding.tagRow.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            binding.tagRow.setVisibility(View.GONE);
            return;
        }
        binding.tagRow.setVisibility(View.VISIBLE);
        int max = Math.min(tags.size(), 5);
        for (int i = 0; i < max; i++) {
            String tag = tags.get(i);
            if (TextUtils.isEmpty(tag)) continue;
            TextView chip = new TextView(requireContext());
            chip.setText(tag);
            chip.setTextColor(0xFFFFFFFF);
            chip.setTextSize(12f);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setBackgroundResource(R.drawable.bg_partnerbrowse_tag_chip);
            chip.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(24));
            lp.rightMargin = AndroidUtilities.dp(7);
            binding.tagRow.addView(chip, lp);
        }
    }

    private void openProfilePage() {
        if (!isViewAlive() || partner == null || TextUtils.isEmpty(partner.uid)) return;
        Context context = getContext();
        if (context != null) PartnerBrowseHostBridge.openProfile(context, partner.uid);
    }

    private String buildAgeSex() {
        if (partner == null) return "";
        String sex = "";
        if (partner.sex == 1) sex = "♂";
        else if (partner.sex == 0) sex = "♀";
        if (partner.age > 0 && !TextUtils.isEmpty(sex)) return sex + " " + partner.age;
        if (partner.age > 0) return String.valueOf(partner.age);
        return sex;
    }

    private String buildLanguages() {
        if (partner == null) return "";
        String left = join(partner.getNativeLanguagesSafe());
        String right = join(partner.getLearningLanguagesSafe());
        if (TextUtils.isEmpty(left) && TextUtils.isEmpty(right)) return "";
        if (TextUtils.isEmpty(left)) return right;
        if (TextUtils.isEmpty(right)) return left;
        return left + "  ↔  " + right;
    }

    private String buildLastOnline() {
        if (partner == null) return "";
        if (partner.online == 1) return getString(R.string.partnerbrowse_online_now);
        long last = partner.getLastActiveMillisSafe();
        if (last <= 0) return "";
        long diff = Math.max(0, System.currentTimeMillis() - last);
        long minute = diff / 60000L;
        if (minute < 1) return getString(R.string.partnerbrowse_last_online_just_now);
        if (minute < 60) return getString(R.string.partnerbrowse_last_online_format, minute + getString(R.string.partnerbrowse_minutes_ago));
        long hour = minute / 60L;
        if (hour < 24) return getString(R.string.partnerbrowse_last_online_format, hour + getString(R.string.partnerbrowse_hours_ago));
        long day = hour / 24L;
        return getString(R.string.partnerbrowse_last_online_format, day + getString(R.string.partnerbrowse_days_ago));
    }

    private String join(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            if (TextUtils.isEmpty(item)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(item.toUpperCase(Locale.US));
        }
        return sb.toString();
    }

    private void updateIndicator(int position, int total) {
        if (binding == null) return;
        if (total <= 1) {
            binding.indicatorTv.setVisibility(View.GONE);
        } else {
            binding.indicatorTv.setVisibility(View.VISIBLE);
            binding.indicatorTv.setText((position + 1) + "/" + total);
        }
    }

    private void onActionClick() {
        if (!isViewAlive() || partner == null || TextUtils.isEmpty(partner.uid)) return;
        final PartnerBrowseBean target = partner;
        if (target.follow == 1) {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            PartnerBrowseHostBridge.openChat(activity, target.uid);
            return;
        }
        binding.actionBtn.setEnabled(false);
        PartnerBrowseHostBridge.applyAddFriend(requireContext(), target.uid, target.vercode, getString(R.string.partnerbrowse_default_hello_plain), (success, msg) -> {
            if (!isViewAlive() || partner != target) return;
            if (success) {
                target.markHelloSent();
                binding.actionBtn.setText(R.string.partnerbrowse_hello_sent);
                binding.actionBtn.setEnabled(false);
                binding.actionBtn.setAlpha(0.55f);
            } else {
                binding.actionBtn.setEnabled(true);
                if (!TextUtils.isEmpty(msg)) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
