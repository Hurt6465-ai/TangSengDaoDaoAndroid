package com.chat.dating;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.dating.databinding.ViewWkDatingCardBinding;
import com.chat.dating.model.DatingProfile;
import com.yuyakaido.android.cardstackview.Direction;

import java.util.ArrayList;
import java.util.List;

/** 首页单张交友卡。只负责当前图片，周边图片统一由 DatingImagePreloader 管理。 */
public class DatingCardView extends FrameLayout {
    private static final int CARD_WIDTH = 720;
    private static final int CARD_HEIGHT = 1280;
    private static final float MAX_PHOTO_SCALE = 0.04f;
    private static final float MIN_INFO_ALPHA = 0.90f;

    private final ViewWkDatingCardBinding binding;
    private DatingProfile profile;
    private int photoIndex;

    public DatingCardView(@NonNull Context context) {
        super(context);
        binding = ViewWkDatingCardBinding.inflate(LayoutInflater.from(context), this, true);
        setClickable(true);
        setClipChildren(true);
        setClipToPadding(true);
    }

    public void bind(DatingProfile profile, int initialPhotoIndex) {
        this.profile = profile;
        int max = profile == null ? 0 : Math.max(0, profile.safeCardPhotos().size() - 1);
        this.photoIndex = Math.max(0, Math.min(initialPhotoIndex, max));
        bindPhoto();
        resetDragProgress();
    }

    public DatingProfile getProfile() { return profile; }
    public int getPhotoIndex() { return photoIndex; }
    public View getProfileArrowView() { return binding.profileArrowBtn; }

    public void showNextPhoto() {
        if (profile == null) return;
        List<String> photos = profile.safeCardPhotos();
        if (photoIndex < photos.size() - 1) {
            photoIndex++;
            bindPhoto();
        }
    }

    public void showPreviousPhoto() {
        if (profile != null && photoIndex > 0) {
            photoIndex--;
            bindPhoto();
        }
    }

    public void setDragProgress(Direction direction, float ratio) {
        float progress = Math.max(0f, Math.min(1f, ratio));
        float scale = 1f + progress * MAX_PHOTO_SCALE;
        binding.photoIv.setScaleX(scale);
        binding.photoIv.setScaleY(scale);
        binding.infoPanel.setTranslationY(dp(8) * progress);
        binding.infoPanel.setAlpha(1f - progress * (1f - MIN_INFO_ALPHA));
        binding.profileArrowBtn.setAlpha(1f - progress * 0.25f);
        binding.photoIv.setTranslationY(direction == Direction.Top ? -dp(3) * progress : 0f);
    }

    public void resetDragProgress() {
        binding.photoIv.setScaleX(1f);
        binding.photoIv.setScaleY(1f);
        binding.photoIv.setTranslationY(0f);
        binding.infoPanel.setTranslationY(0f);
        binding.infoPanel.setAlpha(1f);
        binding.profileArrowBtn.setAlpha(1f);
    }

    /** ViewHolder 回收时终止请求并释放大图引用，避免低端机滑动后仍持有 Bitmap。 */
    public void recycle() {
        Glide.with(this).clear(binding.photoIv);
        binding.photoIv.setImageDrawable(null);
        binding.tagRow.removeAllViews();
        binding.profileArrowBtn.setOnClickListener(null);
        binding.profileArrowBtn.setOnTouchListener(null);
        setOnTouchListener(null);
        profile = null;
        photoIndex = 0;
        resetDragProgress();
    }

    private void bindPhoto() {
        if (profile == null) return;
        List<String> photos = profile.safeCardPhotos();
        if (photos.isEmpty()) {
            Glide.with(this).clear(binding.photoIv);
            binding.photoIv.setImageDrawable(null);
        } else {
            int index = Math.max(0, Math.min(photoIndex, photos.size() - 1));
            photoIndex = index;
            Object source = DatingImageSource.resolve(getContext(), photos.get(index));
            String avatarPath = TextUtils.isEmpty(profile.avatar)
                    ? "users/" + profile.safeUid() + "/avatar"
                    : profile.avatar;
            Object fallback = DatingImageSource.resolve(getContext(), avatarPath);
            ColorDrawable placeholder = new ColorDrawable(0xFF202124);
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> fallbackRequest = Glide.with(this)
                    .load(fallback)
                    .override(CARD_WIDTH, CARD_HEIGHT)
                    .centerCrop()
                    .error(placeholder);
            Glide.with(this)
                    .load(source)
                    .thumbnail(0.25f)
                    .override(CARD_WIDTH, CARD_HEIGHT)
                    .centerCrop()
                    .placeholder(placeholder)
                    .error(fallbackRequest)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(binding.photoIv);
        }
        bindIndicators(photos.size());
        bindTextForPhoto();
    }

    private void bindTextForPhoto() {
        if (profile == null) return;
        binding.nameTv.setText(DatingUi.nameAgeFlag(profile));
        String meta = profile.displayLocation();
        binding.metaTv.setText(meta);
        binding.metaTv.setVisibility(TextUtils.isEmpty(meta) ? GONE : VISIBLE);

        if (photoIndex == 0) {
            binding.jobTv.setVisibility(GONE);
            binding.tagRow.setVisibility(GONE);
            binding.tagRow.removeAllViews();
            String intro = profile.safeIntro();
            binding.introTv.setText(intro);
            binding.introTv.setMaxLines(1);
            binding.introTv.setVisibility(TextUtils.isEmpty(intro) ? GONE : VISIBLE);
            return;
        }

        binding.jobTv.setVisibility(VISIBLE);
        binding.introTv.setMaxLines(2);
        if (photoIndex == 1) {
            binding.jobTv.setText(R.string.dating_section_about);
            binding.introTv.setText(profile.safeIntro());
            binding.introTv.setVisibility(TextUtils.isEmpty(profile.safeIntro()) ? GONE : VISIBLE);
            bindPlainTags(profile.safeTags(), 4);
        } else if (photoIndex == 2) {
            binding.jobTv.setText(R.string.dating_section_goal);
            String line = joinText(DatingIntent.displayLabel(getContext(), profile.safeRelationshipGoal()), profile.relationship_status);
            binding.introTv.setText(line);
            binding.introTv.setVisibility(TextUtils.isEmpty(line) ? GONE : VISIBLE);
            bindPlainTags(profile.love_tags, 4);
        } else if (photoIndex == 3) {
            binding.jobTv.setText(R.string.dating_section_lifestyle);
            String line = joinText(
                    TextUtils.isEmpty(profile.drinking) ? "" : getResources().getString(R.string.dating_drinking_value, profile.drinking),
                    TextUtils.isEmpty(profile.smoking) ? "" : getResources().getString(R.string.dating_smoking_value, profile.smoking));
            binding.introTv.setText(line);
            binding.introTv.setVisibility(TextUtils.isEmpty(line) ? GONE : VISIBLE);
            bindPlainTags(profile.lifestyle_tags, 4);
        } else {
            binding.jobTv.setText(R.string.dating_section_basic);
            ArrayList<String> basics = new ArrayList<>();
            if (profile.height_cm > 0) basics.add(profile.height_cm + "cm");
            if (profile.weight_kg > 0) basics.add(profile.weight_kg + "kg");
            if (!TextUtils.isEmpty(profile.sexual_orientation)) basics.add(profile.sexual_orientation);
            if (!TextUtils.isEmpty(profile.education)) basics.add(profile.education);
            if (!TextUtils.isEmpty(profile.job)) basics.add(profile.job);
            String line = TextUtils.join(" · ", basics);
            binding.introTv.setText(line);
            binding.introTv.setVisibility(TextUtils.isEmpty(line) ? GONE : VISIBLE);
            bindPlainTags(profile.safeTags(), 4);
        }
    }

    private String joinText(String first, String second) {
        if (TextUtils.isEmpty(first)) return second == null ? "" : second;
        if (TextUtils.isEmpty(second)) return first;
        return first + " · " + second;
    }

    private void bindIndicators(int count) {
        binding.indicatorRow.removeAllViews();
        int realCount = Math.max(1, Math.min(DatingPhotoPolicy.MAX_PHOTO_COUNT, count));
        for (int i = 0; i < realCount; i++) {
            View bar = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(3), 1f);
            if (i > 0) lp.setMarginStart(dp(3));
            bar.setLayoutParams(lp);
            bar.setBackgroundResource(i == Math.min(photoIndex, realCount - 1)
                    ? R.drawable.bg_dating_indicator_active : R.drawable.bg_dating_indicator_inactive);
            binding.indicatorRow.addView(bar);
        }
        binding.indicatorRow.setVisibility(count <= 1 ? GONE : VISIBLE);
    }

    private void bindPlainTags(List<String> source, int maxCount) {
        binding.tagRow.removeAllViews();
        ArrayList<String> list = new ArrayList<>();
        if (source != null) {
            for (String item : source) {
                if (TextUtils.isEmpty(item)) continue;
                boolean duplicate = false;
                for (String old : list) {
                    if (old.equalsIgnoreCase(item.trim())) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) list.add(item.trim());
            }
        }
        if (list.isEmpty()) {
            binding.tagRow.setVisibility(GONE);
            return;
        }
        binding.tagRow.setVisibility(VISIBLE);
        for (int i = 0; i < Math.min(maxCount, list.size()); i++) {
            TextView tag = new TextView(getContext());
            tag.setText("#" + list.get(i));
            tag.setTextColor(0xE8FFFFFF);
            tag.setTextSize(13);
            tag.setSingleLine(true);
            tag.setShadowLayer(dp(4), 0, dp(1), 0xAA000000);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(dp(11));
            binding.tagRow.addView(tag, lp);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
