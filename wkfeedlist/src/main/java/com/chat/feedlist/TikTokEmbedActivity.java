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
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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
import com.chat.feedlist.databinding.ActivityTiktokEmbedBinding;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Full-screen TikTok official embed player. */
public class TikTokEmbedActivity extends Activity {
    private static final String TAG = "TikTokEmbed";
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:/video/|/v/|/player/v1/)([0-9]{8,32})|(?:video_id|item_id)=([0-9]{8,32})",
            Pattern.CASE_INSENSITIVE
    );
    private static final String EXTRA_ID = "video_id";
    private static final String EXTRA_URL = "external_url";
    private static final String EXTRA_COVER = "cover_url";
    private static final String HOST_BASE_URL = "https://www.tiktok.com/";
    private static final long PLAYER_TIMEOUT_MS = 18_000L;
    private static final AtomicBoolean PREWARMED = new AtomicBoolean(false);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityTiktokEmbedBinding binding;
    private String videoId = "";
    private String externalUrl = "";
    private String coverUrl = "";
    private boolean hostMode = true;
    private boolean hostPageFinished;
    private boolean frameLoaded;
    private boolean playerReady;
    private boolean playerVisible;
    private boolean playing = true;
    private boolean resumed;
    private boolean fallbackTried;
    private int unmuteAttempts;

    private final Runnable loadTimeout = () -> {
        if (binding == null || isFinishing() || isDestroyed() || playerReady) return;
        if (!fallbackTried) {
            loadDirectPlayer("timeout");
        } else {
            showError();
        }
    };

    private final Runnable loopKeeper = new Runnable() {
        @Override
        public void run() {
            if (binding == null || isFinishing() || isDestroyed()) return;
            if (resumed && playing && playerVisible) sendPlaybackCommand(true, true);
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

    /** Starts Chromium once while the feed is idle to reduce first-open delay. */
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
                        finalWarm.removeAllViews();
                        finalWarm.destroy();
                    } catch (Throwable ignored) {
                    }
                }, 500L);
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
        binding.rightActionBlocker.setOnClickListener(v -> { });
        binding.rightActionBlocker.setOnLongClickListener(v -> true);

        configureWebView();
        showPoster();
        loadIframePlayer();
        mainHandler.post(loopKeeper);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(binding.webView, true);
        }

        binding.webView.setBackgroundColor(Color.BLACK);
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        binding.webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        binding.webView.setVerticalScrollBarEnabled(false);
        binding.webView.setHorizontalScrollBarEnabled(false);
        binding.webView.setLongClickable(false);
        binding.webView.setHapticFeedbackEnabled(false);
        binding.webView.setOnLongClickListener(v -> true);
        binding.webView.addJavascriptInterface(new PlayerBridge(), "TalkamiTikTokBridge");
        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    Log.d(TAG, "js: " + consoleMessage.message());
                }
                return true;
            }
        });
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = url == null ? null : Uri.parse(url);
                return shouldBlockMainNavigation(uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null) return true;
                Uri uri = request.getUrl();
                if (!request.isForMainFrame()) {
                    String scheme = uri == null ? "" : uri.getScheme();
                    return !("https".equalsIgnoreCase(scheme)
                            || "about".equalsIgnoreCase(scheme)
                            || "data".equalsIgnoreCase(scheme)
                            || "blob".equalsIgnoreCase(scheme));
                }
                return shouldBlockMainNavigation(uri);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                hostPageFinished = false;
                binding.errorPanel.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                startTimeout();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                hostPageFinished = true;
                if (!hostMode) {
                    revealPlayer();
                    mainHandler.postDelayed(() -> sendPlaybackCommand(true, true), 250L);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    Log.e(TAG, "main frame error: " + request.getUrl() + ", code="
                            + (error == null ? "" : error.getErrorCode()));
                    if (!fallbackTried) loadDirectPlayer("web-error");
                    else showError();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null
                        && response.getStatusCode() >= 400) {
                    Log.e(TAG, "main frame http error: " + request.getUrl()
                            + ", status=" + response.getStatusCode());
                    if (!fallbackTried) loadDirectPlayer("http-" + response.getStatusCode());
                    else showError();
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

    private boolean shouldBlockMainNavigation(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if ("about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)) return false;
        if (!isAllowedTikTokUri(uri)) return true;
        String path = uri.getPath();
        if (hostMode) {
            return path != null && path.startsWith("/player/v1/");
        }
        return path == null || !path.startsWith("/player/v1/");
    }

    private void showPoster() {
        if (binding == null) return;
        binding.posterIv.setAlpha(1f);
        binding.posterIv.setVisibility(View.VISIBLE);
        binding.loadingView.setVisibility(View.VISIBLE);
        binding.centerPlayIndicator.setVisibility(View.GONE);
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

    private void loadIframePlayer() {
        if (binding == null || TextUtils.isEmpty(videoId)) {
            showError();
            return;
        }
        hostMode = true;
        fallbackTried = false;
        frameLoaded = false;
        playerReady = false;
        playerVisible = false;
        playing = true;
        unmuteAttempts = 0;
        String html = buildHostHtml(buildPlayerUrl(videoId, true));
        binding.webView.stopLoading();
        binding.webView.loadDataWithBaseURL(HOST_BASE_URL, html, "text/html", "UTF-8", null);
        startTimeout();
    }

    private void loadDirectPlayer(String reason) {
        if (binding == null || TextUtils.isEmpty(videoId) || fallbackTried) {
            showError();
            return;
        }
        Log.w(TAG, "iframe player fallback: " + reason);
        fallbackTried = true;
        hostMode = false;
        frameLoaded = false;
        playerReady = false;
        playerVisible = false;
        playing = true;
        unmuteAttempts = 0;
        binding.webView.stopLoading();
        binding.webView.loadUrl(buildPlayerUrl(videoId, false));
        startTimeout();
    }

    private void startTimeout() {
        mainHandler.removeCallbacks(loadTimeout);
        mainHandler.postDelayed(loadTimeout, PLAYER_TIMEOUT_MS);
    }

    private static String buildPlayerUrl(String id, boolean iframeMode) {
        return "https://www.tiktok.com/player/v1/" + Uri.encode(id)
                + "?autoplay=1"
                + "&muted=0"
                + "&loop=1"
                + "&controls=1"
                + "&progress_bar=1"
                + "&play_button=1"
                + "&volume_control=1"
                + "&fullscreen_button=0"
                + "&timestamp=0"
                + "&music_info=0"
                + "&description=0"
                + "&native_context_menu=0"
                + "&closed_caption=0"
                + "&rel=0";
    }

    private String buildHostHtml(String playerUrl) {
        String safePlayerUrl = htmlAttribute(playerUrl);
        String language = htmlAttribute(Locale.getDefault().toLanguageTag());
        return "<!doctype html><html lang=\"" + language + "\"><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover\">"
                + "<style>html,body{width:100%;height:100%;margin:0;padding:0;overflow:hidden;background:#000;}"
                + "#tt{display:block;width:100%;height:100%;border:0;background:#000;}</style>"
                + "</head><body>"
                + "<iframe id=\"tt\" title=\"TikTok video\" src=\"" + safePlayerUrl + "\" "
                + "allow=\"autoplay; encrypted-media; fullscreen; picture-in-picture\" allowfullscreen></iframe>"
                + "<script>(function(){"
                + "var f=document.getElementById('tt');"
                + "function bridge(n,a,b){try{var x=window.TalkamiTikTokBridge;if(x&&typeof x[n]==='function')x[n](a||'',b||'');}catch(e){}}"
                + "function send(t,v){try{if(!f||!f.contentWindow)return false;var m={'x-tiktok-player':true,type:t};"
                + "if(typeof v!=='undefined')m.value=v;f.contentWindow.postMessage(m,'*');return true;}catch(e){return false;}}"
                + "window.__talkamiSend=send;"
                + "function start(){send('play');setTimeout(function(){send('unMute');send('play');},60);}"
                + "f.addEventListener('load',function(){bridge('onFrameLoaded','','');setTimeout(start,80);setTimeout(start,500);});"
                + "window.addEventListener('message',function(e){try{if(e.source!==f.contentWindow)return;var d=e.data;"
                + "if(!d||d['x-tiktok-player']!==true)return;var value='';try{value=JSON.stringify(d.value);}catch(x){value=String(d.value||'');}"
                + "bridge('onPlayerMessage',String(d.type||''),value);"
                + "if(d.type==='onPlayerReady')start();"
                + "if(d.type==='onStateChange'&&Number(d.value)===0){send('seekTo',0);setTimeout(function(){send('play');},50);}"
                + "}catch(x){bridge('onHostError','message',String(x));}});"
                + "document.addEventListener('visibilitychange',function(){if(!document.hidden)setTimeout(start,80);});"
                + "})();</script></body></html>";
    }

    private void reloadPlayer() {
        if (binding == null) return;
        binding.errorPanel.setVisibility(View.GONE);
        binding.webView.setVisibility(View.VISIBLE);
        showPoster();
        loadIframePlayer();
    }

    private void togglePlayback() {
        if (binding == null) return;
        if (!playerReady && !playerVisible) {
            playing = true;
            sendPlaybackCommand(true, true);
            binding.centerPlayIndicator.setVisibility(View.GONE);
            return;
        }
        playing = !playing;
        sendPlaybackCommand(playing, playing);
        binding.centerPlayIndicator.setVisibility(playing ? View.GONE : View.VISIBLE);
        if (playing) revealPlayer();
    }

    private void sendPlaybackCommand(boolean shouldPlay, boolean unmute) {
        if (binding == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        String script;
        if (hostMode) {
            script = "(function(){try{var s=window.__talkamiSend;if(typeof s!=='function')return false;"
                    + (unmute ? "s('unMute');" : "")
                    + "s('" + (shouldPlay ? "play" : "pause") + "');return true;}catch(e){return false;}})()";
        } else {
            script = "(function(){try{var vs=document.querySelectorAll('video');if(!vs.length)return false;"
                    + "for(var i=0;i<vs.length;i++){var v=vs[i];"
                    + (unmute ? "v.muted=false;v.volume=1;" : "")
                    + (shouldPlay ? "var p=v.play();if(p&&p.catch)p.catch(function(){});" : "v.pause();")
                    + "}return true;}catch(e){return false;}})()";
        }
        try {
            binding.webView.evaluateJavascript(script, null);
        } catch (Throwable ignored) {
        }
    }

    private void revealPlayer() {
        if (binding == null || isFinishing() || isDestroyed()) return;
        playerVisible = true;
        mainHandler.removeCallbacks(loadTimeout);
        binding.loadingView.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.GONE);
        binding.webView.setVisibility(View.VISIBLE);
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
        binding.loadingView.setVisibility(View.GONE);
        binding.posterIv.setVisibility(View.GONE);
        binding.webView.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.VISIBLE);
    }

    public final class PlayerBridge {
        @JavascriptInterface
        public void onFrameLoaded(String ignoredA, String ignoredB) {
            mainHandler.post(() -> {
                if (binding == null || !hostMode) return;
                frameLoaded = true;
                // iframe load alone is not playback success. Wait for TikTok's ready/state message;
                // otherwise switch to the direct official player instead of accepting a black frame.
                mainHandler.postDelayed(() -> {
                    if (binding != null && hostMode && frameLoaded && !playerReady && !playerVisible) {
                        loadDirectPlayer("iframe-no-ready");
                    }
                }, 5_000L);
            });
        }

        @JavascriptInterface
        public void onPlayerMessage(String type, String value) {
            mainHandler.post(() -> handlePlayerMessage(type, value));
        }

        @JavascriptInterface
        public void onHostError(String type, String value) {
            Log.e(TAG, "host js error " + type + ": " + value);
        }
    }

    private void handlePlayerMessage(String type, String value) {
        if (binding == null || !hostMode) return;
        if ("onPlayerReady".equals(type)) {
            playerReady = true;
            revealPlayer();
            mainHandler.postDelayed(() -> sendPlaybackCommand(true, true), 80L);
            return;
        }
        if ("onStateChange".equals(type)) {
            int state = parseState(value);
            if (state == 1 || state == 2 || state == 3) revealPlayer();
            if (state == 1) {
                playing = true;
                binding.centerPlayIndicator.setVisibility(View.GONE);
            }
            return;
        }
        if ("onCurrentTime".equals(type)) {
            revealPlayer();
            return;
        }
        if ("onMute".equals(type) && playing && unmuteAttempts < 3) {
            unmuteAttempts++;
            mainHandler.postDelayed(() -> sendPlaybackCommand(true, true), 120L);
            return;
        }
        if ("onPlayerError".equals(type) || "onError".equals(type)) {
            Log.e(TAG, "player error: " + value);
            // 3002 is browser autoplay policy. The center native tap can still start playback.
            if (value != null && value.contains("3002")) {
                revealPlayer();
                playing = false;
                binding.centerPlayIndicator.setVisibility(View.VISIBLE);
            } else if (!fallbackTried) {
                loadDirectPlayer("player-error");
            } else {
                showError();
            }
        }
    }

    private static int parseState(String value) {
        if (TextUtils.isEmpty(value)) return -1;
        Matcher matcher = Pattern.compile("-?[0-9]+").matcher(value);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group());
        } catch (Throwable ignored) {
            return -1;
        }
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

    private static String htmlAttribute(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (binding != null) {
            binding.webView.onResume();
            if (hostPageFinished || playerVisible) {
                playing = true;
                binding.centerPlayIndicator.setVisibility(View.GONE);
                mainHandler.postDelayed(() -> sendPlaybackCommand(true, true), 120L);
            }
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (binding != null) {
            sendPlaybackCommand(false, false);
            binding.webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (binding != null) {
            Glide.with(this).clear(binding.posterIv);
            try {
                binding.webView.removeJavascriptInterface("TalkamiTikTokBridge");
            } catch (Throwable ignored) {
            }
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
