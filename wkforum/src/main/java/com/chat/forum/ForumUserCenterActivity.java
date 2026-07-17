package com.chat.forum;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Native forum notifications, favorites and current user's topics. */
public class ForumUserCenterActivity extends AppCompatActivity {
    private static final int TAB_MESSAGES = 0;
    private static final int TAB_FAVORITES = 1;
    private static final int TAB_MY_TOPICS = 2;

    private LinearLayout tabContainer;
    private RecyclerView recyclerView;
    private TextView stateView;
    private CenterAdapter adapter;
    private int selectedTab = TAB_MESSAGES;
    private String cursor = "";
    private boolean hasMore;
    private boolean loading;
    private int requestGeneration;

    public static Intent createIntent(Context context) {
        return new Intent(context, ForumUserCenterActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildView();
        load(true);
    }

    @Override
    protected void onDestroy() {
        requestGeneration++;
        super.onDestroy();
    }

    private void buildView() {
        boolean dark = isDark(this);
        getWindow().setStatusBarColor(dark ? 0xFF17181B : Color.WHITE);
        if (!dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF111214 : 0xFFF6F7F9);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(6), 0, dp(8), 0);
        toolbar.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        TextView back = text("‹", 35, dark ? Color.WHITE : 0xFF1C1E21, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView title = text("我的社区", 18, dark ? Color.WHITE : 0xFF1C1E21, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));

        TextView refresh = text("刷新", 14, 0xFF1877F2, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(selectableBackground(this));
        refresh.setOnClickListener(v -> load(true));
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(58), dp(44)));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setPadding(dp(12), dp(6), dp(12), dp(10));
        tabScroll.addView(tabContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(tabScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout content = new FrameLayout(this);
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, dp(6), 0, dp(18));
        adapter = new CenterAdapter(this, this::openRow);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) return;
                RecyclerView.LayoutManager manager = rv.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
                    if (last >= adapter.getItemCount() - 4) load(false);
                }
            }
        });
        content.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stateView = text("正在加载…", 14, dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
        stateView.setGravity(Gravity.CENTER);
        stateView.setPadding(dp(24), dp(24), dp(24), dp(24));
        content.addView(stateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        renderTabs();
    }

    private void renderTabs() {
        tabContainer.removeAllViews();
        addTab("通知", TAB_MESSAGES);
        addTab("收藏", TAB_FAVORITES);
        addTab("我的帖子", TAB_MY_TOPICS);
    }

    private void addTab(String title, int tab) {
        boolean selected = selectedTab == tab;
        boolean dark = isDark(this);
        TextView chip = text(title, 14,
                selected ? Color.WHITE : (dark ? 0xFFD8DADE : 0xFF44484F), selected);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(17), dp(7), dp(17), dp(7));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(18));
        background.setColor(selected ? 0xFF1877F2 : (dark ? 0xFF25272B : 0xFFF0F2F5));
        chip.setBackground(background);
        chip.setOnClickListener(v -> {
            if (selectedTab == tab) {
                load(true);
                return;
            }
            selectedTab = tab;
            renderTabs();
            load(true);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), 0, dp(4), 0);
        tabContainer.addView(chip, params);
    }

    private void load(boolean reset) {
        if (loading && !reset) return;
        if (reset) {
            requestGeneration++;
            loading = false;
            cursor = "";
            hasMore = false;
            adapter.replaceAll(new ArrayList<>());
            showState("正在加载…");
        }
        final int generation = requestGeneration;
        final int requestTab = selectedTab;
        final String requestCursor = cursor;
        loading = true;
        ForumApiClient.getInstance().ensureSession(this, new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (!isCurrent(generation, requestTab)) return;
                requestPage(generation, requestTab, requestCursor, reset);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isCurrent(generation, requestTab)) return;
                loading = false;
                if (adapter.getItemCount() == 0) showState(message + "\n点击刷新重试");
            }
        });
    }

    private void requestPage(int generation, int requestTab, String requestCursor, boolean reset) {
        if (requestTab == TAB_MESSAGES) {
            ForumApiClient.getInstance().getMessages(requestCursor,
                    new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Message>>() {
                        @Override
                        public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Message> page) {
                            List<Row> rows = new ArrayList<>();
                            if (page != null && page.results != null) {
                                for (ForumApiClient.Message message : page.results) rows.add(messageRow(message));
                            }
                            finishPage(generation, requestTab, reset, page, rows);
                            if (isCurrent(generation, requestTab)) setResult(Activity.RESULT_OK);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            finishError(generation, requestTab, message);
                        }
                    });
        } else if (requestTab == TAB_FAVORITES) {
            ForumApiClient.getInstance().getFavorites(requestCursor,
                    new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Favorite>>() {
                        @Override
                        public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Favorite> page) {
                            List<Row> rows = new ArrayList<>();
                            if (page != null && page.results != null) {
                                for (ForumApiClient.Favorite favorite : page.results) rows.add(favoriteRow(favorite));
                            }
                            finishPage(generation, requestTab, reset, page, rows);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            finishError(generation, requestTab, message);
                        }
                    });
        } else {
            String forumUserId = ForumApiClient.getInstance().getCurrentForumUserId();
            if (TextUtils.isEmpty(forumUserId)) {
                finishError(generation, requestTab, "论坛账号尚未建立，请刷新重试");
                return;
            }
            ForumApiClient.getInstance().getUserTopics(forumUserId, requestCursor,
                    new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Topic>>() {
                        @Override
                        public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Topic> page) {
                            List<Row> rows = new ArrayList<>();
                            if (page != null && page.results != null) {
                                for (ForumApiClient.Topic topic : page.results) rows.add(topicRow(topic));
                            }
                            finishPage(generation, requestTab, reset, page, rows);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            finishError(generation, requestTab, message);
                        }
                    });
        }
    }

    private <T> void finishPage(int generation, int requestTab, boolean reset,
                                @Nullable ForumApiClient.Page<T> page, List<Row> rows) {
        if (!isCurrent(generation, requestTab)) return;
        loading = false;
        if (reset) adapter.replaceAll(rows); else adapter.append(rows);
        if (page != null && !TextUtils.isEmpty(page.cursor)) cursor = page.cursor;
        hasMore = page != null && page.hasMore;
        if (adapter.getItemCount() == 0) {
            showState(emptyText(requestTab));
        } else {
            hideState();
        }
    }

    private void finishError(int generation, int requestTab, String message) {
        if (!isCurrent(generation, requestTab)) return;
        loading = false;
        if (adapter.getItemCount() == 0) showState(message + "\n点击刷新重试");
    }

    private boolean isCurrent(int generation, int tab) {
        return !isFinishing() && generation == requestGeneration && tab == selectedTab;
    }

    private String emptyText(int tab) {
        if (tab == TAB_MESSAGES) return "暂时没有社区通知";
        if (tab == TAB_FAVORITES) return "还没有收藏帖子";
        return "你还没有发布帖子";
    }

    private Row messageRow(@Nullable ForumApiClient.Message message) {
        Row row = new Row();
        if (message == null) return row;
        row.title = firstNonEmpty(message.title, "社区通知");
        row.summary = firstNonEmpty(message.content, message.quoteContent);
        String from = message.from == null ? "系统" : firstNonEmpty(message.from.nickname, "系统");
        row.meta = from + " · " + formatTime(message.createTime);
        row.topicId = ForumApiClient.getInstance().topicIdFromUrl(message.detailUrl);
        row.disabled = TextUtils.isEmpty(row.topicId);
        return row;
    }

    private Row favoriteRow(@Nullable ForumApiClient.Favorite favorite) {
        Row row = new Row();
        if (favorite == null) return row;
        row.title = favorite.deleted ? "内容已删除" : firstNonEmpty(favorite.title, "收藏内容");
        row.summary = favorite.deleted ? "该内容已经不存在" : favorite.content;
        String author = favorite.user == null ? "" : firstNonEmpty(favorite.user.nickname, "");
        row.meta = (TextUtils.isEmpty(author) ? "收藏" : author) + " · " + formatTime(favorite.createTime);
        row.topicId = "topic".equals(favorite.entityType)
                ? ForumApiClient.getInstance().topicIdFromUrl(favorite.url) : "";
        row.disabled = favorite.deleted || TextUtils.isEmpty(row.topicId);
        return row;
    }

    private Row topicRow(@Nullable ForumApiClient.Topic topic) {
        Row row = new Row();
        if (topic == null) return row;
        row.title = firstNonEmpty(topic.title, "未命名帖子");
        row.summary = topic.summary;
        String category = topic.category == null ? "" : firstNonEmpty(topic.category.name, "");
        String prefix = TextUtils.isEmpty(category) ? "我的帖子" : category;
        row.meta = prefix + " · " + formatTime(topic.createTime)
                + " · 评论 " + topic.commentCount + " · 赞 " + topic.likeCount;
        row.topicId = topic.id;
        row.disabled = TextUtils.isEmpty(row.topicId);
        return row;
    }

    private void openRow(Row row) {
        if (row == null || row.disabled || TextUtils.isEmpty(row.topicId)) {
            Toast.makeText(this, "该通知没有可打开的帖子", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(ForumTopicActivity.createIntent(this, row.topicId));
    }

    private void showState(String value) {
        stateView.setText(value);
        stateView.setVisibility(View.VISIBLE);
    }

    private void hideState() {
        stateView.setVisibility(View.GONE);
    }

    private static final class Row {
        String title = "";
        String summary = "";
        String meta = "";
        String topicId = "";
        boolean disabled;
    }

    private interface RowClickListener {
        void onClick(Row row);
    }

    private static final class CenterAdapter extends RecyclerView.Adapter<RowHolder> {
        private final Context context;
        private final List<Row> items = new ArrayList<>();
        private final RowClickListener listener;

        private CenterAdapter(Context context, RowClickListener listener) {
            this.context = context;
            this.listener = listener;
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RowHolder(createRowView(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            Row row = items.get(position);
            holder.title.setText(row.title);
            holder.title.setAlpha(row.disabled ? 0.55f : 1f);
            if (TextUtils.isEmpty(row.summary)) {
                holder.summary.setVisibility(View.GONE);
            } else {
                holder.summary.setVisibility(View.VISIBLE);
                holder.summary.setText(row.summary);
                holder.summary.setAlpha(row.disabled ? 0.55f : 1f);
            }
            holder.meta.setText(row.meta);
            holder.itemView.setOnClickListener(v -> listener.onClick(row));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void replaceAll(List<Row> rows) {
            items.clear();
            if (rows != null) items.addAll(rows);
            notifyDataSetChanged();
        }

        private void append(List<Row> rows) {
            if (rows == null || rows.isEmpty()) return;
            int start = items.size();
            items.addAll(rows);
            notifyItemRangeInserted(start, rows.size());
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView summary;
        final TextView meta;

        private RowHolder(@NonNull View itemView) {
            super(itemView);
            LinearLayout root = (LinearLayout) itemView;
            title = (TextView) root.getChildAt(0);
            summary = (TextView) root.getChildAt(1);
            meta = (TextView) root.getChildAt(2);
        }
    }

    private static View createRowView(Context context) {
        boolean dark = isDark(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 17), dp(context, 15), dp(context, 17), dp(context, 14));
        root.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        root.setForeground(selectableBackground(context));

        TextView title = text(context, "", 16, dark ? Color.WHITE : 0xFF1C1E21, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView summary = text(context, "", 14, dark ? 0xFFB8BBC2 : 0xFF60656D, false);
        summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(context, 7);
        root.addView(summary, summaryParams);

        TextView meta = text(context, "", 12, dark ? 0xFF858991 : 0xFF7A7F87, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(context, 9);
        root.addView(meta, metaParams);

        View divider = new View(context);
        divider.setBackgroundColor(dark ? 0xFF2B2D31 : 0xFFE8EAED);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = dp(context, 13);
        root.addView(divider, dividerParams);
        return root;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        return text(this, value, sizeSp, color, bold);
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

    private int dp(float value) {
        return dp(this, value);
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
        if (!TextUtils.isEmpty(first)) return first;
        return second == null ? "" : second;
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
