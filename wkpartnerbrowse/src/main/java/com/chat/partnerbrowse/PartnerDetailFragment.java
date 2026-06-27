package com.chat.partnerbrowse;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.partnerbrowse.databinding.FragmentWkPartnerDetailBinding;
import com.chat.partnerbrowse.model.PartnerBrowseBean;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.service.FriendModel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.List;

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
        if (binding != null && imagePageCallback != null) {
            binding.viewPagerInner.unregisterOnPageChangeCallback(imagePageCallback);
        }
        if (binding != null) binding.viewPagerInner.setAdapter(null);
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
        binding.nameTv.setText(R.string.partnerbrowse_loading);
        binding.metaTv.setVisibility(View.GONE);
        binding.nearbyGroup.setVisibility(View.GONE);
        binding.langTv.setVisibility(View.GONE);
        binding.introTv.setText(R.string.partnerbrowse_retry_tip);
        binding.actionBtn.setText(R.string.partnerbrowse_retry);
        binding.actionBtn.setEnabled(true);
        binding.actionBtn.setAlpha(1f);
        binding.actionBtn.setOnClickListener(v -> reloadProfileByKey());
    }

    private void bindEmptyState() {
        if (binding == null) return;
        binding.indicatorTv.setVisibility(View.GONE);
        binding.nameTv.setText(R.string.partnerbrowse_empty);
        binding.metaTv.setVisibility(View.GONE);
        binding.nearbyGroup.setVisibility(View.GONE);
        binding.langTv.setVisibility(View.GONE);
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
        binding.nameTv.setText(partner.getNameSafe());
        String meta = buildMeta();
        binding.metaTv.setText(meta);
        binding.metaTv.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);
        String lang = buildLanguages();
        binding.langTv.setText(lang);
        binding.langTv.setVisibility(TextUtils.isEmpty(lang) ? View.GONE : View.VISIBLE);
        binding.introTv.setText(TextUtils.isEmpty(partner.intro) ? getString(R.string.partnerbrowse_intro_empty) : partner.intro);
        String nearby = partner.getNearbyLabel();
        binding.nearbyGroup.setVisibility(TextUtils.isEmpty(nearby) ? View.GONE : View.VISIBLE);
        binding.nearbyTv.setText(nearby);
        binding.actionBtn.setAlpha(partner.isHelloSent() ? 0.55f : 1f);
        binding.actionBtn.setText(partner.follow == 1 ? R.string.partnerbrowse_send_message : (partner.isHelloSent() ? R.string.partnerbrowse_hello_sent : R.string.partnerbrowse_say_hello));
        binding.actionBtn.setEnabled(!partner.isHelloSent() || partner.follow == 1);
        binding.actionBtn.setOnClickListener(v -> onActionClick());
        View.OnClickListener openProfile = v -> openProfilePage();
        binding.nameTv.setOnClickListener(openProfile);
        binding.metaTv.setOnClickListener(openProfile);
        binding.langTv.setOnClickListener(openProfile);
        binding.introTv.setOnClickListener(openProfile);
    }

    private void openProfilePage() {
        if (!isViewAlive() || partner == null || TextUtils.isEmpty(partner.uid)) return;
        Context context = getContext();
        if (context != null) PartnerBrowseHostBridge.openProfile(context, partner.uid);
    }

    private String buildMeta() {
        if (partner == null) return "";
        StringBuilder sb = new StringBuilder();
        if (partner.age > 0) sb.append(partner.age);
        if (!TextUtils.isEmpty(partner.country_code)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(partner.country_code.toUpperCase(java.util.Locale.US));
        }
        return sb.toString();
    }

    private String buildLanguages() {
        if (partner == null) return "";
        String left = join(partner.getNativeLanguagesSafe());
        String right = join(partner.getLearningLanguagesSafe());
        if (TextUtils.isEmpty(left) && TextUtils.isEmpty(right)) return "";
        if (TextUtils.isEmpty(left)) return right;
        if (TextUtils.isEmpty(right)) return left;
        return left + "  ⇄  " + right;
    }

    private String join(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            if (TextUtils.isEmpty(item)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(item.toUpperCase(java.util.Locale.US));
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
            WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(activity, target.uid, WKChannelType.PERSONAL, 0, false));
            return;
        }
        binding.actionBtn.setEnabled(false);
        FriendModel.getInstance().applyAddFriend(target.uid, target.vercode, getString(R.string.partnerbrowse_default_hello_plain), (code, msg) -> {
            if (!isViewAlive() || partner != target) return;
            if (code == HttpResponseCode.success) {
                target.markHelloSent();
                binding.actionBtn.setText(R.string.partnerbrowse_hello_sent);
                binding.actionBtn.setEnabled(false);
                binding.actionBtn.setAlpha(0.55f);
            } else {
                binding.actionBtn.setEnabled(true);
            }
        });
    }
}
