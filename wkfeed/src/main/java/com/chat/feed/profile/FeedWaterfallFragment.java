package com.chat.feed.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chat.base.net.IRequestResultListener;
import com.chat.feed.FeedModel;
import com.chat.feed.R;
import com.chat.feed.browse.FeedBrowseActivity;
import com.chat.feed.model.FeedBean;
import com.chat.feed.model.FeedListResponse;
import com.chat.feed.model.FeedMedia;
import com.chat.feed.model.FeedTikTokPreview;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FeedWaterfallFragment extends Fragment {
    private static final String ARG_UID = "uid";
    private static final long TIKTOK_RETRY_COOLDOWN_MS = 60_000L;
    private String uid;
    private String cursor = "";
    private boolean loading;
    private boolean hasMore = true;
    private int skippedPageCount;
    private int viewGeneration;
    private FeedWaterfallAdapter adapter;
    private RecyclerView recyclerView;
    private ProgressBar loadingView;
    private TextView stateTv;
    private int hostTopInset;
    private int hostBottomInset;
    private int baseTopPadding;
    private int baseBottomPadding;
    private final Set<String> tiktokCoverResolving = new HashSet<>();
    private final Map<String, Long> tiktokCoverFailedAt = new HashMap<>();

    public static FeedWaterfallFragment newInstance(String uid) {
        FeedWaterfallFragment fragment = new FeedWaterfallFragment();
        Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed_waterfall, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewGeneration++;
        loading = false;
        cursor = "";
        hasMore = true;
        skippedPageCount = 0;
        uid = getArguments() == null ? "" : getArguments().getString(ARG_UID, "");
        recyclerView = view.findViewById(R.id.feedWaterfallRecyclerView);
        loadingView = view.findViewById(R.id.feedWaterfallLoading);
        stateTv = view.findViewById(R.id.feedWaterfallStateTv);
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(
                2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setNestedScrollingEnabled(true);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setHasFixedSize(false);
        baseTopPadding = recyclerView.getPaddingTop();
        baseBottomPadding = recyclerView.getPaddingBottom();
        applyHostInsets();
        if (recyclerView.getItemAnimator() != null) recyclerView.getItemAnimator().setChangeDuration(0);
        adapter = new FeedWaterfallAdapter(this::openDetail, this::resolveTikTokCover);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || adapter == null) return;
                RecyclerView.LayoutManager manager = rv.getLayoutManager();
                if (!(manager instanceof StaggeredGridLayoutManager)) return;
                int[] positions = ((StaggeredGridLayoutManager) manager)
                        .findLastVisibleItemPositions(null);
                int last = -1;
                for (int position : positions) last = Math.max(last, position);
                if (last >= adapter.getItemCount() - 5) loadMore(false);
            }
        });
        stateTv.setOnClickListener(v -> loadMore(adapter == null || adapter.getItemCount() == 0));
        loadMore(true);
    }

    /** Retained for compatibility with older profile hosts. */
    public void loadMoreIfNeeded() {
        loadMore(false);
    }

    /**
     * Insets supplied by the profile Activity for its overlay top bars and bottom action button.
     * Values are pixels because they are calculated from the real laid-out overlay positions.
     */
    public void setHostInsets(int topInset, int bottomInset) {
        hostTopInset = Math.max(0, topInset);
        hostBottomInset = Math.max(0, bottomInset);
        applyHostInsets();
    }

    public void scrollToTop() {
        if (recyclerView != null) recyclerView.scrollToPosition(0);
    }

    private void applyHostInsets() {
        if (recyclerView == null) return;
        int left = recyclerView.getPaddingLeft();
        int right = recyclerView.getPaddingRight();
        int top = baseTopPadding + hostTopInset;
        int bottom = Math.max(baseBottomPadding, hostBottomInset);
        if (recyclerView.getPaddingTop() == top
                && recyclerView.getPaddingBottom() == bottom) return;
        recyclerView.setPadding(left, top, right, bottom);
    }

    private void loadMore(boolean first) {
        if (loading || TextUtils.isEmpty(uid) || (!hasMore && !first) || adapter == null) return;
        loading = true;
        final int requestGeneration = viewGeneration;
        final String requestCursor = first ? "" : cursor;
        if (first && loadingView != null && stateTv != null) {
            loadingView.setVisibility(View.VISIBLE);
            stateTv.setVisibility(View.GONE);
        }
        FeedModel.getInstance().userFeeds(uid, requestCursor,
                new IRequestResultListener<FeedListResponse>() {
                    @Override
                    public void onSuccess(FeedListResponse result) {
                        if (!isCurrentView(requestGeneration)) return;
                        loading = false;
                        loadingView.setVisibility(View.GONE);
                        if (result == null) {
                            updateState(false);
                            requestWaterfallRelayout();
                            return;
                        }

                        java.util.List<FeedBean> page = result.safeList();
                        String nextCursor = result.cursor == null ? "" : result.cursor;
                        boolean cursorAdvanced = !TextUtils.isEmpty(nextCursor)
                                && !TextUtils.equals(nextCursor, requestCursor);

                        int before = adapter.getItemCount();
                        if (first) {
                            adapter.submitList(page);
                            skippedPageCount = 0;
                        } else {
                            adapter.append(page);
                        }
                        int inserted = Math.max(0, adapter.getItemCount() - (first ? 0 : before));

                        cursor = nextCursor;
                        hasMore = result.has_more == 1 && cursorAdvanced;
                        if (inserted == 0 && result.has_more == 1) {
                            skippedPageCount++;
                            if (skippedPageCount >= 2) hasMore = false;
                        } else if (inserted > 0) {
                            skippedPageCount = 0;
                        }

                        updateState(false);
                        requestWaterfallRelayout();
                        maybeFillViewport();

                        // A filtered or duplicate page may contain no new cards while still
                        // advancing the cursor. Skip at most one such page automatically so the
                        // personal profile does not appear to stop early.
                        if (hasMore && inserted == 0 && recyclerView != null) {
                            recyclerView.post(() -> loadMore(false));
                        }
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        if (!isCurrentView(requestGeneration)) return;
                        loading = false;
                        loadingView.setVisibility(View.GONE);
                        updateState(true);
                        requestWaterfallRelayout();
                    }
                });
    }

    private boolean isCurrentView(int generation) {
        return generation == viewGeneration
                && isAdded()
                && recyclerView != null
                && loadingView != null
                && stateTv != null
                && adapter != null;
    }

    private void requestWaterfallRelayout() {
        RecyclerView currentRecycler = recyclerView;
        if (currentRecycler == null) return;
        currentRecycler.post(() -> {
            if (recyclerView != currentRecycler) return;
            currentRecycler.requestLayout();
            if (currentRecycler.getParent() instanceof View) {
                ((View) currentRecycler.getParent()).requestLayout();
            }
        });
    }

    private void maybeFillViewport() {
        RecyclerView current = recyclerView;
        if (current == null || !hasMore || loading || adapter == null) return;
        current.post(() -> {
            if (recyclerView != current || loading || !hasMore || adapter == null) return;
            if (adapter.getItemCount() > 0 && !current.canScrollVertically(1)) {
                loadMore(false);
            }
        });
    }

    private void updateState(boolean error) {
        if (adapter == null || stateTv == null) return;
        if (adapter.getItemCount() > 0) {
            stateTv.setVisibility(View.GONE);
            return;
        }
        stateTv.setVisibility(View.VISIBLE);
        stateTv.setText(error ? R.string.feed_retry : R.string.feed_empty_posts);
    }


    private void resolveTikTokCover(FeedBean item, FeedMedia media, boolean forceFresh) {
        if (item == null || media == null || !media.isTikTok() || !isAdded()) return;
        String sourceUrl = media.tiktokSourceUrl();
        if (TextUtils.isEmpty(sourceUrl)) return;

        String key = sourceUrl;
        long now = System.currentTimeMillis();
        Long failedAt = tiktokCoverFailedAt.get(key);
        if (failedAt != null && now - failedAt < TIKTOK_RETRY_COOLDOWN_MS) return;
        if (!tiktokCoverResolving.add(key)) return;

        int generation = viewGeneration;
        if (forceFresh) {
            resolveTikTokCoverDirect(item, media, key, sourceUrl, generation);
            return;
        }

        FeedModel.getInstance().tiktokPreview(sourceUrl,
                new IRequestResultListener<FeedTikTokPreview>() {
                    @Override
                    public void onSuccess(FeedTikTokPreview result) {
                        if (!isCurrentView(generation)) {
                            tiktokCoverResolving.remove(key);
                            return;
                        }
                        if (result != null && !TextUtils.isEmpty(result.bestCoverUrl())) {
                            finishTikTokCoverSuccess(item, media, key, sourceUrl, result, generation);
                        } else {
                            resolveTikTokCoverDirect(item, media, key, sourceUrl, generation);
                        }
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        if (!isCurrentView(generation)) {
                            tiktokCoverResolving.remove(key);
                            return;
                        }
                        resolveTikTokCoverDirect(item, media, key, sourceUrl, generation);
                    }
                });
    }

    private void resolveTikTokCoverDirect(FeedBean item, FeedMedia media, String key,
                                          String sourceUrl, int generation) {
        TikTokCoverResolver.resolve(sourceUrl, new TikTokCoverResolver.Callback() {
            @Override
            public void onSuccess(FeedTikTokPreview preview) {
                finishTikTokCoverSuccess(item, media, key, sourceUrl, preview, generation);
            }

            @Override
            public void onFail(String message) {
                finishTikTokCoverFailure(key);
            }
        });
    }

    private void finishTikTokCoverSuccess(FeedBean item, FeedMedia media, String key,
                                          String sourceUrl, FeedTikTokPreview preview,
                                          int generation) {
        tiktokCoverResolving.remove(key);
        if (!isCurrentView(generation) || preview == null) return;
        String coverUrl = preview.bestCoverUrl();
        if (TextUtils.isEmpty(coverUrl)) {
            finishTikTokCoverFailure(key);
            return;
        }

        media.cover_url = coverUrl;
        String videoId = preview.bestVideoId();
        String resolvedUrl = preview.bestUrl();
        if (!TextUtils.isEmpty(videoId)) media.external_id = videoId;
        if (!TextUtils.isEmpty(resolvedUrl)) media.external_url = resolvedUrl;
        else if (TextUtils.isEmpty(media.external_url)) media.external_url = sourceUrl;
        media.external_provider = "tiktok";
        if (!TextUtils.isEmpty(preview.title)) media.external_title = preview.title;
        if (!TextUtils.isEmpty(preview.author_name)) media.external_author = preview.author_name;

        tiktokCoverFailedAt.remove(key);
        notifyFeedChanged(item.stableKey());
    }

    private void finishTikTokCoverFailure(String key) {
        tiktokCoverResolving.remove(key);
        tiktokCoverFailedAt.put(key, System.currentTimeMillis());
    }

    private void notifyFeedChanged(String stableKey) {
        FeedWaterfallAdapter currentAdapter = adapter;
        if (currentAdapter == null || TextUtils.isEmpty(stableKey)) return;
        for (int i = 0; i < currentAdapter.getItemCount(); i++) {
            FeedBean candidate = currentAdapter.getItem(i);
            if (candidate != null && TextUtils.equals(stableKey, candidate.stableKey())) {
                currentAdapter.notifyItemChanged(i);
                return;
            }
        }
    }

    private void openDetail(FeedBean item, int position) {
        if (item == null || !isAdded()) return;
        if (item.isTikTok() && openTikTok(item.firstMedia())) return;
        Intent intent = new Intent(requireContext(), FeedBrowseActivity.class);
        intent.putExtra(FeedBrowseActivity.EXTRA_MODE, FeedBrowseActivity.MODE_PROFILE);
        intent.putExtra(FeedBrowseActivity.EXTRA_UID, uid);
        intent.putExtra(FeedBrowseActivity.EXTRA_START_FEED_ID, item.stableKey());
        intent.putExtra(FeedBrowseActivity.EXTRA_START_POSITION, position);
        startActivity(intent);
    }

    /** Avoids a wkfeed -> wkfeedlist Gradle dependency cycle while using the installed player. */
    private boolean openTikTok(@Nullable FeedMedia media) {
        if (media == null) return false;
        String videoId = media.tiktokVideoId();
        String sourceUrl = media.tiktokSourceUrl();
        if (TextUtils.isEmpty(videoId) && TextUtils.isEmpty(sourceUrl)) return false;
        try {
            Class<?> playerClass = Class.forName("com.chat.feedlist.TikTokEmbedActivity");
            Method open = playerClass.getMethod(
                    "open", Context.class, String.class, String.class, String.class);
            open.invoke(null, requireContext(), videoId, sourceUrl, media.tiktokCoverUrl());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onDestroyView() {
        viewGeneration++;
        loading = false;
        if (stateTv != null) stateTv.setOnClickListener(null);
        if (recyclerView != null) {
            recyclerView.clearOnScrollListeners();
            recyclerView.setAdapter(null);
        }
        tiktokCoverResolving.clear();
        tiktokCoverFailedAt.clear();
        adapter = null;
        recyclerView = null;
        loadingView = null;
        stateTv = null;
        super.onDestroyView();
    }
}
