package com.chat.uikit.chat;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;

import android.widget.TextView;
import android.widget.Switch;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.net.Uri;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.app.AlertDialog;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.PopupWindow;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.common.WKCommonModel;
import com.chat.base.act.WKWebViewActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.db.WKContactsDB;
import com.chat.base.config.WKBinder;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.emoji.MoonUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.AvatarOtherViewMenu;
import com.chat.base.endpoint.entity.CallingViewMenu;
import com.chat.base.endpoint.entity.RTCMenu;
import com.chat.base.endpoint.entity.ReadMsgMenu;
import com.chat.base.endpoint.entity.SetChatBgMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.entity.UserOnlineStatus;
import com.chat.base.entity.WKChannelCustomerExtras;
import com.chat.base.entity.WKGroupType;
import com.chat.base.msg.ChatAdapter;
import com.chat.base.msg.ChatContentSpanType;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.NumberTextView;
import com.chat.base.ui.components.SystemMsgBackgroundColorSpan;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.UserUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKPlaySound;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.views.CommonAnim;
import com.chat.base.views.swipeback.SwipeBackActivity;
import com.chat.base.views.swipeback.SwipeBackLayout;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.chat.manager.SendMsgEntity;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.chat.manager.WKSendMsgUtils;
import com.chat.uikit.chat.msgmodel.WKCardContent;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.databinding.ActChatLayoutBinding;
import com.chat.uikit.group.ChooseVideoCallMembersActivity;
import com.chat.uikit.group.GroupDetailActivity;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.partner.PartnerLocalMessageStore;
import com.chat.uikit.partner.PartnerPendingStore;
import com.chat.uikit.robot.service.WKRobotModel;
import com.chat.uikit.user.ProfileNavigator;
import com.chat.uikit.user.service.UserModel;
import com.chat.uikit.view.WKPlayVoiceUtils;
import com.chat.translate.ui.TranslateSettingsActivity;
import com.chat.deepseek.DeepSeekAssistant;
import com.chat.deepseek.DeepSeekRequest;
import com.effective.android.panel.PanelSwitchHelper;
import com.effective.android.panel.interfaces.ContentScrollMeasurer;
import com.effective.android.panel.interfaces.listener.OnPanelChangeListener;
import com.effective.android.panel.view.panel.IPanelView;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMD;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelState;
import com.xinbida.wukongim.entity.WKChannelStatus;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMentionType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgReaction;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.interfaces.IGetOrSyncHistoryMsgBack;
import com.xinbida.wukongim.message.type.WKConnectStatus;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKTextContent;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;
import com.xinbida.wukongim.msgmodel.WKReply;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChatActivity extends SwipeBackActivity implements IConversationContext {
    private String channelId = "";
    private byte channelType = WKChannelType.PERSONAL;
    private ChatAdapter chatAdapter;
    //是否在查看历史消息
    private boolean isShowHistory;
    private boolean isSyncLastMsg = false;
    private boolean isToEnd = true;
    private boolean isViewingPicture = false;
    private final boolean showNickName = true; // 是否显示聊天昵称
    private long lastPreviewMsgOrderSeq = 0; //上次浏览消息
    private long unreadStartMsgOrderSeq = 0; //新消息开始位置
    private long tipsOrderSeq = 0; //需要强提示的msg
    private int keepOffsetY = 0; // 上次浏览消息的偏移量
    private int redDot = 0; // 未读消息数量
    private int lastVisibleMsgSeq = 0; // 最后可见消息序号
    private int maxMsgSeq = 0;
    private long maxMsgOrderSeq = 0;
    //回复的消息对象
    private WKMsg replyWKMsg;
    // 编辑对象
    private WKMsg editMsg;
    // 群成员数量
    private int count;
    private int groupType = WKGroupType.normalGroup;
    //已读消息ID
    private final Set<String> readMsgIds = new LinkedHashSet<>();
    private Disposable disposable;
    private final CompositeDisposable asyncDisposables = new CompositeDisposable();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long TYPING_TIMEOUT_MS = 8_000L;
    @Nullable
    private Runnable typingExpiryRunnable;
    @Nullable
    private WeakReference<WKMsg> typingExpiryMessageRef;
    private long typingExpiryVersion = 0L;
    // 每次切换聊天对象都递增。异步回调只允许更新发起请求时对应的会话。
    private long channelGeneration = 1L;
    // SDK 的 listener key 只是监听器标识，不是频道过滤条件。必须按 Activity + 会话代次唯一，
    // 否则两个同频道聊天页会互相覆盖/移除监听器。
    private String activeChannelListenerKey = "";
    private boolean globalEndpointsRegistered = false;
    private GlobalEndpointRegistration globalEndpointRegistration;
    private static final Object GLOBAL_ENDPOINT_LOCK = new Object();
    private static final List<GlobalEndpointRegistration> GLOBAL_ENDPOINT_STACK = new ArrayList<>();

    private static final class GlobalEndpointRegistration {
        boolean active = true;
    }
    private boolean isUploadReadMsg = true;
    private NumberTextView numberTextView;
    //    boolean isUpdateCoverMsg = false;
    private boolean isCanLoadMore;
    boolean isRefreshLoading = false;
    boolean isMoreLoading = false;
    boolean isCanRefresh = true;
    private boolean isShowChatActivity = true;
    LinearLayoutManager linearLayoutManager;
    private final List<WKReminder> reminderList = new ArrayList<>();
    private final List<WKReminder> groupApproveList = new ArrayList<>();
    private final List<Long> reminderIds = new ArrayList<>();
    private long browseTo = 0;
    private boolean isUpdateRedDot = true;
    private ImageView callIV;
    private TextView moreIV;
    private PopupWindow callPopupWindow;
    //查询聊天数据偏移量
    private final int limit = 30;
    private boolean isShowPinnedView = false;
    private boolean isShowCallingView = false;
    private boolean isTipMessage = false;
    private int hideChannelAllPinnedMessage = 0;
    private PanelSwitchHelper mHelper;
    private ChatPanelManager chatPanelManager;
    private final PartnerPendingStore.Listener partnerPendingListener = peerUid -> {
        if (TextUtils.equals(channelId, peerUid)) {
            updatePartnerPendingUi();
        }
    };
    private final PartnerLocalMessageStore.Listener partnerLocalMessageListener = msg -> {
        if (msg == null || chatAdapter == null
                || msg.channelType != channelType
                || !TextUtils.equals(msg.channelID, channelId)) {
            return;
        }
        // saveAndUpdateConversationMsg may emit only a refresh callback when the SDK DB
        // already knows this client_msg_no. refreshMsg cannot insert an absent row, so
        // explicitly insert when needed and otherwise refresh the existing bubble.
        if (chatAdapter.isExist(msg.clientMsgNO, msg.messageID)) refreshMsg(msg);
        else sendMsgInserted(msg);
    };
    private ActChatLayoutBinding wkVBinding;
    private int unfilledHeight = 0;
    // 输入栏浮在消息列表上方；动态 padding 只在布局高度或系统栏 inset 变化时更新。
    private int recyclerBasePaddingLeft;
    private int recyclerBasePaddingTop;
    private int recyclerBasePaddingRight;
    private int recyclerBasePaddingBottom;
    private int unreadBaseBottomMargin;
    private int contentOverlayBaseBottomMargin;
    private boolean floatingComposerLayoutInstalled = false;
    @Nullable
    private View.OnLayoutChangeListener floatingComposerLayoutChangeListener;
    private final String loginUID = WKConfig.getInstance().getUid();
    private final int callingViewHeight = AndroidUtilities.dp(40f);
    private final int pinnedViewHeight = AndroidUtilities.dp(50f);
    private int lastReminderCount = -1;
    private int lastGroupApproveCount = -1;
    private String lastFloatingTime = "";
    private boolean lastFloatingTimeVisible = false;

    private static final String KEY_AI_ENDPOINT = "chat_ai_endpoint";
    private static final String KEY_AI_KEY = "chat_ai_key";
    private static final String KEY_AI_MODEL = "chat_ai_model";
    private static final String KEY_AI_SOURCE_LANG = "chat_ai_source_lang";
    private static final String KEY_AI_TARGET_LANG = "chat_ai_target_lang";
    private static final String KEY_AI_AUTO_TRANSLATE = "chat_ai_auto_translate";
    private static final String KEY_AI_SEND_TRANSLATE = "chat_ai_send_translate";
    private static final String KEY_AI_WINGMAN_ENABLED = "chat_ai_wingman_enabled";
    private static final String KEY_DEEPSEEK_OLD_FLAGS_SAVED = "deepseek_old_ai_flags_saved";
    private static final String KEY_DEEPSEEK_OLD_SEND_TRANSLATE = "deepseek_old_send_translate";
    private static final String KEY_DEEPSEEK_OLD_WINGMAN = "deepseek_old_wingman";
    private static final String KEY_IMAGE_COMPRESS = "chat_image_compress";
    private static final long TOPIC_ROOM_READ_THROTTLE_MS = 1_000L;
    private boolean topicRoomClosing = false;
    private long lastTopicRoomReadAt = 0L;
    private String lastTopicRoomReadKey = "";

    private interface BooleanAction {
        void onChanged(boolean value);
    }


    // 图片字段反射缓存：key 为 Content 的 class，value 为可写入路径的 Field。避免每次发图都全量反射。
    private static final Map<Class<?>, List<Field>> IMG_PATH_FIELDS_CACHE = new HashMap<>();

    private long pendingChatBgGeneration = -1L;
    private String pendingChatBgChannelId = "";
    private byte pendingChatBgChannelType = WKChannelType.PERSONAL;
    private long pendingChatBgOperationVersion = -1L;
    // 每个会话独立记录最后一次背景选择，避免旧的异步保存覆盖用户刚选的内置背景。
    private final Map<String, Long> chatBgOperationVersions = new HashMap<>();
    private long chatBgOperationSequence = 0L;
    private long pendingPreviewGeneration = -1L;
    private String pendingPreviewChannelId = "";
    private byte pendingPreviewChannelType = WKChannelType.PERSONAL;
    private WKMsg pendingPreviewReplyMsg;
    private long pendingCardGeneration = -1L;
    private String pendingCardChannelId = "";
    private byte pendingCardChannelType = WKChannelType.PERSONAL;

    private final ActivityResultLauncher<String> chooseChatBgLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleChatBackgroundResult);

    private String chatBgKey() {
        return chatBgKey(channelId, channelType);
    }

    private String chatBgKey(String targetChannelId, byte targetChannelType) {
        return "local_chat_bg_" + targetChannelType + "_" + targetChannelId;
    }

    private String chatBgModeKey() {
        return chatBgModeKey(channelId, channelType);
    }

    private String chatBgModeKey(String targetChannelId, byte targetChannelType) {
        return "local_chat_bg_mode_" + targetChannelType + "_" + targetChannelId;
    }

    private String chatBgBuiltinKey() {
        return chatBgBuiltinKey(channelId, channelType);
    }

    private String chatBgBuiltinKey(String targetChannelId, byte targetChannelType) {
        return "local_chat_bg_builtin_" + targetChannelType + "_" + targetChannelId;
    }

    private File chatBgFile() {
        return chatBgFile(channelId, channelType);
    }

    private File chatBgFile(String targetChannelId, byte targetChannelType) {
        File dir = new File(getFilesDir(), "chat_bg");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        String safeId = targetChannelId == null ? "" : targetChannelId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new File(dir, "bg_" + targetChannelType + "_" + safeId + ".jpg");
    }

    private boolean getLocalFlag(String key, boolean defaultValue) {
        String value = WKSharedPreferencesUtil.getInstance().getSP(key);
        if (TextUtils.isEmpty(value)) return defaultValue;
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private void putLocalFlag(String key, boolean value) {
        WKSharedPreferencesUtil.getInstance().putSP(key, value ? "1" : "0");
    }

    private String getLocalString(String key, String defaultValue) {
        String value = WKSharedPreferencesUtil.getInstance().getSP(key);
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }


    private int getBuiltinChatBgRes(int index) {
        switch (index) {
            case 1: return R.drawable.chat_bg_01;
            case 2: return R.drawable.chat_bg_02;
            case 3: return R.drawable.chat_bg_03;
            case 4: return R.drawable.chat_bg_04;
            case 5: return R.drawable.chat_bg_05;
            case 6: return R.drawable.chat_bg_06;
            case 7: return R.drawable.chat_bg_07;
            case 8: return R.drawable.chat_bg_08;
            case 9:
            default: return R.drawable.chat_bg_09;
        }
    }

    private int getBuiltinChatBgIndex() {
        String value = WKSharedPreferencesUtil.getInstance().getSP(chatBgBuiltinKey());
        if (TextUtils.isEmpty(value)) return 9;
        try {
            int index = Integer.parseInt(value);
            return index >= 1 && index <= 9 ? index : 9;
        } catch (Exception ignored) {
            return 9;
        }
    }

    private void applyBuiltinChatBackground(int index) {
        // 这里只负责按已保存的配置显示背景，不能让一次普通刷新取消正在进行的相册保存任务。
        displayBuiltinChatBackground(index, channelGeneration, channelId, channelType);
    }

    private void applyBuiltinChatBackground(int index, long requestGeneration,
                                            String targetChannelId, byte targetChannelType) {
        long operationVersion = beginChatBackgroundOperation(targetChannelId, targetChannelType);
        synchronized (chatBgOperationVersions) {
            if (!isCurrentChatBackgroundOperationLocked(targetChannelId, targetChannelType, operationVersion)) {
                return;
            }
            deleteCustomChatBackgroundFile(targetChannelId, targetChannelType);
            WKSharedPreferencesUtil.getInstance().putSP(chatBgModeKey(targetChannelId, targetChannelType), "builtin");
            WKSharedPreferencesUtil.getInstance().putSP(chatBgBuiltinKey(targetChannelId, targetChannelType), String.valueOf(index));
            WKSharedPreferencesUtil.getInstance().putSP(chatBgKey(targetChannelId, targetChannelType), "");
        }
        displayBuiltinChatBackground(index, requestGeneration, targetChannelId, targetChannelType);
    }

    private void displayBuiltinChatBackground(int index, long requestGeneration,
                                              String targetChannelId, byte targetChannelType) {
        if (!isCurrentSession(requestGeneration, targetChannelId, targetChannelType) || wkVBinding == null) return;
        wkVBinding.imageView.setImageResource(getBuiltinChatBgRes(index));
        wkVBinding.imageView.setVisibility(View.VISIBLE);
        if (wkVBinding.blurView != null) wkVBinding.blurView.setVisibility(View.GONE);
    }

    private String chatBackgroundOperationKey(String targetChannelId, byte targetChannelType) {
        return targetChannelType + ":" + (targetChannelId == null ? "" : targetChannelId);
    }

    private long beginChatBackgroundOperation(String targetChannelId, byte targetChannelType) {
        synchronized (chatBgOperationVersions) {
            long version = ++chatBgOperationSequence;
            chatBgOperationVersions.put(chatBackgroundOperationKey(targetChannelId, targetChannelType), version);
            return version;
        }
    }

    private boolean isCurrentChatBackgroundOperation(String targetChannelId, byte targetChannelType,
                                                     long operationVersion) {
        synchronized (chatBgOperationVersions) {
            return isCurrentChatBackgroundOperationLocked(targetChannelId, targetChannelType, operationVersion);
        }
    }

    private boolean isCurrentChatBackgroundOperationLocked(String targetChannelId, byte targetChannelType,
                                                           long operationVersion) {
        Long current = chatBgOperationVersions.get(chatBackgroundOperationKey(targetChannelId, targetChannelType));
        return current != null && current == operationVersion;
    }

    private void deleteCustomChatBackgroundFile(String targetChannelId, byte targetChannelType) {
        File file = chatBgFile(targetChannelId, targetChannelType);
        if (file.exists() && !file.delete()) {
            Log.w("ChatActivity", "delete stale chat background failed: " + file.getAbsolutePath());
        }
        File parent = file.getParentFile();
        if (parent == null) return;
        File[] staleFiles = parent.listFiles((dir, name) ->
                name.equals(file.getName() + ".bak")
                        || name.startsWith(file.getName() + ".tmp_"));
        if (staleFiles == null) return;
        for (File stale : staleFiles) {
            if (stale != null && stale.exists() && !stale.delete()) {
                Log.w("ChatActivity", "delete stale chat background artifact failed: "
                        + stale.getAbsolutePath());
            }
        }
    }

    private void loadLocalChatBackground() {
        String mode = WKSharedPreferencesUtil.getInstance().getSP(chatBgModeKey());
        String path = WKSharedPreferencesUtil.getInstance().getSP(chatBgKey());
        if ("custom".equals(mode) || !TextUtils.isEmpty(path)) {
            if (TextUtils.isEmpty(path)) {
                path = chatBgFile().getAbsolutePath();
            }
            File file = new File(path);
            if (file.exists()) {
                wkVBinding.imageView.setImageURI(Uri.fromFile(file));
                wkVBinding.imageView.setVisibility(View.VISIBLE);
                if (wkVBinding.blurView != null) wkVBinding.blurView.setVisibility(View.GONE);
                return;
            }
        }
        applyBuiltinChatBackground(getBuiltinChatBgIndex());
    }

    private void clearLocalChatBackground(long requestGeneration, String targetChannelId, byte targetChannelType) {
        applyBuiltinChatBackground(9, requestGeneration, targetChannelId, targetChannelType);
        if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
            WKToastUtils.getInstance().showToast(getString(R.string.chat_bg_cleared));
        }
    }

    private void captureChatBackgroundTarget(long requestGeneration, String targetChannelId, byte targetChannelType) {
        pendingChatBgGeneration = requestGeneration;
        pendingChatBgChannelId = targetChannelId;
        pendingChatBgChannelType = targetChannelType;
        pendingChatBgOperationVersion = beginChatBackgroundOperation(targetChannelId, targetChannelType);
    }

    private void handleChatBackgroundResult(Uri uri) {
        final long requestGeneration = pendingChatBgGeneration;
        final String targetChannelId = pendingChatBgChannelId;
        final byte targetChannelType = pendingChatBgChannelType;
        final long operationVersion = pendingChatBgOperationVersion;
        pendingChatBgGeneration = -1L;
        pendingChatBgChannelId = "";
        pendingChatBgOperationVersion = -1L;
        if (uri == null || TextUtils.isEmpty(targetChannelId) || operationVersion < 0) return;

        Disposable task = Observable.fromCallable(() -> saveChatBackgroundFile(
                        uri, targetChannelId, targetChannelType, operationVersion))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(path -> {
                    if (TextUtils.isEmpty(path)
                            || !isCurrentChatBackgroundOperation(targetChannelId, targetChannelType, operationVersion)) {
                        return;
                    }
                    if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
                        loadLocalChatBackground();
                        WKToastUtils.getInstance().showToast(getString(R.string.chat_bg_saved));
                    }
                }, throwable -> {
                    Log.e("ChatActivity", "save chat bg failed", throwable);
                    if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)
                            && isCurrentChatBackgroundOperation(targetChannelId, targetChannelType, operationVersion)) {
                        WKToastUtils.getInstance().showToast(getString(R.string.chat_bg_failed));
                    }
                });
        asyncDisposables.add(task);
    }

    private String saveChatBackgroundFile(Uri uri, String targetChannelId, byte targetChannelType,
                                          long operationVersion) throws Exception {
        Bitmap bitmap = null;
        File targetFile = chatBgFile(targetChannelId, targetChannelType);
        File tempFile = new File(targetFile.getParentFile(),
                targetFile.getName() + ".tmp_" + operationVersion + "_" + Math.abs(System.nanoTime()));
        try {
            bitmap = decodeBitmapFromUri(uri, 1800);
            if (bitmap == null) {
                throw new IllegalStateException("decode chat background failed");
            }
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, outputStream)) {
                    throw new IllegalStateException("bitmap compress failed");
                }
                outputStream.flush();
            }

            synchronized (chatBgOperationVersions) {
                if (!isCurrentChatBackgroundOperationLocked(
                        targetChannelId, targetChannelType, operationVersion)) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                    return "";
                }
                replaceChatBackgroundFile(tempFile, targetFile);
                WKSharedPreferencesUtil.getInstance().putSP(
                        chatBgModeKey(targetChannelId, targetChannelType), "custom");
                WKSharedPreferencesUtil.getInstance().putSP(
                        chatBgKey(targetChannelId, targetChannelType), targetFile.getAbsolutePath());
            }
            return targetFile.getAbsolutePath();
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    private void replaceChatBackgroundFile(File tempFile, File targetFile) throws Exception {
        File backupFile = new File(targetFile.getParentFile(), targetFile.getName() + ".bak");
        if (backupFile.exists() && !backupFile.delete()) {
            throw new IllegalStateException("delete old chat background backup failed");
        }
        boolean hadOldFile = targetFile.exists();
        if (hadOldFile && !targetFile.renameTo(backupFile)) {
            throw new IllegalStateException("backup old chat background failed");
        }
        if (!tempFile.renameTo(targetFile)) {
            if (hadOldFile && backupFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                backupFile.renameTo(targetFile);
            }
            throw new IllegalStateException("replace chat background failed");
        }
        if (backupFile.exists() && !backupFile.delete()) {
            Log.w("ChatActivity", "delete chat background backup failed: " + backupFile.getAbsolutePath());
        }
    }

    private Bitmap decodeBitmapFromUri(Uri uri, int maxSide) {
        if (uri == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream boundsStream = getContentResolver().openInputStream(uri)) {
                if (boundsStream == null) return null;
                BitmapFactory.decodeStream(boundsStream, null, bounds);
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sample = 1;
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            // 避免透明图片丢失 Alpha，也减少聊天截图和渐变出现色带。
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream == null) return null;
                return BitmapFactory.decodeStream(inputStream, null, options);
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "decode bitmap failed", e);
            return null;
        }
    }

    /**
     * 根据系统版本返回正确的 WebP 压缩格式。
     * 这里不用 Bitmap.CompressFormat.WEBP_LOSSY 常量直连，避免 compileSdk 低于 30 时编译失败。
     */
    private Bitmap.CompressFormat getWebpCompressFormat() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return Bitmap.CompressFormat.valueOf("WEBP_LOSSY");
            } catch (Exception ignored) {
            }
        }
        //noinspection deprecation
        return Bitmap.CompressFormat.WEBP;
    }

    private String compressImagePathIfNeeded(String path) {
        if (TextUtils.isEmpty(path) || !getLocalFlag(KEY_IMAGE_COMPRESS, true)) return path;
        if (isHttpUrl(path)) return path;

        // content:// 先检查 MIME，GIF 必须保留动画，不能解码成单帧 WebP。
        if (path.startsWith("content://")) {
            try {
                String mime = getContentResolver().getType(Uri.parse(path));
                if (!TextUtils.isEmpty(mime) && mime.toLowerCase(Locale.US).contains("gif")) {
                    return path;
                }
            } catch (Exception ignored) {
            }
            return compressImageUriToWebp(path);
        }

        String localPath = normalizeLocalImagePath(path);
        if (TextUtils.isEmpty(localPath)) return path;

        Bitmap bitmap = null;
        FileOutputStream outputStream = null;
        File out = null;
        try {
            File source = new File(localPath);
            if (!source.exists() || !source.isFile()) return path;

            String lowerPath = localPath.toLowerCase(Locale.US);
            // GIF 保留动画，不在这里压成静态 WebP。
            if (lowerPath.endsWith(".gif")) return path;
            // 产品规则：小于 100KB 的 WebP 不压缩；其它格式即使小于 100KB 也只做 WebP 转换。
            if (lowerPath.endsWith(".webp") && source.length() < 100 * 1024) return path;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(localPath, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return path;

            int sample = 1;
            while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 1440) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeFile(localPath, options);
            if (bitmap == null) return path;

            out = createCompressedWebpFile(source.getName());
            outputStream = new FileOutputStream(out);
            boolean ok = bitmap.compress(getWebpCompressFormat(), 76, outputStream);
            outputStream.flush();
            outputStream.close();
            outputStream = null;
            if (ok && out.exists() && out.length() > 0 && out.length() < source.length()) {
                Log.d("ChatImage", "compressed image path=" + out.getAbsolutePath());
                return out.getAbsolutePath();
            }
            if (out.exists()) {
                // 转换后没有变小就保留原图，避免浪费流量和缓存空间。
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                out = null;
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "compress image failed", e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (out != null && out.exists() && out.length() <= 0) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
        Log.e("ChatImage", "final local image path=" + path);
        return path;
    }

    private String compressImageUriToWebp(String uriString) {
        Bitmap bitmap = null;
        FileOutputStream outputStream = null;
        File out = null;
        try {
            bitmap = decodeBitmapFromUri(Uri.parse(uriString), 1440);
            if (bitmap == null) return uriString;
            out = createCompressedWebpFile("content");
            outputStream = new FileOutputStream(out);
            boolean ok = bitmap.compress(getWebpCompressFormat(), 76, outputStream);
            outputStream.flush();
            if (ok && out.exists() && out.length() > 0) {
                return out.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "compress image uri failed", e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (out != null && out.exists() && out.length() <= 0) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
        return uriString;
    }

    private File createCompressedWebpFile(String sourceName) {
        File dir = new File(getCacheDir(), "chat_send_img");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        String safeName = TextUtils.isEmpty(sourceName) ? "img" : sourceName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new File(dir, "img_" + System.currentTimeMillis() + "_" + Math.abs(safeName.hashCode()) + "_" + Math.abs(System.nanoTime()) + ".webp");
    }

    private boolean isHttpUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isLocalImagePath(String value) {
        if (TextUtils.isEmpty(value) || isHttpUrl(value)) return false;
        return value.startsWith("content://") || value.startsWith("file://") || value.startsWith("/");
    }

    private String normalizeLocalImagePath(String path) {
        if (TextUtils.isEmpty(path)) return path;
        if (path.startsWith("file://")) {
            try {
                return Uri.parse(path).getPath();
            } catch (Exception ignored) {
                return path.substring("file://".length());
            }
        }
        return path;
    }

    /**
     * 在继承链上查找所有可能承载本地图片路径的字段，并缓存结果。
     * 旧版本只改第一个字段，WKImageContent 如果同时有 path/localPath/url，就可能仍按旧 jpg 路径发送。
     */
    private List<Field> findImagePathFields(WKMessageContent content) {
        Class<?> clazz = content.getClass();
        synchronized (IMG_PATH_FIELDS_CACHE) {
            List<Field> cached = IMG_PATH_FIELDS_CACHE.get(clazz);
            if (cached != null) return cached;

            List<Field> fields = new ArrayList<>();
            String[] names = new String[]{"localPath", "path", "filePath", "file_path", "imagePath", "image_path", "url"};
            for (String name : names) {
                Class<?> cur = clazz;
                while (cur != null && cur != Object.class) {
                    try {
                        Field field = cur.getDeclaredField(name);
                        if (field.getType() == String.class) {
                            field.setAccessible(true);
                            boolean exists = false;
                            for (Field item : fields) {
                                if (item.getName().equals(field.getName()) && item.getDeclaringClass().equals(field.getDeclaringClass())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) fields.add(field);
                        }
                        break;
                    } catch (NoSuchFieldException ignored) {
                        cur = cur.getSuperclass();
                    } catch (Exception ignored) {
                        break;
                    }
                }
            }
            IMG_PATH_FIELDS_CACHE.put(clazz, fields);
            return fields;
        }
    }

    private void compressImageContentIfNeeded(WKMessageContent messageContent) {
        if (!(messageContent instanceof WKImageContent) || !getLocalFlag(KEY_IMAGE_COMPRESS, true)) return;
        List<Field> fields = findImagePathFields(messageContent);
        if (WKReader.isEmpty(fields)) return;

        Map<String, String> compressedCache = new HashMap<>();
        for (Field field : fields) {
            try {
                Object value = field.get(messageContent);
                if (!(value instanceof String)) continue;
                String oldPath = (String) value;
                if (!isLocalImagePath(oldPath)) continue;

                String compressed = compressedCache.get(oldPath);
                if (compressed == null) {
                    compressed = compressImagePathIfNeeded(oldPath);
                    compressedCache.put(oldPath, compressed);
                }
                if (!TextUtils.equals(oldPath, compressed)) {
                    field.set(messageContent, compressed);
                }
            } catch (Exception e) {
                Log.e("ChatActivity", "compress image content failed", e);
            }
        }
    }

    private void addChatMoreButton() {
        if (moreIV != null) return;
        moreIV = new TextView(this);
        moreIV.setText("⋮");
        moreIV.setGravity(Gravity.CENTER);
        moreIV.setTextSize(24);
        moreIV.setIncludeFontPadding(false);
        moreIV.setTextColor(ContextCompat.getColor(this, R.color.popupTextColor));
        moreIV.setBackground(Theme.createSelectorDrawable(Theme.getPressedColor()));
        wkVBinding.topLayout.rightView.addView(moreIV, LayoutHelper.createFrame(40, 40, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        moreIV.setOnClickListener(v -> showChatMoreDialog());
    }

    private void showChatMoreDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f));
        root.setBackground(makeGlassBg(24f));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setElevation(AndroidUtilities.dp(14f));
        }

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(makeRoundBg(Color.argb(190, 255, 255, 255), 18f));
        root.addView(group, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addChatMoreRow(group, getString(R.string.set_remark), false, () -> {
            dialog.dismiss();
            showChatRemarkDialog();
        });
        addDivider(group);

        WKChannel curChannel = getCurrentChatChannel();
        boolean muteOn = curChannel != null && curChannel.mute == 1;
        addChatMoreSwitchRow(group, getString(R.string.chat_more_mute_notifications), muteOn, enabled -> {
            dialog.dismiss();
            setChatMute(enabled);
        });
        addDivider(group);

        addChatMoreRow(group, getString(R.string.chat_more_bg), false, () -> {
            dialog.dismiss();
            showChatBackgroundDialog();
        });
        addDivider(group);

        boolean deepSeekEnabled = DeepSeekAssistant.isEnabled(this);
        addChatMoreSwitchRow(group, getString(com.chat.deepseek.R.string.wkdeepseek_name), deepSeekEnabled, enabled -> {
            dialog.dismiss();
            if (enabled) {
                if (!DeepSeekAssistant.isEnabled(this)) {
                    DeepSeekAssistant.requestFirstEnable(this, this::refreshDeepSeekAssistantBar);
                } else {
                    refreshDeepSeekAssistantBar();
                }
            } else {
                DeepSeekAssistant.setEnabled(this, false);
                refreshDeepSeekAssistantBar();
            }
        });
        addDivider(group);

        if (!deepSeekEnabled) {
            addChatMoreRow(group, getString(R.string.chat_more_ai_translate), false, () -> {
                dialog.dismiss();
                showChatAiSettingsDialog();
            });
            addDivider(group);
        }

        addChatMoreRow(group, getString(R.string.clear_history), true, () -> {
            dialog.dismiss();
            clearChatHistoryFromMore();
        });

        if (channelType == WKChannelType.PERSONAL && isFriendChatChannel()) {
            addDivider(group);
            addChatMoreRow(group, getString(R.string.delete_friends), true, () -> {
                dialog.dismiss();
                deleteFriendFromMore();
            });
        }

        if (channelType == WKChannelType.PERSONAL) {
            addDivider(group);
            boolean black = isBlacklistedByMe();
            addChatMoreRow(group, getString(black ? R.string.pull_out_black_list : R.string.push_black_list), !black, () -> {
                dialog.dismiss();
                toggleBlacklistFromMore();
            });
        }

        addDivider(group);
        addChatMoreRow(group, getString(R.string.report), true, () -> {
            dialog.dismiss();
            openChatReportPage();
        });

        dialog.setView(root);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.width = Math.min(AndroidUtilities.dp(330f), AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(36f));
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity = Gravity.CENTER;
                window.setAttributes(lp);
            }
        });
        dialog.show();
    }

    private GradientDrawable makeRoundBg(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(AndroidUtilities.dp(radiusDp));
        return drawable;
    }

    private GradientDrawable makeGlassBg(float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(242, 255, 255, 255), Color.argb(226, 247, 250, 255)}
        );
        drawable.setCornerRadius(AndroidUtilities.dp(radiusDp));
        drawable.setStroke(AndroidUtilities.dp(1f), Color.argb(115, 255, 255, 255));
        return drawable;
    }

    private void addDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(Color.argb(90, 210, 214, 220));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = AndroidUtilities.dp(18f);
        parent.addView(line, lp);
    }

    private void addChatMoreSwitchRow(LinearLayout parent, String text, boolean checked, BooleanAction action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(10f), 0);
        row.setBackground(Theme.createSelectorDrawable(Color.argb(22, 0, 0, 0)));

        TextView textTv = new TextView(this);
        textTv.setText(text);
        textTv.setGravity(Gravity.CENTER_VERTICAL);
        textTv.setSingleLine(true);
        textTv.setTextSize(15);
        textTv.setIncludeFontPadding(false);
        textTv.setTextColor(ContextCompat.getColor(this, R.color.popupTextColor));
        row.addView(textTv, LayoutHelper.createLinear(0, 44, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        row.addView(toggle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (action != null) action.onChanged(isChecked);
        });
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        parent.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
    }

    private void addChatMoreRow(LinearLayout parent, String text, boolean danger, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(10f), 0);
        row.setBackground(Theme.createSelectorDrawable(Color.argb(22, 0, 0, 0)));

        TextView textTv = new TextView(this);
        textTv.setText(text);
        textTv.setGravity(Gravity.CENTER_VERTICAL);
        textTv.setSingleLine(true);
        textTv.setTextSize(15);
        textTv.setIncludeFontPadding(false);
        textTv.setTextColor(danger ? Color.rgb(230, 57, 70) : ContextCompat.getColor(this, R.color.popupTextColor));
        row.addView(textTv, LayoutHelper.createLinear(0, 44, 1f));

        TextView arrowTv = new TextView(this);
        arrowTv.setText("›");
        arrowTv.setGravity(Gravity.CENTER);
        arrowTv.setTextSize(24);
        arrowTv.setIncludeFontPadding(false);
        arrowTv.setTextColor(Color.rgb(118, 124, 132));
        row.addView(arrowTv, LayoutHelper.createLinear(18, 44));

        row.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        parent.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
    }

    private WKChannel getCurrentChatChannel() {
        try {
            return WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isFriendChatChannel() {
        if (channelType != WKChannelType.PERSONAL) return false;
        WKChannel channel = getCurrentChatChannel();
        return channel != null && channel.follow == 1;
    }

    private boolean isBlacklistedByMe() {
        if (channelType != WKChannelType.PERSONAL) return false;
        WKChannel channel = getCurrentChatChannel();
        return channel != null && channel.status == 2;
    }

    private String getCurrentChatShowName() {
        WKChannel channel = getCurrentChatChannel();
        if (channel == null) return "";
        if (!TextUtils.isEmpty(channel.channelRemark)) return channel.channelRemark;
        if (!TextUtils.isEmpty(channel.channelName)) return channel.channelName;
        return "";
    }

    private void showChatRemarkDialog() {
        WKChannel channel = getCurrentChatChannel();
        String old = channel == null ? "" : channel.channelRemark;
        final long requestGeneration = channelGeneration;
        final String targetChannelId = channelId;
        final byte targetChannelType = channelType;
        WKDialogUtils.getInstance().showInputDialog(this, getString(R.string.set_remark), getString(R.string.input_remark), old, getString(R.string.input_remark), 40, text -> {
            if (targetChannelType == WKChannelType.GROUP) {
                GroupModel.getInstance().updateGroupSetting(targetChannelId, "remark", text, (code, msg) ->
                        onRemarkUpdated(code, msg, text, requestGeneration, targetChannelId, targetChannelType));
            } else {
                UserModel.getInstance().updateUserRemark(targetChannelId, text, (code, msg) -> {
                    onRemarkUpdated(code, msg, text, requestGeneration, targetChannelId, targetChannelType);
                    if (code == HttpResponseCode.success) {
                        EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null);
                    }
                });
            }
        });
    }

    private void onRemarkUpdated(int code, String msg, String remark, long requestGeneration,
                                 String targetChannelId, byte targetChannelType) {
        if (code == HttpResponseCode.success) {
            WKChannel target = WKIM.getInstance().getChannelManager().getChannel(targetChannelId, targetChannelType);
            if (target != null) {
                target.channelRemark = remark;
                WKIM.getInstance().getChannelManager().saveOrUpdateChannel(target);
                if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
                    showChannelName(target);
                }
            }
            if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.chat_more_remark_saved));
            }
        } else if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
            WKToastUtils.getInstance().showToastNormal(msg);
        }
    }

    private void toggleChatMute() {
        WKChannel channel = getCurrentChatChannel();
        setChatMute(channel == null || channel.mute != 1);
    }

    private void setChatMute(boolean enabled) {
        final int target = enabled ? 1 : 0;
        final long requestGeneration = channelGeneration;
        final String targetChannelId = channelId;
        final byte targetChannelType = channelType;
        if (targetChannelType == WKChannelType.GROUP) {
            GroupModel.getInstance().updateGroupSetting(targetChannelId, "mute", target,
                    (code, msg) -> onChatMuteUpdated(code, msg, target, requestGeneration, targetChannelId, targetChannelType));
        } else {
            FriendModel.getInstance().updateUserSetting(targetChannelId, "mute", target,
                    (code, msg) -> onChatMuteUpdated(code, msg, target, requestGeneration, targetChannelId, targetChannelType));
        }
    }


    private void onChatMuteUpdated(int code, String msg, int mute, long requestGeneration,
                                   String targetChannelId, byte targetChannelType) {
        if (code == HttpResponseCode.success) {
            WKChannel targetChannel = WKIM.getInstance().getChannelManager().getChannel(targetChannelId, targetChannelType);
            if (targetChannel != null) {
                targetChannel.mute = mute;
                WKIM.getInstance().getChannelManager().saveOrUpdateChannel(targetChannel);
            }
            if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
                WKToastUtils.getInstance().showToastNormal(getString(mute == 1 ? R.string.chat_more_mute_enabled : R.string.chat_more_mute_disabled));
            }
        } else if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
            WKToastUtils.getInstance().showToastNormal(msg);
        }
    }

    private void clearChatHistoryFromMore() {
        String showName = getCurrentChatShowName();
        String content = String.format(getString(R.string.clear_history_tip), TextUtils.isEmpty(showName) ? "" : showName);
        final long requestGeneration = channelGeneration;
        final String targetChannelId = channelId;
        final byte targetChannelType = channelType;
        WKDialogUtils.getInstance().showDialog(this, getString(R.string.clear_history), content, true, "", getString(R.string.base_delete), 0, ContextCompat.getColor(this, R.color.red), index -> {
            if (index != 1) return;
            MsgModel.getInstance().offsetMsg(targetChannelId, targetChannelType, null);
            WKIM.getInstance().getMsgManager().clearWithChannel(targetChannelId, targetChannelType);
            MsgModel.getInstance().clearUnread(targetChannelId, targetChannelType, 0, (code, msg) -> { });
            if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.cleared));
            }
        });
    }

    private void deleteFriendFromMore() {
        String name = getCurrentChatShowName();
        String content = String.format(getString(R.string.delete_friends_tips), TextUtils.isEmpty(name) ? "" : name);
        final long requestGeneration = channelGeneration;
        final String targetChannelId = channelId;
        WKDialogUtils.getInstance().showDialog(this, getString(R.string.delete_friends), content, true, "", getString(R.string.delete), 0, ContextCompat.getColor(this, R.color.red), index -> {
            if (index != 1) return;
            UserModel.getInstance().deleteUser(targetChannelId, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    WKIM.getInstance().getConversationManager().deleteWitchChannel(targetChannelId, WKChannelType.PERSONAL);
                    MsgModel.getInstance().offsetMsg(targetChannelId, WKChannelType.PERSONAL, null);
                    WKIM.getInstance().getMsgManager().clearWithChannel(targetChannelId, WKChannelType.PERSONAL);
                    WKContactsDB.getInstance().updateFriendStatus(targetChannelId, 0);
                    WKIM.getInstance().getChannelManager().updateFollow(targetChannelId, WKChannelType.PERSONAL, 0);
                    EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null);
                    EndpointManager.getInstance().invokes(EndpointCategory.wkExitChat, new WKChannel(targetChannelId, WKChannelType.PERSONAL));
                    if (isCurrentSession(requestGeneration, targetChannelId, WKChannelType.PERSONAL)) {
                        finish();
                    }
                } else if (isCurrentSession(requestGeneration, targetChannelId, WKChannelType.PERSONAL)) {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        });
    }

    private void toggleBlacklistFromMore() {
        final boolean black = isBlacklistedByMe();
        int titleRes = black ? R.string.pull_out_black_list : R.string.push_black_list;
        int tipRes = black ? R.string.pull_out_black_list_tips : R.string.join_black_list_tips;
        final long requestGeneration = channelGeneration;
        final String targetChannelId = channelId;
        final byte targetChannelType = channelType;
        WKDialogUtils.getInstance().showDialog(this, getString(titleRes), getString(tipRes), true, "", getString(R.string.sure), 0, black ? Theme.colorAccount : ContextCompat.getColor(this, R.color.red), index -> {
            if (index != 1) return;
            if (black) {
                UserModel.getInstance().removeBlackList(targetChannelId, (code, msg) ->
                        onBlacklistUpdated(code, msg, false, requestGeneration, targetChannelId, targetChannelType));
            } else {
                UserModel.getInstance().addBlackList(targetChannelId, (code, msg) ->
                        onBlacklistUpdated(code, msg, true, requestGeneration, targetChannelId, targetChannelType));
            }
        });
    }

    private void onBlacklistUpdated(int code, String msg, boolean black, long requestGeneration,
                                    String targetChannelId, byte targetChannelType) {
        if (code == HttpResponseCode.success) {
            WKChannel target = WKIM.getInstance().getChannelManager().getChannel(targetChannelId, targetChannelType);
            if (target != null) {
                target.status = black ? 2 : 1;
                WKIM.getInstance().getChannelManager().saveOrUpdateChannel(target);
            }
            if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
                WKToastUtils.getInstance().showToastNormal(getString(black ? R.string.chat_more_blacklist_added : R.string.chat_more_blacklist_removed));
            }
        } else if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
            WKToastUtils.getInstance().showToastNormal(msg);
        }
    }

    private void openChatReportPage() {
        Intent intent = new Intent(this, WKWebViewActivity.class);
        intent.putExtra("channelType", channelType);
        intent.putExtra("channelID", channelId);
        intent.putExtra("url", WKApiConfig.baseWebUrl + "report.html");
        startActivity(intent);
    }

    private void openRtcDebugLog() {
        try {
            Object ok = EndpointManager.getInstance().invoke("rtc_open_debug_log", this);
            if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                WKToastUtils.getInstance().showToast("RTC 日志入口未初始化");
            }
        } catch (Exception e) {
            WKToastUtils.getInstance().showToast("打开 RTC 日志失败");
        }
    }

    private void showChatBackgroundDialog() {
        final long requestGeneration = channelGeneration;
        final String targetChannelId = channelId;
        final byte targetChannelType = channelType;
        String[] items = new String[11];
        items[0] = getString(R.string.chat_bg_default);
        for (int i = 1; i <= 9; i++) {
            items[i] = getString(R.string.chat_bg_builtin, i);
        }
        items[10] = getString(R.string.chat_bg_choose_local);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.chat_more_bg)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        clearLocalChatBackground(requestGeneration, targetChannelId, targetChannelType);
                    } else if (which >= 1 && which <= 9) {
                        applyBuiltinChatBackground(which, requestGeneration, targetChannelId, targetChannelType);
                        if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
                            WKToastUtils.getInstance().showToast(getString(R.string.chat_bg_saved));
                        }
                    } else {
                        captureChatBackgroundTarget(requestGeneration, targetChannelId, targetChannelType);
                        chooseChatBgLauncher.launch("image/*");
                    }
                })
                .show();
        applyGlassDialogStyle(dialog);
    }

    private void applyGlassDialogStyle(AlertDialog dialog) {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(242, 255, 255, 255), Color.argb(234, 244, 247, 255), Color.argb(237, 239, 246, 255)}
        );
        bg.setCornerRadius(AndroidUtilities.dp(22f));
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(bg);
            dialog.getWindow().setDimAmount(0.28f);
        }
    }

    private EditText createSettingEdit(LinearLayout parent, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ContextCompat.getColor(this, R.color.color999));
        tv.setTextSize(13);
        parent.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 2));
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setText(value);
        editText.setTextSize(14);
        parent.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return editText;
    }

    private void initDeepSeekAssistantBar() {
        wkVBinding.deepSeekReplyBtn.setOnClickListener(v -> openDeepSeekAction(DeepSeekRequest.ACTION_REPLY));
        wkVBinding.deepSeekPolishBtn.setOnClickListener(v -> openDeepSeekAction(DeepSeekRequest.ACTION_POLISH));
        wkVBinding.deepSeekSettingsBtn.setOnClickListener(v -> {
            DeepSeekRequest request = buildDeepSeekRequest(DeepSeekRequest.ACTION_REPLY);
            DeepSeekAssistant.showSettings(this, request, this::refreshDeepSeekAssistantBar);
        });
        refreshDeepSeekAssistantBar();
    }

    private void refreshDeepSeekAssistantBar() {
        if (wkVBinding == null) return;
        boolean enabled = DeepSeekAssistant.isEnabled(this);
        boolean oldFlagsSaved = getLocalFlag(KEY_DEEPSEEK_OLD_FLAGS_SAVED, false);
        if (enabled) {
            // DeepSeek 模式替代旧助手，但关闭后要恢复用户原来的翻译设置。
            if (!oldFlagsSaved) {
                putLocalFlag(KEY_DEEPSEEK_OLD_SEND_TRANSLATE, getLocalFlag(KEY_AI_SEND_TRANSLATE, false));
                putLocalFlag(KEY_DEEPSEEK_OLD_WINGMAN, getLocalFlag(KEY_AI_WINGMAN_ENABLED, false));
                putLocalFlag(KEY_DEEPSEEK_OLD_FLAGS_SAVED, true);
            }
            putLocalFlag(KEY_AI_SEND_TRANSLATE, false);
            putLocalFlag(KEY_AI_WINGMAN_ENABLED, false);
        } else if (oldFlagsSaved) {
            putLocalFlag(KEY_AI_SEND_TRANSLATE, getLocalFlag(KEY_DEEPSEEK_OLD_SEND_TRANSLATE, false));
            putLocalFlag(KEY_AI_WINGMAN_ENABLED, getLocalFlag(KEY_DEEPSEEK_OLD_WINGMAN, false));
            putLocalFlag(KEY_DEEPSEEK_OLD_FLAGS_SAVED, false);
        }
        wkVBinding.deepSeekAssistBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        wkVBinding.aiAssistBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void openDeepSeekAction(int action) {
        if (channelType != WKChannelType.PERSONAL) {
            WKToastUtils.getInstance().showToastNormal("DeepSeek 聊天助手当前只支持单聊");
            return;
        }
        if (!DeepSeekAssistant.isEnabled(this)) {
            DeepSeekAssistant.requestFirstEnable(this, this::refreshDeepSeekAssistantBar);
            return;
        }
        DeepSeekRequest request = buildDeepSeekRequest(action);
        final long requestGeneration = channelGeneration;
        final String requestChannelId = request.channelId;
        final byte requestChannelType = channelType;
        DeepSeekAssistant.openAction(this, request, (text, localDisplayText, sendNow) -> runOnUiThread(() -> {
            if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)
                    || TextUtils.isEmpty(text) || wkVBinding == null || chatPanelManager == null) {
                return;
            }

            restoreChatAfterDeepSeek(requestGeneration, requestChannelId, requestChannelType);
            chatPanelManager.setDeepSeekReplyDraft(text, localDisplayText);
            EditText editText = wkVBinding.editText;
            editText.requestFocus();

            if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                wkVBinding.recyclerView.post(() -> {
                    if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) {
                        scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                });
            }

            if (sendNow) {
                mainHandler.postDelayed(() -> {
                    if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)
                            && wkVBinding != null && !TextUtils.isEmpty(wkVBinding.editText.getText())) {
                        wkVBinding.sendIV.performClick();
                        if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                            mainHandler.postDelayed(() -> {
                                if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) {
                                    scrollToPosition(chatAdapter.getItemCount() - 1);
                                }
                            }, 180);
                        }
                    }
                }, 180);
            } else {
                mainHandler.postDelayed(() -> {
                    if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) return;
                    if (mHelper != null) mHelper.toKeyboardState();
                    SoftKeyboardUtils.getInstance().showSoftKeyBoard(this, editText);
                }, 180);
            }
        }), () -> {
            if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) {
                restoreChatAfterDeepSeek(requestGeneration, requestChannelId, requestChannelType);
            }
        });
    }

    private void restoreChatAfterDeepSeek(long requestGeneration, String requestChannelId, byte requestChannelType) {
        runOnUiThread(() -> {
            if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)
                    || wkVBinding == null) {
                return;
            }
            if (mHelper != null) mHelper.resetState();
            wkVBinding.recyclerViewLayout.setVisibility(View.VISIBLE);
            wkVBinding.recyclerView.setVisibility(View.VISIBLE);
            wkVBinding.recyclerView.setAlpha(1f);
            wkVBinding.recyclerView.setTranslationY(0f);
            wkVBinding.recyclerViewLayout.setTranslationY(0f);
            if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                wkVBinding.recyclerView.post(() -> {
                    if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)
                            && chatAdapter != null && chatAdapter.getItemCount() > 0) {
                        scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                });
            }
        });
    }

    private DeepSeekRequest buildDeepSeekRequest(int action) {
        DeepSeekRequest request = new DeepSeekRequest();
        request.action = action;
        request.channelId = channelId;
        request.channelType = channelType;
        request.selfUid = loginUID;
        request.draft = wkVBinding == null || wkVBinding.editText.getText() == null
                ? "" : wkVBinding.editText.getText().toString().trim();
        request.myNativeLanguage = getMyProfileLanguage("native_languages", "native_language", "自动");
        request.myLearningLanguages = getMyProfileLanguage("learning_languages", "learning_language", "");
        request.peerNativeLanguage = getChannelLanguage("native_languages", "native_language", "自动");
        request.peerLearningLanguages = getChannelLanguage("learning_languages", "learning_language", "");
        populateDeepSeekContextSnapshot(request);
        return request;
    }

    /**
     * Copies only messages already loaded by this ChatActivity. This is deliberately read-only:
     * calling the IM history-sync API here can refresh the open adapter and make visible messages
     * disappear while the DeepSeek WebView is open.
     */
    private void populateDeepSeekContextSnapshot(DeepSeekRequest request) {
        if (request == null || chatAdapter == null || WKReader.isEmpty(chatAdapter.getData())) return;

        StringBuilder snapshot = new StringBuilder();
        String latestPeerText = "";
        String latestPeerId = "";
        int kept = 0;

        for (WKUIChatMsgItemEntity item : chatAdapter.getData()) {
            if (item == null || item.wkMsg == null) continue;
            WKMsg msg = item.wkMsg;
            if (!shouldUseForMsgLinks(msg)) continue;
            if (msg.remoteExtra != null
                    && (msg.remoteExtra.revoke == 1 || msg.remoteExtra.isMutualDeleted == 1)) {
                continue;
            }

            String content = "";
            try {
                if (msg.baseContentMsgModel != null) {
                    content = msg.baseContentMsgModel.getDisplayContent();
                }
            } catch (Exception ignored) {
            }
            if (TextUtils.isEmpty(content)) content = msg.content;
            content = sanitizeDeepSeekContextText(content);
            if (TextUtils.isEmpty(content)) continue;

            boolean mine = TextUtils.equals(loginUID, msg.fromUID);
            // Local back-translation is not part of the peer-visible message.
            if (mine) {
                int backTranslationAt = content.lastIndexOf("\n回译：");
                if (backTranslationAt > 0) {
                    content = content.substring(0, backTranslationAt).trim();
                }
            }
            if (snapshot.length() > 0) snapshot.append('\n');
            snapshot.append(mine ? "我：" : "对方：").append(content);
            kept++;

            if (!mine && msg.type == WKContentType.WK_TEXT) {
                latestPeerText = content;
                latestPeerId = deepSeekMessageId(msg);
            }
        }

        request.contextSnapshot = snapshot.toString();
        request.contextSnapshotCount = kept;
        request.contextLimit = 0;
        if (TextUtils.isEmpty(request.targetMessageText) && !TextUtils.isEmpty(latestPeerText)) {
            request.targetMessageText = latestPeerText;
            request.targetMessageId = latestPeerId;
        }
    }

    private String sanitizeDeepSeekContextText(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String clean = value.replace('\u0000', ' ').replace("```", "` ` `").trim();
        if ((clean.startsWith("{") && clean.endsWith("}"))
                || clean.startsWith("__cp_harmony_rtc__:")) {
            return "";
        }
        // Basic local data minimisation before any chat text reaches the third-party webpage.
        clean = clean.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[邮箱已隐藏]");
        clean = clean.replaceAll("(?<!\\d)(?:\\+?\\d[\\d\\s-]{6,}\\d)(?!\\d)", "[号码已隐藏]");
        clean = clean.replaceAll("(?<!\\d)\\d{17}[0-9Xx](?!\\d)", "[证件号已隐藏]");
        return clean;
    }

    private String deepSeekMessageId(WKMsg msg) {
        if (msg == null) return "";
        if (!TextUtils.isEmpty(msg.messageID) && !"0".equals(msg.messageID)) return msg.messageID;
        if (!TextUtils.isEmpty(msg.clientMsgNO)) return msg.clientMsgNO;
        if (msg.messageSeq > 0) return String.valueOf(msg.messageSeq);
        if (msg.orderSeq > 0) return String.valueOf(msg.orderSeq);
        return "";
    }

    private String getMyProfileLanguage(String plural, String singular, String fallback) {
        String prefix = TextUtils.isEmpty(loginUID) ? "current" : loginUID;
        android.content.SharedPreferences sp = getSharedPreferences("front_profile_extra", MODE_PRIVATE);
        String value = sp.getString(prefix + "_" + plural, "");
        if (TextUtils.isEmpty(value)) value = sp.getString(prefix + "_" + singular, "");
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private String getChannelLanguage(String plural, String singular, String fallback) {
        WKChannel channel = getCurrentChatChannel();
        if (channel == null) return fallback;
        Object value = null;
        if (channel.remoteExtraMap != null) {
            value = channel.remoteExtraMap.get(plural);
            if (value == null) value = channel.remoteExtraMap.get(singular);
        }
        if (value == null && channel.localExtra != null) {
            value = channel.localExtra.get(plural);
            if (value == null) value = channel.localExtra.get(singular);
        }
        String normalized = normalizeLanguageValue(value);
        return TextUtils.isEmpty(normalized) ? fallback : normalized;
    }

    private String normalizeLanguageValue(Object value) {
        if (value == null) return "";
        if (value instanceof List) {
            StringBuilder out = new StringBuilder();
            for (Object item : (List<?>) value) {
                if (item == null || TextUtils.isEmpty(item.toString())) continue;
                if (out.length() > 0) out.append(", ");
                out.append(item.toString().trim());
            }
            return out.toString();
        }
        String text = value.toString().trim();
        if ((text.startsWith("[") && text.endsWith("]")) || (text.startsWith("{") && text.endsWith("}"))) {
            text = text.replace("[", "").replace("]", "").replace("\"", "").trim();
        }
        return text;
    }

    private void showChatAiSettingsDialog() {
        TranslateSettingsActivity.Companion.start(this, "chat_more");
    }


    private void showCallPopupMenuLower(View anchor, long requestGeneration,
                                        String targetChannelId, byte targetChannelType) {
        dismissCallPopup();
        try {
            final int popupWidth = AndroidUtilities.dp(184f);
            final PopupWindow[] popupRef = new PopupWindow[1];

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f));
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.WHITE, Color.rgb(250, 251, 255), Color.WHITE}
            );
            bg.setCornerRadius(AndroidUtilities.dp(18f));
            root.setBackground(bg);

            root.addView(createCallPopupItem(getString(R.string.video_call), R.mipmap.chat_calls_video, () -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                p2pCall(requestGeneration, targetChannelId, targetChannelType, 1);
            }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            root.addView(createCallPopupItem(getString(R.string.audio_call), R.mipmap.chat_calls_voice, () -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                p2pCall(requestGeneration, targetChannelId, targetChannelType, 0);
            }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

            PopupWindow popup = new PopupWindow(root, popupWidth, LayoutHelper.WRAP_CONTENT, true);
            popupRef[0] = popup;
            callPopupWindow = popup;
            popup.setOutsideTouchable(true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                popup.setElevation(AndroidUtilities.dp(14f));
            }

            View dimView = createCallPopupDimView();
            if (dimView != null) dimView.setOnClickListener(v -> popup.dismiss());
            popup.setOnDismissListener(() -> {
                if (callPopupWindow == popup) callPopupWindow = null;
                removeCallPopupDimView(dimView);
            });

            if (dimView != null) {
                try {
                    ((ViewGroup) getWindow().getDecorView()).addView(dimView, new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    ));
                } catch (Exception ignored) {
                }
            }

            int xOffset = -(popupWidth - AndroidUtilities.dp(42f));
            int yOffset = AndroidUtilities.dp(14f);
            popup.showAsDropDown(anchor, xOffset, yOffset);
        } catch (Exception e) {
            callPopupWindow = null;
            Log.e("ChatActivity", "show call popup failed", e);
            List<PopupMenuItem> fallback = new ArrayList<>();
            fallback.add(new PopupMenuItem(getString(R.string.video_call), R.mipmap.chat_calls_video,
                    () -> p2pCall(requestGeneration, targetChannelId, targetChannelType, 1)));
            fallback.add(new PopupMenuItem(getString(R.string.audio_call), R.mipmap.chat_calls_voice,
                    () -> p2pCall(requestGeneration, targetChannelId, targetChannelType, 0)));
            WKDialogUtils.getInstance().showScreenPopup(anchor, fallback);
        }
    }

    private View createCallPopupItem(String title, int iconRes, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(12f), 0);
        row.setBackground(Theme.createSelectorDrawable(Color.argb(22, 0, 0, 0)));

        ImageView icon = new AppCompatImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.popupTextColor), PorterDuff.Mode.MULTIPLY));
        row.addView(icon, LayoutHelper.createLinear(24, 24));

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(15);
        tv.setSingleLine(true);
        tv.setTextColor(ContextCompat.getColor(this, R.color.popupTextColor));
        row.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        row.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        return row;
    }

    private View createCallPopupDimView() {
        // 通话菜单不要加半透明遮罩。PopupWindow 已设置 outsideTouchable，点击外部仍可关闭。
        return null;
    }

    private void removeCallPopupDimView(View dimView) {
        if (dimView == null) return;
        try {
            ViewGroup parent = (ViewGroup) dimView.getParent();
            if (parent != null) parent.removeView(dimView);
        } catch (Exception ignored) {
        }
    }

    private void initRtcCallModule() {
        EndpointManager.getInstance().invoke("rtc_init", null);
    }

    private void rtcLog(String text) {
        // RTC diagnostic logs are disabled in the chat UI.
    }

    private boolean isRtcSignalMessage(WKMsg msg) {
        if (msg == null) return false;
        try {
            Object result = EndpointManager.getInstance().invoke("rtc_is_signal_msg", msg);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean shouldHideBasicFromChatList(WKMsg msg) {
        if (msg == null) return true;
        if (msg.isDeleted == 1) return true;
        if (hasNoPersistHeader(msg)) return true;
        return msg.type == WKContentType.WK_INSIDE_MSG
                || msg.type == WKContentType.withdrawSystemInfo;
    }

    private boolean shouldHideFromChatList(WKMsg msg) {
        return shouldHideBasicFromChatList(msg) || isRtcSignalMessage(msg);
    }

    private boolean hasNoPersistHeader(WKMsg msg) {
        if (msg == null || msg.header == null) return false;
        try {
            return msg.header.noPersist;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean shouldUseForMsgLinks(WKMsg msg) {
        if (shouldHideFromChatList(msg)) return false;
        int type = msg.type;
        return type != WKContentType.msgPromptTime
                && type != WKContentType.msgPromptNewMsg
                && type != WKContentType.loading
                && type != WKContentType.emptyView
                && type != WKContentType.spanEmptyView
                && type != WKContentType.typing
                && type != WKContentType.noRelation;
    }

    private boolean isReceivedTextMsgForInlineTranslate(WKMsg msg) {
        if (msg == null) return false;
        if (shouldHideFromChatList(msg)) return false;
        if (msg.type != WKContentType.WK_TEXT) return false;
        if (msg.isDeleted == 1) return false;
        if (msg.remoteExtra != null && (msg.remoteExtra.revoke == 1 || msg.remoteExtra.isMutualDeleted == 1)) {
            return false;
        }
        if (TextUtils.isEmpty(msg.fromUID) || TextUtils.equals(msg.fromUID, loginUID)) return false;

        String displayContent = "";
        if (msg.baseContentMsgModel != null) {
            try {
                displayContent = msg.baseContentMsgModel.getDisplayContent();
            } catch (Exception ignored) {
            }
        }
        if (TextUtils.isEmpty(displayContent)) {
            displayContent = msg.content;
        }
        return !TextUtils.isEmpty(displayContent);
    }

    private int findLatestReceivedTextMsgIndex() {
        if (chatAdapter == null || WKReader.isEmpty(chatAdapter.getData())) return -1;
        for (int i = chatAdapter.getData().size() - 1; i >= 0; i--) {
            WKUIChatMsgItemEntity item = chatAdapter.getData().get(i);
            if (item != null && isReceivedTextMsgForInlineTranslate(item.wkMsg)) {
                return i;
            }
        }
        return -1;
    }

    private void refreshOldInlineTranslateButton(int oldLatestReceivedTextIndex, WKMsg newMsg) {
        if (oldLatestReceivedTextIndex < 0) return;
        if (chatAdapter == null || WKReader.isEmpty(chatAdapter.getData())) return;
        if (!isReceivedTextMsgForInlineTranslate(newMsg)) return;
        if (oldLatestReceivedTextIndex >= chatAdapter.getData().size()) return;

        WKUIChatMsgItemEntity oldItem = chatAdapter.getData().get(oldLatestReceivedTextIndex);
        if (oldItem == null || !isReceivedTextMsgForInlineTranslate(oldItem.wkMsg)) return;

        // 这里必须做完整数据刷新，不能用 payload 局部刷新。
        // payload 只会刷新头像/背景/状态，WKTextProvider.setData() 不会重新执行，
        // 旧“最后一条对方文字消息”上的快捷翻译按钮就会残留。
        chatAdapter.notifyData(oldLatestReceivedTextIndex);
    }

    private boolean isSameMessage(WKMsg first, WKMsg second) {
        if (first == null || second == null) return false;
        if (first.clientSeq != 0 && second.clientSeq != 0 && first.clientSeq == second.clientSeq) return true;
        if (!TextUtils.isEmpty(first.clientMsgNO)
                && !TextUtils.isEmpty(second.clientMsgNO)
                && TextUtils.equals(first.clientMsgNO, second.clientMsgNO)) {
            return true;
        }
        if (TextUtils.isEmpty(first.messageID) || "0".equals(first.messageID)
                || TextUtils.isEmpty(second.messageID) || "0".equals(second.messageID)) {
            return false;
        }
        return TextUtils.equals(first.messageID, second.messageID);
    }

    private boolean hasRemoteExtra(WKUIChatMsgItemEntity item) {
        return item != null && item.wkMsg != null && item.wkMsg.remoteExtra != null;
    }

    private WKChannel getChatChannelInfo(String targetChannelId, byte targetChannelType) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(targetChannelId, targetChannelType);
        return channel == null ? new WKChannel(targetChannelId, targetChannelType) : channel;
    }

    private String getRtcPeerName(String targetChannelId, byte targetChannelType) {
        WKChannel channel = getChatChannelInfo(targetChannelId, targetChannelType);
        String name = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
        return TextUtils.isEmpty(name) ? getString(R.string.app_name) : name;
    }

    private String getRtcPeerAvatar(String targetChannelId, byte targetChannelType) {
        WKChannel channel = getChatChannelInfo(targetChannelId, targetChannelType);
        return TextUtils.isEmpty(channel.avatar) ? "" : channel.avatar;
    }


    private int getTopPinViewHeight() {
        int totalHeight = 0;
        if (isShowCallingView) {
            totalHeight += callingViewHeight;
        }
        if (isShowPinnedView) {
            totalHeight += pinnedViewHeight;
        }
        return totalHeight;
    }

    private boolean validateP2pCallTarget(long requestGeneration, String targetChannelId,
                                          byte targetChannelType, boolean requireCurrentSession) {
        if (requireCurrentSession
                && !isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
            return false;
        }
        if (TextUtils.isEmpty(targetChannelId)) {
            WKToastUtils.getInstance().showToast("无法发起通话");
            return false;
        }
        if (targetChannelType != WKChannelType.PERSONAL) {
            WKToastUtils.getInstance().showToast("群通话后续再接，当前只支持单聊 P2P");
            return false;
        }
        if (TextUtils.equals(targetChannelId, loginUID)) {
            WKToastUtils.getInstance().showToast("不能给自己发起通话");
            rtcLog("p2pCall blocked self channelId=" + targetChannelId);
            return false;
        }

        WKChannel targetChannel = getChatChannelInfo(targetChannelId, targetChannelType);
        WKChannelMember member = WKIM.getInstance().getChannelMembersManager()
                .getMember(targetChannelId, targetChannelType, loginUID);
        if (targetChannel.forbidden == 1 || (member != null && member.forbiddenExpirationTime > 0)) {
            WKToastUtils.getInstance().showToast(getString(R.string.can_not_call_forbidden));
            return false;
        }
        if (UserUtils.getInstance().checkMyFriendDelete(targetChannelId)
                || UserUtils.getInstance().checkFriendRelation(targetChannelId)) {
            showToast(R.string.non_friend_relationship);
            return false;
        }
        if (UserUtils.getInstance().checkBlacklist(targetChannelId)) {
            showToast(R.string.call_be_blacklist);
            return false;
        }
        if (targetChannel.status == WKChannelStatus.statusBlacklist) {
            showToast(R.string.call_blacklist);
            return false;
        }
        return true;
    }

    private void p2pCall(long requestGeneration, String targetChannelId,
                         byte targetChannelType, int callType) {
        if (!validateP2pCallTarget(requestGeneration, targetChannelId, targetChannelType, true)) {
            return;
        }

        rtcLog("p2pCall click channelId=" + targetChannelId + " login=" + loginUID + " type=" + callType);
        initRtcCallModule();

        // 显式传固定的 peer_uid，避免弹层打开后切换会话导致呼叫对象变化。
        Map<String, Object> request = new HashMap<>();
        request.put("activity", this);
        request.put("peer_uid", targetChannelId);
        request.put("peer_name", getRtcPeerName(targetChannelId, targetChannelType));
        request.put("peer_avatar", getRtcPeerAvatar(targetChannelId, targetChannelType));
        request.put("call_type", callType);

        Object handled = EndpointManager.getInstance().invoke("wk_p2p_call", request);
        rtcLog("wk_p2p_call result=" + handled + " peer=" + targetChannelId);
        if (!(handled instanceof Boolean) || !((Boolean) handled)) {
            // 旧 RTCMenu 会从当前 Activity 取频道，因此上面必须确保会话仍是打开弹层时的目标。
            handled = EndpointManager.getInstance().invoke("wk_p2p_call", new RTCMenu(this, callType));
        }
        if (!(handled instanceof Boolean) || !((Boolean) handled)) {
            WKToastUtils.getInstance().showToast("通话插件未初始化");
        }
    }

    private void dismissCallPopup() {
        PopupWindow popup = callPopupWindow;
        callPopupWindow = null;
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
        }
    }

    private void toggleStatusBarMode() {
        Window window = getWindow();
        if (window == null) return;

        // 让聊天背景真正绘制到状态栏和导航栏后方。IME 位移仍交给 PanelSwitchLayout。
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        boolean useDarkIcons = !Theme.getDarkModeStatus(this);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(useDarkIcons);
        controller.setAppearanceLightNavigationBars(useDarkIcons);
    }

    private void applyChatSystemBarInsets() {
        if (wkVBinding == null || wkVBinding.rootView == null || wkVBinding.bottomView == null) return;

        final View statusBarSpacer = wkVBinding.topLayout.statusBarSpacer;
        final View titleView = wkVBinding.topLayout.titleView;
        final int titleBaseLeft = titleView.getPaddingLeft();
        final int titleBaseTop = titleView.getPaddingTop();
        final int titleBaseRight = titleView.getPaddingRight();
        final int titleBaseBottom = titleView.getPaddingBottom();
        final int bottomBaseLeft = wkVBinding.bottomView.getPaddingLeft();
        final int bottomBaseTop = wkVBinding.bottomView.getPaddingTop();
        final int bottomBaseRight = wkVBinding.bottomView.getPaddingRight();
        final int bottomBaseBottom = wkVBinding.bottomView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(wkVBinding.rootView, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            if (statusBarSpacer != null && statusBarSpacer.getLayoutParams() != null
                    && statusBarSpacer.getLayoutParams().height != bars.top) {
                ViewGroup.LayoutParams params = statusBarSpacer.getLayoutParams();
                params.height = bars.top;
                statusBarSpacer.setLayoutParams(params);
            }

            titleView.setPadding(
                    titleBaseLeft + bars.left,
                    titleBaseTop,
                    titleBaseRight + bars.right,
                    titleBaseBottom
            );
            wkVBinding.bottomView.setPadding(
                    bottomBaseLeft + bars.left,
                    bottomBaseTop,
                    bottomBaseRight + bars.right,
                    bottomBaseBottom + bars.bottom
            );
            updateFloatingComposerSpacing();
            return insets;
        });
        ViewCompat.requestApplyInsets(wkVBinding.rootView);
    }

    private void installFloatingComposerLayout() {
        if (floatingComposerLayoutInstalled || wkVBinding == null || wkVBinding.recyclerView == null) return;
        floatingComposerLayoutInstalled = true;

        recyclerBasePaddingLeft = wkVBinding.recyclerView.getPaddingLeft();
        recyclerBasePaddingTop = wkVBinding.recyclerView.getPaddingTop();
        recyclerBasePaddingRight = wkVBinding.recyclerView.getPaddingRight();
        recyclerBasePaddingBottom = wkVBinding.recyclerView.getPaddingBottom();
        wkVBinding.recyclerView.setClipToPadding(false);

        View unreadRoot = wkVBinding.chatUnreadLayout.getRoot();
        if (unreadRoot != null && unreadRoot.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            unreadBaseBottomMargin =
                    ((RelativeLayout.LayoutParams) unreadRoot.getLayoutParams()).bottomMargin;
        }
        if (wkVBinding.recyclerViewContentLayout.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            contentOverlayBaseBottomMargin =
                    ((RelativeLayout.LayoutParams) wkVBinding.recyclerViewContentLayout.getLayoutParams()).bottomMargin;
        }

        floatingComposerLayoutChangeListener = (view, left, top, right, bottom,
                                                 oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top != oldBottom - oldTop) {
                updateFloatingComposerSpacing();
            }
        };
        wkVBinding.bottomView.addOnLayoutChangeListener(floatingComposerLayoutChangeListener);
        wkVBinding.bottomView.post(this::updateFloatingComposerSpacing);
    }

    private void updateFloatingComposerSpacing() {
        if (!floatingComposerLayoutInstalled || wkVBinding == null || wkVBinding.bottomView == null
                || wkVBinding.recyclerView == null) return;
        int composerHeight = wkVBinding.bottomView.getHeight();
        if (composerHeight <= 0) return;

        boolean wasAtBottom = !wkVBinding.recyclerView.canScrollVertically(1);
        int visualGap = AndroidUtilities.dp(8f);
        int targetPaddingBottom = Math.max(recyclerBasePaddingBottom, composerHeight + visualGap);
        boolean paddingChanged = wkVBinding.recyclerView.getPaddingBottom() != targetPaddingBottom;
        if (paddingChanged) {
            wkVBinding.recyclerView.setPadding(
                    recyclerBasePaddingLeft,
                    recyclerBasePaddingTop,
                    recyclerBasePaddingRight,
                    targetPaddingBottom
            );
        }

        View unreadRoot = wkVBinding.chatUnreadLayout.getRoot();
        if (unreadRoot != null && unreadRoot.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) unreadRoot.getLayoutParams();
            int margin = unreadBaseBottomMargin + composerHeight;
            if (params.bottomMargin != margin) {
                params.bottomMargin = margin;
                unreadRoot.setLayoutParams(params);
            }
        }

        if (wkVBinding.recyclerViewContentLayout.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams params =
                    (RelativeLayout.LayoutParams) wkVBinding.recyclerViewContentLayout.getLayoutParams();
            int margin = contentOverlayBaseBottomMargin + composerHeight;
            if (params.bottomMargin != margin) {
                params.bottomMargin = margin;
                wkVBinding.recyclerViewContentLayout.setLayoutParams(params);
            }
        }

        // 只有 padding 实际变化时才校正到底部，避免每次 inset 分发都触发无意义滚动。
        if (paddingChanged && wasAtBottom && chatAdapter != null && chatAdapter.getItemCount() > 0) {
            wkVBinding.recyclerView.post(this::chatRecyclerViewScrollToEnd);
        }
    }

    private boolean initParam() {
        toggleStatusBarMode();
        Intent currentIntent = getIntent();
        String targetChannelId = currentIntent == null ? null : currentIntent.getStringExtra("channelId");
        channelId = TextUtils.isEmpty(targetChannelId) ? "" : targetChannelId;
        channelType = currentIntent == null
                ? WKChannelType.PERSONAL
                : currentIntent.getByteExtra("channelType", WKChannelType.PERSONAL);
        if (TextUtils.isEmpty(channelId)) {
            Log.e("ChatActivity", "missing channelId, close invalid chat page");
            return false;
        }

        maxMsgOrderSeq = WKIM.getInstance().getMsgManager().getMaxOrderSeqWithChannel(channelId, channelType);
        maxMsgSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(channelId, channelType);
        resetHideChannelAllPinnedMessage();

        // 转发确认框可能在切换到另一个会话后才回调，发送目标必须固定为打开页面时的会话。
        if (currentIntent.hasExtra("msgContentList")) {
            final ArrayList<WKMessageContent> forwardContents = currentIntent.getParcelableArrayListExtra("msgContentList");
            currentIntent.removeExtra("msgContentList");
            if (WKReader.isNotEmpty(forwardContents)) {
                WKChannel localChannel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
                final WKChannel forwardChannel = localChannel == null
                        ? new WKChannel(channelId, channelType)
                        : localChannel;
                final int receipt = forwardChannel.receipt;
                List<WKChannel> channels = new ArrayList<>();
                channels.add(forwardChannel);
                WKUIKitApplication.getInstance().showChatConfirmDialog(this, channels, forwardContents, (list, confirmedContents) -> {
                    List<WKMessageContent> contents = WKReader.isNotEmpty(confirmedContents)
                            ? confirmedContents
                            : forwardContents;
                    List<SendMsgEntity> msgList = new ArrayList<>();
                    WKSendOptions options = new WKSendOptions();
                    options.setting.receipt = receipt;
                    for (WKMessageContent content : contents) {
                        if (content != null) {
                            msgList.add(new SendMsgEntity(content, forwardChannel, options));
                        }
                    }
                    if (WKReader.isNotEmpty(msgList)) {
                        WKSendMsgUtils.getInstance().sendMessages(msgList);
                    }
                });
            }
        }
        return true;
    }

    private void initSwipeBackFinish() {
        SwipeBackLayout mSwipeBackLayout = getSwipeBackLayout();
        mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);
        mSwipeBackLayout.setEnableGesture(true);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initSwipeBackFinish();
        wkVBinding = DataBindingUtil.setContentView(this, R.layout.act_chat_layout);
        if (!initParam()) {
            finish();
            return;
        }
        initRtcCallModule();
        initView();
        initListener();
        ActManagerUtils.getInstance().addActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isShowChatActivity = true;
        WKUIKitApplication.getInstance().chattingChannelID = channelId;
        isUploadReadMsg = true;
        if (closeCurrentTopicRoomIfExpired()) return;
        refreshDeepSeekAssistantBar();
        if (chatPanelManager != null) {
            chatPanelManager.initRefreshListener();
        }
        EndpointManager.getInstance().invoke("start_screen_shot", this);

        Object addSecurityModule = EndpointManager.getInstance().invoke("add_security_module", null);
        if (addSecurityModule instanceof Boolean) {
            boolean disable_screenshot;
            String uid = WKConfig.getInstance().getUid();
            if (!TextUtils.isEmpty(uid)) {
                disable_screenshot = WKSharedPreferencesUtil.getInstance().getBoolean(uid + "_disable_screenshot", false);
            } else {
                disable_screenshot = WKSharedPreferencesUtil.getInstance().getBoolean("disable_screenshot", false);
            }
            if (disable_screenshot)
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mHelper == null) {
            mHelper = new PanelSwitchHelper.Builder(this)
                    .addKeyboardStateListener((visible, height) -> {
                        if (visible && height > 0) {
                            WKConstants.setKeyboardHeight(height);
                        }
                    })
                    .addPanelChangeListener(new OnPanelChangeListener() {

                        @Override
                        public void onKeyboard() {
                            if (chatPanelManager != null) {
                                chatPanelManager.resetToolBar();
                            }
                            if (wkVBinding != null) {
                                SoftKeyboardUtils.getInstance().requestFocus(wkVBinding.editText);
                            }
                        }

                        @Override
                        public void onNone() {
                        }

                        @Override
                        public void onPanel(IPanelView view) {
                        }


                        @Override
                        public void onPanelSizeChange(IPanelView panelView, boolean portrait, int oldWidth, int oldHeight, int width, int height) {

                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            View bottomView = findViewById(R.id.bottomView);
                            View followView = findViewById(R.id.followScrollView);
                            return i - (bottomView.getTop() - followView.getBottom());
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.recyclerViewLayout;
                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            return 0;
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.scrollViewLayout;
                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            return 0;
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.timeTv;
                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            return i - unfilledHeight;
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.recyclerView;
                        }
                    })
                    .logTrack(WKBinder.isDebug)
                    .build(false);
        }
        if (chatPanelManager == null) {
            FrameLayout moreView = findViewById(R.id.chatMoreLayout);
            chatPanelManager = new ChatPanelManager(mHelper, findViewById(R.id.bottomView), moreView, findViewById(R.id.followScrollView), this, () -> {
                CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_ab_back);
                numberTextView.setNumber(0, true);
                CommonAnim.getInstance().showOrHide(numberTextView, false, true);
                CommonAnim.getInstance().showOrHide(callIV, true, true);
                return null;
            }, path -> {
                pendingPreviewGeneration = channelGeneration;
                pendingPreviewChannelId = channelId;
                pendingPreviewChannelType = channelType;
                pendingPreviewReplyMsg = replyWKMsg;
                Intent intent = new Intent(ChatActivity.this, PreviewNewImgActivity.class);
                intent.putExtra("path", path);
                previewNewImgResultLac.launch(intent);
                return null;
            });
            initData();
        }
    }

    protected void initView() {
        applyChatSystemBarInsets();
        installFloatingComposerLayout();
        EndpointManager.getInstance().invoke("set_chat_bg", new SetChatBgMenu(channelId, channelType, wkVBinding.imageView, wkVBinding.rootView, wkVBinding.blurView));
        PartnerPendingStore.addListener(partnerPendingListener);
        PartnerLocalMessageStore.addListener(partnerLocalMessageListener);
        updatePartnerPendingUi();
        loadLocalChatBackground();
        Object pinnedLayoutView = EndpointManager.getInstance().invoke("get_pinned_message_view", this);
        if (pinnedLayoutView instanceof View) {
            wkVBinding.pinnedLayout.addView((View) pinnedLayoutView);
        }
        wkVBinding.timeTv.setShadowLayer(AndroidUtilities.dp(5f), 0f, 0f, 0);
        CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, true);
        Theme.setPressedBackground(wkVBinding.topLayout.backIv);
        wkVBinding.topLayout.backIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.titleBarIcon), PorterDuff.Mode.MULTIPLY));
        wkVBinding.topLayout.avatarView.setSize(40);
        wkVBinding.chatUnreadLayout.progress.setSize(40);
        wkVBinding.chatUnreadLayout.progress.setStrokeWidth(1.5f);
        wkVBinding.chatUnreadLayout.progress.setProgressColor(ContextCompat.getColor(this, R.color.popupTextColor));

        wkVBinding.chatUnreadLayout.msgCountTv.setColors(R.color.white, R.color.reminderColor);
        wkVBinding.chatUnreadLayout.remindCountTv.setColors(R.color.white, R.color.reminderColor);
        wkVBinding.chatUnreadLayout.approveCountTv.setColors(R.color.white, R.color.reminderColor);

        numberTextView = new NumberTextView(this);
        numberTextView.setTextSize(18);
        numberTextView.setTextColor(Theme.colorAccount);
        wkVBinding.topLayout.rightView.addView(numberTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.END, 0, 0, 102, 0));

        boolean isRegister = true;
        Object isRegisterRTC = EndpointManager.getInstance().invoke("is_register_rtc", null);
        if (isRegisterRTC instanceof Boolean) {
            isRegister = (boolean) isRegisterRTC;
        }

        callIV = new AppCompatImageView(this);
        callIV.setImageResource(R.mipmap.ic_call);
        if (isRegister) {
            wkVBinding.topLayout.rightView.addView(callIV, LayoutHelper.createFrame(40, 40, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 58, 0));
        }
        callIV.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        callIV.setPadding(AndroidUtilities.dp(7f), AndroidUtilities.dp(7f), AndroidUtilities.dp(7f), AndroidUtilities.dp(7f));
        callIV.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.popupTextColor), PorterDuff.Mode.MULTIPLY));
        callIV.setBackground(Theme.createSelectorDrawable(Theme.getPressedColor()));

        CommonAnim.getInstance().showOrHide(numberTextView, false, false);
        addChatMoreButton();
        initDeepSeekAssistantBar();

        ((DefaultItemAnimator) Objects.requireNonNull(wkVBinding.recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
        chatAdapter = new ChatAdapter(this, ChatAdapter.AdapterType.normalMessage);
        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        wkVBinding.recyclerView.setLayoutManager(linearLayoutManager);
        wkVBinding.recyclerView.setAdapter(chatAdapter);
        wkVBinding.recyclerView.setItemAnimator(new MyItemAnimator());
        chatAdapter.setAnimationFirstOnly(true);
        chatAdapter.setAnimationEnable(false);

    }

    private void initListener() {
        ItemTouchHelper helper = new ItemTouchHelper(new MessageSwipeController(this, new SwipeControllerActions() {
            @Override
            public void showReplyUI(int position) {
                showReply(chatAdapter.getData().get(position).wkMsg);
            }

            @Override
            public void hideSoft() {
            }
        }));
        helper.attachToRecyclerView(wkVBinding.recyclerView);
        wkVBinding.topLayout.backIv.setOnClickListener(v -> setBackListener());
        callIV.setOnClickListener(view -> {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
            if (getChatChannelInfo().forbidden == 1 || (member != null && member.forbiddenExpirationTime > 0)) {
                WKToastUtils.getInstance().showToast(getString(R.string.can_not_call_forbidden));
                return;
            }

            if (channelType == WKChannelType.PERSONAL) {
                final long requestGeneration = channelGeneration;
                final String targetChannelId = channelId;
                final byte targetChannelType = channelType;
                if (!validateP2pCallTarget(requestGeneration, targetChannelId, targetChannelType, true)) {
                    return;
                }
                // 顶部通话按钮只展示语音/视频选择面板，不在这里提前申请麦克风权限。
                // 真正点击“视频/语音”后会再次校验关系和禁言状态。
                showCallPopupMenuLower(view, requestGeneration, targetChannelId, targetChannelType);
                return;
            }

            WKChannelMember channelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
            if (channelMember != null && channelMember.status == WKChannelStatus.statusBlacklist) {
                showToast(R.string.call_blacklist_group);
                return;
            }
            Intent intent = new Intent(ChatActivity.this, ChooseVideoCallMembersActivity.class);
            intent.putExtra("channelID", channelId);
            intent.putExtra("channelType", channelType);
            intent.putExtra("isCreate", true);
            startActivity(intent);
        });

        WKDialogUtils.getInstance().setViewLongClickPopup(wkVBinding.chatUnreadLayout.groupApproveLayout, getGroupApprovePopupItems());
        wkVBinding.chatUnreadLayout.groupApproveLayout.setOnClickListener(view -> {
            if (WKReader.isNotEmpty(groupApproveList)) {
                WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(groupApproveList.get(0).messageID);
                if (msg != null && !TextUtils.isEmpty(msg.clientMsgNO)) {
                    tipsMsg(msg.clientMsgNO);
                }
            }
        });
        WKDialogUtils.getInstance().setViewLongClickPopup(wkVBinding.chatUnreadLayout.remindLayout, getRemindPopupItems());
        wkVBinding.chatUnreadLayout.remindLayout.setOnClickListener(view -> {

            if (WKReader.isNotEmpty(reminderList)) {
                reminderIds.add(reminderList.get(0).reminderID);
                WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(reminderList.get(0).messageID);
                if (msg != null && !TextUtils.isEmpty(msg.clientMsgNO)) {
                    tipsMsg(msg.clientMsgNO);
                } else {
                    long orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(reminderList.get(0).messageSeq, channelId, channelType);
                    unreadStartMsgOrderSeq = 0;
                    tipsOrderSeq = orderSeq;
                    getData(1, true, orderSeq, false);
                    isCanLoadMore = true;
                }
            }
        });

        SingleClickUtil.onSingleClick(wkVBinding.topLayout.titleView, view -> {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);

            if ((member != null && member.isDeleted == 1) || channelType == WKChannelType.CUSTOMER_SERVICE)
                return;
            Intent intent = new Intent(ChatActivity.this, channelType == WKChannelType.GROUP ? GroupDetailActivity.class : ChatPersonalActivity.class);
            intent.putExtra("channelId", channelId);
            startActivity(intent);
        });

        SingleClickUtil.onSingleClick(wkVBinding.topLayout.avatarView, view -> openTopPersonalProfile());
        SingleClickUtil.onSingleClick(wkVBinding.topLayout.otherLayout, view -> openTopPersonalProfile());

        wkVBinding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (chatAdapter.getData().size() <= 1) return;
                setShowTime();
                int lastItemPosition = linearLayoutManager.findLastVisibleItemPosition();
                boolean showNewMsgLayout = lastItemPosition < chatAdapter.getItemCount() - 1
                        ? (dy > 0 || redDot > 0)
                        : redDot > 0;
                postForCurrentSession(
                        wkVBinding.chatUnreadLayout.newMsgLayout,
                        () -> CommonAnim.getInstance().showOrHide(
                                wkVBinding.chatUnreadLayout.newMsgLayout,
                                showNewMsgLayout,
                                true,
                                false
                        )
                );
                resetRemindView();
                resetGroupApproveView();

                View lastChildView = linearLayoutManager.findViewByPosition(lastItemPosition);
                if (lastChildView != null) {
                    int bottom = lastChildView.getBottom();
                    int listHeight = wkVBinding.recyclerView.getHeight() - wkVBinding.recyclerView.getPaddingBottom();
                    unfilledHeight = listHeight - bottom;
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                int lastItemPosition = linearLayoutManager.findLastVisibleItemPosition();
                isShowHistory = lastItemPosition < chatAdapter.getItemCount() - 1;
                if (newState == SCROLL_STATE_IDLE) {
                    isTipMessage = false;
                    CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, true);
                    EndpointManager.getInstance().invoke("stop_reaction_animation", null);
                    if (!wkVBinding.recyclerView.canScrollVertically(1)) {
                        showMoreLoading();
                    } else if (!wkVBinding.recyclerView.canScrollVertically(-1)) {
                        showRefreshLoading();
                    }
                } else {
                    MsgModel.getInstance().doneReminder(new ArrayList<>(reminderIds));
                    if (!isUpdateRedDot) return;
                    final long requestGeneration = channelGeneration;
                    final String requestChannelId = channelId;
                    final byte requestChannelType = channelType;
                    final int requestUnreadCount = redDot;
                    MsgModel.getInstance().clearUnread(requestChannelId, requestChannelType, requestUnreadCount, (code, msg) -> {
                        if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)
                                && code == HttpResponseCode.success && requestUnreadCount == 0) {
                            isUpdateRedDot = false;
                        }
                    });
                }
            }
        });

        wkVBinding.chatUnreadLayout.newMsgLayout.setOnClickListener(v -> {
            redDot = 0;
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            MsgModel.getInstance().clearUnread(requestChannelId, requestChannelType, 0, (code, msg) -> {
                if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)
                        && code == HttpResponseCode.success) {
                    isUpdateRedDot = false;
                }
            });
            if (isCanLoadMore) {
                isSyncLastMsg = true;
                wkVBinding.chatUnreadLayout.progress.setVisibility(View.VISIBLE);
                wkVBinding.chatUnreadLayout.msgDownIv.setVisibility(View.GONE);
                unreadStartMsgOrderSeq = 0;
                lastPreviewMsgOrderSeq = 0;
                long maxSeq = WKIM.getInstance().getMsgManager().getMaxOrderSeqWithChannel(requestChannelId, requestChannelType);
                mainHandler.postDelayed(() -> {
                    if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) return;
                    getData(0, true, maxSeq, true);
                    showUnReadCountView();
                }, 500);
            } else {
                scrollToPosition(chatAdapter.getItemCount() - 1);
                showUnReadCountView();
            }

            isShowHistory = false;
            isCanLoadMore = false;
        });

        registerChannelListeners();

        EndpointManager.getInstance().setMethod("hide_pinned_view", object -> {
            if (!isShowPinnedView) return null;
            isShowPinnedView = false;
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) wkVBinding.timeTv.getLayoutParams();
            lp.topMargin = AndroidUtilities.dp(10) + getTopPinViewHeight();
            wkVBinding.timeTv.setVisibility(View.GONE);
            ObjectAnimator animator = ObjectAnimator.ofFloat(wkVBinding.pinnedLayout, "translationY", 0, -AndroidUtilities.dp(53));
            animator.setDuration(200);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    wkVBinding.pinnedLayout.clearAnimation();
                    wkVBinding.pinnedLayout.setVisibility(View.GONE);
                    if (WKReader.isNotEmpty(chatAdapter.getData()) && chatAdapter.getData().get(0).wkMsg != null && chatAdapter.getData().get(0).wkMsg.type == WKContentType.spanEmptyView) {
                        if (!isShowCallingView) {
                            chatAdapter.removeAt(0);
                            relinkAfterRemoval(0);
                        }
                    }
                }

                public void onAnimationStart(Animator animation) {
                    wkVBinding.pinnedLayout.setVisibility(View.VISIBLE);
                }
            });
            wkVBinding.pinnedLayout.setVisibility(View.VISIBLE);
            animator.start();
            return null;
        });
        EndpointManager.getInstance().setMethod("show_pinned_view", object -> {
            if (isShowPinnedView) {
                return null;
            }
            isShowPinnedView = true;

            if (WKReader.isNotEmpty(chatAdapter.getData()) && chatAdapter.getData().get(0).wkMsg != null && chatAdapter.getData().get(0).wkMsg.type != WKContentType.spanEmptyView) {
                WKMsg msg = getSpanEmptyMsg();
                chatAdapter.addData(0, new WKUIChatMsgItemEntity(this, msg, null));
            }
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) wkVBinding.timeTv.getLayoutParams();
            lp.topMargin = AndroidUtilities.dp(10) + getTopPinViewHeight();
            wkVBinding.timeTv.setVisibility(View.GONE);
            ObjectAnimator animator = ObjectAnimator.ofFloat(wkVBinding.pinnedLayout, "translationY", -wkVBinding.pinnedLayout.getHeight(), 0);
            animator.setDuration(200);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    wkVBinding.pinnedLayout.setVisibility(View.VISIBLE);
                }
            });
            animator.start();
            wkVBinding.pinnedLayout.setVisibility(View.VISIBLE);
            return null;
        });
        EndpointManager.getInstance().setMethod("tip_msg_in_chat", object -> {
            if (object instanceof String) {
                tipsMsg((String) object);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("reset_channel_all_pinned_msg", object -> {
            resetHideChannelAllPinnedMessage();
            for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                if (hideChannelAllPinnedMessage == 1) {
                    if (chatAdapter.getData().get(i).isPinned == 1) {
                        chatAdapter.getData().get(i).isPinned = 0;
                        chatAdapter.notifyStatus(i);
                    }
                } else {
                    if (chatAdapter.getData().get(i).isPinned == 0) {
                        if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.remoteExtra != null && chatAdapter.getData().get(i).wkMsg.remoteExtra.isPinned == 1) {
                            chatAdapter.getData().get(i).isPinned = 1;
                            chatAdapter.notifyStatus(i);
                        }
                    }
                }
            }
            return null;
        });
        synchronized (GLOBAL_ENDPOINT_LOCK) {
            globalEndpointRegistration = new GlobalEndpointRegistration();
            GLOBAL_ENDPOINT_STACK.add(globalEndpointRegistration);
            globalEndpointsRegistered = true;
        }
    }



    private String buildChannelListenerKey() {
        return "ChatActivity@" + Integer.toHexString(System.identityHashCode(this))
                + "#" + channelGeneration;
    }

    private void registerChannelListeners() {
        unregisterChannelListeners();
        final String listenerKey = buildChannelListenerKey();
        activeChannelListenerKey = listenerKey;
        final String listenerChannelId = channelId;
        final byte listenerChannelType = channelType;
        final long listenerGeneration = channelGeneration;
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo(listenerKey, (channel, isEnd) -> {
            Runnable callbackAction = () -> {
            if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
            if (channel == null) return;
            if (channel.channelID.equals(channelId) && channel.channelType == channelType) {
                showChannelName(channel);
                showTopAvatar(channel);
                if (isTopicRoomChannel(channel) && isTopicRoomExpired(channel)) {
                    finishCurrentTopicRoom(getString(R.string.topic_room_expired));
                    return;
                }
                if (channel.channelType == WKChannelType.PERSONAL) {
                    setOnlineView(channel);
                } else {
                    if (channel.remoteExtraMap != null) {
                        Object memberCountObject = channel.remoteExtraMap.get(WKChannelCustomerExtras.memberCount);
                        if (memberCountObject instanceof Integer) {
                            int count = (int) memberCountObject;
                            wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                        }
                        Object onlineCountObject = channel.remoteExtraMap.get(WKChannelCustomerExtras.onlineCount);
                        if (onlineCountObject instanceof Integer) {
                            int onlineCount = (int) onlineCountObject;
                            if (onlineCount > 0) {
                                wkVBinding.topLayout.subtitleCountTv.setVisibility(View.VISIBLE);
                                wkVBinding.topLayout.subtitleCountTv.setText(String.format(getString(R.string.online_count), onlineCount));
                            }
                        }
                    }
                }
                EndpointManager.getInstance().invoke("set_chat_bg", new SetChatBgMenu(channelId, channelType, wkVBinding.imageView, wkVBinding.rootView, wkVBinding.blurView));
                loadLocalChatBackground();
            } else {
                for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                    if (TextUtils.isEmpty(chatAdapter.getData().get(i).wkMsg.fromUID)) continue;
                    boolean isRefresh = false;
                    if (chatAdapter.getData().get(i).wkMsg.fromUID.equals(channel.channelID) && channel.channelType == WKChannelType.PERSONAL) {
                        chatAdapter.getData().get(i).wkMsg.setFrom(channel);
                        isRefresh = true;
                    }
                    if (chatAdapter.getData().get(i).wkMsg.getMemberOfFrom() != null && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID.equals(channel.channelID) && channel.channelType == WKChannelType.PERSONAL) {
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberRemark = channel.channelRemark;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberName = channel.channelName;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatar = channel.avatar;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatarCacheKey = channel.avatarCacheKey;
                        isRefresh = true;
                    }
                    if (chatAdapter.getData().get(i).wkMsg.baseContentMsgModel != null && WKReader.isNotEmpty(chatAdapter.getData().get(i).wkMsg.baseContentMsgModel.entities)) {
                        for (WKMsgEntity entity : chatAdapter.getData().get(i).wkMsg.baseContentMsgModel.entities) {
                            if (entity.type.equals(ChatContentSpanType.getMention()) && !TextUtils.isEmpty(entity.value) && entity.value.equals(channel.channelID)) {
                                isRefresh = true;
                                chatAdapter.getData().get(i).formatSpans(ChatActivity.this, chatAdapter.getData().get(i).wkMsg);
                                break;
                            }
                        }
                    }
                    if (isRefresh) {
                        chatAdapter.getData().get(i).isRefreshAvatarAndName = true;
                        chatAdapter.notifyItemChanged(i, chatAdapter.getData().get(i));
                    }
                }
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });

        WKIM.getInstance().getChannelMembersManager().addOnRefreshChannelMemberInfo(listenerKey, (channelMember, isEnd) -> {
            Runnable callbackAction = () -> {
            if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
            if (channelMember != null && !TextUtils.isEmpty(channelMember.channelID)) {
                if (channelMember.channelID.equals(channelId) && channelMember.channelType == channelType) {
                    if (channelMember.channelType == WKChannelType.PERSONAL) {
                        String name = channelMember.memberRemark;
                        if (TextUtils.isEmpty(name)) name = channelMember.memberName;
                        wkVBinding.topLayout.titleCenterTv.setText(name);
                    } else {
                        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                            if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom() != null && !TextUtils.isEmpty(chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID) && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID.equals(channelMember.memberUID)) {
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberName = channelMember.memberName;
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberRemark = channelMember.memberRemark;
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatar = channelMember.memberAvatar;
                                chatAdapter.getData().get(i).isRefreshAvatarAndName = true;
                                chatAdapter.notifyItemChanged(i, chatAdapter.getData().get(i));
                            }
                        }
                    }
                }
            }
            if (isEnd) {
                checkLoginUserInGroupStatus();
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });

        WKIM.getInstance().getChannelMembersManager().addOnRemoveChannelMemberListener(listenerKey, list -> {
            final List<WKChannelMember> callbackList = list == null ? new ArrayList<>() : new ArrayList<>(list);
            Runnable callbackAction = () -> {
            if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
            if (WKReader.isNotEmpty(callbackList) && !TextUtils.isEmpty(callbackList.get(0).channelID) && callbackList.get(0).channelID.equals(channelId) && callbackList.get(0).channelType == channelType) {
                if (groupType == WKGroupType.normalGroup) {
                    count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                    wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                }
                checkLoginUserInGroupStatus();
                WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });
        WKIM.getInstance().getChannelMembersManager().addOnAddChannelMemberListener(listenerKey, list -> {
            final List<WKChannelMember> callbackList = list == null ? new ArrayList<>() : new ArrayList<>(list);
            Runnable callbackAction = () -> {
            if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
            if (WKReader.isNotEmpty(callbackList) && !TextUtils.isEmpty(callbackList.get(0).channelID) && callbackList.get(0).channelID.equals(channelId) && callbackList.get(0).channelType == channelType && groupType == WKGroupType.normalGroup) {
                count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
                checkLoginUserInGroupStatus();
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });
        WKIM.getInstance().getMsgManager().addOnDeleteMsgListener(listenerKey, msg -> {
            Runnable callbackAction = () -> {
            if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
            if (msg != null) {
                removeMsg(msg);
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });
        WKIM.getInstance().getCMDManager().addCmdListener(listenerKey, wkCmd -> {
            Runnable callbackAction = () -> {
            if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
            if (wkCmd == null || TextUtils.isEmpty(wkCmd.cmdKey) || wkCmd.paramJsonObject == null) return;
            switch (wkCmd.cmdKey) {
                case WKCMDKeys.wk_typing -> typing(wkCmd);
                case WKCMDKeys.wk_unreadClear -> {
                    if (wkCmd.paramJsonObject.has("channel_id") && wkCmd.paramJsonObject.has("channel_type")) {
                        String channelId = wkCmd.paramJsonObject.optString("channel_id");
                        int channelType = wkCmd.paramJsonObject.optInt("channel_type");
                        int unreadCount = wkCmd.paramJsonObject.optInt("unread");
                        if (channelId.equals(this.channelId) && channelType == this.channelType) {
                            if (unreadCount < redDot) {
                                this.redDot = unreadCount;
                                wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> {
                                    if (isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) {
                                        CommonAnim.getInstance().showOrHide(
                                                wkVBinding.chatUnreadLayout.newMsgLayout,
                                                redDot > 0,
                                                true,
                                                false
                                        );
                                    }
                                });
                            }
                        }
                    }
                }
                case "topicRoomDeleted" -> handleTopicRoomDeletedCmd(wkCmd.paramJsonObject);
                case "sync_channel_state" -> {
                    String sourceChannelId = wkCmd.paramJsonObject.optString("channel_id");
                    int sourceChannelType = wkCmd.paramJsonObject.optInt("channel_type");
                    if (sourceChannelId.equals(channelId) && sourceChannelType == channelType) {
                        getChannelState();
                    }
                }
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });

        WKIM.getInstance().getMsgManager().addOnRefreshMsgListener(listenerKey, (wkMsg, left) -> {
            Runnable callbackAction = () -> {
            if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
            if (shouldHideFromChatList(wkMsg)) {
                removeMsg(wkMsg);
                return;
            }
            if (wkMsg.remoteExtra != null && wkMsg.remoteExtra.isMutualDeleted == 1) {
                removeMsg(wkMsg);
                return;
            }
            refreshMsg(wkMsg);
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });
        WKIM.getInstance().getMsgManager().addOnSendMsgCallback(listenerKey, msg -> {
            Runnable callbackAction = () -> {
            if (isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) {
                sendMsgInserted(msg);
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });

        WKIM.getInstance().getMsgManager().addOnNewMsgListener(listenerKey, list -> {
            final List<WKMsg> callbackList = list == null ? new ArrayList<>() : new ArrayList<>(list);
            Runnable callbackAction = () -> {
            if (isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) {
                receivedMessages(callbackList);
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });
        WKIM.getInstance().getMsgManager().addOnClearMsgListener(listenerKey,
                (channelID, clearChannelType, fromUID) -> handleClearMessageCallback(
                        listenerGeneration, listenerChannelId, listenerChannelType,
                        channelID, clearChannelType, fromUID));

        WKIM.getInstance().getReminderManager().addOnNewReminderListener(listenerKey, reminder -> {
            Runnable callbackAction = () -> {
            if (isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) {
                resetReminder(reminder);
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });
        EndpointManager.getInstance().setMethod(listenerKey, EndpointCategory.wkExitChat, object -> {
            Runnable callbackAction = () -> {
                if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
                if (object instanceof WKChannel) {
                    WKChannel exitChannel = (WKChannel) object;
                    if (TextUtils.equals(channelId, exitChannel.channelID)
                            && exitChannel.channelType == channelType) {
                        finish();
                    }
                }
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
            return null;
        });
        WKIM.getInstance().getConnectionManager().addOnConnectionStatusListener(listenerKey, (i, s) -> {
            Runnable callbackAction = () -> {
            if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
            if (i == WKConnectStatus.syncCompleted && WKUIKitApplication.getInstance().isRefreshChatActivityMessage) {
                WKUIKitApplication.getInstance().isRefreshChatActivityMessage = false;
                int maxOrderSeq = WKIM.getInstance().getMsgManager().getMaxOrderSeqWithChannel(channelId, channelType);
                long tempMaxOrderSeq = 0;
                if (chatAdapter != null && chatAdapter.getLastMsg() != null) {
                    tempMaxOrderSeq = chatAdapter.getLastMsg().orderSeq;
                }
                if (maxOrderSeq > tempMaxOrderSeq) {
                    getData(0, true, maxOrderSeq, true);
                }
            }
        
            };
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
        });
        EndpointManager.getInstance().setMethod(listenerKey, EndpointCategory.refreshProhibitWord, object -> {
            Runnable callbackAction = () -> refreshProhibitWords(
                    listenerGeneration, listenerChannelId, listenerChannelType);
            if (isMainThread()) {
                callbackAction.run();
            } else {
                postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType, callbackAction);
            }
            return 1;
        });
    }

    private void refreshProhibitWords(long listenerGeneration, String listenerChannelId,
                                       byte listenerChannelType) {
        if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)
                || chatAdapter == null || WKReader.isEmpty(chatAdapter.getData())) {
            return;
        }
        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
            WKUIChatMsgItemEntity item = chatAdapter.getData().get(i);
            if (item != null && item.wkMsg != null && item.wkMsg.type == WKContentType.WK_TEXT) {
                WKIMUtils.getInstance().resetMsgProhibitWord(item.wkMsg);
                item.formatSpans(ChatActivity.this, item.wkMsg);
                chatAdapter.notifyItemChanged(i);
            }
        }
    }

    private void handleClearMessageCallback(long listenerGeneration, String listenerChannelId,
                                            byte listenerChannelType, String clearChannelId,
                                            byte clearChannelType, String fromUID) {
        if (!isMainThread()) {
            postToMainForSession(listenerGeneration, listenerChannelId, listenerChannelType,
                    () -> handleClearMessageCallback(listenerGeneration, listenerChannelId,
                            listenerChannelType, clearChannelId, clearChannelType, fromUID));
            return;
        }
        if (!isCurrentSession(listenerGeneration, listenerChannelId, listenerChannelType)) return;
        if (TextUtils.isEmpty(clearChannelId)
                || !TextUtils.equals(channelId, clearChannelId)
                || channelType != clearChannelType
                || chatAdapter == null) {
            return;
        }
        if (TextUtils.isEmpty(fromUID)) {
            clearChatAdapterAfterHistoryCleared();
            return;
        }

        boolean removedAny = false;
        for (int i = 0; i < chatAdapter.getData().size(); i++) {
            WKUIChatMsgItemEntity item = chatAdapter.getData().get(i);
            if (item != null && item.wkMsg != null
                    && TextUtils.equals(item.wkMsg.fromUID, fromUID)) {
                chatAdapter.removeAt(i);
                relinkAfterRemoval(i);
                removedAny = true;
                i--;
            }
        }
        if (removedAny) {
            removeOrphanLocalDividers();
            refreshLatestInlineTranslateButton();
        }
    }

    private void clearChatAdapterAfterHistoryCleared() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            mainHandler.post(() -> {
                if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) {
                    clearChatAdapterAfterHistoryCleared();
                }
            });
            return;
        }
        if (chatAdapter == null) return;
        cancelTypingExpiry(null);
        chatAdapter.getData().clear();
        chatAdapter.notifyDataSetChanged();
        redDot = 0;
        lastVisibleMsgSeq = 0;
        maxMsgSeq = 0;
        maxMsgOrderSeq = 0;
        unreadStartMsgOrderSeq = 0;
        lastPreviewMsgOrderSeq = 0;
        tipsOrderSeq = 0;
        isCanLoadMore = false;
        // 用户主动清空后不应再通过顶部加载把刚清掉的旧记录重新拉回。
        isCanRefresh = false;
        isShowHistory = false;
        isSyncLastMsg = false;
        isRefreshLoading = false;
        isMoreLoading = false;
        isUpdateRedDot = false;
        isTipMessage = false;
        browseTo = 0;
        unfilledHeight = 0;
        clearReadReceiptIds();
        reminderList.clear();
        groupApproveList.clear();
        reminderIds.clear();
        lastReminderCount = -1;
        lastGroupApproveCount = -1;
        showUnReadCountView();
        resetRemindView();
        resetGroupApproveView();
        wkVBinding.chatUnreadLayout.progress.setVisibility(View.GONE);
        wkVBinding.chatUnreadLayout.msgDownIv.setVisibility(View.VISIBLE);
        lastFloatingTime = "";
        lastFloatingTimeVisible = false;
        CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, false);
    }

    private void unregisterGlobalEndpoints() {
        synchronized (GLOBAL_ENDPOINT_LOCK) {
            if (!globalEndpointsRegistered) return;
            globalEndpointsRegistered = false;
            if (globalEndpointRegistration != null) {
                globalEndpointRegistration.active = false;
                globalEndpointRegistration = null;
            }

            // EndpointManager.remove(sid) 只移除最后注册的同名处理器。
            // 因此只能从注册栈顶连续弹出已经销毁的 Activity，避免底层页面销毁时误删顶部页面的处理器。
            while (!GLOBAL_ENDPOINT_STACK.isEmpty()) {
                int lastIndex = GLOBAL_ENDPOINT_STACK.size() - 1;
                GlobalEndpointRegistration registration = GLOBAL_ENDPOINT_STACK.get(lastIndex);
                if (registration.active) break;
                EndpointManager.getInstance().remove("hide_pinned_view");
                EndpointManager.getInstance().remove("show_pinned_view");
                EndpointManager.getInstance().remove("tip_msg_in_chat");
                EndpointManager.getInstance().remove("reset_channel_all_pinned_msg");
                GLOBAL_ENDPOINT_STACK.remove(lastIndex);
            }
        }
    }

    private void unregisterChannelListeners() {
        final String listenerKey = activeChannelListenerKey;
        activeChannelListenerKey = "";
        if (TextUtils.isEmpty(listenerKey)) return;
        EndpointManager.getInstance().remove(listenerKey);
        WKIM.getInstance().getMsgManager().removeDeleteMsgListener(listenerKey);
        WKIM.getInstance().getMsgManager().removeNewMsgListener(listenerKey);
        WKIM.getInstance().getMsgManager().removeRefreshMsgListener(listenerKey);
        WKIM.getInstance().getMsgManager().removeSendMsgCallBack(listenerKey);
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo(listenerKey);
        WKIM.getInstance().getChannelMembersManager().removeRefreshChannelMemberInfo(listenerKey);
        WKIM.getInstance().getChannelMembersManager().removeAddChannelMemberListener(listenerKey);
        WKIM.getInstance().getChannelMembersManager().removeRemoveChannelMemberListener(listenerKey);
        WKIM.getInstance().getCMDManager().removeCmdListener(listenerKey);
        WKIM.getInstance().getMsgManager().removeSendMsgAckListener(listenerKey);
        WKIM.getInstance().getMsgManager().removeClearMsg(listenerKey);
        WKIM.getInstance().getRobotManager().removeRefreshRobotMenu(listenerKey);
        WKIM.getInstance().getReminderManager().removeNewReminderListener(listenerKey);
        removeConnectionStatusListenerSafely(listenerKey);
    }

    private void removeConnectionStatusListenerSafely(String listenerKey) {
        try {
            WKIM.getInstance().getConnectionManager().removeOnConnectionStatusListener(listenerKey);
        } catch (Exception e) {
            Log.w("ChatActivity", "remove connection listener failed", e);
        }
    }

    private boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /**
     * Adapter notifications are illegal while RecyclerView is computing a layout. Defer only for
     * that short critical section; a fling itself is not a reason to keep typing rows alive or to
     * wake the main thread every 16 ms until scrolling stops.
     */
    private boolean deferAdapterMutationIfComputing(@NonNull Runnable action) {
        RecyclerView recyclerView = wkVBinding == null ? null : wkVBinding.recyclerView;
        if (recyclerView == null || !recyclerView.isComputingLayout()) return false;
        final long requestGeneration = channelGeneration;
        final String requestChannelId = channelId;
        final byte requestChannelType = channelType;
        mainHandler.post(() -> {
            if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) {
                action.run();
            }
        });
        return true;
    }

    private void postToMainForSession(long generation, String targetChannelId,
                                      byte targetChannelType, Runnable action) {
        if (action == null || !isCurrentSession(generation, targetChannelId, targetChannelType)) return;
        mainHandler.post(() -> {
            if (isCurrentSession(generation, targetChannelId, targetChannelType)) {
                action.run();
            }
        });
    }

    private boolean isCurrentSession(long generation, String targetChannelId, byte targetChannelType) {
        return generation == channelGeneration
                && TextUtils.equals(channelId, targetChannelId)
                && channelType == targetChannelType
                && !isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed());
    }

    private void postForCurrentSession(View view, Runnable action) {
        if (view == null || action == null) return;
        final long requestGeneration = channelGeneration;
        final String requestChannelId = channelId;
        final byte requestChannelType = channelType;
        view.post(() -> {
            if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) {
                action.run();
            }
        });
    }

    private boolean hasPendingReadReceipts() {
        synchronized (readMsgIds) {
            return !readMsgIds.isEmpty();
        }
    }

    private void addReadReceiptId(String messageId) {
        if (TextUtils.isEmpty(messageId)) return;
        synchronized (readMsgIds) {
            readMsgIds.add(messageId);
        }
    }

    private void clearReadReceiptIds() {
        synchronized (readMsgIds) {
            readMsgIds.clear();
        }
    }

    private void flushReadReceipts(String targetChannelId, byte targetChannelType) {
        if (TextUtils.isEmpty(targetChannelId)) return;
        final List<String> ids;
        synchronized (readMsgIds) {
            if (readMsgIds.isEmpty()) return;
            ids = new ArrayList<>(readMsgIds);
            readMsgIds.clear();
        }
        EndpointManager.getInstance().invoke("read_msg", new ReadMsgMenu(targetChannelId, targetChannelType, ids));
    }

    private void resetChannelSessionState() {
        reminderList.clear();
        groupApproveList.clear();
        reminderIds.clear();
        clearReadReceiptIds();
        replyWKMsg = null;
        editMsg = null;
        isViewingPicture = false;
        isShowHistory = false;
        isSyncLastMsg = false;
        isToEnd = true;
        lastPreviewMsgOrderSeq = 0;
        unreadStartMsgOrderSeq = 0;
        tipsOrderSeq = 0;
        keepOffsetY = 0;
        redDot = 0;
        lastVisibleMsgSeq = 0;
        count = 0;
        groupType = WKGroupType.normalGroup;
        browseTo = 0;
        isUpdateRedDot = true;
        isUploadReadMsg = true;
        isCanLoadMore = false;
        isCanRefresh = true;
        isRefreshLoading = false;
        isMoreLoading = false;
        unfilledHeight = 0;
        isShowPinnedView = false;
        isShowCallingView = false;
        isTipMessage = false;
        lastReminderCount = -1;
        lastGroupApproveCount = -1;
        lastFloatingTime = "";
        lastFloatingTimeVisible = false;
        resetChatPanelSessionUi();
        if (wkVBinding != null) {
            wkVBinding.topLayout.categoryLayout.removeAllViews();
            wkVBinding.topLayout.subtitleView.setVisibility(View.GONE);
            wkVBinding.topLayout.subtitleCountTv.setVisibility(View.GONE);
            wkVBinding.callLayout.removeAllViews();
            wkVBinding.callLayout.setVisibility(View.GONE);
            wkVBinding.pinnedLayout.setVisibility(View.GONE);
            wkVBinding.timeTv.setVisibility(View.GONE);
            wkVBinding.chatUnreadLayout.msgCountTv.setCount(0, false);
            wkVBinding.chatUnreadLayout.msgCountTv.setVisibility(View.GONE);
            wkVBinding.chatUnreadLayout.newMsgLayout.clearAnimation();
            wkVBinding.chatUnreadLayout.newMsgLayout.setVisibility(View.GONE);
            wkVBinding.chatUnreadLayout.remindCountTv.setCount(0, false);
            wkVBinding.chatUnreadLayout.remindCountTv.setVisibility(View.GONE);
            wkVBinding.chatUnreadLayout.remindLayout.clearAnimation();
            wkVBinding.chatUnreadLayout.remindLayout.setVisibility(View.GONE);
            wkVBinding.chatUnreadLayout.approveCountTv.setCount(0, false);
            wkVBinding.chatUnreadLayout.approveCountTv.setVisibility(View.GONE);
            wkVBinding.chatUnreadLayout.groupApproveLayout.clearAnimation();
            wkVBinding.chatUnreadLayout.groupApproveLayout.setVisibility(View.GONE);
            wkVBinding.chatUnreadLayout.progress.setVisibility(View.GONE);
            wkVBinding.chatUnreadLayout.msgDownIv.setVisibility(View.VISIBLE);
            if (callIV != null) {
                CommonAnim.getInstance().showOrHide(callIV, true, false);
            }
        }
    }


    private void resetChatPanelSessionUi() {
        dismissCallPopup();
        if (chatPanelManager != null) {
            // 依次关闭图片预览、回复/编辑栏和键盘面板。直接隐藏外层 View 会留下
            // ChatPanelManager 内部的 110dp 回复模式高度，切换会话后会挤压消息列表。
            int closeGuard = 0;
            while (closeGuard < 4 && !chatPanelManager.isCanBack()) {
                closeGuard++;
            }
            if (numberTextView != null && numberTextView.getVisibility() == View.VISIBLE) {
                chatPanelManager.hideMultipleChoice();
                numberTextView.setNumber(0, false);
                CommonAnim.getInstance().showOrHide(numberTextView, false, false);
            }
            chatPanelManager.resetToolBar();
            if (chatPanelManager.getEditText() != null) {
                chatPanelManager.getEditText().setText("");
            }
        }
        if (mHelper != null) {
            mHelper.resetState();
        }

        // chatTopLayout 还可能包含阅后即焚或 AI 建议等会话级临时卡片。
        View chatTopLayout = findViewById(R.id.chatTopLayout);
        if (chatTopLayout instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) chatTopLayout;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                child.clearAnimation();
                child.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        final String oldChannelId = channelId;
        final byte oldChannelType = channelType;

        // 先保存旧会话，再立刻让旧代次的异步回调失效，然后释放监听器。
        markTopicRoomRead(oldChannelId, oldChannelType);
        saveEditContent();
        flushReadReceipts(oldChannelId, oldChannelType);
        channelGeneration++;
        unregisterChannelListeners();
        cancelTypingExpiry(null);
        mainHandler.removeCallbacksAndMessages(null);

        resetChannelSessionState();
        setIntent(intent);
        if (!initParam()) {
            finish();
            return;
        }
        topicRoomClosing = false;
        lastTopicRoomReadKey = "";
        lastTopicRoomReadAt = 0L;
        if (chatPanelManager != null) {
            chatPanelManager.onConversationChanged(
                    oldChannelId, oldChannelType, channelId, channelType, channelGeneration);
        }
        registerChannelListeners();
        EndpointManager.getInstance().invoke(
                "set_chat_bg",
                new SetChatBgMenu(channelId, channelType, wkVBinding.imageView, wkVBinding.rootView, wkVBinding.blurView)
        );
        loadLocalChatBackground();
        refreshDeepSeekAssistantBar();
        updatePartnerPendingUi();
        WKUIKitApplication.getInstance().chattingChannelID = channelId;
        if (chatPanelManager != null) {
            initData();
        }
    }


    private void openTopPersonalProfile() {
        if (channelType != WKChannelType.PERSONAL) return;
        if (TextUtils.isEmpty(channelId)) return;
        if (channelType == WKChannelType.CUSTOMER_SERVICE) return;
        WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
        if (member != null && member.isDeleted == 1) return;
        ProfileNavigator.open(ChatActivity.this, channelId);
    }

    private void initData() {
        final long initGeneration = channelGeneration;
        final String initChannelId = channelId;
        final byte initChannelType = channelType;
        reminderList.clear();
        groupApproveList.clear();
        reminderIds.clear();
        startTimer();
        EndpointManager.getInstance().invoke(EndpointSID.openChatPage, getChatChannelInfo());
        WKIM.getInstance().getChannelManager().fetchChannelInfo(channelId, channelType);
        MsgModel.getInstance().syncExtraMsg(channelId, channelType);
        WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
        getChannelState();

        cancelTypingExpiry(null);
        chatAdapter.setList(new ArrayList<>());
        if (WKSystemAccount.isSystemAccount(channelId) || channelType == WKChannelType.CUSTOMER_SERVICE) {
            CommonAnim.getInstance().showOrHide(callIV, false, false);
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);

        String avatarKey = "";
        if (channel != null) {
            wkVBinding.topLayout.categoryLayout.removeAllViews();
            avatarKey = channel.avatarCacheKey;
            if (channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.groupType)) {
                Object object = channel.remoteExtraMap.get(WKChannelExtras.groupType);
                if (object instanceof Integer) {
                    groupType = (int) object;
                }
            }
            if (!TextUtils.isEmpty(channel.category)) {
                if (channel.category.equals(WKSystemAccount.accountCategorySystem)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.official), ContextCompat.getColor(this, R.color.transparent), ContextCompat.getColor(this, R.color.reminderColor), ContextCompat.getColor(this, R.color.reminderColor)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.accountCategoryCustomerService)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.customer_service), Theme.colorAccount, ContextCompat.getColor(this, R.color.white), Theme.colorAccount), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.accountCategoryVisitor)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.visitor), ContextCompat.getColor(this, R.color.transparent), ContextCompat.getColor(this, R.color.colorFFC107), ContextCompat.getColor(this, R.color.colorFFC107)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.channelCategoryOrganization)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.all_staff), ContextCompat.getColor(this, R.color.category_org_bg), ContextCompat.getColor(this, R.color.category_org_text), ContextCompat.getColor(this, R.color.transparent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.channelCategoryDepartment)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.department), ContextCompat.getColor(this, R.color.category_org_bg), ContextCompat.getColor(this, R.color.category_org_text), ContextCompat.getColor(this, R.color.transparent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
            }
            showChannelName(channel);
            if (channel.robot == 1) {
                wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.bot), ContextCompat.getColor(this, R.color.colorFFC107), ContextCompat.getColor(this, R.color.white), ContextCompat.getColor(this, R.color.colorFFC107)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 1, 0));
            }
            showTopAvatar(channel);
        } else {
            wkVBinding.topLayout.avatarView.showAvatar(channelId, channelType, avatarKey);
        }

        if (channelType == WKChannelType.GROUP) {
            if (groupType == WKGroupType.normalGroup) {
                GroupModel.getInstance().groupMembersSync(initChannelId, (code, msg) -> {
                    if (!isCurrentSession(initGeneration, initChannelId, initChannelType)) return;
                    if (code == HttpResponseCode.success) {
                        WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
                        hideOrShowRightView(member == null || member.isDeleted != 1);
                        WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
                        chatPanelManager.showOrHideForbiddenView();
                    }
                });
            } else {
                UserModel.getInstance().getUserInfo(WKConfig.getInstance().getUid(), channelId, null);
            }
            if (channel != null) {
                count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                showChannelName(channel);
                if (channel.forbidden == 1) {
                    chatPanelManager.showOrHideForbiddenView();
                }
                if (channel.status == WKChannelStatus.statusDisabled) {
                    chatPanelManager.showBan();
                } else {
                    chatPanelManager.hideBan();
                }
            }

            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
            hideOrShowRightView(member == null || member.isDeleted == 0);
            if (groupType == WKGroupType.normalGroup) {
                wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
            }
            wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
            chatPanelManager.showOrHideForbiddenView();
        } else {
            hideOrShowRightView(true);
            wkVBinding.topLayout.subtitleCountTv.setVisibility(View.GONE);
            if (channel != null) {
                setOnlineView(channel);
                showChannelName(channel);
            }
        }


        if (getIntent().hasExtra("lastPreviewMsgOrderSeq")) {
            lastPreviewMsgOrderSeq = getIntent().getLongExtra("lastPreviewMsgOrderSeq", 0L);
            isCanLoadMore = lastPreviewMsgOrderSeq > 0;
        }
        if (getIntent().hasExtra("keepOffsetY")) {
            keepOffsetY = getIntent().getIntExtra("keepOffsetY", 0);
        }
        if (getIntent().hasExtra("redDot")) redDot = getIntent().getIntExtra("redDot", 0);
        if (getIntent().hasExtra("tipsOrderSeq")) {
            tipsOrderSeq = getIntent().getLongExtra("tipsOrderSeq", 0);
        }
        if (getIntent().hasExtra("unreadStartMsgOrderSeq")) {
            unreadStartMsgOrderSeq = getIntent().getLongExtra("unreadStartMsgOrderSeq", 0);
        }

        List<WKReminder> allReminder = WKIM.getInstance().getReminderManager().getReminders(channelId, channelType);
        if (WKReader.isNotEmpty(allReminder)) {
            for (WKReminder reminder : allReminder) {
                boolean isPublisher = !TextUtils.isEmpty(reminder.publisher) && reminder.publisher.equals(loginUID);
                if (reminder.type == WKMentionType.WKReminderTypeMentionMe && reminder.done == 0 && !isPublisher) {
                    reminderList.add(reminder);
                }
                if (reminder.type == WKMentionType.WKApplyJoinGroupApprove && reminder.done == 0) {
                    groupApproveList.add(reminder);
                }
            }
        }
        boolean isScrollToEnd = unreadStartMsgOrderSeq == 0 && lastPreviewMsgOrderSeq == 0;
        long aroundMsgSeq = 0;
        if (unreadStartMsgOrderSeq != 0) {
            aroundMsgSeq = unreadStartMsgOrderSeq;
            isCanLoadMore = true;
        }
        isUpdateRedDot = unreadStartMsgOrderSeq > 0;
        if (lastPreviewMsgOrderSeq != 0) aroundMsgSeq = lastPreviewMsgOrderSeq;
        if (tipsOrderSeq != 0) {
            aroundMsgSeq = tipsOrderSeq;
            isCanLoadMore = true;
        }
        if (aroundMsgSeq == 0 && getIntent().hasExtra("aroundMsgSeq")) {
            aroundMsgSeq = getIntent().getLongExtra("aroundMsgSeq", 0);
        }
        getData(lastPreviewMsgOrderSeq == 0 ? 0 : 1, unreadStartMsgOrderSeq > 0, aroundMsgSeq, isScrollToEnd);

        WKConversationMsgExtra extra = WKIM.getInstance().getConversationManager().getMsgExtraWithChannel(channelId, channelType);
        if (extra != null) {
            if (!TextUtils.isEmpty(extra.draft)) {
                chatPanelManager.setEditContent(extra.draft);
            }
            browseTo = extra.browseTo;
        }
        final long requestGeneration = channelGeneration;
        final String requestChannelId = channelId;
        final byte requestChannelType = channelType;
        mainHandler.postDelayed(() -> {
            if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) return;
            resetRemindView();
            resetGroupApproveView();
        }, 150);

    }

    private void getChannelState() {
        final long requestGeneration = channelGeneration;
        final String requestChannelId = channelId;
        final byte requestChannelType = channelType;
        WKCommonModel.getInstance().getChannelState(requestChannelId, requestChannelType, channelState -> {
            if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) return;
            if (channelState != null) {
                if (channelType == WKChannelType.GROUP && channelState.online_count > 0) {
                    wkVBinding.topLayout.subtitleCountTv.setVisibility(View.VISIBLE);
                    wkVBinding.topLayout.subtitleCountTv.setText(String.format(getString(R.string.online_count), channelState.online_count));
                }
                if (channelType == WKChannelType.PERSONAL) {
                    return;
                }
                if (channelState.call_info == null || WKReader.isEmpty(channelState.call_info.getCalling_participants())) {
                    wkVBinding.callLayout.setVisibility(View.GONE);
                    isShowCallingView = false;
                    // 注意：补判空，避免 wkMsg 为 null 时 NPE
                    if (WKReader.isNotEmpty(chatAdapter.getData())
                            && chatAdapter.getData().get(0).wkMsg != null
                            && chatAdapter.getData().get(0).wkMsg.type == WKContentType.spanEmptyView) {
                        if (!isShowPinnedView) {
                            chatAdapter.removeAt(0);
                            relinkAfterRemoval(0);
                        } else {
                            chatAdapter.getData().get(0).wkMsg.messageSeq = getTopPinViewHeight();
                            chatAdapter.notifyItemChanged(0);
                        }
                    }
                } else {
                    Object object = EndpointManager.getInstance().invoke("show_calling_participants", new CallingViewMenu(this, channelState.call_info));
                    if (object != null) {
                        View view = (View) object;
                        wkVBinding.callLayout.removeAllViews();
                        wkVBinding.callLayout.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        wkVBinding.callLayout.setVisibility(View.VISIBLE);
                        isShowCallingView = true;
                        if (isAddedSpanEmptyView()) {
                            chatAdapter.getData().get(0).wkMsg.messageSeq = getTopPinViewHeight();
                            chatAdapter.notifyItemChanged(0);
                        } else {
                            WKMsg msg = getSpanEmptyMsg();
                            chatAdapter.addData(0, new WKUIChatMsgItemEntity(this, msg, null));
                        }
                    } else {
                        isShowCallingView = false;
                    }
                }
            }

            if (WKReader.isEmpty(MsgModel.getInstance().channelStatus)) {
                MsgModel.getInstance().channelStatus = new ArrayList<>();
            }
            boolean isAdd = true;
            for (int i = 0; i < MsgModel.getInstance().channelStatus.size(); i++) {
                WKChannelState savedState = MsgModel.getInstance().channelStatus.get(i);
                if (savedState.channel_id.equals(channelId) && savedState.channel_type == channelType) {
                    savedState.calling = isShowCallingView ? 1 : 0;
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                WKChannelState state = new WKChannelState();
                state.channel_id = channelId;
                state.channel_type = channelType;
                state.calling = isShowCallingView ? 1 : 0;
                MsgModel.getInstance().channelStatus.add(state);
            }
            EndpointManager.getInstance().invoke("refresh_conversation_calling", null);
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) wkVBinding.timeTv.getLayoutParams();
            lp.topMargin = AndroidUtilities.dp(10) + getTopPinViewHeight();
            wkVBinding.timeTv.setVisibility(View.GONE);
        });
    }

    // 获取聊天记录
    private void getData(int pullMode, boolean isSetNewData, long aroundMsgOrderSeq, boolean isScrollToEnd) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> getData(pullMode, isSetNewData, aroundMsgOrderSeq, isScrollToEnd));
            return;
        }
        final long requestGeneration = channelGeneration;
        final String requestChannelId = channelId;
        final byte requestChannelType = channelType;
        boolean contain = false;
        long oldestOrderSeq;
        if (pullMode == 1) {
            oldestOrderSeq = chatAdapter.getEndMsgOrderSeq();
        } else {
            oldestOrderSeq = chatAdapter.getFirstMsgOrderSeq();
        }
        if (isSyncLastMsg) {
            oldestOrderSeq = 0;
        }
        if (lastPreviewMsgOrderSeq != 0) {
            contain = true;
            oldestOrderSeq = lastPreviewMsgOrderSeq;
        }
        if (unreadStartMsgOrderSeq != 0) contain = true;
        WKIM.getInstance().getMsgManager().getOrSyncHistoryMessages(requestChannelId, requestChannelType, oldestOrderSeq, contain, pullMode, limit, aroundMsgOrderSeq, new IGetOrSyncHistoryMsgBack() {
            @Override
            public void onSyncing() {
                if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) return;
                if (!isMainThread()) {
                    postToMainForSession(requestGeneration, requestChannelId, requestChannelType, this::onSyncing);
                    return;
                }

                if (isShowPinnedView && !isRefreshLoading && !isMoreLoading && !isSyncLastMsg) {
                    EndpointManager.getInstance().invoke("is_syncing_message", 1);
                } else {
                    if (WKReader.isEmpty(chatAdapter.getData())) {
                        WKMsg wkMsg = new WKMsg();
                        wkMsg.type = WKContentType.loading;
                        chatAdapter.addData(new WKUIChatMsgItemEntity(ChatActivity.this, wkMsg, null));
                    }
                }
            }

            @Override
            public void onResult(List<WKMsg> list) {
                if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) return;
                if (!isMainThread()) {
                    final List<WKMsg> snapshot = list == null ? new ArrayList<>() : new ArrayList<>(list);
                    postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                            () -> onResult(snapshot));
                    return;
                }
                if (list == null) list = new ArrayList<>();
                if (isShowPinnedView) {
                    EndpointManager.getInstance().invoke("is_syncing_message", 0);
                }
                if (pullMode == 0) {
                    if (WKReader.isEmpty(list))
                        isCanRefresh = false;
                } else {
                    if (WKReader.isEmpty(list)) {
                        isCanLoadMore = false;
                    }
                }
                isSyncLastMsg = false;
                List<WKMsg> tempList = new ArrayList<>();
                for (WKMsg msg : list) {
                    // RTC 由 wkrtc 的全局 new/refresh 监听器统一处理；聊天页只负责隐藏。
                    // 向上翻旧记录时绝不能再次派发历史信令，否则旧通话状态会被重复处理。
                    if (shouldHideFromChatList(msg)) {
                        continue;
                    }
                    if (isSetNewData || !chatAdapter.isExist(msg.clientMsgNO, msg.messageID)) {
                        tempList.add(msg);
                    }
                }
                showData(tempList, pullMode, isSetNewData, isScrollToEnd);
                wkVBinding.chatUnreadLayout.progress.setVisibility(View.GONE);
                wkVBinding.chatUnreadLayout.msgDownIv.setVisibility(View.VISIBLE);

                if (WKReader.isNotEmpty(chatAdapter.getData())) {
                    for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                        if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.type == WKContentType.loading) {
                            chatAdapter.removeAt(i);
                            relinkAfterRemoval(i);
                            break;
                        }
                    }
                }
                isRefreshLoading = false;
                isMoreLoading = false;

            }
        });


    }

    private void showData(List<WKMsg> msgList, int pullMode, boolean isSetNewData, boolean isScrollToEnd) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            final List<WKMsg> snapshot = msgList == null ? new ArrayList<>() : new ArrayList<>(msgList);
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> showData(snapshot, pullMode, isSetNewData, isScrollToEnd));
            return;
        }
        boolean isAddEmptyView = WKReader.isNotEmpty(msgList) && msgList.size() < limit;
        if (isAddEmptyView) {
            WKMsg msg = new WKMsg();
            msg.timestamp = 0;
            msg.type = WKContentType.emptyView;
            msgList.add(0, msg);
        }

        if ((isShowCallingView || isShowPinnedView) && pullMode == 0) {
            if (WKReader.isNotEmpty(chatAdapter.getData())) {
                for (int i = 0; i < chatAdapter.getData().size(); i++) {
                    if (chatAdapter.getData().get(i).wkMsg != null
                            && chatAdapter.getData().get(i).wkMsg.type == WKContentType.spanEmptyView) {
                        chatAdapter.removeAt(i);
                        break;
                    }
                }
            }
            msgList.add(0, getSpanEmptyMsg());
        }

        List<WKUIChatMsgItemEntity> list = new ArrayList<>();
        if (WKReader.isNotEmpty(msgList)) {
            long preMsgTime = chatAdapter.getLastTimeMsg();
            for (WKMsg msg : msgList) {
                // getData.onResult 已经识别并处理过 RTC；这里仅做基础隐藏校验，
                // 避免同一页历史消息再次解析 RTC 信令。
                if (shouldHideBasicFromChatList(msg)) {
                    continue;
                }
                if (!WKTimeUtils.getInstance().isSameDay(msg.timestamp, preMsgTime)
                        && msg.type != WKContentType.emptyView
                        && msg.type != WKContentType.spanEmptyView) {
                    WKUIChatMsgItemEntity timeItem = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
                    timeItem.wkMsg.type = WKContentType.msgPromptTime;
                    timeItem.wkMsg.content = WKTimeUtils.getInstance().getShowDate(msg.timestamp * 1000);
                    timeItem.wkMsg.timestamp = msg.timestamp;
                    list.add(timeItem);
                }
                preMsgTime = msg.timestamp;
                WKUIChatMsgItemEntity uiMsg = WKIMUtils.getInstance().msg2UiMsg(
                        this, msg, count, showNickName, chatAdapter.isShowChooseItem());
                if (msg.remoteExtra != null) {
                    uiMsg.isPinned = hideChannelAllPinnedMessage == 1 ? 0 : msg.remoteExtra.isPinned;
                }
                list.add(uiMsg);
            }
        }

        if (isSetNewData) {
            if (unreadStartMsgOrderSeq != 0) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    if (list.get(i).wkMsg != null && list.get(i).wkMsg.orderSeq == unreadStartMsgOrderSeq) {
                        WKUIChatMsgItemEntity unreadDivider = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
                        unreadDivider.wkMsg.type = WKContentType.msgPromptNewMsg;
                        int index = Math.max(0, Math.min(i, list.size() - 1));
                        list.add(index, unreadDivider);
                        if (index >= 1) {
                            linearLayoutManager.scrollToPositionWithOffset(index, 50);
                        } else {
                            wkVBinding.recyclerView.scrollToPosition(index);
                        }
                        unreadStartMsgOrderSeq = 0;
                        break;
                    }
                }
            }
            // resetData 会按原版规则把时间/未读分割线也纳入相邻关系，避免跨分割线错误连尾。
            chatAdapter.resetData(list);
            chatAdapter.setNewInstance(list);
        } else {
            chatAdapter.resetData(list);
            if (pullMode == 1) {
                // 向底部追加较新的消息：同时维护旧尾与新头的双向关系。
                // 只刷新这个分页边界，不扫描整张消息表。
                int oldLastIndex = chatAdapter.getData().size() - 1;
                if (oldLastIndex >= 0 && WKReader.isNotEmpty(list)) {
                    WKUIChatMsgItemEntity oldLast = chatAdapter.getData().get(oldLastIndex);
                    WKUIChatMsgItemEntity newFirst = list.get(0);
                    if (oldLast != null && oldLast.wkMsg != null && newFirst != null) {
                        oldLast.nextMsg = newFirst.wkMsg;
                        newFirst.previousMsg = oldLast.wkMsg;
                    }
                }
                chatAdapter.addData(list);
                notifyMessageAppearance(oldLastIndex);
            } else {
                // 向顶部插入更旧的消息：同时维护新尾与旧头的双向关系。
                // 特别修复置顶/通话占位被替换后，旧第一条消息仍指向已删除占位的问题。
                WKUIChatMsgItemEntity oldFirst = WKReader.isNotEmpty(chatAdapter.getData())
                        ? chatAdapter.getData().get(0) : null;
                if (WKReader.isNotEmpty(list) && oldFirst != null && oldFirst.wkMsg != null) {
                    WKUIChatMsgItemEntity newLast = list.get(list.size() - 1);
                    if (newLast != null) {
                        newLast.nextMsg = oldFirst.wkMsg;
                        oldFirst.previousMsg = newLast.wkMsg;
                    }
                }
                int insertedCount = list.size();
                chatAdapter.addData(0, list);
                notifyMessageAppearance(insertedCount);
            }
        }

        if (tipsOrderSeq != 0 || lastPreviewMsgOrderSeq != 0) {
            wkVBinding.recyclerView.setVisibility(View.VISIBLE);
            if (tipsOrderSeq != 0) {
                for (int i = 0; i < chatAdapter.getData().size(); i++) {
                    if (chatAdapter.getItem(i).wkMsg.orderSeq == tipsOrderSeq) {
                        linearLayoutManager.scrollToPositionWithOffset(i, AndroidUtilities.dp(50));
                        chatAdapter.getItem(i).isShowTips = true;
                        chatAdapter.notifyItemChanged(i);
                        tipsOrderSeq = 0;
                        break;
                    }
                }
            }
            if (lastPreviewMsgOrderSeq != 0) {
                for (int i = 0; i < chatAdapter.getData().size(); i++) {
                    if (chatAdapter.getItem(i).wkMsg.orderSeq == lastPreviewMsgOrderSeq) {
                        linearLayoutManager.scrollToPositionWithOffset(i, keepOffsetY);
                        break;
                    }
                }
            }
        } else if (isScrollToEnd) {
            wkVBinding.recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        } else {
            wkVBinding.recyclerView.setVisibility(View.VISIBLE);
        }

        if (isCanLoadMore && WKReader.isNotEmpty(chatAdapter.getData())
                && chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg != null) {
            int maxSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(channelId, channelType);
            if (chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg.messageSeq == maxSeq) {
                isCanLoadMore = false;
            }
        }

        final long requestGeneration = channelGeneration;
        final String requestChannelId = channelId;
        final byte requestChannelType = channelType;
        mainHandler.postDelayed(() -> {
            if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) return;
            if (isUpdateRedDot) {
                final int requestUnreadCount = redDot;
                MsgModel.getInstance().clearUnread(requestChannelId, requestChannelType, requestUnreadCount, (code, msg) -> {
                    if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)
                            && code == HttpResponseCode.success && requestUnreadCount == 0) {
                        isUpdateRedDot = false;
                    }
                });
            }
        }, 500);
    }


    private void notifyMessageAppearance(int index) {
        if (chatAdapter == null || index < 0 || index >= chatAdapter.getData().size()) return;
        WKUIChatMsgItemEntity item = chatAdapter.getData().get(index);
        if (item == null || item.wkMsg == null) return;
        int type = item.wkMsg.type;
        if (WKContentType.isLocalMsg(type) || WKContentType.isSystemMsg(type)) {
            chatAdapter.notifyItemChanged(index);
        } else {
            chatAdapter.notifyBackground(index);
        }
    }

    /**
     * Adapter 删除一个 item 后，只修复删除点两侧的 previous/next。
     * 不再扫描整张消息表，保持唐僧叨叨原版的局部增量刷新节奏。
     */
    private void relinkAfterRemoval(int removedIndex) {
        if (chatAdapter == null) return;
        List<WKUIChatMsgItemEntity> data = chatAdapter.getData();
        int previousIndex = removedIndex - 1;
        int nextIndex = removedIndex;
        WKUIChatMsgItemEntity previous = previousIndex >= 0 && previousIndex < data.size()
                ? data.get(previousIndex) : null;
        WKUIChatMsgItemEntity next = nextIndex >= 0 && nextIndex < data.size()
                ? data.get(nextIndex) : null;
        if (previous != null) {
            previous.nextMsg = next == null ? null : next.wkMsg;
        }
        if (next != null) {
            next.previousMsg = previous == null ? null : previous.wkMsg;
        }
        notifyMessageAppearance(previousIndex);
        notifyMessageAppearance(nextIndex);
    }

    @Nullable
    private WKUIChatMsgItemEntity detachTrailingTypingItem() {
        return removeTrailingTypingItem(false);
    }

    /**
     * Removes the single temporary typing row without BRVAH removeAt(), whose 3.x implementation
     * also refreshes the entire remaining range. The active expiry is preserved when the row is
     * only being moved behind a newly inserted message.
     */
    @Nullable
    private WKUIChatMsgItemEntity removeTrailingTypingItem(boolean cancelExpiry) {
        if (chatAdapter == null || WKReader.isEmpty(chatAdapter.getData())) return null;
        List<WKUIChatMsgItemEntity> data = chatAdapter.getData();
        int lastIndex = data.size() - 1;
        WKUIChatMsgItemEntity last = data.get(lastIndex);
        if (last == null || last.wkMsg == null || last.wkMsg.type != WKContentType.typing) {
            return null;
        }
        if (cancelExpiry) {
            cancelTypingExpiry(last.wkMsg);
        }
        data.remove(lastIndex);
        int previousIndex = lastIndex - 1;
        WKUIChatMsgItemEntity previous = previousIndex >= 0 && previousIndex < data.size()
                ? data.get(previousIndex) : null;
        if (previous != null) previous.nextMsg = null;

        int header = chatAdapter.getHeaderLayoutCount();
        chatAdapter.notifyItemRemoved(lastIndex + header);
        if (previousIndex >= 0 && previousIndex < data.size()) {
            chatAdapter.notifyItemChanged(previousIndex + header);
        }
        last.previousMsg = null;
        last.nextMsg = null;
        return last;
    }

    private void restoreTrailingTypingItem(@Nullable WKUIChatMsgItemEntity typingItem) {
        if (typingItem == null || chatAdapter == null || typingItem.wkMsg == null) return;
        int previousIndex = chatAdapter.getData().size() - 1;
        if (previousIndex >= 0) {
            WKUIChatMsgItemEntity previous = chatAdapter.getData().get(previousIndex);
            if (previous != null && previous.wkMsg != null) {
                previous.nextMsg = typingItem.wkMsg;
                typingItem.previousMsg = previous.wkMsg;
            }
        }
        typingItem.nextMsg = null;
        chatAdapter.addData(typingItem);
        notifyMessageAppearance(previousIndex);
    }

    private void hideOrShowRightView(boolean isShow) {
        if (((channelId.equals(WKSystemAccount.system_file_helper) || channelId.equals(WKSystemAccount.system_team)) && channelType == WKChannelType.PERSONAL) || channelType == WKChannelType.CUSTOMER_SERVICE) {
            isShow = false;
        }
        WKChannel channel = getChatChannelInfo();
        if (channelType == WKChannelType.PERSONAL && (channel.isDeleted == 1 || UserUtils.getInstance().checkFriendRelation(channelId))) {
            isShow = false;
        }
        CommonAnim.getInstance().showOrHide(callIV, isShow, true);
    }

    private void resetReminder(List<WKReminder> list) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            final List<WKReminder> snapshot = list == null ? new ArrayList<>() : new ArrayList<>(list);
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> resetReminder(snapshot));
            return;
        }
        if (WKReader.isEmpty(list)) {
            return;
        }
        List<WKUIChatMsgItemEntity> msgList = chatAdapter.getData();
        List<Long> ids = new ArrayList<>();
        for (int i = 0, size = msgList.size(); i < size; i++) {
            for (WKReminder reminder : list) {
                if (msgList.get(i).wkMsg != null && !TextUtils.isEmpty(msgList.get(i).wkMsg.messageID) && msgList.get(i).wkMsg.messageID.equals(reminder.messageID)) {
                    if (msgList.get(i).wkMsg.viewed == 1 && reminder.done == 0) {
                        ids.add(reminder.reminderID);
                    }
                }
            }
        }

        MsgModel.getInstance().doneReminder(ids);

        for (WKReminder reminder : list) {
            boolean isPublisher = !TextUtils.isEmpty(reminder.publisher) && reminder.publisher.equals(loginUID);
            if (!reminder.channelID.equals(channelId) || isPublisher) continue;
            if (reminder.done == 0) {
                boolean isAdd = true;
                for (int i = 0, size = reminderList.size(); i < size; i++) {
                    if (reminder.reminderID == reminderList.get(i).reminderID && reminder.type == reminderList.get(i).type) {
                        isAdd = false;
                        reminderList.get(i).done = 0;
                        break;
                    }
                }
                for (int i = 0; i < ids.size(); i++) {
                    if (ids.get(i) == reminder.reminderID) {
                        isAdd = false;
                        break;
                    }
                }
                if (isAdd && reminder.type == WKMentionType.WKReminderTypeMentionMe)
                    reminderList.add(reminder);
                boolean isAddApprove = true;
                for (int i = 0, size = groupApproveList.size(); i < size; i++) {
                    if (reminder.reminderID == groupApproveList.get(i).reminderID && reminder.type == groupApproveList.get(i).type) {
                        isAddApprove = false;
                        groupApproveList.get(i).done = 0;
                        break;
                    }
                }
                if (isAddApprove && reminder.type == WKMentionType.WKApplyJoinGroupApprove)
                    groupApproveList.add(reminder);
            } else {
                if (WKReader.isNotEmpty(reminderList)) {
                    for (int i = 0, size = reminderList.size(); i < size; i++) {
                        if (reminder.messageID.equals(reminderList.get(i).messageID)) {
                            reminderList.remove(i);
                            break;
                        }
                    }
                }
                if (WKReader.isNotEmpty(groupApproveList)) {
                    for (int i = 0, size = groupApproveList.size(); i < size; i++) {
                        if (reminder.messageID.equals(groupApproveList.get(i).messageID)) {
                            groupApproveList.remove(i);
                            break;
                        }
                    }
                }
            }
        }
        resetRemindView();
        resetGroupApproveView();
    }

    private void resetRemindView() {
        int count = reminderList.size();
        if (count == lastReminderCount) return;
        lastReminderCount = count;
        wkVBinding.chatUnreadLayout.remindCountTv.setCount(count, true);
        wkVBinding.chatUnreadLayout.remindCountTv.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        postForCurrentSession(
                wkVBinding.chatUnreadLayout.remindLayout,
                () -> CommonAnim.getInstance().showOrHide(
                        wkVBinding.chatUnreadLayout.remindLayout,
                        count > 0,
                        count > 0,
                        false
                )
        );
    }

    private void resetGroupApproveView() {
        int count = groupApproveList.size();
        if (count == lastGroupApproveCount) return;
        lastGroupApproveCount = count;
        wkVBinding.chatUnreadLayout.approveCountTv.setCount(count, true);
        wkVBinding.chatUnreadLayout.approveCountTv.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        final boolean hasReminder = WKReader.isNotEmpty(reminderList);
        postForCurrentSession(
                wkVBinding.chatUnreadLayout.groupApproveLayout,
                () -> CommonAnim.getInstance().showOrHide(
                        wkVBinding.chatUnreadLayout.groupApproveLayout,
                        count > 0,
                        hasReminder,
                        false
                )
        );
    }

    private void showUnReadCountView() {
        boolean show = redDot > 0;
        wkVBinding.chatUnreadLayout.msgCountTv.setCount(redDot, false);
        wkVBinding.chatUnreadLayout.msgCountTv.setVisibility(show ? View.VISIBLE : View.GONE);
        postForCurrentSession(
                wkVBinding.chatUnreadLayout.newMsgLayout,
                () -> CommonAnim.getInstance().showOrHide(
                        wkVBinding.chatUnreadLayout.newMsgLayout,
                        show,
                        show,
                        false
                )
        );
    }

    private void showTopAvatar(WKChannel channel) {
        if (channel == null) return;
        wkVBinding.topLayout.avatarView.setSize(40);
        if (isTopicRoomChannel(channel)) {
            wkVBinding.topLayout.otherLayout.setVisibility(View.GONE);
            wkVBinding.topLayout.avatarView.showAvatar(channel);
        } else {
            wkVBinding.topLayout.otherLayout.setVisibility(View.VISIBLE);
            wkVBinding.topLayout.avatarView.showAvatar(channel);
            EndpointManager.getInstance().invoke("show_avatar_other_info", new AvatarOtherViewMenu(wkVBinding.topLayout.otherLayout, channel, wkVBinding.topLayout.avatarView, true));
        }
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

    private boolean isCurrentTopicRoom() {
        if (channelType != WKChannelType.GROUP || TextUtils.isEmpty(channelId)) return false;
        if (channelId.startsWith("topic_")) return true;
        return isTopicRoomChannel(getCurrentChatChannel());
    }

    private long getTopicRoomExpireAt(WKChannel channel) {
        if (channel == null) return 0L;
        long value = getLongExtra(channel.localExtra, "expire_at");
        if (value <= 0) value = getLongExtra(channel.remoteExtraMap, "expire_at");
        return value;
    }

    private long getLongExtra(Map<String, Object> extras, String key) {
        if (extras == null || TextUtils.isEmpty(key)) return 0L;
        Object value = extras.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private boolean isTopicRoomExpired(WKChannel channel) {
        long expireAt = getTopicRoomExpireAt(channel);
        return expireAt > 0 && expireAt <= System.currentTimeMillis();
    }

    private boolean closeCurrentTopicRoomIfExpired() {
        if (!isCurrentTopicRoom()) return false;
        WKChannel channel = getCurrentChatChannel();
        if (!isTopicRoomExpired(channel)) return false;
        finishCurrentTopicRoom(getString(R.string.topic_room_expired));
        return true;
    }

    private void handleTopicRoomDeletedCmd(JSONObject params) {
        if (params == null || topicRoomClosing) return;
        String deletedChannelId = params.optString("channel_id");
        if (TextUtils.isEmpty(deletedChannelId)) deletedChannelId = params.optString("room_id");
        int deletedType = params.optInt("channel_type", WKChannelType.GROUP);
        if (!TextUtils.equals(channelId, deletedChannelId) || channelType != (byte) deletedType) return;
        if (!isCurrentTopicRoom()) return;
        String reason = params.optString("reason");
        finishCurrentTopicRoom("expired".equals(reason)
                ? getString(R.string.topic_room_expired)
                : getString(R.string.topic_room_ended));
    }

    private void finishCurrentTopicRoom(String message) {
        if (topicRoomClosing) return;
        topicRoomClosing = true;
        if (wkVBinding != null && wkVBinding.panelView != null) {
            wkVBinding.panelView.setEnabled(false);
            wkVBinding.panelView.setAlpha(0.55f);
        }
        MsgModel.getInstance().clearUnread(channelId, channelType, 0, null);
        WKIM.getInstance().getConversationManager().deleteWitchChannel(channelId, channelType);
        if (!TextUtils.isEmpty(message)) WKToastUtils.getInstance().showToastNormal(message);
        mainHandler.post(this::finish);
    }

    private void markCurrentTopicRoomRead() {
        markTopicRoomRead(channelId, channelType);
    }

    private void markTopicRoomRead(String targetChannelId, byte targetChannelType) {
        if (targetChannelType != WKChannelType.GROUP || TextUtils.isEmpty(targetChannelId)) return;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(targetChannelId, targetChannelType);
        boolean topic = targetChannelId.startsWith("topic_") || isTopicRoomChannel(channel);
        if (!topic) return;
        String key = targetChannelType + ":" + targetChannelId;
        long now = System.currentTimeMillis();
        if (TextUtils.equals(lastTopicRoomReadKey, key)
                && now - lastTopicRoomReadAt < TOPIC_ROOM_READ_THROTTLE_MS) return;
        lastTopicRoomReadKey = key;
        lastTopicRoomReadAt = now;
        WKChannel payload = channel == null ? new WKChannel(targetChannelId, targetChannelType) : channel;
        EndpointManager.getInstance().invoke(EndpointSID.topicRoomMarkRead, payload);
    }

    private void showChannelName(WKChannel channel) {
        if (channelId.equals(WKSystemAccount.system_team)) {
            wkVBinding.topLayout.titleCenterTv.setText(R.string.wk_system_notice);
        } else if (channelId.equals(WKSystemAccount.system_file_helper)) {
            wkVBinding.topLayout.titleCenterTv.setText(R.string.wk_file_helper);
        } else {
            String showName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
            if (TextUtils.isEmpty(showName) && isTopicRoomChannel(channel)) {
                showName = getTopicExtraString(channel, "topic_title");
            }
            wkVBinding.topLayout.titleCenterTv.setText(showName);
        }
    }

    private void removeMsg(WKMsg msg) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> removeMsg(msg));
            return;
        }
        if (msg == null || chatAdapter == null || WKReader.isEmpty(chatAdapter.getData())) return;
        EndpointManager.getInstance().invoke("stop_reaction_animation", null);
        int removeIndex = -1;
        boolean removedReceivedText = false;
        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
            WKUIChatMsgItemEntity entity = chatAdapter.getData().get(i);
            if (entity != null && isSameMessage(entity.wkMsg, msg)) {
                removeIndex = i;
                WKMsg removedMsg = entity.wkMsg;
                removedReceivedText = removedMsg != null
                        && removedMsg.type == WKContentType.WK_TEXT
                        && !TextUtils.isEmpty(removedMsg.fromUID)
                        && !TextUtils.equals(removedMsg.fromUID, loginUID);
                break;
            }
        }
        if (removeIndex < 0) return;

        chatAdapter.removeAt(removeIndex);
        relinkAfterRemoval(removeIndex);

        // 删除点前面可能夹着“新消息”分割线，不能只检查紧邻位置。
        // 删除属于低频操作，这里从后往前清理孤立分割线，避免日期或“新消息”提示悬空。
        removeOrphanLocalDividers();

        // 删除最后一条对方文字后，上一条对方文字必须重新绑定，恢复快捷翻译按钮。
        if (removedReceivedText) {
            refreshLatestInlineTranslateButton();
        }
    }

    private boolean isOrphanTimeDivider(int timeIndex) {
        if (chatAdapter == null || timeIndex < 0) return true;
        List<WKUIChatMsgItemEntity> data = chatAdapter.getData();
        for (int i = timeIndex + 1; i < data.size(); i++) {
            WKUIChatMsgItemEntity item = data.get(i);
            if (item == null || item.wkMsg == null) continue;
            int type = item.wkMsg.type;
            if (type == WKContentType.msgPromptTime) return true;
            if (type == WKContentType.loading
                    || type == WKContentType.emptyView
                    || type == WKContentType.spanEmptyView
                    || type == WKContentType.msgPromptNewMsg) {
                continue;
            }
            // typing/noRelation/system 等仍是当前日期组内可见内容，不应误删日期。
            return false;
        }
        return true;
    }

    private boolean isOrphanUnreadDivider(int unreadIndex) {
        if (chatAdapter == null || unreadIndex < 0) return true;
        List<WKUIChatMsgItemEntity> data = chatAdapter.getData();
        for (int i = unreadIndex + 1; i < data.size(); i++) {
            WKUIChatMsgItemEntity item = data.get(i);
            if (item == null || item.wkMsg == null) continue;
            int type = item.wkMsg.type;
            if (type == WKContentType.msgPromptTime
                    || type == WKContentType.msgPromptNewMsg
                    || type == WKContentType.loading
                    || type == WKContentType.emptyView
                    || type == WKContentType.spanEmptyView
                    || type == WKContentType.typing) {
                continue;
            }
            return false;
        }
        return true;
    }

    private void refreshLatestInlineTranslateButton() {
        int latestIndex = findLatestReceivedTextMsgIndex();
        if (latestIndex >= 0 && chatAdapter != null && latestIndex < chatAdapter.getData().size()) {
            chatAdapter.notifyData(latestIndex);
        }
    }

    private void removeOrphanLocalDividers() {
        if (chatAdapter == null || WKReader.isEmpty(chatAdapter.getData())) return;
        for (int i = chatAdapter.getData().size() - 1; i >= 0; i--) {
            WKUIChatMsgItemEntity item = chatAdapter.getData().get(i);
            if (item == null || item.wkMsg == null) continue;
            int type = item.wkMsg.type;
            boolean remove = type == WKContentType.msgPromptTime
                    ? isOrphanTimeDivider(i)
                    : type == WKContentType.msgPromptNewMsg && isOrphanUnreadDivider(i);
            if (remove) {
                chatAdapter.removeAt(i);
                relinkAfterRemoval(i);
            }
        }
    }

    private void showToast(int textId) {
        WKToastUtils.getInstance().showToast(getString(textId));
    }

    private void setShowTime() {
        String showTime = "";
        int index = linearLayoutManager.findFirstVisibleItemPosition();
        if (index > 0 && index < chatAdapter.getData().size()) {
            WKUIChatMsgItemEntity itemEntity = chatAdapter.getData().get(index);
            if (itemEntity != null && itemEntity.wkMsg != null && itemEntity.wkMsg.timestamp > 0) {
                showTime = WKTimeUtils.getInstance().getShowDate(itemEntity.wkMsg.timestamp * 1000);
            }
        }
        boolean visible = !TextUtils.isEmpty(showTime);
        if (visible) {
            if (!TextUtils.equals(showTime, lastFloatingTime) || !lastFloatingTimeVisible) {
                lastFloatingTime = showTime;
                lastFloatingTimeVisible = true;
                SpannableString str = new SpannableString(showTime);
                str.setSpan(new SystemMsgBackgroundColorSpan(ContextCompat.getColor(this, R.color.colorSystemBg), AndroidUtilities.dp(5), AndroidUtilities.dp(2 * 5)), 0, showTime.length(), 0);
                wkVBinding.timeTv.setText(str);
            }
            CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, true, true);
        } else if (lastFloatingTimeVisible) {
            lastFloatingTimeVisible = false;
            lastFloatingTime = "";
            CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, false);
        }
    }

    private boolean isRefreshReaction(List<WKMsgReaction> oldList, List<WKMsgReaction> newList) {
        if (WKReader.isEmpty(oldList) && WKReader.isEmpty(newList)) return false;
        if ((WKReader.isEmpty(oldList) && WKReader.isNotEmpty(newList)) || (WKReader.isEmpty(newList) && WKReader.isNotEmpty(oldList)) || (oldList.size() != newList.size())) {
            return true;
        }
        boolean isRefresh = false;
        for (WKMsgReaction reaction : newList) {
            boolean refresh = true;
            for (WKMsgReaction reaction1 : oldList) {
                if (reaction1.messageID.equals(reaction.messageID) && reaction1.emoji.equals(reaction.emoji) && reaction1.isDeleted == reaction.isDeleted) {
                    refresh = false;
                    break;
                }
            }
            if (refresh) {
                isRefresh = true;
                break;
            }
        }
        return isRefresh;
    }

    private void scrollToPosition(int index) {
        linearLayoutManager.scrollToPosition(index);
    }


    private void showRefreshLoading() {
        if (isRefreshLoading || !isCanRefresh) return;
        isRefreshLoading = true;
        WKMsg wkMsg = new WKMsg();
        wkMsg.type = WKContentType.loading;
        int index = 0;
        if (isShowPinnedView || isShowCallingView) {
            for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.type == WKContentType.spanEmptyView) {
                    index = i + 1;
                    break;
                }
            }
        }
        chatAdapter.addData(index, new WKUIChatMsgItemEntity(this, wkMsg, null));
        wkVBinding.recyclerView.scrollToPosition(0);
        lastPreviewMsgOrderSeq = 0;
        final long requestGeneration = channelGeneration;
        final String requestChannelId = channelId;
        final byte requestChannelType = channelType;
        mainHandler.postDelayed(() -> {
            if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) {
                getData(0, false, 0, false);
            }
        }, 300);
    }

    private void showMoreLoading() {
        if (isMoreLoading || !isCanLoadMore) return;
        isMoreLoading = true;
        WKMsg wkMsg = new WKMsg();
        wkMsg.type = WKContentType.loading;
        chatAdapter.addData(new WKUIChatMsgItemEntity(this, wkMsg, null));
        wkVBinding.recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        lastPreviewMsgOrderSeq = 0;
        unreadStartMsgOrderSeq = 0;
        final long requestGeneration = channelGeneration;
        final String requestChannelId = channelId;
        final byte requestChannelType = channelType;
        mainHandler.postDelayed(() -> {
            if (isCurrentSession(requestGeneration, requestChannelId, requestChannelType)) {
                getData(1, false, 0, false);
            }
        }, 300);
    }

    private List<PopupMenuItem> getGroupApprovePopupItems() {
        PopupMenuItem item = new PopupMenuItem(getString(R.string.clear_all_remind), R.mipmap.msg_seen, () -> {
            List<WKReminder> list = WKIM.getInstance().getReminderManager().getRemindersWithType(channelId, channelType, WKMentionType.WKApplyJoinGroupApprove);
            List<Long> ids = new ArrayList<>();
            for (WKReminder reminder : list) {
                if (reminder.done == 0) {
                    ids.add(reminder.reminderID);
                }
            }
            groupApproveList.clear();
            resetGroupApproveView();
            MsgModel.getInstance().doneReminder(ids);
        });

        List<PopupMenuItem> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    private List<PopupMenuItem> getRemindPopupItems() {
        PopupMenuItem item = new PopupMenuItem(getString(R.string.clear_all_remind), R.mipmap.msg_seen, () -> {
            List<WKReminder> list = WKIM.getInstance().getReminderManager().getRemindersWithType(channelId, channelType, WKMentionType.WKReminderTypeMentionMe);
            List<Long> ids = new ArrayList<>();
            for (WKReminder reminder : list) {
                if (reminder.done == 0) {
                    ids.add(reminder.reminderID);
                }
            }
            reminderList.clear();
            resetRemindView();
            MsgModel.getInstance().doneReminder(ids);
        });

        List<PopupMenuItem> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    private void checkLoginUserInGroupStatus() {
        if (channelType == WKChannelType.GROUP) {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
            hideOrShowRightView(member == null || member.isDeleted == 0);
        }
    }

    private void scrollToEnd() {
        linearLayoutManager.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private WKMsg addTimeMsg(long newMsgTime) {
        long lastMsgTime = chatAdapter.getLastTimeMsg();
        WKMsg msg = null;
        if (!WKTimeUtils.getInstance().isSameDay(newMsgTime, lastMsgTime)) {
            int lastIndex = chatAdapter.getData().size() - 1;
            WKUIChatMsgItemEntity uiChatMsgEntity = new WKUIChatMsgItemEntity(this, null, null);
            msg = new WKMsg();
            uiChatMsgEntity.wkMsg = msg;
            uiChatMsgEntity.isChoose = (chatAdapter.getItemCount() > 0 && chatAdapter.getData().get(0).isChoose);
            uiChatMsgEntity.wkMsg.type = WKContentType.msgPromptTime;
            uiChatMsgEntity.wkMsg.content = WKTimeUtils.getInstance().getShowDate(newMsgTime * 1000);
            uiChatMsgEntity.wkMsg.timestamp = WKTimeUtils.getInstance().getCurrentSeconds();
            if (lastIndex >= 0) {
                WKUIChatMsgItemEntity previous = chatAdapter.getData().get(lastIndex);
                if (previous != null && previous.wkMsg != null) {
                    previous.nextMsg = msg;
                    uiChatMsgEntity.previousMsg = previous.wkMsg;
                }
            }
            chatAdapter.addData(uiChatMsgEntity);
            notifyMessageAppearance(lastIndex);
        }
        return msg;
    }

    private boolean setBackListener() {
        if (isViewingPicture) return false;

        if (numberTextView != null && numberTextView.getVisibility() == View.VISIBLE) {
            for (int i = 0, size = chatAdapter.getItemCount(); i < size; i++) {
                chatAdapter.getItem(i).isChoose = false;
                chatAdapter.getItem(i).isChecked = false;
                chatAdapter.notifyItemChanged(i, chatAdapter.getItem(i));
            }
            chatPanelManager.hideMultipleChoice();
            CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_ab_back);
            numberTextView.setNumber(0, true);
            hideOrShowRightView(true);
            EndpointManager.getInstance().invoke("chat_page_reset", getChatChannelInfo());
            CommonAnim.getInstance().showOrHide(numberTextView, false, true);
            return true;
        }

        if (chatPanelManager != null) {
            if (chatPanelManager.isCanBack()) {
                mainHandler.postDelayed(this::finish, 150);
            }
            // isCanBack() 返回 false 表示它已经关闭了回复栏、图片预览或键盘面板。
            return true;
        }
        return false;
    }


    private void startTimer() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            disposable = null;
        }
        Observable.interval(0, 3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<>() {
            @Override
            public void onComplete() {
            }

            @Override
            public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                Log.e("ChatActivity", "read timer error", e);
            }

            @Override
            public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                disposable = d;
            }

            @Override
            public void onNext(@io.reactivex.rxjava3.annotations.NonNull Long value) {
                if (!hasPendingReadReceipts() || !isUploadReadMsg) {
                    return;
                }
                flushReadReceipts(channelId, channelType);
            }
        });
    }

    private void resetHideChannelAllPinnedMessage() {
        String key = String.format("hide_pin_msg_%s_%s", channelId, channelType);
        hideChannelAllPinnedMessage = WKSharedPreferencesUtil.getInstance().getIntWithUID(key);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN)
            EndpointManager.getInstance().invoke("chat_activity_touch", null);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float density = getResources().getDisplayMetrics().density;
        AndroidUtilities.setDensity(density);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            AndroidUtilities.isPORTRAIT = false;
            chatAdapter.notifyItemRangeChanged(0, chatAdapter.getItemCount());
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            AndroidUtilities.isPORTRAIT = true;
            chatAdapter.notifyItemRangeChanged(0, chatAdapter.getItemCount());
        }
        toggleStatusBarMode();
        if (wkVBinding != null && wkVBinding.rootView != null) {
            ViewCompat.requestApplyInsets(wkVBinding.rootView);
            wkVBinding.bottomView.post(this::updateFloatingComposerSpacing);
        }
    }

    private void sendImageContentAsync(WKMessageContent messageContent) {
        sendImageContentAsync(messageContent, channelId, channelType);
    }

    private void sendImageContentAsync(WKMessageContent messageContent,
                                       String targetChannelId, byte targetChannelType) {
        Disposable task = Observable.fromCallable(() -> {
                    compressImageContentIfNeeded(messageContent);
                    return messageContent;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(content -> sendMsg(content, targetChannelId, targetChannelType), throwable -> {
                    Log.e("ChatActivity", "async image compress failed", throwable);
                    sendMsg(messageContent, targetChannelId, targetChannelType);
                });
        asyncDisposables.add(task);
    }

    private void sendImagePathAsync(String path) {
        final WKMsg replySnapshot = replyWKMsg;
        replyWKMsg = null;
        sendImagePathAsync(path, channelId, channelType, replySnapshot);
    }

    private void sendImagePathAsync(String path, String targetChannelId, byte targetChannelType, WKMsg replySnapshot) {
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(targetChannelId)) return;
        Disposable task = Observable.fromCallable(() -> compressImagePathIfNeeded(path))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(compressedPath -> {
                    WKImageContent content = new WKImageContent(compressedPath);
                    attachReply(content, replySnapshot);
                    sendMsg(content, targetChannelId, targetChannelType);
                }, throwable -> {
                    Log.e("ChatActivity", "async image path compress failed", throwable);
                    WKImageContent content = new WKImageContent(path);
                    attachReply(content, replySnapshot);
                    sendMsg(content, targetChannelId, targetChannelType);
                });
        asyncDisposables.add(task);
    }

    @Override
    public void sendMessage(WKMessageContent messageContent) {
        if (messageContent == null) return;

        if (messageContent.type == WKContentType.WK_TEXT && editMsg != null) {
            JSONObject jsonObject = messageContent.encodeMsg();
            if (jsonObject == null) jsonObject = new JSONObject();
            try {
                jsonObject.put("type", messageContent.type);
            } catch (JSONException e) {
                Log.e("消息类型错误", "-->");
            }
            if (isUpdate(messageContent)) {
                WKIM.getInstance().getMsgManager().updateMsgEdit(editMsg.messageID, channelId, channelType, jsonObject.toString());
            }
            deleteOperationMsg();
            return;
        }

        if (replyWKMsg != null) {
            attachReply(messageContent, replyWKMsg);
            // 在异步任务启动前清除当前回复，回调不得再碰这个全局字段。
            replyWKMsg = null;
        }

        if (messageContent instanceof WKImageContent) {
            sendImageContentAsync(messageContent);
            return;
        }

        sendMsg(messageContent);
    }

    @Override
    public void sendMessageToChannel(WKMessageContent messageContent, String targetChannelId, byte targetChannelType) {
        if (messageContent == null || TextUtils.isEmpty(targetChannelId)) return;
        // 该入口专供已经捕获目标会话的异步任务使用，不读取 replyWKMsg/editMsg，
        // 避免回调回来时把旧会话状态附加到新会话。
        if (messageContent instanceof WKImageContent) {
            sendImageContentAsync(messageContent, targetChannelId, targetChannelType);
        } else {
            sendMsg(messageContent, targetChannelId, targetChannelType);
        }
    }

    private void attachReply(WKMessageContent messageContent, WKMsg sourceMsg) {
        if (messageContent == null || sourceMsg == null) return;
        WKReply wkReply = new WKReply();
        if (sourceMsg.remoteExtra != null && sourceMsg.remoteExtra.contentEditMsgModel != null) {
            wkReply.payload = sourceMsg.remoteExtra.contentEditMsgModel;
        } else {
            wkReply.payload = sourceMsg.baseContentMsgModel;
        }
        String showName = "";
        if (sourceMsg.getFrom() != null) {
            showName = sourceMsg.getFrom().channelName;
        } else {
            WKChannel sourceChannel = WKIM.getInstance().getChannelManager().getChannel(sourceMsg.fromUID, WKChannelType.PERSONAL);
            if (sourceChannel != null) showName = sourceChannel.channelName;
        }
        wkReply.from_name = showName;
        wkReply.from_uid = sourceMsg.fromUID;
        wkReply.message_id = sourceMsg.messageID;
        wkReply.message_seq = sourceMsg.messageSeq;
        if (sourceMsg.baseContentMsgModel != null && sourceMsg.baseContentMsgModel.reply != null
                && !TextUtils.isEmpty(sourceMsg.baseContentMsgModel.reply.root_mid)) {
            wkReply.root_mid = sourceMsg.baseContentMsgModel.reply.root_mid;
        } else {
            wkReply.root_mid = wkReply.message_id;
        }
        messageContent.reply = wkReply;
    }

    private void sendMsg(WKMessageContent messageContent) {
        sendMsg(messageContent, channelId, channelType);
    }

    private void sendMsg(WKMessageContent messageContent, String targetChannelId, byte targetChannelType) {
        if (messageContent == null || TextUtils.isEmpty(targetChannelId)) return;
        boolean canTouchUi = !isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed())
                && wkVBinding != null;
        if (canTouchUi && TextUtils.equals(channelId, targetChannelId)
                && channelType == targetChannelType && redDot > 0) {
            wkVBinding.chatUnreadLayout.newMsgLayout.performClick();
        }
        WKMsg wkMsg = new WKMsg();
        wkMsg.channelID = targetChannelId;
        wkMsg.channelType = targetChannelType;
        wkMsg.type = messageContent.type;
        wkMsg.baseContentMsgModel = messageContent;
        WKChannel targetChannel = WKIM.getInstance().getChannelManager().getChannel(targetChannelId, targetChannelType);
        if (targetChannel == null) {
            targetChannel = new WKChannel(targetChannelId, targetChannelType);
        }
        wkMsg.setChannelInfo(targetChannel);
        WKSendMsgUtils.getInstance().sendMessage(wkMsg);
    }


    private boolean isUpdate(WKMessageContent messageContent) {
        if (editMsg == null || messageContent == null) return false;
        String newContent = messageContent.getDisplayContent();
        if (editMsg.remoteExtra != null && editMsg.remoteExtra.contentEditMsgModel != null) {
            return !TextUtils.equals(editMsg.remoteExtra.contentEditMsgModel.getDisplayContent(), newContent);
        }
        if (editMsg.baseContentMsgModel == null) return !TextUtils.isEmpty(newContent);
        return !TextUtils.equals(editMsg.baseContentMsgModel.getDisplayContent(), newContent);
    }

    private void setOnlineView(WKChannel channel) {
        if (channel.online == 1) {
            String device = getString(R.string.phone);
            if (channel.deviceFlag == UserOnlineStatus.Web) device = getString(R.string.web);
            else if (channel.deviceFlag == UserOnlineStatus.PC) device = getString(R.string.pc);
            String content = String.format("%s%s", device, getString(R.string.online));
            wkVBinding.topLayout.subtitleTv.setText(content);
            wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
        } else {
            if (channel.lastOffline > 0) {
                String showTime = WKTimeUtils.getInstance().getOnlineTime(channel.lastOffline);
                if (TextUtils.isEmpty(showTime)) {
                    wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
                    String time = WKTimeUtils.getInstance().getShowDateAndMinute(channel.lastOffline * 1000L);
                    String content = String.format("%s%s", getString(R.string.last_seen_time), time);
                    wkVBinding.topLayout.subtitleTv.setText(content);
                } else {
                    wkVBinding.topLayout.subtitleTv.setText(showTime);
                    wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
                }
            } else wkVBinding.topLayout.subtitleView.setVisibility(View.GONE);
        }
    }

    @Override
    public WKChannel getChatChannelInfo() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel == null) {
            channel = new WKChannel(channelId, channelType);
        }
        return channel;
    }

    @Override
    public void showMultipleChoice() {
        chatPanelManager.showMultipleChoice();
        CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_close_white);
        CommonAnim.getInstance().showOrHide(numberTextView, true, true);
        CommonAnim.getInstance().showOrHide(callIV, false, false);
        EndpointManager.getInstance().invoke("hide_pinned_view", null);
    }

    @Override
    public void setTitleRightText(String text) {
        int num;
        try {
            num = Integer.parseInt(TextUtils.isEmpty(text) ? "0" : text);
        } catch (NumberFormatException e) {
            Log.w("ChatActivity", "invalid selected message count: " + text, e);
            num = 0;
        }
        chatPanelManager.updateForwardView(num);
        numberTextView.setNumber(num, true);
        CommonAnim.getInstance().showOrHide(numberTextView, true, true);
        CommonAnim.getInstance().showOrHide(callIV, false, false);
    }

    @Override
    public void showReply(WKMsg wkMsg) {
        this.editMsg = null;
        boolean showDialog = false;
        WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel != null && mChannelMember != null) {
            if ((channel.forbidden == 1 && mChannelMember.role == WKChannelMemberRole.normal) || mChannelMember.forbiddenExpirationTime > 0) {
                showDialog = true;
            }
        }

        if (showDialog) {
            WKDialogUtils.getInstance().showSingleBtnDialog(this, "", getString(R.string.cannot_reply_msg), "", null);
            return;
        }

        if (channelType == WKChannelType.GROUP && !wkMsg.fromUID.equals(loginUID)) {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, wkMsg.fromUID);
            if (member != null) {
                chatPanelManager.addSpan(member.memberName, member.memberUID);
            } else {
                WKChannel mChannel = WKIM.getInstance().getChannelManager().getChannel(wkMsg.fromUID, WKChannelType.PERSONAL);
                if (mChannel != null) {
                    chatPanelManager.addSpan(mChannel.channelName, mChannel.channelID);
                }
            }
        }
        this.replyWKMsg = wkMsg;
        if (replyWKMsg != null) {
            chatPanelManager.showReplyLayout(replyWKMsg);
        }

    }

    @Override
    public void showEdit(WKMsg wkMsg) {
        boolean showDialog = false;
        WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel != null && mChannelMember != null) {
            if ((channel.forbidden == 1 && mChannelMember.role == WKChannelMemberRole.normal) || mChannelMember.forbiddenExpirationTime > 0) {
                showDialog = true;
            }
        }

        if (showDialog) {
            WKDialogUtils.getInstance().showSingleBtnDialog(this, "", getString(R.string.cannot_edit_msg), "", null);
            return;
        }
        this.replyWKMsg = null;
        this.editMsg = null;
        if (wkMsg != null && wkMsg.baseContentMsgModel instanceof WKTextContent) {
            this.editMsg = wkMsg;
            chatPanelManager.showEditLayout(wkMsg);
        }

    }

    @Override
    public void tipsMsg(String clientMsgNo) {

        isTipMessage = true;
        int index = -1;
        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
            if (chatAdapter.getData().get(i).wkMsg != null && TextUtils.equals(chatAdapter.getData().get(i).wkMsg.clientMsgNO, clientMsgNo)) {
                chatAdapter.getData().get(i).isShowTips = true;
                index = i;
                break;
            }
        }
        if (index != -1) {
            int lastItemPosition = linearLayoutManager.findLastVisibleItemPosition();
            int firstItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
            if (index < firstItemPosition || index > lastItemPosition) {
                linearLayoutManager.scrollToPositionWithOffset(index, AndroidUtilities.dp(70));
            }
            chatAdapter.notifyItemChanged(index);
        } else {
            WKMsg msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
            if (msg != null && msg.isDeleted == 0) {
                unreadStartMsgOrderSeq = 0;
                tipsOrderSeq = msg.orderSeq;
                getData(0, true, msg.orderSeq, true);
                isCanLoadMore = true;
            } else {
                showToast(R.string.cannot_tips_msg);
            }
        }

    }

    @Override
    public void setEditContent(String content) {

        int curPosition = chatPanelManager.getEditText().getSelectionStart();
        StringBuilder sb = new StringBuilder(Objects.requireNonNull(chatPanelManager.getEditText().getText()).toString());
        sb.insert(curPosition, content);
        chatPanelManager.getEditText().setText(MoonUtil.getEmotionContent(this, chatPanelManager.getEditText(), sb.toString()));
        chatPanelManager.getEditText().setSelection(curPosition + content.length());

    }

    @Override
    public AppCompatActivity getChatActivity() {
        return this;
    }

    @Override
    public WKMsg getReplyMsg() {
        return replyWKMsg;
    }

    @Override
    public void hideSoftKeyboard() {
        if (mHelper != null) {
            mHelper.hookSystemBackByPanelSwitcher();
        } else {
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
        }
    }

    @Override
    public ChatAdapter getChatAdapter() {
        return chatAdapter;
    }

    @Override
    public void sendCardMsg() {
        pendingCardGeneration = channelGeneration;
        pendingCardChannelId = channelId;
        pendingCardChannelType = channelType;

        Intent intent = new Intent(this, ChooseContactsActivity.class);
        intent.putExtra("chooseBack", true);
        intent.putExtra("singleChoose", true);
        if (channelType == WKChannelType.PERSONAL) {
            intent.putExtra("unVisibleUIDs", channelId);
        }
        chooseCardResultLac.launch(intent);
    }

    @Override
    public void chatRecyclerViewScrollToEnd() {
        if (isToEnd) {
            scrollToEnd();
        }

    }

    @Override
    public void deleteOperationMsg() {

        this.replyWKMsg = null;
        this.editMsg = null;
    }

    @Override
    public void onChatAvatarClick(String uid, boolean isLongClick) {
        chatPanelManager.chatAvatarClick(uid, isLongClick);
    }

    @Override
    public void onViewPicture(boolean isViewing) {
        isViewingPicture = isViewing;
    }

    @Override
    public void onMsgViewed(WKMsg wkMsg, int position) {
        if (wkMsg == null || shouldHideFromChatList(wkMsg)) return;
        if (!TextUtils.isEmpty(wkMsg.messageID) && !isTipMessage) {
            EndpointManager.getInstance().invoke("tip_pinned_message", wkMsg.messageID);
        }
        if (wkMsg.flame == 1 && wkMsg.viewed == 0 && wkMsg.type != WKContentType.WK_IMAGE && wkMsg.type != WKContentType.WK_VIDEO && wkMsg.type != WKContentType.WK_VOICE) {

            wkMsg.viewed = 1;
            wkMsg.viewedAt = WKTimeUtils.getInstance().getCurrentMills();
            chatAdapter.updateDeleteTimer(position);
            WKIM.getInstance().getMsgManager().updateViewedAt(1, wkMsg.viewedAt, wkMsg.clientMsgNO);
        }
        if (wkMsg.viewed == 0 && wkMsg.type == WKContentType.WK_TEXT) {
            wkMsg.viewed = 1;
        }

        if (wkMsg.remoteExtra != null && wkMsg.remoteExtra.readed == 0
                && wkMsg.setting != null && wkMsg.setting.receipt == 1
                && !TextUtils.isEmpty(wkMsg.messageID)
                && !TextUtils.isEmpty(wkMsg.fromUID) && !wkMsg.fromUID.equals(loginUID)) {
            addReadReceiptId(wkMsg.messageID);
        }
        boolean isResetRemind = false;
        if (WKReader.isNotEmpty(reminderList) && !TextUtils.isEmpty(wkMsg.messageID)) {
            for (int j = 0; j < reminderList.size(); j++) {
                if (reminderList.get(j).messageID.equals(wkMsg.messageID)) {
                    if (reminderList.get(j).done == 0) {
                        reminderIds.add(reminderList.get(j).reminderID);
                    }
                    reminderList.remove(j);
                    j = j - 1;
                    isResetRemind = true;
                }
            }
        }

        boolean isResetGroupApprove = false;
        if (WKReader.isNotEmpty(groupApproveList) && !TextUtils.isEmpty(wkMsg.messageID)) {
            for (int j = 0, size = groupApproveList.size(); j < size; j++) {
                if (groupApproveList.get(j).messageID.equals(wkMsg.messageID) && groupApproveList.get(j).done == 0) {
                    reminderIds.add(groupApproveList.get(j).reminderID);
                    groupApproveList.remove(j);
                    isResetGroupApprove = true;
                    break;
                }
            }
        }

        if (wkMsg.messageSeq > browseTo) {
            browseTo = wkMsg.messageSeq;
        }
        boolean isResetUnread = false;
        if (wkMsg.messageSeq > lastVisibleMsgSeq) {
            lastVisibleMsgSeq = wkMsg.messageSeq;
        }
        if (lastVisibleMsgSeq != 0) {
            long lastVisibleMsgOrderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(lastVisibleMsgSeq, channelId, channelType);
            if (lastVisibleMsgOrderSeq < unreadStartMsgOrderSeq) {
                lastVisibleMsgSeq = (int) WKIM.getInstance().getMsgManager().getReliableMessageSeq(unreadStartMsgOrderSeq);
                lastVisibleMsgSeq = lastVisibleMsgSeq - 1;
            }
        }
        if (redDot > 0) {
            if (lastVisibleMsgSeq != 0) {
                redDot = maxMsgSeq - lastVisibleMsgSeq;
            }
            if (redDot < 0) redDot = 0;
            isResetUnread = true;

        }

        if (isResetGroupApprove) {
            resetGroupApproveView();
        }
        if (isResetUnread) {
            showUnReadCountView();
        }
        if (isResetRemind) {
            resetRemindView();
        }
    }

    @Override
    public View getRecyclerViewLayout() {
        return wkVBinding.recyclerViewLayout;
    }

    @Override
    public boolean isShowChatActivity() {
        return isShowChatActivity;
    }

    @Override
    public void closeActivity() {
        finish();
    }

    @Override
    public void finish() {
        dismissCallPopup();
        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
        flushReadReceipts(channelId, channelType);
        if (!topicRoomClosing) markCurrentTopicRoomRead();
        unregisterChannelListeners();
        unregisterGlobalEndpoints();
        EndpointManager.getInstance().invoke("stop_screen_shot", this);
        super.finish();
    }

    @Override
    protected void onDestroy() {
        dismissCallPopup();
        // 必须在销毁输入面板前保存草稿。
        saveEditContent();
        flushReadReceipts(channelId, channelType);
        unregisterChannelListeners();
        unregisterGlobalEndpoints();
        cancelTypingExpiry(null);
        mainHandler.removeCallbacksAndMessages(null);
        PartnerPendingStore.removeListener(partnerPendingListener);
        PartnerLocalMessageStore.removeListener(partnerLocalMessageListener);
        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }
        asyncDisposables.clear();
        if (wkVBinding != null && wkVBinding.bottomView != null
                && floatingComposerLayoutChangeListener != null) {
            wkVBinding.bottomView.removeOnLayoutChangeListener(floatingComposerLayoutChangeListener);
            floatingComposerLayoutChangeListener = null;
        }
        if (chatPanelManager != null) chatPanelManager.onDestroy();
        ActManagerUtils.getInstance().removeActivity(this);
        MsgModel.getInstance().startCheckFlameMsgTimer();
        super.onDestroy();
    }

    private void updatePartnerPendingUi() {
        if (wkVBinding == null || wkVBinding.partnerPendingTip == null || wkVBinding.panelView == null) return;
        if (channelType != WKChannelType.PERSONAL) {
            wkVBinding.partnerPendingTip.setVisibility(View.GONE);
            wkVBinding.panelView.setAlpha(1f);
            wkVBinding.panelView.setOnTouchListener(null);
            return;
        }
        PartnerPendingStore.Entry state = PartnerPendingStore.get(channelId);
        if (state == null || !state.pending || !state.requester) {
            wkVBinding.partnerPendingTip.setVisibility(View.GONE);
            wkVBinding.panelView.setAlpha(1f);
            wkVBinding.panelView.setOnTouchListener(null);
            return;
        }
        if (state.replyObserved) {
            // A reply is already visible. Keep the hidden gateway state only to
            // bridge the short server webhook/whitelist race; the user must be able
            // to type and send immediately.
            wkVBinding.partnerPendingTip.setVisibility(View.GONE);
            wkVBinding.panelView.setAlpha(1f);
            wkVBinding.panelView.setOnTouchListener(null);
            return;
        }
        int remaining = state.remaining();
        wkVBinding.partnerPendingTip.setVisibility(View.VISIBLE);
        if (remaining <= 0) {
            wkVBinding.partnerPendingTip.setText(R.string.partner_pending_waiting);
            wkVBinding.panelView.setAlpha(0.55f);
            wkVBinding.panelView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    WKToastUtils.getInstance().showToast(getString(R.string.partner_pending_waiting));
                }
                return true;
            });
        } else {
            wkVBinding.partnerPendingTip.setText(getString(R.string.partner_pending_remaining, remaining));
            wkVBinding.panelView.setAlpha(1f);
            wkVBinding.panelView.setOnTouchListener(null);
        }
    }

    private void saveEditContent() {
        saveEditContent(true);
    }

    private void saveEditContent(boolean syncRemote) {
        if (chatPanelManager == null || TextUtils.isEmpty(channelId)) return;
        long keepMsgSeq = 0;
        int offsetY = 0;
        if (chatAdapter != null && linearLayoutManager != null && WKReader.isNotEmpty(chatAdapter.getData())) {
            int firstItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
            int endItemPosition = linearLayoutManager.findLastVisibleItemPosition();
            if (firstItemPosition != RecyclerView.NO_POSITION
                    && endItemPosition != RecyclerView.NO_POSITION
                    && endItemPosition != chatAdapter.getData().size() - 1) {
                WKMsg msg = chatAdapter.getFirstVisibleItem(firstItemPosition);
                if (msg != null) {
                    keepMsgSeq = msg.messageSeq;
                    int index = chatAdapter.getFirstVisibleItemIndex(firstItemPosition);
                    View view = linearLayoutManager.findViewByPosition(index);
                    if (view != null) offsetY = view.getTop();
                }
            }
        }
        CharSequence editable = chatPanelManager.getEditText() == null ? null : chatPanelManager.getEditText().getText();
        String content = editable == null ? "" : editable.toString();
        MsgModel.getInstance().updateCoverExtraLocal(
                channelId, channelType, browseTo, keepMsgSeq, offsetY, content);
        if (syncRemote) {
            MsgModel.getInstance().clearUnread(channelId, channelType, redDot, null);
            MsgModel.getInstance().syncCoverExtraRemote(
                    channelId, channelType, browseTo, keepMsgSeq, offsetY, content);
            MsgModel.getInstance().deleteFlameMsg();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return setBackListener();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        // 系统后台杀进程不保证回调 onDestroy，先把草稿和浏览位置落本地。
        saveEditContent(false);
        super.onStop();
        isShowChatActivity = false;
        WKUIKitApplication.getInstance().chattingChannelID = "";
        isUploadReadMsg = false;
        if (!topicRoomClosing) markCurrentTopicRoomRead();
        WKPlayVoiceUtils.getInstance().stopPlay();
        MsgModel.getInstance().doneReminder(reminderIds);
        EndpointManager.getInstance().invoke("stop_screen_shot", this);
    }


    ActivityResultLauncher<Intent> previewNewImgResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        final long requestGeneration = pendingPreviewGeneration;
        final String targetChannelId = pendingPreviewChannelId;
        final byte targetChannelType = pendingPreviewChannelType;
        final WKMsg replySnapshot = pendingPreviewReplyMsg;
        pendingPreviewGeneration = -1L;
        pendingPreviewChannelId = "";
        pendingPreviewReplyMsg = null;

        if (result.getData() == null || result.getResultCode() != Activity.RESULT_OK) return;
        String path = result.getData().getStringExtra("path");
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(targetChannelId)) return;

        if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)
                && replyWKMsg == replySnapshot) {
            replyWKMsg = null;
        }
        sendImagePathAsync(path, targetChannelId, targetChannelType, replySnapshot);
    });

    ActivityResultLauncher<Intent> chooseCardResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        final long requestGeneration = pendingCardGeneration;
        final String targetChannelId = pendingCardChannelId;
        final byte targetChannelType = pendingCardChannelType;
        pendingCardGeneration = -1L;
        pendingCardChannelId = "";

        if (result.getData() == null || result.getResultCode() != Activity.RESULT_OK
                || TextUtils.isEmpty(targetChannelId)) {
            return;
        }
        String uid = result.getData().getStringExtra("uid");
        if (TextUtils.isEmpty(uid)) return;

        WKChannel cardChannel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        WKChannel targetChannel = WKIM.getInstance().getChannelManager().getChannel(targetChannelId, targetChannelType);
        if (cardChannel == null || targetChannel == null) {
            if (isCurrentSession(requestGeneration, targetChannelId, targetChannelType)) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.network_error_tips));
            }
            if (cardChannel == null) {
                WKIM.getInstance().getChannelManager().fetchChannelInfo(uid, WKChannelType.PERSONAL);
            }
            return;
        }

        WKCardContent cardContent = new WKCardContent();
        cardContent.name = cardChannel.channelName;
        cardContent.uid = cardChannel.channelID;
        if (cardChannel.remoteExtraMap != null
                && cardChannel.remoteExtraMap.containsKey(WKChannelExtras.vercode)) {
            cardContent.vercode = (String) cardChannel.remoteExtraMap.get(WKChannelExtras.vercode);
        }
        List<WKMessageContent> messageContentList = new ArrayList<>();
        messageContentList.add(cardContent);
        List<WKChannel> targetChannels = new ArrayList<>();
        targetChannels.add(targetChannel);
        WKUIKitApplication.getInstance().showChatConfirmDialog(
                ChatActivity.this,
                targetChannels,
                messageContentList,
                (list1, messageContentList1) -> sendMsg(cardContent, targetChannelId, targetChannelType)
        );
    });

    private void sendMsgInserted(WKMsg msg) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> sendMsgInserted(msg));
            return;
        }
        if (shouldHideFromChatList(msg)) return;
        if (deferAdapterMutationIfComputing(() -> sendMsgInserted(msg))) return;
        if (msg.channelType == channelType && TextUtils.equals(msg.channelID, channelId)) {
            DeepSeekAssistant.bindPendingReplyToMessage(this, msg);
            if (msg.orderSeq > maxMsgOrderSeq) {
                maxMsgOrderSeq = msg.orderSeq;
            }

            // 对方“正在输入”必须始终留在列表末尾。旧流程先追加日期分割线再按
            // lastMsgIsTyping() 回退索引，在跨天时会形成 typing -> 消息 -> 日期的错序。
            WKUIChatMsgItemEntity trailingTyping = detachTrailingTypingItem();
            addTimeMsg(msg.timestamp);

            int previousIndex = chatAdapter.getData().size() - 1;
            WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(
                    this, msg, count, showNickName, chatAdapter.isShowChooseItem());
            if (previousIndex >= 0) {
                WKUIChatMsgItemEntity previous = chatAdapter.getData().get(previousIndex);
                if (previous != null && previous.wkMsg != null) {
                    previous.nextMsg = msg;
                    itemEntity.previousMsg = previous.wkMsg;
                }
            }
            chatAdapter.addData(itemEntity);
            notifyMessageAppearance(previousIndex);
            restoreTrailingTypingItem(trailingTyping);

            if (isToEnd) {
                scrollToEnd();
            }
            isToEnd = true;
        }
    }

    private void receivedMessages(List<WKMsg> list) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            final List<WKMsg> snapshot = list == null ? new ArrayList<>() : new ArrayList<>(list);
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> receivedMessages(snapshot));
            return;
        }
        if (WKReader.isEmpty(list)) return;
        RecyclerView recyclerView = wkVBinding == null ? null : wkVBinding.recyclerView;
        if (recyclerView != null && recyclerView.isComputingLayout()) {
            final List<WKMsg> deferredList = new ArrayList<>(list);
            if (deferAdapterMutationIfComputing(() -> receivedMessages(deferredList))) return;
        }
        for (WKMsg msg : list) {
            // RTC 已由 wkrtc 全局监听处理，本页只过滤，不重复派发。
            if (shouldHideFromChatList(msg)) {
                continue;
            }
            if (msg.remoteExtra != null && msg.remoteExtra.readedCount == 0) {
                msg.remoteExtra.unreadCount = count - 1;
            }
            if (!TextUtils.equals(msg.channelID, channelId) || msg.channelType != channelType
                    || chatAdapter.isExist(msg.clientMsgNO, msg.messageID)) {
                continue;
            }

            if (!isCanLoadMore) {
                // 只有新收到的文字消息会改变“最后一条快捷翻译消息”，其它消息不做反向扫描。
                int oldLatestReceivedTextIndex = isReceivedTextMsgForInlineTranslate(msg)
                        ? findLatestReceivedTextMsgIndex() : -1;

                WKUIChatMsgItemEntity trailingTyping = null;
                if (!chatAdapter.getData().isEmpty()) {
                    WKUIChatMsgItemEntity last = chatAdapter.getData().get(chatAdapter.getData().size() - 1);
                    if (last != null && last.wkMsg != null && last.wkMsg.type == WKContentType.typing) {
                        // In a group, a message from another member must not clear the person who is
                        // still typing. Move that temporary row behind the real message and preserve
                        // its original expiry. A real message from the same sender ends the state.
                        if (TextUtils.equals(last.wkMsg.fromUID, msg.fromUID)) {
                            removeTrailingTypingItem(true);
                        } else {
                            trailingTyping = detachTrailingTypingItem();
                        }
                    }
                }

                WKMsg timeMsg = addTimeMsg(msg.timestamp);
                WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(
                        this, msg, count, showNickName, chatAdapter.isShowChooseItem());
                if (timeMsg != null && chatAdapter.getData().size() > 1) {
                    chatAdapter.getData().get(chatAdapter.getData().size() - 2).nextMsg = timeMsg;
                }
                int previousMsgIndex = -1;
                if (timeMsg == null) {
                    if (WKReader.isNotEmpty(chatAdapter.getData())) {
                        previousMsgIndex = chatAdapter.getData().size() - 1;
                        itemEntity.previousMsg = chatAdapter.getData().get(previousMsgIndex).wkMsg;
                        chatAdapter.getData().get(previousMsgIndex).nextMsg = itemEntity.wkMsg;
                    }
                } else {
                    itemEntity.previousMsg = timeMsg;
                    if (WKReader.isNotEmpty(chatAdapter.getData())) {
                        previousMsgIndex = chatAdapter.getData().size() - 1;
                    }
                }

                if (!isShowHistory && redDot == 0 && itemEntity.wkMsg.flame == 1
                        && itemEntity.wkMsg.type != WKContentType.WK_VOICE
                        && itemEntity.wkMsg.type != WKContentType.WK_IMAGE
                        && itemEntity.wkMsg.type != WKContentType.WK_VIDEO) {
                    itemEntity.wkMsg.viewed = 1;
                    itemEntity.wkMsg.viewedAt = WKTimeUtils.getInstance().getCurrentMills();
                    WKIM.getInstance().getMsgManager().updateViewedAt(
                            1, itemEntity.wkMsg.viewedAt, itemEntity.wkMsg.clientMsgNO);
                }

                WKPlaySound.getInstance().playInMsg(R.raw.sound_in);
                chatAdapter.addData(itemEntity);
                restoreTrailingTypingItem(trailingTyping);
                if (msg.messageSeq > maxMsgSeq) maxMsgSeq = msg.messageSeq;
                if (msg.orderSeq > maxMsgOrderSeq) maxMsgOrderSeq = msg.orderSeq;
                notifyMessageAppearance(previousMsgIndex);
                refreshOldInlineTranslateButton(oldLatestReceivedTextIndex, msg);
            }

            if (isShowHistory || redDot > 0) {
                redDot += 1;
                showUnReadCountView();
            } else {
                scrollToEnd();
                if (msg.setting != null && msg.setting.receipt == 1
                        && !TextUtils.isEmpty(msg.messageID)) {
                    addReadReceiptId(msg.messageID);
                }
            }
        }
    }

    /**
     * The typing timeout belongs to the CMD receiver, not RecyclerView binding. Every real typing
     * CMD resets the full timeout; ordinary rebinds never affect the deadline.
     */
    private void touchTypingExpiry(@NonNull WKMsg typingMsg) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> touchTypingExpiry(typingMsg));
            return;
        }

        cancelTypingExpiry(null);
        final long version = typingExpiryVersion;
        final WeakReference<WKMsg> messageRef = new WeakReference<>(typingMsg);
        typingExpiryMessageRef = messageRef;
        typingExpiryRunnable = () -> runTypingExpiryWhenRecyclerSafe(messageRef, version);
        mainHandler.postDelayed(typingExpiryRunnable, TYPING_TIMEOUT_MS);
    }

    /** Cancels the active timeout. If expectedMsg is non-null, only that exact typing row matches. */
    private void cancelTypingExpiry(@Nullable WKMsg expectedMsg) {
        if (!isMainThread()) {
            mainHandler.post(() -> cancelTypingExpiry(expectedMsg));
            return;
        }
        WKMsg active = typingExpiryMessageRef == null ? null : typingExpiryMessageRef.get();
        if (expectedMsg != null && active != expectedMsg) return;
        typingExpiryVersion++;
        if (typingExpiryRunnable != null) {
            mainHandler.removeCallbacks(typingExpiryRunnable);
        }
        typingExpiryRunnable = null;
        typingExpiryMessageRef = null;
    }

    private void runTypingExpiryWhenRecyclerSafe(
            @NonNull WeakReference<WKMsg> expectedMessageRef,
            long expectedVersion
    ) {
        if (expectedVersion != typingExpiryVersion) return;
        WKMsg expectedMessage = expectedMessageRef.get();
        if (expectedMessage == null || chatAdapter == null || isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            cancelTypingExpiry(expectedMessage);
            return;
        }

        if (deferAdapterMutationIfComputing(
                () -> runTypingExpiryWhenRecyclerSafe(expectedMessageRef, expectedVersion))) {
            return;
        }
        removeTypingMessageNow(expectedMessage, expectedVersion);
    }

    /**
     * Removes the exact trailing typing object and an optional date divider created only for it.
     * Data is mutated directly so BRVAH does not dispatch its broad notifyItemRangeChanged fallback.
     */
    private void removeTypingMessageNow(@NonNull WKMsg expectedMessage, long expectedVersion) {
        if (expectedVersion != typingExpiryVersion || chatAdapter == null) return;
        List<WKUIChatMsgItemEntity> data = chatAdapter.getData();
        int typingIndex = -1;
        for (int i = data.size() - 1; i >= 0; i--) {
            WKUIChatMsgItemEntity item = data.get(i);
            if (item != null && item.wkMsg == expectedMessage
                    && item.wkMsg.type == WKContentType.typing) {
                typingIndex = i;
                break;
            }
        }

        cancelTypingExpiry(expectedMessage);
        if (typingIndex < 0) return;

        int precedingTimeIndex = typingIndex - 1;
        WKUIChatMsgItemEntity preceding = precedingTimeIndex >= 0
                ? data.get(precedingTimeIndex) : null;
        boolean dropTimeDivider = typingIndex == data.size() - 1
                && preceding != null
                && preceding.wkMsg != null
                && preceding.wkMsg.type == WKContentType.msgPromptTime;

        int removeFrom = dropTimeDivider ? precedingTimeIndex : typingIndex;
        int removeCount = dropTimeDivider ? 2 : 1;
        for (int i = 0; i < removeCount; i++) {
            data.remove(removeFrom);
        }

        int previousIndex = removeFrom - 1;
        int nextIndex = removeFrom;
        WKUIChatMsgItemEntity previous = previousIndex >= 0 && previousIndex < data.size()
                ? data.get(previousIndex) : null;
        WKUIChatMsgItemEntity next = nextIndex >= 0 && nextIndex < data.size()
                ? data.get(nextIndex) : null;
        if (previous != null) previous.nextMsg = next == null ? null : next.wkMsg;
        if (next != null) next.previousMsg = previous == null ? null : previous.wkMsg;

        int header = chatAdapter.getHeaderLayoutCount();
        chatAdapter.notifyItemRangeRemoved(removeFrom + header, removeCount);
        if (previousIndex >= 0 && previousIndex < data.size()) {
            chatAdapter.notifyItemChanged(previousIndex + header);
        }
        if (nextIndex >= 0 && nextIndex < data.size()) {
            chatAdapter.notifyItemChanged(nextIndex + header);
        }
    }

    private void typing(WKCMD wkCmd) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> typing(wkCmd));
            return;
        }
        if (wkCmd == null || wkCmd.paramJsonObject == null || redDot > 0 || chatAdapter == null) return;
        if (deferAdapterMutationIfComputing(() -> typing(wkCmd))) return;
        String channel_id = wkCmd.paramJsonObject.optString("channel_id");
        byte channel_type = (byte) wkCmd.paramJsonObject.optInt("channel_type");
        String from_uid = wkCmd.paramJsonObject.optString("from_uid");
        String from_name = wkCmd.paramJsonObject.optString("from_name");
        if (TextUtils.isEmpty(channel_id) || TextUtils.isEmpty(from_uid)) return;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(from_uid, WKChannelType.PERSONAL);
        if (channel == null) {
            channel = new WKChannel(from_uid, WKChannelType.PERSONAL);
            channel.channelName = from_name;
        }
        int isRobot = channel.robot;
        if (!TextUtils.equals(channelId, channel_id) || channelType != channel_type
                || TextUtils.equals(from_uid, loginUID)) {
            return;
        }

        WKChannelMember memberOfFrom = null;
        if (channelType == WKChannelType.GROUP && isRobot == 0) {
            memberOfFrom = WKIM.getInstance().getChannelMembersManager()
                    .getMember(channelId, channelType, from_uid);
            if (memberOfFrom == null || memberOfFrom.isDeleted == 1) return;
        }

        if (!chatAdapter.getData().isEmpty()) {
            WKUIChatMsgItemEntity last = chatAdapter.getData().get(chatAdapter.getData().size() - 1);
            if (last != null && last.wkMsg != null && last.wkMsg.type == WKContentType.typing) {
                last.wkMsg.setFrom(channel);
                last.wkMsg.fromUID = from_uid;
                last.wkMsg.setMemberOfFrom(memberOfFrom);
                int typingDataIndex = chatAdapter.getData().size() - 1;
                chatAdapter.notifyItemChanged(typingDataIndex + chatAdapter.getHeaderLayoutCount());
                touchTypingExpiry(last.wkMsg);
                return;
            }
        }

        addTimeMsg(WKTimeUtils.getInstance().getCurrentSeconds());
        int index = chatAdapter.getData().size() - 1;
        if (chatAdapter.lastMsgIsTyping()) index--;
        if (index < 0) index = 0;

        WKUIChatMsgItemEntity typingItem = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
        typingItem.wkMsg.channelType = channelType;
        typingItem.wkMsg.channelID = channelId;
        typingItem.wkMsg.type = WKContentType.typing;
        typingItem.wkMsg.setFrom(channel);
        typingItem.showNickName = showNickName;
        typingItem.wkMsg.fromUID = from_uid;
        WKChannelMember displayMember = memberOfFrom;
        if (displayMember == null) {
            displayMember = new WKChannelMember();
            displayMember.memberUID = from_uid;
            displayMember.channelID = channelId;
            displayMember.channelType = channelType;
            displayMember.memberName = channel.channelName;
            displayMember.memberRemark = channel.channelRemark;
        }
        typingItem.wkMsg.setMemberOfFrom(displayMember);
        typingItem.previousMsg = chatAdapter.getLastMsg();
        chatAdapter.addData(typingItem);
        if (index >= 0 && index < chatAdapter.getData().size()) {
            chatAdapter.getData().get(index).nextMsg = typingItem.wkMsg;
        }
        touchTypingExpiry(typingItem.wkMsg);
        notifyMessageAppearance(index);

        if (!isShowHistory && !isCanLoadMore) {
            scrollToEnd();
        }
    }

    private void refreshMsg(WKMsg wkMsg) {
        if (!isMainThread()) {
            final long requestGeneration = channelGeneration;
            final String requestChannelId = channelId;
            final byte requestChannelType = channelType;
            postToMainForSession(requestGeneration, requestChannelId, requestChannelType,
                    () -> refreshMsg(wkMsg));
            return;
        }
        if (wkMsg == null) return;
        if (deferAdapterMutationIfComputing(() -> refreshMsg(wkMsg))) return;
        if (shouldHideFromChatList(wkMsg)) {
            removeMsg(wkMsg);
            return;
        }
        WKIMUtils.getInstance().resetMsgProhibitWord(wkMsg);
        List<WKUIChatMsgItemEntity> list = chatAdapter.getData();
        chatAdapter.refreshReplyMsg(wkMsg);
        for (int i = 0, size = list.size(); i < size; i++) {
            WKUIChatMsgItemEntity entity = list.get(i);
            if (entity == null || entity.wkMsg == null) {
                continue;
            }
            boolean isNotify = false;
            if (isSameMessage(entity.wkMsg, wkMsg)) {
                WKMsg oldMsg = entity.wkMsg;
                final int previousStatus = oldMsg.status;
                final boolean wasNoRelationFailure = previousStatus == WKSendMsgResult.no_relation
                        || previousStatus == WKSendMsgResult.not_on_white_list;
                final boolean isNoRelationFailure = wkMsg.status == WKSendMsgResult.no_relation
                        || wkMsg.status == WKSendMsgResult.not_on_white_list;
                final boolean justBecameNoRelation = !wasNoRelationFailure && isNoRelationFailure;
                boolean wasLatestReceivedText = isReceivedTextMsgForInlineTranslate(oldMsg)
                        && i == findLatestReceivedTextMsgIndex();
                if (wkMsg.messageSeq > maxMsgSeq) {
                    maxMsgSeq = wkMsg.messageSeq;
                }
                if (wkMsg.messageSeq > lastVisibleMsgSeq) {
                    lastVisibleMsgSeq = wkMsg.messageSeq;
                }

                if (oldMsg.remoteExtra == null && wkMsg.remoteExtra != null) {
                    oldMsg.remoteExtra = wkMsg.remoteExtra;
                }

                boolean isResetStatus = false;
                boolean isResetListener = false;
                boolean isResetData = false;
                boolean isResetReaction = false;

                if (oldMsg.remoteExtra != null && wkMsg.remoteExtra != null) {
                    if (oldMsg.remoteExtra.revoke != wkMsg.remoteExtra.revoke) {
                        isNotify = true;
                    }
                    oldMsg.remoteExtra.revoke = wkMsg.remoteExtra.revoke;
                    oldMsg.remoteExtra.revoker = wkMsg.remoteExtra.revoker;

                    if (oldMsg.status != wkMsg.status
                            || (oldMsg.remoteExtra.readedCount != wkMsg.remoteExtra.readedCount && oldMsg.remoteExtra.readedCount == 0)
                            || oldMsg.remoteExtra.editedAt != wkMsg.remoteExtra.editedAt) {
                        entity.isUpdateStatus = true;
                        isResetStatus = true;
                    }
                    if (oldMsg.remoteExtra.isPinned != wkMsg.remoteExtra.isPinned) {
                        isResetStatus = true;
                    }

                    if (hideChannelAllPinnedMessage == 0) {
                        entity.isPinned = wkMsg.remoteExtra.isPinned;
                    } else {
                        entity.isPinned = 0;
                    }
                    if (oldMsg.remoteExtra.readedCount != wkMsg.remoteExtra.readedCount && !isResetStatus) {
                        isResetListener = true;
                    }
                    oldMsg.remoteExtra.isPinned = wkMsg.remoteExtra.isPinned;
                    oldMsg.remoteExtra.readed = wkMsg.remoteExtra.readed;
                    oldMsg.remoteExtra.readedCount = wkMsg.remoteExtra.readedCount;
                    oldMsg.remoteExtra.needUpload = wkMsg.remoteExtra.needUpload;
                    if (oldMsg.remoteExtra.readedCount == 0) {
                        oldMsg.remoteExtra.unreadCount = count - 1;
                    } else {
                        oldMsg.remoteExtra.unreadCount = wkMsg.remoteExtra.unreadCount;
                    }
                    if ((TextUtils.isEmpty(oldMsg.remoteExtra.contentEdit) && !TextUtils.isEmpty(wkMsg.remoteExtra.contentEdit))
                            || (!TextUtils.isEmpty(oldMsg.remoteExtra.contentEdit)
                            && !TextUtils.isEmpty(wkMsg.remoteExtra.contentEdit)
                            && !TextUtils.equals(oldMsg.remoteExtra.contentEdit, wkMsg.remoteExtra.contentEdit))) {
                        oldMsg.remoteExtra.editedAt = wkMsg.remoteExtra.editedAt;
                        oldMsg.remoteExtra.contentEdit = wkMsg.remoteExtra.contentEdit;
                        oldMsg.remoteExtra.contentEditMsgModel = wkMsg.remoteExtra.contentEditMsgModel;
                        entity.isUpdateStatus = true;
                        entity.formatSpans(ChatActivity.this, oldMsg);
                        isResetData = true;
                    }
                } else if (oldMsg.status != wkMsg.status) {
                    entity.isUpdateStatus = true;
                    isResetStatus = true;
                }

                oldMsg.voiceStatus = wkMsg.voiceStatus;
                if (oldMsg.status != WKSendMsgResult.send_success && wkMsg.status == WKSendMsgResult.send_success) {
                    WKPlaySound.getInstance().playOutMsg(R.raw.sound_out);
                }

                oldMsg.isDeleted = wkMsg.isDeleted;
                oldMsg.messageID = wkMsg.messageID;
                oldMsg.messageSeq = wkMsg.messageSeq;
                oldMsg.orderSeq = wkMsg.orderSeq;
                if ((wkMsg.localExtraMap != null && !wkMsg.localExtraMap.isEmpty())) {
                    isNotify = true;
                }
                if (isRefreshReaction(oldMsg.reactionList, wkMsg.reactionList)) {
                    isResetReaction = true;
                }
                oldMsg.localExtraMap = wkMsg.localExtraMap;
                oldMsg.content = wkMsg.content;
                oldMsg.reactionList = wkMsg.reactionList;
                oldMsg.baseContentMsgModel = wkMsg.baseContentMsgModel;
                oldMsg.status = wkMsg.status;
                if (isNotify) {
                    EndpointManager.getInstance().invoke("stop_reaction_animation", null);
                    chatAdapter.notifyItemChanged(i);
                } else {
                    if (isResetStatus) {
                        chatAdapter.notifyStatus(i);
                    }
                    if (isResetListener) {
                        chatAdapter.notifyListener(i);
                    }
                    if (isResetData) {
                        chatAdapter.notifyData(i);
                    }
                    if (isResetReaction) {
                        entity.isRefreshReaction = true;
                        chatAdapter.notifyItemChanged(i, entity);
                    }
                }

                if (oldMsg.remoteExtra != null && oldMsg.remoteExtra.revoke == 1) {
                    if (wasLatestReceivedText) {
                        refreshLatestInlineTranslateButton();
                    }
                    final long requestGeneration = channelGeneration;
                    final String requestChannelId = channelId;
                    final byte requestChannelType = channelType;
                    final WKMsg refreshedMsg = wkMsg;
                    mainHandler.postDelayed(() -> {
                        if (!isCurrentSession(requestGeneration, requestChannelId, requestChannelType)
                                || chatAdapter == null) {
                            return;
                        }
                        int currentIndex = -1;
                        for (int index = 0; index < chatAdapter.getData().size(); index++) {
                            WKUIChatMsgItemEntity item = chatAdapter.getData().get(index);
                            if (item != null && item.wkMsg != null && isSameMessage(item.wkMsg, refreshedMsg)) {
                                currentIndex = index;
                                break;
                            }
                        }
                        if (currentIndex < 0) return;

                        int previousIndex = currentIndex - 1;
                        int nextIndex = currentIndex + 1;
                        if (previousIndex >= 0 && previousIndex < chatAdapter.getData().size()) {
                            WKUIChatMsgItemEntity previous = chatAdapter.getData().get(previousIndex);
                            if (hasRemoteExtra(previous) && previous.wkMsg.remoteExtra.revoke == 0) {
                                chatAdapter.notifyItemChanged(previousIndex);
                            }
                        }
                        if (nextIndex >= 0 && nextIndex < chatAdapter.getData().size()) {
                            WKUIChatMsgItemEntity next = chatAdapter.getData().get(nextIndex);
                            if (hasRemoteExtra(next) && next.wkMsg.remoteExtra.revoke == 0) {
                                chatAdapter.notifyItemChanged(nextIndex);
                            }
                        }
                    }, 200);
                }

                if (justBecameNoRelation && channelType == WKChannelType.PERSONAL) {
                    if (UserUtils.getInstance().checkBlacklist(channelId)) {
                        return;
                    }
                    WKMsg noRelationMsg = new WKMsg();
                    noRelationMsg.channelID = channelId;
                    noRelationMsg.channelType = channelType;
                    noRelationMsg.type = WKContentType.noRelation;
                    long tempOrderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(0, wkMsg.channelID, wkMsg.channelType);
                    noRelationMsg.orderSeq = tempOrderSeq + 1;
                    noRelationMsg.status = WKSendMsgResult.send_success;

                    WKUIChatMsgItemEntity trailingTyping = detachTrailingTypingItem();
                    int previousIndex = chatAdapter.getData().size() - 1;
                    WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(
                            this, noRelationMsg, count, showNickName, chatAdapter.isShowChooseItem());
                    if (previousIndex >= 0) {
                        WKUIChatMsgItemEntity previous = chatAdapter.getData().get(previousIndex);
                        if (previous != null && previous.wkMsg != null) {
                            previous.nextMsg = noRelationMsg;
                            itemEntity.previousMsg = previous.wkMsg;
                        }
                    }
                    chatAdapter.addData(itemEntity);
                    notifyMessageAppearance(previousIndex);
                    restoreTrailingTypingItem(trailingTyping);
                    if (isToEnd) {
                        scrollToEnd();
                    }
                    WKIM.getInstance().getMsgManager().saveAndUpdateConversationMsg(noRelationMsg, false);
                }
                break;
            }
        }
    }

    private WKMsg getSpanEmptyMsg() {
        WKMsg msg = new WKMsg();
        msg.timestamp = 0;
        msg.messageSeq = getTopPinViewHeight();
        msg.type = WKContentType.spanEmptyView;
        return msg;
    }

    private boolean isAddedSpanEmptyView() {
        return WKReader.isNotEmpty(chatAdapter.getData()) && chatAdapter.getData().get(0).wkMsg != null && chatAdapter.getData().get(0).wkMsg.type == WKContentType.spanEmptyView;
    }
}
