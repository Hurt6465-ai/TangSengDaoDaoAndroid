package com.chat.userscript;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AiScriptWebActivity extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_START_PROMPT = "start_prompt";

    private static final int REQ_NATIVE_RECORD_AUDIO = 7402;
    private static final String GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String GOOGLE_RECOGNITION_SERVICE =
            "com.google.android.voicesearch.serviceapi.GoogleRecognitionService";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private UserScriptController controller;
    private View toolbar;
    private boolean toolbarVisible = true;
    private NativeSpeechBridge nativeSpeechBridge;
    private SpeechRecognizer nativeSpeechRecognizer;
    private boolean nativeSpeechUsingGoogle;
    private boolean nativeSpeechTriedDefault;
    private String nativeSpeechLang = "zh-CN";
    private String pendingNativeSpeechLang = "";
    private int nativeSpeechSessionId = 0;
    private Runnable nativeSpeechWatchdog;

    public static void open(Context context, String title, String url) {
        open(context, title, url, null);
    }

    public static void open(Context context, String title, String url, String startPrompt) {
        Intent intent = new Intent(context, AiScriptWebActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_START_PROMPT, startPrompt);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String url = getIntent().getStringExtra(EXTRA_URL);
        String startPrompt = getIntent().getStringExtra(EXTRA_START_PROMPT);
        if (title == null || title.length() == 0) title = "AI 网页";
        if (url == null || url.length() == 0) url = "https://chat.qwen.ai/";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        webView = new WebView(this);
        webView.addJavascriptInterface(new TsddSpeechBridge(this), "TsddSpeech");

        nativeSpeechBridge = new NativeSpeechBridge(this);
        webView.addJavascriptInterface(nativeSpeechBridge, "TsddNativeSpeech");
        // Compatibility aliases for different injected scripts.
        webView.addJavascriptInterface(nativeSpeechBridge, "TsddVoiceBridge");
        webView.addJavascriptInterface(nativeSpeechBridge, "TsddVoice");

        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        toolbar = buildToolbar(title);
        root.addView(toolbar, new FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP));

        View topHit = new View(this);
        topHit.setBackgroundColor(Color.TRANSPARENT);
        topHit.setOnClickListener(v -> showToolbarTemporarily());
        root.addView(topHit, new FrameLayout.LayoutParams(-1, dp(28), Gravity.TOP));

        controller = new UserScriptController(this, webView);
        controller.setStartupPrompt(startPrompt);
        controller.attach();
        controller.loadUrl(url);
        handler.postDelayed(this::hideToolbar, 1800L);
    }

    private View buildToolbar(String title) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(0xF9FFFFFF);
        bar.setElevation(dp(2));

        TextView close = toolbarButton(getString(R.string.script_close));
        bar.addView(close, new LinearLayout.LayoutParams(dp(56), -1));
        close.setOnClickListener(v -> finish());

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.rgb(17, 24, 39));
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1));

        TextView refresh = toolbarButton(getString(R.string.script_refresh));
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(56), -1));
        refresh.setOnClickListener(v -> {
            if (webView != null) webView.reload();
            showToolbarTemporarily();
        });

        TextView scripts = toolbarButton(getString(R.string.script_scripts));
        bar.addView(scripts, new LinearLayout.LayoutParams(dp(56), -1));
        scripts.setOnClickListener(v -> {
            startActivity(new Intent(this, ScriptManagerActivity.class));
            showToolbarTemporarily();
        });
        return bar;
    }

    private void showToolbarTemporarily() {
        showToolbar();
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::hideToolbar, 2200L);
    }

    private void showToolbar() {
        if (toolbar == null || toolbarVisible) return;
        toolbarVisible = true;
        toolbar.animate().translationY(0).alpha(1f).setDuration(180).start();
    }

    private void hideToolbar() {
        if (toolbar == null || !toolbarVisible) return;
        toolbarVisible = false;
        toolbar.animate().translationY(-dp(56)).alpha(0f).setDuration(220).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_NATIVE_RECORD_AUDIO) {
            boolean granted = grantResults != null
                    && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                String lang = pendingNativeSpeechLang == null || pendingNativeSpeechLang.length() == 0
                        ? "zh-CN"
                        : pendingNativeSpeechLang;
                pendingNativeSpeechLang = "";
                startNativeSpeechInternal(lang, false);
            } else {
                pendingNativeSpeechLang = "";
                emitNativeSpeechEvent("error", "", "permission-denied", "没有麦克风权限", 0f);
                emitNativeSpeechEvent("end", "", "", "", 0f);
            }
            return;
        }

        if (controller != null) controller.handleRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controller != null) controller.reinjectCurrentPage();
    }

    @Override
    public void onBackPressed() {
        if (toolbar != null && !toolbarVisible) {
            showToolbarTemporarily();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        destroyNativeSpeechRecognizer();
        nativeSpeechBridge = null;
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
            emitNativeSpeechEvent("error", "", "host-not-allowed", "当前网页不允许调用原生语音识别", 0f);
            emitNativeSpeechEvent("end", "", "", "", 0f);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingNativeSpeechLang = normalizeSpeechLang(lang);
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_NATIVE_RECORD_AUDIO);
            return;
        }

        startNativeSpeechInternal(lang, false);
    }

    private void startNativeSpeechInternal(String lang, boolean forceDefault) {
        nativeSpeechLang = normalizeSpeechLang(lang);

        destroyNativeSpeechRecognizer();

        if (!forceDefault) {
            nativeSpeechTriedDefault = false;
        }

        ComponentName googleComponent = forceDefault ? null : findGoogleRecognitionComponent();
        nativeSpeechUsingGoogle = googleComponent != null;

        try {
            if (nativeSpeechUsingGoogle) {
                nativeSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this, googleComponent);
            } else {
                nativeSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                nativeSpeechUsingGoogle = false;
                nativeSpeechTriedDefault = true;
            }
        } catch (Throwable createError) {
            if (nativeSpeechUsingGoogle) {
                nativeSpeechUsingGoogle = false;
                nativeSpeechTriedDefault = true;
                try {
                    nativeSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                } catch (Throwable fallbackError) {
                    emitNativeSpeechEvent("error", "", "create-failed", "无法创建语音识别服务", 0f);
                    emitNativeSpeechEvent("end", "", "", "", 0f);
                    return;
                }
            } else {
                emitNativeSpeechEvent("error", "", "create-failed", "无法创建语音识别服务", 0f);
                emitNativeSpeechEvent("end", "", "", "", 0f);
                return;
            }
        }

        final int sessionId = ++nativeSpeechSessionId;
        scheduleNativeSpeechWatchdog(sessionId, 14000L, "识别超时，请检查 Google App、网络、麦克风权限，或先切到中文/英文测试");

        nativeSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                emitNativeSpeechEvent("start", "", "", "", 0f);
                scheduleNativeSpeechWatchdog(sessionId, 14000L, "识别超时，请确认已说话，或检查 Google App/网络/麦克风权限");
            }

            @Override
            public void onBeginningOfSpeech() {
                scheduleNativeSpeechWatchdog(sessionId, 12000L, "已听到声音，但没有返回文字，请换中文/英文测试或检查当前语言是否支持");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                scheduleNativeSpeechWatchdog(sessionId, 3500L, "语音结束后没有返回识别结果，请重试");
            }

            @Override
            public void onError(int error) {
                cancelNativeSpeechWatchdog();

                if (shouldRetryWithDefaultRecognizer(error)) {
                    startNativeSpeechInternal(nativeSpeechLang, true);
                    return;
                }

                emitNativeSpeechEvent(
                        "error",
                        "",
                        String.valueOf(error),
                        speechErrorText(error),
                        0f
                );
                emitNativeSpeechEvent("end", "", "", "", 0f);
                destroyNativeSpeechRecognizer();
            }

            @Override
            public void onResults(Bundle results) {
                cancelNativeSpeechWatchdog();
                emitBestSpeechText("final", results);
                emitNativeSpeechEvent("end", "", "", "", 0f);
                destroyNativeSpeechRecognizer();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                emitBestSpeechText("partial", partialResults);
                scheduleNativeSpeechWatchdog(sessionId, 9000L, "识别长时间没有最终结果，请点停止后重试");
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, nativeSpeechLang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, nativeSpeechLang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

        try {
            nativeSpeechRecognizer.startListening(intent);
        } catch (Throwable startError) {
            if (nativeSpeechUsingGoogle && !nativeSpeechTriedDefault) {
                startNativeSpeechInternal(nativeSpeechLang, true);
                return;
            }

            emitNativeSpeechEvent("error", "", "start-failed", "语音识别启动失败", 0f);
            emitNativeSpeechEvent("end", "", "", "", 0f);
            destroyNativeSpeechRecognizer();
        }
    }

    private void stopNativeSpeechFromWeb() {
        cancelNativeSpeechWatchdog();
        try {
            if (nativeSpeechRecognizer != null) {
                nativeSpeechRecognizer.stopListening();
            }
        } catch (Throwable ignored) {
        }
    }

    private void cancelNativeSpeechFromWeb() {
        cancelNativeSpeechWatchdog();
        try {
            if (nativeSpeechRecognizer != null) {
                nativeSpeechRecognizer.cancel();
            }
        } catch (Throwable ignored) {
        }
        destroyNativeSpeechRecognizer();
        emitNativeSpeechEvent("end", "", "", "", 0f);
    }

    private ComponentName findGoogleRecognitionComponent() {
        if (!isPackageInstalled(GOOGLE_APP_PACKAGE)) return null;

        try {
            Intent serviceIntent = new Intent(RecognitionService.SERVICE_INTERFACE);
            serviceIntent.setPackage(GOOGLE_APP_PACKAGE);
            List<ResolveInfo> services = getPackageManager().queryIntentServices(
                    serviceIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
            );

            if (services != null) {
                for (ResolveInfo info : services) {
                    if (info == null || info.serviceInfo == null) continue;
                    if (!GOOGLE_APP_PACKAGE.equals(info.serviceInfo.packageName)) continue;
                    return new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
                }
            }
        } catch (Throwable ignored) {
        }

        // Some Google App versions do not expose the service to package queries, but this legacy
        // component still works on many devices. Keep it as a fallback, not the first choice.
        return new ComponentName(GOOGLE_APP_PACKAGE, GOOGLE_RECOGNITION_SERVICE);
    }

    private boolean shouldRetryWithDefaultRecognizer(int error) {
        if (!nativeSpeechUsingGoogle || nativeSpeechTriedDefault) return false;

        return error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_SERVER
                || error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
    }

    private boolean isNativeSpeechAvailable() {
        return isPackageInstalled(GOOGLE_APP_PACKAGE) || SpeechRecognizer.isRecognitionAvailable(this);
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String normalizeSpeechLang(String lang) {
        if (lang == null) return "zh-CN";

        lang = lang.trim();
        if (lang.length() == 0) return "zh-CN";

        // Keep BCP-47 language tags such as zh-CN, my-MM, en-US.
        if (lang.length() > 16) return lang.substring(0, 16);
        return lang;
    }

    private void scheduleNativeSpeechWatchdog(final int sessionId, long delayMs, final String message) {
        cancelNativeSpeechWatchdog();

        nativeSpeechWatchdog = () -> {
            if (sessionId != nativeSpeechSessionId || nativeSpeechRecognizer == null) return;

            emitNativeSpeechEvent("error", "", "timeout", message == null ? "识别超时" : message, 0f);
            emitNativeSpeechEvent("end", "", "", "", 0f);
            destroyNativeSpeechRecognizer();
        };

        handler.postDelayed(nativeSpeechWatchdog, Math.max(2500L, delayMs));
    }

    private void cancelNativeSpeechWatchdog() {
        if (nativeSpeechWatchdog != null) {
            handler.removeCallbacks(nativeSpeechWatchdog);
            nativeSpeechWatchdog = null;
        }
    }

    private void emitBestSpeechText(String type, Bundle bundle) {
        if (bundle == null) return;

        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;

        String text = matches.get(0);
        if (text == null) text = "";

        float confidence = 0f;
        float[] scores = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (scores != null && scores.length > 0) confidence = scores[0];

        emitNativeSpeechEvent(type, text, "", "", confidence);
    }

    private String speechErrorText(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "录音失败";
            case SpeechRecognizer.ERROR_CLIENT:
                return nativeSpeechUsingGoogle ? "Google 语音服务不可用" : "语音服务不可用";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "没有麦克风权限";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "网络异常，语音识别失败";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "没有识别到语音";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "语音识别服务忙，请稍后再试";
            case SpeechRecognizer.ERROR_SERVER:
                return "语音识别服务异常";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "没有检测到说话";
            default:
                return "语音识别失败";
        }
    }

    private void destroyNativeSpeechRecognizer() {
        cancelNativeSpeechWatchdog();
        nativeSpeechSessionId++;
        if (nativeSpeechRecognizer == null) return;

        try {
            nativeSpeechRecognizer.cancel();
        } catch (Throwable ignored) {
        }

        try {
            nativeSpeechRecognizer.destroy();
        } catch (Throwable ignored) {
        }

        nativeSpeechRecognizer = null;
        nativeSpeechUsingGoogle = false;
    }

    private void emitNativeSpeechEvent(String type, String text, String error, String message, float confidence) {
        final String safeType = type == null ? "" : type;
        final String safeText = text == null ? "" : text;
        final String safeError = error == null ? "" : error;
        final String safeMessage = message == null ? "" : message;

        handler.post(() -> {
            if (webView == null) return;

            JSONObject obj = new JSONObject();
            try {
                obj.put("type", safeType);
                obj.put("text", safeText);
                obj.put("error", safeError);
                obj.put("message", safeMessage);
                obj.put("confidence", confidence);
                obj.put("usingGoogle", nativeSpeechUsingGoogle);
                obj.put("lang", nativeSpeechLang);
            } catch (Throwable ignored) {
            }

            String payload = JSONObject.quote(obj.toString());
            String js = "(function(){"
                    + "var p=JSON.parse(" + payload + ");"
                    + "try{if(window.__TS_DD_NATIVE_SPEECH_EVENT__)window.__TS_DD_NATIVE_SPEECH_EVENT__(p);}catch(e){}"
                    + "try{var h=window.__DSMT_NATIVE_SPEECH__;if(h){"
                    + "if(p.type==='start'&&h.onStart)h.onStart();"
                    + "else if(p.type==='partial'&&h.onPartial)h.onPartial(p.text||'');"
                    + "else if(p.type==='final'&&h.onFinal)h.onFinal(p.text||'');"
                    + "else if(p.type==='error'&&h.onError)h.onError(p.message||p.error||'语音识别失败');"
                    + "else if(p.type==='end'&&h.onEnd)h.onEnd();"
                    + "}}catch(e){}"
                    + "})();";

            try {
                webView.evaluateJavascript(js, null);
            } catch (Throwable ignored) {
            }
        });
    }


    private void speakFromWeb(String text) {
        if (!isSpeechHostAllowed()) {
            Toast.makeText(this, "当前网页不允许调用唐僧语音", Toast.LENGTH_SHORT).show();
            return;
        }

        if (text == null) {
            text = "";
        }
        text = text.replace('\u00A0', ' ').trim();
        if (text.length() == 0) {
            Toast.makeText(this, "没有可朗读内容", Toast.LENGTH_SHORT).show();
            return;
        }

        // 防止网页一次性塞入超长文本导致合成等待太久。
        if (text.length() > 8000) {
            text = text.substring(0, 8000);
        }

        try {
            Class<?> speechManager = Class.forName("com.chat.speech.SpeechManager");
            Method speak = speechManager.getMethod("speak", Context.class, String.class);
            speak.invoke(null, this, text);
        } catch (Throwable e) {
            Toast.makeText(this, "未接入 wkspeech，无法朗读", Toast.LENGTH_SHORT).show();
        }
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
                    || host.equals("chat.deepseek.com")
                    || host.endsWith(".deepseek.com");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class NativeSpeechBridge {
        private final WeakReference<AiScriptWebActivity> activityRef;

        NativeSpeechBridge(AiScriptWebActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @JavascriptInterface
        public void startSpeech(String lang) {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(() -> activity.startNativeSpeechFromWeb(lang));
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

        @JavascriptInterface
        public boolean isAvailable() {
            AiScriptWebActivity activity = activityRef.get();
            return activity != null && activity.isNativeSpeechAvailable();
        }

        @JavascriptInterface
        public boolean isGoogleAppInstalled() {
            AiScriptWebActivity activity = activityRef.get();
            return activity != null && activity.isPackageInstalled(GOOGLE_APP_PACKAGE);
        }
    }

    private static final class TsddSpeechBridge {
        private final WeakReference<AiScriptWebActivity> activityRef;

        TsddSpeechBridge(AiScriptWebActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @JavascriptInterface
        public void speak(String text) {
            AiScriptWebActivity activity = activityRef.get();
            if (activity == null) return;
            activity.runOnUiThread(() -> activity.speakFromWeb(text));
        }

        @JavascriptInterface
        public void speakJson(String json) {
            String text = "";
            try {
                JSONObject obj = new JSONObject(json == null ? "{}" : json);
                text = obj.optString("text", "");
            } catch (Throwable ignored) {
            }
            speak(text);
        }
    }

    private TextView toolbarButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(24, 119, 242));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
