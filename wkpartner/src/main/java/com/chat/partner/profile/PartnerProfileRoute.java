package com.chat.partner.profile;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;

public final class PartnerProfileRoute {
    public static final String EXTRA_UID = "uid";

    private PartnerProfileRoute() {}

    public static void open(Context context) {
        open(context, WKConfig.getInstance().getUid());
    }

    public static void open(Context context, String uid) {
        if (context == null) return;
        Intent intent = new Intent(context, PartnerProfileActivity.class);
        if (!TextUtils.isEmpty(uid)) intent.putExtra(EXTRA_UID, uid);
        context.startActivity(intent);
    }
}
