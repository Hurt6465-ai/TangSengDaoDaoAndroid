package com.chat.feedlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.chat.feedlist.databinding.ActivityTiktokEmbedBinding;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TikTokEmbedActivity extends Activity {
    private static final String TAG = "TikTokEmbed";
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:/video/|/v/)([0-9]{8,32})",
            Pattern.CASE_INSENSITIVE
    );
    private static final String EXTRA_ID = "video_id";
    private static final String EXTRA_URL = "external_url";
    private static final String EXTRA_COVER = "cover_url";
    private static final String HOST_PAGE_URL =
            "https://appassets.androidplatform.net/tiktok-player/index.html";
    private static final long LOAD_TIMEOUT_MS = 18_000L;
    private static final AtomicBoolean PREWARMED = new AtomicBoolean(false);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityTiktokEmbedBinding binding;
    private String videoId = "";
    private String externalUrl = "";
    private String coverUrl = "";
    private boolean hostPageFinished;
    private boolean playerReady;
    private boolean playerVisible;
    private boolean playing = true;
    private boolean resumed;
    private long hostPageFinishedAt;

    private final Runnable loadTimeout = () -> {
        if (!playerReady && !playerVisible) showError();
    };

    private final Runnable loopKeeper = new Runnable() {
        @Override
        public void run() {
            if (binding == null || isFinishing() || isDestroyed()) return;
            if (resumed && playing && hostPageFinished) {
                evaluatePlaybackScript(true, true);
            }
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

    /** Starts Chromium's renderer once while the feed is idle, reducing the first player-open delay. */
    public static void prewarm(Context context) {
        if (context == null || !PREWARMED.compareAndSet(false, true)) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            WebView warm = null;
            try {
                warm = new WebView(context.getApplicationContext());
                warm.setBackgroundColor(Color.BLACK);
                warm.getSettings().setJavaScriptEnabled(true);
                warm.loadUrl("about:blank");
                WebView finalWarm = warm;
                warm.postDelayed(() -> {
                    try {
                        finalWarm.stopLoading();
                        finalWarm.loadUrl("about:blank");
                        finalWarm.removeAllViews();
                        finalWarm.destroy();
                    } catch (Throwable ignored) {
                    }
                }, 600L);
            } catch (Throwable error) {
                PREWARMED.set(false);
                if (warm != null) {
                    try {
                        warm.destroy();
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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

        binding.backBtn.setOnClickListener(v -> finish());
        binding.retryBtn.setOnClickListener(v -> reloadPlayer());
        binding.centerTap.setOnClickListener(v -> togglePlayback());
        // Swallow TikTok's right-side author/like/comment/share hit area.
        binding.rightActionBlocker.setOnClickListener(v -> { });
        binding.rightActionBlocker.setOnLongClickListener(v -> true);

        configureWebView();
        showPoster();
        loadOfficialPlayer();
        mainHandler.post(loopKeeper);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = binding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        String userAgent = settings.getUserAgentString();
        if (!TextUtils.isEmpty(userAgent)) {
            userAgent = userAgent.replace("; wv", "").replace("Version/4.0 ", "");
            settings.setUserAgentString(userAgent);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(binding.webView, true);
        }

        binding.webView.setBackgroundColor(Color.BLACK);
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        binding.webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        binding.webView.setLongClickable(false);
        binding.webView.setHapticFeedbackEnabled(false);
        binding.webView.setOnLongClickListener(v -> true);
        binding.webView.setWebChromeClient(new WebChromeClient());
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return false;
                Uri uri = url == null ? null : Uri.parse(url);
                return !isHostPageUri(uri) && !isOfficialPlayerUri(uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null) return true;
                Uri uri = request.getUrl();
                if (request.isForMainFrame()) {
                    // The app-owned host page must remain the top frame. TikTok stays inside the iframe.
                    return !isHostPageUri(uri);
                }
                String scheme = uri == null ? "" : uri.getScheme();
                return !("https".equalsIgnoreCase(scheme)
                        || "about".equalsIgnoreCase(scheme)
                        || "data".equalsIgnoreCase(scheme)
                        || "blob".equalsIgnoreCase(scheme));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                Uri uri = url == null ? null : Uri.parse(url);
                if (!isHostPageUri(uri)) return;
                hostPageFinished = false;
                playerReady = false;
                playerVisible = false;
                playing = true;
                binding.errorPanel.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                binding.loadingView.setVisibility(View.VISIBLE);
                binding.posterIv.setAlpha(1f);
                binding.posterIv.setVisibility(View.VISIBLE);
                binding.centerPlayIndicator.setVisibility(View.GONE);
                mainHandler.removeCallbacks(loadTimeout);
                mainHandler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Uri uri = url == null ? null : Uri.parse(url);
                if (!isHostPageUri(uri)) return;
                hostPageFinished = true;
                hostPageFinishedAt = System.currentTimeMillis();
                binding.errorPanel.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                forceStartWithSound();
                probePlayerState();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Uri uri = request == null ? null : request.getUrl();
                if (request != null && (request.isForMainFrame() || isOfficialPlayerUri(uri))) {
                    Log.e(TAG, "player error: " + uri + ", code="
                            + (error == null ? "" : error.getErrorCode()));
                    showError();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                Uri uri = request == null ? null : request.getUrl();
                if (response != null && response.getStatusCode() >= 400
                        && request != null && (request.isForMainFrame() || isOfficialPlayerUri(uri))) {
                    Log.e(TAG, "player http error: " + uri + ", status=" + response.getStatusCode());
                    showError();
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e(TAG, "render process gone");
                showError();
                return true;
            }
        });
    }

    private void showPoster() {
        if (binding == null) return;
        binding.posterIv.setAlpha(1f);
        binding.posterIv.setVisibility(View.VISIBLE);
        binding.loadingView.setVisibility(View.VISIBLE);
        if (TextUtils.isEmpty(coverUrl)) {
            binding.posterIv.setImageResource(android.R.color.black);
            return;
        }
        String userAgent = binding.webView.getSettings().getUserAgentString();
        GlideUrl request = new GlideUrl(coverUrl, new LazyHeaders.Builder()
                .addHeader("User-Agent", TextUtils.isEmpty(userAgent) ? "Mozilla/5.0" : userAgent)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .build());
        Glide.with(this)
                .load(request)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .centerCrop()
                .dontAnimate()
                .into(binding.posterIv);
    }

    private void loadOfficialPlayer() {
        if (binding == null || TextUtils.isEmpty(videoId)) {
            showError();
            return;
        }
        playing = true;
        String playerUrl = buildPlayerUrl(videoId);
        String html = buildHostHtml(playerUrl);
        binding.webView.loadDataWithBaseURL(
                HOST_PAGE_URL,
                html,
                "text/html",
                "UTF-8",
                HOST_PAGE_URL
        );
    }

    private static String buildPlayerUrl(String id) {
        return "https://www.tiktok.com/player/v1/" + id
                + "?autoplay=1&muted=0&loop=1&controls=1&progress_bar=1&play_button=0"
                + "&volume_control=1&fullscreen_button=0&timestamp=0&music_info=0"
                + "&description=0&native_context_menu=0&closed_caption=0&rel=0";
    }

    /**
     * TikTok's player messaging API is defined between an HTML host and an iframe. Loading the
     * player as WebView's top page makes window.postMessage target the wrong window, so playback
     * commands never reliably reach the player. This small host page keeps TikTok in its official
     * iframe and forwards play/pause/mute commands to iframe.contentWindow.
     */
    private String buildHostHtml(String playerUrl) {
        String safePlayerUrl = playerUrl.replace("&", "&amp;");
        String language = Locale.getDefault().toLanguageTag();
        return "<!doctype html><html lang=\"" + language + "\"><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover\">"
                + "<style>html,body{width:100%;height:100%;margin:0;overflow:hidden;background:#000;}"
                + "#ttPlayer{display:block;width:100%;height:100%;border:0;background:#000;}</style>"
                + "</head><body>"
                + "<iframe id=\"ttPlayer\" src=\"" + safePlayerUrl + "\" "
                + "allow=\"autoplay; encrypted-media; fullscreen; picture-in-picture\" "
                + "referrerpolicy=\"strict-origin-when-cross-origin\"></iframe>"
                + "<script>(function(){"
                + "var frame=document.getElementById('ttPlayer');"
                + "window.__ttPlayerReady=false;window.__ttPlayerState=-1;"
                + "window.__ttLastMessage=0;window.__ttFrameLoaded=false;window.__ttFrameLoadedAt=0;"
                + "function send(type,value){try{if(!frame||!frame.contentWindow)return false;"
                + "var msg={'x-tiktok-player':true,type:type};"
                + "if(typeof value!=='undefined')msg.value=value;"
                + "frame.contentWindow.postMessage(msg,'*');return true;}catch(e){return false;}}"
                + "window.__talkamiTikTokSend=send;"
                + "function start(){send('unMute');send('play');}"
                + "frame.addEventListener('load',function(){window.__ttFrameLoaded=true;"
                + "window.__ttFrameLoadedAt=Date.now();setTimeout(start,80);setTimeout(start,450);setTimeout(start,1200);});"
                + "window.addEventListener('message',function(event){try{"
                + "if(event.source!==frame.contentWindow)return;var data=event.data;"
                + "if(!data||data['x-tiktok-player']!==true)return;window.__ttLastMessage=Date.now();"
                + "if(data.type==='onPlayerReady'){window.__ttPlayerReady=true;start();}"
                + "if(data.type==='onStateChange'){window.__ttPlayerState=Number(data.value);"
                + "if(window.__ttPlayerState===0){send('seekTo',0);setTimeout(function(){send('play');},40);}}"
                + "if(data.type==='onMute'&&data.value===true){send('unMute');}"
                + "}catch(e){}});"
                + "document.addEventListener('visibilitychange',function(){if(!document.hidden)setTimeout(start,80);});"
                + "})();</script></body></html>";
    }

    private void reloadPlayer() {
        if (binding == null) return;
        binding.errorPanel.setVisibility(View.GONE);
        showPoster();
        binding.webView.stopLoading();
        binding.webView.clearHistory();
        loadOfficialPlayer();
    }

    private void forceStartWithSound() {
        mainHandler.postDelayed(() -> evaluatePlaybackScript(true, true), 100L);
        mainHandler.postDelayed(() -> evaluatePlaybackScript(true, true), 550L);
        mainHandler.postDelayed(() -> evaluatePlaybackScript(true, true), 1_300L);
    }

    private void togglePlayback() {
        if (!hostPageFinished || binding == null) return;
        playing = !playing;
        evaluatePlaybackScript(playing, playing);
        binding.centerPlayIndicator.setVisibility(playing ? View.GONE : View.VISIBLE);
    }

    private void evaluatePlaybackScript(boolean shouldPlay, boolean unmute) {
        if (binding == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        String command = shouldPlay ? "play" : "pause";
        String script = "(function(){try{var send=window.__talkamiTikTokSend;"
                + "if(typeof send!=='function')return false;"
                + (unmute ? "send('unMute');" : "")
                + "send('" + command + "');return true;}catch(e){return false;}})()";
        try {
            binding.webView.evaluateJavascript(script, null);
        } catch (Throwable ignored) {
        }
    }

    private void probePlayerState() {
        if (binding == null || !hostPageFinished) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            revealPlayer();
            return;
        }
        try {
            binding.webView.evaluateJavascript(
                    "(function(){try{if(window.__ttPlayerReady||window.__ttLastMessage>0)return 'ready';"
                            + "if(window.__ttFrameLoaded&&Date.now()-window.__ttFrameLoadedAt>700)return 'loaded';"
                            + "return 'waiting';}catch(e){return 'waiting';}})()",
                    value -> {
                        if (binding == null || isFinishing() || isDestroyed()) return;
                        String state = String.valueOf(value).replace("\"", "");
                        if ("ready".equalsIgnoreCase(state)) {
                            playerReady = true;
                            mainHandler.removeCallbacks(loadTimeout);
                            revealPlayer();
                            return;
                        }
                        if ("loaded".equalsIgnoreCase(state)) revealPlayer();
                        long waited = Math.max(0L, System.currentTimeMillis() - hostPageFinishedAt);
                        if (waited < LOAD_TIMEOUT_MS) {
                            mainHandler.postDelayed(this::probePlayerState, 180L);
                        } else if (!playerVisible) {
                            showError();
                        }
                    }
            );
        } catch (Throwable ignored) {
            mainHandler.postDelayed(this::revealPlayer, 900L);
        }
    }

    private void revealPlayer() {
        if (binding == null || isFinishing() || isDestroyed() || playerVisible) return;
        playerVisible = true;
        binding.loadingView.setVisibility(View.GONE);
        if (binding.posterIv.getVisibility() == View.VISIBLE) {
            binding.posterIv.animate()
                    .alpha(0f)
                    .setDuration(180L)
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
        binding.loadingView.setVisibility(View.GONE);
        binding.posterIv.setVisibility(View.GONE);
        binding.webView.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.VISIBLE);
    }

    private static String resolveVideoId(String videoId, String externalUrl) {
        String value = videoId == null ? "" : videoId.trim();
        if (value.matches("[0-9]{8,32}")) return value;
        String url = externalUrl == null ? "" : externalUrl.trim();
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String safeTikTokUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = value.trim();
        if (normalized.startsWith("//")) normalized = "https:" + normalized;
        Uri uri;
        try {
            uri = Uri.parse(normalized);
        } catch (Throwable ignored) {
            return "";
        }
        return isAllowedTikTokUri(uri) ? uri.toString() : "";
    }

    private static String safeHttpsUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = value.trim();
        if (normalized.startsWith("//")) normalized = "https:" + normalized;
        try {
            Uri uri = Uri.parse(normalized);
            return "https".equalsIgnoreCase(uri.getScheme()) && !TextUtils.isEmpty(uri.getHost())
                    ? uri.toString()
                    : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isHostPageUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        return "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith("/tiktok-player/");
    }

    private static boolean isOfficialPlayerUri(Uri uri) {
        if (!isAllowedTikTokUri(uri)) return false;
        String path = uri.getPath();
        return path != null && path.startsWith("/player/v1/");
    }

    private static boolean isAllowedTikTokUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("tiktok.com") || host.endsWith(".tiktok.com");
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (binding != null) {
            binding.webView.onResume();
            if (hostPageFinished) {
                playing = true;
                binding.centerPlayIndicator.setVisibility(View.GONE);
                mainHandler.postDelayed(() -> evaluatePlaybackScript(true, true), 120L);
            }
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (binding != null) {
            evaluatePlaybackScript(false, false);
            binding.webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (binding != null) {
            Glide.with(this).clear(binding.posterIv);
            ViewGroup parent = (ViewGroup) binding.webView.getParent();
            if (parent != null) parent.removeView(binding.webView);
            binding.webView.stopLoading();
            binding.webView.loadUrl("about:blank");
            binding.webView.clearHistory();
            binding.webView.removeAllViews();
            binding.webView.destroy();
            binding = null;
        }
        super.onDestroy();
    }
}
