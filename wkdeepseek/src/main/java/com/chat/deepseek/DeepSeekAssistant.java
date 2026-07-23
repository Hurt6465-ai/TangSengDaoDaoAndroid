package com.chat.deepseek;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

public final class DeepSeekAssistant {
    public interface ReplyCallback {
        void onReply(@NonNull String text, boolean sendNow);
    }

    public interface StateCallback {
        void onChanged();
    }

    private static final String PREF = "wk_deepseek_settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CONNECTED_ONCE = "connected_once";
    private static final String TAG = "DeepSeekAssistantDialog";

    private DeepSeekAssistant() {}

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static void markConnected(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CONNECTED_ONCE, true)
                .putBoolean(KEY_ENABLED, true)
                .apply();
    }

    private static boolean hasConnectedOnce(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_CONNECTED_ONCE, false);
    }

    public static void requestFirstEnable(FragmentActivity activity, StateCallback callback) {
        if (hasConnectedOnce(activity)) {
            setEnabled(activity, true);
            if (callback != null) callback.onChanged();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(com.chat.deepseek.R.string.wkdeepseek_first_title)
                .setMessage(com.chat.deepseek.R.string.wkdeepseek_first_message)
                .setNegativeButton(com.chat.deepseek.R.string.wkdeepseek_cancel, null)
                .setPositiveButton(com.chat.deepseek.R.string.wkdeepseek_login_register, (dialog, which) ->
                        openLogin(activity, callback))
                .show();
    }

    public static void openLogin(FragmentActivity activity, StateCallback callback) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        DeepSeekAssistantDialog dialog = DeepSeekAssistantDialog.newLogin();
        dialog.setStateCallback(callback);
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    public static void openAction(FragmentActivity activity, DeepSeekRequest request, ReplyCallback callback) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        DeepSeekContactStore.apply(activity, request);
        DeepSeekAssistantDialog dialog = DeepSeekAssistantDialog.newAction(request);
        dialog.setReplyCallback(callback);
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    public static void showSettings(FragmentActivity activity, DeepSeekRequest request, StateCallback callback) {
        DeepSeekContactStore.apply(activity, request);
        int pad = dp(activity, 20);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(activity, 6), pad, dp(activity, 4));

        EditText myNative = addField(activity, root, activity.getString(R.string.wkdeepseek_my_native), request.myNativeLanguage, true);
        EditText peerNative = addField(activity, root, activity.getString(R.string.wkdeepseek_peer_native), request.peerNativeLanguage, true);
        EditText purpose = addField(activity, root, activity.getString(R.string.wkdeepseek_purpose), request.purpose, true);
        EditText background = addField(activity, root, activity.getString(R.string.wkdeepseek_background), request.background, false);
        background.setMinLines(3);
        background.setGravity(Gravity.TOP | Gravity.START);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.wkdeepseek_settings_title)
                .setView(root)
                .setNegativeButton(R.string.wkdeepseek_cancel, null)
                .setPositiveButton(R.string.wkdeepseek_save, (dialog, which) -> {
                    request.myNativeLanguage = text(myNative);
                    request.peerNativeLanguage = text(peerNative);
                    request.purpose = text(purpose);
                    request.background = text(background);
                    DeepSeekContactStore.save(activity, request);
                    if (callback != null) callback.onChanged();
                })
                .show();
    }

    private static EditText addField(Context context, LinearLayout root, String label, String value, boolean singleLine) {
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextColor(Color.rgb(95, 101, 112));
        title.setTextSize(13);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(context, 10);
        root.addView(title, titleLp);

        EditText edit = new EditText(context);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(15);
        edit.setSingleLine(singleLine);
        edit.setInputType(singleLine ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }

    private static String text(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
