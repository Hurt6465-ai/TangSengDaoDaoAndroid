package com.chat.room.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;

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
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 话题聊天室列表：类似帖子列表。
 * 排序：置顶优先，其次最后回复时间；卡片只显示发布者大头像 + 最近6个去重回复者小头像。
 */
public class RoomTopicListFragment extends WKBaseFragment<FragmentRoomTopicListBinding> {
    private RoomTopicAdapter adapter;

    public static RoomTopicListFragment newInstance() {
        return new RoomTopicListFragment();
    }

    @Override
    protected boolean isShowBackLayout() {
        return false;
    }

    @Override
    protected FragmentRoomTopicListBinding getViewBinding() {
        return FragmentRoomTopicListBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        adapter = new RoomTopicAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, adapter);
        if (wkVBinding.recyclerView.getItemAnimator() instanceof DefaultItemAnimator) {
            ((DefaultItemAnimator) wkVBinding.recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(true);
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setOnRefreshListener(refreshLayout -> loadRooms(true));
        wkVBinding.createBtn.setOnClickListener(v -> showCreateDialog());
        adapter.setOnItemClickListener((adapter1, view, position) -> openTopic(adapter.getItem(position)));
        adapter.setOnItemLongClickListener((adapter1, view, position) -> {
            showCardMenu(adapter.getItem(position), position);
            return true;
        });
    }

    @Override
    protected void initData() {
        loadRooms(false);
    }

    private void loadRooms(boolean showError) {
        RoomTopicModel.getInstance().listRooms(new IRequestResultListener<RoomTopicListResponse>() {
            @Override
            public void onSuccess(RoomTopicListResponse result) {
                wkVBinding.refreshLayout.finishRefresh(true);
                List<RoomTopicEntity> rooms = result == null ? null : result.rooms;
                if (rooms == null) rooms = new ArrayList<>();
                sortRooms(rooms);
                adapter.setList(rooms);
                updateEmpty();
            }

            @Override
            public void onFail(int code, String msg) {
                wkVBinding.refreshLayout.finishRefresh(false);
                updateEmpty();
                if (showError && !TextUtils.isEmpty(msg)) WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }

    private void openTopic(RoomTopicEntity room) {
        if (room == null) return;
        RoomTopicModel.getInstance().enterRoom(room, new IRequestResultListener<RoomTopicEntity>() {
            @Override
            public void onSuccess(RoomTopicEntity result) {
                openNativeChat(result == null ? room : result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (!TextUtils.isEmpty(room.getChannelId())) openNativeChat(room);
                else if (!TextUtils.isEmpty(msg)) WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }

    private void openNativeChat(RoomTopicEntity room) {
        if (room == null || TextUtils.isEmpty(room.getChannelId())) return;
        byte type = room.channel_type == 0 ? WKChannelType.GROUP : room.channel_type;
        EndpointManager.getInstance().invoke(EndpointSID.chatView, new ChatViewMenu(getActivity(), room.getChannelId(), type, 0, true));
    }

    private void showCardMenu(RoomTopicEntity room, int position) {
        if (room == null || getContext() == null) return;
        String pinText = room.pinned == 1 ? getString(R.string.peipe_room_unpin) : getString(R.string.peipe_room_pin);
        String[] items = new String[]{pinText, getString(R.string.peipe_room_delete)};
        new AlertDialog.Builder(getContext())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) updatePin(room, position, room.pinned != 1);
                    else deleteRoom(room, position);
                })
                .show();
    }

    private void updatePin(RoomTopicEntity room, int position, boolean pinned) {
        RoomTopicModel.getInstance().pinRoom(room, pinned, new IRequestResultListener<RoomTopicEntity>() {
            @Override
            public void onSuccess(RoomTopicEntity result) {
                RoomTopicEntity target = result == null ? room : result;
                target.pinned = pinned ? 1 : 0;
                int index = adapter.indexOfRoom(target.getRoomId(), target.getChannelId());
                if (index >= 0) adapter.getData().set(index, target);
                else if (position >= 0 && position < adapter.getData().size()) adapter.getData().set(position, target);
                sortRooms(adapter.getData());
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onFail(int code, String msg) {
                if (!TextUtils.isEmpty(msg)) WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }

    private void deleteRoom(RoomTopicEntity room, int position) {
        RoomTopicModel.getInstance().deleteRoom(room, new IRequestResultListener<Object>() {
            @Override
            public void onSuccess(Object result) {
                int index = adapter.indexOfRoom(room.getRoomId(), room.getChannelId());
                if (index < 0) index = position;
                if (index >= 0 && index < adapter.getData().size()) {
                    adapter.getData().remove(index);
                    adapter.notifyItemRemoved(index);
                }
                updateEmpty();
            }

            @Override
            public void onFail(int code, String msg) {
                if (!TextUtils.isEmpty(msg)) WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }

    private void updateEmpty() {
        boolean empty = adapter == null || adapter.getData().isEmpty();
        wkVBinding.emptyLayout.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        wkVBinding.recyclerView.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void sortRooms(List<RoomTopicEntity> rooms) {
        if (rooms == null || rooms.size() <= 1) return;
        Collections.sort(rooms, (a, b) -> {
            int pinCompare = Integer.compare(b == null ? 0 : b.pinned, a == null ? 0 : a.pinned);
            if (pinCompare != 0) return pinCompare;
            long at = a == null ? 0 : Math.max(a.last_reply_at, a.created_at);
            long bt = b == null ? 0 : Math.max(b.last_reply_at, b.created_at);
            return Long.compare(bt, at);
        });
    }

    @SuppressLint("SetTextI18n")
    private void showCreateDialog() {
        Context context = getContext();
        if (context == null) return;

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
        LinearLayout.LayoutParams pubLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(84), AndroidUtilities.dp(42));
        actions.addView(publish, pubLp);

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setView(root);
        cancel.setOnClickListener(v -> dialog.dismiss());
        publish.setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.peipe_room_title_hint));
                return;
            }
            publish.setEnabled(false);
            RoomTopicModel.getInstance().createRoom(text, "闲谈", "中文", new IRequestResultListener<RoomTopicEntity>() {
                @Override
                public void onSuccess(RoomTopicEntity result) {
                    dialog.dismiss();
                    if (result != null) {
                        adapter.getData().add(0, result);
                        sortRooms(adapter.getData());
                        adapter.notifyDataSetChanged();
                        updateEmpty();
                        openNativeChat(result);
                    } else {
                        loadRooms(false);
                    }
                }

                @Override
                public void onFail(int code, String msg) {
                    publish.setEnabled(true);
                    WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msg) ? "发布失败" : msg);
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
