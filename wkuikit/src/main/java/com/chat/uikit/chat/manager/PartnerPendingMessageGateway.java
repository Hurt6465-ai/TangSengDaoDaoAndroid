package com.chat.uikit.chat.manager;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.IRequestResultListener;
import com.chat.uikit.partner.PartnerPendingStore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pending 发起人的第 2、3 条消息统一走业务后端网关。
 * 所有内容类型都从 WKSendMsgUtils 的总入口进入，避免图片/语音/视频绕过限制。
 */
final class PartnerPendingMessageGateway {
    private static final PartnerPendingMessageGateway INSTANCE = new PartnerPendingMessageGateway();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private PartnerPendingMessageGateway() {}

    static PartnerPendingMessageGateway getInstance() {
        return INSTANCE;
    }

    /** @return true 表示消息已被 Pending 网关接管，调用方不能再直接发给悟空 IM。 */
    boolean handle(WKMsg wkMsg) {
        if (wkMsg == null || wkMsg.baseContentMsgModel == null || wkMsg.channelType != WKChannelType.PERSONAL) {
            return false;
        }
        PartnerPendingStore.Entry state = PartnerPendingStore.get(wkMsg.channelID);
        if (state == null || !state.pending) return false;

        // 接收方的第一条回复仍走悟空 SDK；服务端 Webhook 会把关系激活。
        if (!state.requester) {
            PartnerPendingStore.markActive(wkMsg.channelID);
            return false;
        }
        // 已观察到对方回复时，不能再被旧的 3 条计数挡住。下一条继续走
        // REST 网关，由后端返回的 contact_status 完成最终状态校准。
        if (state.messageCount >= state.maxMessageCount && !state.replyObserved) {
            toast("对方回复前最多发送3条消息");
            return true;
        }
        if (!isSupported(wkMsg.type)) {
            toast("对方回复前暂不支持发送这种消息");
            return true;
        }

        if (needsUpload(wkMsg)) {
            WKSendMsgUtils.getInstance().uploadChatAttachment(wkMsg, (success, content) -> {
                if (!success) {
                    toast("附件上传失败，请重试");
                    return;
                }
                wkMsg.baseContentMsgModel = content;
                sendThroughGateway(wkMsg, stableClientNo(wkMsg), 0);
            });
        } else {
            sendThroughGateway(wkMsg, stableClientNo(wkMsg), 0);
        }
        return true;
    }

    private void sendThroughGateway(WKMsg wkMsg, String clientMsgNo, int retry) {
        Map<String, Object> payload = payload(wkMsg);
        if (payload == null) {
            toast("消息格式错误，发送失败");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("token", WKConfig.getInstance().getToken());
        body.put("receive_channel_id", wkMsg.channelID);
        body.put("receive_channel_type", (int) wkMsg.channelType);
        body.put("client_msg_no", clientMsgNo);
        body.put("payload", payload);

        PartnerPendingMessageModel.getInstance().send(body, new IRequestResultListener<>() {
            @Override
            public void onSuccess(PartnerPendingMessageResponse result) {
                if (result == null || !result.success()) {
                    String message = result == null ? "消息发送失败" : result.messageSafe();
                    handleFailure(wkMsg, clientMsgNo, retry, 0, message);
                    return;
                }

                // The REST endpoint sends on behalf of this device, so the normal SDK
                // send pipeline never creates a local bubble. Persist one successful
                // outgoing copy using the IM client_msg_no returned by the backend.
                saveOutgoingCopy(wkMsg, result, clientMsgNo);

                if (result.hasRelationshipState() && result.isActiveRelationship()) {
                    PartnerPendingStore.markActive(wkMsg.channelID);
                    return;
                }

                int max = result.max_message_count > 0 ? result.max_message_count : 3;
                int count = Math.max(1, result.requester_msg_count);
                PartnerPendingStore.updateRequesterCount(wkMsg.channelID, count, max);
                int remaining = Math.max(0, max - count);
                if (remaining == 0) toast("已发送，等待对方回复后可继续聊天");
                else toast("已发送，对方回复前还可发送" + remaining + "条");
            }

            @Override
            public void onFail(int code, String msg) {
                handleFailure(wkMsg, clientMsgNo, retry, code, msg);
            }
        });
    }

    private void handleFailure(WKMsg wkMsg, String clientMsgNo, int retry, int code, String msg) {
        String value = msg == null ? "" : msg;
        boolean limit = code == 429 || value.contains("最多") || value.contains("3条") || value.contains("上限");
        PartnerPendingStore.Entry latest = PartnerPendingStore.get(wkMsg.channelID);

        // The incoming reply may beat the server webhook by a few hundred
        // milliseconds. Retry the same idempotency key instead of re-locking the UI.
        boolean activationLag = limit && latest != null && latest.replyObserved && retry < 4;
        if (activationLag) {
            long delay = 650L + retry * 550L;
            main.postDelayed(() -> sendThroughGateway(wkMsg, clientMsgNo, retry + 1), delay);
            return;
        }

        if (limit) {
            PartnerPendingStore.updateRequesterCount(wkMsg.channelID, 3, 3);
            toast(TextUtils.isEmpty(value) ? "对方回复前最多发送3条消息" : value);
            return;
        }

        // 结果未知或短暂网络错误，用相同 client_msg_no 自动重试，后端和悟空 IM 都可幂等。
        boolean retryable = retry < 3 && (code <= 0 || code >= 500
                || value.contains("确认中") || value.contains("正在发送")
                || value.contains("重试") || value.contains("网络") || value.contains("超时"));
        if (retryable) {
            main.postDelayed(() -> sendThroughGateway(wkMsg, clientMsgNo, retry + 1), 1800L + retry * 1200L);
            return;
        }
        toast(TextUtils.isEmpty(value) ? "消息发送失败，请稍后重试" : value);
    }

    private void saveOutgoingCopy(WKMsg wkMsg, PartnerPendingMessageResponse result, String businessClientNo) {
        try {
            JSONObject json = wkMsg.baseContentMsgModel.encodeMsg();
            if (json == null) json = new JSONObject();
            json.put("type", wkMsg.type);

            wkMsg.content = json.toString();
            wkMsg.fromUID = WKConfig.getInstance().getUid();
            wkMsg.clientMsgNO = !TextUtils.isEmpty(result.im_client_msg_no)
                    ? result.im_client_msg_no
                    : (!TextUtils.isEmpty(result.client_msg_no) ? result.client_msg_no : businessClientNo);
            if (!TextUtils.isEmpty(result.message_id)) wkMsg.messageID = result.message_id;
            wkMsg.messageSeq = Math.max(0, result.message_seq);
            long serverTimestamp = result.timestamp;
            if (serverTimestamp > 100000000000L) serverTimestamp /= 1000L;
            wkMsg.timestamp = serverTimestamp > 0 ? serverTimestamp : System.currentTimeMillis() / 1000L;
            wkMsg.status = WKSendMsgResult.send_success;
            long maxOrderSeq = WKIM.getInstance().getMsgManager()
                    .getMaxOrderSeqWithChannel(wkMsg.channelID, wkMsg.channelType);
            wkMsg.orderSeq = Math.max(wkMsg.orderSeq, maxOrderSeq + 1L);
            WKIM.getInstance().getMsgManager().saveAndUpdateConversationMsg(wkMsg, false);
        } catch (Throwable ignored) {
            // The peer already received the message. A local persistence failure must
            // never retry transmission with a new id and create a duplicate message.
        }
    }

    private Map<String, Object> payload(WKMsg wkMsg) {
        try {
            JSONObject json = wkMsg.baseContentMsgModel.encodeMsg();
            if (json == null) json = new JSONObject();
            json.put("type", wkMsg.type);
            Map<String, Object> map = gson.fromJson(json.toString(), MAP_TYPE);
            return map == null ? new HashMap<>() : map;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String stableClientNo(WKMsg wkMsg) {
        if (!TextUtils.isEmpty(wkMsg.clientMsgNO)) return wkMsg.clientMsgNO;
        String uid = WKConfig.getInstance().getUid();
        String value = "partner-android:" + (TextUtils.isEmpty(uid) ? "unknown" : uid) + ":" + UUID.randomUUID();
        wkMsg.clientMsgNO = value;
        return value;
    }

    private boolean needsUpload(WKMsg msg) {
        if (msg.type == WKContentType.WK_VIDEO && msg.baseContentMsgModel instanceof WKVideoContent) {
            WKVideoContent video = (WKVideoContent) msg.baseContentMsgModel;
            return TextUtils.isEmpty(video.url) || TextUtils.isEmpty(video.cover);
        }
        if (msg.baseContentMsgModel instanceof WKMediaMessageContent) {
            return TextUtils.isEmpty(((WKMediaMessageContent) msg.baseContentMsgModel).url);
        }
        return false;
    }

    private boolean isSupported(int type) {
        return type == WKContentType.WK_TEXT
                || type == WKContentType.WK_IMAGE
                || type == WKContentType.WK_GIF
                || type == WKContentType.WK_VOICE
                || type == WKContentType.WK_VIDEO
                || type == WKContentType.WK_FILE
                || type == WKContentType.WK_LOCATION
                || type == WKContentType.WK_CARD;
    }

    private void toast(String value) {
        main.post(() -> {
            try {
                Toast.makeText(WKBaseApplication.getInstance().getContext(), value, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        });
    }
}
