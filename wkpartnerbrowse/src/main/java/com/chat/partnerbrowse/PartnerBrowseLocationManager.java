package com.chat.partnerbrowse;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Silent location helper for partner browse.
 *
 * Product rules:
 * 1. Granted: use location silently.
 * 2. Not granted: show a weak prompt; request system permission only after user taps.
 * 3. Success/upload cooldown: 24h.
 * 4. Failure cooldown: 90min, not 24h, otherwise one failure blocks nearby for too long.
 * 5. Location timeout: 12s.
 * 6. Cache validity: 30 days; upload again if moved more than 30km.
 */
public class PartnerBrowseLocationManager {
    public static final int REQUEST_LOCATION_PERMISSION = 8026;

    private static final String PREF = "partner_browse_location";
    private static final String KEY_LAST_SUCCESS_MS = "last_success_ms";
    private static final String KEY_LAST_FAIL_MS = "last_fail_ms";
    private static final String KEY_LAST_PROMPT_CLOSE_MS = "last_prompt_close_ms";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";

    private static final long SUCCESS_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long FAIL_RETRY_INTERVAL_MS = 90L * 60L * 1000L;
    private static final long PROMPT_HIDE_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    private static final long ACCEPT_LAST_KNOWN_MS = 6L * 60L * 60L * 1000L;
    private static final long LOCATION_TIMEOUT_MS = 12L * 1000L;
    private static final float REUPLOAD_DISTANCE_METERS = 30_000f;
    private static final int RADIUS_METERS = 70_000;
    private static final int EXPIRES_DAYS = 30;

    private final Context appContext;
    private final SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean requesting;

    public PartnerBrowseLocationManager(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
        this.sp = appContext == null ? null : appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public boolean hasLocationPermission() {
        if (appContext == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean shouldShowSoftPrompt() {
        if (hasLocationPermission()) return false;
        if (sp == null) return true;
        long closedAt = sp.getLong(KEY_LAST_PROMPT_CLOSE_MS, 0L);
        return System.currentTimeMillis() - closedAt >= PROMPT_HIDE_INTERVAL_MS;
    }

    public void suppressPromptTemporarily() {
        if (sp != null) sp.edit().putLong(KEY_LAST_PROMPT_CLOSE_MS, System.currentTimeMillis()).apply();
    }

    public void requestPermission(Activity activity) {
        if (activity == null || hasLocationPermission()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    public void maybeUpdateLocation(boolean userInitiated) {
        if (appContext == null || sp == null || requesting || !hasLocationPermission()) return;
        long now = System.currentTimeMillis();
        long lastSuccess = sp.getLong(KEY_LAST_SUCCESS_MS, 0L);
        long lastFail = sp.getLong(KEY_LAST_FAIL_MS, 0L);
        if (!userInitiated && lastFail > 0 && now - lastFail < FAIL_RETRY_INTERVAL_MS) return;

        LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            markFail();
            return;
        }

        Location lastKnown = getBestLastKnownLocation(lm, now);
        if (lastKnown != null && shouldUpload(lastKnown, userInitiated, now)) {
            uploadLocation(lastKnown);
            return;
        }

        if (!userInitiated && lastSuccess > 0 && now - lastSuccess < SUCCESS_CHECK_INTERVAL_MS) return;

        requestSingleLocation(lm);
    }

    private Location getBestLastKnownLocation(LocationManager lm, long now) {
        Location best = null;
        try {
            List<String> providers = lm.getProviders(true);
            if (providers == null) return null;
            for (String provider : providers) {
                if (TextUtils.isEmpty(provider)) continue;
                Location loc;
                try {
                    loc = lm.getLastKnownLocation(provider);
                } catch (SecurityException ignored) {
                    continue;
                } catch (Throwable ignored) {
                    continue;
                }
                if (loc == null) continue;
                if (now - loc.getTime() > ACCEPT_LAST_KNOWN_MS) continue;
                if (best == null || loc.getAccuracy() < best.getAccuracy()) best = loc;
            }
        } catch (Throwable ignored) {
            return null;
        }
        return best;
    }

    private void requestSingleLocation(LocationManager lm) {
        requesting = true;
        final LocationListener[] holder = new LocationListener[1];
        Runnable timeout = () -> {
            if (!requesting) return;
            requesting = false;
            try {
                if (holder[0] != null) lm.removeUpdates(holder[0]);
            } catch (Throwable ignored) {
            }
            markFail();
        };
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (!requesting) return;
                requesting = false;
                handler.removeCallbacks(timeout);
                try {
                    lm.removeUpdates(this);
                } catch (Throwable ignored) {
                }
                if (location != null && shouldUpload(location, true, System.currentTimeMillis())) uploadLocation(location);
                else markFail();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        holder[0] = listener;
        try {
            String provider = chooseProvider(lm);
            if (TextUtils.isEmpty(provider)) {
                requesting = false;
                markFail();
                return;
            }
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            handler.postDelayed(timeout, LOCATION_TIMEOUT_MS);
        } catch (SecurityException ignored) {
            requesting = false;
            markFail();
        } catch (Throwable ignored) {
            requesting = false;
            markFail();
        }
    }

    private String chooseProvider(LocationManager lm) {
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER;
            List<String> providers = lm.getProviders(true);
            return providers == null || providers.isEmpty() ? "" : providers.get(0);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean shouldUpload(Location loc, boolean userInitiated, long now) {
        if (loc == null || sp == null) return false;
        if (userInitiated) return true;
        long lastSuccess = sp.getLong(KEY_LAST_SUCCESS_MS, 0L);
        if (lastSuccess <= 0) return true;
        if (now - lastSuccess >= SUCCESS_CHECK_INTERVAL_MS) return true;
        float[] result = new float[1];
        double oldLat = Double.longBitsToDouble(sp.getLong(KEY_LAT, Double.doubleToLongBits(0d)));
        double oldLng = Double.longBitsToDouble(sp.getLong(KEY_LNG, Double.doubleToLongBits(0d)));
        if (oldLat == 0d && oldLng == 0d) return true;
        Location.distanceBetween(oldLat, oldLng, loc.getLatitude(), loc.getLongitude(), result);
        return result[0] >= REUPLOAD_DISTANCE_METERS;
    }

    private void uploadLocation(Location loc) {
        Map<String, Object> body = new HashMap<>();
        body.put("lat", loc.getLatitude());
        body.put("lng", loc.getLongitude());
        body.put("latitude", loc.getLatitude());
        body.put("longitude", loc.getLongitude());
        body.put("accuracy", Math.max(0f, loc.getAccuracy()));
        body.put("radius_meters", RADIUS_METERS);
        body.put("expires_days", EXPIRES_DAYS);
        body.put("source", TextUtils.equals(loc.getProvider(), LocationManager.GPS_PROVIDER) ? "gps" : "network");
        PartnerBrowseModel.getInstance().uploadLocation(body, (code, msg, data) -> {
            if (code == com.chat.base.net.HttpResponseCode.success || code == 200 || code == 0) {
                markSuccess(loc);
            } else {
                markFail();
            }
        });
    }

    private void markSuccess(Location loc) {
        if (sp == null || loc == null) return;
        sp.edit()
                .putLong(KEY_LAST_SUCCESS_MS, System.currentTimeMillis())
                .putLong(KEY_LAST_FAIL_MS, 0L)
                .putLong(KEY_LAT, Double.doubleToLongBits(loc.getLatitude()))
                .putLong(KEY_LNG, Double.doubleToLongBits(loc.getLongitude()))
                .apply();
    }

    private void markFail() {
        if (sp != null) sp.edit().putLong(KEY_LAST_FAIL_MS, System.currentTimeMillis()).apply();
    }
}
