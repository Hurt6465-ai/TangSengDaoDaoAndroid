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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports the third-party MultiTTS ByteDance offline model package without loading it into memory. */
public final class ByteDanceOfflinePackageImporter {
    private static final long MAX_UNCOMPRESSED_BYTES = 650L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 128L * 1024L * 1024L;
    private static final String SOURCE_ID = "bytedance_offline";

    private ByteDanceOfflinePackageImporter() {}

    public static boolean looksLikePackage(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 12) {
                String name = normalizedName(entry.getName());
                if (name.startsWith("bytedance/")) return true;
                if (name.equals("engines.yaml") || name.equals("engines.yml") || name.equals("config.yaml")) {
                    String text = new String(readSmallEntry(zip, 512 * 1024), "UTF-8").toLowerCase(Locale.US);
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
        if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("无法创建离线模型目录");
        File staging = new File(parent, "importing_" + System.currentTimeMillis());
        if (!staging.mkdirs()) throw new IllegalStateException("无法创建离线模型临时目录");

        String configText = "";
        boolean foundByteDance = false;
        long total = 0L;
        try (InputStream raw = app.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, 64 * 1024))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizedName(entry.getName());
                if (name.isEmpty()) continue;
                if (name.startsWith("bytedance/")) foundByteDance = true;
                boolean wanted = name.startsWith("bytedance/")
                        || name.equals("config.yaml") || name.equals("config.yml")
                        || name.equals("engines.yaml") || name.equals("engines.yml");
                if (!wanted) continue;

                File output = safeOutput(staging, name);
                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("无法创建目录：" + name);
                    continue;
                }
                File outputParent = output.getParentFile();
                if (outputParent != null && !outputParent.exists() && !outputParent.mkdirs()) {
                    throw new IllegalStateException("无法创建目录：" + outputParent);
                }
                ByteArrayOutputStream configCapture = name.equals("config.yaml") || name.equals("config.yml")
                        ? new ByteArrayOutputStream(8 * 1024) : null;
                long entryBytes = 0L;
                try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(output), 64 * 1024)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = zip.read(buffer)) >= 0) {
                        entryBytes += count;
                        total += count;
                        if (entryBytes > MAX_ENTRY_BYTES) throw new IllegalStateException("单个模型文件过大：" + name);
                        if (total > MAX_UNCOMPRESSED_BYTES) throw new IllegalStateException("离线语音包解压后过大");
                        fileOut.write(buffer, 0, count);
                        if (configCapture != null && configCapture.size() < 2 * 1024 * 1024) {
                            configCapture.write(buffer, 0, count);
                        }
                    }
                }
                if (configCapture != null) configText = configCapture.toString("UTF-8");
            }
        } catch (Exception error) {
            deleteRecursive(staging);
            throw error;
        }

        File byteDanceDir = new File(staging, "bytedance");
        if (!foundByteDance || !byteDanceDir.isDirectory()) {
            deleteRecursive(staging);
            throw new IllegalArgumentException("不是字节跳动离线语音包");
        }
        validatePackage(byteDanceDir);

        List<TtsVoice> voices = parseVoices(configText);
        if (voices.isEmpty()) voices = discoverVoices(byteDanceDir);
        String defaultVoice = chooseDefaultVoice(voices, byteDanceDir);

        File current = new File(parent, "current");
        File old = new File(parent, "old_" + System.currentTimeMillis());
        if (current.exists() && !current.renameTo(old)) {
            deleteRecursive(staging);
            throw new IllegalStateException("无法替换旧离线模型");
        }
        if (!staging.renameTo(current)) {
            if (old.exists()) //noinspection ResultOfMethodCallIgnored
                old.renameTo(current);
            deleteRecursive(staging);
            throw new IllegalStateException("无法启用新离线模型");
        }
        deleteRecursive(old);

        SpeechPrefs prefs = new SpeechPrefs(app);
        List<TtsVoice> merged = new ArrayList<>();
        for (TtsVoice voice : prefs.getAllVoices()) {
            if (!SOURCE_ID.equals(voice.sourceId)) merged.add(voice);
        }
        merged.addAll(voices);
        prefs.saveImportedVoices(merged);
        prefs.setByteDancePackageRoot(new File(current, "bytedance").getAbsolutePath());
        prefs.setByteDanceVoice(defaultVoice);
        prefs.setImportedSource("字节跳动第三方离线语音", voices.size(), TtsSource.TYPE_BYTEDANCE_OFFLINE);

        TtsSource source = TtsSource.byteDanceOffline();
        JSONObject metadata = new JSONObject();
        metadata.put("packageRoot", prefs.getByteDancePackageRoot());
        metadata.put("voiceCount", voices.size());
        metadata.put("arm64Only", true);
        source.extraJson = metadata.toString();
        prefs.upsertSource(source, true);
        return new Result(voices.size(), defaultVoice, prefs.getByteDancePackageRoot(), total);
    }

    private static void validatePackage(File root) {
        File miduBase = new File(root, "midu/zh-cn/ptl.dat");
        File fanqieBase = new File(root, "fanqie/zh-cn/ptl.dat");
        if (!miduBase.isFile() && !fanqieBase.isFile()) {
            throw new IllegalArgumentException("语音包缺少 zh-cn 前端模型");
        }
        if (!new File(root, "midu/speech_license.licbag").isFile()) {
            throw new IllegalArgumentException("语音包缺少 speech_license.licbag");
        }
    }

    private static List<TtsVoice> parseVoices(String text) {
        Map<String, TtsVoice> result = new LinkedHashMap<>();
        if (text == null) return new ArrayList<>();
        Matcher blocks = Pattern.compile(
                "(?s)-\\s*!!org\\.nobody\\.multitts\\.tts\\.speaker\\.Speaker(.*?)(?=\\n-\\s*!!org\\.nobody\\.multitts\\.tts\\.speaker\\.Speaker|\\z)"
        ).matcher(text);
        while (blocks.find()) {
            String block = blocks.group(1);
            String code = yamlValue(block, "code");
            if (code.isEmpty()) continue;
            String name = yamlValue(block, "name");
            String locale = yamlValue(block, "locale");
            String genderText = yamlValue(block, "gender");
            int gender = "0".equals(genderText) ? TtsVoice.GENDER_FEMALE
                    : "1".equals(genderText) ? TtsVoice.GENDER_MALE : TtsVoice.GENDER_UNKNOWN;
            if (name.isEmpty()) name = code;
            if (locale.isEmpty()) locale = "zh-CN";
            result.put(code, new TtsVoice(code, name, locale, gender, SOURCE_ID, "字节跳动离线"));
        }
        return new ArrayList<>(result.values());
    }

    private static List<TtsVoice> discoverVoices(File root) {
        Map<String, TtsVoice> result = new LinkedHashMap<>();
        discoverVariant(result, new File(root, "fanqie"), false);
        discoverVariant(result, new File(root, "midu"), true);
        return new ArrayList<>(result.values());
    }

    private static void discoverVariant(Map<String, TtsVoice> result, File variant, boolean midu) {
        File[] dirs = variant.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File dir : dirs) {
            String name = dir.getName();
            if (!name.endsWith("_streaming") || "zh-cn".equals(name)) continue;
            String code = name.substring(0, name.length() - "_streaming".length());
            if (midu && !code.contains("_24k") && result.containsKey(code)) code += "-md";
            result.put(code, new TtsVoice(code, code, "zh-CN", TtsVoice.GENDER_UNKNOWN, SOURCE_ID, "字节跳动离线"));
        }
    }

    private static String chooseDefaultVoice(List<TtsVoice> voices, File root) {
        String[] preferred = new String[]{"BV001_24k", "BV064_24k", "BV001", "BV004-md", "BV004"};
        for (String code : preferred) {
            for (TtsVoice voice : voices) if (code.equals(voice.code)) return code;
        }
        return voices.isEmpty() ? "BV001_24k" : voices.get(0).code;
    }

    private static String yamlValue(String block, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*([^\\n\\r]+)").matcher(block);
        if (!matcher.find()) return "";
        String value = matcher.group(1).trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return "null".equalsIgnoreCase(value) ? "" : value.trim();
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
        if (!outputPath.startsWith(rootPath)) throw new IllegalArgumentException("压缩包路径不安全：" + name);
        return output;
    }

    private static long deleteRecursive(File file) {
        if (file == null || !file.exists()) return 0L;
        long size = file.isFile() ? file.length() : 0L;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) size += deleteRecursive(child);
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
