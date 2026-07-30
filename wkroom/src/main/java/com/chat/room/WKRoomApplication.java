package com.chat.room;

import android.content.Context;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.net.IRequestResultListener;
import com.chat.room.entity.RoomTopicEntity;
import com.chat.room.model.RoomTopicModel;
import com.chat.room.ui.RoomTopicListFragment;
import com.xinbida.wukongim.entity.WKChannel;

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
        EndpointManager.getInstance().setMethod(EndpointSID.topicRoomMarkRead, object -> {
            if (!(object instanceof WKChannel)) return false;
            WKChannel channel = (WKChannel) object;
            if (channel.channelID == null || channel.channelID.trim().isEmpty()) return false;
            RoomTopicModel.getInstance().readRoom(
                    channel.channelID,
                    channel.channelID,
                    channel.channelType,
                    new IRequestResultListener<RoomTopicEntity>() {
                        @Override
                        public void onSuccess(RoomTopicEntity result) {
                            // 后端已更新 last_read_at；聊天页无需等待该请求完成。
                        }

                        @Override
                        public void onFail(int code, String msg) {
                            // 410 代表话题已结束，聊天页的删除 CMD/过期检查会负责退出。
                        }
                    }
            );
            return true;
        });
    }

    public Context getContext() {
        return contextRef == null ? null : contextRef.get();
    }
}
