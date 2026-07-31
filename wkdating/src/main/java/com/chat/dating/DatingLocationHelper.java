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

import java.util.List;

/**
 * 交友定位策略：
 * - 资料开启交友且允许显示距离时才由页面调用；
 * - 成功上传后 72 小时内只用缓存，不检查权限、不启动定位；
 * - Android“仅本次允许”失效后，下次真正需要更新时重新请求权限；
 * - 普通拒绝后当前页面不重复请求，下次重新进入再请求；
 * - 连续拒绝且系统不再允许弹窗时，才引导到系统设置；
 * - 新定位上传失败时保留一次待上传标记，下次可直接重传缓存，不重复启动定位；
 * - 本地位置最多缓存 7 天。
 */
public final class DatingLocationHelper {
    public static final int REQUEST_LOCATION = 9041;
    public static final long LOCATION_REFRESH_INTERVAL_MS = 72L * 60L * 60L * 1000L;
    public static final long LOCATION_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;

    private static final String SP = "wkdating_location";
    private static final String KEY_DENIAL_COUNT = "permission_denial_count";
    private static final String KEY_PERMISSION_PERMANENTLY_DENIED = "permission_permanently_denied";
    private static final String KEY_LAT = "last_lat";
    private static final String KEY_LNG = "last_lng";
    private static final String KEY_ACCURACY = "last_accuracy";
    private static final String KEY_LOCATION_TIME = "last_location_time";
    private static final String KEY_UPLOAD_TIME = "last_upload_time";
    private static final String KEY_PENDING_UPLOAD = "pending_upload";

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

    /** 每个交友页面实例最多触发一次权限/定位流程。 */
    public void ensureLocation(Callback callback) {
        if (requestedThisEntry) return;
        requestedThisEntry = true;
        pendingCallback = callback;

        Location cached = cachedLocation();

        // 最重要的顺序：72 小时内不需要更新时，完全不检查权限。
        // “仅本次允许”即使已经被系统收回，也不会在冷却期内误提示定位未开启。
        if (!needsRefresh()) {
            if (cached != null) success(cached, false);
            else deny(activity.getString(R.string.dating_location_temporarily_unavailable));
            return;
        }

        // 新位置已取得但上次上传失败：直接重传本地缓存，不再申请权限或重新定位。
        if (cached != null && locationPrefs.getBoolean(KEY_PENDING_UPLOAD, false)) {
            success(cached, true);
            return;
        }

        if (hasPermission()) {
            clearPermissionFailureState();
            locateNow();
            return;
        }

        // 只有已经确认系统不再弹权限框时，才去设置；“仅本次允许”失效不会设置此标记。
        if (permissionPrefs.getBoolean(KEY_PERMISSION_PERMANENTLY_DENIED, false)) {
            showPermissionExplain();
        } else {
            requestSystemPermission();
        }
    }

    private void requestSystemPermission() {
        // 交友只展示国家和模糊距离，大致位置已经足够，不主动索取精确位置。
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_LOCATION);
    }

    public boolean handlePermissionResult(int requestCode, @NonNull int[] grantResults) {
        if (requestCode != REQUEST_LOCATION) return false;

        if (hasPermission()) {
            clearPermissionFailureState();
            locateNow();
            return true;
        }

        int denialCount = permissionPrefs.getInt(KEY_DENIAL_COUNT, 0) + 1;
        boolean canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_COARSE_LOCATION);

        // 第一次拒绝无论 ROM 如何返回 rationale，都允许下次进入再请求一次。
        // 连续拒绝且系统明确不再给出 rationale，才认定为永久拒绝。
        boolean permanentlyDenied = denialCount >= 2 && !canAskAgain;
        permissionPrefs.edit()
                .putInt(KEY_DENIAL_COUNT, denialCount)
                .putBoolean(KEY_PERMISSION_PERMANENTLY_DENIED, permanentlyDenied)
                .apply();

        deny(activity.getString(R.string.dating_location_denied));
        return true;
    }

    /** 只有后端上传成功后才调用，网络失败不能提前进入 72 小时冷却。 */
    public void markUploaded(Location location) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = locationPrefs.edit()
                .putLong(KEY_UPLOAD_TIME, now)
                .putBoolean(KEY_PENDING_UPLOAD, false);
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
        if (hasPermission()) {
            clearPermissionFailureState();
            locateNow();
        } else {
            deny(activity.getString(R.string.dating_location_denied));
        }
    }

    public void release() {
        waitingForSettings = false;
        stopListening();
        pendingCallback = null;
    }

    private boolean needsRefresh() {
        long now = System.currentTimeMillis();
        long uploadedAt = locationPrefs.getLong(KEY_UPLOAD_TIME, 0L);
        long elapsed = now - uploadedAt;
        Location cached = cachedLocation();
        return uploadedAt <= 0L || elapsed < 0L
                || elapsed >= LOCATION_REFRESH_INTERVAL_MS || cached == null;
    }

    private void clearPermissionFailureState() {
        permissionPrefs.edit()
                .putInt(KEY_DENIAL_COUNT, 0)
                .putBoolean(KEY_PERMISSION_PERMANENTLY_DENIED, false)
                .apply();
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
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void locateNow() {
        if (!hasPermission() || locationManager == null) {
            fallbackOrDeny(activity.getString(R.string.dating_location_device_unavailable));
            return;
        }

        Location cached = cachedLocation();
        Location systemCached = bestLastKnown();
        long systemAge = systemCached == null ? Long.MAX_VALUE
                : System.currentTimeMillis() - systemCached.getTime();
        if (systemCached != null && systemAge >= 0L && systemAge <= 15L * 60L * 1000L) {
            saveLocalLocation(systemCached, true);
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
                saveLocalLocation(location, true);
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
        long age = now - savedAt;
        if (savedAt <= 0L || age < 0L || age > LOCATION_CACHE_TTL_MS) return null;
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

    private void saveLocalLocation(Location location, boolean pendingUpload) {
        if (location == null) return;
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = locationPrefs.edit()
                .putBoolean(KEY_PENDING_UPLOAD, pendingUpload);
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
        long age = System.currentTimeMillis() - time;
        if (time <= 0L || age < 0L || age > LOCATION_CACHE_TTL_MS) return null;
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
