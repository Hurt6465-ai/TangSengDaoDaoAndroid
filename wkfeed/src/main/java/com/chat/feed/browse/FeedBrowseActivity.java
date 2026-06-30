package com.chat.feed.browse;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.net.IRequestResultListener;
import com.chat.feed.FeedModel;
import com.chat.feed.FeedRoute;
import com.chat.feed.publish.FeedPublishActivity;
import com.chat.feed.R;
import com.chat.feed.model.FeedBean;
import com.chat.feed.model.FeedListResponse;
import com.chat.feed.player.FeedPlayerManager;

import java.util.HashSet;
import java.util.List;

public class FeedBrowseActivity extends FragmentActivity {
    private static final int REQ_PUBLISH = 1201;
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_START_POSITION = "start_position";
    public static final String EXTRA_START_FEED_ID = "start_feed_id";
    public static final String MODE_DISCOVER = FeedModel.MODE_DISCOVER;
    public static final String MODE_NEARBY = FeedModel.MODE_NEARBY;
    public static final String MODE_PROFILE = FeedModel.MODE_PROFILE;
    public static final String MODE_FOLLOWING = FeedModel.MODE_FOLLOWING;

    private ViewPager2 feedPager;
    private ProgressBar loadingView;
    private TextView stateTv;
    private FeedPagerAdapter adapter;
    private RecyclerView pagerRecyclerView;
    private final FeedPreloadManager preloadManager = new FeedPreloadManager();
    private boolean loading;
    private boolean hasMore = true;
    private boolean didApplyInitialPosition;
    private String cursor = "";
    private String mode = MODE_DISCOVER;
    private String uid = "";
    private String startFeedId = "";
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private FeedPublishButtonController publishButtonController;
    private TextView publishBtn;
    private View topTabs;
    private TextView recommendTab;
    private TextView followingTab;
    private int duplicatePageCount;
    private boolean destroyed;
    private final HashSet<String> seenKeys = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_browse);
        FeedPlayerManager.getInstance().prepareForUse();
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (TextUtils.isEmpty(mode)) mode = MODE_DISCOVER;
        uid = getIntent().getStringExtra(EXTRA_UID);
        startFeedId = getIntent().getStringExtra(EXTRA_START_FEED_ID);
        feedPager = findViewById(R.id.feedPager);
        loadingView = findViewById(R.id.feedLoading);
        stateTv = findViewById(R.id.feedStateTv);
        publishBtn = findViewById(R.id.feedPublishBtn);
        topTabs = findViewById(R.id.feedTopTabs);
        recommendTab = findViewById(R.id.feedRecommendTab);
        followingTab = findViewById(R.id.feedFollowingTab);
        bindTopTabs();
        if (MODE_PROFILE.equals(mode)) {
            publishBtn.setVisibility(View.GONE);
        } else {
            publishButtonController = new FeedPublishButtonController(publishBtn);
            publishBtn.setOnClickListener(v -> openPublishForResult());
            // 即使推荐接口为空/失败，也要显示发布入口。之前按钮默认 GONE，
            // 只有 ViewPager2 触发 onPageSelected 后才会显示，空列表时就永远看不到 +。
            publishButtonController.showNow();
        }
        adapter = new FeedPagerAdapter();
        feedPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        feedPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        feedPager.setOffscreenPageLimit(1);
        feedPager.setSaveEnabled(false);
        feedPager.setAdapter(adapter);
        View child = feedPager.getChildCount() > 0 ? feedPager.getChildAt(0) : null;
        if (child instanceof RecyclerView) {
            pagerRecyclerView = (RecyclerView) child;
            pagerRecyclerView.setItemViewCacheSize(2);
            pagerRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            if (pagerRecyclerView.getItemAnimator() != null) {
                pagerRecyclerView.getItemAnimator().setChangeDuration(0);
            }
        }
        stateTv.setOnClickListener(v -> {
            if (!loading && adapter.getItemCount() == 0) {
                resetAndLoad();
            }
        });
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (destroyed || isFinishing()) return;
                if (publishButtonController != null) publishButtonController.onPageSelected();
                adapter.setActivePosition(pagerRecyclerView, position);
                preloadManager.preloadAround(FeedBrowseActivity.this, adapter.getItems(), position);
                if (hasMore && position >= adapter.getItemCount() - 3) loadMore(false);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (publishButtonController != null) publishButtonController.onPageScrollStateChanged(state);
            }
        };
        feedPager.registerOnPageChangeCallback(pageChangeCallback);
        loadMore(true);
    }


    private void bindTopTabs() {
        if (topTabs == null || recommendTab == null || followingTab == null) return;
        if (MODE_PROFILE.equals(mode)) {
            topTabs.setVisibility(View.GONE);
            return;
        }
        topTabs.setVisibility(View.VISIBLE);
        updateTopTabState(MODE_FOLLOWING.equals(mode));
        recommendTab.setOnClickListener(v -> {
            if (!MODE_DISCOVER.equals(mode)) {
                mode = MODE_DISCOVER;
                updateTopTabState(false);
                resetAndLoad();
            } else {
                updateTopTabState(false);
            }
        });
        followingTab.setOnClickListener(v -> {
            if (!MODE_FOLLOWING.equals(mode)) {
                mode = MODE_FOLLOWING;
                updateTopTabState(true);
                resetAndLoad();
            } else {
                updateTopTabState(true);
            }
        });
    }

    private void updateTopTabState(boolean followingSelected) {
        if (recommendTab == null || followingTab == null) return;
        recommendTab.setTextColor(followingSelected ? 0x99FFFFFF : 0xFFFFFFFF);
        recommendTab.setTextSize(followingSelected ? 16f : 17f);
        followingTab.setTextColor(followingSelected ? 0xFFFFFFFF : 0x99FFFFFF);
        followingTab.setTextSize(followingSelected ? 17f : 16f);
    }

    private void openPublishForResult() {
        Intent intent = new Intent(this, FeedPublishActivity.class);
        startActivityForResult(intent, REQ_PUBLISH);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PUBLISH && resultCode == RESULT_OK) {
            resetAndLoad();
        }
    }

    private void resetAndLoad() {
        cursor = "";
        hasMore = true;
        didApplyInitialPosition = false;
        duplicatePageCount = 0;
        seenKeys.clear();
        adapter.clear();
        loadMore(true);
    }

    private void loadMore(boolean first) {
        if (destroyed || loading || (!hasMore && !first)) return;
        loading = true;
        if (first && adapter.getItemCount() == 0) showLoading();
        IRequestResultListener<FeedListResponse> listener = new IRequestResultListener<FeedListResponse>() {
            @Override
            public void onSuccess(FeedListResponse result) {
                if (destroyed || isFinishing()) return;
                loading = false;
                hideLoading();
                if (result == null) {
                    showEmptyOrKeep(getString(R.string.feed_retry));
                    return;
                }
                List<FeedBean> list = result.safeList();
                String oldCursor = cursor;
                cursor = result.cursor == null ? "" : result.cursor;
                boolean cursorAdvanced = !TextUtils.isEmpty(cursor) && !TextUtils.equals(cursor, oldCursor);
                hasMore = result.has_more == 1 && (cursorAdvanced || TextUtils.isEmpty(oldCursor));
                int inserted = appendUnique(list);
                if (inserted == 0 && !list.isEmpty()) {
                    duplicatePageCount++;
                    if (duplicatePageCount >= 2) hasMore = false;
                } else {
                    duplicatePageCount = 0;
                }
                if (adapter.getItemCount() == 0) {
                    showEmptyOrKeep(getString(R.string.feed_empty_posts));
                    return;
                }
                stateTv.setVisibility(View.GONE);
                applyInitialPositionIfNeeded();
                if (publishButtonController != null) publishButtonController.onPageSelected();
            }

            @Override
            public void onFail(int code, String msg) {
                if (destroyed || isFinishing()) return;
                loading = false;
                hideLoading();
                showEmptyOrKeep(TextUtils.isEmpty(msg) ? getString(R.string.feed_retry) : msg);
            }
        };
        if (MODE_PROFILE.equals(mode) && !TextUtils.isEmpty(uid)) {
            FeedModel.getInstance().userFeeds(uid, cursor, listener);
        } else if (MODE_FOLLOWING.equals(mode)) {
            FeedModel.getInstance().following(cursor, listener);
        } else {
            FeedModel.getInstance().recommend(mode, cursor, uid, listener);
        }
    }

    private int appendUnique(List<FeedBean> list) {
        if (list == null || list.isEmpty()) return 0;
        int before = adapter.getItemCount();
        for (FeedBean item : list) {
            if (item == null) continue;
            String key = item.stableKey();
            if (TextUtils.isEmpty(key)) continue;
            if (seenKeys.add(key)) adapter.add(item);
        }
        return adapter.getItemCount() - before;
    }

    private void showLoading() {
        loadingView.setVisibility(View.VISIBLE);
        stateTv.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingView.setVisibility(View.GONE);
        showPublishButtonIfNeeded();
    }

    private void showEmptyOrKeep(String text) {
        if (adapter.getItemCount() > 0) {
            showPublishButtonIfNeeded();
            return;
        }
        stateTv.setText(text);
        stateTv.setVisibility(View.VISIBLE);
        showPublishButtonIfNeeded();
    }

    private void showPublishButtonIfNeeded() {
        if (publishButtonController != null && !MODE_PROFILE.equals(mode)) {
            publishButtonController.showNow();
        }
    }

    private void applyInitialPositionIfNeeded() {
        if (didApplyInitialPosition || adapter.getItemCount() <= 0) return;
        didApplyInitialPosition = true;
        int start = -1;
        if (!TextUtils.isEmpty(startFeedId)) start = adapter.indexOfFeedId(startFeedId);
        if (start < 0) start = Math.max(0, getIntent().getIntExtra(EXTRA_START_POSITION, 0));
        if (start < adapter.getItemCount()) feedPager.setCurrentItem(start, false);
        feedPager.post(() -> {
            if (!destroyed && adapter != null && feedPager != null) {
                adapter.setActivePosition(pagerRecyclerView, feedPager.getCurrentItem());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && pagerRecyclerView != null) {
            feedPager.post(() -> {
            if (!destroyed && adapter != null && feedPager != null) {
                adapter.setActivePosition(pagerRecyclerView, feedPager.getCurrentItem());
            }
        });
        }
        showPublishButtonIfNeeded();
    }

    @Override
    protected void onPause() {
        if (adapter != null) adapter.setActivePosition(pagerRecyclerView, -1);
        FeedPlayerManager.getInstance().pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (feedPager != null && pageChangeCallback != null) {
            try { feedPager.unregisterOnPageChangeCallback(pageChangeCallback); } catch (Exception ignored) {}
        }
        pageChangeCallback = null;
        if (adapter != null) {
            try { adapter.setActivePosition(pagerRecyclerView, -1); } catch (Throwable ignored) {}
        }
        if (publishButtonController != null) publishButtonController.destroy();
        publishButtonController = null;
        if (feedPager != null) {
            try { feedPager.setAdapter(null); } catch (Throwable ignored) {}
        }
        FeedPlayerManager.getInstance().stopForActivity();
        super.onDestroy();
    }
}
