package com.chat.dating;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Space;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import com.bumptech.glide.Glide;

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
                String action = actionForDirection(direction);
                if (!consumeQuotaForAction(action)) {
                    finishExposure(false);
                    rewindTop(null, false);
                    return;
                }
                finishExposure(true);
                reportSwipe(profile, action, photoIndex);
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
        wkVBinding.editProfileBtn.setOnClickListener(v -> showMyDatingCenterDialog());
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
        String action = actionForDirection(direction);
        if (!DatingQuotaManager.hasQuota(this, myProfile, action)) {
            showQuotaToast(action);
            return;
        }
        animateButton(source);
        performLightHaptic(source);
        playSwipeSound(direction);
        showButtonSwipePreview(direction);
        SwipeAnimationSetting setting = new SwipeAnimationSetting.Builder()
                .setDirection(direction)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(new AccelerateInterpolator())
                .build();
        cardStackManager.setSwipeAnimationSetting(setting);
        wkVBinding.deckView.swipe();
    }

    private void rewindTop(View source) {
        rewindTop(source, true);
    }

    private void rewindTop(View source, boolean consumeFreeQuota) {
        if (currentIndex <= 0) return;
        if (consumeFreeQuota && !DatingQuotaManager.consumeRewind(this)) {
            showToast("今日免费撤回已用完，明天再来。每日免费 " + DatingQuotaManager.rewindDailyLimit() + " 次");
            return;
        }
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

    private void showButtonSwipePreview(Direction direction) {
        DatingCardView card = topCardView();
        if (card == null || wkVBinding == null || wkVBinding.deckView == null) return;
        float width = Math.max(1f, wkVBinding.deckView.getWidth());
        float height = Math.max(1f, wkVBinding.deckView.getHeight());
        if (direction == Direction.Right) card.setSwipeProgress(width * 0.22f, 0f);
        else if (direction == Direction.Left) card.setSwipeProgress(-width * 0.22f, 0f);
        else if (direction == Direction.Top) card.setSwipeProgress(0f, -height * 0.13f);
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
        showMoreProfileDialog(profile);
    }


    private boolean consumeQuotaForAction(String action) {
        if (!DatingQuotaManager.needsQuota(action)) return true;
        if (!DatingQuotaManager.consume(this, myProfile, action)) {
            showQuotaToast(action);
            return false;
        }
        return true;
    }

    private void showQuotaToast(String action) {
        int limit = DatingQuotaManager.dailyLimit(myProfile, action);
        if (DatingSwipeAction.FAVORITE.equals(action)) {
            showToast("今日收藏额度已用完，明天再来。今日额度 " + limit + " 次");
        } else if (DatingSwipeAction.LIKE.equals(action)) {
            showToast("今日喜欢额度已用完，明天再来。今日额度 " + limit + " 次");
        }
    }

    private void showMyDatingCenterDialog() {
        final String[] items = new String[]{
                "编辑交友资料",
                "我的收藏",
                "谁喜欢我（会员）",
                "喜欢/收藏/撤回额度"
        };
        new AlertDialog.Builder(this)
                .setTitle("我的交友")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showEditProfileDialog();
                    } else if (which == 1) {
                        showFavoriteListPlaceholder();
                    } else if (which == 2) {
                        showWhoLikesMePaywall();
                    } else {
                        showQuotaInfoDialog();
                    }
                })
                .show();
    }

    private void showFavoriteListPlaceholder() {
        new AlertDialog.Builder(this)
                .setTitle("我的收藏")
                .setMessage("这里用于查看你收藏过的人。前端入口已预留，后端建议新增 /v1/dating/favorites/list 和 /v1/dating/favorites/remove，收藏不要长期复用 like。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showWhoLikesMePaywall() {
        new AlertDialog.Builder(this)
                .setTitle("谁喜欢我")
                .setMessage("这是适合做会员/付费的功能：只展示喜欢过你的人，不直接解锁聊天；双方喜欢后才可以聊天。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showQuotaInfoDialog() {
        String text = "男：喜欢 40 次/天，收藏 10 次/天\n"
                + "女：喜欢 60 次/天，收藏 20 次/天\n"
                + "撤回：免费 3 次/天\n"
                + "不喜欢：不限额，只做后端频率防刷";
        new AlertDialog.Builder(this)
                .setTitle("今日额度")
                .setMessage(text)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showMoreProfileDialog(DatingProfile profile) {
        if (profile == null) return;
        final Dialog dialog = new Dialog(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(242, 242, 245));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), 0, dp(14), dp(116));
        scrollView.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Color.rgb(230, 230, 235));
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(440));
        coverLp.setMargins(0, 0, 0, dp(12));
        content.addView(cover, coverLp);
        if (!TextUtils.isEmpty(profile.firstPhoto())) {
            Glide.with(this)
                    .load(DatingImageSource.resolve(this, profile.firstPhoto()))
                    .override(1080, 1600)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(cover);
        }

        LinearLayout card = whiteCard();
        TextView name = titleText(profile.safeName() + (profile.age > 0 ? " " + profile.age : "") + flagSuffix(profile));
        card.addView(name);
        String meta = profileMetaLine(profile);
        if (!TextUtils.isEmpty(meta)) card.addView(bodyText(meta, 14, true));
        addSection(card, "关于我", profile.safeIntro());
        addSection(card, "恋爱期待", buildLoveExpectation(profile));
        addTags(card, profile.safeCoreTags(), 10);
        content.addView(card);

        LinearLayout safetyCard = whiteCard();
        TextView safetyTitle = bodyText("安全操作", 14, true);
        safetyCard.addView(safetyTitle);
        LinearLayout safetyRow = new LinearLayout(this);
        safetyRow.setOrientation(LinearLayout.HORIZONTAL);
        safetyRow.setGravity(Gravity.CENTER);
        safetyRow.setPadding(0, dp(12), 0, 0);
        TextView block = smallPill("屏蔽");
        TextView report = smallPill("举报");
        safetyRow.addView(block, new LinearLayout.LayoutParams(0, dp(40), 1f));
        Space safetySpace = new Space(this);
        safetyRow.addView(safetySpace, new LinearLayout.LayoutParams(dp(12), 1));
        safetyRow.addView(report, new LinearLayout.LayoutParams(0, dp(40), 1f));
        safetyCard.addView(safetyRow);
        content.addView(safetyCard);

        block.setOnClickListener(v -> confirmBlock(dialog, profile));
        report.setOnClickListener(v -> confirmReport(dialog, profile));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(0, dp(10), 0, dp(18));
        bottom.setBackgroundResource(R.drawable.bg_dating_more_bottom_bar);
        TextView pass = dialogActionButton("×", 62, "#FFFF4F6A", R.drawable.bg_dating_action_pass);
        TextView favorite = dialogActionButton("★", 56, "#FF61A0FF", R.drawable.bg_dating_action_favorite);
        TextView like = dialogActionButton("♥", 68, "#FFFF426D", R.drawable.bg_dating_action_like);
        bottom.addView(pass);
        bottom.addView(space(16));
        bottom.addView(favorite);
        bottom.addView(space(16));
        bottom.addView(like);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(102), Gravity.BOTTOM);
        root.addView(bottom, bottomLp);

        pass.setOnClickListener(v -> handleMoreProfileAction(dialog, profile, Direction.Left));
        favorite.setOnClickListener(v -> handleMoreProfileAction(dialog, profile, Direction.Top));
        like.setOnClickListener(v -> handleMoreProfileAction(dialog, profile, Direction.Right));

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void showEditProfileDialog() {
        final Dialog dialog = new Dialog(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(242, 242, 245));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(22), dp(16), dp(28));
        scrollView.addView(content);
        root.addView(scrollView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = titleText("编辑交友资料");
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = smallPill("关闭");
        header.addView(close, new LinearLayout.LayoutParams(dp(72), dp(38)));
        content.addView(header);

        LinearLayout photoCard = whiteCard();
        photoCard.addView(bodyText("交友照片（最多 6 张，至少 2 张才能开启交友）", 14, true));
        addSixPhotoSlots(photoCard, myProfile == null ? null : myProfile.safePhotos());
        content.addView(photoCard);

        LinearLayout infoCard = whiteCard();
        infoCard.addView(bodyText("交友资料和唐僧叨叨基础资料共享昵称、头像、年龄、国家；恋爱意向、异国恋设置、交友照片独立。", 14, false));
        TextView copy = largeButton("复制语伴资料");
        TextView favorites = largeButton("查看我的收藏");
        TextView likers = largeButton("谁喜欢我（会员）");
        TextView save = largeButton("保存资料");
        infoCard.addView(copy);
        infoCard.addView(favorites);
        infoCard.addView(likers);
        infoCard.addView(save);
        content.addView(infoCard);

        close.setOnClickListener(v -> dialog.dismiss());
        copy.setOnClickListener(v -> DatingModel.getInstance().copyPartnerProfile((code, msg, data) -> {
            if (data != null) myProfile = data;
            showToast(code == HttpResponseCode.success ? "已复制语伴资料" : (TextUtils.isEmpty(msg) ? "复制失败" : msg));
            dialog.dismiss();
        }));
        favorites.setOnClickListener(v -> showFavoriteListPlaceholder());
        likers.setOnClickListener(v -> showWhoLikesMePaywall());
        save.setOnClickListener(v -> {
            Map<String, Object> body = new HashMap<>();
            body.put("enabled", myProfile != null && myProfile.enabled == 1 ? 1 : 0);
            body.put("photos", myProfile == null ? new ArrayList<>() : myProfile.safePhotos());
            DatingModel.getInstance().saveProfile(body, (code, msg, data) -> {
                if (data != null) myProfile = data;
                showToast(code == HttpResponseCode.success ? "已保存交友资料" : (TextUtils.isEmpty(msg) ? "保存失败" : msg));
                dialog.dismiss();
            });
        });

        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void addSixPhotoSlots(LinearLayout parent, List<String> photos) {
        ArrayList<String> list = new ArrayList<>();
        if (photos != null) list.addAll(photos.subList(0, Math.min(photos.size(), DatingPhotoPolicy.MAX_PHOTO_COUNT)));
        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(126));
            rowLp.setMargins(0, dp(12), 0, 0);
            parent.addView(rowLayout, rowLp);
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                FrameLayout slot = new FrameLayout(this);
                slot.setBackgroundResource(R.drawable.bg_dating_photo_slot);
                TextView plus = new TextView(this);
                plus.setText(index < list.size() ? "" : "+\n上传");
                plus.setTextColor(Color.rgb(145, 145, 155));
                plus.setTextSize(14);
                plus.setGravity(Gravity.CENTER);
                slot.addView(plus, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                if (index < list.size()) {
                    ImageView img = new ImageView(this);
                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    slot.addView(img, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                    Glide.with(this).load(DatingImageSource.resolve(this, list.get(index))).centerCrop().into(img);
                }
                slot.setOnClickListener(v -> showToast("上传页下一步接相册选择；选择后调用 DatingPhotoCompressor 压缩 WebP"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                if (col > 0) lp.setMarginStart(dp(10));
                rowLayout.addView(slot, lp);
            }
        }
    }

    private void handleMoreProfileAction(Dialog dialog, DatingProfile profile, Direction direction) {
        if (dialog != null) dialog.dismiss();
        if (profile == null) return;
        DatingProfile top = cardAdapter == null ? null : cardAdapter.getProfile(currentIndex);
        if (top != null && TextUtils.equals(top.safeUid(), profile.safeUid())) {
            swipeTop(null, direction);
            return;
        }
        String action = actionForDirection(direction);
        if (!consumeQuotaForAction(action)) return;
        reportSwipe(profile, action, 0);
    }

    private void confirmBlock(Dialog dialog, DatingProfile profile) {
        new AlertDialog.Builder(this)
                .setTitle("屏蔽此人？")
                .setMessage("屏蔽后将不再推荐给你。")
                .setNegativeButton("取消", null)
                .setPositiveButton("屏蔽", (d, which) -> DatingModel.getInstance().block(profile.safeUid(), (code, msg, data) -> {
                    showToast(code == HttpResponseCode.success ? "已屏蔽" : (TextUtils.isEmpty(msg) ? "屏蔽失败" : msg));
                    if (dialog != null) dialog.dismiss();
                }))
                .show();
    }

    private void confirmReport(Dialog dialog, DatingProfile profile) {
        new AlertDialog.Builder(this)
                .setTitle("举报此人？")
                .setMessage("确认举报后，管理员可以在后台处理资料和照片。")
                .setNegativeButton("取消", null)
                .setPositiveButton("举报", (d, which) -> DatingModel.getInstance().report(profile.safeUid(), "dating_profile", (code, msg, data) -> {
                    showToast(code == HttpResponseCode.success ? "已举报" : (TextUtils.isEmpty(msg) ? "举报失败" : msg));
                    if (dialog != null) dialog.dismiss();
                }))
                .show();
    }

    private LinearLayout whiteCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_dating_profile_white_card);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private void addSection(LinearLayout parent, String title, String body) {
        if (TextUtils.isEmpty(body)) return;
        TextView titleView = bodyText(title, 13, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, dp(16), 0, dp(6));
        parent.addView(titleView, titleLp);
        parent.addView(bodyText(body, 15, false));
    }

    private void addTags(LinearLayout parent, List<String> tags, int maxCount) {
        if (tags == null || tags.isEmpty()) return;
        TextView title = bodyText("标签", 13, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, dp(16), 0, dp(6));
        parent.addView(title, titleLp);
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String tag : tags) {
            if (TextUtils.isEmpty(tag)) continue;
            if (count >= maxCount) break;
            if (builder.length() > 0) builder.append("   ");
            builder.append("#").append(tag.trim());
            count++;
        }
        parent.addView(bodyText(builder.toString(), 14, false));
    }

    private TextView titleText(String text) {
        TextView view = new TextView(this);
        view.setText(text == null ? "" : text);
        view.setTextColor(Color.rgb(25, 25, 30));
        view.setTextSize(26);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView bodyText(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text == null ? "" : text);
        view.setTextColor(Color.rgb(55, 55, 65));
        view.setTextSize(sp);
        view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private TextView smallPill(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.rgb(85, 85, 95));
        view.setTextSize(14);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(246, 246, 248));
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.rgb(225, 225, 230));
        view.setBackground(bg);
        return view;
    }

    private TextView largeButton(String text) {
        TextView view = smallPill(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp.setMargins(0, dp(12), 0, 0);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView dialogActionButton(String text, int sizeDp, String colorString, int backgroundRes) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTextColor(Color.parseColor(colorString));
        view.setTextSize(sizeDp >= 64 ? 39 : 29);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setBackgroundResource(backgroundRes);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        view.setLayoutParams(lp);
        return view;
    }

    private Space space(int widthDp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1));
        return space;
    }

    private String profileMetaLine(DatingProfile profile) {
        if (profile == null) return "";
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

    private String buildLoveExpectation(DatingProfile profile) {
        if (profile == null) return "";
        StringBuilder line = new StringBuilder();
        if (!TextUtils.isEmpty(profile.safeRelationshipGoal())) line.append(profile.safeRelationshipGoal());
        if (!TextUtils.isEmpty(profile.safeCrossBorderPreference())) {
            if (line.length() > 0) line.append(" · ");
            line.append(profile.safeCrossBorderPreference());
        }
        if (!TextUtils.isEmpty(profile.relationship_status)) {
            if (line.length() > 0) line.append(" · ");
            line.append(profile.relationship_status);
        }
        return line.toString();
    }

    private String flagSuffix(DatingProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.safeCountryCode())) return "";
        return " " + flagEmoji(profile.safeCountryCode());
    }


    private String flagEmoji(String countryCode) {
        if (TextUtils.isEmpty(countryCode) || countryCode.length() < 2) return "";
        String code = countryCode.trim().toUpperCase(Locale.US);
        int first = Character.codePointAt(code, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(code, 1) - 'A' + 0x1F1E6;
        if (first < 0x1F1E6 || first > 0x1F1FF || second < 0x1F1E6 || second > 0x1F1FF) return "";
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
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
