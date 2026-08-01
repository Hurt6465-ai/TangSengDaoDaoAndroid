package com.chat.partnerlist;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.net.IRequestResultListener;
import com.chat.partnerlist.databinding.ActivityPartnerListBinding;
import com.chat.partnerlist.model.PartnerGreetingResponse;
import com.chat.partnerlist.model.PartnerListResponse;
import com.chat.partnerlist.model.PartnerListUser;
import com.chat.partnerlist.model.PartnerOnlineBatchResponse;
import com.chat.partnerlist.model.PartnerOnlineState;
import com.chat.uikit.GlobalBottomNavigationController;
import com.chat.uikit.partner.PartnerPendingStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PartnerListActivity extends WKBaseActivity<ActivityPartnerListBinding> implements PartnerListAdapter.Listener {
    private static final long ONLINE_REFRESH_INTERVAL_MS = 4L * 60L * 1000L;
    // Scrolling to idle must not turn into an online-status API request on every finger release.
    private static final long ONLINE_SCROLL_REFRESH_MIN_INTERVAL_MS = 30_000L;
    private static final long RECOMMENDATION_RETRY_DELAY_MS = 60_000L;
    private static final long CLOCK_TICK_MS = 60_000L;
    private static final int REQ_PROFILE_EDIT = 7301;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PartnerListAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PartnerListResponse currentResponse;
    private boolean requesting;
    private boolean onlineRequesting;
    private int onlineRequestSequence;
    private int activeOnlineRequest;
    private int recommendationRequestSequence;
    private int activeRecommendationRequest;
    private boolean recommendationRefreshPending;
    private boolean recommendationPendingExplicitRetry;
    private boolean hasRenderedData;
    private boolean resumed;
    private boolean refreshAfterProfileEdit;
    private long serverTimeBase;
    private long elapsedTimeBase;
    private boolean lastErrorProfileRequired;
    private long lastOnlineRefreshElapsed;
    private final Set<String> greetingRequests = new HashSet<>();
    private Runnable bannerHideRunnable;
    private String sessionUid = "";
    private final PartnerPendingStore.Listener pendingStoreListener = peerUid -> postToMain(() -> {
        if (!isCurrentAccount() || adapter == null || TextUtils.isEmpty(peerUid)) return;
        adapter.refreshGreeting(peerUid);
    });

    private final Runnable onlineRunnable = new Runnable() {
        @Override public void run() {
            if (!isCurrentAccount() || !resumed || isFinishing() || isDestroyed()) return;
            boolean started = refreshVisibleOnline();
            if (resumed) {
                handler.postDelayed(this, started
                        ? ONLINE_REFRESH_INTERVAL_MS
                        : ONLINE_SCROLL_REFRESH_MIN_INTERVAL_MS);
            }
        }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            if (!isCurrentAccount() || !resumed || isFinishing() || isDestroyed()) return;
            if (currentResponse != null) updateHeader(currentResponse);
            refreshVisibleTimeLabels();
            handler.postDelayed(this, CLOCK_TICK_MS);
        }
    };

    private final Runnable rotationRunnable = new Runnable() {
        @Override public void run() {
            if (!isCurrentAccount() || !resumed || isFinishing() || isDestroyed()
                    || currentResponse == null || currentResponse.rotation_done) {
                return;
            }
            if (requesting) {
                handler.postDelayed(this, 5_000L);
                return;
            }
            requestRecommendations(false);
        }
    };

    private final Runnable dayBoundaryRunnable = () -> {
        if (!isCurrentAccount() || !resumed) return;
        invalidateRecommendationRequest();
        invalidateOnlineRequest();
        PartnerListCache.clearCurrentAccount(this);
        currentResponse = null;
        hasRenderedData = false;
        showSkeleton(true);
        requestRecommendations(false);
    };

    @Override protected ActivityPartnerListBinding getViewBinding() {
        return ActivityPartnerListBinding.inflate(getLayoutInflater());
    }

    @Override public boolean supportSlideBack() { return false; }
    @Override protected void setTitle(TextView titleTv) {}

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        Window window = getWindow();
        // 与交友首页统一为 edge-to-edge；真实状态栏和刘海高度交给 WindowInsets 处理。
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
        boolean lightBars = (getResources().getConfiguration().uiMode & 0x30) != 0x20;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (lightBars && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (lightBars && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
        sessionUid = currentAccountUid();
        super.onCreate(savedInstanceState);
        // WKBaseActivity 会在 super.onCreate() 内再次设置状态栏模式，这里恢复本页的完整布局标志。
        window.getDecorView().setSystemUiVisibility(flags);
    }

    @Override protected void initView() {
        adapter = new PartnerListAdapter(this);
        PartnerPendingStore.addListener(pendingStoreListener);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setRecycleChildrenOnDetach(true);
        layoutManager.setInitialPrefetchItemCount(4);
        wkVBinding.recyclerView.setLayoutManager(layoutManager);
        wkVBinding.recyclerView.setAdapter(adapter);
        // 卡片高度由简介和标签决定，不能 setHasFixedSize(true)。关闭全部 ItemAnimator，
        // 避免 DiffUtil/payload 更新与 AppBar 滚动同时触发阴影和布局动画。
        wkVBinding.recyclerView.setHasFixedSize(false);
        wkVBinding.recyclerView.setItemAnimator(null);
        wkVBinding.recyclerView.setItemViewCacheSize(10);
        RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
        pool.setMaxRecycledViews(0, 14);
        wkVBinding.recyclerView.setRecycledViewPool(pool);
        wkVBinding.recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wkVBinding.recyclerView.setClipToPadding(false);
        GlobalBottomNavigationController.attach(this, wkVBinding.bottomNavigation, com.chat.uikit.R.id.i_partner);
        applySystemBarInsets();
        applyTabletContentWidth();

        showSkeleton(true);
        PartnerListCache.loadAsync(this, cached -> postToMain(() -> {
            if (!isCurrentAccount() || isFinishing() || isDestroyed() || currentResponse != null || cached == null) return;
            String today = PartnerListTime.currentDayKey();
            if (!TextUtils.isEmpty(cached.day_key) && !TextUtils.equals(cached.day_key, today)) {
                PartnerListCache.clearCurrentAccount(this);
                return;
            }
            render(cached, true);
        }));
    }

    @Override protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> finish());
        wkVBinding.partnerModeTab.setOnClickListener(v -> {
            wkVBinding.appBar.setExpanded(true, true);
            if (adapter != null && adapter.getItemCount() > 0) {
                wkVBinding.recyclerView.smoothScrollToPosition(0);
            }
        });
        wkVBinding.datingModeTab.setOnClickListener(v -> openDatingHome());
        wkVBinding.retryBtn.setOnClickListener(v -> {
            if (lastErrorProfileRequired) {
                refreshAfterProfileEdit = true;
                PartnerListHostBridge.openProfileEdit(this, REQ_PROFILE_EDIT);
            } else {
                requestRecommendations(true);
            }
        });
        wkVBinding.completeProfileBtn.setOnClickListener(v -> {
            refreshAfterProfileEdit = true;
            PartnerListHostBridge.openProfileEdit(this, REQ_PROFILE_EDIT);
        });
        wkVBinding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                // 快速滑动时不启动在线批量请求，减少主线程回调与图片绑定争用。
                handler.removeCallbacks(onlineRunnable);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scheduleOnlineRefresh(700L);
                }
            }
        });
    }

    @Override protected void initData() {
        requestRecommendations(false);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (!isCurrentAccount()) {
            finish();
            return;
        }
        if (adapter != null) adapter.refreshAllGreetings();
        if (currentResponse != null && !TextUtils.equals(currentResponse.day_key, PartnerListTime.dayKey(nowServer()))) {
            invalidateRecommendationRequest();
            invalidateOnlineRequest();
            PartnerListCache.clearCurrentAccount(this);
            currentResponse = null;
            hasRenderedData = false;
            showSkeleton(true);
            requestRecommendations(false);
        } else if (refreshAfterProfileEdit) {
            refreshAfterProfileEdit = false;
            invalidateRecommendationRequest();
            requestRecommendations(false);
        }
        scheduleAll();
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(onlineRunnable);
        handler.removeCallbacks(clockRunnable);
        handler.removeCallbacks(rotationRunnable);
        handler.removeCallbacks(dayBoundaryRunnable);
        // 不主动把仍在网络中的在线请求标记为空闲，避免快速暂停/恢复时并发两次请求。
        super.onPause();
    }

    @Override protected void onDestroy() {
        resumed = false;
        abandonRecommendationRequest();
        invalidateOnlineRequest();
        greetingRequests.clear();
        PartnerPendingStore.removeListener(pendingStoreListener);
        if (bannerHideRunnable != null) handler.removeCallbacks(bannerHideRunnable);
        handler.removeCallbacksAndMessages(null);
        if (wkVBinding != null) {
            wkVBinding.updateBanner.animate().cancel();
            wkVBinding.recyclerView.setAdapter(null);
        }
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROFILE_EDIT) {
            // 正常 startActivityForResult 路径只在真正保存成功时刷新；用户按返回键不浪费一次推荐请求。
            // 反射兜底页面没有 result 回调，仍由 onResume 中的 refreshAfterProfileEdit 处理。
            refreshAfterProfileEdit = false;
            if (resultCode == RESULT_OK) {
                invalidateRecommendationRequest();
                requestRecommendations(false);
            }
        }
    }

    private void requestRecommendations(boolean explicitRetry) {
        if (!isCurrentAccount() || isFinishing() || isDestroyed() || wkVBinding == null) return;
        if (requesting) {
            // PartnerListModel does not expose a cancellable Disposable. Keep the real HTTP request
            // as the single in-flight request and run only the newest refresh after its callback.
            recommendationRefreshPending = true;
            recommendationPendingExplicitRetry |= explicitRetry;
            return;
        }

        boolean requestExplicitRetry = explicitRetry || recommendationPendingExplicitRetry;
        recommendationRefreshPending = false;
        recommendationPendingExplicitRetry = false;
        final int requestId = ++recommendationRequestSequence;
        final String requestUid = sessionUid;
        activeRecommendationRequest = requestId;
        requesting = true;
        if (!hasRenderedData && currentResponse == null) showSkeleton(true);
        wkVBinding.retryBtn.setEnabled(false);
        try {
            PartnerListModel.getInstance().recommendations(new IRequestResultListener<>() {
                @Override public void onSuccess(PartnerListResponse result) {
                    postToMain(() -> handleRecommendationSuccess(requestId, requestUid, requestExplicitRetry, result));
                }

                @Override public void onFail(int code, String msg) {
                    postToMain(() -> handleRecommendationFailure(
                            requestId, requestUid, requestExplicitRetry, code, msg));
                }
            });
        } catch (Throwable throwable) {
            postToMain(() -> handleRecommendationFailure(
                    requestId, requestUid, requestExplicitRetry, -1, throwable.getMessage()));
        }
    }

    private void handleRecommendationSuccess(int requestId, String requestUid,
                                             boolean explicitRetry, PartnerListResponse result) {
        if (!finishRecommendationRequest(requestId)) return;
        if (!isRequestAccount(requestUid)) return;
        if (requestId != recommendationRequestSequence || recommendationRefreshPending) {
            runPendingRecommendationRefresh();
            return;
        }
        if (isFinishing() || isDestroyed() || wkVBinding == null) return;
        wkVBinding.retryBtn.setEnabled(true);
        lastErrorProfileRequired = false;
        wkVBinding.retryBtn.setText(R.string.partnerlist_retry);
        if (result == null) {
            handleRecommendationEmptyResult(explicitRetry);
            return;
        }
        render(result, false);
        PartnerListCache.saveAsync(PartnerListActivity.this, result);
        if (result.updated_count > 0) {
            showUpdateBanner(getResources().getQuantityString(R.plurals.partnerlist_updated_count,
                    result.updated_count, result.updated_count));
        }
    }

    private void handleRecommendationFailure(int requestId, String requestUid,
                                             boolean explicitRetry, int code, String msg) {
        if (!finishRecommendationRequest(requestId)) return;
        if (!isRequestAccount(requestUid)) return;
        if (requestId != recommendationRequestSequence || recommendationRefreshPending) {
            runPendingRecommendationRefresh();
            return;
        }
        if (isFinishing() || isDestroyed() || wkVBinding == null) return;
        wkVBinding.retryBtn.setEnabled(true);
        String message = TextUtils.isEmpty(msg) ? getString(R.string.partnerlist_load_failed) : msg;
        if (currentResponse == null && !hasRenderedData) {
            boolean profileRequired = isProfileRequiredError(code, msg);
            showError(message, profileRequired);
        } else {
            if (explicitRetry) toast(message);
            scheduleRecommendationRetry();
        }
    }

    private boolean finishRecommendationRequest(int requestId) {
        if (requestId != activeRecommendationRequest) return false;
        activeRecommendationRequest = 0;
        requesting = false;
        return true;
    }

    private void runPendingRecommendationRefresh() {
        if (!recommendationRefreshPending) return;
        boolean explicitRetry = recommendationPendingExplicitRetry;
        recommendationRefreshPending = false;
        recommendationPendingExplicitRetry = false;
        if (isFinishing() || isDestroyed() || wkVBinding == null) return;
        handler.post(() -> requestRecommendations(explicitRetry));
    }

    private void handleRecommendationEmptyResult(boolean explicitRetry) {
        if (currentResponse == null && !hasRenderedData) {
            showError(getString(R.string.partnerlist_load_failed), false);
        } else {
            if (explicitRetry) toast(getString(R.string.partnerlist_load_failed));
            scheduleRecommendationRetry();
        }
    }

    private void scheduleRecommendationRetry() {
        handler.removeCallbacks(rotationRunnable);
        if (resumed && currentResponse != null && !currentResponse.rotation_done) {
            handler.postDelayed(rotationRunnable, RECOMMENDATION_RETRY_DELAY_MS);
        }
    }

    private void render(PartnerListResponse response, boolean fromCache) {
        invalidateOnlineRequest();
        currentResponse = response;
        updateServerClock(response.server_time);
        List<PartnerListUser> users = sanitizeUsers(response.usersSafe());
        response.users = new ArrayList<>(users);
        hasRenderedData = !users.isEmpty();
        showSkeleton(false);
        wkVBinding.errorLayout.setVisibility(View.GONE);
        wkVBinding.recyclerView.setVisibility(hasRenderedData ? View.VISIBLE : View.GONE);
        wkVBinding.emptyLayout.setVisibility(hasRenderedData ? View.GONE : View.VISIBLE);
        configureEmptyState(response);

        String anchorUid = null;
        int anchorOffset = 0;
        int first = layoutManager == null ? RecyclerView.NO_POSITION : layoutManager.findFirstVisibleItemPosition();
        if (first != RecyclerView.NO_POSITION && first < adapter.getItemCount()) {
            PartnerListUser anchor = adapter.getCurrentList().get(first);
            anchorUid = anchor == null ? null : anchor.stableId();
            View view = layoutManager.findViewByPosition(first);
            if (view != null) anchorOffset = view.getTop();
        }
        final String restoreUid = anchorUid;
        final int restoreOffset = anchorOffset;

        adapter.setServerTime(nowServer());
        adapter.setGreetingRemaining(response.greeting_remaining);
        adapter.setRecentlyAdded(response.added_user_ids);
        adapter.submitList(users, () -> restoreScrollAnchor(restoreUid, restoreOffset));
        updateHeader(response);
        scheduleAll();
        if (!fromCache) scheduleOnlineRefresh(500L);
    }

    private List<PartnerListUser> sanitizeUsers(List<PartnerListUser> source) {
        ArrayList<PartnerListUser> result = new ArrayList<>();
        if (source == null || source.isEmpty()) return result;
        Set<String> seen = new LinkedHashSet<>();
        for (PartnerListUser user : source) {
            if (user == null) continue;
            String uid = user.stableId();
            if (TextUtils.isEmpty(uid) || !seen.add(uid)) continue;
            result.add(user);
        }
        return result;
    }

    private void restoreScrollAnchor(String uid, int offset) {
        if (TextUtils.isEmpty(uid) || layoutManager == null) return;
        List<PartnerListUser> list = adapter.getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            PartnerListUser user = list.get(i);
            if (user != null && TextUtils.equals(uid, user.stableId())) {
                layoutManager.scrollToPositionWithOffset(i, offset);
                return;
            }
        }
    }

    private void configureEmptyState(PartnerListResponse response) {
        boolean dailyFinished = response != null && (response.rotation_done
                || response.unique_assigned_count >= Math.max(1, response.daily_candidate_limit));
        wkVBinding.completeProfileBtn.setVisibility(dailyFinished ? View.GONE : View.VISIBLE);
    }

    private void updateHeader(PartnerListResponse response) {
        int total = response.usersSafe().size();
        wkVBinding.subtitleTv.setText(getResources().getQuantityString(R.plurals.partnerlist_found_count, total, total));
        wkVBinding.quotaTv.setText(getString(R.string.partnerlist_quota_value,
                Math.max(0, response.greeting_remaining), Math.max(1, response.greeting_limit)));
        if (response.rotation_done) {
            wkVBinding.statusHint.setText(getString(R.string.partnerlist_status_finished,
                    response.unique_assigned_count, response.daily_candidate_limit));
            return;
        }
        long dueAt = PartnerListTime.nextDueAt(response.rotate_at, response.rotation_retry_at);
        if (dueAt <= 0) {
            wkVBinding.statusHint.setText(R.string.partnerlist_status_preparing);
            return;
        }
        long minutes = Math.max(0L, (dueAt - nowServer() + 59_999L) / 60_000L);
        if (minutes <= 0) wkVBinding.statusHint.setText(R.string.partnerlist_status_updating);
        else if (minutes < 60) wkVBinding.statusHint.setText(getString(R.string.partnerlist_status_minutes, minutes));
        else wkVBinding.statusHint.setText(getString(R.string.partnerlist_status_hours, Math.max(1L, minutes / 60L)));
    }

    private void showSkeleton(boolean show) {
        if (show && !hasRenderedData) {
            wkVBinding.skeletonLayout.setVisibility(View.VISIBLE);
            wkVBinding.skeletonLayout.startPulse();
            wkVBinding.recyclerView.setVisibility(View.GONE);
            wkVBinding.emptyLayout.setVisibility(View.GONE);
            wkVBinding.errorLayout.setVisibility(View.GONE);
        } else {
            wkVBinding.skeletonLayout.stopPulse();
            wkVBinding.skeletonLayout.setVisibility(View.GONE);
        }
    }

    private void showError(String message, boolean profileRequired) {
        lastErrorProfileRequired = profileRequired;
        showSkeleton(false);
        wkVBinding.recyclerView.setVisibility(View.GONE);
        wkVBinding.emptyLayout.setVisibility(View.GONE);
        wkVBinding.errorLayout.setVisibility(View.VISIBLE);
        wkVBinding.errorTv.setText(message);
        wkVBinding.retryBtn.setText(profileRequired ? R.string.partnerlist_complete_profile : R.string.partnerlist_retry);
    }

    private boolean isProfileRequiredError(int code, String msg) {
        if (code == 412 || code == 428) return true;
        if (TextUtils.isEmpty(msg)) return false;
        String text = msg.toLowerCase();
        return text.contains("profile") || text.contains("language")
                || text.contains("资料") || text.contains("完善") || text.contains("语言")
                || text.contains("ဘာသာ") || text.contains("ကိုယ်ရေး");
    }

    private void showUpdateBanner(String text) {
        if (wkVBinding == null || TextUtils.isEmpty(text) || isFinishing() || isDestroyed()) return;
        if (bannerHideRunnable != null) handler.removeCallbacks(bannerHideRunnable);
        wkVBinding.updateBanner.animate().cancel();
        wkVBinding.updateBanner.setText(text);
        wkVBinding.updateBanner.setAlpha(0f);
        wkVBinding.updateBanner.setTranslationY(-dp(8));
        wkVBinding.updateBanner.setVisibility(View.VISIBLE);

        final Runnable hideTask = new Runnable() {
            @Override public void run() {
                if (bannerHideRunnable != this || wkVBinding == null
                        || isFinishing() || isDestroyed()) return;
                wkVBinding.updateBanner.animate().cancel();
                wkVBinding.updateBanner.animate().alpha(0f).translationY(-dp(8)).setDuration(180L)
                        .withEndAction(() -> {
                            if (bannerHideRunnable != this) return;
                            if (wkVBinding != null) wkVBinding.updateBanner.setVisibility(View.GONE);
                            bannerHideRunnable = null;
                        }).start();
            }
        };
        bannerHideRunnable = hideTask;
        wkVBinding.updateBanner.animate().alpha(1f).translationY(0f).setDuration(180L)
                .withEndAction(() -> {
                    if (bannerHideRunnable == hideTask) handler.postDelayed(hideTask, 2200L);
                }).start();
    }

    private void scheduleAll() {
        if (!resumed) return;
        handler.removeCallbacks(onlineRunnable);
        handler.removeCallbacks(clockRunnable);
        handler.removeCallbacks(dayBoundaryRunnable);
        if (hasRenderedData) scheduleOnlineRefresh(700L);
        handler.postDelayed(clockRunnable, CLOCK_TICK_MS);
        scheduleDayBoundary();
        if (currentResponse != null) scheduleRotation(currentResponse);
    }


    private void scheduleDayBoundary() {
        handler.removeCallbacks(dayBoundaryRunnable);
        if (!resumed || isFinishing() || isDestroyed()) return;
        long serverNow = nowServer();
        long dayDelay = Math.max(1_000L,
                PartnerListTime.nextDayBoundaryMillis(serverNow) - serverNow);
        handler.postDelayed(dayBoundaryRunnable, dayDelay);
    }

    private void scheduleRotation(PartnerListResponse response) {
        handler.removeCallbacks(rotationRunnable);
        if (response == null || response.rotation_done) return;
        long dueAt = PartnerListTime.nextDueAt(response.rotate_at, response.rotation_retry_at);
        if (dueAt <= 0) {
            // 服务端尚未给出明确轮换时间时不能永久停在“准备中”。
            handler.postDelayed(rotationRunnable, RECOMMENDATION_RETRY_DELAY_MS);
            return;
        }
        long delay = dueAt - nowServer();
        if (delay <= 0) delay = 1_000L;
        handler.postDelayed(rotationRunnable, Math.min(delay, 6L * 60L * 60L * 1000L));
    }

    private boolean refreshVisibleOnline() {
        if (!isCurrentAccount() || !resumed || !hasRenderedData || layoutManager == null || adapter == null
                || requesting || onlineRequesting || isFinishing() || isDestroyed()) return false;
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) first = 0;
        if (last == RecyclerView.NO_POSITION) last = Math.min(adapter.getItemCount() - 1, 10);
        int end = Math.min(adapter.getItemCount(), last + 11);
        Set<String> ids = new LinkedHashSet<>();
        List<PartnerListUser> current = adapter.getCurrentList();
        for (int i = Math.max(0, first); i < end && i < current.size(); i++) {
            PartnerListUser user = current.get(i);
            if (user != null && !TextUtils.isEmpty(user.stableId())) ids.add(user.stableId());
        }
        if (ids.isEmpty()) return false;

        onlineRequesting = true;
        lastOnlineRefreshElapsed = SystemClock.elapsedRealtime();
        final int requestId = ++onlineRequestSequence;
        final String requestUid = sessionUid;
        activeOnlineRequest = requestId;
        try {
            PartnerListModel.getInstance().onlineBatch(new ArrayList<>(ids), new IRequestResultListener<>() {
                @Override public void onSuccess(PartnerOnlineBatchResponse result) {
                    postToMain(() -> handleOnlineSuccess(requestId, requestUid, result));
                }

                @Override public void onFail(int code, String msg) {
                    postToMain(() -> handleOnlineFailure(requestId, requestUid));
                }
            });
        } catch (Throwable ignored) {
            if (requestId == activeOnlineRequest) {
                activeOnlineRequest = 0;
                onlineRequesting = false;
            }
            return false;
        }
        return true;
    }

    private void handleOnlineSuccess(int requestId, String requestUid, PartnerOnlineBatchResponse result) {
        if (!finishOnlineRequest(requestId)) return;
        if (!isRequestAccount(requestUid)) return;
        // 名单在请求期间已经变化：丢弃旧结果，并在旧请求真实结束后为新名单补发。
        if (requestId != onlineRequestSequence) {
            if (resumed && hasRenderedData) scheduleOnlineRefresh(500L);
            return;
        }
        if (result == null) {
            if (resumed) scheduleOnlineRefresh(ONLINE_SCROLL_REFRESH_MIN_INTERVAL_MS);
            return;
        }
        if (!resumed || isFinishing() || isDestroyed() || adapter == null) return;
        updateServerClock(result.server_time);
        if (currentResponse != null) {
            currentResponse.server_time = nowServer();
            updateHeader(currentResponse);
            scheduleRotation(currentResponse);
            scheduleDayBoundary();
        }
        Map<String, PartnerOnlineState> map = new HashMap<>();
        for (PartnerOnlineState state : result.usersSafe()) {
            if (state != null && !TextUtils.isEmpty(state.uid)) map.put(state.uid, state);
        }

        ArrayList<PartnerListUser> updated = new ArrayList<>(adapter.getItemCount());
        boolean changed = false;
        for (PartnerListUser original : adapter.getCurrentList()) {
            PartnerListUser user = original == null ? null : original.copy();
            if (user != null) {
                PartnerOnlineState state = map.get(user.stableId());
                if (state != null) {
                    changed |= user.online != state.online || user.last_active_at != state.last_active_at;
                    user.online = state.online;
                    user.last_active_at = state.last_active_at;
                }
            }
            if (user != null) updated.add(user);
        }

        adapter.setServerTime(nowServer());
        if (changed) {
            if (currentResponse != null) {
                currentResponse.users = new ArrayList<>(updated);
                currentResponse.server_time = nowServer();
                PartnerListCache.saveAsync(PartnerListActivity.this, currentResponse);
            }
            adapter.submitList(new ArrayList<>(updated), this::refreshVisibleTimeLabels);
        } else {
            refreshVisibleTimeLabels();
        }
    }


    private void handleOnlineFailure(int requestId, String requestUid) {
        if (!finishOnlineRequest(requestId)) return;
        if (!isRequestAccount(requestUid)) return;
        if (requestId != onlineRequestSequence) {
            if (resumed && hasRenderedData) scheduleOnlineRefresh(500L);
            return;
        }
        if (resumed) scheduleOnlineRefresh(ONLINE_SCROLL_REFRESH_MIN_INTERVAL_MS);
    }

    /** Only the real callback of the active HTTP request may release the single-request lock. */
    private boolean finishOnlineRequest(int requestId) {
        if (requestId != activeOnlineRequest) return false;
        activeOnlineRequest = 0;
        onlineRequesting = false;
        return true;
    }

    private void scheduleOnlineRefresh(long preferredDelayMs) {
        if (!resumed || isFinishing() || isDestroyed()) return;
        handler.removeCallbacks(onlineRunnable);
        long throttleDelay = 0L;
        if (lastOnlineRefreshElapsed > 0L) {
            long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - lastOnlineRefreshElapsed);
            throttleDelay = Math.max(0L, ONLINE_SCROLL_REFRESH_MIN_INTERVAL_MS - elapsed);
        }
        handler.postDelayed(onlineRunnable, Math.max(Math.max(0L, preferredDelayMs), throttleDelay));
    }

    private void invalidateOnlineRequest() {
        // 只让旧结果失效，不能假装取消仍在执行的 Retrofit 请求。
        // onlineRequesting 由该请求的真实 success/fail 回调释放，避免新旧请求重叠。
        onlineRequestSequence++;
        lastOnlineRefreshElapsed = 0L;
        handler.removeCallbacks(onlineRunnable);
    }

    private void invalidateRecommendationRequest() {
        recommendationRequestSequence++;
        handler.removeCallbacks(rotationRunnable);
        if (requesting) {
            recommendationRefreshPending = true;
        } else {
            activeRecommendationRequest = 0;
        }
    }

    private void abandonRecommendationRequest() {
        recommendationRequestSequence++;
        activeRecommendationRequest = 0;
        requesting = false;
        recommendationRefreshPending = false;
        recommendationPendingExplicitRetry = false;
        handler.removeCallbacks(rotationRunnable);
    }

    private void refreshVisibleTimeLabels() {
        if (layoutManager == null || adapter == null) return;
        adapter.setServerTime(nowServer());
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) adapter.refreshVisible(first, last);
    }

    private void updateServerClock(long serverTime) {
        serverTimeBase = serverTime > 0 ? PartnerListTime.normalizeMillis(serverTime) : System.currentTimeMillis();
        elapsedTimeBase = SystemClock.elapsedRealtime();
    }

    private long nowServer() {
        if (serverTimeBase <= 0) return System.currentTimeMillis();
        return serverTimeBase + Math.max(0L, SystemClock.elapsedRealtime() - elapsedTimeBase);
    }

    @Override public void onOpenProfile(PartnerListUser user) {
        if (!isCurrentAccount() || user == null || TextUtils.isEmpty(user.stableId())) return;
        PartnerListHostBridge.openProfile(this, user.stableId(), user.vercode);
    }

    @Override public void onOpenChat(PartnerListUser user) {
        if (!isCurrentAccount() || user == null || TextUtils.isEmpty(user.stableId())) return;
        PartnerListHostBridge.openChat(this, user.stableId());
    }

    @Override public void onGreeting(PartnerListUser user, int position) {
        if (!isCurrentAccount() || user == null || currentResponse == null || adapter == null) return;
        String uid = user.stableId();
        if (TextUtils.isEmpty(uid)) return;
        if (PartnerPendingStore.get(uid) != null) {
            PartnerListHostBridge.openChat(this, uid);
            return;
        }
        if (currentResponse.greeting_remaining - greetingRequests.size() <= 0) {
            showUpdateBanner(getString(R.string.partnerlist_daily_limit_message));
            return;
        }
        if (!greetingRequests.add(uid)) return;
        refreshGreetingAvailability();

        final String requestUid = sessionUid;
        final String greetingDayKey = currentResponse.day_key;
        final String greetingText = getString(R.string.partnerlist_default_greeting);
        adapter.markGreetingPending(uid, true);
        try {
            PartnerListModel.getInstance().sendGreeting(uid, greetingText, new IRequestResultListener<>() {
                @Override public void onSuccess(PartnerGreetingResponse result) {
                    postToMain(() -> handleGreetingSuccess(
                            requestUid, uid, greetingDayKey, greetingText, result));
                }

                @Override public void onFail(int code, String msg) {
                    postToMain(() -> handleGreetingFailure(requestUid, uid, msg));
                }
            });
        } catch (Throwable throwable) {
            postToMain(() -> handleGreetingFailure(requestUid, uid, throwable.getMessage()));
        }
    }

    private void handleGreetingSuccess(String requestUid, String uid, String greetingDayKey,
                                       String greetingText, PartnerGreetingResponse result) {
        greetingRequests.remove(uid);
        if (!isRequestAccount(requestUid)) return;
        boolean alive = !isFinishing() && !isDestroyed() && wkVBinding != null;
        if (alive && adapter != null) adapter.markGreetingPending(uid, false);
        refreshGreetingAvailability();

        if (result == null || !result.success()) {
            if (alive) {
                toast(result == null || TextUtils.isEmpty(result.messageSafe())
                        ? getString(R.string.partnerlist_greeting_failed) : result.messageSafe());
            }
            return;
        }

        // 只在本次请求确实投递了一条招呼时保存本地副本。服务端可能把内置默认
        // 文案随机化，因此必须使用响应 text，不能保存请求前的占位文案。
        String deliveredText = TextUtils.isEmpty(result.text) ? greetingText : result.text;
        if (!TextUtils.isEmpty(result.text) && !Boolean.FALSE.equals(result.requester)) {
            PartnerListHostBridge.saveOutgoingGreeting(uid, deliveredText, result);
        }

        int maxPending = result.max_greeting_count > 0 ? result.max_greeting_count : 3;
        int pendingCount = Math.max(1, result.requester_msg_count);
        if (result.contact_status == 1) {
            PartnerPendingStore.markActive(uid);
        } else if (isGreetingReceiver(result)) {
            // 对方先发起招呼时，本账号是接收方。错误标成 requester 会把回复也套进
            // “最多3条”的发起方限制，并可能造成关系状态长期不一致。
            PartnerPendingStore.markReceiver(uid, pendingCount, maxPending);
        } else {
            PartnerPendingStore.markRequester(uid, pendingCount, maxPending);
        }

        if (!alive) return;
        if (adapter != null) adapter.markGreeted(uid);
        if (currentResponse != null && TextUtils.equals(greetingDayKey, currentResponse.day_key)) {
            if (result.greeting_day_limit > 0) {
                // Multiple greetings may complete out of order. Quota usage must only move forward;
                // an older response must never increase the displayed remaining count again.
                int limit = Math.max(1, result.greeting_day_limit);
                int used = Math.max(Math.max(0, currentResponse.greeting_used),
                        Math.max(0, result.greeting_day_used));
                used = Math.min(limit, used);
                int remaining = Math.max(0, limit - used);
                if (result.greeting_day_remaining > 0 || used >= limit) {
                    remaining = Math.min(remaining, Math.max(0, result.greeting_day_remaining));
                }
                currentResponse.greeting_limit = limit;
                currentResponse.greeting_used = used;
                currentResponse.greeting_remaining = remaining;
            } else {
                currentResponse.greeting_used = Math.min(
                        currentResponse.greeting_limit, currentResponse.greeting_used + 1);
                currentResponse.greeting_remaining = Math.max(
                        0, currentResponse.greeting_limit - currentResponse.greeting_used);
            }
            refreshGreetingAvailability();
            updateHeader(currentResponse);
            PartnerListCache.saveAsync(PartnerListActivity.this, currentResponse);
        }
        showUpdateBanner(getString(R.string.partnerlist_greeting_success));
    }

    private boolean isGreetingReceiver(PartnerGreetingResponse result) {
        if (result == null) return false;
        if (Boolean.FALSE.equals(result.requester)) return true;
        if (result.requester != null) return false;
        // Compatibility with a server deployed before the explicit requester field.
        String message = result.messageSafe();
        return TextUtils.isEmpty(result.text)
                && !TextUtils.isEmpty(message)
                && message.contains("对方已打招呼");
    }

    private void handleGreetingFailure(String requestUid, String uid, String msg) {
        greetingRequests.remove(uid);
        if (!isRequestAccount(requestUid)) return;
        if (isFinishing() || isDestroyed() || wkVBinding == null) return;
        if (adapter != null) adapter.markGreetingPending(uid, false);
        refreshGreetingAvailability();
        toast(TextUtils.isEmpty(msg) ? getString(R.string.partnerlist_greeting_failed) : msg);
    }


    private void refreshGreetingAvailability() {
        if (adapter == null || currentResponse == null) return;
        int available = Math.max(0, currentResponse.greeting_remaining - greetingRequests.size());
        adapter.setGreetingRemaining(available);
    }

    private String currentAccountUid() {
        String uid = WKConfig.getInstance().getUid();
        return uid == null ? "" : uid;
    }

    private boolean isCurrentAccount() {
        return !TextUtils.isEmpty(sessionUid) && TextUtils.equals(sessionUid, currentAccountUid());
    }

    private boolean isRequestAccount(String requestUid) {
        return !TextUtils.isEmpty(requestUid)
                && TextUtils.equals(requestUid, sessionUid)
                && TextUtils.equals(requestUid, currentAccountUid());
    }

    private void postToMain(Runnable action) {
        if (action == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else handler.post(action);
    }

    private void openDatingHome() {
        try {
            Class<?> clazz = Class.forName("com.chat.dating.DatingHomeActivity");
            Intent intent = new Intent(this, clazz);
            intent.putExtra("from_partner_list", true);
            startActivity(intent);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Object handled = EndpointManager.getInstance().invoke("dating_open", this);
            if (handled instanceof Boolean && (Boolean) handled) return;
        } catch (Throwable ignored) {
        }
        toast(getString(R.string.partnerlist_dating_unavailable));
    }


    /**
     * 顶部标签与交友页使用同一套规则：56dp 内容高度 + 真实刘海/状态栏高度 + 4dp 呼吸距离。
     * 底部导航同时扩展到手势区，避免全面屏设备出现遮挡或额外黑条。
     */
    private void applySystemBarInsets() {
        if (wkVBinding == null || wkVBinding.pageRoot == null) return;
        final int topBaseHeight = layoutHeight(wkVBinding.topBar);
        final int topBasePaddingLeft = wkVBinding.topBar.getPaddingLeft();
        final int topBasePaddingTop = wkVBinding.topBar.getPaddingTop();
        final int topBasePaddingRight = wkVBinding.topBar.getPaddingRight();
        final int topBasePaddingBottom = wkVBinding.topBar.getPaddingBottom();
        final int bottomBaseHeight = layoutHeight(wkVBinding.bottomNavigation);
        final int bottomBasePaddingLeft = wkVBinding.bottomNavigation.getPaddingLeft();
        final int bottomBasePaddingTop = wkVBinding.bottomNavigation.getPaddingTop();
        final int bottomBasePaddingRight = wkVBinding.bottomNavigation.getPaddingRight();
        final int bottomBasePaddingBottom = wkVBinding.bottomNavigation.getPaddingBottom();
        final int contentBaseBottomMargin = marginBottom(wkVBinding.contentContainer);
        final int bannerBaseTopMargin = marginTop(wkVBinding.updateBanner);
        final int topExtra = dp(4);

        ViewCompat.setOnApplyWindowInsetsListener(wkVBinding.pageRoot, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());

            wkVBinding.topBar.setPadding(
                    topBasePaddingLeft + bars.left,
                    topBasePaddingTop + bars.top + topExtra,
                    topBasePaddingRight + bars.right,
                    topBasePaddingBottom);
            setLayoutHeight(wkVBinding.topBar, topBaseHeight + bars.top + topExtra);

            wkVBinding.bottomNavigation.setPadding(
                    bottomBasePaddingLeft + bars.left,
                    bottomBasePaddingTop,
                    bottomBasePaddingRight + bars.right,
                    bottomBasePaddingBottom + bars.bottom);
            setLayoutHeight(wkVBinding.bottomNavigation, bottomBaseHeight + bars.bottom);
            setBottomMargin(wkVBinding.contentContainer, contentBaseBottomMargin + bars.bottom);
            setTopMargin(wkVBinding.updateBanner, bannerBaseTopMargin + bars.top + topExtra);
            return insets;
        });
        ViewCompat.requestApplyInsets(wkVBinding.pageRoot);
    }

    private int layoutHeight(View view) {
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        return params == null || params.height < 0 ? 0 : params.height;
    }

    private int marginTop(View view) {
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        return params instanceof ViewGroup.MarginLayoutParams
                ? ((ViewGroup.MarginLayoutParams) params).topMargin : 0;
    }

    private int marginBottom(View view) {
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        return params instanceof ViewGroup.MarginLayoutParams
                ? ((ViewGroup.MarginLayoutParams) params).bottomMargin : 0;
    }

    private void setLayoutHeight(View view, int height) {
        if (view == null || height <= 0 || view.getLayoutParams() == null
                || view.getLayoutParams().height == height) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);
    }

    private void setTopMargin(View view, int margin) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.topMargin == margin) return;
        params.topMargin = margin;
        view.setLayoutParams(params);
    }

    private void setBottomMargin(View view, int margin) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.bottomMargin == margin) return;
        params.bottomMargin = margin;
        view.setLayoutParams(params);
    }

    private void applyTabletContentWidth() {
        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / Math.max(1f, density);
        if (widthDp < 800f || wkVBinding.contentContainer == null) return;
        if (!(wkVBinding.contentContainer.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) wkVBinding.contentContainer.getLayoutParams();
        params.width = dp(760);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        wkVBinding.contentContainer.setLayoutParams(params);
    }

    private void toast(String message) {
        if (!TextUtils.isEmpty(message)) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
