package com.chat.base.web;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

/**
 * JavaScript bridge exposed as window.TangSengLocation in WebView pages.
 */
public class TangSengLocationBridge {
    private final WebLocationHelper helper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TangSengLocationBridge(WebLocationHelper helper) {
        this.helper = helper;
    }

    @JavascriptInterface
    public void requestLocation(String callbackId) {
        runOnMain(() -> {
            if (helper != null) helper.requestLocation(callbackId);
        });
    }

    @JavascriptInterface
    public String getLastLocation() {
        return helper == null ? "" : helper.getLastLocationJson();
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
