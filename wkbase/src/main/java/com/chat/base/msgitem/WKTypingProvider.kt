package com.chat.base.msgitem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.R
import com.chat.base.ui.components.TypingView
import com.chat.base.utils.AndroidUtilities
import com.chat.base.views.BubbleLayout

/**
 * Renders the temporary "typing" row only.
 *
 * Expiry ownership intentionally lives in ChatActivity, where typing CMDs are received. RecyclerView
 * binding is not a reliable signal that the peer typed again, so this provider must stay stateless.
 */
class WKTypingProvider : WKChatBaseProvider() {

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.chat_typing_layout, parentView, false)
            .also { view ->
                view.findViewById<TypingView>(R.id.spin_kit)?.apply {
                    setDotColor(ContextCompat.getColor(context, R.color.colorDark))
                    setDotRadius(AndroidUtilities.dp(3f).toFloat())
                    setDotSpacing(AndroidUtilities.dp(2f).toFloat())
                }
            }
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val message = uiChatMsgItemEntity.wkMsg ?: return
        parentView.findViewById<BubbleLayout>(R.id.contentLayout)?.setAll(
            getMsgBgType(
                uiChatMsgItemEntity.previousMsg,
                message,
                uiChatMsgItemEntity.nextMsg
            ),
            from,
            WKContentType.typing
        )
        parentView.findViewById<TextView>(R.id.receivedTextNameTv)?.let { nameView ->
            setFromName(uiChatMsgItemEntity, from, nameView)
        }
    }

    override val itemViewType: Int
        get() = WKContentType.typing
}
