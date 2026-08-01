package com.chat.forum;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Compact poll editor backed by bbs-go's native vote request model. */
final class ForumVoteEditorView extends LinearLayout {
    private static final int TYPE_SINGLE = 1;
    private static final int TYPE_MULTIPLE = 2;
    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 20;
    private static final long[] DURATIONS_MS = {
            24L * 60L * 60L * 1000L,
            3L * 24L * 60L * 60L * 1000L,
            7L * 24L * 60L * 60L * 1000L,
            30L * 24L * 60L * 60L * 1000L
    };

    static final class State {
        boolean enabled;
        String title = "";
        ArrayList<String> options = new ArrayList<>();
        int type = TYPE_SINGLE;
        int voteNum = 1;
        int durationIndex;
    }

    private final boolean dark;
    private final TextView toggle;
    private final LinearLayout editor;
    private final EditText titleInput;
    private final LinearLayout optionsContainer;
    private final TextView addOption;
    private final TextView singleTab;
    private final TextView multipleTab;
    private final LinearLayout maxSelectionRow;
    private final Spinner maxSelectionSpinner;
    private final Spinner durationSpinner;
    private final ArrayList<String> options = new ArrayList<>();

    private boolean voteEnabled;
    private boolean publishing;
    private int voteType = TYPE_SINGLE;
    private int voteNum = 1;
    private int durationIndex;
    private boolean updatingSpinner;

    ForumVoteEditorView(@NonNull Context context) {
        super(context);
        dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setOrientation(VERTICAL);
        setPadding(0, dp(4), 0, 0);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(ForumText.get(R.string.forum_vote_optional), 13,
                dark ? 0xFFE1E4E8 : 0xFF30353B, true);
        header.addView(label, new LayoutParams(0, dp(40), 1f));
        toggle = text(ForumText.get(R.string.forum_vote_add), 13, 0xFF1877F2, true);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(10), 0, dp(10), 0);
        toggle.setBackground(roundRect(dark ? 0xFF243B59 : 0xFFEAF3FF, 14));
        toggle.setOnClickListener(v -> {
            if (publishing) return;
            setVoteEnabled(!voteEnabled);
        });
        header.addView(toggle, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        editor = new LinearLayout(context);
        editor.setOrientation(VERTICAL);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        editor.setBackground(roundRect(dark ? 0xFF222429 : 0xFFF7F8FA, 12));

        titleInput = input(ForumText.get(R.string.forum_vote_title_hint));
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128)});
        editor.addView(titleInput, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView optionLabel = text(ForumText.get(R.string.forum_vote_options), 12,
                dark ? 0xFFB5BAC2 : 0xFF6D747D, true);
        LayoutParams optionLabelParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optionLabelParams.topMargin = dp(12);
        editor.addView(optionLabel, optionLabelParams);

        optionsContainer = new LinearLayout(context);
        optionsContainer.setOrientation(VERTICAL);
        LayoutParams optionsParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optionsParams.topMargin = dp(5);
        editor.addView(optionsContainer, optionsParams);

        addOption = text(ForumText.get(R.string.forum_vote_add_option), 12.5f,
                0xFF1877F2, true);
        addOption.setGravity(Gravity.CENTER_VERTICAL);
        addOption.setPadding(dp(6), 0, dp(6), 0);
        addOption.setOnClickListener(v -> {
            if (publishing || options.size() >= MAX_OPTIONS) return;
            options.add("");
            renderOptions();
        });
        LayoutParams addParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        addParams.topMargin = dp(2);
        editor.addView(addOption, addParams);

        LinearLayout typeRow = new LinearLayout(context);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView typeLabel = text(ForumText.get(R.string.forum_vote_type), 12,
                dark ? 0xFFB5BAC2 : 0xFF6D747D, true);
        typeRow.addView(typeLabel, new LayoutParams(0, dp(36), 1f));
        LinearLayout typePill = new LinearLayout(context);
        typePill.setGravity(Gravity.CENTER_VERTICAL);
        typePill.setPadding(dp(2), dp(2), dp(2), dp(2));
        typePill.setBackground(roundRect(dark ? 0xFF303238 : 0xFFECEFF2, 14));
        singleTab = typeTab(ForumText.get(R.string.forum_vote_single), TYPE_SINGLE);
        multipleTab = typeTab(ForumText.get(R.string.forum_vote_multiple), TYPE_MULTIPLE);
        typePill.addView(singleTab, new LayoutParams(dp(70), dp(28)));
        typePill.addView(multipleTab, new LayoutParams(dp(70), dp(28)));
        typeRow.addView(typePill, new LayoutParams(dp(144), dp(32)));
        LayoutParams typeRowParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        typeRowParams.topMargin = dp(4);
        editor.addView(typeRow, typeRowParams);

        maxSelectionRow = new LinearLayout(context);
        maxSelectionRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView maxLabel = text(ForumText.get(R.string.forum_vote_max_selection), 12,
                dark ? 0xFFB5BAC2 : 0xFF6D747D, true);
        maxSelectionRow.addView(maxLabel, new LayoutParams(0, dp(42), 1f));
        maxSelectionSpinner = new Spinner(context);
        maxSelectionSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (!updatingSpinner && voteType == TYPE_MULTIPLE) voteNum = position + 2;
        }));
        maxSelectionRow.addView(maxSelectionSpinner, new LayoutParams(dp(100), dp(42)));
        editor.addView(maxSelectionRow, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout durationRow = new LinearLayout(context);
        durationRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView durationLabel = text(ForumText.get(R.string.forum_vote_duration), 12,
                dark ? 0xFFB5BAC2 : 0xFF6D747D, true);
        durationRow.addView(durationLabel, new LayoutParams(0, dp(42), 1f));
        durationSpinner = new Spinner(context);
        List<String> durations = new ArrayList<>();
        durations.add(ForumText.get(R.string.forum_vote_duration_1d));
        durations.add(ForumText.get(R.string.forum_vote_duration_3d));
        durations.add(ForumText.get(R.string.forum_vote_duration_7d));
        durations.add(ForumText.get(R.string.forum_vote_duration_30d));
        ArrayAdapter<String> durationAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, durations);
        durationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        durationSpinner.setAdapter(durationAdapter);
        durationSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                durationIndex = Math.max(0, Math.min(DURATIONS_MS.length - 1, position))));
        durationRow.addView(durationSpinner, new LayoutParams(dp(110), dp(42)));
        editor.addView(durationRow, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        addView(editor, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        options.add("");
        options.add("");
        renderOptions();
        renderType();
        setVoteEnabled(false);
    }

    void setAllowed(boolean allowed) {
        setVisibility(allowed ? VISIBLE : GONE);
    }

    void setPublishing(boolean publishing) {
        this.publishing = publishing;
        toggle.setEnabled(!publishing);
        titleInput.setEnabled(!publishing);
        addOption.setEnabled(!publishing && options.size() < MAX_OPTIONS);
        singleTab.setEnabled(!publishing);
        multipleTab.setEnabled(!publishing);
        maxSelectionSpinner.setEnabled(!publishing);
        durationSpinner.setEnabled(!publishing);
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View row = optionsContainer.getChildAt(i);
            if (!(row instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) row;
            if (group.getChildCount() > 1) group.getChildAt(1).setEnabled(!publishing);
            if (group.getChildCount() > 2) {
                group.getChildAt(2).setEnabled(!publishing && options.size() > MIN_OPTIONS);
            }
        }
        setAlpha(publishing ? 0.72f : 1f);
    }

    boolean isVoteEnabled() {
        return voteEnabled;
    }

    boolean validate() {
        if (!voteEnabled || getVisibility() != VISIBLE) return true;
        clearValidationErrors();
        String title = titleInput.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            titleInput.setError(ForumText.get(R.string.forum_vote_title_required));
            titleInput.requestFocus();
            return false;
        }
        ArrayList<String> values = normalizedOptions();
        if (values.size() < MIN_OPTIONS) {
            focusFirstEmptyOption(ForumText.get(R.string.forum_vote_options_min));
            return false;
        }
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (!unique.add(value.toLowerCase(Locale.ROOT))) {
                focusOption(value, ForumText.get(R.string.forum_vote_option_duplicate));
                return false;
            }
        }
        if (voteType == TYPE_MULTIPLE && (voteNum < 2 || voteNum > values.size())) {
            maxSelectionSpinner.requestFocus();
            return false;
        }
        return true;
    }

    @Nullable
    ForumApiClient.CreateVote buildVote() {
        if (!voteEnabled || getVisibility() != VISIBLE) return null;
        ArrayList<String> values = normalizedOptions();
        ForumApiClient.CreateVote vote = new ForumApiClient.CreateVote();
        vote.type = voteType;
        vote.title = titleInput.getText().toString().trim();
        vote.voteNum = voteType == TYPE_SINGLE ? 1 : Math.min(voteNum, values.size());
        int index = Math.max(0, Math.min(DURATIONS_MS.length - 1, durationIndex));
        vote.expiredAt = System.currentTimeMillis() + DURATIONS_MS[index];
        for (String value : values) vote.options.add(new ForumApiClient.CreateVoteOption(value));
        return vote;
    }

    State snapshot() {
        State state = new State();
        state.enabled = voteEnabled;
        state.title = titleInput.getText().toString();
        state.options = new ArrayList<>(options);
        state.type = voteType;
        state.voteNum = voteNum;
        state.durationIndex = durationIndex;
        return state;
    }

    void restore(@Nullable State state) {
        if (state == null) return;
        titleInput.setText(state.title == null ? "" : state.title);
        options.clear();
        if (state.options != null) {
            for (String option : state.options) {
                if (options.size() >= MAX_OPTIONS) break;
                options.add(option == null ? "" : option);
            }
        }
        while (options.size() < MIN_OPTIONS) options.add("");
        voteType = state.type == TYPE_MULTIPLE ? TYPE_MULTIPLE : TYPE_SINGLE;
        voteNum = voteType == TYPE_SINGLE ? 1 : Math.max(2, state.voteNum);
        durationIndex = Math.max(0, Math.min(DURATIONS_MS.length - 1, state.durationIndex));
        durationSpinner.setSelection(durationIndex);
        renderOptions();
        renderType();
        setVoteEnabled(state.enabled);
    }

    private void setVoteEnabled(boolean enabled) {
        boolean removing = voteEnabled && !enabled;
        voteEnabled = enabled;
        if (removing) {
            titleInput.setText("");
            options.clear();
            options.add("");
            options.add("");
            voteType = TYPE_SINGLE;
            voteNum = 1;
            durationIndex = 0;
            durationSpinner.setSelection(0);
            renderOptions();
            renderType();
        }
        editor.setVisibility(enabled ? VISIBLE : GONE);
        toggle.setText(enabled ? R.string.forum_vote_remove : R.string.forum_vote_add);
        toggle.setTextColor(enabled ? 0xFFE05252 : 0xFF1877F2);
        toggle.setBackground(roundRect(enabled
                ? (dark ? 0xFF482C2C : 0xFFFFEEEE)
                : (dark ? 0xFF243B59 : 0xFFEAF3FF), 14));
    }

    private void renderOptions() {
        optionsContainer.removeAllViews();
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView number = text((i + 1) + ".", 12,
                    dark ? 0xFF8F959D : 0xFF858B93, false);
            number.setGravity(Gravity.CENTER);
            row.addView(number, new LayoutParams(dp(24), dp(44)));
            EditText option = input(ForumText.get(R.string.forum_vote_option_hint, i + 1));
            option.setSingleLine(false);
            option.setMaxLines(2);
            option.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
            option.setText(options.get(i));
            option.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (index < options.size()) options.set(index, s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            row.addView(option, new LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView remove = text("×", 22,
                    options.size() > MIN_OPTIONS ? 0xFFE05252
                            : (dark ? 0xFF656A72 : 0xFFB5BAC0), false);
            remove.setGravity(Gravity.CENTER);
            remove.setEnabled(options.size() > MIN_OPTIONS && !publishing);
            remove.setOnClickListener(v -> {
                if (publishing || options.size() <= MIN_OPTIONS || index >= options.size()) return;
                options.remove(index);
                if (voteType == TYPE_MULTIPLE) {
                    voteNum = Math.max(2, Math.min(voteNum, options.size()));
                }
                renderOptions();
            });
            row.addView(remove, new LayoutParams(dp(34), dp(44)));
            LayoutParams rowParams = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(6);
            optionsContainer.addView(row, rowParams);
        }
        addOption.setText(ForumText.get(R.string.forum_vote_add_option_count,
                options.size(), MAX_OPTIONS));
        addOption.setEnabled(!publishing && options.size() < MAX_OPTIONS);
        updateMaxSelectionSpinner();
    }

    private void renderType() {
        boolean single = voteType == TYPE_SINGLE;
        styleTypeTab(singleTab, single);
        styleTypeTab(multipleTab, !single);
        maxSelectionRow.setVisibility(single ? GONE : VISIBLE);
        updateMaxSelectionSpinner();
    }

    private TextView typeTab(String label, int type) {
        TextView tab = text(label, 12, dark ? 0xFFB7BCC4 : 0xFF69717A, false);
        tab.setGravity(Gravity.CENTER);
        tab.setOnClickListener(v -> {
            if (publishing || voteType == type) return;
            voteType = type;
            voteNum = type == TYPE_SINGLE ? 1 : Math.max(2, Math.min(options.size(), voteNum));
            renderType();
        });
        return tab;
    }

    private void styleTypeTab(TextView tab, boolean selected) {
        tab.setTextColor(selected ? 0xFF1877F2 : (dark ? 0xFFB7BCC4 : 0xFF69717A));
        tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setBackground(selected ? roundRect(dark ? 0xFF34465F : Color.WHITE, 12) : null);
    }

    private void updateMaxSelectionSpinner() {
        if (maxSelectionSpinner == null) return;
        int max = Math.max(2, options.size());
        ArrayList<String> values = new ArrayList<>();
        for (int i = 2; i <= max; i++) {
            values.add(ForumText.get(R.string.forum_vote_max_selection_value, i));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        updatingSpinner = true;
        maxSelectionSpinner.setAdapter(adapter);
        voteNum = Math.max(2, Math.min(voteNum, max));
        maxSelectionSpinner.setSelection(Math.max(0, voteNum - 2), false);
        maxSelectionSpinner.post(() -> updatingSpinner = false);
    }

    private ArrayList<String> normalizedOptions() {
        ArrayList<String> values = new ArrayList<>();
        for (String option : options) {
            String value = option == null ? "" : option.trim();
            if (!TextUtils.isEmpty(value)) values.add(value);
        }
        return values;
    }

    private void clearValidationErrors() {
        titleInput.setError(null);
        addOption.setError(null);
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View row = optionsContainer.getChildAt(i);
            if (!(row instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) row;
            if (group.getChildCount() > 1 && group.getChildAt(1) instanceof EditText) {
                ((EditText) group.getChildAt(1)).setError(null);
            }
        }
    }

    private void focusFirstEmptyOption(String error) {
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View row = optionsContainer.getChildAt(i);
            if (!(row instanceof ViewGroup)) continue;
            View input = ((ViewGroup) row).getChildAt(1);
            if (input instanceof EditText && TextUtils.isEmpty(((EditText) input).getText().toString().trim())) {
                ((EditText) input).setError(error);
                input.requestFocus();
                return;
            }
        }
        addOption.setError(error);
        addOption.requestFocus();
    }

    private void focusOption(String value, String error) {
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View row = optionsContainer.getChildAt(i);
            if (!(row instanceof ViewGroup)) continue;
            View input = ((ViewGroup) row).getChildAt(1);
            if (input instanceof EditText
                    && value.equalsIgnoreCase(((EditText) input).getText().toString().trim())) {
                ((EditText) input).setError(error);
                input.requestFocus();
                return;
            }
        }
    }

    private EditText input(String hint) {
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setTextColor(dark ? Color.WHITE : 0xFF252A30);
        input.setHintTextColor(dark ? 0xFF777D85 : 0xFF9BA1A8);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        input.setBackground(roundRect(dark ? 0xFF2B2D32 : Color.WHITE, 10));
        return input;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private interface PositionListener {
        void onSelected(int position);
    }

    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        private final PositionListener listener;

        private SimpleItemSelectedListener(PositionListener listener) {
            this.listener = listener;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                   int position, long id) {
            listener.onSelected(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }
}
