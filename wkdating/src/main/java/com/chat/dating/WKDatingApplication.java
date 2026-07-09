package com.chat.dating;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.chat.base.endpoint.EndpointManager;

import java.lang.ref.WeakReference;

/** 独立 Tinder 式交友模块入口。 */
public class WKDatingApplication {
    private static final WKDatingApplication INSTANCE = new WKDatingApplication();
    private WeakReference<Context> contextRef;
    private boolean inited;

    public static WKDatingApplication getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        if (inited) return;
        inited = true;
        contextRef = new WeakReference<>(context == null ? null : context.getApplicationContext());
        EndpointManager.getInstance().setMethod("dating_open", object -> {
            openDating(object instanceof Context ? (Context) object : null);
            return true;
        });
        EndpointManager.getInstance().setMethod("peipe_open_dating", object -> {
            openDating(object instanceof Context ? (Context) object : null);
            return true;
        });
    }

    public void openDating() {
        openDating(null);
    }

    public void openDating(Context sourceContext) {
        Context context = sourceContext;
        if (context == null && contextRef != null) context = contextRef.get();
        if (context == null) return;
        Intent intent = new Intent(context, DatingHomeActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
