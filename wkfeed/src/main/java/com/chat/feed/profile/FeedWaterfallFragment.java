package com.chat.feed.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
    private Runnable hostCollapseProfileAction;
    private Runnable hostExpandProfileAction;
    private float gestureDownY;
    private int gestureTouchSlop;
    private boolean gestureDirectionHandled;
    private static final long TIKTOK_RETRY_COOLDOWN_MS = 60_000L;
    private static final long TIKTOK_RESOLVED_COOLDOWN_MS = 5 * 60_000L;
    private final Set<String> tikTokResolveInFlight = new HashSet<>();
    private final Map<String, Long> tikTokResolveFailedAt = new HashMap<>();
    private final Map<String, Long> tikTokResolvedAt = new HashMap<>();

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
        recyclerView = findFirstViewByType(view, RecyclerView.class);
        loadingView = findFirstViewByType(view, ProgressBar.class);
        stateTv = findFirstViewByType(view, TextView.class);
        // Compatibility with older/current layout variants: only RecyclerView is required.
        // Loading and state views are optional, so a partially applied resource patch cannot
        // crash the whole profile page at runtime.
        if (recyclerView == null) {
            return;
        }
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
        adapter = new FeedWaterfallAdapter(this::openDetail);
        adapter.setOnTikTokCoverListener(this::resolveTikTokCover);
        recyclerView.setAdapter(adapter);
        gestureTouchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        gestureDownY = event.getY();
                        gestureDirectionHandled = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getY() - gestureDownY;
                        if (!gestureDirectionHandled && Math.abs(deltaY) >= gestureTouchSlop) {
                            gestureDirectionHandled = true;
                            if (deltaY < 0f) {
                                notifyHostCollapseProfile();
                            } else if (!rv.canScrollVertically(-1)) {
                                notifyHostExpandProfile();
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        gestureDirectionHandled = false;
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0) {
                    // Some OEM builds do not reliably forward the nested scroll from a
                    // RecyclerView hosted inside a Fragment container to AppBarLayout.
                    // Explicitly request collapse so the profile sheet can never remain fixed.
                    notifyHostCollapseProfile();
                } else if (dy < 0 && !rv.canScrollVertically(-1)) {
                    notifyHostExpandProfile();
                }

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
        if (stateTv != null) {
            stateTv.setOnClickListener(v ->
                    loadMore(adapter == null || adapter.getItemCount() == 0));
        }
        loadMore(true);
    }


    @Nullable
    private static <T extends View> T findFirstViewByType(@NonNull View root,
                                                          @NonNull Class<T> viewClass) {
        if (viewClass.isInstance(root)) return viewClass.cast(root);
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            T result = findFirstViewByType(group.getChildAt(i), viewClass);
            if (result != null) return result;
        }
        return null;
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

    /**
     * Host callbacks are java.lang.Runnable on purpose: wkpartner can register them through
     * reflection without creating a Gradle dependency from wkfeed back to wkpartner.
     */
    public void setProfileHeaderActions(@Nullable Runnable collapseAction,
                                        @Nullable Runnable expandAction) {
        hostCollapseProfileAction = collapseAction;
        hostExpandProfileAction = expandAction;
    }

    private void notifyHostCollapseProfile() {
        Runnable action = hostCollapseProfileAction;
        if (action != null) action.run();
    }

    private void notifyHostExpandProfile() {
        Runnable action = hostExpandProfileAction;
        if (action != null) action.run();
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
                        if (loadingView != null) loadingView.setVisibility(View.GONE);
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
                        if (loadingView != null) loadingView.setVisibility(View.GONE);
                        updateState(true);
                        requestWaterfallRelayout();
                    }
                });
    }

    private boolean isCurrentView(int generation) {
        return generation == viewGeneration
                && isAdded()
                && recyclerView != null
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

    private void resolveTikTokCover(FeedBean item, boolean forceFresh) {
        if (item == null || adapter == null || !isAdded()) return;
        FeedMedia media = item.firstMedia();
        if (media == null || !media.isTikTok()) return;
        String sourceUrl = media.tiktokSourceUrl();
        if (TextUtils.isEmpty(sourceUrl)) return;
        if (tikTokResolveInFlight.contains(sourceUrl)) return;

        long now = System.currentTimeMillis();
        Long failedAt = tikTokResolveFailedAt.get(sourceUrl);
        Long resolvedAt = tikTokResolvedAt.get(sourceUrl);
        if (failedAt != null && now - failedAt < TIKTOK_RETRY_COOLDOWN_MS) return;
        if (!forceFresh && resolvedAt != null
                && now - resolvedAt < TIKTOK_RESOLVED_COOLDOWN_MS) return;

        tikTokResolveInFlight.add(sourceUrl);
        final int requestGeneration = viewGeneration;
        final String feedKey = item.stableKey();

        // A signed TikTok CDN thumbnail can expire even when the source video still works.
        // When Glide reports a failed/expired cover, bypass any server cache and ask official
        // oEmbed for a fresh thumbnail first.
        if (forceFresh) {
            resolveTikTokWithOEmbed(item, feedKey, sourceUrl, null, requestGeneration);
            return;
        }

        FeedModel.getInstance().tiktokPreview(sourceUrl,
                new IRequestResultListener<FeedTikTokPreview>() {
                    @Override
                    public void onSuccess(FeedTikTokPreview serverResult) {
                        if (!isCurrentView(requestGeneration)) {
                            tikTokResolveInFlight.remove(sourceUrl);
                            return;
                        }
                        if (serverResult != null
                                && !TextUtils.isEmpty(serverResult.bestCoverUrl())) {
                            finishTikTokCoverSuccess(item, feedKey, sourceUrl,
                                    serverResult, requestGeneration);
                            return;
                        }
                        resolveTikTokWithOEmbed(item, feedKey, sourceUrl,
                                serverResult, requestGeneration);
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        if (!isCurrentView(requestGeneration)) {
                            tikTokResolveInFlight.remove(sourceUrl);
                            return;
                        }
                        resolveTikTokWithOEmbed(item, feedKey, sourceUrl,
                                null, requestGeneration);
                    }
                });
    }

    private void resolveTikTokWithOEmbed(FeedBean item, String feedKey, String sourceUrl,
                                         @Nullable FeedTikTokPreview serverResult,
                                         int requestGeneration) {
        TikTokCoverResolver.resolve(sourceUrl, new TikTokCoverResolver.Callback() {
            @Override
            public void onSuccess(FeedTikTokPreview oEmbedResult) {
                if (!isCurrentView(requestGeneration)) {
                    tikTokResolveInFlight.remove(sourceUrl);
                    return;
                }
                FeedTikTokPreview merged = mergeTikTokPreview(serverResult, oEmbedResult, sourceUrl);
                if (merged == null || TextUtils.isEmpty(merged.bestCoverUrl())) {
                    finishTikTokCoverFailure(sourceUrl);
                    return;
                }
                finishTikTokCoverSuccess(item, feedKey, sourceUrl,
                        merged, requestGeneration);
            }

            @Override
            public void onFail(String message) {
                if (serverResult != null && !TextUtils.isEmpty(serverResult.bestCoverUrl())) {
                    finishTikTokCoverSuccess(item, feedKey, sourceUrl,
                            mergeTikTokPreview(serverResult, null, sourceUrl), requestGeneration);
                    return;
                }
                finishTikTokCoverFailure(sourceUrl);
            }
        });
    }

    @Nullable
    private FeedTikTokPreview mergeTikTokPreview(@Nullable FeedTikTokPreview primary,
                                                  @Nullable FeedTikTokPreview fallback,
                                                  String sourceUrl) {
        if (primary == null && fallback == null) return null;
        FeedTikTokPreview out = new FeedTikTokPreview();
        out.provider = firstNonEmpty(primary == null ? "" : primary.bestProvider(),
                fallback == null ? "" : fallback.bestProvider(), "tiktok");
        out.video_id = firstNonEmpty(primary == null ? "" : primary.bestVideoId(),
                fallback == null ? "" : fallback.bestVideoId());
        out.url = firstNonEmpty(primary == null ? "" : primary.bestUrl(),
                fallback == null ? "" : fallback.bestUrl(), sourceUrl);
        // Prefer oEmbed's newly signed thumbnail over a possibly stale server cache.
        out.cover_url = firstNonEmpty(fallback == null ? "" : fallback.bestCoverUrl(),
                primary == null ? "" : primary.bestCoverUrl());
        out.title = firstNonEmpty(primary == null ? "" : primary.title,
                fallback == null ? "" : fallback.title);
        out.author_name = firstNonEmpty(primary == null ? "" : primary.author_name,
                fallback == null ? "" : fallback.author_name);
        out.thumbnail_width = fallback != null && fallback.thumbnail_width > 0
                ? fallback.thumbnail_width : (primary == null ? 0 : primary.thumbnail_width);
        out.thumbnail_height = fallback != null && fallback.thumbnail_height > 0
                ? fallback.thumbnail_height : (primary == null ? 0 : primary.thumbnail_height);
        return out;
    }

    private void finishTikTokCoverSuccess(FeedBean item, String feedKey, String sourceUrl,
                                           FeedTikTokPreview result, int requestGeneration) {
        tikTokResolveInFlight.remove(sourceUrl);
        tikTokResolveFailedAt.remove(sourceUrl);
        tikTokResolvedAt.put(sourceUrl, System.currentTimeMillis());
        if (!isCurrentView(requestGeneration) || item == null || result == null) return;

        FeedMedia media = item.firstMedia();
        if (media == null) return;
        String cover = result.bestCoverUrl();
        if (!TextUtils.isEmpty(result.bestProvider())) {
            media.external_provider = result.bestProvider();
        }
        if (!TextUtils.isEmpty(result.bestVideoId())) {
            media.external_id = result.bestVideoId();
        }
        if (!TextUtils.isEmpty(result.bestUrl())) {
            media.external_url = result.bestUrl();
        }
        if (!TextUtils.isEmpty(cover)) {
            media.cover_url = cover;
            media.thumb_url = cover;
        }
        if (!TextUtils.isEmpty(result.title)) media.external_title = result.title;
        if (!TextUtils.isEmpty(result.author_name)) media.external_author = result.author_name;
        if (result.thumbnail_width > 0 && result.thumbnail_height > 0) {
            media.width = result.thumbnail_width;
            media.height = result.thumbnail_height;
        }
        adapter.notifyItemChangedByKey(feedKey);
    }

    private void finishTikTokCoverFailure(String sourceUrl) {
        tikTokResolveInFlight.remove(sourceUrl);
        tikTokResolveFailedAt.put(sourceUrl, System.currentTimeMillis());
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !TextUtils.isEmpty(value.trim())) return value.trim();
        }
        return "";
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
        if (adapter != null) adapter.setOnTikTokCoverListener(null);
        if (recyclerView != null) {
            recyclerView.clearOnScrollListeners();
            recyclerView.setAdapter(null);
        }
        tikTokResolveInFlight.clear();
        tikTokResolveFailedAt.clear();
        tikTokResolvedAt.clear();
        adapter = null;
        hostCollapseProfileAction = null;
        hostExpandProfileAction = null;
        recyclerView = null;
        loadingView = null;
        stateTv = null;
        super.onDestroyView();
    }
}
