package com.chat.base.web;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Transparent proxy Activity used as a fallback when SpeechRecognizer fails on some ROMs.
 * It opens the system speech-recognition UI and returns the recognized text to WebSpeechInputHelper.
 */
public class WebSpeechProxyActivity extends Activity {
    public interface Callback {
        void onResult(String text);

        void onError(String message);
    }

    private static final int REQUEST_SPEECH = 9501;
    private static WeakReference<Callback> callbackRef;
    private boolean launched;

    public static void start(Activity activity, Callback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onError("当前页面不可用");
            return;
        }

        callbackRef = new WeakReference<>(callback);
        Intent intent = new Intent(activity, WebSpeechProxyActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchSpeechRecognizer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!launched) {
            launchSpeechRecognizer();
        }
    }

    private void launchSpeechRecognizer() {
        if (launched) return;
        launched = true;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请开始说话");

        try {
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (ActivityNotFoundException e) {
            dispatchError("系统没有可用的语音输入服务");
            finish();
        } catch (Exception e) {
            dispatchError("打开系统语音输入失败");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_SPEECH) {
            finish();
            return;
        }

        if (resultCode != RESULT_OK || data == null) {
            dispatchError("语音输入已取消或没有识别到内容");
            finish();
            return;
        }

        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        String text = "";
        if (results != null && !results.isEmpty()) {
            text = results.get(0);
        }

        if (TextUtils.isEmpty(text)) {
            dispatchError("没有识别到内容");
        } else {
            dispatchResult(text);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            callbackRef = null;
        }
    }

    private void dispatchResult(String text) {
        Callback callback = callbackRef == null ? null : callbackRef.get();
        if (callback != null) callback.onResult(text);
        callbackRef = null;
    }

    private void dispatchError(String message) {
        Callback callback = callbackRef == null ? null : callbackRef.get();
        if (callback != null) callback.onError(message);
        callbackRef = null;
    }
}
