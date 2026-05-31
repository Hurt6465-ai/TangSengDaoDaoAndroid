package com.chat.uikit.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.chat.uikit.TabActivity;

public class WebTabFragment extends Fragment {
    private static final String ARG_URL = "url";
    private static final String STATE_HAS_LOADED = "web_tab_has_loaded";
    private static final String STATE_HAS_VISIBLE_CONTENT = "web_tab_has_visible_content";
    private static final String STATE_LAST_URL = "web_tab_last_url";

    private String url;
    private String lastKnownUrl;
    private Bundle savedWebViewState;

    private FrameLayout cachedRoot;
    private WebView webView;
    private ProgressBar progressBar;
    private View loadingView;
    private View errorView;

    private boolean hasLoaded = false;
    private boolean hasVisibleContent = false;

    public static WebTabFragment newInstance(String url) {
        WebTabFragment fragment = new WebTabFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        url = getArguments() == null ? "" : getArguments().getString(ARG_URL, "");
        lastKnownUrl = url;
        if (savedInstanceState != null) {
            hasLoaded = savedInstanceState.getBoolean(STATE_HAS_LOADED, false);
            hasVisibleContent = savedInstanceState.getBoolean(STATE_HAS_VISIBLE_CONTENT, false);
            lastKnownUrl = savedInstanceState.getString(STATE_LAST_URL, url);
            savedWebViewState = savedInstanceState;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (cachedRoot != null) {
            ViewGroup parent = (ViewGroup) cachedRoot.getParent();
            if (parent != null) {
                parent.removeView(cachedRoot);
            }
            syncBottomNavigationWithCurrentUrl();
            return cachedRoot;
        }

        cachedRoot = new FrameLayout(requireContext());
        cachedRoot.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cachedRoot.setBackgroundColor(Color.WHITE);

        webView = new WebView(requireContext());
        webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        configureWebView(webView);

        progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        progressLp.gravity = Gravity.TOP;
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        loadingView = createLoadingView();
        errorView = createErrorView();
        errorView.setVisibility(View.GONE);

        cachedRoot.addView(webView);
        cachedRoot.addView(loadingView);
        cachedRoot.addView(errorView);
        cachedRoot.addView(progressBar, progressLp);

        if (savedInstanceState != null) {
            savedWebViewState = savedInstanceState;
        }
        restoreOrLoadUrl();
        syncBottomNavigationWithCurrentUrl();
        return cachedRoot;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView targetWebView) {
        WebSettings settings = targetWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(targetWebView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        targetWebView.setBackgroundColor(Color.WHITE);
        targetWebView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        targetWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String requestUrl) {
                return handleUrl(requestUrl);
            }

            @Override
            public void onPageStarted(WebView view, String pageUrl, Bitmap favicon) {
                super.onPageStarted(view, pageUrl, favicon);
                lastKnownUrl = pageUrl;
                showLoadingForNavigation();
                showProgress(5);
                updateBottomNavigationVisibility(pageUrl);
            }

            @Override
            public void onPageCommitVisible(WebView view, String pageUrl) {
                super.onPageCommitVisible(view, pageUrl);
                lastKnownUrl = pageUrl;
                hasVisibleContent = true;
                hideLoadingAndError();
                updateBottomNavigationVisibility(pageUrl);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                lastKnownUrl = pageUrl;
                hasVisibleContent = true;
                hideLoadingAndError();
                hideProgress();
                updateBottomNavigationVisibility(pageUrl);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String pageUrl, boolean isReload) {
                super.doUpdateVisitedHistory(view, pageUrl, isReload);
                lastKnownUrl = pageUrl;
                updateBottomNavigationVisibility(pageUrl);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    hideProgress();
                    if (!hasVisibleContent) showErrorView();
                    updateBottomNavigationVisibility(view == null ? lastKnownUrl : view.getUrl());
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (webView != null) {
                    cachedRoot.removeView(webView);
                    webView.destroy();
                    webView = null;
                }
                hasLoaded = false;
                hasVisibleContent = false;
                showErrorView();
                syncBottomNavigationWithCurrentUrl();
                return true;
            }
        });

        targetWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress >= 100) hideProgress(); else showProgress(newProgress);
            }
        });
    }

    private boolean handleUrl(String requestUrl) {
        if (TextUtils.isEmpty(requestUrl)) return false;
        Uri uri = Uri.parse(requestUrl);
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
        return true;
    }

    private void restoreOrLoadUrl() {
        if (webView == null) return;
        if (savedWebViewState != null && hasLoaded) {
            try {
                webView.restoreState(savedWebViewState);
            } catch (Exception ignored) {
            }
            if (hasVisibleContent) hideLoadingAndError(); else showFullLoading();
            return;
        }
        if (!hasLoaded && !TextUtils.isEmpty(url)) {
            showFullLoading();
            webView.loadUrl(url);
            hasLoaded = true;
            lastKnownUrl = url;
        }
    }

    private View createLoadingView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ProgressBar spinner = new ProgressBar(requireContext());
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        spinnerLp.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerLp.bottomMargin = dp(18);
        root.addView(spinner, spinnerLp);

        TextView title = new TextView(requireContext());
        title.setText("正在加载");
        title.setTextColor(Color.rgb(55, 65, 81));
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("请稍候，内容马上回来");
        subtitle.setTextColor(Color.rgb(156, 163, 175));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = dp(6);
        subtitleLp.bottomMargin = dp(22);
        root.addView(subtitle, subtitleLp);

        for (int i = 0; i < 5; i++) {
            View row = new View(requireContext());
            row.setBackgroundColor(i % 2 == 0 ? Color.rgb(241, 245, 249) : Color.rgb(248, 250, 252));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(i == 0 ? 64 : 48));
            rowLp.topMargin = dp(10);
            root.addView(row, rowLp);
        }
        return root;
    }

    private View createErrorView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(requireContext());
        title.setText("页面加载失败");
        title.setTextColor(Color.rgb(31, 41, 55));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(requireContext());
        desc.setText("网络不稳定或页面暂时无法访问");
        desc.setTextColor(Color.rgb(107, 114, 128));
        desc.setTextSize(14);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(8);
        descLp.bottomMargin = dp(18);
        root.addView(desc, descLp);

        Button retry = new Button(requireContext());
        retry.setText("重新加载");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> reloadCurrentPage());
        root.addView(retry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        return root;
    }

    private void reloadCurrentPage() {
        if (webView == null) {
            recreateWebViewAndLoad();
            return;
        }
        showFullLoading();
        String targetUrl = !TextUtils.isEmpty(lastKnownUrl) ? lastKnownUrl : url;
        if (!TextUtils.isEmpty(targetUrl)) {
            webView.loadUrl(targetUrl);
            hasLoaded = true;
        } else {
            webView.reload();
        }
    }

    private void recreateWebViewAndLoad() {
        if (cachedRoot == null || getContext() == null) return;
        webView = new WebView(requireContext());
        webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        configureWebView(webView);
        cachedRoot.addView(webView, 0);
        hasLoaded = false;
        hasVisibleContent = false;
        restoreOrLoadUrl();
    }

    private void showFullLoading() {
        if (loadingView != null && !hasVisibleContent) loadingView.setVisibility(View.VISIBLE);
        if (errorView != null) errorView.setVisibility(View.GONE);
    }

    private void showLoadingForNavigation() {
        if (!hasVisibleContent && loadingView != null) loadingView.setVisibility(View.VISIBLE);
        if (errorView != null) errorView.setVisibility(View.GONE);
    }

    private void hideLoadingAndError() {
        if (loadingView != null) loadingView.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.GONE);
    }

    private void showErrorView() {
        if (loadingView != null) loadingView.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
    }

    private void showProgress(int progress) {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(Math.max(3, progress));
        }
    }

    private void hideProgress() {
        if (progressBar != null) {
            progressBar.setProgress(100);
            progressBar.setVisibility(View.GONE);
        }
    }

    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public void goBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            syncBottomNavigationWithCurrentUrl();
        }
    }

    public void syncBottomNavigationWithCurrentUrl() {
        updateBottomNavigationVisibility(getCurrentPageUrl());
    }

    private String getCurrentPageUrl() {
        if (webView != null && !TextUtils.isEmpty(webView.getUrl())) return webView.getUrl();
        if (!TextUtils.isEmpty(lastKnownUrl)) return lastKnownUrl;
        return url;
    }

    private void updateBottomNavigationVisibility(String currentUrl) {
        Activity activity = getActivity();
        if (activity instanceof TabActivity) {
            ((TabActivity) activity).setBottomNavigationVisible(isRootPage(currentUrl));
        }
    }

    private boolean isRootPage(String currentUrl) {
        if (TextUtils.isEmpty(currentUrl) || TextUtils.isEmpty(url)) return true;
        try {
            Uri current = Uri.parse(currentUrl);
            Uri root = Uri.parse(url);
            if (!TextUtils.equals(current.getHost(), root.getHost())) return false;
            if (!TextUtils.equals(normalizePath(current.getPath()), normalizePath(root.getPath()))) return false;
            String fragment = current.getFragment();
            return TextUtils.isEmpty(fragment) || "/".equals(fragment);
        } catch (Exception e) {
            return true;
        }
    }

    private String normalizePath(String path) {
        if (TextUtils.isEmpty(path)) return "/";
        if (path.length() > 1 && path.endsWith("/")) return path.substring(0, path.length() - 1);
        return path;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        syncBottomNavigationWithCurrentUrl();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HAS_LOADED, hasLoaded);
        outState.putBoolean(STATE_HAS_VISIBLE_CONTENT, hasVisibleContent);
        outState.putString(STATE_LAST_URL, getCurrentPageUrl());
        if (webView != null) {
            try {
                webView.saveState(outState);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (cachedRoot != null) {
            ViewGroup parent = (ViewGroup) cachedRoot.getParent();
            if (parent != null) parent.removeView(cachedRoot);
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
        progressBar = null;
        loadingView = null;
        errorView = null;
        cachedRoot = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
