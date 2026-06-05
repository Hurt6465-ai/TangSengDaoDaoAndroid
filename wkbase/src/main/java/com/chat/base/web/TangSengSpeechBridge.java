package com.chat.base.web;

import android.webkit.JavascriptInterface;

/**
 * Minimal JavaScript bridge exposed as window.TangSengSpeech in WebView pages.
 */
public class TangSengSpeechBridge {
    private final WebSpeechInputHelper helper;

    public TangSengSpeechBridge(WebSpeechInputHelper helper) {
        this.helper = helper;
    }

    /**
     * For page buttons that want the app to insert text into the currently focused input.
     * JS: window.TangSengSpeech.startSpeech()
     */
    @JavascriptInterface
    public void startSpeech() {
        if (helper != null) helper.startSpeechInput();
    }

    /**
     * For the injected SpeechRecognition polyfill.
     * It returns result through window.__TangSengSpeechNativeResult(text), without direct insertion.
     */
    @JavascriptInterface
    public void startRecognition() {
        if (helper != null) helper.startSpeechRecognitionForPage();
    }

    @JavascriptInterface
    public boolean isAvailable() {
        return helper != null;
    }
}
