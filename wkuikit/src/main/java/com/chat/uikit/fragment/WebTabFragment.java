package com.chat.uikit.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class WebTabFragment extends Fragment {
    private static final String ARG_URL = "url";

    private String url;
    private FrameLayout cachedRoot;
    private WebView webView;
    private ProgressBar progressBar;
    private View loadingView;
    private View errorView;
    private TextView errorTextView;
    private boolean hasLoaded = false;
    private boolean hasVisiblePage = false;
    private String lastVisibleUrl = "";

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
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (cachedRoot != null) {
            detachCachedRoot();
            return cachedRoot;
        }

        cachedRoot = new FrameLayout(requireContext());
        cachedRoot.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cachedRoot.setBackgroundColor(Color.WHITE);

        webView = createWebView(requireContext());
        cachedRoot.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loadingView = createLoadingView(requireContext());
        cachedRoot.addView(loadingView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        errorView = createErrorView(requireContext());
        errorView.setVisibility(View.GONE);
        cachedRoot.addView(errorView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        progressLp.gravity = Gravity.TOP;
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        cachedRoot.addView(progressBar, progressLp);

        boolean restored = false;
        if (savedInstanceState != null) {
            try {
                restored = webView.restoreState(savedInstanceState) != null;
            } catch (Exception ignored) {
                restored = false;
            }
        }

        if (restored) {
            hasLoaded = true;
            hasVisiblePage = true;
            hideLoadingAndError();
        } else if (!hasLoaded && url != null && url.length() > 0) {
            showLoadingIfNoVisiblePage();
            webView.loadUrl(url);
            hasLoaded = true;
        }
        return cachedRoot;
    }

    @Override
    public void onDestroyView() {
        detachCachedRoot();
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            try {
                webView.saveState(outState);
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(Context context) {
        WebView targetWebView = new WebView(context.getApplicationContext());
        targetWebView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        targetWebView.setBackgroundColor(Color.WHITE);
        targetWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        targetWebView.requestFocus(View.FOCUS_DOWN);
        configureWebView(targetWebView);
        return targetWebView;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView targetWebView) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(targetWebView, true);
        }

        WebSettings settings = targetWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        targetWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                return handleUrl(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String pageUrl) {
                return handleUrl(view, pageUrl);
            }

            @Override
            public void onPageStarted(WebView view, String pageUrl, Bitmap favicon) {
                super.onPageStarted(view, pageUrl, favicon);
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(5);
                }
                showLoadingIfNoVisiblePage();
            }

            @Override
            public void onPageCommitVisible(WebView view, String pageUrl) {
                super.onPageCommitVisible(view, pageUrl);
                hasVisiblePage = true;
                lastVisibleUrl = pageUrl == null ? "" : pageUrl;
                hideLoadingAndError();
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                hasVisiblePage = true;
                lastVisibleUrl = pageUrl == null ? "" : pageUrl;
                hideLoadingAndError();
                if (progressBar != null) {
                    progressBar.setProgress(100);
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request != null && request.isForMainFrame()) {
                    String message = error == null ? "页面加载失败" : String.valueOf(error.getDescription());
                    showError(message);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    showError(description == null || description.length() == 0 ? "页面加载失败" : description);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.isForMainFrame()) {
                    int code = errorResponse == null ? 0 : errorResponse.getStatusCode();
                    showError(code > 0 ? "页面加载失败，HTTP " + code : "页面加载失败");
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                recreateWebViewAfterCrash();
                return true;
            }
        });

        targetWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
                if (newProgress >= 80 && hasVisiblePage) {
                    hideLoadingAndError();
                }
            }
        });
    }

    private boolean handleUrl(WebView view, String pageUrl) {
        if (pageUrl == null || pageUrl.length() == 0) return false;
        Uri uri = Uri.parse(pageUrl);
        String scheme = uri.getScheme();
        if (scheme == null || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
        }
        return true;
    }

    private View createLoadingView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(context);
        title.setText("正在加载...");
        title.setTextColor(Color.parseColor("#1F2937"));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < 6; i++) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(makeRoundRect(Color.parseColor("#F5F7FA"), dp(14)));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82));
            cardLp.topMargin = dp(14);
            root.addView(card, cardLp);

            View line1 = new View(context);
            line1.setBackground(makeRoundRect(Color.parseColor("#E6EAF0"), dp(8)));
            LinearLayout.LayoutParams line1Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
            card.addView(line1, line1Lp);

            View line2 = new View(context);
            line2.setBackground(makeRoundRect(Color.parseColor("#EDF1F5"), dp(8)));
            LinearLayout.LayoutParams line2Lp = new LinearLayout.LayoutParams(dp(210), dp(10));
            line2Lp.topMargin = dp(12);
            card.addView(line2, line2Lp);
        }
        return root;
    }

    private View createErrorView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(context);
        title.setText("页面加载失败");
        title.setTextColor(Color.parseColor("#111827"));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        errorTextView = new TextView(context);
        errorTextView.setText("网络不稳定或页面暂时不可用");
        errorTextView.setTextColor(Color.parseColor("#6B7280"));
        errorTextView.setTextSize(14);
        errorTextView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(10);
        root.addView(errorTextView, descLp);

        Button retryButton = new Button(context);
        retryButton.setText("重试");
        retryButton.setAllCaps(false);
        retryButton.setTextColor(Color.WHITE);
        retryButton.setBackground(makeRoundRect(Color.parseColor("#2563EB"), dp(20)));
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retryLoad();
            }
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(dp(132), dp(44));
        buttonLp.topMargin = dp(20);
        root.addView(retryButton, buttonLp);

        return root;
    }

    private void showLoadingIfNoVisiblePage() {
        if (loadingView != null && !hasVisiblePage) {
            loadingView.setVisibility(View.VISIBLE);
        }
        if (errorView != null) {
            errorView.setVisibility(View.GONE);
        }
    }

    private void hideLoadingAndError() {
        if (loadingView != null) {
            loadingView.setVisibility(View.GONE);
        }
        if (errorView != null) {
            errorView.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (hasVisiblePage) {
            if (errorView != null) errorView.setVisibility(View.GONE);
            if (loadingView != null) loadingView.setVisibility(View.GONE);
            return;
        }
        if (loadingView != null) loadingView.setVisibility(View.GONE);
        if (errorTextView != null) errorTextView.setText(message == null || message.length() == 0 ? "网络不稳定或页面暂时不可用" : message);
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
    }

    private void retryLoad() {
        if (webView == null) return;
        showLoadingIfNoVisiblePage();
        String current = webView.getUrl();
        if (current != null && current.length() > 0) {
            webView.reload();
        } else if (lastVisibleUrl != null && lastVisibleUrl.length() > 0) {
            webView.loadUrl(lastVisibleUrl);
        } else if (url != null && url.length() > 0) {
            webView.loadUrl(url);
        }
        hasLoaded = true;
    }

    private void recreateWebViewAfterCrash() {
        if (cachedRoot == null) return;
        String reloadUrl = lastVisibleUrl != null && lastVisibleUrl.length() > 0 ? lastVisibleUrl : url;
        try {
            if (webView != null) {
                cachedRoot.removeView(webView);
                webView.destroy();
            }
        } catch (Exception ignored) {
        }
        webView = createWebView(requireContext());
        cachedRoot.addView(webView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hasVisiblePage = false;
        hasLoaded = false;
        showError("页面进程已重启，请重试");
        if (reloadUrl != null && reloadUrl.length() > 0) {
            url = reloadUrl;
        }
    }

    private void detachCachedRoot() {
        if (cachedRoot == null) return;
        ViewGroup parent = (ViewGroup) cachedRoot.getParent();
        if (parent != null) {
            parent.removeView(cachedRoot);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
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
        errorTextView = null;
        cachedRoot = null;
        super.onDestroy();
    }

    private GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
