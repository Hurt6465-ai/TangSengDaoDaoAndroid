package com.chat.partner.profile;

import android.content.Context;
import android.text.TextUtils;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.UserDetailMenu;

import java.lang.ref.WeakReference;

/**
 * 语伴插件模块入口。
 * 统一接管 App 内查看用户资料的入口，让消息、联系人、聊天室、发现等地方都打开语伴个人主页。
 */
public class WKPartnerApplication {
    private static final WKPartnerApplication INSTANCE = new WKPartnerApplication();
    private WeakReference<Context> contextRef;
    private boolean inited;

    public static WKPartnerApplication getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        if (inited) return;
        inited = true;
        contextRef = new WeakReference<>(context.getApplicationContext());
        EndpointManager.getInstance().setMethod(EndpointSID.userDetailView, object -> {
            if (object instanceof UserDetailMenu) {
                UserDetailMenu menu = (UserDetailMenu) object;
                if (!TextUtils.isEmpty(menu.uid)) {
                    Context targetContext = menu.context == null ? getContext() : menu.context;
                    if (targetContext != null) PartnerProfileRoute.open(targetContext, menu.uid);
                }
            }
            return null;
        });
    }

    public Context getContext() {
        return contextRef == null ? null : contextRef.get();
    }
}
