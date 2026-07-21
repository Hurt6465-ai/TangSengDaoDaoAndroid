package com.chat.speech.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/** Generates Edge Read Aloud request tokens and corrects small device clock skew. */
public final class EdgeDrm {
    private static final long WINDOWS_EPOCH_SECONDS = 11_644_473_600L;
    private static final long FIVE_MINUTES_SECONDS = 300L;
    private static final long FILETIME_TICKS_PER_SECOND = 10_000_000L;
    private static final AtomicLong CLOCK_SKEW_MILLIS = new AtomicLong(0L);
    private static final SecureRandom RANDOM = new SecureRandom();

    private EdgeDrm() {
    }

    public static String generateSecMsGec(String trustedClientToken) throws Exception {
        long unixMillis = System.currentTimeMillis() + CLOCK_SKEW_MILLIS.get();
        long unixSeconds = Math.floorDiv(unixMillis, 1000L);
        long roundedSeconds = unixSeconds - Math.floorMod(unixSeconds, FIVE_MINUTES_SECONDS);
        long windowsSeconds = roundedSeconds + WINDOWS_EPOCH_SECONDS;
        long ticks = Math.multiplyExact(windowsSeconds, FILETIME_TICKS_PER_SECOND);
        String input = ticks + trustedClientToken;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.US_ASCII));
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            result.append(String.format(Locale.US, "%02X", value & 0xFF));
        }
        return result.toString();
    }

    public static boolean adjustFromServerDate(String serverDate) {
        if (serverDate == null || serverDate.trim().isEmpty()) return false;
        try {
            SimpleDateFormat format = new SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss zzz",
                    Locale.US
            );
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date parsed = format.parse(serverDate.trim());
            if (parsed == null) return false;
            long skew = parsed.getTime() - System.currentTimeMillis();
            // A wildly different Date header is more likely a proxy/server problem than clock skew.
            if (Math.abs(skew) > 24L * 60L * 60L * 1000L) return false;
            CLOCK_SKEW_MILLIS.set(skew);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String randomMuid() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder result = new StringBuilder(32);
        for (byte value : bytes) {
            result.append(String.format(Locale.US, "%02X", value & 0xFF));
        }
        return result.toString();
    }
}
