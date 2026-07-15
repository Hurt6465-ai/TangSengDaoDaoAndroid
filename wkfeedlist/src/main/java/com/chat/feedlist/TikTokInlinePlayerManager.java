package com.chat.feedlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Owns the only TikTok WebView used by the timeline.
 * The user taps to start playback; all video bytes are loaded directly from TikTok.
 */
public final class TikTokInlinePlayerManager {
    private static final String TIKTOK_ORIGIN = "https://www.tiktok.com";
    private static final int DEFAULT_CROP_RIGHT_DP = 60;
    private static final float MIN_VISIBLE_RATIO = 0.50f;
    private static final long LOAD_TIMEOUT_MS = 8_000L;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());

    private WebView webView;
    private TikTokInlineContainer currentContainer;
    private ImageView currentCover;
    private View currentShade;
    private ImageView currentPlay;
    private ProgressBar currentProgress;
    private long currentItemId = RecyclerView.NO_ID;
    private String currentVideoId = "";
    private long sessionId;
    private boolean released;
    private int cropRightDp = DEFAULT_CROP_RIGHT_DP;

    public TikTokInlinePlayerManager(@NonNull Activity activity) {
        this.activity = activity;
    }

    /** Kept in one place so this can later come from remote app config. */
    public void setCropRightDp(int cropRightDp) {
        this.cropRightDp = Math.max(0, Math.min(88, cropRightDp));
    }

    public void play(
            long itemId,
            @NonNull String videoId,
            @NonNull TikTokInlineContainer container,
            @NonNull ImageView cover,
            @NonNull View shade,
            @NonNull ImageView play,
            @NonNull ProgressBar progress
    ) {
        String normalizedId = videoId.trim();
        if (released || activity.isFinishing() || TextUtils.isEmpty(normalizedId)
                || !normalizedId.matches("[0-9]{8,32}")) {
            restoreUi(cover, shade, play, progress);
            return;
        }

        if (currentItemId == itemId
                && TextUtils.equals(currentVideoId, normalizedId)
                && webView != null
                && webView.getParent() == container) {
            webView.onResume();
            webView.requestFocus();
            return;
        }

        pauseAndDetach();

        currentItemId = itemId;
        currentVideoId = normalizedId;
        currentContainer = container;
        currentCover = cover;
        currentShade = shade;
        currentPlay = play;
        currentProgress = progress;
        sessionId++;
        long expectedSession = sessionId;

        showLoadingUi(cover, shade, play, progress);
        attachAndLoad(expectedSession);
    }

    private void attachAndLoad(long expectedSession) {
        TikTokInlineContainer container = currentContainer;
        if (container == null || expectedSession != sessionId) return;
        if (container.getWidth() <= 0) {
            container.post(() -> attachAndLoad(expectedSession));
            return;
        }

        WebView player = ensureWebView();
        ViewParent oldParent = player.getParent();
        if (oldParent instanceof ViewGroup) {
            ((ViewGroup) oldParent).removeView(player);
        }

        int cropPx = dp(cropRightDp);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                container.getWidth() + cropPx,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        params.gravity = Gravity.START | Gravity.TOP;
        player.setLayoutParams(params);
        container.addView(player, 0, params);
        player.setVisibility(View.VISIBLE);
        player.onResume();
        player.requestFocus();

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", TIKTOK_ORIGIN + "/");
        player.loadUrl(buildPlayerUrl(currentVideoId), headers);

        main.postDelayed(() -> {
            if (expectedSession != sessionId || currentContainer == null) return;
            if (currentProgress != null && currentProgress.getVisibility() == View.VISIBLE) {
                // Do not expose a permanent black panel. Restore the cover and allow retry.
                pauseAndDetach();
            }
        }, LOAD_TIMEOUT_MS);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView ensureWebView() {
        if (webView != null) return webView;

        WebView player = new WebView(activity);
        WebSettings settings = player.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        player.setBackgroundColor(Color.BLACK);
        player.setOverScrollMode(View.OVER_SCROLL_NEVER);
        player.setVerticalScrollBarEnabled(false);
        player.setHorizontalScrollBarEnabled(false);
        player.setWebChromeClient(new WebChromeClient());
        player.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isTikTokUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
                return !isTikTokUrl(String.valueOf(request.getUrl()));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!isTikTokUrl(url) || currentContainer == null || view != webView) return;
                long expectedSession = sessionId;
                main.postDelayed(() -> {
                    if (expectedSession == sessionId && currentContainer != null && view == webView) {
                        showPlayingUi();
                    }
                }, 350L);
            }

            @Override
            public void onReceivedError(
                    @NonNull WebView view,
                    @NonNull WebResourceRequest request,
                    @NonNull WebResourceError error
            ) {
                if (request.isForMainFrame()) failCurrent();
            }

            @Override
            public void onReceivedHttpError(
                    @NonNull WebView view,
                    @NonNull WebResourceRequest request,
                    @NonNull WebResourceResponse errorResponse
            ) {
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) failCurrent();
            }

        });
        webView = player;
        return player;
    }

    private String buildPlayerUrl(String videoId) {
        return TIKTOK_ORIGIN + "/player/v1/" + Uri.encode(videoId)
                + "?autoplay=1"
                + "&muted=0"
                + "&loop=0"
                + "&controls=1"
                + "&progress_bar=1"
                + "&play_button=1"
                + "&volume_control=1"
                + "&fullscreen_button=0"
                + "&timestamp=0"
                + "&music_info=0"
                + "&description=0"
                + "&native_context_menu=0"
                + "&rel=0";
    }

    public void detachIfMostlyHidden() {
        TikTokInlineContainer container = currentContainer;
        if (container == null || container.getHeight() <= 0) return;

        Rect visible = new Rect();
        boolean shown = container.isShown() && container.getGlobalVisibleRect(visible);
        float ratio = shown ? visible.height() / (float) container.getHeight() : 0f;
        if (ratio < MIN_VISIBLE_RATIO) pauseAndDetach();
    }

    public boolean isOwner(long itemId) {
        return itemId != RecyclerView.NO_ID && itemId == currentItemId && currentContainer != null;
    }

    public void detachIfOwner(long itemId) {
        if (itemId != RecyclerView.NO_ID && itemId == currentItemId) pauseAndDetach();
    }

    public void pauseAndDetach() {
        sessionId++;
        restoreCurrentUi();

        WebView player = webView;
        if (player != null) {
            try {
                player.onPause();
                player.stopLoading();
                player.loadUrl("about:blank");
                ViewParent parent = player.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(player);
            } catch (Throwable ignored) {
            }
        }
        clearOwner();
    }

    public void release() {
        if (released) return;
        released = true;
        main.removeCallbacksAndMessages(null);
        pauseAndDetach();
        destroyWebView();
    }

    private void destroyWebView() {
        WebView player = webView;
        webView = null;
        if (player == null) return;
        try {
            ViewParent parent = player.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(player);
            player.stopLoading();
            player.loadUrl("about:blank");
            player.clearHistory();
            player.removeAllViews();
            player.destroy();
        } catch (Throwable ignored) {
        }
    }

    private void clearOwner() {
        currentContainer = null;
        currentCover = null;
        currentShade = null;
        currentPlay = null;
        currentProgress = null;
        currentItemId = RecyclerView.NO_ID;
        currentVideoId = "";
    }

    private void failCurrent() {
        main.post(this::pauseAndDetach);
    }

    private void restoreCurrentUi() {
        restoreUi(currentCover, currentShade, currentPlay, currentProgress);
    }

    private static void restoreUi(
            @Nullable ImageView cover,
            @Nullable View shade,
            @Nullable ImageView play,
            @Nullable ProgressBar progress
    ) {
        if (cover != null) cover.setVisibility(View.VISIBLE);
        if (shade != null) shade.setVisibility(View.VISIBLE);
        if (play != null) play.setVisibility(View.VISIBLE);
        if (progress != null) progress.setVisibility(View.GONE);
    }

    private static void showLoadingUi(
            @Nullable ImageView cover,
            @Nullable View shade,
            @Nullable ImageView play,
            @Nullable ProgressBar progress
    ) {
        if (cover != null) cover.setVisibility(View.VISIBLE);
        if (shade != null) shade.setVisibility(View.VISIBLE);
        if (play != null) play.setVisibility(View.GONE);
        if (progress != null) progress.setVisibility(View.VISIBLE);
    }

    private void showPlayingUi() {
        if (currentCover != null) currentCover.setVisibility(View.GONE);
        if (currentShade != null) currentShade.setVisibility(View.GONE);
        if (currentPlay != null) currentPlay.setVisibility(View.GONE);
        if (currentProgress != null) currentProgress.setVisibility(View.GONE);
    }

    private boolean isTikTokUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        if ("about:blank".equalsIgnoreCase(value)) return true;
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return "tiktok.com".equals(host) || host.endsWith(".tiktok.com");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
