package com.chat.base.web;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

/**
 * Minimal JavaScript bridge exposed as window.TangSengSpeech in WebView pages.
 *
 * JavaScriptInterface methods may be called from a WebView bridge thread instead
 * of the Android main thread. SpeechRecognizer is strict on some devices, so all
 * calls into WebSpeechInputHelper are forwarded through the main thread here.
 */
public class TangSengSpeechBridge {
    private final WebSpeechInputHelper helper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TangSengSpeechBridge(WebSpeechInputHelper helper) {
        this.helper = helper;
    }

    /**
     * For page buttons that want the app to insert text into the currently focused input.
     * JS: window.TangSengSpeech.startSpeech()
     */
    @JavascriptInterface
    public void startSpeech() {
        startSpeechWithLang("zh-CN");
    }

    @JavascriptInterface
    public void startSpeechWithLang(String language) {
        runOnMain(() -> {
            if (helper != null) helper.startSpeechInput(language);
        });
    }

    /**
     * For the injected SpeechRecognition polyfill.
     * It returns result through window.__TangSengSpeechNativeResult(text), without direct insertion.
     * JS: window.TangSengSpeech.startRecognition()
     */
    @JavascriptInterface
    public void startRecognition() {
        startRecognitionWithLang("zh-CN");
    }

    @JavascriptInterface
    public void startRecognitionWithLang(String language) {
        runOnMain(() -> {
            if (helper != null) helper.startSpeechRecognitionForPage(language);
        });
    }

    @JavascriptInterface
    public boolean isAvailable() {
        return helper != null;
    }

    private void runOnMain(Runnable runnable) {
        if (runnable == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
