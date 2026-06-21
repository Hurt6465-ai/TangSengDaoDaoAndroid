package com.chat.room.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.MotionEvent;
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
import com.chat.room.store.RoomTopicStore;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

public class RoomTopicListFragment extends WKBaseFragment<FragmentRoomTopicListBinding> {
    private FragmentRoomTopicListBinding binding;
    private RoomTopicAdapter adapter;
    private float swipeStartX = 0f;
    private float swipeStartY = 0f;
    private boolean firstResume = true;

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
        adapter = new RoomTopicAdapter(new ArrayList<>());
        initAdapter(binding.recyclerView, adapter);
        if (binding.recyclerView.getItemAnimator() instanceof DefaultItemAnimator) {
            ((DefaultItemAnimator) binding.recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.refreshLayout.setEnableLoadMore(false);
        binding.refreshLayout.setEnableRefresh(true);
    }

    @Override
    protected void initListener() {
        binding.refreshLayout.setOnRefreshListener(refreshLayout -> loadRooms(true));
        binding.createBtn.setOnClickListener(v -> showCreateDialog());
        adapter.setOnItemClickListener((adapter1, view, position) -> openTopic(adapter.getItem(position)));
        adapter.setOnItemLongClickListener((adapter1, view, position) -> {
            showCardMenu(adapter.getItem(position), position);
            return true;
        });
        binding.getRoot().setOnTouchListener((view, event) -> handleSwipe(event));
        binding.recyclerView.setOnTouchListener((view, event) -> handleSwipe(event));
        binding.refreshLayout.setOnTouchListener((view, event) -> handleSwipe(event));
        binding.emptyLayout.setOnTouchListener((view, event) -> handleSwipe(event));
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
        } else {
            loadRooms(false);
        }
    }

    private boolean handleSwipe(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                swipeStartX = event.getX();
                swipeStartY = event.getY();
                return false;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - swipeStartX;
                float dy = event.getY() - swipeStartY;
                if (Math.abs(dx) > AndroidUtilities.dp(70) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    if (dx > 0) {
                        EndpointManager.getInstance().invoke("peipe_show_topic_rooms", false);
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    private void loadRooms(boolean showError) {
        RoomTopicModel.getInstance().listRooms(new IRequestResultListener<RoomTopicListResponse>() {
            @Override
            public void onSuccess(RoomTopicListResponse result) {
                binding.refreshLayout.finishRefresh(true);
                List<RoomTopicEntity> rooms = result == null ? null : result.rooms;
                if (rooms == null) rooms = new ArrayList<>();
                RoomTopicStore.sortRooms(rooms);
                adapter.setList(rooms);
                updateEmpty();
            }

            @Override
            public void onFail(int code, String msg) {
                binding.refreshLayout.finishRefresh(false);
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
                int index = adapter.indexOfRoom(target.getRoomId(), target.getChannelId());
                if (index >= 0) adapter.getData().set(index, target);
                else if (position >= 0 && position < adapter.getData().size()) adapter.getData().set(position, target);
                RoomTopicStore.sortRooms(adapter.getData());
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
        binding.emptyLayout.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.recyclerView.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private TextView createTagChip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText("# " + text);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        chip.setTextSize(14);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        return chip;
    }

    private void updateTagChips(List<TextView> chips, String selectedTag) {
        for (TextView chip : chips) {
            String tagText = chip.getText() == null ? "" : chip.getText().toString().replace("#", "").trim();
            boolean selected = TextUtils.equals(tagText, selectedTag);
            chip.setTextColor(selected ? Color.WHITE : Color.rgb(37, 99, 235));
            chip.setBackgroundResource(selected ? R.drawable.room_chip_blue : R.drawable.room_chip_white);
        }
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

        TextView tagTitle = new TextView(context);
        tagTitle.setText(R.string.peipe_room_tag_title);
        tagTitle.setTextColor(Color.rgb(71, 85, 105));
        tagTitle.setTextSize(13);
        LinearLayout.LayoutParams tagTitleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tagTitleLp.topMargin = AndroidUtilities.dp(12);
        root.addView(tagTitle, tagTitleLp);

        String[] tags = new String[]{"学习", "闲谈", "交友"};
        final String[] selectedTag = new String[]{tags[0]};
        LinearLayout tagRow = new LinearLayout(context);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tagRowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(36));
        tagRowLp.topMargin = AndroidUtilities.dp(6);
        root.addView(tagRow, tagRowLp);
        List<TextView> tagViews = new ArrayList<>();
        for (String tag : tags) {
            TextView chip = createTagChip(context, tag);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(30));
            chipLp.setMarginEnd(AndroidUtilities.dp(8));
            tagRow.addView(chip, chipLp);
            tagViews.add(chip);
            chip.setOnClickListener(v -> {
                selectedTag[0] = tag;
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
        dialog.setView(root);
        cancel.setOnClickListener(v -> dialog.dismiss());
        publish.setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.peipe_room_title_hint));
                return;
            }
            publish.setEnabled(false);
            RoomTopicModel.getInstance().createRoom(text, selectedTag[0], "中文", new IRequestResultListener<RoomTopicEntity>() {
                @Override
                public void onSuccess(RoomTopicEntity result) {
                    dialog.dismiss();
                    if (result != null) {
                        adapter.getData().add(0, result);
                        RoomTopicStore.sortRooms(adapter.getData());
                        adapter.notifyDataSetChanged();
                        updateEmpty();
                        loadRooms(false);
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
