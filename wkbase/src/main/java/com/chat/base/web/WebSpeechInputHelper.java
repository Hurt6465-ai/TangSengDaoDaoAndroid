package com.chat.base.web;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.chat.base.R;
import com.chat.base.utils.WKPermissions;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Native speech-to-text helper for WebView pages.
 *
 * This version intentionally keeps the Android SpeechRecognizer call path simple,
 * similar to common Google ASR sample apps:
 * - use SpeechRecognizer.createSpeechRecognizer(context)
 * - use RecognizerIntent.ACTION_RECOGNIZE_SPEECH
 * - use LANGUAGE_MODEL_FREE_FORM
 * - do not force Locale.getDefault() or zh-CN when JavaScript does not pass a language
 * - enable partial results
 * - reuse one SpeechRecognizer instance instead of destroying it after every run
 *
 * It does not force a recognition language by default. If JavaScript passes a language
 * hint such as zh-CN / my-MM / en-US, Android receives that hint. If JavaScript passes
 * an empty value, Android uses the system/default recognition language.
 *
 * It prefers hidden SpeechRecognizer so the UX feels like browser speech input. If the
 * device speech service is missing, broken or rejects the hidden recognizer, it falls
 * back to the Android RecognizerIntent system dialog and then inserts/returns the result
 * through the same WebView callbacks.
 */
public class WebSpeechInputHelper {
    private static final String TAG = "WebSpeechInputHelper";

    private final FragmentActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private SpeechRecognizer speechRecognizer;
    private ActivityResultLauncher<Intent> systemSpeechLauncher;
    private boolean destroyed;
    private boolean listening;
    private boolean systemSpeechActive;
    private boolean insertResultIntoFocusedElement = true;
    private String currentSpeechLanguageTag = "";
    private String lastPartialText = "";

    public WebSpeechInputHelper(FragmentActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        registerSystemSpeechLauncher();
    }

    public void setWebView(WebView webView) {
        runOnMain(() -> this.webView = webView);
    }

    private void registerSystemSpeechLauncher() {
        if (activity == null) return;
        try {
            systemSpeechLauncher = activity.registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> runOnMain(() -> handleSystemSpeechResultOnMain(result))
            );
        } catch (Throwable e) {
            // If this helper is constructed after the Activity has already started, AndroidX may
            // reject launcher registration. Hidden SpeechRecognizer still works; only popup fallback
            // is unavailable for this host until it registers earlier in the Activity lifecycle.
            Log.w(TAG, "register system speech launcher failed", e);
            systemSpeechLauncher = null;
        }
    }

    private boolean startSystemSpeechIntentOnMain() {
        if (destroyed || activity == null || activity.isFinishing()) return false;
        if (systemSpeechLauncher == null) return false;

        rememberCurrentEditableElement();
        releaseRecognizerOnMain();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        putSpeechLanguageExtrasIfNeeded(intent, currentSpeechLanguageTag);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请讲话");

        try {
            systemSpeechActive = true;
            systemSpeechLauncher.launch(intent);
            Log.i(TAG, "start system speech dialog, lang=" + (TextUtils.isEmpty(currentSpeechLanguageTag) ? "system-default" : currentSpeechLanguageTag));
            return true;
        } catch (ActivityNotFoundException e) {
            systemSpeechActive = false;
            Log.w(TAG, "system speech dialog not found", e);
            return false;
        } catch (Throwable e) {
            systemSpeechActive = false;
            Log.w(TAG, "start system speech dialog failed", e);
            return false;
        }
    }

    private void handleSystemSpeechResultOnMain(ActivityResult result) {
        systemSpeechActive = false;
        if (destroyed) return;

        String text = "";
        if (result != null && result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            ArrayList<String> list = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (list != null && !list.isEmpty() && list.get(0) != null) {
                text = list.get(0).trim();
            }
        }

        if (!TextUtils.isEmpty(text)) {
            if (insertResultIntoFocusedElement) {
                insertTextIntoWebView(text);
            }
            notifySpeechResultToPage(text);
            return;
        }

        String message = "没有识别到内容";
        notifySpeechErrorToPage(message, SpeechRecognizer.ERROR_NO_MATCH);
    }

    /**
     * Start native recognition and insert the final result into the focused page input.
     * JS can call: window.TangSengSpeech.startSpeech()
     */
    public void startSpeechInput() {
        startSpeechInput(null);
    }

    public void startSpeechInput(String language) {
        runOnMain(() -> startSpeechInputOnMain(true, language));
    }

    /**
     * Start native recognition and return the result through the injected Web Speech polyfill.
     * Existing page code like recognition.start() will use this path.
     */
    public void startSpeechRecognitionForPage() {
        startSpeechRecognitionForPage(null);
    }

    public void startSpeechRecognitionForPage(String language) {
        runOnMain(() -> startSpeechInputOnMain(false, language));
    }

    private void startSpeechInputOnMain(boolean insertIntoFocusedElement, String language) {
        if (destroyed || activity == null || activity.isFinishing()) return;

        if (webView == null) {
            String message = "网页还没有准备好";
            showToast(message);
            notifySpeechErrorToPage(message, -1);
            return;
        }

        insertResultIntoFocusedElement = insertIntoFocusedElement;
        currentSpeechLanguageTag = normalizeSpeechLanguageTag(language);
        lastPartialText = "";

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListeningOnMain(false);
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
                        startListeningOnMain(false);
                    } else if (!startSystemSpeechIntentOnMain()) {
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
                    if (startSystemSpeechIntentOnMain()) return;
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
                    "function makeResults(text,isFinal){var alt={transcript:text,confidence:isFinal?1:0.6};var one=[alt];one.isFinal=!!isFinal;one.item=function(i){return this[i];};var results=[one];results.item=function(i){return this[i];};return results;}" +
                    "function NativeSpeechRecognition(){this.lang='';this.continuous=false;this.interimResults=false;this.maxAlternatives=1;this.onstart=null;this.onresult=null;this.onerror=null;this.onend=null;this.onnomatch=null;}" +
                    "NativeSpeechRecognition.prototype.start=function(){window.__TangSengSpeechActiveRecognition=this;call(this.onstart,{type:'start'});var lang=this.lang||'';if(window.TangSengSpeech&&window.TangSengSpeech.startRecognitionWithLang){window.TangSengSpeech.startRecognitionWithLang(String(lang));}else if(window.TangSengSpeech&&window.TangSengSpeech.startRecognition){window.TangSengSpeech.startRecognition();}else if(window.TangSengSpeech&&window.TangSengSpeech.startSpeechWithLang){window.TangSengSpeech.startSpeechWithLang(String(lang));}else if(window.TangSengSpeech&&window.TangSengSpeech.startSpeech){window.TangSengSpeech.startSpeech();}else{call(this.onerror,{type:'error',error:'not-allowed',message:'TangSengSpeech bridge not found'});call(this.onend,{type:'end'});}};" +
                    "NativeSpeechRecognition.prototype.stop=function(){try{if(window.TangSengSpeech&&window.TangSengSpeech.stop)window.TangSengSpeech.stop();}catch(e){}call(this.onend,{type:'end'});};" +
                    "NativeSpeechRecognition.prototype.abort=function(){try{if(window.TangSengSpeech&&window.TangSengSpeech.stop)window.TangSengSpeech.stop();}catch(e){}call(this.onend,{type:'end'});};" +
                    "window.__TangSengSpeechNativePartial=function(text){var rec=window.__TangSengSpeechActiveRecognition;fire('TangSengSpeechPartial',{text:text});if(rec&&rec.interimResults){call(rec.onresult,{type:'result',resultIndex:0,results:makeResults(text,false)});}};" +
                    "window.__TangSengSpeechNativeResult=function(text){var rec=window.__TangSengSpeechActiveRecognition;fire('TangSengSpeechResult',{text:text});if(rec){call(rec.onresult,{type:'result',resultIndex:0,results:makeResults(text,true)});call(rec.onend,{type:'end'});}};" +
                    "window.__TangSengSpeechNativeError=function(message,code){var rec=window.__TangSengSpeechActiveRecognition;fire('TangSengSpeechError',{message:message,code:code});if(rec){call(rec.onerror,{type:'error',error:'no-speech',message:message,code:code});call(rec.onend,{type:'end'});}};" +
                    "window.SpeechRecognition=NativeSpeechRecognition;" +
                    "window.webkitSpeechRecognition=NativeSpeechRecognition;" +
                    "})();";
            runJavascript(js);
        });
    }

    public void stop() {
        runOnMain(() -> {
            if (systemSpeechActive) {
                // The Android system dialog owns its UI; there is no reliable programmatic stop
                // for ActivityResultLauncher. The dialog can be dismissed by the user.
                return;
            }
            if (speechRecognizer != null && listening) {
                try {
                    speechRecognizer.stopListening();
                } catch (Exception e) {
                    Log.w(TAG, "stopListening failed", e);
                    try {
                        speechRecognizer.cancel();
                    } catch (Exception ignored) {
                    }
                    listening = false;
                }
            }
        });
    }

    public void destroy() {
        runOnMain(() -> {
            destroyed = true;
            systemSpeechActive = false;
            releaseRecognizerOnMain();
            webView = null;
        });
    }

    private void startListeningOnMain(boolean delayedAfterCancel) {
        if (destroyed || activity == null || activity.isFinishing()) return;

        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            if (startSystemSpeechIntentOnMain()) return;
            String message = "当前系统没有可用的语音识别服务";
            showToast(message);
            notifySpeechErrorToPage(message, -2);
            return;
        }

        if (!ensureRecognizerOnMain()) {
            if (startSystemSpeechIntentOnMain()) return;
            String message = "创建语音识别服务失败";
            showToast(message);
            notifySpeechErrorToPage(message, -3);
            return;
        }

        if (listening) {
            try {
                speechRecognizer.cancel();
            } catch (Exception e) {
                Log.w(TAG, "cancel before restart failed", e);
            }
            listening = false;
            if (!delayedAfterCancel) {
                mainHandler.postDelayed(() -> startListeningOnMain(true), 180);
            }
            return;
        }

        rememberCurrentEditableElement();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        putSpeechLanguageExtrasIfNeeded(intent, currentSpeechLanguageTag);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try {
            listening = true;
            lastPartialText = "";
            speechRecognizer.startListening(intent);
            Log.i(TAG, "startListening success, lang=" + (TextUtils.isEmpty(currentSpeechLanguageTag) ? "system-default" : currentSpeechLanguageTag));
        } catch (Exception e) {
            listening = false;
            Log.e(TAG, "startListening failed", e);
            if (startSystemSpeechIntentOnMain()) return;
            String message = "启动语音识别失败：" + safeExceptionName(e);
            showToast(message);
            notifySpeechErrorToPage(message, -4);
        }
    }

    private boolean ensureRecognizerOnMain() {
        if (speechRecognizer != null) return true;

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
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

                @Override
                public void onPartialResults(Bundle partialResults) {
                    runOnMain(() -> handlePartialResultsOnMain(partialResults));
                }

                @Override public void onEvent(int eventType, Bundle params) {}
            });
            return true;
        } catch (Exception e) {
            Log.e(TAG, "createSpeechRecognizer failed", e);
            speechRecognizer = null;
            return false;
        }
    }

    private void handleRecognitionErrorOnMain(int error) {
        listening = false;
        String message = getErrorMessage(error);
        Log.w(TAG, "SpeechRecognizer error code=" + error + ", message=" + message);

        if (shouldTrySystemSpeechDialogAfterError(error) && startSystemSpeechIntentOnMain()) {
            return;
        }

        showToast(message);
        notifySpeechErrorToPage(message, error);

        if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            releaseRecognizerOnMain();
        }
    }

    private void handlePartialResultsOnMain(Bundle partialResults) {
        String text = readBestResult(partialResults);
        if (TextUtils.isEmpty(text) || TextUtils.equals(text, lastPartialText)) return;
        lastPartialText = text;
        notifySpeechPartialToPage(text);
    }

    private void handleRecognitionResultsOnMain(Bundle results) {
        listening = false;
        String text = readBestResult(results);
        if (TextUtils.isEmpty(text)) {
            text = lastPartialText;
        }

        if (TextUtils.isEmpty(text)) {
            String message = "没有识别到内容";
            showToast(message);
            notifySpeechErrorToPage(message, SpeechRecognizer.ERROR_NO_MATCH);
            return;
        }

        if (insertResultIntoFocusedElement) {
            insertTextIntoWebView(text);
        }
        notifySpeechResultToPage(text);
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

    private void notifySpeechPartialToPage(String text) {
        String js = "(function(text){" +
                "try{if(window.__TangSengSpeechNativePartial)window.__TangSengSpeechNativePartial(text);}" +
                "catch(e){console.error(e);}" +
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

    private String normalizeSpeechLanguageTag(String language) {
        String value = language == null ? "" : language.trim();
        if (TextUtils.isEmpty(value)) return "";
        if (value.length() > 40) value = value.substring(0, 40);
        value = value.replace('_', '-');

        String lower = value.toLowerCase(Locale.US);
        if ("auto".equals(lower) || "default".equals(lower) || "system".equals(lower) || "und".equals(lower)) {
            return "";
        }
        if ("zh".equals(lower) || "cn".equals(lower) || "zh-cn".equals(lower)
                || "chinese".equals(lower) || "中文".equals(value) || "汉语".equals(value)) {
            return "zh-CN";
        }
        if ("zh-tw".equals(lower) || "zh-hk".equals(lower)) {
            return value;
        }
        if ("my".equals(lower) || "mm".equals(lower) || "my-mm".equals(lower)
                || "burmese".equals(lower) || "myanmar".equals(lower)
                || "缅语".equals(value) || "缅甸语".equals(value) || "မြန်မာ".equals(value)) {
            return "my-MM";
        }
        if ("en".equals(lower) || "english".equals(lower) || "英文".equals(value) || "英语".equals(value)) {
            return "en-US";
        }
        return value;
    }

    private void putSpeechLanguageExtrasIfNeeded(Intent intent, String language) {
        String lang = normalizeSpeechLanguageTag(language);
        if (TextUtils.isEmpty(lang)) return;
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
    }

    private boolean shouldTrySystemSpeechDialogAfterError(int error) {
        return error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_SERVER
                || error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;
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
                    return "系统语音服务返回权限错误（错误码 9），App 麦克风权限已允许，请检查系统语音服务设置";
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
