package com.chat.uikit.chat

import android.Manifest
import android.app.AlertDialog
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.emoji.EmojiAdapter
import com.chat.base.emoji.EmojiEntry
import com.chat.base.emoji.EmojiManager
import com.chat.base.emoji.MoonUtil
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatChooseContacts
import com.chat.base.endpoint.entity.ChatToolBarMenu
import com.chat.base.endpoint.entity.ChooseChatMenu
import com.chat.base.endpoint.entity.InitInputPanelMenu
import com.chat.base.endpoint.entity.SearchChatEditStickerMenu
import com.chat.base.endpoint.entity.SendTextMenu
import com.chat.base.entity.BottomSheetItem
import com.chat.base.glide.GlideUtils
import com.chat.base.msg.IConversationContext
import com.chat.base.msg.model.WKGifContent
import com.chat.base.msgitem.WKChannelMemberRole
import com.chat.base.msgitem.WKContentType
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.ui.components.ContactEditText
import com.chat.base.ui.components.SeekBarView
import com.chat.base.ui.components.SwitchView
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.ImageUtils
import com.chat.base.utils.LayoutHelper
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.utils.StringUtils
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKPermissions
import com.chat.base.utils.WKTimeUtils
import com.chat.base.utils.WKToastUtils
import com.chat.base.utils.singleclick.SingleClickUtil
import com.chat.base.views.CommonAnim
import com.chat.base.views.FullyGridLayoutManager
import com.chat.base.views.NoEventRecycleView
import com.chat.uikit.R
import com.chat.uikit.chat.adapter.WKChatToolBarAdapter
import com.chat.uikit.chat.face.WKVoiceViewManager
import com.chat.uikit.chat.manager.SendMsgEntity
import com.chat.uikit.chat.manager.WKSendMsgUtils
import com.chat.uikit.chat.msgmodel.WKMultiForwardContent
import com.chat.uikit.contacts.service.FriendModel
import com.chat.uikit.group.GroupMemberEntity
import com.chat.uikit.group.RemindMemberAdapter
import com.chat.uikit.group.service.GroupModel
import com.chat.uikit.message.MsgModel
import com.chat.uikit.robot.RobotGIFAdapter
import com.chat.uikit.robot.RobotMenuAdapter
import com.chat.uikit.robot.entity.WKRobotEntity
import com.chat.uikit.robot.entity.WKRobotGIFEntity
import com.chat.uikit.robot.entity.WKRobotInlineQueryResult
import com.chat.uikit.robot.entity.WKRobotMenuEntity
import com.chat.uikit.robot.service.WKRobotModel
import com.chat.uikit.user.ProfileNavigator
import com.chat.uikit.utils.mentionDisplay
import com.chat.translate.api.ChatBeforeSendRequest
import com.chat.translate.api.WkTranslateBridge
import com.chat.deepseek.DeepSeekAssistant
import com.effective.android.panel.PanelSwitchHelper
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelExtras
import com.xinbida.wukongim.entity.WKChannelMember
import com.xinbida.wukongim.entity.WKChannelStatus
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMentionInfo
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.entity.WKSendOptions
import com.xinbida.wukongim.msgmodel.WKMessageContent
import com.xinbida.wukongim.msgmodel.WKMsgEntity
import com.xinbida.wukongim.msgmodel.WKTextContent
import com.xinbida.wukongim.msgmodel.WKReply
import org.json.JSONObject
import java.util.Locale
import java.util.ArrayDeque
import java.util.Timer
import java.util.TimerTask
import kotlin.math.min
import androidx.core.view.isGone
import kotlinx.coroutines.runBlocking


class TranslateStatusView(context: android.content.Context) : View(context) {
    var active: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(AndroidUtilities.dp(10f), AndroidUtilities.dp(24f))
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f + AndroidUtilities.dp(3.2f).toFloat()

        if (active) {
            // 低饱和翡翠绿光晕 + 实心点，状态明确但不会像荧光灯一样抢眼。
            dotPaint.color = android.graphics.Color.rgb(43, 138, 120)
            dotPaint.alpha = 48
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(4.2f).toFloat(), dotPaint)
            dotPaint.alpha = 255
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(2.45f).toFloat(), dotPaint)
        } else {
            dotPaint.color = android.graphics.Color.rgb(148, 163, 184)
            dotPaint.alpha = 190
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(2.15f).toFloat(), dotPaint)
        }
    }
}


class ChatPanelManager(
    val helper: PanelSwitchHelper,
    val parentView: View,
    private val moreLayout: FrameLayout,
    private val followScrollLayout: FrameLayout,
    val iConversationContext: IConversationContext,
    val resetTitleViewListener: () -> Unit,
    val showNewImageListener: (path: String) -> Unit,
) {
    private class EmojiEndpointRegistration(var active: Boolean = true)

    companion object {
        private val EMOJI_ENDPOINT_LOCK = Any()
        private val EMOJI_ENDPOINT_STACK = ArrayList<EmojiEndpointRegistration>()
        private val DIRECT_VOICE_LOCK = Any()
        private var directVoiceOwner: ChatPanelManager? = null
    }

    private var emojiEndpointRegistration: EmojiEndpointRegistration? = null
    // SDK listener keys are global identifiers. A fixed "InputPanel" key makes two ChatActivity
    // instances overwrite/remove each other, so every panel owns a unique key.
    private val eventKey = "InputPanel@" + Integer.toHexString(System.identityHashCode(this))
    private val robotListenerKey = "$eventKey@robot"
    private val loginUID = WKConfig.getInstance().uid
    private var activeChannelId: String = iConversationContext.chatChannelInfo.channelID
    private var activeChannelType: Byte = iConversationContext.chatChannelInfo.channelType
    private var activeSessionGeneration: Long = 1L
    private var refreshListenersRegistered = false
    private var robotQueryToken = 0L
    @Volatile private var destroyed = false
    private var isShowSendBtn: Boolean = false
    private var flame = 0
    private var lastInputTime: Long = 0
    private var inlineQueryOffset: String = ""
    private var searchKey: String = ""
    private var username: String = ""
    private val maxLength = 300

    private val keyAiSourceLang = "chat_ai_source_lang"
    private val keyAiTargetLang = "chat_ai_target_lang"
    private val keyAiSendTranslate = "chat_ai_send_translate"
    private val keyWingmanEnabled = "chat_ai_wingman_enabled"
    private val langNames = arrayOf("自动检测", "中文", "မြန်မာစာ", "English", "日本語", "한국어", "ภาษาไทย", "Tiếng Việt", "Русский")
    private var voiceDownX = 0f
    private var voiceCanceling = false
    private var voiceHolding = false
    // WKVoiceViewManager 是全局单例，只能取消本面板自己启动的录音，
    // 否则底层 ChatActivity 销毁时可能误停顶部页面正在录制的语音。
    private var directVoiceStarted = false
    private var voiceForwardTarget: View? = null
    private var voiceLastMoveEvent: MotionEvent? = null
    private val voiceCancelDistance = AndroidUtilities.dp(70f).toFloat()
    private var voiceRecordOverlay: LinearLayout? = null
    private var voiceRecordDotTv: AppCompatTextView? = null
    private var voiceRecordTimeTv: AppCompatTextView? = null
    private var voiceRecordCancelTv: AppCompatTextView? = null
    private var voiceRecordWaveLayout: LinearLayout? = null
    private val voiceWaveBars = ArrayList<View>()
    private val voiceUiHandler = Handler(Looper.getMainLooper())
    private var voiceUiRunnable: Runnable? = null
    private var voiceRecordStartMs = 0L
    private val voiceWaveFrames = arrayOf(
        intArrayOf(8, 14, 22, 12, 28, 16, 10, 24, 18, 30, 13, 20),
        intArrayOf(20, 9, 26, 15, 32, 11, 23, 17, 28, 12, 24, 16),
        intArrayOf(12, 27, 10, 22, 15, 31, 18, 26, 9, 20, 29, 14),
        intArrayOf(28, 16, 24, 11, 19, 30, 13, 25, 17, 10, 22, 15)
    )
    private val wingmanSuggestionTag = "chat_wingman_suggestions"

    private class LocalOriginalTextContent(remoteText: String, private val localText: String) : WKTextContent(remoteText) {
        override fun getDisplayContent(): String {
            return localText
        }

        override fun getSearchableWord(): String {
            return localText
        }
    }

    private data class PendingBeforeSendTranslate(
        val originalText: String,
        val mentionUids: ArrayList<String>?,
        val entities: ArrayList<WKMsgEntity>?,
        val reply: WKReply?,
        val channelId: String,
        val channelType: Byte,
        val sessionGeneration: Long,
        val loginUid: String,
        val sourceLanguage: String,
        val targetLanguage: String
    )

    private val beforeSendTranslateQueue = ArrayDeque<PendingBeforeSendTranslate>()
    private var beforeSendTranslateRunning = false
    private var beforeSendTranslateRunToken = 0L
    private var beforeSendTranslateThread: Thread? = null
    private var pendingDeepSeekReplyText = ""
    private var pendingDeepSeekBackTranslation = ""
    private var handlingKeyboardSend = false


    private val menuView: View = parentView.findViewById(R.id.menuView)
    private val menuLayout: View = parentView.findViewById(R.id.menuLayout)
    private val editText: ContactEditText = parentView.findViewById(R.id.editText)
    private val hitTv: AppCompatTextView = parentView.findViewById(R.id.hitTv)
    private val plusBtn: AppCompatTextView = parentView.findViewById(R.id.plusBtn)
    private val sendIV: AppCompatImageView = parentView.findViewById(R.id.sendIV)
    private val markdownIv: AppCompatImageView = parentView.findViewById(R.id.markdownIv)
    private val flameIV: AppCompatImageView = parentView.findViewById(R.id.flameIV)
    private val menuIv: AppCompatImageView = parentView.findViewById(R.id.menuIv)
    private val panelView: FrameLayout = parentView.findViewById(R.id.panelView)
    private val chatView: LinearLayout = parentView.findViewById(R.id.chatView)
    private val chatTopLayout: FrameLayout = parentView.findViewById(R.id.chatTopLayout)
    private val sourceLangBtn: AppCompatTextView = parentView.findViewById(R.id.sourceLangBtn)
    private val swapLangBtn: AppCompatTextView = parentView.findViewById(R.id.swapLangBtn)
    private val targetLangBtn: AppCompatTextView = parentView.findViewById(R.id.targetLangBtn)
    private val aiSendToggle: AppCompatImageView = parentView.findViewById(R.id.aiSendToggle)
    private val aiSendDotHost: View = parentView.findViewById(R.id.aiSendDot)
    private val aiSendStatusView: TranslateStatusView = installTranslateStatusView(aiSendDotHost)
    private var flameLayout: LinearLayout? = null

    // 相册有新图
    private var newImageLayout: LinearLayout? = null

    // 回复 | 编辑
    private var chatTopView: LinearLayout? = null
    private val chatInputNormalHeight = AndroidUtilities.dp(52f)
    private val chatReplyPanelHeight = AndroidUtilities.dp(58f)
    private val chatInputReplyHeight = AndroidUtilities.dp(110f)

    // 多选
    private var multipleChoiceView: LinearLayout? = null

    // 封禁
    private var banView: FrameLayout? = null

    // 禁言
    private var forbiddenView: FrameLayout? = null

    // 工具栏
    private var toolBarAdapter: WKChatToolBarAdapter? = null
    private val toolbarRecyclerView: RecyclerView =
        parentView.findViewById(R.id.toolbarRecyclerView)

    // 艾特
    private var remindRecycleView: NoEventRecycleView? = null
    private var remindHeaderView: View? = null
    private var remindMemberAdapter: RemindMemberAdapter? = null

    // gif
    private var robotGifRecyclerView: NoEventRecycleView? = null
    private var robotGIFAdapter: RobotGIFAdapter? = null
    private var robotGifHeaderView: View? = null

    // menu
    private var menuRecyclerView: NoEventRecycleView? = null
    private var menuHeaderView: View? = null
    private var robotMenuAdapter: RobotMenuAdapter? = null
    private var lastHeight = 0
    private var lastTargetLines = 1 // 追踪上一次的目标行数
    private val maxLines: Int = 3

    init {
        this.menuView.background = Theme.getBackground(Theme.colorAccount, 30f)
        editText.filters = arrayOf<InputFilter>(StringUtils.getInputFilter(maxLength))
        editText.setMaxLength(maxLength)
        // 设置输入框的初始行数
        editText.setMinLines(1)
        editText.setMaxLines(maxLines)
        initListener()
        initRemind()
        initRobotGIF()
        initRobotMenu()
        initTool()
        initAiAssistBar()
        initMultipleChoiceView()
        initBanView()
        initForbiddenView()
        initChatTopView()
        initFlame()
        initNewImageView()
        EndpointManager.getInstance().invoke(
            "initInputPanel",
            InitInputPanelMenu(
                parentView,
                iConversationContext,
                followScrollLayout
            )
        )
    }

    fun updateForwardView(num: Int) {
        val forwardView = multipleChoiceView?.findViewWithTag<View>("forwardView")
        val deleteIv = multipleChoiceView?.findViewWithTag<AppCompatImageView>("deleteIv")
        val forwardIv = multipleChoiceView?.findViewWithTag<AppCompatImageView>("forwardIv")
        val forwardTv = multipleChoiceView?.findViewWithTag<AppCompatTextView>("forwardTv")
        val deleteTv = multipleChoiceView?.findViewWithTag<AppCompatTextView>("deleteTv")
        if (num > 0) {
            forwardView?.isEnabled = true
            deleteTv?.setTextColor(
                ContextCompat.getColor(
                    iConversationContext.chatActivity,
                    R.color.colorDark
                )
            )
            forwardTv?.setTextColor(
                ContextCompat.getColor(
                    iConversationContext.chatActivity,
                    R.color.colorDark
                )
            )
            deleteIv?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    iConversationContext.chatActivity, R.color.colorDark
                ), PorterDuff.Mode.MULTIPLY
            )
            forwardIv?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    iConversationContext.chatActivity, R.color.colorDark
                ), PorterDuff.Mode.MULTIPLY
            )
        } else {
            forwardView?.isEnabled = false
            deleteTv?.setTextColor(
                ContextCompat.getColor(
                    iConversationContext.chatActivity,
                    R.color.color999
                )
            )
            forwardTv?.setTextColor(
                ContextCompat.getColor(
                    iConversationContext.chatActivity,
                    R.color.color999
                )
            )
            deleteIv?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    iConversationContext.chatActivity, R.color.color999
                ), PorterDuff.Mode.MULTIPLY
            )
            forwardIv?.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(
                    iConversationContext.chatActivity, R.color.color999
                ), PorterDuff.Mode.MULTIPLY
            )
        }
    }

    fun isCanBack(): Boolean {
        if (newImageLayout?.visibility == View.VISIBLE) {
            newImageLayout?.visibility = View.GONE
            return false
        }
        if (chatTopView?.visibility == View.VISIBLE) {
            closeChatReplyPanel()
            iConversationContext.deleteOperationMsg()
            return false
        }
        if (helper.isPanelState()) {
            resetToolBar()
            helper.resetState()
            return false
        }
        return true
    }

    fun showMultipleChoice() {
        chatView.visibility = View.GONE
        isDisableToolBar(true)
        helper.resetState()
        CommonAnim.getInstance().showBottom2Top(multipleChoiceView)
    }

    fun hideMultipleChoice() {
        multipleChoiceView?.visibility = View.GONE
//        chatView.visibility=View.VISIBLE
        showOrHideForbiddenView()
        isDisableToolBar(false)
        CommonAnim.getInstance().showBottom2Top(chatView)
    }


    // 显示封禁
    fun showBan() {
        banView?.visibility = View.VISIBLE
        forbiddenView?.visibility = View.GONE
        chatView.visibility = View.GONE
        isDisableToolBar(true)
    }

    //隐藏封禁
    fun hideBan() {
        if (banView?.visibility == View.GONE) return
        banView?.visibility = View.GONE
        chatView.visibility = View.VISIBLE
        isDisableToolBar(false)
    }

    private fun safeEditSelectionStart(): Int {
        val length = editText.text?.length ?: 0
        return editText.selectionStart.coerceIn(0, length)
    }

    fun setEditContent(text: String) {
        val curPosition = safeEditSelectionStart()
        val sb = StringBuilder(editText.text?.toString().orEmpty())
        sb.insert(curPosition, text)
        editText.setText(sb.toString())
        editText.setText(
            MoonUtil.getEmotionContent(
                iConversationContext.chatActivity,
                editText,
                sb.toString()
            )
        )
        editText.setSelection(curPosition + text.length)
    }

    private fun showForbiddenView() {
        helper.resetState()
        forbiddenView?.visibility = View.VISIBLE
        chatView.visibility = View.GONE
        toolbarRecyclerView.visibility = View.GONE
        banView?.visibility = View.GONE
        val forbiddenTV =
            forbiddenView?.findViewWithTag<AppCompatTextView>("forbiddenTV")
        forbiddenTV?.text = iConversationContext.chatActivity.getString(R.string.fullStaffing)
    }

    private fun hideForbiddenView() {
        if (forbiddenView?.visibility == View.GONE) return
        forbiddenView?.visibility = View.GONE
        chatView.visibility = View.VISIBLE
        toolbarRecyclerView.visibility = View.GONE
        val forbiddenTV =
            forbiddenView?.findViewWithTag<AppCompatTextView>("forbiddenTV")
        forbiddenTV?.text = iConversationContext.chatActivity.getString(R.string.fullStaffing)
    }

    private fun isDisableToolBar(isDisable: Boolean) {
        for (index in toolBarAdapter!!.data.indices) {
            toolBarAdapter!!.data[index].isDisable = isDisable
        }
        toolBarAdapter!!.notifyItemRangeChanged(0, toolBarAdapter!!.itemCount)

    }

    fun getEditText(): ContactEditText {
        return this.editText
    }

    /**
     * Fills the composer with the peer-facing reply while keeping the back-translation only in
     * local memory. If the user edits the reply, the stale back-translation is discarded.
     */
    fun setDeepSeekReplyDraft(remoteText: String, backTranslation: String?) {
        val reply = remoteText.trim()
        if (reply.isEmpty()) return
        pendingDeepSeekReplyText = reply
        pendingDeepSeekBackTranslation = backTranslation?.trim().orEmpty()
            .takeUnless { it == reply }
            .orEmpty()
        editText.setText(reply)
        editText.setSelection(reply.length)
    }

    private fun clearPendingDeepSeekReply() {
        pendingDeepSeekReplyText = ""
        pendingDeepSeekBackTranslation = ""
    }

    fun showReplyLayout(mMsg: WKMsg) {
        var showName: String? = ""
        if (mMsg.from != null) {
            showName = mMsg.from.channelName
        } else {
            val channel = WKIM.getInstance().channelManager.getChannel(
                mMsg.fromUID,
                WKChannelType.PERSONAL
            )
            if (channel != null) {
                showName =
                    if (TextUtils.isEmpty(channel.channelRemark)) channel.channelName else channel.channelRemark
            }
        }
        val topLeftIv = chatTopView?.findViewWithTag<AppCompatImageView>("topLeftIv")
        val topTitleTv = chatTopView?.findViewWithTag<AppCompatTextView>("topTitleTv")
        val contentTv = chatTopView?.findViewWithTag<AppCompatTextView>("contentTv")
        topLeftIv?.setImageResource(R.mipmap.msg_panel_reply)
        topTitleTv?.text = if (TextUtils.isEmpty(showName)) "回复" else "回复 $showName"
        val content = mMsg.remoteExtra?.contentEditMsgModel?.displayContent
            ?: mMsg.baseContentMsgModel?.displayContent
            ?: mMsg.content
            ?: ""
        contentTv?.text = content
//        MoonUtil.identifyFaceExpression(
//            iConversationContext!!.chatActivity,
//            replyDisplayTv,
//            mMsg.baseContentMsgModel.getDisplayContent(),
//            MoonUtil.DEF_SCALE
//        )
        openChatReplyPanel()

    }

    fun showEditLayout(mMsg: WKMsg) {
        val textModel = mMsg.baseContentMsgModel as? WKTextContent ?: return
        var content = textModel.displayContent.orEmpty()
        val contentEdit = mMsg.remoteExtra?.contentEdit.orEmpty()
        if (!TextUtils.isEmpty(contentEdit)) {
            try {
                val json = JSONObject(contentEdit)
                content = json.optString("content", content)
            } catch (e: Exception) {
                Log.w("ChatPanelManager", "parse edited message failed", e)
            }
        }

        val topLeftIv = chatTopView?.findViewWithTag<AppCompatImageView>("topLeftIv")
        val topTitleTv = chatTopView?.findViewWithTag<AppCompatTextView>("topTitleTv")
        val contentTv = chatTopView?.findViewWithTag<AppCompatTextView>("contentTv")
        topTitleTv?.text = iConversationContext.chatActivity.getString(R.string.edit_msg)
        contentTv?.text = content
        editText.setText(content)
        editText.setSelection(content.length)
        openChatReplyPanel()
        topLeftIv?.setImageResource(R.mipmap.msg_edit)
    }

    fun initRefreshListener() {
        if (destroyed || refreshListenersRegistered) return
        refreshListenersRegistered = true
        WKIM.getInstance().channelMembersManager.addOnAddChannelMemberListener(this.eventKey) { list ->
            for (channelMember in list) {
                if (channelMember.memberUID == loginUID) {
                    showOrHideForbiddenView()
                    break
                }
            }
        }
        WKIM.getInstance().channelMembersManager.addOnRefreshChannelMemberInfo(
            this.eventKey
        ) { mChannelMember, _ ->
            if (mChannelMember != null
                && mChannelMember.channelID.equals(iConversationContext.chatChannelInfo.channelID)
                && mChannelMember.channelType == iConversationContext.chatChannelInfo.channelType
                && iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP
            ) {
                //禁言
                if (mChannelMember.memberUID == this.loginUID) {
                    showOrHideForbiddenView()
                }
            }
        }
        WKIM.getInstance().channelManager.addOnRefreshChannelInfo(
            this.eventKey
        ) { mChannel, _ ->
            if (mChannel.channelType == iConversationContext.chatChannelInfo.channelType && mChannel.channelID.equals(
                    iConversationContext.chatChannelInfo.channelID
                )
            ) {
                showOrHideForbiddenView()
                // 封禁群
                if (mChannel.status == WKChannelStatus.statusDisabled) {
                    showBan()
                } else {
                    hideBan()
                }
                flame = mChannel.flame
                CommonAnim.getInstance().showOrHide(flameIV, flame == 1, true)
                markdownIv.visibility = View.GONE
                showFlame(mChannel.flameSecond)
            }
        }
    }

    @Volatile
    private var timer: Timer? = null

    @Synchronized
    private fun cancelForbiddenTimer(expectedTimer: Timer? = null) {
        if (expectedTimer != null && timer !== expectedTimer) return
        val current = timer
        timer = null
        current?.cancel()
        current?.purge()
    }

    private fun showForbiddenTimer(totalTime: Long) {
        val localTimer = synchronized(this) {
            if (timer != null) return
            Timer().also { timer = it }
        }
        val requestChannelId = activeChannelId
        val requestChannelType = activeChannelType
        val requestGeneration = activeSessionGeneration
        val timerTask: TimerTask = object : TimerTask() {
            override fun run() {
                val nowTime = WKTimeUtils.getInstance().currentSeconds
                val day = (totalTime - nowTime) / (60 * 60 * 24)
                val hour = (totalTime - nowTime - day * 60 * 60 * 24) / (60 * 60)
                val min = (totalTime - nowTime - day * 60 * 60 * 24 - hour * 3600) / 60
                val second = (totalTime - nowTime) % 60
                if (nowTime >= totalTime) {
                    AndroidUtilities.runOnUIThread {
                        if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) {
                            return@runOnUIThread
                        }
                        val channel = iConversationContext.chatChannelInfo
                        if (channel.forbidden == 1) {
                            showOrHideForbiddenView()
                        } else {
                            hideForbiddenView()
                        }
                    }
                    cancel()
                    cancelForbiddenTimer(localTimer)
                } else {
                    var dayStr = "00"
                    if (day > 0) {
                        dayStr = if (day < 10) {
                            "0$day"
                        } else "$day"
                    }
                    var hourStr = "00"
                    if (hour > 0) {
                        hourStr = if (hour < 10) {
                            "0$hour"
                        } else "$hour"
                    }
                    var minStr = "00"
                    if (min > 0) {
                        minStr = if (min < 10) {
                            "0$min"
                        } else "$min"
                    }
                    var secondStr = "00"
                    if (second > 0) {
                        secondStr = if (second < 10) {
                            "0$second"
                        } else "$second"
                    }
                    val content: String
                    if (day > 0) {
                        content = String.format(
                            iConversationContext.chatActivity.getString(R.string.forbidden_detail_day),
                            dayStr,
                            hourStr,
                            minStr,
                            secondStr
                        )
                    } else {
                        if (hour > 0) {
                            content = String.format(
                                iConversationContext.chatActivity.getString(R.string.forbidden_detail_hour),
                                hourStr,
                                minStr,
                                secondStr
                            )
                        } else {
                            content = if (min > 0) {
                                String.format(
                                    iConversationContext.chatActivity.getString(R.string.forbidden_detail_minute),
                                    minStr,
                                    secondStr
                                )
                            } else {
                                String.format(
                                    iConversationContext.chatActivity.getString(R.string.forbidden_detail_second),
                                    secondStr
                                )
                            }
                        }
                    }
                    AndroidUtilities.runOnUIThread {
                        if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) {
                            return@runOnUIThread
                        }
                        val forbiddenTV =
                            forbiddenView?.findViewWithTag<AppCompatTextView>("forbiddenTV")
                        forbiddenTV?.text = content
                    }
                }
            }
        }
        localTimer.schedule(timerTask, 0, 1000)

    }

    fun showOrHideForbiddenView() {
        cancelForbiddenTimer()
        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.CUSTOMER_SERVICE) {
            hideBan()
            return
        }
        val mChannel = WKIM.getInstance().channelManager.getChannel(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType
        )
        val mChannelMember = WKIM.getInstance().channelMembersManager.getMember(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType,
            this.loginUID
        )
        if (mChannelMember != null) {
            if (mChannelMember.role == WKChannelMemberRole.admin) {
                hideForbiddenView()
            } else {
                if (mChannel != null && mChannel.forbidden == 1) {
                    if (mChannelMember.role == WKChannelMemberRole.manager) {
                        if (mChannelMember.forbiddenExpirationTime == 0L)
                            hideForbiddenView()
                        else {
                            // 显示成员禁言
                            showForbiddenWithMemberView(mChannelMember.forbiddenExpirationTime)
                        }
                    } else {
                        // 显示全员禁言
                        showForbiddenView()
                    }
                } else {
                    if (mChannelMember.forbiddenExpirationTime > 0) {
                        // 显示成员禁言
                        showForbiddenWithMemberView(mChannelMember.forbiddenExpirationTime)
                    } else {
                        hideForbiddenView()
                    }
                }
            }
        }

    }


    private fun showForbiddenWithMemberView(time: Long) {
        val nowTime = WKTimeUtils.getInstance().currentSeconds
        // 本地成员缓存可能短暂保留已经到期的禁言时间。继续为过去时间启动 Timer
        // 会不断触发“到期 -> 重新检查 -> 再启动已到期 Timer”的循环。
        if (time <= nowTime) {
            cancelForbiddenTimer()
            hideForbiddenView()
            return
        }
        showForbiddenView()
        val day = (time - nowTime) / (3600 * 24)
        val hour = (time - nowTime) / 3600
        val min = (time - nowTime) / 60
        var showText = String.format(
            iConversationContext.chatActivity.getString(R.string.forbidden_to_minute),
            1
        )
        if (day > 0)
            showText = String.format(
                iConversationContext.chatActivity.getString(R.string.forbidden_to_day),
                day
            )
        else {
            if (hour > 0) {
                showText = String.format(
                    iConversationContext.chatActivity.getString(R.string.forbidden_to_hour),
                    hour
                )
            } else {
                if (min > 0) {
                    showText = String.format(
                        iConversationContext.chatActivity.getString(R.string.forbidden_to_minute),
                        min
                    )
                }
            }
        }
        val requestChannelId = activeChannelId
        val requestChannelType = activeChannelType
        val requestGeneration = activeSessionGeneration
        showForbiddenTimer(time)
        AndroidUtilities.runOnUIThread {
            if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) {
                return@runOnUIThread
            }
            val forbiddenTV =
                forbiddenView?.findViewWithTag<AppCompatTextView>("forbiddenTV")
            forbiddenTV?.text = showText
        }
    }

    fun chatAvatarClick(uid: String, isLongClick: Boolean) {
        if (isLongClick) {
            if (uid == this.loginUID) return
            if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP) {
                val loginMember = WKIM.getInstance().channelMembersManager.getMember(
                    iConversationContext.chatChannelInfo.channelID,
                    iConversationContext.chatChannelInfo.channelType,
                    this.loginUID
                )
                if (loginMember != null) {
                    if ((iConversationContext.chatChannelInfo.forbidden == 1 && loginMember.role == WKChannelMemberRole.normal) || loginMember.forbiddenExpirationTime > 0) {
                        return
                    }
                }
                val member =
                    WKIM.getInstance().channelMembersManager.getMember(
                        iConversationContext.chatChannelInfo.channelID,
                        iConversationContext.chatChannelInfo.channelType,
                        uid
                    )
                if (member != null) {

                    addSpan(member.memberName, member.memberUID)
                } else {
                    val channel = WKIM.getInstance().channelManager.getChannel(
                        uid,
                        WKChannelType.PERSONAL
                    )
                    if (channel != null) {
                        addSpan(channel.channelName, channel.channelID)
                    }
                }
            }

        } else {
            if (iConversationContext.chatChannelInfo.channelType != WKChannelType.CUSTOMER_SERVICE) {
                // 点击头像统一进入新个人主页。群聊里必须带 groupID，后端才能返回加好友 vercode。
                val groupId = if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP) {
                    iConversationContext.chatChannelInfo.channelID
                } else ""
                ProfileNavigator.open(iConversationContext.chatActivity, uid, groupId)
            }

        }
    }

    fun onConversationChanged(
        oldChannelId: String,
        oldChannelType: Byte,
        newChannelId: String,
        newChannelType: Byte,
        generation: Long
    ) {
        if (destroyed) return
        // 先切换会话身份再清空输入框；ContactEditText 的 TextWatcher 会同步执行，
        // 必须让它看到新频道及已经重新绑定的 @成员列表。
        activeChannelId = newChannelId
        activeChannelType = newChannelType
        activeSessionGeneration = generation
        rebindRemindForActiveConversation()

        // 已经点击发送的翻译任务继续发送到任务捕获的原会话；这里只重置新会话 UI，
        // 不能静默丢掉用户已经提交的消息。
        resetVoiceState(cancelRecording = true)
        cancelForbiddenTimer()
        clearPendingDeepSeekReply()
        closeChatReplyPanel()
        editText.text = null
        lastInputTime = 0L
        inlineQueryOffset = ""
        searchKey = ""
        username = ""
        robotQueryToken++
        robotGIFAdapter?.setList(emptyList())
        menuRecyclerView?.visibility = View.GONE
        remindRecycleView?.visibility = View.GONE
        robotGifRecyclerView?.visibility = View.GONE
        banView?.visibility = View.GONE
        forbiddenView?.visibility = View.GONE
        chatView.visibility = View.VISIBLE
        toolbarRecyclerView.visibility = View.GONE
        resetMenuIv()

        flame = iConversationContext.chatChannelInfo.flame
        flameIV.visibility = if (flame == 1) View.VISIBLE else View.GONE
        markdownIv.visibility = View.GONE
        showFlame(iConversationContext.chatChannelInfo.flameSecond)
        checkRobotMenu(iConversationContext)
        showOrHideForbiddenView()
        updateSendButtonMode()
    }

    private fun tryClaimDirectVoiceOwner(): Boolean {
        synchronized(DIRECT_VOICE_LOCK) {
            val owner = directVoiceOwner
            if (owner != null && owner !== this) return false
            directVoiceOwner = this
            return true
        }
    }

    private fun isDirectVoiceOwner(): Boolean = synchronized(DIRECT_VOICE_LOCK) {
        directVoiceOwner === this
    }

    private fun releaseDirectVoiceOwner() {
        synchronized(DIRECT_VOICE_LOCK) {
            if (directVoiceOwner === this) directVoiceOwner = null
        }
    }

    private fun resetVoiceState(cancelRecording: Boolean) {
        if (cancelRecording && directVoiceStarted && isDirectVoiceOwner()) {
            try {
                WKVoiceViewManager.getInstance().finishDirectRecord(true)
            } catch (e: Exception) {
                Log.w("ChatPanelManager", "cancel direct record failed", e)
            }
        }
        directVoiceStarted = false
        releaseDirectVoiceOwner()
        // 只移除录音 UI 自己的 runnable。该 Handler 也承载翻译完成回调，
        // 切换会话时 removeCallbacksAndMessages(null) 会让已提交的翻译消息永远丢失。
        stopVoiceUiTimer()
        voiceHolding = false
        voiceCanceling = false
        voiceForwardTarget = null
        voiceLastMoveEvent?.recycle()
        voiceLastMoveEvent = null
        hideVoiceRecordUi()
    }

    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        refreshListenersRegistered = false
        robotQueryToken++
        // 已经点击“发送”的翻译任务属于已提交消息。页面销毁后仍让队列完成并
        // 发送到任务捕获的原频道；这里只停止本页面录音 UI，不能清空翻译回调。
        resetVoiceState(cancelRecording = true)
        cancelForbiddenTimer()
        releaseRemindView()
        unregisterEmojiEndpoint()
        WKIM.getInstance().robotManager.removeRefreshRobotMenu(robotListenerKey)
        WKIM.getInstance().channelManager.removeRefreshChannelInfo(this.eventKey)
        WKIM.getInstance().channelMembersManager.removeRefreshChannelMemberInfo(this.eventKey)
        WKIM.getInstance().channelMembersManager.removeAddChannelMemberListener(this.eventKey)
    }


    private fun initFlame() {
        flame = iConversationContext.chatChannelInfo.flame
        initFlameView()
        val seekBarView = flameLayout?.findViewWithTag<SeekBarView>("seekBarView")
        if (flame == 1) {
            flameIV.visibility = View.VISIBLE
            CommonAnim.getInstance().showOrHide(flameIV, true, true)
            markdownIv.visibility = View.GONE
        } else
            markdownIv.visibility = View.GONE
        seekBarView?.setDelegate(object : SeekBarView.SeekBarViewDelegate {
            override fun onSeekBarDrag(stop: Boolean, progress: Float) {
                if (stop)
                    setProgress(progress)
            }

            override fun onSeekBarPressed(pressed: Boolean) {
            }
        })
        flameIV.setOnClickListener {
            if (flameLayout?.visibility == View.GONE) {
                CommonAnim.getInstance().animateOpen(
                    flameLayout,
                    0,
                    AndroidUtilities.dp(65f)
                )
                //    CommonAnim.getInstance().showBottom2Top(flameLayout)
            } else {
                CommonAnim.getInstance().animateClose(flameLayout)
            }
        }
        showFlame(iConversationContext.chatChannelInfo.flameSecond)
    }

    private fun showFlame(flameSecond: Int) {
        val burnSwitchView = flameLayout?.findViewWithTag<SwitchView>("switchView")
        val seekBarView = flameLayout?.findViewWithTag<SeekBarView>("seekBarView")
        val burnTimeTv = flameLayout?.findViewWithTag<AppCompatTextView>("burnTimeTv")
        burnSwitchView?.isChecked = flame == 1
        if (flame == 0 && flameLayout?.visibility == View.VISIBLE) {
            CommonAnim.getInstance().animateClose(flameLayout)
        }
        var content: String? = ""
        when (flameSecond) {
            0 -> {
                content = iConversationContext.chatActivity.getString(R.string.burn_time_0)
                seekBarView?.setProgress(0f, true)
            }

            10 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_10)
                seekBarView?.setProgress(10 / 180f, true)
            }

            20 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_20)
                seekBarView?.setProgress(20 / 180f, true)
            }

            30 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_30)
                seekBarView?.setProgress(30 / 180f, true)
            }

            60 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_60)
                seekBarView?.setProgress(60 / 180f, true)
            }

            120 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_120)
                seekBarView?.setProgress(120 / 180f, true)
            }

            180 -> {
                content = iConversationContext.chatActivity.getString(R.string.time_180)
                seekBarView?.setProgress(180 / 180f, true)
            }
        }
        if (flameSecond == 0) {
            burnTimeTv?.text = content
        } else burnTimeTv?.text = String.format(
            iConversationContext.chatActivity.getString(R.string.burn_time_desc),
            content
        )
    }

    private fun setProgress(progress: Float) {
        val seekBarView = flameLayout?.findViewWithTag<SeekBarView>("seekBarView")
        val burnTimeTv = flameLayout?.findViewWithTag<AppCompatTextView>("burnTimeTv")
        val seekPg = progress * 180
        val newProgress: Int
        val content: String
        if (seekPg < 5) {
            newProgress = 0
            content = iConversationContext.chatActivity.getString(R.string.burn_time_0)
            seekBarView?.setProgress(0f, true)
        } else if (seekPg in 5.0..15.0) {
            newProgress = 10
            content = iConversationContext.chatActivity.getString(R.string.time_10)
        } else if (seekPg > 15 && seekPg <= 25) {
            newProgress = 20
            content = iConversationContext.chatActivity.getString(R.string.time_20)
        } else if (seekPg > 25 && seekPg <= 35) {
            newProgress = 30
            content = iConversationContext.chatActivity.getString(R.string.time_30)
        } else if (seekPg > 35 && seekPg <= 90) {
            newProgress = 60
            content = iConversationContext.chatActivity.getString(R.string.time_60)
        } else if (seekPg > 90 && seekPg <= 150) {
            newProgress = 120
            content = iConversationContext.chatActivity.getString(R.string.time_120)
        } else {
            newProgress = 180
            content = iConversationContext.chatActivity.getString(R.string.time_180)
        }
        if (newProgress == 0) {
            burnTimeTv?.text = content
        } else burnTimeTv?.text = String.format(
            iConversationContext.chatActivity.getString(R.string.burn_time_desc),
            content
        )
        seekBarView?.setProgress(newProgress.toFloat() / 180, true)
        val requestChannelId = activeChannelId
        val requestChannelType = activeChannelType
        val requestGeneration = activeSessionGeneration
        if (requestChannelType == WKChannelType.PERSONAL) {
            FriendModel.getInstance().updateUserSetting(
                requestChannelId, "flame_second", newProgress
            ) { code: Int, msg: String? ->
                if (isActiveSession(requestChannelId, requestChannelType, requestGeneration)
                    && code != HttpResponseCode.success.toInt()
                ) {
                    WKToastUtils.getInstance().showToast(msg)
                }
            }
        } else {
            GroupModel.getInstance().updateGroupSetting(
                requestChannelId, "flame_second", newProgress
            ) { code: Int, msg: String? ->
                if (isActiveSession(requestChannelId, requestChannelType, requestGeneration)
                    && code != HttpResponseCode.success.toInt()
                ) {
                    WKToastUtils.getInstance().showToastNormal(msg)
                }
            }
        }
    }


    private fun initTool() {
        toolBarAdapter = WKChatToolBarAdapter()
        toolBarAdapter?.animationEnable = false
        toolbarRecyclerView.adapter = toolBarAdapter
        toolbarRecyclerView.layoutManager =
            LinearLayoutManager(
                iConversationContext.chatActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        //去除刷新条目闪动动画
        (toolbarRecyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        val toolBarList = EndpointManager.getInstance()
            .invokes<ChatToolBarMenu>(EndpointCategory.wkChatToolBar, iConversationContext)
        val tempToolBarList: MutableList<ChatToolBarMenu> = ArrayList()
        var isAddEmojiLayout = false
        for (menu in toolBarList) {
            if (menu != null) {
                if (menu.sid.equals("chat_toolbar_sticker")) {
                    isAddEmojiLayout = false
                }
                tempToolBarList.add(menu)
            }
        }
        if (isAddEmojiLayout) {
            val emojiToolBar = ChatToolBarMenu(
                "emojiToolBar",
                R.mipmap.icon_chat_toolbar_emoji,
                R.mipmap.icon_chat_toolbar_emoji,
                getEmojiLayout()
            ) { _, _ -> }
            tempToolBarList.add(0, emojiToolBar)
        }
        toolBarAdapter?.setList(tempToolBarList)
        toolbarRecyclerView.visibility = View.GONE
        toolBarAdapter?.addChildClickViewIds(R.id.imageView)
        toolBarAdapter?.setOnItemChildClickListener { adapter1: BaseQuickAdapter<*, *>, view: View, position: Int ->
            if (view.id == R.id.imageView) {
                SingleClickUtil.determineTriggerSingleClick(view, 500) {
                    val mChatToolBarMenu =
                        adapter1.getItem(position) as ChatToolBarMenu?
                            ?: return@determineTriggerSingleClick
                    if (mChatToolBarMenu.isDisable) return@determineTriggerSingleClick
                    // 如果点击的是@
                    if (mChatToolBarMenu.sid == "wk_chat_toolbar_remind") {
                        val editable = editText.text
                        val index = safeEditSelectionStart()
                        if (index != editable.length) {
                            editable.insert(index, "@")
                        } else {
                            editText.append("@")
                        }
                        return@determineTriggerSingleClick
                    }
                    //如果点击的是更多
                    if (mChatToolBarMenu.sid == "wk_chat_toolbar_more") {
                        val path = ImageUtils.getInstance().newestPhoto
                        val oldPath =
                            WKSharedPreferencesUtil.getInstance().getSP("new_img_path")
                        if (!TextUtils.isEmpty(path) && TextUtils.isEmpty(oldPath)
                            || !TextUtils.isEmpty(path) && !TextUtils.isEmpty(oldPath) && oldPath != path
                        ) {
                            val requestChannelId = activeChannelId
                            val requestChannelType = activeChannelType
                            val requestGeneration = activeSessionGeneration
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (isActiveSession(requestChannelId, requestChannelType, requestGeneration)) {
                                    showNewImgDialog(path, requestChannelId, requestChannelType, requestGeneration)
                                }
                            }, 300)
                        }
                    }
                    if (mChatToolBarMenu.sid == "wk_chat_toolbar_voice") {
                        checkPermission(
                            iConversationContext.chatActivity,
                            mChatToolBarMenu,
                            position,
                            toolBarAdapter!!
                        )
                        return@determineTriggerSingleClick
                    }
                    toolBarClick(mChatToolBarMenu, position, toolBarAdapter!!)
                }
            }
        }
    }

    private fun initRobotMenu() {
        robotMenuAdapter = RobotMenuAdapter()
        this.menuRecyclerView =
            NoEventRecycleView(iConversationContext.chatActivity)
        this.menuRecyclerView!!.visibility = View.GONE
        this.menuHeaderView = View(iConversationContext.chatActivity)
        this.menuHeaderView!!.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.transparent
            )
        )
        this.menuRecyclerView?.setView(parentView, this.menuHeaderView)
        robotMenuAdapter?.addHeaderView(this.menuHeaderView!!)
        this.followScrollLayout.addView(this.menuRecyclerView)
        val menus = WKRobotModel.getInstance().getRobotMenus(
            iConversationContext.chatChannelInfo.channelID,
            iConversationContext.chatChannelInfo.channelType
        )
        menuRecyclerView!!.adapter = robotMenuAdapter
        menuRecyclerView!!.layoutManager = LinearLayoutManager(
            iConversationContext.chatActivity,
            LinearLayoutManager.VERTICAL,
            false
        )
        menuRecyclerView!!.addOnScrollListener(menuRecyclerView!!.onScrollListener)
        if (menus.isNotEmpty()) {
            robotMenuAdapter!!.setList(menus)
            CommonAnim.getInstance().showLeft2Right(menuView)
        }

        resetMenuHeader()

        menuLayout.setOnClickListener {
            menuLayout.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )

            if (robotMenuAdapter!!.data.isEmpty()) {
                val tempMenu: List<WKRobotMenuEntity> =
                    WKRobotModel.getInstance().getRobotMenus(
                        iConversationContext.chatChannelInfo.channelID,
                        iConversationContext.chatChannelInfo.channelType
                    )
                robotMenuAdapter!!.setList(tempMenu)
            }
            menuRecyclerView?.scrollToPosition(0)
            if (menuRecyclerView?.visibility == View.VISIBLE) {
                resetMenuIv()
                CommonAnim.getInstance().hideTop2Bottom(menuRecyclerView)
            } else {
                CommonAnim.getInstance().showBottom2Top(menuRecyclerView)
                showMenuIv()
            }
        }

        robotMenuAdapter!!.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->
            val menu = robotMenuAdapter!!.data[position]
            if (menu != null) {
                menuLayout.performClick()
                val textContent = WKTextContent(menu.cmd)
                val list: MutableList<WKMsgEntity> =
                    ArrayList()
                val entity = WKMsgEntity()
                entity.length = menu.cmd.length
                entity.offset = 0
                entity.type = "bot_command"
                list.add(entity)
                textContent.entities = list

                val wkMsg = WKMsg()
                wkMsg.channelID = iConversationContext.chatChannelInfo.channelID
                wkMsg.channelType = iConversationContext.chatChannelInfo.channelType
                wkMsg.type = textContent.type
                wkMsg.baseContentMsgModel = textContent
                wkMsg.channelInfo = iConversationContext.chatChannelInfo
                wkMsg.robotID = menu.robot_id
                Log.e("ID是：", menu.robot_id)
                WKSendMsgUtils.getInstance().sendMessage(wkMsg)
            }
        }
        // 监听器 key 只用于标识注册项，不是频道过滤条件。使用实例唯一 key，
        // 回调时再读取当前会话，避免多个聊天页互相覆盖。
        WKIM.getInstance().robotManager.addOnRefreshRobotMenu(robotListenerKey) {
            if (!destroyed) checkRobotMenu(iConversationContext)
        }

    }

    private fun checkRobotMenu(iConversationContext: IConversationContext) {
        val channel = iConversationContext.chatChannelInfo
        val robotMembers = WKIM.getInstance().channelMembersManager.getRobotMembers(
            channel.channelID,
            channel.channelType
        )
        val hasRobot = channel.robot == 1 || !robotMembers.isNullOrEmpty()
        val menus = if (hasRobot) {
            WKRobotModel.getInstance().getRobotMenus(channel.channelID, channel.channelType)
        } else {
            emptyList<WKRobotMenuEntity>()
        }
        robotMenuAdapter?.setList(menus)
        if (menus.isNotEmpty()) {
            if (menuView.isGone) CommonAnim.getInstance().showLeft2Right(menuView)
            resetMenuHeader()
        } else {
            menuRecyclerView?.visibility = View.GONE
            menuView.visibility = View.GONE
            resetMenuIv()
        }
    }

    private fun resetMenuHeader() {
        parentView.post {
            var width = 40f
            if (robotMenuAdapter!!.data.size > 3) width = 48f
            menuHeaderView!!.layoutParams.height =
                parentView.top - AndroidUtilities.dp(
                    min(
                        robotMenuAdapter!!.data.size,
                        3
                    ) * width
                )
            //  menuHeaderView!!.layoutParams.height -= WKConstants.getKeyboardHeight()
            this.menuRecyclerView?.setHeaderViewY(this.menuHeaderView!!.layoutParams.height.toFloat())
        }
    }

    private fun resetMenuIv() {
        CommonAnim.getInstance()
            .rotateImage(menuIv, 360f, 180f, R.mipmap.icon_menu)
    }

    private fun showMenuIv() {
        CommonAnim.getInstance().rotateImage(
            menuIv,
            180f,
            360f,
            R.mipmap.icon_menu_close
        )
    }

    private fun releaseRemindView() {
        // 不主动把旧 RecyclerView 的 adapter 设为 null：成员查询可能仍在回调，
        // 让它完成到已经移出界面的旧列表比在 RemindMemberAdapter 内触发空引用更安全。
        remindRecycleView?.let { recyclerView ->
            if (recyclerView.parent === followScrollLayout) {
                followScrollLayout.removeView(recyclerView)
            }
        }
        remindRecycleView = null
        remindHeaderView = null
        remindMemberAdapter = null
    }

    private fun rebindRemindForActiveConversation() {
        releaseRemindView()
        if (activeChannelType != WKChannelType.PERSONAL) {
            initRemind()
        }
    }

    private fun initRemind() {
        val requestChannelId = activeChannelId
        val requestChannelType = activeChannelType
        if (requestChannelType == WKChannelType.PERSONAL) return

        val recyclerView = NoEventRecycleView(iConversationContext.chatActivity)
        val headerView = View(iConversationContext.chatActivity)
        val memberAdapter = RemindMemberAdapter(requestChannelId, requestChannelType)
        remindRecycleView = recyclerView
        remindHeaderView = headerView
        remindMemberAdapter = memberAdapter

        headerView.setBackgroundColor(
            ContextCompat.getColor(iConversationContext.chatActivity, R.color.transparent)
        )
        recyclerView.layoutManager = LinearLayoutManager(
            iConversationContext.chatActivity,
            LinearLayoutManager.VERTICAL,
            false
        )
        recyclerView.setView(parentView, headerView)
        recyclerView.addOnScrollListener(recyclerView.onScrollListener)
        recyclerView.adapter = memberAdapter
        memberAdapter.addHeaderView(headerView)
        memberAdapter.onNormal()
        followScrollLayout.addView(recyclerView)

        parentView.post {
            if (destroyed
                || remindRecycleView !== recyclerView
                || remindMemberAdapter !== memberAdapter
                || activeChannelId != requestChannelId
                || activeChannelType != requestChannelType
            ) return@post
            var height = 40f
            if (memberAdapter.data.size > 3) height = 46f
            headerView.layoutParams.height =
                parentView.top - AndroidUtilities.dp(min(memberAdapter.data.size, 3) * height)
            recyclerView.setHeaderViewY(headerView.layoutParams.height.toFloat())
        }

        memberAdapter.setOnItemClickListener { adapter, _, position ->
            if (destroyed
                || remindMemberAdapter !== memberAdapter
                || activeChannelId != requestChannelId
                || activeChannelType != requestChannelType
            ) return@setOnItemClickListener
            val entity = adapter.data.getOrNull(position) as? GroupMemberEntity
                ?: return@setOnItemClickListener
            val memberEntity = entity.member ?: WKChannelMember().apply {
                memberName = iConversationContext.chatActivity.getString(R.string.all)
                memberUID = "-1"
            }
            var showName = memberEntity.memberName
            val channel = WKIM.getInstance().channelManager.getChannel(
                memberEntity.memberUID,
                WKChannelType.PERSONAL
            )
            if (channel != null) showName = channel.channelName

            var deleteCount = 1
            if (!TextUtils.isEmpty(memberAdapter.searchKey)) {
                deleteCount += memberAdapter.searchKey.length
            }
            repeat(deleteCount) {
                editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }
            editText.requestFocus()
            addSpan(showName, memberEntity.memberUID)
        }
        recyclerView.visibility = View.GONE
    }

    private fun initRobotGIF() {

        robotGifRecyclerView =
            NoEventRecycleView(iConversationContext.chatActivity)
        robotGifRecyclerView!!.addIScrollListener { _, _ ->
            val layoutManager = robotGifRecyclerView!!.layoutManager as LinearLayoutManager
            val lastCompletelyVisibleItemPosition =
                layoutManager.findLastCompletelyVisibleItemPosition()
            if (lastCompletelyVisibleItemPosition == layoutManager.itemCount - 1
                && inlineQueryOffset.isNotEmpty()
                && searchKey.isNotEmpty()
                && username.isNotEmpty()
            ) {
                searchRobotGif(searchKey, username)
            }
        }
        robotGifHeaderView = View(iConversationContext.chatActivity)
        robotGifHeaderView!!.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.transparent
            )
        )
        robotGifRecyclerView!!.layoutManager = FullyGridLayoutManager(
            iConversationContext.chatActivity, 3
        )

        robotGifRecyclerView!!.addOnScrollListener(robotGifRecyclerView!!.onScrollListener)
        robotGIFAdapter = RobotGIFAdapter()
        robotGifRecyclerView!!.adapter = robotGIFAdapter
        robotGIFAdapter!!.addHeaderView(robotGifHeaderView!!)
        followScrollLayout.addView(robotGifRecyclerView)
        parentView.post {
            robotGifHeaderView!!.layoutParams.height =
                parentView.top - AndroidUtilities.dp(100f)
            this.robotGifRecyclerView!!.setHeaderViewY(robotGifHeaderView!!.layoutParams.height.toFloat())
        }
        robotGifRecyclerView!!.setView(parentView, robotGifHeaderView)
        robotGIFAdapter!!.setOnItemClickListener { adapter, _, position ->
            val entity = adapter.data[position] as WKRobotGIFEntity
            if (entity.isNull) return@setOnItemClickListener
            hideRobotView()
            val stickerContent = WKGifContent()
            stickerContent.height = entity.height
            stickerContent.width = entity.width
            stickerContent.url = entity.url
            iConversationContext.sendMessage(stickerContent)
            editText.text = null
//            CommonAnim.getInstance().showOrHide(closeSearchLottieIV, false, true)
            CommonAnim.getInstance().showOrHide(sendIV, true, true)
            CommonAnim.getInstance().showOrHide(hitTv, false, true)
        }
        this.robotGifRecyclerView!!.visibility = View.GONE
    }


    private fun hasInputText(): Boolean {
        return !TextUtils.isEmpty(StringUtils.replaceBlank(editText.text.toString()))
    }

    private fun updateSendButtonMode() {
        if (hasInputText()) {
            sendIV.setImageResource(R.mipmap.icon_chat_send)
            sendIV.colorFilter = PorterDuffColorFilter(Theme.colorAccount, PorterDuff.Mode.MULTIPLY)
        } else {
            sendIV.setImageResource(android.R.drawable.ic_btn_speak_now)
            sendIV.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(iConversationContext.chatActivity, R.color.color999),
                PorterDuff.Mode.MULTIPLY
            )
        }
    }

    private fun getSetting(key: String, defaultValue: String): String {
        val value = WKSharedPreferencesUtil.getInstance().getSP(key)
        return if (TextUtils.isEmpty(value)) defaultValue else value
    }

    private fun getFlag(key: String, defaultValue: Boolean): Boolean {
        val value = WKSharedPreferencesUtil.getInstance().getSP(key)
        if (TextUtils.isEmpty(value)) return defaultValue
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun putFlag(key: String, value: Boolean) {
        WKSharedPreferencesUtil.getInstance().putSP(key, if (value) "1" else "0")
    }

    private fun installTranslateStatusView(anchor: View): TranslateStatusView {
        val statusView = TranslateStatusView(anchor.context)
        statusView.id = anchor.id
        val parent = anchor.parent as? ViewGroup
        val index = parent?.indexOfChild(anchor) ?: -1
        val params = anchor.layoutParams
        params.width = AndroidUtilities.dp(10f)
        params.height = AndroidUtilities.dp(24f)
        statusView.layoutParams = params
        statusView.setOnClickListener { aiSendToggle.performClick() }
        if (parent != null && index >= 0) {
            parent.removeView(anchor)
            parent.addView(statusView, index)
        } else {
            anchor.visibility = View.GONE
        }
        return statusView
    }

    fun refreshAiAssistBar() {
        val sendTranslate = getFlag(keyAiSendTranslate, false)
        sourceLangBtn.text = langLabel(getSetting(keyAiSourceLang, "မြန်မာစာ"))
        targetLangBtn.text = langLabel(getSetting(keyAiTargetLang, "中文"))
        // 输入面板和消息气泡使用同一套“中 / A”双卡片语言标识。
        // 选中态由 drawable selector 切换为低饱和翡翠绿，不再依赖生硬的“文A”文字。
        aiSendToggle.isSelected = sendTranslate
        aiSendToggle.alpha = if (sendTranslate) 1f else 0.82f
        aiSendStatusView.active = sendTranslate
        aiSendStatusView.alpha = if (sendTranslate) 1f else 0.78f
    }

    private fun langLabel(name: String): String {
        return when (name) {
            "自动检测" -> "🌐 自动"
            "中文" -> "🇨🇳 中文"
            "မြန်မာစာ", "缅甸语" -> "🇲🇲 မြန်မာ"
            "English" -> "🇺🇸 English"
            "日本語" -> "🇯🇵 日本語"
            "한국어" -> "🇰🇷 한국어"
            "ภาษาไทย" -> "🇹🇭 ไทย"
            "Tiếng Việt" -> "🇻🇳 Việt"
            "Русский" -> "🇷🇺 Русский"
            else -> name
        }
    }

    private fun showLanguagePicker(isSource: Boolean) {
        val key = if (isSource) keyAiSourceLang else keyAiTargetLang
        val current = getSetting(key, if (isSource) "မြန်မာစာ" else "中文")
        var checked = langNames.indexOf(current)
        if (checked < 0) checked = 0
        val dialog = AlertDialog.Builder(iConversationContext.chatActivity)
            .setTitle(if (isSource) R.string.chat_ai_source_lang else R.string.chat_ai_target_lang)
            .setSingleChoiceItems(langNames, checked) { dialogInterface, which ->
                WKSharedPreferencesUtil.getInstance().putSP(key, langNames[which])
                refreshAiAssistBar()
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.chat_ai_cancel, null)
            .show()
        applyGlassDialogStyle(dialog)
    }


    private fun applyGlassDialogStyle(dialog: AlertDialog) {
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xF2FFFFFF.toInt(), 0xEAF4F7FF.toInt(), 0xEDEFF6FF.toInt())
        )
        bg.cornerRadius = AndroidUtilities.dp(22f).toFloat()
        dialog.window?.setBackgroundDrawable(bg)
        dialog.window?.setDimAmount(0.28f)
    }

    private fun showCenterToast(text: String) {
        val toast = Toast.makeText(iConversationContext.chatActivity, text, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    private fun initAiAssistBar() {
        markdownIv.visibility = View.GONE
        toolbarRecyclerView.visibility = View.GONE
        updateSendButtonMode()
        refreshAiAssistBar()
        plusBtn.setOnClickListener {
            triggerMediaPickerDirect()
        }
        sourceLangBtn.setOnClickListener { showLanguagePicker(true) }
        targetLangBtn.setOnClickListener { showLanguagePicker(false) }
        swapLangBtn.setOnClickListener {
            val source = getSetting(keyAiSourceLang, "မြန်မာစာ")
            val target = getSetting(keyAiTargetLang, "中文")
            WKSharedPreferencesUtil.getInstance().putSP(keyAiSourceLang, target)
            WKSharedPreferencesUtil.getInstance().putSP(keyAiTargetLang, source)
            refreshAiAssistBar()
        }
        aiSendToggle.setOnClickListener {
            val enable = !getFlag(keyAiSendTranslate, false)
            putFlag(keyAiSendTranslate, enable)
            refreshAiAssistBar()
            showCenterToast(
                iConversationContext.chatActivity.getString(
                    if (enable) R.string.chat_ai_translate_enabled_tip else R.string.chat_ai_translate_disabled_tip
                )
            )
        }
        aiSendStatusView.setOnClickListener { aiSendToggle.performClick() }
    }

    private fun sendInputText() {
        var content = StringUtils.replaceBlank(editText.text.toString())
        if (TextUtils.isEmpty(content)) return
        content = editText.text.toString()

        val isDeepSeekReply = pendingDeepSeekReplyText.isNotEmpty()
                && content == pendingDeepSeekReplyText
        val deepSeekBackTranslation = if (isDeepSeekReply) {
            pendingDeepSeekBackTranslation
        } else {
            ""
        }
        clearPendingDeepSeekReply()
        if (isDeepSeekReply) {
            // AI already produced the peer-facing language. The remote payload contains only this
            // text; the back-translation is stored locally and bound to the outgoing clientMsgNO.
            sendTextNow(
                remoteContent = content,
                localDisplayContent = null,
                reply = buildReplySnapshot(iConversationContext.replyMsg),
                deepSeekBackTranslation = deepSeekBackTranslation
            )
            return
        }

        val drawable = EmojiManager.getInstance().getDrawable(iConversationContext.chatActivity, content)
        if (!getFlag(keyAiSendTranslate, false) && drawable != null && iConversationContext.replyMsg == null) {
            val resultObject = EndpointManager.getInstance().invoke(
                "text_to_emoji_sticker",
                SendTextMenu(content, iConversationContext)
            )
            if (resultObject is Boolean && resultObject) {
                editText.text = null
                lastInputTime = 0
                updateSendButtonMode()
                return
            }
        }

        if (getFlag(keyAiSendTranslate, false)) {
            enqueueTranslateBeforeSend(content)
        } else {
            sendTextNow(content, null)
        }
    }

    private fun enqueueTranslateBeforeSend(content: String) {
        val replySnapshot = buildReplySnapshot(iConversationContext.replyMsg)

        // 编辑消息走原逻辑，避免异步翻译把“编辑”误变成一条新消息。
        if (chatTopView?.visibility == View.VISIBLE && replySnapshot == null) {
            sendTextNow(content, null)
            return
        }

        val mentionUids = editText.allUIDs?.let { ArrayList(it) }
        val entities = editText.allEntity?.let { ArrayList(it) }
        beforeSendTranslateQueue.offer(
            PendingBeforeSendTranslate(
                originalText = content,
                mentionUids = mentionUids,
                entities = entities,
                reply = replySnapshot,
                channelId = activeChannelId,
                channelType = activeChannelType,
                sessionGeneration = activeSessionGeneration,
                loginUid = loginUID,
                sourceLanguage = getSetting(keyAiSourceLang, "မြန်မာစာ"),
                targetLanguage = getSetting(keyAiTargetLang, "中文")
            )
        )

        editText.text = null
        lastInputTime = 0
        updateSendButtonMode()
        if (replySnapshot != null) {
            try {
                iConversationContext.deleteOperationMsg()
            } catch (_: Exception) {
                closeChatReplyPanel()
            }
        }

        processBeforeSendTranslateQueue()
    }

    private fun processBeforeSendTranslateQueue() {
        if (beforeSendTranslateRunning) return
        val task = beforeSendTranslateQueue.peek() ?: return

        beforeSendTranslateRunning = true
        val runToken = ++beforeSendTranslateRunToken
        val worker = Thread({
            val result = try {
                runBlocking {
                    WkTranslateBridge().translateBeforeSend(
                        ChatBeforeSendRequest(
                            context = iConversationContext.chatActivity.applicationContext,
                            text = task.originalText,
                            sourceLang = task.sourceLanguage,
                            targetLang = task.targetLanguage
                        )
                    )
                }
            } catch (e: InterruptedException) {
                null
            } catch (e: Exception) {
                Log.e("ChatPanelManager", "before-send translation failed", e)
                null
            }

            voiceUiHandler.post {
                if (runToken != beforeSendTranslateRunToken) return@post
                beforeSendTranslateThread = null
                beforeSendTranslateRunning = false
                val finished = beforeSendTranslateQueue.poll()
                if (finished != null) {
                    if (result != null && result.success && !TextUtils.isEmpty(result.translatedText)) {
                        sendTextNow(
                            remoteContent = result.translatedText,
                            localDisplayContent = finished.originalText,
                            mentionUids = finished.mentionUids,
                            entities = finished.entities,
                            reply = finished.reply,
                            targetChannelId = finished.channelId,
                            targetChannelType = finished.channelType,
                            targetSessionGeneration = finished.sessionGeneration,
                            targetLoginUid = finished.loginUid,
                            explicitTarget = true
                        )
                    } else if (!destroyed) {
                        // 即使用户已经切到其它会话，也必须提示这条已提交消息没有发出。
                        WKToastUtils.getInstance().showToast(
                            iConversationContext.chatActivity.getString(R.string.chat_ai_failed)
                        )
                    }
                }
                processBeforeSendTranslateQueue()
            }
        }, "chat-before-send-translate")
        beforeSendTranslateThread = worker
        worker.start()
    }

    private fun isActiveSession(channelId: String, channelType: Byte, generation: Long): Boolean {
        return !destroyed
            && generation == activeSessionGeneration
            && channelId == activeChannelId
            && channelType == activeChannelType
    }

    private fun buildReplySnapshot(replyMsg: WKMsg?): WKReply? {
        if (replyMsg == null) return null
        return try {
            val reply = WKReply()
            reply.payload = if (replyMsg.remoteExtra != null && replyMsg.remoteExtra.contentEditMsgModel != null) {
                replyMsg.remoteExtra.contentEditMsgModel
            } else {
                replyMsg.baseContentMsgModel
            }
            val from = replyMsg.from
            var showName = from?.channelName ?: ""
            if (TextUtils.isEmpty(showName)) {
                val channel = WKIM.getInstance().channelManager.getChannel(replyMsg.fromUID, WKChannelType.PERSONAL)
                if (channel != null) showName = channel.channelName
            }
            reply.from_name = showName
            reply.from_uid = replyMsg.fromUID
            reply.message_id = replyMsg.messageID
            reply.message_seq = replyMsg.messageSeq.toLong()
            val parentReply = replyMsg.baseContentMsgModel?.reply
            reply.root_mid = if (parentReply != null && !TextUtils.isEmpty(parentReply.root_mid)) {
                parentReply.root_mid
            } else {
                reply.message_id
            }
            reply
        } catch (e: Exception) {
            Log.e("ChatPanelManager", "build reply snapshot failed", e)
            null
        }
    }

    private fun sendTextNow(
        remoteContent: String,
        localDisplayContent: String?,
        mentionUids: List<String>? = null,
        entities: List<WKMsgEntity>? = null,
        reply: WKReply? = null,
        deepSeekBackTranslation: String? = null,
        targetChannelId: String = activeChannelId,
        targetChannelType: Byte = activeChannelType,
        targetSessionGeneration: Long = activeSessionGeneration,
        targetLoginUid: String = loginUID,
        explicitTarget: Boolean = false
    ) {
        if (targetChannelId.isEmpty() || WKConfig.getInstance().uid != targetLoginUid) return
        val targetIsActive = !destroyed && isActiveSession(
            targetChannelId,
            targetChannelType,
            targetSessionGeneration
        )
        // 普通发送必须属于当前会话；显式目标发送已经捕获频道，可以在切换会话后
        // 安全地完成，但绝不能再清空或修改新会话的输入面板。
        if (!explicitTarget && (destroyed || !targetIsActive)) return
        if (!TextUtils.isEmpty(deepSeekBackTranslation)
            && deepSeekBackTranslation != remoteContent
        ) {
            DeepSeekAssistant.rememberReplyForNextSend(
                iConversationContext.chatActivity,
                targetChannelId,
                targetChannelType,
                remoteContent,
                deepSeekBackTranslation!!
            )
        }

        val textMsgModel = if (!TextUtils.isEmpty(localDisplayContent)
            && localDisplayContent != remoteContent
        ) {
            LocalOriginalTextContent(remoteContent, localDisplayContent!!)
        } else {
            WKTextContent(remoteContent)
        }

        // 显式目标发送（例如发送前翻译）必须只使用任务创建时捕获的 @ 信息。
        // null 在这里表示“原消息没有 @”，不能回退读取切换后新会话的输入框。
        val list = if (explicitTarget) mentionUids else mentionUids ?: editText.allUIDs
        if (list != null && list.isNotEmpty()) {
            val mMentionInfo = WKMentionInfo()
            val uidList: MutableList<String> = ArrayList()
            var i = 0
            val size = list.size
            while (i < size) {
                if (list[i].equals("-1", ignoreCase = true)) {
                    textMsgModel.mentionAll = 1
                } else {
                    uidList.add(list[i])
                }
                i++
            }
            mMentionInfo.uids = uidList
            textMsgModel.mentionInfo = mMentionInfo
        }
        // 同上，显式目标发送不能混入当前输入框的实体（@、链接等）。
        textMsgModel.entities = if (explicitTarget) entities else entities ?: editText.allEntity
        if (reply != null) {
            textMsgModel.reply = reply
        }

        if (explicitTarget) {
            iConversationContext.sendMessageToChannel(textMsgModel, targetChannelId, targetChannelType)
        } else {
            iConversationContext.sendMessage(textMsgModel)
        }
        // 发送前翻译在入队时已经清空了当时那条输入。回调完成时用户可能已经
        // 开始输入下一条，显式目标任务绝不能再次清空当前输入框。
        if (targetIsActive && !explicitTarget) {
            editText.text = null
            updateSendButtonMode()
            lastInputTime = 0
            closeChatReplyPanel()
        }
    }

    private fun triggerMediaPickerDirect() {
        val adapter = toolBarAdapter ?: return
        for (index in adapter.data.indices) {
            val menu = adapter.getItem(index) ?: continue
            val sid = menu.sid ?: ""
            val lowerSid = sid.lowercase(Locale.getDefault())
            if ((lowerSid.contains("album") || lowerSid.contains("image") || lowerSid.contains("photo") || lowerSid.contains("media") || lowerSid.contains("video")) &&
                !lowerSid.contains("card") && !menu.isDisable
            ) {
                toolBarClick(menu, index, adapter)
                return
            }
        }
        triggerMorePanelAndAutoOpenMedia()
    }

    private fun findToolBarPosition(sid: String): Int {
        val adapter = toolBarAdapter ?: return -1
        for (index in adapter.data.indices) {
            if (adapter.getItem(index)?.sid == sid) return index
        }
        return -1
    }

    private fun triggerToolBarBySid(sid: String): Boolean {
        val adapter = toolBarAdapter ?: return false
        val position = findToolBarPosition(sid)
        if (position < 0 || position >= adapter.data.size) return false
        val menu = adapter.getItem(position) ?: return false
        if (menu.isDisable) return false
        if (sid == "wk_chat_toolbar_voice") {
            checkPermission(iConversationContext.chatActivity, menu, position, adapter)
        } else {
            toolBarClick(menu, position, adapter)
        }
        return true
    }

    private fun triggerMorePanelAndAutoOpenMedia() {
        val adapter = toolBarAdapter ?: return
        val position = findToolBarPosition("wk_chat_toolbar_more")
        if (position < 0 || position >= adapter.data.size) return
        val menu = adapter.getItem(position) ?: return
        if (menu.isDisable) return
        val requestChannelId = activeChannelId
        val requestChannelType = activeChannelType
        val requestGeneration = activeSessionGeneration
        toolBarClick(menu, position, adapter)
        parentView.postDelayed({
            if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) {
                return@postDelayed
            }
            if (!autoClickMediaAction(moreLayout)) {
                WKToastUtils.getInstance().showToast(iConversationContext.chatActivity.getString(R.string.chat_ai_media_not_found))
            }
        }, 120)
    }

    private fun autoClickMediaAction(root: View?): Boolean {
        if (root == null || root.visibility != View.VISIBLE) return false
        val text = when (root) {
            is TextView -> root.text?.toString() ?: ""
            else -> root.contentDescription?.toString() ?: ""
        }.lowercase(Locale.getDefault())
        val isMedia = text.contains("相册") || text.contains("图片") || text.contains("照片") ||
            text.contains("视频") || text.contains("拍摄") || text.contains("album") ||
            text.contains("photo") || text.contains("image") || text.contains("video")
        val isBlocked = text.contains("名片") || text.contains("联系人") || text.contains("card") || text.contains("contact")
        if (isMedia && !isBlocked) {
            findClickableParent(root).performClick()
            return true
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (autoClickMediaAction(root.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun findClickableParent(view: View): View {
        var current: View = view
        var parent = current.parent
        while (parent is View && !current.isClickable) {
            current = parent
            parent = current.parent
        }
        return current
    }

    private fun ensureVoiceRecordOverlay(): LinearLayout {
        voiceRecordOverlay?.let { return it }
        val overlay = LinearLayout(iConversationContext.chatActivity)
        overlay.orientation = LinearLayout.HORIZONTAL
        overlay.gravity = Gravity.CENTER_VERTICAL
        overlay.setPadding(
            AndroidUtilities.dp(14f),
            AndroidUtilities.dp(5f),
            AndroidUtilities.dp(12f),
            AndroidUtilities.dp(5f)
        )
        overlay.visibility = View.GONE
        overlay.background = createVoiceOverlayBg(false)
        overlay.elevation = AndroidUtilities.dp(10f).toFloat()

        val cancelTv = AppCompatTextView(iConversationContext.chatActivity)
        cancelTv.text = "‹ 左滑取消"
        cancelTv.setTextColor(Color.parseColor("#64748B"))
        cancelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        cancelTv.gravity = Gravity.CENTER_VERTICAL
        cancelTv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val cancelLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        cancelLp.rightMargin = AndroidUtilities.dp(10f)
        overlay.addView(cancelTv, cancelLp)

        val dotTv = AppCompatTextView(iConversationContext.chatActivity)
        dotTv.text = "●"
        dotTv.setTextColor(Color.parseColor("#EF4444"))
        dotTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        dotTv.gravity = Gravity.CENTER
        overlay.addView(dotTv, LinearLayout.LayoutParams(AndroidUtilities.dp(14f), LinearLayout.LayoutParams.MATCH_PARENT))

        val timeTv = AppCompatTextView(iConversationContext.chatActivity)
        timeTv.text = "00:00"
        timeTv.setTextColor(Color.parseColor("#111827"))
        timeTv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        timeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val timeLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        timeLp.rightMargin = AndroidUtilities.dp(12f)
        overlay.addView(timeTv, timeLp)

        val waveLayout = LinearLayout(iConversationContext.chatActivity)
        waveLayout.orientation = LinearLayout.HORIZONTAL
        waveLayout.gravity = Gravity.CENTER_VERTICAL
        voiceWaveBars.clear()
        for (i in 0 until 12) {
            val bar = View(iConversationContext.chatActivity)
            bar.background = GradientDrawable().apply {
                cornerRadius = AndroidUtilities.dp(2f).toFloat()
                setColor(Theme.colorAccount)
            }
            val barLp = LinearLayout.LayoutParams(AndroidUtilities.dp(2f), AndroidUtilities.dp(12f))
            barLp.leftMargin = AndroidUtilities.dp(2f)
            barLp.rightMargin = AndroidUtilities.dp(2f)
            waveLayout.addView(bar, barLp)
            voiceWaveBars.add(bar)
        }
        overlay.addView(waveLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            AndroidUtilities.dp(46f),
            Gravity.CENTER_VERTICAL
        )
        lp.leftMargin = AndroidUtilities.dp(8f)
        lp.rightMargin = AndroidUtilities.dp(8f)
        panelView.addView(overlay, lp)

        voiceRecordOverlay = overlay
        voiceRecordCancelTv = cancelTv
        voiceRecordDotTv = dotTv
        voiceRecordTimeTv = timeTv
        voiceRecordWaveLayout = waveLayout
        return overlay
    }

    private fun createVoiceOverlayBg(canceling: Boolean): GradientDrawable {
        return if (canceling) {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FFFFF1F2"), Color.parseColor("#FFFEE2E2"))
            ).apply {
                cornerRadius = AndroidUtilities.dp(999f).toFloat()
                setStroke(AndroidUtilities.dp(1f), Color.parseColor("#FFFCA5A5"))
            }
        } else {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FAFFFFFF"), Color.parseColor("#EEF7FBFF"), Color.parseColor("#F5F3E8FF"))
            ).apply {
                cornerRadius = AndroidUtilities.dp(999f).toFloat()
                setStroke(AndroidUtilities.dp(1f), Color.parseColor("#EFFFFFFF"))
            }
        }
    }

    private fun showVoiceRecordUi() {
        voiceRecordStartMs = System.currentTimeMillis()
        voiceCanceling = false
        ensureVoiceRecordOverlay().visibility = View.VISIBLE
        updateVoiceRecordUi(false)
        startVoiceUiTimer()
    }

    private fun hideVoiceRecordUi() {
        stopVoiceUiTimer()
        voiceRecordOverlay?.visibility = View.GONE
        voiceRecordStartMs = 0L
    }

    private fun startVoiceUiTimer() {
        stopVoiceUiTimer()
        val runnable = object : Runnable {
            override fun run() {
                if (voiceHolding && voiceRecordOverlay?.visibility == View.VISIBLE) {
                    updateVoiceRecordUi(voiceCanceling)
                    voiceUiHandler.postDelayed(this, 180L)
                }
            }
        }
        voiceUiRunnable = runnable
        voiceUiHandler.post(runnable)
    }

    private fun stopVoiceUiTimer() {
        voiceUiRunnable?.let { voiceUiHandler.removeCallbacks(it) }
        voiceUiRunnable = null
    }

    private fun updateVoiceRecordUi(canceling: Boolean) {
        val elapsed = if (voiceRecordStartMs > 0) System.currentTimeMillis() - voiceRecordStartMs else 0L
        val totalSeconds = (elapsed / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        voiceRecordTimeTv?.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        val color = if (canceling) Color.parseColor("#EF4444") else Theme.colorAccount
        val textColor = if (canceling) Color.parseColor("#EF4444") else Color.parseColor("#64748B")
        voiceRecordDotTv?.setTextColor(Color.parseColor("#EF4444"))
        voiceRecordCancelTv?.text = if (canceling) "松手取消" else "‹ 左滑取消"
        voiceRecordCancelTv?.setTextColor(textColor)
        voiceRecordOverlay?.background = createVoiceOverlayBg(canceling)
        val frame = voiceWaveFrames[((elapsed / 180L) % voiceWaveFrames.size).toInt()]
        for (i in voiceWaveBars.indices) {
            val bar = voiceWaveBars[i]
            val lp = bar.layoutParams as LinearLayout.LayoutParams
            lp.height = AndroidUtilities.dp(frame[i % frame.size].toFloat())
            bar.layoutParams = lp
            bar.background = GradientDrawable().apply {
                cornerRadius = AndroidUtilities.dp(2f).toFloat()
                setColor(color)
            }
            bar.alpha = if (canceling) 0.95f else 0.55f + ((i % 4) * 0.12f)
        }
    }

    private fun handleVoiceTouch(event: MotionEvent): Boolean {
        if (hasInputText()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                voiceDownX = event.rawX
                voiceCanceling = false
                voiceHolding = true
                voiceForwardTarget = null
                voiceLastMoveEvent?.recycle()
                voiceLastMoveEvent = null
                sendIV.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val desc = String.format(
                    iConversationContext.chatActivity.getString(R.string.microphone_permissions_des),
                    iConversationContext.chatActivity.getString(R.string.app_name)
                )
                val requestChannelId = activeChannelId
                val requestChannelType = activeChannelType
                val requestGeneration = activeSessionGeneration
                WKPermissions.getInstance().checkPermissions(object : WKPermissions.IPermissionResult {
                    override fun onResult(result: Boolean) {
                        if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) return
                        if (result && voiceHolding) {
                            if (!tryClaimDirectVoiceOwner()) {
                                voiceHolding = false
                                hideVoiceRecordUi()
                                return
                            }
                            showVoiceRecordUi()
                            try {
                                WKVoiceViewManager.getInstance().startDirectRecord(iConversationContext)
                                directVoiceStarted = true
                            } catch (e: Exception) {
                                directVoiceStarted = false
                                releaseDirectVoiceOwner()
                                voiceHolding = false
                                hideVoiceRecordUi()
                                Log.e("ChatPanelManager", "start direct record failed", e)
                            }
                        } else if (!result) {
                            voiceHolding = false
                            hideVoiceRecordUi()
                        }
                    }

                    override fun clickResult(isCancel: Boolean) {
                        if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) return
                        if (isCancel) {
                            voiceHolding = false
                            hideVoiceRecordUi()
                        }
                    }
                }, iConversationContext.chatActivity, desc, Manifest.permission.RECORD_AUDIO)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!voiceHolding) return true
                val cancelNow = voiceDownX - event.rawX > voiceCancelDistance
                if (cancelNow != voiceCanceling) {
                    voiceCanceling = cancelNow
                    updateVoiceRecordUi(voiceCanceling)
                    sendIV.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (voiceHolding && directVoiceStarted && isDirectVoiceOwner()) {
                    val shouldCancel = voiceCanceling || event.actionMasked == MotionEvent.ACTION_CANCEL
                    try {
                        WKVoiceViewManager.getInstance().finishDirectRecord(shouldCancel)
                    } catch (e: Exception) {
                        Log.e("ChatPanelManager", "finish direct record failed", e)
                    }
                }
                directVoiceStarted = false
                releaseDirectVoiceOwner()
                voiceHolding = false
                voiceForwardTarget = null
                voiceLastMoveEvent?.recycle()
                voiceLastMoveEvent = null
                voiceCanceling = false
                hideVoiceRecordUi()
                return true
            }
        }
        return true
    }

    private fun installKeyboardSendAction() {
        // 让手机输入法右下角尽量显示“发送/Send”，并兼容部分输入法只插入换行的情况。
        editText.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        editText.setOnEditorActionListener { _, actionId, event ->
            val isSendAction = actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO
            val isEnterUp = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_UP &&
                !event.isShiftPressed
            if (isSendAction || isEnterUp) {
                submitTextFromKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun submitTextFromKeyboard(): Boolean {
        if (handlingKeyboardSend) return true
        handlingKeyboardSend = true
        try {
            val raw = editText.text?.toString() ?: ""
            val cleaned = raw.trimEnd('\r', '\n')
            if (cleaned != raw) {
                editText.setText(cleaned)
                editText.setSelection(cleaned.length)
            }
            if (!TextUtils.isEmpty(StringUtils.replaceBlank(cleaned))) {
                sendInputText()
            }
            return true
        } finally {
            handlingKeyboardSend = false
        }
    }

    private fun shouldSubmitInsertedNewLine(s: Editable): Boolean {
        if (handlingKeyboardSend) return false
        val text = s.toString()
        return text.endsWith("\n") || text.endsWith("\r")
    }

    private fun findVoiceRecordTarget(root: View?): View? {
        if (root == null || root.visibility != View.VISIBLE) return null
        val label = when (root) {
            is TextView -> root.text?.toString() ?: ""
            else -> root.contentDescription?.toString() ?: ""
        }.lowercase(Locale.getDefault())
        val looksLikeRecord = label.contains("按住") || label.contains("说话") ||
            label.contains("录音") || label.contains("hold") || label.contains("record")
        if (looksLikeRecord && root.isShown) return findClickableParent(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findVoiceRecordTarget(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun dispatchVoiceEventToTarget(target: View, source: MotionEvent, action: Int) {
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val x = (target.width / 2f).coerceAtLeast(1f)
        val y = if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_CANCEL) {
            target.height / 2f
        } else {
            target.height / 2f
        }
        val ev = MotionEvent.obtain(source.downTime, source.eventTime, action, x, y, source.metaState)
        target.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun registerEmojiEndpoint() {
        synchronized(EMOJI_ENDPOINT_LOCK) {
            if (emojiEndpointRegistration != null) return
            val registration = EmojiEndpointRegistration()
            emojiEndpointRegistration = registration
            EMOJI_ENDPOINT_STACK.add(registration)
            EndpointManager.getInstance().setMethod("emoji_click") { value ->
                handleEmojiClick(value)
            }
        }
    }

    private fun unregisterEmojiEndpoint() {
        synchronized(EMOJI_ENDPOINT_LOCK) {
            val registration = emojiEndpointRegistration ?: return
            registration.active = false
            emojiEndpointRegistration = null

            // EndpointManager.remove(sid) 只删除最后注册的同名处理器。
            // 底层 ChatActivity 先销毁时只能标记失效，等顶部页面销毁后再按栈顺序清理。
            while (EMOJI_ENDPOINT_STACK.isNotEmpty()) {
                val lastIndex = EMOJI_ENDPOINT_STACK.lastIndex
                val last = EMOJI_ENDPOINT_STACK[lastIndex]
                if (last.active) break
                EndpointManager.getInstance().remove("emoji_click")
                EMOJI_ENDPOINT_STACK.removeAt(lastIndex)
            }
        }
    }

    private fun handleEmojiClick(value: Any?): Any? {
        if (destroyed) return null
        val emojiName = value as? String ?: return null
        if (TextUtils.isEmpty(emojiName)) {
            editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            return null
        }

        val safePosition = safeEditSelectionStart()
        // EditText 未获得焦点或刚恢复布局时 selectionStart 可能为 -1。
        // MoonUtil 内部会直接按当前 selectionStart 插入，必须先修正光标。
        if (editText.selectionStart != safePosition) {
            editText.setSelection(safePosition)
        }
        MoonUtil.addEmojiSpan(editText, emojiName, iConversationContext.chatActivity)
        val maxPosition = editText.text?.length ?: 0
        editText.setSelection((safePosition + emojiName.length).coerceAtMost(maxPosition))
        return null
    }

    private fun initListener() {
        panelView.setOnClickListener {

        }
        registerEmojiEndpoint()
        SingleClickUtil.onSingleClick(markdownIv) {
            // 富文本入口已隐藏。
        }
        sendIV.setOnClickListener {
            if (hasInputText()) {
                sendInputText()
            } else {
                WKToastUtils.getInstance().showToast(iConversationContext.chatActivity.getString(R.string.chat_voice_long_press_hint))
            }
        }
        sendIV.setOnTouchListener { _, event ->
            if (hasInputText()) false else handleVoiceTouch(event)
        }
        installKeyboardSendAction()
        editText.addTextChangedListener(object : TextWatcher {
//            var linesCount = 0

            // var lastHeight = AndroidUtilities.dp(35f)
            var start = 0
            var count = 0
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                this.start = start
                this.count = count
                if (pendingDeepSeekReplyText.isNotEmpty()
                    && s.toString() != pendingDeepSeekReplyText) {
                    clearPendingDeepSeekReply()
                }
                updateSendButtonMode()
                if (!TextUtils.isEmpty(s.toString())) {
                    val content = StringUtils.replaceBlank(s.toString())
//                    val content = s.toString().replace("\\s*|\r|\n|\t", "")
                    if (!isShowSendBtn && !TextUtils.isEmpty(content)) {
                        CommonAnim.getInstance().animImageView(sendIV)
                    }
                    isShowSendBtn = true
                    if (TextUtils.isEmpty(content)) {
                        sendIV.colorFilter = PorterDuffColorFilter(
                            ContextCompat.getColor(
                                iConversationContext.chatActivity, R.color.popupTextColor
                            ), PorterDuff.Mode.MULTIPLY
                        )
                    } else {
                        sendIV.colorFilter = PorterDuffColorFilter(
                            Theme.colorAccount, PorterDuff.Mode.MULTIPLY
                        )
                    }
                    CommonAnim.getInstance().showOrHide(markdownIv, false, true)
                    if (flame == 1) {
                        CommonAnim.getInstance().showOrHide(flameIV, false, true)
                    }
                } else {
                    CommonAnim.getInstance().showOrHide(markdownIv, false, true)
                    if (flame == 1) {
                        CommonAnim.getInstance().showOrHide(flameIV, true, true)
                    }
                    isShowSendBtn = false
                    sendIV.colorFilter = PorterDuffColorFilter(
                        ContextCompat.getColor(
                            iConversationContext.chatActivity, R.color.popupTextColor
                        ), PorterDuff.Mode.MULTIPLY
                    )
                }
                val selectionStart = editText.selectionStart
                val selectionEnd = editText.selectionEnd
                if (selectionEnd != selectionStart || selectionStart <= 0) {
                    hideRemindView()
                    return
                }

                var text = s.toString().substring(start, start + count)
                if (start + count == s.toString().length) {
                    if (count == 0 || TextUtils.isEmpty(text)) {
                        // 删除了字符串
//                        text = s.toString().substring(0, selectionStart)
                        if (s.toString().lastIndexOf("@") >= 0) {
                            val index = s.toString().lastIndexOf("@")
                            val remindText = s.toString().substring(index, s.toString().length)
                            if (!TextUtils.isEmpty(remindText)) text = remindText
                        }
                    } else {
                        if (s.toString().startsWith("@") && s.toString().contains(" ")) {
                            text = s.toString()
                        } else {
                            if (s.toString().lastIndexOf("@") >= 0) {
                                val index = s.toString().lastIndexOf("@")
                                val remindText = s.toString().substring(index, s.toString().length)
                                if (!TextUtils.isEmpty(remindText)) text = remindText
                            }
                        }
                    }
                } else {
                    val temp = s.toString().substring(0, start)
                    if (!TextUtils.isEmpty(temp) && temp.contains("@")) {
                        val index = temp.lastIndexOf("@")

                        if (count == 0) {
                            // 点击删除
                            val endIndex = editText.selectionEnd
                            val str = s.toString().substring(index, endIndex) + text
                            if (!TextUtils.isEmpty(str)) {
                                text = str
                            }
                        } else {
                            text = s.toString().substring(index, index + count) + text
                        }
                    }
                }
                //  text = s.toString().substring(0, selectionStart)
                if (!TextUtils.isEmpty(text) && (mentionDisplay(text) || text.startsWith("@"))) {
                    // 搜索成员
                    searchInputText(text)
                } else {
                    hideRemindView()
                    hideRobotView()
                    CommonAnim.getInstance().showOrHide(hitTv, false, true)
//                    CommonAnim.getInstance()
//                        .showOrHide(closeSearchLottieIV, false, true)
                    CommonAnim.getInstance().showOrHide(sendIV, true, true)
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // 保存当前高度
                lastHeight = editText.height
            }

            override fun afterTextChanged(s: Editable) {
                if (shouldSubmitInsertedNewLine(s)) {
                    submitTextFromKeyboard()
                    return
                }
                if (handlingKeyboardSend) return
                updateEditHeight()
                MoonUtil.replaceEmoticons(
                    iConversationContext.chatActivity,
                    s, start, count
                )
                if (s.toString().length <= 2 && !s.toString().startsWith("@")) {
                    //搜索表情
                    EndpointManager.getInstance()
                        .invoke(
                            "search_chat_edit_content",
                            SearchChatEditStickerMenu(
                                iConversationContext.chatActivity,
                                s.toString(),
                                parentView
                            ) { editText.text = null })
                } else {
                    EndpointManager.getInstance().invoke("hide_search_chat_edit_view", null)
                }

                //发送'正在输入'命令
                val nowTime = WKTimeUtils.getInstance().currentSeconds
                if (nowTime - lastInputTime >= 5 && !TextUtils.isEmpty(s)) {
                    var isSend = true
                    if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP) {
                        val mChannelMember =
                            WKIM.getInstance().channelMembersManager.getMember(
                                iConversationContext.chatChannelInfo.channelID,
                                iConversationContext.chatChannelInfo.channelType,
                                loginUID
                            )
                        if (mChannelMember == null || mChannelMember.isDeleted == 1 || mChannelMember.status != 1) {
                            isSend = false
                        }
                    } else {
                        val channel = iConversationContext.chatChannelInfo
                        if (channel?.localExtra != null) {
                            var beDeleted = 0
                            var beBlacklist = 0
                            if (channel.localExtra.containsKey(WKChannelExtras.beBlacklist)) {
                                beBlacklist =
                                    channel.localExtra[WKChannelExtras.beBlacklist] as Int
                            }
                            if (channel.localExtra.containsKey(WKChannelExtras.beDeleted)) {
                                beDeleted =
                                    channel.localExtra[WKChannelExtras.beDeleted] as Int
                            }
                            if (beDeleted == 1 || beBlacklist == 1) isSend = false
                        }
                    }
                    if (isSend) {
                        MsgModel.getInstance().typing(
                            iConversationContext.chatChannelInfo.channelID,
                            iConversationContext.chatChannelInfo.channelType,
                        )
                    }
                    lastInputTime = WKTimeUtils.getInstance().currentSeconds
                }
            }
        })
    }

    private
    fun searchInputText(content: String) {
        var isSearchGroupMembers = true
        if (content.startsWith("@")) {
            val chars: CharArray = content.toCharArray()
            var index = 0
            var i = 0
            val size = chars.size
            while (i < size) {
                if (chars[i] == " "[0]) {
                    index = i
                    break
                }
                i++
            }
            var username: String = content
            if (index != 0) {
                username = content.substring(0, index + 1)
            }

            // 搜索机器人
            username = username.replace("@".toRegex(), "").replace(" ".toRegex(), "")
            if (!TextUtils.isEmpty(username)) {
//                if (!content.endsWith("@")) {
//                    isSearchGroupMembers = false
//                }
                val mRobot =
                    WKIM.getInstance().robotManager.getWithUsername(username.lowercase(Locale.getDefault()))
                if (mRobot != null && index != 0 && editText.text.toString()
                        .startsWith("@") && editText.text.toString()
                        .startsWith("@$username ")
                ) {
                    isSearchGroupMembers = false
                    hideRemindView()
                    inlineQueryOffset = ""
                    val searchKey: String =
                        content.substring(index, content.length).replace(" ".toRegex(), "")
                    if (!TextUtils.isEmpty(searchKey) && mRobot.username.lowercase(Locale.getDefault())
                            .equals(
                                "gif",
                                ignoreCase = true
                            )
                    ) {

                        CommonAnim.getInstance().showOrHide(hitTv, false, true)
                        inlineQueryOffset = ""
//                        if (TextUtils.isEmpty(searchKey)) {
//                            if (this.robotGifRecyclerView!!.visibility != View.GONE) {
//                                CommonAnim.getInstance().hideTop2Bottom(this.robotGifRecyclerView)
//                            }
//                        } else
                        searchRobotGif(searchKey, username)
                    } else {
                        val mTextPaint: TextPaint = editText.paint
                        val textWidth = mTextPaint.measureText(editText.text.toString())
                        val searchNameChars: CharArray = content.toCharArray()
                        var searchNameCharsIndex = 0
                        var count = 0
                        while (searchNameCharsIndex < searchNameChars.size) {
                            if (searchNameChars[searchNameCharsIndex] == " "[0]) {
                                count++
                                if (count > 1)
                                    break
                            }
                            searchNameCharsIndex++
                        }
                        if (count == 1) {
                            hitTv.hint = mRobot.placeholder
                            CommonAnim.getInstance().showOrHide(hitTv, true, true)
                            val lp = RelativeLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lp.topMargin = AndroidUtilities.dp(8f)
                            lp.leftMargin = textWidth.toInt() + AndroidUtilities.dp(10f)
                            hitTv.layoutParams = lp
                        } else {
                            CommonAnim.getInstance().showOrHide(hitTv, false, true)
                        }
                    }
                    CommonAnim.getInstance().showOrHide(sendIV, false, true)
//                    CommonAnim.getInstance().showOrHide(closeSearchLottieIV, true, true)

                } else {
                    CommonAnim.getInstance().showOrHide(hitTv, false, true)
//                    CommonAnim.getInstance()
//                        .showOrHide(closeSearchLottieIV, false, true)
                    CommonAnim.getInstance().showOrHide(sendIV, true, true)

                    val list: MutableList<WKRobotEntity> = ArrayList()
                    list.add(
                        WKRobotEntity(
                            "",
                            username,
                            0
                        )
                    )
                    WKRobotModel.getInstance().syncRobot(2, list)
                    hideRobotView()
                }
            } else {
                CommonAnim.getInstance().showOrHide(hitTv, false, true)
//                CommonAnim.getInstance().showOrHide(closeSearchLottieIV, false, true)
                CommonAnim.getInstance().showOrHide(sendIV, true, true)
                hideRobotView()
            }
        }
        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP && isSearchGroupMembers) {
            val memberAdapter = remindMemberAdapter ?: return
            val memberRecyclerView = remindRecycleView ?: return
            val headerView = remindHeaderView ?: return
            var remindSearchKey: String = content

            remindSearchKey = remindSearchKey.replace("@".toRegex(), "")
//            val keyword = mentionEnd(content)
            if (!TextUtils.isEmpty(remindSearchKey) && (content == "@" || content.endsWith("@"))) {
                memberAdapter.onNormal()
            } else {
                memberAdapter.onSearch(remindSearchKey)
            }
            memberRecyclerView.scrollToPosition(0)
            val min =
                (memberAdapter.itemCount - memberAdapter.headerLayoutCount).coerceAtMost(3)
            var height = 40f
            if (memberAdapter.data.size > 3) height = 48f

            headerView.layoutParams.height = parentView.top - AndroidUtilities.dp((min * height))
            memberRecyclerView.setHeaderViewY(headerView.layoutParams.height.toFloat())
            if (memberRecyclerView.isGone) CommonAnim.getInstance().showBottom2Top(memberRecyclerView)
        }
    }

    private fun updateEditHeight() {
        val layout = editText.layout
        if (layout == null) {
            return
        }
        // 将高度更新和动画放到post中，确保Layout已更新
//        editText.post(Runnable {
//            val layout = editText.layout
//            if (layout == null) {
//                return@Runnable
//            }
        val lineCount = layout.lineCount
        // 计算目标行数（不超过MAX_LINES）
        val targetLines = min(
            lineCount.toDouble(),
            maxLines.toDouble()
        ).toInt()
        // 只有当目标行数改变时才执行动画或调整高度
        if (targetLines != lastTargetLines) {
            // 计算精确的高度
            var newHeight = layout.getLineTop(targetLines) +
                    editText.getCompoundPaddingTop() +
                    editText.getCompoundPaddingBottom()
            if (newHeight < AndroidUtilities.dp(35f)) {
                newHeight = AndroidUtilities.dp(35f)
            }
            // 创建高度动画
            val animator = ValueAnimator.ofInt(lastHeight, newHeight)
            animator.setDuration(200) // 动画持续时间
            animator.interpolator = AccelerateDecelerateInterpolator()

            animator.addUpdateListener(ValueAnimator.AnimatorUpdateListener { animation: ValueAnimator? ->
                val animatedValue = animation!!.getAnimatedValue() as Int
                val params = editText.layoutParams
                params.height = animatedValue
                editText.setLayoutParams(params)
                iConversationContext.chatRecyclerViewScrollToEnd()
            })
            animator.start()
            // 更新上一次的目标行数
            lastTargetLines = targetLines

        } else if (lineCount <= maxLines) {
            // 如果行数未变且在限制内，确保高度正确（无动画，作为备用检查）
            var correctHeight = layout.getLineTop(targetLines) +
                    editText.getCompoundPaddingTop() +
                    editText.getCompoundPaddingBottom()
            if (correctHeight < AndroidUtilities.dp(35f)) {
                correctHeight = AndroidUtilities.dp(35f)
            }
            if (editText.height != correctHeight) {
                val params = editText.layoutParams
                params.height = correctHeight
                editText.setLayoutParams(params)
                iConversationContext.chatRecyclerViewScrollToEnd()
            }
        }
//        })
    }

    private fun searchRobotGif(searchKey: String, username: String) {
        this.searchKey = searchKey
        this.username = username
        val requestChannelId = activeChannelId
        val requestChannelType = activeChannelType
        val requestGeneration = activeSessionGeneration
        val requestOffset = inlineQueryOffset
        val requestToken = ++robotQueryToken
        WKRobotModel.getInstance().inlineQuery(
            requestOffset,
            username,
            searchKey,
            requestChannelId,
            requestChannelType
        ) { _: Int, _: String?, result: WKRobotInlineQueryResult? ->
            if (requestToken != robotQueryToken
                || !isActiveSession(requestChannelId, requestChannelType, requestGeneration)
                || this.searchKey != searchKey
                || this.username != username
            ) return@inlineQuery

            if (TextUtils.isEmpty(requestOffset)) {
                robotGifRecyclerView?.scrollToPosition(0)
                robotGifHeaderView?.layoutParams?.height = parentView.top - AndroidUtilities.dp(100f)
                val headerHeight = robotGifHeaderView?.layoutParams?.height ?: 0
                robotGifRecyclerView?.setHeaderViewY(headerHeight.toFloat())
            }
            val results = result?.results.orEmpty()
            // 即使最后一页为空也必须清空 next_offset，否则滚到底部会无限重试同一页。
            inlineQueryOffset = result?.next_offset.orEmpty()
            if (results.isNotEmpty()) {
                if (TextUtils.isEmpty(requestOffset)) {
                    robotGIFAdapter?.setList(results)
                } else {
                    robotGIFAdapter?.addData(results)
                }
                resetData()
                robotGifRecyclerView?.let { recyclerView ->
                    if (recyclerView.visibility != View.VISIBLE) {
                        CommonAnim.getInstance().showBottom2Top(recyclerView)
                    }
                }
            }
        }
    }


    private fun resetData() {
        val adapter = robotGIFAdapter ?: return
        // 占位项可能连续出现，正序 removeAt 会跳过后一个并让空占位逐页累积。
        for (index in adapter.data.lastIndex downTo 0) {
            if (adapter.data[index].isNull) adapter.removeAt(index)
        }
        val num = adapter.data.size % 3
        if (num != 0) {
            var count = 3 - num
            while (count > 0) {
                val sticker = WKRobotGIFEntity()
                sticker.isNull = true
                adapter.addData(sticker)
                count--
            }
        }
    }

    fun hideRemindView() {
        val recyclerView = remindRecycleView ?: return
        if (iConversationContext.chatChannelInfo.channelType == WKChannelType.GROUP
            && recyclerView.visibility != View.GONE
        ) {
            CommonAnim.getInstance().hideTop2Bottom(recyclerView)
        }
    }

    private fun hideRobotView() {
        if (robotGifRecyclerView!!.visibility != View.GONE) {
            CommonAnim.getInstance().hideTop2Bottom(robotGifRecyclerView!!)
//            initRobotGIF(iConversationContext!!)
            robotGifHeaderView!!.layoutParams.height =
                parentView.top - AndroidUtilities.dp(100f)
            this.robotGifRecyclerView!!.setHeaderViewY(robotGifHeaderView!!.layoutParams.height.toFloat())
        }
    }


    fun resetToolBar() {
        for (index in toolBarAdapter!!.data.indices) {
            toolBarAdapter!!.getItem(index).isDisable =
                false
            toolBarAdapter!!.getItem(index).isSelected = false
        }
        toolBarAdapter!!.notifyItemRangeChanged(0, toolBarAdapter!!.itemCount)
    }

    private fun getEmojiLayout(): View {
        val width = AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(30f) * 8
        val normalList = EmojiManager.getInstance().getEmojiWithType("0_")
        val naturelList = EmojiManager.getInstance().getEmojiWithType("1_")
        val symbolsList = EmojiManager.getInstance().getEmojiWithType("2_")
        val list = ArrayList<EmojiEntry>()
        list.addAll(normalList)
        list.addAll(naturelList)
        list.addAll(symbolsList)
        val emojiLayout = LinearLayout(iConversationContext.chatActivity)
        val emojiAdapter = EmojiAdapter(list, width)
        val recyclerView = RecyclerView(iConversationContext.chatActivity)
        val emojiLayoutManager = GridLayoutManager(iConversationContext.chatActivity, 8)
        recyclerView.layoutManager = emojiLayoutManager
        recyclerView.adapter = emojiAdapter
        var height = WKConstants.getKeyboardHeight()
        if (height == 0) {
            height = AndroidUtilities.getScreenHeight() / 3
        }
        emojiLayout.addView(
            recyclerView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                (height / AndroidUtilities.density).toInt()
            )
        )

        emojiAdapter.setOnItemClickListener { adapter, _, position ->
            val emojiEntry = adapter.getItem(position) as? EmojiEntry
                ?: return@setOnItemClickListener
            val curPosition = safeEditSelectionStart()
            if (editText.selectionStart != curPosition) {
                editText.setSelection(curPosition)
            }
            MoonUtil.addEmojiSpan(editText, emojiEntry.text, iConversationContext.chatActivity)
            val maxPosition = editText.text?.length ?: 0
            editText.setSelection((curPosition + emojiEntry.text.length).coerceAtMost(maxPosition))
        }
        return emojiLayout
    }


    private fun checkPermission(
        activity: FragmentActivity, mChatToolBarMenu: ChatToolBarMenu,
        position: Int,
        adapter1: WKChatToolBarAdapter
    ) {
        val desc = String.format(
            activity.getString(R.string.microphone_permissions_des),
            activity.getString(R.string.app_name)
        )
        val requestChannelId = activeChannelId
        val requestChannelType = activeChannelType
        val requestGeneration = activeSessionGeneration
        WKPermissions.getInstance().checkPermissions(object : WKPermissions.IPermissionResult {
            override fun onResult(result: Boolean) {
                if (result && isActiveSession(requestChannelId, requestChannelType, requestGeneration)) {
                    toolBarClick(
                        mChatToolBarMenu,
                        position,
                        adapter1
                    )
                }
            }

            override fun clickResult(isCancel: Boolean) {}
        }, activity, desc, Manifest.permission.RECORD_AUDIO)
    }


    private fun toolBarClick(
        mChatToolBarMenu: ChatToolBarMenu,
        position: Int,
        adapter1: WKChatToolBarAdapter
    ) {
        //存在点击显示的view
        if (mChatToolBarMenu.bottomView != null) {
            if (mChatToolBarMenu.isSelected) {
                //已经选中就隐藏底部view弹起软键盘
                mChatToolBarMenu.isSelected = false
                SoftKeyboardUtils.getInstance().requestFocus(editText)
                SoftKeyboardUtils.getInstance()
                    .showSoftKeyBoard(iConversationContext.chatActivity, editText)
                helper.toKeyboardState()
                toolBarAdapter!!.notifyItemChanged(position)
            } else {
                var i = 0
                val size = toolBarAdapter!!.data.size
                while (i < size) {
                    toolBarAdapter!!.data[i].isSelected = false
                    i++
                }
                mChatToolBarMenu.isSelected = true
                adapter1.notifyItemRangeChanged(0, adapter1.data.size)
                if (!helper.isPanelState()) {
                    helper.toPanelState(R.id.emotionView)
                }
                moreLayout.removeAllViews()
                moreLayout.addView(
                    mChatToolBarMenu.bottomView,
                    LayoutHelper.createFrame(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.MATCH_PARENT.toFloat()
                    )
                )
                mChatToolBarMenu.bottomView.startAnimation(
                    loadAnimation(
                        iConversationContext
                    )
                )
                SoftKeyboardUtils.getInstance().loseFocus(editText)
                SoftKeyboardUtils.getInstance()
                    .hideInput(iConversationContext.chatActivity, editText)
            }
        }
        if (mChatToolBarMenu.iChatToolBarListener != null) mChatToolBarMenu.iChatToolBarListener.onChecked(
            true,
            iConversationContext
        )
    }

    private fun loadAnimation(iConversationContext: IConversationContext): Animation? {
        return AnimationUtils.loadAnimation(
            iConversationContext.chatActivity,
            R.anim.anim_add_child
        )
    }

    //相册有新的图片
    private fun showNewImgDialog(
        path: String,
        requestChannelId: String,
        requestChannelType: Byte,
        requestGeneration: Long
    ) {
        if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) return
        WKSharedPreferencesUtil.getInstance().putSP("new_img_path", path)
        val imageView = newImageLayout?.findViewWithTag<AppCompatImageView>("imageView")
        GlideUtils.getInstance().showImg(iConversationContext.chatActivity, path, imageView)
        imageView?.setOnClickListener {
            if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) return@setOnClickListener
            showNewImageListener(path)
            newImageLayout?.visibility = View.GONE
        }
        newImageLayout?.visibility = View.VISIBLE
    }

    private fun initMultipleChoiceView() {
        multipleChoiceView = LinearLayout(iConversationContext.chatActivity)
        multipleChoiceView?.visibility = View.GONE
        multipleChoiceView?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        panelView.addView(
            multipleChoiceView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 55f)
        )
        val forwardView = LinearLayout(iConversationContext.chatActivity)
        forwardView.orientation = LinearLayout.VERTICAL
        val deleteView = LinearLayout(iConversationContext.chatActivity)
        deleteView.orientation = LinearLayout.VERTICAL
        multipleChoiceView?.addView(
            forwardView,
            LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER)
        )
        multipleChoiceView?.addView(
            deleteView,
            LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER)
        )

        val forwardIV = AppCompatImageView(iConversationContext.chatActivity)
        forwardIV.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                iConversationContext.chatActivity, R.color.colorDark
            ), PorterDuff.Mode.MULTIPLY
        )
        forwardIV.setImageResource(R.mipmap.msg_forward)
        forwardView.addView(
            forwardIV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val forwardTV = AppCompatTextView(iConversationContext.chatActivity)
        forwardTV.text = iConversationContext.chatActivity.getString(R.string.base_forward)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_12)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        forwardTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)

        forwardTV.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.colorDark
            )
        )
        forwardView.addView(
            forwardTV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                0,
                3,
                0,
                0
            )
        )

        // 删除
        val deleteIV = AppCompatImageView(iConversationContext.chatActivity)
        deleteIV.setImageResource(R.mipmap.msg_delete)
        deleteIV.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                iConversationContext.chatActivity, R.color.colorDark
            ), PorterDuff.Mode.MULTIPLY
        )
        deleteView.addView(
            deleteIV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val deleteTV = AppCompatTextView(iConversationContext.chatActivity)
        deleteTV.text = iConversationContext.chatActivity.getString(R.string.delete)
        deleteTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        deleteTV.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.colorDark
            )
        )
        deleteView.addView(
            deleteTV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                0,
                3,
                0,
                0
            )
        )


        forwardView.tag = "forwardView"
        deleteTV.tag = "deleteTv"
        deleteIV.tag = "deleteIv"
        forwardIV.tag = "forwardIv"
        forwardTV.tag = "forwardTv"

        forwardView.setOnClickListener {
            val chatAdapter = iConversationContext.chatAdapter
            val bottomSheetItemList = ArrayList<BottomSheetItem>()
            bottomSheetItemList.add(
                BottomSheetItem(
                    iConversationContext.chatActivity.getString(R.string.merge_forward),
                    R.mipmap.msg_share,
                    object : BottomSheetItem.IBottomSheetClick {
                        override fun onClick() {

                            //合并转发
                            val forwardContent =
                                WKMultiForwardContent()
                            forwardContent.channelType =
                                iConversationContext.chatChannelInfo.channelType
                            val list: MutableList<WKMsg> =
                                ArrayList()
                            forwardContent.userList = ArrayList()
                            var i = 0
                            val itemCount: Int = chatAdapter.itemCount
                            while (i < itemCount) {
                                if (chatAdapter.getItem(i).isChecked) {
                                    list.add(chatAdapter.getItem(i).wkMsg)
                                    if (iConversationContext.chatChannelInfo.channelType == WKChannelType.PERSONAL) {
                                        var isAdd: Boolean
                                        if (forwardContent.userList.isEmpty()) {
                                            isAdd = true
                                        } else {
                                            isAdd = true
                                            for (j in forwardContent.userList.indices) {
                                                if ((!TextUtils.isEmpty(forwardContent.userList[j].channelID) && (forwardContent.userList[j].channelID == chatAdapter.getItem(
                                                        i
                                                    ).wkMsg.fromUID))
                                                ) {
                                                    isAdd = false
                                                    break
                                                }
                                            }
                                        }
                                        if (isAdd) {
                                            if (chatAdapter.getItem(i).wkMsg.from == null) {
                                                val mChannel = WKChannel()
                                                mChannel.channelID =
                                                    chatAdapter.getItem(i).wkMsg.fromUID
                                                chatAdapter.getItem(i).wkMsg.from = mChannel
                                            }
                                            forwardContent.userList.add(chatAdapter.getItem(i).wkMsg.from)
                                        }
                                    }
                                }
                                i++
                            }
                            forwardContent.msgList = list
                            EndpointManager.getInstance()
                                .invoke(
                                    EndpointSID.showChooseChatView,
                                    ChooseChatMenu(
                                        ChatChooseContacts { channelList: List<WKChannel>? ->
                                            if (!channelList.isNullOrEmpty()) {
                                                for (index in chatAdapter.data.indices) {
                                                    chatAdapter.getItem(index).isChoose = false
                                                    chatAdapter.getItem(index).isChecked = false
                                                }
                                                chatAdapter.notifyItemRangeChanged(
                                                    0,
                                                    chatAdapter.itemCount
                                                )


                                                for (mChannel: WKChannel in channelList) {
                                                    val option = WKSendOptions()
                                                    option.setting.receipt = mChannel.receipt
                                                    WKIM.getInstance().msgManager.sendWithOptions(
                                                        forwardContent,
                                                        mChannel,
                                                        option
                                                    )
                                                }
                                                WKToastUtils.getInstance()
                                                    .showToastNormal(
                                                        iConversationContext.chatActivity.getString(
                                                            R.string.is_forward
                                                        )
                                                    )

                                                for (index in toolBarAdapter!!.data.indices) {
                                                    toolBarAdapter!!.getItem(index).isDisable =
                                                        false
                                                }
                                                toolBarAdapter!!.notifyItemRangeChanged(
                                                    0,
                                                    toolBarAdapter!!.itemCount
                                                )
                                                multipleChoiceView?.visibility = View.GONE
                                                chatView.visibility = View.VISIBLE
                                                toolbarRecyclerView.visibility =
                                                    View.VISIBLE
                                                resetTitleViewListener()
                                            }
                                        },
                                        forwardContent
                                    )
                                )

                        }
                    })
            )
            bottomSheetItemList.add(
                BottomSheetItem(
                    iConversationContext.chatActivity.getString(R.string.item_forward),
                    R.mipmap.msg_forward,
                    object : BottomSheetItem.IBottomSheetClick {
                        override fun onClick() {

                            //逐条转发
                            val list: MutableList<WKMessageContent> =
                                ArrayList()
                            var i = 0
                            val itemCount: Int = chatAdapter.itemCount
                            while (i < itemCount) {
                                if (chatAdapter.getItem(i).isChecked) {
                                    if ((chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_TEXT
                                                ) || (chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_IMAGE
                                                ) || (chatAdapter.getItem(i).wkMsg.type == WKContentType.WK_GIF)
                                    ) list.add(chatAdapter.getItem(i).wkMsg.baseContentMsgModel) else {
                                        val textContent =
                                            WKTextContent(chatAdapter.getItem(i).wkMsg.baseContentMsgModel.displayContent)
                                        list.add(textContent)
                                    }
                                }
                                i++
                            }
                            if (list.isNotEmpty()) {
                                EndpointManager.getInstance()
                                    .invoke(
                                        EndpointSID.showChooseChatView,
                                        ChooseChatMenu(
                                            ChatChooseContacts { channelList: List<WKChannel>? ->
                                                val sendMsgEntityList: MutableList<SendMsgEntity> =
                                                    ArrayList()
                                                if (!channelList.isNullOrEmpty()) {
                                                    for (mChannel: WKChannel in channelList) {
                                                        for (index in list.indices) {
                                                            val option = WKSendOptions()
                                                            option.setting.receipt =
                                                                iConversationContext.chatChannelInfo.receipt
                                                            sendMsgEntityList.add(
                                                                SendMsgEntity(
                                                                    list[index], mChannel,
                                                                    option
                                                                )
                                                            )
                                                        }
                                                    }

                                                    WKSendMsgUtils.getInstance()
                                                        .sendMessages(sendMsgEntityList)
                                                    WKToastUtils.getInstance()
                                                        .showToastNormal(
                                                            iConversationContext.chatActivity.getString(
                                                                R.string.is_forward
                                                            )
                                                        )
                                                    for (index in chatAdapter.data.indices) {
                                                        chatAdapter.getItem(index).isChoose =
                                                            false
                                                        chatAdapter.getItem(index).isChecked =
                                                            false
                                                    }
                                                    chatAdapter.notifyItemRangeChanged(
                                                        0,
                                                        chatAdapter.itemCount
                                                    )
                                                    multipleChoiceView?.visibility =
                                                        View.GONE
                                                    chatView.visibility = View.VISIBLE
                                                    resetTitleViewListener()
                                                }
                                            },
                                            list
                                        )
                                    )
                            }

                        }
                    })
            )
            WKDialogUtils.getInstance().showBottomSheet(
                iConversationContext.chatActivity,
                iConversationContext.chatActivity.getString(R.string.base_forward),
                false,
                bottomSheetItemList
            )
        }

        deleteView.setOnClickListener {
            val chatAdapter = iConversationContext.chatAdapter
            val list: MutableList<WKMsg> = ArrayList()
            val ids = mutableListOf<String>()
            run {
                var i = 0
                val itemCount: Int = chatAdapter.itemCount
                while (i < itemCount) {
                    if (chatAdapter.getItem(i).isChecked) {
                        list.add(chatAdapter.getItem(i).wkMsg)
                        ids.add(chatAdapter.getItem(i).wkMsg.clientMsgNO)
                    }
                    i++
                }
            }
            if (list.isNotEmpty()) {
                WKDialogUtils.getInstance().showDialog(
                    iConversationContext.chatActivity,
                    iConversationContext.chatActivity.getString(R.string.delete_messages),
                    iConversationContext.chatActivity.getString(R.string.delete_select_msg),
                    true,
                    "",
                    iConversationContext.chatActivity.getString(R.string.delete),
                    0,
                    ContextCompat.getColor(iConversationContext.chatActivity, R.color.red)
                ) { index: Int ->
                    if (index == 1) {
                        WKIM.getInstance().msgManager.deleteWithClientMsgNos(ids)
                        MsgModel.getInstance().deleteMsg(list, null)
                        resetTitleViewListener()
                        multipleChoiceView?.visibility = View.GONE
                        toolbarRecyclerView.visibility = View.GONE
                        CommonAnim.getInstance().showBottom2Top(chatView)
                        var i = 0
                        val itemCount: Int = chatAdapter.itemCount
                        while (i < itemCount) {
                            chatAdapter.getItem(i).isChoose = false
                            chatAdapter.getItem(i).isChecked = false
                            chatAdapter.notifyItemChanged(i)
                            i++
                        }
                        resetMenuIv()
                        resetToolBar()
                        iConversationContext.deleteOperationMsg()
                    }
                }
            }
        }
    }

    private fun initBanView() {
        banView = FrameLayout(iConversationContext.chatActivity)
        banView?.visibility = View.GONE
        banView?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        panelView.addView(
            banView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 55, Gravity.CENTER)
        )
        val textView = AppCompatTextView(iConversationContext.chatActivity)
        textView.text = iConversationContext.chatActivity.getString(R.string.group_ban)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_16)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        textView.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.color999
            )
        )
        banView?.addView(
            textView,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private fun initForbiddenView() {
        forbiddenView = FrameLayout(iConversationContext.chatActivity)
        forbiddenView?.visibility = View.GONE
        panelView.addView(
            forbiddenView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 55, Gravity.CENTER)
        )
        forbiddenView?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        val contentLayout = LinearLayout(iConversationContext.chatActivity)
        contentLayout.orientation = LinearLayout.HORIZONTAL
        forbiddenView?.addView(
            contentLayout,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        val imageView = AppCompatImageView(iConversationContext.chatActivity)
        imageView.setImageResource(R.mipmap.icon_forbidden)
        contentLayout.addView(imageView, LayoutHelper.createLinear(20, 20, Gravity.CENTER))
        val textView = AppCompatTextView(iConversationContext.chatActivity)
        textView.text = iConversationContext.chatActivity.getString(R.string.fullStaffing)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_16)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        textView.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.color999
            )
        )
        contentLayout.addView(
            textView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 10, 0, 0, 0
            )
        )
        textView.tag = "forbiddenTV"
    }

    private fun initWingmanSuggestionsEndpoint() {
        // Wingman suggestions are disabled to avoid extra AI cost.
        EndpointManager.getInstance().setMethod("chat_wingman_suggestions") { null }
    }

    private fun showWingmanSuggestions(replies: List<String>) {
        val old = chatTopLayout.findViewWithTag<View>(wingmanSuggestionTag)
        if (old != null) chatTopLayout.removeView(old)
        if (replies.isEmpty()) return

        val scrollView = HorizontalScrollView(iConversationContext.chatActivity)
        scrollView.tag = wingmanSuggestionTag
        scrollView.isHorizontalScrollBarEnabled = false
        scrollView.overScrollMode = View.OVER_SCROLL_NEVER
        scrollView.setPadding(AndroidUtilities.dp(10f), AndroidUtilities.dp(4f), AndroidUtilities.dp(10f), AndroidUtilities.dp(6f))

        val row = LinearLayout(iConversationContext.chatActivity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        scrollView.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        replies.forEach { reply ->
            val chip = AppCompatTextView(iConversationContext.chatActivity)
            chip.text = reply
            chip.maxLines = 1
            chip.ellipsize = TextUtils.TruncateAt.END
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            chip.setTextColor(Color.parseColor("#374151"))
            chip.setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(7f), AndroidUtilities.dp(12f), AndroidUtilities.dp(7f))
            chip.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FAFFFFFF"), Color.parseColor("#EEF4F7FF"))
            ).apply {
                cornerRadius = AndroidUtilities.dp(999f).toFloat()
                setStroke(AndroidUtilities.dp(1f), Color.parseColor("#E8FFFFFF"))
            }
            chip.elevation = AndroidUtilities.dp(3f).toFloat()
            chip.setOnClickListener {
                editText.setText(reply)
                editText.setSelection(editText.text?.length ?: 0)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = AndroidUtilities.dp(8f)
            row.addView(chip, lp)
        }
        chatTopLayout.addView(
            scrollView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER)
        )
    }

    private fun initChatTopView() {
        chatTopView = LinearLayout(iConversationContext.chatActivity)
        chatTopView?.visibility = View.GONE
        chatTopView?.orientation = LinearLayout.HORIZONTAL
        chatTopView?.gravity = Gravity.CENTER_VERTICAL
        chatTopView?.setPadding(
            AndroidUtilities.dp(14f),
            AndroidUtilities.dp(6f),
            AndroidUtilities.dp(12f),
            AndroidUtilities.dp(5f)
        )
        chatTopView?.background = null
        chatTopView?.elevation = AndroidUtilities.dp(7f).toFloat()

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            chatReplyPanelHeight,
            Gravity.TOP
        )
        panelView.addView(chatTopView, lp)

        val imageView = AppCompatImageView(iConversationContext.chatActivity)
        imageView.setImageResource(R.mipmap.ic_ab_forward)
        imageView.colorFilter = PorterDuffColorFilter(
            Color.parseColor("#60A5FA"),
            PorterDuff.Mode.MULTIPLY
        )
        imageView.alpha = 0.95f
        chatTopView?.addView(
            imageView,
            LayoutHelper.createLinear(
                26,
                26,
                Gravity.CENTER,
                0,
                0,
                10,
                0
            )
        )

        val centerLayout = LinearLayout(iConversationContext.chatActivity)
        centerLayout.orientation = LinearLayout.VERTICAL
        centerLayout.gravity = Gravity.CENTER_VERTICAL
        chatTopView?.addView(
            centerLayout,
            LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, Gravity.CENTER)
        )

        val nameTv = AppCompatTextView(iConversationContext.chatActivity)
        nameTv.setTextColor(Color.parseColor("#3B82F6"))
        nameTv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        nameTv.includeFontPadding = false
        nameTv.maxLines = 1
        nameTv.ellipsize = TextUtils.TruncateAt.END
        centerLayout.addView(
            nameTv,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )

        val contentTv = AppCompatTextView(iConversationContext.chatActivity)
        contentTv.setTextColor(Color.parseColor("#64748B"))
        contentTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        contentTv.includeFontPadding = false
        contentTv.maxLines = 1
        contentTv.ellipsize = TextUtils.TruncateAt.END
        val contentLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        contentLp.topMargin = AndroidUtilities.dp(4f)
        centerLayout.addView(contentTv, contentLp)

        val rightIv = AppCompatTextView(iConversationContext.chatActivity)
        rightIv.text = "×"
        rightIv.gravity = Gravity.CENTER
        rightIv.includeFontPadding = false
        rightIv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        rightIv.setTextColor(Color.parseColor("#64748B"))
        rightIv.alpha = 0.88f
        chatTopView?.addView(
            rightIv,
            LayoutHelper.createLinear(
                36,
                LayoutHelper.MATCH_PARENT,
                Gravity.CENTER,
                10,
                0,
                0,
                0
            )
        )
        rightIv.setOnClickListener {
            closeChatReplyPanel()
            editText.text = null
            iConversationContext.deleteOperationMsg()
        }
        rightIv.background = Theme.createSelectorDrawable(Theme.getPressedColor())

        val divider = View(iConversationContext.chatActivity)
        divider.tag = "replyDivider"
        divider.setBackgroundColor(Color.parseColor("#14000000"))
        val dividerLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            AndroidUtilities.dp(0.6f),
            Gravity.TOP
        )
        dividerLp.topMargin = chatReplyPanelHeight - AndroidUtilities.dp(1.2f)
        dividerLp.leftMargin = AndroidUtilities.dp(14f)
        dividerLp.rightMargin = AndroidUtilities.dp(14f)
        divider.visibility = View.GONE
        panelView.addView(divider, dividerLp)

        imageView.tag = "topLeftIv"
        nameTv.tag = "topTitleTv"
        contentTv.tag = "contentTv"
    }

    private fun openChatReplyPanel() {
        val topView = chatTopView ?: return
        if (topView.visibility != View.VISIBLE) {
            topView.alpha = 0f
            topView.visibility = View.VISIBLE
            panelView.findViewWithTag<View>("replyDivider")?.visibility = View.VISIBLE
            topView.animate().alpha(1f).setDuration(140L).start()
        }
        setInputReplyMode(true)
        iConversationContext.chatRecyclerViewScrollToEnd()
        helper.toKeyboardState()
    }

    private fun closeChatReplyPanel() {
        val topView = chatTopView
        if (topView != null && topView.visibility == View.VISIBLE) {
            topView.animate().cancel()
            topView.visibility = View.GONE
            topView.alpha = 1f
        }
        panelView.findViewWithTag<View>("replyDivider")?.visibility = View.GONE
        setInputReplyMode(false)
    }

    private fun setInputReplyMode(enable: Boolean) {
        val targetHeight = if (enable) chatInputReplyHeight else chatInputNormalHeight
        val params = chatView.layoutParams
        if (params.height != targetHeight) {
            params.height = targetHeight
            chatView.layoutParams = params
        }
        chatView.minimumHeight = targetHeight
        chatView.setPadding(
            AndroidUtilities.dp(8f),
            if (enable) chatReplyPanelHeight else AndroidUtilities.dp(4f),
            AndroidUtilities.dp(8f),
            AndroidUtilities.dp(4f)
        )
        chatView.gravity = Gravity.CENTER_VERTICAL
    }


    private fun initFlameView() {
        flameLayout = LinearLayout(iConversationContext.chatActivity)

        chatTopLayout.addView(
            flameLayout,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER)
        )
        flameLayout?.visibility = View.GONE
        flameLayout?.orientation = LinearLayout.HORIZONTAL
        flameLayout?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.chat_face_tab_bg
            )
        )
        flameLayout?.setPadding(
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(0f),
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(0f)
        )
        val contentLayout = LinearLayout(iConversationContext.chatActivity)
        contentLayout.orientation = LinearLayout.VERTICAL
        flameLayout?.addView(
            contentLayout,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                1f,
                Gravity.CENTER
            )
        )
        val topLayout = LinearLayout(iConversationContext.chatActivity)
        topLayout.orientation = LinearLayout.HORIZONTAL
        contentLayout.addView(
            topLayout,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val imageView = AppCompatImageView(iConversationContext.chatActivity)
        imageView.setImageResource(R.mipmap.flame_small)
        imageView.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                iConversationContext.chatActivity, R.color.color999
            ), PorterDuff.Mode.MULTIPLY
        )
        val burnTimeTv = AppCompatTextView(iConversationContext.chatActivity)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_14)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        burnTimeTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        burnTimeTv.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.color999
            )
        )
        burnTimeTv.text = iConversationContext.chatActivity.getString(R.string.burn_time_desc)
        topLayout.addView(
            imageView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        topLayout.addView(
            burnTimeTv,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                5,
                0,
                0,
                0
            )
        )
        val seekBarView = SeekBarView(iConversationContext.chatActivity, false)
        seekBarView.setColors(
            Theme.color999,
            Theme.colorAccount
        )
        seekBarView.setDelegate(object : SeekBarView.SeekBarViewDelegate {
            override fun onSeekBarDrag(stop: Boolean, progress: Float) {
                if (stop)
                    setProgress(progress)
            }

            override fun onSeekBarPressed(pressed: Boolean) {
            }
        })
        contentLayout.addView(
            seekBarView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                30,
                Gravity.CENTER,
                10,
                0,
                15,
                0
            )
        )
        val switchView = SwitchView(iConversationContext.chatActivity)
        flameLayout?.addView(
            switchView,
            LayoutHelper.createLinear(45, 40, Gravity.CENTER, 0, 0, 0, 0)
        )
        switchView.setOnCheckedChangeListener { v, isChecked ->
            if (!v.isPressed) return@setOnCheckedChangeListener
            val requestChannelId = activeChannelId
            val requestChannelType = activeChannelType
            val requestGeneration = activeSessionGeneration
            if (requestChannelType == WKChannelType.PERSONAL) {
                FriendModel.getInstance().updateUserSetting(
                    requestChannelId,
                    "flame",
                    if (isChecked) 1 else 0
                ) { code: Int, msg: String? ->
                    if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) {
                        return@updateUserSetting
                    }
                    if (code != HttpResponseCode.success.toInt()) {
                        switchView.isChecked = !isChecked
                        WKToastUtils.getInstance().showToast(msg)
                    } else if (!isChecked) {
                        CommonAnim.getInstance().animateClose(flameLayout)
                    }
                }
            } else {
                GroupModel.getInstance().updateGroupSetting(
                    requestChannelId,
                    "flame",
                    if (isChecked) 1 else 0
                ) { code: Int, msg: String? ->
                    if (!isActiveSession(requestChannelId, requestChannelType, requestGeneration)) {
                        return@updateGroupSetting
                    }
                    if (code != HttpResponseCode.success.toInt()) {
                        switchView.isChecked = !isChecked
                        WKToastUtils.getInstance().showToast(msg)
                    } else if (!isChecked) {
                        CommonAnim.getInstance().animateClose(flameLayout)
                    }
                }
            }
        }

        switchView.tag = "switchView"
        seekBarView.tag = "seekBarView"
        burnTimeTv.tag = "burnTimeTv"
    }

    private fun initNewImageView() {
        newImageLayout = LinearLayout(iConversationContext.chatActivity)
        newImageLayout?.setBackgroundColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.layoutColor
            )
        )
        newImageLayout?.orientation = LinearLayout.VERTICAL
        newImageLayout?.visibility = View.GONE
        newImageLayout?.setPadding(
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(10f),
            AndroidUtilities.dp(10f)
        )
        followScrollLayout.addView(
            newImageLayout,
            LayoutHelper.createFrame(
                90,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.CENTER or Gravity.END,
                0f,
                0f,
                10f,
                0f
            )
        )
        val textView = AppCompatTextView(iConversationContext.chatActivity)
        textView.setTextColor(
            ContextCompat.getColor(
                iConversationContext.chatActivity,
                R.color.popupTextColor
            )
        )
        textView.text = iConversationContext.chatActivity.getString(R.string.probably_send_img)
        val size = iConversationContext.chatActivity.getResources()
            .getDimension(R.dimen.font_size_10)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            iConversationContext.chatActivity.resources.displayMetrics
        )
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        newImageLayout?.addView(
            textView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val imageView = AppCompatImageView(iConversationContext.chatActivity)
        imageView.setImageResource(R.drawable.default_view_bg)
        imageView.tag = "imageView"
        newImageLayout?.addView(
            imageView,
            LayoutHelper.createLinear(70, 120, Gravity.CENTER, 0, 10, 0, 0)
        )
    }

    fun addSpan(name: String, uid: String) {
        val text = "@${name} "
        editText.addSpan(
            text,
            uid
        )
    }
}
