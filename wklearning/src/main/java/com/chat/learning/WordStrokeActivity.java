package com.chat.learning;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.HorizontalScrollView;
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

/**
 * Full-screen Hanzi Writer stroke animation page.
 *
 * The Hanzi Writer runtime is bundled in assets. Character JSON is fetched from the pinned
 * hanzi-writer-data CDN on first use and then stored in the app's private files directory.
 * No JavaScript bridge is exposed to the page.
 */
public class WordStrokeActivity extends AppCompatActivity {
    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_PINYIN = "pinyin";

    private static final String LOCAL_HOST = "hanzi.local";
    private static final String DATA_CDN =
            "https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1/";
    private static final int MAX_CHARACTER_DATA_BYTES = 1024 * 1024;

    private static final int COLOR_BG_TOP = 0xFFF8FAFD;
    private static final int COLOR_BG_BOTTOM = 0xFFF0F4FA;
    private static final int COLOR_TEXT = 0xFF151922;
    private static final int COLOR_SUB = 0xFF747D8A;
    private static final int COLOR_ACCENT = 0xFF7067E8;

    private String word;
    private String pinyin;
    private WebView webView;
    private boolean pageReady;
    private String selectedCharacter = "";
    private final List<TextView> characterButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG_TOP);
        window.setNavigationBarColor(COLOR_BG_BOTTOM);

        word = safe(getIntent().getStringExtra(EXTRA_WORD), "你");
        pinyin = safe(getIntent().getStringExtra(EXTRA_PINYIN), "");
        buildLayout();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM}));
        setContentView(root);

        TextView title = text(getString(R.string.stroke_title), 22, COLOR_TEXT, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text(getString(R.string.stroke_subtitle), 13, COLOR_SUB, false);
        subtitle.setLineSpacing(dp(3), 1.05f);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.setMargins(0, dp(6), 0, dp(14));
        root.addView(subtitle, subtitleLp);

        TextView wordView = text(word, 34, COLOR_TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        root.addView(wordView, new LinearLayout.LayoutParams(-1, -2));

        if (!pinyin.isEmpty()) {
            TextView pinyinView = text(pinyin, 16, COLOR_ACCENT, true);
            pinyinView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pinyinLp = new LinearLayout.LayoutParams(-1, -2);
            pinyinLp.setMargins(0, dp(5), 0, dp(12));
            root.addView(pinyinView, pinyinLp);
        }

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chars = new LinearLayout(this);
        chars.setOrientation(LinearLayout.HORIZONTAL);
        chars.setGravity(Gravity.CENTER);
        chars.setPadding(dp(2), 0, dp(2), 0);
        scroller.addView(chars, new HorizontalScrollView.LayoutParams(-2, dp(42)));
        LinearLayout.LayoutParams scrollerLp = new LinearLayout.LayoutParams(-1, dp(42));
        scrollerLp.setMargins(0, 0, 0, dp(12));
        root.addView(scroller, scrollerLp);
        populateCharacterButtons(chars);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);
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
        webView.setWebViewClient(new StrokeWebViewClient());

        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        webLp.setMargins(0, 0, 0, dp(14));
        root.addView(webView, webLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        TextView replay = actionButton(getString(R.string.stroke_replay), 0xFFF0F1F4, 0xFF5D6570);
        replay.setOnClickListener(v -> replay());
        actions.addView(replay, new LinearLayout.LayoutParams(0, dp(46), 1f));

        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        TextView speak = actionButton(getString(R.string.stroke_speak_word), 0xFFECEBFF, COLOR_ACCENT);
        speak.setOnClickListener(v -> LearningTtsBridge.speak(
                this, word, LearningTtsBridge.LANG_ZH_CN, LearningTtsBridge.MODE_WORD));
        actions.addView(speak, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(46)));

        webView.loadUrl("https://" + LOCAL_HOST + "/stroke.html");
    }

    private void populateCharacterButtons(LinearLayout parent) {
        List<String> characters = extractChineseCharacters(word);
        if (characters.isEmpty()) characters.add("你");
        for (int i = 0; i < characters.size(); i++) {
            String character = characters.get(i);
            TextView button = text(character, 20, COLOR_TEXT, true);
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(v -> selectCharacter(character));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
            if (i > 0) lp.setMargins(dp(8), 0, 0, 0);
            parent.addView(button, lp);
            characterButtons.add(button);
        }
        selectedCharacter = characters.get(0);
        updateCharacterButtons();
    }

    private void selectCharacter(String character) {
        if (character == null || character.isEmpty()) return;
        selectedCharacter = character;
        updateCharacterButtons();
        showSelectedCharacter();
    }

    private void updateCharacterButtons() {
        for (TextView button : characterButtons) {
            boolean selected = selectedCharacter.contentEquals(button.getText());
            button.setTextColor(selected ? Color.WHITE : COLOR_TEXT);
            button.setBackground(rounded(
                    selected ? COLOR_ACCENT : 0xFFFFFFFF,
                    dp(13), selected ? 0 : 0xFFE3E6EB, selected ? 0 : dp(1)));
        }
    }

    private void showSelectedCharacter() {
        if (!pageReady || webView == null || selectedCharacter.isEmpty()) return;
        webView.evaluateJavascript(
                "setCharacter(" + JSONObject.quote(selectedCharacter) + ")", null);
    }

    private void replay() {
        if (!pageReady || webView == null) return;
        webView.evaluateJavascript("replayAnimation()", null);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
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
                    String character = Uri.decode(encoded);
                    return characterDataResponse(character);
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
                        value -> showSelectedCharacter());
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

    private TextView actionButton(String value, int background, int foreground) {
        TextView view = text(value, 14, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(background, dp(15), 0, 0));
        return view;
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
