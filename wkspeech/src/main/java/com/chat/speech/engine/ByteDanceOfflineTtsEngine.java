package com.chat.speech.engine;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.chat.speech.PinyinNormalizer;
import com.chat.speech.SpeechCache;
import com.chat.speech.SpeechPrefs;
import com.chat.speech.debug.SpeechDebugLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.concurrent.TimeUnit;

import dalvik.system.DexClassLoader;

/**
 * Adapter for the ByteDance offline engine bundled by the imported third-party MultiTTS package.
 *
 * The SDK bytecode is loaded from an app asset through reflection so this module does not need a
 * compile-time vendor AAR. Models stay outside the APK and are imported by the user at runtime.
 */
public final class ByteDanceOfflineTtsEngine {
    private static final String TAG = "ByteDanceOfflineTts";
    private static final String RUNTIME_ASSET = "bytedance_runtime/bytedance_runtime.jar";
    private static final long SYNTHESIS_TIMEOUT_SECONDS = 45L;
    private static final String[] NATIVE_LIBRARIES = {
            "libttcrypto.so",
            "libttboringssl.so",
            "libsscronet.so",
            "libspeechengine.so"
    };

    private static final int WORK_MODE_OFFLINE = 2048;
    private static final int DIRECTIVE_START_ENGINE = 1000;
    private static final int DIRECTIVE_STOP_ENGINE = 1001;
    private static final int DIRECTIVE_SYNTHESIS = 1400;
    private static final int MESSAGE_ENGINE_ERROR = 1003;
    private static final int MESSAGE_AUDIO_DATA = 1400;
    // The original MultiTTS adapter only releases its synthesis latch on 1409.
    // 1403/1404/1408 are intermediate status/progress messages, not audio completion.
    private static final int MESSAGE_AUDIO_DATA_END = 1409;

    private final Object lock = new Object();
    private Context logContext;
    private Object engine;
    private ClassLoader runtimeLoader;
    private String initializedSignature = "";
    private volatile CountDownLatch activeLatch;
    private volatile ByteArrayOutputStream activeAudio;
    private volatile String activeError;
    private volatile String fatalEngineError;
    private volatile boolean cancelled;

    public File synthesize(
            Context context,
            String text,
            String pinyin,
            String mode,
            String voice,
            int sampleRate,
            int ratePercent
    ) throws Exception {
        Context app = context.getApplicationContext();
        logContext = app;
        SpeechDebugLog.append(app, "engine.synthesize.begin mode=" + mode
                + " text=" + abbreviate(text) + " pinyin=" + abbreviate(pinyin));
        requireArm64();
        SpeechPrefs prefs = new SpeechPrefs(app);
        String rootPath = prefs.getByteDancePackageRoot();
        if (rootPath.isEmpty()) throw new IllegalStateException("尚未导入字节离线语音包");

        File bytedanceRoot = new File(rootPath);
        SpeechDebugLog.append(app, "engine.resolve_resources root=" + bytedanceRoot.getAbsolutePath()
                + " voice=" + voice + " sampleRate=" + sampleRate);
        VoiceResources resources = resolveResources(bytedanceRoot, voice, sampleRate);
        SpeechDebugLog.append(app, "engine.resources_ready signature=" + resources.signature);
        String actualText = text == null ? "" : text.trim();
        String textType = "plain";
        if ("spelling".equalsIgnoreCase(mode) && pinyin != null && !pinyin.trim().isEmpty()) {
            // MultiTTS does not synthesize the Hanzi in spelling mode. It first turns the supplied
            // pinyin into teaching text, e.g. bà -> "b à", then lets the ByteDance Chinese
            // frontend read that text. Keep this as plain text rather than SSML so the result
            // matches the original third-party app's pinyin-spelling behaviour.
            String teachingText = PinyinNormalizer.buildTeachingSpellingText(pinyin);
            if (!teachingText.isEmpty()) actualText = teachingText;
            Log.i(TAG, "Spelling input: " + actualText);
            SpeechDebugLog.append(app, "engine.spelling_input=" + actualText);
        }
        if (actualText.isEmpty()) throw new IllegalArgumentException("朗读内容为空");

        String cacheKey = "bytedance-offline|" + resources.signature + "|" + textType + "|"
                + ratePercent + "|" + actualText;
        File cached = SpeechCache.audioFile(app, cacheKey, "wav");
        if (cached.exists() && cached.length() > 44L) {
            //noinspection ResultOfMethodCallIgnored
            cached.setLastModified(System.currentTimeMillis());
            SpeechDebugLog.append(app, "engine.cache_hit bytes=" + cached.length());
            return cached;
        }

        synchronized (lock) {
            ensureInitialized(app, resources);
            cancelled = false;
            activeError = null;
            activeAudio = new ByteArrayOutputStream(64 * 1024);
            activeLatch = new CountDownLatch(1);
            try {
                if (fatalEngineError != null && !fatalEngineError.isEmpty()) {
                    String error = fatalEngineError;
                    destroyEngineOnly();
                    throw new IllegalStateException(error);
                }
                setOptionString("tts_text_type", textType);
                setOptionInt("tts_speed", speedValue(ratePercent));
                setOptionInt("tts_volume", 10);
                setOptionString("tts_text", actualText);
                SpeechDebugLog.append(app, "engine.send_synthesis text=" + abbreviate(actualText));
                int result = sendDirective(DIRECTIVE_SYNTHESIS, "");
                SpeechDebugLog.append(app, "engine.send_synthesis.result=" + result);
                if (result != 0) throw new IllegalStateException("离线合成启动失败：" + result);
                if (!activeLatch.await(SYNTHESIS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("离线合成超时");
                }
                if (cancelled || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("离线语音已取消");
                }
                if (activeError != null && !activeError.isEmpty()) {
                    destroyEngineOnly();
                    throw new IllegalStateException(activeError);
                }
                byte[] audio = activeAudio.toByteArray();
                if (audio.length == 0) throw new IllegalStateException("离线引擎没有返回音频数据");
                writeAudioFile(cached, audio, resources.sampleRate);
                SpeechCache.trim(app);
                SpeechDebugLog.append(app, "engine.synthesize.success bytes=" + cached.length());
                return cached;
            } finally {
                activeLatch = null;
                activeAudio = null;
            }
        }
    }

    public void cancelActive() {
        cancelled = true;
        CountDownLatch latch = activeLatch;
        if (latch != null) latch.countDown();
        synchronized (lock) {
            if (engine != null) {
                try {
                    sendDirective(DIRECTIVE_STOP_ENGINE, "");
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void destroy() {
        synchronized (lock) {
            cancelActive();
            if (engine != null) {
                try {
                    invokeEngine("destroyEngine", new Class<?>[0]);
                } catch (Throwable ignored) {
                }
            }
            engine = null;
            initializedSignature = "";
        }
    }

    private void ensureInitialized(Context context, VoiceResources resources) throws Exception {
        if (engine != null && resources.signature.equals(initializedSignature)) {
            SpeechDebugLog.append(context, "engine.init.reuse signature=" + resources.signature);
            return;
        }
        SpeechDebugLog.append(context, "engine.init.begin signature=" + resources.signature);
        destroyEngineOnly();
        fatalEngineError = null;

        SpeechDebugLog.append(context, "engine.runtime_loader.begin");
        runtimeLoader = createRuntimeLoader(context);
        SpeechDebugLog.append(context, "engine.runtime_loader.ready");

        /*
         * Keep the exact initialization order used by the original third-party app:
         *
         *   1. SpeechEngineGenerator.PrepareEnvironment(context, application)
         *   2. SpeechEngineGenerator.getInstance()
         *   3. createEngine()
         *
         * getInstance() initializes the vendor implementation class. Its static initializer calls
         * SpeechEngineLoader.load() by itself. Calling SpeechEngineLoader.load() before
         * PrepareEnvironment() is wrong for this SDK build because libspeechengine.so depends on
         * the Cronet/crypto libraries prepared in step 1.
         */
        Class<?> generator = runtimeLoader.loadClass("com.bytedance.speech.speechengine.SpeechEngineGenerator");
        SpeechDebugLog.append(context, "engine.prepare_environment.begin nativeDir="
                + context.getApplicationInfo().nativeLibraryDir);
        boolean prepared = invokePrepareEnvironment(generator, context);
        SpeechDebugLog.append(context, "engine.prepare_environment.result=" + prepared);

        SpeechDebugLog.append(context, "engine.getInstance.begin (vendor loader runs here)");
        try {
            engine = generator.getMethod("getInstance").invoke(null);
        } catch (InvocationTargetException error) {
            throw reflectionFailure("SpeechEngineGenerator.getInstance", error);
        }
        SpeechDebugLog.append(context, "engine.getInstance.ready class="
                + (engine == null ? "null" : engine.getClass().getName()));
        if (engine == null) throw new IllegalStateException("无法创建字节离线语音引擎");

        SpeechDebugLog.append(context, "engine.createEngine.begin");
        invokeEngine("createEngine", new Class<?>[0]);
        SpeechDebugLog.append(context, "engine.createEngine.ready");
        setOptionInt("tts_work_mode", WORK_MODE_OFFLINE);
        setOptionString("engine_name", "tts");
        setOptionString("authenticate_type", "pre_bind");
        setOptionString("license_directory", resources.licenseDirectory.getAbsolutePath());
        setOptionString("tts_scenario", "novel");
        setOptionBoolean("prevent_player_creation", true);
        setOptionString("tts_text_type", "plain");
        setOptionBoolean("tts_enable_progress", true);
        setOptionString("tts_off_resource_path", resources.resourceJson);
        setOptionInt("tts_rate", resources.sampleRate);
        setOptionInt("tts_pitch", 10);
        setOptionBoolean("use_alog", false);
        setOptionString("log_level", "WARN");
        setOptionBoolean("tts_enable_player", false);
        setOptionBoolean("tts_enable_dump", false);
        setOptionBoolean("enable_ws_reconnect", false);
        setOptionBoolean("tts_limit_cpu_usage", false);
        setOptionInt("tts_data_callback_mode", 2);

        SpeechDebugLog.append(context, "engine.initEngine.begin");
        Object initValue = invokeEngine("initEngine", new Class<?>[0]);
        int initResult = initValue instanceof Number ? ((Number) initValue).intValue() : 0;
        SpeechDebugLog.append(context, "engine.initEngine.result=" + initResult);
        if (initResult != 0) {
            destroyEngineOnly();
            throw new IllegalStateException("字节离线引擎初始化失败：" + initResult);
        }
        SpeechDebugLog.append(context, "engine.listener.begin");
        installListener();
        SpeechDebugLog.append(context, "engine.listener.ready");
        setOptionString("tts_voice_offline", "other");
        setOptionString("tts_voice_type_offline", resources.voiceType);
        SpeechDebugLog.append(context, "engine.start.begin");
        int startResult = sendDirective(DIRECTIVE_START_ENGINE, "");
        SpeechDebugLog.append(context, "engine.start.result=" + startResult);
        if (startResult != 0) {
            destroyEngineOnly();
            throw new IllegalStateException("字节离线引擎启动失败：" + startResult);
        }
        initializedSignature = resources.signature;
        Log.i(TAG, "Offline engine initialized: " + resources.signature);
        SpeechDebugLog.append(context, "engine.init.success signature=" + resources.signature);
    }

    private void installListener() throws Exception {
        Class<?> listenerClass = runtimeLoader.loadClass(
                "com.bytedance.speech.speechengine.SpeechEngine$SpeechListener"
        );
        Object listener = Proxy.newProxyInstance(
                runtimeLoader,
                new Class<?>[]{listenerClass},
                (proxy, method, args) -> {
                    if ("onSpeechMessage".equals(method.getName()) && args != null && args.length >= 3) {
                        int type = ((Number) args[0]).intValue();
                        byte[] data = args[1] instanceof byte[] ? (byte[]) args[1] : null;
                        int length = ((Number) args[2]).intValue();
                        onSpeechMessage(type, data, length);
                    }
                    return null;
                }
        );
        Method setListener = findMethod(engine.getClass(), "setListener", 1);
        setListener.invoke(engine, listener);
    }

    private void onSpeechMessage(int type, byte[] data, int length) {
        try {
            if (type == MESSAGE_AUDIO_DATA && data != null && length > 0) {
                ByteArrayOutputStream out = activeAudio;
                if (out != null) out.write(data, 0, Math.min(length, data.length));
                return;
            }
            if (type == MESSAGE_AUDIO_DATA_END) {
                SpeechDebugLog.append(logContext, "engine.callback.end type=" + type
                        + " audioBytes=" + (activeAudio == null ? 0 : activeAudio.size()));
                CountDownLatch latch = activeLatch;
                if (latch != null) latch.countDown();
                return;
            }
            // The vendor adapter reports its terminal SDK error with positive message type 1003.
            // Negative types are kept as a defensive fallback for other engine builds.
            if (type == MESSAGE_ENGINE_ERROR || type < 0) {
                String error = "离线合成错误：" + type + decodeMessage(data, length);
                fatalEngineError = error;
                activeError = error;
                SpeechDebugLog.append(logContext, "engine.callback.error " + error);
                CountDownLatch latch = activeLatch;
                if (latch != null) latch.countDown();
                return;
            }
            // Preserve non-audio status callbacks (1001/1002/1403/1404/1408, etc.) for diagnosis.
            SpeechDebugLog.append(logContext, "engine.callback.status type=" + type
                    + decodeMessage(data, length));
        } catch (Throwable error) {
            activeError = "离线音频回调失败：" + error.getMessage();
            SpeechDebugLog.append(logContext, "engine.callback.exception " + activeError);
            CountDownLatch latch = activeLatch;
            if (latch != null) latch.countDown();
        }
    }

    private static String decodeMessage(byte[] data, int length) {
        if (data == null || length <= 0) return "";
        try {
            return " · " + new String(data, 0, Math.min(length, data.length), "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static VoiceResources resolveResources(File bytedanceRoot, String selectedVoice, int requestedRate) throws Exception {
        String voice = selectedVoice == null || selectedVoice.trim().isEmpty()
                ? "BV001_24k" : selectedVoice.trim();
        String normalizedCode = voice;
        String variant = "";
        if (normalizedCode.endsWith("-md")) {
            normalizedCode = normalizedCode.substring(0, normalizedCode.length() - 3);
            variant = "midu";
        }
        String voiceType = normalizedCode + "_streaming";
        File fanqie = new File(bytedanceRoot, "fanqie");
        File midu = new File(bytedanceRoot, "midu");
        File variantRoot;
        if ("midu".equals(variant) || normalizedCode.contains("_24k")
                || !new File(fanqie, voiceType).isDirectory()) {
            variantRoot = midu;
        } else {
            variantRoot = fanqie;
        }
        File base = new File(variantRoot, "zh-cn");
        File voiceDir = new File(variantRoot, voiceType);
        if (!base.isDirectory()) throw new IllegalStateException("缺少中文前端模型：" + base);
        if (!voiceDir.isDirectory()) throw new IllegalStateException("缺少离线音色模型：" + voiceDir);

        File licenseDir = variantRoot;
        if (!new File(licenseDir, "speech_license.licbag").isFile()
                && new File(midu, "speech_license.licbag").isFile()) {
            licenseDir = midu;
        }
        if (!new File(licenseDir, "speech_license.licbag").isFile()) {
            throw new IllegalStateException("缺少 speech_license.licbag");
        }

        JSONObject voiceObject = new JSONObject();
        voiceObject.put(voiceType, voiceDir.getAbsolutePath());
        JSONArray voices = new JSONArray();
        voices.put(voiceObject);
        JSONObject resources = new JSONObject();
        resources.put("base", base.getAbsolutePath());
        resources.put("voice", voices);
        int sampleRate = requestedRate > 0 ? requestedRate : (normalizedCode.contains("_24k") ? 24000 : 16000);
        return new VoiceResources(
                voiceType,
                sampleRate,
                licenseDir,
                resources.toString(),
                variantRoot.getName() + "|" + voiceType + "|" + sampleRate
        );
    }

    private ClassLoader createRuntimeLoader(Context context) throws Exception {
        File dir = context.getDir("bytedance_offline_runtime", Context.MODE_PRIVATE);
        File jar = new File(dir, "runtime.jar");
        long assetSize = assetSize(context, RUNTIME_ASSET);
        if (!jar.isFile() || jar.length() != assetSize) {
            File temp = new File(dir, "runtime.jar.tmp");
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            try (InputStream input = new BufferedInputStream(context.getAssets().open(RUNTIME_ASSET));
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            }
            // Android 14+ requires dynamically loaded code to be read-only before loading.
            //noinspection ResultOfMethodCallIgnored
            temp.setReadable(true, true);
            //noinspection ResultOfMethodCallIgnored
            temp.setWritable(false, false);
            //noinspection ResultOfMethodCallIgnored
            jar.delete();
            if (!temp.renameTo(jar)) throw new IllegalStateException("无法安装离线运行库");
        }
        //noinspection ResultOfMethodCallIgnored
        jar.setReadable(true, true);
        //noinspection ResultOfMethodCallIgnored
        jar.setWritable(false, false);
        File optimized = new File(dir, "opt");
        if (!optimized.exists() && !optimized.mkdirs()) throw new IllegalStateException("无法创建 Dex 优化目录");
        File nativeDir = prepareNativeLibraries(context);
        SpeechDebugLog.append(context, "engine.runtime_loader.native_path="
                + nativeDir.getAbsolutePath());
        return new DexClassLoader(
                jar.getAbsolutePath(),
                optimized.getAbsolutePath(),
                nativeDir.getAbsolutePath(),
                context.getClassLoader()
        );
    }

    /**
     * A library module's JNI files may stay uncompressed inside base.apk on modern Android builds.
     * In that case ApplicationInfo.nativeLibraryDir does not contain real files and a newly-created
     * DexClassLoader cannot see the host PathClassLoader's internal "apk!/lib/arm64-v8a" path.
     * Extract the four vendor libraries to a private, read-only directory and give that directory
     * explicitly to the vendor DexClassLoader.
     */
    private static File prepareNativeLibraries(Context context) throws Exception {
        File targetDir = context.getDir("bytedance_offline_native", Context.MODE_PRIVATE);
        if (!targetDir.isDirectory() && !targetDir.mkdirs()) {
            throw new IllegalStateException("无法创建字节离线 Native 目录");
        }
        for (String library : NATIVE_LIBRARIES) {
            File target = new File(targetDir, library);
            if (target.isFile() && target.length() > 0L) {
                // Existing libraries came from this APK build. Keep them unless a later extraction
                // finds a different size; this avoids rewriting executable files every request.
                continue;
            }
            if (!extractNativeLibrary(context, library, target)) {
                throw new IllegalStateException("APK 中缺少字节离线运行库：" + library);
            }
            SpeechDebugLog.append(context, "engine.native.ready " + library
                    + " bytes=" + target.length());
        }
        return targetDir;
    }

    private static boolean extractNativeLibrary(
            Context context,
            String library,
            File target
    ) throws Exception {
        File installed = new File(context.getApplicationInfo().nativeLibraryDir, library);
        if (installed.isFile() && installed.length() > 0L) {
            copyNativeFile(installed, target);
            return true;
        }

        String[] apkPaths = applicationApkPaths(context);
        String entryName = "lib/arm64-v8a/" + library;
        for (String apkPath : apkPaths) {
            if (apkPath == null || apkPath.trim().isEmpty()) continue;
            File apk = new File(apkPath);
            if (!apk.isFile()) continue;
            try (ZipFile zip = new ZipFile(apk)) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null || entry.isDirectory()) continue;
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry))) {
                    copyNativeStream(input, target);
                }
                return true;
            }
        }
        return false;
    }

    private static String[] applicationApkPaths(Context context) {
        String source = context.getApplicationInfo().sourceDir;
        String[] splits = context.getApplicationInfo().splitSourceDirs;
        int splitCount = splits == null ? 0 : splits.length;
        String[] result = new String[1 + splitCount];
        result[0] = source;
        if (splitCount > 0) System.arraycopy(splits, 0, result, 1, splitCount);
        return result;
    }

    private static void copyNativeFile(File source, File target) throws Exception {
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            copyNativeStream(input, target);
        }
    }

    private static void copyNativeStream(InputStream input, File target) throws Exception {
        File temp = new File(target.getAbsolutePath() + ".tmp");
        //noinspection ResultOfMethodCallIgnored
        temp.delete();
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temp))) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
        if (temp.length() <= 0L) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IllegalStateException("复制 Native 库失败：" + target.getName());
        }
        // Dynamic code must be immutable before Android loads it.
        //noinspection ResultOfMethodCallIgnored
        temp.setReadable(true, true);
        //noinspection ResultOfMethodCallIgnored
        temp.setExecutable(true, true);
        //noinspection ResultOfMethodCallIgnored
        temp.setWritable(false, false);
        //noinspection ResultOfMethodCallIgnored
        target.delete();
        if (!temp.renameTo(target)) {
            throw new IllegalStateException("安装 Native 库失败：" + target.getName());
        }
        //noinspection ResultOfMethodCallIgnored
        target.setReadable(true, true);
        //noinspection ResultOfMethodCallIgnored
        target.setExecutable(true, true);
        //noinspection ResultOfMethodCallIgnored
        target.setWritable(false, false);
    }

    private static long assetSize(Context context, String name) throws Exception {
        long total = 0L;
        try (InputStream input = context.getAssets().open(name)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) total += count;
        }
        return total;
    }

    private static boolean invokePrepareEnvironment(Class<?> generator, Context context) throws Exception {
        Method prepare = findMethod(generator, "PrepareEnvironment", 2);
        Application application = (Application) context.getApplicationContext();
        Class<?> first = prepare.getParameterTypes()[0];
        Object firstArg = Application.class.isAssignableFrom(first) ? application : context;
        Class<?> second = prepare.getParameterTypes()[1];
        Object secondArg = Application.class.isAssignableFrom(second) ? application : context;
        try {
            Object value = prepare.invoke(null, firstArg, secondArg);
            return !(value instanceof Boolean) || (Boolean) value;
        } catch (InvocationTargetException error) {
            throw reflectionFailure("SpeechEngineGenerator.PrepareEnvironment", error);
        }
    }

    private Object invokeEngine(String name, Class<?>[] types, Object... args) throws Exception {
        Method method;
        try {
            method = engine.getClass().getMethod(name, types);
        } catch (NoSuchMethodException ignored) {
            method = findMethod(engine.getClass(), name, args.length);
        }
        try {
            return method.invoke(engine, args);
        } catch (InvocationTargetException error) {
            throw reflectionFailure(engine.getClass().getName() + "." + name, error);
        }
    }

    private void setOptionString(String key, String value) throws Exception {
        invokeEngine("setOptionString", new Class<?>[]{String.class, String.class}, key, value);
    }

    private void setOptionInt(String key, int value) throws Exception {
        invokeEngine("setOptionInt", new Class<?>[]{String.class, int.class}, key, value);
    }

    private void setOptionBoolean(String key, boolean value) throws Exception {
        invokeEngine("setOptionBoolean", new Class<?>[]{String.class, boolean.class}, key, value);
    }

    private int sendDirective(int directive, String payload) throws Exception {
        Object value = invokeEngine("sendDirective", new Class<?>[]{int.class, String.class}, directive, payload);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + parameterCount);
    }

    private void destroyEngineOnly() {
        if (engine != null) {
            try {
                invokeEngine("destroyEngine", new Class<?>[0]);
            } catch (Throwable ignored) {
            }
        }
        engine = null;
        initializedSignature = "";
    }

    private static Exception reflectionFailure(
            String stage,
            InvocationTargetException wrapper
    ) {
        Throwable cause = wrapper.getCause() == null ? wrapper : wrapper.getCause();
        String message = cause.getMessage();
        String detail = stage + " 失败：" + cause.getClass().getName()
                + (message == null || message.trim().isEmpty() ? "" : " · " + message.trim());
        if (cause instanceof Error) throw (Error) cause;
        if (cause instanceof Exception) return new IllegalStateException(detail, cause);
        return new IllegalStateException(detail, cause);
    }

    private static void requireArm64() {
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                arm64 = true;
                break;
            }
        }
        if (!arm64) throw new IllegalStateException("当前离线运行库仅支持 arm64-v8a");
    }

    private static int speedValue(int ratePercent) {
        return Math.max(5, Math.min(18, Math.round(10f * (1f + ratePercent / 100f))));
    }

    private static void writeAudioFile(File target, byte[] data, int sampleRate) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建语音缓存目录");
        }
        File temp = new File(target.getAbsolutePath() + ".tmp");
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temp))) {
            if (isWave(data)) {
                output.write(data);
            } else {
                writeWaveHeader(output, data.length, sampleRate, 1, 16);
                output.write(data);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        target.delete();
        if (!temp.renameTo(target)) throw new IllegalStateException("无法保存离线语音缓存");
    }

    private static boolean isWave(byte[] data) {
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E';
    }

    private static void writeWaveHeader(
            BufferedOutputStream out,
            int pcmSize,
            int sampleRate,
            int channels,
            int bitsPerSample
    ) throws Exception {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        out.write(new byte[]{'R', 'I', 'F', 'F'});
        writeLeInt(out, 36 + pcmSize);
        out.write(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        writeLeInt(out, 16);
        writeLeShort(out, 1);
        writeLeShort(out, channels);
        writeLeInt(out, sampleRate);
        writeLeInt(out, byteRate);
        writeLeShort(out, blockAlign);
        writeLeShort(out, bitsPerSample);
        out.write(new byte[]{'d', 'a', 't', 'a'});
        writeLeInt(out, pcmSize);
    }

    private static void writeLeInt(BufferedOutputStream out, int value) throws Exception {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private static void writeLeShort(BufferedOutputStream out, int value) throws Exception {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static String abbreviate(String text) {
        if (text == null) return "";
        String clean = text.replace('\n', ' ').replace('\r', ' ');
        return clean.length() <= 100 ? clean : clean.substring(0, 100) + "...";
    }

    private static final class VoiceResources {
        final String voiceType;
        final int sampleRate;
        final File licenseDirectory;
        final String resourceJson;
        final String signature;

        VoiceResources(String voiceType, int sampleRate, File licenseDirectory, String resourceJson, String signature) {
            this.voiceType = voiceType;
            this.sampleRate = sampleRate;
            this.licenseDirectory = licenseDirectory;
            this.resourceJson = resourceJson;
            this.signature = signature;
        }
    }
}
