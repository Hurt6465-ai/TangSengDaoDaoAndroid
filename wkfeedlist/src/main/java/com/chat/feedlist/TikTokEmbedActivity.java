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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
    private static final long LOAD_TIMEOUT_MS = 15_000L;
    private static final AtomicBoolean PREWARMED = new AtomicBoolean(false);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityTiktokEmbedBinding binding;
    private String videoId = "";
    private String externalUrl = "";
    private String coverUrl = "";
    private boolean mainPageFinished;
    private boolean playing = true;
    private boolean resumed;
    private long pageFinishedAt;

    private final Runnable loadTimeout = () -> {
        if (!mainPageFinished) showError();
    };

    private final Runnable loopKeeper = new Runnable() {
        @Override
        public void run() {
            if (binding == null || isFinishing() || isDestroyed()) return;
            if (resumed && playing && mainPageFinished) {
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
        settings.setLoadWithOverviewMode(true);
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
        binding.webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        binding.webView.setLongClickable(false);
        binding.webView.setHapticFeedbackEnabled(false);
        binding.webView.setOnLongClickListener(v -> true);
        binding.webView.setWebChromeClient(new WebChromeClient());
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldBlockNavigation(url == null ? null : Uri.parse(url));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldBlockNavigation(request == null ? null : request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainPageFinished = false;
                playing = true;
                binding.errorPanel.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                binding.loadingView.setVisibility(View.VISIBLE);
                binding.posterIv.setVisibility(View.VISIBLE);
                binding.centerPlayIndicator.setVisibility(View.GONE);
                mainHandler.removeCallbacks(loadTimeout);
                mainHandler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!isOfficialPlayerUri(url == null ? null : Uri.parse(url))) return;
                mainPageFinished = true;
                pageFinishedAt = System.currentTimeMillis();
                mainHandler.removeCallbacks(loadTimeout);
                binding.errorPanel.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                forceStartWithSound();
                probeFirstFrame();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    Log.e(TAG, "main frame error: " + request.getUrl() + ", code="
                            + (error == null ? "" : error.getErrorCode()));
                    showError();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null
                        && response.getStatusCode() >= 400) {
                    Log.e(TAG, "main frame http error: " + request.getUrl()
                            + ", status=" + response.getStatusCode());
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
        binding.posterIv.setVisibility(View.VISIBLE);
        binding.loadingView.setVisibility(View.VISIBLE);
        if (TextUtils.isEmpty(coverUrl)) {
            binding.posterIv.setImageResource(android.R.color.black);
            return;
        }
        String userAgent = binding.webView.getSettings().getUserAgentString();
        GlideUrl request = new GlideUrl(coverUrl, new LazyHeaders.Builder()
                .addHeader("Referer", "https://www.tiktok.com/")
                .addHeader("User-Agent", TextUtils.isEmpty(userAgent) ? "Mozilla/5.0" : userAgent)
                .build());
        Glide.with(this)
                .load(request)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
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
        String playerUrl = "https://www.tiktok.com/player/v1/" + videoId
                + "?autoplay=1&muted=0&loop=1&controls=1&progress_bar=1&play_button=0"
                + "&volume_control=1&fullscreen_button=0&timestamp=0&music_info=0"
                + "&description=0&native_context_menu=0&closed_caption=0&rel=0";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.tiktok.com/");
        headers.put("Accept-Language", Locale.getDefault().toLanguageTag());
        binding.webView.loadUrl(playerUrl, headers);
    }

    private void reloadPlayer() {
        if (binding == null) return;
        binding.errorPanel.setVisibility(View.GONE);
        showPoster();
        binding.webView.clearHistory();
        loadOfficialPlayer();
    }

    private void forceStartWithSound() {
        mainHandler.postDelayed(() -> evaluatePlaybackScript(true, true), 100L);
        mainHandler.postDelayed(() -> evaluatePlaybackScript(true, true), 550L);
        mainHandler.postDelayed(() -> evaluatePlaybackScript(true, true), 1_300L);
    }

    private void togglePlayback() {
        if (!mainPageFinished || binding == null) return;
        playing = !playing;
        evaluatePlaybackScript(playing, playing);
        binding.centerPlayIndicator.setVisibility(playing ? View.GONE : View.VISIBLE);
    }

    private void evaluatePlaybackScript(boolean shouldPlay, boolean unmute) {
        if (binding == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        String command = shouldPlay ? "play" : "pause";
        String script = "(function(){"
                + "try{window.postMessage({'x-tiktok-player':true,type:'" + command + "'},'*');}catch(e){}"
                + (unmute
                ? "try{window.postMessage({'x-tiktok-player':true,type:'unMute'},'*');}catch(e){}"
                : "")
                + "try{var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++){"
                + "var v=vs[i];v.loop=true;"
                + (unmute ? "v.muted=false;v.volume=1;" : "")
                + (shouldPlay
                ? "if(v.ended){v.currentTime=0;}var p=v.play();if(p&&p.catch){p.catch(function(){});}"
                : "v.pause();")
                + "}}catch(e){}return true;})()";
        try {
            binding.webView.evaluateJavascript(script, null);
        } catch (Throwable ignored) {
        }
    }

    private void probeFirstFrame() {
        if (binding == null || !mainPageFinished) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            revealPlayer();
            return;
        }
        try {
            binding.webView.evaluateJavascript(
                    "(function(){try{var v=document.querySelector('video');return !!(v&&v.readyState>=2);}catch(e){return false;}})()",
                    value -> {
                        if (binding == null || isFinishing() || isDestroyed()) return;
                        boolean ready = "true".equalsIgnoreCase(String.valueOf(value));
                        long waited = Math.max(0L, System.currentTimeMillis() - pageFinishedAt);
                        if (ready || waited >= 1_800L) {
                            revealPlayer();
                        } else {
                            mainHandler.postDelayed(this::probeFirstFrame, 160L);
                        }
                    }
            );
        } catch (Throwable ignored) {
            mainHandler.postDelayed(this::revealPlayer, 700L);
        }
    }

    private void revealPlayer() {
        if (binding == null || isFinishing() || isDestroyed()) return;
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

    /** Blocks every main-frame navigation except the official player itself. */
    private boolean shouldBlockNavigation(Uri uri) {
        return !isOfficialPlayerUri(uri);
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
        Uri uri;
        try {
            uri = Uri.parse(value.trim());
        } catch (Throwable ignored) {
            return "";
        }
        return isAllowedTikTokUri(uri) ? uri.toString() : "";
    }

    private static String safeHttpsUrl(String value) {
        if (TextUtils.isEmpty(value)) return "";
        try {
            Uri uri = Uri.parse(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && !TextUtils.isEmpty(uri.getHost())
                    ? uri.toString()
                    : "";
        } catch (Throwable ignored) {
            return "";
        }
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
            if (mainPageFinished) {
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
