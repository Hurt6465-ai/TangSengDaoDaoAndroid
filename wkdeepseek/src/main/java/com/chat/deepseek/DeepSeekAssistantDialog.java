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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;


public class DeepSeekAssistantDialog extends DialogFragment {
    private static final String URL = "https://chat.deepseek.com/";
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

    private static final int MAX_FILL_ATTEMPTS = 5;
    private static final int MAX_SUBMIT_ATTEMPTS = 5;
    private static final int MAX_SUBMIT_VERIFY_ATTEMPTS = 4;

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
    private String promptVisibleLabel = "";
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
    private FrameLayout assistantHost;
    private ScrollView nativeReplyScroll;
    private LinearLayout nativeReplyList;
    private String nativeReplySignature = "";
    private boolean nativeReplyPolling;
    private boolean nativeReplyScanInFlight;
    private int nativeReplyScanAttempts;
    private int nativeReplyStableCount;
    private String nativeReplyLastCandidate = "";
    private String nativeReplyLastScanState = "";
    private boolean historyLogStarted;
    private final Runnable nativeReplyPollRunnable = this::pollNativeReplyOptions;
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
    /** Snapshot whose hashes must become the next incremental-sync baseline after success. */
    private String submittedContextBaselineSnapshot = "";
    private int contextOverflowRecoveryLevel;
    private boolean contextOverflowProbeInFlight;
    private boolean contextOverflowRecoveryInProgress;
    private boolean contextPlanApplied;
    private boolean forceFullContextForFreshConversation;
    private int promptBuildGeneration;
    private boolean freshConversationClickInFlight;
    private int freshConversationClickAttempts;

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
            submittedContextBaselineSnapshot = fullContextSnapshot;
            ensureHistoryLogStarted(args);
            DeepSeekHistoryLog.log("NATIVE_RESULT_V12_INIT",
                    "action=" + request.action + " channel_type=" + request.channelType);
        }
        DeepSeekHistoryLog.log("DIALOG_ON_CREATE_VIEW", "loginMode=" + loginMode
                + " action=" + (request == null ? -1 : request.action)
                + " saved=" + (savedInstanceState != null));
        return buildContent(requireContext());
    }


    private void ensureHistoryLogStarted(Bundle args) {
        if (historyLogStarted || args == null || args.getBoolean(ARG_LOGIN, false)) return;
        historyLogStarted = true;
        DeepSeekHistoryLog.begin(requireContext().getApplicationContext(),
                args.getString(ARG_CHANNEL_ID, ""),
                args.getByte(ARG_CHANNEL_TYPE, (byte) 1));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Bundle args = getArguments() == null ? Bundle.EMPTY : getArguments();
        ensureHistoryLogStarted(args);
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

        // 登录页和普通助手页都必须允许 WebView 输入框调用系统输入法。
        // FLAG_ALT_FOCUSABLE_IM 会让 Dialog 永远位于输入法之上，导致网页 textarea
        // 即使已经获得焦点，也无法弹出软键盘。
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
            // 面板以外的透明区域就是关闭热区。面板本身会消费点击，不会误关。
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
            // 消费点击，防止冒泡到透明区域关闭监听。
        });
        // 不对包含 WebView 的父容器启用 clipToOutline。部分 MIUI/Android WebView
        // 在透明全屏 Dialog + 非统一圆角轮廓下会得到空裁剪区域，表现为整页白屏。
        contentPanel.setClipToOutline(false);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        // 登录页全屏；普通助手只让底部面板占 60%，透明上半区仍可看到并关闭聊天页。
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

        // 登录/注册页保留状态和“完成”按钮。正常聊天助手不再占用顶部 48dp：
        // 直接让 DeepSeek 网页铺满固定面板，关闭使用系统返回键。
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
            // 普通助手也把状态控件加入布局，但默认不占空间。这样主页面加载失败时
            // 不会只剩一块白色区域，而能显示明确错误；加载期间保留一条细进度线。
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

        assistantHost = new FrameLayout(context);
        assistantHost.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        nativeReplyScroll = new ScrollView(context);
        nativeReplyScroll.setFillViewport(true);
        nativeReplyScroll.setVisibility(View.GONE);
        nativeReplyScroll.setBackgroundColor(Color.WHITE);
        nativeReplyList = new LinearLayout(context);
        nativeReplyList.setOrientation(LinearLayout.VERTICAL);
        nativeReplyList.setPadding(dp(12), dp(10), dp(12), dp(16));
        nativeReplyScroll.addView(nativeReplyList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        assistantHost.addView(nativeReplyScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        contentPanel.addView(assistantHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!isOnline(context)) {
            statusView.setText(R.string.wkdeepseek_no_network);
            progressBar.setVisibility(View.GONE);
            notifyUser(getString(R.string.wkdeepseek_no_network));
        } else {
            if (!loginMode) {
                mappedConversationId = DeepSeekConversationStore.getConversationId(context, request);
                conversationRouteTarget = TextUtils.isEmpty(mappedConversationId)
                        ? URL
                        : DeepSeekConversationStore.conversationUrl(mappedConversationId);
                conversationRouteLoading = false;
                DeepSeekHistoryLog.log("CONVERSATION_ROUTE_START",
                        "mapped=" + !TextUtils.isEmpty(mappedConversationId)
                                + " channel=" + safeChannelForLog(request)
                                + " cold_start=homepage");
            }
            // First load the stable homepage so the React shell, cookies and model selector render.
            // Contact routing is performed only after login detection succeeds.
            webView.loadUrl(URL);
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
                // DeepSeek 登录后经常使用 SPA 路由，页面不会再次触发 onPageFinished。
                // 在页面主体基本可用时主动探测输入框，避免已经登录却一直无法开启。
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
                // 登录成功后 DeepSeek 可能只通过 history.pushState 改地址，
                // 这里负责捕获 /a/chat 等单页路由变化。
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
                // 登录页面由 React 动态渲染，持续轻量探测，直到用户登录或关闭。
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
                // 提示词准备完成时重新校验一次网页模式，避免页面进度 70% 时过早
                // 判定“稳定”，导致专家模式/思考/搜索尚未渲染就直接提交。
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
        // A normal incremental/full plan represents the complete local snapshot after it succeeds.
        submittedContextBaselineSnapshot = fullContextSnapshot;
        contextPlanApplied = true;
        forceFullContextForFreshConversation = false;
        DeepSeekHistoryLog.log("CONTEXT_REUSE_PLAN", "mode=" + plan.mode
                + " mapped=" + hasMappedConversation
                + " full_count=" + fullContextSnapshotCount
                + " send_count=" + plan.count
                + " send_chars=" + plan.snapshot.length());
    }

    /**
     * A deleted/inaccessible mapped DeepSeek conversation must restart with the complete current
     * Talkami snapshot. Reusing the previously built delta on a fresh conversation would omit
     * essential history and produce an answer without enough context.
     */
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
        promptVisibleLabel = "";
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
        if (context == null || request == null
                || TextUtils.isEmpty(submittedContextBaselineSnapshot)) return;
        DeepSeekConversationStore.markContextSubmitted(
                context, request, submittedContextBaselineSnapshot);
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
                    // 登录页可能在当前 WebView 内显示。保持低频探测，用户完成登录后
                    // 自动继续，不需要关闭窗口再重新点一次。
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

    /**
     * Makes sure an action starts in the conversation assigned to the current Talkami contact.
     * DeepSeek often redirects to the last web conversation after login; without this gate the
     * prompt for contact B could be appended to contact A's DeepSeek history.
     */
    private boolean ensureConversationRouteReady() {
        if (loginMode || webView == null || request == null) return true;
        String currentUrl = webView.getUrl();
        if (TextUtils.isEmpty(currentUrl)) {
            handler.postDelayed(this::tryFillPrompt, 260);
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
                loadConversationRoute(URL, "mapped_route_unavailable_home");
                return false;
            }
            loadConversationRoute(DeepSeekConversationStore.conversationUrl(mappedConversationId),
                    "restore_mapped");
            return false;
        }

        // A first-use contact must not inherit the last conversation opened in DeepSeek.
        // Do not cold-load /a/chat/: on some Android WebView versions that route renders a blank
        // React shell. Click DeepSeek's own visible "开启新对话" control after the homepage loads.
        if (!TextUtils.isEmpty(currentId) && !promptSubmitted) {
            openFreshConversationFromPage();
            return false;
        }

        // Root or /a/chat without a conversation id is the fresh composer. At this point the
        // normal login probe has already confirmed that a usable composer exists.
        conversationRouteCorrections = 0;
        freshConversationClickAttempts = 0;
        return true;
    }

    private void openFreshConversationFromPage() {
        if (webView == null || freshConversationClickInFlight || !isAdded()) return;
        freshConversationClickInFlight = true;
        String script = "(function(){try{" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;}" +
                "function norm(el){return ((el&&(el.innerText||el.textContent||el.getAttribute('aria-label')||el.getAttribute('title')))||'').replace(/\\s+/g,'').toLowerCase();}" +
                "var names=['开启新对话','新对话','新建对话','newchat','newconversation'];" +
                "var nodes=Array.from(document.querySelectorAll('button,a,[role=\\\"button\\\"],[role=\\\"menuitem\\\"],[tabindex]:not([tabindex=\\\"-1\\\"]),._5a8ac7a'));" +
                "var target=nodes.find(function(el){return visible(el)&&names.indexOf(norm(el))>=0;});" +
                "if(!target){var spans=Array.from(document.querySelectorAll('span,div')).filter(function(el){return visible(el)&&names.indexOf(norm(el))>=0;});" +
                "if(spans.length){target=spans[0].closest('button,a,[role=\\\"button\\\"],[tabindex],._5a8ac7a')||spans[0];}}" +
                "if(!target)return 'retry';target.click();return 'clicked';" +
                "}catch(e){return 'retry';}})();";
        try {
            webView.evaluateJavascript(script, value -> {
                freshConversationClickInFlight = false;
                String result = cleanJsResult(value);
                freshConversationClickAttempts++;
                if ("clicked".equals(result)) {
                    webUiPrepared = false;
                    webUiPrepareAttempts = 0;
                    handler.postDelayed(this::tryFillPrompt, 500);
                } else if (freshConversationClickAttempts < 12) {
                    handler.postDelayed(this::tryFillPrompt, 500);
                } else {
                    // Fail open on the already rendered page instead of replacing it with a blank
                    // direct route. The user can still see the page and the expert-mode selector.
                    freshConversationClickAttempts = 0;
                    handler.postDelayed(this::tryFillPrompt, 800);
                }
            });
        } catch (Exception ignored) {
            freshConversationClickInFlight = false;
            handler.postDelayed(this::tryFillPrompt, 700);
        }
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
        freshConversationClickInFlight = false;
        if (!promptSubmitted) {
            // The transport hook lives in the page's JavaScript world and is destroyed by loadUrl.
            // Reinstall it before filling the composer on the newly loaded conversation route.
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
            // A login redirect landed on another old conversation. Do not bind or submit there.
            handler.post(() -> loadConversationRoute(
                    DeepSeekConversationStore.conversationUrl(mappedConversationId),
                    "redirected_to_other_conversation"));
            return;
        }

        if (promptSubmitted) {
            saveActiveConversation(conversationId, "navigation_after_submit");
        } else if (TextUtils.equals(mappedConversationId, conversationId)) {
            // Touch the entry so frequently used contacts survive bounded-LRU cleanup.
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
        if (loginMode || webView == null || promptFilled || webUiPrepared) return;
        handler.removeCallbacks(webUiPrepareRunnable);
        handler.postDelayed(webUiPrepareRunnable, Math.max(0L, delayMs));
    }

    /**
     * 在填入提示词前整理 DeepSeek 网页状态：有限次数尝试切换专家模式、关闭思考/搜索，
     * 并独立持续隐藏“下载应用”入口。模式控制绝不挂到 MutationObserver 上，避免反复
     * 展开/关闭模式菜单，导致用户看不到专家模式或关闭页面后脚本仍持续运行。
     */
    private void applyWebUiPreferences(boolean continueFill) {
        if (loginMode || webView == null || !isAdded() || promptFilled || webUiPrepared) return;

        // 页面进度、SPA history 和 tryFillPrompt 可能同时触发准备流程。设置硬上限，
        // 即使旧的延迟任务仍在队列里，也会在入口处停止，不再出现几十次重复执行。
        if (webUiPrepareAttempts >= 6) {
            webUiPrepared = true;
            handler.removeCallbacks(webUiPrepareRunnable);
            if (continueFill || !TextUtils.isEmpty(pendingPrompt)) {
                handler.postDelayed(this::tryFillPrompt, 80);
            }
            return;
        }

        if (webUiPrepareInFlight) {
            return;
        }
        webUiPrepareInFlight = true;
        try {
            webView.evaluateJavascript(buildWebUiPreferenceScript(), value -> {
                webUiPrepareInFlight = false;
                if (!isAdded() || webView == null || promptFilled || webUiPrepared) return;

                String result = cleanJsResult(value);
                webUiPrepareAttempts++;
                DeepSeekHistoryLog.log("WEB_UI_PREF_RESULT", "attempt=" + webUiPrepareAttempts
                        + " result=" + result + " continue=" + continueFill);

                boolean needsRetry = "changed".equals(result) || "retry".equals(result);
                if (needsRetry && webUiPrepareAttempts < 6) {
                    handler.removeCallbacks(webUiPrepareRunnable);
                    handler.postDelayed(() -> {
                        if (!webUiPrepared && isAdded() && webView != null && !promptFilled) {
                            applyWebUiPreferences(continueFill);
                        }
                    }, randomDelay(320, 520));
                    return;
                }

                webUiPrepared = true;
                handler.removeCallbacks(webUiPrepareRunnable);
                if (continueFill || !TextUtils.isEmpty(pendingPrompt)) {
                    handler.postDelayed(this::tryFillPrompt, 80);
                }
            });
        } catch (Exception ignored) {
            webUiPrepareInFlight = false;
            webUiPrepared = true;
            handler.removeCallbacks(webUiPrepareRunnable);
            if (continueFill) fillPromptNow();
        }
    }

    private String buildWebUiPreferenceScript() {
        return "(function(){try{" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;}" +
                "function txt(el){return ((el&&(el.innerText||el.textContent||el.getAttribute('aria-label')||el.getAttribute('title')))||'').replace(/\\s+/g,' ').trim();}" +
                "function norm(el){return txt(el).replace(/\\s+/g,'').toLowerCase();}" +
                "function selected(el){if(!el)return false;var cur=el,n=0;while(cur&&cur!==document.documentElement&&n++<4){var c=String(cur.className||'').toLowerCase();" +
                "if(/(^|[ _-])(selected|active|checked|current)([ _-]|$)/.test(c))return true;" +
                "if(cur.getAttribute('aria-checked')==='true'||cur.getAttribute('aria-pressed')==='true'||cur.getAttribute('aria-selected')==='true'||cur.getAttribute('aria-current')==='true')return true;" +
                "var ds=String(cur.getAttribute('data-state')||'').toLowerCase();if(ds==='checked'||ds==='selected'||ds==='active'||ds==='on')return true;" +
                "if(cur.getAttribute('data-selected')==='true'||cur.getAttribute('data-active')==='true')return true;cur=cur.parentElement;}return false;}" +
                "function click(el){if(!el||!visible(el)||el.disabled||el.getAttribute('aria-disabled')==='true')return false;" +
                "var r=el.getBoundingClientRect();if(r.width>window.innerWidth*0.98&&r.height>window.innerHeight*0.45)return false;" +
                "try{el.focus({preventScroll:true});}catch(e){try{el.focus();}catch(e2){}}" +
                "try{el.click();return true;}catch(e){try{el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));return true;}catch(e2){return false;}}}" +
                "function pool(){var q='button,a,label,[role=\\\"button\\\"],[role=\\\"radio\\\"],[role=\\\"switch\\\"],[role=\\\"checkbox\\\"],[role=\\\"menuitem\\\"],[role=\\\"option\\\"],[role=\\\"tab\\\"],[data-model-type],[tabindex]:not([tabindex=\\\"-1\\\"])';" +
                "return Array.from(document.querySelectorAll(q)).filter(function(el){var t=txt(el);return visible(el)&&t.length>0&&t.length<80;});}" +
                "function exactExpert(){var labels=Array.from(document.querySelectorAll('div.dfb78875>span._321831d,span._321831d')).filter(function(el){return norm(el)==='专家模式';}),rows=[];" +
                "labels.forEach(function(label){var row=label.closest('div.dfb78875');if(!row||!visible(row))return;var r=row.getBoundingClientRect();if(r.width<36||r.height<22||r.height>96||r.width>window.innerWidth*0.96)return;if(rows.indexOf(row)<0)rows.push(row);});" +
                "if(!rows.length)return null;var option=null;rows.some(function(row){var p=row.parentElement,depth=0;while(p&&p!==document.body&&depth++<4){var modeRows=Array.from(p.querySelectorAll('div.dfb78875')).filter(visible);var modeTexts=modeRows.map(norm),hasOtherMode=modeTexts.some(function(t){return t==='快速模式'||t==='快速'||t==='普通模式'||t==='标准模式'||t==='常规模式'||t==='思考模式'||t==='深度思考';});if(modeRows.length>=2&&modeTexts.indexOf('专家模式')>=0&&hasOtherMode){option=row;return true;}p=p.parentElement;}return false;});" +
                "return {el:option||rows[0],isOption:!!option};}" +
                "function actionRoot(node){var cur=node,n=0;while(cur&&cur!==document.body&&cur!==document.documentElement&&n++<6){var tag=String(cur.tagName||'').toLowerCase(),role=String(cur.getAttribute&&cur.getAttribute('role')||'').toLowerCase(),r=cur.getBoundingClientRect();if(r.height>0&&r.height<140&&(tag==='button'||tag==='a'||role==='button'||role==='menuitem'))return cur;cur=cur.parentElement;}return node;}" +
                "function hideDownloads(list){var re=/(下载(\\s*deepseek)?(\\s*应用|\\s*app)|打开(\\s*deepseek)?(\\s*应用|\\s*app)|在\\s*app\\s*中打开|download\\s*(the\\s*)?app|get\\s*(the\\s*)?app|open\\s*in\\s*app)/i;" +
                "list.forEach(function(el){if(!re.test(txt(el)))return;var root=actionRoot(el),r=root.getBoundingClientRect();if(root&&root!==document.body&&r.height>0&&r.height<140&&r.width<window.innerWidth*0.95){root.style.setProperty('display','none','important');root.style.setProperty('visibility','hidden','important');root.style.setProperty('pointer-events','none','important');root.setAttribute('aria-hidden','true');}});}" +
                "function enforce(allowMenu){var list=pool(),changed=false,retry=false,now=Date.now();hideDownloads(list);" +
                "var exact=exactExpert(),expert=exact?exact.el:list.find(function(el){var d=String(el.getAttribute('data-model-type')||'').toLowerCase(),t=norm(el);return d==='expert'||t==='专家模式'||t==='专家'||t==='expert'||t==='expertmode';});" +
                "if(expert&&!window.__tsddExpertDone){if(selected(expert)){window.__tsddExpertDone=true;}else if(!window.__tsddExpertClickAt||now-window.__tsddExpertClickAt>900){if(click(expert)){window.__tsddExpertClickAt=now;window.__tsddExpertClickCount=(window.__tsddExpertClickCount||0)+1;changed=true;if(exact&&exact.isOption)window.__tsddExpertDone=true;else retry=true;}}if(!window.__tsddExpertDone&&(window.__tsddExpertClickCount||0)<3)retry=true;}" +
                "else if(!window.__tsddExpertDone&&allowMenu){retry=true;var trigger=list.find(function(el){var t=norm(el);return (el.getAttribute('aria-haspopup')||'')!==''&&(/快速模式|普通模式|标准模式|常规模式|quickmode|normalmode|standardmode|^模式$|^mode$/.test(t));});if(trigger&&(!window.__tsddModeMenuTryAt||now-window.__tsddModeMenuTryAt>1200)){window.__tsddModeMenuTryAt=now;if(click(trigger))changed=true;}}" +
                "function disable(re){var el=list.find(function(x){return re.test(txt(x))&&selected(x);});if(el&&click(el))changed=true;}" +
                "disable(/(^|\\s)(深度思考|深度思索|思考|deepthink|deep\\s*think|thinking|reasoning)(\\s|$)/i);" +
                "disable(/(^|\\s)(智能搜索|联网搜索|网络搜索|搜索|smart\\s*search|web\\s*search|search|browse)(\\s|$)/i);" +
                "return changed?'changed':(retry?'retry':'stable');}" +
                "window.__tsddEnforcePrefs=enforce;window.__tsddHidePageChrome=function(){try{hideDownloads(pool());}catch(e){}};" +
                "if(!window.__tsddDownloadObserver){window.__tsddDownloadObserver=true;var timer=null;new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(function(){try{hideDownloads(pool());}catch(e){}},220);}).observe(document.documentElement,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['aria-label','title']});}" +
                "return enforce(true);" +
                "}catch(e){return 'error';}})();";
    }

    private void fillPromptNow() {
        if (promptFilled || webView == null || TextUtils.isEmpty(pendingPrompt)) return;
        // Send the complete prompt through the normal DeepSeek composer. Do not intercept or
        // rewrite webpage network request bodies. This is less fragile and easier to audit.
        String js = buildFillScript(pendingPrompt);
        webView.evaluateJavascript(js, value -> {
            boolean success = "true".equals(value) || "\"true\"".equals(value);
            if (success) {
                promptFilled = true;
                statusView.setText(R.string.wkdeepseek_submitting);
                captureNativeResultBaseline();
                installReplyButtons();
                // 给 DeepSeek 的 React 输入状态留出更充分的稳定时间。
                // 每次都使用随机等待，避免填入后立即触发发送。
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(300, 600));
                return;
            }
            fillAttempts++;
            // A mapped conversation may have been deleted or may belong to another login. Retry
            // that route only once, then use bounded exponential backoff instead of a retry storm.
            if (!TextUtils.isEmpty(mappedConversationId) && fillAttempts == 3) {
                DeepSeekHistoryLog.log("CONVERSATION_MAPPING_STALE",
                        "id=" + shortConversationId(mappedConversationId));
                resetMappingAndPromptForFreshConversation("stale_mapping");
                fillAttempts = 0;
                conversationRouteCorrections = 0;
                loadConversationRoute(URL, "stale_mapping_home");
                return;
            }
            if (fillAttempts < MAX_FILL_ATTEMPTS) {
                handler.postDelayed(this::tryFillPrompt,
                        retryDelay(fillAttempts, 500, 4_000));
            } else {
                failAutomation(R.string.wkdeepseek_fill_failed, "fill_prompt");
            }
        });
    }

    private void submitPromptAutomatically() {
        if (promptSubmitted || submitReadyPending || submitClickPending || webView == null || !isAdded()) return;
        webView.evaluateJavascript(buildSendReadyProbeScript(), value -> {
            String result = cleanJsResult(value);
            if ("ready".equals(result)) {
                // 发送图标出现后也不立刻点击，再等待一段随机时间并重新确认。
                // 这样既能避免 React 刚完成重绘时的误触，也让发送节奏不会过快。
                submitReadyPending = true;
                handler.postDelayed(this::clickPromptSendButton, randomDelay(180, 360));
                return;
            }

            probeAndRecoverContextOverflow(recovered -> {
                if (recovered || !isAdded()) return;
                submitAttempts++;
                if (submitAttempts < MAX_SUBMIT_ATTEMPTS) {
                    handler.postDelayed(this::submitPromptAutomatically,
                            retryDelay(submitAttempts, 600, 5_000));
                } else {
                    failAutomation(R.string.wkdeepseek_submit_failed,
                            "send_button_not_ready:" + result);
                }
            });
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
                // 不在 click() 返回后立刻当成成功。先确认输入框已经清空、发送图标消失
                // 或页面进入生成状态，避免 React 尚未处理点击时误报“已发送”。
                submitClickPending = true;
                submitVerifyAttempts = 0;
                statusView.setText(R.string.wkdeepseek_submitting);
                handler.postDelayed(this::verifyPromptSubmission, randomDelay(250, 500));
                return;
            }

            // The page may disable the send button because the actual server-side context is
            // too long. Only an explicit webpage message is allowed to trigger context reduction.
            probeAndRecoverContextOverflow(recovered -> {
                if (recovered || !isAdded()) return;
                submitAttempts++;
                if (submitAttempts < MAX_SUBMIT_ATTEMPTS) {
                    handler.postDelayed(this::submitPromptAutomatically,
                            retryDelay(submitAttempts, 700, 5_000));
                } else {
                    failAutomation(R.string.wkdeepseek_submit_failed,
                            "send_click_failed:" + result);
                }
            });
        });
    }

    private void verifyPromptSubmission() {
        if (promptSubmitted || webView == null || !isAdded()) return;
        webView.evaluateJavascript(buildSubmitVerifyScript(), value -> {
            String result = cleanJsResult(value);
            if ("submitted".equals(result)) {
                submitClickPending = false;
                promptSubmitted = true;
                DeepSeekUsageGuard.recordSubmitted(getContext(), request);
                markContextSnapshotSubmitted();
                captureConversationFromCurrentUrl();
                handler.postDelayed(this::captureConversationFromCurrentUrl, 250);
                handler.postDelayed(this::captureConversationFromCurrentUrl, 800);
                handler.postDelayed(this::captureConversationFromCurrentUrl, 1800);
                handler.postDelayed(this::captureConversationFromCurrentUrl, 3500);
                statusView.setText(R.string.wkdeepseek_thinking);
                hideSubmittedPromptBubble();
                // Flip into answer phase before reinstalling the observer. DeepSeek may already have
                // created the streaming assistant container at this point; installing first would
                // incorrectly mark that new container as historical content.
                beginAnswerObservation();
                installReplyButtons();
                startNativeReplyPolling();
                return;
            }

            submitVerifyAttempts++;
            if (submitVerifyAttempts < MAX_SUBMIT_VERIFY_ATTEMPTS) {
                handler.postDelayed(this::verifyPromptSubmission,
                        retryDelay(submitVerifyAttempts, 500, 4_000));
                return;
            }

            // The click was not confirmed. Before a generic retry, check for an explicit
            // context-length error. No local shortening happens without that visible signal.
            submitClickPending = false;
            probeAndRecoverContextOverflow(recovered -> {
                if (recovered || !isAdded()) return;
                submitAttempts++;
                if (submitAttempts < MAX_SUBMIT_ATTEMPTS) {
                    handler.postDelayed(this::submitPromptAutomatically,
                            retryDelay(submitAttempts, 800, 5_000));
                } else {
                    failAutomation(R.string.wkdeepseek_submit_failed, "submit_not_confirmed");
                }
            });
        });
    }


    private void preparePromptTransportText() {
        if (request == null) {
            promptVisibleLabel = "正在处理，请稍候…";
        } else if (request.action == DeepSeekRequest.ACTION_TRANSLATE) {
            promptVisibleLabel = "正在翻译所选消息…";
        } else if (request.action == DeepSeekRequest.ACTION_POLISH) {
            promptVisibleLabel = "正在润色这段消息…";
        } else {
            promptVisibleLabel = "正在分析聊天并生成回复…";
        }
    }

    /**
     * The complete prompt is submitted through the normal composer. We intentionally do not hide
     * or rewrite the submitted user bubble because doing so requires fragile DOM manipulation.
     */
    private void hideSubmittedPromptBubble() {
        // No-op by design.
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

    /**
     * 自动提交只操作 DeepSeek 页面中与用户给出的 SVG path 完全匹配的发送按钮。
     * 不再使用“离输入框最近的按钮”、requestSubmit 或 Enter 兜底，避免误触。
     */
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

    private interface ContextOverflowCallback {
        void onResult(boolean recovered);
    }

    /**
     * Reads only visible DeepSeek error/status UI. Context is never shortened from a local estimate;
     * recovery starts only after the webpage itself explicitly says the input/context is too long.
     */
    private void probeAndRecoverContextOverflow(ContextOverflowCallback callback) {
        if (callback == null) return;
        if (contextOverflowRecoveryInProgress) {
            callback.onResult(true);
            return;
        }
        if (contextOverflowProbeInFlight || webView == null || !isAdded()) {
            callback.onResult(false);
            return;
        }
        contextOverflowProbeInFlight = true;
        String js = "(function(){try{" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;}" +
                "function text(el){return String((el&&(el.innerText||el.textContent||el.getAttribute('aria-label')||el.getAttribute('title')))||'').replace(/\\s+/g,' ').trim();}" +
                "var q='[role=alert],[role=status],[aria-live],dialog,[class*=toast],[class*=error],[class*=notice],[class*=warning],[class*=message]';" +
                "var nodes=Array.from(document.querySelectorAll(q)).filter(visible),parts=[];nodes.forEach(function(x){var t=text(x);if(t&&t.length<800)parts.push(t);});" +
                "var joined=parts.join(' | ');if(!joined){var b=text(document.body);joined=b.slice(Math.max(0,b.length-4000));}" +
                "var re=/(内容|输入|消息|文本|提示词|上下文).{0,18}(过长|太长|超出|超过|上限|限制)|(过长|太长|超出|超过).{0,18}(上下文|最大长度|token|令牌)|input.{0,12}too\\s*long|message.{0,12}too\\s*long|prompt.{0,12}too\\s*long|context.{0,18}(too\\s*long|length|limit|window|exceed)|maximum.{0,12}(context|token)|reduce.{0,18}(input|prompt|length)/i;" +
                "var m=joined.match(re);return m?JSON.stringify({tooLong:true,text:(m[0]||'').slice(0,160)}):JSON.stringify({tooLong:false,text:''});" +
                "}catch(e){return JSON.stringify({tooLong:false,text:''});}})();";
        try {
            webView.evaluateJavascript(js, value -> {
                contextOverflowProbeInFlight = false;
                boolean tooLong = false;
                String message = "";
                try {
                    String decoded = decodeJavascriptString(value);
                    JSONObject payload = new JSONObject(decoded);
                    tooLong = payload.optBoolean("tooLong", false);
                    message = payload.optString("text", "");
                } catch (Exception ignored) {
                }
                if (tooLong) {
                    callback.onResult(recoverFromExplicitContextOverflow(message));
                } else {
                    callback.onResult(false);
                }
            });
        } catch (Exception ignored) {
            contextOverflowProbeInFlight = false;
            callback.onResult(false);
        }
    }

    private boolean recoverFromExplicitContextOverflow(String webpageMessage) {
        if (contextOverflowRecoveryInProgress || request == null || closing) return true;
        if (TextUtils.isEmpty(fullContextSnapshot)) return false;
        if (contextOverflowRecoveryLevel >= 2) {
            String message = "DeepSeek 仍提示上下文过长，请关闭窗口后重新尝试";
            if (statusView != null) statusView.setText(message);
            notifyUser(message);
            DeepSeekHistoryLog.log("CONTEXT_OVERFLOW_STOP",
                    "level=" + contextOverflowRecoveryLevel + " page=" + safeLogText(webpageMessage));
            return true;
        }

        contextOverflowRecoveryInProgress = true;
        contextOverflowRecoveryLevel++;
        int divisor = contextOverflowRecoveryLevel == 1 ? 2 : 4;
        String reduced = reduceContextSnapshot(fullContextSnapshot, divisor);
        if (TextUtils.isEmpty(reduced) || TextUtils.equals(reduced, request.contextSnapshot)) {
            contextOverflowRecoveryInProgress = false;
            return false;
        }

        stopNativeReplyPolling("context_overflow_recovery");
        Context context = getContext();
        if (context != null) DeepSeekConversationStore.clear(context, request);
        mappedConversationId = "";
        conversationRouteTarget = "";
        conversationRouteLoading = false;
        conversationRouteCorrections = 0;
        forceFullContextForFreshConversation = false;
        contextPlanApplied = true;
        request.contextSnapshot = reduced;
        request.contextSnapshotCount = countSnapshotLines(reduced);
        request.contextSyncMode = contextOverflowRecoveryLevel == 1
                ? "overflow_half" : "overflow_quarter";
        submittedContextBaselineSnapshot = reduced;

        promptBuildGeneration++;
        pendingPrompt = "";
        promptFilled = false;
        promptSubmitted = false;
        promptVisibleLabel = "";
        fillAttempts = 0;
        submitAttempts = 0;
        submitVerifyAttempts = 0;
        submitReadyPending = false;
        submitClickPending = false;
        fallbackCopied = false;
        webUiPrepared = false;
        webUiPrepareInFlight = false;
        webUiPrepareAttempts = 0;
        nativeReplyScanAttempts = 0;
        nativeReplyStableCount = 0;
        nativeReplyLastCandidate = "";
        statusView.setText("DeepSeek 提示内容过长，正在缩短上下文后重试…");
        DeepSeekHistoryLog.log("CONTEXT_OVERFLOW_RECOVERY",
                "level=" + contextOverflowRecoveryLevel
                        + " original_chars=" + fullContextSnapshot.length()
                        + " reduced_chars=" + reduced.length()
                        + " reduced_lines=" + request.contextSnapshotCount
                        + " page=" + safeLogText(webpageMessage));

        loadMessagesAndBuildPrompt();
        loadConversationRoute(URL, "explicit_context_overflow_" + contextOverflowRecoveryLevel);
        handler.postDelayed(() -> contextOverflowRecoveryInProgress = false, 600L);
        return true;
    }

    private String reduceContextSnapshot(String source, int divisor) {
        if (TextUtils.isEmpty(source) || divisor <= 1) return source == null ? "" : source;
        String[] raw = source.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String item : raw) {
            String line = item == null ? "" : item.trim();
            if (!TextUtils.isEmpty(line)) lines.add(line);
        }
        if (lines.isEmpty()) return "";

        // Reduce by actual characters rather than only message count. This also handles a single
        // unusually long message while preserving the newest conversation first.
        int targetChars = Math.max(2_000, source.length() / divisor);
        List<String> kept = new ArrayList<>();
        int chars = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            int extra = line.length() + (kept.isEmpty() ? 0 : 1);
            if (chars + extra > targetChars && !kept.isEmpty()) break;
            if (line.length() > targetChars && kept.isEmpty()) {
                line = line.substring(Math.max(0, line.length() - targetChars));
            }
            kept.add(0, line);
            chars += line.length() + (kept.size() > 1 ? 1 : 0);
        }
        return TextUtils.join("\n", kept);
    }

    private int countSnapshotLines(String snapshot) {
        if (TextUtils.isEmpty(snapshot)) return 0;
        int count = 0;
        for (String line : snapshot.split("\\r?\\n")) {
            if (!TextUtils.isEmpty(line == null ? "" : line.trim())) count++;
        }
        return count;
    }

    private String safeLogText(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 160 ? clean.substring(0, 160) : clean;
    }

    private long retryDelay(int attempt, int baseMs, int maxMs) {
        int exponent = Math.max(0, Math.min(6, attempt - 1));
        long raw = (long) Math.max(1, baseMs) << exponent;
        long capped = Math.min(Math.max(baseMs, maxMs), raw);
        // Small jitter prevents synchronized UI retries after a network stall; it is not intended
        // to conceal automation or bypass platform controls.
        return capped + randomDelay(0, 250);
    }

    private void failAutomation(int messageRes, String reason) {
        long delay = DeepSeekUsageGuard.recordFailure(getContext(), reason);
        String message = getString(messageRes);
        if (delay > 0L) {
            long seconds = Math.max(1L, (delay + 999L) / 1000L);
            message = message + "，请 " + seconds + " 秒后重试";
        }
        if (statusView != null) statusView.setText(message);
        notifyUser(message);
        DeepSeekHistoryLog.log("AUTOMATION_STOPPED", "reason=" + reason + " backoff_ms=" + delay);
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

    /**
     * 提交完成后开启本轮回答观察。历史回答已在提交前标记，避免复用会话时给旧内容
     * 重复添加“发送”按钮。
     */
    private void beginAnswerObservation() {
        if (webView == null || !isAdded()) return;
        String js = "(function(){try{" +
                // Existing messages are marked when the result observer is installed before submit.
                // At this point a new assistant placeholder may already exist, so never mark current
                // message roots here or the streaming answer would be ignored permanently.
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
        // V12: results are read from the completed DeepSeek answer and rendered entirely with
        // Android native views. Do not inject buttons/cards into DeepSeek's React DOM.
    }

    private void captureNativeResultBaseline() {
        if (webView == null || request == null) return;
        String js = "(function(){try{" +
                "var selectors=['.ds-assistant-message-main-content','[data-message-author-role=\"assistant\"]','[class*=\"assistant-message\"]'];var all=[];" +
                "selectors.forEach(function(q){document.querySelectorAll(q).forEach(function(x){if(all.indexOf(x)<0)all.push(x);});});" +
                "all.sort(function(a,b){if(a===b)return 0;return (a.compareDocumentPosition(b)&Node.DOCUMENT_POSITION_FOLLOWING)?-1:1;});" +
                "window.__tsddNativeBaselineCount=all.length;window.__tsddNativeBaselineText=all.length?String(all[all.length-1].innerText||all[all.length-1].textContent||'').trim():'';" +
                "window.__tsddNativeBaselineReady=true;return String(all.length);" +
                "}catch(e){window.__tsddNativeBaselineReady=false;return 'error';}})();";
        try {
            webView.evaluateJavascript(js, value -> DeepSeekHistoryLog.log(
                    "NATIVE_RESULT_BASELINE", "count=" + cleanJsResult(value) + " ui=v12"));
        } catch (Exception error) {
            DeepSeekHistoryLog.log("NATIVE_RESULT_BASELINE_ERROR", error.getClass().getSimpleName());
        }
    }

    /**
     * Legacy entry point retained for source compatibility. V12 no longer injects a
     * MutationObserver or navigates to a custom URL. Android polls the final assistant
     * answer directly through evaluateJavascript and receives the JSON in its callback.
     */
    private void installNativeReplyOptionsBridge() {
        startNativeReplyPolling();
    }

    private void startNativeReplyPolling() {
        if (webView == null || request == null || closing || loginMode) {
            return;
        }
        nativeReplyPolling = true;
        nativeReplyScanInFlight = false;
        nativeReplyScanAttempts = 0;
        nativeReplyStableCount = 0;
        nativeReplyLastCandidate = "";
        nativeReplyLastScanState = "";
        nativeReplySignature = "";
        if (nativeReplyScroll != null) nativeReplyScroll.setVisibility(View.GONE);
        if (webView != null) webView.setVisibility(View.VISIBLE);
        handler.removeCallbacks(nativeReplyPollRunnable);
        handler.postDelayed(nativeReplyPollRunnable, 450);
        DeepSeekHistoryLog.log("NATIVE_RESULT_POLL_START", "ui=v12 action=" + request.action);
    }

    private void scheduleNextNativeReplyPoll(long delayMs) {
        if (!nativeReplyPolling || closing || webView == null || !isAdded()) return;
        handler.removeCallbacks(nativeReplyPollRunnable);
        handler.postDelayed(nativeReplyPollRunnable, Math.max(180L, delayMs));
    }

    private void pollNativeReplyOptions() {
        if (!nativeReplyPolling || nativeReplyScanInFlight || closing
                || webView == null || !isAdded()) {
            return;
        }
        nativeReplyScanInFlight = true;
        nativeReplyScanAttempts++;
        try {
            webView.evaluateJavascript(buildNativeReplyExtractScript(), value -> {
                nativeReplyScanInFlight = false;
                if (!nativeReplyPolling || closing || webView == null || !isAdded()) return;
                String decoded = decodeJavascriptString(value);
                if (TextUtils.isEmpty(decoded)) {
                    logNativeReplyScanState("empty", 0, false, decoded);
                    if (nativeReplyScanAttempts < 90) {
                        if (nativeReplyScanAttempts >= 4 && nativeReplyScanAttempts % 4 == 0) {
                            probeAndRecoverContextOverflow(recovered -> {
                                if (!recovered) scheduleNextNativeReplyPoll(520);
                            });
                        } else {
                            scheduleNextNativeReplyPoll(520);
                        }
                    } else stopNativeReplyPolling("empty_timeout");
                    return;
                }
                try {
                    JSONObject payload = new JSONObject(decoded);
                    String state = payload.optString("state", "unknown");
                    boolean generating = payload.optBoolean("generating", false);
                    String translation = payload.optString("translation", "").trim();
                    String analysis = limitCodePoints(payload.optString("analysis", "").trim(), 50);
                    JSONArray items = payload.optJSONArray("items");
                    if (items == null) items = new JSONArray();
                    int count = items.length();
                    logNativeReplyScanState(state, count, generating, decoded);

                    JSONObject stablePayload = new JSONObject();
                    stablePayload.put("translation", translation);
                    stablePayload.put("analysis", analysis);
                    stablePayload.put("items", items);
                    String candidate = stablePayload.toString();

                    boolean hasResult;
                    if (request.action == DeepSeekRequest.ACTION_TRANSLATE) {
                        hasResult = !TextUtils.isEmpty(translation);
                    } else {
                        // Reply suggestions are intentionally variable: DeepSeek may return 3-6
                        // options according to the amount of useful context. Wait for at least three
                        // completed choices, but still fall back after a while if the model disobeys.
                        int minimum = 3;
                        hasResult = count >= minimum
                                || (count > 0 && !generating && nativeReplyScanAttempts >= 14);
                    }

                    if (hasResult) {
                        if (TextUtils.equals(candidate, nativeReplyLastCandidate)) {
                            nativeReplyStableCount++;
                        } else {
                            nativeReplyLastCandidate = candidate;
                            nativeReplyStableCount = 1;
                        }
                        if (!generating && nativeReplyStableCount >= 2) {
                            nativeReplyPolling = false;
                            handler.removeCallbacks(nativeReplyPollRunnable);
                            showNativeAssistantResult(candidate);
                            return;
                        }
                    } else {
                        nativeReplyStableCount = 0;
                        nativeReplyLastCandidate = "";
                    }
                } catch (Exception parseError) {
                    DeepSeekHistoryLog.log("NATIVE_RESULT_SCAN_PARSE_ERROR",
                            parseError.getClass().getSimpleName()
                                    + " chars=" + decoded.length());
                }

                if (nativeReplyScanAttempts < 90) {
                    if (nativeReplyScanAttempts >= 4 && nativeReplyScanAttempts % 4 == 0) {
                        probeAndRecoverContextOverflow(recovered -> {
                            if (!recovered) scheduleNextNativeReplyPoll(520);
                        });
                    } else {
                        scheduleNextNativeReplyPoll(520);
                    }
                } else {
                    stopNativeReplyPolling("scan_timeout");
                }
            });
        } catch (Exception error) {
            nativeReplyScanInFlight = false;
            DeepSeekHistoryLog.log("NATIVE_RESULT_SCAN_EVAL_ERROR",
                    error.getClass().getSimpleName());
            if (nativeReplyScanAttempts < 90) scheduleNextNativeReplyPoll(650);
            else stopNativeReplyPolling("eval_timeout");
        }
    }

    private void logNativeReplyScanState(String state, int count, boolean generating,
                                         String rawResult) {
        String compactState = state + "/" + count + "/" + generating;
        if (!TextUtils.equals(compactState, nativeReplyLastScanState)
                || nativeReplyScanAttempts == 1
                || nativeReplyScanAttempts % 8 == 0) {
            nativeReplyLastScanState = compactState;
            DeepSeekHistoryLog.log("NATIVE_RESULT_SCAN",
                    "attempt=" + nativeReplyScanAttempts
                            + " state=" + state
                            + " count=" + count
                            + " generating=" + generating
                            + " stable=" + nativeReplyStableCount
                            + " chars=" + (rawResult == null ? 0 : rawResult.length())
                            + " ui=v12");
        }
    }

    private void stopNativeReplyPolling(String reason) {
        nativeReplyPolling = false;
        nativeReplyScanInFlight = false;
        handler.removeCallbacks(nativeReplyPollRunnable);
        DeepSeekHistoryLog.log("NATIVE_RESULT_POLL_STOP",
                "reason=" + reason + " attempts=" + nativeReplyScanAttempts + " ui=v12");
    }

    private String decodeJavascriptString(String value) {
        if (TextUtils.isEmpty(value) || "null".equals(value) || "undefined".equals(value)) {
            return "";
        }
        try {
            JSONArray wrapper = new JSONArray("[" + value + "]");
            return wrapper.optString(0, "");
        } catch (Exception ignored) {
            return cleanJsResult(value);
        }
    }

    private String buildNativeReplyExtractScript() {
        return "(function(){try{" +
                "function norm(v){return String(v||'').replace(/\\s+/g,' ').trim();}" +
                "function raw(v){return String(v||'').trim();}" +
                "function after(a,b){return !!(a&&b&&(a.compareDocumentPosition(b)&Node.DOCUMENT_POSITION_FOLLOWING));}" +
                "function roots(){var selectors=['.ds-assistant-message-main-content','[data-message-author-role=assistant]','[class*=assistant-message]'];var all=[];selectors.forEach(function(q){document.querySelectorAll(q).forEach(function(x){if(all.indexOf(x)<0)all.push(x);});});all.sort(function(a,b){if(a===b)return 0;return (a.compareDocumentPosition(b)&Node.DOCUMENT_POSITION_FOLLOWING)?-1:1;});var base=Number(window.__tsddNativeBaselineCount||0);if(window.__tsddNativeBaselineReady&&all.length>base)return all.slice(base);if(window.__tsddNativeBaselineReady&&all.length){var last=all[all.length-1],now=raw(last.innerText||last.textContent),before=raw(window.__tsddNativeBaselineText||'');if(now&&now!==before)return [last];return [];}return all;}" +
                "function generating(){return Array.from(document.querySelectorAll('button,[role=\\\"button\\\"]')).some(function(b){var t=norm((b.innerText||'')+' '+(b.getAttribute('aria-label')||'')+' '+(b.getAttribute('title')||'')).toLowerCase().replace(/\\s+/g,'');var s=getComputedStyle(b),r=b.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0&&/停止生成|停止|stopgenerating|stopgeneration/.test(t);});}" +
                "function localAfter(root,anchor,nextAnchor){var list=Array.from(root.querySelectorAll('[data-tsdd-back-translation],p,li'));for(var i=0;i<list.length;i++){var row=list[i];if(!after(anchor,row))continue;if(nextAnchor&&after(nextAnchor,row))break;var t=norm(row.innerText||row.textContent),m=t.match(/^(?:回译|我的母语译文|中文回译|本地显示)\\s*[：:]\\s*([\\s\\S]+)$/);if(m&&norm(m[1]))return norm(m[1]);}return '';}" +
                "function meta(root){var out={translation:'',analysis:''},rows=Array.from(root.querySelectorAll('p,li,h1,h2,h3'));for(var i=0;i<rows.length;i++){if(rows[i].closest('pre'))continue;var t=norm(rows[i].innerText||rows[i].textContent),m;if(!out.translation&&(m=t.match(/^(?:对方(?:最新)?消息(?:的)?(?:翻译|译文)|对方原话(?:翻译|译文)|消息翻译|译文)\\s*[：:]\\s*([\\s\\S]+)$/))){out.translation=norm(m[1]);continue;}if(!out.analysis&&(m=t.match(/^(?:意图(?:与|和|\\/)?情绪(?:分析)?|意图分析|情绪分析|分析)\\s*[：:]\\s*([\\s\\S]+)$/))){out.analysis=norm(m[1]);}}return out;}" +
                "function add(out,seen,text,local){text=raw(text);local=norm(local);if(!text)return;var key=text+'\\n'+local;if(seen[key])return;seen[key]=1;out.push({text:text,local:local});}" +
                "var rs=roots(),root=rs.length?rs[rs.length-1]:null,state='ok';if(!root&&!window.__tsddNativeBaselineReady){var anchors=Array.from(document.querySelectorAll('[data-tsdd-reply-card],pre'));var last=anchors.length?anchors[anchors.length-1]:null;if(last){root=last.closest('article,[data-message-author-role=assistant],main')||last.parentElement;state='fallback_root';}}if(!root)return JSON.stringify({state:'no_root',generating:generating(),translation:'',analysis:'',items:[]});var info=meta(root),out=[],seen={};" +
                "var cards=Array.from(root.querySelectorAll('[data-tsdd-reply-card]'));cards.forEach(function(card,i){var body=card.querySelector('[data-tsdd-reply-text]'),text=raw(body?body.innerText||body.textContent:card.innerText||card.textContent),next=i+1<cards.length?cards[i+1]:null;add(out,seen,text,localAfter(root,card,next));});" +
                "if(!out.length){var pres=Array.from(root.querySelectorAll('pre'));pres.forEach(function(pre,i){var code=pre.querySelector('code'),text=raw(code?code.innerText||code.textContent:pre.innerText||pre.textContent);text=text.replace(/^reply\\s*/i,'').trim();var next=i+1<pres.length?pres[i+1]:null;if(!text||/^(translation|translate|译文)$/i.test(text))return;add(out,seen,text,localAfter(root,pre,next));});}" +
                "if(!out.length){var rows=Array.from(root.querySelectorAll('p,li'));for(var j=0;j<rows.length;j++){var line=norm(rows[j].innerText||rows[j].textContent),m=line.match(/^(?:回复|原文|回复原文|对方语言|发送内容)\\s*[：:]\\s*([\\s\\S]+)$/);if(!m||!norm(m[1]))continue;add(out,seen,norm(m[1]),localAfter(root,rows[j],null));}}" +
                "return JSON.stringify({state:state,generating:generating(),translation:info.translation,analysis:info.analysis,items:out});" +
                "}catch(e){return JSON.stringify({state:'error',generating:false,error:String(e&&e.message||e),translation:'',analysis:'',items:[]});}})();";
    }

    private void handlePluginUrl(Uri uri) {
        if ("options".equals(uri.getHost())) {
            showNativeReplyOptions(uri.getQueryParameter("data"));
            return;
        }
        if (!"result".equals(uri.getHost())) return;
        String text = uri.getQueryParameter("text");
        String localDisplayText = uri.getQueryParameter("local");
        String mode = uri.getQueryParameter("mode");
        if (TextUtils.isEmpty(text) || text.length() > 12000) return;
        final String cleanText = text.trim();
        final String cleanLocalDisplayText = TextUtils.isEmpty(localDisplayText)
                ? "" : localDisplayText.trim();
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
            // The user reviewed this option and explicitly tapped “发送”.
            deliverReply(cleanText, cleanLocalDisplayText, true);
            return;
        }
        deliverReply(cleanText, cleanLocalDisplayText, false);
    }

    private void showNativeReplyOptions(String rawJson) {
        if (TextUtils.isEmpty(rawJson)) return;
        try {
            JSONArray array = new JSONArray(rawJson);
            JSONObject payload = new JSONObject();
            payload.put("translation", "");
            payload.put("analysis", "");
            payload.put("items", array);
            showNativeAssistantResult(payload.toString());
        } catch (Exception e) {
            DeepSeekHistoryLog.log("NATIVE_RESULT_PARSE_ERROR", e.getClass().getSimpleName());
        }
    }

    private void showNativeAssistantResult(String rawJson) {
        if (TextUtils.isEmpty(rawJson) || rawJson.length() > 40000 || !isAdded()) return;
        try {
            JSONObject payload = new JSONObject(rawJson);
            String translation = payload.optString("translation", "").trim();
            String analysis = limitCodePoints(payload.optString("analysis", "").trim(), 50);
            JSONArray items = payload.optJSONArray("items");
            if (items == null) items = new JSONArray();

            String signature = payload.toString();
            if (TextUtils.equals(signature, nativeReplySignature)
                    && nativeReplyScroll != null
                    && nativeReplyScroll.getVisibility() == View.VISIBLE) {
                return;
            }
            nativeReplySignature = signature;

            if (request != null && request.action == DeepSeekRequest.ACTION_TRANSLATE) {
                if (TextUtils.isEmpty(translation)) return;
                renderNativeTranslationResult(translation, analysis);
            } else {
                if (items.length() == 0) return;
                renderNativeReplyOptions(items, translation, analysis);
            }
        } catch (Exception e) {
            DeepSeekHistoryLog.log("NATIVE_RESULT_PARSE_ERROR", e.getClass().getSimpleName());
        }
    }

    private void prepareNativeResultPage(String titleText) {
        nativeReplyList.removeAllViews();
        TextView title = new TextView(requireContext());
        title.setText(titleText);
        title.setTextColor(Color.rgb(24, 31, 42));
        title.setTextSize(19);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(2), 0, 0, dp(7));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        nativeReplyList.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addNativeContextCard(String translation, String analysis) {
        if (TextUtils.isEmpty(translation) && TextUtils.isEmpty(analysis)) return;
        Context context = requireContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(239, 250, 246));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(190, 229, 214));
        card.setBackground(background);

        if (!TextUtils.isEmpty(translation)) {
            TextView label = new TextView(context);
            label.setText(request != null && request.action == DeepSeekRequest.ACTION_TRANSLATE
                    ? "译文" : "对方消息译文");
            label.setTextColor(Color.rgb(43, 113, 88));
            label.setTextSize(12);
            label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
            label.setPadding(0, 0, 0, dp(3));
            card.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView value = new TextView(context);
            value.setText(translation);
            value.setTextColor(Color.rgb(28, 69, 55));
            value.setTextSize(15);
            value.setLineSpacing(dp(2), 1f);
            value.setTextIsSelectable(true);
            card.addView(value, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (!TextUtils.isEmpty(analysis)) {
            TextView insight = new TextView(context);
            insight.setText("意图/情绪：" + analysis);
            insight.setTextColor(Color.rgb(80, 99, 92));
            insight.setTextSize(13);
            insight.setLineSpacing(dp(1), 1f);
            insight.setPadding(0, TextUtils.isEmpty(translation) ? 0 : dp(6), 0, 0);
            card.addView(insight, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        nativeReplyList.addView(card, params);
    }

    private void renderNativeReplyOptions(JSONArray array, String translation, String analysis) {
        if (nativeReplyList == null || nativeReplyScroll == null || webView == null) return;
        String titleText = request != null && request.action == DeepSeekRequest.ACTION_POLISH
                ? "润色建议" : "聊天回复建议";
        prepareNativeResultPage(titleText);
        if (request == null || request.action == DeepSeekRequest.ACTION_REPLY) {
            addNativeContextCard(translation, analysis);
        }

        int maxCount = request != null && request.action == DeepSeekRequest.ACTION_REPLY ? 6 : 3;
        int rendered = 0;
        for (int i = 0; i < array.length() && rendered < maxCount; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String replyText = item.optString("text", "").trim();
            String local = item.optString("local", "").trim();
            if (TextUtils.isEmpty(replyText)) continue;
            rendered++;
            addNativeReplyCard(rendered, replyText, local);
        }
        if (rendered == 0) return;

        webView.setVisibility(View.GONE);
        nativeReplyScroll.setVisibility(View.VISIBLE);
        nativeReplyScroll.scrollTo(0, 0);
        DeepSeekHistoryLog.log("NATIVE_RESULT_SHOWN", "action="
                + (request == null ? -1 : request.action)
                + " count=" + rendered + " ui=v12");
    }

    private void addNativeReplyCard(int index, String text, String local) {
        Context context = requireContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.rgb(247, 249, 255));
        cardBg.setCornerRadius(dp(12));
        cardBg.setStroke(dp(1), Color.rgb(215, 224, 249));
        card.setBackground(cardBg);

        TextView replyView = new TextView(context);
        replyView.setText(text);
        replyView.setTextColor(Color.rgb(27, 34, 47));
        replyView.setTextSize(16);
        replyView.setLineSpacing(dp(2), 1f);
        replyView.setTextIsSelectable(true);
        card.addView(replyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!TextUtils.isEmpty(local)) {
            TextView localView = new TextView(context);
            localView.setText("回译：" + local);
            localView.setTextColor(Color.rgb(40, 91, 74));
            localView.setTextSize(13);
            localView.setLineSpacing(dp(1), 1f);
            localView.setPadding(0, dp(6), 0, 0);
            card.addView(localView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView send = createNativePrimaryButton(context, "发送");
        send.setOnClickListener(v -> {
            DeepSeekHistoryLog.log("NATIVE_REPLY_SEND", "ui=v12 index=" + index
                    + " text_chars=" + text.length()
                    + " local_chars=" + local.length());
            deliverReply(text, local, true);
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        sendParams.topMargin = dp(8);
        card.addView(send, sendParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        nativeReplyList.addView(card, cardParams);
    }

    private void renderNativeTranslationResult(String translation, String analysis) {
        if (nativeReplyList == null || nativeReplyScroll == null || webView == null) return;
        prepareNativeResultPage("消息翻译");
        addNativeContextCard(translation, analysis);

        TextView use = createNativePrimaryButton(requireContext(), "显示译文");
        use.setOnClickListener(v -> deliverTranslation(translation));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        params.bottomMargin = dp(8);
        nativeReplyList.addView(use, params);

        webView.setVisibility(View.GONE);
        nativeReplyScroll.setVisibility(View.VISIBLE);
        nativeReplyScroll.scrollTo(0, 0);
        DeepSeekHistoryLog.log("NATIVE_RESULT_SHOWN", "action=2 count=1 ui=v12");
    }

    private TextView createNativePrimaryButton(Context context, String label) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(11, 143, 104));
        background.setCornerRadius(dp(20));
        button.setBackground(background);
        return button;
    }

    private void deliverTranslation(String translation) {
        if (TextUtils.isEmpty(translation)) return;
        pendingTranslationText = translation.trim();
        pendingTranslationDelivery = true;
        dismissAssistant();
    }

    private String limitCodePoints(String value, int maxCodePoints) {
        if (TextUtils.isEmpty(value) || maxCodePoints <= 0) return "";
        String trimmed = value.trim();
        int count = trimmed.codePointCount(0, trimmed.length());
        if (count <= maxCodePoints) return trimmed;
        int end = trimmed.offsetByCodePoints(0, maxCodePoints);
        return trimmed.substring(0, end).trim();
    }

    private void deliverReply(String text, String localDisplayText, boolean sendNow) {
        if (TextUtils.isEmpty(text)) return;
        // Chat input and remote message always contain only the reply in the peer's language.
        // The back-translation is local display metadata and must never be sent or auto-copied.
        pendingReplyText = text.trim();
        pendingReplyLocalDisplayText = TextUtils.isEmpty(localDisplayText)
                ? "" : localDisplayText.trim();
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
        // 给输入法一个很短的时间从 Dialog Window 脱离，避免关闭后继续压缩聊天页。
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
        out.contextLimit = 0;
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
            final String localDisplayText = pendingReplyLocalDisplayText;
            final boolean sendNow = pendingReplySendNow;
            pendingReplyDelivery = false;
            FragmentActivity activity = getActivity();
            if (activity != null && !activity.isFinishing()) {
                activity.getWindow().getDecorView().postDelayed(
                        () -> callback.onReply(text, localDisplayText, sendNow), 60);
            } else {
                callback.onReply(text, localDisplayText, sendNow);
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
        nativeReplyPolling = false;
        nativeReplyScanInFlight = false;
        handler.removeCallbacks(nativeReplyPollRunnable);
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
