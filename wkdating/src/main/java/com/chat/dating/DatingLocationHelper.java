package com.chat.dating;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 交友附近定位：
 * - 第一次选择附近时先弹业务说明，再请求系统权限；
 * - 一旦授权成功，后续直接静默获取位置，不再重复询问；
 * - 没授权或被拒绝时，每次进入附近都会再次询问。
 */
public final class DatingLocationHelper {
    public static final int REQUEST_LOCATION = 9041;
    private static final String SP = "wkdating_location";
    private static final String KEY_GRANTED_ONCE = "granted_once";

    public interface Callback {
        void onSuccess(Location location);
        void onDenied(String message);
    }

    private final Activity activity;
    private final LocationManager locationManager;
    private final SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Callback pendingCallback;
    private LocationListener listener;
    private Runnable timeoutTask;

    public DatingLocationHelper(Activity activity) {
        this.activity = activity;
        this.locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        this.sp = activity.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public void ensureLocation(Callback callback) {
        pendingCallback = callback;
        if (hasPermission()) {
            sp.edit().putBoolean(KEY_GRANTED_ONCE, true).apply();
            locateNow();
            return;
        }
        showPermissionExplain();
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
            sp.edit().putBoolean(KEY_GRANTED_ONCE, true).apply();
            locateNow();
        } else {
            sp.edit().putBoolean(KEY_GRANTED_ONCE, false).apply();
            deny("未开启定位，无法查看附近的人");
        }
        return true;
    }

    public void release() {
        stopListening();
        pendingCallback = null;
    }

    private void showPermissionExplain() {
        String message = sp.getBoolean(KEY_GRANTED_ONCE, false)
                ? "为了继续查看附近的人，请重新开启定位权限。"
                : "开启定位后才能查看附近的人。授权成功后，后续默认静默定位，不会重复询问。";
        new AlertDialog.Builder(activity)
                .setTitle("开启定位")
                .setMessage(message)
                .setNegativeButton("暂不", (dialog, which) -> deny("未开启定位，已切回推荐"))
                .setPositiveButton("去开启", (dialog, which) -> ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_LOCATION))
                .show();
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void locateNow() {
        if (!hasPermission() || locationManager == null) {
            deny("当前设备无法获取定位");
            return;
        }

        Location cached = bestLastKnown();
        if (cached != null && System.currentTimeMillis() - cached.getTime() <= 15L * 60L * 1000L) {
            success(cached);
            return;
        }

        List<String> providers = new ArrayList<>();
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER);
        } catch (Throwable ignored) {
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER);
        } catch (Throwable ignored) {
        }

        if (providers.isEmpty()) {
            if (cached != null) success(cached);
            else deny("请先打开系统定位服务");
            return;
        }

        stopListening();
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                stopListening();
                success(location);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        try {
            for (String provider : providers) {
                locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper());
            }
        } catch (SecurityException error) {
            deny("定位权限不可用");
            return;
        } catch (Throwable error) {
            if (cached != null) success(cached);
            else deny("定位失败，请稍后重试");
            return;
        }

        Location fallback = cached;
        timeoutTask = () -> {
            stopListening();
            if (fallback != null) success(fallback);
            else deny("暂时无法获取位置，请稍后重试");
        };
        handler.postDelayed(timeoutTask, 8000L);
    }

    private Location bestLastKnown() {
        if (!hasPermission() || locationManager == null) return null;
        Location best = null;
        try {
            List<String> providers = locationManager.getProviders(true);
            for (String provider : providers) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) continue;
                if (best == null || location.getTime() > best.getTime()
                        || (location.getTime() == best.getTime() && location.getAccuracy() < best.getAccuracy())) {
                    best = location;
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    private void success(Location location) {
        Callback callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) callback.onSuccess(location);
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
