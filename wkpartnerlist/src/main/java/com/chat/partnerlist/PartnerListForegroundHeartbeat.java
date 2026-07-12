package com.chat.partnerlist;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.base.net.IRequestResultListener;
import com.chat.partnerlist.model.PartnerHeartbeatResponse;

import java.util.concurrent.ThreadLocalRandom;

/**
 * App 级前台活跃心跳，与具体页面生命周期解耦。
 * 默认按“最近 10 分钟活跃”设计，约 4.5~5.5 分钟写一次；如果服务端下发更短周期，优先遵循服务端。
 */
public final class PartnerListForegroundHeartbeat {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long DEFAULT_MIN_MS = 270_000L;
    private static final long DEFAULT_MAX_MS = 330_000L;
    private static boolean foreground;
    private static boolean requestInFlight;

    private PartnerListForegroundHeartbeat() {}

    private static final Runnable TASK = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            if (TextUtils.isEmpty(WKConfig.getInstance().getToken())) {
                // App 已在前台但用户尚未登录；稍后轻量重试，避免登录后必须切后台才开始心跳。
                scheduleRange(30_000L, 45_000L);
                return;
            }
            if (requestInFlight) {
                scheduleDefault();
                return;
            }
            requestInFlight = true;
            PartnerListModel.getInstance().heartbeat(new IRequestResultListener<>() {
                @Override public void onSuccess(PartnerHeartbeatResponse result) {
                    requestInFlight = false;
                    scheduleFromServer(result == null ? 0 : result.next_heartbeat_in_seconds);
                }

                @Override public void onFail(int code, String msg) {
                    requestInFlight = false;
                    // 失败后较快重试，但仍加入抖动，避免弱网用户形成同步洪峰。
                    scheduleRange(60_000L, 90_000L);
                }
            });
        }
    };

    public static synchronized void onAppForeground() {
        foreground = true;
        MAIN.removeCallbacks(TASK);
        MAIN.post(TASK);
    }

    public static synchronized void onAppBackground() {
        foreground = false;
        requestInFlight = false;
        MAIN.removeCallbacks(TASK);
    }

    private static void scheduleFromServer(int seconds) {
        if (!foreground) return;
        if (seconds > 0) {
            long center = Math.max(45L, Math.min(330L, seconds)) * 1000L;
            long jitter = Math.min(15_000L, Math.max(3_000L, center / 10L));
            scheduleRange(Math.max(30_000L, center - jitter), center + jitter);
        } else {
            scheduleDefault();
        }
    }

    private static void scheduleDefault() {
        scheduleRange(DEFAULT_MIN_MS, DEFAULT_MAX_MS);
    }

    private static void scheduleRange(long minMs, long maxMs) {
        if (!foreground) return;
        long upper = Math.max(minMs + 1L, maxMs + 1L);
        long delay = ThreadLocalRandom.current().nextLong(minMs, upper);
        MAIN.removeCallbacks(TASK);
        MAIN.postDelayed(TASK, delay);
    }
}
