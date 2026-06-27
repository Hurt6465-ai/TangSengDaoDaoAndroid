package com.chat.feed;

import android.content.Context;

import com.chat.base.endpoint.EndpointManager;

public class WKFeedApplication {
    private static final WKFeedApplication INSTANCE = new WKFeedApplication();
    private Context appContext;

    private WKFeedApplication() {}

    public static WKFeedApplication getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        if (context != null) appContext = context.getApplicationContext();
        registerEndpoints();
    }

    private void registerEndpoints() {
        EndpointManager.getInstance().setMethod("peipe_open_feed_discover", object -> {
            Context context = object instanceof Context ? (Context) object : appContext;
            if (context == null) return false;
            FeedRoute.openDiscover(context);
            return true;
        });
        EndpointManager.getInstance().setMethod("feed_open_discover", object -> {
            Context context = object instanceof Context ? (Context) object : appContext;
            if (context == null) return false;
            FeedRoute.openDiscover(context);
            return true;
        });
        EndpointManager.getInstance().setMethod("feed_open_publish", object -> {
            Context context = object instanceof Context ? (Context) object : appContext;
            if (context == null) return false;
            FeedRoute.openPublish(context);
            return true;
        });
    }
}
