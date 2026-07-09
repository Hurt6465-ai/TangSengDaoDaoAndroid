package com.chat.learning;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * 学习模块统一 ASR / 发音练习桥。
 *
 * 录音对比和发音评分放在同一入口：
 * - 未安装本地 ASR 插件时，只做“标准音 / 我的录音”对比。
 * - 安装 sherpa-onnx 等独立插件后，插件可以接管识别或评分。
 */
final class LearningAsrBridge {
    private LearningAsrBridge() {}

    static boolean startPronunciationCheck(Context context, String word, String pinyin, String audioPath) {
        if (context == null) return false;
        if (tryStatic(context, word, pinyin, audioPath)) return true;
        if (tryIntent(context, word, pinyin, audioPath)) return true;
        Toast.makeText(context, context.getString(R.string.pronunciation_asr_plugin_missing), Toast.LENGTH_SHORT).show();
        return false;
    }

    private static boolean tryStatic(Context context, String word, String pinyin, String audioPath) {
        String[] classNames = new String[]{
                "com.chat.asr.TsddAsrManager",
                "com.chat.asr.WKAsrManager",
                "com.chat.asr.SherpaAsrManager",
                "com.chat.speech.TsddAsrManager",
                "com.chat.speech.WKAsrBridge"
        };
        String[] methodNames = new String[]{"startPronunciationCheck", "startPractice", "evaluate", "recognize"};
        for (String clsName : classNames) {
            try {
                Class<?> cls = Class.forName(clsName);
                for (String m : methodNames) {
                    if (invoke(cls, m, new Class[]{Context.class, String.class, String.class, String.class}, new Object[]{context, word, pinyin, audioPath})) return true;
                    if (invoke(cls, m, new Class[]{Context.class, String.class, String.class}, new Object[]{context, word, pinyin})) return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static boolean invoke(Class<?> cls, String methodName, Class<?>[] types, Object[] args) {
        try {
            Method method = cls.getMethod(methodName, types);
            method.setAccessible(true);
            method.invoke(null, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryIntent(Context context, String word, String pinyin, String audioPath) {
        try {
            Intent intent = new Intent("com.chat.asr.action.PRONUNCIATION_CHECK");
            intent.setPackage(context.getPackageName());
            intent.putExtra("word", word);
            intent.putExtra("pinyin", pinyin);
            intent.putExtra("audioPath", audioPath);
            context.sendBroadcast(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
