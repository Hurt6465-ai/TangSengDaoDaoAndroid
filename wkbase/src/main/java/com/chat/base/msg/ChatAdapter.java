package com.chat.base.msg;

import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseProviderMultiAdapter;
import com.chad.library.adapter.base.provider.BaseItemProvider;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.R;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ShowMsgReactionMenu;
import com.chat.base.msgitem.WKChatBaseProvider;
import com.chat.base.msgitem.WKChatIteMsgFromType;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.SecretDeleteTimer;
import com.chat.base.utils.WKReader;
import com.chat.base.views.ChatItemView;
import com.chat.base.views.pinnedsectionitemdecoration.utils.FullSpanUtil;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgReaction;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2020-08-03 13:46
 * 消息适配器
 */
public class ChatAdapter extends BaseProviderMultiAdapter<WKUIChatMsgItemEntity> {
    private final IConversationContext iConversationContext;
    private final Map<String, Integer> clientMsgNoIndex = new HashMap<>();
    private final Map<String, Integer> messageIdIndex = new HashMap<>();
    private int indexedSize = -1;
    private WKUIChatMsgItemEntity indexedFirstItem;
    private WKUIChatMsgItemEntity indexedLastItem;

    public enum AdapterType {
        normalMessage, pinnedMessage
    }


    @Override
    public void onAttachedToRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        FullSpanUtil.onAttachedToRecyclerView(recyclerView, this, WKContentType.msgPromptTime);
    }

    @Override
    public void onViewAttachedToWindow(@NotNull BaseViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        FullSpanUtil.onViewAttachedToWindow(holder, this, WKContentType.msgPromptTime);
    }

    private final AdapterType adapterType;

    ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> getItemProviderList() {
        return adapterType == AdapterType.normalMessage ? WKMsgItemViewManager.getInstance().getChatItemProviderList() : WKMsgItemViewManager.getInstance().getPinnedChatItemProviderList();
    }

    public ChatAdapter(@NonNull IConversationContext iConversationContext, AdapterType adapterType) {
        super();
        this.adapterType = adapterType;
        this.iConversationContext = iConversationContext;
        ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> list = getItemProviderList();
        for (int type : list.keySet()) {
            addItemProvider(Objects.requireNonNull(list.get(type)));
        }
    }

    @Override
    protected int getItemType(@NotNull List<? extends WKUIChatMsgItemEntity> list, int i) {
        if (list.get(i).wkMsg.remoteExtra != null && list.get(i).wkMsg.remoteExtra.revoke == 1) {
            //撤回消息
            return WKContentType.revoke;
        }
        if (getItemProviderList().containsKey(list.get(i).wkMsg.type)) {
            return list.get(i).wkMsg.type;
        }
        if (list.get(i).wkMsg.type >= 1000 && list.get(i).wkMsg.type <= 2000) {
            //系统消息
            return WKContentType.systemMsg;
        }
        return WKContentType.unknown_msg;
    }

    public long getLastTimeMsg() {
        long timestamp = 0;
        for (int i = getData().size() - 1; i >= 0; i--) {
            if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.timestamp > 0) {
                timestamp = getData().get(i).wkMsg.timestamp;
                break;
            }
        }
        return timestamp;
    }


    public IConversationContext getConversationContext() {
        return iConversationContext;
    }

    //显示多选
    public void showMultipleChoice() {
        iConversationContext.showMultipleChoice();
    }

    public void hideSoftKeyboard() {
        iConversationContext.hideSoftKeyboard();
    }

    //回复某条消息
    public void replyMsg(WKMsg wkMsg) {
        iConversationContext.showReply(wkMsg);
    }

    public void showTitleRightText(String content) {
        iConversationContext.setTitleRightText(content);
    }

    //提示某条消息
    public void showTipsMsg(String clientMsgNo) {
        iConversationContext.tipsMsg(clientMsgNo);
    }

    //设置输入框内容
    public void setEditContent(String content) {
        iConversationContext.setEditContent(content);
    }

    //是否存在某条消息。尾部新增采用增量索引；旧位置命中后仍校验实体，
    //避免状态更新或列表删改让过期索引产生错误结果。
    public boolean isExist(String clientMsgNo, String messageId) {
        return findMessagePosition(clientMsgNo, messageId) >= 0;
    }

    public int findMessagePosition(String clientMsgNo, String messageId) {
        if (TextUtils.isEmpty(clientMsgNo) && TextUtils.isEmpty(messageId)) return -1;
        ensureMessageIndex();

        boolean staleIndexHit = false;
        if (!TextUtils.isEmpty(messageId)) {
            Integer position = messageIdIndex.get(messageId);
            if (isIndexedMessageIdMatch(position, messageId)) return position;
            staleIndexHit = position != null;
        }
        if (!TextUtils.isEmpty(clientMsgNo)) {
            Integer position = clientMsgNoIndex.get(clientMsgNo);
            if (isIndexedClientMsgNoMatch(position, clientMsgNo)) return position;
            staleIndexHit = staleIndexHit || position != null;
        }

        // 命中旧位置但实体已变化时重建一次再确认。普通“收到一条新消息”的未命中
        // 直接返回，避免每条新消息仍然全量扫描而退化回 O(n²)。
        if (staleIndexHit) {
            rebuildMessageIndex();
            if (!TextUtils.isEmpty(messageId)) {
                Integer position = messageIdIndex.get(messageId);
                if (isIndexedMessageIdMatch(position, messageId)) return position;
            }
            if (!TextUtils.isEmpty(clientMsgNo)) {
                Integer position = clientMsgNoIndex.get(clientMsgNo);
                if (isIndexedClientMsgNoMatch(position, clientMsgNo)) return position;
            }
        }

        // 少数旧消息或插件消息可能没有 clientMsgNO。仅在这种非常规情况下做一次
        // 线性兜底，既保证 messageID 查重正确，也不让普通实时消息退化回 O(n²)。
        if (TextUtils.isEmpty(clientMsgNo) && !TextUtils.isEmpty(messageId)) {
            for (int i = 0, size = getData().size(); i < size; i++) {
                WKUIChatMsgItemEntity entity = getData().get(i);
                WKMsg msg = entity == null ? null : entity.wkMsg;
                if (msg != null && TextUtils.equals(messageId, msg.messageID)) {
                    rebuildMessageIndex();
                    return i;
                }
            }
        }
        return -1;
    }

    private void ensureMessageIndex() {
        List<WKUIChatMsgItemEntity> data = getData();
        int size = data.size();
        WKUIChatMsgItemEntity first = size == 0 ? null : data.get(0);
        WKUIChatMsgItemEntity last = size == 0 ? null : data.get(size - 1);

        if (indexedSize < 0) {
            rebuildMessageIndex();
            return;
        }
        if (indexedSize == size) {
            if (indexedFirstItem != first || indexedLastItem != last) rebuildMessageIndex();
            return;
        }

        // 实时消息最常见的是尾部逐条追加。只索引新增区间，避免每收到一条消息
        // 都重扫整份聊天记录，批量收消息时才能真正保持接近 O(n)。
        if (size > indexedSize && indexedSize > 0
                && indexedFirstItem == first
                && data.get(indexedSize - 1) == indexedLastItem) {
            indexMessageRange(data, indexedSize, size);
            indexedSize = size;
            indexedFirstItem = first;
            indexedLastItem = last;
            return;
        }
        rebuildMessageIndex();
    }

    private void indexMessageRange(List<WKUIChatMsgItemEntity> data, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(end, data.size());
        for (int i = safeStart; i < safeEnd; i++) {
            WKUIChatMsgItemEntity entity = data.get(i);
            WKMsg msg = entity == null ? null : entity.wkMsg;
            if (msg == null) continue;
            if (!TextUtils.isEmpty(msg.clientMsgNO)) clientMsgNoIndex.put(msg.clientMsgNO, i);
            if (!TextUtils.isEmpty(msg.messageID)) messageIdIndex.put(msg.messageID, i);
        }
    }

    private void rebuildMessageIndex() {
        clientMsgNoIndex.clear();
        messageIdIndex.clear();
        List<WKUIChatMsgItemEntity> data = getData();
        indexMessageRange(data, 0, data.size());
        indexedSize = data.size();
        indexedFirstItem = indexedSize == 0 ? null : data.get(0);
        indexedLastItem = indexedSize == 0 ? null : data.get(indexedSize - 1);
    }

    private WKMsg getIndexedMessage(Integer position) {
        if (position == null || position < 0 || position >= getData().size()) return null;
        WKUIChatMsgItemEntity entity = getData().get(position);
        return entity == null ? null : entity.wkMsg;
    }

    private boolean isIndexedMessageIdMatch(Integer position, String messageId) {
        WKMsg msg = getIndexedMessage(position);
        return msg != null && !TextUtils.isEmpty(messageId)
                && TextUtils.equals(messageId, msg.messageID);
    }

    private boolean isIndexedClientMsgNoMatch(Integer position, String clientMsgNo) {
        WKMsg msg = getIndexedMessage(position);
        return msg != null && !TextUtils.isEmpty(clientMsgNo)
                && TextUtils.equals(clientMsgNo, msg.clientMsgNO);
    }


    //获取最后一条消息
    public WKMsg getLastMsg() {
        WKMsg wkMsg = null;
        for (int i = getData().size() - 1; i >= 0; i--) {
            if (getData().get(i).wkMsg != null
                    && getData().get(i).wkMsg.type != WKContentType.msgPromptNewMsg
                    && getData().get(i).wkMsg.type != WKContentType.typing) {
                wkMsg = getData().get(i).wkMsg;
                break;
            }
        }
        return wkMsg;
    }

    //获取最后一条消息是否为正在输入
    public boolean lastMsgIsTyping() {
        boolean isTyping = false;
        for (int i = getData().size() - 1; i >= 0; i--) {
            if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.type == WKContentType.typing) {
                isTyping = true;
                break;
            }
        }

        return isTyping;
    }

    public long getEndMsgOrderSeq() {
        long oldestOrderSeq = 0;
        for (int i = getData().size() - 1; i >= 0; i--) {
            if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.orderSeq != 0) {
                oldestOrderSeq = getData().get(i).wkMsg.orderSeq;
                break;
            }
        }
        return oldestOrderSeq;
    }

    public long getFirstMsgOrderSeq() {
        long oldestOrderSeq = 0;
        for (int i = 0, size = getData().size(); i < size; i++) {
            if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.orderSeq != 0) {
                oldestOrderSeq = getData().get(i).wkMsg.orderSeq;
                break;
            }
        }
        return oldestOrderSeq;
    }

    public void resetData(List<WKUIChatMsgItemEntity> list) {
        // resetData 紧接着通常会 setNewInstance/addData。先让索引失效，避免同尺寸
        // 替换列表且首尾对象碰巧相同时保留旧位置。
        indexedSize = -1;
        indexedFirstItem = null;
        indexedLastItem = null;
        if (WKReader.isEmpty(list)) return;
        for (int i = 0, size = list.size(); i < size; i++) {
            int previousIndex = i - 1;
            int nextIndex = i + 1;
            if (previousIndex >= 0) {
                list.get(i).previousMsg = list.get(previousIndex).wkMsg;
            }
            if (nextIndex <= list.size() - 1) {
                list.get(i).nextMsg = list.get(nextIndex).wkMsg;
            }
        }
    }

    public int getFirstVisibleItemIndex(int startIndex) {
        int index = startIndex;
        if (startIndex >= 0 && startIndex <= getData().size() - 1) {
            if (getData().get(startIndex).wkMsg == null || getData().get(startIndex).wkMsg.orderSeq == 0) {
                for (int i = startIndex; i < getData().size(); i++) {
                    if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.orderSeq != 0) {
                        index = i;
                        break;
                    }
                }
            }
        }
        return index;
    }

    public WKMsg getFirstVisibleItem(int startIndex) {
        WKMsg wkMsg = null;
        if (startIndex >= 0 && startIndex <= getData().size() - 1) {
            if (getData().get(startIndex).wkMsg == null || getData().get(startIndex).wkMsg.orderSeq == 0) {
                for (int i = startIndex; i < getData().size(); i++) {
                    if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.orderSeq != 0) {
                        wkMsg = getData().get(i).wkMsg;
                        break;
                    }
                }
            } else {
                wkMsg = getData().get(startIndex).wkMsg;
            }
        }
        return wkMsg;
    }

    public boolean isShowChooseItem() {
        boolean isShowChoose = false;
        for (int i = 0, size = getData().size(); i < size; i++) {
            if (getData().get(i).isChoose) {
                isShowChoose = true;
                break;
            }
        }
        return isShowChoose;
    }

    public boolean isCanSwipe(int index) {
        if (index < 0 || index >= getData().size()) {
            return false;
        }
        int type = getData().get(index).wkMsg.type;
        if (type <= 0 || getData().get(index).wkMsg.flame == 1 || (getData().get(index).wkMsg.remoteExtra != null && getData().get(index).wkMsg.remoteExtra.revoke == 1)) {
            return false;
        }
        WKChannel channel = iConversationContext.getChatChannelInfo();
        ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> list = getItemProviderList();
        WKChatBaseProvider baseItemProvider = (WKChatBaseProvider) list.get(type);
        if (baseItemProvider != null && channel.status == 1)
            return baseItemProvider.getMsgConfig(type).isCanReply;
        return false;
    }

    public void updateDeleteTimer(int position) {
        WKUIChatMsgItemEntity entity = getData().get(position);
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
        if (linearLayoutManager == null) return;
        View view = linearLayoutManager.findViewByPosition(position);
        LinearLayout baseView = null;
        if (view != null) {
            baseView = view.findViewById(R.id.wkBaseContentLayout);
        }
        if (baseView == null) return;
        ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> list = getItemProviderList();
        WKChatBaseProvider baseItemProvider = (WKChatBaseProvider) list.get(entity.wkMsg.type);
        if (baseItemProvider != null) {
            SecretDeleteTimer deleteTimer = null;
            WKChatIteMsgFromType from = baseItemProvider.getMsgFromType(entity.wkMsg);
            if (baseView.getChildCount() > 1) {
                if (from == WKChatIteMsgFromType.SEND) {
                    View childView = baseView.getChildAt(0);
                    if (childView instanceof SecretDeleteTimer) {
                        deleteTimer = (SecretDeleteTimer) childView;
                    }
                } else if (from == WKChatIteMsgFromType.RECEIVED) {
                    View childView = baseView.getChildAt(1);
                    if (childView instanceof SecretDeleteTimer) {
                        deleteTimer = (SecretDeleteTimer) childView;
                    }
                }
            }

            if (deleteTimer != null) {
                deleteTimer.setVisibility(View.VISIBLE);
                deleteTimer.setDestroyTime(entity.wkMsg.clientMsgNO, entity.wkMsg.flameSecond, entity.wkMsg.viewedAt, false);
            }
        }
    }


    public enum RefreshType {
        status, background, data, reaction, reply, listener
    }

    public void notifyStatus(int position) {
        notify(position, RefreshType.status, null);
    }

    public void notifyData(int position) {
        notify(position, RefreshType.data, null);
    }

    public void notifyListener(int position) {
        notify(position, RefreshType.listener, null);
    }

    public void notifyBackground(int position) {
        notify(position, RefreshType.background, null);
    }

    public void notifyReaction(int position, List<WKMsgReaction> reactionList) {
        notify(position, RefreshType.reaction, reactionList);
    }

    private void notify(int position, RefreshType refreshType, List<WKMsgReaction> reactionList) {
        WKUIChatMsgItemEntity entity = getData().get(position);
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
        if (linearLayoutManager == null) return;
        View view = linearLayoutManager.findViewByPosition(position);
        View baseView = null;
        if (view != null) {
            baseView = view.findViewById(R.id.wkBaseContentLayout);
        }
        if (baseView == null) return;
        ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> list = getItemProviderList();
        WKChatBaseProvider baseItemProvider = (WKChatBaseProvider) list.get(entity.wkMsg.type);
        if (baseItemProvider != null) {
            WKChatIteMsgFromType from = baseItemProvider.getMsgFromType(entity.wkMsg);
            // 刷新
            if (refreshType == RefreshType.data) {
                baseItemProvider.refreshData(position, baseView, entity, from);
                return;
            }
            if (refreshType == RefreshType.reaction) {
                FrameLayout reactionsView = view.findViewById(R.id.reactionsView);
                EndpointManager.getInstance().invoke(
                        "refresh_msg_reaction", new ShowMsgReactionMenu(
                                reactionsView,
                                from,
                                this,
                                reactionList)
                );
                AvatarView avatarView = view.findViewById(R.id.avatarView);
                if (avatarView != null) {
                    baseItemProvider.setAvatarLayoutParams(entity, from, avatarView);
                }
                return;
            }
            if (refreshType == RefreshType.background) {
                AvatarView avatarView = view.findViewById(R.id.avatarView);
                if (avatarView != null) {
                    baseItemProvider.setAvatarLayoutParams(entity, from, avatarView);
                }
                baseItemProvider.resetCellBackground(baseView, entity, from);
                LinearLayout fullContentLayout = view.findViewById(R.id.fullContentLayout);
                if (fullContentLayout != null) {
                    baseItemProvider.setFullLayoutParams(entity, from, fullContentLayout);
                }
                ChatItemView viewGroupLayout = view.findViewById(R.id.viewGroupLayout);
                if (viewGroupLayout != null) {
                    baseItemProvider.setItemPadding(position, viewGroupLayout);
                }
                return;
            }

            if (refreshType == RefreshType.status) {
                baseItemProvider.resetCellListener(position, baseView, entity, from);
                baseItemProvider.setMsgTimeAndStatus(
                        entity,
                        baseView,
                        from
                );
                return;
            }
            if (refreshType == RefreshType.listener) {
                baseItemProvider.resetCellListener(position, baseView, entity, from);
                return;
            }

            if (refreshType == RefreshType.reply) {
                baseItemProvider.refreshReply(position, baseView, entity, from);
            }
        }

    }

    public void refreshReplyMsg(WKMsg wkMsg) {
        if (wkMsg == null || wkMsg.remoteExtra == null || TextUtils.isEmpty(wkMsg.remoteExtra.messageID))
            return;
        List<WKUIChatMsgItemEntity> list = getData();
        for (int i = 0, size = list.size(); i < size; i++) {
            if (list.get(i).wkMsg.baseContentMsgModel == null || list.get(i).wkMsg.baseContentMsgModel.reply == null) {
                continue;
            }
            if (list.get(i).wkMsg.baseContentMsgModel.reply.message_seq == wkMsg.messageSeq) {
                list.get(i).wkMsg.baseContentMsgModel.reply.contentEditMsgModel = wkMsg.remoteExtra.contentEditMsgModel;
                list.get(i).wkMsg.baseContentMsgModel.reply.contentEdit = wkMsg.remoteExtra.contentEdit;
                list.get(i).wkMsg.baseContentMsgModel.reply.editAt = wkMsg.remoteExtra.editedAt;
                list.get(i).wkMsg.baseContentMsgModel.reply.revoke = wkMsg.remoteExtra.revoke;
                notify(i, RefreshType.reply, null);
            }
        }

    }
}
