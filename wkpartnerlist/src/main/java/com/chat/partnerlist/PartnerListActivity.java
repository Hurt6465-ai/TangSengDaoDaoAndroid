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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PartnerListActivity extends WKBaseActivity<ActivityPartnerListBinding> implements PartnerListAdapter.Listener {
    private static final long ONLINE_REFRESH_INTERVAL_MS = 4L * 60L * 1000L;
    private static final long CLOCK_TICK_MS = 60_000L;
    private static final int REQ_PROFILE_EDIT = 7301;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PartnerListAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PartnerListResponse currentResponse;
    private boolean requesting;
    private boolean onlineRequesting;
    private int onlineRequestSequence;
    private boolean hasRenderedData;
    private boolean resumed;
    private boolean refreshAfterProfileEdit;
    private long serverTimeBase;
    private long elapsedTimeBase;
    private boolean lastErrorProfileRequired;

    private int topBarBaseHeight;
    private int topBarBasePaddingTop;
    private int updateBannerBaseMarginTop;

    private final Runnable onlineRunnable = new Runnable() {
        @Override public void run() {
            if (!resumed || isFinishing() || isDestroyed()) return;
            refreshVisibleOnline();
            handler.postDelayed(this, ONLINE_REFRESH_INTERVAL_MS);
        }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            if (!resumed || isFinishing() || isDestroyed()) return;
            if (currentResponse != null) updateHeader(currentResponse);
            refreshVisibleTimeLabels();
            handler.postDelayed(this, CLOCK_TICK_MS);
        }
    };

    private final Runnable rotationRunnable = () -> {
        if (resumed && !requesting && currentResponse != null && !currentResponse.rotation_done) {
            requestRecommendations(false);
        }
    };

    private final Runnable dayBoundaryRunnable = () -> {
        if (!resumed) return;
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
        configureEdgeToEdgeWindow();
        super.onCreate(savedInstanceState);
    }

    @Override protected void toggleStatusBarMode() {
        super.toggleStatusBarMode();
        configureEdgeToEdgeWindow();
    }

    @Override protected void initView() {
        adapter = new PartnerListAdapter(this);
        layoutManager = new LinearLayoutManager(this);
        wkVBinding.recyclerView.setLayoutManager(layoutManager);
        wkVBinding.recyclerView.setAdapter(adapter);
        wkVBinding.recyclerView.setHasFixedSize(false);
        wkVBinding.recyclerView.setItemViewCacheSize(8);
        wkVBinding.recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wkVBinding.recyclerView.setClipToPadding(false);
        GlobalBottomNavigationController.attach(this, wkVBinding.bottomNavigation, com.chat.uikit.R.id.i_partner);
        applyPartnerListInsets();
        applyTabletContentWidth();

        showSkeleton(true);
        PartnerListCache.loadAsync(this, cached -> {
            if (isFinishing() || isDestroyed() || currentResponse != null || cached == null) return;
            render(cached, true);
        });
    }

    @Override protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> finish());
        wkVBinding.partnerModeTab.setOnClickListener(v -> {
            if (adapter != null && adapter.getItemCount() > 0) wkVBinding.recyclerView.smoothScrollToPosition(0);
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
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    handler.removeCallbacks(onlineRunnable);
                    handler.postDelayed(onlineRunnable, 500L);
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
        if (currentResponse != null && !TextUtils.equals(currentResponse.day_key, PartnerListTime.currentDayKey())) {
            PartnerListCache.clearCurrentAccount(this);
            currentResponse = null;
            hasRenderedData = false;
            showSkeleton(true);
            requestRecommendations(false);
        } else if (refreshAfterProfileEdit) {
            refreshAfterProfileEdit = false;
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
        super.onPause();
    }

    @Override protected void onDestroy() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        if (wkVBinding != null) wkVBinding.recyclerView.setAdapter(null);
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROFILE_EDIT) {
            refreshAfterProfileEdit = false;
            requestRecommendations(false);
        }
    }

    private void requestRecommendations(boolean explicitRetry) {
        if (requesting) return;
        requesting = true;
        if (!hasRenderedData && currentResponse == null) showSkeleton(true);
        wkVBinding.retryBtn.setEnabled(false);
        PartnerListModel.getInstance().recommendations(new IRequestResultListener<>() {
            @Override public void onSuccess(PartnerListResponse result) {
                requesting = false;
                if (isFinishing() || isDestroyed()) return;
                wkVBinding.retryBtn.setEnabled(true);
                lastErrorProfileRequired = false;
                wkVBinding.retryBtn.setText(R.string.partnerlist_retry);
                if (result == null) {
                    showError(getString(R.string.partnerlist_load_failed), false);
                    return;
                }
                PartnerListCache.saveAsync(PartnerListActivity.this, result);
                render(result, false);
                if (result.updated_count > 0) {
                    showUpdateBanner(getResources().getQuantityString(R.plurals.partnerlist_updated_count,
                            result.updated_count, result.updated_count));
                }
            }

            @Override public void onFail(int code, String msg) {
                requesting = false;
                if (isFinishing() || isDestroyed()) return;
                wkVBinding.retryBtn.setEnabled(true);
                if (currentResponse == null && !hasRenderedData) {
                    boolean profileRequired = isProfileRequiredError(code, msg);
                    showError(TextUtils.isEmpty(msg) ? getString(R.string.partnerlist_load_failed) : msg, profileRequired);
                } else if (explicitRetry) {
                    toast(TextUtils.isEmpty(msg) ? getString(R.string.partnerlist_load_failed) : msg);
                }
            }
        });
    }

    private void render(PartnerListResponse response, boolean fromCache) {
        currentResponse = response;
        updateServerClock(response.server_time);
        List<PartnerListUser> users = new ArrayList<>(response.usersSafe());
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
        if (!fromCache) handler.postDelayed(this::refreshVisibleOnline, 500L);
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
        wkVBinding.updateBanner.setText(text);
        wkVBinding.updateBanner.setAlpha(0f);
        wkVBinding.updateBanner.setTranslationY(-dp(8));
        wkVBinding.updateBanner.setVisibility(View.VISIBLE);
        wkVBinding.updateBanner.animate().alpha(1f).translationY(0f).setDuration(180L).withEndAction(() ->
                handler.postDelayed(() -> wkVBinding.updateBanner.animate().alpha(0f).translationY(-dp(8)).setDuration(180L)
                        .withEndAction(() -> wkVBinding.updateBanner.setVisibility(View.GONE)).start(), 2200L)).start();
    }

    private void scheduleAll() {
        if (!resumed) return;
        handler.removeCallbacks(onlineRunnable);
        handler.removeCallbacks(clockRunnable);
        handler.removeCallbacks(dayBoundaryRunnable);
        handler.postDelayed(onlineRunnable, 700L);
        handler.postDelayed(clockRunnable, CLOCK_TICK_MS);
        long dayDelay = Math.max(1_000L, PartnerListTime.nextDayBoundaryMillis() - System.currentTimeMillis());
        handler.postDelayed(dayBoundaryRunnable, dayDelay);
        if (currentResponse != null) scheduleRotation(currentResponse);
    }

    private void scheduleRotation(PartnerListResponse response) {
        handler.removeCallbacks(rotationRunnable);
        if (response == null || response.rotation_done) return;
        long dueAt = PartnerListTime.nextDueAt(response.rotate_at, response.rotation_retry_at);
        if (dueAt <= 0) return;
        long delay = dueAt - nowServer();
        if (delay <= 0) delay = 1_000L;
        handler.postDelayed(rotationRunnable, Math.min(delay, 6L * 60L * 60L * 1000L));
    }

    private void refreshVisibleOnline() {
        if (!hasRenderedData || layoutManager == null || adapter == null || requesting || onlineRequesting) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) first = 0;
        if (last == RecyclerView.NO_POSITION) last = Math.min(adapter.getItemCount() - 1, 10);
        int end = Math.min(adapter.getItemCount(), last + 11);
        Set<String> ids = new LinkedHashSet<>();
        List<PartnerListUser> current = adapter.getCurrentList();
        for (int i = Math.max(0, first); i < end; i++) {
            PartnerListUser user = current.get(i);
            if (user != null && !TextUtils.isEmpty(user.stableId())) ids.add(user.stableId());
        }
        if (ids.isEmpty()) return;
        onlineRequesting = true;
        int requestId = ++onlineRequestSequence;
        final int visibleFirst = first;
        final int visibleLast = last;
        PartnerListModel.getInstance().onlineBatch(new ArrayList<>(ids), new IRequestResultListener<>() {
            @Override public void onSuccess(PartnerOnlineBatchResponse result) {
                if (requestId != onlineRequestSequence) return;
                onlineRequesting = false;
                if (result == null || isFinishing() || isDestroyed()) return;
                updateServerClock(result.server_time);
                Map<String, PartnerOnlineState> map = new HashMap<>();
                for (PartnerOnlineState state : result.usersSafe()) {
                    if (state != null && !TextUtils.isEmpty(state.uid)) map.put(state.uid, state);
                }
                ArrayList<PartnerListUser> updated = new ArrayList<>();
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
                    updated.add(user);
                }
                adapter.setServerTime(nowServer());
                if (changed) {
                    if (currentResponse != null) {
                        currentResponse.users = updated;
                        currentResponse.server_time = nowServer();
                        PartnerListCache.saveAsync(PartnerListActivity.this, currentResponse);
                    }
                    adapter.submitList(new ArrayList<>(updated));
                } else {
                    adapter.refreshVisible(visibleFirst, visibleLast);
                }
            }

            @Override public void onFail(int code, String msg) {
                if (requestId == onlineRequestSequence) onlineRequesting = false;
            }
        });
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
        if (user != null) PartnerListHostBridge.openProfile(this, user.stableId());
    }

    @Override public void onOpenChat(PartnerListUser user) {
        if (user != null) PartnerListHostBridge.openChat(this, user.stableId());
    }

    @Override public void onGreeting(PartnerListUser user, int position) {
        if (user == null || currentResponse == null) return;
        if (currentResponse.greeting_remaining <= 0) {
            showUpdateBanner(getString(R.string.partnerlist_daily_limit_message));
            return;
        }
        String uid = user.stableId();
        adapter.markGreetingPending(uid, true);
        PartnerListModel.getInstance().sendGreeting(uid, getString(R.string.partnerlist_default_greeting), new IRequestResultListener<>() {
            @Override public void onSuccess(PartnerGreetingResponse result) {
                adapter.markGreetingPending(uid, false);
                if (result != null && result.success()) {
                    String greetingText = getString(R.string.partnerlist_default_greeting);
                    PartnerListHostBridge.saveOutgoingGreeting(uid, greetingText, result);
                    int maxPending = result.max_greeting_count > 0 ? result.max_greeting_count : 3;
                    int pendingCount = Math.max(1, result.requester_msg_count);
                    if (result.contact_status == 1) PartnerPendingStore.markActive(uid);
                    else PartnerPendingStore.markRequester(uid, pendingCount, maxPending);
                    adapter.markGreeted(uid);
                    if (result.greeting_day_limit > 0) {
                        currentResponse.greeting_limit = result.greeting_day_limit;
                        currentResponse.greeting_used = Math.max(0, result.greeting_day_used);
                        currentResponse.greeting_remaining = Math.max(0, result.greeting_day_remaining);
                    } else {
                        currentResponse.greeting_used = Math.min(currentResponse.greeting_limit, currentResponse.greeting_used + 1);
                        currentResponse.greeting_remaining = Math.max(0, currentResponse.greeting_limit - currentResponse.greeting_used);
                    }
                    adapter.setGreetingRemaining(currentResponse.greeting_remaining);
                    updateHeader(currentResponse);
                    PartnerListCache.saveAsync(PartnerListActivity.this, currentResponse);
                    showUpdateBanner(getString(R.string.partnerlist_greeting_success));
                } else {
                    toast(result == null || TextUtils.isEmpty(result.messageSafe())
                            ? getString(R.string.partnerlist_greeting_failed) : result.messageSafe());
                }
            }

            @Override public void onFail(int code, String msg) {
                adapter.markGreetingPending(uid, false);
                toast(TextUtils.isEmpty(msg) ? getString(R.string.partnerlist_greeting_failed) : msg);
            }
        });
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

    private void configureEdgeToEdgeWindow() {
        Window window = getWindow();
        if (window == null) return;
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(getResources().getColor(com.chat.uikit.R.color.tab_bg));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void applyPartnerListInsets() {
        if (wkVBinding == null || wkVBinding.getRoot() == null || wkVBinding.topBar == null) return;
        topBarBaseHeight = layoutHeight(wkVBinding.topBar);
        topBarBasePaddingTop = wkVBinding.topBar.getPaddingTop();
        if (wkVBinding.updateBanner.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            updateBannerBaseMarginTop = ((FrameLayout.LayoutParams) wkVBinding.updateBanner.getLayoutParams()).topMargin;
        }
        ViewCompat.setOnApplyWindowInsetsListener(wkVBinding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int topExtra = dp(4);
            setLayoutHeight(wkVBinding.topBar, topBarBaseHeight + bars.top + topExtra);
            wkVBinding.topBar.setPadding(wkVBinding.topBar.getPaddingLeft(),
                    topBarBasePaddingTop + bars.top + topExtra,
                    wkVBinding.topBar.getPaddingRight(),
                    wkVBinding.topBar.getPaddingBottom());
            if (wkVBinding.updateBanner.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams bannerLp = (FrameLayout.LayoutParams) wkVBinding.updateBanner.getLayoutParams();
                int target = updateBannerBaseMarginTop + bars.top + topExtra;
                if (bannerLp.topMargin != target) {
                    bannerLp.topMargin = target;
                    wkVBinding.updateBanner.setLayoutParams(bannerLp);
                }
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(wkVBinding.getRoot());
    }

    private int layoutHeight(View view) {
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        return params == null || params.height < 0 ? 0 : params.height;
    }

    private void setLayoutHeight(View view, int height) {
        if (view == null || height <= 0 || view.getLayoutParams() == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.height == height) return;
        params.height = height;
        view.setLayoutParams(params);
    }

    private void applyTabletContentWidth() {
        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / Math.max(1f, density);
        if (widthDp < 800f || wkVBinding.contentContainer == null) return;
        View parent = (View) wkVBinding.contentContainer.getParent();
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
