package com.chat.base.web;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.chat.base.R;
import com.chat.base.utils.WKPermissions;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Native speech-to-text helper for WebView pages.
 *
 * Important:
 * 1. It never opens the system speech recognition popup. Some phones crash when
 *    that popup is launched from an embedded WebView page.
 * 2. All SpeechRecognizer calls are forced onto the Android main thread. Some
 *    devices return fake permission/service errors when SpeechRecognizer is
 *    created or started from a WebView bridge thread.
 */
public class WebSpeechInputHelper {
    private static final String TAG = "WebSpeechInputHelper";

    private final FragmentActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private SpeechRecognizer speechRecognizer;
    private boolean destroyed;
    private boolean insertResultIntoFocusedElement = true;
    private boolean listening;
    private boolean usingOnDeviceRecognizer;
    private boolean triedOnDeviceRecognizer;
    private boolean triedStandardRecognizer;
    private String currentLanguage = "zh-CN";

    public WebSpeechInputHelper(FragmentActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void setWebView(WebView webView) {
        runOnMain(() -> this.webView = webView);
    }

    /**
     * Start native recognition and insert the final result into the focused page input.
     * JS can call: window.TangSengSpeech.startSpeech()
     */
    public void startSpeechInput() {
        startSpeechInput("zh-CN");
    }

    public void startSpeechInput(String language) {
        runOnMain(() -> startSpeechInputOnMain(true, language));
    }

    /**
     * Start native recognition and return the result through the injected Web Speech polyfill.
     * Existing page code like recognition.start() will use this path.
     */
    public void startSpeechRecognitionForPage() {
        startSpeechRecognitionForPage("zh-CN");
    }

    public void startSpeechRecognitionForPage(String language) {
        runOnMain(() -> startSpeechInputOnMain(false, language));
    }

    private void startSpeechInputOnMain(boolean insertIntoFocusedElement, String language) {
        if (destroyed || activity == null || activity.isFinishing()) return;
        if (webView == null) {
            showToast("网页还没有准备好");
            notifySpeechErrorToPage("网页还没有准备好", -1);
            return;
        }

        insertResultIntoFocusedElement = insertIntoFocusedElement;
        currentLanguage = normalizeLanguage(language);
        triedOnDeviceRecognizer = false;
        triedStandardRecognizer = false;
        usingOnDeviceRecognizer = false;

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListeningInternalOnMain();
            return;
        }

        String desc = String.format(
                activity.getString(R.string.microphone_permissions_des),
                activity.getString(R.string.app_name)
        );
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {
                runOnMain(() -> {
                    if (destroyed) return;
                    if (result) {
                        startListeningInternalOnMain();
                    } else {
                        String message = "缺少麦克风权限";
                        showToast(message);
                        notifySpeechErrorToPage(message, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
                    }
                });
            }

            @Override
            public void clickResult(boolean isCancel) {
                if (!isCancel) return;
                runOnMain(() -> {
                    String message = "缺少麦克风权限";
                    showToast(message);
                    notifySpeechErrorToPage(message, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
                });
            }
        }, activity, desc, Manifest.permission.RECORD_AUDIO);
    }

    /**
     * Inject a small Web Speech API compatibility layer.
     * Existing page code like new webkitSpeechRecognition().start() will call Android native recognition.
     */
    public void injectSpeechRecognitionPolyfill() {
        runOnMain(() -> {
            String js = "(function(){" +
                    "if(window.__TangSengSpeechPolyfillInstalled)return;" +
                    "window.__TangSengSpeechPolyfillInstalled=true;" +
                    "function call(fn,arg){try{if(typeof fn==='function')fn(arg);}catch(e){console.error(e);}}" +
                    "function fire(name,detail){try{window.dispatchEvent(new CustomEvent(name,{detail:detail}));}catch(e){try{var ev=document.createEvent('CustomEvent');ev.initCustomEvent(name,false,false,detail);window.dispatchEvent(ev);}catch(_){}}}" +
                    "function makeResults(text){var alt={transcript:text,confidence:1};var one=[alt];one.isFinal=true;one.item=function(i){return this[i];};var results=[one];results.item=function(i){return this[i];};return results;}" +
                    "function NativeSpeechRecognition(){this.lang='zh-CN';this.continuous=false;this.interimResults=false;this.maxAlternatives=1;this.onstart=null;this.onresult=null;this.onerror=null;this.onend=null;this.onnomatch=null;}" +
                    "NativeSpeechRecognition.prototype.start=function(){window.__TangSengSpeechActiveRecognition=this;call(this.onstart,{type:'start'});var lang=this.lang||'zh-CN';if(window.TangSengSpeech&&window.TangSengSpeech.startRecognitionWithLang){window.TangSengSpeech.startRecognitionWithLang(String(lang));}else if(window.TangSengSpeech&&window.TangSengSpeech.startRecognition){window.TangSengSpeech.startRecognition();}else if(window.TangSengSpeech&&window.TangSengSpeech.startSpeechWithLang){window.TangSengSpeech.startSpeechWithLang(String(lang));}else if(window.TangSengSpeech&&window.TangSengSpeech.startSpeech){window.TangSengSpeech.startSpeech();}else{call(this.onerror,{type:'error',error:'not-allowed',message:'TangSengSpeech bridge not found'});call(this.onend,{type:'end'});}};" +
                    "NativeSpeechRecognition.prototype.stop=function(){call(this.onend,{type:'end'});};" +
                    "NativeSpeechRecognition.prototype.abort=function(){call(this.onend,{type:'end'});};" +
                    "window.__TangSengSpeechNativeResult=function(text){var rec=window.__TangSengSpeechActiveRecognition;fire('TangSengSpeechResult',{text:text});if(rec){call(rec.onresult,{type:'result',resultIndex:0,results:makeResults(text)});call(rec.onend,{type:'end'});}};" +
                    "window.__TangSengSpeechNativeError=function(message,code){var rec=window.__TangSengSpeechActiveRecognition;fire('TangSengSpeechError',{message:message,code:code});if(rec){call(rec.onerror,{type:'error',error:'no-speech',message:message,code:code});call(rec.onend,{type:'end'});}};" +
                    "window.SpeechRecognition=NativeSpeechRecognition;" +
                    "window.webkitSpeechRecognition=NativeSpeechRecognition;" +
                    "})();";
            runJavascript(js);
        });
    }

    public void destroy() {
        runOnMain(() -> {
            destroyed = true;
            releaseRecognizerOnMain();
            webView = null;
        });
    }

    private void startListeningInternalOnMain() {
        if (destroyed || activity == null || activity.isFinishing()) return;

        rememberCurrentEditableElement();

        boolean onDeviceAvailable = isOnDeviceRecognitionAvailableOnMain();
        boolean standardAvailable = isStandardRecognitionAvailableOnMain();

        if (!onDeviceAvailable && !standardAvailable) {
            String message = "当前系统没有可用的语音识别服务";
            showToast(message);
            notifySpeechErrorToPage(message, -2);
            return;
        }

        if (onDeviceAvailable && !triedOnDeviceRecognizer) {
            triedOnDeviceRecognizer = true;
            if (startListeningWithRecognizerOnMain(true)) return;
        }

        if (standardAvailable && !triedStandardRecognizer) {
            triedStandardRecognizer = true;
            if (startListeningWithRecognizerOnMain(false)) return;
        }

        String message = "启动语音识别失败，请检查系统语音服务";
        showToast(message);
        notifySpeechErrorToPage(message, -4);
    }

    private boolean startListeningWithRecognizerOnMain(boolean useOnDevice) {
        releaseRecognizerOnMain();
        usingOnDeviceRecognizer = useOnDevice;

        try {
            if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
            } else {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            }
        } catch (Exception e) {
            Log.e(TAG, (useOnDevice ? "createOnDeviceSpeechRecognizer" : "createSpeechRecognizer") + " failed", e);
            releaseRecognizerOnMain();
            if (useOnDevice && isStandardRecognitionAvailableOnMain() && !triedStandardRecognizer) {
                triedStandardRecognizer = true;
                return startListeningWithRecognizerOnMain(false);
            }
            String message = "创建语音识别服务失败：" + safeExceptionName(e);
            showToast(message);
            notifySpeechErrorToPage(message, -3);
            return false;
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnMain(() -> showToast("请开始说话"));
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                runOnMain(() -> handleRecognitionErrorOnMain(error));
            }

            @Override
            public void onResults(Bundle results) {
                runOnMain(() -> handleRecognitionResultsOnMain(results));
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请开始说话");
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
        if (useOnDevice) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }

        try {
            listening = true;
            speechRecognizer.startListening(intent);
            Log.i(TAG, "startListening success, onDevice=" + useOnDevice + ", lang=" + currentLanguage);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "startListening failed, onDevice=" + useOnDevice, e);
            releaseRecognizerOnMain();
            if (useOnDevice && isStandardRecognitionAvailableOnMain() && !triedStandardRecognizer) {
                triedStandardRecognizer = true;
                return startListeningWithRecognizerOnMain(false);
            }
            String message = "启动语音识别失败：" + safeExceptionName(e);
            showToast(message);
            notifySpeechErrorToPage(message, -4);
            return false;
        }
    }

    private void handleRecognitionErrorOnMain(int error) {
        listening = false;
        boolean shouldFallbackToStandard = usingOnDeviceRecognizer
                && !triedStandardRecognizer
                && isStandardRecognitionAvailableOnMain()
                && isFallbackError(error);

        String message = getErrorMessage(error);
        Log.w(TAG, "SpeechRecognizer error code=" + error + ", onDevice=" + usingOnDeviceRecognizer + ", message=" + message);
        releaseRecognizerOnMain();

        if (shouldFallbackToStandard) {
            triedStandardRecognizer = true;
            startListeningWithRecognizerOnMain(false);
            return;
        }

        showToast(message);
        notifySpeechErrorToPage(message, error);
    }

    private void handleRecognitionResultsOnMain(Bundle results) {
        listening = false;
        String text = readBestResult(results);
        if (TextUtils.isEmpty(text)) {
            String message = "没有识别到内容";
            showToast(message);
            notifySpeechErrorToPage(message, SpeechRecognizer.ERROR_NO_MATCH);
        } else {
            if (insertResultIntoFocusedElement) {
                insertTextIntoWebView(text);
            }
            notifySpeechResultToPage(text);
        }
        releaseRecognizerOnMain();
    }

    private String readBestResult(Bundle results) {
        if (results == null) return "";
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return "";
        return list.get(0);
    }

    private void rememberCurrentEditableElement() {
        String js = "(function(){" +
                "function editable(el){" +
                "return !!el && (el.tagName==='TEXTAREA' || el.isContentEditable || " +
                "(el.tagName==='INPUT' && ['','text','search','email','url','tel','number'].indexOf((el.type||'').toLowerCase())!==-1));" +
                "}" +
                "var old=document.querySelectorAll('[data-tangseng-speech-target=\"1\"]');" +
                "for(var i=0;i<old.length;i++){old[i].removeAttribute('data-tangseng-speech-target');}" +
                "var el=document.activeElement;" +
                "if(editable(el)){el.setAttribute('data-tangseng-speech-target','1');}" +
                "})();";
        runJavascript(js);
    }

    private void insertTextIntoWebView(String text) {
        String js = "(function(text){" +
                "function editable(el){" +
                "return !!el && !el.disabled && !el.readOnly && (el.tagName==='TEXTAREA' || el.isContentEditable || " +
                "(el.tagName==='INPUT' && ['','text','search','email','url','tel','number'].indexOf((el.type||'').toLowerCase())!==-1));" +
                "}" +
                "function visible(el){" +
                "var r=el.getBoundingClientRect();" +
                "var s=window.getComputedStyle(el);" +
                "return r.width>0 && r.height>0 && s.visibility!=='hidden' && s.display!=='none';" +
                "}" +
                "function fire(el,type){try{el.dispatchEvent(new Event(type,{bubbles:true}));}catch(e){var ev=document.createEvent('Event');ev.initEvent(type,true,true);el.dispatchEvent(ev);}}" +
                "var el=document.querySelector('[data-tangseng-speech-target=\"1\"]');" +
                "if(!editable(el)) el=document.activeElement;" +
                "if(!editable(el)){" +
                "var list=document.querySelectorAll('textarea,input[type=text],input[type=search],input:not([type]),[contenteditable=true],[contenteditable=\"\"]');" +
                "for(var i=0;i<list.length;i++){if(editable(list[i])&&visible(list[i])){el=list[i];break;}}" +
                "}" +
                "if(!editable(el)) return false;" +
                "el.focus();" +
                "if(el.isContentEditable){" +
                "try{document.execCommand('insertText',false,text);}catch(e){el.textContent=(el.textContent||'')+text;}" +
                "fire(el,'input');fire(el,'change');return true;" +
                "}" +
                "var value=el.value||'';" +
                "var start=(typeof el.selectionStart==='number')?el.selectionStart:value.length;" +
                "var end=(typeof el.selectionEnd==='number')?el.selectionEnd:value.length;" +
                "el.value=value.slice(0,start)+text+value.slice(end);" +
                "var pos=start+text.length;" +
                "try{el.setSelectionRange(pos,pos);}catch(e){}" +
                "fire(el,'input');fire(el,'change');return true;" +
                "})(" + JSONObject.quote(text) + ");";
        runJavascript(js);
    }

    private void notifySpeechResultToPage(String text) {
        String js = "(function(text){" +
                "try{if(window.__TangSengSpeechNativeResult)window.__TangSengSpeechNativeResult(text);}" +
                "catch(e){console.error(e);}" +
                "})(" + JSONObject.quote(text) + ");";
        runJavascript(js);
    }

    private void notifySpeechErrorToPage(String message, int code) {
        String js = "(function(message,code){" +
                "try{if(window.__TangSengSpeechNativeError)window.__TangSengSpeechNativeError(message,code);}" +
                "catch(e){console.error(e);}" +
                "})(" + JSONObject.quote(message) + "," + code + ");";
        runJavascript(js);
    }

    private void runJavascript(String js) {
        if (TextUtils.isEmpty(js)) return;
        runOnMain(() -> {
            WebView target = webView;
            if (target == null || destroyed) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                target.evaluateJavascript(js, null);
            } else {
                target.loadUrl("javascript:" + js);
            }
        });
    }

    private void releaseRecognizerOnMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::releaseRecognizerOnMain);
            return;
        }
        listening = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception e) {
                Log.w(TAG, "cancel recognizer failed", e);
            }
            try {
                speechRecognizer.destroy();
            } catch (Exception e) {
                Log.w(TAG, "destroy recognizer failed", e);
            }
            speechRecognizer = null;
        }
    }

    private boolean isFallbackError(int error) {
        return error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_SERVER
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;
    }

    private boolean isOnDeviceRecognitionAvailableOnMain() {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        try {
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(activity);
        } catch (Exception e) {
            Log.w(TAG, "isOnDeviceRecognitionAvailable failed", e);
            return false;
        }
    }

    private boolean isStandardRecognitionAvailableOnMain() {
        if (activity == null) return false;
        try {
            return SpeechRecognizer.isRecognitionAvailable(activity);
        } catch (Exception e) {
            Log.w(TAG, "isRecognitionAvailable failed", e);
            return false;
        }
    }

    private String normalizeLanguage(String language) {
        if (TextUtils.isEmpty(language)) return "zh-CN";
        String normalized = language.trim().replace('_', '-');
        if (TextUtils.isEmpty(normalized)) return "zh-CN";
        return normalized;
    }

    private String getErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "录音失败，请检查麦克风（错误码 3）";
            case SpeechRecognizer.ERROR_CLIENT:
                return "语音识别客户端错误（错误码 5）";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                if (activity != null && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    return "系统语音服务返回权限错误（错误码 9），App 麦克风权限已允许，请检查系统语音服务或 ROM 限制";
                }
                return "缺少麦克风权限（错误码 9）";
            case SpeechRecognizer.ERROR_NETWORK:
                return "网络异常，语音识别失败（错误码 2）";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "网络超时，语音识别失败（错误码 1）";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "没有识别到内容（错误码 7）";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "语音识别正在忙，请稍后再试（错误码 8）";
            case SpeechRecognizer.ERROR_SERVER:
                return "语音识别服务异常（错误码 4）";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "没有听到说话内容（错误码 6）";
            default:
                return "语音识别失败：" + error;
        }
    }

    private void showToast(String text) {
        if (activity == null || activity.isFinishing() || TextUtils.isEmpty(text)) return;
        runOnMain(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }

    private void runOnMain(Runnable runnable) {
        if (runnable == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private String safeExceptionName(Exception e) {
        if (e == null) return "未知异常";
        String name = e.getClass().getSimpleName();
        return TextUtils.isEmpty(name) ? "异常" : name;
    }
}
