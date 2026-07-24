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
        if (!loginMode) request = requestFrom(args);
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

        // 普通助手不需要用户在 DeepSeek 输入框继续打字。让透明覆盖 Window 与输入法
        // 完全隔离，避免它改变唐僧聊天页 PanelSwitchLayout/RecyclerView 的高度。
        // 登录页仍需输入账号密码，因此保持正常的输入法交互。
        if (loginMode) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = ViewGroup.LayoutParams.MATCH_PARENT;
        attributes.height = ViewGroup.LayoutParams.MATCH_PARENT;
        attributes.gravity = loginMode ? Gravity.CENTER : Gravity.BOTTOM;
        attributes.dimAmount = 0f;
        window.setAttributes(attributes);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setSoftInputMode((loginMode
                ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
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
        configureWebView(webView);
        contentPanel.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!isOnline(context)) {
            statusView.setText(R.string.wkdeepseek_no_network);
            progressBar.setVisibility(View.GONE);
            notifyUser(getString(R.string.wkdeepseek_no_network));
        } else {
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
        DeepSeekMessageLoader.load(request, result -> handler.post(() -> {
            if (!isAdded()) return;
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

    private void scheduleWebUiPreparation(long delayMs) {
        if (loginMode || webView == null || promptFilled) return;
        handler.removeCallbacks(webUiPrepareRunnable);
        handler.postDelayed(webUiPrepareRunnable, Math.max(0L, delayMs));
    }

    /**
     * 在填入提示词前整理 DeepSeek 网页状态：优先专家模式、关闭已开启的思考/搜索，
     * 并持续隐藏“下载应用”入口。这里仅操作可见网页控件，不拦截私有接口。
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

                // 专家模式可能比输入框晚渲染，或先要展开模式菜单。允许多轮复查，
                // 但达到上限后仍继续提交，避免网页改版导致助手完全不可用。
                if (("changed".equals(result) || "retry".equals(result))
                        && webUiPrepareAttempts < 12) {
                    handler.postDelayed(() -> applyWebUiPreferences(continueFill), randomDelay(280, 460));
                    return;
                }
                webUiPrepared = true;
                if (continueFill || !TextUtils.isEmpty(pendingPrompt)) {
                    handler.postDelayed(this::tryFillPrompt, 80);
                }
            });
        } catch (Exception ignored) {
            webUiPrepareInFlight = false;
            webUiPrepared = true;
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
                // DeepSeek currently renders the expert choice as:
                // <div class="dfb78875"><span class="_321831d">专家模式</span></div>
                // It is not a semantic button, so locate this exact compact row explicitly. Do not
                // restore broad div/span scanning because a large React container can share the text.
                "function pool(){var q='button,a,label,[role=\"button\"],[role=\"radio\"],[role=\"switch\"],[role=\"checkbox\"],[role=\"menuitem\"],[role=\"option\"],[role=\"tab\"],[data-model-type],[tabindex]:not([tabindex=\"-1\"])';" +
                "return Array.from(document.querySelectorAll(q)).filter(function(el){var t=txt(el);return visible(el)&&t.length>0&&t.length<80;});}" +
                "function exactExpert(){var labels=Array.from(document.querySelectorAll('div.dfb78875>span._321831d,span._321831d')).filter(function(el){return norm(el)==='专家模式';}),rows=[];" +
                "labels.forEach(function(label){var row=label.closest('div.dfb78875');if(!row||!visible(row))return;var r=row.getBoundingClientRect();if(r.width<36||r.height<22||r.height>96||r.width>window.innerWidth*0.96)return;if(rows.indexOf(row)<0)rows.push(row);});" +
                "if(!rows.length)return null;var option=null;rows.some(function(row){var p=row.parentElement,depth=0;while(p&&p!==document.body&&depth++<4){var modeRows=Array.from(p.querySelectorAll('div.dfb78875')).filter(visible);var modeTexts=modeRows.map(norm),hasOtherMode=modeTexts.some(function(t){return t==='快速模式'||t==='快速'||t==='普通模式'||t==='标准模式'||t==='常规模式'||t==='思考模式'||t==='深度思考';});if(modeRows.length>=2&&modeTexts.indexOf('专家模式')>=0&&hasOtherMode){option=row;return true;}p=p.parentElement;}return false;});" +
                "return {el:option||rows[0],isOption:!!option};}" +
                "function hideDownloads(list){var re=/(下载(\\s*deepseek)?(\\s*应用|\\s*app)|打开(\\s*deepseek)?(\\s*应用|\\s*app)|在\\s*app\\s*中打开|download\\s*(the\\s*)?app|get\\s*(the\\s*)?app|open\\s*in\\s*app)/i;" +
                "list.forEach(function(el){if(re.test(txt(el))){var r=el.getBoundingClientRect();if(r.height<120&&r.width<window.innerWidth*0.95){el.style.setProperty('display','none','important');el.setAttribute('aria-hidden','true');}}});}" +
                "function enforce(allowMenu){var list=pool(),changed=false,retry=false,now=Date.now();hideDownloads(list);" +
                "var exact=exactExpert(),expert=exact?exact.el:list.find(function(el){var d=String(el.getAttribute('data-model-type')||'').toLowerCase(),t=norm(el);return d==='expert'||t==='专家模式'||t==='专家'||t==='expert'||t==='expertmode';});" +
                "var quick=list.find(function(el){var t=norm(el);return t==='快速模式'||t==='快速'||t==='quickmode'||t==='quick';});" +
                "if(expert&&!window.__tsddExpertDone){if(selected(expert)){window.__tsddExpertDone=true;}else if(!window.__tsddExpertClickAt||now-window.__tsddExpertClickAt>900){if(click(expert)){window.__tsddExpertClickAt=now;window.__tsddExpertClickCount=(window.__tsddExpertClickCount||0)+1;changed=true;if(exact&&exact.isOption)window.__tsddExpertDone=true;else retry=true;}}if(!window.__tsddExpertDone&&(window.__tsddExpertClickCount||0)<5)retry=true;}" +
                "else if(!window.__tsddExpertDone&&allowMenu){retry=true;var trigger=list.find(function(el){var t=norm(el);return (el.getAttribute('aria-haspopup')||'')!==''&&(/快速模式|普通模式|标准模式|常规模式|quickmode|normalmode|standardmode|^模式$|^mode$/.test(t));});if(trigger&&(!window.__tsddModeMenuTryAt||now-window.__tsddModeMenuTryAt>1200)){window.__tsddModeMenuTryAt=now;if(click(trigger))changed=true;}}" +
                "function disable(re){var el=list.find(function(x){return re.test(txt(x))&&selected(x);});if(el&&click(el))changed=true;}" +
                "disable(/(^|\\s)(深度思考|深度思索|思考|deepthink|deep\\s*think|thinking|reasoning)(\\s|$)/i);" +
                "disable(/(^|\\s)(智能搜索|联网搜索|网络搜索|搜索|smart\\s*search|web\\s*search|search|browse)(\\s|$)/i);" +
                "return changed?'changed':(retry?'retry':'stable');}" +
                "window.__tsddEnforcePrefs=enforce;" +
                "if(!window.__tsddPrefObserver){window.__tsddPrefObserver=true;var timer=null;new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(function(){try{if(window.__tsddEnforcePrefs)window.__tsddEnforcePrefs(false);}catch(e){}},240);}).observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','aria-checked','aria-pressed','aria-selected','data-state','data-selected','data-active']});}" +
                "return enforce(true);" +
                "}catch(e){return 'error';}})();";
    }

    private void fillPromptNow() {
        if (promptFilled || webView == null || TextUtils.isEmpty(pendingPrompt)) return;
        String js = buildFillScript(pendingPrompt);
        webView.evaluateJavascript(js, value -> {
            boolean success = "true".equals(value) || "\"true\"".equals(value);
            if (success) {
                promptFilled = true;
                statusView.setText(R.string.wkdeepseek_submitting);
                installReplyButtons();
                // 给 DeepSeek 的 React 输入状态留出更充分的稳定时间。
                // 每次都使用随机等待，避免填入后立即触发发送。
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(1400, 2800));
                return;
            }
            fillAttempts++;
            if (fillAttempts < 7) {
                handler.postDelayed(this::tryFillPrompt, 900);
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
                // 发送图标出现后也不立刻点击，再等待一段随机时间并重新确认。
                // 这样既能避免 React 刚完成重绘时的误触，也让发送节奏不会过快。
                submitReadyPending = true;
                handler.postDelayed(this::clickPromptSendButton, randomDelay(650, 1450));
                return;
            }

            submitAttempts++;
            if (submitAttempts < 12) {
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(700, 1350));
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
                // 不在 click() 返回后立刻当成成功。先确认输入框已经清空、发送图标消失
                // 或页面进入生成状态，避免 React 尚未处理点击时误报“已发送”。
                submitClickPending = true;
                submitVerifyAttempts = 0;
                statusView.setText(R.string.wkdeepseek_submitting);
                handler.postDelayed(this::verifyPromptSubmission, randomDelay(700, 1250));
                return;
            }

            // 随机等待期间按钮可能被 React 重绘。此时重新探测，绝不盲目连点。
            submitAttempts++;
            if (submitAttempts < 12) {
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(800, 1500));
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
                statusView.setText(R.string.wkdeepseek_thinking);
                installReplyButtons();
                beginAnswerObservation();
                return;
            }

            submitVerifyAttempts++;
            if (submitVerifyAttempts < 5) {
                handler.postDelayed(this::verifyPromptSubmission, randomDelay(650, 1150));
                return;
            }

            // 点击后页面仍没有进入生成状态，允许重新探测一次发送图标。
            // 只有同一个精确上箭头再次可见、可点击时才会再次 click。
            submitClickPending = false;
            submitAttempts++;
            if (submitAttempts < 12) {
                handler.postDelayed(this::submitPromptAutomatically, randomDelay(900, 1650));
            } else {
                statusView.setText(R.string.wkdeepseek_submit_failed);
                notifyUser(getString(R.string.wkdeepseek_submit_failed));
            }
        });
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
     * 提交完成后才开始识别回答代码块。提交前以及刚提交时出现的代码块属于我们发送
     * 给 DeepSeek 的提示词，必须标记为忽略，否则会在用户提示气泡下面重复叠加
     * “填入聊天/直接发送”按钮。
     */
    private void beginAnswerObservation() {
        if (webView == null || !isAdded()) return;
        String js = "(function(){try{" +
                "function clearPromptBlocks(){document.querySelectorAll('pre code').forEach(function(code){code.dataset.tsddIgnore='1';var pre=code.closest('pre');if(pre)pre.dataset.tsddIgnore='1';});document.querySelectorAll('[data-tsdd-reply-bar]').forEach(function(x){x.remove();});}" +
                "window.__tsddEnforcePrefs=null;window.__tsddAnswerPhase=true;window.__tsddAnswerReady=false;window.__tsddAnswerNotBefore=Date.now()+900;clearPromptBlocks();" +
                "setTimeout(clearPromptBlocks,220);setTimeout(clearPromptBlocks,600);setTimeout(function(){window.__tsddAnswerReady=true;if(window.__tsddDeepSeekAdd)window.__tsddDeepSeekAdd();},920);return 'ok';" +
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
                "if(window.__tsddDeepSeekInstalled)return;window.__tsddDeepSeekInstalled=true;" +
                "var fallbackMode='" + fallbackMode + "';" +
                "function go(mode,text,local){if(!text)return;var u='tsdd-deepseek://result?mode='+mode+'&text='+encodeURIComponent(text);if(local)u+='&local='+encodeURIComponent(local);location.href=u;}" +
                "function localTextFor(pre){try{var n=pre?pre.nextElementSibling:null,steps=0;while(n&&steps++<6){if(n.tagName==='PRE'||/^H[1-6]$/.test(n.tagName))break;var t=(n.innerText||n.textContent||'').trim();var m=t.match(/(?:【?本地显示】?|我的母语翻译)\\s*[：:]\\s*([\\s\\S]+)/);if(m){var v=(m[1]||'').trim().split(/\\n(?=【|#{1,6}\\s)/)[0].trim();if(v)return v;}n=n.nextElementSibling;}return '';}catch(e){return '';}}" +
                "function button(label,mode,code,pre){var b=document.createElement('button');b.type='button';b.textContent=label;b.dataset.tsddAction='1';" +
                "b.style.cssText='border:0;border-radius:16px;padding:7px 12px;background:#edf3ff;color:#295eea;font-size:12px;font-weight:600;margin-left:8px';" +
                "b.onclick=function(e){e.preventDefault();e.stopPropagation();var local=(mode==='use'||mode==='send')?localTextFor(pre):'';go(mode,(code.innerText||'').trim(),local);};return b;}" +
                "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0;}" +
                "function composerRoot(){var boxes=Array.from(document.querySelectorAll('textarea,[contenteditable=\"true\"],[role=\"textbox\"]')).filter(visible);boxes.sort(function(a,b){return a.getBoundingClientRect().top-b.getBoundingClientRect().top;});var input=boxes.length?boxes[boxes.length-1]:null;if(!input)return null;var best=input,cur=input,n=0,maxH=Math.min(320,Math.max(150,window.innerHeight*0.38));while(cur.parentElement&&n++<6){var p=cur.parentElement;if(p===document.body||p===document.documentElement)break;var r=p.getBoundingClientRect();if(r.width<window.innerWidth*0.45||r.height<=0||r.height>maxH||r.bottom<window.innerHeight*0.55)break;if(p.querySelectorAll('article,pre,[data-message-id],[data-testid*=message]').length>2)break;best=p;cur=p;}return best;}" +
                "function hideComposer(){var root=composerRoot();if(!root||root===document.body||root===document.documentElement)return;var r=root.getBoundingClientRect(),maxH=Math.min(320,Math.max(150,window.innerHeight*0.38));if(r.height>maxH||r.width>window.innerWidth*1.01)return;root.dataset.tsddComposerHidden='1';root.style.setProperty('display','none','important');root.style.setProperty('visibility','hidden','important');root.style.setProperty('pointer-events','none','important');document.documentElement.style.setProperty('scroll-padding-bottom','8px','important');}" +
                "function keepComposerHidden(){hideComposer();setTimeout(hideComposer,250);setTimeout(hideComposer,850);setTimeout(hideComposer,1700);}" +
                "function cleanup(){var seen={};document.querySelectorAll('[data-tsdd-reply-bar]').forEach(function(bar){var owner=bar.dataset.tsddOwner||'';if(!owner||seen[owner]||!document.querySelector('pre[data-tsdd-pre-id=\"'+owner+'\"]'))bar.remove();else seen[owner]=true;});}" +
                "function add(){cleanup();if(!window.__tsddAnswerPhase||!window.__tsddAnswerReady||Date.now()<(window.__tsddAnswerNotBefore||0))return;var added=false,lastPre=null;document.querySelectorAll('pre code').forEach(function(code){" +
                "var pre=code.closest('pre');if(!pre||pre.dataset.tsddIgnore==='1'||code.dataset.tsddIgnore==='1')return;if(code.dataset.tsddReady==='1')return;" +
                "var cls=(code.className||'').toLowerCase(),raw=(code.innerText||'').trim();var looksProfile=cls.indexOf('profile')>=0||(raw.charAt(0)==='{'&&raw.indexOf('\"interaction_state\"')>=0);var mode=looksProfile?'profile':(cls.indexOf('translate')>=0?fallbackMode:(cls.indexOf('reply')>=0?'use':fallbackMode));" +
                "if(!pre.dataset.tsddPreId){window.__tsddPreSeq=(window.__tsddPreSeq||0)+1;pre.dataset.tsddPreId='p'+window.__tsddPreSeq;}var owner=pre.dataset.tsddPreId;if(document.querySelector('[data-tsdd-reply-bar][data-tsdd-owner=\"'+owner+'\"]')){code.dataset.tsddReady='1';return;}" +
                "code.dataset.tsddReady='1';var box=document.createElement('div');box.dataset.tsddReplyBar='1';box.dataset.tsddOwner=owner;" +
                "box.style.cssText='display:flex;justify-content:flex-end;align-items:center;padding:8px 2px 12px 2px';" +
                "if(mode==='profile'){box.appendChild(button('更新联系人记录','profile',code,pre));}" +
                "else if(mode==='copy'){box.appendChild(button('复制译文','copy',code,pre));}" +
                "else if(mode==='translate_use'){box.appendChild(button('显示译文','translate_use',code,pre));box.appendChild(button('复制译文','copy',code,pre));}" +
                "else{box.appendChild(button('填入聊天','use',code,pre));box.appendChild(button('直接发送','send',code,pre));}" +
                "if(pre.parentNode){pre.parentNode.insertBefore(box,pre.nextSibling);added=true;lastPre=pre;}});if(added||window.__tsddResultReady){window.__tsddResultReady=true;keepComposerHidden();if(lastPre)setTimeout(function(){try{lastPre.scrollIntoView({behavior:'smooth',block:'end'});}catch(e){lastPre.scrollIntoView(false);}},180);}}" +
                "window.__tsddDeepSeekAdd=add;var timer=null;if(!window.__tsddResultObserver){window.__tsddResultObserver=true;new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(add,520);}).observe(document.documentElement,{childList:true,subtree:true,characterData:true});}add();" +
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
            Toast.makeText(requireContext(), R.string.wkdeepseek_translation_copied, Toast.LENGTH_SHORT).show();
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
            new android.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.wkdeepseek_send_confirm)
                    .setMessage(cleanLocalDisplayText)
                    .setNegativeButton(R.string.wkdeepseek_cancel, null)
                    .setPositiveButton(R.string.wkdeepseek_send,
                            (dialog, which) -> deliverReply(cleanText, cleanLocalDisplayText, true))
                    .show();
            return;
        }
        deliverReply(cleanText, cleanLocalDisplayText, false);
    }

    private void deliverReply(String text, String localDisplayText, boolean sendNow) {
        if (TextUtils.isEmpty(text)) return;
        copyText(text);
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
        // 给输入法一个很短的时间从 Dialog Window 脱离，避免关闭后继续压缩聊天页。
        handler.postDelayed(() -> {
            DeepSeekHistoryLog.log("DIALOG_DISMISS_EXECUTE", "reason=" + dismissReason
                    + " added=" + isAdded());
            if (isAdded()) {
                dismissAllowingStateLoss();
            }
        }, 90);
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
                        () -> callback.onReply(text, sendNow), 280);
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
