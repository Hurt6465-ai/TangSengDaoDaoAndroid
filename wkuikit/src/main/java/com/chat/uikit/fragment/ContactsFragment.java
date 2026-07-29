package com.chat.uikit.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseFragment;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ContactsMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.HanziToPinyin;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener;
import com.chat.uikit.R;
import com.chat.uikit.contacts.FriendAdapter;
import com.chat.uikit.contacts.FriendUIEntity;
import com.chat.uikit.databinding.FragContactsLayoutBinding;
import com.chat.uikit.search.SearchAllActivity;
import com.chat.uikit.search.remote.GlobalActivity;
import com.chat.uikit.user.UserDetailActivity;
import com.chat.uikit.utils.CharacterParser;
import com.chat.uikit.utils.PyingUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * 2019-11-12 14:57
 * 联系人
 */
public class ContactsFragment extends WKBaseFragment<FragContactsLayoutBinding> implements OnQuickSideBarTouchListener {

    private static final String ARG_EMBEDDED_IN_CHAT = "embedded_in_chat";
    private boolean embeddedInChat = false;

    public static ContactsFragment newEmbeddedInstance() {
        ContactsFragment fragment = new ContactsFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean(ARG_EMBEDDED_IN_CHAT, true);
        fragment.setArguments(bundle);
        return fragment;
    }

    private static final Object CONTACTS_REFRESH_ENDPOINT_LOCK = new Object();
    private static final List<WeakReference<ContactsFragment>> ACTIVE_CONTACTS_FRAGMENTS = new ArrayList<>();
    private static boolean contactsRefreshEndpointRegistered = false;

    private ContactsHeaderAdapter contactsHeaderAdapter;
    private FriendAdapter friendAdapter;
    private TextView allContactsCountTv;
    private boolean firstResume = true;
    private final HashMap<String, Integer> contactIndexMap = new HashMap<>();
    // ChatFragment 内嵌联系人页与旧独立联系人页可能同时存在，监听 key 必须按实例隔离。
    private final String instanceKey = Integer.toHexString(System.identityHashCode(this));
    private final String channelRefreshListenerKey = "contacts_fragment_refresh_channel_" + instanceKey;
    private final String mailListEndpointKey = "contacts_fragment_mail_list_" + instanceKey;


    @Override
    protected boolean isShowBackLayout() {
        return false;
    }

    @Override
    protected FragContactsLayoutBinding getViewBinding() {
        return FragContactsLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void getDataBundle(Bundle bundle) {
        super.getDataBundle(bundle);
        embeddedInChat = bundle != null && bundle.getBoolean(ARG_EMBEDDED_IN_CHAT, false);
    }

    @Override
    protected void initView() {
        wkVBinding.textView.setTextSize(22);
        Typeface face = Typeface.createFromAsset(getResources().getAssets(),
                "fonts/mw_bold.ttf");
        wkVBinding.textView.setTypeface(face);
        wkVBinding.quickSideBarView.setTextChooseColor(Theme.colorAccount);
        wkVBinding.quickSideBarTipsView.setBackgroundColor(Theme.colorAccount);
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        if (embeddedInChat) {
            wkVBinding.contactsRootLayout.setPadding(0, 0, 0, 0);
            wkVBinding.contactsRootLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.homeColor));
            wkVBinding.contactsHeaderLayout.setVisibility(View.GONE);
        }
        Theme.setPressedBackground(wkVBinding.searchIv);
        Theme.setPressedBackground(wkVBinding.rightIv);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        Object orgViewObject = EndpointManager.getInstance().invoke("org_contacts_view", requireContext());
        friendAdapter = new FriendAdapter();
        RecyclerView headerRecyclerView = new RecyclerView(requireContext());
        friendAdapter.addHeaderView(headerRecyclerView);
        if (orgViewObject != null) {
            View orgView = (View) orgViewObject;
            friendAdapter.addHeaderView(orgView);
        }
        friendAdapter.addFooterView(getFooterView());
        initAdapter(wkVBinding.recyclerView, friendAdapter);
        headerRecyclerView.setNestedScrollingEnabled(false);
        contactsHeaderAdapter = new ContactsHeaderAdapter();
        initAdapter(headerRecyclerView, contactsHeaderAdapter);
        wkVBinding.quickSideBarView.setOnQuickSideBarTouchListener(this);
        if (embeddedInChat) {
            wkVBinding.quickSideBarView.setOnTouchListener((view, event) -> {
                boolean touching = event.getActionMasked() != MotionEvent.ACTION_UP
                        && event.getActionMasked() != MotionEvent.ACTION_CANCEL;
                view.getParent().requestDisallowInterceptTouchEvent(touching);
                return false;
            });
        }
        friendAdapter.addChildClickViewIds(R.id.contentLayout);
        friendAdapter.setOnItemChildClickListener((adapter, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, view1 -> {
            FriendUIEntity friendEntity = (FriendUIEntity) adapter.getItem(position);
            if (friendEntity != null) {
                Intent intent = new Intent(getActivity(), UserDetailActivity.class);
                intent.putExtra("uid", friendEntity.channel.channelID);
                startActivity(intent);
            }
        }));
        contactsHeaderAdapter.setOnItemClickListener((adapter, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, view1 -> {
            ContactsMenu item = (ContactsMenu) adapter.getItem(position);
            if (item != null && item.iMenuClick != null) {
                item.iMenuClick.onClick();
            }
        }));
        wkVBinding.rightIv.setOnClickListener(view -> {
            List<PopupMenuItem> list = EndpointManager.getInstance().invokes(EndpointCategory.tabMenus, null);
            WKDialogUtils.getInstance().showScreenPopup(view, list);
        });
        // 成员刷新监听。主线程只做 O(1) 索引定位，避免大量在线状态更新时反复扫描完整联系人列表。
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo(channelRefreshListenerKey, (channel, isEnd) -> {
            if (channel == null || TextUtils.isEmpty(channel.channelID)) return;
            AndroidUtilities.runOnUIThread(() -> updateContactChannel(channel));
        });
        wkVBinding.searchIv.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @SuppressWarnings("unchecked") ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), new Pair<>(wkVBinding.searchIv, "searchView"));
                startActivity(new Intent(getActivity(), GlobalActivity.class), activityOptions.toBundle());
            } else {
                startActivity(new Intent(getActivity(), GlobalActivity.class));
            }
        });
        // 该分类支持多监听者，内嵌页与旧独立页分别使用实例 key。
        EndpointManager.getInstance().setMethod(mailListEndpointKey, EndpointCategory.wkRefreshMailList, object -> {
            AndroidUtilities.runOnUIThread(this::resetHeaderData);
            return null;
        });
        registerContactsRefreshEndpoint();
    }

    @Override
    protected void initData() {
        wkVBinding.quickSideBarView.setLetters(CharacterParser.getInstance().getList());
        contactsHeaderAdapter.setList(EndpointManager.getInstance().invokes(EndpointCategory.mailList, getActivity()));
        getContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        resetHeaderData();
        if (firstResume) {
            firstResume = false;
        } else {
            getContacts();
        }
    }

    private void getContacts() {
        List<WKChannel> allList = WKIM.getInstance().getChannelManager().getWithFollowAndStatus(WKChannelType.PERSONAL, 1, 1);
        List<FriendUIEntity> list = new ArrayList<>();
        for (int i = 0, size = allList.size(); i < size; i++) {
            list.add(new FriendUIEntity(allList.get(i)));
        }
        List<FriendUIEntity> otherList = new ArrayList<>();
        List<FriendUIEntity> letterList = new ArrayList<>();
        List<FriendUIEntity> numList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            String showName = list.get(i).channel.channelRemark;
            if (TextUtils.isEmpty(showName))
                showName = list.get(i).channel.channelName;
            if (list.get(i).channel.channelID.equals(WKSystemAccount.system_file_helper)) {
                if (isAdded())
                    showName = getString(R.string.wk_file_helper);
                list.get(i).channel.channelName = showName;
            }
            if (list.get(i).channel.channelID.equals(WKSystemAccount.system_team)) {
                if (isAdded())
                    showName = getString(R.string.wk_system_notice);
                list.get(i).channel.channelName = showName;
            }
            if (!TextUtils.isEmpty(showName)) {
                if (PyingUtils.getInstance().isStartNum(showName)) {
                    list.get(i).pying = "#";
                } else
                    list.get(i).pying = HanziToPinyin.getInstance().getPY(showName);
            } else list.get(i).pying = "#";
        }
        PyingUtils.getInstance().sortListBasic(list);

        for (int i = 0, size = list.size(); i < size; i++) {
            if (TextUtils.isEmpty(list.get(i).pying)){
                otherList.add(list.get(i));
                continue;
            }
            if (PyingUtils.getInstance().isStartLetter(list.get(i).pying)) {
                //字母
                letterList.add(list.get(i));
            } else if (PyingUtils.getInstance().isStartNum(list.get(i).pying)) {
                //数字
                numList.add(list.get(i));
            } else otherList.add(list.get(i));
        }
        List<FriendUIEntity> tempList = new ArrayList<>();
        tempList.addAll(letterList);
        tempList.addAll(numList);
        tempList.addAll(otherList);
        friendAdapter.setList(tempList);
        rebuildContactIndex();
        if (isAdded())
            allContactsCountTv.setText(String.format(getString(R.string.contacts_num), tempList.size()));
    }

    private String getContactKey(String channelID, byte channelType) {
        return channelID + "_" + channelType;
    }

    private void rebuildContactIndex() {
        contactIndexMap.clear();
        if (friendAdapter == null) return;
        List<FriendUIEntity> data = friendAdapter.getData();
        for (int i = 0, size = data.size(); i < size; i++) {
            FriendUIEntity item = data.get(i);
            if (item != null && item.channel != null && !TextUtils.isEmpty(item.channel.channelID)) {
                contactIndexMap.put(getContactKey(item.channel.channelID, item.channel.channelType), i);
            }
        }
    }

    private int findContactIndex(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID) || friendAdapter == null) return -1;
        String key = getContactKey(channelID, channelType);
        Integer index = contactIndexMap.get(key);
        List<FriendUIEntity> data = friendAdapter.getData();
        if (index != null && index >= 0 && index < data.size()) {
            FriendUIEntity item = data.get(index);
            if (item != null && item.channel != null
                    && TextUtils.equals(item.channel.channelID, channelID)
                    && item.channel.channelType == channelType) {
                return index;
            }
        }
        // 索引若因外部 setList 失效，只降级扫描一次并立即修复。
        for (int i = 0, size = data.size(); i < size; i++) {
            FriendUIEntity item = data.get(i);
            if (item != null && item.channel != null
                    && TextUtils.equals(item.channel.channelID, channelID)
                    && item.channel.channelType == channelType) {
                contactIndexMap.put(key, i);
                return i;
            }
        }
        return -1;
    }

    private void updateContactChannel(WKChannel channel) {
        if (!isAdded() || friendAdapter == null || channel == null) return;
        int index = findContactIndex(channel.channelID, channel.channelType);
        if (index < 0) return;
        List<FriendUIEntity> data = friendAdapter.getData();
        if (index >= data.size()) return;
        FriendUIEntity item = data.get(index);
        if (item == null) return;
        item.channel = channel;
        friendAdapter.notifyItemChanged(index + friendAdapter.getHeaderLayoutCount());
    }

    private void registerContactsRefreshEndpoint() {
        synchronized (CONTACTS_REFRESH_ENDPOINT_LOCK) {
            removeInactiveContactsFragmentsLocked(this);
            ACTIVE_CONTACTS_FRAGMENTS.add(new WeakReference<>(this));
            if (!contactsRefreshEndpointRegistered) {
                EndpointManager.getInstance().setMethod(WKConstants.refreshContacts, object -> {
                    dispatchContactsRefresh();
                    return null;
                });
                contactsRefreshEndpointRegistered = true;
            }
        }
    }

    private static void dispatchContactsRefresh() {
        List<ContactsFragment> targets = new ArrayList<>();
        synchronized (CONTACTS_REFRESH_ENDPOINT_LOCK) {
            Iterator<WeakReference<ContactsFragment>> iterator = ACTIVE_CONTACTS_FRAGMENTS.iterator();
            while (iterator.hasNext()) {
                ContactsFragment fragment = iterator.next().get();
                if (fragment == null) {
                    iterator.remove();
                } else {
                    targets.add(fragment);
                }
            }
        }
        for (ContactsFragment fragment : targets) {
            AndroidUtilities.runOnUIThread(() -> {
                if (fragment.isAdded() && fragment.friendAdapter != null) {
                    fragment.getContacts();
                }
            });
        }
    }

    private void unregisterContactsRefreshEndpoint() {
        synchronized (CONTACTS_REFRESH_ENDPOINT_LOCK) {
            removeInactiveContactsFragmentsLocked(this);
            if (ACTIVE_CONTACTS_FRAGMENTS.isEmpty() && contactsRefreshEndpointRegistered) {
                EndpointManager.getInstance().remove(WKConstants.refreshContacts);
                contactsRefreshEndpointRegistered = false;
            }
        }
    }

    private static void removeInactiveContactsFragmentsLocked(ContactsFragment target) {
        Iterator<WeakReference<ContactsFragment>> iterator = ACTIVE_CONTACTS_FRAGMENTS.iterator();
        while (iterator.hasNext()) {
            ContactsFragment fragment = iterator.next().get();
            if (fragment == null || fragment == target) {
                iterator.remove();
            }
        }
    }

    private View getFooterView() {
        allContactsCountTv = new TextView(requireContext());
        allContactsCountTv.setGravity(Gravity.CENTER);
        allContactsCountTv.setTextSize(16);
        allContactsCountTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorDark));
        LinearLayout linearLayout = new LinearLayout(requireContext());
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        linearLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.homeColor));
        linearLayout.addView(allContactsCountTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) allContactsCountTv.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(15);
        layoutParams.bottomMargin = AndroidUtilities.dp(15);
        return linearLayout;
    }

    @Override
    public void onLetterChanged(String letter, int position, float y) {
        wkVBinding.quickSideBarTipsView.setText(letter, position, y);
        //有此key则获取位置并滚动到该位置
        List<FriendUIEntity> list = friendAdapter.getData();
        if (WKReader.isNotEmpty(list)) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).pying.startsWith(letter)) {
                    wkVBinding.recyclerView.scrollToPosition(i + friendAdapter.getHeaderLayoutCount());
                    break;
                }
            }
        }
    }

    @Override
    public void onLetterTouching(boolean touching) {
        wkVBinding.quickSideBarTipsView.setVisibility(touching ? View.VISIBLE : View.INVISIBLE);
    }

    private void resetHeaderData() {
        if (isAdded()) {
            List<ContactsMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.mailList, getActivity());
            for (int i = 0, size = list.size(); i < size; i++) {
                if (!TextUtils.isEmpty(list.get(i).sid) && list.get(i).sid.equals("friend")) {
                    list.get(i).badgeNum = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_new_friend_count");
                    break;
                }
            }
            contactsHeaderAdapter.setList(list);
        }
    }

    @Override
    public void onDestroy() {
        WKIM.getInstance().getChannelManager()
                .removeRefreshChannelInfo(channelRefreshListenerKey);
        EndpointManager.getInstance().remove(mailListEndpointKey);
        unregisterContactsRefreshEndpoint();
        contactIndexMap.clear();
        super.onDestroy();
    }

}
