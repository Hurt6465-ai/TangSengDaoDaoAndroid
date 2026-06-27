package com.chat.partnerbrowse;

import android.content.Context;
import android.text.TextUtils;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.UserDetailMenu;

import java.lang.reflect.Method;

/** Host integration kept here so wkpartnerbrowse does not depend on the wkpartner profile module. */
public final class PartnerBrowseHostBridge {
    private PartnerBrowseHostBridge() {}

    public static void openProfile(Context context, String uid) {
        if (context == null || TextUtils.isEmpty(uid)) return;
        if (tryOpenPartnerProfileRoute(context, uid)) return;
        EndpointManager.getInstance().invoke(EndpointSID.userDetailView, new UserDetailMenu(context, uid));
    }

    private static boolean tryOpenPartnerProfileRoute(Context context, String uid) {
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            Method open = route.getMethod("open", Context.class, String.class);
            open.invoke(null, context, uid);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
