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
import android.view.animation.AccelerateDecelerateInterpolator;
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
import com.chat.base.config.WKConfig;
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
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FeedItemView extends android.widget.FrameLayout {
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
    private View bottomMask;
    private final Random animationRandom = new Random();
    private FeedBean feed;
    private boolean active;
    private ViewPager2.OnPageChangeCallback imagePageCallback;
    private String activeFeedKey = "";
    private long activeStartAtMs;
    private boolean exposureReported;
    private boolean completeReported;


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
        bottomMask = findViewById(R.id.bottomMask);

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

        likeBtn.setOnClickListener(v -> { scaleClick(likeBtn); toggleLike(false); });
        commentBtn.setOnClickListener(v -> { scaleClick(commentBtn); showComments(false); });
        shareBtn.setOnClickListener(v -> { scaleClick(shareBtn); showShareMenu(); });
        actionBtn.setOnClickListener(v -> onActionClick());
        avatarView.setOnClickListener(v -> openProfile());
        nameTv.setOnClickListener(v -> openProfile());
        descTv.setOnClickListener(v -> showComments(true));
    }

    private boolean isPointInside(View view, float x, float y) {
        return view != null && view.getVisibility() == VISIBLE
                && x >= view.getLeft() && x <= view.getRight()
                && y >= view.getTop() && y <= view.getBottom();
    }

    public void bind(FeedBean item) {
        recycleMediaOnly();
        resetEventState();
        applyCommentTransform(0f);
        feed = item;
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
            boolean followed = user.follow == 1;
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
        if (TextUtils.isEmpty(safeText)) {
            descTv.setText("");
            return;
        }
        // 抖音式：信息流里只折叠展示；点击文案打开评论窗口，顶部展示完整文案。
        descTv.setText(safeText);
        descTv.setMaxLines(4);
        descTv.setEllipsize(TextUtils.TruncateAt.END);
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
        if (this.active == active && active) return;
        if (!active) reportExitEvent(false);
        this.active = active;
        if (active) {
            activeFeedKey = feed == null ? "" : feed.stableKey();
            activeStartAtMs = System.currentTimeMillis();
            exposureReported = false;
            completeReported = false;
            reportExposeIfNeeded();
            if (feed != null && feed.isVideo()) play();
        } else {
            pause();
        }
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
                reportExposeIfNeeded();
            }

            @Override
            public void onEnded(@Nullable String id) {
                if (!isCurrentPlayback(id)) return;
                videoLoading.setVisibility(GONE);
                playPauseView.setVisibility(VISIBLE);
                reportExitEvent(true);
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

    private void resetEventState() {
        activeFeedKey = "";
        activeStartAtMs = 0L;
        exposureReported = false;
        completeReported = false;
    }

    private void reportExposeIfNeeded() {
        if (feed == null || exposureReported || !active) return;
        String key = feed.stableKey();
        if (TextUtils.isEmpty(key)) return;
        exposureReported = true;
        Map<String, Object> body = new HashMap<>();
        body.put("media_type", feed.isVideo() ? "video" : "image");
        FeedModel.getInstance().event(key, "expose", body, null);
    }

    private void reportExitEvent(boolean completed) {
        if (feed == null || TextUtils.isEmpty(activeFeedKey)) return;
        if (!activeFeedKey.equals(feed.stableKey())) return;
        long watchMs = activeStartAtMs <= 0 ? 0L : Math.max(0L, System.currentTimeMillis() - activeStartAtMs);
        if (completed) {
            completeReported = true;
            reportWatchEvent("complete", Math.max(watchMs, mediaDurationMs()), 100);
            resetEventState();
            return;
        }
        if (watchMs < 800L) {
            resetEventState();
            return;
        }
        long duration = mediaDurationMs();
        int percent = duration > 0 ? (int) Math.min(100L, Math.max(0L, watchMs * 100L / duration)) : 0;
        if (feed.isVideo() && !completeReported && watchMs <= 1500L && percent <= 10) {
            reportWatchEvent("skip", watchMs, percent);
        } else {
            reportWatchEvent("watch", watchMs, percent);
        }
        resetEventState();
    }

    private long mediaDurationMs() {
        FeedMedia media = feed == null ? null : feed.firstMedia();
        return media == null ? 0L : Math.max(0L, media.duration_ms);
    }

    private void reportWatchEvent(String type, long watchMs, int percent) {
        if (feed == null || TextUtils.isEmpty(type)) return;
        String key = feed.stableKey();
        if (TextUtils.isEmpty(key)) return;
        Map<String, Object> body = new HashMap<>();
        body.put("watch_ms", Math.max(0L, watchMs));
        body.put("duration_ms", mediaDurationMs());
        body.put("percent", Math.max(0, Math.min(100, percent)));
        body.put("media_type", feed.isVideo() ? "video" : "image");
        FeedModel.getInstance().event(key, type, body, null);
    }

    public void recycle() {
        reportExitEvent(false);
        pause();
        recycleMediaOnly();
        resetEventState();
        applyCommentTransform(0f);
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
        if (bigHeartView == null) return;
        bigHeartView.animate().cancel();
        int width = bigHeartView.getWidth() > 0 ? bigHeartView.getWidth() : dp(120);
        int height = bigHeartView.getHeight() > 0 ? bigHeartView.getHeight() : dp(120);
        float rotation = -12f + animationRandom.nextFloat() * 24f;
        bigHeartView.setVisibility(VISIBLE);
        bigHeartView.setX(x - width / 2f);
        bigHeartView.setY(y - height / 2f);
        bigHeartView.setTranslationY(0f);
        bigHeartView.setRotation(rotation);
        bigHeartView.setAlpha(0.98f);
        bigHeartView.setScaleX(0.42f);
        bigHeartView.setScaleY(0.42f);
        bigHeartView.animate()
                .alpha(0f)
                .scaleX(1.72f)
                .scaleY(1.72f)
                .translationY(-dp(42))
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(620)
                .withEndAction(() -> {
                    bigHeartView.setVisibility(GONE);
                    bigHeartView.setTranslationY(0f);
                })
                .start();
    }

    private void showComments(boolean fromCaption) {
        if (feed == null) return;
        FragmentActivity activity = findFragmentActivity(getContext());
        if (activity == null) return;
        String caption = feed.displayTitle();
        FeedUser user = feed.user;
        FeedCommentBottomSheet sheet = FeedCommentBottomSheet.newInstance(
                feed.stableKey(),
                feed.comment_count,
                user == null ? "" : user.uid,
                feed.userName(),
                user == null ? "" : user.avatar,
                user == null ? "" : user.avatar_cache_key,
                user == null ? "" : user.country_code,
                user != null && user.follow == 1,
                caption
        );
        sheet.setOnCommentSentListener(delta -> {
            if (feed == null) return;
            feed.comment_count += delta;
            if (feed.comment_count < 0) feed.comment_count = 0;
            commentCountTv.setText(formatCount(feed.comment_count));
        });
        sheet.setOnSheetTransformListener(new FeedCommentBottomSheet.OnSheetTransformListener() {
            @Override
            public void onSheetProgress(float progress) {
                applyCommentTransform(progress);
            }

            @Override
            public void onSheetClosed() {
                applyCommentTransform(0f);
            }
        });
        sheet.show(activity.getSupportFragmentManager(), "feed_comments");
    }

    private void applyCommentTransform(float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        float scale = 1f - 0.10f * progress;
        float translateY = -getHeight() * 0.045f * progress;
        applyMediaTransform(imagePagerHost, scale, translateY);
        applyMediaTransform(playerView, scale, translateY);
        applyMediaTransform(coverView, scale, translateY);
        float overlayAlpha = 1f - 0.82f * progress;
        applyOverlayAlpha(actionPanel, overlayAlpha);
        applyOverlayAlpha(descPanel, overlayAlpha);
        applyOverlayAlpha(bottomMask, 1f - 0.72f * progress);
    }

    private void applyMediaTransform(View view, float scale, float translateY) {
        if (view == null) return;
        view.animate().cancel();
        view.setPivotX(view.getWidth() / 2f);
        view.setPivotY(0f);
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setTranslationY(translateY);
    }

    private void applyOverlayAlpha(View view, float alpha) {
        if (view == null) return;
        view.animate().cancel();
        view.setAlpha(Math.max(0f, Math.min(1f, alpha)));
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
        final boolean nextFollow = target.follow != 1;
        scaleClick(actionBtn);
        actionBtn.setEnabled(false);
        FeedModel.getInstance().setFollow(target.uid, nextFollow, new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (feed == null || feed.user != target) return;
                target.follow = nextFollow ? 1 : 0;
                actionBtn.setText(nextFollow ? R.string.feed_followed : R.string.feed_follow);
                actionBtn.setAlpha(nextFollow ? 0.72f : 1f);
                actionBtn.setEnabled(true);
            }

            @Override
            public void onFail(int code, String msg) {
                actionBtn.setEnabled(true);
                Toast.makeText(getContext(), TextUtils.isEmpty(msg) ? getResources().getString(R.string.feed_action_unavailable) : msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scaleClick(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.animate().scaleX(0.86f).scaleY(0.86f).setDuration(80)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(110).start())
                .start();
    }

    private void showShareMenu() {
        if (feed == null) return;
        Dialog dialog = new Dialog(getContext());
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackgroundResource(R.drawable.bg_feed_share_dialog);
        root.addView(makeShareMenuItem(getResources().getString(R.string.feed_share_to_friend), v -> {
            dialog.dismiss();
            shareToSystem();
            FeedModel.getInstance().share(feed.stableKey(), new IRequestResultListener<CommonResponse>() { @Override public void onSuccess(CommonResponse result) {} @Override public void onFail(int code, String msg) {} });
        }));
        root.addView(makeShareMenuItem(getResources().getString(R.string.feed_copy_link), v -> {
            dialog.dismiss();
            copyShareLink();
            FeedModel.getInstance().share(feed.stableKey(), new IRequestResultListener<CommonResponse>() { @Override public void onSuccess(CommonResponse result) {} @Override public void onFail(int code, String msg) {} });
        }));
        if (isCreator()) {
            root.addView(makeShareMenuItem(getResources().getString(R.string.feed_delete), v -> {
                dialog.dismiss();
                requestDeleteFeed();
            }));
        }
        root.addView(makeShareMenuItem(getResources().getString(R.string.feed_report), v -> {
            dialog.dismiss();
            FeedModel.getInstance().report(feed.stableKey(), "normal", new IRequestResultListener<CommonResponse>() {
                @Override public void onSuccess(CommonResponse result) {
                    Toast.makeText(getContext(), R.string.feed_report_received, Toast.LENGTH_SHORT).show();
                }
                @Override public void onFail(int code, String msg) {
                    Toast.makeText(getContext(), TextUtils.isEmpty(msg) ? getResources().getString(R.string.feed_action_unavailable) : msg, Toast.LENGTH_SHORT).show();
                }
            });
        }));
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.48f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(72), dp(360));
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            window.setAttributes(lp);
        }
    }

    private boolean isCreator() {
        return feed != null && feed.user != null
                && !TextUtils.isEmpty(feed.user.uid)
                && feed.user.uid.equals(WKConfig.getInstance().getUid());
    }

    private void requestDeleteFeed() {
        if (feed == null) return;
        FeedModel.getInstance().delete(feed.stableKey(), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                Toast.makeText(getContext(), R.string.feed_delete_requested, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFail(int code, String msg) {
                Toast.makeText(getContext(), TextUtils.isEmpty(msg) ? getResources().getString(R.string.feed_delete_not_ready) : msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private TextView makeShareMenuItem(String text, View.OnClickListener listener) {
        TextView item = new TextView(getContext());
        item.setText(text);
        item.setTextColor(0xFF111827);
        item.setTextSize(16f);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
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
