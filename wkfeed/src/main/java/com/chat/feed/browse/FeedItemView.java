package com.chat.feed.browse;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.ui.components.AvatarView;
import com.chat.feed.FeedModel;
import com.chat.feed.R;
import com.chat.feed.comment.FeedCommentBottomSheet;
import com.chat.feed.model.FeedBean;
import com.chat.feed.model.FeedMedia;
import com.chat.feed.model.FeedUser;
import com.chat.feed.player.FeedPlayerManager;
import com.chat.uikit.contacts.service.FriendModel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeedItemView extends android.widget.FrameLayout {
    private static final Set<String> LOCAL_FOLLOWED_UIDS = new HashSet<>();

    private FeedGestureLayout feedRoot;
    private View imagePagerHost;
    private ViewPager2 imagePager;
    private PlayerView playerView;
    private ImageView coverView;
    private ProgressBar videoLoading;
    private TextView playPauseView;
    private TextView indicatorTv;
    private AvatarView avatarView;
    private ImageButton likeBtn;
    private TextView likeCountTv;
    private ImageButton commentBtn;
    private TextView commentCountTv;
    private ImageButton shareBtn;
    private TextView nameTv;
    private TextView metaTv;
    private TextView descTv;
    private TextView actionBtn;
    private ImageView bigHeartView;
    private View actionPanel;
    private View descPanel;
    private FeedBean feed;
    private boolean active;
    private boolean descExpanded;
    private ViewPager2.OnPageChangeCallback imagePageCallback;


    public FeedItemView(@NonNull Context context) {
        this(context, null);
    }

    public FeedItemView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_feed_item, this, true);
        feedRoot = findViewById(R.id.feedRoot);
        imagePagerHost = findViewById(R.id.imagePagerHost);
        imagePager = findViewById(R.id.imagePager);
        playerView = findViewById(R.id.playerView);
        coverView = findViewById(R.id.coverView);
        videoLoading = findViewById(R.id.videoLoading);
        playPauseView = findViewById(R.id.playPauseView);
        indicatorTv = findViewById(R.id.imageIndicatorTv);
        avatarView = findViewById(R.id.avatarView);
        likeBtn = findViewById(R.id.likeBtn);
        likeCountTv = findViewById(R.id.likeCountTv);
        commentBtn = findViewById(R.id.commentBtn);
        commentCountTv = findViewById(R.id.commentCountTv);
        shareBtn = findViewById(R.id.shareBtn);
        nameTv = findViewById(R.id.nameTv);
        metaTv = findViewById(R.id.metaTv);
        descTv = findViewById(R.id.descTv);
        actionBtn = findViewById(R.id.actionBtn);
        bigHeartView = findViewById(R.id.bigHeartView);
        actionPanel = findViewById(R.id.actionPanel);
        descPanel = findViewById(R.id.descPanel);

        imagePager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        imagePager.setOffscreenPageLimit(1);
        imagePager.setSaveEnabled(false);
        imagePager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        View innerPagerRecycler = imagePager.getChildCount() > 0 ? imagePager.getChildAt(0) : null;
        if (innerPagerRecycler instanceof RecyclerView) {
            innerPagerRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
            RecyclerView.ItemAnimator animator = ((RecyclerView) innerPagerRecycler).getItemAnimator();
            if (animator != null) animator.setChangeDuration(0);
        }

        if (feedRoot != null) {
            feedRoot.setGestureListener(new FeedGestureLayout.GestureListener() {
                @Override
                public boolean isGestureEnabled() {
                    return feed != null;
                }

                @Override
                public boolean shouldIgnoreGesture(float x, float y) {
                    return isPointInside(actionPanel, x, y) || isPointInside(descPanel, x, y);
                }

                @Override
                public void onSingleTap() {
                    if (feed == null || !feed.isVideo()) return;
                    if (FeedPlayerManager.getInstance().isAttachedFeed(feed.stableKey())) {
                        FeedPlayerManager.getInstance().toggle();
                        postDelayed(() -> syncPlayPauseOverlay(true), 80);
                    } else {
                        play();
                    }
                }

                @Override
                public void onDoubleTap(float x, float y) {
                    toggleLike(true);
                    showHeart(x, y);
                }
            });
        }

        likeBtn.setOnClickListener(v -> toggleLike(false));
        commentBtn.setOnClickListener(v -> showComments());
        shareBtn.setOnClickListener(v -> showShareMenu());
        actionBtn.setOnClickListener(v -> onActionClick());
        avatarView.setOnClickListener(v -> openProfile());
        nameTv.setOnClickListener(v -> openProfile());
        descTv.setOnClickListener(v -> toggleDescExpand());
    }

    private boolean isPointInside(View view, float x, float y) {
        return view != null && view.getVisibility() == VISIBLE
                && x >= view.getLeft() && x <= view.getRight()
                && y >= view.getTop() && y <= view.getBottom();
    }

    public void bind(FeedBean item) {
        recycleMediaOnly();
        feed = item;
        descExpanded = false;
        if (item == null) return;
        FeedUser user = item.user;
        nameTv.setText("@" + item.userName());
        metaTv.setText(buildMeta(item));
        bindDesc(item.displayTitle());
        likeCountTv.setText(formatCount(item.like_count));
        commentCountTv.setText(formatCount(item.comment_count));
        likeBtn.setImageResource(item.liked == 1 ? R.drawable.ic_feed_heart_fill : R.drawable.ic_feed_heart);
        if (user != null) {
            bindAvatarSafely(user);
            boolean followed = isLocalFollowed(user);
            actionBtn.setEnabled(!TextUtils.isEmpty(user.uid));
            actionBtn.setAlpha(followed ? 0.72f : 1f);
            actionBtn.setText(followed ? R.string.feed_followed : R.string.feed_follow);
        } else {
            avatarView.showDefaultAvatar(item.userName(), item.userName());
            actionBtn.setEnabled(false);
            actionBtn.setAlpha(0.65f);
            actionBtn.setText(R.string.feed_follow);
        }
        bindMedia(item);
    }

    private void bindDesc(String text) {
        String safeText = text == null ? "" : text.trim();
        descTv.setVisibility(TextUtils.isEmpty(safeText) ? GONE : VISIBLE);
        descTv.setText(safeText);
        descTv.setMaxLines(4);
        descTv.setEllipsize(TextUtils.TruncateAt.END);
    }

    private void toggleDescExpand() {
        if (descTv == null || descTv.getVisibility() != VISIBLE) return;
        descExpanded = !descExpanded;
        descTv.setMaxLines(descExpanded ? Integer.MAX_VALUE : 4);
        descTv.setEllipsize(descExpanded ? null : TextUtils.TruncateAt.END);
    }

    private void bindMedia(FeedBean item) {
        List<FeedMedia> media = item.safeMedia();
        boolean video = item.isVideo();
        playerView.setVisibility(video ? VISIBLE : GONE);
        coverView.setVisibility(video ? VISIBLE : GONE);
        videoLoading.setVisibility(GONE);
        playPauseView.setVisibility(GONE);
        imagePagerHost.setVisibility(video ? GONE : VISIBLE);
        indicatorTv.setVisibility(!video && media.size() > 1 ? VISIBLE : GONE);
        if (video) {
            FeedMedia first = item.firstMedia();
            if (first != null) Glide.with(coverView).load(first.thumbUrl()).centerCrop().dontAnimate().into(coverView);
            FeedPlayerManager.getInstance().detach(playerView);
            if (active) play();
        } else {
            FeedImageAdapter adapter = new FeedImageAdapter(media);
            imagePager.setAdapter(adapter);
            imagePager.setCurrentItem(0, false);
            updateIndicator(0, media.size());
            imagePageCallback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateIndicator(position, media.size());
                    FeedMedia next = position + 1 < media.size() ? media.get(position + 1) : null;
                    if (next != null) Glide.with(FeedItemView.this).load(next.displayUrl()).preload();
                }
            };
            imagePager.registerOnPageChangeCallback(imagePageCallback);
        }
    }

    private void updateIndicator(int position, int total) {
        if (total <= 1) {
            indicatorTv.setVisibility(GONE);
            return;
        }
        indicatorTv.setVisibility(VISIBLE);
        indicatorTv.setText(getResources().getString(R.string.feed_image_indicator, position + 1, total));
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) play();
        else pause();
    }

    public void play() {
        if (feed == null || !feed.isVideo()) return;
        FeedMedia media = feed.firstMedia();
        String playUrl = media == null ? "" : media.playUrl();
        if (TextUtils.isEmpty(playUrl)) return;
        coverView.setVisibility(VISIBLE);
        videoLoading.setVisibility(VISIBLE);
        playPauseView.setVisibility(GONE);
        String feedId = feed.stableKey();
        FeedPlayerManager.getInstance().attach(getContext(), playerView, feedId, playUrl, true, new FeedPlayerManager.PlaybackCallback() {
            @Override
            public void onBuffering(@Nullable String id) {
                if (!isCurrentPlayback(id)) return;
                videoLoading.setVisibility(VISIBLE);
            }

            @Override
            public void onReady(@Nullable String id) {
                if (!isCurrentPlayback(id)) return;
                coverView.setVisibility(GONE);
                videoLoading.setVisibility(GONE);
                playPauseView.setVisibility(GONE);
            }

            @Override
            public void onEnded(@Nullable String id) {
                if (!isCurrentPlayback(id)) return;
                videoLoading.setVisibility(GONE);
                playPauseView.setVisibility(VISIBLE);
            }

            @Override
            public void onError(@Nullable String id, @Nullable Throwable error) {
                if (!isCurrentPlayback(id)) return;
                coverView.setVisibility(VISIBLE);
                videoLoading.setVisibility(GONE);
                playPauseView.setVisibility(VISIBLE);
            }
        });
    }

    private boolean isCurrentPlayback(@Nullable String id) {
        return feed != null && !TextUtils.isEmpty(id) && id.equals(feed.stableKey());
    }

    public void pause() {
        if (feed != null && feed.isVideo()
                && FeedPlayerManager.getInstance().isAttachedView(playerView)
                && FeedPlayerManager.getInstance().isAttachedFeed(feed.stableKey())) {
            FeedPlayerManager.getInstance().stopAndDetach(playerView);
        } else {
            FeedPlayerManager.getInstance().detach(playerView);
        }
        if (videoLoading != null) videoLoading.setVisibility(GONE);
        if (coverView != null && feed != null && feed.isVideo()) coverView.setVisibility(VISIBLE);
        if (playPauseView != null && feed != null && feed.isVideo()) playPauseView.setVisibility(VISIBLE);
    }

    private void syncPlayPauseOverlay(boolean userToggle) {
        if (feed == null || !feed.isVideo() || playPauseView == null) return;
        boolean playing = FeedPlayerManager.getInstance().isPlaying();
        playPauseView.setVisibility(playing ? GONE : VISIBLE);
        if (playing && coverView != null) coverView.setVisibility(GONE);
        if (userToggle && !playing && videoLoading != null) videoLoading.setVisibility(GONE);
    }

    public void recycle() {
        pause();
        recycleMediaOnly();
        feed = null;
    }

    private void recycleMediaOnly() {
        unregisterImageCallback();
        if (coverView != null) Glide.with(coverView).clear(coverView);
        if (imagePager != null) imagePager.setAdapter(null);
        if (videoLoading != null) videoLoading.setVisibility(GONE);
        if (playPauseView != null) playPauseView.setVisibility(GONE);
    }

    private void unregisterImageCallback() {
        if (imagePager != null && imagePageCallback != null) {
            try { imagePager.unregisterOnPageChangeCallback(imagePageCallback); } catch (Exception ignored) {}
            imagePageCallback = null;
        }
    }

    private String buildMeta(FeedBean item) {
        StringBuilder sb = new StringBuilder();
        FeedUser user = item.user;
        if (user != null) {
            if (user.age > 0) sb.append(user.age).append("  ");
            if (!TextUtils.isEmpty(user.native_languages)) sb.append(user.native_languages).append("  ");
        }
        if (item.distance_meters > 0 && item.distance_meters <= 70000) {
            int km = bucketDistance(item.distance_meters);
            sb.append(getResources().getString(R.string.feed_distance_within, km)).append("  ");
        }
        String activeLabel = activeLabel(item.last_active_at);
        if (!TextUtils.isEmpty(activeLabel)) sb.append(activeLabel);
        return sb.toString().trim();
    }

    private int bucketDistance(int meters) {
        int km = Math.max(1, (int) Math.ceil(meters / 1000.0));
        if (km <= 5) return 5;
        if (km <= 10) return 10;
        if (km <= 30) return 30;
        return 70;
    }

    private String activeLabel(long lastActiveAt) {
        if (lastActiveAt <= 0) return "";
        long timeMs = lastActiveAt < 100000000000L ? lastActiveAt * 1000L : lastActiveAt;
        long diff = Math.max(0, System.currentTimeMillis() - timeMs);
        long min = diff / 60000L;
        if (min <= 1) return getResources().getString(R.string.feed_active_online);
        if (min <= 60) return getResources().getString(R.string.feed_active_minutes, (int) min);
        return "";
    }

    private String formatCount(int count) {
        if (count <= 0) return "0";
        if (count < 10000) return String.valueOf(count);
        return (count / 10000) + "w";
    }

    private void toggleLike(boolean forceLike) {
        if (feed == null) return;
        boolean target = forceLike || feed.liked != 1;
        if (feed.liked == 1 && forceLike) return;
        int oldLiked = feed.liked;
        int oldCount = feed.like_count;
        feed.liked = target ? 1 : 0;
        feed.like_count += target ? 1 : -1;
        if (feed.like_count < 0) feed.like_count = 0;
        likeBtn.setImageResource(target ? R.drawable.ic_feed_heart_fill : R.drawable.ic_feed_heart);
        likeCountTv.setText(formatCount(feed.like_count));
        FeedModel.getInstance().like(feed.stableKey(), target, new IRequestResultListener<CommonResponse>() {
            @Override public void onSuccess(CommonResponse result) {}
            @Override public void onFail(int code, String msg) {
                if (feed == null) return;
                feed.liked = oldLiked;
                feed.like_count = oldCount;
                likeBtn.setImageResource(feed.liked == 1 ? R.drawable.ic_feed_heart_fill : R.drawable.ic_feed_heart);
                likeCountTv.setText(formatCount(feed.like_count));
            }
        });
    }

    private void showHeart(float x, float y) {
        bigHeartView.animate().cancel();
        bigHeartView.setVisibility(VISIBLE);
        bigHeartView.setX(x - bigHeartView.getWidth() / 2f);
        bigHeartView.setY(y - bigHeartView.getHeight() / 2f);
        bigHeartView.setAlpha(1f);
        bigHeartView.setScaleX(0.6f);
        bigHeartView.setScaleY(0.6f);
        bigHeartView.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f).setDuration(520).withEndAction(() -> bigHeartView.setVisibility(GONE)).start();
    }

    private void showComments() {
        if (feed == null) return;
        FragmentActivity activity = findFragmentActivity(getContext());
        if (activity == null) return;
        FeedCommentBottomSheet sheet = FeedCommentBottomSheet.newInstance(feed.stableKey(), feed.comment_count);
        sheet.setOnCommentSentListener(delta -> {
            if (feed == null) return;
            feed.comment_count += delta;
            if (feed.comment_count < 0) feed.comment_count = 0;
            commentCountTv.setText(formatCount(feed.comment_count));
        });
        sheet.show(activity.getSupportFragmentManager(), "feed_comments");
    }

    private FragmentActivity findFragmentActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof FragmentActivity) return (FragmentActivity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private void onActionClick() {
        if (feed == null || feed.user == null || TextUtils.isEmpty(feed.user.uid)) return;
        final FeedUser target = feed.user;
        // 这里不能再把好友关系当作关注关系。当前后端还没有真正 feed_follow 表，
        // 前端先做本地关注态，避免“全是已关注”和按钮不可点。后端接入关注接口后，把这里替换成 /v1/feed/follow 即可。
        if (!isLocalFollowed(target)) {
            LOCAL_FOLLOWED_UIDS.add(target.uid);
            target.follow = 1;
            actionBtn.setText(R.string.feed_followed);
            actionBtn.setAlpha(0.72f);
            Toast.makeText(getContext(), R.string.feed_followed, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLocalFollowed(@Nullable FeedUser user) {
        return user != null && !TextUtils.isEmpty(user.uid) && LOCAL_FOLLOWED_UIDS.contains(user.uid);
    }

    private void showShareMenu() {
        if (feed == null) return;
        Dialog dialog = new Dialog(getContext());
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackgroundResource(R.drawable.bg_feed_glass_sheet);
        root.addView(makeShareMenuItem(getResources().getString(R.string.feed_share_to_friend), v -> {
            dialog.dismiss();
            shareToSystem();
        }));
        root.addView(makeShareMenuItem(getResources().getString(R.string.feed_copy_link), v -> {
            dialog.dismiss();
            copyShareLink();
        }));
        root.addView(makeShareMenuItem(getResources().getString(R.string.feed_report), v -> {
            dialog.dismiss();
            Toast.makeText(getContext(), R.string.feed_report_received, Toast.LENGTH_SHORT).show();
        }));
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = getResources().getDisplayMetrics().widthPixels - dp(32);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.y = dp(18);
            window.setAttributes(lp);
        }
    }

    private TextView makeShareMenuItem(String text, View.OnClickListener listener) {
        TextView item = new TextView(getContext());
        item.setText(text);
        item.setTextColor(0xFFFFFFFF);
        item.setTextSize(16f);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(2), 0, dp(2));
        item.setLayoutParams(lp);
        item.setOnClickListener(listener);
        return item;
    }

    private String buildShareLink() {
        if (feed == null) return "";
        String base = com.chat.base.config.WKApiConfig.baseUrl;
        if (TextUtils.isEmpty(base)) base = "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/web/feed/" + feed.stableKey();
    }

    private String buildShareText() {
        StringBuilder sb = new StringBuilder();
        if (feed != null) {
            sb.append("@").append(feed.userName());
            String title = feed.displayTitle();
            if (!TextUtils.isEmpty(title)) sb.append("\n").append(title);
            String link = buildShareLink();
            if (!TextUtils.isEmpty(link)) sb.append("\n").append(link);
        }
        return sb.toString();
    }

    private void shareToSystem() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, buildShareText());
            getContext().startActivity(Intent.createChooser(intent, getResources().getString(R.string.feed_share)));
        } catch (Throwable ignored) {
            Toast.makeText(getContext(), R.string.feed_action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyShareLink() {
        try {
            ClipboardManager manager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("feed", buildShareLink()));
            Toast.makeText(getContext(), R.string.feed_link_copied, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
            Toast.makeText(getContext(), R.string.feed_action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void bindAvatarSafely(FeedUser user) {
        if (user == null) return;
        try {
            if (!TextUtils.isEmpty(user.uid)) {
                avatarView.showAvatar(user.uid, WKChannelType.PERSONAL, user.avatar_cache_key);
            } else if (!TextUtils.isEmpty(user.avatar)) {
                avatarView.showAvatarUrl(user.avatar, user.avatar_cache_key, user.name, user.uid);
            } else {
                avatarView.showDefaultAvatar(user.name, user.uid);
            }
            if (!TextUtils.isEmpty(user.country_code)) {
                avatarView.showFlag(user.country_code);
            }
        } catch (Throwable ignored) {
            avatarView.showDefaultAvatar(user.name, user.uid);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openProfile() {
        if (feed == null || feed.user == null || TextUtils.isEmpty(feed.user.uid)) return;
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            route.getMethod("open", Context.class, String.class).invoke(null, getContext(), feed.user.uid);
            return;
        } catch (Exception ignored) {}
        try {
            EndpointManager.getInstance().invoke(EndpointSID.userDetailView, feed.user.uid);
        } catch (Throwable ignored) {}
    }
}
