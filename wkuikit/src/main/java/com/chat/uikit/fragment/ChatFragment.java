package com.chat.uikit.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.base.WKBaseFragment;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.TabActivity;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.chat.adapter.ChatConversationAdapter;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.FragChatConversationLayoutBinding;
import com.chat.uikit.enity.ChatConversationMsg;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.search.remote.GlobalActivity;
import com.chat.uikit.user.service.UserModel;
import com.chat.uikit.user.MyInfoActivity;
import com.chat.uikit.setting.WKThemeSettingActivity;
import com.chat.uikit.setting.WKSetFontSizeActivity;
import com.chat.uikit.setting.WKLanguageActivity;
import com.chat.uikit.setting.MsgNoticesSettingActivity;
import com.chat.base.utils.DataCleanManager;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelState;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.message.type.WKConnectReason;
import com.xinbida.wukongim.message.type.WKConnectStatus;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 2019-11-12 14:55
 * 会话
 */
public class ChatFragment extends WKBaseFragment<FragChatConversationLayoutBinding> {

    private ChatConversationAdapter chatConversationAdapter;
    private Disposable disposable;
    private Disposable sortDisposable; // 排序任务的 Disposable
    private final List<Integer> refreshIds = new ArrayList<>();
    private Timer connectTimer;
    private TabActivity tabActivity;
    // 会话位置缓存，用于快速查找
    private final HashMap<String, Integer> conversationIndexMap = new HashMap<>();
    // 缓存未读数，避免频繁计算
    private int cachedUnreadCount = 0;
    private boolean isUnreadCountDirty = true;
    private static final int PAGE_MESSAGES = 0;
    private static final int PAGE_TOPIC_ROOMS = 1;
    private static final int PAGE_CONTACTS = 2;
    private int currentHomePage = PAGE_MESSAGES;
    private boolean isShowingTopicRooms = false;
    private boolean topicRoomFragmentLoaded = false;
    private boolean contactsFragmentLoaded = false;
    private ViewPager2.OnPageChangeCallback homePagerCallback;
    private int homePagerScrollState = ViewPager2.SCROLL_STATE_IDLE;
    private long ignoreConversationClickUntilMs = 0L;
    private float conversationTouchStartX = 0f;
    private float conversationTouchStartY = 0f;
    private boolean conversationHorizontalDragging = false;
    private int conversationTouchSlop = 0;
    // 定时清理消息会话列表里已经过期的话题聊天室，避免后端删除后本地会话仍残留。
    private Disposable topicRoomExpireDisposable;
    private static final long TOPIC_ROOM_EXPIRE_CHECK_SECONDS = 60L;

    @Override
    protected boolean isShowBackLayout() {
        return false;
    }

    @Override
    protected FragChatConversationLayoutBinding getViewBinding() {
        return FragChatConversationLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        wkVBinding.textSwitcher.setTag(-1);
        wkVBinding.textSwitcher.setFactory(() -> {
            TextView textView = new TextView(getActivity());
            textView.setTextSize(22);
            Typeface face = Typeface.createFromAsset(getResources().getAssets(),
                    "fonts/mw_bold.ttf");
            textView.setTypeface(face);
            textView.setTextColor(ContextCompat.getColor(requireActivity(), R.color.colorDark));
            return textView;
        });
        wkVBinding.textSwitcher.setText(getString(R.string.app_name));
        //去除刷新条目闪动动画
        ((DefaultItemAnimator) Objects.requireNonNull(wkVBinding.recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
        chatConversationAdapter = new ChatConversationAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, chatConversationAdapter);
        chatConversationAdapter.setAnimationEnable(false);
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);

        Theme.setPressedBackground(wkVBinding.deviceIv);
        Theme.setPressedBackground(wkVBinding.searchIv);
        if (wkVBinding.profileAvatarView != null) {
            wkVBinding.profileAvatarView.setSize(38);
            Theme.setPressedBackground(wkVBinding.profileAvatarView);
            refreshTopProfileAvatar();
        }
        wkVBinding.rightIv.setBackgroundColor(Color.TRANSPARENT);
        Theme.setPressedBackground(wkVBinding.messageTabTv);
        Theme.setPressedBackground(wkVBinding.roomTabTv);
        Theme.setPressedBackground(wkVBinding.contactsTabLayout);
        conversationTouchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        setupHomeViewPager();
        showHomePage(PAGE_MESSAGES, false);
        updateContactsBadge();
    }

    private void showTopicRooms(boolean showRooms) {
        showHomePage(showRooms ? PAGE_TOPIC_ROOMS : PAGE_MESSAGES);
    }

    private void setupHomeViewPager() {
        if (wkVBinding.homeViewPager == null || wkVBinding.homeViewPager.getAdapter() != null) return;
        wkVBinding.homeViewPager.setOffscreenPageLimit(2);
        wkVBinding.homeViewPager.setAdapter(new HomePagerAdapter());
        homePagerCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                homePagerScrollState = state;
                if (state != ViewPager2.SCROLL_STATE_IDLE) {
                    suppressConversationClick(260L);
                }
            }

            @Override
            public void onPageSelected(int position) {
                updateSelectedHomePage(position);
                suppressConversationClick(220L);
            }
        };
        wkVBinding.homeViewPager.registerOnPageChangeCallback(homePagerCallback);
    }

    private void showHomePage(int page) {
        showHomePage(page, true);
    }

    private void showHomePage(int page, boolean smoothScroll) {
        page = normalizeHomePage(page);
        if (wkVBinding != null && wkVBinding.homeViewPager != null && wkVBinding.homeViewPager.getAdapter() != null) {
            if (wkVBinding.homeViewPager.getCurrentItem() != page) {
                wkVBinding.homeViewPager.setCurrentItem(page, smoothScroll);
            } else {
                updateSelectedHomePage(page);
            }
        } else {
            updateSelectedHomePage(page);
        }
    }

    private int normalizeHomePage(int page) {
        if (page < PAGE_MESSAGES) return PAGE_MESSAGES;
        if (page > PAGE_CONTACTS) return PAGE_CONTACTS;
        return page;
    }

    private void updateSelectedHomePage(int page) {
        page = normalizeHomePage(page);
        currentHomePage = page;
        isShowingTopicRooms = page == PAGE_TOPIC_ROOMS;
        boolean showMessages = page == PAGE_MESSAGES;
        boolean showRooms = page == PAGE_TOPIC_ROOMS;
        boolean showContacts = page == PAGE_CONTACTS;

        updateHomeTabStyle(wkVBinding.messageTabTv, showMessages);
        updateHomeTabStyle(wkVBinding.roomTabTv, showRooms);
        updateHomeTabStyle(wkVBinding.contactsTabTv, showContacts);

        if (showRooms) {
            ensureTopicRoomFragment();
        } else if (showContacts) {
            ensureContactsFragment();
        }
    }

    private View getHomePageView(int page) {
        if (page == PAGE_TOPIC_ROOMS) return wkVBinding.roomContainer;
        if (page == PAGE_CONTACTS) return wkVBinding.contactsContainer;
        return wkVBinding.refreshLayout;
    }

    private class HomePagerAdapter extends RecyclerView.Adapter<HomePageViewHolder> {
        @Override
        public HomePageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout page = new FrameLayout(parent.getContext());
            page.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return new HomePageViewHolder(page);
        }

        @Override
        public void onBindViewHolder(HomePageViewHolder holder, int position) {
            holder.bind(getHomePageView(position));
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    private static class HomePageViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout container;

        HomePageViewHolder(FrameLayout itemView) {
            super(itemView);
            container = itemView;
        }

        void bind(View pageView) {
            if (pageView == null) return;
            ViewGroup oldParent = pageView.getParent() instanceof ViewGroup ? (ViewGroup) pageView.getParent() : null;
            if (oldParent != container) {
                if (oldParent != null) {
                    oldParent.removeView(pageView);
                }
                container.removeAllViews();
                container.addView(pageView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            }
            pageView.setVisibility(View.VISIBLE);
        }
    }

    private void suppressConversationClick(long durationMs) {
        long until = SystemClock.uptimeMillis() + durationMs;
        if (until > ignoreConversationClickUntilMs) {
            ignoreConversationClickUntilMs = until;
        }
    }

    private void trackConversationTouch(MotionEvent event) {
        if (event == null) return;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                conversationTouchStartX = event.getRawX();
                conversationTouchStartY = event.getRawY();
                conversationHorizontalDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - conversationTouchStartX;
                float dy = event.getRawY() - conversationTouchStartY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDx > Math.max(conversationTouchSlop, AndroidUtilities.dp(8)) && absDx > absDy * 1.2f) {
                    conversationHorizontalDragging = true;
                    suppressConversationClick(260L);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (conversationHorizontalDragging) {
                    suppressConversationClick(260L);
                }
                conversationHorizontalDragging = false;
                break;
            default:
                break;
        }
    }

    private boolean shouldIgnoreConversationClick() {
        return currentHomePage != PAGE_MESSAGES
                || homePagerScrollState != ViewPager2.SCROLL_STATE_IDLE
                || conversationHorizontalDragging
                || SystemClock.uptimeMillis() < ignoreConversationClickUntilMs;
    }

    private void updateHomeTabStyle(TextView tabView, boolean selected) {
        tabView.setTextSize(selected ? 20 : 16);
        tabView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        tabView.setTextColor(ContextCompat.getColor(requireActivity(), selected ? R.color.colorDark : R.color.popupTextColor));
    }

    private void updateContactsBadge() {
        if (wkVBinding == null || wkVBinding.contactsBadgeView == null) return;
        int count = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_new_friend_count");
        wkVBinding.contactsBadgeView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void removeTopicRoomRowsFromMessageList() {
        if (chatConversationAdapter == null || chatConversationAdapter.getData() == null || chatConversationAdapter.getData().isEmpty()) {
            return;
        }
        List<ChatConversationMsg> kept = new ArrayList<>();
        boolean changed = false;
        for (ChatConversationMsg item : chatConversationAdapter.getData()) {
            if (item == null || isTopicRoomConversation(item.uiConversationMsg)) {
                changed = true;
                continue;
            }
            kept.add(item);
        }
        if (changed) {
            chatConversationAdapter.setList(kept);
            rebuildIndexCache();
            setAllCount();
        }
    }

    private void ensureTopicRoomFragment() {
        if (topicRoomFragmentLoaded || !isAdded()) return;
        Object fragmentObject = EndpointManager.getInstance().invoke("peipe_topic_room_fragment", null);
        if (fragmentObject instanceof Fragment) {
            topicRoomFragmentLoaded = true;
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.roomContainer, (Fragment) fragmentObject)
                    .commitAllowingStateLoss();
        }
    }

    private void ensureContactsFragment() {
        if (contactsFragmentLoaded || !isAdded()) return;
        contactsFragmentLoaded = true;
        ContactsFragment contactsFragment = ContactsFragment.newEmbeddedInstance();
        getChildFragmentManager().beginTransaction()
                .replace(R.id.contactsContainer, contactsFragment)
                .commitAllowingStateLoss();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        wkVBinding.rightIv.setOnClickListener(view -> openSideMenu());
        if (wkVBinding.profileAvatarView != null) {
            wkVBinding.profileAvatarView.setOnClickListener(view -> openPartnerProfile());
        }
        wkVBinding.sideMenuMask.setOnClickListener(view -> closeSideMenu());
        wkVBinding.messageTabTv.setOnClickListener(view -> showHomePage(PAGE_MESSAGES));
        wkVBinding.roomTabTv.setOnClickListener(view -> showHomePage(PAGE_TOPIC_ROOMS));
        wkVBinding.contactsTabLayout.setOnClickListener(view -> showHomePage(PAGE_CONTACTS));
        wkVBinding.contactsTabTv.setOnClickListener(view -> showHomePage(PAGE_CONTACTS));
        initSideMenuListeners();
        EndpointManager.getInstance().setMethod("peipe_show_topic_rooms", object -> {
            showTopicRooms(Boolean.TRUE.equals(object));
            return null;
        });
        EndpointManager.getInstance().setMethod("peipe_switch_home_page", object -> {
            if (object instanceof Integer) {
                showHomePage((Integer) object);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("chat_fragment_contacts_badge", EndpointCategory.wkRefreshMailList, object -> {
            updateContactsBadge();
            return null;
        });
        wkVBinding.recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent event) {
                trackConversationTouch(event);
                return false;
            }
        });

        wkVBinding.deviceIv.setOnClickListener(v -> EndpointManager.getInstance().invoke("show_pc_login_view", getActivity()));
        wkVBinding.searchIv.setOnClickListener(view1 -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @SuppressWarnings("unchecked") ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), new Pair<>(wkVBinding.searchIv, "searchView"));
                startActivity(new Intent(getActivity(), GlobalActivity.class), activityOptions.toBundle());
            } else {
                startActivity(new Intent(getActivity(), GlobalActivity.class));
            }
        });
        chatConversationAdapter.addChildClickViewIds(R.id.contentLayout);
        chatConversationAdapter.setOnItemChildClickListener((adapter, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, v -> {
            if (shouldIgnoreConversationClick()) return;
            ChatConversationMsg uiConversationMsg = (ChatConversationMsg) adapter.getItem(position);
            if (uiConversationMsg != null && uiConversationMsg.uiConversationMsg != null) {
                if (view.getId() == R.id.contentLayout) {
                    if (uiConversationMsg.uiConversationMsg.channelType == WKChannelType.COMMUNITY) {
                        EndpointManager.getInstance().invoke("show_community", uiConversationMsg.uiConversationMsg.channelID);
                    } else
                        WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(getActivity(), uiConversationMsg.uiConversationMsg.channelID, uiConversationMsg.uiConversationMsg.channelType, 0, false));
                }
            }
        }));
        chatConversationAdapter.addListener((menu, item) -> {
            if (menu == ChatConversationAdapter.ItemMenu.delete) {
                WKDialogUtils.getInstance().showDialog(getActivity(), getString(R.string.delete_chat), getString(R.string.delete_conver_msg_tips), true, "", getString(R.string.base_delete), 0, ContextCompat.getColor(requireActivity(), R.color.red), index -> {
                    if (index == 1) {
                        List<WKReminder> list = WKIM.getInstance().getReminderManager().getReminders(item.channelID, item.channelType);
                        if (WKReader.isNotEmpty(list)) {
                            List<Long> reminderIds = new ArrayList<>();
                            for (WKReminder reminder : list) {
                                if (reminder.done == 0) {
                                    reminder.done = 1;
                                    reminderIds.add(reminder.reminderID);
                                }
                            }
                            if (WKReader.isNotEmpty(reminderIds))
                                MsgModel.getInstance().doneReminder(reminderIds);
                        }
                        MsgModel.getInstance().offsetMsg(item.channelID, item.channelType, null);
                        WKIM.getInstance().getReminderManager().saveOrUpdateReminders(list);
                        MsgModel.getInstance().clearUnread(item.channelID, item.channelType, 0, null);
                        boolean result = WKIM.getInstance().getConversationManager().deleteWitchChannel(item.channelID, item.channelType);
                        if (result) {
                            if (item.getWkChannel() != null && item.getWkChannel().top == 1) {
                                updateTop(item.channelID, item.channelType, 0);
                            }
                            WKIM.getInstance().getMsgManager().clearWithChannel(item.channelID, item.channelType);
                        }
                    }
                });
            } else if (menu == ChatConversationAdapter.ItemMenu.top) {
                boolean top = false;
                if (item.getWkChannel() != null) {
                    top = item.getWkChannel().top == 1;
                }
                updateTop(item.channelID, item.channelType, top ? 0 : 1);
            } else if (menu == ChatConversationAdapter.ItemMenu.mute) {
                boolean mute = false;
                if (item.getWkChannel() != null) {
                    mute = item.getWkChannel().mute == 1;
                }
                //免打扰
                if (item.channelType == WKChannelType.GROUP) {
                    GroupModel.getInstance().updateGroupSetting(item.channelID, "mute", mute ? 0 : 1, (code, msg) -> {
                        if (code != HttpResponseCode.success) {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                } else {
                    FriendModel.getInstance().updateUserSetting(item.channelID, "mute", mute ? 0 : 1, (code, msg) -> {
                        if (code != HttpResponseCode.success) {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                }
            }
        });
        //频道刷新监听 - 使用缓存快速查找
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("chat_fragment_refresh_channel", (channel, isEnd) -> {
            if (channel != null && !TextUtils.isEmpty(channel.channelID)) {
                if (isExpiredTopicRoomChannel(channel)) {
                    deleteExpiredTopicRoomLocal(channel.channelID, channel.channelType, 0);
                    removeConversationIfExists(channel.channelID, channel.channelType);
                    return;
                }
                int i = findConversationIndex(channel.channelID, channel.channelType);
                if (i >= 0) {
                    ChatConversationMsg msg = chatConversationAdapter.getData().get(i);
                    msg.uiConversationMsg.setWkChannel(channel);
                    // fixme 不能强制刷新整个列表，导致重新获取channel 频繁刷新UI卡顿
                    if (msg.isTop != channel.top) {
                        msg.isTop = channel.top;
                        sortMsg(chatConversationAdapter.getData());
                    } else {
                        msg.isRefreshChannelInfo = true;
                        msg.isResetCounter = true;
                        notifyRecycler(i, msg);
                    }
                    markUnreadCountDirty();
                    setAllCount();
                }
            }
        });
        //监听移除最近会话 - 使用缓存快速查找
        WKIM.getInstance().getConversationManager().addOnDeleteMsgListener("chat_fragment", (s, b) -> {
            if (!TextUtils.isEmpty(s)) {
                int i = findConversationIndex(s, b);
                if (i >= 0) {
                    boolean isResetCount = chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount > 0;
                    chatConversationAdapter.removeAt(i);
                    // 删除后重建索引
                    rebuildIndexCache();
                    if (isResetCount) {
                        markUnreadCountDirty();
                        setAllCount();
                    }
                }
            }
        });

        WKIM.getInstance().getCMDManager().addCmdListener("chat_fragment_cmd", wkCmd -> {
            if (wkCmd == null || TextUtils.isEmpty(wkCmd.cmdKey)) return;
            //监听正在输入
            switch (wkCmd.cmdKey) {
                case WKCMDKeys.wk_typing -> {
                    String channelID = wkCmd.paramJsonObject.optString("channel_id");
                    byte channelType = (byte) wkCmd.paramJsonObject.optInt("channel_type");
                    String from_uid = wkCmd.paramJsonObject.optString("from_uid");
                    String from_name = wkCmd.paramJsonObject.optString("from_name");
                    WKChannel channel = new WKChannel(from_uid, WKChannelType.PERSONAL);
                    channel.channelName = from_name;
                    if (TextUtils.isEmpty(from_name)) {
                        WKChannel tempChannel = WKIM.getInstance().getChannelManager().getChannel(from_uid, WKChannelType.PERSONAL);
                        if (tempChannel != null) {
                            channel.channelName = tempChannel.channelName;
                            channel.channelRemark = tempChannel.channelRemark;
                        }
                    }
                    if (from_uid.equals(WKConfig.getInstance().getUid())) return;
                    // 使用缓存快速查找
                    int i = findConversationIndex(channelID, channelType);
                    if (i >= 0) {
                        ChatConversationMsg msg = chatConversationAdapter.getData().get(i);
                        msg.isResetTyping = true;
                        msg.typingUserName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
                        msg.typingStartTime = WKTimeUtils.getInstance().getCurrentSeconds();
                        notifyRecycler(i, msg);
                        if (disposable == null) {
                            startTimer();
                        }
                    }
                }
                case WKCMDKeys.wk_onlineStatus -> {
                    if (wkCmd.paramJsonObject != null) {
                        int device_flag = wkCmd.paramJsonObject.optInt("device_flag");
                        int online = wkCmd.paramJsonObject.optInt("online");
                        String uid = wkCmd.paramJsonObject.optString("uid");
                        if (uid.equals(WKConfig.getInstance().getUid()) && device_flag == 1) {
                            wkVBinding.deviceIv.setVisibility(online == 1 ? View.VISIBLE : View.GONE);
                            WKSharedPreferencesUtil.getInstance().putInt(WKConfig.getInstance().getUid() + "_pc_online", online);
                        }
                    }
                }
                case "topicRoomDeleted" -> handleTopicRoomDeletedCmd(wkCmd.paramJsonObject);
                case "sync_channel_state" -> {
                    String fromUID = wkCmd.paramJsonObject.optString("from_uid");
                    String channelId = wkCmd.paramJsonObject.optString("channel_id");
                    int channelType = wkCmd.paramJsonObject.optInt("channel_type");
                    if (channelId.equals(WKConfig.getInstance().getUid())) {
                        channelId = fromUID;
                    }
                    String finalChannelId = channelId;
                    byte finalChannelType = (byte) channelType;
                    WKCommonModel.getInstance().getChannelState(channelId, (byte) channelType, channelState -> {
                        if (channelState != null) {
                            int isCalling = 0;
                            if (WKReader.isNotEmpty(channelState.call_info.getCalling_participants())) {
                                isCalling = 1;
                            }
                            // 使用缓存快速查找
                            int i = findConversationIndex(finalChannelId, finalChannelType);
                            if (i >= 0) {
                                chatConversationAdapter.getData().get(i).isCalling = isCalling;
                                chatConversationAdapter.notifyItemChanged(i);
                            }
                        }
                    });
                }
            }
        });
        // 监听刷新消息 - 使用缓存快速查找
        WKIM.getInstance().getMsgManager().addOnRefreshMsgListener("chat_fragment", (msg, left) -> {
            if (msg == null) return;
            int i = findConversationIndex(msg.channelID, msg.channelType);
            if (i >= 0) {
                ChatConversationMsg convMsg = chatConversationAdapter.getData().get(i);
                if (convMsg.uiConversationMsg.getWkMsg() != null
                        && (convMsg.uiConversationMsg.getWkMsg().clientSeq == msg.clientSeq
                        || convMsg.uiConversationMsg.getWkMsg().clientMsgNO.equals(msg.clientMsgNO))) {
                    if (convMsg.uiConversationMsg.getWkMsg().status != msg.status || convMsg.uiConversationMsg.getWkMsg().remoteExtra.readedCount != msg.remoteExtra.readedCount) {
                        convMsg.isRefreshStatus = true;
                    }
                    if (convMsg.uiConversationMsg.getWkMsg().remoteExtra.revoke != msg.remoteExtra.revoke) {
                        convMsg.isResetContent = true;
                    }
                    convMsg.uiConversationMsg.getWkMsg().status = msg.status;
                    if (convMsg.uiConversationMsg.getWkMsg().remoteExtra.editedAt != msg.remoteExtra.editedAt) {
                        convMsg.uiConversationMsg.getWkMsg().remoteExtra.editedAt = msg.remoteExtra.editedAt;
                        convMsg.uiConversationMsg.getWkMsg().remoteExtra.contentEdit = msg.remoteExtra.contentEdit;
                        WKIMUtils.getInstance().resetMsgProhibitWord(convMsg.uiConversationMsg.getWkMsg());
                    }
                    convMsg.uiConversationMsg.getWkMsg().remoteExtra.revoker = msg.remoteExtra.revoker;
                    convMsg.uiConversationMsg.getWkMsg().remoteExtra.revoke = msg.remoteExtra.revoke;
                    convMsg.uiConversationMsg.getWkMsg().remoteExtra.unreadCount = msg.remoteExtra.unreadCount;
                    convMsg.uiConversationMsg.getWkMsg().remoteExtra.readedCount = msg.remoteExtra.readedCount;
                    convMsg.uiConversationMsg.getWkMsg().messageID = msg.messageID;
                    refreshIds.add(i);
                }
            }
            if (left && WKReader.isNotEmpty(refreshIds)) {
                for (int j = 0, size = refreshIds.size(); j < size; j++) {
                    int idx = refreshIds.get(j);
                    notifyRecycler(idx, chatConversationAdapter.getData().get(idx));
                }
                refreshIds.clear();
            }
        });
        WKIM.getInstance().getMsgManager().addOnClearMsgListener("chat_fragment", (channelID, channelType, fromUID) -> {
            if (TextUtils.isEmpty(fromUID)) {
                // 使用缓存快速查找
                int i = findConversationIndex(channelID, channelType);
                if (i >= 0) {
                    ChatConversationMsg msg = chatConversationAdapter.getData().get(i);
                    msg.uiConversationMsg.setWkMsg(null);
                    msg.isResetContent = true;
                    notifyRecycler(i, msg);
                }
            }
        });
        WKIM.getInstance().getReminderManager().addOnNewReminderListener("chat_fragment", list -> {
            if (WKReader.isEmpty(list) || WKReader.isEmpty(chatConversationAdapter.getData()))
                return;
            for (WKReminder reader : list) {
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (reader.done == 0
                            && !TextUtils.isEmpty(reader.messageID)
                            && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null
                            && !TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().messageID)
                            && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null
                            && reader.messageID.equals(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().messageID)) {
                        chatConversationAdapter.getData().get(i).isResetReminders = true;
                        notifyRecycler(i, chatConversationAdapter.getData().get(i));
                        break;
                    }
                }
            }
        });
        // 监听刷新最近列表
        WKIM.getInstance().getConversationManager().addOnRefreshMsgListListener("chat_fragment", list -> {
            if (WKReader.isEmpty(list)) {
                return;
            }
            if (list.size() == 1) {
                WKUIConversationMsg single = list.get(0);
                if (isExpiredTopicRoomConversation(single)) {
                    deleteExpiredTopicRoomLocal(single);
                    removeConversationIfExists(single.channelID, single.channelType);
                    return;
                }
                resetData(single, true);
                return;
            }

            if (chatConversationAdapter.getData().isEmpty()) {
                List<ChatConversationMsg> uiList = new ArrayList<>();
                for (WKUIConversationMsg uiConversationMsg : list) {
                    if (isExpiredTopicRoomConversation(uiConversationMsg)) {
                        deleteExpiredTopicRoomLocal(uiConversationMsg);
                        continue;
                    }
                    uiList.add(new ChatConversationMsg(uiConversationMsg));
                }
                sortMsg(uiList);
                setAllCount();
                return;
            }
            List<ChatConversationMsg> uiList = new ArrayList<>();
            // 多条
            for (WKUIConversationMsg uiConversationMsg : list) {
                if (isExpiredTopicRoomConversation(uiConversationMsg)) {
                    deleteExpiredTopicRoomLocal(uiConversationMsg);
                    removeConversationIfExists(uiConversationMsg.channelID, uiConversationMsg.channelType);
                    continue;
                }
                boolean isAdd = true;
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (!TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID) && !TextUtils.isEmpty(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == uiConversationMsg.channelType) {
//                            if (!isEnd) {
//                                isAdd = false;
//                                chatConversationAdapter.getData().get(i).uiConversationMsg = uiConversationMsg;
//                                break;
//                            }
                        isAdd = false;
                        if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq != uiConversationMsg.lastMsgSeq || chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp || (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null && uiConversationMsg.getWkMsg() != null && !chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().clientMsgNO.equals(uiConversationMsg.getWkMsg().clientMsgNO))) {
                            chatConversationAdapter.getData().get(i).isResetTyping = true;
                            chatConversationAdapter.getData().get(i).typingUserName = "";
                            chatConversationAdapter.getData().get(i).typingStartTime = 0;
                            chatConversationAdapter.getData().get(i).isRefreshStatus = true;
                        }
                        if (chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount != uiConversationMsg.unreadCount) {
                            chatConversationAdapter.getData().get(i).isResetCounter = true;
                        }
                        if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp) {
                            chatConversationAdapter.getData().get(i).isResetTime = true;
                        }
                        chatConversationAdapter.getData().get(i).uiConversationMsg.setWkMsg(uiConversationMsg.getWkMsg());
                        if (!chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo.equals(uiConversationMsg.clientMsgNo)) {
                            chatConversationAdapter.getData().get(i).isResetContent = true;
                        }
                        WKIMUtils.getInstance().resetMsgProhibitWord(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg());
                        // todo 比较是否真的改过提醒内容
                        chatConversationAdapter.getData().get(i).isResetReminders = true;
                        chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                        chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo = uiConversationMsg.clientMsgNo;
                        chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount = uiConversationMsg.unreadCount;
                        chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                        chatConversationAdapter.getData().get(i).uiConversationMsg.setRemoteMsgExtra(uiConversationMsg.getRemoteMsgExtra());

                        chatConversationAdapter.getData().get(i).uiConversationMsg.setReminderList(uiConversationMsg.getReminderList());
                        chatConversationAdapter.getData().get(i).uiConversationMsg.localExtraMap = null;
                        notifyRecycler(i, chatConversationAdapter.getData().get(i));
                        setAllCount();
                        break;
                    }
                }
                if (isAdd) {
                    uiList.add(new ChatConversationMsg(uiConversationMsg));
                }
            }
//            if (!uiList.isEmpty()) {
//                uiList.addAll(chatConversationAdapter.getData());
//                sortMsg(uiList);
//                setAllCount();
//            } else {
//                resetData(list.get(0), true);
//            }
            uiList.addAll(chatConversationAdapter.getData());
            sortMsg(uiList);
            setAllCount();
        });
//        WKIM.getInstance().getConversationManager().addOnRefreshMsgListener("chat_fragment", this::resetData);
        // 监听连接状态
        WKIM.getInstance().getConnectionManager().addOnConnectionStatusListener("chat_fragment", (i, reason) -> {
            if (wkVBinding.textSwitcher.getTag() != null) {
                Object tag = wkVBinding.textSwitcher.getTag();
                if (tag instanceof Integer) {
                    int tag1 = (int) tag;
                    if (tag1 == i) {
                        return;
                    }
                }
            }
            if (i == WKConnectStatus.syncMsg) {
                wkVBinding.textSwitcher.setText(getString(R.string.sync_msg));
            } else if (i == WKConnectStatus.success) {
                wkVBinding.textSwitcher.setText(getString(R.string.app_name));
            } else if (i == WKConnectStatus.connecting) {
                wkVBinding.textSwitcher.setText(getString(R.string.connecting));
            } else if (i == WKConnectStatus.noNetwork) {
                wkVBinding.textSwitcher.setText(getString(R.string.network_error_tips));
            } else if (i == WKConnectStatus.kicked) {
                int from = 0;
                if (reason.equals(WKConnectReason.ReasonConnectKick)) {
                    from = 1;
                }
                WKUIKitApplication.getInstance().exitLogin(from);
            }
            wkVBinding.textSwitcher.setTag(i);
            if (i == WKConnectStatus.success || i == WKConnectStatus.syncMsg) {
                EndpointManager.getInstance().invoke("wk_close_disconnect_screen", null);
                stopConnectTimer();
            } else if (i == WKConnectStatus.connecting) {
                // 正在连接/重连时不要弹全屏断线遮罩，允许用户继续操作本地缓存。
                EndpointManager.getInstance().invoke("wk_close_disconnect_screen", null);
                stopConnectTimer();
            } else if (i == WKConnectStatus.noNetwork) {
                // 真正无网络时再延迟提示，避免启动或短暂切后台时像死机。
                startConnectTimer();
            } else {
                stopConnectTimer();
            }
        });
        EndpointManager.getInstance().setMethod("", EndpointCategory.wkExitChat, object -> {
            if (object != null) {
                WKChannel channel = (WKChannel) object;
                // 使用缓存快速查找
                int i = findConversationIndex(channel.channelID, channel.channelType);
                if (i >= 0) {
                    boolean isResetCount = chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount > 0;
                    chatConversationAdapter.removeAt(i);
                    rebuildIndexCache();
                    if (isResetCount) {
                        markUnreadCountDirty();
                        setAllCount();
                    }
                }
            }
            return null;
        });

        EndpointManager.getInstance().setMethod("chat_cover", EndpointCategory.refreshProhibitWord, object -> {
            if (WKReader.isEmpty(chatConversationAdapter.getData())) {
                return 1;
            }
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (chatConversationAdapter.getData().get(i).uiConversationMsg != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().type == WKContentType.WK_TEXT) {
                    WKIMUtils.getInstance().resetMsgProhibitWord(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg());
                    chatConversationAdapter.notifyItemChanged(i);
                }
            }
            return 1;
        });

        EndpointManager.getInstance().setMethod("refresh_conversation_calling", object -> {
            if (WKReader.isNotEmpty(MsgModel.getInstance().channelStatus)) {
                for (WKChannelState state : MsgModel.getInstance().channelStatus) {
                    for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                        if (chatConversationAdapter.getData().get(i).uiConversationMsg != null
                                && !TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID)
                                && state.channel_id.equals(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID)) {
                            chatConversationAdapter.getData().get(i).isCalling = state.calling;
                            chatConversationAdapter.notifyItemChanged(i);
                        }
                    }
                }
                return null;
            }
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (chatConversationAdapter.getData().get(i).isCalling == 1) {
                    chatConversationAdapter.getData().get(i).isCalling = 0;
                    chatConversationAdapter.notifyItemChanged(i);
                }
            }
            return null;
        });
    }


    @Override
    protected void initData() {
        getData();
    }

    private void getData() {
        getChatMsg();
    }


    private void getChatMsg() {
        WKIM.getInstance().getConversationManager().getAll(list -> {
            List<ChatConversationMsg> tempList = new ArrayList<>();
            if (WKReader.isNotEmpty(list)) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    WKUIConversationMsg conversation = list.get(i);
                    if (isExpiredTopicRoomConversation(conversation)) {
                        deleteExpiredTopicRoomLocal(conversation);
                        continue;
                    }
                    tempList.add(new ChatConversationMsg(conversation));
                }
            }
            AndroidUtilities.runOnUIThread(() -> sortMsg(tempList));
        });

//        List<ChatConversationMsg> list = new ArrayList<>();
//        List<WKUIConversationMsg> tempList = WKIM.getInstance().getConversationManager().getAll();
//        if (WKReader.isNotEmpty(tempList)) {
//            for (int i = 0, size = tempList.size(); i < size; i++) {
//                list.add(new ChatConversationMsg(tempList.get(i)));
//            }
//        }
//        return list;
    }

    private void setAllCount() {
        if (!isUnreadCountDirty) {
            // 未读数未变化，直接使用缓存
            if (tabActivity != null) {
                tabActivity.setMsgCount(cachedUnreadCount);
            }
            return;
        }
        int allCount = 0;
        List<ChatConversationMsg> data = chatConversationAdapter.getData();
        for (int i = 0, size = data.size(); i < size; i++) {
            ChatConversationMsg msg = data.get(i);
            if (msg.uiConversationMsg.getWkChannel() != null && msg.uiConversationMsg.getWkChannel().mute == 0)
                allCount = allCount + msg.uiConversationMsg.unreadCount;
        }
        cachedUnreadCount = allCount;
        isUnreadCountDirty = false;
        if (tabActivity != null) {
            tabActivity.setMsgCount(allCount);
        }
        // EndpointManager.getInstance().invoke("refresh_chat_unread_count",allCount);
    }

    // 生成会话的唯一键
    private String getChannelKey(String channelID, byte channelType) {
        return channelID + "_" + channelType;
    }

    // 根据 channelID 和 channelType 快速查找位置
    private int findConversationIndex(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID)) return -1;
        String key = getChannelKey(channelID, channelType);
        Integer index = conversationIndexMap.get(key);
        if (index != null && index >= 0 && index < chatConversationAdapter.getData().size()) {
            ChatConversationMsg msg = chatConversationAdapter.getData().get(index);
            if (msg.uiConversationMsg.channelID.equals(channelID) && msg.uiConversationMsg.channelType == channelType) {
                return index;
            }
        }
        // 缓存失效，遍历查找并更新缓存
        List<ChatConversationMsg> data = chatConversationAdapter.getData();
        for (int i = 0, size = data.size(); i < size; i++) {
            ChatConversationMsg msg = data.get(i);
            if (!TextUtils.isEmpty(msg.uiConversationMsg.channelID)
                    && msg.uiConversationMsg.channelID.equals(channelID)
                    && msg.uiConversationMsg.channelType == channelType) {
                conversationIndexMap.put(key, i);
                return i;
            }
        }
        return -1;
    }

    // 重建索引缓存
    private void rebuildIndexCache() {
        conversationIndexMap.clear();
        List<ChatConversationMsg> data = chatConversationAdapter.getData();
        for (int i = 0, size = data.size(); i < size; i++) {
            ChatConversationMsg msg = data.get(i);
            if (!TextUtils.isEmpty(msg.uiConversationMsg.channelID)) {
                String key = getChannelKey(msg.uiConversationMsg.channelID, msg.uiConversationMsg.channelType);
                conversationIndexMap.put(key, i);
            }
        }
    }

    // 标记未读数需要重新计算
    private void markUnreadCountDirty() {
        isUnreadCountDirty = true;
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        tabActivity = (TabActivity) context;
    }

    private void resetChildData(WKUIConversationMsg uiConversationMsg, boolean isEnd) {
        if (WKReader.isNotEmpty(chatConversationAdapter.getData())) {
            boolean isAdd = true;
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                boolean isBreak = false;
                if (WKReader.isNotEmpty(chatConversationAdapter.getData().get(i).childList)) {
                    for (int j = 0, len = chatConversationAdapter.getData().get(i).childList.size(); j < len; j++) {
                        if (chatConversationAdapter.getData().get(i).childList.get(j).uiConversationMsg.channelID.equals(uiConversationMsg.channelID)) {
                            chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo = uiConversationMsg.clientMsgNo;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount += uiConversationMsg.unreadCount;
                            notifyRecycler(i, chatConversationAdapter.getData().get(i));
                            isBreak = true;
                            isAdd = false;
                        }
                    }
                }
                if (isBreak) break;
            }
            if (isAdd) {
                WKUIConversationMsg msg = new WKUIConversationMsg();
                msg.channelID = uiConversationMsg.parentChannelID;
                msg.channelType = uiConversationMsg.parentChannelType;
                msg.clientMsgNo = uiConversationMsg.clientMsgNo;
                msg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                msg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                msg.unreadCount = uiConversationMsg.unreadCount;
                msg.setReminderList(uiConversationMsg.getReminderList());
                msg.setRemoteMsgExtra(uiConversationMsg.getRemoteMsgExtra());

                ChatConversationMsg chatConversationMsg = new ChatConversationMsg(msg);
                ChatConversationMsg child = new ChatConversationMsg(uiConversationMsg);
                chatConversationMsg.childList = new ArrayList<>();
                chatConversationMsg.childList.add(child);
                if (!isEnd) {
                    chatConversationAdapter.addData(chatConversationMsg);
                } else {
                    int insertIndex = getInsertIndex(msg);
                    chatConversationAdapter.addData(insertIndex, chatConversationMsg);
                }
            }
        }
    }

    private void handleTopicRoomDeletedCmd(JSONObject paramJsonObject) {
        if (paramJsonObject == null) return;
        String channelID = paramJsonObject.optString("channel_id");
        if (TextUtils.isEmpty(channelID)) {
            channelID = paramJsonObject.optString("room_id");
        }
        int type = paramJsonObject.optInt("channel_type", WKChannelType.GROUP);
        byte channelType = (byte) type;
        if (TextUtils.isEmpty(channelID)) return;

        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        boolean isTopicRoom = channelID.startsWith("topic_") || isTopicRoomChannel(channel);
        if (!isTopicRoom) return;

        int unreadCount = 0;
        int index = findConversationIndex(channelID, channelType);
        if (index >= 0
                && chatConversationAdapter != null
                && chatConversationAdapter.getData() != null
                && index < chatConversationAdapter.getData().size()
                && chatConversationAdapter.getData().get(index).uiConversationMsg != null) {
            unreadCount = chatConversationAdapter.getData().get(index).uiConversationMsg.unreadCount;
        }
        deleteExpiredTopicRoomLocal(channelID, channelType, unreadCount);
        removeConversationIfExists(channelID, channelType);
    }

    private boolean isTopicRoomConversation(WKUIConversationMsg uiConversationMsg) {
        if (uiConversationMsg == null) return false;
        if (uiConversationMsg.channelType == WKChannelType.GROUP && !TextUtils.isEmpty(uiConversationMsg.channelID) && uiConversationMsg.channelID.startsWith("topic_")) {
            return true;
        }
        return isTopicRoomChannel(uiConversationMsg.getWkChannel());
    }

    private boolean isTopicRoomChannel(WKChannel channel) {
        if (channel == null) return false;
        if (channel.channelType == WKChannelType.GROUP && !TextUtils.isEmpty(channel.channelID) && channel.channelID.startsWith("topic_")) {
            return true;
        }
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

    private boolean isExpiredTopicRoomConversation(WKUIConversationMsg uiConversationMsg) {
        if (!isTopicRoomConversation(uiConversationMsg)) return false;
        long expireAt = getTopicRoomExpireAt(uiConversationMsg);
        return expireAt > 0 && expireAt <= System.currentTimeMillis();
    }

    private boolean isExpiredTopicRoomChannel(WKChannel channel) {
        if (!isTopicRoomChannel(channel)) return false;
        long expireAt = getTopicRoomExpireAt(channel);
        return expireAt > 0 && expireAt <= System.currentTimeMillis();
    }

    private long getTopicRoomExpireAt(WKUIConversationMsg uiConversationMsg) {
        if (uiConversationMsg == null) return 0L;
        long expireAt = getTopicRoomExpireAt(uiConversationMsg.getWkChannel());
        if (expireAt > 0) return expireAt;
        return getLongExtra(uiConversationMsg.localExtraMap, "expire_at");
    }

    private long getTopicRoomExpireAt(WKChannel channel) {
        if (channel == null) return 0L;
        long expireAt = getLongExtra(channel.localExtra, "expire_at");
        if (expireAt <= 0) expireAt = getLongExtra(channel.remoteExtraMap, "expire_at");
        return expireAt;
    }

    private long getLongExtra(java.util.Map<String, Object> map, String key) {
        if (map == null || TextUtils.isEmpty(key)) return 0L;
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void startTopicRoomExpireWatcher() {
        if (topicRoomExpireDisposable != null && !topicRoomExpireDisposable.isDisposed()) return;
        topicRoomExpireDisposable = Observable.interval(0, TOPIC_ROOM_EXPIRE_CHECK_SECONDS, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(value -> cleanupExpiredTopicRoomConversations(), throwable -> {
                });
    }

    private void cleanupExpiredTopicRoomConversations() {
        if (chatConversationAdapter == null || WKReader.isEmpty(chatConversationAdapter.getData())) return;
        List<WKUIConversationMsg> expiredList = new ArrayList<>();
        for (ChatConversationMsg item : chatConversationAdapter.getData()) {
            if (item != null && isExpiredTopicRoomConversation(item.uiConversationMsg)) {
                expiredList.add(item.uiConversationMsg);
            }
        }
        if (WKReader.isEmpty(expiredList)) return;
        for (WKUIConversationMsg item : expiredList) {
            deleteExpiredTopicRoomLocal(item);
        }
        boolean changed = false;
        boolean unreadChanged = false;
        for (WKUIConversationMsg item : expiredList) {
            int index = findConversationIndex(item.channelID, item.channelType);
            if (index >= 0) {
                ChatConversationMsg removed = chatConversationAdapter.getData().get(index);
                if (removed != null && removed.uiConversationMsg != null && removed.uiConversationMsg.unreadCount > 0) {
                    unreadChanged = true;
                }
                chatConversationAdapter.removeAt(index);
                rebuildIndexCache();
                changed = true;
            }
        }
        if (changed) {
            if (unreadChanged) markUnreadCountDirty();
            setAllCount();
        }
    }

    private boolean isStrictTopicRoomChannel(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID) || channelType != WKChannelType.GROUP) return false;
        if (channelID.startsWith("topic_")) return true;
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
            return isTopicRoomChannel(channel);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void deleteExpiredTopicRoomLocal(WKUIConversationMsg item) {
        if (item == null) return;
        deleteExpiredTopicRoomLocal(item.channelID, item.channelType, item.unreadCount);
    }

    private void deleteExpiredTopicRoomLocal(String channelID, byte channelType, int unreadCount) {
        if (TextUtils.isEmpty(channelID)) return;
        if (!isStrictTopicRoomChannel(channelID, channelType)) return;
        List<WKReminder> reminders = WKIM.getInstance().getReminderManager().getReminders(channelID, channelType);
        if (WKReader.isNotEmpty(reminders)) {
            List<Long> reminderIds = new ArrayList<>();
            for (WKReminder reminder : reminders) {
                if (reminder != null && reminder.done == 0) {
                    reminder.done = 1;
                    reminderIds.add(reminder.reminderID);
                }
            }
            if (WKReader.isNotEmpty(reminderIds)) {
                MsgModel.getInstance().doneReminder(reminderIds);
            }
            WKIM.getInstance().getReminderManager().saveOrUpdateReminders(reminders);
        }
        MsgModel.getInstance().clearUnread(channelID, channelType, 0, null);
        WKIM.getInstance().getConversationManager().deleteWitchChannel(channelID, channelType);
        // Never clear message history here. A wrong topic-room flag must not wipe normal chats.
    }

    private void removeConversationIfExists(String channelID, byte channelType) {
        int index = findConversationIndex(channelID, channelType);
        if (index < 0) return;
        boolean unreadChanged = chatConversationAdapter.getData().get(index).uiConversationMsg.unreadCount > 0;
        chatConversationAdapter.removeAt(index);
        rebuildIndexCache();
        if (unreadChanged) markUnreadCountDirty();
        setAllCount();
    }

    private int msgCount = 0;

    private void resetData(WKUIConversationMsg uiConversationMsg, boolean isEnd) {
        if (uiConversationMsg == null) {
            return;
        }
        if (isExpiredTopicRoomConversation(uiConversationMsg)) {
            deleteExpiredTopicRoomLocal(uiConversationMsg);
            removeConversationIfExists(uiConversationMsg.channelID, uiConversationMsg.channelType);
            if (isEnd) {
                sortMsg(chatConversationAdapter.getData());
            }
            return;
        }
        // 话题聊天室已经是原生群会话：进入过的话题应该出现在消息列表里，
        // 最后消息、未读、@提醒和排序都交给唐僧原生会话系统处理。
        // || (uiConversationMsg.getWkChannel() != null && uiConversationMsg.getWkChannel().follow == 0 && uiConversationMsg.channelType == WKChannelType.PERSONAL)
        if (uiConversationMsg.isDeleted == 1 || TextUtils.equals(uiConversationMsg.channelID, "0")) {
            if (isEnd) {
                sortMsg(chatConversationAdapter.getData());
            }
            return;
        }
        if (!TextUtils.isEmpty(uiConversationMsg.parentChannelID)) {
            resetChildData(uiConversationMsg, isEnd);
            return;
        }
        boolean isAdd = true;
        int index = -1;
        boolean isSort = false;
        if (WKReader.isNotEmpty(chatConversationAdapter.getData())) {
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (!TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID) && !TextUtils.isEmpty(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == uiConversationMsg.channelType) {
                    if (!isEnd) {
                        isAdd = false;
                        chatConversationAdapter.getData().get(i).uiConversationMsg = uiConversationMsg;
                        break;
                    }
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq != uiConversationMsg.lastMsgSeq || chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp || (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null && uiConversationMsg.getWkMsg() != null && !chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().clientMsgNO.equals(uiConversationMsg.getWkMsg().clientMsgNO))) {
                        isSort = true;
                        chatConversationAdapter.getData().get(i).isResetTyping = true;
                        chatConversationAdapter.getData().get(i).typingUserName = "";
                        chatConversationAdapter.getData().get(i).typingStartTime = 0;
                        chatConversationAdapter.getData().get(i).isRefreshStatus = true;
                        index = i;
                    }
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount != uiConversationMsg.unreadCount) {
                        chatConversationAdapter.getData().get(i).isResetCounter = true;
                    }
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp) {
                        chatConversationAdapter.getData().get(i).isResetTime = true;
                    }
                    chatConversationAdapter.getData().get(i).uiConversationMsg.setWkMsg(uiConversationMsg.getWkMsg());
                    if (!chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo.equals(uiConversationMsg.clientMsgNo)) {
                        chatConversationAdapter.getData().get(i).isResetContent = true;
                    }
                    WKIMUtils.getInstance().resetMsgProhibitWord(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg());
                    // todo 比较是否真的改过提醒内容
                    chatConversationAdapter.getData().get(i).isResetReminders = true;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo = uiConversationMsg.clientMsgNo;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount = uiConversationMsg.unreadCount;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.setRemoteMsgExtra(uiConversationMsg.getRemoteMsgExtra());

                    chatConversationAdapter.getData().get(i).uiConversationMsg.setReminderList(uiConversationMsg.getReminderList());
                    chatConversationAdapter.getData().get(i).uiConversationMsg.localExtraMap = null;
                    isAdd = false;
                    notifyRecycler(i, chatConversationAdapter.getData().get(i));
                    setAllCount();
                    break;
                }
            }
        }
        if (!isEnd) msgCount++;

        if (isAdd) {
            if (!isEnd) {
                chatConversationAdapter.addData(new ChatConversationMsg(uiConversationMsg));
            } else {
                int insertIndex = getInsertIndex(uiConversationMsg);
                chatConversationAdapter.addData(insertIndex, new ChatConversationMsg(uiConversationMsg));
            }
            setAllCount();
        }
        if (isEnd) {
            if (isSort && msgCount == 0) {
                int insertIndex = getInsertIndex(uiConversationMsg);
                if (insertIndex != index) {
                    if (index != -1) chatConversationAdapter.removeAt(index);
                    chatConversationAdapter.addData(insertIndex, new ChatConversationMsg(uiConversationMsg));
                }
            } else {
                if (msgCount > 0) {
                    msgCount = 0;
                    sortMsg(chatConversationAdapter.getData());
                }
            }
        }
    }

    //排序消息 - 在后台线程执行耗时操作
    private void sortMsg(List<ChatConversationMsg> list) {
        // 取消之前的排序任务，避免并发问题
        if (sortDisposable != null && !sortDisposable.isDisposed()) {
            sortDisposable.dispose();
        }

        // 排序前先过滤已过期的话题聊天室，避免旧数据再次被 setList 带回消息列表。
        // 先收集再删除，避免在遍历 adapter 数据时触发删除监听导致并发修改。
        List<ChatConversationMsg> cleanedList = new ArrayList<>();
        List<WKUIConversationMsg> expiredList = new ArrayList<>();
        if (WKReader.isNotEmpty(list)) {
            for (ChatConversationMsg item : list) {
                if (item == null || item.uiConversationMsg == null) {
                    continue;
                }
                if (isExpiredTopicRoomConversation(item.uiConversationMsg)) {
                    expiredList.add(item.uiConversationMsg);
                    continue;
                }
                cleanedList.add(item);
            }
        }
        if (WKReader.isNotEmpty(expiredList)) {
            for (WKUIConversationMsg item : expiredList) {
                deleteExpiredTopicRoomLocal(item);
            }
        }

        // 复制列表避免并发修改
        List<ChatConversationMsg> listCopy = new ArrayList<>(cleanedList);

        sortDisposable = Observable.fromCallable(() -> {
                    // 在后台线程执行分组和排序
                    groupMsg(listCopy);
                    // 使用 Long.compare 避免溢出
                    listCopy.sort((conversationMsg, t1) ->
                            Long.compare(t1.uiConversationMsg.lastMsgTimestamp, conversationMsg.uiConversationMsg.lastMsgTimestamp));

                    List<ChatConversationMsg> topList = new ArrayList<>();
                    List<ChatConversationMsg> normalList = new ArrayList<>();
                    for (int i = 0, size = listCopy.size(); i < size; i++) {
                        ChatConversationMsg msg = listCopy.get(i);
                        if (msg == null) {
                            continue;
                        }
                        if (msg.uiConversationMsg.getWkChannel() != null && msg.uiConversationMsg.getWkChannel().top == 1) {
                            topList.add(msg);
                        } else {
                            normalList.add(msg);
                        }
                    }
                    List<ChatConversationMsg> tempList = new ArrayList<>(topList.size() + normalList.size());
                    tempList.addAll(topList);
                    tempList.addAll(normalList);
                    return tempList;
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tempList -> {
                    chatConversationAdapter.setList(tempList);
                    rebuildIndexCache();
                    markUnreadCountDirty();
                    setAllCount();
                }, throwable -> {
                    // 发生错误时的降级处理
                    AndroidUtilities.runOnUIThread(() -> {
                        chatConversationAdapter.setList(listCopy);
                        rebuildIndexCache();
                        markUnreadCountDirty();
                        setAllCount();
                    });
                });
    }

    private void openPartnerProfile() {
        try {
            Intent intent = new Intent();
            intent.setClassName(requireContext(), "com.chat.partner.profile.PartnerProfileActivity");
            intent.putExtra("uid", WKConfig.getInstance().getUid());
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(getActivity(), MyInfoActivity.class));
        }
    }

    private void refreshTopProfileAvatar() {
        if (wkVBinding == null || wkVBinding.profileAvatarView == null) return;
        wkVBinding.profileAvatarView.setSize(38);
        wkVBinding.profileAvatarView.showAvatar(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
    }

    private void initSideMenuListeners() {
        SingleClickUtil.onSingleClick(wkVBinding.sideProfileLayout, view -> {
            closeSideMenu();
            startActivity(new Intent(getActivity(), MyInfoActivity.class));
        });
        SingleClickUtil.onSingleClick(wkVBinding.sideNewMsgNoticeTv, view -> openSideMenuActivity(MsgNoticesSettingActivity.class));
        SingleClickUtil.onSingleClick(wkVBinding.sideDarkModeTv, view -> openSideMenuActivity(WKThemeSettingActivity.class));
        SingleClickUtil.onSingleClick(wkVBinding.sideLanguageTv, view -> openSideMenuActivity(WKLanguageActivity.class));
        SingleClickUtil.onSingleClick(wkVBinding.sideFontSizeTv, view -> openSideMenuActivity(WKSetFontSizeActivity.class));
        SingleClickUtil.onSingleClick(wkVBinding.sideClearCacheLayout, view -> clearImageCacheFromSideMenu());
        SingleClickUtil.onSingleClick(wkVBinding.sideLogoutTv, view -> logoutFromSideMenu());
    }

    private void openSideMenuActivity(Class<?> activityClass) {
        closeSideMenu();
        startActivity(new Intent(getActivity(), activityClass));
    }

    private void refreshSideMenuUserInfo() {
        if (wkVBinding == null || wkVBinding.sideAvatarView == null) return;
        wkVBinding.sideAvatarView.setSize(58);
        if (WKConfig.getInstance().getUserInfo() != null) {
            wkVBinding.sideNameTv.setText(WKConfig.getInstance().getUserInfo().name);
        }
        wkVBinding.sideAvatarView.showAvatar(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
        refreshSideCacheSize();
    }

    private void refreshSideCacheSize() {
        Context context = getContext();
        if (context == null) return;
        new Thread(() -> {
            String cacheSize = "0.00M";
            try {
                cacheSize = DataCleanManager.getTotalCacheSize(context);
                if ("0.0Byte".equalsIgnoreCase(cacheSize)) {
                    cacheSize = "0.00M";
                }
            } catch (Exception ignored) {
            }
            String finalCacheSize = cacheSize;
            AndroidUtilities.runOnUIThread(() -> {
                if (isAdded() && wkVBinding != null && wkVBinding.sideCacheSizeTv != null) {
                    wkVBinding.sideCacheSizeTv.setText(finalCacheSize);
                }
            });
        }).start();
    }

    private void clearImageCacheFromSideMenu() {
        WKDialogUtils.getInstance().showDialog(getActivity(), getString(R.string.clear_img_cache), getString(R.string.clear_img_cache_tips), true, "", getString(R.string.sure), 0, Theme.colorAccount, index -> {
            if (index == 1) {
                try {
                    DataCleanManager.clearAllCache(requireContext());
                } catch (Exception ignored) {
                }
                wkVBinding.sideCacheSizeTv.setText("0.00M");
            }
        });
    }

    private void logoutFromSideMenu() {
        WKDialogUtils.getInstance().showDialog(getActivity(), getString(R.string.login_out), getString(R.string.login_out_dialog), true, "", getString(R.string.login_out), 0, 0, index -> {
            if (index == 1) {
                UserModel.getInstance().quit(null);
                WKUIKitApplication.getInstance().exitLogin(0);
            }
        });
    }

    private void openSideMenu() {
        refreshSideMenuUserInfo();
        wkVBinding.sideMenuMask.setVisibility(View.VISIBLE);
        wkVBinding.sideMenuPanel.setVisibility(View.VISIBLE);
        wkVBinding.sideMenuPanel.post(() -> {
            wkVBinding.sideMenuPanel.setTranslationX(wkVBinding.sideMenuPanel.getWidth());
            wkVBinding.sideMenuPanel.animate().translationX(0).setDuration(180).start();
        });
    }

    private void closeSideMenu() {
        if (wkVBinding == null || wkVBinding.sideMenuPanel.getVisibility() != View.VISIBLE) return;
        wkVBinding.sideMenuPanel.animate()
                .translationX(wkVBinding.sideMenuPanel.getWidth())
                .setDuration(160)
                .withEndAction(() -> {
                    if (wkVBinding == null) return;
                    wkVBinding.sideMenuPanel.setVisibility(View.GONE);
                    wkVBinding.sideMenuMask.setVisibility(View.GONE);
                })
                .start();
    }

    public boolean closeSideMenuIfOpen() {
        if (wkVBinding != null && wkVBinding.sideMenuPanel.getVisibility() == View.VISIBLE) {
            closeSideMenu();
            return true;
        }
        return false;
    }

    //检测正在输入的定时器
    private void startTimer() {
        Observable.interval(0, 1, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<>() {
            @Override
            public void onComplete() {
            }

            @Override
            public void onError(@NonNull Throwable e) {
            }

            @Override
            public void onSubscribe(@NonNull Disposable d) {
                disposable = d;
            }

            @Override
            public void onNext(@NonNull Long value) {
                boolean isCancel = true;
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (chatConversationAdapter.getData().get(i).typingStartTime > 0) {
                        long typingStartTime = chatConversationAdapter.getData().get(i).typingStartTime;
                        if (WKTimeUtils.getInstance().getCurrentSeconds() - typingStartTime >= 8) {
                            chatConversationAdapter.getData().get(i).isResetTyping = true;
                            chatConversationAdapter.getData().get(i).typingStartTime = 0;
                            chatConversationAdapter.getData().get(i).typingUserName = "";
                            chatConversationAdapter.getData().get(i).isResetContent = true;
                            notifyRecycler(i, chatConversationAdapter.getData().get(i));
//                                    chatConversationAdapter.notifyItemChanged(i, chatConversationAdapter.getData().get(i));
                        }
                        isCancel = false;
                    }
                }
                if (disposable != null && isCancel) {
                    disposable.dispose();
                    disposable = null;
                }
            }
        });
    }

    private void releaseHomeViewPager() {
        if (wkVBinding != null && wkVBinding.homeViewPager != null) {
            if (homePagerCallback != null) {
                wkVBinding.homeViewPager.unregisterOnPageChangeCallback(homePagerCallback);
            }
            wkVBinding.homeViewPager.setAdapter(null);
        }
        homePagerCallback = null;
        homePagerScrollState = ViewPager2.SCROLL_STATE_IDLE;
    }

    @Override
    public void onDestroy() {
        releaseHomeViewPager();
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }
        // 取消排序任务
        if (sortDisposable != null) {
            sortDisposable.dispose();
            sortDisposable = null;
        }
        if (topicRoomExpireDisposable != null) {
            topicRoomExpireDisposable.dispose();
            topicRoomExpireDisposable = null;
        }
        // 清理缓存
        conversationIndexMap.clear();
        WKIM.getInstance().getConversationManager().removeOnRefreshMsgListListener("chat_fragment");
        WKIM.getInstance().getConversationManager().removeOnRefreshMsgListener("chat_fragment");
        WKIM.getInstance().getConversationManager().removeOnDeleteMsgListener("chat_fragment");
        WKIM.getInstance().getCMDManager().removeCmdListener("chat_fragment_cmd");
        WKIM.getInstance().getMsgManager().removeRefreshMsgListener("chat_fragment");
        WKIM.getInstance().getConnectionManager().removeOnConnectionStatusListener("chat_fragment");
        WKIM.getInstance().getMsgManager().removeSendMsgAckListener("chat_fragment");
        WKIM.getInstance().getReminderManager().removeNewReminderListener("chat_fragment");
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSideMenuUserInfo();
        refreshTopProfileAvatar();
        updateContactsBadge();
        startTopicRoomExpireWatcher();
        cleanupExpiredTopicRoomConversations();
        int pcOnline = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_pc_online");
        wkVBinding.deviceIv.setVisibility(pcOnline == 1 ? View.VISIBLE : View.GONE);
//        String appLoginType = String.format(getString(R.string.pc_login), getString(R.string.app_name));
//        int muteForApp = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_mute_of_app");
//        if (muteForApp == 1) {
//            pcLoginTv.setText(String.format("%s %s", appLoginType, getString(R.string.wk_kit_phone_notice_close)));
//        } else pcLoginTv.setText(appLoginType);
        EndpointManager.getInstance().setMethod("scroll_to_unread_channel", object -> {
            scrollToUnreadChannel();
            return null;
        });
    }

    private void startConnectTimer() {
        stopConnectTimer();
        connectTimer = new Timer();
        connectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                AndroidUtilities.runOnUIThread(() -> {
                    if (isAdded() && getContext() != null) {
                        EndpointManager.getInstance().invoke("show_disconnect_screen", getContext());
                    }
                });
            }
        }, 8000);
    }

    private void stopConnectTimer() {
        if (connectTimer != null) {
            connectTimer.cancel();
            connectTimer = null;
        }
    }

    private int getTopChatCount() {
        int count = 0;
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
            if (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel() != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel().top == 1)
                count++;
        }
        return count;
    }

    private int getInsertIndex(WKUIConversationMsg msg) {
        if (msg.getWkChannel() != null && msg.getWkChannel().top == 1) return 0;
        return getTopChatCount();
    }

    private void notifyRecycler(int index, ChatConversationMsg msg) {
        if (wkVBinding.recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE || (!wkVBinding.recyclerView.isComputingLayout())) {
            chatConversationAdapter.notifyItemChanged(index, msg);
        }
    }

    private void updateTop(String channelID, byte channelType, int top) {
        if (channelType == WKChannelType.PERSONAL) {
            FriendModel.getInstance().updateUserSetting(channelID, "top", top, (code, msg) -> {
                if (code != HttpResponseCode.success) {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        } else {
            GroupModel.getInstance().updateGroupSetting(channelID, "top", top, (code, msg) -> {
                if (code != HttpResponseCode.success) {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        }

    }

    private void groupMsg(List<ChatConversationMsg> list) {
        // 将消息分组
        HashMap<String, List<ChatConversationMsg>> msgMap = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            if (!TextUtils.isEmpty(list.get(i).uiConversationMsg.parentChannelID)) {
                String key = list.get(i).uiConversationMsg.parentChannelID + "@" + list.get(i).uiConversationMsg.parentChannelType;
                List<ChatConversationMsg> tempList = null;
                if (msgMap.containsKey(key)) {
                    tempList = msgMap.get(key);
                }
                if (tempList == null) tempList = new ArrayList<>();
                tempList.add(list.get(i));
                msgMap.put(key, tempList);
                list.remove(i);
                i--;
            }
        }

        if (!msgMap.isEmpty()) {
            for (String key : msgMap.keySet()) {
                List<ChatConversationMsg> msgList = msgMap.get(key);
                WKUIConversationMsg lastMsg = new WKUIConversationMsg();
//                if (msgList != null && msgList.size() > 0) {
//                    msg.channelID = msgList.get(0).uiConversationMsg.parentChannelID;
//                    msg.channelType = msgList.get(0).uiConversationMsg.parentChannelType;
//                }
                //   Log.e("消息信息",msg.clientMsgNo+"");
                //  ChatConversationMsg lastMsg = new ChatConversationMsg(msg);
                //lastMsg.childList = msgList;
                ChatConversationMsg lastConvMsg = null;
                if (WKReader.isNotEmpty(msgList)) {
                    lastMsg.channelID = msgList.get(0).uiConversationMsg.parentChannelID;
                    lastMsg.channelType = msgList.get(0).uiConversationMsg.parentChannelType;
                    int unreadCount = 0;
                    List<WKReminder> reminderList = new ArrayList<>();
                    for (int i = 0, size = msgList.size(); i < size; i++) {
                        WKUIConversationMsg msg = msgList.get(i).uiConversationMsg;
                        if (msg.lastMsgSeq > lastMsg.lastMsgSeq) {
                            lastMsg.lastMsgSeq = msg.lastMsgSeq;
                        }
                        if (msg.lastMsgTimestamp > lastMsg.lastMsgTimestamp) {
                            lastMsg.lastMsgTimestamp = msg.lastMsgTimestamp;
                            lastMsg.clientMsgNo = msg.clientMsgNo;
                        }
                        unreadCount += msg.unreadCount;
                        List<WKReminder> tempReminders = msg.getReminderList();
                        if (WKReader.isNotEmpty(tempReminders)) {
                            reminderList.addAll(tempReminders);
                        }
                    }
                    lastMsg.unreadCount = unreadCount;
                    lastMsg.setReminderList(reminderList);

                    lastConvMsg = new ChatConversationMsg(lastMsg);
                    lastConvMsg.childList = msgList;
                }
                if (lastConvMsg != null)
                    list.add(lastConvMsg);
            }
        }
    }

    long lastMessageTime = 0L;

    private void scrollToUnreadChannel() {
        long firstTime = 0L;
        int firstIndex = 0;
        boolean isScrollToFirstIndex = true;
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) wkVBinding.recyclerView.getLayoutManager();
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
            if (chatConversationAdapter.getData().get(i).getUnReadCount() > 0 && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel() != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel().mute == 0) {
                if (firstTime == 0) {
                    firstTime = chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp;
                    firstIndex = i;
                }
                if (lastMessageTime == 0 || lastMessageTime > chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp) {
                    lastMessageTime = chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp;
                    if (linearLayoutManager != null) {
                        linearLayoutManager.scrollToPositionWithOffset(i, 0);
                    }
                    isScrollToFirstIndex = false;
                    break;
                }
            }

        }
        if (isScrollToFirstIndex) {
            lastMessageTime = firstTime;
            if (linearLayoutManager != null) {
                linearLayoutManager.scrollToPositionWithOffset(firstIndex, 0);
            }
        }
    }

}
