package com.chat.speech.importer;

import android.content.Context;
import android.net.Uri;

import com.chat.speech.SpeechPrefs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
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
        int voices = 0;
        String sourceName = "MultiTTS 导入源";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase(Locale.US);
                if (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json")) {
                    String text = new String(readEntry(zis), "UTF-8");
                    all.append('\n').append(text);
                    if (name.contains("config")) voices += countVoices(text);
                    if (name.contains("engine")) sourceName = parseEngineName(text, sourceName);
                }
            }
        }
        Result result = importText(context, all.toString(), sourceName);
        if (voices > 0) result.voiceCount = voices;
        new SpeechPrefs(context).setImportedSource(sourceName, result.voiceCount);
        return result;
    }

    private static Result importText(Context context, String text, String sourceName) {
        SpeechPrefs prefs = new SpeechPrefs(context);
        String format = parseAudioFormat(text);
        if (format != null) prefs.setAudioFormat(format);
        int voiceCount = countVoices(text);
        prefs.setImportedSource(sourceName, voiceCount);
        prefs.setMsEnabled(true);
        return new Result(sourceName, voiceCount, format == null ? prefs.getAudioFormat() : format);
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
        Matcher matcher = Pattern.compile("name\\s*:\\s*([^\\n\\r]+)").matcher(text);
        if (matcher.find()) return matcher.group(1).trim();
        return def;
    }

    private static int countVoices(String text) {
        if (text == null) return 0;
        int count = 0;
        Matcher matcher = Pattern.compile("(?m)^\\s*code:\\s*[-A-Za-z0-9]+(?:Neural|MultilingualNeural)\\s*$").matcher(text);
        while (matcher.find()) count++;
        if (count == 0) {
            Matcher json = Pattern.compile("[a-z]{2}-[A-Z]{2}-[A-Za-z0-9]+(?:Neural|MultilingualNeural)").matcher(text);
            while (json.find()) count++;
        }
        return count;
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

        Result(String sourceName, int voiceCount, String audioFormat) {
            this.sourceName = sourceName;
            this.voiceCount = voiceCount;
            this.audioFormat = audioFormat;
        }
    }
}
