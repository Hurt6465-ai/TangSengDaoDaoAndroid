package com.chat.feedlist;

import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;

import com.chat.feed.comment.FeedCommentBottomSheet;
import com.chat.feedlist.model.FeedListItem;
import com.chat.feedlist.model.FeedListInteractionResponse;
import com.chat.feedlist.model.FeedListResponse;
import com.chat.feedlist.model.FeedListTikTokPreview;
import com.chat.feedlist.model.FeedListMedia;
import com.chat.feedlist.model.FeedListUser;
import com.chat.feedlist.publish.FeedListPublishActivity;
import com.chat.feedlist.databinding.ActivityFeedTimelineBinding;
import com.chat.uikit.GlobalBottomNavigationController;
import com.chat.uikit.user.service.UserModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeedTimelineActivity extends WKBaseActivity<ActivityFeedTimelineBinding> implements FeedTimelineAdapter.Listener {
    private static final int PAGE_SIZE = 12;
    private static final int REQ_PUBLISH = 5101;
    private static final String MODE_LATEST = "latest";
    private static final String MODE_FOLLOWING = "following";
    private static final long TIKTOK_RETRY_COOLDOWN_MS = 5L * 60L * 1000L;
    private static final long TIKTOK_COVER_RETRY_COOLDOWN_MS = 30L * 1000L;

    private final TimelineState latest = new TimelineState(MODE_LATEST);
    private final TimelineState following = new TimelineState(MODE_FOLLOWING);
    private final Set<String> likeInFlight = new HashSet<>();
    private final Set<String> followInFlight = new HashSet<>();
    private final Set<String> tiktokResolveInFlight = new HashSet<>();
    private final Set<String> tiktokOpenAfterResolve = new HashSet<>();
    private final Map<String, Long> tiktokResolveFailedAt = new HashMap<>();
    private final Map<String, Long> tiktokResolvedAt = new HashMap<>();
    private TimelineState current = latest;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable publishRefreshTask = this::refreshAfterPublish;
    private FeedTimelineAdapter adapter;
    private LinearLayoutManager layoutManager;
    private RecyclerView.OnScrollListener timelineScrollListener;
    private boolean resumed;
    private boolean destroyed;

    @Override protected ActivityFeedTimelineBinding getViewBinding() { return ActivityFeedTimelineBinding.inflate(getLayoutInflater()); }
    @Override protected boolean supportSlideBack() { return false; }
    @Override protected void setTitle(TextView titleTv) {}

    @Override protected void initView() {
        adapter = new FeedTimelineAdapter(this);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setInitialPrefetchItemCount(4);
        wkVBinding.recyclerView.setLayoutManager(layoutManager);
        wkVBinding.recyclerView.setAdapter(adapter);
        wkVBinding.recyclerView.setHasFixedSize(false);
        wkVBinding.recyclerView.setItemViewCacheSize(5);
        wkVBinding.recyclerView.getRecycledViewPool().setMaxRecycledViews(0, 10);
        wkVBinding.recyclerView.setClipToPadding(false);
        if (wkVBinding.recyclerView.getItemAnimator() instanceof DefaultItemAnimator) {
            ((DefaultItemAnimator) wkVBinding.recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        GlobalBottomNavigationController.attach(this, wkVBinding.bottomNavigation, com.chat.uikit.R.id.i_discover);
        applyInsets();
        updateTabs();
        showLoadingIfNeeded();
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(wkVBinding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            android.view.ViewGroup.LayoutParams topParams = wkVBinding.topBar.getLayoutParams();
            topParams.height = dp(52) + bars.top;
            wkVBinding.topBar.setLayoutParams(topParams);
            wkVBinding.topBar.setPadding(0, bars.top, 0, 0);
            android.view.ViewGroup.LayoutParams navParams = wkVBinding.bottomNavigation.getLayoutParams();
            navParams.height = dp(64) + bars.bottom;
            wkVBinding.bottomNavigation.setLayoutParams(navParams);
            wkVBinding.bottomNavigation.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(wkVBinding.getRoot());
    }

    @Override protected void initListener() {
        wkVBinding.latestTab.setOnClickListener(v -> switchMode(latest));
        wkVBinding.followingTab.setOnClickListener(v -> switchMode(following));
        wkVBinding.publishBtn.setOnClickListener(v -> {
            try {
                FeedListPublishActivity.openForResult(this, REQ_PUBLISH);
            } catch (Throwable error) {
                toast(getString(R.string.feedlist_publish_open_failed));
            }
        });
        wkVBinding.refreshLayout.setOnRefreshListener(layout -> requestPage(current, true));
        wkVBinding.statePanel.setOnClickListener(v -> requestPage(current, true));
        timelineScrollListener = new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (isUnavailable()) return;
                saveScrollState(current);
                if (dy <= 0 || current.loading || !current.hasMore || current.items.isEmpty()) return;
                int last = layoutManager.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 4) requestPage(current, false);
            }

            @Override public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    preloadTikTokCovers();
                }
            }
        };
        wkVBinding.recyclerView.addOnScrollListener(timelineScrollListener);
    }

    @Override protected void initData() { loadCacheThenRefresh(latest); }

    private void loadCacheThenRefresh(TimelineState state) {
        if (state.cacheRequested) {
            if (!state.networkRequested) requestPage(state, true);
            return;
        }
        state.cacheRequested = true;
        FeedListCache.load(this, state.mode, page -> {
            if (isUnavailable()) return;
            state.cacheLoaded = true;
            if (page != null && state.items.isEmpty()) {
                state.items.addAll(page.items == null ? new ArrayList<>() : page.items);
                state.cursor = page.cursor == null ? "" : page.cursor;
                state.hasMore = page.has_more == 1;
                long elapsedSinceSave = Math.max(0L, System.currentTimeMillis() - page.saved_at);
                state.serverTime = page.server_time > 0 ? normalizeEpochMillis(page.server_time) + elapsedSinceSave : 0L;
                state.serverTimeLocalAt = System.currentTimeMillis();
                if (current == state) render(true);
            }
            requestPage(state, true);
        });
    }

    private void switchMode(TimelineState state) {
        if (current == state) {
            if (!state.items.isEmpty()) {
                int first = layoutManager.findFirstVisibleItemPosition();
                if (first > 12) wkVBinding.recyclerView.scrollToPosition(0);
                else wkVBinding.recyclerView.smoothScrollToPosition(0);
            }
            return;
        }
        saveScrollState(current);
        // One SmartRefreshLayout is shared by both tabs. Stop the visual spinner before
        // changing modes; the old tab's in-flight callback must not control the new tab UI.
        wkVBinding.refreshLayout.finishRefresh(false);
        current = state;
        updateTabs();
        render(true);
        loadCacheThenRefresh(state);
    }

    private void updateTabs() {
        boolean isLatest = current == latest;
        wkVBinding.latestIndicator.setVisibility(isLatest ? View.VISIBLE : View.INVISIBLE);
        wkVBinding.followingIndicator.setVisibility(isLatest ? View.INVISIBLE : View.VISIBLE);
        int selected = ContextCompat.getColor(this, R.color.feedlist_text);
        int normal = ContextCompat.getColor(this, R.color.feedlist_secondary);
        wkVBinding.latestTabText.setTextColor(isLatest ? selected : normal);
        wkVBinding.followingTabText.setTextColor(isLatest ? normal : selected);
        wkVBinding.latestTabText.setTypeface(null, isLatest ? Typeface.BOLD : Typeface.NORMAL);
        wkVBinding.followingTabText.setTypeface(null, isLatest ? Typeface.NORMAL : Typeface.BOLD);
    }

    private void requestPage(TimelineState state, boolean refresh) {
        if (state == null || isUnavailable()) return;
        if (state.loading) {
            if (refresh) {
                state.pendingRefresh = true;
                if (current == state) wkVBinding.refreshLayout.finishRefresh(false);
            }
            return;
        }
        if (!refresh && !state.hasMore) return;
        if (current == state) saveScrollState(state);
        state.loading = true;
        state.networkRequested = true;
        if (current == state && state.items.isEmpty()) showLoadingIfNeeded();
        String cursor = refresh ? "" : state.cursor;
        IRequestResultListener<FeedListResponse> listener = new IRequestResultListener<>() {
            @Override public void onSuccess(FeedListResponse result) {
                state.loading = false;
                boolean runPendingRefresh = state.pendingRefresh;
                state.pendingRefresh = false;
                if (isUnavailable()) return;
                if (refresh && current == state) wkVBinding.refreshLayout.finishRefresh(result != null);
                if (result == null) {
                    if (current == state) renderErrorIfEmpty();
                    if (runPendingRefresh) requestPage(state, true);
                    return;
                }
                List<FeedListItem> incoming = result.safeList();
                if (refresh) state.items.clear();
                merge(state.items, incoming);
                state.cursor = result.cursor == null ? "" : result.cursor;
                state.hasMore = result.has_more == 1 && !TextUtils.isEmpty(state.cursor);
                state.serverTime = normalizeEpochMillis(result.server_time);
                state.serverTimeLocalAt = System.currentTimeMillis();
                FeedListCache.save(FeedTimelineActivity.this, state.mode, state.items, state.cursor, state.hasMore, currentServerTime(state));
                if (current == state) render(true);
                if (runPendingRefresh) requestPage(state, true);
            }

            @Override public void onFail(int code, String msg) {
                state.loading = false;
                boolean runPendingRefresh = state.pendingRefresh;
                state.pendingRefresh = false;
                if (isUnavailable()) return;
                if (refresh && current == state) wkVBinding.refreshLayout.finishRefresh(false);
                if (current == state) {
                    if (state.items.isEmpty()) renderErrorIfEmpty();
                    else toast(TextUtils.isEmpty(msg) ? getString(R.string.feedlist_action_failed) : msg);
                }
                if (runPendingRefresh) requestPage(state, true);
            }
        };
        if (MODE_FOLLOWING.equals(state.mode)) FeedListModel.getInstance().following(cursor, PAGE_SIZE, listener);
        else FeedListModel.getInstance().timeline(cursor, PAGE_SIZE, listener);
    }

    private void merge(ArrayList<FeedListItem> target, List<FeedListItem> incoming) {
        LinkedHashMap<String, FeedListItem> map = new LinkedHashMap<>();
        for (FeedListItem item : target) if (item != null) map.put(item.stableKey(), item);
        if (incoming != null) for (FeedListItem item : incoming) if (item != null) map.put(item.stableKey(), item);
        target.clear();
        target.addAll(map.values());
    }

    private void render(boolean restorePosition) {
        if (isUnavailable() || adapter == null || layoutManager == null) return;
        TimelineState state = current;
        adapter.setServerTime(currentServerTime(state));
        adapter.submitList(new ArrayList<>(state.items), () -> {
            if (isUnavailable() || adapter == null || layoutManager == null) return;
            if (restorePosition && current == state && state.hasScrollState && !state.items.isEmpty()) {
                int position = Math.min(state.firstVisiblePosition, Math.max(0, adapter.getItemCount() - 1));
                layoutManager.scrollToPositionWithOffset(position, state.firstVisibleOffset);
            }
            preloadTikTokCovers();
        });
        boolean empty = state.items.isEmpty();
        wkVBinding.statePanel.setVisibility(empty ? View.VISIBLE : View.GONE);
        wkVBinding.loadingView.setVisibility(View.GONE);
        if (empty) wkVBinding.stateText.setText(state == latest ? R.string.feedlist_empty_latest : R.string.feedlist_empty_following);
        wkVBinding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }


    private void preloadTikTokCovers() {
        if (!resumed || isUnavailable() || adapter == null || layoutManager == null || adapter.getItemCount() == 0) return;
        TikTokEmbedActivity.prewarm(getApplicationContext());
        int first = layoutManager.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) first = 0;
        adapter.preloadTikTokCovers(this, first, 4);
    }

    private void saveScrollState(TimelineState state) {
        if (state == null || current != state || layoutManager == null || state.items.isEmpty()) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) return;
        View firstView = layoutManager.findViewByPosition(first);
        state.firstVisiblePosition = first;
        state.firstVisibleOffset = firstView == null ? 0 : layoutManager.getDecoratedTop(firstView) - wkVBinding.recyclerView.getPaddingTop();
        state.hasScrollState = true;
    }

    private void showLoadingIfNeeded() {
        if (!current.items.isEmpty()) return;
        wkVBinding.recyclerView.setVisibility(View.GONE);
        wkVBinding.statePanel.setVisibility(View.VISIBLE);
        wkVBinding.loadingView.setVisibility(View.VISIBLE);
        wkVBinding.stateText.setText(R.string.feedlist_loading);
    }

    private void renderErrorIfEmpty() {
        wkVBinding.recyclerView.setVisibility(View.GONE);
        wkVBinding.statePanel.setVisibility(View.VISIBLE);
        wkVBinding.loadingView.setVisibility(View.GONE);
        wkVBinding.stateText.setText(R.string.feedlist_retry);
    }

    @Override public void onProfile(FeedListItem item) {
        if (item == null || TextUtils.isEmpty(item.authorUid())) return;
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            route.getMethod("open", android.content.Context.class, String.class).invoke(null, this, item.authorUid());
        } catch (Throwable ignored) {
            toast(getString(R.string.feedlist_action_failed));
        }
    }

    @Override public void onMore(FeedListItem item) {
        if (item == null) return;
        boolean mine = TextUtils.equals(WKConfig.getInstance().getUid(), item.authorUid());
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, dp(14));
        int danger = ContextCompat.getColor(this, R.color.feedlist_danger);
        if (mine) {
            addSheetItem(container, getString(R.string.feedlist_delete), danger, () -> { dialog.dismiss(); confirmDelete(item); });
        } else {
            addSheetItem(container, getString(R.string.feedlist_report), danger, () -> { dialog.dismiss(); showReportReasons(item); });
            addSheetItem(container, getString(R.string.feedlist_block), danger, () -> { dialog.dismiss(); confirmBlock(item); });
        }
        dialog.setContentView(container);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void addSheetItem(LinearLayout parent, String text, int color, Runnable action) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(color);
        item.setTextSize(16);
        item.setGravity(Gravity.CENTER);
        item.setBackgroundResource(R.drawable.bg_feedlist_sheet_item);
        item.setOnClickListener(v -> action.run());
        parent.addView(item, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
    }

    private void showReportReasons(FeedListItem item) {
        String[] reasons = getResources().getStringArray(R.array.feedlist_report_reasons);
        String[] codes = {"sexual", "harassment", "spam", "fraud", "privacy", "other"};
        new AlertDialog.Builder(this).setTitle(R.string.feedlist_report_reason_title)
                .setItems(reasons, (dialog, which) -> {
                    int index = Math.max(0, Math.min(which, reasons.length - 1));
                    report(item, index < codes.length ? codes[index] : "other");
                }).show();
    }

    private void report(FeedListItem item, String reason) {
        FeedListModel.getInstance().report(item.feed_id, reason, new IRequestResultListener<CommonResponse>() {
            @Override public void onSuccess(CommonResponse result) {
                if (isUnavailable()) return;
                removeFeedEverywhere(item.feed_id);
                toast(getString(R.string.feedlist_reported));
            }
            @Override public void onFail(int code, String msg) {
                if (isUnavailable()) return;
                toast(TextUtils.isEmpty(msg) ? getString(R.string.feedlist_action_failed) : msg);
            }
        });
    }

    private void confirmBlock(FeedListItem item) {
        new AlertDialog.Builder(this).setMessage(R.string.feedlist_block_confirm)
                .setPositiveButton(R.string.feedlist_block_now, (dialog, which) -> block(item.authorUid()))
                .setNegativeButton(R.string.feedlist_not_now, null).show();
    }

    private void block(String uid) {
        if (TextUtils.isEmpty(uid)) return;
        UserModel.getInstance().addBlackList(uid, (code, msg) -> {
            if (isUnavailable()) return;
            if (code == HttpResponseCode.success) { removeUserEverywhere(uid); toast(getString(R.string.feedlist_blocked)); }
            else toast(TextUtils.isEmpty(msg) ? getString(R.string.feedlist_action_failed) : msg);
        });
    }

    private void confirmDelete(FeedListItem item) {
        new AlertDialog.Builder(this).setMessage(R.string.feedlist_delete_confirm)
                .setPositiveButton(R.string.feedlist_delete, (dialog, which) -> delete(item))
                .setNegativeButton(R.string.feedlist_not_now, null).show();
    }

    private void delete(FeedListItem item) {
        FeedListModel.getInstance().delete(item.feed_id, new IRequestResultListener<CommonResponse>() {
            @Override public void onSuccess(CommonResponse result) {
                if (isUnavailable()) return;
                removeFeedEverywhere(item.feed_id);
                toast(getString(R.string.feedlist_deleted));
            }
            @Override public void onFail(int code, String msg) {
                if (isUnavailable()) return;
                toast(TextUtils.isEmpty(msg) ? getString(R.string.feedlist_action_failed) : msg);
            }
        });
    }

    private void removeFeedEverywhere(String feedId) {
        removeFeed(latest.items, feedId);
        removeFeed(following.items, feedId);
        FeedListCache.removeFeed(this, feedId);
        render(false);
    }

    private void removeUserEverywhere(String uid) {
        removeUser(latest.items, uid);
        removeUser(following.items, uid);
        FeedListCache.removeUser(this, uid);
        render(false);
    }

    @Override public void onFollow(FeedListItem item) {
        if (item == null) return;
        String uid = item.authorUid();
        if (TextUtils.isEmpty(uid) || TextUtils.equals(uid, WKConfig.getInstance().getUid()) || followInFlight.contains(uid)) return;
        FeedListUser user = item.user;
        boolean oldFollowed = user != null && user.follow == 1;
        boolean desired = !oldFollowed;
        followInFlight.add(uid);
        syncFollow(uid, desired);
        adapter.notifyUserChanged(uid);
        persistStates();
        FeedListCache.updateFollow(this, uid, desired, false);
        FeedListModel.getInstance().setFollow(uid, desired, new IRequestResultListener<CommonResponse>() {
            @Override public void onSuccess(CommonResponse result) {
                followInFlight.remove(uid);
                if (isUnavailable()) return;
                syncFollow(uid, desired);
                if (!desired) {
                    removeUser(following.items, uid);
                    render(false);
                } else {
                    adapter.notifyUserChanged(uid);
                }
                persistStates();
                FeedListCache.updateFollow(FeedTimelineActivity.this, uid, desired, true);
            }

            @Override public void onFail(int code, String msg) {
                followInFlight.remove(uid);
                if (isUnavailable()) return;
                syncFollow(uid, oldFollowed);
                adapter.notifyUserChanged(uid);
                persistStates();
                FeedListCache.updateFollow(FeedTimelineActivity.this, uid, oldFollowed, false);
                toast(TextUtils.isEmpty(msg) ? getString(R.string.feedlist_action_failed) : msg);
            }
        });
    }

    @Override public void onLike(FeedListItem item, int position) {
        if (item == null || TextUtils.isEmpty(item.feed_id) || likeInFlight.contains(item.feed_id)) return;
        likeInFlight.add(item.feed_id);
        int oldLiked = item.liked;
        int oldCount = item.like_count;
        int desired = oldLiked == 1 ? 0 : 1;
        int optimisticCount = Math.max(0, oldCount + (desired == 1 ? 1 : -1));
        syncInteraction(item.feed_id, desired, optimisticCount, null, null);
        notifyInteraction(item.feed_id);
        persistStates();
        FeedListModel.getInstance().like(item.feed_id, desired == 1, new IRequestResultListener<FeedListInteractionResponse>() {
            @Override public void onSuccess(FeedListInteractionResponse result) {
                likeInFlight.remove(item.feed_id);
                if (isUnavailable()) return;
                int liked = result == null ? desired : result.liked;
                int count = result == null ? optimisticCount : Math.max(0, result.like_count);
                syncInteraction(item.feed_id, liked, count, null, null);
                notifyInteraction(item.feed_id);
                persistStates();
            }

            @Override public void onFail(int code, String msg) {
                likeInFlight.remove(item.feed_id);
                if (isUnavailable()) return;
                syncInteraction(item.feed_id, oldLiked, oldCount, null, null);
                notifyInteraction(item.feed_id);
                persistStates();
                toast(TextUtils.isEmpty(msg) ? getString(R.string.feedlist_action_failed) : msg);
            }
        });
    }

    @Override public void onComment(FeedListItem item, int position) {
        if (item == null) return;
        FeedListUser user = item.user;
        FeedCommentBottomSheet sheet = FeedCommentBottomSheet.newInstance(item.feed_id, item.comment_count,
                item.authorUid(), item.userName(), user == null ? "" : user.avatar, user == null ? "" : user.avatar_cache_key,
                user == null ? "" : user.country_code, user != null && user.follow == 1, item.displayTitle());
        sheet.setOnCommentSentListener(delta -> {
            if (isUnavailable()) return;
            int count = Math.max(0, item.comment_count + delta);
            syncInteraction(item.feed_id, null, null, count, null);
            notifyInteraction(item.feed_id);
            persistStates();
        });
        sheet.show(getSupportFragmentManager(), "feed_comments");
    }

    @Override public void onShare(FeedListItem item) {
        if (item == null || TextUtils.isEmpty(item.feed_id)) return;
        FeedListModel.getInstance().share(item.feed_id, new IRequestResultListener<FeedListInteractionResponse>() {
            @Override public void onSuccess(FeedListInteractionResponse result) {
                if (isUnavailable() || result == null) return;
                syncInteraction(item.feed_id, null, null, null, Math.max(0, result.share_count));
                notifyInteraction(item.feed_id);
                persistStates();
            }
            @Override public void onFail(int code, String msg) {}
        });
        String shareText = item.displayTitle();
        FeedListMedia first = item.firstMedia();
        if (first != null && first.isTikTok() && !TextUtils.isEmpty(first.external_url)) {
            shareText += (TextUtils.isEmpty(shareText) ? "" : "\n") + first.external_url;
        } else if (first != null && !TextUtils.isEmpty(first.displayUrl())) {
            shareText += (TextUtils.isEmpty(shareText) ? "" : "\n") + first.displayUrl();
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText.trim());
        startActivity(Intent.createChooser(intent, getString(R.string.feedlist_share)));
    }

    @Override public void onImages(FeedListItem item, int index, List<FeedListMedia> media) {
        ArrayList<String> urls = new ArrayList<>();
        int selected = 0;
        if (media != null) {
            for (int i = 0; i < media.size(); i++) {
                FeedListMedia value = media.get(i);
                if (value == null || TextUtils.isEmpty(value.displayUrl())) continue;
                if (i < index) selected++;
                urls.add(value.displayUrl());
            }
        }
        FeedImageViewerActivity.open(this, urls, Math.min(selected, Math.max(0, urls.size() - 1)));
    }

    @Override public void onTikTok(FeedListItem item, FeedListMedia media) {
        if (media == null || isUnavailable()) return;
        String videoId = media.tiktokVideoId();
        if (!TextUtils.isEmpty(videoId)) {
            TikTokEmbedActivity.open(this, videoId, media.tiktokSourceUrl(), media.tiktokCoverUrl());
            return;
        }
        resolveTikTokMetadata(item, media, true, false);
    }

    @Override public void onTikTokMetadataNeeded(FeedListItem item, FeedListMedia media) {
        resolveTikTokMetadata(item, media, false, false);
    }

    @Override public void onTikTokCoverLoadFailed(FeedListItem item, FeedListMedia media) {
        resolveTikTokMetadata(item, media, false, true);
    }

    @Override public void onOpenTikTok(FeedListItem item, FeedListMedia media) {
        if (media == null || TextUtils.isEmpty(media.external_url)) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(media.external_url));
            startActivity(intent);
        } catch (Throwable error) {
            toast(getString(R.string.feedlist_tiktok_failed));
        }
    }

    private void notifyInteraction(String feedId) {
        int index = indexOf(current.items, feedId);
        if (index >= 0) adapter.notifyInteractionChanged(index);
    }

    private int indexOf(ArrayList<FeedListItem> items, String feedId) {
        for (int i = 0; i < items.size(); i++) {
            FeedListItem item = items.get(i);
            if (item != null && TextUtils.equals(feedId, item.feed_id)) return i;
        }
        return -1;
    }

    private void resolveTikTokMetadata(FeedListItem item, FeedListMedia media,
                                       boolean openAfterResolve, boolean forceFreshCover) {
        if (media == null || isUnavailable()) return;
        String sourceUrl = media.tiktokSourceUrl();
        if (TextUtils.isEmpty(sourceUrl)) {
            if (openAfterResolve) toast(getString(R.string.feedlist_tiktok_failed));
            return;
        }

        if (openAfterResolve) tiktokOpenAfterResolve.add(sourceUrl);
        if (tiktokResolveInFlight.contains(sourceUrl)) return;

        long now = System.currentTimeMillis();
        Long failedAt = tiktokResolveFailedAt.get(sourceUrl);
        Long resolvedAt = tiktokResolvedAt.get(sourceUrl);
        if (forceFreshCover && failedAt != null && now - failedAt < TIKTOK_COVER_RETRY_COOLDOWN_MS) {
            return;
        }
        if (!forceFreshCover && !openAfterResolve
                && ((failedAt != null && now - failedAt < TIKTOK_RETRY_COOLDOWN_MS)
                || (resolvedAt != null && now - resolvedAt < TIKTOK_RETRY_COOLDOWN_MS))) {
            return;
        }

        String feedId = item == null ? "" : item.feed_id;
        tiktokResolveInFlight.add(sourceUrl);

        // A failed/expired CDN thumbnail must bypass the app server cache. Ask TikTok oEmbed
        // directly for a new signed thumbnail before trying the normal server preview path.
        if (forceFreshCover) {
            tiktokResolvedAt.remove(sourceUrl);
            boolean shouldOpen = tiktokOpenAfterResolve.remove(sourceUrl);
            resolveTikTokWithOEmbed(feedId, sourceUrl, null, shouldOpen);
            return;
        }

        FeedListModel.getInstance().tiktokPreview(sourceUrl, new IRequestResultListener<>() {
            @Override public void onSuccess(FeedListTikTokPreview serverResult) {
                boolean shouldOpen = tiktokOpenAfterResolve.remove(sourceUrl);
                if (isUnavailable()) {
                    tiktokResolveInFlight.remove(sourceUrl);
                    return;
                }
                if (serverResult != null && serverResult.hasPlayableVideo()
                        && !TextUtils.isEmpty(serverResult.bestCoverUrl())) {
                    finishTikTokResolveSuccess(feedId, sourceUrl, serverResult, shouldOpen);
                    return;
                }
                resolveTikTokWithOEmbed(feedId, sourceUrl, serverResult, shouldOpen);
            }

            @Override public void onFail(int code, String msg) {
                boolean shouldOpen = tiktokOpenAfterResolve.remove(sourceUrl);
                if (isUnavailable()) {
                    tiktokResolveInFlight.remove(sourceUrl);
                    return;
                }
                resolveTikTokWithOEmbed(feedId, sourceUrl, null, shouldOpen);
            }
        });
    }

    private void resolveTikTokWithOEmbed(String feedId, String sourceUrl,
                                         FeedListTikTokPreview serverResult, boolean shouldOpen) {
        TikTokMetadataResolver.resolve(sourceUrl, new TikTokMetadataResolver.Callback() {
            @Override public void onSuccess(FeedListTikTokPreview oEmbedResult) {
                if (isUnavailable()) {
                    tiktokResolveInFlight.remove(sourceUrl);
                    return;
                }
                FeedListTikTokPreview merged = mergeTikTokPreview(serverResult, oEmbedResult, sourceUrl);
                if (merged == null || !merged.hasPlayableVideo()
                        || (!shouldOpen && TextUtils.isEmpty(merged.bestCoverUrl()))) {
                    finishTikTokResolveFailure(sourceUrl, shouldOpen, "");
                    return;
                }
                finishTikTokResolveSuccess(feedId, sourceUrl, merged, shouldOpen);
            }

            @Override public void onFail(String message) {
                if (serverResult != null && serverResult.hasPlayableVideo()
                        && (shouldOpen || !TextUtils.isEmpty(serverResult.bestCoverUrl()))) {
                    finishTikTokResolveSuccess(feedId, sourceUrl,
                            mergeTikTokPreview(serverResult, null, sourceUrl), shouldOpen);
                    return;
                }
                finishTikTokResolveFailure(sourceUrl, shouldOpen, message);
            }
        });
    }

    private FeedListTikTokPreview mergeTikTokPreview(FeedListTikTokPreview primary,
                                                      FeedListTikTokPreview fallback,
                                                      String sourceUrl) {
        if (primary == null && fallback == null) return null;
        FeedListTikTokPreview out = new FeedListTikTokPreview();
        out.provider = firstNonEmpty(primary == null ? "" : primary.bestProvider(),
                fallback == null ? "" : fallback.bestProvider(), "tiktok");
        out.video_id = firstNonEmpty(primary == null ? "" : primary.bestVideoId(),
                fallback == null ? "" : fallback.bestVideoId());
        out.url = firstNonEmpty(primary == null ? "" : primary.bestUrl(),
                fallback == null ? "" : fallback.bestUrl(), sourceUrl);
        out.cover_url = firstNonEmpty(fallback == null ? "" : fallback.bestCoverUrl(),
                primary == null ? "" : primary.bestCoverUrl());
        out.title = firstNonEmpty(primary == null ? "" : primary.title,
                fallback == null ? "" : fallback.title);
        out.author_name = firstNonEmpty(primary == null ? "" : primary.author_name,
                fallback == null ? "" : fallback.author_name);
        return out;
    }

    private void finishTikTokResolveSuccess(String feedId, String sourceUrl,
                                            FeedListTikTokPreview result, boolean shouldOpen) {
        tiktokResolveInFlight.remove(sourceUrl);
        tiktokResolveFailedAt.remove(sourceUrl);
        tiktokResolvedAt.put(sourceUrl, System.currentTimeMillis());
        updateTikTokMetadata(feedId, sourceUrl, result);
        persistStates();
        refreshTikTokRows(feedId, sourceUrl);
        if (shouldOpen) {
            TikTokEmbedActivity.open(this, result.bestVideoId(),
                    firstNonEmpty(result.bestUrl(), sourceUrl), result.bestCoverUrl());
        }
    }

    private void finishTikTokResolveFailure(String sourceUrl, boolean shouldOpen, String message) {
        tiktokResolveInFlight.remove(sourceUrl);
        tiktokResolveFailedAt.put(sourceUrl, System.currentTimeMillis());
        if (isUnavailable() || !shouldOpen) return;
        toast(TextUtils.isEmpty(message) ? getString(R.string.feedlist_tiktok_failed) : message);
    }

    private void updateTikTokMetadata(String feedId, String oldUrl, FeedListTikTokPreview result) {
        String resolvedId = result == null ? "" : result.bestVideoId();
        String resolvedUrl = result == null ? "" : result.bestUrl();
        String resolvedCover = result == null ? "" : result.bestCoverUrl();
        String resolvedProvider = result == null ? "tiktok" : result.bestProvider();
        for (TimelineState state : new TimelineState[]{latest, following}) {
            for (FeedListItem candidate : state.items) {
                if (candidate == null) continue;
                boolean sameFeed = !TextUtils.isEmpty(feedId) && TextUtils.equals(feedId, candidate.feed_id);
                for (FeedListMedia value : candidate.safeMedia()) {
                    if (value == null || !value.isTikTok()) continue;
                    boolean sameUrl = !TextUtils.isEmpty(oldUrl)
                            && (TextUtils.equals(oldUrl, value.external_url)
                            || TextUtils.equals(oldUrl, value.tiktokSourceUrl()));
                    if (!sameFeed && !sameUrl) continue;
                    value.external_provider = firstNonEmpty(resolvedProvider, "tiktok");
                    if (!TextUtils.isEmpty(resolvedId)) value.external_id = resolvedId;
                    if (!TextUtils.isEmpty(resolvedUrl)) value.external_url = resolvedUrl;
                    if (!TextUtils.isEmpty(resolvedCover)) value.cover_url = resolvedCover;
                    if (result != null && !TextUtils.isEmpty(result.title)) value.external_title = result.title;
                    if (result != null && !TextUtils.isEmpty(result.author_name)) value.external_author = result.author_name;
                }
            }
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !TextUtils.isEmpty(value.trim())) return value.trim();
        }
        return "";
    }

    private void refreshTikTokRows(String feedId, String oldUrl) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            FeedListItem candidate = adapter.getItemAt(i);
            if (candidate == null) continue;
            boolean sameFeed = !TextUtils.isEmpty(feedId) && TextUtils.equals(feedId, candidate.feed_id);
            FeedListMedia media = candidate.firstMedia();
            boolean sameUrl = media != null && !TextUtils.isEmpty(oldUrl) && TextUtils.equals(oldUrl, media.external_url);
            if (sameFeed || sameUrl) adapter.notifyItemChanged(i);
        }
    }

    private void removeFeed(ArrayList<FeedListItem> items, String feedId) {
        for (int i = items.size() - 1; i >= 0; i--) {
            FeedListItem item = items.get(i);
            if (item == null || TextUtils.equals(feedId, item.feed_id)) items.remove(i);
        }
    }

    private void removeUser(ArrayList<FeedListItem> items, String uid) {
        for (int i = items.size() - 1; i >= 0; i--) {
            FeedListItem item = items.get(i);
            if (item == null || TextUtils.equals(uid, item.authorUid())) items.remove(i);
        }
    }

    private void syncFollow(String uid, boolean followed) {
        for (TimelineState state : new TimelineState[]{latest, following}) {
            for (FeedListItem candidate : state.items) {
                if (candidate == null || !TextUtils.equals(uid, candidate.authorUid()) || candidate.user == null) continue;
                candidate.user.follow = followed ? 1 : 0;
            }
        }
    }

    private void syncInteraction(String feedId, Integer liked, Integer likeCount, Integer commentCount, Integer shareCount) {
        for (TimelineState state : new TimelineState[]{latest, following}) {
            for (FeedListItem candidate : state.items) {
                if (candidate == null || !TextUtils.equals(feedId, candidate.feed_id)) continue;
                if (liked != null) candidate.liked = liked;
                if (likeCount != null) candidate.like_count = likeCount;
                if (commentCount != null) candidate.comment_count = commentCount;
                if (shareCount != null) candidate.share_count = shareCount;
            }
        }
    }

    private void persistStates() {
        // Do not overwrite an untouched tab's existing disk cache with the default empty state.
        if (latest.cacheLoaded) {
            FeedListCache.save(this, latest.mode, latest.items, latest.cursor, latest.hasMore, currentServerTime(latest));
        }
        if (following.cacheLoaded) {
            FeedListCache.save(this, following.mode, following.items, following.cursor, following.hasMore, currentServerTime(following));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (tiktokPlaybackPreloader == null && wkVBinding != null) {
            }
        mainHandler.removeCallbacks(publishRefreshTask);
        if (FeedListPublishActivity.consumePublishSuccess(this)) {
            mainHandler.post(publishRefreshTask);
        } else {
            mainHandler.post(this::preloadTikTokCovers);
        }
    }

    @Override protected void onPause() {
        resumed = false;
        mainHandler.removeCallbacks(publishRefreshTask);
        if (wkVBinding != null) {
            try {
                wkVBinding.recyclerView.stopScroll();
                wkVBinding.refreshLayout.finishRefresh(false);
            } catch (Throwable ignored) {
            }
        }
        super.onPause();
    }

    private void refreshAfterPublish() {
        if (!resumed || isUnavailable()) return;
        current = latest;
        latest.firstVisiblePosition = 0;
        latest.firstVisibleOffset = 0;
        latest.hasScrollState = true;
        updateTabs();
        render(true);
        requestPage(latest, true);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PUBLISH && resultCode == RESULT_OK) {
            FeedListPublishActivity.consumePublishSuccess(this);
            mainHandler.removeCallbacks(publishRefreshTask);
            mainHandler.post(publishRefreshTask);
        }
    }

    @Override protected void onDestroy() {
        saveScrollState(current);
        destroyed = true;
        resumed = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (adapter != null) adapter.release();
        if (wkVBinding != null) {
            try {
                if (timelineScrollListener != null) {
                    wkVBinding.recyclerView.removeOnScrollListener(timelineScrollListener);
                }
                wkVBinding.recyclerView.stopScroll();
                wkVBinding.recyclerView.setAdapter(null);
            } catch (Throwable ignored) {
            }
        }
        timelineScrollListener = null;
        adapter = null;
        layoutManager = null;
        super.onDestroy();
    }


    private boolean isUnavailable() {
        return destroyed || isFinishing() || isDestroyed() || wkVBinding == null;
    }

    private static long normalizeEpochMillis(long value) {
        if (value <= 0) return 0L;
        return value < 10_000_000_000L ? value * 1000L : value;
    }

    private long currentServerTime(TimelineState state) {
        if (state == null || state.serverTime <= 0) return 0L;
        long anchor = state.serverTimeLocalAt > 0 ? state.serverTimeLocalAt : System.currentTimeMillis();
        return state.serverTime + Math.max(0L, System.currentTimeMillis() - anchor);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private static final class TimelineState {
        final String mode;
        final ArrayList<FeedListItem> items = new ArrayList<>();
        String cursor = "";
        boolean hasMore = true;
        boolean loading;
        boolean pendingRefresh;
        boolean cacheRequested;
        boolean cacheLoaded;
        boolean networkRequested;
        long serverTime;
        long serverTimeLocalAt;
        int firstVisiblePosition;
        int firstVisibleOffset;
        boolean hasScrollState;
        TimelineState(String mode) { this.mode = mode; }
    }
}
