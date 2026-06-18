package com.chat.uikit.debug;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * 手机端调试日志窗口。
 *
 * 用途：没有电脑 adb/logcat 时，在 App 内直接查看连接状态、前后台切换、主动连接/断开等关键日志。
 * 打开方式：在会话列表首页，长按顶部标题/连接状态文字。
 *
 * 注意：日志只保存在当前 App 进程内，杀进程后会清空。不要把 token、密码等敏感信息写进这里。
 */
public final class DebugLogManager {
    private static final int MAX_LINES = 800;
    private static final LinkedList<String> LOGS = new LinkedList<>();
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private DebugLogManager() {
    }

    public static void log(String tag, String message) {
        String safeTag = tag == null ? "DebugLog" : tag;
        String safeMessage = message == null ? "" : message;
        Log.e(safeTag, safeMessage);
        synchronized (LOGS) {
            String line = TIME_FORMAT.format(new Date()) + "  " + safeTag + "  " + safeMessage;
            LOGS.add(line);
            while (LOGS.size() > MAX_LINES) {
                LOGS.removeFirst();
            }
        }
    }

    public static String getLogs() {
        synchronized (LOGS) {
            if (LOGS.isEmpty()) {
                return "暂无日志。\n\n操作方法：返回桌面再回到 App，或切后台等几秒，再打开这里查看。";
            }
            StringBuilder builder = new StringBuilder();
            for (String line : LOGS) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    public static void clear() {
        synchronized (LOGS) {
            LOGS.clear();
        }
    }

    public static void showLogWindow(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(() -> showDialog(activity));
    }

    private static void showDialog(Activity activity) {
        final Dialog dialog = new Dialog(activity);

        int padding = dp(activity, 12);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(activity);
        title.setText("调试日志");
        title.setTextColor(Color.BLACK);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, 0, dp(activity, 8));
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView content = new TextView(activity);
        content.setTextColor(Color.BLACK);
        content.setTextSize(12);
        content.setTextIsSelectable(true);
        content.setText(getLogs());

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(activity, 8), 0, 0);

        Button refreshBtn = new Button(activity);
        refreshBtn.setText("刷新");
        refreshBtn.setOnClickListener(v -> content.setText(getLogs()));
        buttons.addView(refreshBtn);

        Button copyBtn = new Button(activity);
        copyBtn.setText("复制");
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("TangSengDaoDao Debug Logs", getLogs()));
                Toast.makeText(activity, "日志已复制", Toast.LENGTH_SHORT).show();
            }
        });
        buttons.addView(copyBtn);

        Button clearBtn = new Button(activity);
        clearBtn.setText("清空");
        clearBtn.setOnClickListener(v -> {
            clear();
            content.setText(getLogs());
        });
        buttons.addView(clearBtn);

        Button closeBtn = new Button(activity);
        closeBtn.setText("关闭");
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(closeBtn);

        root.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        dialog.setContentView(root);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                params.copyFrom(window.getAttributes());
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.78f);
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
        dialog.show();
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
