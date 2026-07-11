package com.chat.learning;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Centered Hanzi Writer popup. Tap outside to close. */
public class WordStrokeActivity extends AppCompatActivity {
    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_PINYIN = "pinyin";

    private static final String LOCAL_HOST = "hanzi.local";
    private static final String DATA_CDN =
            "https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1/";
    private static final int MAX_CHARACTER_DATA_BYTES = 1024 * 1024;

    private static final int COLOR_TEXT = 0xFF151922;
    private static final int COLOR_SUB = 0xFF747D8A;
    private static final int COLOR_ACCENT = 0xFF7067E8;

    private String word;
    private String pinyin;
    private WebView webView;
    private boolean pageReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        word = safe(getIntent().getStringExtra(EXTRA_WORD), "你");
        pinyin = safe(getIntent().getStringExtra(EXTRA_PINYIN), "");
        configureWindow();
        buildLayout();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = Build.VERSION.SDK_INT >= 31 ? 0.08f : 0.18f;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                attributes.getClass().getMethod("setBlurBehindRadius", int.class)
                        .invoke(attributes, dp(26));
            } catch (Throwable ignored) { }
        }
        window.setAttributes(attributes);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClickable(true);
        root.setOnClickListener(v -> finish());
        setContentView(root);

        LinearLayout popup = new LinearLayout(this);
        popup.setOrientation(LinearLayout.VERTICAL);
        popup.setPadding(dp(18), dp(16), dp(18), dp(18));
        popup.setBackground(rounded(0xF7FFFFFF, dp(26), 0xCCFFFFFF, dp(1)));
        popup.setElevation(dp(14));
        popup.setClickable(true);
        popup.setOnClickListener(v -> { });

        FrameLayout.LayoutParams popupLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        popupLp.setMargins(dp(18), dp(30), dp(18), dp(30));
        root.addView(popup, popupLp);

        FrameLayout header = new FrameLayout(this);
        TextView wordView = text(word, 27, COLOR_TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        header.addView(wordView, new FrameLayout.LayoutParams(-1, dp(38), Gravity.CENTER));

        TextView replay = text("↻", 23, COLOR_SUB, true);
        replay.setGravity(Gravity.CENTER);
        replay.setContentDescription(getString(R.string.stroke_replay));
        replay.setBackground(rounded(0xFFF2F3F5, dp(18), 0, 0));
        replay.setOnClickListener(v -> playAll());
        FrameLayout.LayoutParams replayLp = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(replay, replayLp);
        popup.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));

        if (!pinyin.isEmpty()) {
            TextView pinyinView = text(pinyin, 15, COLOR_ACCENT, true);
            pinyinView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pinyinLp = new LinearLayout.LayoutParams(-1, -2);
            pinyinLp.setMargins(0, dp(2), 0, dp(8));
            popup.addView(pinyinView, pinyinLp);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);
        webView.removeJavascriptInterface("searchBoxJavaBridge_");
        webView.removeJavascriptInterface("accessibility");
        webView.removeJavascriptInterface("accessibilityTraversal");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.addJavascriptInterface(new StrokeBridge(), "StrokeHost");
        webView.setWebViewClient(new StrokeWebViewClient());

        int count = Math.max(1, extractChineseCharacters(word).size());
        int webHeight = count <= 1 ? dp(260) : count <= 2 ? dp(220) : count <= 4 ? dp(300) : dp(340);
        popup.addView(webView, new LinearLayout.LayoutParams(-1, webHeight));
        webView.loadUrl("https://" + LOCAL_HOST + "/stroke.html");
    }

    private void playAll() {
        if (!pageReady || webView == null) return;
        webView.evaluateJavascript("playSequence(0)", null);
    }

    private void playCharacter(int index) {
        if (!pageReady || webView == null) return;
        webView.evaluateJavascript("playCharacter(" + Math.max(0, index) + ")", null);
    }

    private final class StrokeBridge {
        @JavascriptInterface
        public void onCharacterTapped(String character, int index) {
            if (character == null || character.isEmpty()) return;
            runOnUiThread(() -> {
                boolean speaking = LearningTtsBridge.speak(WordStrokeActivity.this, character,
                        LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_WORD);
                if (webView != null) webView.postDelayed(
                        () -> playCharacter(index), speaking ? 650L : 0L);
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("StrokeHost");
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private final class StrokeWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!LOCAL_HOST.equalsIgnoreCase(uri.getHost())) return blockedResponse();
            String path = uri.getPath() == null ? "" : uri.getPath();
            try {
                if ("/stroke.html".equals(path) || "/".equals(path)) {
                    return assetResponse("learning/hanzi/stroke.html", "text/html");
                }
                if ("/hanzi-writer.min.js".equals(path)) {
                    return assetResponse("learning/hanzi/hanzi-writer.min.js", "application/javascript");
                }
                if (path.startsWith("/data/") && path.endsWith(".json")) {
                    String encoded = path.substring("/data/".length(), path.length() - ".json".length());
                    return characterDataResponse(Uri.decode(encoded));
                }
            } catch (Throwable ignored) {
                return notFoundResponse();
            }
            return notFoundResponse();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url != null && url.startsWith("https://" + LOCAL_HOST + "/")) {
                pageReady = true;
                view.evaluateJavascript(
                        "setMessages(" + JSONObject.quote(getString(R.string.stroke_loading))
                                + "," + JSONObject.quote(getString(R.string.stroke_data_missing)) + ")",
                        value -> view.evaluateJavascript("setWord(" + JSONObject.quote(word) + ")", null));
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !LOCAL_HOST.equalsIgnoreCase(request.getUrl().getHost());
        }
    }

    private WebResourceResponse assetResponse(String assetPath, String mimeType) throws Exception {
        InputStream input = getAssets().open(assetPath);
        return response(mimeType, input, 200, "OK", "public, max-age=31536000");
    }

    private WebResourceResponse characterDataResponse(String character) throws Exception {
        if (character == null || character.isEmpty() || character.codePointCount(0, character.length()) != 1) {
            return notFoundResponse();
        }
        int codePoint = character.codePointAt(0);
        File directory = new File(getFilesDir(), "learning/hanzi-data");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Unable to create Hanzi cache directory");
        }
        File cached = new File(directory, Integer.toHexString(codePoint) + ".json");
        if (cached.isFile() && cached.length() > 20 && cached.length() <= MAX_CHARACTER_DATA_BYTES) {
            return response("application/json", new FileInputStream(cached), 200, "OK",
                    "public, max-age=31536000");
        }

        byte[] bytes = downloadCharacterData(character);
        File temp = new File(directory, cached.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (!temp.renameTo(cached)) {
            try (FileOutputStream output = new FileOutputStream(cached)) {
                output.write(bytes);
            }
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
        return response("application/json", new ByteArrayInputStream(bytes), 200, "OK",
                "public, max-age=31536000");
    }

    private byte[] downloadCharacterData(String character) throws Exception {
        URL url = new URL(DATA_CDN + Uri.encode(character) + ".json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "TangSengDaoDao-Learning/1.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_CHARACTER_DATA_BYTES) {
                        throw new IllegalStateException("Character data too large");
                    }
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                if (bytes.length < 20) throw new IllegalStateException("Empty character data");
                return bytes;
            }
        } finally {
            connection.disconnect();
        }
    }

    private WebResourceResponse blockedResponse() {
        return response("text/plain", new ByteArrayInputStream(new byte[0]), 403, "Forbidden", "no-store");
    }

    private WebResourceResponse notFoundResponse() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        return response("application/json", new ByteArrayInputStream(body), 404, "Not Found", "no-store");
    }

    private WebResourceResponse response(
            String mimeType,
            InputStream input,
            int status,
            String reason,
            String cacheControl) {
        WebResourceResponse response = new WebResourceResponse(mimeType, "UTF-8", input);
        response.setStatusCodeAndReasonPhrase(status, reason);
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", cacheControl);
        headers.put("Access-Control-Allow-Origin", "https://" + LOCAL_HOST);
        headers.put("X-Content-Type-Options", "nosniff");
        response.setResponseHeaders(headers);
        return response;
    }

    private List<String> extractChineseCharacters(String value) {
        ArrayList<String> result = new ArrayList<>();
        if (value == null) return result;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (isChinese(codePoint)) result.add(new String(Character.toChars(codePoint)));
            offset += Character.charCount(codePoint);
        }
        return result;
    }

    private boolean isChinese(int codePoint) {
        return (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x20000 && codePoint <= 0x2EBEF)
                || (codePoint >= 0x30000 && codePoint <= 0x323AF);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
