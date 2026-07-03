package com.chat.rtc

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKUIChatMsgItemEntity

class RtcCallRecordProvider : WKChatBaseProvider() {
    override val itemViewType: Int
        get() = RtcConstants.CONTENT_TYPE_CALL_RECORD

    override val layoutId: Int
        get() = R.layout.wkrtc_chat_call_record_layout

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? = null

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
    }

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        super.convert(helper, item)
        helper.getView<View>(R.id.systemRootView).setOnClickListener {
            (getAdapter() as? ChatAdapter)?.conversationContext?.hideSoftKeyboard()
        }
        val textView = helper.getView<TextView>(R.id.contentTv)
        val content = (item.wkMsg.baseContentMsgModel as? RtcCallRecordContent)?.displayText
            ?: item.wkMsg.baseContentMsgModel?.getDisplayContent()
            ?: item.wkMsg.content
            ?: ""
        textView.text = content
        textView.background = ContextCompat.getDrawable(context, R.drawable.wkrtc_bg_call_record)
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_call)?.mutate()
        icon?.setTint(ContextCompat.getColor(context, R.color.wkrtc_call_record_text))
        textView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        textView.compoundDrawablePadding = com.chat.base.utils.AndroidUtilities.dp(5f)
    }
}
