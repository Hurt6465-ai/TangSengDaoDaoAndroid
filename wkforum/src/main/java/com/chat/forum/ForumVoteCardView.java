package com.chat.forum;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Native bbs-go poll card used inside topic detail. */
final class ForumVoteCardView extends LinearLayout {
    interface Listener {
        void onSelectionChanged(long voteId, @NonNull List<Long> optionIds);
        void onSubmit(long voteId, @NonNull List<Long> optionIds);
    }

    private final boolean dark;
    private final Set<Long> selectedIds = new LinkedHashSet<>();
    private ForumApiClient.Vote vote;
    private Listener listener;
    private long boundVoteId;
    private boolean submitting;

    ForumVoteCardView(@NonNull Context context) {
        super(context);
        dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setOrientation(VERTICAL);
        setVisibility(GONE);
    }

    void bind(@Nullable ForumApiClient.Vote value, boolean submitting,
              @Nullable List<Long> pendingSelection,
              @Nullable Listener listener) {
        this.listener = listener;
        this.submitting = submitting;
        if (value == null || value.id <= 0 || TextUtils.isEmpty(value.title)
                || value.options == null || value.options.size() < 2) {
            vote = null;
            boundVoteId = 0L;
            selectedIds.clear();
            removeAllViews();
            setVisibility(GONE);
            return;
        }
        boolean newVote = boundVoteId != value.id;
        boolean expiredNow = isExpired(value);
        vote = value;
        boundVoteId = value.id;
        if (value.voted) {
            selectedIds.clear();
            if (value.optionIds != null) selectedIds.addAll(value.optionIds);
            if (value.optionIds == null || value.optionIds.isEmpty()) {
                for (ForumApiClient.VoteOption option : value.options) {
                    if (option != null && option.voted) selectedIds.add(option.id);
                }
            }
        } else if (!expiredNow && pendingSelection != null) {
            selectedIds.clear();
            selectedIds.addAll(pendingSelection);
        } else if (newVote || expiredNow) {
            selectedIds.clear();
        }
        setVisibility(VISIBLE);
        render();
    }

    private void render() {
        removeAllViews();
        ForumApiClient.Vote value = vote;
        if (value == null) {
            setVisibility(GONE);
            return;
        }
        setPadding(dp(13), dp(13), dp(13), dp(13));
        setBackground(roundRect(dark ? 0xFF22252A : 0xFFF7F9FC, 13,
                dark ? 0xFF34373D : 0xFFE7EBF0, 1));

        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text(ForumText.get(R.string.forum_vote_badge), 10.5f,
                Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(7), 0, dp(7), 0);
        badge.setBackground(roundRect(0xFFFF7827, 9, 0, 0));
        titleRow.addView(badge, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(21)));
        TextView title = text(value.title, 15.5f,
                dark ? Color.WHITE : 0xFF23272D, true);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams titleParams = new LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(8);
        titleRow.addView(title, titleParams);
        addView(titleRow, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int maxSelect = Math.max(1, value.voteNum);
        boolean multiple = value.type == 2;
        TextView meta = text(multiple
                        ? ForumText.get(R.string.forum_vote_meta_multiple,
                        value.options.size(), maxSelect)
                        : ForumText.get(R.string.forum_vote_meta_single,
                        value.options.size()),
                11.5f, dark ? 0xFF9EA4AD : 0xFF747C86, false);
        LayoutParams metaParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(5);
        addView(meta, metaParams);

        long expiredAt = normalizeTime(value.expiredAt);
        boolean expired = isExpired(value);
        if (expired && !value.voted) selectedIds.clear();
        boolean canVote = !expired && !value.voted && !submitting;
        boolean showResults = value.voted || expired;

        for (ForumApiClient.VoteOption option : value.options) {
            if (option == null || option.id <= 0 || TextUtils.isEmpty(option.content)) continue;
            boolean selected = selectedIds.contains(option.id) || option.voted;
            LinearLayout optionBox = new LinearLayout(getContext());
            optionBox.setOrientation(VERTICAL);
            int fill = selected && canVote
                    ? (dark ? 0xFF303F52 : 0xFFEEF5FF)
                    : (dark ? 0xFF2A2D32 : Color.WHITE);
            int stroke = selected
                    ? 0xFF1877F2
                    : (dark ? 0xFF3A3E45 : 0xFFE1E5EA);
            optionBox.setBackground(roundRect(fill, 10, stroke, selected ? 1.2f : 1f));
            optionBox.setPadding(dp(10), dp(8), dp(10), dp(7));
            optionBox.setClickable(canVote);
            optionBox.setOnClickListener(canVote ? v -> toggle(option.id) : null);

            LinearLayout line = new LinearLayout(getContext());
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView marker = text(selected ? "✓" : (multiple ? "□" : "○"),
                    selected ? 14 : 13,
                    selected ? 0xFF1877F2 : (dark ? 0xFF989EA6 : 0xFF858C95),
                    selected);
            marker.setGravity(Gravity.CENTER);
            line.addView(marker, new LayoutParams(dp(24), dp(26)));
            TextView content = text(option.content, 13.5f,
                    selected ? (dark ? 0xFFAFD0FF : 0xFF1769CC)
                            : (dark ? 0xFFE0E3E7 : 0xFF41474F),
                    selected);
            content.setMaxLines(4);
            content.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(content, new LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (showResults) {
                int percent = resultPercent(value, option);
                TextView result = text(ForumText.get(R.string.forum_vote_option_result,
                                option.voteCount, percent),
                        11, dark ? 0xFFADB2BA : 0xFF6E7680, false);
                result.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                LayoutParams resultParams = new LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(26));
                resultParams.leftMargin = dp(8);
                line.addView(result, resultParams);
            }
            optionBox.addView(line, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (showResults) {
                ProgressBar progress = new ProgressBar(getContext(), null,
                        android.R.attr.progressBarStyleHorizontal);
                progress.setMax(100);
                progress.setProgress(resultPercent(value, option));
                progress.setProgressTintList(ColorStateList.valueOf(selected
                        ? 0xFFFF7827 : 0xFF8DB9F2));
                progress.setProgressBackgroundTintList(ColorStateList.valueOf(
                        dark ? 0xFF383B41 : 0xFFE9EDF2));
                LayoutParams progressParams = new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
                progressParams.topMargin = dp(5);
                optionBox.addView(progress, progressParams);
            }

            LayoutParams optionParams = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            optionParams.topMargin = dp(8);
            addView(optionBox, optionParams);
        }

        TextView status = text(buildStatus(value, expired), 11.5f,
                dark ? 0xFF9298A1 : 0xFF7C838D, false);
        LayoutParams statusParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(10);
        addView(status, statusParams);

        TextView submit = text(submitLabel(value, expired), 13.5f,
                Color.WHITE, true);
        submit.setGravity(Gravity.CENTER);
        boolean selectionValid = multiple
                ? !selectedIds.isEmpty() && selectedIds.size() <= maxSelect
                : selectedIds.size() == 1;
        boolean enabled = canVote && selectionValid;
        submit.setEnabled(enabled);
        submit.setAlpha(enabled ? 1f : 0.48f);
        submit.setBackground(roundRect(0xFF1877F2, 18, 0, 0));
        submit.setOnClickListener(enabled ? v -> {
            Listener callback = listener;
            if (callback != null) callback.onSubmit(value.id, new ArrayList<>(selectedIds));
        } : null);
        LayoutParams submitParams = new LayoutParams(dp(160), dp(38));
        submitParams.topMargin = dp(12);
        submitParams.gravity = Gravity.START;
        addView(submit, submitParams);
    }

    private void toggle(long optionId) {
        ForumApiClient.Vote value = vote;
        if (value == null || submitting || value.voted || isExpired(value)) return;
        int maxSelect = Math.max(1, value.voteNum);
        if (value.type == 2) {
            if (selectedIds.contains(optionId)) {
                selectedIds.remove(optionId);
            } else if (selectedIds.size() >= maxSelect) {
                Toast.makeText(getContext(), ForumText.get(
                        R.string.forum_vote_select_max, maxSelect), Toast.LENGTH_SHORT).show();
                return;
            } else {
                selectedIds.add(optionId);
            }
        } else {
            selectedIds.clear();
            selectedIds.add(optionId);
        }
        Listener callback = listener;
        if (callback != null) {
            callback.onSelectionChanged(value.id, new ArrayList<>(selectedIds));
        }
        render();
    }

    private int resultPercent(ForumApiClient.Vote value,
                              ForumApiClient.VoteOption option) {
        double percent = option.percent;
        if (percent <= 0d && value.voteCount > 0 && option.voteCount > 0) {
            percent = option.voteCount * 100d / value.voteCount;
        }
        return (int) Math.max(0, Math.min(100, Math.round(percent)));
    }

    private String buildStatus(ForumApiClient.Vote value, boolean expired) {
        String participants = ForumText.get(R.string.forum_vote_participants,
                Math.max(0, value.voteCount));
        if (expired) return participants + " · " + ForumText.get(R.string.forum_vote_ended);
        long expiredAt = normalizeTime(value.expiredAt);
        if (expiredAt <= 0) return participants;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(expiredAt));
        return participants + " · " + ForumText.get(R.string.forum_vote_ends_at, time);
    }

    private String submitLabel(ForumApiClient.Vote value, boolean expired) {
        if (submitting) return ForumText.get(R.string.forum_vote_submitting);
        if (value.voted) return ForumText.get(R.string.forum_vote_voted);
        if (expired) return ForumText.get(R.string.forum_vote_ended);
        return ForumText.get(R.string.forum_vote_submit);
    }

    private boolean isExpired(@NonNull ForumApiClient.Vote value) {
        long expiredAt = normalizeTime(value.expiredAt);
        return value.expired || (expiredAt > 0L && System.currentTimeMillis() >= expiredAt);
    }

    private long normalizeTime(long value) {
        return value > 0L && value < 10_000_000_000L ? value * 1000L : value;
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

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke,
                                       float strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0 && strokeWidthDp > 0) {
            drawable.setStroke(Math.max(1, Math.round(dp(strokeWidthDp))), stroke);
        }
        return drawable;
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
