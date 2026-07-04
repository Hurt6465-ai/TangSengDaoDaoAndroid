package com.chat.userscript;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.PermissionRequest;
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
    private static final int REQ_RECORD_AUDIO = 7301;
    private String startupPrompt = "";
    private boolean startupPromptInjected = false;
    private PermissionRequest pendingPermissionRequest;

    public UserScriptController(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.store = UserScriptStore.get(activity);
    }

    public void setStartupPrompt(String prompt) {
        this.startupPrompt = prompt == null ? "" : prompt;
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
        webView.addJavascriptInterface(new SafeGmBridge(activity, webView, secret), BRIDGE_NAME);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                handleWebPermissionRequest(request);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                return shouldBlock(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldBlock(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                startupPromptInjected = false;
                exposeStartupPrompt();
                injectNativeSpeechPolyfill(url);
                inject(url, "document-start");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                exposeStartupPrompt();
                injectNativeSpeechPolyfill(url);
                inject(url, "document-end");
                injectStartupPromptButton();
                mainHandler.postDelayed(() -> {
                    injectNativeSpeechPolyfill(view.getUrl());
                    inject(view.getUrl(), "document-idle");
                    injectStartupPromptButton();
                }, 650L);
            }
        });
    }


    public boolean handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_RECORD_AUDIO) return false;
        PermissionRequest request = pendingPermissionRequest;
        pendingPermissionRequest = null;
        if (request != null && grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            }
        } else if (request != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            request.deny();
        }
        return true;
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (request == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        String origin = request.getOrigin() == null ? "" : request.getOrigin().toString();
        if (!AiWebPolicy.isScriptHostAllowed(origin)) {
            request.deny();
            return;
        }
        boolean wantsAudio = false;
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) wantsAudio = true;
        }
        if (!wantsAudio) {
            request.deny();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionRequest = request;
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        } else {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        }
    }

    public void loadUrl(String url) {
        if (!AiWebPolicy.isNavigationAllowed(url)) {
            Toast.makeText(activity, activity.getString(R.string.script_allowed_web_only), Toast.LENGTH_SHORT).show();
            return;
        }
        webView.loadUrl(url);
    }

    public void reinjectCurrentPage() {
        String url = webView.getUrl();
        exposeStartupPrompt();
        injectNativeSpeechPolyfill(url);
        inject(url, "document-end");
        injectStartupPromptButton();
        mainHandler.postDelayed(() -> {
            injectNativeSpeechPolyfill(webView.getUrl());
            inject(webView.getUrl(), "document-idle");
        }, 650L);
    }

    private boolean shouldBlock(String url) {
        if (AiWebPolicy.isNavigationAllowed(url)) return false;
        Toast.makeText(activity, activity.getString(R.string.script_blocked_external, AiWebPolicy.hostOf(url)), Toast.LENGTH_SHORT).show();
        return true;
    }

    private void injectNativeSpeechPolyfill(String url) {
        if (webView == null || url == null || !AiWebPolicy.isScriptHostAllowed(url)) return;

        String js = "(function(){"
                + "if(window.__TS_DD_NATIVE_SPEECH_POLYFILL__)return;"
                + "var bridge=window.TsddNativeSpeech||window.TsddVoiceBridge||window.TsddVoice;"
                + "if(!bridge||typeof bridge.startSpeech!=='function')return;"
                + "var originalSpeech=window.SpeechRecognition||window.webkitSpeechRecognition;"
                + "if(originalSpeech&&!window.__TS_DD_FORCE_NATIVE_SPEECH__){window.__TS_DD_NATIVE_SPEECH_AVAILABLE__=true;return;}"
                + "window.__TS_DD_NATIVE_SPEECH_POLYFILL__=true;"
                + "window.__TS_DD_ORIGINAL_SPEECH_RECOGNITION__=window.SpeechRecognition||null;"
                + "window.__TS_DD_ORIGINAL_WEBKIT_SPEECH_RECOGNITION__=window.webkitSpeechRecognition||null;"
                + "var active=null;"
                + "function makeResult(text,isFinal,confidence){"
                + "var item=[{transcript:String(text||''),confidence:confidence||0}];"
                + "item.isFinal=!!isFinal;"
                + "var arr=[];"
                + "arr[0]=item;"
                + "arr.length=1;"
                + "return arr;"
                + "}"
                + "function NativeSpeechRecognition(){"
                + "this.lang='zh-CN';"
                + "this.continuous=false;"
                + "this.interimResults=false;"
                + "this.maxAlternatives=1;"
                + "this.onstart=null;"
                + "this.onaudiostart=null;"
                + "this.onsoundstart=null;"
                + "this.onspeechstart=null;"
                + "this.onspeechend=null;"
                + "this.onsoundend=null;"
                + "this.onaudioend=null;"
                + "this.onresult=null;"
                + "this.onerror=null;"
                + "this.onend=null;"
                + "this._started=false;"
                + "}"
                + "NativeSpeechRecognition.prototype.start=function(){"
                + "active=this;"
                + "this._started=true;"
                + "try{bridge.startSpeech(String(this.lang||'zh-CN'));}catch(e){"
                + "this._started=false;"
                + "if(this.onerror)this.onerror({error:'native-start-failed',message:String(e&&e.message||e)});"
                + "if(this.onend)this.onend({type:'end'});"
                + "}"
                + "};"
                + "NativeSpeechRecognition.prototype.stop=function(){"
                + "try{bridge.stopSpeech();}catch(e){}"
                + "};"
                + "NativeSpeechRecognition.prototype.abort=function(){"
                + "try{bridge.cancelSpeech?bridge.cancelSpeech():bridge.stopSpeech();}catch(e){}"
                + "};"
                + "window.__TS_DD_NATIVE_SPEECH_EVENT__=function(payload){"
                + "var p=payload;"
                + "if(typeof p==='string'){try{p=JSON.parse(p);}catch(e){p={type:'error',message:p};}}"
                + "p=p||{};"
                + "var rec=active;"
                + "if(!rec)return;"
                + "var type=p.type||'';"
                + "if(type==='start'){"
                + "if(rec.onstart)rec.onstart({type:'start'});"
                + "if(rec.onaudiostart)rec.onaudiostart({type:'audiostart'});"
                + "return;"
                + "}"
                + "if(type==='partial'||type==='final'){"
                + "var text=String(p.text||'');"
                + "if(!text)return;"
                + "if(type==='partial'&&rec.interimResults===false)return;"
                + "var ev={resultIndex:0,results:makeResult(text,type==='final',p.confidence||0)};"
                + "if(rec.onresult)rec.onresult(ev);"
                + "return;"
                + "}"
                + "if(type==='error'){"
                + "if(rec.onerror)rec.onerror({error:p.error||'native-error',message:p.message||p.error||'语音识别失败'});"
                + "return;"
                + "}"
                + "if(type==='end'){"
                + "rec._started=false;"
                + "if(rec.onspeechend)rec.onspeechend({type:'speechend'});"
                + "if(rec.onsoundend)rec.onsoundend({type:'soundend'});"
                + "if(rec.onaudioend)rec.onaudioend({type:'audioend'});"
                + "if(rec.onend)rec.onend({type:'end'});"
                + "if(active===rec)active=null;"
                + "}"
                + "};"
                + "window.SpeechRecognition=NativeSpeechRecognition;"
                + "window.webkitSpeechRecognition=NativeSpeechRecognition;"
                + "})();";

        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void inject(String url, String runAt) {
        if (webView == null || url == null || !AiWebPolicy.isScriptHostAllowed(url)) return;
        List<UserScript> scripts = store.getRunnableScripts(url, runAt);
        for (UserScript script : scripts) evaluate(script);
    }

    private void evaluate(UserScript script) {
        if (script == null || script.code == null) return;
        String js = buildWrappedScript(script);
        webView.evaluateJavascript(js, value -> {
        });
    }

    private void exposeStartupPrompt() {
        if (startupPrompt == null || startupPrompt.length() == 0) return;
        String js = "window.__TS_DD_START_PROMPT__=" + JSONObject.quote(startupPrompt) + ";";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void injectStartupPromptButton() {
        if (startupPrompt == null || startupPrompt.length() == 0 || startupPromptInjected) return;
        startupPromptInjected = true;
        String prompt = JSONObject.quote(startupPrompt);
        String js = "(function(){" +
                "window.__TS_DD_START_PROMPT__=" + prompt + ";" +
                "if(document.getElementById('tsdd-start-prompt-btn'))return;" +
                "var st=document.createElement('style');st.textContent='#tsdd-start-prompt-btn{position:fixed;right:16px;bottom:156px;z-index:2147483647;border:0;border-radius:999px;padding:10px 14px;background:#16a34a;color:#fff;font-weight:700;box-shadow:0 8px 24px rgba(22,163,74,.25)}';document.documentElement.appendChild(st);" +
                "function fill(t){var el=document.querySelector('textarea, input[type=text], [contenteditable=true], [role=textbox]');if(!el)return false;el.focus();if('value'in el){el.value=t;}else{el.textContent=t;}el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:t}));el.dispatchEvent(new Event('change',{bubbles:true}));return true;}" +
                "var b=document.createElement('button');b.id='tsdd-start-prompt-btn';b.textContent='使用场景';b.onclick=function(){if(!fill(window.__TS_DD_START_PROMPT__||'')){navigator.clipboard&&navigator.clipboard.writeText(window.__TS_DD_START_PROMPT__||'');alert('已复制场景 prompt，请粘贴到输入框');}};document.documentElement.appendChild(b);" +
                "})();";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private String buildWrappedScript(UserScript script) {
        String scriptId = JSONObject.quote(script.id);
        String scriptName = JSONObject.quote(script.name == null ? "" : script.name);
        String source = JSONObject.quote(script.code + "\n//# sourceURL=tsdd-userscript-" + script.id + ".user.js");
        String secretValue = JSONObject.quote(secret);
        String grants = new org.json.JSONArray(script.grants).toString();
        boolean networkAllowed = script.networkAllowed;
        return "(function(){\n" +
                "var __scriptId=" + scriptId + ";\n" +
                "window.__TS_DD_USER_SCRIPT_RAN__=window.__TS_DD_USER_SCRIPT_RAN__||{};\n" +
                "if(window.__TS_DD_USER_SCRIPT_RAN__[__scriptId]){return;}\n" +
                "window.__TS_DD_USER_SCRIPT_RAN__[__scriptId]=true;\n" +
                "var __secret=" + secretValue + ";\n" +
                "var __scriptName=" + scriptName + ";\n" +
                "var __grants=" + grants + ";\n" +
                "var __networkAllowed=" + networkAllowed + ";\n" +
                "window.__TS_DD_GM_XHR_CALLBACKS__=window.__TS_DD_GM_XHR_CALLBACKS__||{};\n" +
                "window.__TS_DD_GM_XHR_DONE__=window.__TS_DD_GM_XHR_DONE__||function(id,res){var cb=window.__TS_DD_GM_XHR_CALLBACKS__[id];if(!cb)return;delete window.__TS_DD_GM_XHR_CALLBACKS__[id];try{if(res&&res.ok){cb.onload&&cb.onload(res);}else{cb.onerror&&cb.onerror(res);}}catch(e){console.error(e);}};\n" +
                "function __hasGrant(n){return __grants.indexOf(n)>=0||__grants.indexOf('GM.'+n.replace('GM_',''))>=0||__grants.indexOf('GM_'+n.replace('GM.',''))>=0;}\n" +
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
                "var GM_xmlhttpRequest=function(details){if(!__hasGrant('GM_xmlhttpRequest')){console.warn('GM_xmlhttpRequest grant missing');return {abort:function(){}};}if(!__networkAllowed){console.warn('GM_xmlhttpRequest disabled by TangSeng permission');details&&details.onerror&&details.onerror({status:0,statusText:'disabled'});return {abort:function(){}};}details=details||{};var id='xhr_'+Date.now()+'_'+Math.random().toString(16).slice(2);window.__TS_DD_GM_XHR_CALLBACKS__[id]=details;var req={method:details.method||'GET',url:details.url||'',headers:details.headers||{},data:details.data||'',timeout:details.timeout||15000};" + BRIDGE_NAME + ".xmlHttpRequest(__secret,__scriptId,id,JSON.stringify(req));return {abort:function(){delete window.__TS_DD_GM_XHR_CALLBACKS__[id];}};};\n" +
                "var GM={getValue:function(k,d){return Promise.resolve(GM_getValue(k,d));},setValue:function(k,v){return Promise.resolve(GM_setValue(k,v));},deleteValue:function(k){return Promise.resolve(GM_deleteValue(k));},listValues:function(){return Promise.resolve(GM_listValues());},addStyle:GM_addStyle,log:GM_log,notification:GM_notification,setClipboard:GM_setClipboard,xmlHttpRequest:GM_xmlhttpRequest,xmlhttpRequest:GM_xmlhttpRequest};\n" +
                "var unsafeWindow=window;\n" +
                "try{eval(" + source + ");}catch(e){console.error('[TangSeng userscript] '+__scriptName,e);}\n" +
                "})();";
    }
}
