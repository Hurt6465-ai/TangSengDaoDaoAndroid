package com.chat.feedlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chat.feedlist.databinding.ActivityTiktokEmbedBinding;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Full-screen TikTok official Embed Player using the single process-wide preloaded WebView. */
public class TikTokEmbedActivity extends Activity implements TikTokPlayerPool.Listener {
    private static final String TAG = "TikTokEmbed";
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:/video/|/v/|/player/v1/)([0-9]{8,32})|(?:video_id|item_id)=([0-9]{8,32})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");
    private static final String EXTRA_ID = "video_id";
    private static final String EXTRA_URL = "external_url";
    private static final String EXTRA_COVER = "cover_url";
    private static final long PLAYER_TIMEOUT_MS = 18_000L;
    private static final long IFRAME_READY_TIMEOUT_MS = 5_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityTiktokEmbedBinding binding;
    private TikTokPlayerPool playerPool;
    private String videoId = "";
    private String externalUrl = "";
    private String coverUrl = "";
    private boolean hostMode = true;
    private boolean frameLoaded;
    private boolean playerReady;
    private boolean playerVisible;
    private boolean playing = true;
    private boolean resumed;
    private boolean fallbackTried;
    private int unmuteAttempts;

    private final Runnable loadTimeout = () -> {
        if (binding == null || isFinishing() || isDestroyed() || playerVisible) return;
        if (!fallbackTried) loadDirectPlayer("timeout");
        else showError();
    };

    private final Runnable iframeReadyTimeout = () -> {
        if (binding == null || !hostMode || !frameLoaded || playerReady || playerVisible) return;
        loadDirectPlayer("iframe-no-ready");
    };

    private final Runnable loopKeeper = new Runnable() {
        @Override public void run() {
            if (binding == null || isFinishing() || isDestroyed()) return;
            if (resumed && playing && playerVisible && playerPool != null) playerPool.play(true);
            mainHandler.postDelayed(this, 2_500L);
        }
    };

    public static void open(Context context, String videoId, String externalUrl) {
        open(context, videoId, externalUrl, "");
    }

    public static void open(Context context, String videoId, String externalUrl, String coverUrl) {
        if (context == null) return;
        String resolvedId = resolveVideoId(videoId, externalUrl);
        String safeUrl = safeTikTokUrl(externalUrl);
        if (TextUtils.isEmpty(resolvedId) && TextUtils.isEmpty(safeUrl)) return;
        Intent intent = new Intent(context, TikTokEmbedActivity.class);
        intent.putExtra(EXTRA_ID, resolvedId);
        intent.putExtra(EXTRA_URL, safeUrl);
        intent.putExtra(EXTRA_COVER, safeHttpsUrl(coverUrl));
        context.startActivity(intent);
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTiktokEmbedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        externalUrl = safeTikTokUrl(getIntent().getStringExtra(EXTRA_URL));
        videoId = resolveVideoId(getIntent().getStringExtra(EXTRA_ID), externalUrl);
        coverUrl = safeHttpsUrl(getIntent().getStringExtra(EXTRA_COVER));
        if (TextUtils.isEmpty(videoId)) {
            finish();
            return;
        }

        playerPool = TikTokPlayerPool.get(this);
        binding.backBtn.setOnClickListener(v -> finish());
        binding.retryBtn.setOnClickListener(v -> reloadPlayer());
        binding.centerTap.setOnClickListener(v -> togglePlayback());
        binding.rightActionBlocker.setOnClickListener(v -> { });
        binding.rightActionBlocker.setOnLongClickListener(v -> true);

        showPoster();
        attachPlayer();
        mainHandler.post(loopKeeper);
    }

    private void attachPlayer() {
        if (binding == null || playerPool == null) return;
        resetLoadState();
        binding.playerHost.setVisibility(View.VISIBLE);
        playerPool.attachFullScreen(this, binding.playerHost, videoId, this);
        // Run immediately while opening still originates from the list-cover tap.
        playerPool.play(true);
        startTimeout();
    }

    private void resetLoadState() {
        hostMode = true;
        frameLoaded = false;
        playerReady = false;
        playerVisible = false;
        playing = true;
        fallbackTried = false;
        unmuteAttempts = 0;
        mainHandler.removeCallbacks(iframeReadyTimeout);
    }

    private void showPoster() {
        if (binding == null) return;
        binding.posterIv.animate().cancel();
        binding.posterIv.setAlpha(1f);
        binding.posterIv.setVisibility(View.VISIBLE);
        binding.loadingView.setVisibility(View.VISIBLE);
        binding.centerPlayIndicator.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.GONE);
        if (TextUtils.isEmpty(coverUrl)) {
            binding.posterIv.setImageResource(android.R.color.black);
            return;
        }
        Glide.with(this)
                .load(coverUrl)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .centerCrop()
                .dontAnimate()
                .error(android.R.color.black)
                .into(binding.posterIv);
    }

    private void reloadPlayer() {
        if (binding == null || playerPool == null) return;
        showPoster();
        resetLoadState();
        playerPool.reloadHost(videoId);
        playerPool.play(true);
        startTimeout();
    }

    private void loadDirectPlayer(String reason) {
        if (binding == null || playerPool == null || TextUtils.isEmpty(videoId) || fallbackTried) {
            showError();
            return;
        }
        Log.w(TAG, "official direct-player fallback: " + reason);
        fallbackTried = true;
        hostMode = false;
        frameLoaded = false;
        playerReady = false;
        playerVisible = false;
        playing = true;
        unmuteAttempts = 0;
        mainHandler.removeCallbacks(iframeReadyTimeout);
        playerPool.loadDirect(videoId);
        playerPool.play(true);
        startTimeout();
    }

    private void startTimeout() {
        mainHandler.removeCallbacks(loadTimeout);
        mainHandler.postDelayed(loadTimeout, PLAYER_TIMEOUT_MS);
    }

    private void togglePlayback() {
        if (binding == null || playerPool == null) return;
        if (!playerVisible) {
            playing = true;
            playerPool.play(true);
            binding.centerPlayIndicator.setVisibility(View.GONE);
            return;
        }
        playing = !playing;
        if (playing) playerPool.play(true);
        else playerPool.pause();
        binding.centerPlayIndicator.setVisibility(playing ? View.GONE : View.VISIBLE);
    }

    /** Poster is removed only after a playing state or currentTime > 0 proves a visual frame exists. */
    private void revealPlayer() {
        if (binding == null || isFinishing() || isDestroyed() || playerVisible) return;
        playerVisible = true;
        mainHandler.removeCallbacks(loadTimeout);
        mainHandler.removeCallbacks(iframeReadyTimeout);
        binding.loadingView.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.GONE);
        binding.playerHost.setVisibility(View.VISIBLE);
        if (binding.posterIv.getVisibility() == View.VISIBLE) {
            binding.posterIv.animate().cancel();
            binding.posterIv.animate()
                    .alpha(0f)
                    .setDuration(160L)
                    .withEndAction(() -> {
                        if (binding == null) return;
                        binding.posterIv.setVisibility(View.GONE);
                        binding.posterIv.setAlpha(1f);
                    })
                    .start();
        }
    }

    private void showError() {
        if (binding == null || isFinishing() || isDestroyed()) return;
        mainHandler.removeCallbacks(loadTimeout);
        mainHandler.removeCallbacks(iframeReadyTimeout);
        binding.loadingView.setVisibility(View.GONE);
        binding.posterIv.setVisibility(View.GONE);
        binding.playerHost.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.VISIBLE);
    }

    @Override public void onPageStarted(boolean isHostMode) {
        if (binding == null) return;
        hostMode = isHostMode;
        binding.errorPanel.setVisibility(View.GONE);
        binding.playerHost.setVisibility(View.VISIBLE);
        startTimeout();
    }

    @Override public void onPageFinished(boolean isHostMode) {
        hostMode = isHostMode;
        if (!hostMode && playerPool != null) {
            // The pool injects a video playing/time observer. Keep the poster until that callback.
            mainHandler.postDelayed(() -> {
                if (playerPool != null && !playerVisible) playerPool.play(true);
            }, 100L);
        }
    }

    @Override public void onFrameLoaded() {
        if (binding == null || !hostMode) return;
        frameLoaded = true;
        mainHandler.removeCallbacks(iframeReadyTimeout);
        mainHandler.postDelayed(iframeReadyTimeout, IFRAME_READY_TIMEOUT_MS);
    }

    @Override public void onPlayerMessage(String type, String value) {
        if (binding == null || !hostMode) return;
        if ("onPlayerReady".equals(type)) {
            playerReady = true;
            mainHandler.removeCallbacks(iframeReadyTimeout);
            if (playerPool != null) playerPool.play(true);
            return;
        }
        if ("onStateChange".equals(type)) {
            int state = parseState(value);
            if (state == 1) {
                playing = true;
                binding.centerPlayIndicator.setVisibility(View.GONE);
                revealPlayer();
            } else if (state == 2) {
                playing = false;
                if (playerVisible) binding.centerPlayIndicator.setVisibility(View.VISIBLE);
            }
            return;
        }
        if ("onCurrentTime".equals(type)) {
            if (parseNumber(value) > 0.02d) revealPlayer();
            return;
        }
        if ("onMute".equals(type) && playing && unmuteAttempts < 3) {
            unmuteAttempts++;
            mainHandler.postDelayed(() -> {
                if (playerPool != null) playerPool.play(true);
            }, 120L);
            return;
        }
        if ("onPlayerError".equals(type) || "onError".equals(type)) {
            Log.e(TAG, "player error: " + value);
            if (value != null && value.contains("3002")) {
                // Autoplay was blocked. Keep the poster and let the center native tap retry with sound.
                mainHandler.removeCallbacks(loadTimeout);
                playing = false;
                binding.loadingView.setVisibility(View.GONE);
                binding.centerPlayIndicator.setVisibility(View.VISIBLE);
            } else if (!fallbackTried) {
                loadDirectPlayer("player-error");
            } else {
                showError();
            }
        }
    }

    @Override public void onDirectVisual() {
        revealPlayer();
    }

    @Override public void onMainFrameError(String reason) {
        Log.e(TAG, "main frame error: " + reason);
        if (!fallbackTried) loadDirectPlayer(reason);
        else showError();
    }

    @Override public void onRenderProcessGone() {
        Log.e(TAG, "render process gone");
        showError();
    }

    private static int parseState(String value) {
        double parsed = parseNumber(value);
        return Double.isNaN(parsed) ? -1 : (int) parsed;
    }

    private static double parseNumber(String value) {
        if (TextUtils.isEmpty(value)) return Double.NaN;
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        if (!matcher.find()) return Double.NaN;
        try { return Double.parseDouble(matcher.group()); }
        catch (Throwable ignored) { return Double.NaN; }
    }

    private static String resolveVideoId(String videoId, String externalUrl) {
        String value = videoId == null ? "" : videoId.trim();
        if (value.matches("[0-9]{8,32}")) return value;
        String url = externalUrl == null ? "" : externalUrl.trim();
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        if (!matcher.find()) return "";
        return !TextUtils.isEmpty(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
    }

    private static String safeTikTokUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = decodeUrl(value);
        try {
            Uri uri = Uri.parse(normalized);
            return isAllowedTikTokUri(uri) ? uri.toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeHttpsUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = decodeUrl(value);
        try {
            Uri uri = Uri.parse(normalized);
            return "https".equalsIgnoreCase(uri.getScheme()) && !TextUtils.isEmpty(uri.getHost())
                    ? uri.toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String decodeUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replace("&amp;", "&")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .replace("\\/", "/");
        if (normalized.startsWith("//")) normalized = "https:" + normalized;
        if (normalized.startsWith("http://")) normalized = "https://" + normalized.substring(7);
        return normalized;
    }

    private static boolean isAllowedTikTokUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("tiktok.com") || host.endsWith(".tiktok.com");
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (playerPool != null) {
            playerPool.onResume();
            mainHandler.postDelayed(() -> {
                if (playerPool != null && playing) playerPool.play(true);
            }, 80L);
        }
    }

    @Override protected void onPause() {
        resumed = false;
        if (playerPool != null) playerPool.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (binding != null) Glide.with(this).clear(binding.posterIv);
        if (playerPool != null) playerPool.detachFullScreen(this);
        binding = null;
        super.onDestroy();
    }
}
