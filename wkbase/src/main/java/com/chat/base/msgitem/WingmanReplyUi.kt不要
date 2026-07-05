package com.chat.base.msgitem

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.R
import com.chat.base.ui.Theme
import com.chat.base.utils.AndroidUtilities

/**
 * UI helper for the wingman quick-reply bubbles shown under a translated peer message.
 *
 * Recommended usage in your concrete text message provider:
 *
 * val replies = getWingmanReplies(uiChatMsgItemEntity.wkMsg)
 * WingmanReplyUi.bind(
 *     host = replyContainer,
 *     replies = replies,
 *     onReplyClick = { reply -> fillInputWithWingmanReply(reply.text) },
 *     onNeedHide = { clearWingmanReplies(uiChatMsgItemEntity.wkMsg) }
 * )
 *
 * In your input view click/focus listener, call:
 * WingmanReplyUi.hide(replyContainer)
 * clearWingmanReplies()
 *
 * Note:
 * attachHideOnInput() installs click/focus listeners on inputView. If your input view already owns
 * listeners, prefer calling hide() manually from those existing listeners instead of using attachHideOnInput().
 */
object WingmanReplyUi {

    private const val MAX_REPLY_COUNT = 5

    fun bind(
        host: ViewGroup,
        replies: List<ChatWingmanReply>,
        onReplyClick: (ChatWingmanReply) -> Unit,
        onNeedHide: (() -> Unit)? = null
    ) {
        // Important for RecyclerView reuse:
        // cancel any pending hide animation first, otherwise the old hide endAction can remove new views.
        host.animate().cancel()
        host.clearAnimation()

        val shouldAnimateIn = host.visibility != View.VISIBLE || host.childCount == 0

        host.alpha = 1f
        host.translationY = 0f
        host.isEnabled = true
        host.removeAllViews()

        val safeReplies = replies
            .filter { it.text.isNotBlank() }
            .take(MAX_REPLY_COUNT)

        if (safeReplies.isEmpty()) {
            host.visibility = View.GONE
            return
        }

        host.visibility = View.VISIBLE

        val context = host.context
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(
                AndroidUtilities.dp(2f),
                AndroidUtilities.dp(2f),
                AndroidUtilities.dp(2f),
                AndroidUtilities.dp(2f)
            )
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        scroll.addView(
            row,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        safeReplies.forEach { reply ->
            row.addView(createBubble(context, reply, onReplyClick))
        }

        row.addView(createCloseButton(context, host, onNeedHide))

        host.addView(
            scroll,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        if (shouldAnimateIn) {
            animateIn(host)
        }
    }

    fun hide(host: ViewGroup) {
        host.animate().cancel()
        host.clearAnimation()

        if (host.visibility != View.VISIBLE) {
            host.removeAllViews()
            host.alpha = 1f
            host.translationY = 0f
            return
        }

        host.isEnabled = false
        host.animate()
            .alpha(0f)
            .translationY(AndroidUtilities.dp(8f).toFloat())
            .setDuration(140L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                host.removeAllViews()
                host.alpha = 1f
                host.translationY = 0f
                host.visibility = View.GONE
                host.isEnabled = true
            }
            .start()
    }

    /**
     * Convenience helper.
     *
     * This replaces inputView's click/focus listeners. If the input view already has important listeners,
     * do not use this method; call WingmanReplyUi.hide(host) from your existing listeners instead.
     */
    fun attachHideOnInput(
        inputView: View,
        host: ViewGroup,
        onNeedHide: (() -> Unit)? = null
    ) {
        inputView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                hide(host)
                onNeedHide?.invoke()
            }
        }

        inputView.setOnClickListener {
            hide(host)
            onNeedHide?.invoke()
        }
    }

    private fun createBubble(
        context: Context,
        reply: ChatWingmanReply,
        onReplyClick: (ChatWingmanReply) -> Unit
    ): TextView {
        return TextView(context).apply {
            text = reply.text
            textSize = 13.5f
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            isSingleLine = true
            contentDescription = reply.text

            setTextColor(Theme.colorAccount)
            setPadding(
                AndroidUtilities.dp(12f),
                0,
                AndroidUtilities.dp(12f),
                0
            )
            background = roundBg(
                Color.argb(235, 238, 242, 255),
                AndroidUtilities.dp(16f).toFloat()
            )

            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false

                animate().cancel()
                animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(70L)
                    .withEndAction {
                        scaleX = 1f
                        scaleY = 1f
                        isEnabled = true
                        onReplyClick(reply)
                    }
                    .start()
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                AndroidUtilities.dp(32f)
            ).apply {
                rightMargin = AndroidUtilities.dp(8f)
            }
        }
    }

    private fun createCloseButton(
        context: Context,
        host: ViewGroup,
        onNeedHide: (() -> Unit)?
    ): TextView {
        return TextView(context).apply {
            text = "×"
            textSize = 18f
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = "隐藏快捷回复"

            setTextColor(ContextCompat.getColor(context, R.color.color999))
            background = roundBg(
                Color.argb(215, 255, 255, 255),
                AndroidUtilities.dp(15f).toFloat()
            )

            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                hide(host)
                onNeedHide?.invoke()
            }

            layoutParams = LinearLayout.LayoutParams(
                AndroidUtilities.dp(30f),
                AndroidUtilities.dp(30f)
            ).apply {
                leftMargin = AndroidUtilities.dp(6f)
                rightMargin = AndroidUtilities.dp(2f)
            }
        }
    }

    private fun animateIn(view: View) {
        view.animate().cancel()
        view.clearAnimation()

        view.alpha = 0f
        view.translationY = AndroidUtilities.dp(8f).toFloat()

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(170L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun roundBg(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            setStroke(
                AndroidUtilities.dp(0.6f).coerceAtLeast(1),
                Color.argb(35, 0, 0, 0)
            )
        }
    }
}
