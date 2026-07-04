package com.chat.speech.importer;

import android.content.Context;
import android.net.Uri;

import com.chat.speech.SpeechPrefs;
import com.chat.speech.model.TtsSource;
import com.chat.speech.model.TtsVoice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

public class MultiTtsImporter {
    private MultiTtsImporter() {}

    public static Result importFromUri(Context context, Uri uri) throws Exception {
        byte[] data = readAll(context.getContentResolver().openInputStream(uri));
        if (data.length >= 2 && data[0] == 'P' && data[1] == 'K') {
            return importZip(context, data);
        }
        String text = new String(data, "UTF-8");
        return importText(context, text, "用户导入配置");
    }

    private static Result importZip(Context context, byte[] data) throws Exception {
        StringBuilder all = new StringBuilder();
        String sourceName = "MultiTTS 导入源";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String lower = entry.getName().toLowerCase(Locale.US);
                if (lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".json")) {
                    String text = new String(readEntry(zis), "UTF-8");
                    all.append('\n').append(text);
                    if (lower.contains("engine")) sourceName = parseEngineName(text, sourceName);
                }
            }
        }
        return importText(context, all.toString(), sourceName);
    }

    private static Result importText(Context context, String text, String sourceName) {
        SpeechPrefs prefs = new SpeechPrefs(context);
        String format = parseAudioFormat(text);
        if (format != null) prefs.setAudioFormat(format);

        String sourceType = detectSourceType(text);
        List<TtsVoice> voices = parseVoices(text, sourceName, sourceType);
        if (voices.isEmpty() && SpeechPrefs.SOURCE_TYPE_MS_TRANSLATOR.equals(sourceType)) {
            voices.addAll(SpeechPrefs.defaultVoices());
        }
        if (!voices.isEmpty()) {
            prefs.saveImportedVoices(voices);
            autoSelectDefaultVoices(prefs, voices);
        }

        int voiceCount = voices.size() > 0 ? voices.size() : countVoices(text);
        prefs.setImportedSource(sourceName, voiceCount, sourceType);
        if (SpeechPrefs.SOURCE_TYPE_MS_TRANSLATOR.equals(sourceType)) {
            TtsSource source = TtsSource.builtinMsTranslator();
            source.name = sourceName == null || sourceName.trim().isEmpty() ? source.name : sourceName.trim();
            if (format != null && !format.trim().isEmpty()) source.audioFormat = format.trim();
            prefs.upsertSource(source, true);
            prefs.setMsEnabled(true);
        }
        return new Result(sourceName, voiceCount, format == null ? prefs.getAudioFormat() : format, sourceType);
    }

    private static void autoSelectDefaultVoices(SpeechPrefs prefs, List<TtsVoice> voices) {
        boolean hasCurrentZh = false;
        boolean hasCurrentMy = false;
        for (TtsVoice voice : voices) {
            if (voice.code.equals(prefs.getZhVoice())) hasCurrentZh = true;
            if (voice.code.equals(prefs.getMyVoice())) hasCurrentMy = true;
        }
        if (!hasCurrentZh) {
            for (TtsVoice voice : voices) {
                if (voice.isChinese()) {
                    prefs.setZhVoice(voice.code);
                    break;
                }
            }
        }
        if (!hasCurrentMy) {
            for (TtsVoice voice : voices) {
                if (voice.isMyanmar()) {
                    prefs.setMyVoice(voice.code);
                    break;
                }
            }
        }
    }

    private static List<TtsVoice> parseVoices(String text, String sourceName, String sourceType) {
        Map<String, TtsVoice> map = new LinkedHashMap<>();
        if (text == null) return new ArrayList<>();

        // MultiTTS YAML speaker blocks.
        Matcher blockMatcher = Pattern.compile("(?s)-\\s*!!org\\.nobody\\.multitts\\.tts\\.speaker\\.Speaker(.*?)(?=\\n-\\s*!!org\\.nobody\\.multitts\\.tts\\.speaker\\.Speaker|\\z)").matcher(text);
        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            String code = yamlValue(block, "code");
            if (code.isEmpty()) continue;
            String name = yamlValue(block, "name");
            String locale = yamlValue(block, "locale");
            int gender = parseGender(yamlValue(block, "gender"));
            if (locale.isEmpty()) locale = inferLocale(code);
            if (name.isEmpty()) name = inferName(code);
            map.put(code, new TtsVoice(code, name, locale, gender, sourceType, sourceName));
        }

        // Microsoft voices/list JSON-like text or plugin text containing ShortName/Locale/LocalName.
        Matcher shortName = Pattern.compile("(?s)\\\"ShortName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"(.{0,800})").matcher(text);
        while (shortName.find()) {
            String code = shortName.group(1);
            String near = shortName.group(2);
            String locale = jsonNear(near, "Locale");
            String name = jsonNear(near, "LocalName");
            String gender = jsonNear(near, "Gender");
            if (locale.isEmpty()) locale = inferLocale(code);
            if (name.isEmpty()) name = inferName(code);
            map.put(code, new TtsVoice(code, name, locale, parseGender(gender), sourceType, sourceName));
        }

        // Fallback: match Neural voice codes.
        Matcher codes = Pattern.compile("[a-z]{2}-[A-Z]{2}-[A-Za-z0-9]+(?:MultilingualNeural|Neural)").matcher(text);
        while (codes.find()) {
            String code = codes.group();
            if (!map.containsKey(code)) {
                map.put(code, new TtsVoice(code, inferName(code), inferLocale(code), TtsVoice.GENDER_UNKNOWN, sourceType, sourceName));
            }
        }
        return new ArrayList<>(map.values());
    }

    private static String detectSourceType(String text) {
        if (text == null) return SpeechPrefs.SOURCE_TYPE_UNKNOWN;
        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("mstrans") || lower.contains("microsofttranslator") || lower.contains("tts.speech.microsoft.com") || lower.contains("mstts")) {
            return SpeechPrefs.SOURCE_TYPE_MS_TRANSLATOR;
        }
        return SpeechPrefs.SOURCE_TYPE_UNKNOWN;
    }

    private static String parseAudioFormat(String text) {
        if (text == null) return null;
        Matcher json = Pattern.compile("\\\"audioFormat\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(text);
        if (json.find()) return json.group(1);
        Matcher yaml = Pattern.compile("audioFormat\\s*[:=]\\s*([A-Za-z0-9_\\-]+)").matcher(text);
        if (yaml.find()) return yaml.group(1);
        Matcher plain = Pattern.compile("audio-[0-9a-zA-Z\\-]+(?:mp3|opus|riff|raw)").matcher(text);
        if (plain.find()) return plain.group();
        return null;
    }

    private static String parseEngineName(String text, String def) {
        if (text == null) return def;
        Matcher matcher = Pattern.compile("(?m)^\\s*name\\s*:\\s*([^\\n\\r]+)").matcher(text);
        if (matcher.find()) return cleanYamlValue(matcher.group(1));
        Matcher json = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(text);
        if (json.find()) return json.group(1).trim();
        return def;
    }

    private static String yamlValue(String block, String key) {
        if (block == null) return "";
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*([^\\n\\r]+)").matcher(block);
        return matcher.find() ? cleanYamlValue(matcher.group(1)) : "";
    }

    private static String cleanYamlValue(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) v = v.substring(1, v.length() - 1);
        if (v.startsWith("'") && v.endsWith("'") && v.length() >= 2) v = v.substring(1, v.length() - 1);
        if ("null".equalsIgnoreCase(v)) return "";
        return v.trim();
    }

    private static String jsonNear(String text, String key) {
        if (text == null) return "";
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String inferLocale(String code) {
        if (code != null && code.length() >= 5) return code.substring(0, 5);
        return "";
    }

    private static String inferName(String code) {
        if (code == null) return "";
        String name = code;
        int last = name.lastIndexOf('-');
        if (last >= 0 && last + 1 < name.length()) name = name.substring(last + 1);
        name = name.replace("MultilingualNeural", " 多语言").replace("Neural", "");
        return name;
    }

    private static int parseGender(String value) {
        if (value == null) return TtsVoice.GENDER_UNKNOWN;
        String v = value.trim().toLowerCase(Locale.US);
        if ("0".equals(v) || v.contains("female") || v.contains("女")) return TtsVoice.GENDER_FEMALE;
        if ("1".equals(v) || v.contains("male") || v.contains("男")) return TtsVoice.GENDER_MALE;
        return TtsVoice.GENDER_UNKNOWN;
    }

    private static int countVoices(String text) {
        return parseVoices(text, "统计", SpeechPrefs.SOURCE_TYPE_UNKNOWN).size();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        try (InputStream input = in) {
            return readEntry(input);
        }
    }

    private static byte[] readEntry(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = input.read(buffer)) >= 0) out.write(buffer, 0, len);
        return out.toByteArray();
    }

    public static class Result {
        public final String sourceName;
        public int voiceCount;
        public final String audioFormat;
        public final String sourceType;

        Result(String sourceName, int voiceCount, String audioFormat, String sourceType) {
            this.sourceName = sourceName;
            this.voiceCount = voiceCount;
            this.audioFormat = audioFormat;
            this.sourceType = sourceType;
        }
    }
}
