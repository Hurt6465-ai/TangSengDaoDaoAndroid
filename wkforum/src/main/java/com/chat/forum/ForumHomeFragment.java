package com.chat.forum;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.LongSparseArray;
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
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.chat.base.config.WKApiConfig;
import com.chat.base.ui.components.AvatarView;
import com.google.android.material.appbar.AppBarLayout;
import com.xinbida.wukongim.entity.WKChannelType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Native forum home shown inside the Community bottom tab. */
public class ForumHomeFragment extends Fragment {
    private static final String ARG_BOARD_MODE = "forum_arg_board_mode";
    private static final String ARG_BOARD_ID = "forum_arg_board_id";
    private static final String ARG_BOARD_NAME = "forum_arg_board_name";
    private static final String ARG_BOARD_DESCRIPTION = "forum_arg_board_description";
    private static final String ARG_TAG_MODE = "forum_arg_tag_mode";
    private static final String ARG_TAG_ID = "forum_arg_tag_id";
    private static final String ARG_TAG_NAME = "forum_arg_tag_name";
    private static final String STATE_BOARD_ID = "forum_state_board_id";
    private static final String STATE_BOARD_NAME = "forum_state_board_name";
    private static final String STATE_BOARD_DESCRIPTION = "forum_state_board_description";
    private static final String STATE_BOARD_SORT = "forum_state_board_sort";
    private static final String STATE_TAG_ID = "forum_state_tag_id";
    private static final String STATE_TAG_NAME = "forum_state_tag_name";
    private static final long CATEGORY_COMPREHENSIVE = -100L;
    private static final long CATEGORY_LATEST = 0L;
    private static final long CATEGORY_RECOMMEND = -1L;
    private static final long CATEGORY_FOLLOW = -2L;
    private static final int BOARD_SORT_LATEST = 0;
    private static final int BOARD_SORT_HOT = 1;
    private static final int BOARD_SORT_FEATURED = 2;
    private static final int FEATURED_CATEGORY_COUNT = 4;
    private static final int BOARD_FEATURED_SCAN_PAGES = 5;
    private static final int BOARD_FEATURED_TARGET_COUNT = 20;
    private static final long FEED_CACHE_TTL_MS = 3 * 60_000L;

    private DrawerLayout drawerLayout;
    private LinearLayout drawerContent;
    private AppBarLayout appBarLayout;
    private LinearLayout collapsibleHeader;
    private LinearLayout feedTabContainer;
    private LinearLayout featuredSection;
    private GridLayout featuredGrid;
    private TextView allCategoriesButton;
    private LinearLayout boardHeaderSection;
    private TextView boardIconView;
    private TextView boardTitleView;
    private TextView boardDescriptionView;
    private TextView boardFollowButton;
    private TextView toolbarTitleView;
    private TextView toolbarSubtitleView;
    private TextView currentCategoryView;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView stateView;
    private TextView loginHintView;
    private TextView centerButton;
    private TextView composeFab;
    private TopicAdapter adapter;
    private ArticleAdapter articleAdapter;
    private MixedFeedAdapter mixedFeedAdapter;
    private boolean boardMode;
    private boolean tagMode;
    private long boardCategoryId;
    private String boardCategoryName = "";
    private String boardCategoryDescription = "";
    private long tagId;
    private String tagName = "";
    private int boardSort = BOARD_SORT_LATEST;
    private long selectedCategory = CATEGORY_COMPREHENSIVE;
    private String cursor = "";
    private boolean hasMore;
    private boolean loading;
    private boolean firstLoadDone;
    private int authGeneration;
    private int topicRequestGeneration;
    private int appBarOffset;
    private ForumApiClient.RequestScope requestScope;
    private final List<ForumApiClient.Category> categories = new ArrayList<>();
    private final LongSparseArray<TopicFeedState> topicFeedStates = new LongSparseArray<>();
    private final ArticleFeedState articleFeedState = new ArticleFeedState();

    private final ActivityResultLauncher<Intent> topicDetailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (!isAdded() || !firstLoadDone) return;
                if (adapter != null) adapter.refreshSeenState();
                if (mixedFeedAdapter != null && mixedFeedAdapter.getItemCount() > 0) {
                    mixedFeedAdapter.notifyItemRangeChanged(0, mixedFeedAdapter.getItemCount(), "seen");
                }
                loadTopics(true, false);
            });
    private final ActivityResultLauncher<Intent> articleDetailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (!isAdded() || articleAdapter == null) return;
                articleAdapter.refreshSeenState();
                if (mixedFeedAdapter != null && mixedFeedAdapter.getItemCount() > 0) {
                    mixedFeedAdapter.notifyItemRangeChanged(0, mixedFeedAdapter.getItemCount(), "seen");
                }
            });
    private final ActivityResultLauncher<Intent> createTopicLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && isAdded()) {
                    loadCategories();
                    loadTopics(true, false);
                }
            });
    private final ActivityResultLauncher<Intent> userCenterLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isAdded()) loadUnreadCount();
            });

    static ForumHomeFragment newBoardInstance(long categoryId, @Nullable String name,
                                              @Nullable String description) {
        ForumHomeFragment fragment = new ForumHomeFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_BOARD_MODE, true);
        arguments.putLong(ARG_BOARD_ID, categoryId);
        arguments.putString(ARG_BOARD_NAME, safe(name));
        arguments.putString(ARG_BOARD_DESCRIPTION, safe(description));
        fragment.setArguments(arguments);
        return fragment;
    }

    static ForumHomeFragment newTagInstance(long tagId, @Nullable String name) {
        ForumHomeFragment fragment = new ForumHomeFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_TAG_MODE, true);
        arguments.putLong(ARG_TAG_ID, tagId);
        arguments.putString(ARG_TAG_NAME, safe(name));
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        boardMode = arguments != null && arguments.getBoolean(ARG_BOARD_MODE, false);
        tagMode = arguments != null && arguments.getBoolean(ARG_TAG_MODE, false);
        if (boardMode) {
            boardCategoryId = arguments.getLong(ARG_BOARD_ID, 0L);
            boardCategoryName = safe(arguments.getString(ARG_BOARD_NAME));
            boardCategoryDescription = safe(arguments.getString(ARG_BOARD_DESCRIPTION));
            if (savedInstanceState != null) {
                boardCategoryId = savedInstanceState.getLong(STATE_BOARD_ID, boardCategoryId);
                boardCategoryName = savedInstanceState.getString(STATE_BOARD_NAME, boardCategoryName);
                boardCategoryDescription = savedInstanceState.getString(
                        STATE_BOARD_DESCRIPTION, boardCategoryDescription);
                boardSort = savedInstanceState.getInt(STATE_BOARD_SORT, BOARD_SORT_LATEST);
            }
            selectedCategory = boardCategoryId;
        } else if (tagMode) {
            tagId = arguments.getLong(ARG_TAG_ID, 0L);
            tagName = safe(arguments.getString(ARG_TAG_NAME));
            if (savedInstanceState != null) {
                tagId = savedInstanceState.getLong(STATE_TAG_ID, tagId);
                tagName = savedInstanceState.getString(STATE_TAG_NAME, tagName);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (boardMode) {
            outState.putLong(STATE_BOARD_ID, boardCategoryId);
            outState.putString(STATE_BOARD_NAME, boardCategoryName);
            outState.putString(STATE_BOARD_DESCRIPTION, boardCategoryDescription);
            outState.putInt(STATE_BOARD_SORT, boardSort);
        } else if (tagMode) {
            outState.putLong(STATE_TAG_ID, tagId);
            outState.putString(STATE_TAG_NAME, tagName);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        requestScope = new ForumApiClient.RequestScope();
        boolean dark = isDark(context);

        drawerLayout = new DrawerLayout(context);
        drawerLayout.setBackgroundColor(dark ? 0xFF101113 : 0xFFF5F6F8);
        drawerLayout.setScrimColor(dark ? 0x99000000 : 0x55000000);
        drawerLayout.setDrawerElevation(dp(context, 14));

        CoordinatorLayout coordinator = new CoordinatorLayout(context);
        coordinator.setBackgroundColor(dark ? 0xFF101113 : 0xFFF5F6F8);
        drawerLayout.addView(coordinator, new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        appBarLayout = new AppBarLayout(context);
        appBarLayout.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        appBarLayout.setElevation(0f);
        appBarLayout.setStateListAnimator(null);
        appBarLayout.addOnOffsetChangedListener((layout, verticalOffset) -> appBarOffset = verticalOffset);
        appBarLayout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets;
        });
        appBarLayout.requestApplyInsets();

        collapsibleHeader = new LinearLayout(context);
        collapsibleHeader.setOrientation(LinearLayout.VERTICAL);
        collapsibleHeader.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        collapsibleHeader.addView(buildToolbar(context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loginHintView = text(context, "", 12, dark ? 0xFFFFC46B : 0xFF8B5B00, false);
        loginHintView.setPadding(dp(context, 16), dp(context, 6), dp(context, 16), dp(context, 6));
        loginHintView.setBackgroundColor(dark ? 0xFF302619 : 0xFFFFF5D8);
        loginHintView.setVisibility(View.GONE);
        collapsibleHeader.addView(loginHintView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (boardMode) {
            boardHeaderSection = buildBoardHeader(context);
            collapsibleHeader.addView(boardHeaderSection, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (!tagMode) {
            featuredSection = new LinearLayout(context);
            featuredSection.setOrientation(LinearLayout.VERTICAL);
            featuredSection.setPadding(dp(context, 14), dp(context, 4), dp(context, 14), dp(context, 9));
            featuredSection.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
            collapsibleHeader.addView(featuredSection, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            buildFeaturedSectionHeader(context);
        }

        feedTabContainer = new LinearLayout(context);
        feedTabContainer.setGravity(Gravity.CENTER_VERTICAL);
        feedTabContainer.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
        feedTabContainer.setBackground(roundRect(context,
                dark ? 0xFF23252A : 0xFFF2F3F5, 15));
        LinearLayout feedTabsRow = new LinearLayout(context);
        feedTabsRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        feedTabsRow.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 6));
        feedTabsRow.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        feedTabsRow.addView(feedTabContainer, new LinearLayout.LayoutParams(
                boardMode ? dp(context, 216) : dp(context, 158), dp(context, 29)));
        if (!boardMode && !tagMode) {
            // The home feed switch disappears with the discovery header.
            collapsibleHeader.addView(feedTabsRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        AppBarLayout.LayoutParams collapsibleParams = new AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        collapsibleParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        appBarLayout.addView(collapsibleHeader, collapsibleParams);

        if (boardMode) {
            // Tieba-style board sorting remains visible while the board profile collapses.
            appBarLayout.addView(feedTabsRow, new AppBarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (!tagMode) {
            currentCategoryView = text(context, "", 12,
                    dark ? 0xFFAAB0B8 : 0xFF66707B, false);
            currentCategoryView.setPadding(dp(context, 16), dp(context, 7),
                    dp(context, 16), dp(context, 7));
            currentCategoryView.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
            currentCategoryView.setVisibility(View.GONE);
            currentCategoryView.setOnClickListener(v -> selectCategory(CATEGORY_COMPREHENSIVE));
            appBarLayout.addView(currentCategoryView, new AppBarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        CoordinatorLayout.LayoutParams appBarParams = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        appBarParams.setBehavior(new AppBarLayout.Behavior());
        coordinator.addView(appBarLayout, appBarParams);

        swipeRefreshLayout = new SwipeRefreshLayout(context);
        swipeRefreshLayout.setColorSchemeColors(0xFF1877F2);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(dark ? 0xFF24262B : Color.WHITE);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (!tagMode) loadCategories();
            loadTopics(true, false);
        });
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) ->
                appBarOffset != 0 || (recyclerView != null && recyclerView.canScrollVertically(-1)));

        FrameLayout content = new FrameLayout(context);
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setBackgroundColor(dark ? 0xFF111214 : Color.WHITE);
        recyclerView.setPadding(0, dp(context, 3), 0, dp(context, 86));
        recyclerView.setItemViewCacheSize(10);
        adapter = new TopicAdapter(context, topic ->
                topicDetailLauncher.launch(ForumTopicActivity.createIntent(context, topic.id)));
        articleAdapter = new ArticleAdapter(context, article ->
                articleDetailLauncher.launch(ForumArticleActivity.createIntent(context, article.id)));
        mixedFeedAdapter = new MixedFeedAdapter(adapter, articleAdapter);
        recyclerView.setAdapter(mixedFeedAdapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) return;
                RecyclerView.LayoutManager manager = rv.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
                    if (last >= currentItemCount() - 5) loadTopics(false);
                }
            }
        });
        content.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stateView = text(context, ForumText.get(R.string.forum_connecting_forum), 14,
                dark ? 0xFFB8BBC2 : 0xFF6E737B, false);
        stateView.setGravity(Gravity.CENTER);
        stateView.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        content.addView(stateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefreshLayout.addView(content, new SwipeRefreshLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        CoordinatorLayout.LayoutParams contentParams = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentParams.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        coordinator.addView(swipeRefreshLayout, contentParams);

        composeFab = text(context, "+", 29, Color.WHITE, false);
        composeFab.setIncludeFontPadding(false);
        composeFab.setGravity(Gravity.CENTER);
        composeFab.setContentDescription(ForumText.get(R.string.forum_publish_content));
        composeFab.setBackground(roundRect(context, 0xFF1877F2, 27));
        composeFab.setElevation(dp(context, 7));
        composeFab.setOnClickListener(v -> openComposer());
        composeFab.setVisibility(tagMode ? View.GONE : View.VISIBLE);
        CoordinatorLayout.LayoutParams fabParams = new CoordinatorLayout.LayoutParams(
                dp(context, 54), dp(context, 54));
        fabParams.gravity = Gravity.END | Gravity.BOTTOM;
        fabParams.setMargins(0, 0, dp(context, 17), dp(context, 18));
        coordinator.addView(composeFab, fabParams);

        if (!tagMode) {
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
        }

        renderNavigation();
        renderDrawer();
        if (restoreCurrentFeedState()) firstLoadDone = true;
        return drawerLayout;
    }

    private View buildToolbar(Context context) {
        boolean dark = isDark(context);
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(context, 3), 0, dp(context, 6), 0);
        toolbar.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        boolean standalone = boardMode || tagMode;
        TextView leading = text(context, standalone ? "‹" : "☰",
                standalone ? 35 : 22, dark ? Color.WHITE : 0xFF252A30, false);
        leading.setGravity(Gravity.CENTER);
        leading.setBackground(selectableBackground(context));
        leading.setContentDescription(ForumText.get(standalone
                ? R.string.forum_back_to_community : R.string.forum_open_board_drawer));
        leading.setOnClickListener(v -> {
            if (standalone) {
                Activity activity = getActivity();
                if (activity != null) activity.finish();
            } else {
                openDrawer();
            }
        });
        toolbar.addView(leading, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));

        LinearLayout titleBox = new LinearLayout(context);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        String toolbarTitle = boardMode ? displayBoardName()
                : (tagMode ? displayTagName() : ForumText.get(R.string.forum_community));
        toolbarTitleView = text(context, toolbarTitle, 19,
                dark ? Color.WHITE : 0xFF17191C, true);
        toolbarSubtitleView = text(context,
                boardMode ? ForumText.get(R.string.forum_tap_switch_board)
                        : (tagMode ? ForumText.get(R.string.forum_tag_topics)
                        : ForumText.get(R.string.forum_community_subtitle)), 11,
                dark ? 0xFF8F949C : 0xFF7A8088, false);
        titleBox.addView(toolbarTitleView);
        titleBox.addView(toolbarSubtitleView);
        titleBox.setOnClickListener(v -> {
            if (boardMode) openDrawer(); else refreshAll();
        });
        toolbar.addView(titleBox, new LinearLayout.LayoutParams(0, dp(context, 44), 1f));

        if (boardMode) {
            TextView boardMenu = text(context, "☰", 20,
                    dark ? 0xFFD9DCE1 : 0xFF4D535B, false);
            boardMenu.setGravity(Gravity.CENTER);
            boardMenu.setBackground(selectableBackground(context));
            boardMenu.setContentDescription(ForumText.get(R.string.forum_switch_board));
            boardMenu.setOnClickListener(v -> openDrawer());
            toolbar.addView(boardMenu, new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)));
        }

        FrameLayout notificationButton = new FrameLayout(context);
        notificationButton.setForeground(selectableBackground(context));
        notificationButton.setContentDescription(ForumText.get(R.string.forum_community_notifications));
        notificationButton.setOnClickListener(v -> {
            if (isAdded()) userCenterLauncher.launch(ForumUserCenterActivity.createIntent(requireContext()));
        });
        AppCompatImageView bell = new AppCompatImageView(context);
        bell.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        setImageIcon(bell, R.drawable.ic_forum_bell, 22,
                dark ? 0xFFD9DCE1 : 0xFF4D535B);
        FrameLayout.LayoutParams bellParams = new FrameLayout.LayoutParams(
                dp(context, 38), dp(context, 38), Gravity.CENTER);
        notificationButton.addView(bell, bellParams);

        centerButton = text(context, "", 10, 0xFFE64646, true);
        centerButton.setGravity(Gravity.CENTER);
        centerButton.setMinWidth(dp(context, 18));
        centerButton.setPadding(dp(context, 4), 0, dp(context, 4), 0);
        centerButton.setBackground(roundRect(context,
                dark ? 0xFF3A2428 : 0xFFFFEEEE, 9));
        centerButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 18), Gravity.END | Gravity.TOP);
        countParams.setMargins(0, dp(context, 1), dp(context, 1), 0);
        notificationButton.addView(centerButton, countParams);
        toolbar.addView(notificationButton, new LinearLayout.LayoutParams(dp(context, 50), dp(context, 42)));
        return toolbar;
    }

    private LinearLayout buildBoardHeader(Context context) {
        boolean dark = isDark(context);
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(context, 15), dp(context, 10), dp(context, 15), dp(context, 13));
        section.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        boardIconView = text(context, categoryInitial(displayBoardName()), 21,
                categoryAccent(displayBoardName()), true);
        boardIconView.setGravity(Gravity.CENTER);
        boardIconView.setBackground(roundRect(context,
                dark ? 0xFF262A30 : 0xFFF1F5FA, 14));
        row.addView(boardIconView, new LinearLayout.LayoutParams(dp(context, 54), dp(context, 54)));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        boardTitleView = text(context, displayBoardName(), 18,
                dark ? Color.WHITE : 0xFF1D2126, true);
        boardDescriptionView = text(context, displayBoardDescription(), 12,
                dark ? 0xFFA1A6AE : 0xFF737A83, false);
        boardDescriptionView.setMaxLines(2);
        boardDescriptionView.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(boardTitleView);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(context, 3);
        copy.addView(boardDescriptionView, descriptionParams);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(context, 11);
        row.addView(copy, copyParams);

        boardFollowButton = text(context, "", 13, 0xFF1877F2, true);
        boardFollowButton.setGravity(Gravity.CENTER);
        boardFollowButton.setOnClickListener(v -> {
            if (!isAdded() || boardCategoryId <= 0) return;
            ForumBoardStore.toggleFollowed(requireContext(), boardCategoryId);
            renderBoardHeader();
            renderDrawer();
        });
        row.addView(boardFollowButton, new LinearLayout.LayoutParams(dp(context, 72), dp(context, 34)));
        section.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = text(context, ForumText.get(R.string.forum_board_header_hint), 11,
                dark ? 0xFF777D86 : 0xFF8B929A, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(context, 9);
        section.addView(hint, hintParams);
        renderBoardHeader();
        return section;
    }

    private void renderBoardHeader() {
        if (!boardMode) return;
        if (boardIconView != null) {
            boardIconView.setText(categoryInitial(displayBoardName()));
            boardIconView.setTextColor(categoryAccent(displayBoardName()));
        }
        if (boardTitleView != null) boardTitleView.setText(displayBoardName());
        if (boardDescriptionView != null) boardDescriptionView.setText(displayBoardDescription());
        if (toolbarTitleView != null) toolbarTitleView.setText(displayBoardName());
        if (toolbarSubtitleView != null) toolbarSubtitleView.setText(R.string.forum_tap_switch_board);
        if (boardFollowButton == null || !isAdded()) return;
        boolean followed = ForumBoardStore.isFollowed(requireContext(), boardCategoryId);
        boardFollowButton.setText(followed ? R.string.forum_followed : R.string.forum_follow);
        boardFollowButton.setTextColor(followed
                ? (isDark(requireContext()) ? 0xFFB6BBC3 : 0xFF68707A) : 0xFF1877F2);
        GradientDrawable background = roundRect(requireContext(), followed
                ? (isDark(requireContext()) ? 0xFF282B30 : 0xFFF1F3F5)
                : (isDark(requireContext()) ? 0xFF233B59 : 0xFFEAF3FF), 17);
        background.setStroke(dp(requireContext(), 0.7f), followed
                ? (isDark(requireContext()) ? 0xFF3A3E45 : 0xFFD9DDE2) : 0xFFB9D8FF);
        boardFollowButton.setBackground(background);
    }

    private String displayBoardName() {
        return TextUtils.isEmpty(boardCategoryName)
                ? ForumText.get(R.string.forum_default_board_name) : boardCategoryName;
    }

    private String displayBoardDescription() {
        return TextUtils.isEmpty(boardCategoryDescription)
                ? ForumText.get(R.string.forum_default_board_description) : boardCategoryDescription;
    }

    private String displayTagName() {
        return TextUtils.isEmpty(tagName)
                ? ForumText.get(R.string.forum_tag_topics) : "#" + tagName;
    }

    private void buildFeaturedSectionHeader(Context context) {
        boolean dark = isDark(context);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        allCategoriesButton = text(context, ForumText.get(R.string.forum_more), 13, 0xFF1877F2, true);
        allCategoriesButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        allCategoriesButton.setOnClickListener(v -> openDrawer());
        header.addView(allCategoriesButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 27)));
        featuredSection.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        featuredGrid = new GridLayout(context);
        featuredGrid.setColumnCount(2);
        featuredGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        featuredGrid.setUseDefaultMargins(false);
        featuredSection.addView(featuredGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View sectionDivider = new View(context);
        sectionDivider.setBackgroundColor(dark ? 0x332F3338 : 0x335F6872);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 0.7f));
        dividerParams.topMargin = dp(context, 9);
        featuredSection.addView(sectionDivider, dividerParams);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (boardMode && isAdded() && boardCategoryId > 0) {
            ForumBoardStore.addRecent(requireContext(), boardCategoryId);
        }
        if (!firstLoadDone && getView() != null) {
            firstLoadDone = true;
            authenticateAndLoad();
        }
        if (getView() != null) loadUnreadCount();
    }

    @Override
    public void onDestroyView() {
        saveCurrentFeedState();
        if (requestScope != null) requestScope.cancelAll();
        requestScope = null;
        authGeneration++;
        topicRequestGeneration++;
        loading = false;
        firstLoadDone = false;
        drawerLayout = null;
        drawerContent = null;
        appBarLayout = null;
        collapsibleHeader = null;
        feedTabContainer = null;
        featuredSection = null;
        featuredGrid = null;
        allCategoriesButton = null;
        boardHeaderSection = null;
        boardIconView = null;
        boardTitleView = null;
        boardDescriptionView = null;
        boardFollowButton = null;
        toolbarTitleView = null;
        toolbarSubtitleView = null;
        currentCategoryView = null;
        recyclerView = null;
        swipeRefreshLayout = null;
        stateView = null;
        loginHintView = null;
        centerButton = null;
        composeFab = null;
        adapter = null;
        articleAdapter = null;
        mixedFeedAdapter = null;
        super.onDestroyView();
    }

    private void authenticateAndLoad() {
        final int generation = ++authGeneration;
        showState(ForumText.get(R.string.forum_logging_in));
        ForumApiClient.getInstance().ensureSession(requireContext(), requestScope,
                new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (!isAdded() || generation != authGeneration) return;
                loginHintView.setVisibility(View.GONE);
                loadUnreadCount();
                if (!tagMode) {
                    loadCategories();
                    renderDrawer();
                }
                loadTopics(true);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded() || generation != authGeneration) return;
                loginHintView.setText(ForumText.get(R.string.forum_login_failed_public, message));
                loginHintView.setVisibility(View.VISIBLE);
                if (!tagMode) {
                    loadCategories();
                    renderDrawer();
                }
                loadTopics(true);
            }
        });
    }

    private void loadUnreadCount() {
        if (!isAdded() || centerButton == null || !ForumApiClient.getInstance().hasValidSession()) {
            if (centerButton != null) centerButton.setVisibility(View.GONE);
            return;
        }
        ForumApiClient.getInstance().getRecentMessages(requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.RecentMessages>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.RecentMessages data) {
                        if (!isAdded() || centerButton == null) return;
                        long count = data == null ? 0L : Math.max(0L, data.count);
                        centerButton.setText(count > 99 ? "99+" : String.valueOf(count));
                        centerButton.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (centerButton != null) centerButton.setVisibility(View.GONE);
                    }
                });
    }

    private void openComposer() {
        if (!isAdded() || tagMode) return;
        ForumApiClient.getInstance().ensureSession(requireContext(), requestScope,
                new ForumApiClient.ResultCallback<String>() {
            @Override
            public void onSuccess(@Nullable String data) {
                if (isAdded()) createTopicLauncher.launch(boardMode
                        ? ForumCreateTopicActivity.createIntent(requireContext(), boardCategoryId)
                        : ForumCreateTopicActivity.createIntent(requireContext()));
            }

            @Override
            public void onError(@NonNull String message) {
                if (isAdded()) {
                    loginHintView.setText(ForumText.get(R.string.forum_cannot_publish, message));
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
        if (tagMode) return;
        ForumApiClient.getInstance().getCategories(requestScope,
                new ForumApiClient.ResultCallback<List<ForumApiClient.Category>>() {
                    @Override
                    public void onSuccess(@Nullable List<ForumApiClient.Category> data) {
                        if (!isAdded()) return;
                        categories.clear();
                        if (data != null) categories.addAll(data);
                        if (boardMode) {
                            ForumApiClient.Category board = findCategory(boardCategoryId);
                            if (board != null) {
                                boardCategoryName = safe(board.name);
                                boardCategoryDescription = safe(board.description);
                            }
                            if (boardCategoryId > 0) {
                                ForumBoardStore.addRecent(requireContext(), boardCategoryId);
                            }
                        }
                        renderNavigation();
                        renderBoardHeader();
                        renderDrawer();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (!isAdded()) return;
                        renderNavigation();
                        renderDrawer();
                    }
                });
    }

    private void loadTopics(boolean reset) {
        loadTopics(reset, true);
    }

    private void loadTopics(boolean reset, boolean clearBeforeLoad) {
        if (loading && !reset) return;
        if (reset) {
            topicRequestGeneration++;
            loading = false;
            cursor = "";
            hasMore = false;
            if (clearBeforeLoad && adapter != null) adapter.replaceAll(new ArrayList<>());
            if (clearBeforeLoad && articleAdapter != null) articleAdapter.replaceAll(new ArrayList<>());
            if (mixedFeedAdapter != null) mixedFeedAdapter.rebuild();
            if (clearBeforeLoad || adapter == null || adapter.getItemCount() == 0) {
                showState(tagMode ? ForumText.get(R.string.forum_loading_tag_topics)
                        : boardMode ? ForumText.get(R.string.forum_entering_board)
                        : ForumText.get(R.string.forum_loading_topics));
            }
        }
        final int generation = topicRequestGeneration;
        final long requestSelection = selectedCategory;
        final int requestBoardSort = boardSort;
        final long apiCategory = boardMode ? boardCategoryId
                : (requestSelection == CATEGORY_COMPREHENSIVE ? CATEGORY_LATEST : requestSelection);
        final String sort = boardMode
                ? (requestBoardSort == BOARD_SORT_HOT ? "" : "latestPublish")
                : (requestSelection == CATEGORY_LATEST || requestSelection == CATEGORY_RECOMMEND
                ? "latestPublish" : "");
        final String requestCursor = cursor;
        loading = true;
        if (tagMode) {
            if (tagId <= 0L) {
                handleTopicError(generation, requestSelection, requestBoardSort,
                        ForumText.get(R.string.forum_empty_tag_topics));
                return;
            }
            ForumApiClient.getInstance().getTagTopics(tagId, requestCursor, requestScope,
                    new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Topic>>() {
                        @Override
                        public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Topic> page) {
                            if (!isTopicRequestCurrent(generation, requestSelection,
                                    requestBoardSort)) return;
                            List<ForumApiClient.Topic> list = page == null || page.results == null
                                    ? new ArrayList<>() : page.results;
                            finishTopicPage(reset, generation, requestSelection, requestBoardSort,
                                    list, page == null ? "" : safe(page.cursor),
                                    page != null && page.hasMore);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            handleTopicError(generation, requestSelection, requestBoardSort, message);
                        }
                    });
            return;
        }
        if (boardMode && requestBoardSort == BOARD_SORT_FEATURED) {
            loadBoardFeaturedPage(reset, generation, requestSelection, requestBoardSort,
                    apiCategory, requestCursor, 0, new ArrayList<>());
            return;
        }
        ForumApiClient.getInstance().getTopics(apiCategory, requestCursor, sort, requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Topic>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Topic> page) {
                        if (!isTopicRequestCurrent(generation, requestSelection, requestBoardSort)) return;
                        List<ForumApiClient.Topic> list = page == null || page.results == null
                                ? new ArrayList<>() : page.results;
                        finishTopicPage(reset, generation, requestSelection, requestBoardSort,
                                list, page == null ? "" : safe(page.cursor),
                                page != null && page.hasMore);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        handleTopicError(generation, requestSelection, requestBoardSort, message);
                    }
                });
    }

    private void loadBoardFeaturedPage(boolean reset, int generation, long requestSelection,
                                       int requestBoardSort, long categoryId,
                                       String pageCursor, int scannedPages,
                                       List<ForumApiClient.Topic> collected) {
        ForumApiClient.getInstance().getTopics(categoryId, pageCursor, "latestPublish", requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Topic>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Topic> page) {
                        if (!isTopicRequestCurrent(generation, requestSelection, requestBoardSort)) return;
                        List<ForumApiClient.Topic> source = page == null || page.results == null
                                ? new ArrayList<>() : page.results;
                        for (ForumApiClient.Topic topic : source) {
                            if (topic != null && topic.recommend) collected.add(topic);
                            if (collected.size() >= BOARD_FEATURED_TARGET_COUNT) break;
                        }
                        String nextCursor = page == null ? "" : safe(page.cursor);
                        boolean more = page != null && page.hasMore && !TextUtils.isEmpty(nextCursor);
                        boolean shouldContinue = collected.size() < BOARD_FEATURED_TARGET_COUNT
                                && more && scannedPages + 1 < BOARD_FEATURED_SCAN_PAGES;
                        if (shouldContinue) {
                            loadBoardFeaturedPage(reset, generation, requestSelection,
                                    requestBoardSort, categoryId, nextCursor,
                                    scannedPages + 1, collected);
                            return;
                        }
                        // If no featured post exists in the scanned window, stop instead of
                        // exposing an endless empty pagination state.
                        if (collected.isEmpty() && scannedPages + 1 >= BOARD_FEATURED_SCAN_PAGES) {
                            more = false;
                        }
                        finishTopicPage(reset, generation, requestSelection, requestBoardSort,
                                collected, nextCursor, more);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        handleTopicError(generation, requestSelection, requestBoardSort, message);
                    }
                });
    }

    private boolean isTopicRequestCurrent(int generation, long requestSelection,
                                          int requestBoardSort) {
        return isAdded() && generation == topicRequestGeneration
                && requestSelection == selectedCategory
                && (!boardMode || requestBoardSort == boardSort);
    }

    private void finishTopicPage(boolean reset, int generation, long requestSelection,
                                 int requestBoardSort, List<ForumApiClient.Topic> list,
                                 String nextCursor, boolean serverHasMore) {
        if (!isTopicRequestCurrent(generation, requestSelection, requestBoardSort)) return;
        loading = false;
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (reset) adapter.replaceAll(list); else adapter.append(list);
        if (mixedFeedAdapter != null) mixedFeedAdapter.rebuild();
        if (!boardMode && !tagMode && reset && requestSelection == CATEGORY_COMPREHENSIVE) {
            loadInlineArticles(generation, requestSelection);
        }
        if (!TextUtils.isEmpty(nextCursor)) cursor = nextCursor;
        hasMore = serverHasMore && !TextUtils.isEmpty(nextCursor);
        updateCurrentFeedState();
        if (currentItemCount() == 0) {
            if (boardMode && boardSort == BOARD_SORT_FEATURED) {
                showState(ForumText.get(R.string.forum_no_featured_board));
            } else if (boardMode) {
                showState(ForumText.get(R.string.forum_empty_board));
            } else if (tagMode) {
                showState(ForumText.get(R.string.forum_empty_tag_topics));
            } else {
                showState(ForumText.get(R.string.forum_empty_feed));
            }
        } else {
            hideState();
        }
    }

    private void handleTopicError(int generation, long requestSelection,
                                  int requestBoardSort, @NonNull String message) {
        if (!isTopicRequestCurrent(generation, requestSelection, requestBoardSort)) return;
        loading = false;
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (currentItemCount() == 0) showState(ForumText.get(R.string.forum_loading_retry_pull, message));
    }

    private void loadInlineArticles(int generation, long requestSelection) {
        if (requestSelection != CATEGORY_COMPREHENSIVE || articleAdapter == null) return;
        ForumApiClient.getInstance().getArticles("", requestScope,
                new ForumApiClient.ResultCallback<ForumApiClient.Page<ForumApiClient.Article>>() {
                    @Override
                    public void onSuccess(@Nullable ForumApiClient.Page<ForumApiClient.Article> page) {
                        if (!isAdded() || generation != topicRequestGeneration
                                || selectedCategory != CATEGORY_COMPREHENSIVE) return;
                        List<ForumApiClient.Article> source = page == null || page.results == null
                                ? new ArrayList<>() : page.results;
                        List<ForumApiClient.Article> inline = new ArrayList<>();
                        for (ForumApiClient.Article article : source) {
                            if (article == null || article.id <= 0) continue;
                            inline.add(article);
                            if (inline.size() >= 6) break;
                        }
                        articleAdapter.replaceAll(inline);
                        articleFeedState.items = articleAdapter.snapshot();
                        articleFeedState.updatedAt = System.currentTimeMillis();
                        if (mixedFeedAdapter != null) mixedFeedAdapter.rebuild();
                        saveCurrentFeedState();
                        if (currentItemCount() > 0) hideState();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        // Keep the last cached article cards when the article endpoint is temporarily unavailable.
                    }
                });
    }

    private void updateCurrentFeedState() {
        saveCurrentFeedState();
        TopicFeedState state = topicState(currentFeedStateKey(), true);
        state.updatedAt = System.currentTimeMillis();
    }

    private void saveCurrentFeedState() {
        TopicFeedState state = topicState(currentFeedStateKey(), true);
        if (adapter != null) state.items = adapter.snapshot();
        state.cursor = cursor;
        state.hasMore = hasMore;
        captureScroll(state);
        if (!boardMode && !tagMode && selectedCategory == CATEGORY_COMPREHENSIVE
                && articleAdapter != null) {
            articleFeedState.items = articleAdapter.snapshot();
        }
    }

    private boolean restoreCurrentFeedState() {
        applyListAdapter();
        TopicFeedState state = topicState(currentFeedStateKey(), false);
        if (state == null || !state.isFresh() || state.items.isEmpty()) return false;
        adapter.replaceAll(state.items);
        if (!boardMode && !tagMode && selectedCategory == CATEGORY_COMPREHENSIVE
                && articleFeedState.isFresh()) {
            articleAdapter.replaceAll(articleFeedState.items);
        } else {
            articleAdapter.replaceAll(new ArrayList<>());
        }
        if (mixedFeedAdapter != null) mixedFeedAdapter.rebuild();
        cursor = state.cursor;
        hasMore = state.hasMore;
        restoreScroll(state);
        hideState();
        return true;
    }

    private void captureScroll(FeedState state) {
        if (recyclerView == null) return;
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) return;
        LinearLayoutManager linear = (LinearLayoutManager) layoutManager;
        int position = linear.findFirstVisibleItemPosition();
        if (position < 0) return;
        View child = linear.findViewByPosition(position);
        state.position = position;
        state.offset = child == null ? 0 : child.getTop() - recyclerView.getPaddingTop();
    }

    private void restoreScroll(FeedState state) {
        if (recyclerView == null) return;
        recyclerView.post(() -> {
            if (recyclerView == null) return;
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(
                        Math.max(0, state.position), state.offset);
            }
        });
    }

    private long currentFeedStateKey() {
        if (tagMode) return Long.MIN_VALUE + Math.max(0L, tagId);
        if (!boardMode) return selectedCategory;
        return boardCategoryId * 4L + Math.max(0, Math.min(BOARD_SORT_FEATURED, boardSort));
    }

    @Nullable
    private TopicFeedState topicState(long categoryId, boolean create) {
        TopicFeedState state = topicFeedStates.get(categoryId);
        if (state == null && create) {
            state = new TopicFeedState();
            topicFeedStates.put(categoryId, state);
        }
        return state;
    }

    private int currentItemCount() {
        return mixedFeedAdapter == null ? 0 : mixedFeedAdapter.getItemCount();
    }

    private void applyListAdapter() {
        if (recyclerView != null && mixedFeedAdapter != null
                && recyclerView.getAdapter() != mixedFeedAdapter) {
            recyclerView.setAdapter(mixedFeedAdapter);
        }
    }

    private void renderNavigation() {
        if (!isAdded() || feedTabContainer == null) return;
        feedTabContainer.removeAllViews();
        if (tagMode) return;
        if (boardMode) {
            addBoardSortTab(ForumText.get(R.string.forum_sort_latest), BOARD_SORT_LATEST);
            addBoardSortTab(ForumText.get(R.string.forum_sort_hot), BOARD_SORT_HOT);
            addBoardSortTab(ForumText.get(R.string.forum_sort_featured), BOARD_SORT_FEATURED);
            renderBoardHeader();
            return;
        }
        if (featuredGrid == null || allCategoriesButton == null) return;
        addFeedTab(ForumText.get(R.string.forum_feed_all), CATEGORY_COMPREHENSIVE);
        addFeedTab(ForumText.get(R.string.forum_sort_latest), CATEGORY_LATEST);
        addFeedTab(ForumText.get(R.string.forum_feed_recommended), CATEGORY_RECOMMEND);

        List<ForumApiClient.Category> flat = flattenCategories();
        allCategoriesButton.setText(R.string.forum_more);
        featuredGrid.removeAllViews();

        List<ForumApiClient.Category> featured = featuredCategories(flat);
        if (featured.isEmpty()) {
            TextView loadingView = text(requireContext(), ForumText.get(R.string.forum_boards_loading), 13,
                    isDark(requireContext()) ? 0xFF8F949C : 0xFF7B818A, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(requireContext(), 44);
            params.columnSpec = GridLayout.spec(0, 2, 1f);
            loadingView.setGravity(Gravity.CENTER_VERTICAL);
            featuredGrid.addView(loadingView, params);
        } else {
            for (int i = 0; i < featured.size(); i++) addFeaturedCategory(featured.get(i), i);
        }
        renderCurrentCategory();
    }

    private void addBoardSortTab(String label, int sortMode) {
        Context context = requireContext();
        boolean dark = isDark(context);
        boolean selected = boardSort == sortMode;
        TextView tab = text(context, label, 12,
                selected ? (dark ? Color.WHITE : 0xFF1877F2)
                        : (dark ? 0xFFA9AFB7 : 0xFF626A73), selected);
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine(true);
        tab.setBackground(selected
                ? roundRect(context, dark ? 0xFF3A4554 : Color.WHITE, 11)
                : null);
        tab.setOnClickListener(v -> selectBoardSort(sortMode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(context, 0.5f), 0, dp(context, 0.5f), 0);
        feedTabContainer.addView(tab, params);
    }

    private void addFeedTab(String label, long categoryId) {
        Context context = requireContext();
        boolean dark = isDark(context);
        boolean selected = selectedCategory == categoryId;
        TextView tab = text(context, label, 10.8f,
                selected ? (dark ? Color.WHITE : 0xFF1877F2)
                        : (dark ? 0xFFA9AFB7 : 0xFF626A73), selected);
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine(true);
        tab.setBackground(selected
                ? roundRect(context, dark ? 0xFF3A4554 : Color.WHITE, 11)
                : null);
        tab.setOnClickListener(v -> selectCategory(categoryId));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(context, 0.5f), 0, dp(context, 0.5f), 0);
        feedTabContainer.addView(tab, params);
    }

    private void addFeaturedCategory(ForumApiClient.Category category, int index) {
        Context context = requireContext();
        boolean dark = isDark(context);
        boolean selected = selectedCategory == category.id;
        int accent = categoryAccent(category.name);
        int backgroundColor = selected
                ? (dark ? blend(accent, 0xFF17181B, 0.72f) : blend(accent, Color.WHITE, 0.86f))
                : (dark ? 0xFF202226 : 0xFFF7F8FA);

        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 9), dp(context, 7), dp(context, 9), dp(context, 7));
        GradientDrawable cardBg = roundRect(context, backgroundColor, 13);
        cardBg.setStroke(dp(context, 0.7f), dark ? 0xFF34373C : 0xFFE1E4E8);
        card.setBackground(cardBg);
        card.setOnClickListener(v -> openBoard(category));

        TextView icon = text(context, categoryInitial(category.name), 14,
                selected ? 0xFF1877F2 : accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(context,
                dark ? blend(accent, 0xFF17181B, 0.72f) : blend(accent, Color.WHITE, 0.82f), 10));
        card.addView(icon, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));

        TextView name = text(context, safe(category.name), 14,
                selected ? 0xFF1877F2 : (dark ? Color.WHITE : 0xFF2B2F35), true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(context, 9);
        card.addView(name, nameParams);

        TextView arrow = text(context, "›", 23,
                selected ? 0xFF1877F2 : (dark ? 0xFF8F949C : 0xFF9AA0A8), false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 34)));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(context, 52);
        params.columnSpec = GridLayout.spec(index % 2, 1f);
        params.rowSpec = GridLayout.spec(index / 2);
        int gap = dp(context, 5);
        params.setMargins(index % 2 == 0 ? 0 : gap, index >= 2 ? gap : 0,
                index % 2 == 0 ? gap : 0, 0);
        featuredGrid.addView(card, params);
    }

    private void renderCurrentCategory() {
        if (currentCategoryView == null) return;
        ForumApiClient.Category selected = findCategory(selectedCategory);
        if (selected == null) {
            currentCategoryView.setVisibility(View.GONE);
        } else {
            currentCategoryView.setText(ForumText.get(R.string.forum_current_board, safe(selected.name)));
            currentCategoryView.setVisibility(View.VISIBLE);
        }
    }

    private void selectCategory(long categoryId) {
        if (selectedCategory == categoryId) {
            if (recyclerView != null) recyclerView.smoothScrollToPosition(0);
            return;
        }
        saveCurrentFeedState();
        topicRequestGeneration++;
        loading = false;
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        selectedCategory = categoryId;
        if (selectedCategory != CATEGORY_COMPREHENSIVE && articleAdapter != null) {
            articleAdapter.replaceAll(new ArrayList<>());
        }
        if (mixedFeedAdapter != null) mixedFeedAdapter.rebuild();
        applyListAdapter();
        renderNavigation();
        renderDrawer();
        closeDrawer();
        if (!restoreCurrentFeedState()) loadTopics(true);
    }

    private void selectBoardSort(int sortMode) {
        if (!boardMode || boardSort == sortMode) {
            if (recyclerView != null) recyclerView.smoothScrollToPosition(0);
            return;
        }
        saveCurrentFeedState();
        topicRequestGeneration++;
        loading = false;
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        boardSort = sortMode;
        cursor = "";
        hasMore = false;
        if (articleAdapter != null) articleAdapter.replaceAll(new ArrayList<>());
        if (mixedFeedAdapter != null) mixedFeedAdapter.rebuild();
        renderNavigation();
        if (!restoreCurrentFeedState()) loadTopics(true);
    }

    private void openBoard(@NonNull ForumApiClient.Category category) {
        if (!isAdded() || category.id <= 0) return;
        if (boardMode) {
            switchBoard(category);
        } else {
            startActivity(ForumBoardActivity.createIntent(requireContext(), category.id,
                    category.name, category.description));
        }
    }

    private void switchBoard(@NonNull ForumApiClient.Category category) {
        if (!boardMode || category.id <= 0) return;
        if (boardCategoryId == category.id) {
            closeDrawer();
            if (recyclerView != null) recyclerView.smoothScrollToPosition(0);
            return;
        }
        saveCurrentFeedState();
        topicRequestGeneration++;
        loading = false;
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        boardCategoryId = category.id;
        selectedCategory = category.id;
        boardCategoryName = safe(category.name);
        boardCategoryDescription = safe(category.description);
        boardSort = BOARD_SORT_LATEST;
        cursor = "";
        hasMore = false;
        ForumBoardStore.addRecent(requireContext(), boardCategoryId);
        if (adapter != null) adapter.replaceAll(new ArrayList<>());
        if (articleAdapter != null) articleAdapter.replaceAll(new ArrayList<>());
        if (mixedFeedAdapter != null) mixedFeedAdapter.rebuild();
        renderBoardHeader();
        renderNavigation();
        renderDrawer();
        closeDrawer();
        if (!restoreCurrentFeedState()) loadTopics(true);
    }

    private void openDrawer() {
        if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
    }

    private void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    boolean closeDrawerIfOpen() {
        if (drawerLayout == null || !drawerLayout.isDrawerOpen(GravityCompat.START)) return false;
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
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
        TextView title = text(context, boardMode ? ForumText.get(R.string.forum_switch_board)
                : ForumText.get(R.string.forum_default_board_name), 20,
                dark ? Color.WHITE : 0xFF1C2025, true);
        TextView subtitle = text(context, ForumText.get(R.string.forum_drawer_swipe_hint), 11,
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

        if (!boardMode) {
            addDrawerSection(ForumText.get(R.string.forum_content_feed));
            addDrawerItem(ForumText.get(R.string.forum_feed_all),
                ForumText.get(R.string.forum_all_boards_active), CATEGORY_COMPREHENSIVE, 0, false);
            addDrawerItem(ForumText.get(R.string.forum_sort_latest),
                ForumText.get(R.string.forum_latest_description), CATEGORY_LATEST, 0, false);
            addDrawerItem(ForumText.get(R.string.forum_feed_recommended),
                ForumText.get(R.string.forum_recommended_description), CATEGORY_RECOMMEND, 0, false);
        }

        List<ForumApiClient.Category> followed = resolveBoardIds(
                ForumBoardStore.followedIds(context));
        if (!followed.isEmpty()) {
            addDrawerSection(ForumText.get(R.string.forum_my_boards));
            for (ForumApiClient.Category category : followed) addDrawerCategory(category, 0);
        }

        List<ForumApiClient.Category> recent = resolveBoardIds(
                ForumBoardStore.recentIds(context));
        if (boardMode && recent.isEmpty()) {
            ForumApiClient.Category current = currentBoardCategory();
            if (current != null) recent.add(current);
        }
        if (!recent.isEmpty()) {
            addDrawerSection(ForumText.get(R.string.forum_recently_visited));
            int count = 0;
            for (ForumApiClient.Category category : recent) {
                addDrawerCategory(category, 0);
                if (++count >= 6) break;
            }
        }

        if (!categories.isEmpty()) {
            addDrawerSection(ForumText.get(R.string.forum_all_boards));
            for (ForumApiClient.Category root : categories) addDrawerCategoryTree(root, 0);
        } else {
            addDrawerSection(ForumText.get(R.string.forum_all_boards));
            TextView loading = drawerRow(context, ForumText.get(R.string.forum_loading_boards),
                    ForumText.get(R.string.forum_wait_or_refresh), false, 0);
            drawerContent.addView(loading, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 58)));
        }

        if (ForumApiClient.getInstance().isForumManager()) {
            addDrawerSection(ForumText.get(R.string.forum_admin));
            TextView admin = drawerRow(context, ForumText.get(R.string.forum_admin_tools),
                    ForumText.get(R.string.forum_admin_tools_subtitle),
                    false, 0);
            admin.setOnClickListener(v -> {
                closeDrawer();
                showAdminToolsDialog();
            });
            drawerContent.addView(admin, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 58)));
        }
    }

    private List<ForumApiClient.Category> resolveBoardIds(List<Long> ids) {
        List<ForumApiClient.Category> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (ids == null) return result;
        for (Long id : ids) {
            if (id == null || id <= 0 || !seen.add(id)) continue;
            ForumApiClient.Category category = findCategory(id);
            if (category == null && boardMode && id == boardCategoryId) {
                category = currentBoardCategory();
            }
            if (category != null) result.add(category);
        }
        return result;
    }

    @Nullable
    private ForumApiClient.Category currentBoardCategory() {
        if (!boardMode || boardCategoryId <= 0) return null;
        ForumApiClient.Category category = findCategory(boardCategoryId);
        if (category != null) return category;
        category = new ForumApiClient.Category();
        category.id = boardCategoryId;
        category.name = displayBoardName();
        category.description = boardCategoryDescription;
        return category;
    }

    private void addDrawerCategoryTree(@Nullable ForumApiClient.Category category, int level) {
        if (category == null || category.id <= 0 || TextUtils.isEmpty(category.name)) return;
        addDrawerCategory(category, Math.min(level, 3));
        if (category.children == null) return;
        for (ForumApiClient.Category child : category.children) {
            addDrawerCategoryTree(child, level + 1);
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
        row.setOnClickListener(v -> openBoard(category));
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
        String message = ForumText.get(R.string.forum_admin_help_message);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.forum_admin_tools)
                .setMessage(message)
                .setPositiveButton(R.string.forum_open_web_admin, (dialog, which) -> openAdminWeb())
                .setNeutralButton(R.string.forum_refresh_permissions, (dialog, which) -> {
                    ForumApiClient.getInstance().invalidateSession();
                    authenticateAndLoad();
                })
                .setNegativeButton(R.string.forum_close, null)
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
            loginHintView.setText(R.string.forum_admin_open_failed);
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

    private static class FeedState {
        String cursor = "";
        boolean hasMore;
        int position;
        int offset;
        long updatedAt;

        boolean isFresh() {
            return updatedAt > 0 && System.currentTimeMillis() - updatedAt <= FEED_CACHE_TTL_MS;
        }
    }

    private static final class TopicFeedState extends FeedState {
        List<ForumApiClient.Topic> items = new ArrayList<>();
    }

    private static final class ArticleFeedState extends FeedState {
        List<ForumApiClient.Article> items = new ArrayList<>();
    }

    private static final class TopicAdapter extends RecyclerView.Adapter<TopicHolder> {
        private static final String SEEN_PREF = "forum_topic_seen";
        private final Context context;
        private final SharedPreferences seenPrefs;
        private final List<ForumApiClient.Topic> items = new ArrayList<>();
        private final OnTopicClickListener listener;

        private TopicAdapter(Context context, OnTopicClickListener listener) {
            this.context = context;
            this.listener = listener;
            this.seenPrefs = context.getSharedPreferences(SEEN_PREF, Context.MODE_PRIVATE);
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            String id = items.get(position).id;
            return TextUtils.isEmpty(id) ? RecyclerView.NO_ID : stableStringId(id);
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
                    ? ForumText.get(R.string.forum_user) : topic.user.nickname;
            String category = topic.category == null ? "" : safe(topic.category.name);
            boolean newReply = hasNewReply(topic);
            boolean read = wasSeen(topic);
            boolean dimmed = read && !newReply;
            boolean dark = isDark(context);

            bindAuthorCategory(holder.author, author, category, dark);
            holder.meta.setVisibility(View.GONE);
            bindTopicTags(holder.tagContainer, topic.tags, dark);
            holder.time.setText(formatTime(topic.createTime));
            holder.title.setText(safe(topic.title));
            holder.sticky.setVisibility(topic.sticky ? View.VISIBLE : View.GONE);
            holder.recommend.setVisibility(topic.recommend ? View.VISIBLE : View.GONE);
            boolean question = topic.type == 2;
            boolean solved = question && (topic.acceptedCommentId > 0
                    || "solved".equalsIgnoreCase(safe(topic.qaStatus)));
            holder.qaMark.setVisibility(question ? View.VISIBLE : View.GONE);
            holder.qaStatus.setVisibility(question ? View.VISIBLE : View.GONE);
            if (question) {
                holder.qaStatus.setText(solved ? R.string.forum_qa_solved : R.string.forum_qa_unsolved);
                holder.qaStatus.setTextColor(solved ? 0xFF21875B : 0xFFB76E00);
                holder.qaStatus.setBackground(roundRect(context,
                        solved ? (dark ? 0xFF203D32 : 0xFFE9F7F0)
                                : (dark ? 0xFF40311D : 0xFFFFF3D8), 9));
            } else {
                holder.qaStatus.setBackground(null);
            }
            boolean hasBounty = question && topic.bountyScore > 0;
            holder.bounty.setVisibility(hasBounty ? View.VISIBLE : View.GONE);
            holder.bounty.setText(hasBounty
                    ? ForumText.get(R.string.forum_bounty_list_points, topic.bountyScore) : "");
            boolean hasVote = topic.vote != null && topic.vote.id > 0;
            holder.vote.setVisibility(hasVote ? View.VISIBLE : View.GONE);
            holder.vote.setText(hasVote ? ForumText.get(R.string.forum_vote_badge) : "");
            holder.replyCount.setText(String.valueOf(Math.max(0L, topic.commentCount)));
            holder.replyCount.setTextColor(newReply ? 0xFF1877F2
                    : (dark ? 0xFFB7BCC4 : 0xFF626A74));
            holder.replyCount.setBackground(null);
            setCompoundIcon(holder.replyCount, com.chat.forum.R.drawable.ic_forum_chat_bubble,
                    14, newReply ? 0xFF1877F2 : (dark ? 0xFF9EA4AD : 0xFF69717A));

            int normalTitle = dark ? Color.WHITE : 0xFF171A1F;
            int dimTitle = dark ? 0xFF777C84 : 0xFF9A9FA6;
            holder.title.setTextColor(dimmed ? dimTitle : normalTitle);
            holder.author.setAlpha(dimmed ? 0.58f : 1f);
            holder.tagContainer.setAlpha(dimmed ? 0.52f : 1f);
            holder.time.setAlpha(dimmed ? 0.50f : 1f);
            holder.avatar.setAlpha(dimmed ? 0.56f : 1f);
            holder.sticky.setAlpha(dimmed ? 0.58f : 1f);
            holder.recommend.setAlpha(dimmed ? 0.58f : 1f);
            holder.qaMark.setAlpha(dimmed ? 0.58f : 1f);
            holder.qaStatus.setAlpha(dimmed ? 0.58f : 1f);
            holder.bounty.setAlpha(dimmed ? 0.58f : 1f);
            holder.vote.setAlpha(dimmed ? 0.58f : 1f);

            bindAvatar(holder.avatar, topic.user, author);
            holder.avatar.setOnClickListener(v -> ForumProfileRouter.open(context, topic.user));
            holder.itemView.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(topic.id)) listener.onClick(topic);
            });
        }

        private boolean wasSeen(ForumApiClient.Topic topic) {
            return topic != null && !TextUtils.isEmpty(topic.id)
                    && seenPrefs.contains("time_" + topic.id);
        }

        private boolean hasNewReply(ForumApiClient.Topic topic) {
            if (topic == null || TextUtils.isEmpty(topic.id) || topic.commentCount <= 0) return false;
            long seen = seenPrefs.getLong("time_" + topic.id, 0L);
            long seenCount = seenPrefs.getLong("count_" + topic.id, 0L);
            if (seen <= 0) return false;
            long latest = normalizeTime(topic.lastCommentTime);
            return topic.commentCount > seenCount || latest > seen + 1000L;
        }

        private void replaceAll(List<ForumApiClient.Topic> data) {
            List<ForumApiClient.Topic> next = uniqueTopics(data);
            List<ForumApiClient.Topic> old = new ArrayList<>(items);
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    return TextUtils.equals(old.get(oldPosition).id, next.get(newPosition).id);
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    return TextUtils.equals(topicSignature(old.get(oldPosition)),
                            topicSignature(next.get(newPosition)));
                }
            }, false);
            items.clear();
            items.addAll(next);
            diff.dispatchUpdatesTo(this);
        }

        private void append(List<ForumApiClient.Topic> data) {
            if (data == null || data.isEmpty()) return;
            Set<String> existing = new HashSet<>();
            for (ForumApiClient.Topic item : items) {
                if (item != null && !TextUtils.isEmpty(item.id)) existing.add(item.id);
            }
            List<ForumApiClient.Topic> added = new ArrayList<>();
            for (ForumApiClient.Topic item : data) {
                if (item == null || TextUtils.isEmpty(item.id) || !existing.add(item.id)) continue;
                added.add(item);
            }
            if (added.isEmpty()) return;
            int start = items.size();
            items.addAll(added);
            notifyItemRangeInserted(start, added.size());
        }

        private List<ForumApiClient.Topic> snapshot() {
            return new ArrayList<>(items);
        }

        private void refreshSeenState() {
            if (!items.isEmpty()) notifyItemRangeChanged(0, items.size(), "seen");
        }

        private static List<ForumApiClient.Topic> uniqueTopics(List<ForumApiClient.Topic> data) {
            List<ForumApiClient.Topic> result = new ArrayList<>();
            if (data == null) return result;
            Set<String> ids = new HashSet<>();
            for (ForumApiClient.Topic item : data) {
                if (item == null || TextUtils.isEmpty(item.id) || !ids.add(item.id)) continue;
                result.add(item);
            }
            return result;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void bindAvatar(AvatarView avatar, ForumApiClient.User user, String fallbackName) {
            try {
                String uid = user == null ? "" : safe(user.uid);
                String forumId = user == null ? "" : safe(user.id);
                if (!TextUtils.isEmpty(uid)) {
                    // Use the Talkami uid so this is the exact same AvatarView path as chat/feed.
                    avatar.showAvatar(uid, WKChannelType.PERSONAL);
                } else if (user != null && (!TextUtils.isEmpty(user.smallAvatar)
                        || !TextUtils.isEmpty(user.avatar))) {
                    String remote = TextUtils.isEmpty(user.smallAvatar) ? user.avatar : user.smallAvatar;
                    avatar.showAvatarUrl(ForumApiClient.getInstance().resolveUrl(remote),
                            forumId, fallbackName, forumId);
                } else {
                    avatar.showDefaultAvatar(fallbackName, TextUtils.isEmpty(uid) ? forumId : uid);
                }
                if (user != null && (!TextUtils.isEmpty(user.countryCode)
                        || !TextUtils.isEmpty(user.country))) {
                    avatar.showFlag(TextUtils.isEmpty(user.countryCode) ? user.country : user.countryCode);
                }
            } catch (Throwable ignored) {
                String seed = user == null ? fallbackName
                        : (!TextUtils.isEmpty(user.uid) ? user.uid : safe(user.id));
                avatar.showDefaultAvatar(fallbackName, seed);
            }
        }
    }

    private static final class MixedFeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_TOPIC = 1;
        private static final int TYPE_ARTICLE = 2;
        private final TopicAdapter topics;
        private final ArticleAdapter articles;
        private final List<FeedEntry> entries = new ArrayList<>();

        MixedFeedAdapter(TopicAdapter topics, ArticleAdapter articles) {
            this.topics = topics;
            this.articles = articles;
            setHasStableIds(true);
            rebuild();
        }

        void rebuild() {
            List<FeedEntry> next = new ArrayList<>();
            int ti = 0;
            int ai = 0;
            while (ti < topics.items.size() || ai < articles.items.size()) {
                ForumApiClient.Topic topic = ti < topics.items.size() ? topics.items.get(ti) : null;
                ForumApiClient.Article article = ai < articles.items.size() ? articles.items.get(ai) : null;
                long topicTime = topic == null ? Long.MIN_VALUE
                        : Math.max(normalizeTime(topic.lastCommentTime), normalizeTime(topic.createTime));
                long articleTime = article == null ? Long.MIN_VALUE : normalizeTime(article.createTime);
                if (article != null && (topic == null || (!topic.sticky && articleTime > topicTime))) {
                    next.add(FeedEntry.article(ai++, article));
                } else if (topic != null) {
                    next.add(FeedEntry.topic(ti++, topic));
                }
            }
            List<FeedEntry> old = new ArrayList<>(entries);
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    return TextUtils.equals(old.get(oldPosition).key, next.get(newPosition).key);
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    return TextUtils.equals(old.get(oldPosition).signature,
                            next.get(newPosition).signature);
                }
            }, false);
            entries.clear();
            entries.addAll(next);
            diff.dispatchUpdatesTo(this);
        }

        @Override public long getItemId(int position) {
            FeedEntry entry = entries.get(position);
            if (entry.type == TYPE_ARTICLE) return Long.MIN_VALUE + entry.article.id;
            return TextUtils.isEmpty(entry.topic.id) ? RecyclerView.NO_ID
                    : stableStringId("topic:" + entry.topic.id);
        }

        @Override public int getItemViewType(int position) { return entries.get(position).type; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return viewType == TYPE_ARTICLE
                    ? articles.onCreateViewHolder(parent, 0)
                    : topics.onCreateViewHolder(parent, 0);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            FeedEntry entry = entries.get(position);
            if (entry.type == TYPE_ARTICLE) {
                articles.onBindViewHolder((ArticleHolder) holder, entry.sourceIndex);
            } else {
                topics.onBindViewHolder((TopicHolder) holder, entry.sourceIndex);
            }
        }

        @Override public int getItemCount() { return entries.size(); }

        private static final class FeedEntry {
            final int type;
            final int sourceIndex;
            final ForumApiClient.Topic topic;
            final ForumApiClient.Article article;
            final String key;
            final String signature;

            private FeedEntry(int type, int sourceIndex, ForumApiClient.Topic topic,
                              ForumApiClient.Article article, String key, String signature) {
                this.type = type;
                this.sourceIndex = sourceIndex;
                this.topic = topic;
                this.article = article;
                this.key = key;
                this.signature = signature;
            }

            static FeedEntry topic(int index, ForumApiClient.Topic topic) {
                return new FeedEntry(TYPE_TOPIC, index, topic, null,
                        "t:" + safe(topic.id), topicSignature(topic));
            }

            static FeedEntry article(int index, ForumApiClient.Article article) {
                return new FeedEntry(TYPE_ARTICLE, index, null, article,
                        "a:" + article.id, articleSignature(article));
            }
        }
    }

    private static final class ArticleAdapter extends RecyclerView.Adapter<ArticleHolder> {
        private static final String SEEN_PREF = "forum_article_seen";
        private final Context context;
        private final SharedPreferences seenPrefs;
        private final List<ForumApiClient.Article> items = new ArrayList<>();
        private final OnArticleClickListener listener;

        ArticleAdapter(Context context, OnArticleClickListener listener) {
            this.context = context;
            this.listener = listener;
            this.seenPrefs = context.getSharedPreferences(SEEN_PREF, Context.MODE_PRIVATE);
            setHasStableIds(true);
        }

        @Override public long getItemId(int position) { return items.get(position).id; }

        @NonNull @Override
        public ArticleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ArticleHolder(createArticleItemView(context));
        }

        @Override
        public void onBindViewHolder(@NonNull ArticleHolder holder, int position) {
            ForumApiClient.Article article = items.get(position);
            String author = article.user == null || TextUtils.isEmpty(article.user.nickname)
                    ? ForumText.get(R.string.forum_user) : article.user.nickname;
            boolean read = seenPrefs.getLong("time_" + article.id, 0L) > 0;
            boolean dark = isDark(context);
            bindAuthorCategory(holder.author, author, ForumText.get(R.string.forum_article), dark);
            holder.meta.setVisibility(View.GONE);
            holder.time.setText(formatTime(article.createTime));
            holder.title.setText(safe(article.title));
            holder.replyCount.setText(String.valueOf(Math.max(0L, article.commentCount)));
            int titleColor = read ? (dark ? 0xFF777C84 : 0xFF9A9FA6)
                    : (dark ? Color.WHITE : 0xFF171A1F);
            holder.title.setTextColor(titleColor);
            holder.avatar.setAlpha(read ? 0.58f : 1f);
            holder.author.setAlpha(read ? 0.58f : 1f);
            holder.articleMark.setAlpha(read ? 0.58f : 1f);
            bindArticleAvatar(holder.avatar, article.user, author);
            holder.avatar.setOnClickListener(v -> ForumProfileRouter.open(context, article.user));
            holder.itemView.setOnClickListener(v -> listener.onClick(article));
        }

        void replaceAll(List<ForumApiClient.Article> data) {
            List<ForumApiClient.Article> next = uniqueArticles(data);
            List<ForumApiClient.Article> old = new ArrayList<>(items);
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    return old.get(oldPosition).id == next.get(newPosition).id;
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    return TextUtils.equals(articleSignature(old.get(oldPosition)),
                            articleSignature(next.get(newPosition)));
                }
            }, false);
            items.clear();
            items.addAll(next);
            diff.dispatchUpdatesTo(this);
        }
        void append(List<ForumApiClient.Article> data) {
            if (data == null || data.isEmpty()) return;
            Set<Long> existing = new HashSet<>();
            for (ForumApiClient.Article item : items) if (item != null) existing.add(item.id);
            List<ForumApiClient.Article> added = new ArrayList<>();
            for (ForumApiClient.Article item : data) {
                if (item == null || !existing.add(item.id)) continue;
                added.add(item);
            }
            if (added.isEmpty()) return;
            int start = items.size(); items.addAll(added); notifyItemRangeInserted(start, added.size());
        }
        List<ForumApiClient.Article> snapshot() { return new ArrayList<>(items); }
        void refreshSeenState() {
            if (!items.isEmpty()) notifyItemRangeChanged(0, items.size(), "seen");
        }

        private static List<ForumApiClient.Article> uniqueArticles(List<ForumApiClient.Article> data) {
            List<ForumApiClient.Article> result = new ArrayList<>();
            if (data == null) return result;
            Set<Long> ids = new HashSet<>();
            for (ForumApiClient.Article item : data) {
                if (item == null || !ids.add(item.id)) continue;
                result.add(item);
            }
            return result;
        }
        @Override public int getItemCount() { return items.size(); }

        private void bindArticleAvatar(AvatarView avatar, ForumApiClient.User user, String fallback) {
            try {
                String uid = user == null ? "" : safe(user.uid);
                String id = user == null ? "" : safe(user.id);
                if (!TextUtils.isEmpty(uid)) avatar.showAvatar(uid, WKChannelType.PERSONAL);
                else if (user != null && (!TextUtils.isEmpty(user.smallAvatar) || !TextUtils.isEmpty(user.avatar))) {
                    String remote = TextUtils.isEmpty(user.smallAvatar) ? user.avatar : user.smallAvatar;
                    avatar.showAvatarUrl(ForumApiClient.getInstance().resolveUrl(remote), id, fallback, id);
                } else avatar.showDefaultAvatar(fallback, TextUtils.isEmpty(uid) ? id : uid);
                if (user != null && (!TextUtils.isEmpty(user.countryCode) || !TextUtils.isEmpty(user.country))) {
                    avatar.showFlag(TextUtils.isEmpty(user.countryCode) ? user.country : user.countryCode);
                }
            } catch (Throwable ignored) { avatar.showDefaultAvatar(fallback, fallback); }
        }
    }

    private static final class ArticleHolder extends RecyclerView.ViewHolder {
        final AvatarView avatar;
        final TextView author;
        final TextView meta;
        final TextView time;
        final TextView articleMark;
        final TextView title;
        final TextView replyCount;

        ArticleHolder(@NonNull View itemView) {
            super(itemView);
            LinearLayout root = (LinearLayout) itemView;
            LinearLayout authorRow = (LinearLayout) root.getChildAt(0);
            avatar = (AvatarView) authorRow.getChildAt(0);
            LinearLayout authorText = (LinearLayout) authorRow.getChildAt(1);
            author = (TextView) authorText.getChildAt(0);
            meta = (TextView) authorText.getChildAt(1);
            time = (TextView) authorRow.getChildAt(2);
            LinearLayout titleRow = (LinearLayout) root.getChildAt(1);
            articleMark = (TextView) titleRow.getChildAt(0);
            title = (TextView) titleRow.getChildAt(1);
            replyCount = (TextView) titleRow.getChildAt(2);
        }
    }

    private interface OnArticleClickListener { void onClick(ForumApiClient.Article article); }

    private static View createArticleItemView(Context context) {
        boolean dark = isDark(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 15), dp(context, 10), dp(context, 13), dp(context, 11));
        root.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        root.setForeground(selectableBackground(context));

        LinearLayout authorRow = new LinearLayout(context);
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        AvatarView avatar = new AvatarView(context);
        avatar.setSize(31);
        authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(context, 35), dp(context, 35)));
        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView author = text(context, "", 12.5f, dark ? 0xFFF1F2F4 : 0xFF272B31, true);
        author.setSingleLine(true);
        author.setEllipsize(TextUtils.TruncateAt.END);
        TextView meta = text(context, ForumText.get(R.string.forum_article), 10.5f, dark ? 0xFF8F949C : 0xFF7A818A, false);
        copy.addView(author); copy.addView(meta);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(context, 8);
        authorRow.addView(copy, copyParams);
        TextView time = text(context, "", 10.5f, dark ? 0xFF747981 : 0xFFA1A6AD, false);
        time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        authorRow.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 35)));
        root.addView(authorRow);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView articleMark = text(context, "#", 11.5f,
                dark ? 0xFFF0E7FF : Color.WHITE, true);
        articleMark.setGravity(Gravity.CENTER);
        articleMark.setIncludeFontPadding(false);
        articleMark.setBackground(roundRect(context,
                dark ? 0xFF7651AA : 0xFF8B63D7, 9));
        LinearLayout.LayoutParams articleMarkParams = new LinearLayout.LayoutParams(
                dp(context, 18), dp(context, 18));
        articleMarkParams.rightMargin = dp(context, 6);
        titleRow.addView(articleMark, articleMarkParams);
        TextView title = text(context, "", 16.5f, dark ? Color.WHITE : 0xFF171A1F, true);
        title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView replies = text(context, "0", 11.5f, dark ? 0xFFB7BCC4 : 0xFF626A74, true);
        replies.setGravity(Gravity.CENTER);
        replies.setPadding(dp(context, 4), 0, dp(context, 2), 0);
        replies.setCompoundDrawablePadding(dp(context, 2));
        setCompoundIcon(replies, R.drawable.ic_forum_chat_bubble, 14,
                dark ? 0xFF9EA4AD : 0xFF69717A);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 27));
        rp.leftMargin = dp(context, 9);
        titleRow.addView(replies, rp);
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trp.topMargin = dp(context, 7);
        root.addView(titleRow, trp);
        View divider = new View(context);
        divider.setBackgroundColor(dark ? 0xFF24262B : 0xFFEDEFF2);
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 0.55f));
        dpv.topMargin = dp(context, 10);
        root.addView(divider, dpv);
        return root;
    }

    private static final class TopicHolder extends RecyclerView.ViewHolder {
        private final AvatarView avatar;
        private final TextView author;
        private final TextView meta;
        private final LinearLayout tagContainer;
        private final TextView time;
        private final TextView sticky;
        private final TextView recommend;
        private final TextView qaMark;
        private final TextView title;
        private final TextView qaStatus;
        private final TextView bounty;
        private final TextView vote;
        private final TextView replyCount;

        private TopicHolder(@NonNull View itemView) {
            super(itemView);
            LinearLayout root = (LinearLayout) itemView;
            LinearLayout authorRow = (LinearLayout) root.getChildAt(0);
            avatar = (AvatarView) authorRow.getChildAt(0);
            LinearLayout authorText = (LinearLayout) authorRow.getChildAt(1);
            author = (TextView) authorText.getChildAt(0);
            LinearLayout metaRow = (LinearLayout) authorText.getChildAt(1);
            meta = (TextView) metaRow.getChildAt(0);
            tagContainer = (LinearLayout) metaRow.getChildAt(1);
            LinearLayout right = (LinearLayout) authorRow.getChildAt(2);
            time = (TextView) right.getChildAt(0);
            LinearLayout badges = (LinearLayout) right.getChildAt(1);
            sticky = (TextView) badges.getChildAt(0);
            recommend = (TextView) badges.getChildAt(1);
            qaStatus = (TextView) badges.getChildAt(2);
            LinearLayout titleRow = (LinearLayout) root.getChildAt(1);
            qaMark = (TextView) titleRow.getChildAt(0);
            title = (TextView) titleRow.getChildAt(1);
            bounty = (TextView) titleRow.getChildAt(2);
            vote = (TextView) titleRow.getChildAt(3);
            replyCount = (TextView) titleRow.getChildAt(4);
        }
    }

    private interface OnTopicClickListener {
        void onClick(ForumApiClient.Topic topic);
    }

    private static View createItemView(Context context) {
        boolean dark = isDark(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 15), dp(context, 10), dp(context, 13), dp(context, 11));
        root.setBackgroundColor(dark ? 0xFF17181B : Color.WHITE);
        root.setForeground(selectableBackground(context));
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout authorRow = new LinearLayout(context);
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        AvatarView avatar = new AvatarView(context);
        avatar.setSize(31);
        authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(context, 35), dp(context, 35)));

        LinearLayout authorText = new LinearLayout(context);
        authorText.setOrientation(LinearLayout.VERTICAL);
        TextView author = text(context, "", 12.5f, dark ? 0xFFF1F2F4 : 0xFF272B31, true);
        author.setSingleLine(true);
        author.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView meta = text(context, "", 10.5f, dark ? 0xFF8F949C : 0xFF7A818A, false);
        meta.setSingleLine(true);
        meta.setMaxWidth(dp(context, 108));
        meta.setEllipsize(TextUtils.TruncateAt.END);
        metaRow.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 20)));
        LinearLayout tagContainer = new LinearLayout(context);
        tagContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tagsParams = new LinearLayout.LayoutParams(
                0, dp(context, 20), 1f);
        tagsParams.leftMargin = dp(context, 4);
        metaRow.addView(tagContainer, tagsParams);
        authorText.addView(author);
        authorText.addView(metaRow);
        LinearLayout.LayoutParams authorTextParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        authorTextParams.leftMargin = dp(context, 8);
        authorRow.addView(authorText, authorTextParams);

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView time = text(context, "", 10.5f,
                dark ? 0xFF747981 : 0xFFA1A6AD, false);
        time.setGravity(Gravity.END);
        right.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 18)));

        LinearLayout badges = new LinearLayout(context);
        badges.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView sticky = badge(context, ForumText.get(R.string.forum_pinned), 0xFFB96800,
                dark ? 0xFF40311D : 0xFFFFF0D6);
        TextView recommend = badge(context, ForumText.get(R.string.forum_featured), 0xFF1877F2,
                dark ? 0xFF243B59 : 0xFFEAF3FF);
        TextView qaStatus = badge(context, "", 0xFFB76E00,
                dark ? 0xFF40311D : 0xFFFFF3D8);
        badges.addView(sticky, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 19)));
        LinearLayout.LayoutParams recommendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 19));
        recommendParams.leftMargin = dp(context, 4);
        badges.addView(recommend, recommendParams);
        LinearLayout.LayoutParams qaStatusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 18));
        qaStatusParams.leftMargin = dp(context, 4);
        badges.addView(qaStatus, qaStatusParams);
        right.addView(badges, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 20)));
        authorRow.addView(right, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(authorRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView qaMark = text(context, "?", 11.5f, dark ? 0xFF3D2C00 : 0xFF6E4B00, true);
        qaMark.setGravity(Gravity.CENTER);
        qaMark.setIncludeFontPadding(false);
        qaMark.setBackground(roundRect(context, dark ? 0xFFD6A52A : 0xFFFFD65A, 9));
        qaMark.setVisibility(View.GONE);
        LinearLayout.LayoutParams qaMarkParams = new LinearLayout.LayoutParams(
                dp(context, 18), dp(context, 18));
        qaMarkParams.rightMargin = dp(context, 6);
        titleRow.addView(qaMark, qaMarkParams);

        TextView title = text(context, "", 16.5f, dark ? Color.WHITE : 0xFF171A1F, true);
        title.setMaxLines(2);
        title.setLineSpacing(dp(context, 2), 1.08f);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView bounty = badge(context, "", dark ? 0xFFFFCB68 : 0xFFB96A00,
                dark ? 0xFF3A3020 : 0xFFFFF3D8);
        bounty.setVisibility(View.GONE);
        bounty.setMaxLines(1);
        LinearLayout.LayoutParams bountyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 20));
        bountyParams.leftMargin = dp(context, 7);
        titleRow.addView(bounty, bountyParams);

        TextView vote = badge(context, ForumText.get(R.string.forum_vote_badge),
                dark ? 0xFFFFB071 : 0xFFE76516,
                dark ? 0xFF3D2C22 : 0xFFFFEFE5);
        vote.setVisibility(View.GONE);
        vote.setMaxLines(1);
        LinearLayout.LayoutParams voteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 20));
        voteParams.leftMargin = dp(context, 7);
        titleRow.addView(vote, voteParams);

        TextView replyCount = text(context, "0", 11.5f,
                dark ? 0xFFB7BCC4 : 0xFF626A74, true);
        replyCount.setGravity(Gravity.CENTER);
        replyCount.setCompoundDrawablePadding(dp(context, 3));
        replyCount.setPadding(dp(context, 4), 0, dp(context, 2), 0);
        LinearLayout.LayoutParams replyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 27));
        replyParams.leftMargin = dp(context, 9);
        titleRow.addView(replyCount, replyParams);

        LinearLayout.LayoutParams titleRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleRowParams.topMargin = dp(context, 7);
        root.addView(titleRow, titleRowParams);

        View divider = new View(context);
        divider.setBackgroundColor(dark ? 0xFF24262B : 0xFFEDEFF2);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 0.55f));
        dividerParams.topMargin = dp(context, 10);
        root.addView(divider, dividerParams);
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

    private static void bindAuthorCategory(TextView view, String author, String category,
                                           boolean dark) {
        String safeAuthor = TextUtils.isEmpty(author)
                ? ForumText.get(R.string.forum_user) : author;
        if (TextUtils.isEmpty(category)) {
            view.setText(safeAuthor);
            return;
        }
        String suffix = " · " + category;
        SpannableStringBuilder value = new SpannableStringBuilder(safeAuthor).append(suffix);
        value.setSpan(new StyleSpan(Typeface.BOLD), 0, safeAuthor.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new ForegroundColorSpan(dark ? 0xFF8F949C : 0xFF8A9098),
                safeAuthor.length(), value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new StyleSpan(Typeface.NORMAL), safeAuthor.length(), value.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(value);
    }

    private static void bindTopicTags(LinearLayout container, @Nullable List<ForumApiClient.Tag> tags,
                                      boolean dark) {
        container.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        int[] lightBackgrounds = {0xFFEAF3FF, 0xFFF1ECFF, 0xFFFFF1E6, 0xFFFFEAF1};
        int[] lightTexts = {0xFF3477BD, 0xFF7451A8, 0xFFA9682C, 0xFFB65A7A};
        int shown = 0;
        for (ForumApiClient.Tag tag : tags) {
            if (tag == null || TextUtils.isEmpty(tag.name)) continue;
            int index = (tag.name.hashCode() & 0x7fffffff) % lightBackgrounds.length;
            int background = dark ? blend(lightTexts[index], 0xFF17181B, 0.78f)
                    : lightBackgrounds[index];
            TextView chip = text(container.getContext(), tag.name, 9.5f,
                    dark ? 0xFFD7DBE1 : lightTexts[index], true);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setMaxWidth(dp(container.getContext(), 72));
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(dp(container.getContext(), 6), 0, dp(container.getContext(), 6), 0);
            chip.setBackground(roundRect(container.getContext(), background, 9));
            if (tag.id > 0L) {
                chip.setOnClickListener(v -> container.getContext().startActivity(
                        ForumBoardActivity.createTagIntent(container.getContext(), tag.id, tag.name)));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(container.getContext(), 18));
            if (shown > 0) params.leftMargin = dp(container.getContext(), 4);
            container.addView(chip, params);
            if (++shown >= 2) break;
        }
        container.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    private static void setCompoundIcon(TextView view, int resId, int sizeDp, int color) {
        Drawable drawable = AppCompatResources.getDrawable(view.getContext(), resId);
        if (drawable == null) return;
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, color);
        int size = dp(view.getContext(), sizeDp);
        drawable.setBounds(0, 0, size, size);
        view.setCompoundDrawables(drawable, null, null, null);
    }

    private static void setImageIcon(AppCompatImageView view, int resId, int sizeDp, int color) {
        Drawable drawable = AppCompatResources.getDrawable(view.getContext(), resId);
        if (drawable == null) return;
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, color);
        int size = dp(view.getContext(), sizeDp);
        drawable.setBounds(0, 0, size, size);
        view.setImageDrawable(drawable);
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

    private static Drawable selectableBackground(Context context) {
        TypedValue out = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        return AppCompatResources.getDrawable(context, out.resourceId);
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

    private static long normalizeTime(long value) {
        return value > 0 && value < 10_000_000_000L ? value * 1000L : value;
    }

    private static long stableStringId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static String topicSignature(ForumApiClient.Topic topic) {
        if (topic == null) return "";
        ForumApiClient.User user = topic.user;
        ForumApiClient.Category category = topic.category;
        return safe(topic.id) + '|' + safe(topic.title) + '|' + topic.type + '|'
                + topic.createTime + '|' + topic.lastCommentTime + '|' + topic.commentCount + '|'
                + topic.likeCount + '|' + topic.sticky + '|' + topic.recommend + '|'
                + safe(topic.qaStatus) + '|' + topic.acceptedCommentId + '|'
                + topic.bountyScore + '|' + voteSignature(topic.vote) + '|'
                + safe(user == null ? null : user.nickname) + '|'
                + safe(user == null ? null : user.smallAvatar) + '|'
                + safe(category == null ? null : category.name) + '|'
                + tagSignature(topic.tags);
    }

    private static String voteSignature(@Nullable ForumApiClient.Vote vote) {
        if (vote == null) return "";
        return vote.id + ":" + safe(vote.title) + ":" + vote.type + ":"
                + vote.voteNum + ":" + vote.optionCount + ":" + vote.voteCount + ":"
                + vote.expired + ":" + vote.voted;
    }

    private static String tagSignature(@Nullable List<ForumApiClient.Tag> tags) {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (ForumApiClient.Tag tag : tags) {
            if (tag == null || TextUtils.isEmpty(tag.name)) continue;
            result.append(tag.id).append(':').append(tag.name).append(';');
        }
        return result.toString();
    }

    private static String articleSignature(ForumApiClient.Article article) {
        if (article == null) return "";
        ForumApiClient.User user = article.user;
        return article.id + "|" + safe(article.title) + '|' + article.createTime + '|'
                + article.commentCount + '|' + article.likeCount + '|'
                + safe(user == null ? null : user.nickname) + '|'
                + safe(user == null ? null : user.smallAvatar);
    }

    private static String categoryInitial(String name) {
        if (TextUtils.isEmpty(name)) return ForumText.get(R.string.forum_board_initial);
        return name.substring(0, name.offsetByCodePoints(0, 1));
    }

    private static String categoryHint(String name) {
        if (TextUtils.isEmpty(name)) return ForumText.get(R.string.forum_enter_discussion);
        if (name.contains("口语")) return ForumText.get(R.string.forum_desc_speaking);
        if (name.contains("贸易")) return ForumText.get(R.string.forum_desc_trade);
        if (name.contains("影视")) return ForumText.get(R.string.forum_desc_media);
        if (name.contains("游戏")) return ForumText.get(R.string.forum_desc_games);
        if (name.contains("工作") || name.contains("求职"))
            return ForumText.get(R.string.forum_desc_jobs);
        if (name.contains("学习交流")) return ForumText.get(R.string.forum_desc_learning_exchange);
        if (name.contains("学习")) return ForumText.get(R.string.forum_desc_learning);
        if (name.contains("闲聊")) return ForumText.get(R.string.forum_desc_chat);
        return ForumText.get(R.string.forum_enter_board_discussion);
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
        return ForumText.relativeTime(value);
    }


}
