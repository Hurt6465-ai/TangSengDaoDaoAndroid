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
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.AndroidUtilities;
import com.chat.partnerbrowse.databinding.FragmentWkPartnerDetailBinding;
import com.chat.partnerbrowse.model.PartnerBrowseBean;
import com.chat.partnerbrowse.model.PartnerGreetingResponse;

import java.util.ArrayList;
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
        binding.langGroup.setVisibility(View.GONE);
        binding.lastOnlineTv.setVisibility(View.GONE);
        binding.nearbyGroup.setVisibility(View.GONE);
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
        binding.langGroup.setVisibility(View.GONE);
        binding.lastOnlineTv.setVisibility(View.GONE);
        binding.nearbyGroup.setVisibility(View.GONE);
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
        binding.viewPagerInner.setOverScrollMode(View.OVER_SCROLL_NEVER);
        View innerPagerRecycler = binding.viewPagerInner.getChildCount() > 0 ? binding.viewPagerInner.getChildAt(0) : null;
        if (innerPagerRecycler instanceof RecyclerView) {
            innerPagerRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
            RecyclerView.ItemAnimator animator = ((RecyclerView) innerPagerRecycler).getItemAnimator();
            if (animator != null) animator.setChangeDuration(0);
        }
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
        binding.ageSexTv.setVisibility(View.GONE);

        bindLanguages();

        String lastOnline = buildLastOnline();
        binding.lastOnlineTv.setText(lastOnline);
        binding.lastOnlineTv.setVisibility(TextUtils.isEmpty(lastOnline) ? View.GONE : View.VISIBLE);

        String nearby = partner.getNearbyLabel(getContext());
        binding.nearbyGroup.setVisibility(TextUtils.isEmpty(nearby) ? View.GONE : View.VISIBLE);
        binding.nearbyTv.setText(nearby);

        bindTags(partner.getTagsSafe());
        binding.introTv.setText(TextUtils.isEmpty(partner.intro) ? getString(R.string.partnerbrowse_intro_empty) : partner.intro);
        updateActionButton();
        binding.actionBtn.setOnClickListener(v -> onActionClick());

        View.OnClickListener openProfile = v -> openProfilePage();
        binding.avatarView.setOnClickListener(openProfile);
        binding.nameTv.setOnClickListener(openProfile);
        binding.nativeLangTv.setOnClickListener(openProfile);
        binding.learningLangTv.setOnClickListener(openProfile);
        binding.langGroup.setOnClickListener(openProfile);
        binding.lastOnlineTv.setOnClickListener(openProfile);
        binding.introTv.setOnClickListener(openProfile);
    }

    private void updateActionButton() {
        if (binding == null || partner == null) return;
        binding.actionBtn.setAlpha(partner.isHelloSent() ? 0.55f : 1f);
        binding.actionBtn.setText(partner.follow == 1 ? R.string.partnerbrowse_send_message : (partner.isHelloSent() ? R.string.partnerbrowse_hello_sent : R.string.partnerbrowse_say_hello));
        binding.actionBtn.setEnabled(!partner.isHelloSent() || partner.follow == 1);
    }

    private void bindLanguages() {
        if (binding == null || partner == null) return;
        String nativeLang = join(partner.getNativeLanguagesSafe());
        String learningLang = join(partner.getLearningLanguagesSafe());
        boolean hasNative = !TextUtils.isEmpty(nativeLang);
        boolean hasLearning = !TextUtils.isEmpty(learningLang);
        binding.langGroup.setVisibility(hasNative || hasLearning ? View.VISIBLE : View.GONE);
        binding.nativeLangTv.setText(nativeLang);
        binding.learningLangTv.setText(learningLang);
        binding.nativeLangTv.setVisibility(hasNative ? View.VISIBLE : View.GONE);
        binding.learningLangTv.setVisibility(hasLearning ? View.VISIBLE : View.GONE);
        binding.langExchangeIv.setVisibility(hasNative && hasLearning ? View.VISIBLE : View.GONE);
    }

    private void bindTags(List<String> tags) {
        if (binding == null) return;
        binding.tagRow.removeAllViews();
        List<String> clean = cleanTags(tags);
        if (clean.isEmpty()) {
            binding.tagRow.setVisibility(View.GONE);
            return;
        }
        binding.tagRow.setVisibility(View.VISIBLE);
        int max = Math.min(clean.size(), 3);
        for (int i = 0; i < max; i++) {
            addTagChip(clean.get(i));
        }
        if (clean.size() > max) addTagChip("+" + (clean.size() - max));
    }

    private List<String> cleanTags(List<String> tags) {
        ArrayList<String> out = new ArrayList<>();
        if (tags == null) return out;
        for (String tag : tags) {
            if (TextUtils.isEmpty(tag)) continue;
            String clean = tag.trim();
            if (TextUtils.isEmpty(clean) || out.contains(clean)) continue;
            out.add(clean);
        }
        return out;
    }

    private void addTagChip(String tag) {
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

    private void openProfilePage() {
        if (!isViewAlive() || partner == null || TextUtils.isEmpty(partner.uid)) return;
        Context context = getContext();
        if (context != null) PartnerBrowseHostBridge.openProfile(context, partner.uid);
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
        PartnerBrowseModel.getInstance().sendGreeting(target.uid, getString(R.string.partnerbrowse_default_hello_plain), (code, msg, data) -> {
            if (!isViewAlive() || partner != target) return;
            boolean success = code == HttpResponseCode.success && (data == null || data.isSuccessOrAlreadySent());
            if (success) {
                target.markHelloSent();
                PartnerRepository.putOne(target);
                updateActionButton();
                if (data != null && !TextUtils.isEmpty(data.getMessageSafe())) {
                    Toast.makeText(requireContext(), data.getMessageSafe(), Toast.LENGTH_SHORT).show();
                }
            } else {
                binding.actionBtn.setEnabled(true);
                String error = data == null ? msg : data.getMessageSafe();
                if (!TextUtils.isEmpty(error)) Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
