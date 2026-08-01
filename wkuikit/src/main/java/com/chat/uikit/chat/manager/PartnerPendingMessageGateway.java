package com.chat.uikit.chat.manager;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.IRequestResultListener;
import com.chat.uikit.partner.PartnerLocalMessageStore;
import com.chat.uikit.partner.PartnerPendingStore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pending 关系中的发起方第 2、3 条消息和接收方第一条回复统一走业务后端网关。
 * 所有内容类型都从 WKSendMsgUtils 的总入口进入，避免图片/语音/视频绕过限制，
 * 并由服务端原子完成计数、幂等投递和关系激活。
 */
final class PartnerPendingMessageGateway {
    private static final PartnerPendingMessageGateway INSTANCE = new PartnerPendingMessageGateway();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final Object queueLock = new Object();
    private final Map<String, ArrayDeque<PendingSend>> sendQueues = new HashMap<>();

    private static final class PendingSend {
        final WKMsg msg;
        final String ownerUid;
        final String queueKey;
        final String clientMsgNo;
        final boolean requester;

        PendingSend(WKMsg msg, String ownerUid, String queueKey,
                    String clientMsgNo, boolean requester) {
            this.msg = msg;
            this.ownerUid = ownerUid;
            this.queueKey = queueKey;
            this.clientMsgNo = clientMsgNo;
            this.requester = requester;
        }
    }

    private PartnerPendingMessageGateway() {}

    static PartnerPendingMessageGateway getInstance() {
        return INSTANCE;
    }

    /** @return true 表示消息已被 Pending 网关接管，调用方不能再直接发给悟空 IM。 */
    boolean handle(WKMsg wkMsg) {
        if (wkMsg == null || wkMsg.baseContentMsgModel == null
                || wkMsg.channelType != WKChannelType.PERSONAL) {
            return false;
        }
        PartnerPendingStore.Entry state = PartnerPendingStore.get(wkMsg.channelID);
        if (state == null || !state.pending) return false;
        if (!isSupported(wkMsg.type)) {
            toast("临时会话暂不支持发送这种消息");
            return true;
        }

        String ownerUid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(ownerUid)) {
            toast("登录状态已失效，请重新登录");
            return true;
        }
        String queueKey = ownerUid + '\u0000' + wkMsg.channelID;
        PendingSend task = new PendingSend(
                wkMsg, ownerUid, queueKey, stableClientNo(wkMsg, ownerUid), state.requester);
        boolean startNow;
        synchronized (queueLock) {
            ArrayDeque<PendingSend> queue = sendQueues.get(queueKey);
            int queuedCount = queue == null ? 0 : queue.size();
            // 只有发起方受 3 条限制。把已排队但尚未回包的消息也算进去，既允许
            // 第 2、3 条连续发送，又阻止第 4 条穿透本地限制。
            if (state.requester && !state.replyObserved
                    && state.messageCount + queuedCount >= state.maxMessageCount) {
                toast("对方回复前最多发送3条消息");
                return true;
            }
            if (queue == null) {
                queue = new ArrayDeque<>();
                sendQueues.put(queueKey, queue);
            }
            queue.addLast(task);
            startNow = queue.size() == 1;
        }
        if (startNow) startTask(task);
        return true;
    }

    private void startTask(PendingSend task) {
        if (task == null) return;
        if (!isSameAccount(task.ownerUid)) {
            complete(task);
            return;
        }
        if (needsUpload(task.msg)) {
            WKSendMsgUtils.getInstance().uploadChatAttachment(task.msg, (success, content) -> {
                if (!success) {
                    toast("附件上传失败，请重试");
                    complete(task);
                    return;
                }
                if (!isSameAccount(task.ownerUid)) {
                    complete(task);
                    return;
                }
                task.msg.baseContentMsgModel = content;
                sendThroughGateway(task, 0);
            });
        } else {
            sendThroughGateway(task, 0);
        }
    }

    private void sendThroughGateway(PendingSend task, int retry) {
        if (task == null) return;
        if (!isSameAccount(task.ownerUid)) {
            complete(task);
            return;
        }
        Map<String, Object> payload = payload(task.msg);
        if (payload == null) {
            toast("消息格式错误，发送失败");
            complete(task);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("token", WKConfig.getInstance().getToken());
        body.put("receive_channel_id", task.msg.channelID);
        body.put("receive_channel_type", (int) task.msg.channelType);
        body.put("client_msg_no", task.clientMsgNo);
        body.put("payload", payload);

        try {
            PartnerPendingMessageModel.getInstance().send(body, new IRequestResultListener<>() {
                @Override
                public void onSuccess(PartnerPendingMessageResponse result) {
                    if (!isSameAccount(task.ownerUid)) {
                        complete(task);
                        return;
                    }
                    if (result == null || !result.success()) {
                        String message = result == null ? "消息发送失败" : result.messageSafe();
                        int businessCode = result == null ? 0
                                : (result.status != 0 ? result.status : result.code);
                        handleFailure(task, retry, businessCode, message);
                        return;
                    }

                    try {
                        // REST 代表本设备完成投递，普通 SDK 发送流程不会自动创建本地气泡。
                        // 使用后端返回的 IM client_msg_no 落库，并显式通知当前聊天页补插。
                        saveOutgoingCopy(task.msg, result, task.clientMsgNo, task.ownerUid);

                        if (result.hasRelationshipState() && result.isActiveRelationship()) {
                            PartnerPendingStore.markActive(task.msg.channelID);
                        } else if (!task.requester) {
                            // The receiver's reply was delivered, but the activation transaction
                            // may still be waiting for the server webhook/outbox. Preserve the
                            // receiver role instead of incorrectly turning this account into the
                            // capped requester.
                            int max = result.max_message_count > 0 ? result.max_message_count : 3;
                            int count = Math.max(1, result.requester_msg_count);
                            PartnerPendingStore.markReceiver(task.msg.channelID, count, max);
                        } else {
                            int max = result.max_message_count > 0 ? result.max_message_count : 3;
                            int count = Math.max(1, result.requester_msg_count);
                            PartnerPendingStore.updateRequesterCount(task.msg.channelID, count, max);
                            int remaining = Math.max(0, max - count);
                            if (remaining == 0) toast("已发送，等待对方回复后可继续聊天");
                            else toast("已发送，对方回复前还可发送" + remaining + "条");
                        }
                    } finally {
                        complete(task);
                    }
                }

                @Override
                public void onFail(int code, String msg) {
                    handleFailure(task, retry, code, msg);
                }
            });
        } catch (Throwable throwable) {
            handleFailure(task, retry, 0, throwable.getMessage());
        }
    }

    private void handleFailure(PendingSend task, int retry, int code, String msg) {
        if (task == null) return;
        if (!isSameAccount(task.ownerUid)) {
            complete(task);
            return;
        }
        String value = msg == null ? "" : msg;
        boolean limit = code == 429 || value.contains("最多")
                || value.contains("3条") || value.contains("上限");
        PartnerPendingStore.Entry latest = PartnerPendingStore.get(task.msg.channelID);

        // 对方回复已到本机，但关系激活/白名单事件可能慢几百毫秒。使用相同幂等键重试，
        // 不创建新消息，也不提前把本地关系标为 active。
        boolean activationLag = limit && latest != null && latest.requester
                && latest.replyObserved && retry < 4;
        if (activationLag) {
            long delay = 650L + retry * 550L;
            main.postDelayed(() -> sendThroughGateway(task, retry + 1), delay);
            return;
        }

        if (limit) {
            if (latest != null && latest.requester) {
                int max = Math.max(1, latest.maxMessageCount);
                PartnerPendingStore.updateRequesterCount(task.msg.channelID, max, max);
            }
            toast(TextUtils.isEmpty(value) ? "对方回复前最多发送3条消息" : value);
            complete(task);
            return;
        }

        // 结果未知或短暂网络错误，用相同 client_msg_no 自动重试，后端和悟空 IM 都幂等。
        boolean retryable = retry < 3 && (code <= 0 || code >= 500
                || value.contains("确认中") || value.contains("正在发送")
                || value.contains("重试") || value.contains("网络") || value.contains("超时"));
        if (retryable) {
            main.postDelayed(() -> sendThroughGateway(task, retry + 1),
                    1800L + retry * 1200L);
            return;
        }
        toast(TextUtils.isEmpty(value) ? "消息发送失败，请稍后重试" : value);
        complete(task);
    }

    private void complete(PendingSend task) {
        PendingSend next = null;
        synchronized (queueLock) {
            ArrayDeque<PendingSend> queue = sendQueues.get(task.queueKey);
            if (queue == null) return;
            boolean removed;
            if (queue.peekFirst() == task) {
                queue.removeFirst();
                removed = true;
            } else {
                removed = queue.remove(task);
            }
            // Network libraries should callback once, but make completion idempotent so
            // a duplicate callback can never start the next queued message twice.
            if (!removed) return;
            if (queue.isEmpty()) {
                sendQueues.remove(task.queueKey);
            } else {
                next = queue.peekFirst();
            }
        }
        if (next != null) {
            PendingSend finalNext = next;
            main.post(() -> startTask(finalNext));
        }
    }

    private void saveOutgoingCopy(WKMsg wkMsg,
                                  PartnerPendingMessageResponse result,
                                  String businessClientNo,
                                  String ownerUid) {
        try {
            JSONObject json = wkMsg.baseContentMsgModel.encodeMsg();
            if (json == null) json = new JSONObject();
            json.put("type", wkMsg.type);

            wkMsg.content = json.toString();
            wkMsg.fromUID = ownerUid;
            wkMsg.clientMsgNO = !TextUtils.isEmpty(result.im_client_msg_no)
                    ? result.im_client_msg_no
                    : (!TextUtils.isEmpty(result.client_msg_no)
                    ? result.client_msg_no : businessClientNo);
            if (!TextUtils.isEmpty(result.message_id)) wkMsg.messageID = result.message_id;
            wkMsg.messageSeq = Math.max(0, result.message_seq);
            long serverTimestamp = result.timestamp;
            if (serverTimestamp > 100000000000L) serverTimestamp /= 1000L;
            wkMsg.timestamp = serverTimestamp > 0
                    ? serverTimestamp : System.currentTimeMillis() / 1000L;
            wkMsg.status = WKSendMsgResult.send_success;
            PartnerLocalMessageStore.saveAndNotify(wkMsg);
        } catch (Throwable ignored) {
            // The peer already received the message. Never retransmit with a new ID.
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

    private String stableClientNo(WKMsg wkMsg, String ownerUid) {
        if (!TextUtils.isEmpty(wkMsg.clientMsgNO)) return wkMsg.clientMsgNO;
        String value = "partner-android:" + ownerUid + ":" + UUID.randomUUID();
        wkMsg.clientMsgNO = value;
        return value;
    }

    private boolean isSameAccount(String ownerUid) {
        return !TextUtils.isEmpty(ownerUid)
                && TextUtils.equals(ownerUid, WKConfig.getInstance().getUid());
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
                Toast.makeText(WKBaseApplication.getInstance().getContext(),
                        value, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        });
    }
}
