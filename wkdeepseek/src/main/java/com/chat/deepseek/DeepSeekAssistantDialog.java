package com.chat.deepseek;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;


public class DeepSeekAssistantDialog extends DialogFragment {
    private static final String URL = "https://chat.deepseek.com/";
    private static final String NEW_CHAT_URL = "https://chat.deepseek.com/a/chat/";
    private static final String ARG_LOGIN = "login";
    private static final String ARG_ACTION = "action";
    private static final String ARG_CHANNEL_ID = "channel_id";
    private static final String ARG_CHANNEL_TYPE = "channel_type";
    private static final String ARG_SELF_UID = "self_uid";
    private static final String ARG_MY_NATIVE = "my_native";
    private static final String ARG_PEER_NATIVE = "peer_native";
    private static final String ARG_MY_LEARNING = "my_learning";
    private static final String ARG_PEER_LEARNING = "peer_learning";
    private static final String ARG_DRAFT = "draft";
    private static final String ARG_BACKGROUND = "background";
    private static final String ARG_PURPOSE = "purpose";
    private static final String ARG_RELATIONSHIP_STAGE = "relationship_stage";
    private static final String ARG_PREFERRED_STYLE = "preferred_style";
    private static final String ARG_FLIRT_LEVEL = "flirt_level";
    private static final String ARG_CONTEXT_ENABLED = "context_enabled";
    private static final String ARG_CONTEXT_LIMIT = "context_limit";
    private static final String ARG_TARGET_MESSAGE_ID = "target_message_id";
    private static final String ARG_TARGET_MESSAGE_TEXT = "target_message_text";
    private static final String ARG_CONTEXT_SNAPSHOT = "context_snapshot";
    private static final String ARG_CONTEXT_SNAPSHOT_COUNT = "context_snapshot_count";
    private static final String ARG_CONTACT_PROFILE = "contact_profile";
    /**
     * DeepSeek 当前发送按钮使用的上箭头 SVG path。自动提交只在检测到这个
     * 可见、可点击的按钮后执行，避免误点附件、语音、停止生成等按钮。
     */
    private static final String DEEPSEEK_SEND_ICON_PATH =
            "M8.3125 0.981587C8.66767 1.0545 8.97902 1.20558 9.2627 1.43374C9.48724 1.61438 9.73029 1.85933 9.97949 2.10854L14.707 6.83608L13.293 8.25014L9 3.95717V15.0431H7V3.95717L2.70703 8.25014L1.29297 6.83608L6.02051 2.10854C6.26971 1.85933 6.51277 1.61438 6.7373 1.43374C6.97662 1.24126 7.28445 1.04542 7.6875 0.981587C7.8973 0.94841 8.1031 0.956564 8.3125 0.981587Z";

    private WebView webView;
    private TextView statusView;
    private ProgressBar progressBar;
    private boolean loginMode;
    private boolean promptPrepared;
    private boolean promptFilled;
    private boolean promptSubmitted;
    private int fillAttempts;
    private int submitAttempts;
    private int submitVerifyAttempts;
    private boolean submitReadyPending;
    private boolean submitClickPending;
    private boolean fallbackCopied;
    private String pendingPrompt = "";
    private String promptTransportToken = "";
    private String promptVisibleText = "";
    private String promptVisibleLabel = "";
    private boolean promptTransportPrepared;
    private boolean promptTransportInFlight;
    private boolean promptTransportEnabled;
    private DeepSeekRequest request;
    private DeepSeekAssistant.ReplyCallback replyCallback;
    private DeepSeekAssistant.TranslationCallback translationCallback;
    private DeepSeekAssistant.StateCallback stateCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random timingRandom = new Random();
    private boolean loginConfirmed;
    private boolean loginProbeInFlight;
    private int loginWaitAttempts;
    private boolean loginReminderShown;
    private final Runnable loginProbeRunnable = this::runLoginProbe;
    private FrameLayout dialogRoot;
    private LinearLayout contentPanel;
    private int normalPanelHeight;
    private boolean closing;
    private String dismissReason = "unknown";
    private int lastLoggedProgressBucket = -1;
    private boolean pendingReplyDelivery;
    private String pendingReplyText = "";
    private String pendingReplyLocalDisplayText = "";
    private boolean pendingReplySendNow;
    private boolean pendingTranslationDelivery;
    private String pendingTranslationText = "";
    private boolean webUiPrepared;
    private boolean webUiPrepareInFlight;
    private int webUiPrepareAttempts;
    private final Runnable webUiPrepareRunnable = () -> applyWebUiPreferences(false);
    private String mappedConversationId = "";
    private String conversationRouteTarget = "";
    private boolean conversationRouteLoading;
    private int conversationRouteCorrections;
    private String fullContextSnapshot = "";
    private int fullContextSnapshotCount;
    private boolean contextPlanApplied;
    private boolean forceFullContextForFreshConversation;
    private int promptBuildGeneration;

    private interface LoginStateCallback {
        void onResult(boolean loggedIn);
    }

    public static DeepSeekAssistantDialog newLogin() {
        DeepSeekAssistantDialog dialog = new DeepSeekAssistantDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_LOGIN, true);
        dialog.setArguments(args);
        return dialog;
    }

    public static DeepSeekAssistantDialog newAction(DeepSeekRequest request) {
        DeepSeekAssistantDialog dialog = new DeepSeekAssistantDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_LOGIN, false);
        args.putInt(ARG_ACTION, request.action);
        args.putString(ARG_CHANNEL_ID, request.channelId);
        args.putByte(ARG_CHANNEL_TYPE, request.channelType);
        args.putString(ARG_SELF_UID, request.selfUid);
        args.putString(ARG_MY_NATIVE, request.myNativeLanguage);
        args.putString(ARG_PEER_NATIVE, request.peerNativeLanguage);
        args.putString(ARG_MY_LEARNING, request.myLearningLanguages);
        args.putString(ARG_PEER_LEARNING, request.peerLearningLanguages);
        args.putString(ARG_DRAFT, request.draft);
        args.putString(ARG_BACKGROUND, request.background);
        args.putString(ARG_PURPOSE, request.purpose);
        args.putString(ARG_RELATIONSHIP_STAGE, request.relationshipStage);
        args.putString(ARG_PREFERRED_STYLE, request.preferredStyle);
        args.putInt(ARG_FLIRT_LEVEL, request.flirtLevel);
        args.putBoolean(ARG_CONTEXT_ENABLED, request.contextEnabled);
        args.putInt(ARG_CONTEXT_LIMIT, request.contextLimit);
        args.putString(ARG_TARGET_MESSAGE_ID, request.targetMessageId);
        args.putString(ARG_TARGET_MESSAGE_TEXT, request.targetMessageText);
        args.putString(ARG_CONTEXT_SNAPSHOT, request.contextSnapshot);
        args.putInt(ARG_CONTEXT_SNAPSHOT_COUNT, request.contextSnapshotCount);
        args.putString(ARG_CONTACT_PROFILE, request.contactProfile == null ? "{}" : request.contactProfile.toJson());
        dialog.setArguments(args);
        return dialog;
    }

    void setReplyCallback(DeepSeekAssistant.ReplyCallback callback) {
        this.replyCallback = callback;
    }

    void setTranslationCallback(DeepSeekAssistant.TranslationCallback callback) {
        this.translationCallback = callback;
    }

    void setStateCallback(DeepSeekAssistant.StateCallback callback) {
        this.stateCallback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() == null ? Bundle.EMPTY : getArguments();
        loginMode = args.getBoolean(ARG_LOGIN, false);
        if (!loginMode) {
            request = requestFrom(args);
            fullContextSnapshot = request.contextSnapshot;
            fullContextSnapshotCount = request.contextSnapshotCount;
        }
        DeepSeekHistoryLog.log("DIALOG_ON_CREATE_VIEW", "loginMode=" + loginMode
                + " action=" + (request == null ? -1 : request.action)
                + " saved=" + (savedInstanceState != null));
        return buildContent(requireContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Bundle args = getArguments() == null ? Bundle.EMPTY : getArguments();
        dialog.setCanceledOnTouchOutside(!args.getBoolean(ARG_LOGIN, false));
        DeepSeekHistoryLog.log("DIALOG_ON_CREATE_DIALOG", "loginMode="
                + args.getBoolean(ARG_LOGIN, false));
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;

        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setGravity(loginMode ? Gravity.CENTER : Gravity.BOTTOM);

        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = ViewGroup.LayoutParams.MATCH_PARENT;
        attributes.height = ViewGroup.LayoutParams.MATCH_PARENT;
        attributes.gravity = loginMode ? Gravity.CENTER : Gravity.BOTTOM;
        attributes.dimAmount = 0f;
        window.setAttributes(attributes);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(Color.WHITE);
        }
        DeepSeekHistoryLog.log("DIALOG_ON_START", "loginMode=" + loginMode
                + " window=" + attributes.width + "x" + attributes.height
                + " flags=0x" + Integer.toHexString(attributes.flags)
                + " softInput=0x" + Integer.toHexString(attributes.softInputMode)
                + " panelTarget=" + normalPanelHeight);
        if (dialogRoot != null) {
            dialogRoot.post(() -> DeepSeekHistoryLog.log("DIALOG_LAYOUT_READY",
                    viewState(dialogRoot, "root") + " "
                            + viewState(contentPanel, "panel") + " "
                            + viewState(webView, "web")));
        }
    }

    private View buildContent(Context context) {
        dialogRoot = new FrameLayout(context);
        dialogRoot.setBackgroundColor(Color.TRANSPARENT);
        dialogRoot.setClickable(true);
        dialogRoot.setFocusable(true);
        if (!loginMode) {
            dialogRoot.setOnClickListener(v -> dismissAssistant("outside_tap"));
        }

        contentPanel = new LinearLayout(context);
        contentPanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.WHITE);
        if (!loginMode) {
            float radius = dp(18);
            panelBackground.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        }
        contentPanel.setBackground(panelBackground);
        contentPanel.setClickable(true);
        contentPanel.setOnClickListener(v -> {
        });
        contentPanel.setClipToOutline(false);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        normalPanelHeight = loginMode ? screenHeight : Math.max(dp(360), Math.round(screenHeight * 0.60f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                loginMode ? ViewGroup.LayoutParams.MATCH_PARENT : normalPanelHeight,
                Gravity.BOTTOM);
        dialogRoot.addView(contentPanel, panelParams);

        statusView = new TextView(context);
        statusView.setText(loginMode ? R.string.wkdeepseek_need_login : R.string.wkdeepseek_connecting);
        statusView.setTextColor(Color.rgb(36, 42, 52));
        statusView.setTextSize(15);
        statusView.setSingleLine(false);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);

        if (loginMode) {
            LinearLayout toolbar = new LinearLayout(context);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setPadding(dp(16), dp(8), dp(8), dp(8));
            toolbar.addView(statusView, new LinearLayout.LayoutParams(0, dp(46), 1f));

            TextView close = toolbarButton(context, getString(R.string.wkdeepseek_done));
            close.setOnClickListener(v -> {
                if (!loginConfirmed) {
                    verifyLoginBeforeClose();
                } else {
                    dismissAssistant();
                }
            });
            toolbar.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
            contentPanel.addView(toolbar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            contentPanel.addView(progressBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        } else {
            statusView.setVisibility(View.GONE);
            statusView.setPadding(dp(16), dp(10), dp(16), dp(10));
            contentPanel.addView(statusView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            progressBar.setVisibility(View.VISIBLE);
            contentPanel.addView(progressBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        }

        webView = new WebView(context);
        webView.setBackgroundColor(Color.WHITE);
        webView.setAlpha(1f);
        webView.setVisibility(View.VISIBLE);
        webView.setNestedScrollingEnabled(true);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.setVerticalScrollBarEnabled(true);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                Dialog dialog = getDialog();
                Window window = dialog == null ? null : dialog.getWindow();

                if (window != null) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    window.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
                }

                v.setFocusable(true);
                v.setFocusableInTouchMode(true);
                v.requestFocusFromTouch();
            }
            return false;
        });
        configureWebView(webView);
        contentPanel.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!isOnline(context)) {
            statusView.setText(R.string.wkdeepseek_no_network);
            progressBar.setVisibility(View.GONE);
            notifyUser(getString(R.string.wkdeepseek_no_network));
        } else {
            String startUrl = URL;
            if (!loginMode) {
                mappedConversationId = DeepSeekConversationStore.getConversationId(context, request);
                startUrl = TextUtils.isEmpty(mappedConversationId)
                        ? NEW_CHAT_URL
                        : DeepSeekConversationStore.conversationUrl(mappedConversationId);
                conversationRouteTarget = startUrl;
                conversationRouteLoading = true;
                DeepSeekHistoryLog.log("CONVERSATION_ROUTE_START",
                        "mapped=" + !TextUtils.isEmpty(mappedConversationId)
                                + " channel=" + safeChannelForLog(request)
                                + " url=" + safeUrl(startUrl));
            }
            webView.loadUrl(startUrl);
            if (!loginMode) preparePrompt();
        }
        return dialogRoot;
    }

    private TextView toolbarButton(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.rgb(45, 94, 230));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(242, 246, 255));
        bg.setCornerRadius(dp(18));
        view.setBackground(bg);
        view.setPadding(dp(14), 0, dp(14), 0);
        return view;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(view, true);

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                int bucket = Math.min(4, Math.max(0, newProgress / 25));
                if (bucket != lastLoggedProgressBucket) {
                    lastLoggedProgressBucket = bucket;
                    DeepSeekHistoryLog.log("WEB_PROGRESS", "progress=" + newProgress
                            + " url=" + safeUrl(view == null ? null : view.getUrl())
                            + " " + viewState(view, "web"));
                }
                if (progressBar != null) {
                    progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
                if (view != null) {
                    view.setAlpha(1f);
                    view.setVisibility(View.VISIBLE);
                }
                if (loginMode && newProgress >= 80) {
                    scheduleLoginProbe(250);
                } else if (!loginMode && newProgress >= 70 && !promptFilled) {
                    scheduleWebUiPreparation(180);
                }
            }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest webRequest) {
                Uri uri = webRequest.getUrl();
                if ("tsdd-deepseek".equals(uri.getScheme())) {
                    handlePluginUrl(uri);
                    return true;
                }
                if (isDeepSeek(uri)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                conversationRouteLoading = true;
                DeepSeekHistoryLog.log("WEB_PAGE_STARTED", "url=" + safeUrl(url)
                        + " " + viewState(view, "web"));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.setAlpha(1f);
                view.setVisibility(View.VISIBLE);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (!loginMode && statusView != null) statusView.setVisibility(View.GONE);
                conversationRouteLoading = false;
                handleConversationNavigation(url);
                DeepSeekHistoryLog.log("WEB_PAGE_FINISHED", "url=" + safeUrl(url)
                        + " promptFilled=" + promptFilled
                        + " promptSubmitted=" + promptSubmitted
                        + " " + viewState(view, "web"));
                if (loginMode) {
                    scheduleLoginProbe(150);
                } else {
                    webUiPrepared = false;
                    webUiPrepareAttempts = 0;
                    installReplyButtons();
                    scheduleWebUiPreparation(120);
                    tryFillPrompt();
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                DeepSeekHistoryLog.log("WEB_HISTORY", "reload=" + isReload
                        + " url=" + safeUrl(url));
                handleConversationNavigation(url);
                if (loginMode) {
                    scheduleLoginProbe(120);
                } else {
                    if (!promptFilled) {
                        webUiPrepared = false;
                        webUiPrepareAttempts = 0;
                        scheduleWebUiPreparation(120);
                    }
                    tryFillPrompt();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                DeepSeekHistoryLog.log("WEB_SSL_ERROR", "url="
                        + safeUrl(error == null ? null : error.getUrl())
                        + " primary=" + (error == null ? -1 : error.getPrimaryError()));
                statusView.setText("DeepSeek 安全连接失败");
                statusView.setVisibility(View.VISIBLE);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                notifyUser("DeepSeek 安全连接失败");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    DeepSeekHistoryLog.log("WEB_MAIN_FRAME_ERROR", "code="
                            + (error == null ? -1 : error.getErrorCode())
                            + " desc=" + (error == null ? "" : String.valueOf(error.getDescription()))
                            + " url=" + safeUrl(request.getUrl() == null ? null : request.getUrl().toString()));
                    statusView.setText("DeepSeek 页面加载失败，请检查网络后重试");
                    statusView.setVisibility(View.VISIBLE);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    notifyUser("DeepSeek 页面加载失败，请检查网络后重试");
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                DeepSeekHistoryLog.log("WEB_RENDER_GONE", "didCrash="
                        + (detail != null && detail.didCrash())
                        + " priority=" + (detail == null ? -1 : detail.rendererPriorityAtExit()));
                statusView.setText("DeepSeek 页面已被系统回收，请重新打开");
                notifyUser("DeepSeek 页面已被系统回收，请重新打开");
                if (webView == view) webView = null;
                view.destroy();
                dismissAssistant();
                return true;
            }
        });
    }

    private void scheduleLoginProbe(long delayMs) {
        if (!loginMode || loginConfirmed || webView == null) return;
        handler.removeCallbacks(loginProbeRunnable);
        handler.postDelayed(loginProbeRunnable, Math.max(0L, delayMs));
    }

    private void runLoginProbe() {
        if (!loginMode || loginConfirmed || webView == null || !isAdded()) return;
        probeLoginState(loggedIn -> {
            if (!isAdded() || webView == null || loginConfirmed) return;
            if (loggedIn) {
                confirmLogin();
            } else {
                scheduleLoginProbe(900);
            }
        });
    }

    private void verifyLoginBeforeClose() {
        if (webView == null || !isAdded()) return;
        statusView.setText("正在确认 DeepSeek 登录状态…");
        probeLoginState(loggedIn -> {
            if (!isAdded()) return;
            if (loggedIn) {
                confirmLogin();
                dismissAssistant();
            } else {
                statusView.setText("尚未检测到登录，请先完成登录或注册");
                Toast.makeText(requireContext(), "登录成功后再点击完成", Toast.LENGTH_SHORT).show();
                scheduleLoginProbe(700);
            }
        });
    }

    private void confirmLogin() {
        if (loginConfirmed || !isAdded()) return;
        loginConfirmed = true;
        handler.removeCallbacks(loginProbeRunnable);
        CookieManager.getInstance().flush();
        DeepSeekAssistant.markConnected(requireContext());
        statusView.setText("DeepSeek 已登录，社交助手已开启");
        if (stateCallback != null) stateCallback.onChanged();
    }

    private void probeLoginState(LoginStateCallback callback) {
        if (callback == null) return;
        if (webView == null || !isAdded()) {
            callback.onResult(false);
            return;
        }
        String currentUrl = webView.getUrl();
        if (looksLoggedInByUrl(currentUrl)) {
            callback.onResult(true);
            return;
        }
        if (looksLikeLoginUrl(currentUrl)) {
            callback.onResult(false);
            return;
        }
        if (loginProbeInFlight) {
            handler.postDelayed(() -> probeLoginState(callback), 120);
            return;
        }
        loginProbeInFlight = true;
        try {
            webView.evaluateJavascript(buildLoginProbeScript(), value -> {
                loginProbeInFlight = false;
                boolean loggedIn = "\"logged\"".equals(value) || "logged".equals(value);
                callback.onResult(loggedIn);
            });
        } catch (Exception ignored) {
            loginProbeInFlight = false;
            callback.onResult(false);
        }
    }

    private String buildLoginProbeScript() {
        return "(function(){try{" +
                "var path=(location.pathname||'').toLowerCase();" +
                "if(/(^|\\/)(login|signin|sign-in|sign_in|register|signup|sign-up|sign_up)(\\/|$)/.test(path))return 'login';" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el);var r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0;}" +
                "var password=Array.from(document.querySelectorAll('input[type=\"password\"]')).some(visible);if(password)return 'login';" +
                "var boxes=Array.from(document.querySelectorAll('textarea,[contenteditable=\"true\"],[role=\"textbox\"]'));" +
                "var composer=boxes.some(function(el){if(!visible(el)||el.disabled||el.getAttribute('aria-disabled')==='true')return false;" +
                "var tag=(el.tagName||'').toUpperCase();if(tag==='INPUT')return false;" +
                "return tag==='TEXTAREA'||el.getAttribute('contenteditable')==='true'||el.getAttribute('role')==='textbox';});" +
                "if(path.indexOf('/a/chat')===0||composer)return 'logged';return 'unknown';" +
                "}catch(e){return 'unknown';}})();";
    }

    private void preparePrompt() {
        if (promptPrepared || request == null) return;
        promptPrepared = true;
        statusView.setText(R.string.wkdeepseek_reading);
        Context appContext = requireContext().getApplicationContext();
        DeepSeekLanguageResolver.resolve(appContext, request, () ->
                handler.post(this::loadMessagesAndBuildPrompt));
    }

    private void loadMessagesAndBuildPrompt() {
        if (!isAdded() || request == null) return;
        applyContextPlanIfNeeded();
        final int generation = ++promptBuildGeneration;
        DeepSeekMessageLoader.load(request, result -> handler.post(() -> {
            if (!isAdded() || generation != promptBuildGeneration) return;
            if (result.messageCount == 0 && request.action != DeepSeekRequest.ACTION_POLISH) {
                statusView.setText(R.string.wkdeepseek_no_messages);
                notifyUser(getString(R.string.wkdeepseek_no_messages));
                return;
            }
            if (request.action == DeepSeekRequest.ACTION_TRANSLATE && TextUtils.isEmpty(result.targetMessage)) {
                statusView.setText("没有找到对方可翻译的消息");
                notifyUser("没有找到对方可翻译的消息");
                return;
            }
            if (request.action == DeepSeekRequest.ACTION_POLISH && TextUtils.isEmpty(request.draft)) {
                statusView.setText("请先在聊天输入框写好需要润色的内容");
                notifyUser("请先在聊天输入框写好需要润色的内容");
                return;
            }
            try {
                pendingPrompt = DeepSeekPromptBuilder.build(requireContext(), request, result);
                preparePromptTransportText();
                webUiPrepared = false;
                webUiPrepareAttempts = 0;
                tryFillPrompt();
            } catch (Exception e) {
                statusView.setText("生成提示词失败");
                notifyUser("生成提示词失败");
            }
        }));
    }

    private void applyContextPlanIfNeeded() {
        if (contextPlanApplied || request == null || !isAdded()) return;
        Context context = getContext();
        boolean hasMappedConversation = !TextUtils.isEmpty(mappedConversationId);
        DeepSeekConversationStore.ContextPlan plan = DeepSeekConversationStore.planContext(
                context, request, hasMappedConversation, forceFullContextForFreshConversation);
        request.contextSnapshot = plan.snapshot;
        request.contextSnapshotCount = plan.count;
        request.contextSyncMode = plan.mode;
        contextPlanApplied = true;
        forceFullContextForFreshConversation = false;
        DeepSeekHistoryLog.log("CONTEXT_REUSE_PLAN", "mode=" + plan.mode
                + " mapped=" + hasMappedConversation
                + " full_count=" + fullContextSnapshotCount
                + " send_count=" + plan.count
                + " send_chars=" + plan.snapshot.length());
    }

    private void resetMappingAndPromptForFreshConversation(String reason) {
        Context context = getContext();
        if (context != null && request != null) {
            DeepSeekConversationStore.clear(context, request);
        }
        mappedConversationId = "";
        forceFullContextForFreshConversation = true;
        contextPlanApplied = false;
        if (request != null) {
            request.contextSnapshot = fullContextSnapshot;
            request.contextSnapshotCount = fullContextSnapshotCount;
            request.contextSyncMode = "full";
        }
        promptBuildGeneration++;
        pendingPrompt = "";
        promptFilled = false;
        promptSubmitted = false;
        promptTransportToken = "";
        promptVisibleText = "";
        promptVisibleLabel = "";
        promptTransportPrepared = false;
        promptTransportInFlight = false;
        promptTransportEnabled = false;
        fillAttempts = 0;
        submitAttempts = 0;
        submitVerifyAttempts = 0;
        submitReadyPending = false;
        submitClickPending = false;
        fallbackCopied = false;
        webUiPrepared = false;
        webUiPrepareInFlight = false;
        webUiPrepareAttempts = 0;
        DeepSeekHistoryLog.log("CONVERSATION_MAPPING_CLEARED", "reason=" + reason
                + " restore_full_count=" + fullContextSnapshotCount);
        loadMessagesAndBuildPrompt();
    }

    private void markContextSnapshotSubmitted() {
        Context context = getContext();
        if (context == null || request == null || TextUtils.isEmpty(fullContextSnapshot)) return;
        DeepSeekConversationStore.markContextSubmitted(
                context, request, fullContextSnapshot);
    }

    private void tryFillPrompt() {
        if (promptFilled || webView == null || TextUtils.isEmpty(pendingPrompt) || !isAdded()) return;
        probeLoginState(loggedIn -> {
            if (!isAdded() || webView == null || promptFilled) return;
            if (!loggedIn) {
                loginWaitAttempts++;
                if (loginWaitAttempts < 12) {
                    statusView.setText(R.string.wkdeepseek_connecting);
                    handler.postDelayed(this::tryFillPrompt, 800);
                } else {
                    statusView.setText(R.string.wkdeepseek_need_login);
                    if (!loginReminderShown) {
                        loginReminderShown = true;
                        notifyUser("DeepSeek 登录已过期，请完成登录后重试");
                    }
                    handler.postDelayed(this::tryFillPrompt, 1400);
                }
                return;
            }
            if (!ensureConversationRouteReady()) return;
            boolean resumedAfterLogin = loginWaitAttempts > 0;
            loginWaitAttempts = 0;
            loginReminderShown = false;
            if (resumedAfterLogin) {
                webUiPrepared = false;
                webUiPrepareAttempts = 0;
            }
            if (!webUiPrepared) {
                applyWebUiPreferences(true);
            } else {
                fillPromptNow();
            }
        });
    }

    private boolean ensureConversationRouteReady() {
        if (loginMode || webView == null || request == null) return true;
        String currentUrl = webView.getUrl();
        if (TextUtils.isEmpty(currentUrl)) {
            handler.postDelayed(this::tryFillPrompt, 220);
            return false;
        }
        if (looksLikeLoginUrl(currentUrl)) return true;

        String currentId = DeepSeekConversationStore.extractConversationId(currentUrl);
        if (!TextUtils.isEmpty(mappedConversationId)) {
            if (TextUtils.equals(mappedConversationId, currentId)) {
                conversationRouteCorrections = 0;
                return true;
            }
            if (conversationRouteCorrections >= 3) {
                DeepSeekHistoryLog.log("CONVERSATION_ROUTE_RESET",
                        "reason=mapped_route_unavailable expected="
                                + shortConversationId(mappedConversationId)
                                + " actual=" + shortConversationId(currentId));
                resetMappingAndPromptForFreshConversation("mapped_route_unavailable");
                conversationRouteCorrections = 0;
                loadConversationRoute(NEW_CHAT_URL, "mapped_route_unavailable");
                return false;
            }
            loadConversationRoute(DeepSeekConversationStore.conversationUrl(mappedConversationId),
                    "restore_mapped");
            return false;
        }

        if (!TextUtils.isEmpty(currentId) && !promptSubmitted) {
            loadConversationRoute(NEW_CHAT_URL, "avoid_last_web_conversation");
            return false;
        }
        if (DeepSeekConversationStore.isNewChatUrl(currentUrl)) {
            conversationRouteCorrections = 0;
            return true;
        }
        if (!promptSubmitted) {
            loadConversationRoute(NEW_CHAT_URL, "open_new_chat");
            return false;
        }
        return true;
    }

    private void loadConversationRoute(String url, String reason) {
        if (webView == null || TextUtils.isEmpty(url) || !isAdded()) return;
        if (conversationRouteLoading && TextUtils.equals(conversationRouteTarget, url)) {
            handler.postDelayed(this::tryFillPrompt, 260);
            return;
        }
        conversationRouteCorrections++;
        conversationRouteTarget = url;
        conversationRouteLoading = true;
        webUiPrepared = false;
        webUiPrepareInFlight = false;
        webUiPrepareAttempts = 0;
        fillAttempts = 0;
        if (!promptSubmitted) {
            promptTransportPrepared = false;
            promptTransportInFlight = false;
            promptTransportEnabled = false;
        }
        DeepSeekHistoryLog.log("CONVERSATION_ROUTE_LOAD", "reason=" + reason
                + " corrections=" + conversationRouteCorrections
                + " url=" + safeUrl(url));
        webView.loadUrl(url);
    }

    private void handleConversationNavigation(String url) {
        if (loginMode || request == null || TextUtils.isEmpty(url)) return;
        String conversationId = DeepSeekConversationStore.extractConversationId(url);
        if (TextUtils.isEmpty(conversationId)) return;

        if (!TextUtils.isEmpty(mappedConversationId)
                && !TextUtils.equals(mappedConversationId, conversationId)
                && !promptSubmitted) {
            handler.post(() -> loadConversationRoute(
                    DeepSeekConversationStore.conversationUrl(mappedConversationId),
                    "redirected_to_other_conversation"));
            return;
        }

        if (promptSubmitted) {
            saveActiveConversation(conversationId, "navigation_after_submit");
        } else if (TextUtils.equals(mappedConversationId, conversationId)) {
            DeepSeekConversationStore.save(requireContext(), request, conversationId);
        }
    }

    private void captureConversationFromCurrentUrl() {
        if (loginMode || !promptSubmitted || request == null || webView == null || !isAdded()) return;
        String conversationId = DeepSeekConversationStore.extractConversationId(webView.getUrl());
        if (TextUtils.isEmpty(conversationId)) return;
        saveActiveConversation(conversationId, "capture_after_submit");
    }

    private void saveActiveConversation(String conversationId, String reason) {
        if (TextUtils.isEmpty(conversationId) || request == null) return;
        if (!TextUtils.isEmpty(mappedConversationId)
                && !TextUtils.equals(mappedConversationId, conversationId)) {
            DeepSeekHistoryLog.log("CONVERSATION_MAPPING_IGNORED", "reason=" + reason
                    + " expected=" + shortConversationId(mappedConversationId)
                    + " actual=" + shortConversationId(conversationId));
            return;
        }
        boolean changed = !TextUtils.equals(mappedConversationId, conversationId);
        DeepSeekConversationStore.save(requireContext(), request, conversationId);
        mappedConversationId = conversationId;
        conversationRouteCorrections = 0;
        if (changed) {
            DeepSeekHistoryLog.log("CONVERSATION_MAPPING_SAVED", "reason=" + reason
                    + " id=" + shortConversationId(conversationId)
                    + " channel=" + safeChannelForLog(request));
        }
    }

    private static String shortConversationId(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String clean = value.trim();
        if (clean.length() <= 12) return clean;
        return clean.substring(0, 6) + "..." + clean.substring(clean.length() - 4);
    }

    private static String safeChannelForLog(DeepSeekRequest request) {
        if (request == null || TextUtils.isEmpty(request.channelId)) return "";
        String channel = request.channelId.trim();
        if (channel.length() <= 10) return channel + "/" + request.channelType;
        return channel.substring(0, 5) + "..." + channel.substring(channel.length() - 3)
                + "/" + request.channelType;
    }

    private void scheduleWebUiPreparation(long delayMs) {
        if (loginMode || webView == null || promptFilled) return;
        handler.removeCallbacks(webUiPrepareRunnable);
        handler.postDelayed(webUiPrepareRunnable, Math.max(0L, delayMs));
    }

    /**
     * 在填入提示词前整理 DeepSeek 网页状态：优先专家模式、关闭已开启的思考/搜索，
     * 并持续隐藏"下载应用"入口。这里仅操作可见网页控件，不拦截私有接口。
     */
    private void applyWebUiPreferences(boolean continueFill) {
        if (loginMode || webView == null || !isAdded() || promptFilled) return;
        if (webUiPrepareInFlight) {
            if (continueFill) handler.postDelayed(this::tryFillPrompt, 180);
            return;
        }
        webUiPrepareInFlight = true;
        try {
            webView.evaluateJavascript(buildWebUiPreferenceScript(), value -> {
                webUiPrepareInFlight = false;
                String result = cleanJsResult(value);
                webUiPrepareAttempts++;
                DeepSeekHistoryLog.log("WEB_UI_PREF_RESULT", "attempt=" + webUiPrepareAttempts
                        + " result=" + result + " continue=" + continueFill);

                if ("expert-ready".equals(result)) {
                    webUiPrepared = true;
                    // ★ FIX: 只在确认专家模式之后，才执行一次页面按钮隐藏
                    executeHidePageChrome();
                    if (continueFill || !TextUtils.isEmpty(pendingPrompt)) {
                        handler.postDelayed(this::tryFillPrompt, 40);
                    }
                    return;
                }

                webUiPrepared = false;
                if (statusView != null) {
                    statusView.setText("正在切换专家模式…");
                }

                if (webUiPrepareAttempts == 30) {
                    notifyUser("暂时没有找到专家模式入口，正在继续重试");
                }

                long delay;
                if ("changed".equals(result)) {
                    delay = randomDelay(180, 280);
                } else if (webUiPrepareAttempts < 30) {
                    delay = randomDelay(260, 420);
                } else {
                    delay = 1200L;
                }

                handler.postDelayed(
                        () -> applyWebUiPreferences(continueFill),
                        delay);
            });
        } catch (Exception e) {
            webUiPrepareInFlight = false;
            webUiPrepared = false;
            DeepSeekHistoryLog.log("WEB_UI_PREF_ERROR", e.getClass().getSimpleName());
            handler.postDelayed(() -> applyWebUiPreferences(continueFill), 700L);
        }
    }

    // ★ FIX: 单独执行隐藏页面按钮，只在专家模式确认后调用
    private void executeHidePageChrome() {
        if (webView == null || !isAdded()) return;
        try {
            webView.evaluateJavascript(
                    "(function(){try{if(window.__tsddHidePageChrome)window.__tsddHidePageChrome();}catch(e){}})();",
                    null);
        } catch (Exception ignored) {
        }
    }

    private String buildWebUiPreferenceScript() {
        return "(function(){try{" +
                "function visible(el){" +
                "if(!el)return false;" +
                "var s=getComputedStyle(el),r=el.getBoundingClientRect();" +
                "return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;" +
                "}" +

                "function text(el){" +
                "return ((el&&(el.innerText||el.textContent||el.getAttribute('aria-label')||el.getAttribute('title')))||'')" +
                ".replace(/\\s+/g,' ').trim();" +
                "}" +

                "function norm(v){" +
                "return String(v||'').replace(/\\s+/g,'').toLowerCase();" +
                "}" +

                "function compact(el){" +
                "if(!visible(el))return false;" +
                "var r=el.getBoundingClientRect();" +
                "return r.width>=16&&r.height>=16&&r.width<=260&&r.height<=120;" +
                "}" +

                // ★ FIX: actionRoot 遇到专家模式容器(c03d486a)时立即返回 null，绝不作为隐藏目标
                "function actionRoot(node){" +
                "var cur=node,fallback=null,n=0;" +
                "while(cur&&cur!==document.body&&cur!==document.documentElement&&n++<8){" +
                "var r=cur.getBoundingClientRect();" +
                "var tag=String(cur.tagName||'').toLowerCase();" +
                "var role=String(cur.getAttribute&&cur.getAttribute('role')||'').toLowerCase();" +
                "var cls=String(cur.className||'').toLowerCase();" +
                // ★ FIX: 遇到专家模式入口容器，直接返回 null 保护它
                "if(/c03d486a/.test(cls))return null;" +
                "var ok=r.width>=16&&r.height>=16&&r.width<=280&&r.height<=130;" +
                "if(ok&&(tag==='button'||tag==='a'||role==='button'||role==='menuitem'||role==='option'||" +
                "(cur.hasAttribute&&cur.hasAttribute('tabindex'))))return cur;" +
                // ★ FIX: fallback 正则中移除 mode 和 c03d486a，避免误匹配专家模式区域
                "if(!fallback&&ok&&(/ds-button|button|toggle|menu|option/.test(cls)||" +
                "getComputedStyle(cur).cursor==='pointer'||typeof cur.onclick==='function'))fallback=cur;" +
                "cur=cur.parentElement;" +
                "}" +
                "return fallback;" +
                "}" +

                "function click(el){" +
                "if(!el||!visible(el)||el.disabled||el.getAttribute('aria-disabled')==='true')return false;" +
                "try{el.click();return true;}catch(e){" +
                "try{" +
                "el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));" +
                "return true;" +
                "}catch(e2){return false;}" +
                "}" +
                "}" +

                // ★ FIX: hideElement 增加专家模式入口保护
                "function hideElement(el){" +
                "if(!el||el===document.body||el===document.documentElement)return false;" +
                // ★ FIX: 永不隐藏专家模式入口或其祖先/后代
                "if(el.closest&&el.closest('.the-header .c03d486a'))return false;" +
                "if(el.querySelector&&el.querySelector('.c03d486a'))return false;" +
                "var cls=String(el.className||'').toLowerCase();" +
                "if(/c03d486a/.test(cls))return false;" +
                "var r=el.getBoundingClientRect();" +
                "if(r.height<=0||r.height>220||r.width>window.innerWidth*0.98)return false;" +
                "el.style.setProperty('display','none','important');" +
                "el.style.setProperty('visibility','hidden','important');" +
                "el.style.setProperty('pointer-events','none','important');" +
                "el.setAttribute('aria-hidden','true');" +
                "return true;" +
                "}" +

                "function hideExactButtons(words){" +
                "var selector='button,a,[role=\"button\"],[role=\"menuitem\"],[tabindex]:not([tabindex=\"-1\"])';" +
                "Array.from(document.querySelectorAll(selector)).forEach(function(el){" +
                "if(words.indexOf(norm(text(el)))<0)return;" +
                // ★ FIX: 跳过专家模式入口区域内的元素
                "if(el.closest&&el.closest('.c03d486a'))return;" +
                "var root=actionRoot(el);" +
                "if(root)hideElement(root);else hideElement(el);" +
                "});" +
                "}" +

                "function hideCodeDownloads(){" +
                "document.querySelectorAll('pre').forEach(function(pre){" +
                "var scope=pre.parentElement;" +
                "if(!scope)return;" +
                "Array.from(scope.querySelectorAll('button,a,[role=\"button\"],[tabindex]')).forEach(function(el){" +
                "var t=norm(text(el));" +
                "if(t==='下载'||t==='download')hideElement(actionRoot(el)||el);" +
                "});" +
                "});" +
                "}" +

                "function hidePageChrome(){" +
                "document.querySelectorAll('span.ds-button__content').forEach(function(el){" +
                "var t=norm(text(el));" +
                "if(t==='下载应用'||t==='下载app'||t==='downloadapp'||t==='getapp'){" +
                // ★ FIX: 不再用 el.parentElement 兜底，避免误隐藏包含专家模式的父容器
                "var root=actionRoot(el);" +
                "if(root)hideElement(root);" +
                "}" +
                "});" +

                "hideExactButtons([" +
                "'下载应用','下载app','downloadapp','getapp'," +
                "'新对话','新建对话','开启新对话','newchat','newconversation'," +
                "'分享','share'" +
                "]);" +

                "document.querySelectorAll('svg path').forEach(function(path){" +
                "var d=path.getAttribute('d')||'',target=null;" +

                "if(d.indexOf('M9.99994 1.22943')>=0&&" +
                "d.indexOf('M9.21913 6.36949')>=0&&" +
                "d.indexOf('M13.6304 9.22487')>=0){" +
                "target=actionRoot(path);" +
                "}" +

                "else if(d.indexOf('M9.73047 1.98239')>=0&&" +
                "d.indexOf('M18.3906 8.83005')>=0&&" +
                "d.indexOf('M17.2881 9.73142')>=0){" +
                "target=actionRoot(path);" +
                "}" +

                "if(target)hideElement(target);" +
                "});" +

                "hideCodeDownloads();" +
                "}" +

                "function isExpertText(value){" +
                "var t=norm(value);" +
                "return t==='专家模式'||t==='专家'||t==='expertmode'||t==='expert';" +
                "}" +

                "function expertReady(){" +
                "var selectors=[" +
                "'.the-header .c03d486a'," +
                "'.the-header span._46a12ab'," +
                "'.the-header [data-model-type]'" +
                "];" +

                "for(var i=0;i<selectors.length;i++){" +
                "var nodes=Array.from(document.querySelectorAll(selectors[i]));" +
                "for(var j=0;j<nodes.length;j++){" +
                "if(visible(nodes[j])&&isExpertText(text(nodes[j])))return true;" +
                "}" +
                "}" +
                "return false;" +
                "}" +

                "function findExpertOption(){" +
                "var selector='button,[role=\"button\"],[role=\"menuitem\"],[role=\"option\"],li,[tabindex]:not([tabindex=\"-1\"]),div,span';" +
                "var nodes=Array.from(document.querySelectorAll(selector));" +

                "for(var i=0;i<nodes.length;i++){" +
                "var el=nodes[i];" +
                "if(!visible(el)||!isExpertText(text(el)))continue;" +

                "if(el.closest&&el.closest('.the-header'))continue;" +

                "var root=actionRoot(el);" +
                "if(root&&compact(root))return root;" +
                // ★ FIX: 如果 actionRoot 返回 null（被保护），直接用元素本身
                "if(!root&&compact(el))return el;" +
                "}" +
                "return null;" +
                "}" +

                "function findModeTrigger(){" +
                "var nodes=Array.from(document.querySelectorAll('.the-header .c03d486a'));" +
                "for(var i=0;i<nodes.length;i++){" +
                "if(!visible(nodes[i])||!compact(nodes[i]))continue;" +
                // ★ FIX: 不再通过 actionRoot 包装，直接使用 c03d486a 元素本身
                "return nodes[i];" +
                "}" +

                "var labels=Array.from(document.querySelectorAll('.the-header span._46a12ab'));" +
                "for(var j=0;j<labels.length;j++){" +
                "if(!visible(labels[j]))continue;" +
                // ★ FIX: 直接使用 label 的 parentElement，不经过 actionRoot
                "var root=labels[j].parentElement;" +
                "if(root&&compact(root))return root;" +
                "}" +

                "var paths=Array.from(document.querySelectorAll('.the-header svg path'));" +
                "for(var k=0;k<paths.length;k++){" +
                "var d=paths[k].getAttribute('d')||'';" +
                "if(d.indexOf('M11.0289 2.0918')<0||d.indexOf('M3.41858 5.46484')<0)continue;" +
                // ★ FIX: 直接使用 closest('.c03d486a')，不经过 actionRoot
                "var chip=paths[k].closest('.c03d486a');" +
                "if(chip&&visible(chip))return chip;" +
                "}" +

                "return null;" +
                "}" +

                // ★ FIX: 观察器只在专家模式确认后才隐藏页面按钮
                "window.__tsddHidePageChrome=function(){" +
                "try{" +
                "if(!window.__tsddExpertConfirmed)return;" +  // ★ FIX: 守卫
                "hidePageChrome();" +
                "}catch(e){}" +
                "};" +

                "if(!window.__tsddChromeObserverV4){" +
                "window.__tsddChromeObserverV4=true;" +
                "var chromeTimer=null;" +
                "new MutationObserver(function(){" +
                // ★ FIX: 只在专家模式确认后才执行隐藏
                "if(!window.__tsddExpertConfirmed)return;" +
                "clearTimeout(chromeTimer);" +
                "chromeTimer=setTimeout(function(){" +
                "try{hidePageChrome();}catch(e){}" +
                "},120);" +
                "}).observe(document.documentElement,{" +
                "childList:true," +
                "subtree:true" +
                "});" +
                "}" +

                "if(expertReady()){" +
                // ★ FIX: 设置全局标志，允许 observer 开始工作
                "window.__tsddExpertConfirmed=true;" +
                "hidePageChrome();" +
                "return 'expert-ready';" +
                "}" +

                "var option=findExpertOption();" +
                "if(option&&click(option))return 'changed';" +

                "var now=Date.now();" +
                "var trigger=findModeTrigger();" +
                "if(trigger&&(!window.__tsddModeTriggerAt||now-window.__tsddModeTriggerAt>700)){" +
                "window.__tsddModeTriggerAt=now;" +
                "if(click(trigger))return 'changed';" +
                "}" +

                // ★ FIX: retry 分支不再调用 hidePageChrome，避免在模式切换过程中隐藏入口
                "return 'retry';" +
                "}catch(e){return 'retry';}})();";
    }

    private void fillPromptNow() {
        if (promptFilled || webView == null || TextUtils.isEmpty(pendingPrompt)) return;
        if (!promptTransportPrepared) {
            prepareHiddenPromptTransport();
            return;
        }
        String composerText = promptTransportEnabled && !TextUtils.isEmpty(promptVisibleText)
                ? promptVisibleText : pendingPrompt;
        String js = buildFillScript(composerText);
        webView.evaluateJavascript(js, value -> {
            boolean success = "true".equals(value) || "\"true\"".equals(value);
            if (success) {
                promptFilled = true;
                statusView.setText(R.string.wkdeepseek_submitting);
                installReplyButtons();
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(300, 600));
                return;
            }
            fillAttempts++;
            if (!TextUtils.isEmpty(mappedConversationId) && fillAttempts >= 10) {
                DeepSeekHistoryLog.log("CONVERSATION_MAPPING_STALE",
                        "id=" + shortConversationId(mappedConversationId));
                resetMappingAndPromptForFreshConversation("stale_mapping");
                fillAttempts = 0;
                conversationRouteCorrections = 0;
                loadConversationRoute(NEW_CHAT_URL, "stale_mapping");
                return;
            }
            if (fillAttempts < 12) {
                handler.postDelayed(this::tryFillPrompt, 420);
            } else if (!fallbackCopied && isAdded()) {
                fallbackCopied = true;
                copyText(pendingPrompt);
                statusView.setText(R.string.wkdeepseek_fill_failed);
                notifyUser(getString(R.string.wkdeepseek_fill_failed));
            }
        });
    }

    private void submitPromptAutomatically() {
        if (promptSubmitted || submitReadyPending || submitClickPending || webView == null || !isAdded()) return;
        webView.evaluateJavascript(buildSendReadyProbeScript(), value -> {
            String result = cleanJsResult(value);
            if ("ready".equals(result)) {
                submitReadyPending = true;
                handler.postDelayed(this::clickPromptSendButton, randomDelay(180, 360));
                return;
            }

            submitAttempts++;
            if (submitAttempts < 12) {
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(300, 550));
            } else {
                statusView.setText(R.string.wkdeepseek_submit_failed);
                notifyUser(getString(R.string.wkdeepseek_submit_failed));
            }
        });
    }

    private void clickPromptSendButton() {
        if (promptSubmitted || submitClickPending || webView == null || !isAdded()) {
            submitReadyPending = false;
            return;
        }
        webView.evaluateJavascript(buildSubmitScript(), value -> {
            submitReadyPending = false;
            String result = cleanJsResult(value);
            if ("clicked".equals(result)) {
                submitClickPending = true;
                submitVerifyAttempts = 0;
                statusView.setText(R.string.wkdeepseek_submitting);
                handler.postDelayed(this::verifyPromptSubmission, randomDelay(250, 500));
                return;
            }

            submitAttempts++;
            if (submitAttempts < 12) {
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(320, 600));
            } else {
                statusView.setText(R.string.wkdeepseek_submit_failed);
                notifyUser(getString(R.string.wkdeepseek_submit_failed));
            }
        });
    }

    private void verifyPromptSubmission() {
        if (promptSubmitted || webView == null || !isAdded()) return;
        webView.evaluateJavascript(buildSubmitVerifyScript(), value -> {
            String result = cleanJsResult(value);
            if ("submitted".equals(result)) {
                submitClickPending = false;
                promptSubmitted = true;
                markContextSnapshotSubmitted();
                captureConversationFromCurrentUrl();
                handler.postDelayed(this::captureConversationFromCurrentUrl, 250);
                handler.postDelayed(this::captureConversationFromCurrentUrl, 800);
                handler.postDelayed(this::captureConversationFromCurrentUrl, 1800);
                handler.postDelayed(this::captureConversationFromCurrentUrl, 3500);
                statusView.setText(R.string.wkdeepseek_thinking);
                hideSubmittedPromptBubble();
                beginAnswerObservation();
                installReplyButtons();
                return;
            }

            submitVerifyAttempts++;
            if (submitVerifyAttempts < 5) {
                handler.postDelayed(this::verifyPromptSubmission, randomDelay(260, 500));
                return;
            }

            submitClickPending = false;
            submitAttempts++;
            if (submitAttempts < 12) {
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(380, 700));
            } else {
                statusView.setText(R.string.wkdeepseek_submit_failed);
                notifyUser(getString(R.string.wkdeepseek_submit_failed));
            }
        });
    }


    private void preparePromptTransportText() {
        long nonce = Math.abs(System.nanoTime() ^ timingRandom.nextLong());
        promptTransportToken = "<!--TALKAMI_PROMPT_" + Long.toHexString(nonce) + "-->";
        if (request == null) {
            promptVisibleLabel = "正在处理，请稍候…";
        } else if (request.action == DeepSeekRequest.ACTION_TRANSLATE) {
            promptVisibleLabel = "正在翻译所选消息…";
        } else if (request.action == DeepSeekRequest.ACTION_POLISH) {
            promptVisibleLabel = "正在润色这段消息…";
        } else {
            promptVisibleLabel = "正在分析聊天并生成回复…";
        }
        promptVisibleText = promptVisibleLabel + "\n" + promptTransportToken;
        promptTransportPrepared = false;
        promptTransportInFlight = false;
        promptTransportEnabled = false;
    }

    private void prepareHiddenPromptTransport() {
        if (promptTransportPrepared || promptTransportInFlight || webView == null || !isAdded()) return;
        if (TextUtils.isEmpty(promptTransportToken) || TextUtils.isEmpty(promptVisibleText)) {
            promptTransportPrepared = true;
            promptTransportEnabled = false;
            fillPromptNow();
            return;
        }
        promptTransportInFlight = true;
        try {
            webView.evaluateJavascript(buildPromptTransportInstallScript(), value -> {
                promptTransportInFlight = false;
                promptTransportPrepared = true;
                promptTransportEnabled = "true".equals(value) || "\"true\"".equals(value);
                fillPromptNow();
            });
        } catch (Exception ignored) {
            promptTransportInFlight = false;
            promptTransportPrepared = true;
            promptTransportEnabled = false;
            fillPromptNow();
        }
    }

    private String buildPromptTransportInstallScript() {
        String token = JSONObject.quote(promptTransportToken);
        String fullPrompt = JSONObject.quote(pendingPrompt);
        String pageCode = "(function(){try{" +
                "var token=" + token + ",full=" + fullPrompt + ";" +
                "window.__tsddPromptMap=window.__tsddPromptMap||{};window.__tsddPromptMap[token]=full;" +
                "if(window.__tsddPromptHookInstalled)return;window.__tsddPromptHookInstalled=true;" +
                "function patch(v,d){if(d>10||v==null)return v;if(typeof v==='string'){var map=window.__tsddPromptMap||{};for(var k in map){if(Object.prototype.hasOwnProperty.call(map,k)&&v.indexOf(k)>=0)return map[k];}return v;}" +
                "if(Array.isArray(v)){for(var i=0;i<v.length;i++)v[i]=patch(v[i],d+1);return v;}" +
                "if(Object.prototype.toString.call(v)==='[object Object]'){Object.keys(v).forEach(function(k){v[k]=patch(v[k],d+1);});}return v;}" +
                "function body(b){if(typeof b!=='string')return b;var t=b.trim();if(!t||(t.charAt(0)!=='{'&&t.charAt(0)!=='['))return b;try{return JSON.stringify(patch(JSON.parse(b),0));}catch(e){return b;}}" +
                "var oldFetch=window.fetch;if(typeof oldFetch==='function'){window.fetch=async function(input,init){try{if(init&&Object.prototype.hasOwnProperty.call(init,'body')){var ni=Object.assign({},init,{body:body(init.body)});return oldFetch.call(this,input,ni);}" +
                "if(typeof Request!=='undefined'&&input instanceof Request&&!/GET|HEAD/i.test(input.method)){var ot=await input.clone().text(),nt=body(ot);if(nt!==ot)return oldFetch.call(this,new Request(input,{body:nt}),init);}}catch(e){}return oldFetch.apply(this,arguments);};}" +
                "if(typeof XMLHttpRequest!=='undefined'){var oldSend=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(b){try{return oldSend.call(this,body(b));}catch(e){return oldSend.call(this,b);}};}" +
                "}catch(e){}})();";
        String quotedCode = JSONObject.quote(pageCode);
        return "(function(){try{var s=document.createElement('script');s.textContent=" + quotedCode + ";" +
                "(document.documentElement||document.head||document.body).appendChild(s);s.remove();" +
                "return !!(window.__tsddPromptHookInstalled&&window.__tsddPromptMap&&window.__tsddPromptMap[" + token + "]);" +
                "}catch(e){return false;}})();";
    }

    private void hideSubmittedPromptBubble() {
        if (webView == null || TextUtils.isEmpty(promptVisibleLabel)) return;
        String label = JSONObject.quote(promptVisibleLabel);
        String js = "(function(){try{" +
                "var label=" + label + ";window.__tsddPromptLabel=label;" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0;}" +
                "function norm(v){return String(v||'').replace(/\\s+/g,' ').trim();}" +
                "function hide(){var all=Array.from(document.querySelectorAll('div,article,p,span')).filter(function(el){return visible(el)&&norm(el.innerText||el.textContent)===label;});" +
                "if(!all.length)return false;all.sort(function(a,b){return a.getBoundingClientRect().top-b.getBoundingClientRect().top;});var leaf=all[all.length-1],best=leaf,cur=leaf,n=0;" +
                "while(cur&&cur.parentElement&&n++<4){var p=cur.parentElement,r=p.getBoundingClientRect(),t=norm(p.innerText||p.textContent);if(p===document.body||p===document.documentElement)break;if(p.querySelector('pre,textarea,[contenteditable=\\\"true\\\"],[role=\\\"textbox\\\"]'))break;if(r.height>0&&r.height<220&&r.width<window.innerWidth*0.98&&(t===label||t.length<=label.length+24&&t.indexOf(label)===0)){best=p;cur=p;}else break;}" +
                "best.dataset.tsddPromptHidden='1';best.style.setProperty('display','none','important');best.style.setProperty('visibility','hidden','important');return true;}" +
                "if(hide())return true;setTimeout(hide,120);setTimeout(hide,420);" +
                "if(!window.__tsddPromptHideObserver){var obs=new MutationObserver(function(){if(hide()){try{obs.disconnect();}catch(e){}window.__tsddPromptHideObserver=null;}});window.__tsddPromptHideObserver=obs;obs.observe(document.documentElement,{childList:true,subtree:true});setTimeout(function(){try{obs.disconnect();}catch(e){}if(window.__tsddPromptHideObserver===obs)window.__tsddPromptHideObserver=null;},5000);}" +
                "return true;}catch(e){return false;}})();";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private String buildSendReadyProbeScript() {
        String expectedPath = JSONObject.quote(DEEPSEEK_SEND_ICON_PATH);
        return "(function(){try{" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;}" +
                "function norm(v){return (v||'').trim().replace(/\\s+/g,' ');}" +
                "var expected=norm(" + expectedPath + ");" +
                "var boxes=Array.from(document.querySelectorAll('textarea,[contenteditable=\"true\"],[role=\"textbox\"]')).filter(function(x){return visible(x)&&!x.disabled&&x.getAttribute('aria-disabled')!=='true';});" +
                "var input=boxes.length?boxes[boxes.length-1]:null;if(!input)return 'no-input';" +
                "var text=((input.value!==undefined?input.value:input.innerText)||input.textContent||'').trim();if(!text)return 'empty-input';" +
                "var paths=Array.from(document.querySelectorAll('svg[viewBox=\"0 0 16 16\"] path,svg[viewbox=\"0 0 16 16\"] path'));" +
                "var path=paths.find(function(p){return visible(p.closest('svg'))&&norm(p.getAttribute('d'))===expected;});" +
                "if(!path)return 'no-send-icon';" +
                "var button=path.closest('button,[role=\"button\"]');if(!button||!visible(button))return 'no-send-button';" +
                "if(button.disabled||button.getAttribute('aria-disabled')==='true')return 'send-disabled';" +
                "return 'ready';" +
                "}catch(e){return 'error';}})();";
    }

    private String buildSubmitScript() {
        String expectedPath = JSONObject.quote(DEEPSEEK_SEND_ICON_PATH);
        return "(function(){try{" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;}" +
                "function norm(v){return (v||'').trim().replace(/\\s+/g,' ');}" +
                "var expected=norm(" + expectedPath + ");" +
                "var boxes=Array.from(document.querySelectorAll('textarea,[contenteditable=\"true\"],[role=\"textbox\"]')).filter(function(x){return visible(x)&&!x.disabled&&x.getAttribute('aria-disabled')!=='true';});" +
                "var input=boxes.length?boxes[boxes.length-1]:null;if(!input)return 'no-input';" +
                "var text=((input.value!==undefined?input.value:input.innerText)||input.textContent||'').trim();if(!text)return 'empty-input';" +
                "var paths=Array.from(document.querySelectorAll('svg[viewBox=\"0 0 16 16\"] path,svg[viewbox=\"0 0 16 16\"] path'));" +
                "var path=paths.find(function(p){return visible(p.closest('svg'))&&norm(p.getAttribute('d'))===expected;});" +
                "if(!path)return 'no-send-icon';" +
                "var button=path.closest('button,[role=\"button\"]');if(!button||!visible(button))return 'no-send-button';" +
                "if(button.disabled||button.getAttribute('aria-disabled')==='true')return 'send-disabled';" +
                "try{button.focus({preventScroll:true});}catch(ignore){try{button.focus();}catch(ignore2){}}button.click();return 'clicked';" +
                "}catch(e){return 'error';}})();";
    }

    private String buildSubmitVerifyScript() {
        String expectedPath = JSONObject.quote(DEEPSEEK_SEND_ICON_PATH);
        return "(function(){try{" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;}" +
                "function norm(v){return (v||'').trim().replace(/\\s+/g,' ');}" +
                "var expected=norm(" + expectedPath + ");" +
                "var boxes=Array.from(document.querySelectorAll('textarea,[contenteditable=\"true\"],[role=\"textbox\"]')).filter(function(x){return visible(x)&&!x.disabled&&x.getAttribute('aria-disabled')!=='true';});" +
                "var input=boxes.length?boxes[boxes.length-1]:null;" +
                "var text=input?(((input.value!==undefined?input.value:input.innerText)||input.textContent||'').trim()):'';" +
                "var sendVisible=Array.from(document.querySelectorAll('svg[viewBox=\"0 0 16 16\"] path,svg[viewbox=\"0 0 16 16\"] path')).some(function(p){var b=p.closest('button,[role=\"button\"]');return norm(p.getAttribute('d'))===expected&&visible(p.closest('svg'))&&b&&visible(b)&&!b.disabled&&b.getAttribute('aria-disabled')!=='true';});" +
                "var stopVisible=Array.from(document.querySelectorAll('button,[role=\"button\"]')).some(function(b){if(!visible(b))return false;var t=((b.innerText||'')+' '+(b.getAttribute('aria-label')||'')+' '+(b.getAttribute('title')||'')).toLowerCase();return /stop|停止生成|停止/.test(t);});" +
                "if(!text||stopVisible||!sendVisible)return 'submitted';return 'waiting';" +
                "}catch(e){return 'waiting';}})();";
    }

    private String cleanJsResult(String value) {
        if (value == null) return "";
        String result = value.trim();
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result.replace("\\\"", "\"");
    }

    private long randomDelay(int minMs, int maxMs) {
        int low = Math.max(0, minMs);
        int high = Math.max(low, maxMs);
        if (high == low) return low;
        return low + timingRandom.nextInt(high - low + 1);
    }

    private String buildFillScript(String prompt) {
        String quoted = JSONObject.quote(prompt);
        return "(function(){try{" +
                "var all=Array.from(document.querySelectorAll('textarea,[contenteditable=\"true\"],[role=\"textbox\"]'));" +
                "var visible=all.filter(function(x){return !x.disabled&&x.getClientRects().length>0;});" +
                "var el=visible.length?visible[visible.length-1]:null;if(!el)return false;var text=" + quoted + ";" +
                "el.focus();" +
                "if(el.tagName==='TEXTAREA'||el.tagName==='INPUT'){" +
                "var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;" +
                "var p=Object.getOwnPropertyDescriptor(proto,'value');if(p&&p.set)p.set.call(el,text);else el.value=text;" +
                "}else{el.textContent=text;}" +
                "try{el.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,inputType:'insertText',data:text}));}catch(ignore){}" +
                "try{el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));}catch(ignore){el.dispatchEvent(new Event('input',{bubbles:true}));}" +
                "el.dispatchEvent(new Event('change',{bubbles:true}));return true;" +
                "}catch(e){return false;}})();";
    }

    private void beginAnswerObservation() {
        if (webView == null || !isAdded()) return;
        String js = "(function(){try{" +
                "document.querySelectorAll('[data-tsdd-reply-bar]').forEach(function(x){x.remove();});" +
                "window.__tsddAnswerPhase=true;window.__tsddAnswerReady=false;window.__tsddResultReady=false;window.__tsddAnswerNotBefore=Date.now()+120;" +
                "setTimeout(function(){window.__tsddAnswerReady=true;if(window.__tsddDeepSeekAddV3)window.__tsddDeepSeekAddV3();},150);return 'ok';" +
                "}catch(e){return 'error';}})();";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void installReplyButtons() {
        if (webView == null) return;
        String fallbackMode;
        if (request != null && request.action == DeepSeekRequest.ACTION_TRANSLATE) {
            fallbackMode = translationCallback != null ? "translate_use" : "copy";
        } else {
            fallbackMode = "use";
        }
        String js = "(function(){" +
                "function norm(v){return String(v||'').replace(/\\s+/g,' ').trim();}" +
                "function compact(v){return String(v||'').replace(/\\s+/g,'').toLowerCase();}" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0;}" +
                "function restoreComposer(){document.querySelectorAll('[data-tsdd-composer-hidden]').forEach(function(root){root.removeAttribute('data-tsdd-composer-hidden');root.removeAttribute('aria-hidden');root.style.removeProperty('display');root.style.removeProperty('visibility');root.style.removeProperty('pointer-events');});document.documentElement.style.removeProperty('scroll-padding-bottom');}" +
                "function markBaseline(){if(window.__tsddAnswerPhase)return;document.querySelectorAll('.ds-assistant-message-main-content').forEach(function(root){root.dataset.tsddIgnoreAnswer='1';});document.querySelectorAll('pre code').forEach(function(code){code.dataset.tsddIgnore='1';var pre=code.closest('pre');if(pre)pre.dataset.tsddIgnore='1';});}" +
                "restoreComposer();markBaseline();" +
                "if(window.__tsddDeepSeekInstalledV3){if(window.__tsddDeepSeekAddV3)window.__tsddDeepSeekAddV3();return;}" +
                "window.__tsddDeepSeekInstalledV3=true;" +
                "var fallbackMode='" + fallbackMode + "';" +
                "function go(mode,text,local){if(!text)return;var u='tsdd-deepseek://result?mode='+mode+'&text='+encodeURIComponent(text);if(local)u+='&local='+encodeURIComponent(local);location.href=u;}" +
                "function actionRoot(node){var cur=node,fallback=null,n=0;while(cur&&cur!==document.body&&cur!==document.documentElement&&n++<8){var r=cur.getBoundingClientRect(),tag=String(cur.tagName||'').toLowerCase(),role=String(cur.getAttribute&&cur.getAttribute('role')||'').toLowerCase(),cls=String(cur.className||'').toLowerCase(),isCompact=r.width>=16&&r.height>=16&&r.width<=190&&r.height<=100;if(isCompact&&(tag==='button'||tag==='a'||role==='button'||role==='menuitem'||(cur.hasAttribute&&cur.hasAttribute('tabindex'))))return cur;if(!fallback&&isCompact&&(/ds-button|button|icon/.test(cls)||getComputedStyle(cur).cursor==='pointer'||typeof cur.onclick==='function'))fallback=cur;cur=cur.parentElement;}return fallback;}" +
                "function hideNativeDownload(pre){var scope=pre&&pre.parentElement;if(!scope)return;Array.from(scope.querySelectorAll('button,a,[role=\"button\"],span,div')).forEach(function(el){var t=compact(el.innerText||el.textContent||el.getAttribute('aria-label')||el.getAttribute('title'));if(t!=='下载'&&t!=='download')return;var root=actionRoot(el)||el;if(root&&root!==document.body)root.style.setProperty('display','none','important');});}" +
                "function button(label,mode,text,local){var b=document.createElement('button');b.type='button';b.textContent=label;b.dataset.tsddAction='1';b.style.cssText='display:inline-flex!important;visibility:visible!important;align-items:center;justify-content:center;border:0;border-radius:17px;padding:8px 13px;background:#edf3ff;color:#295eea;font-size:13px;font-weight:600;margin-left:8px;min-height:34px;position:relative;z-index:8';b.onclick=function(e){e.preventDefault();e.stopPropagation();go(mode,text,local||'');};return b;}" +
                "function nextValue(elements,start,re,maxSteps){for(var i=start+1,steps=0;i<elements.length&&steps++<(maxSteps||5);i++){var t=norm(elements[i].innerText||elements[i].textContent);var m=t.match(re);if(m&&norm(m[1]))return {value:norm(m[1]),element:elements[i]};if(/^(?:【?(?:建议|版本)\\s*\\d+】?)$/.test(t)||/^(?:原文|回复原文|对方语言|发送内容)\\s*[：:]/.test(t))break;}return null;}" +
                "function rows(root){return Array.from(root.querySelectorAll('p,li')).filter(function(el){return visible(el)&&!el.closest('[data-tsdd-reply-bar]');});}" +
                "function ownerFor(el,prefix){if(!el.dataset.tsddPlainOwner){window.__tsddPlainSeq=(window.__tsddPlainSeq||0)+1;el.dataset.tsddPlainOwner=prefix+(window.__tsddPlainSeq);}return el.dataset.tsddPlainOwner;}" +
                "function addBar(anchor,owner,buttons){if(!anchor||!anchor.parentNode||document.querySelector('[data-tsdd-reply-bar][data-tsdd-owner=\"'+owner+'\"]'))return false;var box=document.createElement('div');box.dataset.tsddReplyBar='1';box.dataset.tsddOwner=owner;box.style.cssText='display:flex!important;visibility:visible!important;justify-content:flex-end;align-items:center;flex-wrap:wrap;padding:8px 2px 12px;position:relative;z-index:7';buttons.forEach(function(b){box.appendChild(b);});anchor.parentNode.insertBefore(box,anchor.nextSibling);return true;}" +
                "function parsePlain(root){if(!root||root.dataset.tsddIgnoreAnswer==='1')return {added:false,last:null};var elements=rows(root),added=false,last=null;if(fallbackMode==='translate_use'||fallbackMode==='copy'){for(var i=0;i<elements.length;i++){var t=norm(elements[i].innerText||elements[i].textContent),m=t.match(/^(?:自然)?译文\\s*[：:]\\s*([\\s\\S]+)$/);if(!m||!norm(m[1]))continue;var translated=norm(m[1]),owner=ownerFor(elements[i],'t');var buttons=[];if(fallbackMode==='translate_use')buttons.push(button('显示译文','translate_use',translated,''));buttons.push(button('复制译文','copy',translated,''));if(addBar(elements[i],owner,buttons)){added=true;last=elements[i];}break;}return {added:added,last:last};}" +
                "for(var j=0;j<elements.length;j++){var line=norm(elements[j].innerText||elements[j].textContent);var match=line.match(/^(?:原文|回复原文|对方语言|发送内容)\\s*[：:]\\s*([\\s\\S]+)$/);if(!match||!norm(match[1]))continue;var original=norm(match[1]);var translatedInfo=nextValue(elements,j,/^(?:译文|我的母语译文|中文译文|本地显示)\\s*[：:]\\s*([\\s\\S]+)$/,5);if(!translatedInfo)continue;var translated=translatedInfo.value,anchor=translatedInfo.element,owner=ownerFor(anchor,'r'),combined=original+(translated?'\\n'+translated:'');var buttons=[button('填入聊天','use',original,translated),button('直接发送','send',original,translated),button('复制原文+译文','copy',combined,'')];if(addBar(anchor,owner,buttons)){added=true;last=anchor;}}" +
                "return {added:added,last:last};}" +
                "function localTextFor(pre){try{var n=pre?pre.nextElementSibling:null,steps=0;while(n&&steps++<6){if(n.tagName==='PRE'||/^H[1-6]$/.test(n.tagName))break;var t=norm(n.innerText||n.textContent),m=t.match(/(?:译文|本地显示|我的母语翻译)\\s*[：:]\\s*([\\s\\S]+)/);if(m&&norm(m[1]))return {value:norm(m[1]),element:n};n=n.nextElementSibling;}return {value:'',element:null};}catch(e){return {value:'',element:null};}}" +
                "function plainCard(pre,original,translated){if(pre.dataset.tsddPlainCardId){return document.querySelector('[data-tsdd-plain-card=\"'+pre.dataset.tsddPlainCardId+'\"]');}window.__tsddCardSeq=(window.__tsddCardSeq||0)+1;var id='c'+window.__tsddCardSeq,card=document.createElement('div');pre.dataset.tsddPlainCardId=id;card.dataset.tsddPlainCard=id;card.style.cssText='display:block;margin:8px 0 2px;padding:10px 12px;border-radius:10px;background:rgba(127,127,127,.08);white-space:pre-wrap;word-break:break-word;';var a=document.createElement('div');a.textContent='原文：'+original;card.appendChild(a);if(translated){var b=document.createElement('div');b.style.marginTop='6px';b.textContent='译文：'+translated;card.appendChild(b);}pre.parentNode.insertBefore(card,pre);pre.style.setProperty('display','none','important');return card;}" +
                "function parseCodeFallback(){var added=false,last=null;document.querySelectorAll('pre code').forEach(function(code){var pre=code.closest('pre');if(!pre||pre.dataset.tsddIgnore==='1'||code.dataset.tsddIgnore==='1'||code.dataset.tsddReadyV3==='1')return;hideNativeDownload(pre);var raw=norm(code.innerText||''),cls=String(code.className||'').toLowerCase(),looksProfile=cls.indexOf('profile')>=0||(raw.charAt(0)==='{'&&raw.indexOf('\"interaction_state\"')>=0);if(looksProfile){var owner=ownerFor(pre,'p');if(addBar(pre,owner,[button('更新联系人记录','profile',raw,'')])){added=true;last=pre;}code.dataset.tsddReadyV3='1';return;}var local=localTextFor(pre),card=plainCard(pre,raw,local.value),owner2=ownerFor(card,'f'),buttons;if(fallbackMode==='translate_use'||fallbackMode==='copy'){buttons=[];if(fallbackMode==='translate_use')buttons.push(button('显示译文','translate_use',raw,''));buttons.push(button('复制译文','copy',raw,''));}else{var combined=raw+(local.value?'\\n'+local.value:'');buttons=[button('填入聊天','use',raw,local.value),button('直接发送','send',raw,local.value),button('复制原文+译文','copy',combined,'')];}if(addBar(card,owner2,buttons)){added=true;last=card;}if(local.element)local.element.style.setProperty('display','none','important');code.dataset.tsddReadyV3='1';});return {added:added,last:last};}" +
                "function cleanup(){restoreComposer();var seen={};document.querySelectorAll('[data-tsdd-reply-bar]').forEach(function(bar){var owner=bar.dataset.tsddOwner||'';if(!owner||seen[owner])bar.remove();else seen[owner]=true;});}" +
                "function add(){cleanup();if(!window.__tsddAnswerPhase||!window.__tsddAnswerReady||Date.now()<(window.__tsddAnswerNotBefore||0))return;var added=false,last=null;Array.from(document.querySelectorAll('.ds-assistant-message-main-content')).forEach(function(root){var out=parsePlain(root);if(out.added){added=true;last=out.last;}});var fallback=parseCodeFallback();if(fallback.added){added=true;last=fallback.last;}if(added||window.__tsddResultReady){window.__tsddResultReady=true;restoreComposer();if(window.__tsddHidePageChrome)window.__tsddHidePageChrome();if(last)setTimeout(function(){try{last.scrollIntoView({behavior:'smooth',block:'end'});}catch(e){last.scrollIntoView(false);}},120);}}" +
                "window.__tsddDeepSeekAddV3=add;var timer=null;if(!window.__tsddResultObserverV3){window.__tsddResultObserverV3=true;new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(add,90);}).observe(document.documentElement,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['class','style','disabled','aria-disabled']});}add();" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void handlePluginUrl(Uri uri) {
        if (!"result".equals(uri.getHost())) return;
        String text = uri.getQueryParameter("text");
        String localDisplayText = uri.getQueryParameter("local");
        String mode = uri.getQueryParameter("mode");
        if (TextUtils.isEmpty(text) || text.length() > 12000) return;
        final String cleanText = text.trim();
        final String cleanLocalDisplayText = TextUtils.isEmpty(localDisplayText)
                ? cleanText : localDisplayText.trim();
        if ("copy".equals(mode)) {
            copyText(cleanText);
            Toast.makeText(requireContext(), "已复制", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("profile".equals(mode)) {
            confirmProfileUpdate(cleanText);
            return;
        }
        if ("translate_use".equals(mode)) {
            pendingTranslationText = cleanText;
            pendingTranslationDelivery = true;
            dismissAssistant();
            return;
        }
        if ("send".equals(mode)) {
            deliverReply(cleanText, cleanLocalDisplayText, true);
            return;
        }
        deliverReply(cleanText, cleanLocalDisplayText, false);
    }

    private void deliverReply(String text, String localDisplayText, boolean sendNow) {
        if (TextUtils.isEmpty(text)) return;
        String localText = TextUtils.isEmpty(localDisplayText) ? "" : localDisplayText.trim();
        String clipboardText = TextUtils.isEmpty(localText) || TextUtils.equals(text.trim(), localText)
                ? text : text.trim() + "\n" + localText;
        copyText(clipboardText);
        pendingReplyText = text;
        pendingReplyLocalDisplayText = TextUtils.isEmpty(localDisplayText) ? text : localDisplayText;
        pendingReplySendNow = sendNow;
        pendingReplyDelivery = true;
        if (!sendNow) {
            Toast.makeText(requireContext(), R.string.wkdeepseek_reply_used, Toast.LENGTH_SHORT).show();
        }
        dismissAssistant();
    }

    private void confirmProfileUpdate(String raw) {
        DeepSeekProfileParser.Update update = DeepSeekProfileParser.parse(raw);
        if (update == null) {
            Toast.makeText(requireContext(), R.string.wkdeepseek_profile_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        String description = DeepSeekProfileParser.describe(update);
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.wkdeepseek_profile_update_title)
                .setMessage(description)
                .setNegativeButton(R.string.wkdeepseek_ignore, null)
                .setPositiveButton(R.string.wkdeepseek_save, (dialog, which) -> {
                    if (request == null) return;
                    DeepSeekContactProfile profile = request.contactProfile == null
                            ? new DeepSeekContactProfile() : request.contactProfile;
                    update.applyTo(profile);
                    DeepSeekContactStore.saveProfile(requireContext(), request, profile);
                    Toast.makeText(requireContext(), R.string.wkdeepseek_profile_saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void dismissAssistant() {
        dismissAssistant("internal");
    }

    private void dismissAssistant(String reason) {
        if (closing) {
            DeepSeekHistoryLog.log("DIALOG_DISMISS_DUPLICATE", "reason=" + reason
                    + " current=" + dismissReason);
            return;
        }
        dismissReason = TextUtils.isEmpty(reason) ? "unknown" : reason;
        closing = true;
        DeepSeekHistoryLog.log("DIALOG_DISMISS_REQUEST", "reason=" + dismissReason
                + " added=" + isAdded()
                + " stateSaved=" + fragmentStateSaved()
                + " " + viewState(dialogRoot, "root")
                + " " + viewState(contentPanel, "panel")
                + " " + viewState(webView, "web"));
        hideWebKeyboard();
        handler.postDelayed(() -> {
            DeepSeekHistoryLog.log("DIALOG_DISMISS_EXECUTE", "reason=" + dismissReason
                    + " added=" + isAdded());
            if (isAdded()) {
                dismissAllowingStateLoss();
            }
        }, 40);
    }

    private void hideWebKeyboard() {
        if (webView == null) return;
        try {
            webView.clearFocus();
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && webView.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void copyText(String text) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("DeepSeek", text));
    }

    private void notifyUser(String message) {
        if (!isAdded() || TextUtils.isEmpty(message)) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private boolean isDeepSeek(Uri uri) {
        String host = uri == null ? "" : uri.getHost();
        return "https".equalsIgnoreCase(uri == null ? "" : uri.getScheme())
                && host != null
                && (host.equals("chat.deepseek.com") || host.endsWith(".deepseek.com"));
    }

    private boolean looksLoggedInByUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("chat.deepseek.com/a/chat") && !looksLikeLoginUrl(lower);
    }

    private boolean looksLikeLoginUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/login")
                || lower.contains("/signin")
                || lower.contains("/sign-in")
                || lower.contains("/sign_in")
                || lower.contains("/register")
                || lower.contains("/signup")
                || lower.contains("/sign-up")
                || lower.contains("/sign_up");
    }

    private boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        android.net.NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private DeepSeekRequest requestFrom(Bundle args) {
        DeepSeekRequest out = new DeepSeekRequest();
        out.action = args.getInt(ARG_ACTION, DeepSeekRequest.ACTION_REPLY);
        out.channelId = args.getString(ARG_CHANNEL_ID, "");
        out.channelType = args.getByte(ARG_CHANNEL_TYPE, (byte) 1);
        out.selfUid = args.getString(ARG_SELF_UID, "");
        out.myNativeLanguage = args.getString(ARG_MY_NATIVE, "自动");
        out.peerNativeLanguage = args.getString(ARG_PEER_NATIVE, "自动");
        out.myLearningLanguages = args.getString(ARG_MY_LEARNING, "");
        out.peerLearningLanguages = args.getString(ARG_PEER_LEARNING, "");
        out.draft = args.getString(ARG_DRAFT, "");
        out.background = args.getString(ARG_BACKGROUND, "");
        out.purpose = args.getString(ARG_PURPOSE, "自然继续聊天");
        out.relationshipStage = args.getString(ARG_RELATIONSHIP_STAGE, "auto");
        out.preferredStyle = args.getString(ARG_PREFERRED_STYLE, "natural");
        out.flirtLevel = args.getInt(ARG_FLIRT_LEVEL, 0);
        out.contextEnabled = args.getBoolean(ARG_CONTEXT_ENABLED, true);
        out.contextLimit = args.getInt(ARG_CONTEXT_LIMIT, 100);
        out.targetMessageId = args.getString(ARG_TARGET_MESSAGE_ID, "");
        out.targetMessageText = args.getString(ARG_TARGET_MESSAGE_TEXT, "");
        out.contextSnapshot = args.getString(ARG_CONTEXT_SNAPSHOT, "");
        out.contextSnapshotCount = args.getInt(ARG_CONTEXT_SNAPSHOT_COUNT, 0);
        out.contactProfile = DeepSeekContactProfile.fromJson(args.getString(ARG_CONTACT_PROFILE, "{}"));
        DeepSeekContactStore.apply(requireContext(), out);
        return out;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onResume() {
        super.onResume();
        DeepSeekHistoryLog.log("DIALOG_ON_RESUME", "activity=" + activityState()
                + " " + viewState(dialogRoot, "root")
                + " " + viewState(webView, "web"));
    }

    @Override
    public void onPause() {
        DeepSeekHistoryLog.log("DIALOG_ON_PAUSE", "activity=" + activityState()
                + " " + viewState(dialogRoot, "root")
                + " " + viewState(webView, "web"));
        super.onPause();
    }

    @Override
    public void onStop() {
        DeepSeekHistoryLog.log("DIALOG_ON_STOP", "activity=" + activityState()
                + " reason=" + dismissReason);
        super.onStop();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        dismissReason = "system_cancel";
        DeepSeekHistoryLog.log("DIALOG_ON_CANCEL", viewState(dialogRoot, "root")
                + " " + viewState(webView, "web"));
        super.onCancel(dialog);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        hideWebKeyboard();
        DeepSeekHistoryLog.log("DIALOG_ON_DISMISS_BEFORE", "reason=" + dismissReason
                + " pendingReply=" + pendingReplyDelivery
                + " pendingTranslation=" + pendingTranslationDelivery
                + " activity=" + activityState());
        super.onDismiss(dialog);

        if (pendingTranslationDelivery && translationCallback != null) {
            final DeepSeekAssistant.TranslationCallback callback = translationCallback;
            final String text = pendingTranslationText;
            pendingTranslationDelivery = false;
            callback.onTranslation(text);
        }

        if (!loginMode && stateCallback != null) {
            stateCallback.onChanged();
        }

        if (pendingReplyDelivery && replyCallback != null) {
            final DeepSeekAssistant.ReplyCallback callback = replyCallback;
            final String text = pendingReplyText;
            final boolean sendNow = pendingReplySendNow;
            pendingReplyDelivery = false;
            FragmentActivity activity = getActivity();
            if (activity != null && !activity.isFinishing()) {
                activity.getWindow().getDecorView().postDelayed(
                        () -> callback.onReply(text, sendNow), 60);
            } else {
                callback.onReply(text, sendNow);
            }
        }
        DeepSeekHistoryLog.log("DIALOG_ON_DISMISS_AFTER", "reason=" + dismissReason
                + " activity=" + activityState());
    }

    @Override
    public void onDestroyView() {
        DeepSeekHistoryLog.log("DIALOG_ON_DESTROY_VIEW_BEGIN", "reason=" + dismissReason
                + " activity=" + activityState()
                + " " + viewState(webView, "web"));
        hideWebKeyboard();
        handler.removeCallbacks(loginProbeRunnable);
        handler.removeCallbacks(webUiPrepareRunnable);
        if (webView != null) {
            captureConversationFromCurrentUrl();
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        CookieManager.getInstance().flush();
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        DeepSeekHistoryLog.log("DIALOG_ON_DESTROY_VIEW_END", "reason=" + dismissReason
                + " activity=" + activityState());
    }

    private boolean fragmentStateSaved() {
        try {
            return isAdded() && getParentFragmentManager().isStateSaved();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String activityState() {
        FragmentActivity activity = getActivity();
        if (activity == null) return "null";
        return activity.getClass().getSimpleName()
                + " finishing=" + activity.isFinishing()
                + " destroyed=" + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed())
                + " lifecycle=" + activity.getLifecycle().getCurrentState();
    }

    private static String viewState(View view, String name) {
        if (view == null) return name + "=null";
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        return name + "{vis=" + view.getVisibility()
                + ",shown=" + view.isShown()
                + ",attached=" + view.isAttachedToWindow()
                + ",size=" + view.getWidth() + "x" + view.getHeight()
                + ",measured=" + view.getMeasuredWidth() + "x" + view.getMeasuredHeight()
                + ",lp=" + (lp == null ? "null" : lp.width + "x" + lp.height)
                + ",alpha=" + view.getAlpha()
                + ",ty=" + view.getTranslationY()
                + "}";
    }

    private static String safeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            return String.valueOf(uri.getScheme()) + "://" + String.valueOf(uri.getHost())
                    + String.valueOf(uri.getPath());
        } catch (Throwable ignored) {
            return "invalid";
        }
    }
}
