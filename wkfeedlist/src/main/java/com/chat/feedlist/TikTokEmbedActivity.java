package com.chat.feedlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.chat.feedlist.databinding.ActivityTiktokEmbedBinding;

public class TikTokEmbedActivity extends Activity {
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("(?:/video/|/v/)([0-9]{8,32})", Pattern.CASE_INSENSITIVE);
    private static final String EXTRA_ID = "video_id";
    private static final String EXTRA_URL = "external_url";
    private ActivityTiktokEmbedBinding binding;
    private String videoId;
    private String externalUrl;

    public static void open(Context context, String videoId, String externalUrl) {
        if (context == null) return;
        String resolvedId = resolveVideoId(videoId, externalUrl);
        if (TextUtils.isEmpty(resolvedId) && !isAllowedTikTokUri(Uri.parse(String.valueOf(externalUrl)))) return;
        Intent intent = new Intent(context, TikTokEmbedActivity.class);
        intent.putExtra(EXTRA_ID, resolvedId);
        intent.putExtra(EXTRA_URL, externalUrl);
        context.startActivity(intent);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTiktokEmbedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        videoId = resolveVideoId(getIntent().getStringExtra(EXTRA_ID), getIntent().getStringExtra(EXTRA_URL));
        externalUrl = getIntent().getStringExtra(EXTRA_URL);
        if (TextUtils.isEmpty(videoId) && !isAllowedTikTokUri(Uri.parse(String.valueOf(externalUrl)))) {
            finish();
            return;
        }
        binding.backBtn.setOnClickListener(v -> finish());
        binding.openExternalBtn.setOnClickListener(v -> openExternal());
        WebSettings settings = binding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return TextUtils.isEmpty(url) || !isAllowedTikTokUri(Uri.parse(url));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request == null || !isAllowedTikTokUri(request.getUrl());
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) showError();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null && response.getStatusCode() >= 400) showError();
            }
        });
        if (!TextUtils.isEmpty(videoId)) {
            binding.webView.loadUrl("https://www.tiktok.com/player/v1/" + videoId + "?autoplay=1&controls=1");
        } else {
            // Last-resort compatibility for old/short-link rows whose video_id was never stored.
            binding.webView.loadUrl(externalUrl);
        }
    }

    private void showError() {
        if (binding == null || isFinishing() || isDestroyed()) return;
        binding.webView.setVisibility(View.GONE);
        binding.errorPanel.setVisibility(View.VISIBLE);
    }

    private void openExternal() {
        if (TextUtils.isEmpty(externalUrl)) return;
        Uri uri = Uri.parse(externalUrl);
        if (!isAllowedTikTokUri(uri)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Throwable ignored) {}
    }

    private static String resolveVideoId(String videoId, String externalUrl) {
        String value = videoId == null ? "" : videoId.trim();
        if (value.matches("[0-9]{8,32}")) return value;
        String url = externalUrl == null ? "" : externalUrl.trim();
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static boolean isAllowedTikTokUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        return host.equals("tiktok.com") || host.endsWith(".tiktok.com");
    }

    @Override protected void onResume() {
        super.onResume();
        if (binding != null) binding.webView.onResume();
    }

    @Override protected void onPause() {
        if (binding != null) {
            try {
                binding.webView.evaluateJavascript(
                        "document.querySelectorAll('video').forEach(function(v){v.pause();});", null);
            } catch (Throwable ignored) {}
            binding.webView.onPause();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
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
