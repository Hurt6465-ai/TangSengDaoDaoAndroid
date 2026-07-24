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
    private boolean pendingReplyDelivery;
    private String pendingReplyText = "";
    private boolean pendingReplySendNow;
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
        dialog.setArguments(args);
        return dialog;
    }

    void setReplyCallback(DeepSeekAssistant.ReplyCallback callback) {
        this.replyCallback = callback;
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
        return buildContent(requireContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;

        // 普通助手使用“真实的固定高度 Window”，而不是全屏透明 Window 里再放一个
        // 60% 高度的面板。全屏透明 Window 会在部分系统上接管整屏 Insets/IME 布局，
        // 关闭后 Activity 的 PanelSwitchLayout 可能保留错误高度，表现为历史消息消失。
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        int windowHeight = loginMode
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : normalPanelHeight;
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = ViewGroup.LayoutParams.MATCH_PARENT;
        attributes.height = windowHeight;
        attributes.gravity = Gravity.BOTTOM;
        attributes.dimAmount = 0f;
        window.setAttributes(attributes);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, windowHeight);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(Color.WHITE);
        }
    }

    private View buildContent(Context context) {
        dialogRoot = new FrameLayout(context);
        dialogRoot.setBackgroundColor(Color.TRANSPARENT);
        dialogRoot.setClickable(true);
        dialogRoot.setFocusable(true);

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
        if (!loginMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 顶部原生工具条移除后，WebView 会直接贴到面板顶部；启用轮廓裁剪，
            // 防止网页白底盖住圆角。
            contentPanel.setClipToOutline(true);
        }

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        // 登录页全屏；普通助手的 Dialog Window 本身就是固定 60% 高度。
        // WebView 只在这个 Window 内滚动，顶部聊天历史不再被透明 Dialog 覆盖。
        normalPanelHeight = loginMode ? screenHeight : Math.max(dp(360), Math.round(screenHeight * 0.60f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
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
            statusView.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        webView = new WebView(context);
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
                if (progressBar != null && loginMode) {
                    progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
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
                statusView.setText("DeepSeek 安全连接失败");
                notifyUser("DeepSeek 安全连接失败");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    statusView.setText("DeepSeek 页面加载失败，请检查网络后重试");
                    notifyUser("DeepSeek 页面加载失败，请检查网络后重试");
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
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
        statusView.setText("DeepSeek 已登录，聊天助手已开启");
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

                // 点击模式或关闭开关后，等待 React 完成重绘再复查。最多复查 6 次，
                // 找不到控件时按最佳努力继续，避免网页改版后整个助手不可用。
                if ("changed".equals(result) && webUiPrepareAttempts < 6) {
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
                "function selected(el){if(!el)return false;var c=String(el.className||'').toLowerCase();" +
                "if(/(^|[ _-])(selected|active|checked)([ _-]|$)/.test(c))return true;" +
                "if(el.getAttribute('aria-checked')==='true'||el.getAttribute('aria-pressed')==='true'||el.getAttribute('aria-selected')==='true')return true;" +
                "var ds=String(el.getAttribute('data-state')||'').toLowerCase();if(ds==='checked'||ds==='selected'||ds==='active'||ds==='on')return true;" +
                "return el.getAttribute('data-selected')==='true'||el.getAttribute('data-active')==='true';}" +
                "function click(el){if(!el||!visible(el)||el.disabled||el.getAttribute('aria-disabled')==='true')return false;" +
                "try{el.focus({preventScroll:true});}catch(e){try{el.focus();}catch(e2){}}" +
                "['pointerdown','mousedown','mouseup'].forEach(function(type){try{el.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,view:window}));}catch(e){}});" +
                "try{el.click();}catch(e){try{el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));}catch(e2){return false;}}return true;}" +
                "function pool(){return Array.from(document.querySelectorAll('[role=\"radio\"],button,[role=\"button\"],[role=\"switch\"],[role=\"checkbox\"],[role=\"menuitem\"],[role=\"option\"],div[data-model-type],a')).filter(visible);}" +
                "function hideDownloads(){var re=/(下载(\\s*deepseek)?(\\s*应用|\\s*app)|打开(\\s*deepseek)?(\\s*应用|\\s*app)|在\\s*app\\s*中打开|download\\s*(the\\s*)?app|get\\s*(the\\s*)?app|open\\s*in\\s*app)/i;" +
                "pool().forEach(function(el){if(re.test(txt(el))){el.style.setProperty('display','none','important');el.style.setProperty('visibility','hidden','important');el.setAttribute('aria-hidden','true');}});}" +
                "function enforce(allowMenu){hideDownloads();var list=pool(),changed=false;" +
                "var expert=list.find(function(el){var dmt=String(el.getAttribute('data-model-type')||'').toLowerCase();var t=txt(el).replace(/\\s+/g,'').toLowerCase();return dmt==='expert'||t==='专家模式'||t==='专家'||t==='expert'||t.indexOf('expertmode')>=0||t.indexOf('专家模式')>=0;});" +
                "if(expert){if(!selected(expert)&&click(expert))changed=true;}" +
                "else if(allowMenu){var trigger=list.find(function(el){var t=txt(el).replace(/\\s+/g,'').toLowerCase();return (el.getAttribute('aria-haspopup')||'')!==''&&(/快速模式|普通模式|标准模式|常规模式|quickmode|normalmode|standardmode|^模式$|^mode$/.test(t));});" +
                "var now=Date.now();if(trigger&&(!window.__tsddModeMenuTryAt||now-window.__tsddModeMenuTryAt>1800)){window.__tsddModeMenuTryAt=now;if(click(trigger))changed=true;}}" +
                "function disable(re){var el=list.find(function(x){return re.test(txt(x))&&selected(x);});if(el&&click(el))changed=true;}" +
                "disable(/(^|\\s)(深度思考|深度思索|思考|deepthink|deep\\s*think|thinking|reasoning)(\\s|$)/i);" +
                "disable(/(^|\\s)(智能搜索|联网搜索|网络搜索|搜索|smart\\s*search|web\\s*search|search|browse)(\\s|$)/i);" +
                "return changed;}" +
                "window.__tsddEnforcePrefs=enforce;" +
                "if(!window.__tsddPrefObserver){window.__tsddPrefObserver=true;var timer=null;new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(function(){try{if(window.__tsddEnforcePrefs)window.__tsddEnforcePrefs(false);}catch(e){}},180);}).observe(document.documentElement,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['class','aria-checked','aria-pressed','aria-selected','data-state','data-selected','data-active']});}" +
                "return enforce(true)?'changed':'stable';" +
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

    private void installReplyButtons() {
        if (webView == null) return;
        String fallbackMode = request != null && request.action == DeepSeekRequest.ACTION_TRANSLATE ? "copy" : "use";
        String js = "(function(){" +
                "if(window.__tsddDeepSeekInstalled)return;window.__tsddDeepSeekInstalled=true;" +
                "var fallbackMode='" + fallbackMode + "';" +
                "function go(mode,text){if(!text)return;location.href='tsdd-deepseek://result?mode='+mode+'&text='+encodeURIComponent(text);}" +
                "function button(label,mode,code){var b=document.createElement('button');b.type='button';b.textContent=label;b.dataset.tsddAction='1';" +
                "b.style.cssText='border:0;border-radius:16px;padding:7px 12px;background:#edf3ff;color:#295eea;font-size:12px;font-weight:600;margin-left:8px';" +
                "b.onclick=function(e){e.preventDefault();e.stopPropagation();go(mode,(code.innerText||'').trim());};return b;}" +
                "function add(){document.querySelectorAll('pre code').forEach(function(code){" +
                "if(code.dataset.tsddReady==='1')return;var pre=code.closest('pre');if(!pre)return;" +
                "var cls=(code.className||'').toLowerCase();var mode=cls.indexOf('translate')>=0?'copy':(cls.indexOf('reply')>=0?'use':fallbackMode);" +
                "code.dataset.tsddReady='1';var box=document.createElement('div');box.dataset.tsddReplyBar='1';" +
                "box.style.cssText='display:flex;justify-content:flex-end;align-items:center;padding:8px 2px 12px 2px';" +
                "if(mode==='copy'){box.appendChild(button('复制译文','copy',code));}" +
                "else{box.appendChild(button('填入聊天','use',code));box.appendChild(button('直接发送','send',code));}" +
                "if(pre.parentNode)pre.parentNode.insertBefore(box,pre.nextSibling);});}" +
                "var timer=null;new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(add,450);}).observe(document.documentElement,{childList:true,subtree:true,characterData:true});add();" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void handlePluginUrl(Uri uri) {
        if (!"result".equals(uri.getHost())) return;
        String text = uri.getQueryParameter("text");
        String mode = uri.getQueryParameter("mode");
        if (TextUtils.isEmpty(text) || text.length() > 4000) return;
        final String cleanText = text.trim();
        if ("copy".equals(mode)) {
            copyText(cleanText);
            Toast.makeText(requireContext(), R.string.wkdeepseek_translation_copied, Toast.LENGTH_SHORT).show();
            return;
        }

        // 用户点击“直接发送”本身就是明确确认，不再额外弹一次确认框。
        // 回调由聊天页走原有 sendIV 发送流程，不绕过回复、编辑和陌生人限制。
        copyText(cleanText);
        pendingReplyText = cleanText;
        pendingReplySendNow = "send".equals(mode);
        pendingReplyDelivery = true;
        if (!pendingReplySendNow) {
            Toast.makeText(requireContext(), R.string.wkdeepseek_reply_used, Toast.LENGTH_SHORT).show();
        }
        // 先关闭网页和输入法，再把结果交给聊天页，避免两个 Window 同时争夺 IME/焦点。
        dismissAssistant();
    }

    private void dismissAssistant() {
        if (closing) return;
        closing = true;
        hideWebKeyboard();
        // 给输入法一个很短的时间从 Dialog Window 脱离，避免关闭后继续压缩聊天页。
        handler.postDelayed(() -> {
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
        DeepSeekContactStore.apply(requireContext(), out);
        return out;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        hideWebKeyboard();
        super.onDismiss(dialog);

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
                        () -> callback.onReply(text, sendNow), 280);
            } else {
                callback.onReply(text, sendNow);
            }
        }
    }

    @Override
    public void onDestroyView() {
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
    }
}
