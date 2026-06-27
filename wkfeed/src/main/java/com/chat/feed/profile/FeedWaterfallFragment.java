package com.chat.feed.profile;

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

public class FeedWaterfallFragment extends Fragment {
    private static final String ARG_UID = "uid";
    private String uid;
    private String cursor = "";
    private boolean loading;
    private boolean hasMore = true;
    private FeedWaterfallAdapter adapter;
    private ProgressBar loadingView;
    private TextView stateTv;

    public static FeedWaterfallFragment newInstance(String uid) {
        FeedWaterfallFragment fragment = new FeedWaterfallFragment();
        Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed_waterfall, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        uid = getArguments() == null ? "" : getArguments().getString(ARG_UID, "");
        RecyclerView recyclerView = view.findViewById(R.id.feedWaterfallRecyclerView);
        loadingView = view.findViewById(R.id.feedWaterfallLoading);
        stateTv = view.findViewById(R.id.feedWaterfallStateTv);
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        recyclerView.setLayoutManager(layoutManager);
        if (recyclerView.getItemAnimator() != null) recyclerView.getItemAnimator().setChangeDuration(0);
        adapter = new FeedWaterfallAdapter(this::openDetail);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!rv.canScrollVertically(1)) loadMore(false);
            }
        });
        stateTv.setOnClickListener(v -> loadMore(adapter.getItemCount() == 0));
        loadMore(true);
    }

    private void loadMore(boolean first) {
        if (loading || TextUtils.isEmpty(uid) || (!hasMore && !first)) return;
        loading = true;
        if (first) {
            loadingView.setVisibility(View.VISIBLE);
            stateTv.setVisibility(View.GONE);
        }
        FeedModel.getInstance().userFeeds(uid, first ? "" : cursor, new IRequestResultListener<FeedListResponse>() {
            @Override
            public void onSuccess(FeedListResponse result) {
                loading = false;
                loadingView.setVisibility(View.GONE);
                if (result == null) return;
                cursor = result.cursor;
                hasMore = result.has_more == 1 && !TextUtils.isEmpty(cursor);
                if (first) adapter.submitList(result.safeList());
                else adapter.append(result.safeList());
                updateState(false);
            }

            @Override
            public void onFail(int code, String msg) {
                loading = false;
                loadingView.setVisibility(View.GONE);
                updateState(true);
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

    private void openDetail(FeedBean item, int position) {
        if (item == null) return;
        Intent intent = new Intent(requireContext(), FeedBrowseActivity.class);
        intent.putExtra(FeedBrowseActivity.EXTRA_MODE, FeedBrowseActivity.MODE_PROFILE);
        intent.putExtra(FeedBrowseActivity.EXTRA_UID, uid);
        intent.putExtra(FeedBrowseActivity.EXTRA_START_FEED_ID, item.stableKey());
        intent.putExtra(FeedBrowseActivity.EXTRA_START_POSITION, position);
        startActivity(intent);
    }
}
