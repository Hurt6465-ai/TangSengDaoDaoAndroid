package com.chat.partnerlist;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.base.net.IRequestResultListener;
import com.chat.partnerlist.model.PartnerHeartbeatResponse;

import java.util.concurrent.ThreadLocalRandom;

/**
 * App 级前台活跃心跳，与具体页面生命周期解耦。
 * 服务端返回周期时优先服从服务端；没有返回时才使用约 4.5~5.5 分钟的兜底周期。
 * 所有状态只在主线程读写；同一账号快速前后台切换不会产生重叠请求。
 */
public final class PartnerListForegroundHeartbeat {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long DEFAULT_MIN_MS = 270_000L;
    private static final long DEFAULT_MAX_MS = 330_000L;
    private static final long LOGIN_RETRY_MIN_MS = 30_000L;
    private static final long LOGIN_RETRY_MAX_MS = 45_000L;
    private static final long FAILURE_RETRY_MIN_MS = 60_000L;
    private static final long FAILURE_RETRY_MAX_MS = 90_000L;

    private static boolean foreground;
    private static boolean requestInFlight;
    private static long nextRunAtElapsed;
    private static int requestGeneration;
    private static String inFlightUid = "";
    private static String inFlightToken = "";
    private static String lastKnownUid = "";
    private static String lastKnownToken = "";

    private PartnerListForegroundHeartbeat() {}

    private static final Runnable TASK = new Runnable() {
        @Override public void run() {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                MAIN.post(this);
                return;
            }
            if (!foreground) return;

            String uid = safeUid();
            String token = safeToken();
            updateAccount(uid, token);
            if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(token)) {
                scheduleRange(LOGIN_RETRY_MIN_MS, LOGIN_RETRY_MAX_MS);
                return;
            }

            // PartnerListModel 没有暴露可取消的 Disposable，而全局 OkHttp 超时较长。
            // 绝不能用“逻辑看门狗”把锁提前释放，否则旧 HTTP 请求仍在执行，
            // 弱网下会不断叠加新的心跳。只有真实回调或账号切换才能释放该锁。
            if (requestInFlight) return;

            requestInFlight = true;
            inFlightUid = uid;
            inFlightToken = token;
            final int generation = ++requestGeneration;
            try {
                PartnerListModel.getInstance().heartbeat(new IRequestResultListener<>() {
                    @Override public void onSuccess(PartnerHeartbeatResponse result) {
                        completeRequestOnMain(generation, uid, token, true, result);
                    }

                    @Override public void onFail(int code, String msg) {
                        completeRequestOnMain(generation, uid, token, false, null);
                    }
                });
            } catch (Throwable ignored) {
                completeRequestOnMain(generation, uid, token, false, null);
            }
        }
    };

    public static void onAppForeground() {
        runOnMain(() -> {
            foreground = true;
            MAIN.removeCallbacks(TASK);

            String uid = safeUid();
            String token = safeToken();
            updateAccount(uid, token);
            // 已有真实网络请求时等待它的回调，不再另设会制造重复请求的逻辑超时。
            if (requestInFlight) return;

            long delay = Math.max(0L, nextRunAtElapsed - SystemClock.elapsedRealtime());
            MAIN.postDelayed(TASK, delay);
        });
    }

    public static void onAppBackground() {
        runOnMain(() -> {
            foreground = false;
            // Retrofit 请求不会因为进入后台自动取消，不能在这里把 requestInFlight 清零。
            MAIN.removeCallbacks(TASK);
        });
    }

    private static void completeRequestOnMain(int generation, String requestUid,
                                              String requestToken, boolean success,
                                              PartnerHeartbeatResponse result) {
        runOnMain(() -> {
            if (generation != requestGeneration
                    || !requestInFlight
                    || !TextUtils.equals(requestUid, inFlightUid)
                    || !TextUtils.equals(requestToken, inFlightToken)) {
                return;
            }
            requestInFlight = false;
            inFlightUid = "";
            inFlightToken = "";

            String currentUid = safeUid();
            String currentToken = safeToken();
            if (TextUtils.isEmpty(currentUid) || TextUtils.isEmpty(currentToken)
                    || !TextUtils.equals(currentUid, requestUid)
                    || !TextUtils.equals(currentToken, requestToken)) {
                nextRunAtElapsed = 0L;
                if (foreground) MAIN.post(TASK);
                return;
            }

            if (success) {
                scheduleFromServer(result == null ? 0 : result.next_heartbeat_in_seconds);
            } else {
                scheduleRange(FAILURE_RETRY_MIN_MS, FAILURE_RETRY_MAX_MS);
            }
        });
    }

    private static void scheduleFromServer(int seconds) {
        if (seconds > 0) {
            long center = Math.max(45L, Math.min(330L, seconds)) * 1000L;
            long jitter = Math.min(15_000L, Math.max(3_000L, center / 10L));
            scheduleRange(Math.max(30_000L, center - jitter), center + jitter);
        } else {
            scheduleRange(DEFAULT_MIN_MS, DEFAULT_MAX_MS);
        }
    }

    private static void scheduleRange(long minMs, long maxMs) {
        long safeMin = Math.max(1_000L, minMs);
        long safeMax = Math.max(safeMin, maxMs);
        long delay;
        if (safeMax == safeMin) {
            delay = safeMin;
        } else {
            long upperExclusive = safeMax == Long.MAX_VALUE ? Long.MAX_VALUE : safeMax + 1L;
            delay = ThreadLocalRandom.current().nextLong(safeMin, upperExclusive);
        }
        nextRunAtElapsed = SystemClock.elapsedRealtime() + delay;
        MAIN.removeCallbacks(TASK);
        if (foreground) MAIN.postDelayed(TASK, delay);
    }

    private static void updateAccount(String currentUid, String currentToken) {
        String safeUid = currentUid == null ? "" : currentUid;
        String safeToken = currentToken == null ? "" : currentToken;
        boolean uidChanged = !TextUtils.equals(safeUid, lastKnownUid);
        boolean tokenChanged = !TextUtils.equals(safeToken, lastKnownToken);
        if (!uidChanged && !tokenChanged) return;

        lastKnownUid = safeUid;
        lastKnownToken = safeToken;
        nextRunAtElapsed = 0L;
        MAIN.removeCallbacks(TASK);

        if (!uidChanged || !requestInFlight || TextUtils.equals(safeUid, inFlightUid)) {
            // Same-account token refresh waits for the real callback; this avoids overlapping
            // two heartbeats merely because the auth token rotated.
            return;
        }
        requestGeneration++;
        requestInFlight = false;
        inFlightUid = "";
        inFlightToken = "";
    }

    private static String safeUid() {
        String uid = WKConfig.getInstance().getUid();
        return uid == null ? "" : uid;
    }

    private static String safeToken() {
        String token = WKConfig.getInstance().getToken();
        return token == null ? "" : token;
    }

    private static void runOnMain(Runnable action) {
        if (action == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else MAIN.post(action);
    }
}
