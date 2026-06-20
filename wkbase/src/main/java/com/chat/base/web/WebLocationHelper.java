package com.chat.base.web;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.chat.base.R;
import com.chat.base.utils.WKPermissions;

import org.json.JSONObject;

import java.util.List;

/**
 * Native location helper for NodeBB WebView pages.
 *
 * The page can either use the injected navigator.geolocation polyfill or call
 * window.TangSengLocation.requestLocation(callbackId) directly.
 */
public class WebLocationHelper {
    private static final long LAST_LOCATION_MAX_AGE_MS = 5 * 60 * 1000L;
    private static final long REQUEST_TIMEOUT_MS = 10 * 1000L;

    private final FragmentActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private Location lastLocation;
    private boolean destroyed;

    public WebLocationHelper(FragmentActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void setWebView(WebView webView) {
        runOnMain(() -> this.webView = webView);
    }

    public void requestLocation(String callbackId) {
        runOnMain(() -> requestLocationOnMain(callbackId));
    }

    public String getLastLocationJson() {
        Location location = lastLocation;
        if (location == null) {
            try {
                location = getBestLastKnownLocation();
            } catch (Exception ignored) {
            }
        }
        return location == null ? "" : toJson(location).toString();
    }

    public void injectLocationPolyfill() {
        runOnMain(() -> {
            if (webView == null || destroyed) return;
            String js = "(function(){" +
                    "if(window.__TangSengLocationPolyfillInstalled)return;" +
                    "window.__TangSengLocationPolyfillInstalled=true;" +
                    "var callbacks={};var seq=1;" +
                    "function fire(name,detail){try{window.dispatchEvent(new CustomEvent(name,{detail:detail}));}catch(e){try{var ev=document.createEvent('CustomEvent');ev.initCustomEvent(name,false,false,detail);window.dispatchEvent(ev);}catch(_){}}}" +
                    "function parsePayload(payload){if(!payload)return null;if(typeof payload==='string'){try{return JSON.parse(payload);}catch(e){return null;}}return payload;}" +
                    "window.__TangSengLocationNativeResult=function(id,payload){var data=parsePayload(payload);var cb=callbacks[id];delete callbacks[id];if(data)fire('TangSengLocationResult',data);if(cb&&data){cb.ok({coords:{latitude:Number(data.lat),longitude:Number(data.lng),accuracy:Number(data.accuracy||0),altitude:null,altitudeAccuracy:null,heading:null,speed:null},timestamp:Number(data.time||Date.now())});}};" +
                    "window.__TangSengLocationNativeError=function(id,message){var cb=callbacks[id];delete callbacks[id];fire('TangSengLocationError',{message:message||'定位失败'});if(cb&&cb.fail)cb.fail({code:1,message:message||'定位失败'});};" +
                    "function requestNative(success,error,options){var id='tsloc_'+Date.now()+'_'+(seq++);callbacks[id]={ok:typeof success==='function'?success:function(){},fail:typeof error==='function'?error:function(){}};var timeout=options&&Number(options.timeout||0)||12000;if(timeout>0){setTimeout(function(){if(callbacks[id]){var cb=callbacks[id];delete callbacks[id];if(cb.fail)cb.fail({code:3,message:'定位超时'});}},timeout+1000);}try{if(window.TangSengLocation&&window.TangSengLocation.requestLocation){window.TangSengLocation.requestLocation(id);}else{throw new Error('TangSengLocation bridge not found');}}catch(e){var cb=callbacks[id];delete callbacks[id];if(cb&&cb.fail)cb.fail({code:2,message:e&&e.message||'定位不可用'});}}" +
                    "window.__TangSengRequestLocation=function(options){return new Promise(function(resolve,reject){requestNative(resolve,reject,options||{});});};" +
                    "try{var nativeGeo={getCurrentPosition:function(success,error,options){requestNative(success,error,options||{});},watchPosition:function(success,error,options){requestNative(success,error,options||{});return seq;},clearWatch:function(){}};Object.defineProperty(nativeGeo,'__tangsengNative',{value:true});Object.defineProperty(navigator,'geolocation',{configurable:true,value:nativeGeo});}catch(e){}" +
                    "})();";
            runJavascript(js);
        });
    }

    public void destroy() {
        runOnMain(() -> {
            destroyed = true;
            webView = null;
        });
    }

    private void requestLocationOnMain(String callbackId) {
        if (destroyed || activity == null || activity.isFinishing()) {
            notifyLocationError(callbackId, "页面已经关闭");
            return;
        }

        if (hasLocationPermission()) {
            locateOnMain(callbackId);
            return;
        }

        String desc = String.format(activity.getString(R.string.location_permissions_desc), activity.getString(R.string.app_name));
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {
                runOnMain(() -> {
                    if (result) {
                        locateOnMain(callbackId);
                    } else {
                        notifyLocationError(callbackId, "缺少定位权限");
                    }
                });
            }

            @Override
            public void clickResult(boolean isCancel) {
                if (isCancel) notifyLocationError(callbackId, "缺少定位权限");
            }
        }, activity, desc, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @SuppressLint("MissingPermission")
    private void locateOnMain(String callbackId) {
        Location cached = getBestLastKnownLocation();
        if (isFresh(cached)) {
            lastLocation = cached;
            notifyLocationResult(callbackId, cached);
            return;
        }

        LocationManager manager = getLocationManager();
        if (manager == null) {
            notifyLocationError(callbackId, "定位服务不可用");
            return;
        }

        String provider = chooseProvider(manager);
        if (TextUtils.isEmpty(provider)) {
            if (cached != null) {
                lastLocation = cached;
                notifyLocationResult(callbackId, cached);
            } else {
                notifyLocationError(callbackId, "请先打开系统定位服务");
            }
            return;
        }

        final boolean[] finished = new boolean[]{false};
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (finished[0]) return;
                finished[0] = true;
                try {
                    manager.removeUpdates(this);
                } catch (Exception ignored) {
                }
                lastLocation = location;
                notifyLocationResult(callbackId, location);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        mainHandler.postDelayed(() -> {
            if (finished[0]) return;
            finished[0] = true;
            try {
                manager.removeUpdates(listener);
            } catch (Exception ignored) {
            }
            Location fallback = getBestLastKnownLocation();
            if (fallback != null) {
                lastLocation = fallback;
                notifyLocationResult(callbackId, fallback);
            } else {
                notifyLocationError(callbackId, "定位超时");
            }
        }, REQUEST_TIMEOUT_MS);

        try {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
        } catch (Exception e) {
            Location fallback = getBestLastKnownLocation();
            if (fallback != null) {
                lastLocation = fallback;
                notifyLocationResult(callbackId, fallback);
            } else {
                notifyLocationError(callbackId, "启动定位失败");
            }
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private LocationManager getLocationManager() {
        if (activity == null) return null;
        return (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    private Location getBestLastKnownLocation() {
        if (!hasLocationPermission()) return null;
        LocationManager manager = getLocationManager();
        if (manager == null) return null;
        Location best = null;
        try {
            List<String> providers = manager.getProviders(true);
            if (providers == null) return null;
            for (String provider : providers) {
                Location location = manager.getLastKnownLocation(provider);
                if (location == null) continue;
                if (best == null || location.getTime() > best.getTime()) best = location;
            }
        } catch (Exception ignored) {
        }
        return best;
    }

    private String chooseProvider(LocationManager manager) {
        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER;
        } catch (Exception ignored) {
        }
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER;
        } catch (Exception ignored) {
        }
        return "";
    }

    private boolean isFresh(Location location) {
        return location != null && Math.abs(System.currentTimeMillis() - location.getTime()) <= LAST_LOCATION_MAX_AGE_MS;
    }

    private JSONObject toJson(Location location) {
        JSONObject json = new JSONObject();
        try {
            json.put("lat", location.getLatitude());
            json.put("lng", location.getLongitude());
            json.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : 0);
            json.put("provider", location.getProvider());
            json.put("time", location.getTime() > 0 ? location.getTime() : System.currentTimeMillis());
            json.put("source", "tangseng-native");
        } catch (Exception ignored) {
        }
        return json;
    }

    private void notifyLocationResult(String callbackId, Location location) {
        if (location == null) {
            notifyLocationError(callbackId, "定位失败");
            return;
        }
        String js = "window.__TangSengLocationNativeResult&&window.__TangSengLocationNativeResult(" +
                JSONObject.quote(callbackId == null ? "" : callbackId) + "," + JSONObject.quote(toJson(location).toString()) + ");";
        runJavascript(js);
    }

    private void notifyLocationError(String callbackId, String message) {
        String js = "window.__TangSengLocationNativeError&&window.__TangSengLocationNativeError(" +
                JSONObject.quote(callbackId == null ? "" : callbackId) + "," + JSONObject.quote(message == null ? "定位失败" : message) + ");";
        runJavascript(js);
        if (!TextUtils.isEmpty(message)) {
            try {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
        }
    }

    private void runJavascript(String js) {
        if (webView == null || TextUtils.isEmpty(js)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(js, null);
        } else {
            webView.loadUrl("javascript:" + js);
        }
    }

    private void runOnMain(Runnable runnable) {
        if (runnable == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
