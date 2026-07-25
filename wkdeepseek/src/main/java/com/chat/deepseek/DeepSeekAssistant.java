package com.chat.deepseek;

import android.app.AlertDialog;
import android.content.Context;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

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
    private static final String KEY_COPIED_REPLY_CHANNEL_ID = "copied_reply_channel_id";
    private static final String KEY_COPIED_REPLY_CHANNEL_TYPE = "copied_reply_channel_type";
    private static final String KEY_COPIED_REPLY_TEXT = "copied_reply_text";
    private static final String KEY_COPIED_REPLY_BACK_TRANSLATION = "copied_reply_back_translation";
    private static final String KEY_COPIED_REPLY_AT = "copied_reply_at";
    private static final long COPIED_REPLY_TTL_MS = 30L * 60L * 1000L;
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
    private static final String[] CONTEXT_LABELS = {"最近 50 条", "最近 100 条"};

    private DeepSeekAssistant() {}

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * Remembers the back-translation for a reply copied from the embedded DeepSeek page. Only the
     * peer-facing reply is placed on the clipboard; this metadata stays inside Talkami.
     */
    public static void rememberCopiedReply(Context context, DeepSeekRequest request,
                                           String text, String backTranslation) {
        if (context == null || request == null || TextUtils.isEmpty(text)) return;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_COPIED_REPLY_CHANNEL_ID, request.channelId == null ? "" : request.channelId)
                .putInt(KEY_COPIED_REPLY_CHANNEL_TYPE, request.channelType)
                .putString(KEY_COPIED_REPLY_TEXT, normalizeCopiedReplyText(text))
                .putString(KEY_COPIED_REPLY_BACK_TRANSLATION,
                        backTranslation == null ? "" : backTranslation.trim())
                .putLong(KEY_COPIED_REPLY_AT, System.currentTimeMillis())
                .commit();
    }

    /**
     * Returns null when the current composer text is not the copied AI reply. An empty string is a
     * valid match with no back-translation and still means the text must bypass send translation.
     */
    @Nullable
    public static String consumeCopiedReplyBackTranslation(Context context, String channelId,
                                                            byte channelType, String text) {
        if (context == null || TextUtils.isEmpty(text)) return null;
        android.content.SharedPreferences preferences =
                context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long copiedAt = preferences.getLong(KEY_COPIED_REPLY_AT, 0L);
        if (copiedAt <= 0L || System.currentTimeMillis() - copiedAt > COPIED_REPLY_TTL_MS) {
            clearCopiedReply(preferences);
            return null;
        }
        String storedChannelId = preferences.getString(KEY_COPIED_REPLY_CHANNEL_ID, "");
        int storedChannelType = preferences.getInt(KEY_COPIED_REPLY_CHANNEL_TYPE, -1);
        String storedText = preferences.getString(KEY_COPIED_REPLY_TEXT, "");
        boolean matches = TextUtils.equals(storedChannelId, channelId == null ? "" : channelId)
                && storedChannelType == channelType
                && TextUtils.equals(storedText, normalizeCopiedReplyText(text));
        String backTranslation = matches
                ? preferences.getString(KEY_COPIED_REPLY_BACK_TRANSLATION, "") : null;
        // A different message, an edited reply, or a different contact invalidates stale metadata.
        clearCopiedReply(preferences);
        return backTranslation;
    }

    private static String normalizeCopiedReplyText(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static void clearCopiedReply(android.content.SharedPreferences preferences) {
        preferences.edit()
                .remove(KEY_COPIED_REPLY_CHANNEL_ID)
                .remove(KEY_COPIED_REPLY_CHANNEL_TYPE)
                .remove(KEY_COPIED_REPLY_TEXT)
                .remove(KEY_COPIED_REPLY_BACK_TRANSLATION)
                .remove(KEY_COPIED_REPLY_AT)
                .apply();
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
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
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
        DeepSeekContactStore.apply(activity, request);
        DeepSeekAssistantDialog dialog = DeepSeekAssistantDialog.newAction(request);
        dialog.setTranslationCallback(callback);
        dialog.setStateCallback(closeCallback);
        dialog.show(activity.getSupportFragmentManager(), TAG);
        return true;
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

        Spinner contextLimit = addSpinner(activity, root, activity.getString(R.string.wkdeepseek_context_limit), CONTEXT_LABELS,
                request.contextLimit <= 50 ? 0 : 1);
        contextLimit.setEnabled(request.contextEnabled);
        contextEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> contextLimit.setEnabled(isChecked));

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
                request.contextLimit = contextLimit.getSelectedItemPosition() == 0 ? 50 : 100;
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
