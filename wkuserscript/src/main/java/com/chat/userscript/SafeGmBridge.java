package com.chat.userscript;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.chat.userscript.model.UserScript;

public class SafeGmBridge {
    private final Context appContext;
    private final UserScriptStore store;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String secret;

    public SafeGmBridge(Context context, String secret) {
        this.appContext = context.getApplicationContext();
        this.store = UserScriptStore.get(context);
        this.secret = secret;
    }

    @JavascriptInterface
    public String getValue(String inputSecret, String scriptId, String key, String defaultJson) {
        if (!check(inputSecret, scriptId, "GM_getValue")) return defaultJson == null ? "null" : defaultJson;
        return store.getScriptValue(scriptId, key, defaultJson);
    }

    @JavascriptInterface
    public boolean setValue(String inputSecret, String scriptId, String key, String valueJson) {
        if (!check(inputSecret, scriptId, "GM_setValue")) return false;
        store.setScriptValue(scriptId, key, valueJson);
        return true;
    }

    @JavascriptInterface
    public boolean deleteValue(String inputSecret, String scriptId, String key) {
        if (!check(inputSecret, scriptId, "GM_deleteValue")) return false;
        store.deleteScriptValue(scriptId, key);
        return true;
    }

    @JavascriptInterface
    public String listValues(String inputSecret, String scriptId) {
        if (!check(inputSecret, scriptId, "GM_listValues")) return "[]";
        return store.listScriptValues(scriptId);
    }

    @JavascriptInterface
    public void log(String inputSecret, String scriptId, String message) {
        if (!checkSecret(inputSecret)) return;
        android.util.Log.d("TsddUserScript", "[" + scriptId + "] " + message);
    }

    @JavascriptInterface
    public void notification(String inputSecret, String scriptId, String message) {
        if (!check(inputSecret, scriptId, "GM_notification")) return;
        final String text = TextUtils.isEmpty(message) ? "脚本通知" : message;
        mainHandler.post(() -> Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public boolean setClipboard(String inputSecret, String scriptId, String text) {
        if (!check(inputSecret, scriptId, "GM_setClipboard")) return false;
        mainHandler.post(() -> {
            ClipboardManager manager = (ClipboardManager) appContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("tsdd-script", text == null ? "" : text));
            Toast.makeText(appContext, "脚本已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
        return true;
    }

    private boolean check(String inputSecret, String scriptId, String grant) {
        if (!checkSecret(inputSecret) || TextUtils.isEmpty(scriptId)) return false;
        UserScript script = store.getById(scriptId);
        return script != null && script.enabled && script.hasGrant(grant);
    }

    private boolean checkSecret(String inputSecret) {
        return !TextUtils.isEmpty(secret) && secret.equals(inputSecret);
    }
}
