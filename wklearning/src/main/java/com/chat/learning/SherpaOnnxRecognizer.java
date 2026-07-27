package com.chat.learning;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Process-wide sherpa-onnx recognizer and offline-model manager. */
final class SherpaOnnxRecognizer {
    private static final String TAG = "LearningSherpaAsr";
    private static final String PREFS = "learning_sherpa_asr";
    private static final String KEY_MODEL = "selected_model";
    private static final String ROOT_FOLDER = "learning-asr";
    private static final String MODEL_FILE = "model.int8.onnx";
    private static final String TOKENS_FILE = "tokens.txt";
    private static final int SAMPLE_RATE = 16_000;
    private static final int DOWNLOAD_BUFFER = 64 * 1024;

    enum ModelType {
        ZIPFORMER("zipformer"),
        SENSE_VOICE("sense_voice");

        final String value;

        ModelType(String value) {
            this.value = value;
        }

        static ModelType fromValue(String value) {
            for (ModelType type : values()) {
                if (type.value.equals(value)) return type;
            }
            return ZIPFORMER;
        }
    }

    enum ModelState {
        NOT_INSTALLED,
        PREPARING,
        DOWNLOADING,
        IMPORTING,
        READY,
        FAILED
    }

    interface ModelListener {
        void onModelStateChanged(ModelType type, ModelState state);
    }

    interface RecognitionCallback {
        void onComplete(String text, boolean usedSherpa);
    }

    interface OperationCallback {
        void onComplete(boolean success, String message);
    }

    private static final class ModelSpec {
        final ModelType type;
        final String folder;
        final String baseUrl;
        final long minModelBytes;
        final long maxModelBytes;
        final long requiredFreeBytes;

        ModelSpec(ModelType type, String folder, String baseUrl,
                  long minModelBytes, long maxModelBytes, long requiredFreeBytes) {
            this.type = type;
            this.folder = folder;
            this.baseUrl = baseUrl;
            this.minModelBytes = minModelBytes;
            this.maxModelBytes = maxModelBytes;
            this.requiredFreeBytes = requiredFreeBytes;
        }
    }

    private static final class InstallTransaction {
        final File target;
        final File backup;
        final boolean hadPreviousModel;

        InstallTransaction(File target, File backup, boolean hadPreviousModel) {
            this.target = target;
            this.backup = backup;
            this.hadPreviousModel = hadPreviousModel;
        }
    }

    private static final ModelSpec ZIPFORMER_SPEC = new ModelSpec(
            ModelType.ZIPFORMER,
            "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01",
            "https://huggingface.co/csukuangfj/"
                    + "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01"
                    + "/resolve/main/",
            20L * 1024L * 1024L,
            120L * 1024L * 1024L,
            70L * 1024L * 1024L
    );

    private static final ModelSpec SENSE_VOICE_SPEC = new ModelSpec(
            ModelType.SENSE_VOICE,
            "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17",
            "https://huggingface.co/csukuangfj/"
                    + "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"
                    + "/resolve/main/",
            150L * 1024L * 1024L,
            420L * 1024L * 1024L,
            520L * 1024L * 1024L
    );

    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "learning-sherpa-onnx");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final List<ModelListener> LISTENERS = new ArrayList<>();

    private static volatile ModelState state = ModelState.NOT_INSTALLED;
    private static volatile ModelType activeType;
    private static OnlineRecognizer onlineRecognizer;
    private static OfflineRecognizer offlineRecognizer;

    private SherpaOnnxRecognizer() { }

    static ModelType getSelectedModel(Context context) {
        if (context == null) return ModelType.ZIPFORMER;
        return ModelType.fromValue(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODEL, ModelType.ZIPFORMER.value));
    }

    static void selectModel(Context context, ModelType type) {
        if (context == null || type == null) return;
        Context appContext = context.getApplicationContext();
        setSelectedModel(appContext, type);
        boolean installed = isInstalled(appContext, type);
        synchronized (LOCK) {
            releaseRecognizersLocked();
            activeType = type;
            state = installed ? ModelState.PREPARING : ModelState.NOT_INSTALLED;
            notifyListenersLocked(type, state);
        }
        if (installed) EXECUTOR.execute(() -> loadSelectedModel(appContext, type));
    }

    static boolean isInstalled(Context context, ModelType type) {
        if (context == null || type == null) return false;
        return validateModelFiles(modelDir(context, spec(type)), spec(type), false);
    }

    static long installedBytes(Context context, ModelType type) {
        if (!isInstalled(context, type)) return 0L;
        File dir = modelDir(context, spec(type));
        return new File(dir, MODEL_FILE).length() + new File(dir, TOKENS_FILE).length();
    }

    static ModelState getState(Context context) {
        return getState(context, getSelectedModel(context));
    }

    static ModelState getState(Context context, ModelType type) {
        if (type == null) return ModelState.NOT_INSTALLED;
        if (activeType == type) return state;
        return isInstalled(context, type) ? ModelState.READY : ModelState.NOT_INSTALLED;
    }

    static void prepare(Context context, ModelListener listener) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        ModelType selected = getSelectedModel(appContext);
        if (listener != null) addModelListener(listener);

        synchronized (LOCK) {
            if (state == ModelState.DOWNLOADING || state == ModelState.IMPORTING) {
                notifyOne(listener, activeType == null ? selected : activeType, state);
                return;
            }
            if (activeType == selected && state == ModelState.READY) {
                notifyOne(listener, selected, ModelState.READY);
                return;
            }
            if (activeType == selected && state == ModelState.PREPARING) return;
            if (!isInstalled(appContext, selected)) {
                state = ModelState.NOT_INSTALLED;
                activeType = selected;
                notifyListenersLocked(selected, state);
                return;
            }
            state = ModelState.PREPARING;
            activeType = selected;
            notifyListenersLocked(selected, state);
        }

        EXECUTOR.execute(() -> loadSelectedModel(appContext, selected));
    }

    static void download(Context context, ModelType type, ModelListener listener,
                         OperationCallback callback) {
        if (context == null || type == null) {
            postOperation(callback, false, "参数无效");
            return;
        }
        Context appContext = context.getApplicationContext();
        if (listener != null) addModelListener(listener);
        synchronized (LOCK) {
            releaseRecognizersLocked();
            activeType = type;
            state = ModelState.DOWNLOADING;
            notifyListenersLocked(type, state);
        }

        ModelType previousSelected = getSelectedModel(appContext);
        EXECUTOR.execute(() -> {
            ModelSpec spec = spec(type);
            File targetDir = modelDir(appContext, spec);
            File staging = stagingDir(appContext, spec);
            InstallTransaction transaction = null;
            try {
                ensureStorage(targetDir.getParentFile(), spec.requiredFreeBytes);
                recreateDirectory(staging);
                downloadFile(spec.baseUrl + MODEL_FILE + "?download=true",
                        new File(staging, MODEL_FILE), spec.minModelBytes, spec.maxModelBytes);
                downloadFile(spec.baseUrl + TOKENS_FILE + "?download=true",
                        new File(staging, TOKENS_FILE), 1024L, 16L * 1024L * 1024L);
                if (!validateModelFiles(staging, spec, true)) {
                    throw new IOException("模型文件校验失败");
                }
                transaction = installStaging(appContext, spec, staging);
                setSelectedModel(appContext, type);
                if (!loadSelectedModel(appContext, type)) {
                    throw new IOException("模型已下载，但加载失败");
                }
                commitInstall(transaction);
                postOperation(callback, true, "在线下载完成");
            } catch (Throwable error) {
                deleteRecursively(staging);
                rollbackInstall(transaction);
                restorePreviousSelection(appContext, previousSelected);
                Log.w(TAG, "Model download failed", error);
                postOperation(callback, false, safeMessage(error));
            }
        });
    }

    static void importZip(Context context, ModelType type, Uri uri, ModelListener listener,
                          OperationCallback callback) {
        if (context == null || type == null || uri == null) {
            postOperation(callback, false, "未选择模型文件");
            return;
        }
        Context appContext = context.getApplicationContext();
        if (listener != null) addModelListener(listener);
        synchronized (LOCK) {
            releaseRecognizersLocked();
            activeType = type;
            state = ModelState.IMPORTING;
            notifyListenersLocked(type, state);
        }

        ModelType previousSelected = getSelectedModel(appContext);
        EXECUTOR.execute(() -> {
            ModelSpec spec = spec(type);
            File staging = stagingDir(appContext, spec);
            InstallTransaction transaction = null;
            try {
                ensureStorage(staging.getParentFile(), spec.requiredFreeBytes);
                recreateDirectory(staging);
                extractRequiredFiles(appContext, uri, staging, spec);
                if (!validateModelFiles(staging, spec, true)) {
                    throw new IOException("ZIP 中缺少匹配的 model.int8.onnx 或 tokens.txt");
                }
                transaction = installStaging(appContext, spec, staging);
                setSelectedModel(appContext, type);
                if (!loadSelectedModel(appContext, type)) {
                    throw new IOException("模型已导入，但加载失败");
                }
                commitInstall(transaction);
                postOperation(callback, true, "模型导入成功");
            } catch (Throwable error) {
                deleteRecursively(staging);
                rollbackInstall(transaction);
                restorePreviousSelection(appContext, previousSelected);
                Log.w(TAG, "Model import failed", error);
                postOperation(callback, false, safeMessage(error));
            }
        });
    }

    static void deleteModel(Context context, ModelType type, OperationCallback callback) {
        if (context == null || type == null) return;
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            synchronized (LOCK) {
                if (activeType == type) releaseRecognizersLocked();
            }
            boolean deleted = deleteRecursively(modelDir(appContext, spec(type)));
            ModelType selected = getSelectedModel(appContext);
            synchronized (LOCK) {
                if (selected == type) {
                    activeType = type;
                    state = ModelState.NOT_INSTALLED;
                    notifyListenersLocked(type, state);
                }
            }
            postOperation(callback, deleted, deleted ? "模型已删除" : "删除失败");
        });
    }

    static void addModelListener(ModelListener listener) {
        if (listener == null) return;
        synchronized (LOCK) {
            if (!LISTENERS.contains(listener)) LISTENERS.add(listener);
        }
    }

    static void removeModelListener(ModelListener listener) {
        if (listener == null) return;
        synchronized (LOCK) {
            LISTENERS.remove(listener);
        }
    }

    static void recognize(Context context, File wavFile, RecognitionCallback callback) {
        if (callback == null) return;
        if (context == null || wavFile == null || !wavFile.isFile() || wavFile.length() <= 44) {
            postRecognition(callback, "", false);
            return;
        }
        Context appContext = context.getApplicationContext();
        ModelType selected = getSelectedModel(appContext);
        if (activeType != selected || state != ModelState.READY) {
            prepare(appContext, null);
            postRecognition(callback, "", false);
            return;
        }

        EXECUTOR.execute(() -> {
            String text = "";
            try {
                float[] samples = readPcm16MonoWav(wavFile);
                if (samples.length > 0) {
                    text = selected == ModelType.SENSE_VOICE
                            ? decodeSenseVoice(samples)
                            : decodeZipformer(samples);
                }
            } catch (Throwable error) {
                Log.w(TAG, "Local recognition failed; using system fallback", error);
                markFailed(selected, error);
            }
            text = normalizeResult(text);
            postRecognition(callback, text, !text.isEmpty());
        });
    }

    private static boolean loadSelectedModel(Context context, ModelType type) {
        try {
            ModelSpec spec = spec(type);
            File dir = modelDir(context, spec);
            if (!validateModelFiles(dir, spec, true)) {
                synchronized (LOCK) {
                    activeType = type;
                    state = ModelState.NOT_INSTALLED;
                    notifyListenersLocked(type, state);
                }
                return false;
            }
            File model = new File(dir, MODEL_FILE);
            File tokens = new File(dir, TOKENS_FILE);
            OnlineRecognizer newOnline = null;
            OfflineRecognizer newOffline = null;
            if (type == ModelType.SENSE_VOICE) {
                newOffline = createSenseVoiceRecognizer(model, tokens);
            } else {
                newOnline = createZipformerRecognizer(model, tokens);
            }
            synchronized (LOCK) {
                if (getSelectedModel(context) != type || activeType != type) {
                    releaseRecognizersQuietly(newOnline, newOffline);
                    return false;
                }
                releaseRecognizersLocked();
                onlineRecognizer = newOnline;
                offlineRecognizer = newOffline;
                activeType = type;
                state = ModelState.READY;
                notifyListenersLocked(type, state);
            }
            return true;
        } catch (Throwable error) {
            markFailed(type, error);
            return false;
        }
    }

    private static OnlineRecognizer createZipformerRecognizer(File model, File tokens) {
        // The Android AAR exposes Kotlin data classes to Java through constructors
        // and property setters. It does not provide the Builder API.
        OnlineZipformer2CtcModelConfig ctc = new OnlineZipformer2CtcModelConfig();
        ctc.setModel(model.getAbsolutePath());

        OnlineModelConfig modelConfig = new OnlineModelConfig();
        modelConfig.setZipformer2Ctc(ctc);
        modelConfig.setTokens(tokens.getAbsolutePath());
        modelConfig.setNumThreads(2);
        modelConfig.setDebug(false);
        modelConfig.setProvider("cpu");
        modelConfig.setModelingUnit("cjkchar");

        OnlineRecognizerConfig config = new OnlineRecognizerConfig();
        config.setModelConfig(modelConfig);
        config.setDecodingMethod("greedy_search");
        config.setEnableEndpoint(false);
        return new OnlineRecognizer(null, config);
    }

    private static OfflineRecognizer createSenseVoiceRecognizer(File model, File tokens) {
        FeatureConfig feature = new FeatureConfig();
        feature.setSampleRate(SAMPLE_RATE);
        feature.setFeatureDim(80);
        feature.setDither(0.0f);

        OfflineSenseVoiceModelConfig sense = new OfflineSenseVoiceModelConfig();
        sense.setModel(model.getAbsolutePath());
        sense.setLanguage("zh");
        sense.setUseInverseTextNormalization(false);

        OfflineModelConfig modelConfig = new OfflineModelConfig();
        modelConfig.setSenseVoice(sense);
        modelConfig.setTokens(tokens.getAbsolutePath());
        modelConfig.setNumThreads(2);
        modelConfig.setDebug(false);
        modelConfig.setProvider("cpu");
        modelConfig.setModelType("sense_voice");
        modelConfig.setModelingUnit("cjkchar");

        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(feature);
        config.setModelConfig(modelConfig);
        config.setDecodingMethod("greedy_search");
        config.setMaxActivePaths(4);
        return new OfflineRecognizer(null, config);
    }

    private static String decodeZipformer(float[] samples) {
        OnlineRecognizer current = onlineRecognizer;
        if (current == null) return "";
        OnlineStream stream = current.createStream("");
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE);
            stream.acceptWaveform(new float[SAMPLE_RATE * 3 / 10], SAMPLE_RATE);
            stream.inputFinished();
            while (current.isReady(stream)) current.decode(stream);
            return current.getResult(stream).getText();
        } finally {
            stream.release();
        }
    }

    private static String decodeSenseVoice(float[] samples) {
        OfflineRecognizer current = offlineRecognizer;
        if (current == null) return "";
        OfflineStream stream = current.createStream();
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE);
            current.decode(stream);
            return current.getResult(stream).getText();
        } finally {
            stream.release();
        }
    }

    private static void extractRequiredFiles(Context context, Uri uri, File staging,
                                             ModelSpec spec) throws IOException {
        boolean foundModel = false;
        boolean foundTokens = false;
        int entries = 0;
        long total = 0L;
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("无法读取所选文件");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > 500) throw new IOException("ZIP 文件项目过多");
                    if (entry.isDirectory()) continue;
                    String name = new File(entry.getName()).getName();
                    File output = null;
                    long max = 0L;
                    if (MODEL_FILE.equalsIgnoreCase(name)) {
                        output = new File(staging, MODEL_FILE);
                        max = spec.maxModelBytes;
                        foundModel = true;
                    } else if (TOKENS_FILE.equalsIgnoreCase(name)) {
                        output = new File(staging, TOKENS_FILE);
                        max = 16L * 1024L * 1024L;
                        foundTokens = true;
                    }
                    if (output == null) continue;
                    long copied = copyLimited(zip, output, max);
                    total += copied;
                    if (total > spec.maxModelBytes + 16L * 1024L * 1024L) {
                        throw new IOException("ZIP 解压后过大");
                    }
                }
            }
        }
        if (!foundModel || !foundTokens) {
            throw new IOException("请选择包含 model.int8.onnx 和 tokens.txt 的 ZIP");
        }
    }

    private static long copyLimited(InputStream input, File output, long maxBytes)
            throws IOException {
        long total = 0L;
        try (FileOutputStream fileOutput = new FileOutputStream(output);
             BufferedOutputStream buffered = new BufferedOutputStream(fileOutput)) {
            byte[] buffer = new byte[DOWNLOAD_BUFFER];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) throw new IOException("模型文件大小异常");
                buffered.write(buffer, 0, count);
            }
            buffered.flush();
            fileOutput.getFD().sync();
        }
        return total;
    }

    private static void downloadFile(String sourceUrl, File target, long minBytes, long maxBytes)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(90_000);
            connection.setRequestProperty("User-Agent", "TangSengDaoDao-Android");
            connection.setRequestProperty("Accept", "application/octet-stream,*/*");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("下载失败，HTTP " + status);
            }
            long expected = connection.getContentLengthLong();
            if (expected > maxBytes) throw new IOException("服务器模型文件大小异常");
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
                long copied = copyLimited(input, target, maxBytes);
                if (copied < minBytes) throw new IOException("下载的模型文件不完整");
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static InstallTransaction installStaging(Context context, ModelSpec spec,
                                                     File staging) throws IOException {
        File target = modelDir(context, spec);
        File backup = new File(target.getParentFile(), target.getName() + ".backup");
        synchronized (LOCK) {
            releaseRecognizersLocked();
            deleteRecursively(backup);
            boolean hadPreviousModel = target.exists();
            if (hadPreviousModel && !target.renameTo(backup)) {
                throw new IOException("无法备份旧模型");
            }
            if (!staging.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target);
                throw new IOException("无法安装模型");
            }
            return new InstallTransaction(target, backup, hadPreviousModel);
        }
    }

    private static void commitInstall(InstallTransaction transaction) {
        if (transaction != null) deleteRecursively(transaction.backup);
    }

    private static void rollbackInstall(InstallTransaction transaction) {
        if (transaction == null) return;
        synchronized (LOCK) {
            releaseRecognizersLocked();
            deleteRecursively(transaction.target);
            if (transaction.hadPreviousModel && transaction.backup.exists()) {
                //noinspection ResultOfMethodCallIgnored
                transaction.backup.renameTo(transaction.target);
            } else {
                deleteRecursively(transaction.backup);
            }
        }
    }

    private static void restorePreviousSelection(Context context, ModelType previousSelected) {
        setSelectedModel(context, previousSelected);
        if (isInstalled(context, previousSelected)) {
            synchronized (LOCK) {
                activeType = previousSelected;
                state = ModelState.PREPARING;
                notifyListenersLocked(previousSelected, state);
            }
            loadSelectedModel(context, previousSelected);
        } else {
            synchronized (LOCK) {
                releaseRecognizersLocked();
                activeType = previousSelected;
                state = ModelState.NOT_INSTALLED;
                notifyListenersLocked(previousSelected, state);
            }
        }
    }

    private static void ensureStorage(File parent, long requiredBytes) throws IOException {
        if (parent == null) throw new IOException("模型目录不可用");
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("无法创建模型目录");
        }
        if (parent.getUsableSpace() < requiredBytes) {
            throw new IOException("存储空间不足");
        }
    }

    private static void recreateDirectory(File dir) throws IOException {
        deleteRecursively(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("无法创建临时目录");
    }

    private static boolean validateModelFiles(File dir, ModelSpec spec, boolean strict) {
        if (dir == null || !dir.isDirectory()) return false;
        File model = new File(dir, MODEL_FILE);
        File tokens = new File(dir, TOKENS_FILE);
        if (!model.isFile() || !tokens.isFile()) return false;
        if (model.length() < spec.minModelBytes || model.length() > spec.maxModelBytes) return false;
        if (tokens.length() < 1024L || tokens.length() > 16L * 1024L * 1024L) return false;
        if (!strict) return true;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(model))) {
            byte[] head = new byte[8];
            int read = input.read(head);
            return read == head.length;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static ModelSpec spec(ModelType type) {
        return type == ModelType.SENSE_VOICE ? SENSE_VOICE_SPEC : ZIPFORMER_SPEC;
    }

    private static File modelDir(Context context, ModelSpec spec) {
        return new File(new File(context.getFilesDir(), ROOT_FOLDER), spec.folder);
    }

    private static File stagingDir(Context context, ModelSpec spec) {
        return new File(new File(context.getFilesDir(), ROOT_FOLDER),
                ".import-" + spec.type.value + "-" + UUID.randomUUID());
    }

    private static void setSelectedModel(Context context, ModelType type) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MODEL, type.value).apply();
    }

    private static void markFailed(ModelType type, Throwable error) {
        Log.w(TAG, "Unable to prepare sherpa-onnx model " + type.value, error);
        synchronized (LOCK) {
            releaseRecognizersLocked();
            activeType = type;
            state = ModelState.FAILED;
            notifyListenersLocked(type, state);
        }
    }

    private static void releaseRecognizersLocked() {
        releaseRecognizersQuietly(onlineRecognizer, offlineRecognizer);
        onlineRecognizer = null;
        offlineRecognizer = null;
    }

    private static void releaseRecognizersQuietly(OnlineRecognizer online,
                                                   OfflineRecognizer offline) {
        try {
            if (online != null) online.release();
        } catch (Throwable ignored) { }
        try {
            if (offline != null) offline.release();
        } catch (Throwable ignored) { }
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return file.delete();
    }

    private static float[] readPcm16MonoWav(File wavFile) throws IOException {
        long payloadBytes = wavFile.length() - 44L;
        if (payloadBytes <= 0 || payloadBytes > Integer.MAX_VALUE - 8) return new float[0];
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(wavFile))) {
            byte[] header = new byte[44];
            readFully(input, header, 0, header.length);
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
                    || header[8] != 'W' || header[9] != 'A' || header[10] != 'V'
                    || header[11] != 'E') {
                throw new IOException("Unsupported WAV header");
            }
            int channels = littleEndian16(header, 22);
            int sampleRate = littleEndian32(header, 24);
            int bitsPerSample = littleEndian16(header, 34);
            if (channels != 1 || sampleRate != SAMPLE_RATE || bitsPerSample != 16) {
                throw new IOException("Expected 16 kHz mono PCM16 WAV");
            }
            byte[] pcm = new byte[(int) payloadBytes];
            readFully(input, pcm, 0, pcm.length);
            float[] samples = new float[pcm.length / 2];
            for (int i = 0, index = 0; i + 1 < pcm.length; i += 2, index++) {
                short value = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
                samples[index] = value / 32768.0f;
            }
            return samples;
        }
    }

    private static void readFully(InputStream input, byte[] target, int offset, int length)
            throws IOException {
        int total = 0;
        while (total < length) {
            int count = input.read(target, offset + total, length - total);
            if (count < 0) throw new IOException("Unexpected end of file");
            total += count;
        }
    }

    private static int littleEndian16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int littleEndian32(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static String normalizeResult(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", "").trim();
        return normalized.toLowerCase(Locale.ROOT).equals("null") ? "" : normalized;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.trim().isEmpty() ? "操作失败" : message.trim();
    }

    private static void notifyListenersLocked(ModelType type, ModelState newState) {
        if (LISTENERS.isEmpty()) return;
        List<ModelListener> listeners = new ArrayList<>(LISTENERS);
        MAIN.post(() -> {
            for (ModelListener listener : listeners) {
                try {
                    listener.onModelStateChanged(type, newState);
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void notifyOne(ModelListener listener, ModelType type, ModelState newState) {
        if (listener == null) return;
        MAIN.post(() -> listener.onModelStateChanged(type, newState));
    }

    private static void postRecognition(RecognitionCallback callback, String text,
                                        boolean usedSherpa) {
        MAIN.post(() -> callback.onComplete(text == null ? "" : text, usedSherpa));
    }

    private static void postOperation(OperationCallback callback, boolean success, String message) {
        if (callback == null) return;
        MAIN.post(() -> callback.onComplete(success, message));
    }
}
