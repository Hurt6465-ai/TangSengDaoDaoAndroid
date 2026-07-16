package com.chat.feedlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Warms one real TikTok player without moving that WebView into the full-screen Activity.
 * Chromium's DNS, TLS, cookies and static player resources remain warm for the stable
 * dedicated full-screen WebView, while avoiding the lifecycle problems caused by reparenting.
 */
public final class TikTokPlaybackPreloader {
    private static final String HOST_BASE_URL = "https://www.tiktok.com/";
    private static final long LOAD_TIMEOUT_MS = 12_000L;
    private static final long SAME_VIDEO_TTL_MS = 3L * 60L * 1000L;

    private final Activity activity;
    private final FrameLayout host;
    private final Handler main = new Handler(Looper.getMainLooper());

    private WebView webView;
    private String currentVideoId = "";
    private long currentStartedAt;
    private long generation;
    private boolean playerWarmed;
    private boolean loading;
    private boolean released;

    public TikTokPlaybackPreloader(@NonNull Activity activity, @NonNull FrameLayout host) {
        this.activity = activity;
        this.host = host;
    }

    public void preload(String videoId) {
        String normalized = videoId == null ? "" : videoId.trim();
        if (released || activity.isFinishing() || activity.isDestroyed()
                || !normalized.matches("[0-9]{8,32}") || !canUseCurrentNetwork()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (TextUtils.equals(currentVideoId, normalized)
                && now - currentStartedAt < SAME_VIDEO_TTL_MS
                && (playerWarmed || loading)) {
            return;
        }

        generation++;
        long expectedGeneration = generation;
        currentVideoId = normalized;
        currentStartedAt = now;
        playerWarmed = false;
        loading = true;

        WebView player = ensureWebView();
        if (player == null) return;
        attach(player);
        player.stopLoading();
        player.loadDataWithBaseURL(
                HOST_BASE_URL,
                buildHostHtml(buildPlayerUrl(normalized)),
                "text/html",
                "UTF-8",
                null
        );

        main.postDelayed(() -> {
            if (released || expectedGeneration != generation || playerWarmed) return;
            // Let a later idle pass retry instead of retaining a permanently failed page.
            currentVideoId = "";
            currentStartedAt = 0L;
            loading = false;
            try {
                if (webView != null) webView.loadUrl("about:blank");
            } catch (Throwable ignored) {
            }
        }, LOAD_TIMEOUT_MS);
    }

    public void pause() {
        WebView player = webView;
        if (player == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        try {
            player.evaluateJavascript(
                    "(function(){try{var s=window.__talkamiPreloadSend;if(typeof s==='function')s('pause');}catch(e){}})();",
                    null
            );
            player.onPause();
            if (!playerWarmed) loading = false;
        } catch (Throwable ignored) {
        }
    }

    public void release() {
        if (released) return;
        released = true;
        generation++;
        main.removeCallbacksAndMessages(null);
        WebView player = webView;
        webView = null;
        currentVideoId = "";
        playerWarmed = false;
        loading = false;
        try {
            host.removeAllViews();
        } catch (Throwable ignored) {
        }
        if (player != null) {
            try {
                player.stopLoading();
                player.loadUrl("about:blank");
                player.clearHistory();
                player.removeJavascriptInterface("TalkamiTikTokPreloadBridge");
                player.removeAllViews();
                player.destroy();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean canUseCurrentNetwork() {
        try {
            ConnectivityManager manager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            Network active = manager.getActiveNetwork();
            NetworkCapabilities capabilities = active == null ? null : manager.getNetworkCapabilities(active);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && !manager.isActiveNetworkMetered();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void attach(WebView player) {
        if (player.getParent() instanceof ViewGroup) {
            ((ViewGroup) player.getParent()).removeView(player);
        }
        host.removeAllViews();
        host.addView(player, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        host.setVisibility(View.VISIBLE);
        player.setVisibility(View.VISIBLE);
        player.onResume();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView ensureWebView() {
        if (webView != null) return webView;
        if (activity.isFinishing() || activity.isDestroyed()) return null;

        WebView player = new WebView(activity);
        WebSettings settings = player.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(player, true);
        }

        player.setBackgroundColor(Color.BLACK);
        player.setOverScrollMode(View.OVER_SCROLL_NEVER);
        player.setVerticalScrollBarEnabled(false);
        player.setHorizontalScrollBarEnabled(false);
        player.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        player.addJavascriptInterface(new PreloadBridge(), "TalkamiTikTokPreloadBridge");
        player.setWebChromeClient(new WebChromeClient());
        player.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowedUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return !isAllowedUrl(String.valueOf(request.getUrl()));
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view == webView) {
                    try {
                        host.removeView(view);
                    } catch (Throwable ignored) {
                    }
                    webView = null;
                    currentVideoId = "";
                    playerWarmed = false;
                    loading = false;
                }
                return true;
            }
        });
        webView = player;
        return player;
    }

    private boolean isAllowedUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        Uri uri;
        try {
            uri = Uri.parse(value);
        } catch (Throwable ignored) {
            return false;
        }
        String scheme = uri.getScheme();
        if ("about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)
                || "blob".equalsIgnoreCase(scheme)) {
            return true;
        }
        String hostName = uri.getHost();
        return "https".equalsIgnoreCase(scheme)
                && hostName != null
                && (hostName.equalsIgnoreCase("tiktok.com")
                || hostName.toLowerCase(Locale.ROOT).endsWith(".tiktok.com"));
    }

    private String buildPlayerUrl(String videoId) {
        return "https://www.tiktok.com/player/v1/" + Uri.encode(videoId)
                + "?autoplay=1"
                + "&muted=1"
                + "&loop=0"
                + "&controls=0"
                + "&progress_bar=0"
                + "&play_button=0"
                + "&volume_control=0"
                + "&fullscreen_button=0"
                + "&timestamp=0"
                + "&music_info=0"
                + "&description=0"
                + "&native_context_menu=0"
                + "&closed_caption=0"
                + "&rel=0";
    }

    private String buildHostHtml(String playerUrl) {
        String safeUrl = htmlAttribute(playerUrl);
        return "<!doctype html><html><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">"
                + "<style>html,body,#tt{width:100%;height:100%;margin:0;padding:0;border:0;overflow:hidden;background:#000}</style>"
                + "</head><body><iframe id=\"tt\" src=\"" + safeUrl + "\" "
                + "allow=\"autoplay; encrypted-media; fullscreen; picture-in-picture\"></iframe>"
                + "<script>(function(){var f=document.getElementById('tt'),done=false;"
                + "function b(n,a){try{var x=window.TalkamiTikTokPreloadBridge;if(x&&typeof x[n]==='function')x[n](a||'');}catch(e){}}"
                + "function s(t,v){try{if(!f||!f.contentWindow)return;var m={'x-tiktok-player':true,type:t};if(typeof v!=='undefined')m.value=v;f.contentWindow.postMessage(m,'*');}catch(e){}}"
                + "window.__talkamiPreloadSend=s;"
                + "function start(){s('mute');s('play');}"
                + "f.addEventListener('load',function(){setTimeout(start,80);setTimeout(start,450);});"
                + "window.addEventListener('message',function(e){try{if(e.source!==f.contentWindow)return;var d=e.data;if(!d||d['x-tiktok-player']!==true)return;"
                + "if(d.type==='onPlayerReady')start();"
                + "if(!done&&d.type==='onCurrentTime'&&Number(d.value)>0.05){done=true;s('pause');b('onWarmed','');}"
                + "if(d.type==='onPlayerError')b('onError',String(d.value||''));"
                + "}catch(x){b('onError',String(x));}});})();</script></body></html>";
    }

    private static String htmlAttribute(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private final class PreloadBridge {
        @JavascriptInterface
        public void onWarmed(String ignored) {
            main.post(() -> {
                if (released || TextUtils.isEmpty(currentVideoId)) return;
                playerWarmed = true;
                loading = false;
                currentStartedAt = System.currentTimeMillis();
            });
        }

        @JavascriptInterface
        public void onError(String ignored) {
            main.post(() -> {
                if (released) return;
                currentVideoId = "";
                currentStartedAt = 0L;
                playerWarmed = false;
                loading = false;
            });
        }
    }
}
