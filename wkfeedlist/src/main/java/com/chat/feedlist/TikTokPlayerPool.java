package com.chat.feedlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Locale;

import org.json.JSONObject;

/**
 * Process-wide single TikTok WebView.
 *
 * It preloads at most one official TikTok player while the timeline is idle, then moves the same
 * WebView into the full-screen activity. Moving instead of recreating avoids a second DNS/TLS,
 * player-JS and iframe startup sequence.
 */
public final class TikTokPlayerPool {
    private static final String TAG = "TikTokPlayerPool";
    private static final String HOST_BASE_URL = "https://www.tiktok.com/";
    private static final long DETACHED_KEEP_ALIVE_MS = 90_000L;
    private static final TikTokPlayerPool INSTANCE = new TikTokPlayerPool();

    public interface Listener {
        void onPageStarted(boolean hostMode);
        void onPageFinished(boolean hostMode);
        void onFrameLoaded();
        void onPlayerMessage(String type, String value);
        void onDirectVisual();
        void onMainFrameError(String reason);
        void onRenderProcessGone();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private Context appContext;
    private MutableContextWrapper contextWrapper;
    private WebView webView;
    private ViewGroup currentHost;
    private WeakReference<Activity> fullScreenOwner = new WeakReference<>(null);
    private Listener listener;
    private String currentVideoId = "";
    private boolean hostMode = true;
    private boolean preloading;
    private boolean playerReady;
    private boolean firstVisual;
    private long loadGeneration;

    private final Runnable destroyWhenIdle = () -> {
        if (fullScreenOwner.get() == null && currentHost == null) destroyNow();
    };

    private TikTokPlayerPool() {
    }

    public static TikTokPlayerPool get(@NonNull Context context) {
        INSTANCE.ensureAppContext(context);
        return INSTANCE;
    }

    public boolean isMatchingReady(String videoId) {
        return isValidVideoId(videoId)
                && TextUtils.equals(currentVideoId, videoId.trim())
                && playerReady
                && webView != null;
    }

    /** Loads one muted real player into an off-screen portrait host. */
    public void preload(@NonNull Activity activity, @NonNull ViewGroup hiddenHost, @NonNull String videoId) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> preload(activity, hiddenHost, videoId));
            return;
        }
        if (activity.isFinishing() || !isValidVideoId(videoId)) return;
        if (fullScreenOwner.get() != null) return;

        String normalized = videoId.trim();
        cancelIdleDestroy();
        if (!attachWebView(activity, hiddenHost, false)) return;

        if (TextUtils.equals(currentVideoId, normalized) && webView != null && hostMode) {
            preloading = true;
            listener = null;
            if (playerReady) {
                sendCommand("mute", null);
                sendCommand("pause", null);
            }
            return;
        }
        loadHost(normalized, true);
    }

    /**
     * Runs inside the list-cover click callback. This uses the real user gesture to request play and
     * sound before the Activity transition starts, improving autoplay success on stricter WebViews.
     */
    public void promoteFromUserGesture(@NonNull String videoId) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> promoteFromUserGesture(videoId));
            return;
        }
        if (!isValidVideoId(videoId) || !TextUtils.equals(currentVideoId, videoId.trim()) || webView == null) return;
        preloading = false;
        sendCommand("unMute", null);
        sendCommand("play", null);
    }

    /** Attaches the pooled player to full screen. Returns true when the requested id was preloaded. */
    public boolean attachFullScreen(
            @NonNull Activity activity,
            @NonNull ViewGroup host,
            @NonNull String videoId,
            @NonNull Listener callback
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> attachFullScreen(activity, host, videoId, callback));
            return false;
        }
        if (activity.isFinishing() || !isValidVideoId(videoId)) return false;

        String normalized = videoId.trim();
        boolean reused = TextUtils.equals(currentVideoId, normalized)
                && webView != null && hostMode && playerReady;
        cancelIdleDestroy();
        listener = callback;
        preloading = false;
        fullScreenOwner = new WeakReference<>(activity);
        if (!attachWebView(activity, host, true)) {
            listener = null;
            fullScreenOwner.clear();
            return false;
        }

        if (!reused) {
            // A matching but not-ready hidden preload must not be treated as reusable. Some WebView
            // implementations defer a tiny/off-screen iframe; reload after the real full-screen size
            // is attached so the user does not wait on a permanently half-initialized player.
            loadHost(normalized, false);
        } else {
            // Moving a WebView can require one fresh compositor frame. Do not reuse a hidden-host
            // firstVisual flag; full screen keeps its poster until a new playing/time event arrives.
            firstVisual = false;
            callback.onPageStarted(hostMode);
            if (playerReady) callback.onPlayerMessage("onPlayerReady", "true");
            sendCommand("unMute", null);
            sendCommand("play", null);
        }
        return reused;
    }

    public void reloadHost(@NonNull String videoId) {
        if (isValidVideoId(videoId)) loadHost(videoId.trim(), false);
    }

    public void loadDirect(@NonNull String videoId) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> loadDirect(videoId));
            return;
        }
        if (webView == null || !isValidVideoId(videoId)) return;
        loadGeneration++;
        currentVideoId = videoId.trim();
        hostMode = false;
        preloading = false;
        playerReady = false;
        firstVisual = false;
        webView.stopLoading();
        webView.loadUrl(buildPlayerUrl(currentVideoId, true, false));
    }

    public void play(boolean unmute) {
        if (unmute) sendCommand("unMute", null);
        sendCommand("play", null);
    }

    public void pause() {
        sendCommand("pause", null);
    }

    public void seekTo(double seconds) {
        sendCommand("seekTo", Math.max(0d, seconds));
    }

    public void onResume() {
        if (webView != null) webView.onResume();
    }

    public void onPause() {
        pause();
        if (webView != null) webView.onPause();
    }

    /** Detaches from the finished full-screen Activity and removes all Activity references. */
    public void detachFullScreen(@NonNull Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> detachFullScreen(activity));
            return;
        }
        Activity owner = fullScreenOwner.get();
        if (owner != null && owner != activity) return;
        listener = null;
        fullScreenOwner.clear();
        pause();
        detachFromParent();
        if (contextWrapper != null && appContext != null) contextWrapper.setBaseContext(appContext);
        main.removeCallbacks(destroyWhenIdle);
        main.postDelayed(destroyWhenIdle, DETACHED_KEEP_ALIVE_MS);
    }

    /** Releases hidden timeline ownership but keeps the player briefly for a fast back/open cycle. */
    public void detachPreloadHost(@NonNull ViewGroup hiddenHost) {
        if (currentHost != hiddenHost || fullScreenOwner.get() != null) return;
        listener = null;
        pause();
        detachFromParent();
        if (contextWrapper != null && appContext != null) contextWrapper.setBaseContext(appContext);
        main.removeCallbacks(destroyWhenIdle);
        main.postDelayed(destroyWhenIdle, DETACHED_KEEP_ALIVE_MS);
    }

    public void destroyNow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::destroyNow);
            return;
        }
        main.removeCallbacks(destroyWhenIdle);
        listener = null;
        fullScreenOwner.clear();
        detachFromParent();
        WebView player = webView;
        webView = null;
        currentVideoId = "";
        playerReady = false;
        firstVisual = false;
        preloading = false;
        hostMode = true;
        loadGeneration++;
        if (player != null) {
            try {
                player.removeJavascriptInterface("TalkamiTikTokBridge");
                player.stopLoading();
                player.loadUrl("about:blank");
                player.clearHistory();
                player.removeAllViews();
                player.destroy();
            } catch (Throwable ignored) {
            }
        }
        contextWrapper = null;
    }

    private void ensureAppContext(Context context) {
        if (appContext == null) appContext = context.getApplicationContext();
    }

    private void cancelIdleDestroy() {
        main.removeCallbacks(destroyWhenIdle);
    }

    private boolean attachWebView(Activity activity, ViewGroup host, boolean fullSize) {
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed())) return false;
        WebView player = ensureWebView(activity);
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed())) return false;
        if (contextWrapper != null) contextWrapper.setBaseContext(activity);
        ViewParent oldParent = player.getParent();
        if (oldParent instanceof ViewGroup && oldParent != host) {
            ((ViewGroup) oldParent).removeView(player);
        }
        currentHost = host;
        if (player.getParent() != host) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            host.addView(player, 0, params);
        } else {
            ViewGroup.LayoutParams params = player.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            player.setLayoutParams(params);
        }
        player.setAlpha(fullSize ? 1f : 0.01f);
        player.setVisibility(View.VISIBLE);
        player.onResume();
        return true;
    }

    private void detachFromParent() {
        WebView player = webView;
        if (player != null) {
            ViewParent parent = player.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(player);
        }
        currentHost = null;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView ensureWebView(Activity activity) {
        if (webView != null) return webView;
        if (appContext == null) appContext = activity.getApplicationContext();
        contextWrapper = new MutableContextWrapper(activity);
        WebView player = new WebView(contextWrapper);
        WebSettings settings = player.getSettings();
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
            cookieManager.setAcceptThirdPartyCookies(player, true);
        }

        player.setBackgroundColor(Color.BLACK);
        player.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        player.setOverScrollMode(View.OVER_SCROLL_NEVER);
        player.setVerticalScrollBarEnabled(false);
        player.setHorizontalScrollBarEnabled(false);
        player.setLongClickable(false);
        player.setHapticFeedbackEnabled(false);
        player.setOnLongClickListener(v -> true);
        player.addJavascriptInterface(new PlayerBridge(this), "TalkamiTikTokBridge");
        player.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (BuildConfig.DEBUG && consoleMessage != null) {
                    Log.d(TAG, "js: " + consoleMessage.message());
                }
                return true;
            }
        });
        player.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // This legacy callback does not expose isForMainFrame. Do not accidentally block
                // the TikTok iframe itself; the API-24 request overload below enforces main-frame
                // navigation rules precisely.
                if (TextUtils.isEmpty(url)) return true;
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme();
                if ("about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)
                        || "blob".equalsIgnoreCase(scheme)) return false;
                return !isAllowedTikTokUri(uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
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
                Listener callback = listener;
                if (callback != null) callback.onPageStarted(hostMode);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Listener callback = listener;
                if (callback != null) callback.onPageFinished(hostMode);
                if (!hostMode) installDirectVisualObserver(view, loadGeneration);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    Listener callback = listener;
                    if (callback != null) callback.onMainFrameError("web-" + (error == null ? "" : error.getErrorCode()));
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null && response.getStatusCode() >= 400) {
                    Listener callback = listener;
                    if (callback != null) callback.onMainFrameError("http-" + response.getStatusCode());
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Listener callback = listener;
                if (callback != null) callback.onRenderProcessGone();
                main.post(TikTokPlayerPool.this::destroyNow);
                return true;
            }
        });
        webView = player;
        return player;
    }

    private void loadHost(String videoId, boolean asPreload) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> loadHost(videoId, asPreload));
            return;
        }
        if (webView == null || !isValidVideoId(videoId)) return;
        loadGeneration++;
        currentVideoId = videoId;
        hostMode = true;
        preloading = asPreload;
        playerReady = false;
        firstVisual = false;
        String html = buildHostHtml(videoId, asPreload);
        webView.stopLoading();
        webView.loadDataWithBaseURL(HOST_BASE_URL, html, "text/html", "UTF-8", null);
    }

    private void installDirectVisualObserver(WebView view, long expectedGeneration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        String script = "(function watch(n){try{"
                + "if(window.__talkamiDirectWatch)return true;"
                + "var v=document.querySelector('video');"
                + "if(!v){if(n<30)setTimeout(function(){watch(n+1);},100);return false;}"
                + "window.__talkamiDirectWatch=true;"
                + "function seen(){try{if(v.currentTime>0||(!v.paused&&v.readyState>=2)){TalkamiTikTokBridge.onDirectVisual();}}catch(e){}}"
                + "v.addEventListener('playing',seen);v.addEventListener('timeupdate',seen);v.addEventListener('loadeddata',seen);"
                + "seen();return true;}catch(e){return false;}})(0)";
        main.postDelayed(() -> {
            if (webView == view && expectedGeneration == loadGeneration) {
                try { view.evaluateJavascript(script, null); } catch (Throwable ignored) { }
            }
        }, 120L);
    }

    private void sendCommand(String type, @Nullable Object value) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> sendCommand(type, value));
            return;
        }
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        String script;
        if (hostMode) {
            String valueJs = value == null ? "undefined" : String.valueOf(value);
            script = "(function(){try{var s=window.__talkamiSend;if(typeof s!=='function')return false;"
                    + "return s('" + jsString(type) + "'," + valueJs + ");}catch(e){return false;}})()";
        } else {
            boolean play = "play".equals(type);
            boolean pause = "pause".equals(type);
            boolean unmute = "unMute".equals(type);
            boolean mute = "mute".equals(type);
            script = "(function(){try{var vs=document.querySelectorAll('video');if(!vs.length)return false;"
                    + "for(var i=0;i<vs.length;i++){var v=vs[i];"
                    + (unmute ? "v.muted=false;v.volume=1;" : "")
                    + (mute ? "v.muted=true;" : "")
                    + (play ? "var p=v.play();if(p&&p.catch)p.catch(function(){});" : "")
                    + (pause ? "v.pause();" : "")
                    + "}return true;}catch(e){return false;}})()";
        }
        try { webView.evaluateJavascript(script, null); } catch (Throwable ignored) { }
    }

    private boolean shouldBlockMainNavigation(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if ("about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)) return false;
        if (!isAllowedTikTokUri(uri)) return true;
        String path = uri.getPath();
        if (hostMode) return path != null && path.startsWith("/player/v1/");
        return path == null || !path.startsWith("/player/v1/");
    }

    private static String buildPlayerUrl(String id, boolean autoplay, boolean muted) {
        return "https://www.tiktok.com/player/v1/" + Uri.encode(id)
                + "?autoplay=" + (autoplay ? "1" : "0")
                + "&muted=" + (muted ? "1" : "0")
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

    private static String buildHostHtml(String videoId, boolean preload) {
        String playerUrl = buildPlayerUrl(videoId, !preload, preload);
        String safePlayerUrl = htmlAttribute(playerUrl);
        String language = htmlAttribute(Locale.getDefault().toLanguageTag());
        String startCommands = preload
                ? "send('mute');send('pause');"
                : "send('unMute');send('play');";
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
                + "if(typeof v!=='undefined')m.value=v;f.contentWindow.postMessage(m,'https://www.tiktok.com');return true;}catch(e){return false;}}"
                + "window.__talkamiSend=send;"
                + "function start(){" + startCommands + "}"
                + "f.addEventListener('load',function(){bridge('onFrameLoaded','','');setTimeout(start,60);setTimeout(start,420);});"
                + "window.addEventListener('message',function(e){try{if(e.source!==f.contentWindow)return;"
                + "if(e.origin!=='https://www.tiktok.com'&&!/\\.tiktok\\.com$/.test((new URL(e.origin)).hostname))return;"
                + "var d=e.data;if(!d||d['x-tiktok-player']!==true)return;var value='';"
                + "try{value=JSON.stringify(d.value);}catch(x){value=String(d.value||'');}"
                + "bridge('onPlayerMessage',String(d.type||''),value);"
                + "if(d.type==='onPlayerReady')start();"
                + "if(d.type==='onStateChange'&&Number(d.value)===0){send('seekTo',0);setTimeout(function(){send('play');},40);}" 
                + "}catch(x){bridge('onHostError','message',String(x));}});"
                + "document.addEventListener('visibilitychange',function(){if(!document.hidden)setTimeout(start,60);});"
                + "})();</script></body></html>";
    }

    private static boolean isAllowedTikTokUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("tiktok.com") || host.endsWith(".tiktok.com");
    }

    private static boolean isValidVideoId(String value) {
        return !TextUtils.isEmpty(value) && value.trim().matches("[0-9]{8,32}");
    }

    private static String htmlAttribute(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String jsString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static final class PlayerBridge {
        private final WeakReference<TikTokPlayerPool> owner;

        private PlayerBridge(TikTokPlayerPool owner) {
            this.owner = new WeakReference<>(owner);
        }

        @JavascriptInterface
        public void onFrameLoaded(String ignoredA, String ignoredB) {
            TikTokPlayerPool pool = owner.get();
            if (pool == null) return;
            pool.main.post(() -> {
                Listener callback = pool.listener;
                if (callback != null) callback.onFrameLoaded();
            });
        }

        @JavascriptInterface
        public void onPlayerMessage(String type, String value) {
            TikTokPlayerPool pool = owner.get();
            if (pool == null) return;
            pool.main.post(() -> {
                if ("onPlayerReady".equals(type)) {
                    pool.playerReady = true;
                    if (pool.preloading) {
                        pool.sendCommand("mute", null);
                        pool.sendCommand("pause", null);
                    }
                }
                if ("onStateChange".equals(type) && parseStateValue(value) == 1) {
                    pool.firstVisual = true;
                }
                if ("onCurrentTime".equals(type) && parsePositiveNumber(value) > 0.02d) {
                    pool.firstVisual = true;
                }
                Listener callback = pool.listener;
                if (callback != null) callback.onPlayerMessage(type, value);
            });
        }

        @JavascriptInterface
        public void onDirectVisual() {
            TikTokPlayerPool pool = owner.get();
            if (pool == null) return;
            pool.main.post(() -> {
                pool.firstVisual = true;
                Listener callback = pool.listener;
                if (callback != null) callback.onDirectVisual();
            });
        }

        @JavascriptInterface
        public void onHostError(String type, String value) {
            Log.e(TAG, "host js error " + type + ": " + value);
        }

        private static int parseStateValue(String value) {
            if (TextUtils.isEmpty(value)) return -1;
            String normalized = value.trim();
            try {
                if (normalized.startsWith("{")) {
                    JSONObject object = new JSONObject(normalized);
                    if (object.has("state")) return object.optInt("state", -1);
                    if (object.has("value")) return object.optInt("value", -1);
                    return -1;
                }
                if (normalized.length() >= 2 && normalized.startsWith("\"")
                        && normalized.endsWith("\"")) {
                    normalized = normalized.substring(1, normalized.length() - 1);
                }
                return (int) Double.parseDouble(normalized);
            } catch (Throwable ignored) {
                return -1;
            }
        }

        private static double parsePositiveNumber(String value) {
            if (TextUtils.isEmpty(value)) return 0d;
            try {
                String normalized = value.replaceAll("[^0-9.eE+-]", "");
                return Double.parseDouble(normalized);
            } catch (Throwable ignored) {
                return 0d;
            }
        }
    }
}
