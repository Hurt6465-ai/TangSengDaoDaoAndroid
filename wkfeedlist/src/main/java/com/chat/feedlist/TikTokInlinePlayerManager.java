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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

/**
 * Owns the only TikTok WebView used by the timeline.
 * Video and player resources are loaded directly from tiktok.com on the user's device.
 */
public final class TikTokInlinePlayerManager {
    private static final String TIKTOK_ORIGIN = "https://www.tiktok.com";
    private static final int DEFAULT_CROP_RIGHT_DP = 60;
    private static final float MIN_VISIBLE_RATIO = 0.50f;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PlayerBridge bridge = new PlayerBridge();

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
        if (released || activity.isFinishing() || TextUtils.isEmpty(videoId)
                || !videoId.matches("\\d{6,32}")) {
            restoreUi(cover, shade, play, progress);
            return;
        }

        if (currentItemId == itemId
                && TextUtils.equals(currentVideoId, videoId)
                && webView != null
                && webView.getParent() == container) {
            showLoadingUi(cover, shade, play, progress);
            sendPlayWithSound();
            schedulePlayFallback(sessionId);
            return;
        }

        pauseAndDetach();

        currentItemId = itemId;
        currentVideoId = videoId;
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

        player.loadDataWithBaseURL(
                TIKTOK_ORIGIN + "/",
                buildPlayerHtml(currentVideoId, expectedSession),
                "text/html",
                "UTF-8",
                null
        );

        schedulePlayFallback(expectedSession);
    }


    private void schedulePlayFallback(long expectedSession) {
        // Some older TikTok player builds do not emit state messages reliably.
        main.postDelayed(() -> {
            if (expectedSession != sessionId || currentContainer == null) return;
            sendPlayWithSound();
            if (currentProgress != null && currentProgress.getVisibility() == View.VISIBLE) {
                // Keep the cover instead of exposing a black player if TikTok did not report playing.
                restoreCurrentUi();
            }
        }, 3200L);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
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
        player.addJavascriptInterface(bridge, "TalkamiTikTokBridge");
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
            public void onReceivedError(
                    @NonNull WebView view,
                    @NonNull WebResourceRequest request,
                    @NonNull WebResourceError error
            ) {
                if (request.isForMainFrame()) failCurrent();
            }

        });
        webView = player;
        return player;
    }

    private String buildPlayerHtml(String videoId, long expectedSession) {
        String playerUrl = TIKTOK_ORIGIN + "/player/v1/" + Uri.encode(videoId)
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
                + "&native_context_menu=0";

        return "<!doctype html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">"
                + "<link rel=\"preconnect\" href=\"https://www.tiktok.com\">"
                + "<style>html,body,#shell{margin:0;width:100%;height:100%;overflow:hidden;background:#000;}"
                + "iframe{position:absolute;inset:0;width:100%;height:100%;border:0;background:#000;}</style>"
                + "</head><body><div id=\"shell\">"
                + "<iframe id=\"player\" src=\"" + playerUrl + "\""
                + " allow=\"autoplay; encrypted-media; picture-in-picture\""
                + " referrerpolicy=\"strict-origin-when-cross-origin\"></iframe>"
                + "</div><script>"
                + "const SESSION=" + expectedSession + ";"
                + "const ORIGIN='https://www.tiktok.com';"
                + "function post(type,value){const f=document.getElementById('player');if(!f||!f.contentWindow)return;"
                + "const m={'x-tiktok-player':true,type:type};if(value!==undefined)m.value=value;f.contentWindow.postMessage(m,ORIGIN);}"
                + "function playSound(){post('unMute');post('play');setTimeout(()=>{post('unMute');post('play');},280);}"
                + "function pausePlayer(){post('pause');}"
                + "window.addEventListener('message',function(e){if(e.origin!==ORIGIN)return;const d=e.data;"
                + "if(!d||!d['x-tiktok-player'])return;"
                + "if(d.type==='onPlayerReady'){TalkamiTikTokBridge.onReady(SESSION);}"
                + "if(d.type==='onStateChange'&&Number(d.value)===1){TalkamiTikTokBridge.onPlaying(SESSION);}});"
                + "</script></body></html>";
    }

    private void sendPlayWithSound() {
        WebView player = webView;
        if (player == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        player.evaluateJavascript("window.playSound&&window.playSound()", null);
    }

    private void sendPause() {
        WebView player = webView;
        if (player == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        player.evaluateJavascript("window.pausePlayer&&window.pausePlayer()", null);
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
        sendPause();
        restoreCurrentUi();

        WebView player = webView;
        if (player != null) {
            player.loadUrl("about:blank");
            player.onPause();
            ViewParent parent = player.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(player);
        }

        currentContainer = null;
        currentCover = null;
        currentShade = null;
        currentPlay = null;
        currentProgress = null;
        currentItemId = RecyclerView.NO_ID;
        currentVideoId = "";
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
            player.removeJavascriptInterface("TalkamiTikTokBridge");
            player.stopLoading();
            player.loadUrl("about:blank");
            player.clearHistory();
            player.removeAllViews();
            player.destroy();
        } catch (Throwable ignored) {
        }
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

    private final class PlayerBridge {
        @JavascriptInterface
        public void onReady(long callbackSession) {
            main.post(() -> {
                if (released || callbackSession != sessionId || currentContainer == null) return;
                sendPlayWithSound();
            });
        }

        @JavascriptInterface
        public void onPlaying(long callbackSession) {
            main.post(() -> {
                if (released || callbackSession != sessionId || currentContainer == null) return;
                showPlayingUi();
            });
        }
    }
}
