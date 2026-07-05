package com.chat.userscript;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.lang.reflect.Method;

public class AiScriptWebActivity extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_START_PROMPT = "start_prompt";
    public static final String EXTRA_SCRIPT_MODE = "script_mode";

    private static final int REQ_NATIVE_RECORD_AUDIO = 7401;
    private static final int REQ_NATIVE_SPEECH_INTENT = 7402;
    private static final long NATIVE_SPEECH_TIMEOUT_MS = 120000L;

    private final Handler speechHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private SpeechRecognizer nativeRecognizer;
    private String pendingNativeSpeechLang = "zh-CN";
    private boolean nativeSpeechActive = false;
    private boolean nativeSpeechIntentActive = false;
    private boolean nativeSpeechPermissionPending = false;
    private final Runnable nativeSpeechTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!nativeSpeechActive) return;
            emitNativeSpeechEvent("error", "", "timeout");
            finishNativeSpeech(true);
        }
    };
    private UserScriptController controller;
    private String currentScriptMode = "";

    public static void open(Context context, String title, String url) {
        open(context, title, url, null, null);
    }

    public static void open(Context context, String title, String url, String startPrompt) {
        open(context, title, url, startPrompt, null);
    }

    public static void open(Context context, String title, String url, String startPrompt, String scriptMode) {
        Intent intent = new Intent(context, AiScriptWebActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_START_PROMPT, startPrompt);
        intent.putExtra(EXTRA_SCRIPT_MODE, normalizeScriptModeValue(scriptMode));
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String url = getIntent().getStringExtra(EXTRA_URL);
        String startPrompt = getIntent().getStringExtra(EXTRA_START_PROMPT);
        String scriptMode = normalizeScriptModeValue(getIntent().getStringExtra(EXTRA_SCRIPT_MODE));
        currentScriptMode = scriptMode;
        if (title == null || title.length() == 0) title = "AI 网页";
        if (url == null || url.length() == 0) url = "https://chat.qwen.ai/";
        url = appendTsddMode(url, scriptMode);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        webView = new WebView(this);
        webView.addJavascriptInterface(new TsddSpeechBridge(this), "TsddSpeech");
        TsddNativeSpeechBridge nativeSpeechBridge = new TsddNativeSpeechBridge(this);
        webView.addJavascriptInterface(nativeSpeechBridge, "TsddNativeSpeech");
        webView.addJavascriptInterface(nativeSpeechBridge, "TsddVoiceBridge");
        webView.addJavascriptInterface(nativeSpeechBridge, "TsddVoice");
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        // 不再创建顶部“关闭 / 刷新 / 脚本”工具栏，也不再添加顶部透明点击区。
        // 所有 AI 网页都使用系统返回键 / 手势返回，避免顶部工具栏多拦一层返回。

        controller = new UserScriptController(this, webView);
        controller.setStartupPrompt(startPrompt);
        controller.setScriptMode(scriptMode);
        controller.attach();
        controller.loadUrl(url);
    }


    private static String normalizeScriptModeValue(String mode) {
        if (mode == null) return "";
        String value = mode.trim().toLowerCase();
        if (value.length() == 0) return "";
        if (value.length() > 40) value = value.substring(0, 40);
        value = value.replaceAll("[^a-z0-9_-]", "");
        return value;
    }

    private String appendTsddMode(String url, String mode) {
        mode = normalizeScriptModeValue(mode);
        if (url == null) url = "";
        if (mode.length() == 0) return url;
        if (url.indexOf("tsdd_mode=") >= 0) return url;

        String host = "";
        try {
            host = Uri.parse(url).getHost();
            host = host == null ? "" : host.toLowerCase();
        } catch (Throwable ignored) {
        }

        // 目前入口绑定主要用于 DeepSeek；其他网页不用强塞 hash，避免影响登录/跳转。
        boolean isDeepSeek = host.equals("chat.deepseek.com") || host.endsWith(".deepseek.com");
        if (!isDeepSeek) return url;

        String encoded = Uri.encode(mode);
        int hashIndex = url.indexOf('#');
        if (hashIndex >= 0) {
            if (url.endsWith("#") || url.endsWith("&") || url.endsWith("?")) {
                return url + "tsdd_mode=" + encoded;
            }
            return url + "&tsdd_mode=" + encoded;
        }
        return url + "#tsdd_mode=" + encoded;
    }

    private boolean isQuestionMode() {
        return "question".equals(currentScriptMode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (handleNativeSpeechPermissionResult(requestCode, grantResults)) return;
        if (controller != null) controller.handleRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_NATIVE_SPEECH_INTENT) {
            handleNativeSpeechIntentResult(resultCode, data);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controller != null) controller.reinjectCurrentPage();
    }

    @Override
    protected void onPause() {
        stopSpeechFromWeb();
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopSpeechFromWeb();
        cancelNativeSpeechFromWeb();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        // 互动题/题目解析入口是临时页：返回手势直接退出 DeepSeek，回到题目页，
        // 不要先回到 DeepSeek 首页/历史页。
        if (isQuestionMode()) {
            stopSpeechFromWeb();
            finish();
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        stopSpeechFromWeb();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        speechHandler.removeCallbacksAndMessages(null);
        releaseNativeSpeechRecognizer();
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
        super.onDestroy();
    }


    private void startNativeSpeechFromWeb(String lang) {
        if (!isSpeechHostAllowed()) {
            emitNativeSpeechEvent("error", "", "not-allowed");
            emitNativeSpeechEvent("end", "", "");
            Toast.makeText(this, "当前网页不允许调用唐僧语音识别", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingNativeSpeechLang = normalizeSpeechLang(lang);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            nativeSpeechPermissionPending = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_NATIVE_RECORD_AUDIO);
            return;
        }

        beginNativeSpeech(pendingNativeSpeechLang);
    }

    private boolean handleNativeSpeechPermissionResult(int requestCode, int[] grantResults) {
        if (requestCode != REQ_NATIVE_RECORD_AUDIO) return false;

        boolean granted = grantResults != null
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (nativeSpeechPermissionPending) {
            nativeSpeechPermissionPending = false;
            if (granted) {
                beginNativeSpeech(pendingNativeSpeechLang);
            } else {
                emitNativeSpeechEvent("error", "", "not-allowed");
                emitNativeSpeechEvent("end", "", "");
                Toast.makeText(this, "请先允许麦克风权限", Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    private void beginNativeSpeech(String lang) {
        cancelNativeSpeechFromWeb();
        pendingNativeSpeechLang = normalizeSpeechLang(lang);
        nativeSpeechActive = true;
        nativeSpeechIntentActive = false;
        speechHandler.removeCallbacks(nativeSpeechTimeoutRunnable);
        speechHandler.postDelayed(nativeSpeechTimeoutRunnable, NATIVE_SPEECH_TIMEOUT_MS);

        emitNativeSpeechEvent("start", "", "");

        if (startSpeechRecognizerFallback(pendingNativeSpeechLang)) {
            return;
        }

        if (startSystemSpeechIntent(pendingNativeSpeechLang)) {
            return;
        }

        emitNativeSpeechEvent("error", "", "no-service");
        finishNativeSpeech(true);
        Toast.makeText(this, "当前设备没有可用的系统语音识别", Toast.LENGTH_SHORT).show();
    }

    private boolean startSystemSpeechIntent(String lang) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请讲话");

        if (intent.resolveActivity(getPackageManager()) == null) {
            return false;
        }

        try {
            nativeSpeechIntentActive = true;
            startActivityForResult(intent, REQ_NATIVE_SPEECH_INTENT);
            return true;
        } catch (ActivityNotFoundException e) {
            nativeSpeechIntentActive = false;
            return false;
        } catch (Throwable e) {
            nativeSpeechIntentActive = false;
            return false;
        }
    }

    private boolean startSpeechRecognizerFallback(String lang) {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) return false;
            releaseNativeSpeechRecognizer();
            nativeRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            nativeRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
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
                    emitNativeSpeechEvent("error", "", speechErrorName(error));
                    finishNativeSpeech(true);
                }

                @Override
                public void onResults(Bundle results) {
                    String text = firstSpeechResult(results);
                    if (text.length() > 0) {
                        emitNativeSpeechEvent("final", text, "");
                    } else {
                        emitNativeSpeechEvent("error", "", "no-match");
                    }
                    finishNativeSpeech(true);
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    String text = firstSpeechResult(partialResults);
                    if (text.length() > 0) emitNativeSpeechEvent("partial", text, "");
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2600L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30000L);
            nativeRecognizer.startListening(intent);
            return true;
        } catch (Throwable e) {
            releaseNativeSpeechRecognizer();
            return false;
        }
    }

    private void handleNativeSpeechIntentResult(int resultCode, Intent data) {
        nativeSpeechIntentActive = false;

        if (!nativeSpeechActive) {
            return;
        }

        String text = "";
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (list != null && !list.isEmpty() && list.get(0) != null) {
                text = list.get(0).trim();
            }
        }

        if (text.length() > 0) {
            emitNativeSpeechEvent("final", text, "");
        } else {
            emitNativeSpeechEvent("error", "", resultCode == RESULT_CANCELED ? "aborted" : "no-match");
        }
        finishNativeSpeech(true);
    }

    private void stopNativeSpeechFromWeb() {
        if (!nativeSpeechActive) {
            emitNativeSpeechEvent("end", "", "");
            return;
        }

        if (nativeSpeechIntentActive) {
            try {
                finishActivity(REQ_NATIVE_SPEECH_INTENT);
            } catch (Throwable ignored) {
            }
            return;
        }

        try {
            if (nativeRecognizer != null) nativeRecognizer.stopListening();
        } catch (Throwable ignored) {
            finishNativeSpeech(true);
        }
    }

    private void cancelNativeSpeechFromWeb() {
        if (!nativeSpeechActive && nativeRecognizer == null) return;

        try {
            if (nativeSpeechIntentActive) finishActivity(REQ_NATIVE_SPEECH_INTENT);
        } catch (Throwable ignored) {
        }

        try {
            if (nativeRecognizer != null) nativeRecognizer.cancel();
        } catch (Throwable ignored) {
        }

        finishNativeSpeech(true);
    }

    private void finishNativeSpeech(boolean emitEnd) {
        nativeSpeechActive = false;
        nativeSpeechIntentActive = false;
        speechHandler.removeCallbacks(nativeSpeechTimeoutRunnable);
        releaseNativeSpeechRecognizer();
        if (emitEnd) emitNativeSpeechEvent("end", "", "");
    }

    private void releaseNativeSpeechRecognizer() {
        if (nativeRecognizer != null) {
            try {
                nativeRecognizer.destroy();
            } catch (Throwable ignored) {
            }
            nativeRecognizer = null;
        }
    }

    private void emitNativeSpeechEvent(String type, String text, String error) {
        if (webView == null) return;
        String js = "window.__TS_DD_NATIVE_SPEECH_DISPATCH__&&window.__TS_DD_NATIVE_SPEECH_DISPATCH__(" +
                JSONObject.quote(type == null ? "" : type) + "," +
                JSONObject.quote(text == null ? "" : text) + "," +
                JSONObject.quote(error == null ? "" : error) + ");";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Throwable ignored) {
        }
    }

    private String normalizeSpeechLang(String lang) {
        String value = lang == null ? "" : lang.trim();
        if (value.length() == 0) return "zh-CN";
        if (value.length() > 20) value = value.substring(0, 20);
        return value;
    }

    private String firstSpeechResult(Bundle results) {
        if (results == null) return "";
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty() || list.get(0) == null) return "";
        return list.get(0).trim();
    }

    private String speechErrorName(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "audio";
            case SpeechRecognizer.ERROR_CLIENT:
                return "client";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "not-allowed";
            case SpeechRecognizer.ERROR_NETWORK:
                return "network";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "network-timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "no-match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "server";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "no-speech";
            default:
                return "error-" + error;
        }
    }


    private void speakFromWeb(String text) {
        speakTextFromWeb(text);
    }

    private void speakTextFromWeb(String text) {
        if (!isSpeechHostAllowed()) {
            Toast.makeText(this, "Current page cannot use Tsdd speech", Toast.LENGTH_SHORT).show();
            return;
        }

        text = safeSpeechText(text);
        if (text.length() == 0) {
            Toast.makeText(this, "No text to speak", Toast.LENGTH_SHORT).show();
            return;
        }

        if (callSpeechManager("speak", new Class[]{Context.class, String.class}, new Object[]{this, text})) {
            return;
        }
        Toast.makeText(this, "wkspeech is not available", Toast.LENGTH_SHORT).show();
    }

    private void speakJsonFromWeb(String json, String mode) {
        if (!isSpeechHostAllowed()) {
            Toast.makeText(this, "Current page cannot use Tsdd speech", Toast.LENGTH_SHORT).show();
            return;
        }

        String safeJson = json == null ? "{}" : json.trim();
        String text = "";
        try {
            JSONObject obj = new JSONObject(safeJson.length() == 0 ? "{}" : safeJson);
            text = obj.optString("text", "");
        } catch (Throwable ignored) {
        }

        if (safeJson.length() > 60000) {
            speakTextFromWeb(text);
            return;
        }

        boolean stream = "stream".equals(mode);
        boolean mixed = "mixed".equals(mode);

        if (stream && callSpeechManager("speakStream", new Class[]{Context.class, String.class}, new Object[]{this, safeJson})) {
            return;
        }
        if (mixed && callSpeechManager("speakMixed", new Class[]{Context.class, String.class}, new Object[]{this, safeJson})) {
            return;
        }
        if (callSpeechManager("speakJson", new Class[]{Context.class, String.class}, new Object[]{this, safeJson})) {
            return;
        }
        if (stream && callSpeechManager("speakJson", new Class[]{Context.class, String.class, boolean.class}, new Object[]{this, safeJson, true})) {
            return;
        }
        if (mixed && callSpeechManager("speakJson", new Class[]{Context.class, String.class, boolean.class}, new Object[]{this, safeJson, true})) {
            return;
        }

        speakTextFromWeb(text);
    }

    private void stopSpeechFromWeb() {
        if (!isSpeechHostAllowed()) return;
        if (callSpeechManager("stop", new Class[]{Context.class}, new Object[]{this})) return;
        callSpeechManager("stop", new Class[]{}, new Object[]{});
    }

    private String safeSpeechText(String text) {
        if (text == null) return "";
        String value = text.replace('\u00A0', ' ').trim();
        if (value.length() > 8000) value = value.substring(0, 8000);
        return value;
    }

    private boolean callSpeechManager(String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Class<?> speechManager = Class.forName("com.chat.speech.SpeechManager");
            Method method = speechManager.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isWkSpeechAvailable() {
        try {
            Class.forName("com.chat.speech.SpeechManager");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isNativeSpeechAvailable() {
        if (!isSpeechHostAllowed()) return false;
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) return true;
        } catch (Throwable ignored) {
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            return intent.resolveActivity(getPackageManager()) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String nativeSpeechEngineName() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) return "system-speechrecognizer";
        } catch (Throwable ignored) {
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            if (intent.resolveActivity(getPackageManager()) != null) return "system-intent";
        } catch (Throwable ignored) {
        }
        return "none";
    }

    private boolean isSpeechHostAllowed() {
        if (webView == null) return false;
        try {
            String currentUrl = webView.getUrl();
            if (currentUrl == null || currentUrl.length() == 0) return false;
            String host = Uri.parse(currentUrl).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.equals("chat.qwen.ai")
                    || host.equals("qwen.ai")
                    || host.endsWith(".qwen.ai")
                    || host.equals("tongyi.aliyun.com")
                    || host.endsWith(".tongyi.aliyun.com")
                    || host.equals("qianwen.com")
                    || host.endsWith(".qianwen.com")
                    || host.equals("chat.deepseek.com")
                    || host.endsWith(".deepseek.com");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class TsddSpeechBridge {
        private final WeakReference<AiScriptWebActivity> activityRef;

        TsddSpeechBridge(AiScriptWebActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @JavascriptInterface
        public boolean isAvailable() {
            AiScriptWebActivity activity = activityRef.get();
            return activity != null && activity.isSpeechHostAllowed() && activity.isWkSpeechAvailable();
        }

        @JavascriptInterface
        public String engine() {
            return isAvailable() ? "wkspeech" : "none";
        }

        @JavascriptInterface
        public void speak(String text) {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(() -> activity.speakTextFromWeb(text));
        }

        @JavascriptInterface
        public void speakJson(String json) {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(() -> activity.speakJsonFromWeb(json, "json"));
        }

        @JavascriptInterface
        public void speakStream(String json) {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(() -> activity.speakJsonFromWeb(json, "stream"));
        }

        @JavascriptInterface
        public void speakMixed(String json) {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(() -> activity.speakJsonFromWeb(json, "mixed"));
        }

        @JavascriptInterface
        public void stop() {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(activity::stopSpeechFromWeb);
        }
    }

    private static final class TsddNativeSpeechBridge {
        private final WeakReference<AiScriptWebActivity> activityRef;

        TsddNativeSpeechBridge(AiScriptWebActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @JavascriptInterface
        public boolean isAvailable() {
            AiScriptWebActivity activity = activityRef.get();
            return activity != null && activity.isNativeSpeechAvailable();
        }

        @JavascriptInterface
        public String engine() {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return "none";
            return activity.nativeSpeechEngineName();
        }

        @JavascriptInterface
        public void startSpeech(String lang) {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(() -> activity.startNativeSpeechFromWeb(lang));
        }

        @JavascriptInterface
        public void startSpeechJson(String json) {
            String lang = "zh-CN";
            try {
                JSONObject obj = new JSONObject(json == null ? "{}" : json);
                lang = obj.optString("lang", lang);
            } catch (Throwable ignored) {
            }
            startSpeech(lang);
        }

        @JavascriptInterface
        public void stopSpeech() {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(activity::stopNativeSpeechFromWeb);
        }

        @JavascriptInterface
        public void cancelSpeech() {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(activity::cancelNativeSpeechFromWeb);
        }
    }

}
