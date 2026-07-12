package com.chat.partnerlist;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.IRequestResultListener;
import com.chat.partnerlist.databinding.ActivityPartnerListBinding;
import com.chat.partnerlist.model.PartnerGreetingResponse;
import com.chat.partnerlist.model.PartnerHeartbeatResponse;
import com.chat.partnerlist.model.PartnerListResponse;
import com.chat.partnerlist.model.PartnerListUser;
import com.chat.partnerlist.model.PartnerOnlineBatchResponse;
import com.chat.partnerlist.model.PartnerOnlineState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PartnerListActivity extends WKBaseActivity<ActivityPartnerListBinding> implements PartnerListAdapter.Listener {
    private static final long HEARTBEAT_INTERVAL_MS = 55_000L;
    private static final long ONLINE_REFRESH_INTERVAL_MS = 90_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PartnerListAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PartnerListResponse currentResponse;
    private boolean requesting;
    private boolean hasRenderedData;
    private boolean resumed;

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override public void run() {
            if (!resumed || isFinishing() || isDestroyed()) return;
            PartnerListModel.getInstance().heartbeat(new IRequestResultListener<>() {
                @Override public void onSuccess(PartnerHeartbeatResponse result) {}
                @Override public void onFail(int code, String msg) {}
            });
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    private final Runnable onlineRunnable = new Runnable() {
        @Override public void run() {
            if (!resumed || isFinishing() || isDestroyed()) return;
            refreshVisibleOnline();
            handler.postDelayed(this, ONLINE_REFRESH_INTERVAL_MS);
        }
    };

    private final Runnable rotationRunnable = () -> {
        if (resumed && !requesting && currentResponse != null && !currentResponse.rotation_done) {
            requestRecommendations(false);
        }
    };

    @Override protected ActivityPartnerListBinding getViewBinding() {
        return ActivityPartnerListBinding.inflate(getLayoutInflater());
    }

    @Override public boolean supportSlideBack() { return true; }

    @Override protected void setTitle(TextView titleTv) {}

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(0xFFF5F7FF);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        super.onCreate(savedInstanceState);
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

        PartnerListResponse cached = PartnerListCache.load(this);
        if (cached != null && !cached.usersSafe().isEmpty()) {
            render(cached, true);
        } else {
            showSkeleton(true);
        }
    }

    @Override protected void initListener() {
        wkVBinding.backBtn.setOnClickListener(v -> finish());
        wkVBinding.retryBtn.setOnClickListener(v -> requestRecommendations(true));
        wkVBinding.completeProfileBtn.setOnClickListener(v -> PartnerListHostBridge.openProfileEdit(this));
        wkVBinding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    handler.removeCallbacks(onlineRunnable);
                    handler.postDelayed(onlineRunnable, 250L);
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
        handler.removeCallbacks(heartbeatRunnable);
        handler.removeCallbacks(onlineRunnable);
        handler.post(heartbeatRunnable);
        handler.postDelayed(onlineRunnable, 400L);
        if (currentResponse != null) scheduleRotation(currentResponse);
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(heartbeatRunnable);
        handler.removeCallbacks(onlineRunnable);
        handler.removeCallbacks(rotationRunnable);
        super.onPause();
    }

    @Override protected void onDestroy() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        if (wkVBinding != null) wkVBinding.recyclerView.setAdapter(null);
        super.onDestroy();
    }

    private void requestRecommendations(boolean explicitRetry) {
        if (requesting) return;
        requesting = true;
        if (!hasRenderedData) showSkeleton(true);
        wkVBinding.retryBtn.setEnabled(false);
        PartnerListModel.getInstance().recommendations(new IRequestResultListener<>() {
            @Override public void onSuccess(PartnerListResponse result) {
                requesting = false;
                if (isFinishing() || isDestroyed()) return;
                wkVBinding.retryBtn.setEnabled(true);
                if (result == null) {
                    showError(getString(R.string.partnerlist_load_failed));
                    return;
                }
                PartnerListCache.save(PartnerListActivity.this, result);
                render(result, false);
                if (result.updated_count > 0) {
                    showUpdateBanner(getResources().getQuantityString(R.plurals.partnerlist_updated_count, result.updated_count, result.updated_count));
                }
            }

            @Override public void onFail(int code, String msg) {
                requesting = false;
                if (isFinishing() || isDestroyed()) return;
                wkVBinding.retryBtn.setEnabled(true);
                if (!hasRenderedData) showError(TextUtils.isEmpty(msg) ? getString(R.string.partnerlist_load_failed) : msg);
                else if (explicitRetry) toast(TextUtils.isEmpty(msg) ? getString(R.string.partnerlist_load_failed) : msg);
            }
        });
    }

    private void render(PartnerListResponse response, boolean fromCache) {
        currentResponse = response;
        List<PartnerListUser> users = new ArrayList<>(response.usersSafe());
        hasRenderedData = !users.isEmpty();
        showSkeleton(false);
        wkVBinding.errorLayout.setVisibility(View.GONE);
        wkVBinding.recyclerView.setVisibility(hasRenderedData ? View.VISIBLE : View.GONE);
        wkVBinding.emptyLayout.setVisibility(hasRenderedData ? View.GONE : View.VISIBLE);

        adapter.setServerTime(response.server_time > 0 ? response.server_time : System.currentTimeMillis());
        adapter.setGreetingRemaining(response.greeting_remaining);
        adapter.submitList(users);
        updateHeader(response);
        updateFooter(response, users.size());
        scheduleRotation(response);
        if (!fromCache) handler.postDelayed(this::refreshVisibleOnline, 300L);
    }

    private void updateHeader(PartnerListResponse response) {
        int total = response.usersSafe().size();
        wkVBinding.subtitleTv.setText(getResources().getQuantityString(R.plurals.partnerlist_found_count, total, total));
        wkVBinding.quotaTv.setText(getString(R.string.partnerlist_quota_value, Math.max(0, response.greeting_remaining), Math.max(1, response.greeting_limit)));
        if (response.rotation_done) {
            wkVBinding.statusHint.setText(getString(R.string.partnerlist_status_finished, response.unique_assigned_count, response.daily_candidate_limit));
        } else {
            long dueAt = PartnerListTime.nextDueAt(response.rotate_at, response.rotation_retry_at);
            if (dueAt <= 0) {
                wkVBinding.statusHint.setText(R.string.partnerlist_status_preparing);
                return;
            }
            long now = response.server_time > 0 ? response.server_time : System.currentTimeMillis();
            long minutes = Math.max(0L, (dueAt - now + 59_999L) / 60_000L);
            if (minutes <= 0) wkVBinding.statusHint.setText(R.string.partnerlist_status_updating);
            else if (minutes < 60) wkVBinding.statusHint.setText(getString(R.string.partnerlist_status_minutes, minutes));
            else wkVBinding.statusHint.setText(getString(R.string.partnerlist_status_hours, Math.max(1L, minutes / 60L)));
        }
    }

    private void updateFooter(PartnerListResponse response, int count) {
        if (count <= 0) {
            wkVBinding.footerTv.setVisibility(View.GONE);
            return;
        }
        wkVBinding.footerTv.setVisibility(View.VISIBLE);
        if (response.rotation_done || response.unique_assigned_count >= response.daily_candidate_limit) {
            wkVBinding.footerTv.setText(getString(R.string.partnerlist_footer_finished, response.unique_assigned_count));
        } else {
            wkVBinding.footerTv.setText(R.string.partnerlist_footer_waiting);
        }
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

    private void showError(String message) {
        showSkeleton(false);
        wkVBinding.recyclerView.setVisibility(View.GONE);
        wkVBinding.emptyLayout.setVisibility(View.GONE);
        wkVBinding.errorLayout.setVisibility(View.VISIBLE);
        wkVBinding.errorTv.setText(message);
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

    private void scheduleRotation(PartnerListResponse response) {
        handler.removeCallbacks(rotationRunnable);
        if (response == null || response.rotation_done) return;
        long dueAt = PartnerListTime.nextDueAt(response.rotate_at, response.rotation_retry_at);
        if (dueAt <= 0) return;
        long serverNow = response.server_time > 0 ? response.server_time : System.currentTimeMillis();
        long delay = dueAt - serverNow;
        if (delay <= 0) delay = 1000L;
        handler.postDelayed(rotationRunnable, Math.min(delay, 6L * 60L * 60L * 1000L));
    }

    private void refreshVisibleOnline() {
        if (!hasRenderedData || layoutManager == null || adapter == null || requesting) return;
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
        PartnerListModel.getInstance().onlineBatch(new ArrayList<>(ids), new IRequestResultListener<>() {
            @Override public void onSuccess(PartnerOnlineBatchResponse result) {
                if (result == null || result.usersSafe().isEmpty() || isFinishing() || isDestroyed()) return;
                Map<String, PartnerOnlineState> map = new HashMap<>();
                for (PartnerOnlineState state : result.usersSafe()) if (state != null && !TextUtils.isEmpty(state.uid)) map.put(state.uid, state);
                ArrayList<PartnerListUser> updated = new ArrayList<>();
                for (PartnerListUser original : adapter.getCurrentList()) updated.add(original == null ? null : original.copy());
                boolean changed = false;
                for (PartnerListUser user : updated) {
                    PartnerOnlineState state = map.get(user.stableId());
                    if (state == null) continue;
                    if (user.online != state.online || user.last_active_at != state.last_active_at) changed = true;
                    user.online = state.online;
                    user.last_active_at = state.last_active_at;
                }
                if (changed) {
                    if (currentResponse != null) {
                        currentResponse.users = updated;
                        currentResponse.server_time = result.server_time;
                        PartnerListCache.save(PartnerListActivity.this, currentResponse);
                    }
                    adapter.setServerTime(result.server_time > 0 ? result.server_time : System.currentTimeMillis());
                    adapter.submitList(new ArrayList<>(updated));
                }
            }
            @Override public void onFail(int code, String msg) {}
        });
    }

    @Override public void onOpenProfile(PartnerListUser user) {
        if (user != null) PartnerListHostBridge.openProfile(this, user.stableId());
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
                    PartnerListCache.save(PartnerListActivity.this, currentResponse);
                    showUpdateBanner(getString(R.string.partnerlist_greeting_success));
                } else {
                    toast(result == null || TextUtils.isEmpty(result.messageSafe()) ? getString(R.string.partnerlist_greeting_failed) : result.messageSafe());
                }
            }

            @Override public void onFail(int code, String msg) {
                adapter.markGreetingPending(uid, false);
                toast(TextUtils.isEmpty(msg) ? getString(R.string.partnerlist_greeting_failed) : msg);
            }
        });
    }

    private void toast(String message) {
        if (!TextUtils.isEmpty(message)) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
