package com.chat.dating;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingHomeBinding;
import com.chat.dating.model.DatingProfile;
import com.chat.dating.model.DatingSwipeResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class DatingHomeActivity extends WKBaseActivity<ActivityWkDatingHomeBinding> {
    private static final int PAGE_LIMIT = 12;
    private final ArrayList<DatingProfile> profiles = new ArrayList<>();
    private final ArrayList<Map<String, Object>> pendingExposures = new ArrayList<>();
    private final HashSet<String> loadedUids = new HashSet<>();
    private String cursor = "";
    private String scope = "global";
    private String sessionId;
    private boolean loading;
    private boolean noMore;
    private long exposureStartMs;
    private String exposureUid = "";
    private DatingProfile myProfile;
    private DatingFilter filter;

    @Override
    protected ActivityWkDatingHomeBinding getViewBinding() {
        return ActivityWkDatingHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    public boolean supportSlideBack() {
        return false;
    }

    @Override
    protected void setTitle(TextView titleTv) {
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(0x00000000);
            window.setNavigationBarColor(0x00000000);
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView() {
        sessionId = UUID.randomUUID().toString();
        filter = DatingFilter.load(this);
        myProfile = DatingMockData.demoMyProfile();
        wkVBinding.deckView.setOnDeckActionListener(new DatingSwipeDeckView.OnDeckActionListener() {
            @Override
            public void onCurrentChanged(DatingProfile profile, int index) {
                finishExposure(false);
                startExposure(profile);
                DatingImagePreloader.preloadAround(DatingHomeActivity.this, profiles, index);
                if (!loading && !noMore && wkVBinding.deckView.remainingCount() <= 4) loadMore(false);
            }

            @Override
            public void onSwiped(DatingProfile profile, String action, int photoIndex, int nextIndex) {
                finishExposure(true);
                reportSwipe(profile, action, photoIndex);
            }

            @Override
            public void onDeckEmpty() {
                if (!loading && noMore) showEmpty();
            }

            @Override
            public void onCardCenterTap(DatingProfile profile) {
                showProfilePreview(profile);
            }
        });
        updateScopeTabs();
        updateFilterSummary();
        showLoading(true, getString(R.string.dating_loading), false);
    }

    @Override
    protected void initListener() {
        wkVBinding.retryBtn.setOnClickListener(v -> reload());
        wkVBinding.passBtn.setOnClickListener(v -> wkVBinding.deckView.swipeTop(DatingSwipeAction.PASS));
        wkVBinding.favoriteBtn.setOnClickListener(v -> wkVBinding.deckView.swipeTop(DatingSwipeAction.FAVORITE));
        wkVBinding.likeBtn.setOnClickListener(v -> wkVBinding.deckView.swipeTop(DatingSwipeAction.LIKE));
        wkVBinding.recommendTab.setOnClickListener(v -> {
            if (!"global".equals(scope)) {
                scope = "global";
                updateScopeTabs();
                reload();
            }
        });
        wkVBinding.nearbyTab.setOnClickListener(v -> {
            if (!"nearby".equals(scope)) {
                scope = "nearby";
                updateScopeTabs();
                reload();
            }
        });
        wkVBinding.filterBtn.setOnClickListener(v -> showFilterDialog());
    }

    @Override
    protected void initData() {
        loadMyProfileThenRecommend();
    }

    @Override
    protected void onPause() {
        finishExposure(true);
        flushExposures();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        finishExposure(true);
        flushExposures();
        super.onDestroy();
    }

    private void loadMyProfileThenRecommend() {
        DatingModel.getInstance().getMyDatingProfile((code, msg, data) -> {
            if (data != null) myProfile = data;
            loadMore(true);
        });
    }

    private void reload() {
        finishExposure(true);
        cursor = "";
        noMore = false;
        profiles.clear();
        loadedUids.clear();
        pendingExposures.clear();
        wkVBinding.deckView.setProfiles(profiles);
        showLoading(true, getString(R.string.dating_loading), false);
        loadMore(true);
    }

    private void loadMore(boolean firstPage) {
        if (loading) return;
        loading = true;
        if (firstPage) showLoading(true, getString(R.string.dating_loading), false);
        DatingModel.getInstance().recommend(cursor, PAGE_LIMIT, scope, sessionId, filter, (code, msg, data) -> {
            if (isFinishing() || isDestroyed()) return;
            loading = false;
            if (code == HttpResponseCode.success && data != null && data.getItems() != null && !data.getItems().isEmpty()) {
                cursor = data.cursor == null ? "" : data.cursor;
                noMore = !data.hasMore() || TextUtils.isEmpty(cursor);
                appendProfiles(data.getItems(), firstPage, false);
                return;
            }
            if (firstPage) {
                appendProfiles(DatingMockData.demoProfiles(), true, true);
                noMore = true;
            } else {
                noMore = true;
            }
        });
    }

    private void appendProfiles(List<DatingProfile> data, boolean reset, boolean demo) {
        List<DatingProfile> clean = cleanProfiles(data);
        if (reset) {
            profiles.clear();
            loadedUids.clear();
            profiles.addAll(clean);
            for (DatingProfile p : clean) loadedUids.add(p.safeUid());
            wkVBinding.deckView.setProfiles(profiles);
        } else if (!clean.isEmpty()) {
            profiles.addAll(clean);
            for (DatingProfile p : clean) loadedUids.add(p.safeUid());
            wkVBinding.deckView.appendProfiles(clean);
        }
        if (profiles.isEmpty()) showEmpty();
        else showContent();
        if (demo) showToast(R.string.dating_mock_tip);
        DatingImagePreloader.preloadAround(this, profiles, wkVBinding.deckView.getCurrentIndex());
    }

    private List<DatingProfile> cleanProfiles(List<DatingProfile> data) {
        ArrayList<DatingProfile> clean = new ArrayList<>();
        if (data == null) return clean;
        for (DatingProfile profile : data) {
            if (profile == null || TextUtils.isEmpty(profile.safeUid()) || profile.safePhotos().isEmpty()) continue;
            if (loadedUids.contains(profile.safeUid())) continue;
            if (!filter.accepts(myProfile, profile)) continue;
            clean.add(profile);
        }
        return clean;
    }

    private void reportSwipe(DatingProfile profile, String action, int photoIndex) {
        if (profile == null || TextUtils.isEmpty(profile.safeUid())) return;
        DatingModel.getInstance().swipe(profile.safeUid(), action, photoIndex, (code, msg, data) -> {
            if (code == HttpResponseCode.success && data != null && data.isMatched()) {
                showMatchDialog(data, profile);
            }
        });
    }

    private void showMatchDialog(DatingSwipeResult result, DatingProfile profile) {
        String name = profile == null ? "" : profile.safeName();
        String message = TextUtils.isEmpty(name)
                ? getString(R.string.dating_match_notice_ready)
                : getString(R.string.dating_match_notice_named, name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_match_title)
                .setMessage(message)
                .setPositiveButton(R.string.dating_keep_swiping, null)
                .show();
    }

    private void startExposure(DatingProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.safeUid())) return;
        exposureUid = profile.safeUid();
        exposureStartMs = System.currentTimeMillis();
    }

    private void finishExposure(boolean forceFlush) {
        if (!TextUtils.isEmpty(exposureUid) && exposureStartMs > 0) {
            long duration = System.currentTimeMillis() - exposureStartMs;
            if (duration > 250) {
                Map<String, Object> item = new HashMap<>();
                item.put("to_uid", exposureUid);
                item.put("seen_at", System.currentTimeMillis());
                item.put("duration_ms", duration);
                item.put("source", "wkdating");
                item.put("event_type", "expose");
                item.put("scope", scope);
                item.put("country_mode", filter == null ? "" : filter.countryMode);
                pendingExposures.add(item);
            }
        }
        exposureUid = "";
        exposureStartMs = 0;
        if (forceFlush || pendingExposures.size() >= 5) flushExposures();
    }

    private void flushExposures() {
        if (pendingExposures.isEmpty()) return;
        ArrayList<Map<String, Object>> copy = new ArrayList<>(pendingExposures);
        pendingExposures.clear();
        DatingModel.getInstance().reportExposures(copy);
    }

    private void updateScopeTabs() {
        boolean nearby = "nearby".equals(scope);
        styleScopeTab(wkVBinding.recommendTab, !nearby);
        styleScopeTab(wkVBinding.nearbyTab, nearby);
    }

    private void styleScopeTab(TextView tab, boolean selected) {
        if (tab == null) return;
        tab.setBackgroundColor(Color.TRANSPARENT);
        tab.setTextColor(selected ? Color.WHITE : 0x99FFFFFF);
        tab.setTextSize(selected ? 20 : 17);
        tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setAlpha(selected ? 1f : 0.76f);
    }

    private void updateFilterSummary() {
        // 全屏沉浸 UI 不再显示 “智能推荐 · 18-35 · 不限” 胶囊，筛选状态仅保存在本地。
    }

    private void showFilterDialog() {
        DatingFilter draft = new DatingFilter();
        draft.countryMode = filter.countryMode;
        draft.gender = filter.gender;
        draft.ageMin = filter.ageMin;
        draft.ageMax = filter.ageMax;
        draft.goal = filter.goal;

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(8));
        scrollView.addView(root);

        TextView tip = new TextView(this);
        tip.setText(R.string.dating_filter_tip);
        tip.setTextColor(Color.rgb(122, 53, 66));
        tip.setTextSize(13);
        tip.setLineSpacing(dp(2), 1f);
        root.addView(tip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView countryRow = filterRow(getCountryText(draft.countryMode));
        TextView genderRow = filterRow(getGenderText(draft.gender));
        TextView ageRow = filterRow(getAgeText(draft.ageMin, draft.ageMax));
        TextView goalRow = filterRow(getGoalText(draft.goal));
        root.addView(countryRow);
        root.addView(genderRow);
        root.addView(ageRow);
        root.addView(goalRow);

        countryRow.setOnClickListener(v -> {
            if (DatingFilter.COUNTRY_SMART.equals(draft.countryMode)) draft.countryMode = DatingFilter.COUNTRY_SAME;
            else if (DatingFilter.COUNTRY_SAME.equals(draft.countryMode)) draft.countryMode = DatingFilter.COUNTRY_FOREIGN;
            else draft.countryMode = DatingFilter.COUNTRY_SMART;
            countryRow.setText(getCountryText(draft.countryMode));
        });
        genderRow.setOnClickListener(v -> {
            if ("all".equals(draft.gender)) draft.gender = "female";
            else if ("female".equals(draft.gender)) draft.gender = "male";
            else draft.gender = "all";
            genderRow.setText(getGenderText(draft.gender));
        });
        ageRow.setOnClickListener(v -> {
            if (draft.ageMin == 18 && draft.ageMax == 35) { draft.ageMin = 22; draft.ageMax = 35; }
            else if (draft.ageMin == 22 && draft.ageMax == 35) { draft.ageMin = 18; draft.ageMax = 45; }
            else if (draft.ageMin == 18 && draft.ageMax == 45) { draft.ageMin = 30; draft.ageMax = 45; }
            else { draft.ageMin = 18; draft.ageMax = 35; }
            ageRow.setText(getAgeText(draft.ageMin, draft.ageMax));
        });
        goalRow.setOnClickListener(v -> {
            if ("love".equals(draft.goal)) draft.goal = "marriage";
            else if ("marriage".equals(draft.goal)) draft.goal = "all";
            else draft.goal = "love";
            goalRow.setText(getGoalText(draft.goal));
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_filter_title)
                .setView(scrollView)
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(R.string.dating_apply, (dialog, which) -> {
                    filter = draft;
                    filter.save(this);
                    updateFilterSummary();
                    reload();
                })
                .show();
    }

    private TextView filterRow(String text) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(Color.rgb(216, 50, 85));
        row.setTextSize(15);
        row.setTypeface(row.getTypeface(), Typeface.BOLD);
        row.setBackgroundResource(R.drawable.bg_dating_filter_button);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        lp.setMargins(0, dp(12), 0, 0);
        row.setLayoutParams(lp);
        return row;
    }

    private String getCountryText(String mode) {
        if (DatingFilter.COUNTRY_SAME.equals(mode)) return getString(R.string.dating_filter_country_same);
        if (DatingFilter.COUNTRY_FOREIGN.equals(mode)) return getString(R.string.dating_filter_country_foreign);
        return getString(R.string.dating_filter_country_smart);
    }

    private String getGenderText(String gender) {
        if ("female".equals(gender)) return getString(R.string.dating_filter_gender_female);
        if ("male".equals(gender)) return getString(R.string.dating_filter_gender_male);
        return getString(R.string.dating_filter_gender_all);
    }

    private String getAgeText(int min, int max) {
        return getString(R.string.dating_filter_age, min, max);
    }

    private String getGoalText(String goal) {
        if ("marriage".equals(goal)) return getString(R.string.dating_filter_goal_marriage);
        if ("all".equals(goal)) return getString(R.string.dating_filter_goal_all);
        return getString(R.string.dating_filter_goal_love);
    }

    private void showProfilePreview(DatingProfile profile) {
        if (profile == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(profile.safeName());
        if (profile.age > 0) sb.append(" · ").append(profile.age);
        if (!TextUtils.isEmpty(profile.city)) sb.append("\n").append(profile.city);
        sb.append("\n").append(profile.safeRelationshipGoal());
        if (!TextUtils.isEmpty(profile.safeIntro())) sb.append("\n\n").append(profile.safeIntro());
        List<String> tags = profile.safeCoreTags();
        if (!tags.isEmpty()) sb.append("\n\n#").append(TextUtils.join("  #", tags.subList(0, Math.min(tags.size(), 8))));
        new AlertDialog.Builder(this)
                .setTitle(R.string.dating_profile_preview)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.dating_ok, null)
                .show();
    }

    private void showLoading(boolean show, String text, boolean retry) {
        wkVBinding.loadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        wkVBinding.loadingTv.setText(text);
        wkVBinding.retryBtn.setVisibility(retry ? View.VISIBLE : View.GONE);
        wkVBinding.actionBar.setVisibility(show ? View.GONE : View.VISIBLE);
        wkVBinding.deckView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showContent() {
        wkVBinding.loadingLayout.setVisibility(View.GONE);
        wkVBinding.actionBar.setVisibility(View.VISIBLE);
        wkVBinding.deckView.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        String text = String.format(Locale.getDefault(), "%s\n%s", getString(R.string.dating_empty), getString(R.string.dating_empty_tip));
        showLoading(true, text, true);
        wkVBinding.actionBar.setVisibility(View.GONE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
