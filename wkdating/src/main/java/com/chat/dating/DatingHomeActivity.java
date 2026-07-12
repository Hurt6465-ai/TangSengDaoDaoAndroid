package com.chat.dating;

import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.net.HttpResponseCode;
import com.chat.dating.databinding.ActivityWkDatingHomeBinding;
import com.chat.dating.model.DatingProfile;
import com.chat.dating.model.DatingUndoResult;
import com.yuyakaido.android.cardstackview.CardStackLayoutManager;
import com.yuyakaido.android.cardstackview.CardStackListener;
import com.yuyakaido.android.cardstackview.Direction;
import com.yuyakaido.android.cardstackview.Duration;
import com.yuyakaido.android.cardstackview.RewindAnimationSetting;
import com.yuyakaido.android.cardstackview.StackFrom;
import com.yuyakaido.android.cardstackview.SwipeAnimationSetting;
import com.yuyakaido.android.cardstackview.SwipeableMethod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 交友首页只保留：首页推荐、滑卡、分页、曝光、动作回调。
 * 资料详情、我的、编辑资料、收藏、匹配列表全部拆到独立 Activity，避免继续堆成巨型类。
 */
public class DatingHomeActivity extends WKBaseActivity<ActivityWkDatingHomeBinding> {
    private static final int PAGE_LIMIT = 12;
    private static final int MAX_SWIPE_RETRY = 1;
    private static final int REQ_PROFILE_DETAIL = 401;
    private static final int REQ_MINE = 402;

    private final ArrayList<DatingProfile> profiles = new ArrayList<>();
    private final ArrayList<Map<String, Object>> pendingExposures = new ArrayList<>();
    private final ArrayList<Map<String, Object>> inflightExposures = new ArrayList<>();
    private final HashSet<String> loadedUids = new HashSet<>();

    private String cursor = "";
    private String scope = "global";
    private String sessionId;
    private boolean loading;
    private boolean noMore;
    private boolean initialLocationRequested;
    private long exposureStartMs;
    private String exposureUid = "";
    private int visiblePosition;
    private final ArrayDeque<SwipeRecord> swipeHistory = new ArrayDeque<>();
    private SwipeRecord pendingRewindRecord;
    private boolean pendingRewindRefundAction;
    private boolean pendingRewindRemoveFavorite;
    private boolean interactionLocked;
    private boolean exposureUploading;

    private DatingProfile myProfile;
    private DatingFilter filter;
    private DatingCardStackAdapter cardAdapter;
    private CardStackLayoutManager cardStackManager;
    private DatingActionController actionController;
    private DatingLocationHelper locationHelper;

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
        DatingUi.applyFullscreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView() {
        sessionId = UUID.randomUUID().toString();
        filter = DatingFilter.load(this);
        actionController = new DatingActionController(this);
        locationHelper = new DatingLocationHelper(this);
        pendingExposures.addAll(DatingExposureQueue.load(this));
        DatingUi.applyHomeInsets(wkVBinding.getRoot(), wkVBinding.topBar, wkVBinding.actionBar);
        initCardStack();
        updateScopeTabs();
        showLoading(true, getString(R.string.dating_loading), false);
    }

    @Override
    protected void initListener() {
        wkVBinding.retryBtn.setOnClickListener(v -> {
            if (myProfile == null || myProfile.enabled != 1) {
                Intent intent = new Intent(this, DatingMineActivity.class);
                intent.putExtra(DatingMineActivity.EXTRA_PROFILE, myProfile);
                startActivityForResult(intent, REQ_MINE);
            } else {
                reload();
            }
        });
        wkVBinding.rewindBtn.setOnClickListener(v -> rewindTop(v, true));
        wkVBinding.passBtn.setOnClickListener(v -> swipeTop(v, Direction.Left));
        wkVBinding.favoriteBtn.setOnClickListener(v -> swipeTop(v, Direction.Top));
        wkVBinding.likeBtn.setOnClickListener(v -> swipeTop(v, Direction.Right));
        wkVBinding.partnerModeTab.setOnClickListener(v -> openPartnerList());
        wkVBinding.datingModeTab.setOnClickListener(v -> {
            // 当前已经是交友模式，不重复创建页面。
        });
        wkVBinding.recommendTab.setOnClickListener(v -> selectScope("global"));
        wkVBinding.nearbyTab.setOnClickListener(v -> selectScope("nearby"));
        wkVBinding.filterBtn.setOnClickListener(v -> DatingFilterDialog.show(this, filter, value -> {
            filter = value;
            reload();
        }));
        wkVBinding.mineBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, DatingMineActivity.class);
            intent.putExtra(DatingMineActivity.EXTRA_PROFILE, myProfile);
            startActivityForResult(intent, REQ_MINE);
        });
    }

    @Override
    protected void initData() {
        loadMyProfileThenRecommend();
    }

    private void initCardStack() {
        cardAdapter = new DatingCardStackAdapter();
        cardAdapter.setOnCardTapListener(new DatingCardStackAdapter.OnCardTapListener() {
            @Override public void onPreviousPhoto(DatingProfile profile, int position, int photoIndex) {}
            @Override public void onNextPhoto(DatingProfile profile, int position, int photoIndex) {
                // 周边预加载只由 onCardAppeared 统一触发，避免每次切图重复请求。
            }
            @Override public void onOpenProfile(DatingProfile profile, int position, int photoIndex) {
                openProfileDetail(profile, photoIndex);
            }
        });

        cardStackManager = new CardStackLayoutManager(this, new CardStackListener() {
            @Override
            public void onCardDragging(Direction direction, float ratio) {
                DatingCardView card = topCardView();
                if (card != null) card.setDragProgress(direction, ratio);
                actionController.onDragging(wkVBinding.deckView, direction, ratio);
            }

            @Override
            public void onCardSwiped(Direction direction) {
                int swipedPosition = Math.max(0, cardStackManager.getTopPosition() - 1);
                DatingProfile profile = cardAdapter.getProfile(swipedPosition);
                int photoIndex = cardAdapter.getPhotoIndex(swipedPosition);
                String action = actionForDirection(direction);
                actionController.resetDragFeedback();

                if (profile == null) return;
                SwipeRecord record = new SwipeRecord(profile, swipedPosition, action, photoIndex);
                if (!actionController.consume(action)) {
                    restoreSwipedCard(record, false, false, actionController.quotaMessage(action));
                    return;
                }
                playSwipeEffect(direction);

                swipeHistory.addLast(record);
                while (swipeHistory.size() > 30) swipeHistory.removeFirst();
                finishExposure(true);

                setInteractionLocked(true);
                reportSwipe(record, 0);
            }

            @Override
            public void onCardRewound() {
                int top = cardStackManager.getTopPosition();
                visiblePosition = Math.max(0, top);
                SwipeRecord record = pendingRewindRecord;
                boolean refundAction = pendingRewindRefundAction;
                pendingRewindRecord = null;
                pendingRewindRefundAction = false;
                pendingRewindRemoveFavorite = false;
                if (record != null && refundAction) actionController.refund(record.action);
                setInteractionLocked(false);
                DatingProfile profile = cardAdapter.getProfile(visiblePosition);
                if (profile != null) startExposure(profile);
            }

            @Override
            public void onCardCanceled() {
                actionController.resetDragFeedback();
                DatingCardView card = topCardView();
                if (card != null) card.resetDragProgress();
            }

            @Override
            public void onCardAppeared(View view, int position) {
                visiblePosition = Math.max(0, position);
                if (view instanceof DatingCardView) ((DatingCardView) view).resetDragProgress();
                finishExposure(false);
                DatingProfile profile = cardAdapter.getProfile(position);
                startExposure(profile);
                DatingImagePreloader.preloadAround(DatingHomeActivity.this, profiles, position);
                if (!loading && !noMore && cardAdapter.getItemCount() - cardStackManager.getTopPosition() <= 4) {
                    loadMore(false);
                }
            }

            @Override
            public void onCardDisappeared(View view, int position) {
                if (view instanceof DatingCardView) ((DatingCardView) view).resetDragProgress();
            }
        });

        // 借鉴 Shuffle/CardSlidePanel：三层实体卡、后卡明显顶上来、快速飞出但不拖沓。
        cardStackManager.setStackFrom(StackFrom.None);
        cardStackManager.setVisibleCount(3);
        cardStackManager.setTranslationInterval(8f);
        cardStackManager.setScaleInterval(0.97f);
        cardStackManager.setSwipeThreshold(0.30f);
        cardStackManager.setMaxDegree(17f);
        cardStackManager.setDirections(Arrays.asList(Direction.Left, Direction.Right, Direction.Top));
        cardStackManager.setCanScrollHorizontal(true);
        cardStackManager.setCanScrollVertical(true);
        cardStackManager.setSwipeableMethod(SwipeableMethod.AutomaticAndManual);
        cardStackManager.setOverlayInterpolator(new LinearInterpolator());

        wkVBinding.deckView.setLayoutManager(cardStackManager);
        // 保留上一批卡片内存优化：只缓存可见栈所需的少量 ViewHolder。
        wkVBinding.deckView.setItemViewCacheSize(0);
        wkVBinding.deckView.getRecycledViewPool().setMaxRecycledViews(0, 3);
        wkVBinding.deckView.setAdapter(cardAdapter);
        wkVBinding.deckView.setItemAnimator(null);
    }

    private void openPartnerList() {
        if (getIntent().getBooleanExtra("from_partner_list", false)) {
            finish();
            return;
        }
        try {
            Object handled = EndpointManager.getInstance().invoke("peipe_open_partner_list", this);
            if (handled instanceof Boolean && (Boolean) handled) {
                finish();
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> clazz = Class.forName("com.chat.partnerlist.PartnerListActivity");
            Intent intent = new Intent(this, clazz);
            startActivity(intent);
            finish();
        } catch (Throwable ignored) {
            showToast(getString(R.string.dating_partner_unavailable));
        }
    }

    private void selectScope(String value) {
        if (TextUtils.equals(scope, value)) return;
        if (!"nearby".equals(value)) {
            scope = value;
            updateScopeTabs();
            reload();
            return;
        }
        locationHelper.ensureLocation(new DatingLocationHelper.Callback() {
            @Override
            public void onSuccess(Location location) {
                DatingModel.getInstance().updateLocation(location.getLatitude(), location.getLongitude(),
                        myProfile == null ? "" : myProfile.city,
                        myProfile == null ? "" : myProfile.safeCountryCode(), (code, msg, data) -> {
                    if (code != HttpResponseCode.success) {
                        showToast(TextUtils.isEmpty(msg) ? getString(R.string.dating_location_failed) : msg);
                        scope = "global";
                        updateScopeTabs();
                        return;
                    }
                    scope = "nearby";
                    updateScopeTabs();
                    reload();
                });
            }

            @Override
            public void onDenied(String message) {
                showToast(message);
                scope = "global";
                updateScopeTabs();
            }
        });
    }

    private void loadMyProfileThenRecommend() {
        fetchMyProfile(false);
    }

    /**
     * 首次进入只加载第一页；从“我的/编辑资料”返回时必须先清空旧推荐池再重新推荐。
     * 否则旧 loadedUids 会把新一页相同用户全部过滤掉，页面会错误显示为空。
     */
    private void fetchMyProfile(boolean resetRecommendation) {
        DatingModel.getInstance().getMyDatingProfile((code, msg, data) -> {
            if (data != null) myProfile = data;
            if (myProfile == null && BuildConfig.DEBUG) myProfile = DatingMockData.demoMyProfile();
            actionController.setMyProfile(myProfile);
            if (myProfile == null) {
                showLoading(true, TextUtils.isEmpty(msg) ? getString(R.string.dating_profile_load_failed) : msg, true);
                wkVBinding.retryBtn.setText(R.string.dating_retry);
                return;
            }
            if (myProfile.enabled != 1) {
                profiles.clear();
                loadedUids.clear();
                cardAdapter.submitProfiles(profiles);
                showLoading(true, getString(R.string.dating_enable_first), true);
                wkVBinding.retryBtn.setText(R.string.dating_go_enable);
                return;
            }
            wkVBinding.retryBtn.setText(R.string.dating_retry);
            requestInitialLocationThenLoad(resetRecommendation);
        });
    }

    private void requestInitialLocationThenLoad(boolean resetRecommendation) {
        if (initialLocationRequested) {
            if (resetRecommendation) reload();
            else loadMore(true);
            return;
        }
        initialLocationRequested = true;
        locationHelper.ensureLocation(new DatingLocationHelper.Callback() {
            @Override
            public void onSuccess(Location location) {
                DatingModel.getInstance().updateLocation(location.getLatitude(), location.getLongitude(),
                        myProfile == null ? "" : myProfile.city,
                        myProfile == null ? "" : myProfile.safeCountryCode(), (code, msg, data) -> {
                    if (resetRecommendation) reload();
                    else loadMore(true);
                });
            }

            @Override
            public void onDenied(String message) {
                if (!TextUtils.isEmpty(message)) showToast(message);
                if (resetRecommendation) reload();
                else loadMore(true);
            }
        });
    }

    private void reload() {
        finishExposure(true);
        cursor = "";
        noMore = false;
        loading = false;
        profiles.clear();
        loadedUids.clear();
        flushExposures();
        visiblePosition = 0;
        swipeHistory.clear();
        pendingRewindRecord = null;
        pendingRewindRefundAction = false;
        pendingRewindRemoveFavorite = false;
        setInteractionLocked(false);
        cardAdapter.submitProfiles(profiles);
        cardStackManager.setTopPosition(0);
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
                noMore = !data.hasMore();
                appendProfiles(data.getItems(), firstPage, false);
                return;
            }
            if (firstPage && BuildConfig.DEBUG) {
                appendProfiles(DatingMockData.demoProfiles(), true, true);
                noMore = true;
            } else if (firstPage) {
                showLoading(true, TextUtils.isEmpty(msg) ? getString(R.string.dating_empty) : msg, true);
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
            for (DatingProfile item : clean) loadedUids.add(item.safeUid());
            cardAdapter.submitProfiles(profiles);
            cardStackManager.setTopPosition(0);
            wkVBinding.deckView.scrollToPosition(0);
            visiblePosition = 0;
        } else if (!clean.isEmpty()) {
            profiles.addAll(clean);
            for (DatingProfile item : clean) loadedUids.add(item.safeUid());
            cardAdapter.appendProfiles(clean);
        }
        if (profiles.isEmpty()) showEmpty();
        else showContent();
        if (demo) showToast(getString(R.string.dating_demo_tip));
    }

    private List<DatingProfile> cleanProfiles(List<DatingProfile> data) {
        ArrayList<DatingProfile> clean = new ArrayList<>();
        if (data == null) return clean;
        long thirtyDaysAgo = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L;
        for (DatingProfile profile : data) {
            if (profile == null || TextUtils.isEmpty(profile.safeUid()) || profile.safePhotos().isEmpty()) continue;
            if (loadedUids.contains(profile.safeUid())) continue;
            if (profile.last_active_at > 0 && profile.last_active_at < thirtyDaysAgo) continue;
            if (!filter.accepts(myProfile, profile)) continue;
            clean.add(profile);
        }
        return clean;
    }

    private void swipeTop(View source, Direction direction) {
        if (interactionLocked) return;
        int top = cardStackManager.getTopPosition();
        if (top < 0 || top >= cardAdapter.getItemCount()) return;
        String action = actionForDirection(direction);
        if (!actionController.canUse(action)) {
            showToast(actionController.quotaMessage(action));
            return;
        }
        actionController.buttonFeedback(source, direction);
        SwipeAnimationSetting setting = new SwipeAnimationSetting.Builder()
                .setDirection(direction)
                .setDuration(270)
                .setInterpolator(new AccelerateInterpolator())
                .build();
        cardStackManager.setSwipeAnimationSetting(setting);
        wkVBinding.deckView.swipe();
    }

    private void rewindTop(View source, boolean consumeQuota) {
        if (interactionLocked || !consumeQuota) return;
        if (cardStackManager.getTopPosition() <= 0 || swipeHistory.isEmpty()) {
            showToast(getString(R.string.dating_no_undo));
            return;
        }
        if (DatingQuotaManager.rewindRemaining(this) <= 0) {
            showToast(getString(R.string.dating_rewind_quota_empty));
            return;
        }

        SwipeRecord record = swipeHistory.peekLast();
        if (record == null || record.profile == null) {
            showToast(getString(R.string.dating_no_undo));
            return;
        }
        setInteractionLocked(true);
        actionController.animateButton(source);
        DatingModel.getInstance().undoSwipe((code, msg, result) -> {
            if (isFinishing() || isDestroyed()) return;
            if (code != HttpResponseCode.success || result == null) {
                setInteractionLocked(false);
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.dating_rewind_failed) : msg);
                return;
            }
            if (!actionController.consumeRewind()) {
                // 理论上不会发生：请求前已检查额度且交互已锁定。服务端已撤回时刷新保持一致。
                applyServerUndoFallback(result);
                swipeHistory.clear();
                setInteractionLocked(false);
                showToast(getString(R.string.dating_rewind_synced_refresh));
                reload();
                return;
            }
            String serverTarget = result.target_uid == null ? "" : result.target_uid.trim();
            if (!TextUtils.equals(serverTarget, record.profile.safeUid())) {
                // 历史版本可能留下本地/服务端不一致记录。服务端是最终权威，直接刷新推荐池。
                applyServerUndoFallback(result);
                swipeHistory.clear();
                setInteractionLocked(false);
                showToast(getString(R.string.dating_rewind_record_synced));
                reload();
                return;
            }
            swipeHistory.pollLast();
            restoreSwipedCard(record, true, false, null);
        });
    }

    private String actionForDirection(Direction direction) {
        if (direction == Direction.Right) return DatingSwipeAction.LIKE;
        if (direction == Direction.Top) return DatingSwipeAction.FAVORITE;
        return DatingSwipeAction.PASS;
    }

    private void reportSwipe(SwipeRecord record, int retryCount) {
        if (record == null || record.profile == null) {
            setInteractionLocked(false);
            return;
        }
        DatingProfile profile = record.profile;
        DatingModel.getInstance().swipe(profile.safeUid(), record.action, record.photoIndex, sessionId, (code, msg, result) -> {
            if (isFinishing() || isDestroyed()) return;
            if (code != HttpResponseCode.success) {
                // 网络/服务异常先原样重试一次。后端已有短时幂等，响应丢失也不会重复扣额度。
                if (retryCount < MAX_SWIPE_RETRY && (code <= 0 || code >= 500)) {
                    reportSwipe(record, retryCount + 1);
                    return;
                }
                swipeHistory.remove(record);
                restoreSwipedCard(record, true, false,
                        TextUtils.isEmpty(msg) ? getString(R.string.dating_operation_restored) : msg);
                return;
            }
            setInteractionLocked(false);
            afterSwipeConfirmed();
            if (result != null && result.isMatched()) {
                DatingMatchDialog.show(DatingHomeActivity.this, myProfile, profile,
                        () -> DatingUi.openChat(DatingHomeActivity.this, profile.safeUid()));
            }
        });
    }

    private void afterSwipeConfirmed() {
        int top = cardStackManager == null ? 0 : cardStackManager.getTopPosition();
        if (!loading && !noMore && cardAdapter.getItemCount() - top <= 4) loadMore(false);
        if (top >= cardAdapter.getItemCount() && noMore) showEmpty();
    }

    private void restoreSwipedCard(SwipeRecord record, boolean refundAction,
                                   boolean removeFavorite, String message) {
        if (record == null || cardStackManager == null || wkVBinding == null) {
            setInteractionLocked(false);
            return;
        }
        setInteractionLocked(true);
        pendingRewindRecord = record;
        pendingRewindRefundAction = refundAction;
        pendingRewindRemoveFavorite = removeFavorite;
        if (!TextUtils.isEmpty(message)) showToast(message);
        RewindAnimationSetting setting = new RewindAnimationSetting.Builder()
                .setDirection(Direction.Bottom)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .build();
        cardStackManager.setRewindAnimationSetting(setting);
        wkVBinding.deckView.rewind();
    }

    private void applyServerUndoFallback(DatingUndoResult result) {
        if (result == null) return;
        actionController.refund(result.action);
    }

    private void setInteractionLocked(boolean locked) {
        interactionLocked = locked;
        if (cardStackManager != null) {
            cardStackManager.setSwipeableMethod(locked
                    ? SwipeableMethod.None : SwipeableMethod.AutomaticAndManual);
        }
        setActionEnabled(wkVBinding == null ? null : wkVBinding.rewindBtn, !locked);
        setActionEnabled(wkVBinding == null ? null : wkVBinding.passBtn, !locked);
        setActionEnabled(wkVBinding == null ? null : wkVBinding.favoriteBtn, !locked);
        setActionEnabled(wkVBinding == null ? null : wkVBinding.likeBtn, !locked);
    }

    private void setActionEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.58f);
    }

    private void openProfileDetail(DatingProfile profile, int photoIndex) {
        if (profile == null) return;
        Map<String, Object> event = new HashMap<>();
        event.put("to_uid", profile.safeUid());
        event.put("event_type", "profile_open");
        event.put("source", "wkdating");
        event.put("duration_ms", 0);
        event.put("photo_index", photoIndex);
        queueExposure(event);
        Intent intent = new Intent(this, DatingProfileDetailActivity.class);
        intent.putExtra(DatingProfileDetailActivity.EXTRA_PROFILE, profile);
        intent.putExtra(DatingProfileDetailActivity.EXTRA_PHOTO_INDEX, photoIndex);
        startActivityForResult(intent, REQ_PROFILE_DETAIL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROFILE_DETAIL && resultCode == RESULT_OK && data != null) {
            String action = data.getStringExtra(DatingProfileDetailActivity.EXTRA_ACTION);
            if (DatingSwipeAction.PASS.equals(action)) swipeTop(null, Direction.Left);
            else if (DatingSwipeAction.FAVORITE.equals(action)) swipeTop(null, Direction.Top);
            else if (DatingSwipeAction.LIKE.equals(action)) swipeTop(null, Direction.Right);
        } else if (requestCode == REQ_MINE && resultCode == RESULT_OK) {
            showLoading(true, getString(R.string.dating_refresh_after_profile), false);
            fetchMyProfile(true);
        }
    }

    private void startExposure(DatingProfile profile) {
        exposureUid = profile == null ? "" : profile.safeUid();
        exposureStartMs = TextUtils.isEmpty(exposureUid) ? 0L : System.currentTimeMillis();
    }

    private void finishExposure(boolean forceFlush) {
        if (TextUtils.isEmpty(exposureUid) || exposureStartMs <= 0L) return;
        long duration = Math.max(0L, System.currentTimeMillis() - exposureStartMs);
        if (duration >= 250L) {
            Map<String, Object> item = new HashMap<>();
            item.put("to_uid", exposureUid);
            item.put("event_type", "expose");
            item.put("source", "wkdating");
            item.put("duration_ms", duration);
            item.put("photo_index", cardAdapter.getPhotoIndex(Math.max(0, visiblePosition)));
            queueExposure(item);
        }
        exposureUid = "";
        exposureStartMs = 0L;
        if (forceFlush || pendingExposures.size() >= 5) flushExposures();
    }

    private void queueExposure(Map<String, Object> item) {
        if (item == null) return;
        pendingExposures.add(item);
        while (pendingExposures.size() > 100) pendingExposures.remove(0);
        persistExposureQueue();
    }

    private void persistExposureQueue() {
        ArrayList<Map<String, Object>> all = new ArrayList<>(inflightExposures);
        all.addAll(pendingExposures);
        DatingExposureQueue.save(this, all);
    }

    private void flushExposures() {
        if (exposureUploading || pendingExposures.isEmpty()) return;
        exposureUploading = true;
        ArrayList<Map<String, Object>> batch = new ArrayList<>(pendingExposures);
        pendingExposures.clear();
        inflightExposures.clear();
        inflightExposures.addAll(batch);
        persistExposureQueue();
        DatingModel.getInstance().reportExposures(batch, (code, msg, data) -> {
            exposureUploading = false;
            inflightExposures.clear();
            if (code != HttpResponseCode.success) {
                // 失败批次放回队首，下一次切卡、暂停或重新进入页面时继续上报。
                ArrayList<Map<String, Object>> merged = new ArrayList<>(batch);
                merged.addAll(pendingExposures);
                pendingExposures.clear();
                int start = Math.max(0, merged.size() - 100);
                pendingExposures.addAll(merged.subList(start, merged.size()));
                persistExposureQueue();
                return;
            }
            persistExposureQueue();
            if (pendingExposures.size() >= 5) flushExposures();
        });
    }

    private DatingCardView topCardView() {
        int top = cardStackManager == null ? 0 : cardStackManager.getTopPosition();
        RecyclerView.ViewHolder holder = wkVBinding.deckView.findViewHolderForAdapterPosition(top);
        if (holder instanceof DatingCardStackAdapter.CardHolder) return ((DatingCardStackAdapter.CardHolder) holder).card;
        return null;
    }

    private void playSwipeEffect(Direction direction) {
        if (wkVBinding.swipeFxView == null) return;
        if (direction == Direction.Right) wkVBinding.swipeFxView.playLike();
        else if (direction == Direction.Left) wkVBinding.swipeFxView.playPass();
        else if (direction == Direction.Top) wkVBinding.swipeFxView.playFavorite();
    }

    private void updateScopeTabs() {
        styleScopeTab(wkVBinding.recommendTab, "global".equals(scope));
        styleScopeTab(wkVBinding.nearbyTab, "nearby".equals(scope));
    }

    private void styleScopeTab(TextView tab, boolean selected) {
        tab.animate().cancel();
        tab.setTextSize(selected ? 22 : 17);
        tab.setTextColor(selected ? Color.WHITE : 0x88FFFFFF);
        tab.setAlpha(selected ? 1f : 0.82f);
        tab.setScaleX(selected ? 1f : 0.96f);
        tab.setScaleY(selected ? 1f : 0.96f);
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
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (locationHelper != null) locationHelper.handlePermissionResult(requestCode, grantResults);
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
        persistExposureQueue();
        if (actionController != null) actionController.release();
        if (locationHelper != null) locationHelper.release();
        super.onDestroy();
    }

    /** 借鉴 Shuffle 的本地 swipe history；服务端 undo 接口接入后可直接使用同一记录。 */
    private static final class SwipeRecord {
        final DatingProfile profile;
        final int position;
        final String action;
        final int photoIndex;

        SwipeRecord(DatingProfile profile, int position, String action, int photoIndex) {
            this.profile = profile;
            this.position = position;
            this.action = action;
            this.photoIndex = photoIndex;
        }
    }

}
