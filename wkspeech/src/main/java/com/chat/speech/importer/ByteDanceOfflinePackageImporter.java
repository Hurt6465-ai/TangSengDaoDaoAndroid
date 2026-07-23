package com.chat.speech.importer;

import android.content.Context;
import android.net.Uri;

import com.chat.speech.SpeechPrefs;
import com.chat.speech.model.TtsSource;
import com.chat.speech.model.TtsVoice;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports the third-party MultiTTS ByteDance offline package.
 *
 * Only one verified voice is retained: BV119_24k (武侠男声). The original package contains many
 * duplicated voice models and two frontend variants; importing all of them wastes roughly 200-300
 * MB. This importer accepts both the original large package and the supplied slim package, but
 * extracts only the files required by the single working voice.
 */
public final class ByteDanceOfflinePackageImporter {
    public static final String TARGET_VOICE_CODE = "BV119_24k";
    public static final String TARGET_VOICE_TYPE = "BV119_24k_streaming";
    public static final String TARGET_VOICE_NAME = "武侠男声";
    public static final int TARGET_SAMPLE_RATE = 24000;

    private static final String SOURCE_ID = "bytedance_offline";
    private static final String MIDU_PREFIX = "bytedance/midu/";
    private static final String BASE_PREFIX = MIDU_PREFIX + "zh-cn/";
    private static final String VOICE_PREFIX = MIDU_PREFIX + TARGET_VOICE_TYPE + "/";
    private static final String LICENSE_ENTRY = MIDU_PREFIX + "speech_license.licbag";

    private static final long MAX_UNCOMPRESSED_BYTES = 96L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 48L * 1024L * 1024L;

    private ByteDanceOfflinePackageImporter() {}

    public static boolean looksLikePackage(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 32) {
                String name = normalizedName(entry.getName());
                if (name.startsWith("bytedance/")) return true;
                if (name.equals("engines.yaml") || name.equals("engines.yml")
                        || name.equals("config.yaml") || name.equals("config.yml")) {
                    String text = new String(readSmallEntry(zip, 512 * 1024), StandardCharsets.UTF_8)
                            .toLowerCase(Locale.US);
                    if (text.contains("code: bytedance") || text.contains("bytedance:")
                            || text.contains("name: 字节跳动")) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static Result importFromUri(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) throw new IllegalArgumentException("导入文件为空");
        Context app = context.getApplicationContext();
        File parent = new File(app.getFilesDir(), "wkspeech/bytedance_offline");
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建离线模型目录");
        }
        File staging = new File(parent, "importing_" + System.currentTimeMillis());
        if (!staging.mkdirs()) throw new IllegalStateException("无法创建离线模型临时目录");

        boolean foundByteDance = false;
        boolean foundBase = false;
        boolean foundVoice = false;
        boolean foundLicense = false;
        long total = 0L;

        try (InputStream raw = app.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, 64 * 1024))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizedName(entry.getName());
                if (name.isEmpty()) continue;
                if (name.startsWith("bytedance/")) foundByteDance = true;
                if (!isWantedEntry(name)) continue;

                if (name.startsWith(BASE_PREFIX)) foundBase = true;
                if (name.startsWith(VOICE_PREFIX)) foundVoice = true;
                if (LICENSE_ENTRY.equals(name)) foundLicense = true;

                File output = safeOutput(staging, name);
                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) {
                        throw new IllegalStateException("无法创建目录：" + name);
                    }
                    continue;
                }
                File outputParent = output.getParentFile();
                if (outputParent != null && !outputParent.exists() && !outputParent.mkdirs()) {
                    throw new IllegalStateException("无法创建目录：" + outputParent);
                }

                long entryBytes = 0L;
                try (BufferedOutputStream fileOut = new BufferedOutputStream(
                        new FileOutputStream(output), 64 * 1024)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = zip.read(buffer)) >= 0) {
                        entryBytes += count;
                        total += count;
                        if (entryBytes > MAX_ENTRY_BYTES) {
                            throw new IllegalStateException("单个模型文件过大：" + name);
                        }
                        if (total > MAX_UNCOMPRESSED_BYTES) {
                            throw new IllegalStateException("单发音人离线包解压后异常过大");
                        }
                        fileOut.write(buffer, 0, count);
                    }
                }
            }
        } catch (Exception error) {
            deleteRecursive(staging);
            throw error;
        }

        if (!foundByteDance) {
            deleteRecursive(staging);
            throw new IllegalArgumentException("不是字节跳动离线语音包");
        }
        if (!foundBase || !foundVoice || !foundLicense) {
            deleteRecursive(staging);
            throw new IllegalArgumentException(
                    "语音包缺少单发音人资源：" + TARGET_VOICE_CODE
                            + "。请使用原完整包或提供的 BV119 精简包。"
            );
        }

        writeSingleVoiceMetadata(staging);
        File byteDanceDir = new File(staging, "bytedance");
        validatePackage(byteDanceDir);

        File current = new File(parent, "current");
        File old = new File(parent, "old_" + System.currentTimeMillis());
        if (current.exists() && !current.renameTo(old)) {
            deleteRecursive(staging);
            throw new IllegalStateException("无法替换旧离线模型");
        }
        if (!staging.renameTo(current)) {
            if (old.exists()) { //noinspection ResultOfMethodCallIgnored
                old.renameTo(current);
            }
            deleteRecursive(staging);
            throw new IllegalStateException("无法启用新离线模型");
        }
        deleteRecursive(old);

        TtsVoice onlyVoice = new TtsVoice(
                TARGET_VOICE_CODE,
                TARGET_VOICE_NAME,
                "zh-CN",
                TtsVoice.GENDER_MALE,
                SOURCE_ID,
                "字节跳动离线"
        );
        SpeechPrefs prefs = new SpeechPrefs(app);
        List<TtsVoice> merged = new ArrayList<>();
        for (TtsVoice voice : prefs.getAllVoices()) {
            if (!SOURCE_ID.equals(voice.sourceId)) merged.add(voice);
        }
        merged.add(onlyVoice);
        prefs.saveImportedVoices(merged);
        prefs.setByteDancePackageRoot(new File(current, "bytedance").getAbsolutePath());
        prefs.setByteDanceVoice(TARGET_VOICE_CODE);
        prefs.setImportedSource("字节跳动离线语音", 1, TtsSource.TYPE_BYTEDANCE_OFFLINE);

        TtsSource source = TtsSource.byteDanceOffline();
        JSONObject metadata = new JSONObject();
        metadata.put("packageRoot", prefs.getByteDancePackageRoot());
        metadata.put("voiceCount", 1);
        metadata.put("voice", TARGET_VOICE_CODE);
        metadata.put("sampleRate", TARGET_SAMPLE_RATE);
        metadata.put("arm64Only", true);
        metadata.put("singleVoicePackage", true);
        source.extraJson = metadata.toString();
        prefs.upsertSource(source, true);
        return new Result(1, TARGET_VOICE_CODE, prefs.getByteDancePackageRoot(), total);
    }

    private static boolean isWantedEntry(String name) {
        if (name.equals("config.yaml") || name.equals("config.yml")
                || name.equals("engines.yaml") || name.equals("engines.yml")) {
            // The imported copies are replaced with single-voice metadata after extraction.
            return false;
        }
        return LICENSE_ENTRY.equals(name)
                || name.startsWith(BASE_PREFIX)
                || name.startsWith(VOICE_PREFIX);
    }

    private static void validatePackage(File root) {
        requireFile(new File(root, "midu/speech_license.licbag"), "speech_license.licbag");
        requireFile(new File(root, "midu/zh-cn/ptl.dat"), "中文前端模型 ptl.dat");
        requireFile(new File(root, "midu/zh-cn/ptl.idx"), "中文前端模型 ptl.idx");
        requireFile(new File(root, "midu/" + TARGET_VOICE_TYPE + "/ptl.dat"),
                TARGET_VOICE_CODE + " 音色模型 ptl.dat");
        requireFile(new File(root, "midu/" + TARGET_VOICE_TYPE + "/ptl.idx"),
                TARGET_VOICE_CODE + " 音色模型 ptl.idx");
    }

    private static void requireFile(File file, String label) {
        if (!file.isFile() || file.length() <= 0L) {
            throw new IllegalArgumentException("语音包缺少 " + label);
        }
    }

    private static void writeSingleVoiceMetadata(File staging) throws Exception {
        String config = "bytedance:\n"
                + "- !!org.nobody.multitts.tts.speaker.Speaker\n"
                + "  avatar: null\n"
                + "  code: " + TARGET_VOICE_CODE + "\n"
                + "  desc: null\n"
                + "  extendUI: null\n"
                + "  gender: 1\n"
                + "  locale: zh-CN\n"
                + "  name: " + TARGET_VOICE_NAME + "\n"
                + "  note: null\n"
                + "  param: midu\n"
                + "  pitch: 1.0\n"
                + "  sampleRate: " + TARGET_SAMPLE_RATE + "\n"
                + "  speed: 1.0\n"
                + "  type: 0\n"
                + "  volume: 1.0\n";
        String engines = "!!org.nobody.multitts.tts.engine.EngineConfig\n"
                + "engines:\n"
                + "- code: bytedance\n"
                + "  name: 字节跳动\n"
                + "  note: ''\n"
                + "  type: inner\n";
        writeUtf8(new File(staging, "config.yaml"), config);
        writeUtf8(new File(staging, "engines.yaml"), engines);
    }

    private static void writeUtf8(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建目录：" + parent);
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] readSmallEntry(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (output.size() + count > maxBytes) break;
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String normalizedName(String name) {
        if (name == null) return "";
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private static File safeOutput(File root, String name) throws Exception {
        File output = new File(root, name);
        String rootPath = root.getCanonicalPath() + File.separator;
        String outputPath = output.getCanonicalPath();
        if (!outputPath.startsWith(rootPath)) {
            throw new IllegalArgumentException("压缩包路径不安全：" + name);
        }
        return output;
    }

    private static long deleteRecursive(File file) {
        if (file == null || !file.exists()) return 0L;
        long size = file.isFile() ? file.length() : 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) size += deleteRecursive(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        return size;
    }

    public static final class Result {
        public final int voiceCount;
        public final String defaultVoice;
        public final String packageRoot;
        public final long extractedBytes;

        Result(int voiceCount, String defaultVoice, String packageRoot, long extractedBytes) {
            this.voiceCount = voiceCount;
            this.defaultVoice = defaultVoice;
            this.packageRoot = packageRoot;
            this.extractedBytes = extractedBytes;
        }
    }
}
