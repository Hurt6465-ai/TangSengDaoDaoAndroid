package com.chat.uikit.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import com.chat.uikit.view.CircleProgress.Companion.STATUS_ERROR
import com.chat.uikit.view.CircleProgress.Companion.STATUS_PAUSE
import com.chat.uikit.view.CircleProgress.Companion.STATUS_PLAY
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.jvm.JvmName
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class WaveformView : View {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setWaveform(waveform: ByteArray) {
        waveformBytes = waveform
        invalidate()
    }

    private var waveformBytes: ByteArray? = null

    private var innerColor = ContextCompat.getColor(context, R.color.color999)
    private var outerColor = ContextCompat.getColor(context, R.color.colorAccent)
    private var freshColor = ContextCompat.getColor(context, R.color.blue)

    private var paintInner: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var paintOuter: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barRect = RectF()
    private var thumbX = 0

    /**
     * 注意：
     * Boolean 属性 isFresh 默认会在 JVM 生成 setFresh(boolean)。
     * 为了同时兼容 Kotlin 写法 voiceWaveform.isFresh = xxx
     * 和可能存在的 Java/旧代码写法 voiceWaveform.setFresh(xxx)，
     * 这里把属性 setter 的 JVM 名改掉，避免和下面的 setFresh(Boolean) 方法冲突。
     */
    @set:JvmName("setFreshValue")
    var isFresh = false
        set(value) {
            field = value
            invalidate()
        }

    fun setFresh(fresh: Boolean) {
        isFresh = fresh
    }

    fun setProgress(progress: Float) {
        if (progress < 0) {
            return
        }
        thumbX = ceil((width * progress).toDouble()).toInt()
        if (thumbX < 0) {
            thumbX = 0
        } else if (thumbX > width) {
            thumbX = width
        }
        invalidate()
    }

    private var disposable: Disposable? = null
    private var mBindId: String? = null

    fun setBind(id: String?) {
        if (id != mBindId) {
            mBindId = id
        }
    }

    override fun onAttachedToWindow() {
        if (disposable == null) {
            disposable = RxBus.listen(ProgressEvent::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (it.id == mBindId) {
                        if (it.status == STATUS_PAUSE || it.status == STATUS_PLAY) {
                            setProgress(it.progress)
                        }
                    } else {
                        if (it.status == STATUS_PAUSE ||
                            it.status == STATUS_PLAY ||
                            it.status == STATUS_ERROR
                        ) {
                            setProgress(0f)
                        }
                    }
                }
        }
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        disposable?.let {
            if (!it.isDisposed) {
                it.dispose()
                disposable = null
            }
        }
        disposable = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bytes = waveformBytes ?: return
        if (width == 0 || height == 0) {
            return
        }

        val barStep = AndroidUtilities.dp(3f)
        val barWidth = AndroidUtilities.dp(2f).toFloat()
        val radius = AndroidUtilities.dp(1f).toFloat()

        val totalBarsCount = (width / barStep).toFloat()
        if (totalBarsCount <= 0.1f) {
            return
        }

        val samplesCount = bytes.size * 8 / 5
        val samplesPerBar = samplesCount / totalBarsCount
        if (samplesPerBar <= 0f) {
            return
        }

        paintInner.shader = null
        paintOuter.shader = null
        paintInner.color = if (isFresh && thumbX == 0) freshColor else innerColor
        paintOuter.color = outerColor

        // 不再底部对齐，而是以中心线为基准上下展开。
        val centerY = height / 2f

        // 上下留一点空间，避免竖条贴边。
        val verticalPadding = AndroidUtilities.dp(2f).toFloat()
        val maxBarHeight = max(1f, height - verticalPadding * 2f)
        val minBarHeight = AndroidUtilities.dp(3f).toFloat()

        var barCounter = 0f
        var nextBarNum = 0
        var barNum = 0

        for (a in 0 until samplesCount) {
            if (a != nextBarNum) {
                continue
            }

            var drawBarCount = 0
            val lastBarNum = nextBarNum
            while (lastBarNum == nextBarNum) {
                barCounter += samplesPerBar
                nextBarNum = barCounter.toInt()
                drawBarCount++
            }

            val bitPointer = a * 5
            val byteNum = bitPointer / 8
            if (byteNum >= bytes.size) {
                break
            }

            val byteBitOffset = bitPointer - byteNum * 8
            val currentByteCount = 8 - byteBitOffset
            val nextByteRest = 5 - currentByteCount

            var value = (
                bytes[byteNum].toInt() shr byteBitOffset and
                    ((2 shl min(5, currentByteCount)) - 1) - 1
                ).toByte()

            if (nextByteRest > 0 && byteNum + 1 < bytes.size) {
                value = (value.toInt() shl nextByteRest).toByte()
                value = value or (
                    bytes[byteNum + 1] and
                        ((2 shl nextByteRest - 1) - 1).toByte()
                    )
            }

            val amplitude = value.toInt().coerceIn(0, 31) / 31f

            // 让小音量也有可见高度，高音量不生硬顶满。
            val visualAmplitude = 0.18f + amplitude * 0.82f
            val barHeight = max(minBarHeight, maxBarHeight * visualAmplitude)

            for (b in 0 until drawBarCount) {
                val x = barNum * barStep
                val left = x.toFloat()
                val right = left + barWidth
                val top = centerY - barHeight / 2f
                val bottom = centerY + barHeight / 2f

                barRect.set(left, top, right, bottom)

                if (x + barWidth <= thumbX) {
                    canvas.drawRoundRect(barRect, radius, radius, paintOuter)
                } else {
                    canvas.drawRoundRect(barRect, radius, radius, paintInner)

                    // 播放进度切到某根竖条中间时，只给左侧部分上播放色。
                    if (x < thumbX && thumbX < x + barWidth) {
                        canvas.save()
                        canvas.clipRect(left, top, thumbX.toFloat(), bottom)
                        canvas.drawRoundRect(barRect, radius, radius, paintOuter)
                        canvas.restore()
                    }
                }

                barNum++
            }
        }
    }
}
