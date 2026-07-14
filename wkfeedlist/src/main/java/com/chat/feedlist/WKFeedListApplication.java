package com.chat.feedlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.chat.base.endpoint.EndpointManager;

public final class WKFeedListApplication {
    private static final WKFeedListApplication INSTANCE = new WKFeedListApplication();
    private Context appContext;
    private boolean initialized;
    private WKFeedListApplication() {}
    public static WKFeedListApplication getInstance() { return INSTANCE; }

    public synchronized void init(Context context) {
        if (initialized) return;
        initialized = true;
        appContext = context == null ? null : context.getApplicationContext();
        EndpointManager.getInstance().setMethod("peipe_open_feed_discover", object -> { open(object instanceof Context ? (Context) object : null); return true; });
        EndpointManager.getInstance().setMethod("feed_open_discover", object -> { open(object instanceof Context ? (Context) object : null); return true; });
        EndpointManager.getInstance().setMethod("feedlist_clear_account", object -> {
            Context app = appContext;
            if (app != null) FeedListCache.clearCurrentAccount(app);
            return true;
        });
    }

    public void open(Context source) {
        Context context = source != null ? source : appContext;
        if (context == null) return;
        Intent intent = new Intent(context, FeedTimelineActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
