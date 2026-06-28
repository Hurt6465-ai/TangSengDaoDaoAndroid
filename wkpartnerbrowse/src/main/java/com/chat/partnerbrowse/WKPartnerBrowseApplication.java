package com.chat.partnerbrowse;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.chat.base.endpoint.EndpointManager;

import java.lang.ref.WeakReference;

/**
 * 全屏语伴 Browse 插件入口。
 * 只负责注册打开全屏语伴页的端点，不接管个人主页，不接短视频。
 */
public class WKPartnerBrowseApplication {
    private static final WKPartnerBrowseApplication INSTANCE = new WKPartnerBrowseApplication();
    private WeakReference<Context> contextRef;
    private boolean inited;

    public static WKPartnerBrowseApplication getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        if (inited) return;
        inited = true;
        contextRef = new WeakReference<>(context == null ? null : context.getApplicationContext());
        EndpointManager.getInstance().setMethod("peipe_open_partner_browse", object -> {
            openPartnerBrowse(object instanceof Context ? (Context) object : null);
            return true;
        });
        EndpointManager.getInstance().setMethod("partnerbrowse_open", object -> {
            openPartnerBrowse(object instanceof Context ? (Context) object : null);
            return true;
        });
    }

    public void openPartnerBrowse() {
        openPartnerBrowse(null);
    }

    public void openPartnerBrowse(Context sourceContext) {
        Context context = sourceContext;
        if (context == null && contextRef != null) context = contextRef.get();
        if (context == null) return;
        Intent intent = new Intent(context, PartnerBrowseActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
