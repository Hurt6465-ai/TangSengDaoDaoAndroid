package com.chat.speech.model;

import org.json.JSONException;
import org.json.JSONObject;

public class TtsSource {
    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_MS_TRANSLATOR = "ms_translator";
    public static final String TYPE_EDGE_WEBSOCKET = "edge_websocket";
    public static final String TYPE_CUSTOM_HTTP = "custom_http";
    public static final String TYPE_CUSTOM_WEBSOCKET = "custom_websocket";
    public static final String TYPE_OFFLINE_RESERVED = "offline_reserved";
    public static final String TYPE_UNKNOWN = "unknown";

    public String id;
    public String name;
    public String type;
    public String category;
    public String note;
    public boolean enabled;
    public boolean userEditable;

    // Microsoft Translator compatible REST source.
    public String endpointUrl;
    public String ttsUrlTemplate;
    public String appId;
    public String secretBase64;
    public String audioFormat;
    public String clientVersion;
    public String userAgent;
    public String acceptLanguage;
    public String homeGeographicRegion;
    public String userId;

    // Reserved for future configurable sources.
    public String httpMethod;
    public String requestTemplate;
    public String headersJson;
    public String extraJson;

    public TtsSource() {
    }

    public static TtsSource system() {
        TtsSource source = new TtsSource();
        source.id = "system_tts";
        source.name = "系统 TTS";
        source.type = TYPE_SYSTEM;
        source.category = "系统兜底";
        source.note = "调用用户手机已安装的 Android 系统 TTS 引擎。";
        source.enabled = true;
        source.userEditable = false;
        source.audioFormat = "system";
        return source;
    }

    public static TtsSource builtinMsTranslator() {
        TtsSource source = new TtsSource();
        source.id = "builtin_ms_translator";
        source.name = "微软翻译兼容源";
        source.type = TYPE_MS_TRANSLATOR;
        source.category = "在线兼容源";
        source.note = "使用用户端直连的微软翻译兼容 TTS REST 链路，非官方稳定 API，可能失效或限流。";
        source.enabled = false;
        source.userEditable = true;
        source.endpointUrl = "https://dev.microsofttranslator.com/apps/endpoint?api-version=1.0";
        source.ttsUrlTemplate = "https://{region}.tts.speech.microsoft.com/cognitiveservices/v1";
        source.appId = "MSTranslatorAndroidApp";
        source.secretBase64 = "oik6PdDdMnOXemTbwvMn9de/h9lFnfBaCWbGMMZqqoSaQaqUOqjVGm5NqsmjcBI1x+sS9ugjB55HEJWRiFXYFw==";
        source.audioFormat = "audio-24khz-48kbitrate-mono-mp3";
        source.clientVersion = "4.0.530a 5fe1dc6c";
        source.userAgent = "okhttp/4.5.0";
        source.acceptLanguage = "zh-Hans";
        source.homeGeographicRegion = "zh-Hans-CN";
        source.userId = "0f04d16a175c411e";
        source.httpMethod = "POST";
        return source;
    }

    public static TtsSource edgeWebSocketTemplate() {
        TtsSource source = new TtsSource();
        source.id = "edge_readaloud_template";
        source.name = "Edge TTS WebSocket 模板";
        source.type = TYPE_EDGE_WEBSOCKET;
        source.category = "在线兼容源 · 预留";
        source.note = "预留配置。需要后续接入 OkHttp WebSocket 引擎后才能真正朗读。";
        source.enabled = false;
        source.userEditable = true;
        source.audioFormat = "audio-24khz-48kbitrate-mono-mp3";
        source.userAgent = "Mozilla/5.0";
        source.extraJson = "{}";
        return source;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("tsddSpeechSource", 1);
        object.put("id", safe(id));
        object.put("name", safe(name));
        object.put("type", safe(type));
        object.put("category", safe(category));
        object.put("note", safe(note));
        object.put("enabled", enabled);
        object.put("userEditable", userEditable);
        object.put("endpointUrl", safe(endpointUrl));
        object.put("ttsUrlTemplate", safe(ttsUrlTemplate));
        object.put("appId", safe(appId));
        object.put("secretBase64", safe(secretBase64));
        object.put("audioFormat", safe(audioFormat));
        object.put("clientVersion", safe(clientVersion));
        object.put("userAgent", safe(userAgent));
        object.put("acceptLanguage", safe(acceptLanguage));
        object.put("homeGeographicRegion", safe(homeGeographicRegion));
        object.put("userId", safe(userId));
        object.put("httpMethod", safe(httpMethod));
        object.put("requestTemplate", safe(requestTemplate));
        object.put("headersJson", safe(headersJson));
        object.put("extraJson", safe(extraJson));
        return object;
    }

    public static TtsSource fromJson(JSONObject object) {
        if (object == null) return null;
        TtsSource source = new TtsSource();
        source.id = object.optString("id", "").trim();
        source.name = object.optString("name", "").trim();
        source.type = object.optString("type", TYPE_UNKNOWN).trim();
        source.category = object.optString("category", "用户导入").trim();
        source.note = object.optString("note", "").trim();
        source.enabled = object.optBoolean("enabled", false);
        source.userEditable = object.optBoolean("userEditable", true);
        source.endpointUrl = object.optString("endpointUrl", "").trim();
        source.ttsUrlTemplate = object.optString("ttsUrlTemplate", "").trim();
        source.appId = object.optString("appId", "").trim();
        source.secretBase64 = object.optString("secretBase64", "").trim();
        source.audioFormat = object.optString("audioFormat", "").trim();
        source.clientVersion = object.optString("clientVersion", "").trim();
        source.userAgent = object.optString("userAgent", "").trim();
        source.acceptLanguage = object.optString("acceptLanguage", "").trim();
        source.homeGeographicRegion = object.optString("homeGeographicRegion", "").trim();
        source.userId = object.optString("userId", "").trim();
        source.httpMethod = object.optString("httpMethod", "").trim();
        source.requestTemplate = object.optString("requestTemplate", "");
        source.headersJson = object.optString("headersJson", "");
        source.extraJson = object.optString("extraJson", "");
        source.normalize();
        return source;
    }

    public void normalize() {
        if (isEmpty(id)) id = "source_" + Math.abs((name + type + System.currentTimeMillis()).hashCode());
        if (isEmpty(name)) name = "用户导入 TTS 源";
        if (isEmpty(type)) type = TYPE_UNKNOWN;
        if (isEmpty(category)) category = "用户导入";
        if (isEmpty(audioFormat)) audioFormat = "audio-24khz-48kbitrate-mono-mp3";
        if (TYPE_MS_TRANSLATOR.equals(type)) {
            TtsSource def = builtinMsTranslator();
            if (isEmpty(endpointUrl)) endpointUrl = def.endpointUrl;
            if (isEmpty(ttsUrlTemplate)) ttsUrlTemplate = def.ttsUrlTemplate;
            if (isEmpty(appId)) appId = def.appId;
            if (isEmpty(secretBase64)) secretBase64 = def.secretBase64;
            if (isEmpty(clientVersion)) clientVersion = def.clientVersion;
            if (isEmpty(userAgent)) userAgent = def.userAgent;
            if (isEmpty(acceptLanguage)) acceptLanguage = def.acceptLanguage;
            if (isEmpty(homeGeographicRegion)) homeGeographicRegion = def.homeGeographicRegion;
            if (isEmpty(userId)) userId = def.userId;
        }
    }

    public boolean canSpeakOnline() {
        return TYPE_MS_TRANSLATOR.equals(type);
    }

    public String displayType() {
        if (TYPE_SYSTEM.equals(type)) return "系统 TTS";
        if (TYPE_MS_TRANSLATOR.equals(type)) return "微软翻译兼容源";
        if (TYPE_EDGE_WEBSOCKET.equals(type)) return "Edge TTS WebSocket";
        if (TYPE_CUSTOM_HTTP.equals(type)) return "自定义 HTTP TTS";
        if (TYPE_CUSTOM_WEBSOCKET.equals(type)) return "自定义 WebSocket TTS";
        if (TYPE_OFFLINE_RESERVED.equals(type)) return "离线语音包";
        return type == null || type.isEmpty() ? "未知" : type;
    }

    public String shortSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append(displayType());
        if (!safe(category).isEmpty()) builder.append(" · ").append(category);
        if (!safe(audioFormat).isEmpty()) builder.append("\n格式：").append(audioFormat);
        if (!safe(note).isEmpty()) builder.append("\n").append(note);
        return builder.toString();
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
