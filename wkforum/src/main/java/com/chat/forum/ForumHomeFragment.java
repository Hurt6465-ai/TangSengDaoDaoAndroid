package com.chat.forum;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.config.WKApiConfig;
import com.chat.base.ui.components.AvatarView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Native forum home shown inside the Community bottom tab. */
public class ForumHomeFragment extends Fragment {
    /** All sections mixed and ordered by latest activity. */
    private static final long CATEGORY_COMPREHENSIVE = -100L;
    /** All sections ordered by original publish time. */
    private static final long CATEGORY_LATEST = 0L;
    private static final long CATEGORY_FEATURED = -1L;
    private static final long CATEGORY_FOLLOW = -2L;
    private static final int FEATURED_CATEGORY_COUNT = 4;

    private DrawerLayout drawerLayout;
    private LinearLayout drawerContent;
    private LinearLayout feedTabContainer;
    private LinearLayout featuredSection;
    private GridLayout featuredGrid;
    private TextView allCategoriesButton;
    private TextView currentCategoryView;
    private RecyclerView recyclerView;
    private TextView stateView;
    private TextView loginHintView;
    private TextView centerButton;
    private TopicAdapter adapter;
    private long selectedCategory = CATEGORY_COMPREHENSIVE;
    private String cursor = "";
    private boolean hasMore;
    private boolean loading;
    private boolean firstLoadDone;
    private int authGeneration;
    private int topicRequestGeneration;
    private final List<ForumApiClient.Category> categories = new ArrayList<>();

    private final ActivityResultLauncher<Intent> topicDetailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isAdded() && firstLoadDone) loadTopics(true);
            });
    private final ActivityResultLauncher<Intent> createTopicLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && isAdded()) {
                    loadCategories();
                    loadTopics(true);
                }
            });
    private final ActivityResultLauncher<Intent> userCenterLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isAdded()) loadUnreadCount();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        boolean dark = isDark(context);

        drawerLayout = new DrawerLayout(context);
        drawerLayout.setBackgroundColor(dark ? 0xFF101113 : 0xFFF5F6F8);
        drawerLayout.setScrimColor(dark ? 0x99000000 : 0x55000000);
        drawerLayout.setDrawerElevation(dp(context, 14));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF101113 : 0xFFF5F6F8);
        drawerLayout.addView(root, new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildToolbar(context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loginHintView = text(context, "", 12, dark ? 0xFFFFC46B : 0xFF8B5B00, false);
        loginHintView.setPadding(dp(context, 16), dp(context, 7), dp(context, 16), dp(context, 7));
        loginHintView.setBackgroundColor(dark ? 0xFF302619 : 0xFFFFF5D8);
        loginHintView.setVisibility(View.GONE);
        root.addView(loginHintView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        feedTabContainer = new LinearLayout(context);
        feedTabContainer.setGravity(Gravity.CENTER_VERTICAL);
        feedTabContainer.setPadding(dp(context, 8), dp(context, 7), dp(context, 8), dp(context, 7));
        feedTabContainer.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        root.addView(feedTabContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        featuredSection = new LinearLayout(context);
        featuredSection.setOrientation(LinearLayout.VERTICAL);
        featuredSection.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 12));
        featuredSection.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        LinearLayout.LayoutParams featuredParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        featuredParams.topMargin = dp(context, 7);
        root.addView(featuredSection, featuredParams);
        buildFeaturedSectionHeader(context);

        currentCategoryView = text(context, "", 12, dark ? 0xFFAAB0B8 : 0xFF66707B, false);
        currentCategoryView.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        currentCategoryView.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        currentCategoryView.setVisibility(View.GONE);
        currentCategoryView.setOnClickListener(v -> selectCategory(CATEGORY_COMPREHENSIVE));
        root.addView(currentCategoryView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout content = new FrameLayout(context);
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, dp(context, 6), 0, dp(context, 22));
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
        content.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stateView = text(context, "正在连接论坛…", 14,
                dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
        stateView.setGravity(Gravity.CENTER);
        stateView.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        content.addView(stateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView drawerScroll = new ScrollView(context);
        drawerScroll.setFillViewport(true);
        drawerScroll.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        drawerContent = new LinearLayout(context);
        drawerContent.setOrientation(LinearLayout.VERTICAL);
        drawerContent.setPadding(dp(context, 12), dp(context, 18), dp(context, 12), dp(context, 26));
        drawerScroll.addView(drawerContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int drawerWidth = Math.min(dp(context, 340),
                Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.86f));
        DrawerLayout.LayoutParams drawerParams = new DrawerLayout.LayoutParams(
                drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        drawerParams.gravity = GravityCompat.START;
        drawerLayout.addView(drawerScroll, drawerParams);

        renderNavigation();
        renderDrawer();
        return drawerLayout;
    }

    private View buildToolbar(Context context) {
        boolean dark = isDark(context);
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(context, 4), dp(context, 7), dp(context, 8), dp(context, 6));
        toolbar.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        TextView menu = text(context, "☰", 24, dark ? Color.WHITE : 0xFF252A30, false);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(selectableBackground(context));
        menu.setContentDescription("打开板块侧栏");
        menu.setOnClickListener(v -> openDrawer());
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));

        LinearLayout titleBox = new LinearLayout(context);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(context, "社区", 21, dark ? Color.WHITE : 0xFF17191C, true);
        TextView subtitle = text(context, "综合讨论与经验分享", 11,
                dark ? 0xFF8F949C : 0xFF7A8088, false);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        titleBox.setOnClickListener(v -> refreshAll());
        toolbar.addView(titleBox, new LinearLayout.LayoutParams(0, dp(context, 52), 1f));

        TextView publish = text(context, "发布", 14, Color.WHITE, true);
        publish.setGravity(Gravity.CENTER);
        publish.setBackground(roundRect(context, 0xFF1877F2, 18));
        publish.setOnClickListener(v -> openComposer());
        LinearLayout.LayoutParams publishParams = new LinearLayout.LayoutParams(dp(context, 60), dp(context, 36));
        publishParams.setMargins(0, 0, dp(context, 2), 0);
        toolbar.addView(publish, publishParams);

        centerButton = text(context, "我的", 14, dark ? 0xFFD9DCE1 : 0xFF333840, true);
        centerButton.setGravity(Gravity.CENTER);
        centerButton.setBackground(selectableBackground(context));
        centerButton.setOnClickListener(v -> {
            if (isAdded()) userCenterLauncher.launch(ForumUserCenterActivity.createIntent(requireContext()));
        });
        toolbar.addView(centerButton, new LinearLayout.LayoutParams(dp(context, 66), dp(context, 40)));
        return toolbar;
    }

    private void buildFeaturedSectionHeader(Context context) {
        boolean dark = isDark(context);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(context, "常用板块", 15,
                dark ? Color.WHITE : 0xFF202328, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(context, 30), 1f));

        allCategoriesButton = text(context, "全部板块  ›", 13, 0xFF1877F2, true);
        allCategoriesButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        allCategoriesButton.setOnClickListener(v -> openDrawer());
        header.addView(allCategoriesButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 30)));
        featuredSection.addView(header);

        featuredGrid = new GridLayout(context);
        featuredGrid.setColumnCount(2);
        featuredGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        featuredGrid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = dp(context, 5);
        featuredSection.addView(featuredGrid, gridParams);
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
        drawerLayout = null;
        drawerContent = null;
        feedTabContainer = null;
        featuredSection = null;
        featuredGrid = null;
        allCategoriesButton = null;
        currentCategoryView = null;
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
                renderDrawer();
                loadTopics(true);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded() || generation != authGeneration) return;
                loginHintView.setText("统一登录失败，当前可浏览公开内容：" + message);
                loginHintView.setVisibility(View.VISIBLE);
                loadCategories();
                renderDrawer();
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
                if (isAdded()) createTopicLauncher.launch(
                        ForumCreateTopicActivity.createIntent(requireContext()));
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
        ForumApiClient.getInstance().getCategories(
                new ForumApiClient.ResultCallback<List<ForumApiClient.Category>>() {
                    @Override
                    public void onSuccess(@Nullable List<ForumApiClient.Category> data) {
                        if (!isAdded()) return;
                        categories.clear();
                        if (data != null) categories.addAll(data);
                        renderNavigation();
                        renderDrawer();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        renderNavigation();
                        renderDrawer();
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
        final long requestSelection = selectedCategory;
        final long apiCategory = requestSelection == CATEGORY_COMPREHENSIVE
                ? CATEGORY_LATEST : requestSelection;
        final String sort = requestSelection == CATEGORY_LATEST
                || requestSelection == CATEGORY_FEATURED ? "latestPublish" : "";
        final String requestCursor = cursor;
        loading = true;
        ForumApiClient.getInstance().getTopics(apiCategory, requestCursor, sort,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Topic>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Topic> page) {
                        if (!isAdded() || generation != topicRequestGeneration
                                || requestSelection != selectedCategory) return;
                        loading = false;
                        List<ForumApiClient.Topic> list = page == null || page.results == null
                                ? new ArrayList<>() : page.results;
                        if (reset) adapter.replaceAll(list); else adapter.append(list);
                        cursor = page == null || TextUtils.isEmpty(page.cursor) ? cursor : page.cursor;
                        hasMore = page != null && page.hasMore;
                        if (adapter.getItemCount() == 0) {
                            showState("这里还没有帖子\n来发布第一篇内容吧");
                        } else {
                            hideState();
                        }
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (!isAdded() || generation != topicRequestGeneration
                                || requestSelection != selectedCategory) return;
                        loading = false;
                        if (adapter.getItemCount() == 0) {
                            showState(message + "\n点击顶部“社区”重试");
                        }
                    }
                });
    }

    private void renderNavigation() {
        if (!isAdded() || feedTabContainer == null || featuredGrid == null) return;
        feedTabContainer.removeAllViews();
        addFeedTab("综合", CATEGORY_COMPREHENSIVE);
        addFeedTab("最新", CATEGORY_LATEST);
        addFeedTab("精华", CATEGORY_FEATURED);
        addFeedTab("关注", CATEGORY_FOLLOW);

        List<ForumApiClient.Category> flat = flattenCategories();
        allCategoriesButton.setText(flat.isEmpty() ? "全部板块  ›" : "全部板块 " + flat.size() + "  ›");
        featuredGrid.removeAllViews();

        List<ForumApiClient.Category> featured = featuredCategories(flat);
        if (featured.isEmpty()) {
            TextView loadingView = text(requireContext(), "板块加载中…", 13,
                    isDark(requireContext()) ? 0xFF8F949C : 0xFF7B818A, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(requireContext(), 48);
            params.columnSpec = GridLayout.spec(0, 2, 1f);
            loadingView.setGravity(Gravity.CENTER_VERTICAL);
            featuredGrid.addView(loadingView, params);
        } else {
            for (int i = 0; i < featured.size(); i++) addFeaturedCategory(featured.get(i), i);
        }
        renderCurrentCategory();
    }

    private void addFeedTab(String label, long categoryId) {
        Context context = requireContext();
        boolean selected = selectedCategory == categoryId;
        TextView tab = text(context, label, 14,
                selected ? 0xFF1877F2 : (isDark(context) ? 0xFFB8BBC2 : 0xFF565D66),
                selected);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(context, 5), 0, dp(context, 5), 0);
        tab.setBackground(selected
                ? roundRect(context, isDark(context) ? 0xFF243B59 : 0xFFEAF3FF, 15)
                : selectableBackground(context));
        tab.setOnClickListener(v -> selectCategory(categoryId));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(context, 33), 1f);
        params.setMargins(dp(context, 2), 0, dp(context, 2), 0);
        feedTabContainer.addView(tab, params);
    }

    private void addFeaturedCategory(ForumApiClient.Category category, int index) {
        Context context = requireContext();
        boolean dark = isDark(context);
        boolean selected = selectedCategory == category.id;
        int accent = categoryAccent(category.name);
        int backgroundColor = selected
                ? (dark ? blend(accent, Color.BLACK, 0.55f) : blend(accent, Color.WHITE, 0.87f))
                : (dark ? 0xFF222429 : 0xFFF7F8FA);

        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 10), dp(context, 7), dp(context, 9), dp(context, 7));
        GradientDrawable bg = roundRect(context, backgroundColor, 13);
        bg.setStroke(dp(context, 1), selected ? accent : (dark ? 0xFF303238 : 0xFFE9EBEF));
        card.setBackground(bg);
        card.setOnClickListener(v -> selectCategory(category.id));

        TextView icon = text(context, categoryInitial(category.name), 14,
                selected ? accent : (dark ? 0xFFD8DBE0 : 0xFF49515A), true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(context,
                dark ? blend(accent, Color.BLACK, 0.50f) : blend(accent, Color.WHITE, 0.80f), 11));
        card.addView(icon, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));

        TextView name = text(context, safe(category.name), 14,
                dark ? Color.WHITE : 0xFF23272D, true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(context, 9);
        card.addView(name, nameParams);

        TextView arrow = text(context, "›", 20, dark ? 0xFF8F949C : 0xFF8B929A, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(context, 20), dp(context, 36)));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(context, 55);
        params.columnSpec = GridLayout.spec(index % 2, 1f);
        params.rowSpec = GridLayout.spec(index / 2);
        int gap = dp(context, 4);
        params.setMargins(index % 2 == 0 ? 0 : gap,
                index < 2 ? 0 : gap,
                index % 2 == 0 ? gap : 0,
                0);
        featuredGrid.addView(card, params);
    }

    private void renderCurrentCategory() {
        if (currentCategoryView == null) return;
        if (selectedCategory <= 0) {
            currentCategoryView.setVisibility(View.GONE);
            return;
        }
        ForumApiClient.Category selected = findCategory(selectedCategory);
        if (selected == null) {
            currentCategoryView.setVisibility(View.GONE);
            return;
        }
        currentCategoryView.setText("正在浏览：" + safe(selected.name) + "    点击返回综合");
        currentCategoryView.setVisibility(View.VISIBLE);
    }

    private void selectCategory(long categoryId) {
        if (selectedCategory == categoryId) {
            closeDrawer();
            return;
        }
        selectedCategory = categoryId;
        renderNavigation();
        renderDrawer();
        closeDrawer();
        loadTopics(true);
    }

    private void openDrawer() {
        if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
    }

    private void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void renderDrawer() {
        if (!isAdded() || drawerContent == null) return;
        Context context = requireContext();
        boolean dark = isDark(context);
        drawerContent.removeAllViews();

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = new LinearLayout(context);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, "社区板块", 20, dark ? Color.WHITE : 0xFF1C2025, true);
        TextView subtitle = text(context, "从屏幕左侧右滑可随时打开", 11,
                dark ? 0xFF8F949C : 0xFF7A818A, false);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));
        TextView close = text(context, "×", 27, dark ? 0xFFD8DBE0 : 0xFF49515A, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(selectableBackground(context));
        close.setOnClickListener(v -> closeDrawer());
        header.addView(close, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        drawerContent.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addDrawerSection("内容流");
        addDrawerItem("综合", "全部板块的活跃讨论", CATEGORY_COMPREHENSIVE, 0, false);
        addDrawerItem("最新", "按发布时间查看新帖", CATEGORY_LATEST, 0, false);
        addDrawerItem("精华", "管理员精选内容", CATEGORY_FEATURED, 0, false);
        addDrawerItem("关注", "关注用户发布的帖子", CATEGORY_FOLLOW, 0, false);

        List<ForumApiClient.Category> flat = flattenCategories();
        List<ForumApiClient.Category> featured = featuredCategories(flat);
        if (!featured.isEmpty()) {
            addDrawerSection("常用板块");
            for (ForumApiClient.Category category : featured) {
                addDrawerCategory(category, 0);
            }
        }

        if (!categories.isEmpty()) {
            addDrawerSection("全部板块");
            for (ForumApiClient.Category root : categories) {
                if (root == null || root.id <= 0 || TextUtils.isEmpty(root.name)) continue;
                addDrawerCategory(root, 0);
                if (root.children != null) {
                    for (ForumApiClient.Category child : root.children) {
                        if (child == null || child.id <= 0 || TextUtils.isEmpty(child.name)) continue;
                        addDrawerCategory(child, 1);
                    }
                }
            }
        }

        if (ForumApiClient.getInstance().isForumManager()) {
            addDrawerSection("管理员");
            TextView admin = drawerRow(context, "管理员工具", "置顶、加精、删除与后台管理",
                    false, 0);
            admin.setOnClickListener(v -> {
                closeDrawer();
                showAdminToolsDialog();
            });
            drawerContent.addView(admin, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 58)));
        }
    }

    private void addDrawerSection(String label) {
        Context context = requireContext();
        TextView section = text(context, label, 12,
                isDark(context) ? 0xFF8F949C : 0xFF7A818A, true);
        section.setPadding(dp(context, 10), dp(context, 18), dp(context, 10), dp(context, 7));
        drawerContent.addView(section, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addDrawerCategory(ForumApiClient.Category category, int level) {
        TextView row = drawerRow(requireContext(), safe(category.name),
                TextUtils.isEmpty(category.description) ? categoryHint(category.name) : category.description,
                selectedCategory == category.id, level);
        row.setOnClickListener(v -> selectCategory(category.id));
        drawerContent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(requireContext(), 58)));
    }

    private void addDrawerItem(String title, String subtitle, long categoryId, int level,
                               boolean forceSelected) {
        TextView row = drawerRow(requireContext(), title, subtitle,
                forceSelected || selectedCategory == categoryId, level);
        row.setOnClickListener(v -> selectCategory(categoryId));
        drawerContent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(requireContext(), 58)));
    }

    private TextView drawerRow(Context context, String title, String subtitle,
                               boolean selected, int level) {
        boolean dark = isDark(context);
        TextView row = text(context, title + "\n" + subtitle, 14,
                selected ? 0xFF1877F2 : (dark ? 0xFFE0E2E5 : 0xFF2F343A), selected);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLineSpacing(dp(context, 1), 1f);
        row.setPadding(dp(context, 12 + level * 18), dp(context, 5), dp(context, 10), dp(context, 5));
        row.setMaxLines(2);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setBackground(selected
                ? roundRect(context, dark ? 0xFF243B59 : 0xFFEAF3FF, 12)
                : selectableBackground(context));
        return row;
    }

    private void showAdminToolsDialog() {
        if (!isAdded()) return;
        String message = "已启用原生管理操作：\n"
                + "• 帖子右上角可置顶/取消置顶\n"
                + "• 可设为精华/取消精华\n"
                + "• 有权限时可删除帖子和评论\n\n"
                + "板块、用户和举报的批量管理继续使用网页后台。";
        new AlertDialog.Builder(requireContext())
                .setTitle("管理员工具")
                .setMessage(message)
                .setPositiveButton("打开网页后台", (dialog, which) -> openAdminWeb())
                .setNeutralButton("刷新权限", (dialog, which) -> {
                    ForumApiClient.getInstance().invalidateSession();
                    authenticateAndLoad();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void openAdminWeb() {
        if (!isAdded()) return;
        String base = WKApiConfig.getForumBaseUrl();
        if (TextUtils.isEmpty(base)) return;
        String url = base + (base.endsWith("/") ? "" : "/") + "admin";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {
            loginHintView.setText("无法打开论坛后台");
            loginHintView.setVisibility(View.VISIBLE);
        }
    }

    private List<ForumApiClient.Category> flattenCategories() {
        List<ForumApiClient.Category> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ForumApiClient.Category category : categories) addCategoryTree(category, result, seen);
        return result;
    }

    private void addCategoryTree(ForumApiClient.Category category,
                                 List<ForumApiClient.Category> out,
                                 Set<Long> seen) {
        if (category == null || category.id <= 0 || TextUtils.isEmpty(category.name)
                || !seen.add(category.id)) return;
        out.add(category);
        if (category.children == null) return;
        for (ForumApiClient.Category child : category.children) addCategoryTree(child, out, seen);
    }

    private List<ForumApiClient.Category> featuredCategories(List<ForumApiClient.Category> flat) {
        List<ForumApiClient.Category> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        String[] priority = {"口语", "学习交流", "中缅贸易", "贸易", "找工作", "招聘",
                "影视", "游戏", "闲聊", "生活"};
        for (String keyword : priority) {
            for (ForumApiClient.Category category : flat) {
                if (result.size() >= FEATURED_CATEGORY_COUNT) break;
                if (seen.contains(category.id) || TextUtils.isEmpty(category.name)) continue;
                if (category.name.contains(keyword)) {
                    result.add(category);
                    seen.add(category.id);
                }
            }
        }
        for (ForumApiClient.Category category : flat) {
            if (result.size() >= FEATURED_CATEGORY_COUNT) break;
            if (seen.add(category.id)) result.add(category);
        }
        return result;
    }

    @Nullable
    private ForumApiClient.Category findCategory(long id) {
        for (ForumApiClient.Category category : flattenCategories()) {
            if (category.id == id) return category;
        }
        return null;
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
            String author = topic.user == null || TextUtils.isEmpty(topic.user.nickname)
                    ? "用户" : topic.user.nickname;
            String category = topic.category == null ? "" : safe(topic.category.name);

            holder.title.setText(safe(topic.title));
            holder.sticky.setVisibility(topic.sticky ? View.VISIBLE : View.GONE);
            holder.featured.setVisibility(topic.recommend ? View.VISIBLE : View.GONE);
            holder.author.setText(author);
            holder.meta.setText((TextUtils.isEmpty(category) ? "" : category + " · ")
                    + formatTime(topic.createTime));
            holder.stats.setText("评论 " + topic.commentCount + "   浏览 "
                    + topic.viewCount + "   赞 " + topic.likeCount);

            String avatarUrl = topic.user == null ? "" : ForumApiClient.getInstance().resolveUrl(
                    TextUtils.isEmpty(topic.user.smallAvatar) ? topic.user.avatar : topic.user.smallAvatar);
            String seed = topic.user == null ? author : safe(topic.user.id);
            holder.avatar.showAvatarUrl(avatarUrl, seed, author, seed);
            if (topic.user == null) {
                holder.avatar.showFlag("");
            } else {
                holder.avatar.showFlag(TextUtils.isEmpty(topic.user.countryCode)
                        ? topic.user.country : topic.user.countryCode);
            }

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
        private final AvatarView avatar;
        private final TextView author;
        private final TextView meta;
        private final TextView sticky;
        private final TextView featured;
        private final TextView title;
        private final TextView stats;

        private TopicHolder(@NonNull View itemView) {
            super(itemView);
            LinearLayout root = (LinearLayout) itemView;
            LinearLayout authorRow = (LinearLayout) root.getChildAt(0);
            avatar = (AvatarView) authorRow.getChildAt(0);
            LinearLayout authorText = (LinearLayout) authorRow.getChildAt(1);
            author = (TextView) authorText.getChildAt(0);
            meta = (TextView) authorText.getChildAt(1);
            LinearLayout badges = (LinearLayout) authorRow.getChildAt(2);
            sticky = (TextView) badges.getChildAt(0);
            featured = (TextView) badges.getChildAt(1);
            title = (TextView) root.getChildAt(1);
            stats = (TextView) root.getChildAt(2);
        }
    }

    private interface OnTopicClickListener {
        void onClick(ForumApiClient.Topic topic);
    }

    private static View createItemView(Context context) {
        boolean dark = isDark(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 10));
        root.setBackground(roundRect(context, dark ? 0xFF17181B : Color.WHITE, 14));
        root.setForeground(selectableBackground(context));
        RecyclerView.LayoutParams rootParams = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootParams.setMargins(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4));
        root.setLayoutParams(rootParams);

        LinearLayout authorRow = new LinearLayout(context);
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        AvatarView avatar = new AvatarView(context);
        avatar.setSize(34);
        authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)));

        LinearLayout authorText = new LinearLayout(context);
        authorText.setOrientation(LinearLayout.VERTICAL);
        TextView author = text(context, "", 13, dark ? 0xFFF1F2F4 : 0xFF272B31, true);
        TextView meta = text(context, "", 11, dark ? 0xFF8F949C : 0xFF7A818A, false);
        authorText.addView(author);
        authorText.addView(meta);
        LinearLayout.LayoutParams authorTextParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        authorTextParams.leftMargin = dp(context, 8);
        authorRow.addView(authorText, authorTextParams);

        LinearLayout badges = new LinearLayout(context);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        TextView sticky = badge(context, "置顶", 0xFFB96800,
                dark ? 0xFF40311D : 0xFFFFF0D6);
        TextView featured = badge(context, "精华", 0xFF1877F2,
                dark ? 0xFF243B59 : 0xFFEAF3FF);
        badges.addView(sticky, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 23)));
        LinearLayout.LayoutParams featuredParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 23));
        featuredParams.leftMargin = dp(context, 5);
        badges.addView(featured, featuredParams);
        authorRow.addView(badges);
        root.addView(authorRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(context, "", 16.5f, dark ? Color.WHITE : 0xFF1B1F24, true);
        title.setMaxLines(2);
        title.setLineSpacing(dp(context, 2), 1.08f);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(context, 9);
        root.addView(title, titleParams);

        TextView stats = text(context, "", 11.5f, dark ? 0xFF858A92 : 0xFF858B93, false);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = dp(context, 8);
        root.addView(stats, statsParams);
        return root;
    }

    private static TextView badge(Context context, String label, int textColor, int backgroundColor) {
        TextView badge = text(context, label, 10.5f, textColor, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(context, 7), 0, dp(context, 7), 0);
        badge.setBackground(roundRect(context, backgroundColor, 9));
        badge.setVisibility(View.GONE);
        return badge;
    }

    private static TextView text(Context context, String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static GradientDrawable roundRect(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
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
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, context.getResources().getDisplayMetrics()));
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "" : value;
    }

    private static String categoryInitial(String name) {
        if (TextUtils.isEmpty(name)) return "板";
        return name.substring(0, name.offsetByCodePoints(0, 1));
    }

    private static String categoryHint(String name) {
        if (TextUtils.isEmpty(name)) return "进入交流";
        if (name.contains("口语")) return "发音、口语与纠音";
        if (name.contains("贸易")) return "市场、物流与合作";
        if (name.contains("影视")) return "电影、剧集与字幕";
        if (name.contains("游戏")) return "组队、攻略与玩家交流";
        if (name.contains("工作") || name.contains("求职")) return "招聘、求职与经验";
        if (name.contains("学习交流")) return "方法、语法与答疑";
        if (name.contains("学习")) return "方法、资料与答疑";
        if (name.contains("闲聊")) return "日常分享与轻松讨论";
        return "进入板块交流";
    }

    private static int categoryAccent(String name) {
        int[] palette = {0xFF4C7CF3, 0xFF2C9B7A, 0xFF8B63D7, 0xFFE07A3F,
                0xFFCF5C79, 0xFF4E91A8};
        int hash = name == null ? 0 : name.hashCode();
        return palette[Math.abs(hash == Integer.MIN_VALUE ? 0 : hash) % palette.length];
    }

    private static int blend(int foreground, int background, float backgroundRatio) {
        float fgRatio = 1f - Math.max(0f, Math.min(1f, backgroundRatio));
        int red = Math.round(Color.red(foreground) * fgRatio + Color.red(background) * backgroundRatio);
        int green = Math.round(Color.green(foreground) * fgRatio + Color.green(background) * backgroundRatio);
        int blue = Math.round(Color.blue(foreground) * fgRatio + Color.blue(background) * backgroundRatio);
        return Color.rgb(red, green, blue);
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
