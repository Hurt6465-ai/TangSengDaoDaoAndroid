package com.chat.dating;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chat.base.config.WKConfig;


/**
 * 交友定位策略：
 * - 第一次进入请求权限；同意后以后静默定位；
 * - 拒绝后当前页面不重复打扰，下次重新进入交友再请求；
 * - 成功上传后 72 小时内不启动定位；本地位置最多缓存 7 天；
 * - 永久拒绝时只能引导到系统设置，Android 无法再次弹系统权限框。
 */
public final class DatingLocationHelper {
    public static final int REQUEST_LOCATION = 9041;
    public static final long LOCATION_REFRESH_INTERVAL_MS = 72L * 60L * 60L * 1000L;
    public static final long LOCATION_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;

    private static final String SP = "wkdating_location";
    private static final String KEY_PERMISSION_ASKED = "permission_asked";
    private static final String KEY_LAT = "last_lat";
    private static final String KEY_LNG = "last_lng";
    private static final String KEY_ACCURACY = "last_accuracy";
    private static final String KEY_LOCATION_TIME = "last_location_time";
    private static final String KEY_UPLOAD_TIME = "last_upload_time";

    public interface Callback {
        void onSuccess(Location location, boolean needUpload);
        void onDenied(String message);
    }

    private final Activity activity;
    private final LocationManager locationManager;
    private final SharedPreferences permissionPrefs;
    private final SharedPreferences locationPrefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Callback pendingCallback;
    private LocationListener listener;
    private Runnable timeoutTask;
    private boolean requestedThisEntry;
    private boolean waitingForSettings;

    public DatingLocationHelper(Activity activity) {
        this.activity = activity;
        this.locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        this.permissionPrefs = activity.getSharedPreferences(SP, Context.MODE_PRIVATE);
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) uid = "anonymous";
        this.locationPrefs = activity.getSharedPreferences(SP + "_" + uid, Context.MODE_PRIVATE);
    }

    /** 每个交友页面实例最多触发一次权限流程。 */
    public void ensureLocation(Callback callback) {
        if (requestedThisEntry) return;
        requestedThisEntry = true;
        pendingCallback = callback;

        if (hasPermission()) {
            if (!needsRefresh()) {
                Location cached = cachedLocation();
                if (cached != null) success(cached, false);
                else locateNow();
            } else {
                locateNow();
            }
            return;
        }
        boolean askedBefore = permissionPrefs.getBoolean(KEY_PERMISSION_ASKED, false);
        boolean canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_FINE_LOCATION)
                || ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!askedBefore || canAskAgain) {
            requestSystemPermission();
        } else {
            showPermissionExplain();
        }
    }

    private void requestSystemPermission() {
        permissionPrefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply();
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_LOCATION);
    }

    public boolean handlePermissionResult(int requestCode, @NonNull int[] grantResults) {
        if (requestCode != REQUEST_LOCATION) return false;
        boolean granted = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }
        if (granted) {
            permissionPrefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply();
            locateNow();
        } else {
            permissionPrefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply();
            deny(activity.getString(R.string.dating_location_denied));
        }
        return true;
    }

    /** 只有后端上传成功后才调用，网络失败不能提前进入 72 小时冷却。 */
    public void markUploaded(Location location) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = locationPrefs.edit().putLong(KEY_UPLOAD_TIME, now);
        if (location != null) saveLocation(editor, location, now);
        editor.apply();
    }

    public Location getValidCachedLocation() {
        return cachedLocation();
    }

    /** 从系统设置返回时，如果用户已经授权，当前页面直接静默完成一次定位。 */
    public void onHostResume() {
        if (!waitingForSettings) return;
        waitingForSettings = false;
        if (hasPermission()) locateNow();
        else deny(activity.getString(R.string.dating_location_denied));
    }

    public void release() {
        waitingForSettings = false;
        stopListening();
        pendingCallback = null;
    }

    private boolean needsRefresh() {
        long now = System.currentTimeMillis();
        long uploadedAt = locationPrefs.getLong(KEY_UPLOAD_TIME, 0L);
        Location cached = cachedLocation();
        return uploadedAt <= 0L || now - uploadedAt >= LOCATION_REFRESH_INTERVAL_MS || cached == null;
    }

    private void showPermissionExplain() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.dating_location_title)
                .setMessage(R.string.dating_location_open_settings_tip)
                .setNegativeButton(R.string.dating_location_not_now,
                        (dialog, which) -> deny(activity.getString(R.string.dating_location_back_recommend)))
                .setPositiveButton(R.string.dating_location_open_settings, (dialog, which) -> {
                    try {
                        waitingForSettings = true;
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Throwable ignored) {
                        waitingForSettings = false;
                        deny(activity.getString(R.string.dating_location_back_recommend));
                    }
                })
                .show();
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void locateNow() {
        if (!hasPermission() || locationManager == null) {
            fallbackOrDeny(activity.getString(R.string.dating_location_device_unavailable));
            return;
        }

        Location cached = cachedLocation();
        Location systemCached = bestLastKnown();
        if (systemCached != null && System.currentTimeMillis() - systemCached.getTime() <= 15L * 60L * 1000L) {
            saveLocalLocation(systemCached);
            success(systemCached, true);
            return;
        }

        String provider = "";
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                provider = LocationManager.NETWORK_PROVIDER;
            }
        } catch (Throwable ignored) {
        }
        if (provider.isEmpty()) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    provider = LocationManager.GPS_PROVIDER;
                }
            } catch (Throwable ignored) {
            }
        }

        Location fallback = newer(cached, validSystemFallback(systemCached));
        if (provider.isEmpty()) {
            if (fallback != null) success(fallback, false);
            else deny(activity.getString(R.string.dating_location_service_off));
            return;
        }

        stopListening();
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                stopListening();
                saveLocalLocation(location);
                success(location, true);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        try {
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper());
        } catch (SecurityException error) {
            fallbackOrDeny(activity.getString(R.string.dating_location_permission_unavailable));
            return;
        } catch (Throwable error) {
            if (fallback != null) success(fallback, false);
            else deny(activity.getString(R.string.dating_location_retry));
            return;
        }

        timeoutTask = () -> {
            stopListening();
            if (fallback != null) success(fallback, false);
            else deny(activity.getString(R.string.dating_location_temporarily_unavailable));
        };
        handler.postDelayed(timeoutTask, 8000L);
    }

    private void fallbackOrDeny(String message) {
        Location cached = cachedLocation();
        if (cached != null) success(cached, false);
        else deny(message);
    }

    private Location cachedLocation() {
        long savedAt = locationPrefs.getLong(KEY_LOCATION_TIME, 0L);
        long now = System.currentTimeMillis();
        if (savedAt <= 0L || now - savedAt > LOCATION_CACHE_TTL_MS) return null;
        double lat = Double.longBitsToDouble(locationPrefs.getLong(KEY_LAT, Double.doubleToLongBits(0d)));
        double lng = Double.longBitsToDouble(locationPrefs.getLong(KEY_LNG, Double.doubleToLongBits(0d)));
        if (lat == 0d && lng == 0d) return null;
        Location location = new Location("wkdating_cache");
        location.setLatitude(lat);
        location.setLongitude(lng);
        location.setAccuracy(locationPrefs.getFloat(KEY_ACCURACY, 0f));
        location.setTime(savedAt);
        return location;
    }

    private void saveLocalLocation(Location location) {
        if (location == null) return;
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = locationPrefs.edit();
        saveLocation(editor, location, now);
        editor.apply();
    }

    private void saveLocation(SharedPreferences.Editor editor, Location location, long savedAt) {
        editor.putLong(KEY_LAT, Double.doubleToRawLongBits(location.getLatitude()));
        editor.putLong(KEY_LNG, Double.doubleToRawLongBits(location.getLongitude()));
        editor.putFloat(KEY_ACCURACY, Math.max(0f, location.getAccuracy()));
        editor.putLong(KEY_LOCATION_TIME, savedAt);
    }

    private Location bestLastKnown() {
        if (!hasPermission() || locationManager == null) return null;
        Location best = null;
        try {
            List<String> providers = locationManager.getProviders(true);
            for (String provider : providers) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) continue;
                best = newer(best, location);
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    private Location validSystemFallback(Location location) {
        if (location == null) return null;
        long time = location.getTime();
        if (time <= 0L || System.currentTimeMillis() - time > LOCATION_CACHE_TTL_MS) return null;
        return location;
    }

    private Location newer(Location first, Location second) {
        if (first == null) return second;
        if (second == null) return first;
        if (second.getTime() > first.getTime()) return second;
        if (second.getTime() == first.getTime() && second.getAccuracy() < first.getAccuracy()) return second;
        return first;
    }

    private void success(Location location, boolean needUpload) {
        Callback callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) callback.onSuccess(location, needUpload);
    }

    private void deny(String message) {
        stopListening();
        Callback callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) callback.onDenied(message);
    }

    private void stopListening() {
        if (timeoutTask != null) handler.removeCallbacks(timeoutTask);
        timeoutTask = null;
        if (listener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(listener);
            } catch (Throwable ignored) {
            }
        }
        listener = null;
    }
}
import java.util.List;
