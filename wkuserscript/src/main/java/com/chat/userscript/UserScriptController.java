package com.chat.userscript;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
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
    private String scriptMode = "";
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

    public void setScriptMode(String mode) {
        this.scriptMode = normalizeMode(mode);
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
                exposeScriptMode(url);
                exposeStartupPrompt();
                injectNativeSpeechPolyfill();
                inject(url, "document-start");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                exposeScriptMode(url);
                exposeStartupPrompt();
                injectNativeSpeechPolyfill();
                inject(url, "document-end");
                injectStartupPromptButton();
                mainHandler.postDelayed(() -> {
                    exposeScriptMode(view.getUrl());
                    injectNativeSpeechPolyfill();
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
        exposeScriptMode(url);
        exposeStartupPrompt();
        injectNativeSpeechPolyfill();
        inject(url, "document-end");
        injectStartupPromptButton();
        mainHandler.postDelayed(() -> {
            exposeScriptMode(webView.getUrl());
            injectNativeSpeechPolyfill();
            inject(webView.getUrl(), "document-idle");
        }, 650L);
    }

    private boolean shouldBlock(String url) {
        if (AiWebPolicy.isNavigationAllowed(url)) return false;
        Toast.makeText(activity, activity.getString(R.string.script_blocked_external, AiWebPolicy.hostOf(url)), Toast.LENGTH_SHORT).show();
        return true;
    }

    private void inject(String url, String runAt) {
        if (webView == null || url == null || !AiWebPolicy.isScriptHostAllowed(url)) return;
        String mode = effectiveScriptMode(url);
        List<UserScript> scripts = store.getRunnableScripts(url, runAt);
        for (UserScript script : scripts) {
            if (!shouldInjectScriptForMode(url, script, mode)) continue;
            evaluate(script, mode);
        }
    }

    private void evaluate(UserScript script, String mode) {
        if (script == null || script.code == null) return;
        String js = buildWrappedScript(script, mode);
        webView.evaluateJavascript(js, value -> {
        });
    }


    private String effectiveScriptMode(String url) {
        if (scriptMode != null && scriptMode.length() > 0) return scriptMode;
        return extractTsddMode(url);
    }

    private static String normalizeMode(String mode) {
        if (mode == null) return "";
        String value = mode.trim().toLowerCase();
        if (value.length() == 0) return "";
        if (value.length() > 40) value = value.substring(0, 40);
        value = value.replaceAll("[^a-z0-9_-]", "");
        return value;
    }

    private static String extractTsddMode(String url) {
        if (url == null || url.length() == 0) return "";
        String raw = url;
        try {
            Uri uri = Uri.parse(url);
            String query = uri.getQuery();
            String fragment = uri.getFragment();
            String fromQuery = extractParam(query, "tsdd_mode");
            if (fromQuery.length() > 0) return normalizeMode(fromQuery);
            String fromFragment = extractParam(fragment, "tsdd_mode");
            if (fromFragment.length() > 0) return normalizeMode(fromFragment);
        } catch (Throwable ignored) {
        }
        return normalizeMode(extractParam(raw, "tsdd_mode"));
    }

    private static String extractParam(String raw, String key) {
        if (raw == null || key == null || key.length() == 0) return "";
        String[] parts = raw.split("[&#?]");
        for (String part : parts) {
            if (part == null) continue;
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq).trim();
            if (!key.equals(k)) continue;
            String value = part.substring(eq + 1);
            try {
                return Uri.decode(value == null ? "" : value);
            } catch (Throwable ignored) {
                return value == null ? "" : value;
            }
        }
        return "";
    }

    private boolean shouldInjectScriptForMode(String url, UserScript script, String mode) {
        if (script == null || script.code == null) return false;
        mode = normalizeMode(mode);

        // 没有入口模式时保持旧逻辑，避免影响普通脚本和旧入口。
        if (mode.length() == 0) return true;

        return scriptMatchesMode(script, mode);
    }

    private boolean scriptMatchesMode(UserScript script, String mode) {
        mode = normalizeMode(mode);
        if (mode.length() == 0 || script == null) return false;

        String name = script.name == null ? "" : script.name;
        String code = script.code == null ? "" : script.code;
        String all = name + "\n" + code;

        String metaMode = readMetaValue(all, "@tsdd-mode");
        if (metaMode.length() > 0) {
            String[] modes = metaMode.toLowerCase().split("[,|/\\s]+");
            for (String item : modes) {
                String value = normalizeMode(item);
                if (value.equals(mode) || value.equals("all") || value.equals("global") || value.equals("*")) {
                    return true;
                }
            }
            return false;
        }

        // 兼容没有 @tsdd-mode 的旧脚本：根据 APP / SCRIPT_MODE / 文件名兜底判断。
        String lower = all.toLowerCase();
        String compact = lower.replace(" ", "").replace("\t", "").replace("\r", "").replace("\n", "").replace("\"", "'");

        if (compact.contains("script_mode='" + mode + "'")) return true;
        if (compact.contains("constscript_mode='" + mode + "'")) return true;
        if (compact.contains("letscript_mode='" + mode + "'")) return true;
        if (compact.contains("varscript_mode='" + mode + "'")) return true;
        if (compact.contains("@name") && compact.contains(mode)) return true;
        if (compact.contains("tsdd-ds-" + mode)) return true;
        if (compact.contains("deepseek-" + mode)) return true;

        if ("translate".equals(mode)) {
            return lower.contains("tsdd-ds-translate") || lower.contains("deepseek-translate") || lower.contains("翻译专家") || lower.contains("翻译入口");
        }
        if ("question".equals(mode)) {
            return lower.contains("tsdd-ds-question") || lower.contains("deepseek-question") || lower.contains("题目解析") || lower.contains("互动题") || lower.contains("选择题");
        }
        if ("sentence".equals(mode)) {
            return lower.contains("tsdd-ds-sentence") || lower.contains("deepseek-sentence") || lower.contains("句型解析") || lower.contains("句型");
        }
        if ("grammar".equals(mode)) {
            return lower.contains("tsdd-ds-grammar") || lower.contains("deepseek-grammar") || lower.contains("语法解析") || lower.contains("语法");
        }
        if ("pronunciation".equals(mode)) {
            return lower.contains("tsdd-ds-pronunciation") || lower.contains("deepseek-pronunciation") || lower.contains("读法") || lower.contains("发音") || lower.contains(" pronunciation");
        }
        if ("qwen-tts".equals(mode)) {
            return lower.contains("tsdd-qwen-tts") || lower.contains("qwen-tts") || lower.contains("千问") || lower.contains("chat.qwen.ai");
        }

        return false;
    }

    private static String readMetaValue(String text, String key) {
        if (text == null || key == null) return "";
        String[] lines = text.split("\\r?\\n");
        String keyLower = key.toLowerCase();
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();
            int idx = lower.indexOf(keyLower);
            if (idx < 0) continue;
            String value = trimmed.substring(idx + key.length()).trim();
            if (value.startsWith(":")) value = value.substring(1).trim();
            return value;
        }
        return "";
    }

    private void exposeScriptMode(String url) {
        String mode = effectiveScriptMode(url);
        String js = "window.__TS_DD_SCRIPT_MODE__=" + JSONObject.quote(mode) + ";" +
                "window.__TS_DD_ENTRY_MODE__=" + JSONObject.quote(mode) + ";";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void exposeStartupPrompt() {
        if (startupPrompt == null || startupPrompt.length() == 0) return;
        String js = "window.__TS_DD_START_PROMPT__=" + JSONObject.quote(startupPrompt) + ";";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }


    private void injectNativeSpeechPolyfill() {
        if (webView == null) return;
        String js = "(function(){" +
                "if(window.__TS_DD_NATIVE_SPEECH_POLYFILL_INSTALLED__)return;" +
                "if(!window.TsddNativeSpeech)return;" +
                "window.__TS_DD_NATIVE_SPEECH_POLYFILL_INSTALLED__=true;" +
                "var oldSR=window.SpeechRecognition||null;" +
                "var oldWebkitSR=window.webkitSpeechRecognition||null;" +
                "window.__TS_DD_ORIGINAL_SPEECH_RECOGNITION__=oldSR;" +
                "window.__TS_DD_ORIGINAL_WEBKIT_SPEECH_RECOGNITION__=oldWebkitSR;" +
                "var active=null;" +
                "function fire(target,fn,ev){try{if(typeof fn==='function')fn.call(target,ev);}catch(e){console.error(e);}}" +
                "function resultEvent(text,finalResult){" +
                "text=String(text||'');" +
                "var alt={transcript:text,confidence:finalResult?1:0};" +
                "var res=[alt];res.isFinal=!!finalResult;res.length=1;res.item=function(i){return this[i];};" +
                "var results=[res];results.length=1;results.item=function(i){return this[i];};" +
                "return {resultIndex:0,results:results};" +
                "}" +
                "function NativeSpeechRecognition(){" +
                "this.lang='zh-CN';this.interimResults=false;this.continuous=false;" +
                "this.onstart=null;this.onresult=null;this.onerror=null;this.onend=null;" +
                "this.onaudiostart=null;this.onaudioend=null;this.onspeechstart=null;this.onspeechend=null;" +
                "this._active=false;" +
                "}" +
                "NativeSpeechRecognition.prototype.start=function(){" +
                "if(active&&active!==this){try{active.abort();}catch(e){}}" +
                "active=this;this._active=true;" +
                "try{window.TsddNativeSpeech.startSpeech(String(this.lang||'zh-CN'));}" +
                "catch(e){fire(this,this.onerror,{error:'client',message:String(e&&e.message||e)});this._active=false;if(active===this)active=null;fire(this,this.onend,{});}" +
                "};" +
                "NativeSpeechRecognition.prototype.stop=function(){try{window.TsddNativeSpeech.stopSpeech();}catch(e){}};" +
                "NativeSpeechRecognition.prototype.abort=function(){try{window.TsddNativeSpeech.cancelSpeech();}catch(e){}};" +
                "window.__TS_DD_NATIVE_SPEECH_DISPATCH__=function(type,text,error){" +
                "var r=active;if(!r)return;" +
                "if(type==='start'){r._active=true;fire(r,r.onstart,{});fire(r,r.onaudiostart,{});return;}" +
                "if(type==='partial'){if(r.interimResults)fire(r,r.onresult,resultEvent(text,false));return;}" +
                "if(type==='final'){fire(r,r.onresult,resultEvent(text,true));return;}" +
                "if(type==='error'){fire(r,r.onerror,{error:error||'error',message:text||error||''});return;}" +
                "if(type==='end'){r._active=false;fire(r,r.onaudioend,{});fire(r,r.onend,{});if(active===r)active=null;return;}" +
                "};" +
                "NativeSpeechRecognition.prototype.constructor=NativeSpeechRecognition;" +
                "window.TsddNativeSpeechRecognition=NativeSpeechRecognition;" +
                "window.TsddSpeechRecognition=NativeSpeechRecognition;" +
                "if(!oldSR&&!oldWebkitSR){window.SpeechRecognition=NativeSpeechRecognition;window.webkitSpeechRecognition=NativeSpeechRecognition;}" +
                "})();";
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

    private String buildWrappedScript(UserScript script, String mode) {
        String scriptId = JSONObject.quote(script.id);
        String scriptName = JSONObject.quote(script.name == null ? "" : script.name);
        String source = JSONObject.quote(script.code + "\n//# sourceURL=tsdd-userscript-" + script.id + ".user.js");
        String secretValue = JSONObject.quote(secret);
        String modeValue = JSONObject.quote(normalizeMode(mode));
        String grants = new org.json.JSONArray(script.grants).toString();
        boolean networkAllowed = script.networkAllowed;
        return "(function(){\n" +
                "var __scriptId=" + scriptId + ";\n" +
                "window.__TS_DD_USER_SCRIPT_RAN__=window.__TS_DD_USER_SCRIPT_RAN__||{};\n" +
                "if(window.__TS_DD_USER_SCRIPT_RAN__[__scriptId]){return;}\n" +
                "window.__TS_DD_USER_SCRIPT_RAN__[__scriptId]=true;\n" +
                "var __secret=" + secretValue + ";\n" +
                "var __scriptName=" + scriptName + ";\n" +
                "var __tsddMode=" + modeValue + ";\n" +
                "window.__TS_DD_SCRIPT_MODE__=__tsddMode;window.__TS_DD_ENTRY_MODE__=__tsddMode;\n" +
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
