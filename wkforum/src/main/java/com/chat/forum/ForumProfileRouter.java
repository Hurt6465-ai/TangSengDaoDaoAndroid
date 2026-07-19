package com.chat.forum;

import android.content.Context;
import android.text.TextUtils;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.UserDetailMenu;

/** Opens the host app's current native profile page without coupling wkforum to wkpartner. */
final class ForumProfileRouter {
    private ForumProfileRouter() {
    }

    static void open(Context context, ForumApiClient.User user) {
        if (user == null) return;
        open(context, user.uid);
    }

    static void open(Context context, String uid) {
        if (context == null || TextUtils.isEmpty(uid)) return;
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            route.getMethod("open", Context.class, String.class).invoke(null, context, uid);
            return;
        } catch (Throwable ignored) {
            // Older hosts still expose the common user detail endpoint.
        }
        try {
            EndpointManager.getInstance().invoke(
                    EndpointSID.userDetailView, new UserDetailMenu(context, uid));
        } catch (Throwable ignored) {
            // Keep forum browsing usable even if the optional profile module is absent.
        }
    }
}
