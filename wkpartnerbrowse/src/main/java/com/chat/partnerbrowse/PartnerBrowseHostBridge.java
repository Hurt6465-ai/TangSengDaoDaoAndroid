package com.chat.partnerbrowse;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.fragment.app.FragmentActivity;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.UserDetailMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.service.FriendModel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.lang.reflect.Method;

/** Host integration kept here so wkpartnerbrowse UI stays small and isolated. */
public final class PartnerBrowseHostBridge {
    private PartnerBrowseHostBridge() {}

    public interface ResultCallback {
        void onResult(boolean success, String msg);
    }

    public static void openProfile(Context context, String uid) {
        if (context == null || TextUtils.isEmpty(uid)) return;
        if (tryOpenPartnerProfileRoute(context, uid)) return;
        EndpointManager.getInstance().invoke(EndpointSID.userDetailView, new UserDetailMenu(context, uid));
    }

    public static void openProfileEdit(Context context) {
        if (context == null) return;
        try {
            Class<?> clazz = Class.forName("com.chat.partner.profile.PartnerProfileEditActivity");
            Intent intent = new Intent(context, clazz);
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
            tryOpenPartnerProfileRoute(context, null);
        }
    }

    public static void openChat(FragmentActivity activity, String uid) {
        if (activity == null || TextUtils.isEmpty(uid)) return;
        WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(activity, uid, WKChannelType.PERSONAL, 0, false));
    }

    public static void applyAddFriend(Context context, String uid, String vercode, String remark, ResultCallback callback) {
        if (TextUtils.isEmpty(uid)) {
            if (callback != null) callback.onResult(false, "");
            return;
        }
        try {
            FriendModel.getInstance().applyAddFriend(uid, vercode, remark, (code, msg) -> {
                boolean success = code == HttpResponseCode.success || code == 200 || code == 0;
                if (callback != null) callback.onResult(success, msg);
            });
        } catch (Throwable e) {
            if (callback != null) callback.onResult(false, e.getMessage());
        }
    }

    private static boolean tryOpenPartnerProfileRoute(Context context, String uid) {
        try {
            Class<?> route = Class.forName("com.chat.partner.profile.PartnerProfileRoute");
            Method open = TextUtils.isEmpty(uid)
                    ? route.getMethod("open", Context.class)
                    : route.getMethod("open", Context.class, String.class);
            if (TextUtils.isEmpty(uid)) open.invoke(null, context);
            else open.invoke(null, context, uid);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
