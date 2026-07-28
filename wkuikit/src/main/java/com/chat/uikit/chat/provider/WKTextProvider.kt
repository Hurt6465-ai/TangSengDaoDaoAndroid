package com.chat.uikit.chat.provider

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.ContactsContract
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.FragmentActivity
import androidx.emoji2.widget.EmojiTextView
import com.chat.base.WKBaseApplication
import com.chat.base.act.WKWebViewActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.emoji.EmojiManager
import com.chat.base.emoji.MoonUtil
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.CanReactionMenu
import com.chat.base.endpoint.entity.ChatChooseContacts
import com.chat.base.endpoint.entity.ChatItemPopupMenu
import com.chat.base.endpoint.entity.ChooseChatMenu
import com.chat.base.endpoint.entity.MsgConfig
import com.chat.base.entity.BottomSheetItem
import com.chat.base.glide.GlideUtils
import com.chat.base.msg.ChatAdapter
import com.chat.base.msg.model.WKGifContent
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.components.AlignImageSpan
import com.chat.base.ui.components.AvatarView
import com.chat.base.ui.components.NormalClickableContent
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.utils.LayoutHelper
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.utils.StringUtils
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKPermissions
import com.chat.base.utils.WKPermissions.IPermissionResult
import com.chat.base.utils.WKToastUtils
import com.chat.base.views.BubbleLayout
import com.chat.deepseek.DeepSeekAssistant
import com.chat.deepseek.DeepSeekRequest
import com.chat.uikit.R
import com.chat.uikit.user.UserDetailActivity
import com.chat.translate.api.ChatTranslateRequest
import com.chat.translate.api.WkTranslateBridge
import com.chat.translate.core.TranslateErrorCode
import com.chat.translate.core.TranslateMode
import com.chat.translate.core.TranslateScene
import com.chat.translate.prefs.TranslatePrefs
import kotlinx.coroutines.runBlocking
import com.google.android.material.snackbar.Snackbar
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.entity.WKMsgSetting
import com.xinbida.wukongim.entity.WKSendOptions
import com.xinbida.wukongim.msgmodel.WKImageContent
import com.xinbida.wukongim.msgmodel.WKTextContent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Objects
import java.util.concurrent.Executors
import kotlin.math.abs


open class WKTextProvider : WKChatBaseProvider() {
    companion object {
        private const val PAYLOAD_TRANSLATION_CHANGED = "payload_translation_changed"
        private const val MAX_TRANSLATION_MEMORY_ITEMS = 200
        private val TRANSLATION_EXECUTOR = Executors.newFixedThreadPool(2)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val translationMemoryCache = LinkedHashMap<String, TranslationCache>(64, 0.75f, true)

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_text, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
//        val textContentLayout = parentView.findViewById<View>(R.id.textContentLayout)
        //   val linkView = parentView.findViewById<LinearLayout>(R.id.linkView)
        val contentTv = parentView.findViewById<EmojiTextView>(R.id.contentTv)
        val receivedTextNameTv = parentView.findViewById<TextView>(R.id.receivedTextNameTv)
        //val msgTimeView = parentView.findViewById<View>(R.id.msgTimeView)


        val contentTvLayout = parentView.findViewById<BubbleLayout>(R.id.contentTvLayout)

        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)

        //replyLayout.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)
        // 这里要指定文本宽度 - padding的距离
//        textContentLayout.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)
//        val bgType = getMsgBgType(
//            uiChatMsgItemEntity.previousMsg, uiChatMsgItemEntity.wkMsg, uiChatMsgItemEntity.nextMsg
//        )
        resetCellBackground(parentView, uiChatMsgItemEntity, from)
//        if (textContentLayout.layoutParams.width < msgTimeView.layoutParams.width) {
//            textContentLayout.layoutParams.width = msgTimeView.layoutParams.width
//        }
        val textColor: Int
        if (from == WKChatIteMsgFromType.SEND) {
            contentTv.setBackgroundResource(R.drawable.send_chat_text_bg)
            contentLayout.gravity = Gravity.END
            receivedTextNameTv.visibility = View.GONE
            textColor = ContextCompat.getColor(context, R.color.colorDark)
        } else {
            contentTv.setBackgroundResource(R.drawable.received_chat_text_bg)
            setFromName(uiChatMsgItemEntity, from, receivedTextNameTv)
            contentLayout.gravity = Gravity.START
            textColor = ContextCompat.getColor(context, R.color.receive_text_color)
        }
        contentTv.setTextColor(textColor)
        val displayedText = SpannableStringBuilder(uiChatMsgItemEntity.displaySpans ?: "")
        if (from == WKChatIteMsgFromType.SEND) {
            val backTranslation = DeepSeekAssistant.getLocalBackTranslation(
                context,
                uiChatMsgItemEntity.wkMsg
            )
            // Old test messages may already contain an inline back-translation from the previous
            // transient WKTextContent subclass. Do not append it twice after upgrading.
            val hasInlineBackTranslation = TextUtils.indexOf(displayedText, "\n回译：") >= 0
            if (!TextUtils.isEmpty(backTranslation) && !hasInlineBackTranslation) {
                val start = displayedText.length
                displayedText.append("\n回译：").append(backTranslation)
                displayedText.setSpan(
                    ForegroundColorSpan(Color.rgb(70, 91, 84)),
                    start,
                    displayedText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                displayedText.setSpan(
                    RelativeSizeSpan(0.88f),
                    start,
                    displayedText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        contentTv.text = displayedText
        contentTv.movementMethod = LinkMovementMethod.getInstance()
//        val preText =  PrecomputedTextCompat.create(
//            uiChatMsgItemEntity.displaySpans,
//            TextViewCompat.getTextMetricsParams(contentTv)
//        )
//
//        TextViewCompat.setPrecomputedText(contentTv, preText)
//
//
//        fun AppCompatTextView.setTextFuture(charSequence: CharSequence){
//            this.setTextFuture(PrecomputedTextCompat.getTextFuture(
//                charSequence,
//                TextViewCompat.getTextMetricsParams(this),
//                null
//            ))
//        }
//
//        contentTv.setTextFuture(uiChatMsgItemEntity.displaySpans)

        // 链接识别
//        val urls = StringUtils.getStrUrls(contentTv.text.toString())
//        if (urls.size > 0) {
//            showLinkInfo(uiChatMsgItemEntity, msgTimeView, linkView, from, urls[urls.size - 1])
//        } else {
//            linkView.visibility = View.GONE
//            msgTimeView.visibility = View.VISIBLE
//        }

        //setSelectableTextHelper(contentTv,0,true)
        selectText(contentTv, contentTvLayout, uiChatMsgItemEntity)
        if (uiChatMsgItemEntity.wkMsg.baseContentMsgModel.reply != null && uiChatMsgItemEntity.wkMsg.baseContentMsgModel.reply.payload != null) {
            replyView(contentTvLayout, from, uiChatMsgItemEntity)
        }
        bindInlineTranslate(contentTvLayout, contentLayout, uiChatMsgItemEntity, from)
    }

    private val translationViewTag = "chat_inline_translation"
    private val translationButtonTag = "chat_inline_translate_button"
    private val rtcSignalPrefix = "__cp_harmony_rtc__:"

    private fun bindInlineTranslate(
        contentTvLayout: ViewGroup,
        buttonParent: ViewGroup,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        // 翻译区会在同一个 RecyclerView cell 内动态增加/删除。LayoutTransition 会把
        // 被删除的旧气泡临时放进 overlay 再播放消失动画，此时新气泡已经加入，视觉上
        // 就会出现两层气泡叠在一起。文本消息单元不需要这种动画，绑定时明确关闭。
        contentTvLayout.layoutTransition = null
        buttonParent.layoutTransition = null

        // RecyclerView 会复用 item，必须递归清理旧按钮；否则旧 view 会残留到每一条消息上。
        removeTaggedChildDeep(contentTvLayout, translationViewTag)
        removeTaggedChildDeep(contentTvLayout, translationButtonTag)
        removeTaggedChildDeep(buttonParent, translationButtonTag)

        val currentMsg = uiChatMsgItemEntity.wkMsg ?: return
        val content = getMessageText(currentMsg)
        if (TextUtils.isEmpty(content)) return
        if (isRtcSignalText(content)) return

        val cacheKey = translationCacheKey(currentMsg, content)
        val cached = readTranslationCache(cacheKey)
        if (cached != null && cached.expanded) {
            addTranslationView(contentTvLayout, currentMsg, cacheKey, cached.text, cached.status)
        }

        // 使用基类的频道级缓存，避免每个文本气泡绑定时都从列表尾部重新扫描。
        if (from == WKChatIteMsgFromType.RECEIVED
            && shouldShowInlineTranslateButton(uiChatMsgItemEntity)) {
            addQuickTranslateButton(buttonParent, uiChatMsgItemEntity, content, cacheKey, cached)
        }
    }

    private data class TranslationCache(
        val text: String,
        val expanded: Boolean,
        val status: String = "ok"
    )

    private fun removeTaggedChildDeep(parent: ViewGroup, tag: String) {
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.tag == tag) {
                parent.removeViewAt(i)
            } else if (child is ViewGroup) {
                removeTaggedChildDeep(child, tag)
            }
        }
    }

    private fun isRtcSignalText(text: String?): Boolean {
        if (TextUtils.isEmpty(text)) return false
        return text!!.trim().startsWith(rtcSignalPrefix)
    }

    private fun isSameMessage(a: WKMsg?, b: WKMsg?): Boolean {
        if (a == null || b == null) return false
        if (!TextUtils.isEmpty(a.messageID) && a.messageID != "0" && TextUtils.equals(a.messageID, b.messageID)) return true
        if (!TextUtils.isEmpty(a.clientMsgNO) && TextUtils.equals(a.clientMsgNO, b.clientMsgNO)) return true
        if (a.orderSeq > 0 && a.orderSeq == b.orderSeq && TextUtils.equals(a.channelID, b.channelID) && a.channelType == b.channelType) return true
        if (a.messageSeq > 0 && a.messageSeq == b.messageSeq && TextUtils.equals(a.channelID, b.channelID) && a.channelType == b.channelType) return true
        return false
    }

    private fun addQuickTranslateButton(
        parent: ViewGroup,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        content: String,
        cacheKey: String,
        cached: TranslationCache?
    ) {
        val btn = AppCompatImageView(context)
        btn.tag = translationButtonTag
        btn.setImageResource(R.drawable.ic_chat_translate_wa)
        btn.scaleType = ImageView.ScaleType.CENTER_INSIDE
        btn.contentDescription = context.getString(com.chat.translate.R.string.wktranslate_translate)
        btn.isSelected = cached?.expanded == true
        btn.setMinimumWidth(0)
        btn.setMinimumHeight(0)
        // 30dp 点击框 + 4dp 内边距，实际图标约 22dp；无常驻底框，只保留按压反馈。
        btn.setPadding(dp(4), dp(4), dp(4), dp(4))
        btn.setBackgroundResource(R.drawable.bg_chat_translate_quick)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            btn.elevation = 0f
        }
        btn.setOnClickListener {
            val latest = readTranslationCache(cacheKey)
            when {
                latest?.status == "loading" -> return@setOnClickListener
                latest?.status == "error" -> translateMessageIntoBubble(uiChatMsgItemEntity, content, cacheKey, true)
                latest != null -> {
                    saveTranslationCache(cacheKey, latest.text, !latest.expanded, latest.status)
                    notifyMessageChanged(uiChatMsgItemEntity.wkMsg)
                }
                cached?.status == "loading" -> return@setOnClickListener
                cached?.status == "error" -> translateMessageIntoBubble(uiChatMsgItemEntity, content, cacheKey, true)
                cached != null -> {
                    saveTranslationCache(cacheKey, cached.text, !cached.expanded, cached.status)
                    notifyMessageChanged(uiChatMsgItemEntity.wkMsg)
                }
                else -> translateMessageIntoBubble(uiChatMsgItemEntity, content, cacheKey, true)
            }
        }
        val lp = LinearLayout.LayoutParams(dp(30), dp(30))
        lp.gravity = Gravity.BOTTOM
        lp.leftMargin = dp(6)
        lp.bottomMargin = 0
        parent.addView(btn, lp)
    }

    private fun addTranslationView(
        parent: ViewGroup,
        message: WKMsg,
        cacheKey: String,
        text: String,
        status: String = "ok"
    ) {
        val box = LinearLayout(context)
        box.tag = translationViewTag
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(7), 0, 0)

        val divider = View(context)
        divider.setBackgroundColor(ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.color999), 95))
        box.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        val tv = AppCompatTextView(context)
        tv.text = text
        tv.setTextColor(
            when (status) {
                "loading" -> ContextCompat.getColor(context, R.color.color999)
                "error" -> Color.rgb(220, 38, 38)
                else -> ContextCompat.getColor(context, R.color.colorDark)
            }
        )
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (status == "loading") 14f else 15f)
        tv.setLineSpacing(0f, 1.08f)
        tv.setPadding(0, dp(7), 0, 0)
        tv.setOnClickListener {
            if (status != "loading") {
                saveTranslationCache(cacheKey, text, false, status)
                notifyMessageChanged(message)
            }
        }
        box.setOnClickListener {
            if (status != "loading") {
                saveTranslationCache(cacheKey, text, false, status)
                notifyMessageChanged(message)
            }
        }
        box.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(4)
        parent.addView(box, lp)
    }

    private fun translateSelectedTextToBubble(uiChatMsgItemEntity: WKUIChatMsgItemEntity, text: String) {
        val cacheKey = translationCacheKey(uiChatMsgItemEntity.wkMsg, text)
        val cached = readTranslationCache(cacheKey)
        if (cached != null && cached.status == "ok") {
            saveTranslationCache(cacheKey, cached.text, true, cached.status)
            notifyMessageChanged(uiChatMsgItemEntity.wkMsg)
            return
        }
        translateMessageIntoBubble(uiChatMsgItemEntity, text, cacheKey, false)
    }


    /**
     * Bubble translation must use the messages already loaded by the current ChatActivity.
     * This is a read-only snapshot: never call the IM history-sync API here because it can
     * refresh the open adapter and make visible messages disappear while the WebView is open.
     */
    private fun populateDeepSeekTranslationContext(request: DeepSeekRequest) {
        val adapter = getAdapter() as? ChatAdapter ?: return
        if (adapter.data.isNullOrEmpty()) return

        val selfUid = WKConfig.getInstance().uid ?: ""
        val lines = ArrayList<String>()
        for (item in adapter.data) {
            val msg = item?.wkMsg ?: continue
            if (msg.type != WKContentType.WK_TEXT) continue
            if (msg.isDeleted != 0) continue
            if (msg.remoteExtra?.revoke == 1 || msg.remoteExtra?.isMutualDeleted == 1) continue

            var content = getMessageText(msg)
                .replace('\u0000', ' ')
                .replace("```", "` ` `")
                .trim()
            if (content.isEmpty() || isRtcSignalText(content)) continue
            val mine = TextUtils.equals(selfUid, msg.fromUID)
            lines.add((if (mine) "我：" else "对方：") + content)
        }

        val snapshot = StringBuilder()
        for (line in lines) {
            if (snapshot.isNotEmpty()) snapshot.append('\n')
            snapshot.append(line)
        }
        request.contextSnapshot = snapshot.toString()
        request.contextSnapshotCount = lines.size
        request.contextLimit = 0
    }

    private fun openDeepSeekTranslation(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        text: String,
        cacheKey: String
    ): Boolean {
        val activity = context as? FragmentActivity ?: return false
        if (!DeepSeekAssistant.isEnabled(activity)) return false
        val msg = uiChatMsgItemEntity.wkMsg ?: return false
        val request = DeepSeekRequest().apply {
            action = DeepSeekRequest.ACTION_TRANSLATE
            channelId = msg.channelID ?: ""
            channelType = msg.channelType
            selfUid = WKConfig.getInstance().uid ?: ""
            myNativeLanguage = getChatTranslateTargetLang()
            peerNativeLanguage = "自动"
            targetMessageText = text
            targetMessageId = when {
                !TextUtils.isEmpty(msg.messageID) && msg.messageID != "0" -> msg.messageID
                !TextUtils.isEmpty(msg.clientMsgNO) -> msg.clientMsgNO
                msg.messageSeq > 0 -> msg.messageSeq.toString()
                else -> ""
            }
            contextEnabled = true
            contextLimit = 0
        }
        populateDeepSeekTranslationContext(request)
        var delivered = false
        val opened = DeepSeekAssistant.openTranslation(
            activity,
            request,
            DeepSeekAssistant.TranslationCallback { translated: String ->
                delivered = true
                mainHandler.post {
                    if (!TextUtils.isEmpty(translated)) {
                        saveTranslationCache(cacheKey, translated, true, "ok")
                    } else {
                        saveTranslationCache(
                            cacheKey,
                            context.getString(com.chat.translate.R.string.wktranslate_translate_failed),
                            true,
                            "error"
                        )
                    }
                    notifyMessageChanged(msg)
                }
            },
            DeepSeekAssistant.StateCallback {
                mainHandler.post {
                    val latest = readTranslationCache(cacheKey)
                    if (!delivered && latest?.status == "loading") {
                        saveTranslationCache(
                            cacheKey,
                            context.getString(com.chat.translate.R.string.wktranslate_translate_failed),
                            true,
                            "error"
                        )
                        notifyMessageChanged(msg)
                    }
                }
            }
        )
        if (opened) {
            saveTranslationCache(
                cacheKey,
                context.getString(com.chat.translate.R.string.wktranslate_translating),
                true,
                "loading"
            )
            notifyMessageChanged(msg)
            return true
        }
        WKToastUtils.getInstance().showToastNormal(
            context.getString(com.chat.deepseek.R.string.wkdeepseek_busy)
        )
        return true
    }

    private fun translateMessageIntoBubble(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        text: String,
        cacheKey: String,
        requestWingman: Boolean
    ) {
        val msg = uiChatMsgItemEntity.wkMsg ?: return
        if (openDeepSeekTranslation(uiChatMsgItemEntity, text, cacheKey)) return
        val appContext = context.applicationContext
        val translateMode = TranslatePrefs.getMode(appContext)
        if (translateMode == TranslateMode.AI && !TranslatePrefs.hasUsableAi(appContext)) {
            WKToastUtils.getInstance().showToastNormal(context.getString(com.chat.translate.R.string.wktranslate_need_ai_config))
            WkTranslateBridge().openSettings(context, "chat_bubble")
            return
        }

        saveTranslationCache(cacheKey, context.getString(com.chat.translate.R.string.wktranslate_translating), true, "loading")
        notifyMessageChanged(msg)
        TRANSLATION_EXECUTOR.execute {
            try {
                val result = runBlocking {
                    WkTranslateBridge().translate(
                        ChatTranslateRequest(
                            context = context.applicationContext,
                            text = text,
                            sourceLang = "auto",
                            targetLang = getChatTranslateTargetLang(),
                            scene = TranslateScene.MESSAGE_BUBBLE,
                            bypassCache = false
                        )
                    )
                }
                mainHandler.post {
                    if (result.success && !TextUtils.isEmpty(result.translatedText)) {
                        saveTranslationCache(cacheKey, result.translatedText, true, "ok")
                        notifyMessageChanged(msg)
                    } else if (result.errorCode == TranslateErrorCode.NEED_AI_CONFIG) {
                        if (TranslatePrefs.getMode(context.applicationContext) == TranslateMode.AI) {
                            WKToastUtils.getInstance().showToastNormal(context.getString(com.chat.translate.R.string.wktranslate_need_ai_config))
                            WkTranslateBridge().openSettings(context, "chat_bubble")
                        } else {
                            saveTranslationCache(cacheKey, context.getString(com.chat.translate.R.string.wktranslate_translate_failed), true, "error")
                            notifyMessageChanged(msg)
                        }
                    } else {
                        saveTranslationCache(cacheKey, context.getString(com.chat.translate.R.string.wktranslate_translate_failed), true, "error")
                            notifyMessageChanged(msg)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (TranslatePrefs.getMode(context.applicationContext) == TranslateMode.AI && !TranslatePrefs.hasUsableAi(context.applicationContext)) {
                        WKToastUtils.getInstance().showToastNormal(context.getString(com.chat.translate.R.string.wktranslate_need_ai_config))
                        WkTranslateBridge().openSettings(context, "chat_bubble")
                    } else {
                        saveTranslationCache(cacheKey, context.getString(com.chat.translate.R.string.wktranslate_translate_failed), true, "error")
                            notifyMessageChanged(msg)
                    }
                }
            }
        }
    }

    private fun requestAiTranslation(endpoint: String, apiKey: String, model: String, sourceLang: String, targetLang: String, text: String): String {
        val prompt = """
            将以下聊天消息从$sourceLang 翻译成 $targetLang。

            要求：
            - 自然直译，保留原文结构、语气、表情符号和换行。
            - 若原文带有暧昧、调侃、冷淡、敷衍、撒娇、抱怨等语气，译文必须保留这种聊天感觉。
            - 保留链接、用户名、代码块、Markdown、列表和表情。
            - 只输出译文，不要 JSON，不要解释，不要引号，不要前缀。

            待翻译消息：
            $text
        """.trimIndent()
        val raw = requestAi(endpoint, apiKey, model, prompt, 0.25)
        val cleaned = cleanTranslationText(raw)
        if (TextUtils.isEmpty(cleaned)) throw RuntimeException("翻译结果为空")
        return cleaned
    }

    private fun cleanTranslationText(raw: String): String {
        var text = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (text.startsWith("{")) {
            try {
                val obj = JSONObject(text)
                val translation = obj.optString("translation", "")
                if (!TextUtils.isEmpty(translation)) return translation.trim()
            } catch (_: Exception) {
                val match = Regex("\"translation\"\\s*:\\s*\"([\\s\\S]*?)\"").find(text)
                if (match != null) {
                    return match.groupValues[1]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\/", "/")
                        .trim()
                }
                return ""
            }
        }
        return text
    }

    private fun requestWingmanSuggestions(endpoint: String, apiKey: String, model: String, original: String, translated: String) {
        TRANSLATION_EXECUTOR.execute {
            try {
                val myLang = readAiSetting("chat_ai_source_lang", "中文")
                val prompt = """
                    你是聊天僚机。根据对方消息，生成 3-5 条我可以直接发送的短回复建议。
                    我的语言：$myLang
                    对方原文：$original
                    对方消息译文：$translated
                    要求：每条 20 字以内，口语化，自然，不油腻。
                    只输出 JSON：{"quick_replies":[{"text":"回复1"},{"text":"回复2"}]}
                """.trimIndent()
                val content = requestAi(endpoint, apiKey, model, prompt, 0.35)
                val replies = parseWingmanReplies(content)
                if (replies.isNotEmpty()) {
                    mainHandler.post {
                        EndpointManager.getInstance().invoke("chat_wingman_suggestions", replies)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun requestAi(endpoint: String, apiKey: String, model: String, prompt: String, temperature: Double): String {
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "你是移动聊天应用内的翻译和回复建议助手，严格按用户要求输出。"))
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )
        val conn = (URL(endpoint).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
        val response = if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw RuntimeException(err.take(160))
        }
        val content = JSONObject(response)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            ?: ""
        if (content.isEmpty()) throw RuntimeException("empty response")
        return content.removePrefix("```").removePrefix("json").removeSuffix("```").trim()
    }

    private fun parseWingmanReplies(content: String): ArrayList<String> {
        val replies = ArrayList<String>()
        try {
            val obj = JSONObject(content)
            val arr = obj.optJSONArray("quick_replies") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                val text = if (item is JSONObject) item.optString("text") else item.toString()
                if (!TextUtils.isEmpty(text)) replies.add(text.trim())
            }
        } catch (_: Exception) {
        }
        if (replies.isEmpty()) {
            replies.add("我懂你的意思")
            replies.add("哈哈，那后来呢")
            replies.add("这个挺有意思")
        }
        return replies
    }

    private fun translationCacheKey(mMsg: WKMsg, content: String): String {
        return buildChatTranslateUiKey(mMsg, content)
    }

    private fun getMessageText(mMsg: WKMsg): String {
        var content = ""
        try {
            if (mMsg.remoteExtra != null && mMsg.remoteExtra.contentEditMsgModel != null) {
                content = mMsg.remoteExtra.contentEditMsgModel.displayContent ?: ""
            }
            if (TextUtils.isEmpty(content) && mMsg.baseContentMsgModel != null) {
                content = mMsg.baseContentMsgModel.displayContent ?: ""
            }
            if (TextUtils.isEmpty(content)) content = getShowContent(mMsg.content) ?: ""
        } catch (_: Exception) {
        }
        return content.trim()
    }

    private fun readTranslationCache(key: String): TranslationCache? {
        synchronized(translationMemoryCache) {
            translationMemoryCache[key]?.let { return it }
        }
        return try {
            val preferences = context.getSharedPreferences("chat_translate_cache", Context.MODE_PRIVATE)
            val raw = preferences.getString(key, "") ?: ""
            if (raw.isBlank()) return null
            val obj = JSONObject(raw)
            val time = obj.optLong("time", 0L)
            if (time > 0 && System.currentTimeMillis() - time > 7L * 24L * 60L * 60L * 1000L) {
                preferences.edit().remove(key).apply()
                return null
            }
            val text = obj.optString("text", "")
            val status = obj.optString("status", "ok")
            if (text.isBlank()) null else TranslationCache(
                text,
                obj.optBoolean("expanded", false),
                status
            ).also { putTranslationMemoryCache(key, it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun putTranslationMemoryCache(key: String, value: TranslationCache) {
        synchronized(translationMemoryCache) {
            translationMemoryCache[key] = value
            while (translationMemoryCache.size > MAX_TRANSLATION_MEMORY_ITEMS) {
                val iterator = translationMemoryCache.entries.iterator()
                if (!iterator.hasNext()) break
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun saveTranslationCache(key: String, translated: String, expanded: Boolean, status: String = "ok") {
        putTranslationMemoryCache(key, TranslationCache(translated, expanded, status))
        try {
            val obj = JSONObject()
                .put("time", System.currentTimeMillis())
                .put("text", translated)
                .put("expanded", expanded)
                .put("status", status)
            context.getSharedPreferences("chat_translate_cache", Context.MODE_PRIVATE)
                .edit().putString(key, obj.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun readAiSetting(key: String, fallback: String): String {
        val value = WKSharedPreferencesUtil.getInstance().getSP(key)
        return if (!TextUtils.isEmpty(value)) value else fallback
    }

    private fun getFlag(key: String, defaultValue: Boolean): Boolean {
        val value = WKSharedPreferencesUtil.getInstance().getSP(key)
        if (TextUtils.isEmpty(value)) return defaultValue
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun notifyMessageChanged(message: WKMsg?) {
        if (message == null) return
        val action = Runnable {
            try {
                val adapter = getAdapter() as? ChatAdapter ?: return@Runnable
                val index = adapter.data.indexOfFirst { item ->
                    isSameMessage(item?.wkMsg, message)
                }
                if (index >= 0) {
                    adapter.notifyItemChanged(
                        index + adapter.headerLayoutCount,
                        PAYLOAD_TRANSLATION_CHANGED
                    )
                }
            } catch (_: Exception) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
    }

    var selectText: String? = null
    private fun selectText(
        textView: TextView,
        fullLayout: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity
    ) {
//        textMsgBean = msgBean
        val menu = EndpointManager.getInstance()
            .invoke("favorite_item", uiChatMsgItemEntity.wkMsg)
        val favoritePopupMenu = menu as? ChatItemPopupMenu

        val builder = SelectTextHelper.Builder(textView, fullLayout) // 放你的textView到这里！！
            .setCursorHandleColor(ContextCompat.getColor(context, R.color.colorAccent)) // 游标颜色
            .setCursorHandleSizeInDp(22f) // 游标大小 单位dp
            .setSelectedColor(
                ContextCompat.getColor(
                    context,
                    R.color.color_text_select_cursor
                )
            ) // 选中文本的颜色
            .setSelectAll(true) // 初次选中是否全选 default true
            .setScrollShow(false) // 滚动时是否继续显示 default true
            .setSelectedAllNoPop(true) // 已经全选无弹窗，设置了监听会回调 onSelectAllShowCustomPop 方法
            .setMagnifierShow(true) // 放大镜 default true
            .setSelectTextLength(2)// 首次选中文本的长度 default 2
            .setPopDelay(100)// 弹窗延迟时间 default 100毫秒
            .setFlame(uiChatMsgItemEntity.wkMsg.flame)
            .setIsShowPinnedMessage(if (uiChatMsgItemEntity.isShowPinnedMessage) 1 else 0)
            .addItem(com.chat.uikit.R.drawable.ic_chat_translate_panel,
                context.getString(com.chat.translate.R.string.wktranslate_translate),
                object : SelectTextHelper.Builder.onSeparateItemClickListener {
                    override fun onClick() {
                        EndpointManager.getInstance().invoke("chat_activity_touch", null)
                        val content = selectText ?: ""
                        if (TextUtils.isEmpty(content)) return
                        translateSelectedTextToBubble(uiChatMsgItemEntity, content)
                    }
                }).addItem(
                R.mipmap.msg_forward,
                R.string.base_forward,
                object : SelectTextHelper.Builder.onSeparateItemClickListener {
                    override fun onClick() {
                        EndpointManager.getInstance().invoke("chat_activity_touch", null)
                        if (TextUtils.isEmpty(selectText)) return
                        val textContent = WKTextContent(selectText)
                        val chooseChatMenu =
                            ChooseChatMenu(
                                ChatChooseContacts { channelList: List<WKChannel>? ->
                                    if (!channelList.isNullOrEmpty()) {
                                        for (mChannel in channelList) {
                                            textContent.mentionAll = 0
                                            textContent.mentionInfo = null
                                            val option = WKSendOptions()
                                            option.setting.receipt = mChannel.receipt
                                            WKIM.getInstance().msgManager.sendWithOptions(
                                                textContent,
                                                mChannel, option
                                            )
                                        }
                                        val activity = context as? Activity
                                        if (activity != null) {
                                            val viewGroup = activity.findViewById<View>(android.R.id.content)
                                                .rootView as ViewGroup
                                            Snackbar.make(
                                                viewGroup,
                                                context.getString(com.chat.base.R.string.str_forward),
                                                1000
                                            )
                                                .setAction("") { }
                                                .show()
                                        }
                                    }
                                },
                                textContent
                            )
                        EndpointManager.getInstance()
                            .invoke(EndpointSID.showChooseChatView, chooseChatMenu)
                    }

                }).setPopSpanCount(3) // 设置操作弹窗每行个数 default 5
        if (favoritePopupMenu != null) {
            builder.addItem(
                favoritePopupMenu.imageResource,
                favoritePopupMenu.text,
                object : SelectTextHelper.Builder.onSeparateItemClickListener {
                    override fun onClick() {
                        EndpointManager.getInstance().invoke("chat_activity_touch", null)

                        if (!TextUtils.isEmpty(selectText)) {
                            val mMsg = WKMsg()
                            mMsg.type = WKContentType.WK_TEXT
                            mMsg.baseContentMsgModel = WKTextContent(selectText)
                            mMsg.from = uiChatMsgItemEntity.wkMsg.from
                            mMsg.channelID = uiChatMsgItemEntity.wkMsg.channelID
                            mMsg.channelType = uiChatMsgItemEntity.wkMsg.channelType
                            val chatAdapter = getAdapter() as? ChatAdapter ?: return
                            favoritePopupMenu.iPopupItemClick.onClick(
                                mMsg,
                                chatAdapter.conversationContext
                            )
                        }
                    }
                })
        }

        val selectableTextHelper = builder.build()
        selectableTextHelper.setSelectListener(object : SelectTextHelper.OnSelectListener {
            override fun onClick(v: View?, originalContent: String?) {
            }


            /**
             * 长按回调
             */
            override fun onLongClick(v: View, local: FloatArray) {
                // showPopup(messageContent,textView,local)
            }

            override fun onTextSelected(content: String?) {
                selectText = content
            }


            /**
             * 弹窗关闭回调
             */
            override fun onDismiss() {
                selectText = null
            }
            override fun onClickLink(clickableContent: NormalClickableSpan) {
                if (clickableContent.clickableContent.type == NormalClickableContent.NormalClickableTypes.URL) {
                    val intent = Intent(
                        context, WKWebViewActivity::class.java
                    )
                    intent.putExtra("url", clickableContent.clickableContent.content)
                    context.startActivity(intent)
                } else if (clickableContent.clickableContent.type == NormalClickableContent.NormalClickableTypes.Remind) {
                    val uid: String
                    var groupNo = ""
                    if (clickableContent.clickableContent.content.contains("|")) {
                        uid = clickableContent.clickableContent.content.split("|")[0]
                        groupNo = clickableContent.clickableContent.content.split("|")[1]
                    } else {
                        uid = clickableContent.clickableContent.content
                    }
                    val intent = Intent(context, UserDetailActivity::class.java)
                    intent.putExtra("uid", uid)
                    if (!TextUtils.isEmpty(groupNo)) intent.putExtra("groupID", groupNo)
                    context.startActivity(intent)
                } else {
                    val content = clickableContent.clickableContent.content
                    if (StringUtils.isMobile(content)) {
                        val chatAdapter = getAdapter() as ChatAdapter
                        chatAdapter.hideSoftKeyboard()
                        val list = ArrayList<BottomSheetItem>()
                        list.add(
                            BottomSheetItem(
                                context.getString(R.string.copy),
                                R.mipmap.msg_copy,
                                object : BottomSheetItem.IBottomSheetClick {
                                    override fun onClick() {
                                        val cm =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val mClipData = ClipData.newPlainText("Label", content)
                                        cm.setPrimaryClip(mClipData)
                                        WKToastUtils.getInstance()
                                            .showToastNormal(context.getString(R.string.copyed))
                                    }
                                })
                        )
                        list.add(
                            BottomSheetItem(
                                context.getString(R.string.call),
                                R.mipmap.msg_calls,
                                object : BottomSheetItem.IBottomSheetClick {
                                    override fun onClick() {
                                        val desc = String.format(
                                            context.getString(R.string.call_phone_permissions_desc),
                                            context.getString(R.string.app_name)
                                        );
                                        WKPermissions.getInstance().checkPermissions(
                                            object : IPermissionResult {
                                                override fun onResult(result: Boolean) {
                                                    if (result) {
                                                        val intent =
                                                            Intent(
                                                                Intent.ACTION_CALL,
                                                                Uri.parse("tel:$content")
                                                            )
                                                        context.startActivity(intent)
                                                    }
                                                }

                                                override fun clickResult(isCancel: Boolean) {

                                                }
                                            },
                                            chatAdapter.conversationContext.chatActivity,
                                            desc,
                                            Manifest.permission.CALL_PHONE
                                        )

                                    }
                                })
                        )
                        list.add(
                            BottomSheetItem(
                                context.getString(R.string.add_to_phone_book),
                                R.mipmap.msg_contacts,
                                object : BottomSheetItem.IBottomSheetClick {
                                    override fun onClick() {

                                        val addIntent = Intent(
                                            Intent.ACTION_INSERT,
                                            Uri.withAppendedPath(
                                                Uri.parse("content://com.android.contacts"),
                                                "contacts"
                                            )
                                        )
                                        addIntent.type = "vnd.android.cursor.dir/person"
                                        addIntent.type = "vnd.android.cursor.dir/contact"
                                        addIntent.type = "vnd.android.cursor.dir/raw_contact"
                                        addIntent.putExtra(
                                            ContactsContract.Intents.Insert.NAME,
                                            ""
                                        )
                                        addIntent.putExtra(
                                            ContactsContract.Intents.Insert.PHONE,
                                            content
                                        )
                                        context.startActivity(addIntent)

                                    }
                                })
                        )
                        list.add(
                            BottomSheetItem(
                                context.getString(R.string.str_search),
                                R.mipmap.ic_ab_search,
                                object : BottomSheetItem.IBottomSheetClick {
                                    override fun onClick() {
                                        if (uiChatMsgItemEntity.iLinkClick != null)
                                            uiChatMsgItemEntity.iLinkClick.onShowSearchUser(
                                                content
                                            )
                                    }
                                })
                        )
//                        val phoneTips = String.format(
//                            context.getString(R.string.phone_tips),
//                            context.getString(R.string.app_name)
//                        )
                        val displaySpans = SpannableStringBuilder()
                        displaySpans.append(content)
                        displaySpans.setSpan(
                            StyleSpan(Typeface.BOLD), 0,
                            content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        displaySpans.setSpan(
                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.blue)), 0,
                            content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        WKDialogUtils.getInstance()
                            .showBottomSheet(context, displaySpans, false, list)
                        return
                    }
                    if (StringUtils.isEmail(content)) {
                        val displaySpans = SpannableStringBuilder()
                        displaySpans.append(content)
                        displaySpans.setSpan(
                            StyleSpan(Typeface.BOLD), 0,
                            content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        displaySpans.setSpan(
                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.blue)), 0,
                            content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        val list = ArrayList<BottomSheetItem>()
                        list.add(
                            BottomSheetItem(
                                context.getString(R.string.copy),
                                R.mipmap.msg_copy,
                                object : BottomSheetItem.IBottomSheetClick {
                                    override fun onClick() {
                                        val cm =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val mClipData = ClipData.newPlainText("Label", content)
                                        cm.setPrimaryClip(mClipData)
                                        WKToastUtils.getInstance()
                                            .showToastNormal(context.getString(R.string.copyed))
                                    }
                                })
                        )
                        list.add(
                            BottomSheetItem(
                                context.getString(R.string.send_email),
                                R.mipmap.msg2_email,
                                object : BottomSheetItem.IBottomSheetClick {
                                    override fun onClick() {
                                        val uri = Uri.parse("mailto:$content")
                                        val email = arrayOf(content)
                                        val intent = Intent(Intent.ACTION_SENDTO, uri)
                                        intent.putExtra(Intent.EXTRA_CC, email) // 抄送人
                                        intent.putExtra(Intent.EXTRA_SUBJECT, "") // 主题
                                        intent.putExtra(Intent.EXTRA_TEXT, "") // 正文
                                        context.startActivity(Intent.createChooser(intent, ""))
                                    }
                                })
                        )
                        list.add(
                            BottomSheetItem(
                                context.getString(R.string.str_search),
                                R.mipmap.ic_ab_search,
                                object : BottomSheetItem.IBottomSheetClick {
                                    override fun onClick() {
                                        if (uiChatMsgItemEntity.iLinkClick != null)
                                            uiChatMsgItemEntity.iLinkClick.onShowSearchUser(
                                                content
                                            )
                                        // if (iLinkClick != null) iLinkClick.onShowSearchUser(content)
                                    }
                                })
                        )
                        WKDialogUtils.getInstance()
                            .showBottomSheet(context, displaySpans, false, list)
                        return
                    }
                }
            }


            /**
             * 全选显示自定义弹窗回调
             */
            override fun onSelectAllShowCustomPop(local: FloatArray) {
                showPopup(uiChatMsgItemEntity, textView, local)
            }

            /**
             * 重置回调
             */
            override fun onReset() {
            }

            /**
             * 解除自定义弹窗回调
             */
            override fun onDismissCustomPop() {
            }

            /**
             * 是否正在滚动回调
             */
            override fun onScrolling() {
            }
        })


    }

    private fun showPopup(uiChatMsgItemEntity: WKUIChatMsgItemEntity, v: View, local: FloatArray) {
        val mMsgConfig: MsgConfig = getMsgConfig(uiChatMsgItemEntity.wkMsg.type)
        var isShowReaction = false
        val `object` = EndpointManager.getInstance()
            .invoke(
                "is_show_reaction",
                CanReactionMenu(uiChatMsgItemEntity.wkMsg, mMsgConfig)
            )
        if (`object` != null) {
            isShowReaction = `object` as Boolean
        }
        if (uiChatMsgItemEntity.wkMsg.flame == 1) isShowReaction = false
        val finalIsShowReaction = isShowReaction
        showChatPopup(
            uiChatMsgItemEntity.wkMsg,
            v,
            local,
            finalIsShowReaction,
            getPopupList(uiChatMsgItemEntity.wkMsg)
        )
    }

    //    private fun setSelectableTextHelper(
//        textView: TextView?,
//        position: Int,
//        isEmoji: Boolean
//    ) {
//       val selectableTextHelper = SelectTextHelper.Builder(textView)
//            .setCursorHandleColor(
//                context.getColor(R.color.blue)
//            )
//            .setCursorHandleSizeInDp(16f)
//            .setSelectedColor(
//                context.getColor(R.color.blue)
//            )
//            .setSelectAll(true)
//            .setIsEmoji(isEmoji)
//            .setScrollShow(false)
//            .setSelectedAllNoPop(true)
//            .setMagnifierShow(false)
//            .build()
//        selectableTextHelper.setSelectListener(object : SelectTextHelper.OnSelectListener {
//            override fun onClick(v: View) {}
//            override fun onLongClick(v: View) {}
//            override fun onTextSelected(content: CharSequence) {
//                val selectedText = content.toString()
//               // msg.setSelectText(selectedText)
////                if (onItemClickListener != null) {
////                    onItemClickListener.onTextSelected(msgArea, position, msg)
////                }
//            }
//
//            override fun onDismiss() {
////                msg.setSelectText(msg.getExtra())
//            }
//
//            override fun onClickUrl(url: String) {}
//            override fun onSelectAllShowCustomPop() {}
//            override fun onReset() {
////                msg.setSelectText(null)
////                msg.setSelectText(msg.getExtra())
//            }
//
//            override fun onDismissCustomPop() {}
//            override fun onScrolling() {}
//        })
//    }
    override val itemViewType: Int
        get() = WKContentType.WK_TEXT


    private fun shotTipsMsg(mTextContent: WKTextContent) {
        var clientMsgNo = mTextContent.reply.message_id
        val mMsg =
            WKIM.getInstance().msgManager.getWithMessageID(mTextContent.reply.message_id)
        if (mMsg != null) {
            clientMsgNo = mMsg.clientMsgNO
        }
        (Objects.requireNonNull(getAdapter()) as ChatAdapter).showTipsMsg(clientMsgNo)
    }

//    private fun showLinkInfo(
//        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
//        msgTimeStatusView: View,
//        parentView: LinearLayout,
//        from: WKChatIteMsgFromType,
//        url: String
//    ) {
//        uiChatMsgItemEntity.isUpdateStatus = false
//        val linkView = LayoutInflater.from(context)
//            .inflate(R.layout.chat_text_link_desc_layout, parentView, false)
//        val msgTimeView = linkView.findViewById<View>(R.id.msgTimeView)
//        setMsgTimeAndStatus(uiChatMsgItemEntity, msgTimeView, from)
//        val titleTv = linkView.findViewById<TextView>(R.id.linkTitleTv)
//        val nameTv = linkView.findViewById<TextView>(R.id.linkNameTv)
//        val contentTv = linkView.findViewById<TextView>(R.id.linkContentTv)
//        val logoIv = linkView.findViewById<AppCompatImageView>(R.id.linkLogoIv)
//        val coverIv = linkView.findViewById<AppCompatImageView>(R.id.linkCoverIv)
//        if (from == WKChatIteMsgFromType.SEND) {
//            contentTv.setTextColor(ContextCompat.getColor(context, R.color.send_text_color))
//            nameTv.setTextColor(ContextCompat.getColor(context, R.color.send_text_color))
//            titleTv.setTextColor(ContextCompat.getColor(context, R.color.send_text_color))
//        } else {
//            contentTv.setTextColor(ContextCompat.getColor(context, R.color.receive_text_color))
//            nameTv.setTextColor(ContextCompat.getColor(context, R.color.receive_text_color))
//            titleTv.setTextColor(ContextCompat.getColor(context, R.color.receive_text_color))
//        }
//        val jsonStr = WKSharedPreferencesUtil.getInstance().getSP(url)
//        var jsonObject: JSONObject? = null
//        try {
//            if (!TextUtils.isEmpty(jsonStr)) jsonObject = JSONObject(jsonStr)
//        } catch (e: JSONException) {
//            e.printStackTrace()
//        }
//        if (jsonObject == null) {
//            parentView.visibility = View.GONE
//            msgTimeStatusView.visibility = View.VISIBLE
//        } else {
//            val title = jsonObject.optString("title")
//            val content = jsonObject.optString("content")
//            val coverURL = jsonObject.optString("coverURL")
//            val logo = jsonObject.optString("logo")
//            if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(content)) {
//                titleTv.text = title
//                contentTv.text = content
//                Glide.with(context).asBitmap().load(logo)
//                    .into(object : CustomTarget<Bitmap?>(SIZE_ORIGINAL, SIZE_ORIGINAL) {
//                        override fun onResourceReady(
//                            resource: Bitmap, transition: Transition<in Bitmap?>?
//                        ) {
//                            logoIv.visibility = View.VISIBLE
//                            logoIv.setImageBitmap(resource)
//                        }
//
//                        override fun onLoadCleared(placeholder: Drawable?) {}
//                        override fun onLoadFailed(errorDrawable: Drawable?) {
//                            super.onLoadFailed(errorDrawable)
//                            logoIv.visibility = View.GONE
//                        }
//                    })
//                // GlideUtils.getInstance().showImg(getContext(), logo, logoIv);
//                if (!TextUtils.isEmpty(coverURL)) {
//                    // GlideUtils.getInstance().showImg(getContext(), coverURL.replaceAll(" ", ""), coverIv);
//                    Glide.with(context).asBitmap().load(coverURL.replace(" ".toRegex(), ""))
//                        .into(object : CustomTarget<Bitmap?>(SIZE_ORIGINAL, SIZE_ORIGINAL) {
//                            override fun onResourceReady(
//                                resource: Bitmap, transition: Transition<in Bitmap?>?
//                            ) {
//                                coverIv.visibility = View.VISIBLE
//                                coverIv.setImageBitmap(resource)
//                            }
//
//                            override fun onLoadCleared(placeholder: Drawable?) {
//
//                            }
//
//                            override fun onLoadFailed(errorDrawable: Drawable?) {
//                                super.onLoadFailed(errorDrawable)
//                                coverIv.visibility = View.GONE
//                            }
//
//                        })
//                } else coverIv.visibility = View.GONE
//                val strings = url.split("\\.").toTypedArray()
//                if (strings.size > 1) {
//                    val stringBuffer = StringBuffer()
//                    for (i in 1 until strings.size) {
//                        if (!TextUtils.isEmpty(stringBuffer)) stringBuffer.append(".")
//                        stringBuffer.append(strings[i])
//                    }
//                    nameTv.text = stringBuffer
//                }
//                parentView.removeAllViews()
//                parentView.addView(linkView)
//                parentView.visibility = View.VISIBLE
//                msgTimeStatusView.visibility = View.GONE
//            } else {
//                parentView.visibility = View.GONE
//                msgTimeStatusView.visibility = View.VISIBLE
//            }
//        }
//    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
//        val linkView = parentView.findViewById<LinearLayout>(R.id.linkView)
//        if (linkView != null && linkView.childCount > 0) {
//            val msgTimeView = linkView.getChildAt(0)
//            setMsgTimeAndStatus(uiChatMsgItemEntity, msgTimeView, from)
//        }
    }

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val contentTvLayout = parentView.findViewById<BubbleLayout>(R.id.contentTvLayout)
        val textContentLayout = parentView.findViewById<View>(R.id.textContentLayout)
        val msgTimeView = parentView.findViewById<View>(R.id.msgTimeView)
        // 这里要指定文本宽度 - padding的距离
        if (textContentLayout == null || msgTimeView == null) {
            return
        }
        textContentLayout.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        contentTvLayout.setAll(bgType, from, WKContentType.WK_TEXT)
        if (textContentLayout.layoutParams.width < msgTimeView.layoutParams.width) {
            textContentLayout.layoutParams.width = msgTimeView.layoutParams.width
        }
    }

    override fun resetFromName(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val receivedTextNameTv = parentView.findViewById<TextView>(R.id.receivedTextNameTv)
        setFromName(uiChatMsgItemEntity, from, receivedTextNameTv)
    }

    override fun refreshReply(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.refreshReply(adapterPosition, parentView, uiChatMsgItemEntity, from)
        val textModel = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as WKTextContent
        val replyContentRevokedTv = parentView.findViewWithTag<View>("replyRevokedTv")
        val replyContentLayout = parentView.findViewWithTag<View>("replyContentLayout")
        if (replyContentRevokedTv == null || replyContentLayout == null)
            return
        if (textModel.reply != null) {
            if (uiChatMsgItemEntity.wkMsg.baseContentMsgModel.reply.revoke == 1) {
                replyContentRevokedTv.visibility = View.VISIBLE
                replyContentLayout.visibility = View.GONE
            } else {
                val replyIV = parentView.findViewWithTag<AppCompatImageView>("replyIV")
                val replyTV = parentView.findViewWithTag<AppCompatTextView>("replyTV")
                if (replyIV != null && replyTV != null) {
                    showReplyContent(textModel, replyIV, replyTV)
                }
            }
        }
    }

    private fun replyView(
        contentLayout: BubbleLayout,
        from: WKChatIteMsgFromType,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity
    ) {
        val replyLayout = LinearLayout(context)
        replyLayout.orientation = LinearLayout.HORIZONTAL
        replyLayout.background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.rgb(255, 247, 250))
            setStroke(dp(1), Color.rgb(255, 232, 240))
        }
        contentLayout.addView(
            replyLayout, 1,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                5f,
                0f,
                10f
            )
        )
        val lineView = View(context)
        lineView.setBackgroundResource(R.drawable.reply_line)
        replyLayout.addView(
            lineView,
            LayoutHelper.createLinear(3, LayoutHelper.MATCH_PARENT, 0f, 0f, 5f, 0f)
        )

        // revoke
        val replyContentRevokedTv = AppCompatTextView(context)
        replyLayout.addView(
            replyContentRevokedTv,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                10f,
                10f,
                10f
            )
        )
        replyContentRevokedTv.setTextColor(ContextCompat.getColor(context, R.color.popupTextColor))
        replyContentRevokedTv.setText(R.string.reply_msg_is_revoked)
        val size = context.resources.getDimension(R.dimen.font_size_14)
        val pSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            size,
            contentLayout.resources.displayMetrics
        )
        replyContentRevokedTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        // reply content layout
        val replyContentLayout = LinearLayout(context)
        replyContentLayout.orientation = LinearLayout.VERTICAL
        replyLayout.addView(
            replyContentLayout,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                5f,
                5f,
                5f
            )
        )

        val userLayout = LinearLayout(context)
        replyContentLayout.addView(
            userLayout,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
        )
        userLayout.orientation = LinearLayout.HORIZONTAL
        val avatarView = AvatarView(context)
        avatarView.setSize(20f)
        val userNameTv = AppCompatTextView(context)
        val nameSize = context.resources.getDimension(R.dimen.font_size_12)
        val namePSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            nameSize,
            contentLayout.resources.displayMetrics
        )
        userNameTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, namePSize)
        userNameTv.setTextColor(ContextCompat.getColor(context, R.color.color999))
        userLayout.addView(
            avatarView,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
        )
        userLayout.addView(
            userNameTv,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                5f,
                0f,
                0f,
                0f
            )
        )
        val replyTV = AppCompatTextView(context)
        replyTV.ellipsize = TextUtils.TruncateAt.END
        replyTV.isSingleLine = true
        replyTV.setLines(1)
        replyContentLayout.addView(
            replyTV,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                10f,
                0f,
                0f
            )
        )
        replyTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, pSize)
        val textColor: Int = if (from == WKChatIteMsgFromType.SEND) {
            ContextCompat.getColor(context, R.color.colorDark)
        } else {
            ContextCompat.getColor(context, R.color.receive_text_color)
        }
        replyTV.setTextColor(textColor)

        val replyIV = AppCompatImageView(context)
        replyIV.scaleType = ImageView.ScaleType.CENTER
        replyContentLayout.addView(replyIV, LayoutHelper.createLinear(80, 80, 0f, 10f, 0f, 0f))

        val textModel = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as WKTextContent
        val mChannel = WKIM.getInstance().channelManager.getChannel(
            textModel.reply.from_uid, WKChannelType.PERSONAL
        )
        if (mChannel != null) {
            val showName =
                if (TextUtils.isEmpty(mChannel.channelRemark)) {
                    mChannel.channelName
                } else mChannel.channelRemark
            userNameTv.text = showName
            avatarView.showAvatar(mChannel)
        }
        if (!TextUtils.isEmpty(uiChatMsgItemEntity.wkMsg.fromUID)) {
            val colors =
                WKBaseApplication.getInstance().context.resources.getIntArray(R.array.name_colors)
            val index = abs(textModel.reply.from_uid.hashCode()) % colors.size
            val myShapeDrawable = lineView.background as GradientDrawable
            myShapeDrawable.setColor(colors[index])
            userNameTv.setTextColor(colors[index])
            // 回复气囊固定使用特浅粉色，不再按用户名颜色覆盖背景。
            val bgShapeDrawable = replyLayout.background as GradientDrawable
            bgShapeDrawable.setColor(Color.rgb(255, 247, 250))
        }
        if (textModel.reply.revoke == 1) {
            replyContentLayout.visibility = View.GONE
            replyContentRevokedTv.visibility = View.VISIBLE
            return
        }
        replyContentRevokedTv.visibility = View.GONE
        showReplyContent(textModel, replyIV, replyTV)
        replyLayout.setOnClickListener {
            shotTipsMsg(
                textModel
            )
        }
        replyTV.setOnClickListener {
            shotTipsMsg(
                textModel
            )
        }

        replyContentRevokedTv.tag = "replyRevokedTv"
        replyIV.tag = "replyIV"
        replyTV.tag = "replyTV"
        replyContentLayout.tag = "replyContentLayout"
    }

    private fun showReplyContent(
        mTextContent: WKTextContent,
        replyIv: AppCompatImageView,
        replyTv: AppCompatTextView
    ) {
        when (mTextContent.reply.payload.type) {
            WKContentType.WK_GIF -> {
                replyIv.visibility = View.VISIBLE
                replyTv.visibility = View.GONE
                val gifContent = mTextContent.reply.payload as WKGifContent
                GlideUtils.getInstance()
                    .showGif(
                        context,
                        WKApiConfig.getShowUrl(gifContent.url),
                        replyIv,
                        null
                    )
            }

            WKContentType.WK_IMAGE -> {
                replyIv.visibility = View.VISIBLE
                replyTv.visibility = View.GONE
                val imageContent = mTextContent.reply.payload as WKImageContent
                var showUrl: String
                if (!TextUtils.isEmpty(imageContent.localPath)) {
                    showUrl = imageContent.localPath
                    val file = File(showUrl)
                    if (!file.exists()) {
                        //如果本地文件被删除就显示网络图片
                        showUrl = WKApiConfig.getShowUrl(imageContent.url)
                    }
                } else {
                    showUrl = WKApiConfig.getShowUrl(imageContent.url)
                }
                GlideUtils.getInstance().showImg(context, showUrl, replyIv)
            }

            else -> {
                replyIv.visibility = View.GONE
                replyTv.visibility = View.VISIBLE
                var content = mTextContent.reply.payload.displayContent
                if (mTextContent.reply.contentEditMsgModel != null && !TextUtils.isEmpty(
                        mTextContent.reply.contentEditMsgModel.displayContent
                    )
                ) {
                    content = mTextContent.reply.contentEditMsgModel.displayContent
                }
                if (TextUtils.isEmpty(content)) {
                    content = context.getString(R.string.base_unknow_msg)
                }
                replyTv.movementMethod = LinkMovementMethod.getInstance()
                val strUrls = StringUtils.getStrUrls(content)
                val replySpan = SpannableStringBuilder()
                replySpan.append(content)
                if (strUrls.isNotEmpty()) {
                    for (url in strUrls) {
                        if (TextUtils.isEmpty(url)) {
                            continue
                        }
                        var fromIndex = 0
                        while (fromIndex >= 0) {
                            fromIndex = content.indexOf(url, fromIndex)
                            if (fromIndex >= 0) {
                                replySpan.setSpan(
                                    StyleSpan(Typeface.BOLD),
                                    fromIndex,
                                    fromIndex + url.length,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                replySpan.setSpan(
                                    NormalClickableSpan(true,
                                        ContextCompat.getColor(context, R.color.blue),
                                        NormalClickableContent(
                                            NormalClickableContent.NormalClickableTypes.URL,
                                            url
                                        ),
                                        object : NormalClickableSpan.IClick {
                                            override fun onClick(view: View) {
                                                SoftKeyboardUtils.getInstance()
                                                    .hideSoftKeyboard(context as Activity)
                                                val intent = Intent(
                                                    context, WKWebViewActivity::class.java
                                                )
                                                intent.putExtra("url", url)
                                                context.startActivity(intent)
                                            }
                                        }),
                                    fromIndex,
                                    fromIndex + url.length,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                fromIndex += url.length
                            }
                        }
                    }
                }

                // emoji
                val matcher = EmojiManager.getInstance().pattern.matcher(content)
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    val emoji = content.substring(start, end)
                    val d = MoonUtil.getEmotDrawable(context, emoji, MoonUtil.SMALL_SCALE)
                    if (d != null) {
                        val span: AlignImageSpan =
                            object : AlignImageSpan(d, ALIGN_CENTER) {
                                override fun onClick(view: View) {}
                            }
                        replySpan.setSpan(
                            span,
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                replyTv.text = replySpan
            }
        }
    }
}
