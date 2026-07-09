package com.chat.dating;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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

import java.util.ArrayList;
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
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setCornerRadius(dp(18));
        setBackground(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
        }
    }

    public void bind(DatingProfile profile) {
        bind(profile, 0);
    }

    public void bind(DatingProfile profile, int initialPhotoIndex) {
        this.profile = profile;
        int max = profile == null ? 0 : Math.max(0, profile.safePhotos().size() - 1);
        this.photoIndex = Math.max(0, Math.min(initialPhotoIndex, max));
        bindPhoto();
        setSwipeProgress(0f, 0f);
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
        setSwipeProgress(dx, 0f);
    }

    public void setSwipeProgress(float dx, float dy) {
        float width = Math.max(1f, getWidth());
        float height = Math.max(1f, getHeight());
        float horizontal = Math.min(1f, Math.abs(dx) / (width * 0.28f));
        float up = Math.min(1f, Math.max(0f, -dy) / (height * 0.16f));
        binding.likeBadge.setAlpha(dx > 0 && horizontal >= up ? horizontal : 0f);
        binding.nopeBadge.setAlpha(dx < 0 && horizontal >= up ? horizontal : 0f);
        binding.favoriteBadge.setAlpha(up > horizontal ? up : 0f);
        binding.infoPanel.setTranslationY(Math.max(horizontal, up) * dp(4));
    }

    private void bindPhoto() {
        if (profile == null) return;
        List<String> photos = profile.safePhotos();
        if (photos.isEmpty()) {
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
        int page = Math.max(0, photoIndex);

        String nameLine = profile.safeName();
        if (profile.age > 0) nameLine += " " + profile.age;
        if (!TextUtils.isEmpty(profile.safeCountryCode())) nameLine += " " + flagEmoji(profile.safeCountryCode());
        binding.nameTv.setText(nameLine);

        String meta = buildMetaLine();
        binding.metaTv.setText(meta);
        binding.metaTv.setVisibility(TextUtils.isEmpty(meta) ? GONE : VISIBLE);

        if (page == 0) {
            bindBasePage();
        } else if (page == 1) {
            bindIntroPage();
        } else {
            bindLovePage();
        }
    }

    private void bindBasePage() {
        binding.jobTv.setVisibility(GONE);
        binding.tagRow.setVisibility(GONE);
        binding.tagRow.removeAllViews();

        String intro = profile.safeIntro();
        binding.introTv.setText(intro);
        binding.introTv.setMaxLines(1);
        binding.introTv.setVisibility(TextUtils.isEmpty(intro) ? GONE : VISIBLE);
    }

    private void bindIntroPage() {
        binding.introTv.setMaxLines(2);
        binding.jobTv.setText("关于我");
        binding.jobTv.setVisibility(VISIBLE);
        String intro = profile.safeIntro();
        if (TextUtils.isEmpty(intro)) intro = buildProfileLine();
        binding.introTv.setText(intro);
        binding.introTv.setVisibility(TextUtils.isEmpty(intro) ? GONE : VISIBLE);
        bindPlainTags(firstNonEmpty(profile.interest_tags, profile.communication_tags, profile.safeCoreTags()), 4);
    }

    private void bindLovePage() {
        binding.introTv.setMaxLines(2);
        String goal = goalText(profile.safeRelationshipGoal());
        String cross = crossText(profile.safeCrossBorderPreference());
        StringBuilder line = new StringBuilder();
        if (!TextUtils.isEmpty(goal)) line.append(goal);
        if (!TextUtils.isEmpty(cross)) {
            if (line.length() > 0) line.append(" · ");
            line.append(cross);
        }
        binding.jobTv.setText(line.toString());
        binding.jobTv.setVisibility(line.length() == 0 ? GONE : VISIBLE);

        String intro = profile.relationship_status;
        if (TextUtils.isEmpty(intro)) intro = buildProfileLine();
        binding.introTv.setText(intro);
        binding.introTv.setVisibility(TextUtils.isEmpty(intro) ? GONE : VISIBLE);
        bindPlainTags(firstNonEmpty(profile.love_tags, profile.communication_tags, profile.safeCoreTags()), 4);
    }

    private String buildMetaLine() {
        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(profile.city)) meta.append(profile.city);
        else if (!TextUtils.isEmpty(profile.country)) meta.append(profile.country);
        String distance = profile.safeDistanceLabel();
        if (!TextUtils.isEmpty(distance)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(distance);
        }
        return meta.toString();
    }

    private String buildProfileLine() {
        StringBuilder line = new StringBuilder();
        if (!TextUtils.isEmpty(profile.job)) line.append(profile.job);
        if (!TextUtils.isEmpty(profile.education)) {
            if (line.length() > 0) line.append(" · ");
            line.append(profile.education);
        }
        if (!TextUtils.isEmpty(profile.relationship_status)) {
            if (line.length() > 0) line.append(" · ");
            line.append(profile.relationship_status);
        }
        return line.toString();
    }

    private List<String> firstNonEmpty(List<String> first, List<String> second, List<String> fallback) {
        ArrayList<String> list = new ArrayList<>();
        addClean(list, first);
        addClean(list, second);
        if (list.isEmpty()) addClean(list, fallback);
        return list;
    }

    private void addClean(ArrayList<String> out, List<String> source) {
        if (source == null) return;
        for (String item : source) {
            if (TextUtils.isEmpty(item)) continue;
            boolean exists = false;
            for (String old : out) {
                if (old.equalsIgnoreCase(item.trim())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) out.add(item.trim());
        }
    }

    private void bindIndicators(int count) {
        binding.indicatorRow.removeAllViews();
        int realCount = Math.max(1, Math.min(5, count));
        for (int i = 0; i < realCount; i++) {
            View bar = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(3), 1f);
            if (i > 0) lp.setMarginStart(dp(4));
            bar.setLayoutParams(lp);
            bar.setBackgroundResource(i == Math.min(photoIndex, realCount - 1) ? R.drawable.bg_dating_indicator_active : R.drawable.bg_dating_indicator_inactive);
            binding.indicatorRow.addView(bar);
        }
        binding.indicatorRow.setVisibility(count <= 1 ? GONE : VISIBLE);
    }

    private void bindPlainTags(List<String> list, int maxCount) {
        binding.tagRow.removeAllViews();
        if (list == null || list.isEmpty()) {
            binding.tagRow.setVisibility(GONE);
            return;
        }
        binding.tagRow.setVisibility(VISIBLE);
        int count = 0;
        for (String item : list) {
            if (TextUtils.isEmpty(item)) continue;
            if (count >= maxCount) break;
            TextView tag = new TextView(getContext());
            tag.setText("#" + item.trim());
            tag.setTextColor(0xE8FFFFFF);
            tag.setTextSize(13);
            tag.setSingleLine(true);
            tag.setMaxLines(1);
            tag.setEllipsize(TextUtils.TruncateAt.END);
            tag.setShadowLayer(dp(4), 0, dp(1), 0xAA000000);
            tag.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (count > 0) lp.setMarginStart(dp(12));
            binding.tagRow.addView(tag, lp);
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
