package com.chat.forum;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Native forum home shown inside the Community bottom tab. */
public class ForumHomeFragment extends Fragment {
    private static final long CATEGORY_LATEST = 0L;
    private static final long CATEGORY_RECOMMEND = -1L;
    private static final long CATEGORY_FOLLOW = -2L;

    private LinearLayout categoryContainer;
    private RecyclerView recyclerView;
    private TextView stateView;
    private TextView loginHintView;
    private TextView centerButton;
    private TopicAdapter adapter;
    private long selectedCategory = CATEGORY_LATEST;
    private String cursor = "";
    private boolean hasMore;
    private boolean loading;
    private boolean firstLoadDone;
    private int authGeneration;
    private int topicRequestGeneration;
    private final List<ForumApiClient.Category> categories = new ArrayList<>();
    private final ActivityResultLauncher<android.content.Intent> topicDetailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isAdded() && firstLoadDone) loadTopics(true);
            });
    private final ActivityResultLauncher<android.content.Intent> createTopicLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && isAdded()) {
                    loadCategories();
                    loadTopics(true);
                }
            });
    private final ActivityResultLauncher<android.content.Intent> userCenterLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isAdded()) loadUnreadCount();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        boolean dark = isDark(context);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF111214 : 0xFFF6F7F9);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(context, 18), dp(context, 10), dp(context, 12), dp(context, 8));
        toolbar.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        TextView title = text(context, "社区", 22, dark ? Color.WHITE : 0xFF17191C, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(v -> refreshAll());
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(context, 48), 1f));
        TextView publish = text(context, "发布", 14, 0xFF1877F2, true);
        publish.setGravity(Gravity.CENTER);
        publish.setBackground(selectableBackground(context));
        publish.setOnClickListener(v -> openComposer());
        toolbar.addView(publish, new LinearLayout.LayoutParams(dp(context, 58), dp(context, 42)));

        centerButton = text(context, "我的", 14, 0xFF1877F2, true);
        centerButton.setGravity(Gravity.CENTER);
        centerButton.setBackground(selectableBackground(context));
        centerButton.setOnClickListener(v -> {
            if (isAdded()) {
                userCenterLauncher.launch(ForumUserCenterActivity.createIntent(requireContext()));
            }
        });
        toolbar.addView(centerButton, new LinearLayout.LayoutParams(dp(context, 76), dp(context, 42)));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loginHintView = text(context, "", 12, dark ? 0xFFFFC46B : 0xFF9A6300, false);
        loginHintView.setPadding(dp(context, 16), dp(context, 7), dp(context, 16), dp(context, 7));
        loginHintView.setBackgroundColor(dark ? 0xFF302619 : 0xFFFFF6DB);
        loginHintView.setVisibility(View.GONE);
        root.addView(loginHintView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView categoryScroll = new HorizontalScrollView(context);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryScroll.setFillViewport(false);
        categoryScroll.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        categoryContainer = new LinearLayout(context);
        categoryContainer.setOrientation(LinearLayout.HORIZONTAL);
        categoryContainer.setGravity(Gravity.CENTER_VERTICAL);
        categoryContainer.setPadding(dp(context, 12), dp(context, 7), dp(context, 12), dp(context, 10));
        categoryScroll.addView(categoryContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(categoryScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout content = new FrameLayout(context);
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, dp(context, 6), 0, dp(context, 18));
        adapter = new TopicAdapter(context, topic ->
                topicDetailLauncher.launch(ForumTopicActivity.createIntent(context, topic.id)));
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) return;
                RecyclerView.LayoutManager manager = rv.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
                    if (last >= adapter.getItemCount() - 4) loadTopics(false);
                }
            }
        });
        content.addView(recyclerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stateView = text(context, "正在连接论坛…", 14, dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
        stateView.setGravity(Gravity.CENTER);
        stateView.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        content.addView(stateView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        renderCategoryButtons();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!firstLoadDone && getView() != null) {
            firstLoadDone = true;
            authenticateAndLoad();
        }
        if (getView() != null) loadUnreadCount();
    }

    @Override
    public void onDestroyView() {
        authGeneration++;
        topicRequestGeneration++;
        loading = false;
        firstLoadDone = false;
        categoryContainer = null;
        recyclerView = null;
        stateView = null;
        loginHintView = null;
        centerButton = null;
        adapter = null;
        super.onDestroyView();
    }

    private void authenticateAndLoad() {
        final int generation = ++authGeneration;
        showState("正在登录论坛…");
        ForumApiClient.getInstance().ensureSession(requireContext(), new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (!isAdded() || generation != authGeneration) return;
                loginHintView.setVisibility(View.GONE);
                loadUnreadCount();
                loadCategories();
                loadTopics(true);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded() || generation != authGeneration) return;
                loginHintView.setText("统一登录失败，当前可浏览公开内容：" + message);
                loginHintView.setVisibility(View.VISIBLE);
                loadCategories();
                loadTopics(true);
            }
        });
    }

    private void loadUnreadCount() {
        if (!isAdded() || centerButton == null || !ForumApiClient.getInstance().hasValidSession()) {
            if (centerButton != null) centerButton.setText("我的");
            return;
        }
        ForumApiClient.getInstance().getRecentMessages(
                new ForumApiClient.ResultCallback<ForumApiClient.RecentMessages>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.RecentMessages data) {
                        if (!isAdded() || centerButton == null) return;
                        long count = data == null ? 0L : Math.max(0L, data.count);
                        centerButton.setText(count <= 0 ? "我的" : "我的 " + (count > 99 ? "99+" : count));
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (centerButton != null) centerButton.setText("我的");
                    }
                });
    }

    private void openComposer() {
        if (!isAdded()) return;
        ForumApiClient.getInstance().ensureSession(requireContext(), new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (isAdded()) createTopicLauncher.launch(ForumCreateTopicActivity.createIntent(requireContext()));
            }

            @Override
            public void onError(@NonNull String message) {
                if (isAdded()) {
                    loginHintView.setText("无法发布：" + message);
                    loginHintView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void refreshAll() {
        if (!isAdded()) return;
        cursor = "";
        hasMore = false;
        authenticateAndLoad();
    }

    private void loadCategories() {
        ForumApiClient.getInstance().getCategories(new ForumApiClient.ResultCallback<List<ForumApiClient.Category>>() {
            @Override
            public void onSuccess(@Nullable List<ForumApiClient.Category> data) {
                if (!isAdded()) return;
                categories.clear();
                if (data != null) categories.addAll(data);
                renderCategoryButtons();
            }

            @Override
            public void onError(@NonNull String message) {
                // The built-in Latest/Recommended filters remain usable even if category loading fails.
            }
        });
    }

    private void loadTopics(boolean reset) {
        if (loading && !reset) return;
        if (reset) {
            topicRequestGeneration++;
            loading = false;
            cursor = "";
            hasMore = false;
            adapter.replaceAll(new ArrayList<>());
            showState("正在加载帖子…");
        }
        final int generation = topicRequestGeneration;
        final long requestCategory = selectedCategory;
        final String requestCursor = cursor;
        loading = true;
        ForumApiClient.getInstance().getTopics(requestCategory, requestCursor,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Topic>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Topic> page) {
                        if (!isAdded() || generation != topicRequestGeneration || requestCategory != selectedCategory) return;
                        loading = false;
                        List<ForumApiClient.Topic> list = page == null || page.results == null
                                ? new ArrayList<>() : page.results;
                        if (reset) adapter.replaceAll(list); else adapter.append(list);
                        cursor = page == null || TextUtils.isEmpty(page.cursor) ? cursor : page.cursor;
                        hasMore = page != null && page.hasMore;
                        if (adapter.getItemCount() == 0) showState("这里还没有帖子"); else hideState();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (!isAdded() || generation != topicRequestGeneration || requestCategory != selectedCategory) return;
                        loading = false;
                        if (adapter.getItemCount() == 0) showState(message + "\n点击顶部“社区”重试");
                    }
                });
    }

    private void renderCategoryButtons() {
        if (categoryContainer == null || !isAdded()) return;
        categoryContainer.removeAllViews();
        addCategoryButton("最新", CATEGORY_LATEST);
        addCategoryButton("精华", CATEGORY_RECOMMEND);
        addCategoryButton("关注", CATEGORY_FOLLOW);
        for (ForumApiClient.Category category : categories) {
            if (category == null || category.id <= 0 || TextUtils.isEmpty(category.name)) continue;
            addCategoryButton(category.name, category.id);
        }
    }

    private void addCategoryButton(String name, long categoryId) {
        Context context = requireContext();
        boolean selected = selectedCategory == categoryId;
        TextView chip = text(context, name, 14, selected ? Color.WHITE : (isDark(context) ? 0xFFD8DADE : 0xFF44484F), selected);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 15), dp(context, 7), dp(context, 15), dp(context, 7));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(context, 18));
        background.setColor(selected ? 0xFF1877F2 : (isDark(context) ? 0xFF25272B : 0xFFF0F2F5));
        chip.setBackground(background);
        chip.setOnClickListener(v -> {
            if (selectedCategory == categoryId) return;
            selectedCategory = categoryId;
            renderCategoryButtons();
            loadTopics(true);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, 4), 0, dp(context, 4), 0);
        categoryContainer.addView(chip, params);
    }

    private void showState(String text) {
        if (stateView == null) return;
        stateView.setText(text);
        stateView.setVisibility(View.VISIBLE);
    }

    private void hideState() {
        if (stateView != null) stateView.setVisibility(View.GONE);
    }

    private static final class TopicAdapter extends RecyclerView.Adapter<TopicHolder> {
        private final Context context;
        private final List<ForumApiClient.Topic> items = new ArrayList<>();
        private final OnTopicClickListener listener;

        private TopicAdapter(Context context, OnTopicClickListener listener) {
            this.context = context;
            this.listener = listener;
        }

        @NonNull
        @Override
        public TopicHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new TopicHolder(createItemView(context));
        }

        @Override
        public void onBindViewHolder(@NonNull TopicHolder holder, int position) {
            ForumApiClient.Topic topic = items.get(position);
            holder.title.setText((topic.sticky ? "[置顶] " : "") + safe(topic.title));
            if (TextUtils.isEmpty(topic.summary)) {
                holder.summary.setVisibility(View.GONE);
            } else {
                holder.summary.setVisibility(View.VISIBLE);
                holder.summary.setText(topic.summary);
            }
            String author = topic.user == null ? "" : safe(topic.user.nickname);
            String category = topic.category == null ? "" : safe(topic.category.name);
            String separator = !TextUtils.isEmpty(author) && !TextUtils.isEmpty(category) ? " · " : "";
            holder.meta.setText(author + separator + category + " · " + formatTime(topic.createTime));
            holder.stats.setText("浏览 " + topic.viewCount + "   评论 " + topic.commentCount + "   赞 " + topic.likeCount);
            holder.itemView.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(topic.id)) listener.onClick(topic);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void replaceAll(List<ForumApiClient.Topic> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        private void append(List<ForumApiClient.Topic> data) {
            if (data == null || data.isEmpty()) return;
            int start = items.size();
            items.addAll(data);
            notifyItemRangeInserted(start, data.size());
        }
    }

    private static final class TopicHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView summary;
        private final TextView meta;
        private final TextView stats;

        private TopicHolder(@NonNull View itemView) {
            super(itemView);
            LinearLayout root = (LinearLayout) itemView;
            title = (TextView) root.getChildAt(0);
            summary = (TextView) root.getChildAt(1);
            meta = (TextView) root.getChildAt(2);
            stats = (TextView) root.getChildAt(3);
        }
    }

    private interface OnTopicClickListener {
        void onClick(ForumApiClient.Topic topic);
    }

    private static View createItemView(Context context) {
        boolean dark = isDark(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 17), dp(context, 15), dp(context, 17), dp(context, 13));
        root.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        root.setForeground(selectableBackground(context));

        TextView title = text(context, "", 16, dark ? Color.WHITE : 0xFF1C1E21, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView summary = text(context, "", 14, dark ? 0xFFB8BBC2 : 0xFF60656D, false);
        summary.setMaxLines(2);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(context, 7);
        root.addView(summary, summaryParams);

        TextView meta = text(context, "", 12, dark ? 0xFF94989F : 0xFF7A7F87, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(context, 10);
        root.addView(meta, metaParams);

        TextView stats = text(context, "", 12, dark ? 0xFF858991 : 0xFF8A8F96, false);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = dp(context, 5);
        statsParams.bottomMargin = dp(context, 1);
        root.addView(stats, statsParams);

        View divider = new View(context);
        divider.setBackgroundColor(dark ? 0xFF2B2D31 : 0xFFE8EAED);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return root;
    }

    private static TextView text(Context context, String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static android.graphics.drawable.Drawable selectableBackground(Context context) {
        TypedValue out = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        return context.getDrawable(out.resourceId);
    }

    private static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "" : value;
    }

    private static String formatTime(long value) {
        if (value <= 0) return "刚刚";
        long millis = value < 10_000_000_000L ? value * 1000L : value;
        long diff = Math.max(0L, System.currentTimeMillis() - millis);
        if (diff < 60_000L) return "刚刚";
        if (diff < 3_600_000L) return (diff / 60_000L) + "分钟前";
        if (diff < 86_400_000L) return (diff / 3_600_000L) + "小时前";
        if (diff < 7 * 86_400_000L) return (diff / 86_400_000L) + "天前";
        return new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date(millis));
    }
}
