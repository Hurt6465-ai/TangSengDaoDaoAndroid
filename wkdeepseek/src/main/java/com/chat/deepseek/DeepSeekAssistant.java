package com.chat.deepseek;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.xinbida.wukongim.entity.WKMsg;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public final class DeepSeekAssistant {
    public interface ReplyCallback {
        /**
         * @param text reply in the peer-facing language; this is the only text sent remotely
         * @param localDisplayText back-translation for the sender's local bubble only
         * @param sendNow true to send immediately, false to place the reply in the input box
         */
        void onReply(@NonNull String text, @NonNull String localDisplayText, boolean sendNow);
    }

    public interface TranslationCallback {
        void onTranslation(@NonNull String text);
    }

    public interface StateCallback {
        void onChanged();
    }

    private static final String PREF = "wk_deepseek_settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CONNECTED_ONCE = "connected_once";
    private static final String LOCAL_REPLY_PREF = "wk_deepseek_local_reply_meta";
    private static final String KEY_PENDING_CHANNEL_ID = "pending_channel_id";
    private static final String KEY_PENDING_CHANNEL_TYPE = "pending_channel_type";
    private static final String KEY_PENDING_TEXT = "pending_text";
    private static final String KEY_PENDING_BACK_TRANSLATION = "pending_back_translation";
    private static final String KEY_PENDING_AT = "pending_at";
    private static final String KEY_LOCAL_REPLY_RECORDS = "reply_records";
    public static final String LOCAL_BACK_TRANSLATION_KEY = "deepseek_back_translation";
    private static final long PENDING_REPLY_TTL_MS = 5L * 60L * 1000L;
    private static final long LOCAL_REPLY_RETENTION_MS = 90L * 24L * 60L * 60L * 1000L;
    private static final int MAX_LOCAL_REPLY_RECORDS = 500;
    private static final Object LOCAL_REPLY_LOCK = new Object();
    static final String TAG = "DeepSeekAssistantDialog";

    private static final String[] RELATIONSHIP_LABELS = {
            "自动判断", "刚认识", "语伴", "普通朋友", "相亲或约会", "暧昧中", "恋人", "同事或正式关系"
    };
    private static final String[] RELATIONSHIP_VALUES = {
            "auto", "new_contact", "language_partner", "friend", "dating", "ambiguous", "relationship", "formal"
    };
    private static final String[] STYLE_LABELS = {"自然", "简短", "温暖", "轻松", "幽默", "直接", "正式"};
    private static final String[] STYLE_VALUES = {"natural", "short", "warm", "light", "humorous", "direct", "formal"};
    private static final String[] FLIRT_LABELS = {"关闭", "轻微", "明显"};

    private DeepSeekAssistant() {}

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * Stores one pending AI reply immediately before the normal chat send pipeline runs. Only the
     * peer-facing text is serialized into WKTextContent; the back-translation stays in Talkami.
     */
    public static void rememberReplyForNextSend(Context context, String channelId, byte channelType,
                                                String text, String backTranslation) {
        if (context == null || TextUtils.isEmpty(text) || TextUtils.isEmpty(backTranslation)) return;
        String remote = normalizeReplyText(text);
        String local = backTranslation.trim();
        if (TextUtils.isEmpty(remote) || TextUtils.equals(remote, local)) return;
        synchronized (LOCAL_REPLY_LOCK) {
            localReplyPreferences(context).edit()
                    .putString(KEY_PENDING_CHANNEL_ID, channelId == null ? "" : channelId)
                    .putInt(KEY_PENDING_CHANNEL_TYPE, channelType)
                    .putString(KEY_PENDING_TEXT, remote)
                    .putString(KEY_PENDING_BACK_TRANSLATION, local)
                    .putLong(KEY_PENDING_AT, System.currentTimeMillis())
                    .commit();
            DeepSeekHistoryLog.log("LOCAL_REPLY_PENDING_SET",
                    "channel=" + shortLogId(channelId) + "/" + channelType
                            + " has_back=true");
        }
    }

    /**
     * Binds the pending local back-translation to the actual outgoing message's clientMsgNO. This
     * is called before the first sender bubble is built, so later send acknowledgements and list
     * rebinds can recover the local-only text without changing the remote payload.
     */
    public static void bindPendingReplyToMessage(Context context, WKMsg msg) {
        if (context == null || msg == null || TextUtils.isEmpty(msg.clientMsgNO)) return;
        synchronized (LOCAL_REPLY_LOCK) {
            SharedPreferences preferences = localReplyPreferences(context);
            long now = System.currentTimeMillis();
            long pendingAt = preferences.getLong(KEY_PENDING_AT, 0L);
            if (pendingAt <= 0L || now - pendingAt > PENDING_REPLY_TTL_MS) {
                clearPendingReply(preferences);
                return;
            }
            String pendingChannelId = preferences.getString(KEY_PENDING_CHANNEL_ID, "");
            int pendingChannelType = preferences.getInt(KEY_PENDING_CHANNEL_TYPE, -1);
            String pendingText = preferences.getString(KEY_PENDING_TEXT, "");
            String pendingBackTranslation = preferences.getString(KEY_PENDING_BACK_TRANSLATION, "");
            String messageText = readMessageText(msg);
            boolean matches = TextUtils.equals(pendingChannelId, msg.channelID == null ? "" : msg.channelID)
                    && pendingChannelType == msg.channelType
                    && TextUtils.equals(pendingText, normalizeReplyText(messageText))
                    && !TextUtils.isEmpty(pendingBackTranslation);
            if (!matches) return;

            try {
                JSONObject records = readRecords(preferences);
                cleanupRecords(records, now);
                JSONObject record = new JSONObject();
                record.put("client_msg_no", msg.clientMsgNO);
                record.put("message_id", msg.messageID == null ? "" : msg.messageID);
                record.put("channel_id", msg.channelID == null ? "" : msg.channelID);
                record.put("channel_type", msg.channelType);
                record.put("text", pendingText);
                record.put("back_translation", pendingBackTranslation);
                record.put("created_at", now);
                records.put(msg.clientMsgNO, record);
                trimRecords(records);
                preferences.edit().putString(KEY_LOCAL_REPLY_RECORDS, records.toString()).commit();

                if (msg.localExtraMap == null) msg.localExtraMap = new HashMap<>();
                msg.localExtraMap.put(LOCAL_BACK_TRANSLATION_KEY, pendingBackTranslation);
                DeepSeekHistoryLog.log("LOCAL_REPLY_BOUND",
                        "client=" + shortLogId(msg.clientMsgNO)
                                + " message=" + shortLogId(msg.messageID)
                                + " channel=" + shortLogId(msg.channelID) + "/" + msg.channelType);
            } catch (Exception ignored) {
                // The chat message still sends normally; local display metadata is best effort.
            } finally {
                clearPendingReply(preferences);
            }
        }
    }

    /** Returns the sender-only back-translation for an outgoing message, or an empty string. */
    @NonNull
    public static String getLocalBackTranslation(Context context, WKMsg msg) {
        if (context == null || msg == null) return "";
        try {
            if (msg.localExtraMap != null) {
                Object value = msg.localExtraMap.get(LOCAL_BACK_TRANSLATION_KEY);
                if (value != null && !TextUtils.isEmpty(value.toString())) {
                    return value.toString().trim();
                }
            }
        } catch (Exception ignored) {
        }

        // Some SDK paths render the local bubble before ChatActivity receives the insertion event.
        // Binding here is safe because this method is only called for sender-side text bubbles.
        bindPendingReplyToMessage(context, msg);
        try {
            if (msg.localExtraMap != null) {
                Object value = msg.localExtraMap.get(LOCAL_BACK_TRANSLATION_KEY);
                if (value != null && !TextUtils.isEmpty(value.toString())) {
                    return value.toString().trim();
                }
            }
        } catch (Exception ignored) {
        }

        synchronized (LOCAL_REPLY_LOCK) {
            SharedPreferences preferences = localReplyPreferences(context);
            try {
                JSONObject records = readRecords(preferences);
                long now = System.currentTimeMillis();
                cleanupRecords(records, now);
                JSONObject record = findReplyRecord(records, msg);
                if (record == null) return "";

                String storedChannel = record.optString("channel_id", "");
                if (!TextUtils.equals(storedChannel, msg.channelID == null ? "" : msg.channelID)) {
                    return "";
                }
                if (record.optInt("channel_type", -1) != msg.channelType) return "";

                String storedText = record.optString("text", "");
                String currentText = normalizeReplyText(readMessageText(msg));
                if (!TextUtils.isEmpty(currentText) && !TextUtils.equals(storedText, currentText)) {
                    return "";
                }

                String value = record.optString("back_translation", "").trim();
                if (!TextUtils.isEmpty(value)) {
                    if (msg.localExtraMap == null) msg.localExtraMap = new HashMap<>();
                    msg.localExtraMap.put(LOCAL_BACK_TRANSLATION_KEY, value);
                    DeepSeekHistoryLog.log("LOCAL_REPLY_RESTORED",
                            "client=" + shortLogId(msg.clientMsgNO)
                                    + " message=" + shortLogId(msg.messageID));
                }
                preferences.edit().putString(KEY_LOCAL_REPLY_RECORDS, records.toString()).apply();
                return value;
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    private static JSONObject findReplyRecord(JSONObject records, WKMsg msg) {
        if (records == null || msg == null) return null;
        if (!TextUtils.isEmpty(msg.clientMsgNO)) {
            JSONObject direct = records.optJSONObject(msg.clientMsgNO);
            if (direct != null) return direct;
        }
        if (TextUtils.isEmpty(msg.messageID)) return null;
        Iterator<String> keys = records.keys();
        while (keys.hasNext()) {
            JSONObject item = records.optJSONObject(keys.next());
            if (item == null) continue;
            if (TextUtils.equals(item.optString("message_id", ""), msg.messageID)) {
                return item;
            }
        }
        return null;
    }

    private static SharedPreferences localReplyPreferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(LOCAL_REPLY_PREF, Context.MODE_PRIVATE);
    }

    private static JSONObject readRecords(SharedPreferences preferences) {
        String raw = preferences.getString(KEY_LOCAL_REPLY_RECORDS, "{}");
        try {
            return new JSONObject(TextUtils.isEmpty(raw) ? "{}" : raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String readMessageText(WKMsg msg) {
        if (msg == null) return "";
        try {
            if (!TextUtils.isEmpty(msg.content)) {
                JSONObject payload = new JSONObject(msg.content);
                String content = payload.optString("content", "");
                if (!TextUtils.isEmpty(content)) return content;
            }
        } catch (Exception ignored) {
        }
        try {
            if (msg.baseContentMsgModel != null) {
                String display = msg.baseContentMsgModel.getDisplayContent();
                if (!TextUtils.isEmpty(display)) return display;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String normalizeReplyText(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static void clearPendingReply(SharedPreferences preferences) {
        preferences.edit()
                .remove(KEY_PENDING_CHANNEL_ID)
                .remove(KEY_PENDING_CHANNEL_TYPE)
                .remove(KEY_PENDING_TEXT)
                .remove(KEY_PENDING_BACK_TRANSLATION)
                .remove(KEY_PENDING_AT)
                .apply();
    }

    private static void cleanupRecords(JSONObject records, long now) {
        List<String> expired = new ArrayList<>();
        Iterator<String> keys = records.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = records.optJSONObject(key);
            long createdAt = item == null ? 0L : item.optLong("created_at", 0L);
            if (createdAt <= 0L || now - createdAt > LOCAL_REPLY_RETENTION_MS) expired.add(key);
        }
        for (String key : expired) records.remove(key);
    }

    private static void trimRecords(JSONObject records) {
        if (records.length() <= MAX_LOCAL_REPLY_RECORDS) return;
        List<JSONObject> entries = new ArrayList<>();
        Iterator<String> keys = records.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = records.optJSONObject(key);
            if (item == null) continue;
            try {
                JSONObject entry = new JSONObject();
                entry.put("key", key);
                entry.put("created_at", item.optLong("created_at", 0L));
                entries.add(entry);
            } catch (Exception ignored) {
            }
        }
        entries.sort(Comparator.comparingLong(o -> o.optLong("created_at", 0L)));
        int removeCount = Math.max(0, entries.size() - MAX_LOCAL_REPLY_RECORDS);
        for (int i = 0; i < removeCount; i++) {
            records.remove(entries.get(i).optString("key", ""));
        }
    }

    private static String shortLogId(String value) {
        if (TextUtils.isEmpty(value)) return "empty";
        String clean = value.trim();
        if (clean.length() <= 10) return clean;
        return clean.substring(0, 4) + "..." + clean.substring(clean.length() - 4);
    }

    static void markConnected(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CONNECTED_ONCE, true)
                .putBoolean(KEY_ENABLED, true)
                .commit();
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
        openAction(activity, request, callback, null);
    }

    public static void openAction(FragmentActivity activity, DeepSeekRequest request,
                                  ReplyCallback callback, StateCallback closeCallback) {
        if (activity == null || request == null
                || activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        long remaining = DeepSeekUsageGuard.remainingMs(activity, request);
        if (remaining > 0L) {
            showCooldown(activity, remaining);
            return;
        }
        DeepSeekContactStore.apply(activity, request);
        DeepSeekAssistantDialog dialog = DeepSeekAssistantDialog.newAction(request);
        dialog.setReplyCallback(callback);
        dialog.setStateCallback(closeCallback);
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    public static boolean openTranslation(FragmentActivity activity, DeepSeekRequest request,
                                          TranslationCallback callback, StateCallback closeCallback) {
        if (activity == null || request == null
                || activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) {
            return false;
        }
        request.action = DeepSeekRequest.ACTION_TRANSLATE;
        long remaining = DeepSeekUsageGuard.remainingMs(activity, request);
        if (remaining > 0L) {
            showCooldown(activity, remaining);
            return false;
        }
        DeepSeekContactStore.apply(activity, request);
        DeepSeekAssistantDialog dialog = DeepSeekAssistantDialog.newAction(request);
        dialog.setTranslationCallback(callback);
        dialog.setStateCallback(closeCallback);
        dialog.show(activity.getSupportFragmentManager(), TAG);
        return true;
    }


    private static void showCooldown(Context context, long remainingMs) {
        long seconds = Math.max(1L, (remainingMs + 999L) / 1000L);
        Toast.makeText(context, "操作太快，请 " + seconds + " 秒后再试", Toast.LENGTH_SHORT).show();
    }

    public static void showSettings(FragmentActivity activity, DeepSeekRequest request, StateCallback callback) {
        DeepSeekContactStore.apply(activity, request);
        int pad = dp(activity, 20);
        ScrollView scrollView = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(activity, 4), pad, dp(activity, 16));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Spinner relationship = addSpinner(activity, root, activity.getString(R.string.wkdeepseek_relationship_stage), RELATIONSHIP_LABELS,
                indexOf(RELATIONSHIP_VALUES, request.relationshipStage));
        Spinner style = addSpinner(activity, root, activity.getString(R.string.wkdeepseek_preferred_style), STYLE_LABELS,
                indexOf(STYLE_VALUES, request.preferredStyle));
        Spinner flirt = addSpinner(activity, root, activity.getString(R.string.wkdeepseek_flirt_level), FLIRT_LABELS,
                Math.max(0, Math.min(2, request.flirtLevel)));

        EditText myNative = addField(activity, root, activity.getString(R.string.wkdeepseek_my_native), request.myNativeLanguage, true, 100);
        EditText peerNative = addField(activity, root, activity.getString(R.string.wkdeepseek_peer_native), request.peerNativeLanguage, true, 100);
        EditText purpose = addField(activity, root, activity.getString(R.string.wkdeepseek_purpose), request.purpose, true, 200);
        EditText background = addField(activity, root, activity.getString(R.string.wkdeepseek_background), request.background, false, 1000);
        background.setMinLines(3);
        background.setGravity(Gravity.TOP | Gravity.START);

        CheckBox contextEnabled = new CheckBox(activity);
        contextEnabled.setText(R.string.wkdeepseek_context_enabled);
        contextEnabled.setChecked(request.contextEnabled);
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        checkLp.topMargin = dp(activity, 10);
        root.addView(contextEnabled, checkLp);

        TextView contextHint = new TextView(activity);
        contextHint.setText("使用当前聊天页已加载的全部消息；复用同一 DeepSeek 会话时通常只提交新增消息。仅当 DeepSeek 明确提示内容过长时，才自动缩短并新建会话。");
        contextHint.setTextColor(Color.rgb(95, 101, 112));
        contextHint.setTextSize(12);
        contextHint.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams contextHintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contextHintLp.topMargin = dp(activity, 2);
        root.addView(contextHint, contextHintLp);

        TextView record = new TextView(activity);
        record.setTextColor(Color.rgb(95, 101, 112));
        record.setTextSize(13);
        record.setLineSpacing(0f, 1.15f);
        record.setText(buildRecordText(activity, request.contactProfile));
        LinearLayout.LayoutParams recordLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recordLp.topMargin = dp(activity, 14);
        root.addView(record, recordLp);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.wkdeepseek_settings_title)
                .setView(scrollView)
                .setNegativeButton(R.string.wkdeepseek_cancel, null)
                .setNeutralButton(R.string.wkdeepseek_clear_record, null)
                .setPositiveButton(R.string.wkdeepseek_save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                request.relationshipStage = RELATIONSHIP_VALUES[relationship.getSelectedItemPosition()];
                request.preferredStyle = STYLE_VALUES[style.getSelectedItemPosition()];
                request.flirtLevel = flirt.getSelectedItemPosition();
                request.myNativeLanguage = text(myNative);
                request.peerNativeLanguage = text(peerNative);
                request.purpose = text(purpose);
                request.background = text(background);
                request.contextEnabled = contextEnabled.isChecked();
                request.contextLimit = 0;
                if (request.contactProfile == null) request.contactProfile = new DeepSeekContactProfile();
                DeepSeekContactStore.save(activity, request);
                if (callback != null) callback.onChanged();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> new AlertDialog.Builder(activity)
                    .setTitle(R.string.wkdeepseek_clear_record)
                    .setMessage(R.string.wkdeepseek_clear_record_confirm)
                    .setNegativeButton(R.string.wkdeepseek_cancel, null)
                    .setPositiveButton(R.string.wkdeepseek_clear, (confirm, which) -> {
                        DeepSeekContactStore.clearProfile(activity, request);
                        record.setText(buildRecordText(activity, request.contactProfile));
                        Toast.makeText(activity, R.string.wkdeepseek_record_cleared, Toast.LENGTH_SHORT).show();
                    })
                    .show());
        });
        dialog.show();
    }

    private static Spinner addSpinner(Context context, LinearLayout root, String label, String[] values, int selected) {
        addLabel(context, root, label);
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(values.length - 1, selected)));
        root.addView(spinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return spinner;
    }

    private static EditText addField(Context context, LinearLayout root, String label, String value,
                                     boolean singleLine, int maxLength) {
        addLabel(context, root, label);
        EditText edit = new EditText(context);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(15);
        edit.setSingleLine(singleLine);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        edit.setInputType(singleLine
                ? InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }

    private static void addLabel(Context context, LinearLayout root, String label) {
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextColor(Color.rgb(95, 101, 112));
        title.setTextSize(13);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(context, 10);
        root.addView(title, titleLp);
    }

    private static String buildRecordText(Context context, DeepSeekContactProfile profile) {
        if (profile == null || (TextUtils.isEmpty(profile.conversationSummary)
                && "uncertain".equals(profile.interactionState)
                && profile.knownFacts.isEmpty()
                && profile.keyEvents.isEmpty())) {
            return context.getString(R.string.wkdeepseek_no_contact_record);
        }
        StringBuilder out = new StringBuilder(context.getString(R.string.wkdeepseek_contact_record_title));
        String state = DeepSeekProfileParser.stateLabel(profile.interactionState);
        String trend = DeepSeekProfileParser.trendLabel(profile.trend);
        if (!TextUtils.isEmpty(state)) out.append("\n").append(context.getString(R.string.wkdeepseek_record_state)).append("：").append(state);
        if (!TextUtils.isEmpty(trend)) out.append("\n").append(context.getString(R.string.wkdeepseek_record_trend)).append("：").append(trend);
        if (!TextUtils.isEmpty(profile.conversationSummary)) out.append("\n").append(context.getString(R.string.wkdeepseek_record_summary)).append("：").append(profile.conversationSummary);
        if (!profile.knownFacts.isEmpty()) out.append("\n").append(context.getString(R.string.wkdeepseek_record_facts)).append("：").append(profile.knownFactsText());
        return out.toString();
    }

    private static int indexOf(String[] values, String target) {
        if (target != null) {
            for (int i = 0; i < values.length; i++) if (target.equals(values[i])) return i;
        }
        return 0;
    }

    private static String text(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
