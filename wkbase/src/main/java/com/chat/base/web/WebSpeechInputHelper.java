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
 * It does not depend on the page's Web Speech API. The app uses Android's
 * SpeechRecognizer to get text, then either:
 * 1) injects the recognized text into the focused input/textarea/contenteditable element, or
 * 2) sends a Web Speech-like result event back to the page for existing voice buttons.
 */
public class WebSpeechInputHelper {
    private final FragmentActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private SpeechRecognizer speechRecognizer;
    private boolean destroyed;
    private boolean insertResultIntoFocusedElement = true;

    public WebSpeechInputHelper(FragmentActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    /**
     * Start native recognition and insert the final result into the focused page input.
     * This is useful for a page that directly calls window.TangSengSpeech.startSpeech().
     */
    public void startSpeechInput() {
        startSpeechInput(true);
    }

    /**
     * Start native recognition and return the result through the injected Web Speech polyfill.
     * This is useful for pages that already call recognition.start().
     */
    public void startSpeechRecognitionForPage() {
        startSpeechInput(false);
    }

    private void startSpeechInput(boolean insertIntoFocusedElement) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mainHandler.post(() -> startSpeechInput(insertIntoFocusedElement));
            return;
        }

        if (destroyed || activity == null || activity.isFinishing()) return;
        if (webView == null) {
            showToast("网页还没有准备好");
            notifySpeechErrorToPage("网页还没有准备好", -1);
            return;
        }

        insertResultIntoFocusedElement = insertIntoFocusedElement;

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListeningInternal();
            return;
        }

        String desc = String.format(
                activity.getString(R.string.microphone_permissions_des),
                activity.getString(R.string.app_name)
        );
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {
                if (result) startListeningInternal();
                else notifySpeechErrorToPage("缺少麦克风权限", SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            }

            @Override
            public void clickResult(boolean isCancel) {
                if (isCancel) notifySpeechErrorToPage("缺少麦克风权限", SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            }
        }, activity, desc, Manifest.permission.RECORD_AUDIO);
    }

    /**
     * Inject a small Web Speech API compatibility layer.
     * Existing page code like new webkitSpeechRecognition().start() will call Android native recognition.
     */
    public void injectSpeechRecognitionPolyfill() {
        String js = "(function(){" +
                "if(window.__TangSengSpeechPolyfillInstalled)return;" +
                "window.__TangSengSpeechPolyfillInstalled=true;" +
                "function call(fn,arg){try{if(typeof fn==='function')fn(arg);}catch(e){console.error(e);}}" +
                "function fire(name,detail){try{window.dispatchEvent(new CustomEvent(name,{detail:detail}));}catch(e){try{var ev=document.createEvent('CustomEvent');ev.initCustomEvent(name,false,false,detail);window.dispatchEvent(ev);}catch(_){}}}" +
                "function makeResults(text){var alt={transcript:text,confidence:1};var one=[alt];one.isFinal=true;one.item=function(i){return this[i];};var results=[one];results.item=function(i){return this[i];};return results;}" +
                "function NativeSpeechRecognition(){this.lang='zh-CN';this.continuous=false;this.interimResults=false;this.maxAlternatives=1;this.onstart=null;this.onresult=null;this.onerror=null;this.onend=null;this.onnomatch=null;}" +
                "NativeSpeechRecognition.prototype.start=function(){window.__TangSengSpeechActiveRecognition=this;call(this.onstart,{type:'start'});if(window.TangSengSpeech&&window.TangSengSpeech.startRecognition){window.TangSengSpeech.startRecognition();}else if(window.TangSengSpeech&&window.TangSengSpeech.startSpeech){window.TangSengSpeech.startSpeech();}else{call(this.onerror,{type:'error',error:'not-allowed',message:'TangSengSpeech bridge not found'});call(this.onend,{type:'end'});}};" +
                "NativeSpeechRecognition.prototype.stop=function(){call(this.onend,{type:'end'});};" +
                "NativeSpeechRecognition.prototype.abort=function(){call(this.onend,{type:'end'});};" +
                "window.__TangSengSpeechNativeResult=function(text){var rec=window.__TangSengSpeechActiveRecognition;fire('TangSengSpeechResult',{text:text});if(rec){call(rec.onresult,{type:'result',resultIndex:0,results:makeResults(text)});call(rec.onend,{type:'end'});}};" +
                "window.__TangSengSpeechNativeError=function(message,code){var rec=window.__TangSengSpeechActiveRecognition;fire('TangSengSpeechError',{message:message,code:code});if(rec){call(rec.onerror,{type:'error',error:'no-speech',message:message,code:code});call(rec.onend,{type:'end'});}};" +
                "window.SpeechRecognition=NativeSpeechRecognition;" +
                "window.webkitSpeechRecognition=NativeSpeechRecognition;" +
                "})();";
        runJavascript(js);
    }

    public void destroy() {
        destroyed = true;
        releaseRecognizer();
        webView = null;
    }

    private void startListeningInternal() {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mainHandler.post(this::startListeningInternal);
            return;
        }

        if (destroyed || activity == null || activity.isFinishing()) return;

        rememberCurrentEditableElement();

        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            startSystemRecognitionFallback("当前系统没有可用的语音识别服务，尝试打开系统语音输入");
            return;
        }

        releaseRecognizer();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                showToast("请开始说话");
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                String message = getErrorMessage(error);
                releaseRecognizer();

                if (shouldFallbackToSystemRecognizer(error)) {
                    startSystemRecognitionFallback(message);
                    return;
                }

                showToast(message);
                notifySpeechErrorToPage(message, error);
            }

            @Override
            public void onResults(Bundle results) {
                String text = readBestResult(results);
                if (TextUtils.isEmpty(text)) {
                    showToast("没有识别到内容");
                    notifySpeechErrorToPage("没有识别到内容", SpeechRecognizer.ERROR_NO_MATCH);
                } else {
                    if (insertResultIntoFocusedElement) {
                        insertTextIntoWebView(text);
                    }
                    notifySpeechResultToPage(text);
                }
                releaseRecognizer();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请开始说话");

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            releaseRecognizer();
            startSystemRecognitionFallback("启动语音识别失败，尝试打开系统语音输入");
        }
    }

    private boolean shouldFallbackToSystemRecognizer(int error) {
        return error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                || error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_SERVER
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT;
    }

    private void startSystemRecognitionFallback(String reason) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mainHandler.post(() -> startSystemRecognitionFallback(reason));
            return;
        }

        if (destroyed || activity == null || activity.isFinishing()) return;

        if (!TextUtils.isEmpty(reason)) {
            showToast(reason);
        }

        rememberCurrentEditableElement();

        WebSpeechProxyActivity.start(activity, new WebSpeechProxyActivity.Callback() {
            @Override
            public void onResult(String text) {
                if (destroyed) return;
                if (TextUtils.isEmpty(text)) {
                    showToast("没有识别到内容");
                    notifySpeechErrorToPage("没有识别到内容", SpeechRecognizer.ERROR_NO_MATCH);
                    return;
                }

                if (insertResultIntoFocusedElement) {
                    insertTextIntoWebView(text);
                }
                notifySpeechResultToPage(text);
            }

            @Override
            public void onError(String message) {
                if (destroyed) return;
                String safeMessage = TextUtils.isEmpty(message) ? "系统语音输入失败" : message;
                showToast(safeMessage);
                notifySpeechErrorToPage(safeMessage, -4);
            }
        });
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
        mainHandler.post(() -> {
            WebView target = webView;
            if (target == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                target.evaluateJavascript(js, null);
            } else {
                target.loadUrl("javascript:" + js);
            }
        });
    }

    private void releaseRecognizer() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception ignored) {
            }
            speechRecognizer = null;
        }
    }

    private String getErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "录音失败，请检查麦克风";
            case SpeechRecognizer.ERROR_CLIENT:
                return "语音识别客户端错误";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                if (activity != null && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    return "系统语音服务无麦克风权限，请检查 Google/语音服务麦克风权限或系统麦克风开关";
                }
                return "缺少麦克风权限";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "网络异常，语音识别失败";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "没有识别到内容";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "语音识别正在忙，请稍后再试";
            case SpeechRecognizer.ERROR_SERVER:
                return "语音识别服务异常";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "没有听到说话内容";
            default:
                return "语音识别失败：" + error;
        }
    }

    private void showToast(String text) {
        if (activity == null || activity.isFinishing() || TextUtils.isEmpty(text)) return;
        mainHandler.post(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }
}
