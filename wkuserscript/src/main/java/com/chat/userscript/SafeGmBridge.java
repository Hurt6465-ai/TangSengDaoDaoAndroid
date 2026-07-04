package com.chat.userscript;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.chat.userscript.model.UserScript;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

public class SafeGmBridge {
    private final Context appContext;
    private final UserScriptStore store;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String secret;
    private final WeakReference<WebView> webViewRef;

    public SafeGmBridge(Context context, WebView webView, String secret) {
        this.appContext = context.getApplicationContext();
        this.store = UserScriptStore.get(context);
        this.secret = secret;
        this.webViewRef = new WeakReference<>(webView);
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
            Toast.makeText(appContext, "已复制", Toast.LENGTH_SHORT).show();
        });
        return true;
    }

    @JavascriptInterface
    public boolean xmlHttpRequest(String inputSecret, String scriptId, String callbackId, String requestJson) {
        if (!checkSecret(inputSecret) || TextUtils.isEmpty(scriptId) || TextUtils.isEmpty(callbackId)) return false;
        UserScript script = store.getById(scriptId);
        if (script == null || !script.enabled || !script.hasGrant("GM_xmlhttpRequest") || !script.networkAllowed) {
            sendXhrResult(callbackId, errorResponse(0, "GM_xmlhttpRequest disabled"));
            return false;
        }
        try {
            JSONObject req = new JSONObject(requestJson == null ? "{}" : requestJson);
            String url = req.optString("url", "");
            if (!AiWebPolicy.isConnectAllowedByMeta(url, script.connects)) {
                sendXhrResult(callbackId, errorResponse(0, "URL not allowed"));
                return false;
            }
            new Thread(() -> executeRequest(callbackId, req)).start();
            return true;
        } catch (Exception e) {
            sendXhrResult(callbackId, errorResponse(0, e.getMessage()));
            return false;
        }
    }

    private void executeRequest(String callbackId, JSONObject req) {
        HttpURLConnection connection = null;
        try {
            String method = req.optString("method", "GET").toUpperCase(java.util.Locale.US);
            String urlValue = req.optString("url", "");
            int timeout = req.optInt("timeout", 15000);
            URL url = new URL(urlValue);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeout <= 0 ? 15000 : timeout);
            connection.setReadTimeout(timeout <= 0 ? 15000 : timeout);
            connection.setInstanceFollowRedirects(false);
            JSONObject headers = req.optJSONObject("headers");
            if (headers != null) {
                Iterator<String> iterator = headers.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    if (!isBlockedHeader(key)) connection.setRequestProperty(key, headers.optString(key, ""));
                }
            }
            String data = req.optString("data", null);
            if (data != null && data.length() > 0 && !("GET".equals(method) || "HEAD".equals(method))) {
                connection.setDoOutput(true);
                byte[] bytes = data.getBytes("UTF-8");
                connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                OutputStream out = connection.getOutputStream();
                out.write(bytes);
                out.flush();
                out.close();
            }
            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readAll(in);
            JSONObject res = new JSONObject();
            res.put("ok", status >= 200 && status < 400);
            res.put("status", status);
            res.put("statusText", connection.getResponseMessage());
            res.put("responseText", body == null ? "" : body);
            res.put("finalUrl", urlValue);
            sendXhrResult(callbackId, res);
        } catch (Exception e) {
            sendXhrResult(callbackId, errorResponse(0, e.getMessage()));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isBlockedHeader(String key) {
        if (key == null) return true;
        String k = key.toLowerCase(java.util.Locale.US);
        return "cookie".equals(k) || "authorization".equals(k) || "host".equals(k) || k.startsWith("proxy-") || k.startsWith("sec-");
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }

    private JSONObject errorResponse(int status, String message) {
        JSONObject object = new JSONObject();
        try {
            object.put("ok", false);
            object.put("status", status);
            object.put("statusText", message == null ? "error" : message);
            object.put("responseText", "");
        } catch (Exception ignored) {
        }
        return object;
    }

    private void sendXhrResult(String callbackId, JSONObject result) {
        WebView webView = webViewRef.get();
        if (webView == null) return;
        String js = "window.__TS_DD_GM_XHR_DONE__&&window.__TS_DD_GM_XHR_DONE__(" + JSONObject.quote(callbackId) + "," + result.toString() + ");";
        mainHandler.post(() -> {
            try {
                webView.evaluateJavascript(js, null);
            } catch (Exception ignored) {
            }
        });
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
