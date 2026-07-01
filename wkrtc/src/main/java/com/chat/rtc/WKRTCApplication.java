package com.chat.rtc;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.CreateVideoCallMenu;
import com.chat.base.endpoint.entity.RTCMenu;
import com.chat.base.msg.IConversationContext;
import com.chat.rtc.model.RtcSignal;
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

    private WKRTCApplication() {}

    public static WKRTCApplication getInstance() {
        return INSTANCE;
    }

    public void init(Application app) {
        appRef = new WeakReference<>(app);
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

    public void initRtcSignalModule() {
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
            // The app may have registered endpoints before login, when uid was still empty.
            // Reconfigure here before consuming any RTC packet; otherwise a valid invite can be
            // hidden from chat but not delivered to RtcCallManager.
            initRtcSignalModule();
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
