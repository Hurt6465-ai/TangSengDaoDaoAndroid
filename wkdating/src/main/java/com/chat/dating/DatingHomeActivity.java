package com.chat.dating;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingHomeBinding;
import com.chat.dating.model.DatingProfile;
import com.chat.dating.model.DatingSwipeResult;
import com.yuyakaido.android.cardstackview.CardStackLayoutManager;
import com.yuyakaido.android.cardstackview.CardStackListener;
import com.yuyakaido.android.cardstackview.Direction;
import com.yuyakaido.android.cardstackview.Duration;
import com.yuyakaido.android.cardstackview.RewindAnimationSetting;
import com.yuyakaido.android.cardstackview.StackFrom;
import com.yuyakaido.android.cardstackview.SwipeAnimationSetting;
import com.yuyakaido.android.cardstackview.SwipeableMethod;

import java.util.ArrayList;
import java.util.Arrays;
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
    private DatingCardStackAdapter cardAdapter;
    private CardStackLayoutManager cardStackManager;
    private int currentIndex;
    private int lastSwipedIndex = -1;
    private boolean dragFeedbackTriggered;
    private SoundPool soundPool;
    private int likeSoundId;
    private int passSoundId;
    private int favoriteSoundId;

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
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView() {
        sessionId = UUID.randomUUID().toString();
        filter = DatingFilter.load(this);
        myProfile = DatingMockData.demoMyProfile();
        initFeedback();
        initCardStack();
        updateScopeTabs();
        updateFilterSummary();
        showLoading(true, getString(R.string.dating_loading), false);
    }

    private void initCardStack() {
        cardAdapter = new DatingCardStackAdapter();
        cardAdapter.setOnCardTapListener(new DatingCardStackAdapter.OnCardTapListener() {
            @Override
            public void onPreviousPhoto(DatingProfile profile, int position, int photoIndex) {
            }

            @Override
            public void onNextPhoto(DatingProfile profile, int position, int photoIndex) {
                DatingImagePreloader.preloadAround(DatingHomeActivity.this, profiles, position);
            }

            @Override
            public void onOpenProfile(DatingProfile profile, int position, int photoIndex) {
                showProfilePreview(profile);
            }
        });

        cardStackManager = new CardStackLayoutManager(this, new CardStackListener() {
            @Override
            public void onCardDragging(Direction direction, float ratio) {
                updateTopSwipeOverlay(direction, ratio);
                handleDragFeedback(direction, ratio);
            }

            @Override
            public void onCardSwiped(Direction direction) {
                int swipedIndex = Math.max(0, currentIndex);
                DatingProfile profile = cardAdapter.getProfile(swipedIndex);
                int photoIndex = cardAdapter.getPhotoIndex(swipedIndex);
                lastSwipedIndex = swipedIndex;
                dragFeedbackTriggered = false;
                currentIndex = Math.min(cardAdapter.getItemCount(), swipedIndex + 1);
                clearSwipeOverlay(swipedIndex);
                finishExposure(true);
                reportSwipe(profile, actionForDirection(direction), photoIndex);
                if (!loading && !noMore && remainingCount() <= 4) loadMore(false);
                if (remainingCount() == 0 && noMore) showEmpty();
            }

            @Override
            public void onCardRewound() {
                if (lastSwipedIndex >= 0) {
                    currentIndex = Math.max(0, lastSwipedIndex);
                    startExposure(cardAdapter.getProfile(currentIndex));
                    lastSwipedIndex = -1;
                }
            }

            @Override
            public void onCardCanceled() {
                dragFeedbackTriggered = false;
                clearSwipeOverlay(currentIndex);
            }

            @Override
            public void onCardAppeared(View view, int position) {
                currentIndex = Math.max(0, position);
                clearSwipeOverlay(position);
                finishExposure(false);
                DatingProfile profile = cardAdapter.getProfile(position);
                startExposure(profile);
                DatingImagePreloader.preloadAround(DatingHomeActivity.this, profiles, position);
                if (!loading && !noMore && remainingCount() <= 4) loadMore(false);
            }

            @Override
            public void onCardDisappeared(View view, int position) {
                if (view instanceof DatingCardView) {
                    ((DatingCardView) view).setSwipeProgress(0f, 0f);
                }
            }
        });
        cardStackManager.setStackFrom(StackFrom.None);
        cardStackManager.setVisibleCount(3);
        cardStackManager.setTranslationInterval(7.0f);
        cardStackManager.setScaleInterval(0.965f);
        cardStackManager.setSwipeThreshold(0.27f);
        cardStackManager.setMaxDegree(18.0f);
        cardStackManager.setDirections(Arrays.asList(Direction.Left, Direction.Right, Direction.Top));
        cardStackManager.setCanScrollHorizontal(true);
        cardStackManager.setCanScrollVertical(true);
        cardStackManager.setSwipeableMethod(SwipeableMethod.AutomaticAndManual);
        cardStackManager.setOverlayInterpolator(new LinearInterpolator());

        wkVBinding.deckView.setLayoutManager(cardStackManager);
        wkVBinding.deckView.setAdapter(cardAdapter);
        wkVBinding.deckView.setItemAnimator(null);
    }

    @Override
    protected void initListener() {
        wkVBinding.retryBtn.setOnClickListener(v -> reload());
        wkVBinding.rewindBtn.setOnClickListener(v -> rewindTop(v));
        wkVBinding.passBtn.setOnClickListener(v -> swipeTop(v, Direction.Left));
        wkVBinding.favoriteBtn.setOnClickListener(v -> swipeTop(v, Direction.Top));
        wkVBinding.likeBtn.setOnClickListener(v -> swipeTop(v, Direction.Right));
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
        releaseFeedback();
        super.onDestroy();
    }

    private void initFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build();
        } else {
            soundPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
        }
        likeSoundId = soundPool.load(this, R.raw.dating_like, 1);
        passSoundId = soundPool.load(this, R.raw.dating_pass, 1);
        favoriteSoundId = soundPool.load(this, R.raw.dating_favorite, 1);
    }

    private void releaseFeedback() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    private void handleDragFeedback(Direction direction, float ratio) {
        if (direction == null) return;
        if (ratio >= 0.56f && !dragFeedbackTriggered) {
            dragFeedbackTriggered = true;
            performLightHaptic(wkVBinding.deckView);
            playSwipeSound(direction);
        } else if (ratio < 0.44f) {
            dragFeedbackTriggered = false;
        }
    }

    private void performLightHaptic(View view) {
        if (view == null) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignored) {
        }
    }

    private void playSwipeSound(Direction direction) {
        if (soundPool == null) return;
        try {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null) {
                if (audioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
                if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return;
            }
            int soundId = passSoundId;
            if (direction == Direction.Right) soundId = likeSoundId;
            else if (direction == Direction.Top) soundId = favoriteSoundId;
            if (soundId != 0) soundPool.play(soundId, 0.42f, 0.42f, 1, 0, 1.0f);
        } catch (Throwable ignored) {
        }
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
        currentIndex = 0;
        lastSwipedIndex = -1;
        cardAdapter.setProfiles(profiles);
        wkVBinding.deckView.scrollToPosition(0);
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
            currentIndex = 0;
            lastSwipedIndex = -1;
            cardAdapter.setProfiles(profiles);
            wkVBinding.deckView.scrollToPosition(0);
        } else if (!clean.isEmpty()) {
            profiles.addAll(clean);
            for (DatingProfile p : clean) loadedUids.add(p.safeUid());
            cardAdapter.appendProfiles(clean);
        }
        if (profiles.isEmpty()) showEmpty();
        else showContent();
        if (demo) showToast(R.string.dating_mock_tip);
        DatingImagePreloader.preloadAround(this, profiles, currentIndex);
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

    private int remainingCount() {
        return Math.max(0, cardAdapter == null ? 0 : cardAdapter.getItemCount() - currentIndex);
    }

    private void swipeTop(View source, Direction direction) {
        if (remainingCount() <= 0) return;
        animateButton(source);
        performLightHaptic(source);
        playSwipeSound(direction);
        SwipeAnimationSetting setting = new SwipeAnimationSetting.Builder()
                .setDirection(direction)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(new AccelerateInterpolator())
                .build();
        cardStackManager.setSwipeAnimationSetting(setting);
        wkVBinding.deckView.swipe();
    }

    private void rewindTop(View source) {
        if (currentIndex <= 0) return;
        animateButton(source);
        performLightHaptic(source);
        RewindAnimationSetting setting = new RewindAnimationSetting.Builder()
                .setDirection(Direction.Bottom)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(new DecelerateInterpolator())
                .build();
        cardStackManager.setRewindAnimationSetting(setting);
        wkVBinding.deckView.rewind();
    }

    private void animateButton(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setScaleX(0.92f);
        view.setScaleY(0.92f);
        view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        ).start();
    }

    private void updateTopSwipeOverlay(Direction direction, float ratio) {
        DatingCardView card = topCardView();
        if (card == null) return;
        float width = Math.max(1f, wkVBinding.deckView.getWidth());
        float height = Math.max(1f, wkVBinding.deckView.getHeight());
        float clamped = Math.max(0f, Math.min(1f, ratio));
        if (direction == Direction.Right) card.setSwipeProgress(width * clamped, 0f);
        else if (direction == Direction.Left) card.setSwipeProgress(-width * clamped, 0f);
        else if (direction == Direction.Top) card.setSwipeProgress(0f, -height * clamped);
        else card.setSwipeProgress(0f, 0f);
    }

    private void clearSwipeOverlay(int position) {
        RecyclerView.ViewHolder holder = wkVBinding.deckView.findViewHolderForAdapterPosition(position);
        if (holder instanceof DatingCardStackAdapter.CardHolder) {
            ((DatingCardStackAdapter.CardHolder) holder).card.setSwipeProgress(0f, 0f);
        }
    }

    private DatingCardView topCardView() {
        RecyclerView.ViewHolder holder = wkVBinding.deckView.findViewHolderForAdapterPosition(currentIndex);
        if (holder instanceof DatingCardStackAdapter.CardHolder) {
            return ((DatingCardStackAdapter.CardHolder) holder).card;
        }
        return null;
    }

    private String actionForDirection(Direction direction) {
        if (direction == Direction.Right) return DatingSwipeAction.LIKE;
        if (direction == Direction.Top) return DatingSwipeAction.FAVORITE;
        return DatingSwipeAction.PASS;
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
        tab.setTextColor(selected ? Color.WHITE : 0x8CFFFFFF);
        tab.setTextSize(selected ? 21 : 17);
        tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setAlpha(selected ? 1f : 0.72f);
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
