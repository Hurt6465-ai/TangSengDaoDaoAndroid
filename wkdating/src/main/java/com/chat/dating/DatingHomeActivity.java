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
    private static final int REQ_PROFILE_DETAIL = 401;
    private static final int REQ_MINE = 402;

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
    private int visiblePosition;
    private final ArrayDeque<SwipeRecord> swipeHistory = new ArrayDeque<>();
    private SwipeRecord pendingRewindRecord;

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
        initCardStack();
        updateScopeTabs();
        showLoading(true, "正在为你挑选合适的人…", false);
    }

    @Override
    protected void initListener() {
        wkVBinding.retryBtn.setOnClickListener(v -> reload());
        wkVBinding.rewindBtn.setOnClickListener(v -> rewindTop(v, true));
        wkVBinding.passBtn.setOnClickListener(v -> swipeTop(v, Direction.Left));
        wkVBinding.favoriteBtn.setOnClickListener(v -> swipeTop(v, Direction.Top));
        wkVBinding.likeBtn.setOnClickListener(v -> swipeTop(v, Direction.Right));
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
                DatingImagePreloader.preloadAround(DatingHomeActivity.this, profiles, position);
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
                if (!actionController.consume(action)) {
                    showToast(actionController.quotaMessage(action));
                    rewindTop(null, false);
                    return;
                }

                swipeHistory.addLast(new SwipeRecord(profile, swipedPosition, action, photoIndex));
                while (swipeHistory.size() > 30) swipeHistory.removeFirst();
                finishExposure(true);

                if (DatingSwipeAction.FAVORITE.equals(action)) {
                    DatingFavoriteStore.add(DatingHomeActivity.this, profile);
                }
                reportSwipe(profile, action, photoIndex);

                int top = cardStackManager.getTopPosition();
                if (!loading && !noMore && cardAdapter.getItemCount() - top <= 4) loadMore(false);
                if (top >= cardAdapter.getItemCount() && noMore) showEmpty();
            }

            @Override
            public void onCardRewound() {
                int top = cardStackManager.getTopPosition();
                visiblePosition = Math.max(0, top);
                SwipeRecord record = pendingRewindRecord;
                pendingRewindRecord = null;
                if (record != null && DatingSwipeAction.FAVORITE.equals(record.action) && record.profile != null) {
                    DatingFavoriteStore.remove(DatingHomeActivity.this, record.profile.safeUid());
                }
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
        cardStackManager.setTranslationInterval(11f);
        cardStackManager.setScaleInterval(0.95f);
        cardStackManager.setSwipeThreshold(0.30f);
        cardStackManager.setMaxDegree(17f);
        cardStackManager.setDirections(Arrays.asList(Direction.Left, Direction.Right, Direction.Top));
        cardStackManager.setCanScrollHorizontal(true);
        cardStackManager.setCanScrollVertical(true);
        cardStackManager.setSwipeableMethod(SwipeableMethod.AutomaticAndManual);
        cardStackManager.setOverlayInterpolator(new LinearInterpolator());

        wkVBinding.deckView.setLayoutManager(cardStackManager);
        wkVBinding.deckView.setAdapter(cardAdapter);
        wkVBinding.deckView.setItemAnimator(null);
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
                DatingModel.getInstance().updateLocation(location.getLatitude(), location.getLongitude(), "", "",
                        (code, msg, data) -> {
                            if (code != HttpResponseCode.success) {
                                showToast(TextUtils.isEmpty(msg) ? "位置更新失败，请稍后重试" : msg);
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
        DatingModel.getInstance().getMyDatingProfile((code, msg, data) -> {
            if (data != null) myProfile = data;
            if (myProfile == null && BuildConfig.DEBUG) myProfile = DatingMockData.demoMyProfile();
            actionController.setMyProfile(myProfile);
            loadMore(true);
        });
    }

    private void reload() {
        finishExposure(true);
        cursor = "";
        noMore = false;
        loading = false;
        profiles.clear();
        loadedUids.clear();
        pendingExposures.clear();
        visiblePosition = 0;
        swipeHistory.clear();
        pendingRewindRecord = null;
        cardAdapter.submitProfiles(profiles);
        cardStackManager.setTopPosition(0);
        wkVBinding.deckView.scrollToPosition(0);
        showLoading(true, "正在为你挑选合适的人…", false);
        loadMore(true);
    }

    private void loadMore(boolean firstPage) {
        if (loading) return;
        loading = true;
        if (firstPage) showLoading(true, "正在为你挑选合适的人…", false);
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
                showLoading(true, TextUtils.isEmpty(msg) ? "暂时没有新的推荐" : msg, true);
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
        if (demo) showToast("当前为开发演示图片，正式版接口失败不会显示假用户");
        DatingImagePreloader.preloadAround(this, profiles, cardStackManager.getTopPosition());
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
        if (cardStackManager.getTopPosition() <= 0) return;
        if (consumeQuota && swipeHistory.isEmpty()) {
            showToast("没有可以撤回的操作");
            return;
        }
        if (consumeQuota && !actionController.consumeRewind()) {
            showToast("今日免费撤回已用完，每天免费 3 次");
            return;
        }
        pendingRewindRecord = consumeQuota ? swipeHistory.pollLast() : null;
        actionController.animateButton(source);
        RewindAnimationSetting setting = new RewindAnimationSetting.Builder()
                .setDirection(Direction.Bottom)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .build();
        cardStackManager.setRewindAnimationSetting(setting);
        wkVBinding.deckView.rewind();
    }

    private String actionForDirection(Direction direction) {
        if (direction == Direction.Right) return DatingSwipeAction.LIKE;
        if (direction == Direction.Top) return DatingSwipeAction.FAVORITE;
        return DatingSwipeAction.PASS;
    }

    private void reportSwipe(DatingProfile profile, String action, int photoIndex) {
        if (profile == null) return;
        DatingModel.getInstance().swipe(profile.safeUid(), action, photoIndex, sessionId, (code, msg, result) -> {
            if (code != HttpResponseCode.success) {
                if (!TextUtils.isEmpty(msg)) showToast(msg);
                return;
            }
            if (result != null && result.isMatched()) {
                DatingMatchDialog.show(DatingHomeActivity.this, myProfile, profile,
                        () -> DatingUi.openChat(DatingHomeActivity.this, profile.safeUid()));
            }
        });
    }

    private void openProfileDetail(DatingProfile profile, int photoIndex) {
        if (profile == null) return;
        Map<String, Object> event = new HashMap<>();
        event.put("to_uid", profile.safeUid());
        event.put("event_type", "profile_open");
        event.put("source", "wkdating");
        event.put("duration_ms", 0);
        event.put("photo_index", photoIndex);
        pendingExposures.add(event);
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
            loadMyProfileThenRecommend();
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
            pendingExposures.add(item);
        }
        exposureUid = "";
        exposureStartMs = 0L;
        if (forceFlush || pendingExposures.size() >= 5) flushExposures();
    }

    private void flushExposures() {
        if (pendingExposures.isEmpty()) return;
        ArrayList<Map<String, Object>> copy = new ArrayList<>(pendingExposures);
        pendingExposures.clear();
        DatingModel.getInstance().reportExposures(copy);
    }

    private DatingCardView topCardView() {
        int top = cardStackManager == null ? 0 : cardStackManager.getTopPosition();
        RecyclerView.ViewHolder holder = wkVBinding.deckView.findViewHolderForAdapterPosition(top);
        if (holder instanceof DatingCardStackAdapter.CardHolder) return ((DatingCardStackAdapter.CardHolder) holder).card;
        return null;
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
        String text = String.format(Locale.getDefault(), "%s\n%s", "暂时没有新的推荐", "可以放宽筛选，或稍后再来看看。");
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
