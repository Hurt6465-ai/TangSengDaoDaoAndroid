package com.chat.feed.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.chat.feed.config.FeedConfig;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Feed 单播放器管理器。
 *
 * 规则：
 * 1. 全局只维护一个 Media3 ExoPlayer。
 * 2. 当前页进入屏幕中心时 attach 到当前 PlayerView。
 * 3. 当前页滑走时 pause + detach，不让多个页面同时播放。
 * 4. 封面/Loading 由 Player.Listener 的真实状态驱动，不再用固定延迟猜。
 */
public class FeedPlayerManager {
    private static final long MAX_CACHE_BYTES = 200L * 1024L * 1024L;
    private static volatile FeedPlayerManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService preloadExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean released;

    private ExoPlayer player;
    private SimpleCache cache;
    private CacheDataSource.Factory cacheDataSourceFactory;
    private String attachedFeedId;
    private PlayerView attachedPlayerView;
    private PlaybackCallback playbackCallback;
    private volatile CacheWriter currentPreloadWriter;

    private final Player.Listener internalListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            PlaybackCallback callback = playbackCallback;
            if (callback == null) return;
            if (playbackState == Player.STATE_BUFFERING) {
                callback.onBuffering(attachedFeedId);
            } else if (playbackState == Player.STATE_READY) {
                callback.onReady(attachedFeedId);
            } else if (playbackState == Player.STATE_ENDED) {
                callback.onEnded(attachedFeedId);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            PlaybackCallback callback = playbackCallback;
            if (callback != null) callback.onError(attachedFeedId, error);
        }
    };

    public interface PlaybackCallback {
        void onBuffering(@Nullable String feedId);
        void onReady(@Nullable String feedId);
        void onEnded(@Nullable String feedId);
        void onError(@Nullable String feedId, @Nullable Throwable error);
    }

    public static FeedPlayerManager getInstance() {
        if (instance == null) {
            synchronized (FeedPlayerManager.class) {
                if (instance == null) instance = new FeedPlayerManager();
            }
        }
        return instance;
    }

    private FeedPlayerManager() {}

    public void attach(Context context, PlayerView playerView, String feedId, String playUrl, boolean playWhenReady, @Nullable PlaybackCallback callback) {
        if (released) return;
        if (context == null || playerView == null || playUrl == null || playUrl.length() == 0) return;
        ExoPlayer p = ensurePlayer(context.getApplicationContext());
        if (p == null) return;
        if (attachedPlayerView != null && attachedPlayerView != playerView) {
            attachedPlayerView.setPlayer(null);
        }
        attachedPlayerView = playerView;
        playbackCallback = callback;
        playerView.setPlayer(p);
        if (!feedIdEquals(feedId) || p.getMediaItemCount() == 0) {
            attachedFeedId = feedId;
            p.setMediaItem(MediaItem.fromUri(Uri.parse(playUrl)));
            p.prepare();
            if (callback != null) callback.onBuffering(attachedFeedId);
        }
        p.setRepeatMode(Player.REPEAT_MODE_ONE);
        p.setPlayWhenReady(playWhenReady);
        if (playWhenReady) p.play();
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void toggle() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    public void pause() {
        if (player != null) player.pause();
    }

    public void detach(PlayerView playerView) {
        if (playerView != null) playerView.setPlayer(null);
        if (attachedPlayerView == playerView) {
            attachedPlayerView = null;
            playbackCallback = null;
        }
    }

    /**
     * 只允许当前真正 attached 的 PlayerView 停止全局播放器。
     * 非当前页 bind/recycle 时只能清掉自己的 PlayerView，不能误停当前页。
     */
    public void stopAndDetach(PlayerView playerView) {
        if (attachedPlayerView == playerView) {
            pause();
            detach(playerView);
        } else if (playerView != null) {
            playerView.setPlayer(null);
        }
    }

    public boolean isAttachedView(PlayerView playerView) {
        return attachedPlayerView == playerView;
    }

    /**
     * 轻量视频预缓存：只缓存下一条视频前 768KB，避免一次拉完整视频导致流量和磁盘压力过大。
     */
    public void preloadVideo(Context context, String playUrl) {
        if (context == null || playUrl == null || playUrl.length() == 0) return;
        if (released) return;
        Context appContext = context.getApplicationContext();
        ExecutorService executor = ensurePreloadExecutor();
        if (executor == null) return;
        executor.execute(() -> {
            if (released) return;
            CacheWriter writer = null;
            try {
                CacheDataSource.Factory factory = ensureCacheDataSourceFactory(appContext);
                if (factory == null || released) return;
                CacheDataSource dataSource = factory.createDataSource();
                DataSpec dataSpec = new DataSpec.Builder()
                        .setUri(Uri.parse(playUrl))
                        .setPosition(0)
                        .setLength(FeedConfig.VIDEO_PRELOAD_BYTES)
                        .build();
                writer = new CacheWriter(dataSource, dataSpec, null, null);
                currentPreloadWriter = writer;
                if (released) {
                    writer.cancel();
                    return;
                }
                writer.cache();
            } catch (Throwable ignored) {
                if (writer != null) writer.cancel();
                // 预加载失败不能影响主播放。
            } finally {
                if (currentPreloadWriter == writer) currentPreloadWriter = null;
            }
        });
    }

    /**
     * Activity 销毁时释放全局播放器。
     * 顺序很重要：先断开回调和 PlayerView，再取消预加载并等待线程结束，最后释放 cache。
     * 否则 CacheWriter 线程可能还在写 SimpleCache，cache.release() 后会触发异常。
     */
    public void release() {
        released = true;

        playbackCallback = null;
        attachedFeedId = null;
        if (attachedPlayerView != null) {
            try { attachedPlayerView.setPlayer(null); } catch (Exception ignored) {}
            attachedPlayerView = null;
        }

        CacheWriter writer = currentPreloadWriter;
        currentPreloadWriter = null;
        if (writer != null) {
            try { writer.cancel(); } catch (Exception ignored) {}
        }

        ExecutorService executor = preloadExecutor;
        preloadExecutor = null;
        if (executor != null) {
            try {
                executor.shutdownNow();
                executor.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        }

        if (player != null) {
            try {
                player.removeListener(internalListener);
                player.stop();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }

        cacheDataSourceFactory = null;
        if (cache != null) {
            try { cache.release(); } catch (Exception ignored) {}
            cache = null;
        }
    }

    /**
     * 新的 FeedBrowseActivity 进入时显式复活播放器管理器。
     * 不允许 ensurePlayer() 在销毁后的回调里偷偷把 released 改回 false。
     */
    public void prepareForUse() {
        released = false;
        ensurePreloadExecutor();
    }

    private boolean feedIdEquals(String feedId) {
        if (attachedFeedId == null) return feedId == null;
        return attachedFeedId.equals(feedId);
    }


    @Nullable
    private synchronized ExecutorService ensurePreloadExecutor() {
        if (released) return null;
        if (preloadExecutor == null || preloadExecutor.isShutdown() || preloadExecutor.isTerminated()) {
            preloadExecutor = Executors.newSingleThreadExecutor();
        }
        return preloadExecutor;
    }

    @Nullable
    private synchronized ExoPlayer ensurePlayer(Context context) {
        if (released) return null;
        if (player != null) return player;
        CacheDataSource.Factory factory = ensureCacheDataSourceFactory(context);
        if (factory == null) return null;
        player = new ExoPlayer.Builder(context)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(factory))
                .build();
        player.addListener(internalListener);
        return player;
    }

    @Nullable
    private synchronized CacheDataSource.Factory ensureCacheDataSourceFactory(Context context) {
        if (released) return null;
        if (cacheDataSourceFactory != null) return cacheDataSourceFactory;
        SimpleCache simpleCache = ensureCache(context);
        if (simpleCache == null) return null;
        DataSource.Factory upstream = new DefaultDataSource.Factory(context);
        cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        return cacheDataSourceFactory;
    }

    @Nullable
    private synchronized SimpleCache ensureCache(Context context) {
        if (released) return null;
        if (cache != null) return cache;
        File dir = new File(context.getCacheDir(), "wkfeed_media3_cache");
        cache = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES), new StandaloneDatabaseProvider(context));
        return cache;
    }

    @Nullable
    public String getAttachedFeedId() {
        return attachedFeedId;
    }

    public boolean isAttachedFeed(String feedId) {
        return feedIdEquals(feedId);
    }
}
