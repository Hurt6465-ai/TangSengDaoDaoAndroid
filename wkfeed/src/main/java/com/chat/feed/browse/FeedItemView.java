package com.chat.feed.browse;

import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.ui.PlayerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
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
import com.chat.uikit.chat.manager.WKIMUtils;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.List;

public class FeedItemView extends android.widget.FrameLayout {
    private View imagePagerHost;
    private ViewPager2 imagePager;
    private PlayerView playerView;
    private ImageView coverView;
    private ProgressBar videoLoading;
    private TextView indicatorTv;
    private AvatarView avatarView;
    private ImageButton likeBtn;
    private TextView likeCountTv;
    private ImageButton commentBtn;
    private TextView commentCountTv;
    private TextView nameTv;
    private TextView metaTv;
    private TextView descTv;
    private TextView actionBtn;
    private ImageView bigHeartView;
    private View actionPanel;
    private View descPanel;
    private FeedBean feed;
    private boolean active;
    private GestureDetector gestureDetector;
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
        imagePagerHost = findViewById(R.id.imagePagerHost);
        imagePager = findViewById(R.id.imagePager);
        playerView = findViewById(R.id.playerView);
        coverView = findViewById(R.id.coverView);
        videoLoading = findViewById(R.id.videoLoading);
        indicatorTv = findViewById(R.id.imageIndicatorTv);
        avatarView = findViewById(R.id.avatarView);
        likeBtn = findViewById(R.id.likeBtn);
        likeCountTv = findViewById(R.id.likeCountTv);
        commentBtn = findViewById(R.id.commentBtn);
        commentCountTv = findViewById(R.id.commentCountTv);
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
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                toggleLike(true);
                showHeart(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (feed != null && feed.isVideo()) {
                    if (FeedPlayerManager.getInstance().isAttachedFeed(feed.stableKey())) {
                        FeedPlayerManager.getInstance().toggle();
                    } else {
                        play();
                    }
                    return true;
                }
                return false;
            }
        });
        likeBtn.setOnClickListener(v -> toggleLike(false));
        commentBtn.setOnClickListener(v -> showComments());
        actionBtn.setOnClickListener(v -> onActionClick());
        avatarView.setOnClickListener(v -> openProfile());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // GestureDetector 放在 dispatchTouchEvent 里“旁路监听”，不消费事件。
        // 这样视频单击/双击更稳定，同时不会抢多图 ViewPager2 的左右滑手势，
        // 也不会影响右侧点赞/评论按钮、底部资料区和打招呼按钮。
        if (gestureDetector != null && shouldHandleMediaGesture(event)) {
            try { gestureDetector.onTouchEvent(event); } catch (Exception ignored) {}
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean shouldHandleMediaGesture(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        return !isPointInside(actionPanel, x, y) && !isPointInside(descPanel, x, y);
    }

    private boolean isPointInside(View view, float x, float y) {
        return view != null && view.getVisibility() == VISIBLE
                && x >= view.getLeft() && x <= view.getRight()
                && y >= view.getTop() && y <= view.getBottom();
    }

    public void bind(FeedBean item) {
        // 不能在 bind 里无脑 pause 全局播放器。ViewPager2 预加载下一页时也会 bind，
        // 如果这里 pause，会把当前正在播放的页误停。
        recycleMediaOnly();
        feed = item;
        if (item == null) return;
        FeedUser user = item.user;
        nameTv.setText("@" + item.userName());
        metaTv.setText(buildMeta(item));
        descTv.setText(item.displayTitle());
        likeCountTv.setText(formatCount(item.like_count));
        commentCountTv.setText(formatCount(item.comment_count));
        likeBtn.setImageResource(item.liked == 1 ? R.drawable.ic_feed_heart_fill : R.drawable.ic_feed_heart);
        actionBtn.setEnabled(true);
        actionBtn.setAlpha(1f);
        if (user != null) {
            bindAvatarSafely(user);
            actionBtn.setText(user.follow == 1 ? R.string.feed_send_message : R.string.feed_say_hello);
        } else {
            actionBtn.setText(R.string.feed_say_hello);
        }
        bindMedia(item);
    }

    private void bindMedia(FeedBean item) {
        List<FeedMedia> media = item.safeMedia();
        boolean video = item.isVideo();
        playerView.setVisibility(video ? VISIBLE : GONE);
        coverView.setVisibility(video ? VISIBLE : GONE);
        videoLoading.setVisibility(GONE);
        imagePagerHost.setVisibility(video ? GONE : VISIBLE);
        indicatorTv.setVisibility(!video && media.size() > 1 ? VISIBLE : GONE);
        if (video) {
            FeedMedia first = item.firstMedia();
            if (first != null) {
                Glide.with(coverView).load(first.thumbUrl()).centerCrop().dontAnimate().into(coverView);
            }
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
            }

            @Override
            public void onEnded(@Nullable String id) {
                if (!isCurrentPlayback(id)) return;
                videoLoading.setVisibility(GONE);
            }

            @Override
            public void onError(@Nullable String id, @Nullable Throwable error) {
                if (!isCurrentPlayback(id)) return;
                coverView.setVisibility(VISIBLE);
                videoLoading.setVisibility(GONE);
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
        if (target.follow == 1) {
            try {
                FragmentActivity activity = findFragmentActivity(getContext());
                if (activity == null) {
                    Toast.makeText(getContext(), R.string.feed_action_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }
                WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(activity, target.uid, WKChannelType.PERSONAL, 0, false));
            } catch (Throwable ignored) {
                Toast.makeText(getContext(), R.string.feed_action_unavailable, Toast.LENGTH_SHORT).show();
            }
        } else {
            try {
                actionBtn.setEnabled(false);
                FriendModel.getInstance().applyAddFriend(target.uid, target.vercode, getResources().getString(R.string.feed_say_hello), (code, msg) -> {
                    if (feed == null || feed.user != target) return;
                    if (code == HttpResponseCode.success) {
                        actionBtn.setText(R.string.feed_hello_sent);
                        actionBtn.setAlpha(0.65f);
                        actionBtn.setEnabled(false);
                    } else {
                        actionBtn.setEnabled(true);
                        Toast.makeText(getContext(), TextUtils.isEmpty(msg) ? getResources().getString(R.string.feed_action_unavailable) : msg, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Throwable ignored) {
                actionBtn.setEnabled(true);
                Toast.makeText(getContext(), R.string.feed_action_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void bindAvatarSafely(FeedUser user) {
        if (user == null) return;
        try {
            avatarView.showAvatarUrl(user.avatar, user.avatar_cache_key, user.name, user.uid);
        } catch (Throwable ignored) {
            avatarView.showDefaultAvatar(user.name, user.uid);
        }
    }

    private void openProfile() {
        if (feed == null || feed.user == null || TextUtils.isEmpty(feed.user.uid)) return;
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            route.getMethod("open", Context.class, String.class).invoke(null, getContext(), feed.user.uid);
            return;
        } catch (Exception ignored) {
        }
        try {
            EndpointManager.getInstance().invoke(EndpointSID.userDetailView, feed.user.uid);
        } catch (Throwable ignored) {
        }
    }
}
