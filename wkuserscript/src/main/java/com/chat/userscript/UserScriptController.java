package com.chat.userscript;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.chat.userscript.model.UserScript;

import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

public class UserScriptController {
    private static final String BRIDGE_NAME = "__TS_DD_GM_BRIDGE__";
    private final Activity activity;
    private final WebView webView;
    private final UserScriptStore store;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String secret = UUID.randomUUID().toString().replace("-", "");

    public UserScriptController(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.store = UserScriptStore.get(activity);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void attach() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        webView.addJavascriptInterface(new SafeGmBridge(activity, secret), BRIDGE_NAME);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                return shouldBlock(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldBlock(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                inject(url, "document-start");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                inject(url, "document-end");
                mainHandler.postDelayed(() -> inject(view.getUrl(), "document-idle"), 650L);
            }
        });
    }

    public void loadUrl(String url) {
        if (!AiWebPolicy.isNavigationAllowed(url)) {
            Toast.makeText(activity, "只允许打开 DeepSeek / 千问相关 HTTPS 网页", Toast.LENGTH_SHORT).show();
            return;
        }
        webView.loadUrl(url);
    }

    public void reinjectCurrentPage() {
        String url = webView.getUrl();
        inject(url, "document-end");
        mainHandler.postDelayed(() -> inject(webView.getUrl(), "document-idle"), 650L);
    }

    private boolean shouldBlock(WebView view, String url) {
        if (AiWebPolicy.isNavigationAllowed(url)) return false;
        Toast.makeText(activity, "已阻止外部网页：" + AiWebPolicy.hostOf(url), Toast.LENGTH_SHORT).show();
        return true;
    }

    private void inject(String url, String runAt) {
        if (webView == null || url == null || !AiWebPolicy.isScriptHostAllowed(url)) return;
        List<UserScript> scripts = store.getRunnableScripts(url, runAt);
        for (UserScript script : scripts) {
            evaluate(script);
        }
    }

    private void evaluate(UserScript script) {
        if (script == null || script.code == null) return;
        String js = buildWrappedScript(script);
        webView.evaluateJavascript(js, value -> {
        });
    }

    private String buildWrappedScript(UserScript script) {
        String scriptId = JSONObject.quote(script.id);
        String scriptName = JSONObject.quote(script.name == null ? "" : script.name);
        String source = JSONObject.quote(script.code + "\n//# sourceURL=tsdd-userscript-" + script.id + ".user.js");
        String secretValue = JSONObject.quote(secret);
        String grants = new org.json.JSONArray(script.grants).toString();
        return "(function(){\n" +
                "var __scriptId=" + scriptId + ";\n" +
                "window.__TS_DD_USER_SCRIPT_RAN__=window.__TS_DD_USER_SCRIPT_RAN__||{};\n" +
                "if(window.__TS_DD_USER_SCRIPT_RAN__[__scriptId]){return;}\n" +
                "window.__TS_DD_USER_SCRIPT_RAN__[__scriptId]=true;\n" +
                "var __secret=" + secretValue + ";\n" +
                "var __scriptName=" + scriptName + ";\n" +
                "var __grants=" + grants + ";\n" +
                "function __hasGrant(n){return __grants.indexOf(n)>=0||__grants.indexOf('GM.'+n.replace('GM_',''))>=0;}\n" +
                "function __parse(v,d){try{return JSON.parse(v);}catch(e){return d;}}\n" +
                "function __json(v){return v===undefined?'null':JSON.stringify(v);}\n" +
                "var GM_getValue=function(key,def){if(!__hasGrant('GM_getValue'))return def;return __parse(" + BRIDGE_NAME + ".getValue(__secret,__scriptId,String(key),__json(def)),def);};\n" +
                "var GM_setValue=function(key,val){if(!__hasGrant('GM_setValue'))return false;return " + BRIDGE_NAME + ".setValue(__secret,__scriptId,String(key),__json(val));};\n" +
                "var GM_deleteValue=function(key){if(!__hasGrant('GM_deleteValue'))return false;return " + BRIDGE_NAME + ".deleteValue(__secret,__scriptId,String(key));};\n" +
                "var GM_listValues=function(){if(!__hasGrant('GM_listValues'))return [];return __parse(" + BRIDGE_NAME + ".listValues(__secret,__scriptId),[]);};\n" +
                "var GM_addStyle=function(css){if(!__hasGrant('GM_addStyle'))return null;var s=document.createElement('style');s.textContent=String(css||'');(document.head||document.documentElement).appendChild(s);return s;};\n" +
                "var GM_log=function(msg){" + BRIDGE_NAME + ".log(__secret,__scriptId,String(msg));};\n" +
                "var GM_notification=function(msg){if(__hasGrant('GM_notification'))" + BRIDGE_NAME + ".notification(__secret,__scriptId,String(msg));};\n" +
                "var GM_setClipboard=function(text){if(!__hasGrant('GM_setClipboard'))return false;return " + BRIDGE_NAME + ".setClipboard(__secret,__scriptId,String(text||''));};\n" +
                "var GM_xmlhttpRequest=function(){console.warn('GM_xmlhttpRequest is disabled in TangSeng stage-3 userscript sandbox.');};\n" +
                "var GM={getValue:function(k,d){return Promise.resolve(GM_getValue(k,d));},setValue:function(k,v){return Promise.resolve(GM_setValue(k,v));},deleteValue:function(k){return Promise.resolve(GM_deleteValue(k));},listValues:function(){return Promise.resolve(GM_listValues());},addStyle:GM_addStyle,log:GM_log,notification:GM_notification,setClipboard:GM_setClipboard,xmlHttpRequest:GM_xmlhttpRequest,xmlhttpRequest:GM_xmlhttpRequest};\n" +
                "var unsafeWindow=window;\n" +
                "try{eval(" + source + ");}catch(e){console.error('[TangSeng userscript] '+__scriptName,e);}\n" +
                "})();";
    }
}
