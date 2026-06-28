package com.chat.uikit.user;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.chat.base.config.WKSystemAccount;

/**
 * 全 App 用户资料页统一跳转入口。
 *
 * wkuikit 不能直接依赖 wkpartner，否则会形成模块循环依赖：
 * wkpartner -> wkuikit，wkuikit -> wkpartner。
 * 所以这里用反射优先打开 com.chat.partner.profile.PartnerProfileActivity，
 * 找不到 wkpartner 或系统账号时回退旧 UserDetailActivity，保证不会崩溃。
 */
public final class ProfileNavigator {
    private static final String PARTNER_PROFILE_ACTIVITY = "com.chat.partner.profile.PartnerProfileActivity";

    private ProfileNavigator() {
    }

    public static void open(Context context, String uid) {
        open(context, uid, "", "", "");
    }

    public static void open(Context context, String uid, String groupId) {
        open(context, uid, groupId, "", "");
    }

    public static void open(Context context, String uid, String groupId, String vercode, String name) {
        if (context == null || TextUtils.isEmpty(uid)) return;
        Intent intent = makeIntent(context, uid, groupId, vercode, name);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static Intent makeIntent(Context context, String uid, String groupId, String vercode, String name) {
        if (!WKSystemAccount.isSystemAccount(uid)) {
            try {
                Class<?> cls = Class.forName(PARTNER_PROFILE_ACTIVITY);
                Intent intent = new Intent(context, cls);
                putCommonExtras(intent, uid, groupId, vercode, name);
                return intent;
            } catch (Throwable ignored) {
                // wkpartner 未接入或类名变化时，回退旧资料页。
            }
        }
        Intent fallback = new Intent(context, UserDetailActivity.class);
        putCommonExtras(fallback, uid, groupId, vercode, name);
        return fallback;
    }

    private static void putCommonExtras(Intent intent, String uid, String groupId, String vercode, String name) {
        intent.putExtra("uid", uid);
        if (!TextUtils.isEmpty(groupId)) {
            // 兼容旧 UserDetailActivity 的 groupID，也兼容新 PartnerProfileActivity 的 group_id / groupId。
            intent.putExtra("groupID", groupId);
            intent.putExtra("groupId", groupId);
            intent.putExtra("group_id", groupId);
        }
        if (!TextUtils.isEmpty(vercode)) intent.putExtra("vercode", vercode);
        if (!TextUtils.isEmpty(name)) intent.putExtra("name", name);
    }
}
