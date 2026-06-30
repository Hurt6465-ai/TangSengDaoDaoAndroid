package com.chat.partnerbrowse;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
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
    /**
     * 标签一行只展示真实标签，不再展示 +N。
     * 规则：根据标签文字长度和当前可用宽度，自动显示 3~5 个。
     * - 中文短标签估算宽度小，更容易显示到 4~5 个。
     * - 外语长词估算宽度大，通常只显示 3 个。
     * - 总标签数不足 3 个时按真实数量显示。
     */
    private static final int TAG_MIN_VISIBLE = 3;
    private static final int TAG_MAX_VISIBLE = 5;
    private static final int TAG_HORIZONTAL_MARGIN_DP = 7;
    private static final int TAG_HORIZONTAL_PADDING_DP = 10;
    private static final int TAG_HEIGHT_DP = 24;
    private static final int TAG_MULTI_MAX_WIDTH_DP = 112;
    private static final int TAG_SINGLE_MAX_WIDTH_DP = 150;

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

        binding.introTv.setText(TextUtils.isEmpty(partner.intro) ? getString(R.string.partnerbrowse_intro_empty) : partner.intro);
        ensureIntroAboveTags();
        bindTags(partner.getTagsSafe());
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
        if (partner.follow == 1) {
            binding.actionBtn.setAlpha(1f);
            binding.actionBtn.setText(R.string.partnerbrowse_send_message);
            binding.actionBtn.setEnabled(true);
            return;
        }
        if (!partner.isHelloSent()) {
            binding.actionBtn.setAlpha(1f);
            binding.actionBtn.setText(R.string.partnerbrowse_say_hello);
            binding.actionBtn.setEnabled(true);
            return;
        }
        int count = partner.requester_msg_count <= 0 ? 1 : partner.requester_msg_count;
        int max = partner.getMaxGreetingCountSafe();
        boolean canMore = count < max;
        binding.actionBtn.setAlpha(canMore ? 1f : 0.55f);
        if (!canMore) {
            binding.actionBtn.setText(R.string.partnerbrowse_wait_reply);
        } else if (count == max - 1) {
            binding.actionBtn.setText(R.string.partnerbrowse_last_hello);
        } else {
            binding.actionBtn.setText(R.string.partnerbrowse_hello_again);
        }
        binding.actionBtn.setEnabled(canMore);
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

        int availableWidth = getTagRowAvailableWidth();
        int visibleCount = calculateVisibleTagCount(clean, availableWidth);
        int maxChipWidth = calculateChipMaxWidth(visibleCount, availableWidth);

        for (int i = 0; i < visibleCount; i++) {
            addTagChip(clean.get(i), maxChipWidth);
        }
    }

    /**
     * 当前布局文件如果已经把 introTv 放在 tagRow 上面，这里不会做任何事。
     * 如果旧布局里 introTv 和 tagRow 同在一个 LinearLayout/FrameLayout 等父容器，
     * 这里会把介绍文本移动到标签前面，让视觉顺序变成：介绍 -> 标签。
     *
     * 注意：如果 XML 使用 ConstraintLayout 固定约束，必须同步改 XML 约束；
     * 这个兜底方法不会破坏旧布局，也不会导致崩溃。
     */
    private void ensureIntroAboveTags() {
        if (binding == null) return;
        View introView = binding.introTv;
        View tagView = binding.tagRow;
        if (!(introView.getParent() instanceof ViewGroup) || introView.getParent() != tagView.getParent()) {
            return;
        }
        ViewGroup parent = (ViewGroup) introView.getParent();
        int introIndex = parent.indexOfChild(introView);
        int tagIndex = parent.indexOfChild(tagView);
        if (introIndex < 0 || tagIndex < 0 || introIndex < tagIndex) return;

        ViewGroup.LayoutParams params = introView.getLayoutParams();
        parent.removeView(introView);
        int newTagIndex = parent.indexOfChild(tagView);
        parent.addView(introView, Math.max(0, newTagIndex), params);
    }

    private int getTagRowAvailableWidth() {
        if (binding == null) return AndroidUtilities.dp(280);
        int width = binding.tagRow.getWidth();
        if (width <= 0 && binding.tagRow.getParent() instanceof View) {
            width = ((View) binding.tagRow.getParent()).getWidth();
        }
        if (width <= 0) {
            // 兜底：常见手机宽度减去左右安全边距，避免首次布局前按 0 计算。
            width = AndroidUtilities.dp(300);
        }
        width -= binding.tagRow.getPaddingLeft() + binding.tagRow.getPaddingRight();
        return Math.max(AndroidUtilities.dp(120), width);
    }

    private int calculateVisibleTagCount(List<String> tags, int availableWidth) {
        int totalCount = tags == null ? 0 : tags.size();
        if (totalCount <= 0) return 0;
        if (totalCount <= TAG_MIN_VISIBLE) return totalCount;

        int maxCandidate = Math.min(TAG_MAX_VISIBLE, totalCount);
        int minCandidate = Math.min(TAG_MIN_VISIBLE, totalCount);

        int best = minCandidate;
        int usedWidth = 0;
        for (int i = 0; i < maxCandidate; i++) {
            int estimate = estimateTagWidth(tags.get(i));
            if (i > 0) estimate += AndroidUtilities.dp(TAG_HORIZONTAL_MARGIN_DP);
            usedWidth += estimate;
            if (usedWidth <= availableWidth) {
                best = i + 1;
            } else {
                break;
            }
        }

        // 至少显示 3 个真实标签。外语长标签会通过 maxWidth 省略，不会撑爆整行。
        return Math.max(minCandidate, Math.min(best, maxCandidate));
    }

    private int calculateChipMaxWidth(int visibleCount, int availableWidth) {
        if (visibleCount <= 0) return AndroidUtilities.dp(TAG_MULTI_MAX_WIDTH_DP);
        int totalMargin = Math.max(0, visibleCount - 1) * AndroidUtilities.dp(TAG_HORIZONTAL_MARGIN_DP);
        int avgWidth = (availableWidth - totalMargin) / visibleCount;
        int hardMax = visibleCount == 1 ? AndroidUtilities.dp(TAG_SINGLE_MAX_WIDTH_DP) : AndroidUtilities.dp(TAG_MULTI_MAX_WIDTH_DP);
        int minWidth = AndroidUtilities.dp(46);
        return Math.max(minWidth, Math.min(avgWidth, hardMax));
    }

    private int estimateTagWidth(String tag) {
        if (TextUtils.isEmpty(tag)) return AndroidUtilities.dp(46);
        int textWidthDp = 0;
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (Character.isWhitespace(c)) {
                textWidthDp += 3;
            } else if (isCjk(c)) {
                textWidthDp += 13;
            } else if (c <= 0x007F) {
                textWidthDp += 7;
            } else {
                textWidthDp += 9;
            }
        }
        int chipWidthDp = textWidthDp + TAG_HORIZONTAL_PADDING_DP * 2;
        int maxWidthDp = tag.length() <= 2 ? 58 : TAG_MULTI_MAX_WIDTH_DP;
        return AndroidUtilities.dp(Math.min(Math.max(chipWidthDp, 46), maxWidthDp));
    }

    private boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0x3000 && c <= 0x303F)
                || (c >= 0x3040 && c <= 0x30FF)
                || (c >= 0xAC00 && c <= 0xD7AF);
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

    private void addTagChip(String tag, int maxWidth) {
        TextView chip = new TextView(requireContext());
        chip.setText(tag);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(12f);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(maxWidth);
        chip.setBackgroundResource(R.drawable.bg_partnerbrowse_tag_chip);
        chip.setPadding(AndroidUtilities.dp(TAG_HORIZONTAL_PADDING_DP), 0, AndroidUtilities.dp(TAG_HORIZONTAL_PADDING_DP), 0);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(TAG_HEIGHT_DP));
        lp.rightMargin = AndroidUtilities.dp(TAG_HORIZONTAL_MARGIN_DP);
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
                if (data != null) {
                    target.updateGreetingState(data.requester_msg_count, data.max_greeting_count, data.next_allowed_at);
                } else {
                    target.markHelloSent();
                }
                PartnerRepository.putOne(target);
                updateActionButton();
                if (data != null && !TextUtils.isEmpty(data.getMessageSafe())) {
                    Toast.makeText(requireContext(), data.getMessageSafe(), Toast.LENGTH_SHORT).show();
                }
            } else {
                updateActionButton();
                String error = data == null ? msg : data.getMessageSafe();
                if (!TextUtils.isEmpty(error)) Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
