package com.chat.dating;

import android.content.Context;
import android.graphics.Outline;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.dating.databinding.ViewWkDatingCardBinding;
import com.chat.dating.model.DatingProfile;

import java.util.List;
import java.util.Locale;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(30));
                }
            });
        }
    }

    public void bind(DatingProfile profile) {
        this.profile = profile;
        this.photoIndex = 0;
        bindText();
        bindPhoto();
        setSwipeProgress(0f);
    }

    public DatingProfile getProfile() {
        return profile;
    }

    public int getPhotoIndex() {
        return photoIndex;
    }

    public void showNextPhoto() {
        if (profile == null) return;
        List<String> photos = profile.safePhotos();
        if (photos.isEmpty()) return;
        if (photoIndex < photos.size() - 1) {
            photoIndex++;
            bindPhoto();
        }
    }

    public void showPreviousPhoto() {
        if (profile == null) return;
        if (photoIndex > 0) {
            photoIndex--;
            bindPhoto();
        }
    }

    public void setSwipeProgress(float dx) {
        float width = Math.max(1f, getWidth());
        float progress = Math.min(1f, Math.abs(dx) / (width * 0.28f));
        binding.likeBadge.setAlpha(dx > 0 ? progress : 0f);
        binding.nopeBadge.setAlpha(dx < 0 ? progress : 0f);
        binding.infoPanel.setTranslationY(progress * dp(4));
    }

    private void bindText() {
        if (profile == null) return;
        String nameLine = profile.safeName();
        if (profile.age > 0) nameLine += ", " + profile.age;
        if (!TextUtils.isEmpty(profile.safeCountryCode())) nameLine += " " + flagEmoji(profile.safeCountryCode());
        binding.nameTv.setText(nameLine);

        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(profile.city)) meta.append(profile.city);
        else if (!TextUtils.isEmpty(profile.country)) meta.append(profile.country);
        String distance = profile.safeDistanceLabel();
        if (!TextUtils.isEmpty(distance)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(distance);
        }
        binding.metaTv.setText(meta.toString());
        binding.metaTv.setVisibility(meta.length() == 0 ? GONE : VISIBLE);

        int score = profile.profile_score > 0 ? Math.min(99, profile.profile_score) : 88;
        binding.scoreTv.setText(getResources().getString(R.string.dating_match_score, score));

        String goal = goalText(profile.safeRelationshipGoal());
        binding.goalChipTv.setText(TextUtils.isEmpty(goal) ? getResources().getString(R.string.dating_goal_love) : goal);
        binding.crossChipTv.setText(crossText(profile.safeCrossBorderPreference()));

        StringBuilder jobLine = new StringBuilder();
        if (!TextUtils.isEmpty(profile.job)) jobLine.append(profile.job);
        if (!TextUtils.isEmpty(profile.education)) {
            if (jobLine.length() > 0) jobLine.append(" · ");
            jobLine.append(profile.education);
        }
        if (jobLine.length() == 0 && !TextUtils.isEmpty(profile.relationship_status)) jobLine.append(profile.relationship_status);
        binding.jobTv.setText(jobLine.toString());
        binding.jobTv.setVisibility(jobLine.length() == 0 ? GONE : VISIBLE);

        String intro = profile.safeIntro();
        binding.introTv.setText(intro);
        binding.introTv.setVisibility(TextUtils.isEmpty(intro) ? GONE : VISIBLE);

        bindTagChips(profile.safeCoreTags());
    }

    private void bindPhoto() {
        if (profile == null) return;
        List<String> photos = profile.safePhotos();
        if (photos.isEmpty()) {
            binding.photoIv.setImageDrawable(null);
        } else {
            String url = photos.get(Math.max(0, Math.min(photoIndex, photos.size() - 1)));
            Glide.with(this)
                    .load(DatingImageSource.resolve(getContext(), url))
                    .override(900, 1400)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(binding.photoIv);
            if (photoIndex + 1 < photos.size()) DatingImagePreloader.preload(getContext(), photos.get(photoIndex + 1));
        }
        bindIndicators(photos.size());
    }

    private void bindIndicators(int count) {
        binding.indicatorRow.removeAllViews();
        int realCount = Math.max(1, count);
        for (int i = 0; i < realCount; i++) {
            View bar = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(4), 1f);
            if (i > 0) lp.setMarginStart(dp(5));
            bar.setLayoutParams(lp);
            bar.setBackgroundResource(i == photoIndex ? R.drawable.bg_dating_indicator_active : R.drawable.bg_dating_indicator_inactive);
            binding.indicatorRow.addView(bar);
        }
    }

    private void bindTagChips(List<String> list) {
        binding.tagRow.removeAllViews();
        if (list == null || list.isEmpty()) {
            binding.tagRow.setVisibility(GONE);
            return;
        }
        binding.tagRow.setVisibility(VISIBLE);
        int count = 0;
        for (String item : list) {
            if (TextUtils.isEmpty(item)) continue;
            if (count >= 4) break;
            TextView chip = new TextView(getContext());
            chip.setText("#" + item);
            chip.setTextColor(0xE6FFFFFF);
            chip.setTextSize(12);
            chip.setSingleLine(true);
            chip.setMaxLines(1);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setBackgroundResource(R.drawable.bg_dating_chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            if (count > 0) lp.setMarginStart(dp(7));
            binding.tagRow.addView(chip, lp);
            count++;
        }
    }

    private String goalText(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String value = raw.trim().toLowerCase(Locale.US);
        if (value.contains("marriage") || value.contains("结婚") || value.contains("奔")) return getResources().getString(R.string.dating_goal_marriage);
        if (value.contains("chat") || value.contains("了解") || value.contains("慢慢")) return getResources().getString(R.string.dating_goal_chat);
        if (value.contains("long") || value.contains("稳定") || value.contains("长期")) return getResources().getString(R.string.dating_goal_long_term);
        if (value.contains("love") || value.contains("date") || value.contains("恋爱") || value.contains("认真")) return getResources().getString(R.string.dating_goal_love);
        return raw;
    }

    private String crossText(String raw) {
        if (TextUtils.isEmpty(raw)) return getResources().getString(R.string.dating_cross_open);
        String value = raw.trim().toLowerCase(Locale.US);
        if (value.contains("same") || value.contains("local") || value.contains("nearby") || value.contains("本国") || value.contains("拒绝")) {
            return getResources().getString(R.string.dating_cross_same_country);
        }
        if (value.contains("prefer") || value.contains("喜欢异国")) return getResources().getString(R.string.dating_cross_prefer_foreign);
        return getResources().getString(R.string.dating_cross_open);
    }

    private String flagEmoji(String countryCode) {
        if (TextUtils.isEmpty(countryCode) || countryCode.length() < 2) return "";
        String code = countryCode.trim().toUpperCase(Locale.US);
        int first = Character.codePointAt(code, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(code, 1) - 'A' + 0x1F1E6;
        if (first < 0x1F1E6 || first > 0x1F1FF || second < 0x1F1E6 || second > 0x1F1FF) return "";
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
