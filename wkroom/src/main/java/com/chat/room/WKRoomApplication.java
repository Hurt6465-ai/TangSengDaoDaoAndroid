package com.chat.room;

import android.content.Context;

import com.chat.base.endpoint.EndpointManager;
import com.chat.room.ui.RoomTopicListFragment;

import java.lang.ref.WeakReference;

/** 语伴话题聊天室模块入口。 */
public class WKRoomApplication {
    private static final WKRoomApplication INSTANCE = new WKRoomApplication();
    private WeakReference<Context> contextRef;
    private boolean inited;

    public static WKRoomApplication getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        if (inited) return;
        inited = true;
        contextRef = new WeakReference<>(context.getApplicationContext());
        EndpointManager.getInstance().setMethod(RoomEndpointSID.topicRoomFragment, object -> RoomTopicListFragment.newInstance());
    }

    public Context getContext() {
        return contextRef == null ? null : contextRef.get();
    }
}
