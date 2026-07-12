package com.chat.dating;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 筛选弹窗。业务值与展示文案分离，切换语言不会改变筛选枚举。 */
public final class DatingFilterDialog {
    public interface Callback {
        void onApplied(DatingFilter filter);
    }

    private DatingFilterDialog() {}

    public static void show(Context context, DatingFilter original, Callback callback) {
        DatingFilter draft = copyOf(original);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 22), dp(context, 16), dp(context, 22), dp(context, 8));

        TextView tip = text(context, context.getString(R.string.dating_filter_cross_tip), 13, false);
        tip.setTextColor(Color.rgb(120, 120, 128));
        root.addView(tip);

        TextView country = row(context, countryText(context, draft.countryMode));
        TextView gender = row(context, genderText(context, draft.gender));
        TextView age = row(context, ageText(context, draft.ageMin, draft.ageMax));
        TextView goal = row(context, goalText(context, draft.goal));
        root.addView(country);
        root.addView(gender);
        root.addView(age);
        root.addView(goal);

        final String[] countryValues = {
                DatingFilter.COUNTRY_SMART,
                DatingFilter.COUNTRY_SAME,
                DatingFilter.COUNTRY_FOREIGN
        };
        final String[] countryLabels = {
                context.getString(R.string.dating_filter_country_smart_value),
                context.getString(R.string.dating_filter_country_same_value),
                context.getString(R.string.dating_filter_country_foreign_value)
        };
        country.setOnClickListener(v -> choose(context,
                R.string.dating_filter_country_title,
                countryLabels,
                indexOf(countryValues, draft.countryMode),
                which -> {
                    draft.countryMode = countryValues[which];
                    country.setText(countryText(context, draft.countryMode));
                }));

        final String[] genderValues = {"all", "female", "male"};
        final String[] genderLabels = {
                context.getString(R.string.dating_filter_gender_all_value),
                context.getString(R.string.dating_filter_gender_female_value),
                context.getString(R.string.dating_filter_gender_male_value)
        };
        gender.setOnClickListener(v -> choose(context,
                R.string.dating_filter_gender_title,
                genderLabels,
                indexOf(genderValues, draft.gender),
                which -> {
                    draft.gender = genderValues[which];
                    gender.setText(genderText(context, draft.gender));
                }));

        final int[][] ageValues = {{18, 28}, {18, 35}, {22, 35}, {25, 40}, {30, 50}, {18, 60}};
        final String[] ageLabels = {
                context.getString(R.string.dating_filter_age_18_28),
                context.getString(R.string.dating_filter_age_18_35),
                context.getString(R.string.dating_filter_age_22_35),
                context.getString(R.string.dating_filter_age_25_40),
                context.getString(R.string.dating_filter_age_30_50),
                context.getString(R.string.dating_filter_age_18_60)
        };
        age.setOnClickListener(v -> choose(context,
                R.string.dating_filter_age_title,
                ageLabels,
                indexOfAge(ageValues, draft.ageMin, draft.ageMax),
                which -> {
                    draft.ageMin = ageValues[which][0];
                    draft.ageMax = ageValues[which][1];
                    age.setText(ageText(context, draft.ageMin, draft.ageMax));
                }));

        final String[] goalValues = {
                DatingIntent.GOAL_SERIOUS,
                DatingIntent.GOAL_MARRIAGE,
                DatingIntent.GOAL_ALL
        };
        final String[] goalLabels = {
                context.getString(R.string.dating_filter_goal_serious_value),
                context.getString(R.string.dating_filter_goal_marriage_value),
                context.getString(R.string.dating_filter_goal_all_value)
        };
        goal.setOnClickListener(v -> choose(context,
                R.string.dating_filter_goal_title,
                goalLabels,
                indexOf(goalValues, DatingIntent.normalizeGoal(draft.goal)),
                which -> {
                    draft.goal = goalValues[which];
                    goal.setText(goalText(context, draft.goal));
                }));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.dating_filter_title)
                .setView(root)
                .setNegativeButton(R.string.dating_cancel, null)
                .setPositiveButton(R.string.dating_apply, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            draft.save(context);
            dialog.dismiss();
            if (callback != null) callback.onApplied(draft);
        }));
        dialog.show();
    }

    private static DatingFilter copyOf(DatingFilter original) {
        DatingFilter draft = new DatingFilter();
        if (original != null) {
            draft.countryMode = original.countryMode;
            draft.gender = original.gender;
            draft.ageMin = original.ageMin;
            draft.ageMax = original.ageMax;
            draft.goal = original.goal;
        }
        return draft;
    }

    private static void choose(Context context, int titleRes, String[] labels, int checked, IndexCallback callback) {
        int safeChecked = checked >= 0 && checked < labels.length ? checked : 0;
        new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, safeChecked, (dialog, which) -> {
                    if (callback != null) callback.onIndex(which);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dating_cancel, null)
                .show();
    }

    private static TextView row(Context context, String value) {
        TextView view = text(context, value, 15, true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(247, 247, 249));
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), Color.rgb(230, 230, 235));
        view.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 50));
        lp.setMargins(0, dp(context, 12), 0, 0);
        view.setLayoutParams(lp);
        return view;
    }

    private static TextView text(Context context, String value, int sp, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(45, 45, 52));
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private static String countryText(Context context, String mode) {
        int valueRes = R.string.dating_filter_country_smart_value;
        if (DatingFilter.COUNTRY_SAME.equals(mode)) valueRes = R.string.dating_filter_country_same_value;
        else if (DatingFilter.COUNTRY_FOREIGN.equals(mode)) valueRes = R.string.dating_filter_country_foreign_value;
        return context.getString(R.string.dating_filter_country_format, context.getString(valueRes));
    }

    private static String genderText(Context context, String value) {
        int valueRes = R.string.dating_filter_gender_all_value;
        if ("female".equals(value)) valueRes = R.string.dating_filter_gender_female_value;
        else if ("male".equals(value)) valueRes = R.string.dating_filter_gender_male_value;
        return context.getString(R.string.dating_filter_gender_format, context.getString(valueRes));
    }

    private static String ageText(Context context, int min, int max) {
        return context.getString(R.string.dating_filter_age_format, min, max);
    }

    private static String goalText(Context context, String value) {
        String goal = DatingIntent.normalizeGoal(value);
        int valueRes = R.string.dating_filter_goal_serious_value;
        if (DatingIntent.GOAL_MARRIAGE.equals(goal)) valueRes = R.string.dating_filter_goal_marriage_value;
        else if (DatingIntent.GOAL_ALL.equals(goal)) valueRes = R.string.dating_filter_goal_all_value;
        return context.getString(R.string.dating_filter_goal_format, context.getString(valueRes));
    }

    private static int indexOf(String[] values, String value) {
        if (values == null) return 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }

    private static int indexOfAge(int[][] values, int min, int max) {
        for (int i = 0; i < values.length; i++) {
            if (values[i][0] == min && values[i][1] == max) return i;
        }
        return 1;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface IndexCallback {
        void onIndex(int which);
    }
}
