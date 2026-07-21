package com.chat.speech.engine;

import com.chat.speech.SpeechPrefs;
import com.chat.speech.model.TtsSource;

import org.json.JSONObject;

/**
 * Runtime protocol values for the unofficial Microsoft Edge Read Aloud service.
 *
 * <p>The protocol changes occasionally. Values are read from {@link TtsSource#extraJson}
 * first so a small server-side change can be distributed as a source JSON update instead
 * of requiring an APK release.</p>
 */
public final class EdgeProtocolConfig {
    public static final String DEFAULT_TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    public static final String DEFAULT_WSS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
                    + "?TrustedClientToken=" + DEFAULT_TRUSTED_CLIENT_TOKEN;
    public static final String DEFAULT_CHROMIUM_FULL_VERSION = "143.0.3650.75";
    public static final String DEFAULT_SEC_MS_GEC_VERSION = "1-" + DEFAULT_CHROMIUM_FULL_VERSION;
    public static final String DEFAULT_ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";
    public static final String DEFAULT_ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 12;
    public static final int DEFAULT_RECEIVE_TIMEOUT_SECONDS = 60;

    public final String wssUrl;
    public final String trustedClientToken;
    public final String secMsGecVersion;
    public final String origin;
    public final String userAgent;
    public final String acceptLanguage;
    public final String outputFormat;
    public final int connectTimeoutSeconds;
    public final int receiveTimeoutSeconds;

    private EdgeProtocolConfig(
            String wssUrl,
            String trustedClientToken,
            String secMsGecVersion,
            String origin,
            String userAgent,
            String acceptLanguage,
            String outputFormat,
            int connectTimeoutSeconds,
            int receiveTimeoutSeconds
    ) {
        this.wssUrl = wssUrl;
        this.trustedClientToken = trustedClientToken;
        this.secMsGecVersion = secMsGecVersion;
        this.origin = origin;
        this.userAgent = userAgent;
        this.acceptLanguage = acceptLanguage;
        this.outputFormat = outputFormat;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.receiveTimeoutSeconds = receiveTimeoutSeconds;
    }

    public static EdgeProtocolConfig from(TtsSource source) {
        TtsSource safeSource = source == null ? TtsSource.edgeWebSocketTemplate() : source;
        safeSource.normalize();

        JSONObject extra = new JSONObject();
        try {
            if (!TtsSource.isEmpty(safeSource.extraJson)) {
                extra = new JSONObject(safeSource.extraJson);
            }
        } catch (Exception ignored) {
            // A malformed optional override must not make the built-in source unusable.
        }

        String chromiumFullVersion = nonEmpty(
                extra.optString("chromiumFullVersion", ""),
                DEFAULT_CHROMIUM_FULL_VERSION
        );
        String trustedClientToken = nonEmpty(
                extra.optString("trustedClientToken", ""),
                DEFAULT_TRUSTED_CLIENT_TOKEN
        );
        String defaultWssUrl = DEFAULT_WSS_URL.replace(DEFAULT_TRUSTED_CLIENT_TOKEN, trustedClientToken);
        String wssUrl = nonEmpty(extra.optString("wssUrl", ""), defaultWssUrl);
        String secMsGecVersion = nonEmpty(
                extra.optString("secMsGecVersion", ""),
                "1-" + chromiumFullVersion
        );
        String origin = nonEmpty(extra.optString("origin", ""), DEFAULT_ORIGIN);
        String userAgent = nonEmpty(
                extra.optString("userAgent", ""),
                nonEmpty(safeSource.userAgent, defaultUserAgent(chromiumFullVersion))
        );
        String acceptLanguage = nonEmpty(
                extra.optString("acceptLanguage", ""),
                nonEmpty(safeSource.acceptLanguage, DEFAULT_ACCEPT_LANGUAGE)
        );
        String outputFormat = nonEmpty(
                safeSource.audioFormat,
                nonEmpty(extra.optString("outputFormat", ""), SpeechPrefs.DEFAULT_AUDIO_FORMAT)
        );
        int connectTimeoutSeconds = clamp(
                extra.optInt("connectTimeoutSeconds", DEFAULT_CONNECT_TIMEOUT_SECONDS),
                5,
                30
        );
        int receiveTimeoutSeconds = clamp(
                extra.optInt("receiveTimeoutSeconds", DEFAULT_RECEIVE_TIMEOUT_SECONDS),
                15,
                120
        );

        return new EdgeProtocolConfig(
                wssUrl,
                trustedClientToken,
                secMsGecVersion,
                origin,
                userAgent,
                acceptLanguage,
                outputFormat,
                connectTimeoutSeconds,
                receiveTimeoutSeconds
        );
    }

    public static String defaultUserAgent() {
        return defaultUserAgent(DEFAULT_CHROMIUM_FULL_VERSION);
    }

    public static String defaultExtraJson() {
        try {
            JSONObject object = new JSONObject();
            object.put("wssUrl", DEFAULT_WSS_URL);
            object.put("trustedClientToken", DEFAULT_TRUSTED_CLIENT_TOKEN);
            object.put("chromiumFullVersion", DEFAULT_CHROMIUM_FULL_VERSION);
            object.put("secMsGecVersion", DEFAULT_SEC_MS_GEC_VERSION);
            object.put("origin", DEFAULT_ORIGIN);
            object.put("acceptLanguage", DEFAULT_ACCEPT_LANGUAGE);
            object.put("outputFormat", SpeechPrefs.DEFAULT_AUDIO_FORMAT);
            object.put("connectTimeoutSeconds", DEFAULT_CONNECT_TIMEOUT_SECONDS);
            object.put("receiveTimeoutSeconds", DEFAULT_RECEIVE_TIMEOUT_SECONDS);
            return object.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    public EdgeProtocolConfig withOutputFormat(String format) {
        if (format == null || format.trim().isEmpty() || outputFormat.equals(format.trim())) {
            return this;
        }
        return new EdgeProtocolConfig(
                wssUrl,
                trustedClientToken,
                secMsGecVersion,
                origin,
                userAgent,
                acceptLanguage,
                format.trim(),
                connectTimeoutSeconds,
                receiveTimeoutSeconds
        );
    }

    public String buildWebSocketUrl(String connectionId, String secMsGec) {
        String separator = wssUrl.contains("?") ? "&" : "?";
        return wssUrl
                + separator + "ConnectionId=" + connectionId
                + "&Sec-MS-GEC=" + secMsGec
                + "&Sec-MS-GEC-Version=" + secMsGecVersion;
    }

    private static String defaultUserAgent(String chromiumFullVersion) {
        String major = chromiumFullVersion;
        int dot = major.indexOf('.');
        if (dot > 0) major = major.substring(0, dot);
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/" + major + ".0.0.0 Safari/537.36"
                + " Edg/" + major + ".0.0.0";
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
