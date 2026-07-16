package com.chat.feedlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
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

import com.chat.feedlist.databinding.ActivityTiktokEmbedBinding;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
    private static final long LOAD_TIMEOUT_MS = 15_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityTiktokEmbedBinding binding;
    private String videoId = "";
    private String externalUrl = "";
    private boolean fallbackTried;
    private boolean mainPageFinished;

    private final Runnable loadTimeout = () -> {
        if (!mainPageFinished) fallbackOrShowError("timeout");
    };

    public static void open(Context context, String videoId, String externalUrl) {
        if (context == null) return;
        String resolvedId = resolveVideoId(videoId, externalUrl);
        String safeUrl = safeTikTokUrl(externalUrl);
        if (TextUtils.isEmpty(resolvedId) && TextUtils.isEmpty(safeUrl)) return;
        Intent intent = new Intent(context, TikTokEmbedActivity.class);
        intent.putExtra(EXTRA_ID, resolvedId);
        intent.putExtra(EXTRA_URL, safeUrl);
        context.startActivity(intent);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTiktokEmbedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        externalUrl = safeTikTokUrl(getIntent().getStringExtra(EXTRA_URL));
        videoId = resolveVideoId(getIntent().getStringExtra(EXTRA_ID), externalUrl);
        if (TextUtils.isEmpty(videoId) && TextUtils.isEmpty(externalUrl)) {
            finish();
            return;
        }

        binding.backBtn.setOnClickListener(v -> finish());
        binding.openExternalBtn.setOnClickListener(v -> openExternal());
        configureWebView();
        loadOfficialPlayer();
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
            // Some TikTok pages reject the special Android WebView marker even though
            // the same Chromium version works in normal Chrome.
            userAgent = userAgent.replace("; wv", "").replace("Version/4.0 ", "");
            settings.setUserAgentString(userAgent);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(binding.webView, true);
        }

        binding.webView.setWebChromeClient(new WebChromeClient());
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldBlock(url == null ? null : Uri.parse(url));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldBlock(request == null ? null : request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainPageFinished = false;
                binding.errorPanel.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                mainHandler.removeCallbacks(loadTimeout);
                mainHandler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mainPageFinished = true;
                mainHandler.removeCallbacks(loadTimeout);
                binding.errorPanel.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    Log.e(TAG, "main frame error: " + request.getUrl() + ", code="
                            + (error == null ? "" : error.getErrorCode()));
                    fallbackOrShowError("web-error");
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null && response.getStatusCode() >= 400) {
                    Log.e(TAG, "main frame http error: " + request.getUrl() + ", status=" + response.getStatusCode());
                    fallbackOrShowError("http-" + response.getStatusCode());
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

    private void loadOfficialPlayer() {
        if (TextUtils.isEmpty(videoId)) {
            loadExternalFallback();
            return;
        }
        String playerUrl = "https://www.tiktok.com/player/v1/" + videoId
                + "?autoplay=1&muted=0&controls=1&progress_bar=1&play_button=1"
                + "&volume_control=1&fullscreen_button=1&loop=0";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.tiktok.com/");
        headers.put("Accept-Language", Locale.getDefault().toLanguageTag());
        binding.webView.loadUrl(playerUrl, headers);
    }

    private void fallbackOrShowError(String reason) {
        if (binding == null || isFinishing() || isDestroyed()) return;
        mainHandler.removeCallbacks(loadTimeout);
        if (!fallbackTried && !TextUtils.isEmpty(externalUrl)) {
            fallbackTried = true;
            Log.w(TAG, "player failed, loading canonical page: " + reason);
            loadExternalFallback();
            return;
        }
        showError();
    }

    private void loadExternalFallback() {
        if (TextUtils.isEmpty(externalUrl)) {
            showError();
            return;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.tiktok.com/");
        headers.put("Accept-Language", Locale.getDefault().toLanguageTag());
        binding.webView.loadUrl(externalUrl, headers);
    }

    private boolean shouldBlock(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if ("about".equalsIgnoreCase(scheme)) return false;
        if (isAllowedTikTokUri(uri)) return false;
        if ("intent".equalsIgnoreCase(scheme) || "snssdk1233".equalsIgnoreCase(scheme)) {
            openExternal();
        }
        return true;
    }

    private void showError() {
        if (binding == null || isFinishing() || isDestroyed()) return;
        mainHandler.removeCallbacks(loadTimeout);
        binding.webView.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.VISIBLE);
    }

    private void openExternal() {
        if (TextUtils.isEmpty(externalUrl)) return;
        Uri uri = Uri.parse(externalUrl);
        if (!isAllowedTikTokUri(uri)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Throwable ignored) {
        }
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
        if (binding != null) binding.webView.onResume();
    }

    @Override
    protected void onPause() {
        if (binding != null) {
            try {
                binding.webView.evaluateJavascript(
                        "document.querySelectorAll('video').forEach(function(v){v.pause();});", null);
            } catch (Throwable ignored) {
            }
            binding.webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (binding != null) {
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
