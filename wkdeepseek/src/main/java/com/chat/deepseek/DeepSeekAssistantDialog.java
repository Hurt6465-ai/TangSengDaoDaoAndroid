package com.chat.deepseek;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONObject;

import java.util.Locale;


public class DeepSeekAssistantDialog extends BottomSheetDialogFragment {
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

    private WebView webView;
    private TextView statusView;
    private ProgressBar progressBar;
    private boolean loginMode;
    private boolean promptPrepared;
    private boolean promptFilled;
    private int fillAttempts;
    private boolean fallbackCopied;
    private String pendingPrompt = "";
    private DeepSeekRequest request;
    private DeepSeekAssistant.ReplyCallback replyCallback;
    private DeepSeekAssistant.StateCallback stateCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loginConfirmed;
    private boolean loginProbeInFlight;
    private int loginWaitAttempts;
    private final Runnable loginProbeRunnable = this::runLoginProbe;

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

    @Override
    public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) return;
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        ViewGroup.LayoutParams lp = sheet.getLayoutParams();
        lp.height = loginMode ? ViewGroup.LayoutParams.MATCH_PARENT : Math.round(screenHeight * 0.86f);
        sheet.setLayoutParams(lp);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setSkipCollapsed(loginMode);
        behavior.setFitToContents(false);
        behavior.setHalfExpandedRatio(0.58f);
        behavior.setExpandedOffset(dp(18));
        behavior.setState(loginMode ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_HALF_EXPANDED);
    }

    private View buildContent(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(8), dp(8), dp(8));

        statusView = new TextView(context);
        statusView.setText(loginMode ? R.string.wkdeepseek_need_login : R.string.wkdeepseek_connecting);
        statusView.setTextColor(Color.rgb(36, 42, 52));
        statusView.setTextSize(15);
        statusView.setSingleLine(false);
        toolbar.addView(statusView, new LinearLayout.LayoutParams(0, dp(46), 1f));

        TextView close = toolbarButton(context, loginMode ? getString(R.string.wkdeepseek_done) : getString(R.string.wkdeepseek_close));
        close.setOnClickListener(v -> {
            if (loginMode && !loginConfirmed) {
                verifyLoginBeforeClose();
            } else {
                dismissAllowingStateLoss();
            }
        });
        toolbar.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        webView = new WebView(context);
        configureWebView(webView);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!isOnline(context)) {
            statusView.setText(R.string.wkdeepseek_no_network);
            progressBar.setVisibility(View.GONE);
        } else {
            webView.loadUrl(URL);
            if (!loginMode) preparePrompt();
        }
        return root;
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
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                // DeepSeek 登录后经常使用 SPA 路由，页面不会再次触发 onPageFinished。
                // 在页面主体基本可用时主动探测输入框，避免已经登录却一直无法开启。
                if (loginMode && newProgress >= 80) {
                    scheduleLoginProbe(250);
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
                progressBar.setVisibility(View.GONE);
                if (loginMode) {
                    scheduleLoginProbe(150);
                } else {
                    installReplyButtons();
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
                    tryFillPrompt();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                statusView.setText("DeepSeek 安全连接失败");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) statusView.setText("DeepSeek 页面加载失败，请检查网络后重试");
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                statusView.setText("DeepSeek 页面已被系统回收，请重新打开");
                view.destroy();
                dismissAllowingStateLoss();
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
                dismissAllowingStateLoss();
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
                return;
            }
            if (request.action == DeepSeekRequest.ACTION_TRANSLATE && TextUtils.isEmpty(result.targetMessage)) {
                statusView.setText("没有找到对方可翻译的消息");
                return;
            }
            if (request.action == DeepSeekRequest.ACTION_POLISH && TextUtils.isEmpty(request.draft)) {
                statusView.setText("请先在聊天输入框写好需要润色的内容");
                return;
            }
            try {
                pendingPrompt = DeepSeekPromptBuilder.build(requireContext(), request, result);
                tryFillPrompt();
            } catch (Exception e) {
                statusView.setText("生成提示词失败");
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
                }
                return;
            }
            loginWaitAttempts = 0;
            fillPromptNow();
        });
    }

    private void fillPromptNow() {
        if (promptFilled || webView == null || TextUtils.isEmpty(pendingPrompt)) return;
        String js = buildFillScript(pendingPrompt);
        webView.evaluateJavascript(js, value -> {
            boolean success = "true".equals(value) || "\"true\"".equals(value);
            if (success) {
                promptFilled = true;
                statusView.setText(R.string.wkdeepseek_filled);
                installReplyButtons();
                return;
            }
            fillAttempts++;
            if (fillAttempts < 7) {
                handler.postDelayed(this::tryFillPrompt, 900);
            } else if (!fallbackCopied && isAdded()) {
                fallbackCopied = true;
                copyText(pendingPrompt);
                statusView.setText(R.string.wkdeepseek_fill_failed);
            }
        });
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
                "function button(label,mode,code){var b=document.createElement('button');b.type='button';b.textContent=label;" +
                "b.style.cssText='border:0;border-radius:16px;padding:6px 10px;background:rgba(255,255,255,.96);color:#295eea;font-size:12px;box-shadow:0 2px 10px rgba(0,0,0,.12);margin-left:6px';" +
                "b.onclick=function(e){e.preventDefault();e.stopPropagation();var t=(code.innerText||'').trim();if(t)location.href='tsdd-deepseek://result?mode='+mode+'&text='+encodeURIComponent(t);};return b;}" +
                "function add(){document.querySelectorAll('pre code').forEach(function(code){" +
                "var pre=code.closest('pre');if(!pre||pre.dataset.tsddReady==='1')return;" +
                "var cls=(code.className||'').toLowerCase();var mode=cls.indexOf('translate')>=0?'copy':(cls.indexOf('reply')>=0?'use':fallbackMode);" +
                "pre.dataset.tsddReady='1';pre.style.position='relative';" +
                "var box=document.createElement('div');box.style.cssText='position:absolute;right:8px;top:8px;z-index:9999;display:flex;align-items:center';" +
                "if(mode==='copy'){box.appendChild(button('复制译文','copy',code));}" +
                "else{box.appendChild(button('填入','use',code));box.appendChild(button('发送','send',code));}" +
                "pre.appendChild(box);});}" +
                "var timer=null;new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(add,1500);}).observe(document.documentElement,{childList:true,subtree:true,characterData:true});add();" +
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
        if ("send".equals(mode)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.wkdeepseek_send_confirm)
                    .setMessage(cleanText)
                    .setNegativeButton(R.string.wkdeepseek_cancel, null)
                    .setPositiveButton(R.string.wkdeepseek_send, (dialog, which) -> {
                        copyText(cleanText);
                        if (replyCallback != null) replyCallback.onReply(cleanText, true);
                        dismissAllowingStateLoss();
                    })
                    .show();
            return;
        }
        copyText(cleanText);
        if (replyCallback != null) replyCallback.onReply(cleanText, false);
        Toast.makeText(requireContext(), R.string.wkdeepseek_reply_used, Toast.LENGTH_SHORT).show();
        dismissAllowingStateLoss();
    }

    private void copyText(String text) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("DeepSeek", text));
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
    public void onDestroyView() {
        handler.removeCallbacks(loginProbeRunnable);
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
