package com.chat.base.msgitem

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.R
import com.chat.base.ui.Theme
import com.chat.base.utils.AndroidUtilities

/**
 * Reusable UI helpers for the input-area translation panel.
 *
 * Put this file in your app module and use it from your chat input panel class.
 * It is intentionally not wired into WKChatBaseProvider because message item providers
 * should not own input-bar UI state.
 */
object TranslatePanelUi {

    val defaultLanguages = listOf(
        "中文",
        "English",
        "မြန်မာစာ",
        "日本語",
        "한국어",
        "ภาษาไทย",
        "Tiếng Việt",
        "Русский"
    )

    val HIGH_FIDELITY_TRANSLATE_PROMPT: String = """
        你是多语言聊天翻译专家，擅长翻译日常聊天、私信、评论、社交软件消息和生活对话。
        
        请自动识别我输入的语言，并翻译成另一方能自然理解的目标语言。
        如果我指定目标语言，就严格按我指定的语言翻译。
        
        翻译风格：高保真自然翻译。
        
        核心要求：
        1. 忠实原文意思，保留语气、情绪、称呼、轻重程度和表达顺序。
        2. 不新增、不删减、不总结、不解释、不美化、不改写。
        3. 原文随意，译文也随意；原文冷淡、强硬、委屈、讽刺或暧昧，译文也保持对应感觉。
        4. 遇到口语、省略句、短句，不要擅自补全原文没有说出的内容。
        5. 遇到歧义，尽量保留歧义，不要擅自替原文选择一种解释。
        6. 译文要符合目标语言的日常表达习惯，读起来自然地道，不生硬。
        7. 保留原文换行、表情符号、链接、用户名、代码块、Markdown 和列表结构。
        
        输出要求：
        默认只输出译文，无需解释。
    """.trimIndent()

    fun createPanelBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = AndroidUtilities.dp(22f).toFloat()
            setColor(Color.argb(222, 255, 255, 255))
            setStroke(
                AndroidUtilities.dp(0.7f).coerceAtLeast(1),
                Color.argb(95, 255, 255, 255)
            )
        }
    }

    fun createTranslateGlyph(context: Context): FrameLayout {
        val box = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                AndroidUtilities.dp(21f),
                AndroidUtilities.dp(18f)
            )
        }

        val a = TextView(context).apply {
            text = "A"
            textSize = 10f
            gravity = Gravity.CENTER
            alpha = 0.68f
            setTextColor(ContextCompat.getColor(context, R.color.color999))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            translationX = -AndroidUtilities.dp(1.5f).toFloat()
            translationY = AndroidUtilities.dp(3f).toFloat()
            includeFontPadding = false
        }

        val wen = TextView(context).apply {
            text = "文"
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(Theme.colorAccount)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            translationX = AndroidUtilities.dp(3f).toFloat()
            translationY = -AndroidUtilities.dp(2.5f).toFloat()
            includeFontPadding = false
        }

        box.addView(
            a,
            FrameLayout.LayoutParams(
                AndroidUtilities.dp(14f),
                AndroidUtilities.dp(14f),
                Gravity.START or Gravity.BOTTOM
            )
        )

        box.addView(
            wen,
            FrameLayout.LayoutParams(
                AndroidUtilities.dp(15f),
                AndroidUtilities.dp(15f),
                Gravity.END or Gravity.TOP
            )
        )

        return box
    }

    fun animatePanelIn(view: View) {
        view.animate().cancel()
        view.clearAnimation()

        view.alpha = 0f
        view.translationY = AndroidUtilities.dp(10f).toFloat()
        view.scaleX = 0.98f
        view.scaleY = 0.98f

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun showLanguageSheet(
        context: Context,
        current: String,
        title: String = "选择语言",
        languages: List<String> = defaultLanguages,
        onSelected: (String) -> Unit
    ) {
        val activity = findActivity(context) ?: return
        if (activity.isFinishing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AndroidUtilities.dp(18f),
                AndroidUtilities.dp(14f),
                AndroidUtilities.dp(18f),
                AndroidUtilities.dp(18f)
            )
            background = createPanelBackground()
            alpha = 0f
            translationY = AndroidUtilities.dp(18f).toFloat()
        }

        val titleView = TextView(activity).apply {
            text = title
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(51, 51, 51))
            setPadding(0, 0, 0, AndroidUtilities.dp(8f))
            includeFontPadding = true
        }

        root.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        languages.distinct().forEach { lang ->
            root.addView(
                createLanguageRow(activity, lang, lang == current) {
                    onSelected(lang)
                    dialog.dismiss()
                }
            )
        }

        dialog.setContentView(root)

        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setDimAmount(0.18f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setGravity(Gravity.BOTTOM)
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                attributes = attributes.apply {
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        setBackgroundBlurRadius(AndroidUtilities.dp(18f))
                    } catch (_: Throwable) {
                    }
                }
            }

            root.animate().cancel()
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(190L)
                .setInterpolator(OvershootInterpolator(0.75f))
                .start()
        }

        dialog.show()
    }

    private fun createLanguageRow(
        context: Context,
        lang: String,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = if (selected) "$lang  ✓" else lang
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            minHeight = AndroidUtilities.dp(48f)
            setPadding(
                AndroidUtilities.dp(4f),
                AndroidUtilities.dp(10f),
                AndroidUtilities.dp(4f),
                AndroidUtilities.dp(10f)
            )
            setTextColor(
                if (selected) {
                    Theme.colorAccount
                } else {
                    Color.rgb(51, 51, 51)
                }
            )
            typeface = if (selected) {
                android.graphics.Typeface.DEFAULT_BOLD
            } else {
                android.graphics.Typeface.DEFAULT
            }

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
                        onClick()
                    }
                    .start()
            }
        }
    }

    private fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }
}

/**
 * Status view for the input translation toggle.
 * It draws a subtle left half-arc plus a bigger state dot with halo.
 */
class TranslateStatusView(context: Context) : View(context) {

    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            animateStateChange()
            invalidate()
        }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dp(1.45f).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(AndroidUtilities.dp(22f), AndroidUtilities.dp(16f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        val dotRadius = AndroidUtilities.dp(3.5f).toFloat()
        val haloRadius = AndroidUtilities.dp(6.2f).toFloat()
        val dotX = width - AndroidUtilities.dp(6f).toFloat()

        val activeColor = Color.rgb(34, 197, 94)
        val inactiveColor = Color.rgb(148, 163, 184)
        val color = if (active) activeColor else inactiveColor

        arcPaint.color = color
        arcPaint.alpha = if (active) 215 else 150

        val arcRect = RectF(
            AndroidUtilities.dp(2f).toFloat(),
            centerY - AndroidUtilities.dp(6f),
            AndroidUtilities.dp(15f).toFloat(),
            centerY + AndroidUtilities.dp(6f)
        )

        canvas.drawArc(arcRect, 100f, 160f, false, arcPaint)

        haloPaint.color = color
        haloPaint.alpha = if (active) 48 else 32
        canvas.drawCircle(dotX, centerY, haloRadius, haloPaint)

        dotPaint.color = color
        dotPaint.alpha = if (active) 255 else 220
        canvas.drawCircle(dotX, centerY, dotRadius, dotPaint)
    }

    private fun animateStateChange() {
        animate().cancel()
        scaleX = 0.92f
        scaleY = 0.92f
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(140L)
            .setInterpolator(OvershootInterpolator(1.15f))
            .start()
    }
}

/**
 * Circular collapse/expand button for the translated area.
 * The arrow is drawn manually so it is thicker and visually centered than a text glyph.
 */
class TranslationCollapseButton(context: Context) : View(context) {

    var expanded: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            animate().cancel()
            animate()
                .rotation(if (value) 0f else 180f)
                .setDuration(160L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            invalidate()
        }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(238, 255, 255, 255)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dp(0.8f).coerceAtLeast(1).toFloat()
        color = Color.argb(90, 0, 0, 0)
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dp(2.6f).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(95, 99, 104)
    }

    private val arrowPath = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = AndroidUtilities.dp(34f)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - AndroidUtilities.dp(1f)

        canvas.drawCircle(cx, cy, r, circlePaint)
        canvas.drawCircle(cx, cy, r, strokePaint)

        val half = AndroidUtilities.dp(5.2f).toFloat()
        val top = cy - AndroidUtilities.dp(1.4f)
        val bottom = cy + AndroidUtilities.dp(4.2f)

        arrowPath.reset()
        arrowPath.moveTo(cx - half, top)
        arrowPath.lineTo(cx, bottom)
        arrowPath.lineTo(cx + half, top)

        canvas.drawPath(arrowPath, arrowPaint)
    }
}
