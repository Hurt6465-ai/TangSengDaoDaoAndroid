package com.chat.room.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseFragment;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKToastUtils;
import com.chat.room.R;
import com.chat.room.adapter.RoomTopicAdapter;
import com.chat.room.databinding.FragmentRoomTopicListBinding;
import com.chat.room.entity.RoomTopicEntity;
import com.chat.room.entity.RoomTopicListResponse;
import com.chat.room.model.RoomTopicModel;
import com.chat.room.store.RoomTopicStore;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RoomTopicListFragment extends WKBaseFragment<FragmentRoomTopicListBinding> {
    private static final String CHANNEL_CATEGORY_TOPIC_ROOM = "topic_room";
    private static final String EXTRA_TOPIC_TITLE = "topic_title";
    private static final String EXTRA_CREATOR_AVATAR = "creator_avatar";
    private static final String EXTRA_CREATOR_AVATAR_CACHE_KEY = "creator_avatar_cache_key";
    private static final String EXTRA_CREATOR_NAME = "creator_name";
    private static final String EXTRA_CREATOR_UID = "creator_uid";
    private static final String EXTRA_CREATOR_COUNTRY_CODE = "creator_country_code";
    private static final String EXTRA_COUNTRY_CODE = "country_code";
    private static final String ENDPOINT_SHOW_TOPIC_ROOMS = "peipe_show_topic_rooms";
    private static final String DEFAULT_LANGUAGE = "中文";
    private static final String TAG_PREFIX = "# ";
    private static final int EDGE_SWIPE_MAX_START_DP = 32;
    private static final int SWIPE_TRIGGER_DP = 90;
    private static final int SWIPE_CANCEL_VERTICAL_DP = 18;
    private static final float SWIPE_HORIZONTAL_RATIO = 2.0f;

    private FragmentRoomTopicListBinding binding;
    private RecyclerView recyclerView;
    private SmartRefreshLayout refreshLayout;
    private View createBtn;
    private View emptyLayout;
    private RoomTopicAdapter adapter;
    private AlertDialog createDialog;
    private AlertDialog cardMenuDialog;
    private float swipeStartX = 0f;
    private float swipeStartY = 0f;
    private boolean firstResume = true;
    private String openingChannelId = null;
    private boolean refreshOnNextResume = false;
    private boolean trackingBackSwipe = false;
    private boolean backSwipeTriggered = false;

    public static RoomTopicListFragment newInstance() {
        return new RoomTopicListFragment();
    }

    @Override
    protected boolean isShowBackLayout() {
        return false;
    }

    @Override
    protected FragmentRoomTopicListBinding getViewBinding() {
        binding = FragmentRoomTopicListBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView() {
        if (binding == null) return;
        recyclerView = binding.recyclerView;
        refreshLayout = binding.refreshLayout;
        createBtn = binding.createBtn;
        emptyLayout = binding.emptyLayout;

        adapter = new RoomTopicAdapter(new ArrayList<>());
        initAdapter(recyclerView, adapter);
        if (recyclerView.getItemAnimator() instanceof DefaultItemAnimator) {
            ((DefaultItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        refreshLayout.setEnableLoadMore(false);
        refreshLayout.setEnableRefresh(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        if (!canUpdateUi()) return;
        refreshLayout.setOnRefreshListener(layout -> loadRooms(true));
        createBtn.setOnClickListener(v -> showCreateDialog());
        adapter.setOnItemClickListener((adapter1, view, position) -> {
            if (!isValidAdapterPosition(position)) return;
            openTopic(adapter.getItem(position));
        });
        adapter.setOnItemLongClickListener((adapter1, view, position) -> {
            if (isValidAdapterPosition(position)) {
                showCardMenu(adapter.getItem(position));
            }
            return true;
        });
        binding.getRoot().setOnTouchListener((view, event) -> handleBackSwipe(event, false));
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent event) {
                return handleBackSwipe(event, true);
            }
        });
    }

    @Override
    protected void initData() {
        loadRooms(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (refreshOnNextResume) {
            refreshOnNextResume = false;
            loadRooms(false);
        }
    }

    @Override
    public void onDestroyView() {
        dismissDialogs();
        openingChannelId = null;
        trackingBackSwipe = false;
        backSwipeTriggered = false;
        super.onDestroyView();
        binding = null;
        recyclerView = null;
        refreshLayout = null;
        createBtn = null;
        emptyLayout = null;
        adapter = null;
    }

    private boolean canUpdateUi() {
        return isAdded()
                && binding != null
                && recyclerView != null
                && refreshLayout != null
                && createBtn != null
                && emptyLayout != null
                && adapter != null;
    }

    private boolean isValidAdapterPosition(int position) {
        return adapter != null && position >= 0 && position < adapter.getData().size();
    }

    private boolean handleBackSwipe(MotionEvent event, boolean fromRecyclerView) {
        if (event == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swipeStartX = event.getRawX();
                swipeStartY = event.getRawY();
                trackingBackSwipe = swipeStartX <= AndroidUtilities.dp(EDGE_SWIPE_MAX_START_DP);
                backSwipeTriggered = false;
                return !fromRecyclerView && trackingBackSwipe;
            case MotionEvent.ACTION_MOVE:
                if (!trackingBackSwipe || backSwipeTriggered) return !fromRecyclerView && backSwipeTriggered;
                if (fromRecyclerView && recyclerView != null && recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                    trackingBackSwipe = false;
                    return false;
                }
                float dx = event.getRawX() - swipeStartX;
                float dy = event.getRawY() - swipeStartY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (absDy > AndroidUtilities.dp(SWIPE_CANCEL_VERTICAL_DP) && absDy > absDx) {
                    trackingBackSwipe = false;
                    return false;
                }
                if (dx > AndroidUtilities.dp(SWIPE_TRIGGER_DP) && absDx > absDy * SWIPE_HORIZONTAL_RATIO) {
                    backSwipeTriggered = true;
                    trackingBackSwipe = false;
                    EndpointManager.getInstance().invoke(ENDPOINT_SHOW_TOPIC_ROOMS, false);
                    return true;
                }
                return !fromRecyclerView && trackingBackSwipe;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean consume = !fromRecyclerView && backSwipeTriggered;
                trackingBackSwipe = false;
                backSwipeTriggered = false;
                return consume;
            default:
                return false;
        }
    }

    private void loadRooms(boolean showError) {
        RoomTopicModel.getInstance().listRooms(new IRequestResultListener<RoomTopicListResponse>() {
            @Override
            public void onSuccess(RoomTopicListResponse result) {
                if (!canUpdateUi()) return;
                refreshLayout.finishRefresh(true);
                List<RoomTopicEntity> rooms = result == null ? null : result.rooms;
                if (rooms == null) rooms = new ArrayList<>();
                RoomTopicStore.sortRooms(rooms);
                adapter.setList(rooms);
                updateEmpty();
            }

            @Override
            public void onFail(int code, String msg) {
                if (!canUpdateUi()) return;
                refreshLayout.finishRefresh(false);
                updateEmpty();
                if (showError) showToast(msg);
            }
        });
    }

    private void openTopic(RoomTopicEntity room) {
        if (room == null) return;
        normalizeRoomForOpen(room);
        String requestChannelId = room.getChannelId();
        if (TextUtils.isEmpty(requestChannelId)) {
            toastRoomError("聊天室数据异常", "");
            return;
        }
        if (TextUtils.equals(openingChannelId, requestChannelId)) {
            showToast("正在进入聊天室，请稍候");
            return;
        }
        // 新点击的房间覆盖旧请求，旧请求回调回来时会被 requestChannelId 校验拦截，
        // 避免快速连点不同房间导致连续打开多个聊天页。
        openingChannelId = requestChannelId;
        RoomTopicModel.getInstance().enterRoom(room, new IRequestResultListener<RoomTopicEntity>() {
            @Override
            public void onSuccess(RoomTopicEntity result) {
                if (!TextUtils.equals(openingChannelId, requestChannelId)) return;
                openingChannelId = null;
                if (!canUpdateUi()) return;
                RoomTopicEntity target = mergeRoomForOpen(room, result);
                openNativeChat(target);
            }

            @Override
            public void onFail(int code, String msg) {
                if (!TextUtils.equals(openingChannelId, requestChannelId)) return;
                openingChannelId = null;
                if (!canUpdateUi()) return;
                // 进房失败时不能直接打开聊天页。否则用户没有被加入聊天室订阅者，
                // 看起来能进页面，但发消息会一直停在“发送中”。
                toastRoomError("进入聊天室失败", msg);
            }
        });
    }

    private void openNativeChat(RoomTopicEntity room) {
        if (room == null) return;
        normalizeRoomForOpen(room);
        String channelId = room.getChannelId();
        if (TextUtils.isEmpty(channelId)) {
            toastRoomError("聊天室数据异常", "");
            return;
        }
        FragmentActivity activity = getActivity();
        if (activity == null || !isAdded()) {
            toastRoomError("页面未准备好，请重试", "");
            return;
        }
        byte channelType = room.channel_type == 0 ? WKChannelType.GROUP : room.channel_type;
        cacheTopicChannel(room, channelType);
        refreshOnNextResume = true;
        View root = binding == null ? null : binding.getRoot();
        Runnable openChat = () -> {
            if (activity.isFinishing()) return;
            EndpointManager.getInstance().invoke(
                    EndpointSID.chatView,
                    new ChatViewMenu(activity, channelId, channelType, 0, true)
            );
        };
        if (root != null) {
            root.post(openChat);
        } else {
            openChat.run();
        }
    }

    private void normalizeRoomForOpen(RoomTopicEntity room) {
        if (room == null) return;
        if (TextUtils.isEmpty(room.channel_id)) {
            room.channel_id = room.getRoomId();
        }
        if (room.channel_type == 0) {
            room.channel_type = WKChannelType.GROUP;
        }
    }

    private RoomTopicEntity mergeRoomForOpen(RoomTopicEntity fallback, RoomTopicEntity result) {
        if (result == null) {
            normalizeRoomForOpen(fallback);
            return fallback;
        }
        fillMissingRoomFields(fallback, result);
        return result;
    }

    private RoomTopicEntity mergeRoomForListUpdate(RoomTopicEntity fallback, RoomTopicEntity result, boolean pinned) {
        RoomTopicEntity target = result == null ? fallback : result;
        fillMissingRoomFields(fallback, target);
        target.pinned = pinned ? 1 : 0;
        return target;
    }

    private void fillMissingRoomFields(RoomTopicEntity fallback, RoomTopicEntity target) {
        if (target == null) return;
        normalizeRoomForOpen(target);
        if (fallback == null) return;
        normalizeRoomForOpen(fallback);
        if (TextUtils.isEmpty(target.room_id)) target.room_id = fallback.room_id;
        if (TextUtils.isEmpty(target.channel_id)) target.channel_id = fallback.getChannelId();
        if (target.channel_type == 0) target.channel_type = fallback.channel_type == 0 ? WKChannelType.GROUP : fallback.channel_type;
        if (TextUtils.isEmpty(target.title)) target.title = fallback.title;
        if (TextUtils.isEmpty(target.tag)) target.tag = fallback.tag;
        if (TextUtils.isEmpty(target.language)) target.language = fallback.language;
        if (TextUtils.isEmpty(target.creator_uid)) target.creator_uid = fallback.creator_uid;
        if (TextUtils.isEmpty(target.creator_name)) target.creator_name = fallback.creator_name;
        if (TextUtils.isEmpty(target.creator_avatar)) target.creator_avatar = fallback.creator_avatar;
        if (TextUtils.isEmpty(target.creator_avatar_cache_key)) target.creator_avatar_cache_key = fallback.creator_avatar_cache_key;
        if ((target.reply_users == null || target.reply_users.isEmpty()) && fallback.reply_users != null) target.reply_users = fallback.reply_users;
        if ((target.members == null || target.members.isEmpty()) && fallback.members != null) target.members = fallback.members;
    }

    private void toastRoomError(String fallback, String msg) {
        String text = TextUtils.isEmpty(msg) ? fallback : msg;
        showToast(text);
    }

    private void showToast(String text) {
        if (TextUtils.isEmpty(text) || !isAdded()) return;
        WKToastUtils.getInstance().showToastNormal(text);
    }

    private void cacheTopicChannel(RoomTopicEntity room, byte channelType) {
        if (room == null || TextUtils.isEmpty(room.getChannelId())) return;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(room.getChannelId(), channelType);
        if (channel == null) {
            channel = new WKChannel(room.getChannelId(), channelType);
        }
        RoomTopicEntity.RoomMember creator = room.getCreatorMember();
        String creatorAvatar = !TextUtils.isEmpty(room.creator_avatar) ? room.creator_avatar : creator == null ? "" : creator.avatar;
        String creatorAvatarCacheKey = !TextUtils.isEmpty(room.creator_avatar_cache_key) ? room.creator_avatar_cache_key : creator == null ? "" : creator.avatar_cache_key;
        String creatorName = !TextUtils.isEmpty(room.creator_name) ? room.creator_name : creator == null ? "" : creator.name;
        String creatorUID = !TextUtils.isEmpty(room.creator_uid) ? room.creator_uid : creator == null ? "" : creator.uid;
        String creatorCountry = creator == null ? "" : creator.getCountryOrFlag();

        channel.channelName = room.getShowTitle();
        channel.category = CHANNEL_CATEGORY_TOPIC_ROOM;
        if (!TextUtils.isEmpty(creatorAvatar)) {
            channel.avatar = creatorAvatar;
            channel.avatarCacheKey = creatorAvatarCacheKey;
        }
        if (channel.remoteExtraMap == null) {
            channel.remoteExtraMap = new HashMap<>();
        }
        channel.remoteExtraMap.put(CHANNEL_CATEGORY_TOPIC_ROOM, 1);
        channel.remoteExtraMap.put(EXTRA_TOPIC_TITLE, room.getShowTitle());
        if (!TextUtils.isEmpty(creatorAvatar)) channel.remoteExtraMap.put(EXTRA_CREATOR_AVATAR, creatorAvatar);
        if (!TextUtils.isEmpty(creatorAvatarCacheKey)) channel.remoteExtraMap.put(EXTRA_CREATOR_AVATAR_CACHE_KEY, creatorAvatarCacheKey);
        if (!TextUtils.isEmpty(creatorName)) channel.remoteExtraMap.put(EXTRA_CREATOR_NAME, creatorName);
        if (!TextUtils.isEmpty(creatorUID)) channel.remoteExtraMap.put(EXTRA_CREATOR_UID, creatorUID);
        if (!TextUtils.isEmpty(creatorCountry)) {
            channel.remoteExtraMap.put(EXTRA_CREATOR_COUNTRY_CODE, creatorCountry);
            channel.remoteExtraMap.put(EXTRA_COUNTRY_CODE, creatorCountry);
        }
        if (channel.localExtra == null) {
            channel.localExtra = new HashMap<>();
        }
        channel.localExtra.put(CHANNEL_CATEGORY_TOPIC_ROOM, 1);
        channel.localExtra.put(EXTRA_TOPIC_TITLE, room.getShowTitle());
        if (!TextUtils.isEmpty(creatorAvatar)) {
            channel.localExtra.put(EXTRA_CREATOR_AVATAR, creatorAvatar);
            channel.localExtra.put(EXTRA_CREATOR_AVATAR_CACHE_KEY, creatorAvatarCacheKey);
        }
        if (!TextUtils.isEmpty(creatorName)) channel.localExtra.put(EXTRA_CREATOR_NAME, creatorName);
        if (!TextUtils.isEmpty(creatorUID)) channel.localExtra.put(EXTRA_CREATOR_UID, creatorUID);
        if (!TextUtils.isEmpty(creatorCountry)) {
            channel.localExtra.put(EXTRA_CREATOR_COUNTRY_CODE, creatorCountry);
            channel.localExtra.put(EXTRA_COUNTRY_CODE, creatorCountry);
        }
        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
    }

    private void showCardMenu(RoomTopicEntity room) {
        Context context = getContext();
        if (room == null || context == null || !isAdded()) return;
        dismissCardMenuDialog();
        String pinText = room.pinned == 1 ? getString(R.string.peipe_room_unpin) : getString(R.string.peipe_room_pin);
        String[] items = new String[]{pinText, getString(R.string.peipe_room_delete)};
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setItems(items, (dialogInterface, which) -> {
                    if (which == 0) updatePin(room, room.pinned != 1);
                    else confirmDeleteRoom(room);
                })
                .create();
        cardMenuDialog = dialog;
        dialog.setOnDismissListener(dialogInterface -> {
            if (cardMenuDialog == dialog) cardMenuDialog = null;
        });
        dialog.show();
    }

    private void confirmDeleteRoom(RoomTopicEntity room) {
        Context context = getContext();
        if (room == null || context == null || !isAdded()) return;
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.peipe_room_delete)
                .setMessage("确定删除这个聊天室吗？")
                .setNegativeButton(R.string.peipe_room_cancel, null)
                .setPositiveButton(R.string.peipe_room_delete, (dialogInterface, which) -> deleteRoom(room))
                .create();
        cardMenuDialog = dialog;
        dialog.setOnDismissListener(dialogInterface -> {
            if (cardMenuDialog == dialog) cardMenuDialog = null;
        });
        dialog.show();
    }

    private void updatePin(RoomTopicEntity room, boolean pinned) {
        if (room == null) return;
        RoomTopicModel.getInstance().pinRoom(room, pinned, new IRequestResultListener<RoomTopicEntity>() {
            @Override
            public void onSuccess(RoomTopicEntity result) {
                if (!canUpdateUi()) return;
                RoomTopicEntity target = mergeRoomForListUpdate(room, result, pinned);
                int index = findRoomIndex(room, target);
                if (index < 0) {
                    loadRooms(false);
                    return;
                }
                adapter.getData().set(index, target);
                RoomTopicStore.sortRooms(adapter.getData());
                adapter.notifyDataSetChanged();
                updateEmpty();
            }

            @Override
            public void onFail(int code, String msg) {
                if (!canUpdateUi()) return;
                showToast(msg);
            }
        });
    }

    private void deleteRoom(RoomTopicEntity room) {
        if (room == null) return;
        RoomTopicModel.getInstance().deleteRoom(room, new IRequestResultListener<Object>() {
            @Override
            public void onSuccess(Object result) {
                if (!canUpdateUi()) return;
                int index = findRoomIndex(room, null);
                if (index < 0) {
                    loadRooms(false);
                    return;
                }
                adapter.getData().remove(index);
                adapter.notifyItemRemoved(index);
                updateEmpty();
            }

            @Override
            public void onFail(int code, String msg) {
                if (!canUpdateUi()) return;
                showToast(msg);
            }
        });
    }

    private int findRoomIndex(RoomTopicEntity original, RoomTopicEntity updated) {
        if (adapter == null) return -1;
        int index = -1;
        if (original != null) {
            index = adapter.indexOfRoom(original.getRoomId(), original.getChannelId());
        }
        if (index < 0 && updated != null) {
            index = adapter.indexOfRoom(updated.getRoomId(), updated.getChannelId());
        }
        return index;
    }

    private void addOrUpdateRoom(RoomTopicEntity room) {
        if (room == null || adapter == null) return;
        normalizeRoomForOpen(room);
        int index = findRoomIndex(room, room);
        if (index >= 0) {
            adapter.getData().set(index, room);
        } else {
            adapter.getData().add(0, room);
        }
        RoomTopicStore.sortRooms(adapter.getData());
        adapter.notifyDataSetChanged();
        updateEmpty();
    }

    private void updateEmpty() {
        if (emptyLayout == null || recyclerView == null || adapter == null) return;
        boolean empty = adapter.getData().isEmpty();
        emptyLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void recoverCreatedRoomAfterFailure(String title, String tag, AlertDialog dialog, TextView publish, String failMsg) {
        RoomTopicModel.getInstance().listRooms(new IRequestResultListener<RoomTopicListResponse>() {
            @Override
            public void onSuccess(RoomTopicListResponse result) {
                if (!canUpdateUi() || !isActiveCreateDialog(dialog)) return;
                publish.setEnabled(true);
                List<RoomTopicEntity> rooms = result == null ? null : result.rooms;
                if (rooms == null) rooms = new ArrayList<>();
                RoomTopicStore.sortRooms(rooms);
                adapter.setList(rooms);
                updateEmpty();

                RoomTopicEntity created = findUniqueRoomByTitleTag(rooms, title, tag);
                if (created != null) {
                    dialog.dismiss();
                    openTopic(created);
                    return;
                }
                showToast(TextUtils.isEmpty(failMsg) ? "发布失败，请下拉刷新确认房间是否已创建" : failMsg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (!canUpdateUi() || !isActiveCreateDialog(dialog)) return;
                publish.setEnabled(true);
                showToast(TextUtils.isEmpty(failMsg) ? "发布失败" : failMsg);
            }
        });
    }

    private boolean isActiveCreateDialog(AlertDialog dialog) {
        return dialog != null && createDialog == dialog && dialog.isShowing();
    }

    private RoomTopicEntity findUniqueRoomByTitleTag(List<RoomTopicEntity> rooms, String title, String tag) {
        if (rooms == null || TextUtils.isEmpty(title)) return null;
        RoomTopicEntity matched = null;
        for (RoomTopicEntity room : rooms) {
            if (room == null) continue;
            if (!TextUtils.equals(title, room.getShowTitle())) continue;
            if (!TextUtils.isEmpty(tag) && !TextUtils.equals(tag, room.getRawTag())) continue;
            if (matched != null) return null;
            matched = room;
        }
        return matched;
    }

    private TextView createTagChip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText(TAG_PREFIX + text);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        chip.setTextSize(13);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        return chip;
    }

    private void updateTagChips(List<TextView> chips, String selectedTag) {
        for (TextView chip : chips) {
            String tagText = chip.getTag() == null ? "" : String.valueOf(chip.getTag());
            boolean selected = TextUtils.equals(tagText, selectedTag);
            chip.setTextColor(selected ? Color.WHITE : Color.rgb(37, 99, 235));
            chip.setBackgroundResource(selected ? R.drawable.room_chip_blue : R.drawable.room_chip_white);
        }
    }

    private void dismissDialogs() {
        dismissCreateDialog();
        dismissCardMenuDialog();
    }

    private void dismissCreateDialog() {
        if (createDialog != null && createDialog.isShowing()) {
            createDialog.dismiss();
        }
        createDialog = null;
    }

    private void dismissCardMenuDialog() {
        if (cardMenuDialog != null && cardMenuDialog.isShowing()) {
            cardMenuDialog.dismiss();
        }
        cardMenuDialog = null;
    }

    private static class TagOption {
        final String value;
        final String label;

        TagOption(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    @SuppressLint("SetTextI18n")
    private void showCreateDialog() {
        Context context = getContext();
        if (context == null || !isAdded()) return;
        dismissCreateDialog();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(18);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundResource(R.drawable.room_dialog_bg);

        TextView title = new TextView(context);
        title.setText(R.string.peipe_room_create);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tagTitle = new TextView(context);
        tagTitle.setText(R.string.peipe_room_tag_title);
        tagTitle.setTextColor(Color.rgb(71, 85, 105));
        tagTitle.setTextSize(13);
        LinearLayout.LayoutParams tagTitleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tagTitleLp.topMargin = AndroidUtilities.dp(12);
        root.addView(tagTitle, tagTitleLp);

        TagOption[] tags = new TagOption[]{
                new TagOption("练口语", getString(R.string.peipe_room_tag_speaking)),
                new TagOption("找搭子", getString(R.string.peipe_room_tag_partner)),
                new TagOption("工作", getString(R.string.peipe_room_tag_work)),
                new TagOption("影视", getString(R.string.peipe_room_tag_movie)),
                new TagOption("音乐", getString(R.string.peipe_room_tag_music)),
                new TagOption("学习", getString(R.string.peipe_room_tag_study)),
                new TagOption("闲谈", getString(R.string.peipe_room_tag_chat))
        };
        final String[] selectedTag = new String[]{tags[0].value};
        LinearLayout tagWrap = new LinearLayout(context);
        tagWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tagWrapLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tagWrapLp.topMargin = AndroidUtilities.dp(6);
        root.addView(tagWrap, tagWrapLp);
        List<TextView> tagViews = new ArrayList<>();
        LinearLayout tagRow = null;
        for (int i = 0; i < tags.length; i++) {
            if (i % 4 == 0) {
                tagRow = new LinearLayout(context);
                tagRow.setOrientation(LinearLayout.HORIZONTAL);
                tagRow.setGravity(Gravity.CENTER_VERTICAL);
                tagWrap.addView(tagRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(34)));
            }
            TagOption option = tags[i];
            TextView chip = createTagChip(context, option.label);
            chip.setTag(option.value);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(28));
            chipLp.setMarginEnd(AndroidUtilities.dp(7));
            tagRow.addView(chip, chipLp);
            tagViews.add(chip);
            chip.setOnClickListener(v -> {
                selectedTag[0] = String.valueOf(v.getTag());
                updateTagChips(tagViews, selectedTag[0]);
            });
        }
        updateTagChips(tagViews, selectedTag[0]);

        EditText input = new EditText(context);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setHint(R.string.peipe_room_title_hint);
        input.setTextColor(Color.rgb(17, 24, 39));
        input.setHintTextColor(Color.rgb(148, 163, 184));
        input.setTextSize(16);
        input.setBackgroundResource(R.drawable.room_input_bg);
        input.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(52));
        inputLp.topMargin = AndroidUtilities.dp(14);
        root.addView(input, inputLp);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48));
        actionsLp.topMargin = AndroidUtilities.dp(14);
        root.addView(actions, actionsLp);

        TextView cancel = new TextView(context);
        cancel.setText(R.string.peipe_room_cancel);
        cancel.setTextColor(Color.rgb(100, 116, 139));
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextSize(15);
        actions.addView(cancel, new LinearLayout.LayoutParams(AndroidUtilities.dp(76), LinearLayout.LayoutParams.MATCH_PARENT));

        TextView publish = new TextView(context);
        publish.setText(R.string.peipe_room_publish);
        publish.setGravity(Gravity.CENTER);
        publish.setTextColor(Color.WHITE);
        publish.setTextSize(15);
        publish.setTypeface(publish.getTypeface(), android.graphics.Typeface.BOLD);
        publish.setBackgroundResource(R.drawable.room_publish_bg);
        actions.addView(publish, new LinearLayout.LayoutParams(AndroidUtilities.dp(84), AndroidUtilities.dp(42)));

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        createDialog = dialog;
        dialog.setView(root);
        dialog.setOnDismissListener(dialogInterface -> {
            if (createDialog == dialog) createDialog = null;
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        publish.setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                showToast(getString(R.string.peipe_room_title_hint));
                return;
            }
            publish.setEnabled(false);
            RoomTopicModel.getInstance().createRoom(text, selectedTag[0], DEFAULT_LANGUAGE, new IRequestResultListener<RoomTopicEntity>() {
                @Override
                public void onSuccess(RoomTopicEntity result) {
                    if (!canUpdateUi()) return;
                    boolean shouldOpenCreatedRoom = isActiveCreateDialog(dialog);
                    if (shouldOpenCreatedRoom) dialog.dismiss();
                    if (result != null) {
                        addOrUpdateRoom(result);
                        // 发布成功后也先走 enterRoom，确保服务端订阅者、group_member 和本地频道缓存都完整。
                        if (shouldOpenCreatedRoom) openTopic(result);
                    } else {
                        loadRooms(false);
                    }
                }

                @Override
                public void onFail(int code, String msg) {
                    if (!canUpdateUi() || !isActiveCreateDialog(dialog)) return;
                    recoverCreatedRoomAfterFailure(text, selectedTag[0], dialog, publish, msg);
                }
            });
        });
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }
}
