package com.chat.dating;

import android.content.Context;
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

/** 首页单张交友卡。CardStackView 原生负责 overlay alpha，这里只做图片/资料联动动画。 */
public class DatingCardView extends FrameLayout {
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
        int max = profile == null ? 0 : Math.max(0, profile.safePhotos().size() - 1);
        this.photoIndex = Math.max(0, Math.min(initialPhotoIndex, max));
        bindPhoto();
        resetDragProgress();
    }

    public DatingProfile getProfile() {
        return profile;
    }

    public int getPhotoIndex() {
        return photoIndex;
    }

    public View getProfileArrowView() {
        return binding.profileArrowBtn;
    }

    public void showNextPhoto() {
        if (profile == null) return;
        List<String> photos = profile.safePhotos();
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
        binding.photoIv.setScaleX(1f + progress * 0.022f);
        binding.photoIv.setScaleY(1f + progress * 0.022f);
        binding.infoPanel.setTranslationY(dp(6) * progress);
        binding.infoPanel.setAlpha(1f - progress * 0.16f);
        binding.profileArrowBtn.setAlpha(1f - progress * 0.35f);
        if (direction == Direction.Top) {
            binding.photoIv.setTranslationY(-dp(3) * progress);
        } else {
            binding.photoIv.setTranslationY(0f);
        }
    }

    public void resetDragProgress() {
        binding.photoIv.setScaleX(1f);
        binding.photoIv.setScaleY(1f);
        binding.photoIv.setTranslationY(0f);
        binding.infoPanel.setTranslationY(0f);
        binding.infoPanel.setAlpha(1f);
        binding.profileArrowBtn.setAlpha(1f);
    }

    private void bindPhoto() {
        if (profile == null) return;
        List<String> photos = profile.safePhotos();
        if (photos.isEmpty()) {
            Glide.with(this).clear(binding.photoIv);
            binding.photoIv.setImageDrawable(null);
        } else {
            int index = Math.max(0, Math.min(photoIndex, photos.size() - 1));
            String url = photos.get(index);
            Glide.with(this)
                    .load(DatingImageSource.resolve(getContext(), url))
                    .override(1080, 1920)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(binding.photoIv);
            if (index + 1 < photos.size()) DatingImagePreloader.preload(getContext(), photos.get(index + 1));
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
            // 第一张保持克制：姓名、城市、一句介绍，不放标签和职业。
            binding.jobTv.setVisibility(GONE);
            binding.tagRow.setVisibility(GONE);
            binding.tagRow.removeAllViews();
            String intro = profile.safeIntro();
            binding.introTv.setText(intro);
            binding.introTv.setMaxLines(1);
            binding.introTv.setVisibility(TextUtils.isEmpty(intro) ? GONE : VISIBLE);
        } else if (photoIndex == 1) {
            binding.jobTv.setText("关于我");
            binding.jobTv.setVisibility(VISIBLE);
            binding.introTv.setMaxLines(2);
            String intro = profile.safeIntro();
            if (TextUtils.isEmpty(intro)) intro = profile.job;
            binding.introTv.setText(intro);
            binding.introTv.setVisibility(TextUtils.isEmpty(intro) ? GONE : VISIBLE);
            bindPlainTags(profile.safeCoreTags(), 4);
        } else {
            String expectation = DatingUi.loveExpectation(profile);
            binding.jobTv.setText(TextUtils.isEmpty(expectation) ? "恋爱期待" : expectation);
            binding.jobTv.setVisibility(VISIBLE);
            binding.introTv.setMaxLines(2);
            String line = profile.relationship_status;
            if (TextUtils.isEmpty(line)) line = profile.education;
            binding.introTv.setText(line);
            binding.introTv.setVisibility(TextUtils.isEmpty(line) ? GONE : VISIBLE);
            bindPlainTags(profile.safeCoreTags(), 4);
        }
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
