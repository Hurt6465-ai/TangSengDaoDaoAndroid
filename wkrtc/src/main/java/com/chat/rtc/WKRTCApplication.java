package com.chat.rtc;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.CreateVideoCallMenu;
import com.chat.base.endpoint.entity.MsgConfig;
import com.chat.base.endpoint.entity.RTCMenu;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.rtc.model.RtcSignal;
import com.chat.rtc.model.RtcSignalContent;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;

import java.lang.ref.WeakReference;

public class WKRTCApplication {
    private static final WKRTCApplication INSTANCE = new WKRTCApplication();

    private WeakReference<Application> appRef = new WeakReference<>(null);
    private boolean endpointsRegistered = false;
    private boolean signalListenerAdded = false;
    private boolean messageArtifactsRegistered = false;
    private boolean lifecycleRegistered = false;
    private int startedActivities = 0;

    private WKRTCApplication() {}

    public static WKRTCApplication getInstance() {
        return INSTANCE;
    }

    public void init(Application app) {
        appRef = new WeakReference<>(app);
        registerLifecycleCallbacks(app);
        registerRtcMessageArtifacts();
        registerEndpoints();
        initRtcSignalModule();
        if (app != null) {
            RtcCallNotification.ensureChannel(app);
            RtcConfigManager.refreshAsync();
        }
    }

    public Context getContext() {
        Application app = appRef.get();
        return app == null ? null : app.getApplicationContext();
    }

    public boolean isAppInForeground() {
        return startedActivities > 0;
    }

    private void registerLifecycleCallbacks(Application app) {
        if (app == null || lifecycleRegistered) return;
        lifecycleRegistered = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(Activity activity) { startedActivities++; }
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) { if (startedActivities > 0) startedActivities--; }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private void registerRtcMessageArtifacts() {
        if (messageArtifactsRegistered) return;
        messageArtifactsRegistered = true;
        try {
            WKIM.getInstance().getMsgManager().registerContentMsg(RtcSignalContent.class);
        } catch (Exception ignored) {
        }
        try {
            WKIM.getInstance().getMsgManager().registerContentMsg(RtcCallRecordContent.class);
        } catch (Exception ignored) {
        }
        try {
            WKMsgItemViewManager.getInstance().addChatItemViewProvider(
                    RtcConstants.CONTENT_TYPE_CALL_RECORD,
                    new RtcCallRecordProvider()
            );
        } catch (Exception ignored) {
        }
        try {
            EndpointManager.getInstance().setMethod(
                    EndpointCategory.msgConfig + RtcConstants.CONTENT_TYPE_CALL_RECORD,
                    object -> new MsgConfig(false, false, false, false, false, false)
            );
        } catch (Exception ignored) {
        }
    }

    public void initRtcSignalModule() {
        registerRtcMessageArtifacts();
        Context context = getContext();
        String uid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(uid)) {
            RtcCallManager.get().configure(context, uid, new RtcWukongSignalTransport());
            RtcConfigManager.refreshAsync();
        }
        if (signalListenerAdded) return;
        signalListenerAdded = true;
        try {
            WKIM.getInstance().getMsgManager().addOnNewMsgListener("wkrtc_global_signal", list -> {
                String currentUid = WKConfig.getInstance().getUid();
                if (TextUtils.isEmpty(currentUid) || list == null || list.isEmpty()) return;
                RtcCallManager.get().configure(getContext(), currentUid, new RtcWukongSignalTransport());
                for (WKMsg msg : list) {
                    RtcSignalManager.get().tryHandleIncomingMsg(msg);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void registerEndpoints() {
        if (endpointsRegistered) return;
        endpointsRegistered = true;

        EndpointManager.getInstance().setMethod("rtc_init", object -> {
            initRtcSignalModule();
            return true;
        });

        EndpointManager.getInstance().setMethod("is_register_rtc", object -> true);

        EndpointManager.getInstance().setMethod("rtc_is_calling", object -> RtcCallManager.get().isCalling());

        EndpointManager.getInstance().setMethod("rtc_notification_ready", object -> RtcPermissionHelper.isIncomingCallNotificationReady(getContext()));

        EndpointManager.getInstance().setMethod("rtc_has_notification_permission", object -> RtcPermissionHelper.hasPostNotificationPermission(getContext()));

        EndpointManager.getInstance().setMethod("rtc_can_use_full_screen_intent", object -> RtcPermissionHelper.canUseFullScreenIntent(getContext()));

        EndpointManager.getInstance().setMethod("rtc_open_notification_settings", object -> {
            RtcPermissionHelper.openNotificationSettings(getContext());
            return true;
        });

        EndpointManager.getInstance().setMethod("rtc_open_full_screen_intent_settings", object -> {
            RtcPermissionHelper.openFullScreenIntentSettings(getContext());
            return true;
        });

        EndpointManager.getInstance().setMethod("rtc_is_signal_msg", object -> {
            if (object instanceof WKMsg) {
                return RtcSignalManager.isSignalMsg((WKMsg) object);
            }
            return false;
        });

        EndpointManager.getInstance().setMethod("rtc_handle_signal_msg", object -> {
            if (object instanceof WKMsg) {
                return RtcSignalManager.get().tryHandleIncomingMsg((WKMsg) object);
            }
            return false;
        });

        EndpointManager.getInstance().setMethod("wk_p2p_call", object -> {
            initRtcSignalModule();
            if (!(object instanceof RTCMenu)) return false;
            RTCMenu menu = (RTCMenu) object;
            IConversationContext ctx = menu.iConversationContext;
            if (ctx == null || ctx.getChatActivity() == null) return false;
            WKChannel channel = ctx.getChatChannelInfo();
            if (channel == null || TextUtils.isEmpty(channel.channelID)) return false;
            if (channel.channelType != WKChannelType.PERSONAL) {
                Toast.makeText(ctx.getChatActivity(), "群通话后续接 SFU/语音房，这个 P2P 插件先只支持单聊", Toast.LENGTH_SHORT).show();
                return false;
            }
            String name = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
            if (TextUtils.isEmpty(name)) name = ctx.getChatActivity().getString(R.string.rtc_friend);
            RtcCallManager.get().startOutgoing(ctx.getChatActivity(), channel.channelID, name, channel.avatar, menu.callType);
            return true;
        });

        EndpointManager.getInstance().setMethod("create_video_call", object -> {
            if (object instanceof CreateVideoCallMenu) {
                CreateVideoCallMenu menu = (CreateVideoCallMenu) object;
                if (menu.activity != null) {
                    Toast.makeText(menu.activity, "当前 wkrtc 第一版只抽出并增强 1v1 P2P 通话，群视频需要后端房间/SFU 后再接", Toast.LENGTH_SHORT).show();
                }
            }
            return false;
        });
    }
}
