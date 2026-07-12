package com.chat.partnerlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.chat.base.endpoint.EndpointManager;

import java.lang.ref.WeakReference;

public final class WKPartnerListApplication {
    private static final WKPartnerListApplication INSTANCE = new WKPartnerListApplication();
    private WeakReference<Context> contextRef;
    private boolean inited;

    private WKPartnerListApplication() {}

    public static WKPartnerListApplication getInstance() { return INSTANCE; }

    public synchronized void init(Context context) {
        if (inited) return;
        inited = true;
        contextRef = new WeakReference<>(context == null ? null : context.getApplicationContext());
        EndpointManager.getInstance().setMethod("peipe_open_partner_list", object -> {
            open(object instanceof Context ? (Context) object : null);
            return true;
        });
        EndpointManager.getInstance().setMethod("partnerlist_open", object -> {
            open(object instanceof Context ? (Context) object : null);
            return true;
        });
    }

    public void open(Context source) {
        Context context = source;
        if (context == null && contextRef != null) context = contextRef.get();
        if (context == null) return;
        Intent intent = new Intent(context, PartnerListActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
