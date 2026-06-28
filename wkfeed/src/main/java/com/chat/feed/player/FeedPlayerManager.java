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
 * 抖音类页面的退出不要把磁盘 cache 在 Activity.onDestroy 里立刻 release。
 * onDestroy 只停止当前播放、解绑 PlayerView、取消预加载线程；SimpleCache 留到进程级复用。
 * 这样可以避开 CacheWriter 仍在写缓存时 SimpleCache 被 release 导致的返回崩溃。
 */
public class FeedPlayerManager {
    private static final long MAX_CACHE_BYTES = 200L * 1024L * 1024L;
    private static volatile FeedPlayerManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService preloadExecutor = Executors.newSingleThreadExecutor();
    /** 只在真正全局 release 后置 true；普通 Activity 返回不置 true。 */
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

    /** 新的发现页进入时调用。 */
    public void prepareForUse() {
        released = false;
        ensurePreloadExecutor();
    }

    public void attach(Context context, PlayerView playerView, String feedId, String playUrl, boolean playWhenReady, @Nullable PlaybackCallback callback) {
        if (released) return;
        if (context == null || playerView == null || playUrl == null || playUrl.length() == 0) return;
        ExoPlayer p = ensurePlayer(context.getApplicationContext());
        if (p == null) return;
        runOnMain(() -> {
            if (released) return;
            if (attachedPlayerView != null && attachedPlayerView != playerView) {
                try { attachedPlayerView.setPlayer(null); } catch (Throwable ignored) {}
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
        });
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void toggle() {
        runOnMain(() -> {
            if (player == null) return;
            if (player.isPlaying()) player.pause();
            else player.play();
        });
    }

    public void pause() {
        runOnMain(() -> {
            if (player != null) player.pause();
        });
    }

    public void detach(PlayerView playerView) {
        runOnMain(() -> {
            if (playerView != null) {
                try { playerView.setPlayer(null); } catch (Throwable ignored) {}
            }
            if (attachedPlayerView == playerView) {
                attachedPlayerView = null;
                playbackCallback = null;
            }
        });
    }

    /** 只允许当前真正 attached 的 PlayerView 停止全局播放器。 */
    public void stopAndDetach(PlayerView playerView) {
        runOnMain(() -> {
            if (attachedPlayerView == playerView) {
                if (player != null) player.pause();
                detachNow(playerView);
            } else if (playerView != null) {
                try { playerView.setPlayer(null); } catch (Throwable ignored) {}
            }
        });
    }

    public boolean isAttachedView(PlayerView playerView) {
        return attachedPlayerView == playerView;
    }

    /**
     * 发现页 Activity 退出时调用。
     * 只停当前播放和预加载，不 release SimpleCache，不 release ExoPlayer。
     * 这更接近抖音类项目的做法：页面退出清理 View/播放状态，缓存作为进程级资源复用。
     */
    public void stopForActivity() {
        cancelPreloadExecutor();
        runOnMain(() -> {
            playbackCallback = null;
            attachedFeedId = null;
            if (attachedPlayerView != null) {
                try { attachedPlayerView.setPlayer(null); } catch (Throwable ignored) {}
                attachedPlayerView = null;
            }
            if (player != null) {
                try {
                    player.pause();
                    player.stop();
                    player.clearMediaItems();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 轻量视频预缓存：只缓存下一条视频前 768KB。 */
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
                writer.cache();
            } catch (Throwable ignored) {
                if (writer != null) {
                    try { writer.cancel(); } catch (Throwable ignored2) {}
                }
            } finally {
                if (currentPreloadWriter == writer) currentPreloadWriter = null;
            }
        });
    }

    /** 真正全局释放。正常 FeedBrowseActivity 返回不要调用这个。 */
    public void release() {
        released = true;
        stopForActivity();
        runOnMain(() -> {
            if (player != null) {
                try {
                    player.removeListener(internalListener);
                    player.release();
                } catch (Throwable ignored) {}
                player = null;
            }
            cacheDataSourceFactory = null;
            if (cache != null) {
                try { cache.release(); } catch (Throwable ignored) {}
                cache = null;
            }
        });
    }

    private void detachNow(PlayerView playerView) {
        if (playerView != null) {
            try { playerView.setPlayer(null); } catch (Throwable ignored) {}
        }
        if (attachedPlayerView == playerView) {
            attachedPlayerView = null;
            playbackCallback = null;
        }
    }

    private void cancelPreloadExecutor() {
        CacheWriter writer = currentPreloadWriter;
        currentPreloadWriter = null;
        if (writer != null) {
            try { writer.cancel(); } catch (Throwable ignored) {}
        }
        ExecutorService executor = preloadExecutor;
        preloadExecutor = null;
        if (executor != null) {
            try {
                executor.shutdownNow();
                executor.awaitTermination(350, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
            }
        }
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

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
        else mainHandler.post(runnable);
    }

    private boolean feedIdEquals(String feedId) {
        if (attachedFeedId == null) return feedId == null;
        return attachedFeedId.equals(feedId);
    }

    @Nullable
    public String getAttachedFeedId() {
        return attachedFeedId;
    }

    public boolean isAttachedFeed(String feedId) {
        return feedIdEquals(feedId);
    }
}
