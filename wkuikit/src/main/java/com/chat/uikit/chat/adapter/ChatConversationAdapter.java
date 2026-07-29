package com.chat.uikit.chat.adapter;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.emoji.MoonUtil;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.AvatarOtherViewMenu;
import com.chat.base.endpoint.entity.ShowCommunityAvatarMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.entity.WKChannelState;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.base.msgitem.WKRevokeProvider;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.CounterView;
import com.chat.base.ui.components.RoundTextView;
import com.chat.base.ui.components.TypingView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.StringUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.R;
import com.chat.uikit.enity.ChatConversationMsg;
import com.chat.uikit.message.MsgModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMentionType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

import org.jetbrains.annotations.NotNull;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2019-11-15 13:46
 * 会话记录适配器
 */
public class ChatConversationAdapter extends BaseQuickAdapter<ChatConversationMsg, BaseViewHolder> {
    private static final long CHANNEL_INFO_FETCH_SUCCESS_INTERVAL_MS = 5L * 60L * 1000L;
    private static final long CHANNEL_INFO_FETCH_RETRY_INTERVAL_MS = 20L * 1000L;
    private static final int CHANNEL_INFO_FETCH_CACHE_MAX_SIZE = 1000;
    private static final Object CHANNEL_INFO_FETCH_CACHE_LOCK = new Object();
    private static final Map<String, Long> CHANNEL_INFO_FETCH_ATTEMPT_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Long> CHANNEL_INFO_FETCH_SUCCESS_MAP = new ConcurrentHashMap<>();
    private static final Set<String> CHANNEL_INFO_FETCHING_SET = ConcurrentHashMap.newKeySet();

    private IListener iListener;

    private static final class ReminderViews {
        final TextView mentionView;
        final TextView draftView;
        final TextView approveView;

        ReminderViews(TextView mentionView, TextView draftView, TextView approveView) {
            this.mentionView = mentionView;
            this.draftView = draftView;
            this.approveView = approveView;
        }
    }

    private static final class CategoryViews {
        final ImageView muteView;
        final RoundTextView primaryView;
        final RoundTextView communityView;
        final RoundTextView robotView;

        CategoryViews(ImageView muteView, RoundTextView primaryView,
                      RoundTextView communityView, RoundTextView robotView) {
            this.muteView = muteView;
            this.primaryView = primaryView;
            this.communityView = communityView;
            this.robotView = robotView;
        }
    }

    public ChatConversationAdapter(@Nullable List<ChatConversationMsg> data) {
        super(R.layout.item_chat_conv_layout, data);
    }

    @Override
    protected void convert(@NonNull final BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        WKUIConversationMsg item = conversationMsg.uiConversationMsg;
        setUnreadCount(helper, conversationMsg, false);
        showTime(helper, item);
        showChannel(helper, item);
        showContent(helper, item);
        showReminders(helper, conversationMsg);
        setStatus(helper, item, false);
        showTyping(helper, conversationMsg);
        showCalling(helper, conversationMsg);
    }

    public void addListener(IListener iItemMenuClick) {
        this.iListener = iItemMenuClick;
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, ChatConversationMsg uiConversationMsg, @NotNull List<?> payloads) {
        ChatConversationMsg chatConversationMsg = (ChatConversationMsg) payloads.get(0);
        if (chatConversationMsg != null && chatConversationMsg.uiConversationMsg != null) {
            WKUIConversationMsg item = chatConversationMsg.uiConversationMsg;
//            showContent(baseViewHolder, item);
            if (chatConversationMsg.isResetCounter) {
                setUnreadCount(baseViewHolder, chatConversationMsg, true);
                chatConversationMsg.isResetCounter = false;
            }
            if (chatConversationMsg.isResetTime) {
                showTime(baseViewHolder, item);
                chatConversationMsg.isResetTime = false;
            }
            if (chatConversationMsg.isResetTyping) {
                showTyping(baseViewHolder, chatConversationMsg);
                chatConversationMsg.isResetTyping = false;
            }
            if (chatConversationMsg.isRefreshChannelInfo) {
                showChannel(baseViewHolder, item);
                chatConversationMsg.isRefreshChannelInfo = false;
            }
            if (chatConversationMsg.isResetReminders) {
                showReminders(baseViewHolder, chatConversationMsg);
                chatConversationMsg.isResetReminders = false;
            }
            if (chatConversationMsg.isRefreshStatus) {
                setStatus(baseViewHolder, item, true);
                chatConversationMsg.isRefreshStatus = false;
            }
            if (chatConversationMsg.isResetContent) {
                showContent(baseViewHolder, item);
                chatConversationMsg.isResetContent = false;
            }
            showCalling(baseViewHolder, chatConversationMsg);
        }
    }

    public interface IListener {
        void onClick(ItemMenu menu, WKUIConversationMsg item);
    }


    private String getFromName(byte channelType, WKMsg msg) {
        String fromName = "";
        if (msg != null && (WKContentType.isSystemMsg(msg.type)
                || msg.type == WKContentType.revoke
                || msg.remoteExtra.revoke == 1 || msg.type == WKContentType.screenshot)) {
            return fromName;
        }
        if (channelType == WKChannelType.PERSONAL || channelType == WKChannelType.CUSTOMER_SERVICE || msg == null || TextUtils.isEmpty(msg.fromUID) || msg.fromUID.equals(WKConfig.getInstance().getUid())) {
            return fromName;
        }
        String channelName = "";
        String channelRemark = "";
        String memberRemark = "";
        String memberName = "";
        if (msg.getFrom() != null) {
            channelRemark = msg.getFrom().channelRemark;
            channelName = msg.getFrom().channelName;
        }
        if (!TextUtils.isEmpty(channelRemark)) {
            return channelRemark;
        }
        if (msg.getMemberOfFrom() != null) {
            memberName = msg.getMemberOfFrom().memberName;
            memberRemark = msg.getMemberOfFrom().memberRemark;
        }
        if (!TextUtils.isEmpty(memberRemark)) {
            return memberRemark;
        }
        fromName = TextUtils.isEmpty(channelName) ? memberName : channelName;
        return fromName;
    }

    private String getContent(WKMsg msg) {
        String content = "";
        if (msg == null || msg.isDeleted == 1) return content;
        if (isRtcSignalMsg(msg)) return "";
        if (msg.baseContentMsgModel != null) {
            content = msg.baseContentMsgModel.getDisplayContent();
        }

        if (TextUtils.isEmpty(content) || WKContentType.isSystemMsg(msg.type)) {
            content = getShowContent(msg.content);
        }
        if (msg.remoteExtra.contentEditMsgModel != null) {
            content = msg.remoteExtra.contentEditMsgModel.getDisplayContent();
        }
        //判断是否被撤回
        if (msg.remoteExtra.revoke == 1)
            content = WKRevokeProvider.Companion.showRevokeMsg(msg);
        else if (msg.type == WKContentType.WK_CONTENT_FORMAT_ERROR) {
            content = getContext().getString(R.string.str_content_format_err);
        } else if (msg.type == WKContentType.WK_SIGNAL_DECRYPT_ERROR) {
            content = getContext().getString(R.string.str_signal_decrypt_err);
        } else if (msg.type == WKContentType.noRelation) {
            String showName = "";
            if (msg.getChannelInfo() != null) {
                if (TextUtils.isEmpty(msg.getChannelInfo().channelRemark)) {
                    showName = msg.getChannelInfo().channelName;
                } else {
                    showName = msg.getChannelInfo().channelRemark;
                }
            }
            content = String.format(getContext().getString(R.string.no_relation_request), showName);
        } else {
            if (!WKMsgItemViewManager.getInstance().getChatItemProviderList().containsKey(msg.type)) {
                if (TextUtils.isEmpty(content)) {
                    content = getContext().getString(R.string.unknow_msg_type);
                }
            }
        }
        return content;
    }

    private String getShowContent(String contentJson) {
        return StringUtils.getShowContent(getContext(), contentJson);
    }

    private boolean isRtcSignalMsg(WKMsg msg) {
        if (msg == null) return false;
        try {
            Object result = EndpointManager.getInstance().invoke("rtc_is_signal_msg", msg);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }


    private void setStatus(BaseViewHolder helper, WKUIConversationMsg item, boolean isPlayAnimation) {
        RLottieImageView sendingMsgIv = helper.getView(R.id.statusIV);
        RLottieDrawable drawable;
        boolean autoRepeat = false;
        int status = WKSendMsgResult.send_success;
        if (item.getWkMsg() != null) {
            status = item.getWkMsg().status;
        }
        boolean isSend = item.getWkMsg() != null && item.getWkMsg().isDeleted == 0 && !TextUtils.isEmpty(item.getWkMsg().fromUID) && item.getWkMsg().fromUID.equals(WKConfig.getInstance().getUid());
        if (isSend) {
            boolean isSingle = true;
            sendingMsgIv.setVisibility(View.VISIBLE);
            boolean isError = false;
            if (status == WKSendMsgResult.send_success) {
                // 自己发送
                if (item.getWkMsg().setting.receipt == 1 && item.getWkMsg().remoteExtra.readedCount > 0) {
                    drawable = new RLottieDrawable(getContext(), R.raw.ticks_double, "ticks_double", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
                    isSingle = false;
                } else {
                    drawable = new RLottieDrawable(getContext(), R.raw.ticks_single, "ticks_single", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
                }
                Theme.setColorFilter(sendingMsgIv,Theme.colorAccount);
//                sendingMsgIv.setLottieColorFilter(Theme.colorAccount);
            } else if (status == WKSendMsgResult.send_loading) {
                autoRepeat = true;
                drawable = new RLottieDrawable(getContext(), R.raw.msg_sending, "msg_sending", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
//                sendingMsgIv.setLottieColorFilter(ContextCompat.getColor(getContext(), R.color.color999));
                Theme.setColorFilter(sendingMsgIv,ContextCompat.getColor(getContext(), R.color.color999));
            } else {
                isError = true;
//                sendingMsgIv.setLottieColorFilter(ContextCompat.getColor(getContext(), R.color.white));
                Theme.setColorFilter(sendingMsgIv,ContextCompat.getColor(getContext(), R.color.white));
                drawable = new RLottieDrawable(getContext(), R.raw.error, "error", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            }
            sendingMsgIv.setAutoRepeat(autoRepeat);

            if (autoRepeat || isPlayAnimation) {
                sendingMsgIv.setAnimation(drawable);
                sendingMsgIv.playAnimation();
            } else {
                if (isError) {
                    sendingMsgIv.setAnimation(drawable);
                } else {
                    if (isSingle) {
                        sendingMsgIv.setImageDrawable(Theme.getTicksSingleDrawable());
                    } else sendingMsgIv.setImageDrawable(Theme.getTicksDoubleDrawable());
                }
            }
        } else {
            sendingMsgIv.setVisibility(View.GONE);
        }
        int finalStatus = status;
        sendingMsgIv.setOnClickListener(view -> {
            if (finalStatus != WKSendMsgResult.send_success && finalStatus != WKSendMsgResult.send_loading && item.getWkMsg() != null) {
                String content = getContext().getString(R.string.str_resend_msg_tips);
                if (finalStatus == WKSendMsgResult.no_relation) {
                    content = getContext().getString(R.string.no_relation_group);
                } else if (finalStatus == WKSendMsgResult.black_list) {
                    content =
                            getContext().getString(item.channelType == WKChannelType.GROUP ? R.string.blacklist_group : R.string.blacklist_user);

                } else if (finalStatus == WKSendMsgResult.not_on_white_list) {
                    content = getContext().getString(R.string.no_relation_user);
                }
                WKDialogUtils.getInstance().showDialog(getContext(), getContext().getString(R.string.msg_send_fail), content, true, "", getContext().getString(R.string.msg_send_fail_resend), 0, Theme.colorAccount, index -> {
                    if (index == 1) {
                        WKMsg msg = new WKMsg();
                        msg.channelID = item.channelID;
                        msg.channelType = item.channelType;
                        msg.setting = item.getWkMsg().setting;
                        msg.header = item.getWkMsg().header;
                        msg.type = item.getWkMsg().type;
                        msg.content = item.getWkMsg().content;
                        msg.baseContentMsgModel = item.getWkMsg().baseContentMsgModel;
                        msg.fromUID = WKConfig.getInstance().getUid();
                        WKIM.getInstance().getMsgManager()
                                .deleteWithClientMsgNO(item.getWkMsg().clientMsgNO);
                        WKIM.getInstance().getMsgManager().sendMessage(msg);
                    }
                });
            }
        });
    }

    private void setUnreadCount(@NotNull BaseViewHolder baseViewHolder, ChatConversationMsg item, boolean isAnimated) {
        CounterView counterView = baseViewHolder.getView(R.id.msgCountTv);
        boolean isMute;
        if (item.uiConversationMsg.getWkChannel() != null) {
            isMute = item.uiConversationMsg.getWkChannel().mute == 1;
        } else isMute = false;
        counterView.setColors(R.color.white, isMute ? R.color.color999 : R.color.reminderColor);
        counterView.setCount(item.getUnReadCount(), isAnimated);
        counterView.setGravity(Gravity.END);
        counterView.setVisibility(item.getUnReadCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void showTime(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        long msgTimestamp = item.lastMsgTimestamp;
        if (item.getWkMsg() != null) {
            if (item.getWkMsg().remoteExtra.editedAt != 0) {
                msgTimestamp = item.getWkMsg().remoteExtra.editedAt;
            }
        }
        String chatTime = WKTimeUtils.getInstance().getNewChatTime(msgTimestamp * 1000);
        helper.setText(R.id.timeTv, chatTime);
    }

    private void showContent(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        String content = getContent(item.getWkMsg());
        androidx.emoji2.widget.EmojiTextView contentTv = helper.getView(R.id.contentTv);
        boolean isSetChatPwd = isSetChatPwd(item.getWkChannel());
        // 聊天密码
        if (isSetChatPwd) {
            content = "❊❊❊❊❊❊❊❊❊❊❊❊❊";
        } else {
            String fromName = getFromName(item.channelType, item.getWkMsg());
            if (!TextUtils.isEmpty(fromName)) {
                content = fromName + "：" + content;
            }
        }
        //  contentTv.setText(content);
        MoonUtil.identifyFaceExpression(getContext(), contentTv, content, MoonUtil.SMALL_SCALE);
    }

    private void showReminders(@NotNull BaseViewHolder helper, ChatConversationMsg item) {
        TextView contentTv = helper.getView(R.id.contentTv);
        String draft = "";
        String approveContent = "";
        boolean mention = false;
        if (WKReader.isNotEmpty(item.getReminders())) {
            for (int i = 0, size = item.getReminders().size(); i < size; i++) {
                if (!mention && item.getReminders().get(i).type == WKMentionType.WKReminderTypeMentionMe
                        && item.getReminders().get(i).done == 0) {
                    mention = true;
                }
                if (item.getReminders().get(i).type == WKMentionType.WKApplyJoinGroupApprove
                        && item.getReminders().get(i).done == 0) {
                    approveContent = getContext().getString(R.string.apply_join_group);
                }
            }
        }
        if (item.uiConversationMsg.getRemoteMsgExtra() != null) {
            draft = item.uiConversationMsg.getRemoteMsgExtra().draft;
        }
        if (isSetChatPwd(item.uiConversationMsg.getWkChannel()) && !TextUtils.isEmpty(draft)) {
            draft = "❊❊❊❊❊❊❊❊❊❊❊❊❊";
        }

        LinearLayout remindLayout = helper.getView(R.id.remindLayout);
        ReminderViews views = getOrCreateReminderViews(remindLayout);
        views.mentionView.setVisibility(mention ? View.VISIBLE : View.GONE);
        boolean hasDraft = !TextUtils.isEmpty(draft);
        views.draftView.setVisibility(hasDraft ? View.VISIBLE : View.GONE);
        boolean hasApprove = !TextUtils.isEmpty(approveContent);
        views.approveView.setVisibility(hasApprove ? View.VISIBLE : View.GONE);
        if (hasApprove) {
            views.approveView.setText(approveContent);
        }

        if (hasDraft) {
            MoonUtil.identifyFaceExpression(getContext(), contentTv, draft, MoonUtil.SMALL_SCALE);
        } else {
            showContent(helper, item.uiConversationMsg);
        }
    }

    private ReminderViews getOrCreateReminderViews(LinearLayout remindLayout) {
        Object tag = remindLayout.getTag();
        if (tag instanceof ReminderViews) {
            return (ReminderViews) tag;
        }
        remindLayout.removeAllViews();
        TextView mentionView = createReminderTextView();
        mentionView.setText(R.string.last_msg_remind);
        TextView draftView = createReminderTextView();
        draftView.setText(R.string.last_msg_draft);
        TextView approveView = createReminderTextView();
        remindLayout.addView(mentionView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 5, 0));
        remindLayout.addView(draftView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 5, 0));
        remindLayout.addView(approveView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 5, 0));
        ReminderViews views = new ReminderViews(mentionView, draftView, approveView);
        remindLayout.setTag(views);
        return views;
    }

    private TextView createReminderTextView() {
        TextView textView = new TextView(getContext());
        textView.setTypeface(null, Typeface.BOLD);
        textView.setTextColor(ContextCompat.getColor(getContext(), R.color.reminderColor));
        textView.setTextSize(13f);
        textView.setVisibility(View.GONE);
        return textView;
    }

    private void showChannel(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        addEvent(helper, item);
        String showName = "";
        if (item.channelID.equals(WKSystemAccount.system_file_helper)) {
            showName = getContext().getString(R.string.wk_file_helper);
        } else if (item.channelID.equals(WKSystemAccount.system_team)) {
            showName = getContext().getString(R.string.wk_system_notice);
        }
        boolean isTopicRoom = isTopicRoomConversation(item);
        helper.setGone(R.id.groupIV, item.channelType != WKChannelType.GROUP || isTopicRoom);
        boolean isTop;
        AvatarView avatarView = helper.getView(R.id.avatarView);
        avatarView.setSize(56, isTopicRoom ? 12 : 28);
        showTopicBadge(helper, isTopicRoom);
        if (item.getWkChannel() != null) {
            if (TextUtils.isEmpty(showName))
                showName = TextUtils.isEmpty(item.getWkChannel().channelRemark) ? item.getWkChannel().channelName : item.getWkChannel().channelRemark;
            if (item.channelType == WKChannelType.COMMUNITY) {
                EndpointManager.getInstance().invoke("show_community_avatar", new ShowCommunityAvatarMenu(getContext(), avatarView, item.getWkChannel()));
            } else if (isTopicRoom) {
                if (TextUtils.isEmpty(showName)) {
                    showName = getTopicExtraString(item.getWkChannel(), "topic_title");
                }
                avatarView.showAvatar(item.getWkChannel());
            } else {
                avatarView.defaultAvatarTv.setVisibility(View.GONE);
                avatarView.imageView.setVisibility(View.VISIBLE);
                avatarView.showAvatar(item.getWkChannel(), true);
            }
            if (!isTopicRoom) {
                helper.getView(R.id.otherLayout).setVisibility(View.VISIBLE);
                EndpointManager.getInstance().invoke("show_avatar_other_info", new AvatarOtherViewMenu(helper.getView(R.id.otherLayout), item.getWkChannel(), avatarView, false));
            } else {
                helper.getView(R.id.otherLayout).setVisibility(View.GONE);
            }
            isTop = item.getWkChannel().top == 1;
            if (TextUtils.isEmpty(showName)) {
                showName = getContext().getString(R.string.chat);
//                if (!isScrolling)
                maybeFetchChannelInfo(item.channelID, item.channelType);
            }
            LinearLayout categoryLayout = helper.getView(R.id.categoryLayout);
            bindCategoryViews(categoryLayout, item.getWkChannel(), item.channelType);
            ImageView forbiddenIv = helper.getView(R.id.forbiddenIv);
            forbiddenIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.color999), PorterDuff.Mode.MULTIPLY));
            //设置是否置顶
            helper.setBackgroundResource(R.id.contentLayout, isTop ? R.drawable.home_bg : R.drawable.layout_bg);
            //判断是否禁言
            if (item.getWkChannel().forbidden == 1) {
                WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(item.channelID, item.channelType, WKConfig.getInstance().getUid());
                if (mChannelMember != null && mChannelMember.role == 0) {
                    helper.setGone(R.id.forbiddenIv, false);
                } else helper.setGone(R.id.forbiddenIv, true);
            } else {
                helper.setGone(R.id.forbiddenIv, true);
            }
            //消息头像

//            GlideUtils.getInstance().showAvatarImg(getContext(), item.channelID, item.channelType, item.getWkChannel().avatar, helper.getView(R.id.avatarIv));
        } else {
            helper.getView(R.id.otherLayout).setVisibility(View.GONE);
            bindCategoryViews(helper.getView(R.id.categoryLayout), null, item.channelType);
            helper.setGone(R.id.forbiddenIv, true);
            helper.setBackgroundResource(R.id.contentLayout, R.drawable.layout_bg);
            if (TextUtils.isEmpty(showName))
                showName = getContext().getString(R.string.chat);
            if (isTopicRoom) {
                avatarView.showDefaultAvatar(showName);
            } else {
                avatarView.showCachedAvatar(item.channelID, item.channelType, showName);
            }
            // 重新获取频道信息只做低频补全，头像本身先走本地缓存，避免 RecyclerView 反复请求服务器。
            maybeFetchChannelInfo(item.channelID, item.channelType);
        }
        helper.setText(R.id.nameTv, showName);
    }

    private void bindCategoryViews(LinearLayout categoryLayout, WKChannel channel, byte channelType) {
        CategoryViews views = getOrCreateCategoryViews(categoryLayout);
        boolean muted = channel != null && channel.mute == 1;
        views.muteView.setVisibility(muted ? View.VISIBLE : View.GONE);
        views.primaryView.setVisibility(View.GONE);
        views.communityView.setVisibility(View.GONE);
        views.robotView.setVisibility(View.GONE);
        if (channel == null) return;

        String category = channel.category;
        if (WKSystemAccount.accountCategorySystem.equals(category)) {
            configureCategoryView(views.primaryView, getContext().getString(R.string.official),
                    ContextCompat.getColor(getContext(), R.color.transparent),
                    ContextCompat.getColor(getContext(), R.color.reminderColor),
                    ContextCompat.getColor(getContext(), R.color.reminderColor));
        } else if (WKSystemAccount.accountCategoryCustomerService.equals(category)) {
            configureCategoryView(views.primaryView, getContext().getString(R.string.customer_service),
                    Theme.colorAccount, ContextCompat.getColor(getContext(), R.color.white), Theme.colorAccount);
        } else if (WKSystemAccount.accountCategoryVisitor.equals(category)) {
            configureCategoryView(views.primaryView, getContext().getString(R.string.visitor),
                    ContextCompat.getColor(getContext(), R.color.transparent),
                    ContextCompat.getColor(getContext(), R.color.colorFFC107),
                    ContextCompat.getColor(getContext(), R.color.colorFFC107));
        } else if (WKSystemAccount.channelCategoryOrganization.equals(category)) {
            configureCategoryView(views.primaryView, getContext().getString(R.string.all_staff),
                    ContextCompat.getColor(getContext(), R.color.category_org_bg),
                    ContextCompat.getColor(getContext(), R.color.category_org_text),
                    ContextCompat.getColor(getContext(), R.color.transparent));
        } else if (WKSystemAccount.channelCategoryDepartment.equals(category)) {
            configureCategoryView(views.primaryView, getContext().getString(R.string.department),
                    ContextCompat.getColor(getContext(), R.color.category_org_bg),
                    ContextCompat.getColor(getContext(), R.color.category_org_text),
                    ContextCompat.getColor(getContext(), R.color.transparent));
        }

        if (channelType == WKChannelType.COMMUNITY) {
            configureCategoryView(views.communityView, getContext().getString(R.string.community),
                    ContextCompat.getColor(getContext(), R.color.category_community_bg),
                    ContextCompat.getColor(getContext(), R.color.category_community_text),
                    ContextCompat.getColor(getContext(), R.color.transparent));
        }
        if (channel.robot == 1) {
            configureCategoryView(views.robotView, getContext().getString(R.string.bot),
                    ContextCompat.getColor(getContext(), R.color.colorFFC107),
                    ContextCompat.getColor(getContext(), R.color.white),
                    ContextCompat.getColor(getContext(), R.color.colorFFC107));
        }
    }

    private CategoryViews getOrCreateCategoryViews(LinearLayout categoryLayout) {
        Object tag = categoryLayout.getTag();
        if (tag instanceof CategoryViews) {
            return (CategoryViews) tag;
        }
        categoryLayout.removeAllViews();
        ImageView muteView = new ImageView(getContext());
        muteView.setImageResource(R.mipmap.list_mute);
        Theme.setColorFilter(muteView, ContextCompat.getColor(getContext(), R.color.popupTextColor));
        RoundTextView primaryView = Theme.getChannelCategoryTV(getContext(), "", 0, 0, 0);
        RoundTextView communityView = Theme.getChannelCategoryTV(getContext(), "", 0, 0, 0);
        RoundTextView robotView = Theme.getChannelCategoryTV(getContext(), "", 0, 0, 0);
        categoryLayout.addView(muteView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 3, 1, 0, 0));
        categoryLayout.addView(primaryView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
        categoryLayout.addView(communityView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
        categoryLayout.addView(robotView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
        CategoryViews views = new CategoryViews(muteView, primaryView, communityView, robotView);
        categoryLayout.setTag(views);
        return views;
    }

    private void configureCategoryView(RoundTextView view, String text, int bgColor,
                                       int textColor, int borderColor) {
        view.setText(text);
        view.setTextColor(textColor);
        // RoundTextView.setBorderColor() 会重新 new GradientDrawable；直接复用已有背景，
        // 避免会话快速滚动时标签绑定持续制造 Drawable 和 GC。
        if (view.getBackground() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable) view.getBackground();
            background.setColor(bgColor);
            background.setStroke(AndroidUtilities.dp(1), borderColor);
        } else {
            view.setBackGroundColor(bgColor);
            view.setBorderColor(borderColor);
        }
        view.setVisibility(View.VISIBLE);
    }

    private void maybeFetchChannelInfo(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID) || TextUtils.isEmpty(WKConfig.getInstance().getUid())) return;
        String key = buildChannelInfoFetchKey(channelID, channelType);
        long now = System.currentTimeMillis();
        Long lastSuccess = CHANNEL_INFO_FETCH_SUCCESS_MAP.get(key);
        if (lastSuccess != null && now - lastSuccess < CHANNEL_INFO_FETCH_SUCCESS_INTERVAL_MS) {
            return;
        }
        Long lastAttempt = CHANNEL_INFO_FETCH_ATTEMPT_MAP.get(key);
        if (lastAttempt != null && now - lastAttempt < CHANNEL_INFO_FETCH_RETRY_INTERVAL_MS) {
            return;
        }
        CHANNEL_INFO_FETCH_ATTEMPT_MAP.put(key, now);
        CHANNEL_INFO_FETCHING_SET.add(key);
        trimChannelInfoFetchCache();
        try {
            WKIM.getInstance().getChannelManager().fetchChannelInfo(channelID, channelType);
        } catch (Exception ignored) {
            CHANNEL_INFO_FETCHING_SET.remove(key);
        }
    }

    public static void markChannelInfoFetchSuccess(WKChannel channel) {
        if (channel == null || TextUtils.isEmpty(channel.channelID)
                || TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
            return;
        }
        String key = buildChannelInfoFetchKey(channel.channelID, channel.channelType);
        // 只确认由本适配器主动发起的补拉。在线状态、置顶、免打扰等普通频道刷新
        // 不能误判为头像/名称补拉成功，否则空资料会进入 5 分钟冷却。
        if (!CHANNEL_INFO_FETCHING_SET.remove(key)) {
            return;
        }
        if (!hasUsableChannelInfo(channel)) {
            CHANNEL_INFO_FETCH_SUCCESS_MAP.remove(key);
            return;
        }
        long now = System.currentTimeMillis();
        CHANNEL_INFO_FETCH_ATTEMPT_MAP.put(key, now);
        CHANNEL_INFO_FETCH_SUCCESS_MAP.put(key, now);
        trimChannelInfoFetchCache();
    }

    private static boolean hasUsableChannelInfo(WKChannel channel) {
        if (channel == null) return false;
        // 仅把真正可用于会话展示的资料视为补拉成功。
        // online、last_offline、country 等普通扩展更新不能让空头像/空名称进入 5 分钟冷却。
        return !TextUtils.isEmpty(channel.channelName)
                || !TextUtils.isEmpty(channel.channelRemark)
                || !TextUtils.isEmpty(channel.avatar)
                || !TextUtils.isEmpty(channel.category)
                || WKSystemAccount.system_file_helper.equals(channel.channelID)
                || WKSystemAccount.system_team.equals(channel.channelID);
    }

    public static void clearChannelInfoFetchCache() {
        CHANNEL_INFO_FETCH_ATTEMPT_MAP.clear();
        CHANNEL_INFO_FETCH_SUCCESS_MAP.clear();
        CHANNEL_INFO_FETCHING_SET.clear();
    }

    private static String buildChannelInfoFetchKey(String channelID, byte channelType) {
        return WKConfig.getInstance().getUid() + "_" + channelType + "_" + channelID;
    }

    private static void trimChannelInfoFetchCache() {
        if (CHANNEL_INFO_FETCH_ATTEMPT_MAP.size() <= CHANNEL_INFO_FETCH_CACHE_MAX_SIZE
                && CHANNEL_INFO_FETCH_SUCCESS_MAP.size() <= CHANNEL_INFO_FETCH_CACHE_MAX_SIZE) {
            return;
        }
        synchronized (CHANNEL_INFO_FETCH_CACHE_LOCK) {
            trimOldestEntries(CHANNEL_INFO_FETCH_ATTEMPT_MAP);
            trimOldestEntries(CHANNEL_INFO_FETCH_SUCCESS_MAP);
            CHANNEL_INFO_FETCHING_SET.retainAll(CHANNEL_INFO_FETCH_ATTEMPT_MAP.keySet());
        }
    }

    private static void trimOldestEntries(Map<String, Long> map) {
        while (map.size() > CHANNEL_INFO_FETCH_CACHE_MAX_SIZE) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, Long> entry : map.entrySet()) {
                long value = entry.getValue() == null ? Long.MIN_VALUE : entry.getValue();
                if (value < oldestTime) {
                    oldestTime = value;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) break;
            map.remove(oldestKey);
            CHANNEL_INFO_FETCHING_SET.remove(oldestKey);
        }
    }

    private void showTopicBadge(@NotNull BaseViewHolder helper, boolean isTopicRoom) {
        ImageView topicBadgeIv = helper.getView(R.id.topicBadgeIv);
        topicBadgeIv.setVisibility(isTopicRoom ? View.VISIBLE : View.GONE);
        if (!isTopicRoom) {
            topicBadgeIv.setColorFilter(null);
            return;
        }
        GradientDrawable bg;
        if (topicBadgeIv.getBackground() instanceof GradientDrawable) {
            bg = (GradientDrawable) topicBadgeIv.getBackground();
        } else {
            bg = new GradientDrawable();
            topicBadgeIv.setBackground(bg);
        }
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        bg.setStroke(AndroidUtilities.dp(1f), 0xFFFFFFFF);
        int padding = AndroidUtilities.dp(3f);
        topicBadgeIv.setPadding(padding, padding, padding, padding);
        topicBadgeIv.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
    }

    private String getTopicExtraString(WKChannel channel, String key) {
        if (channel == null) return "";
        String value = getExtraString(channel.localExtra, key);
        if (TextUtils.isEmpty(value)) value = getExtraString(channel.remoteExtraMap, key);
        return value;
    }

    private String getExtraString(java.util.Map<String, Object> map, String key) {
        if (map == null || TextUtils.isEmpty(key)) return "";
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isTopicRoomConversation(WKUIConversationMsg item) {
        if (item == null) return false;
        if (item.channelType == WKChannelType.GROUP && !TextUtils.isEmpty(item.channelID) && item.channelID.startsWith("topic_")) {
            return true;
        }
        WKChannel channel = item.getWkChannel();
        if (channel == null) return false;
        if ("topic_room".equals(channel.category)) return true;
        return hasTopicRoomFlag(channel.remoteExtraMap) || hasTopicRoomFlag(channel.localExtra);
    }

    private boolean hasTopicRoomFlag(java.util.Map<String, Object> map) {
        if (map == null) return false;
        Object value = map.get("topic_room");
        if (value == null) return false;
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private boolean isSetChatPwd(WKChannel channel) {
        if (channel == null || channel.remoteExtraMap == null || !channel.remoteExtraMap.containsKey(WKChannelExtras.chatPwdOn))
            return false;
        boolean isSetChatPwd;
        Object object = channel.remoteExtraMap.get(WKChannelExtras.chatPwdOn);
        if (object != null) {
            isSetChatPwd = (int) object == 1;
        } else {
            isSetChatPwd = false;
        }
        return isSetChatPwd;
    }

    private void showTyping(@NotNull BaseViewHolder helper, ChatConversationMsg item) {
        helper.setGone(R.id.spinKit, item.typingStartTime <= 0);
        if (item.typingStartTime > 0) {
            String content;
            if (item.uiConversationMsg.channelType == WKChannelType.GROUP) {
                String name = item.typingUserName;
                content = String.format(getContext().getString(R.string.user_is_typing), name);
            } else {
                content = getContext().getString(R.string.other_is_typing);
            }
            helper.setText(R.id.contentTv, content);
        }
        TypingView typingView = helper.getView(R.id.spinKit);
        typingView.setDotColor(ContextCompat.getColor(getContext(),R.color.color999));
        typingView.setDotRadius(AndroidUtilities.dp(3f));
        typingView.setDotSpacing(1);
    }

    private void addEvent(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        //长按事件
        boolean top;
        boolean mute;
        if (item.getWkChannel() != null) {
            top = item.getWkChannel().top == 1;
            mute = item.getWkChannel().mute == 1;
        } else {
            top = false;
            mute = false;
        }
        List<PopupMenuItem> list = new ArrayList<>();
        if (item.getWkChannel() != null) {
            list.add(new PopupMenuItem(getContext().getString(mute ? R.string.open_channel_notice : R.string.close_channel_notice), mute ? R.mipmap.msg_unmute : R.mipmap.msg_mute, () -> iListener.onClick(ItemMenu.mute, item)));
        }
        //list.add(new ChatLongClickEntity(2, item.unreadCount > 0 ? getContext().getString(R.string.sign_read_msg) : getContext().getString(R.string.sign_unread_msg)));
        list.add(new PopupMenuItem(top ? getContext().getString(R.string.cancel_top) : getContext().getString(R.string.msg_top), top ? R.mipmap.msg_unpin : R.mipmap.msg_pin, () -> iListener.onClick(ItemMenu.top, item)));
        list.add(new PopupMenuItem(getContext().getString(R.string.delete_msg), R.mipmap.msg_delete, () -> iListener.onClick(ItemMenu.delete, item)));
        WKDialogUtils.getInstance().setViewLongClickPopup(helper.getView(R.id.contentLayout), list);
    }

    private void showCalling(final BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        helper.setGone(R.id.callingIv, conversationMsg.isCalling == 0);
    }

    public enum ItemMenu {
        delete, top, mute
    }
}
