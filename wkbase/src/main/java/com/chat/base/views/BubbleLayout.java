package com.chat.base.views;

import static android.graphics.Canvas.ALL_SAVE_FLAG;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.chat.base.R;
import com.chat.base.msgitem.WKChatIteMsgFromType;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgBgType;
import com.chat.base.utils.AndroidUtilities;

/**
 * 气泡布局
 */
public class BubbleLayout extends LinearLayout {
    public boolean isSelected;
    private final Paint mPaint;
    private final Path mPath;
    private Look mLook;
    private int mBubblePadding;
    private int mWidth, mHeight;
    private int mLeft, mTop, mRight, mBottom;
    private int mLookPosition, mLookWidth, mLookLength;
    private int mShadowColor, mShadowX, mShadowY;
    private float mShadowRadius;
    private int mBubbleRadius, mBubbleColor;
    private int mBubbleNormalColor, mBubbleSelectedColor;
    // 左上弧度，右上弧度，右下弧度，左下弧度
    private int mLTR, mRTR, mRDR, mLDR;
    // 箭头
    //     箭头尖分左右两个弧度分别是由 mArrowTopLeftRadius, mArrowTopRightRadius 控制
    //     箭头底部左右两个弧度分别是由 mArrowDownLeftRadius, mArrowDownRightRadius 控制
    private int mArrowTopLeftRadius, mArrowTopRightRadius, mArrowDownLeftRadius, mArrowDownRightRadius;

    private OnClickEdgeListener mListener;
    private final Region mRegion = new Region();

    // 气泡背景图资源
    private int mBubbleBgRes = -1;
    // 气泡背景图
    private Bitmap mBubbleImageBg = null;
    // 气泡背景显示区域
    private final RectF mBubbleImageBgDstRectF = new RectF();
    private final Rect mBubbleImageBgSrcRect = new Rect();
    private final Paint mBubbleImageBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint mBubbleImageBgBeforePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    // 气泡边框颜色
    private int mBubbleBorderColor = Color.BLACK;
    // 气泡边框大小
    private int mBubbleBorderSize = 0;
    // 气泡边框画笔
    private final Paint mBubbleBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    // Lightweight liquid-glass rendering. This deliberately avoids real-time backdrop blur:
    // every chat cell is recycled and a blur layer per message would make long conversations stutter.
    // Translucent gradients, a specular highlight and a soft edge create the same visual depth
    // while keeping drawing to a few inexpensive Canvas operations.
    private boolean mLiquidGlassEnabled = false;
    private boolean mLiquidGlassSent = false;
    private final Paint mGlassFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint mGlassHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint mGlassShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint mGlassBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint mGlassShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint mGlassGlintPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF mGlassBounds = new RectF();
    private final RectF mGlassGlintBounds = new RectF();
    private float mGlassShaderLeft = Float.NaN;
    private float mGlassShaderTop = Float.NaN;
    private float mGlassShaderRight = Float.NaN;
    private float mGlassShaderBottom = Float.NaN;
    private int mGlassShaderColor = Integer.MIN_VALUE;
    private boolean mGlassShaderSelected = false;
    private boolean mGlassShaderSent = false;
    private boolean mGlassShaderDark = false;

    /**
     * 箭头指向
     */
    public enum Look {
        /**
         * 坐上右下
         */
        LEFT(1), TOP(2), RIGHT(3), BOTTOM(4);
        int value;

        Look(int v) {
            value = v;
        }

        public static Look getType(int value) {
            Look type = Look.BOTTOM;
            switch (value) {
                case 1:
                    type = Look.LEFT;
                    break;
                case 2:
                    type = Look.TOP;
                    break;
                case 3:
                    type = Look.RIGHT;
                    break;
                case 4:
                    type = Look.BOTTOM;
                    break;
            }

            return type;
        }
    }


    public BubbleLayout(Context context) {
        this(context, null);
    }

    public BubbleLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
//        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setWillNotDraw(false);
        initAttr(context.obtainStyledAttributes(attrs, R.styleable.BubbleLayout, defStyleAttr, 0));
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        mPath = new Path();
        mBubbleImageBgPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        initPadding();
    }

    public void initPadding() {
        int p = (int) (mBubblePadding + mShadowRadius);
        switch (mLook) {
            case BOTTOM:
                setPadding(p, p, p + mShadowX, mLookLength + p + mShadowY);
                break;
            case TOP:
                setPadding(p, p + mLookLength, p + mShadowX, p + mShadowY);
                break;
            case LEFT:
                setPadding(p + mLookLength, p, p + mShadowX, p + mShadowY);
                break;
            case RIGHT:
                setPadding(p, p, p + mLookLength + mShadowX, p + mShadowY);
                break;
        }
    }

    /**
     * 初始化参数
     */
    private void initAttr(TypedArray a) {
        mLook = Look.getType(a.getInt(R.styleable.BubbleLayout_lookAt, Look.BOTTOM.value));
        mLookPosition = a.getDimensionPixelOffset(R.styleable.BubbleLayout_lookPosition, 0);
        mLookWidth = a.getDimensionPixelOffset(R.styleable.BubbleLayout_lookWidth, AndroidUtilities.dp(13F));
        mLookLength = a.getDimensionPixelOffset(R.styleable.BubbleLayout_lookLength, AndroidUtilities.dp(12F));
        mShadowRadius = a.getDimensionPixelOffset(R.styleable.BubbleLayout_shadowRadius, AndroidUtilities.dp(3.3F));
        mShadowX = a.getDimensionPixelOffset(R.styleable.BubbleLayout_shadowX, AndroidUtilities.dp(1F));
        mShadowY = a.getDimensionPixelOffset(R.styleable.BubbleLayout_shadowY, AndroidUtilities.dp(1F));

        mBubbleRadius = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleRadius, AndroidUtilities.dp(8F));
        mLTR = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleLeftTopRadius, -1);
        mRTR = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleRightTopRadius, -1);
        mRDR = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleRightDownRadius, -1);
        mLDR = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleLeftDownRadius, -1);

        mArrowTopLeftRadius = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleArrowTopLeftRadius, AndroidUtilities.dp(3F));
        mArrowTopRightRadius = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleArrowTopRightRadius, AndroidUtilities.dp(3F));
        mArrowDownLeftRadius = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleArrowDownLeftRadius, AndroidUtilities.dp(6F));
        mArrowDownRightRadius = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleArrowDownRightRadius, AndroidUtilities.dp(6F));

        mBubblePadding = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubblePadding, AndroidUtilities.dp(8));
        mShadowColor = a.getColor(R.styleable.BubbleLayout_bubbleShadowColor, Color.GRAY);
        mBubbleColor = a.getColor(R.styleable.BubbleLayout_bubbleColor, Color.WHITE);

        mBubbleBgRes = a.getResourceId(R.styleable.BubbleLayout_bubbleBgRes, -1);
        if (mBubbleBgRes != -1) {
            mBubbleImageBg = BitmapFactory.decodeResource(getResources(), mBubbleBgRes);
        }

        mBubbleBorderColor = a.getColor(R.styleable.BubbleLayout_bubbleBorderColor, Color.BLACK);
        mBubbleBorderSize = a.getDimensionPixelOffset(R.styleable.BubbleLayout_bubbleBorderSize, 0);
        a.recycle();
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
        initData();
    }

    @Override
    public void invalidate() {
        initData();
        super.invalidate();
    }

    @Override
    public void postInvalidate() {
        initData();
        super.postInvalidate();
    }

    /**
     * 初始化数据
     */
    private void initData() {
//        mPaint.setPathEffect(new CornerPathEffect(mBubbleRadius));
        mPaint.setShadowLayer(mShadowRadius, mShadowX, mShadowY, mShadowColor);
        mBubbleBorderPaint.setColor(mBubbleBorderColor);
        mBubbleBorderPaint.setStrokeWidth(mBubbleBorderSize);
        mBubbleBorderPaint.setStyle(Paint.Style.STROKE);

        mLeft = (int) (mShadowRadius + (mShadowX < 0 ? -mShadowX : 0) + (mLook == Look.LEFT ? mLookLength : 0));
        mTop = (int) (mShadowRadius + (mShadowY < 0 ? -mShadowY : 0) + (mLook == Look.TOP ? mLookLength : 0));
        mRight = (int) (mWidth - mShadowRadius + (mShadowX > 0 ? -mShadowX : 0) - (mLook == Look.RIGHT ? mLookLength : 0));
        mBottom = (int) (mHeight - mShadowRadius + (mShadowY > 0 ? -mShadowY : 0) - (mLook == Look.BOTTOM ? mLookLength : 0));
        mPaint.setColor(mBubbleColor);
        mPath.reset();

        int topOffset = (topOffset = mLookPosition) + mLookLength > mBottom ? mBottom - mLookWidth : topOffset;
        // topOffset = Math.max(topOffset, mShadowRadius);
        topOffset = getHeight();
        int leftOffset = (leftOffset = mLookPosition) + mLookLength > mRight ? mRight - mLookWidth : leftOffset;
        leftOffset = (int) Math.max(leftOffset, mShadowRadius);

        switch (mLook) {
            case LEFT:
                // 判断是否足够画箭头，偏移的量 > 气泡圆角 + 气泡箭头下右圆弧
                if (topOffset >= getLTR() + mArrowDownRightRadius) {
                    mPath.moveTo(mLeft, topOffset - mArrowDownRightRadius);
                    mPath.rCubicTo(0F, mArrowDownRightRadius,
                            -mLookLength, mLookWidth / 2F - mArrowTopRightRadius + mArrowDownRightRadius,
                            -mLookLength, mLookWidth / 2F + mArrowDownRightRadius);
                } else {
                    // 起点移动到箭头尖
                    mPath.moveTo(mLeft - mLookLength, topOffset + mLookWidth / 2F);
                }

                // 判断是否足够画箭头，偏移的量 + 箭头宽 <= 气泡高 - 气泡圆角 - 气泡箭头下右圆弧
                if (topOffset + mLookWidth < mBottom - getLDR() - mArrowDownLeftRadius) {
                    mPath.rCubicTo(0F, mArrowTopLeftRadius,
                            mLookLength, mLookWidth / 2F,
                            mLookLength, mLookWidth / 2F + mArrowDownLeftRadius);
                    mPath.lineTo(mLeft, mBottom - getLDR());
                }
                mPath.quadTo(mLeft, mBottom,
                        mLeft + getLDR(), mBottom);
                mPath.lineTo(mRight - getRDR(), mBottom);
                mPath.quadTo(mRight, mBottom, mRight, mBottom - getRDR());
                mPath.lineTo(mRight, mTop + getRTR());
                mPath.quadTo(mRight, mTop, mRight - getRTR(), mTop);
                mPath.lineTo(mLeft + getLTR(), mTop);
                if (topOffset >= getLTR() + mArrowDownRightRadius) {
                    mPath.quadTo(mLeft, mTop, mLeft, mTop + getLTR());
                } else {
                    mPath.quadTo(mLeft, mTop, mLeft - mLookLength, topOffset + mLookWidth / 2F);
                }
                break;
            case TOP:
                if (leftOffset >= getLTR() + mArrowDownLeftRadius) {
                    mPath.moveTo(leftOffset - mArrowDownLeftRadius, mTop);
                    mPath.rCubicTo(mArrowDownLeftRadius, 0,
                            mLookWidth / 2F - mArrowTopLeftRadius + mArrowDownLeftRadius, -mLookLength,
                            mLookWidth / 2F + mArrowDownLeftRadius, -mLookLength);
                } else {
                    mPath.moveTo(leftOffset + mLookWidth / 2F, mTop - mLookLength);
                }

                if (leftOffset + mLookWidth < mRight - getRTR() - mArrowDownRightRadius) {
                    mPath.rCubicTo(mArrowTopRightRadius, 0F,
                            mLookWidth / 2F, mLookLength,
                            mLookWidth / 2F + mArrowDownRightRadius, mLookLength);
                    mPath.lineTo(mRight - getRTR(), mTop);
                }
                mPath.quadTo(mRight, mTop, mRight, mTop + getRTR());
                mPath.lineTo(mRight, mBottom - getRDR());
                mPath.quadTo(mRight, mBottom, mRight - getRDR(), mBottom);
                mPath.lineTo(mLeft + getLDR(), mBottom);
                mPath.quadTo(mLeft, mBottom, mLeft, mBottom - getLDR());
                mPath.lineTo(mLeft, mTop + getLTR());
                if (leftOffset >= getLTR() + mArrowDownLeftRadius) {
                    mPath.quadTo(mLeft, mTop, mLeft + getLTR(), mTop);
                } else {
                    mPath.quadTo(mLeft, mTop, leftOffset + mLookWidth / 2F, mTop - mLookLength);
                }
                break;
            case RIGHT:
                if (topOffset >= getRTR() + mArrowDownLeftRadius) {
                    mPath.moveTo(mRight, topOffset - mArrowDownLeftRadius);
                    mPath.rCubicTo(0, mArrowDownLeftRadius,
                            mLookLength, mLookWidth / 2F - mArrowTopLeftRadius + mArrowDownLeftRadius,
                            mLookLength, mLookWidth / 2F + mArrowDownLeftRadius);
                } else {
                    mPath.moveTo(mRight + mLookLength, topOffset + mLookWidth / 2F);
                }

                if (topOffset + mLookWidth < mBottom - getRDR() - mArrowDownRightRadius) {
                    mPath.rCubicTo(0F, mArrowTopRightRadius,
                            -mLookLength, mLookWidth / 2F,
                            -mLookLength, mLookWidth / 2F + mArrowDownRightRadius);
                    mPath.lineTo(mRight, mBottom - getRDR());
                }
                mPath.quadTo(mRight, mBottom,
                        mRight - getRDR(), mBottom);
                mPath.lineTo(mLeft + getLDR(), mBottom);
                mPath.quadTo(mLeft, mBottom, mLeft, mBottom - getLDR());
                mPath.lineTo(mLeft, mTop + getLTR());
                mPath.quadTo(mLeft, mTop, mLeft + getLTR(), mTop);
                mPath.lineTo(mRight - getRTR(), mTop);
                if (topOffset >= getRTR() + mArrowDownLeftRadius) {
                    mPath.quadTo(mRight, mTop, mRight, mTop + getRTR());
                } else {
                    mPath.quadTo(mRight, mTop, mRight + mLookLength, topOffset + mLookWidth / 2F);
                }
                break;
            case BOTTOM:
                if (leftOffset >= getLDR() + mArrowDownRightRadius) {
                    mPath.moveTo(leftOffset - mArrowDownRightRadius, mBottom);
                    mPath.rCubicTo(mArrowDownRightRadius, 0,
                            mLookWidth / 2F - mArrowTopRightRadius + mArrowDownRightRadius, mLookLength,
                            mLookWidth / 2F + mArrowDownRightRadius, mLookLength);
                } else {
                    mPath.moveTo(leftOffset + mLookWidth / 2F, mBottom + mLookLength);
                }

                if (leftOffset + mLookWidth < mRight - getRDR() - mArrowDownLeftRadius) {
                    mPath.rCubicTo(mArrowTopLeftRadius, 0F,
                            mLookWidth / 2F, -mLookLength,
                            mLookWidth / 2F + mArrowDownLeftRadius, -mLookLength);
                    mPath.lineTo(mRight - getRDR(), mBottom);
                }
                mPath.quadTo(mRight, mBottom, mRight, mBottom - getRDR());
                mPath.lineTo(mRight, mTop + getRTR());
                mPath.quadTo(mRight, mTop, mRight - getRTR(), mTop);
                mPath.lineTo(mLeft + getLTR(), mTop);
                mPath.quadTo(mLeft, mTop, mLeft, mTop + getLTR());
                mPath.lineTo(mLeft, mBottom - getLDR());
                if (leftOffset >= getLDR() + mArrowDownRightRadius) {
                    mPath.quadTo(mLeft, mBottom, mLeft + getLDR(), mBottom);
                } else {
                    mPath.quadTo(mLeft, mBottom, leftOffset + mLookWidth / 2F, mBottom + mLookLength);
                }
                break;
        }

        mPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLiquidGlassEnabled) {
            drawLiquidGlass(canvas);
            return;
        }

        int color = resolveConfiguredColor(
                isSelected ? mBubbleSelectedColor : mBubbleNormalColor,
                mBubbleColor
        );
        mPaint.setColor(color);
        mPaint.setShader(null);
        canvas.drawPath(mPath, mPaint);
        if (mBubbleImageBg != null) {
            mPath.computeBounds(mBubbleImageBgDstRectF, true);
            int layer = canvas.saveLayer(mBubbleImageBgDstRectF, null, ALL_SAVE_FLAG);
            canvas.drawPath(mPath, mBubbleImageBgBeforePaint);

            float dstRatio = mBubbleImageBgDstRectF.width() / mBubbleImageBgDstRectF.height();
            float imgRatio = mBubbleImageBg.getWidth() * 1F / mBubbleImageBg.getHeight();
            if (dstRatio > imgRatio) {
                final int top = (int) ((mBubbleImageBg.getHeight() - mBubbleImageBg.getWidth() / dstRatio) / 2);
                final int bottom = top + (int) (mBubbleImageBg.getWidth() / dstRatio);
                mBubbleImageBgSrcRect.set(0, top, mBubbleImageBg.getWidth(), bottom);
            } else {
                final int left = (int) ((mBubbleImageBg.getWidth() - mBubbleImageBg.getHeight() * dstRatio) / 2);
                final int width = left + (int) (mBubbleImageBg.getHeight() * dstRatio);
                mBubbleImageBgSrcRect.set(left, 0, width, mBubbleImageBg.getHeight());
            }
            canvas.drawBitmap(mBubbleImageBg, mBubbleImageBgSrcRect, mBubbleImageBgDstRectF, mBubbleImageBgPaint);
            canvas.restoreToCount(layer);
        }

        if (mBubbleBorderSize != 0) {
            canvas.drawPath(mPath, mBubbleBorderPaint);
        }
    }

    private void drawLiquidGlass(Canvas canvas) {
        mPath.computeBounds(mGlassBounds, true);
        if (mGlassBounds.isEmpty()) return;

        int configured = resolveConfiguredColor(
                isSelected ? mBubbleSelectedColor : mBubbleNormalColor,
                mBubbleColor
        );
        boolean darkSurface = ColorUtils.calculateLuminance(configured) < 0.42d;
        ensureLiquidGlassPaints(configured, darkSurface);

        // A one-pixel offset gives separation from bright wallpapers without the cost of blur masks.
        canvas.save();
        canvas.translate(0f, dp(1f));
        canvas.drawPath(mPath, mGlassShadowPaint);
        canvas.restore();

        canvas.drawPath(mPath, mGlassFillPaint);
        canvas.drawPath(mPath, mGlassHighlightPaint);
        canvas.drawPath(mPath, mGlassShadePaint);

        // Short top reflection: enough to look liquid, subtle enough not to resemble a button.
        if (!mGlassGlintBounds.isEmpty()) {
            canvas.save();
            canvas.clipPath(mPath);
            canvas.drawRoundRect(mGlassGlintBounds, dp(1f), dp(1f), mGlassGlintPaint);
            canvas.restore();
        }

        canvas.drawPath(mPath, mGlassBorderPaint);
    }

    private void ensureLiquidGlassPaints(int configured, boolean darkSurface) {
        boolean unchanged = Float.compare(mGlassShaderLeft, mGlassBounds.left) == 0
                && Float.compare(mGlassShaderTop, mGlassBounds.top) == 0
                && Float.compare(mGlassShaderRight, mGlassBounds.right) == 0
                && Float.compare(mGlassShaderBottom, mGlassBounds.bottom) == 0
                && mGlassShaderColor == configured
                && mGlassShaderSelected == isSelected
                && mGlassShaderSent == mLiquidGlassSent
                && mGlassShaderDark == darkSurface;
        if (unchanged) return;

        mGlassShaderLeft = mGlassBounds.left;
        mGlassShaderTop = mGlassBounds.top;
        mGlassShaderRight = mGlassBounds.right;
        mGlassShaderBottom = mGlassBounds.bottom;
        mGlassShaderColor = configured;
        mGlassShaderSelected = isSelected;
        mGlassShaderSent = mLiquidGlassSent;
        mGlassShaderDark = darkSurface;

        int topColor;
        int bottomColor;
        int borderStart;
        int borderEnd;
        int highlightStart;
        int shadeEnd;

        if (darkSurface) {
            topColor = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(configured, Color.WHITE, mLiquidGlassSent ? 0.16f : 0.10f),
                    isSelected ? 235 : 208
            );
            bottomColor = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(configured, Color.BLACK, 0.16f),
                    isSelected ? 226 : 188
            );
            borderStart = Color.argb(isSelected ? 125 : 92, 255, 255, 255);
            borderEnd = Color.argb(44, 255, 255, 255);
            highlightStart = Color.argb(isSelected ? 62 : 45, 255, 255, 255);
            shadeEnd = Color.argb(34, 0, 0, 0);
        } else {
            topColor = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(configured, Color.WHITE, mLiquidGlassSent ? 0.34f : 0.48f),
                    isSelected ? 236 : 215
            );
            bottomColor = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(configured, mLiquidGlassSent ? 0xFFD1C5EE : 0xFFE2E8EC, 0.24f),
                    isSelected ? 225 : 174
            );
            borderStart = Color.argb(isSelected ? 245 : 214, 255, 255, 255);
            borderEnd = mLiquidGlassSent
                    ? Color.argb(86, 119, 103, 171)
                    : Color.argb(68, 116, 130, 143);
            highlightStart = Color.argb(isSelected ? 150 : 112, 255, 255, 255);
            shadeEnd = mLiquidGlassSent
                    ? Color.argb(28, 91, 69, 146)
                    : Color.argb(22, 65, 77, 88);
        }

        mGlassShadowPaint.setShader(null);
        mGlassShadowPaint.setStyle(Paint.Style.FILL);
        mGlassShadowPaint.setColor(darkSurface ? Color.argb(34, 0, 0, 0) : Color.argb(22, 32, 38, 48));

        mGlassFillPaint.setStyle(Paint.Style.FILL);
        mGlassFillPaint.setShader(new LinearGradient(
                mGlassBounds.left, mGlassBounds.top,
                mGlassBounds.right, mGlassBounds.bottom,
                topColor, bottomColor, Shader.TileMode.CLAMP
        ));

        mGlassHighlightPaint.setStyle(Paint.Style.FILL);
        mGlassHighlightPaint.setShader(new LinearGradient(
                0f, mGlassBounds.top,
                0f, mGlassBounds.top + Math.max(dp(18f), mGlassBounds.height() * 0.62f),
                highlightStart, Color.TRANSPARENT, Shader.TileMode.CLAMP
        ));

        mGlassShadePaint.setStyle(Paint.Style.FILL);
        mGlassShadePaint.setShader(new LinearGradient(
                0f, mGlassBounds.top + mGlassBounds.height() * 0.45f,
                0f, mGlassBounds.bottom,
                Color.TRANSPARENT, shadeEnd, Shader.TileMode.CLAMP
        ));

        if (mGlassBounds.width() > dp(50f) && mGlassBounds.height() > dp(28f)) {
            float left = mGlassBounds.left + dp(12f);
            float top = mGlassBounds.top + dp(2.2f);
            float width = Math.min(mGlassBounds.width() * 0.38f, dp(62f));
            mGlassGlintBounds.set(left, top, left + width, top + dp(1.15f));
            mGlassGlintPaint.setShader(new LinearGradient(
                    mGlassGlintBounds.left, 0f, mGlassGlintBounds.right, 0f,
                    Color.argb(darkSurface ? 48 : 105, 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
            ));
        } else {
            mGlassGlintBounds.setEmpty();
            mGlassGlintPaint.setShader(null);
        }

        mGlassBorderPaint.setStyle(Paint.Style.STROKE);
        mGlassBorderPaint.setStrokeWidth(dp(0.85f));
        mGlassBorderPaint.setShader(new LinearGradient(
                mGlassBounds.left, mGlassBounds.top,
                mGlassBounds.right, mGlassBounds.bottom,
                borderStart, borderEnd, Shader.TileMode.CLAMP
        ));
    }

    private void invalidateLiquidGlassShaders() {
        mGlassShaderColor = Integer.MIN_VALUE;
        mGlassShaderLeft = Float.NaN;
    }

    private int resolveConfiguredColor(int configuredColor, int fallbackColor) {
        if (configuredColor == 0) return fallbackColor;
        try {
            return ContextCompat.getColor(getContext(), configuredColor);
        } catch (Exception ignored) {
            return configuredColor;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            RectF r = new RectF();
            mPath.computeBounds(r, true);
            mRegion.setPath(mPath, new Region((int) r.left, (int) r.top, (int) r.right, (int) r.bottom));
            if (!mRegion.contains((int) event.getX(), (int) event.getY()) && mListener != null) {
                mListener.edge();
            }
            isSelected = true;
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            isSelected = false;
            invalidate();
        }
        return super.onTouchEvent(event);
    }

    public Paint getPaint() {
        return mPaint;
    }

    public Path getPath() {
        return mPath;
    }

    public Look getLook() {
        return mLook;
    }

    public int getLookPosition() {
        return mLookPosition;
    }

    public int getLookWidth() {
        return mLookWidth;
    }

    public int getLookLength() {
        return mLookLength;
    }

    public int getShadowColor() {
        return mShadowColor;
    }

    public float getShadowRadius() {
        return mShadowRadius;
    }

    public int getShadowX() {
        return mShadowX;
    }

    public int getShadowY() {
        return mShadowY;
    }

    public int getBubbleRadius() {
        return mBubbleRadius;
    }

    public int getBubbleColor() {
        return mBubbleColor;
    }

    public void setBubbleSelectedColor(int color) {
        this.mBubbleSelectedColor = color;
        invalidateLiquidGlassShaders();
    }

    public void setBubbleNormalColor(int color) {
        this.mBubbleNormalColor = color;
        invalidateLiquidGlassShaders();
    }

    public void setBubbleColor(int mBubbleColor) {
        this.mBubbleColor = mBubbleColor;
        invalidateLiquidGlassShaders();
    }

    public void setLiquidGlassEnabled(boolean enabled) {
        if (this.mLiquidGlassEnabled != enabled) {
            this.mLiquidGlassEnabled = enabled;
            invalidateLiquidGlassShaders();
        }
    }

    public void setLiquidGlassSent(boolean sent) {
        if (this.mLiquidGlassSent != sent) {
            this.mLiquidGlassSent = sent;
            invalidateLiquidGlassShaders();
        }
    }

    public void setLook(Look mLook) {
        this.mLook = mLook;
        initPadding();
    }

    public void setLookPosition(int mLookPosition) {
        this.mLookPosition = mLookPosition;
    }

    public void setLookWidth(int mLookWidth) {
        this.mLookWidth = AndroidUtilities.dp(mLookWidth);
    }

    public void setLookLength(int mLookLength) {
        this.mLookLength = mLookLength;
        initPadding();
    }

    public void setShadowColor(int mShadowColor) {
        this.mShadowColor = mShadowColor;
    }

    public void setShadowRadius(float mShadowRadius) {
        this.mShadowRadius = mShadowRadius;
    }

    public void setShadowX(int mShadowX) {
        this.mShadowX = mShadowX;
    }

    public void setShadowY(int mShadowY) {
        this.mShadowY = mShadowY;
    }

    public void setBubbleRadius(int mBubbleRadius) {
        this.mBubbleRadius = mBubbleRadius;
    }

    public int getLTR() {
        return mLTR == -1 ? mBubbleRadius : mLTR;
    }

    public void setLTR(int mLTR) {
        this.mLTR = AndroidUtilities.dp(mLTR);
    }

    public int getRTR() {
        return mRTR == -1 ? mBubbleRadius : mRTR;
    }

    public void setRTR(int mRTR) {
        this.mRTR = AndroidUtilities.dp(mRTR);
    }

    public int getRDR() {
        return mRDR == -1 ? mBubbleRadius : mRDR;
    }

    public void setRDR(int mRDR) {
        this.mRDR = AndroidUtilities.dp(mRDR);
    }

    public int getLDR() {
        return mLDR == -1 ? mBubbleRadius : mLDR;
    }

    public void setLDR(int mLDR) {
        this.mLDR = AndroidUtilities.dp(mLDR);
    }

    public int getArrowTopLeftRadius() {
        return mArrowTopLeftRadius;
    }

    public void setArrowTopLeftRadius(int mArrowTopLeftRadius) {
        this.mArrowTopLeftRadius = mArrowTopLeftRadius;
    }

    public int getArrowTopRightRadius() {
        return mArrowTopRightRadius;
    }

    public void setArrowTopRightRadius(int mArrowTopRightRadius) {
        this.mArrowTopRightRadius = mArrowTopRightRadius;
    }

    public int getArrowDownLeftRadius() {
        return mArrowDownLeftRadius;
    }

    public void setArrowDownLeftRadius(int mArrowDownLeftRadius) {
        this.mArrowDownLeftRadius = mArrowDownLeftRadius;
    }

    public int getArrowDownRightRadius() {
        return mArrowDownRightRadius;
    }

    public void setArrowDownRightRadius(int mArrowDownRightRadius) {
        this.mArrowDownRightRadius = mArrowDownRightRadius;
    }

    public void setBubblePadding(int bubblePadding) {
        this.mBubblePadding = bubblePadding;
    }

    /**
     * 设置背景图片
     *
     * @param bitmap 图片
     */
    public void setBubbleImageBg(Bitmap bitmap) {
        mBubbleImageBg = bitmap;
    }

    /**
     * 设置背景图片资源
     *
     * @param res 图片资源
     */
    public void setBubbleImageBgRes(int res) {
        mBubbleImageBg = BitmapFactory.decodeResource(getResources(), res);
    }

    public void setBubbleBorderSize(int bubbleBorderSize) {
        this.mBubbleBorderSize = bubbleBorderSize;
    }

    public void setBubbleBorderColor(int bubbleBorderColor) {
        this.mBubbleBorderColor = bubbleBorderColor;
    }

    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("instanceState", super.onSaveInstanceState());
        bundle.putInt("mLookPosition", this.mLookPosition);
        bundle.putInt("mLookWidth", this.mLookWidth);
        bundle.putInt("mLookLength", this.mLookLength);
        bundle.putInt("mShadowColor", this.mShadowColor);
        bundle.putFloat("mShadowRadius", this.mShadowRadius);
        bundle.putInt("mShadowX", this.mShadowX);
        bundle.putInt("mShadowY", this.mShadowY);
        bundle.putInt("mBubbleRadius", this.mBubbleRadius);

        bundle.putInt("mLTR", this.mLTR);
        bundle.putInt("mRTR", this.mRTR);
        bundle.putInt("mRDR", this.mRDR);
        bundle.putInt("mLDR", this.mLDR);

        bundle.putInt("mBubblePadding", this.mBubblePadding);

        bundle.putInt("mArrowTopLeftRadius", this.mArrowTopLeftRadius);
        bundle.putInt("mArrowTopRightRadius", this.mArrowTopRightRadius);
        bundle.putInt("mArrowDownLeftRadius", this.mArrowDownLeftRadius);
        bundle.putInt("mArrowDownRightRadius", this.mArrowDownRightRadius);

        bundle.putInt("mWidth", this.mWidth);
        bundle.putInt("mHeight", this.mHeight);
        bundle.putInt("mLeft", this.mLeft);
        bundle.putInt("mTop", this.mTop);
        bundle.putInt("mRight", this.mRight);
        bundle.putInt("mBottom", this.mBottom);

        bundle.putInt("mBubbleBgRes", this.mBubbleBgRes);

        bundle.putInt("mBubbleBorderColor", this.mBubbleBorderColor);
        bundle.putInt("mBubbleBorderSize", this.mBubbleBorderSize);
        return bundle;
    }

    //    private int mWidth, mHeight;
//    private int mLeft, mTop, mRight, mBottom;
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            this.mLookPosition = bundle.getInt("mLookPosition");
            this.mLookWidth = bundle.getInt("mLookWidth");
            this.mLookLength = bundle.getInt("mLookLength");
            this.mShadowColor = bundle.getInt("mShadowColor");
            this.mShadowRadius = bundle.getFloat("mShadowRadius");
            this.mShadowX = bundle.getInt("mShadowX");
            this.mShadowY = bundle.getInt("mShadowY");
            this.mBubbleRadius = bundle.getInt("mBubbleRadius");

            this.mLTR = bundle.getInt("mLTR");
            this.mRTR = bundle.getInt("mRTR");
            this.mRDR = bundle.getInt("mRDR");
            this.mLDR = bundle.getInt("mLDR");

            this.mBubblePadding = bundle.getInt("mBubblePadding");

            this.mArrowTopLeftRadius = bundle.getInt("mArrowTopLeftRadius");
            this.mArrowTopRightRadius = bundle.getInt("mArrowTopRightRadius");
            this.mArrowDownLeftRadius = bundle.getInt("mArrowDownLeftRadius");
            this.mArrowDownRightRadius = bundle.getInt("mArrowDownRightRadius");

            this.mWidth = bundle.getInt("mWidth");
            this.mHeight = bundle.getInt("mHeight");
            this.mLeft = bundle.getInt("mLeft");
            this.mTop = bundle.getInt("mTop");
            this.mRight = bundle.getInt("mRight");
            this.mBottom = bundle.getInt("mBottom");
            this.mBubbleBgRes = bundle.getInt("mBubbleBgRes");

            if (this.mBubbleBgRes != -1) {
                mBubbleImageBg = BitmapFactory.decodeResource(getResources(), mBubbleBgRes);
            }

            this.mBubbleBorderSize = bundle.getInt("mBubbleBorderSize");
            this.mBubbleBorderColor = bundle.getInt("mBubbleBorderColor");
            super.onRestoreInstanceState(bundle.getParcelable("instanceState"));
            return;
        }
        super.onRestoreInstanceState(state);
    }

    public void setOnClickEdgeListener(OnClickEdgeListener l) {
        this.mListener = l;
    }

    /**
     * 触摸到气泡的边缘
     */
    public interface OnClickEdgeListener {
        void edge();
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
    }

    public void setAll(WKMsgBgType bgType, WKChatIteMsgFromType msgFrom, int normalColor, int selectedColor) {
        setLiquidGlassEnabled(false);
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
        setBubbleBorderSize(1);
        setShadowRadius(1f);
        setShadowX(1);
        setShadowY(1);
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams instanceof LayoutParams linearLayout) {
            if (bgType == WKMsgBgType.center || bgType == WKMsgBgType.top) {
                if (msgFrom == WKChatIteMsgFromType.RECEIVED)
                    linearLayout.leftMargin = AndroidUtilities.dp(10);
                else linearLayout.rightMargin = AndroidUtilities.dp(10);
            } else {
                linearLayout.rightMargin = 0;
                linearLayout.leftMargin = 0;
            }
        }
        int lookWidth = 10;
        int normalRadius = 20;
        int smallRadius = 10;
        if (msgFrom == WKChatIteMsgFromType.SEND) {
            setBubbleBorderColor(ContextCompat.getColor(getContext(), R.color.colorB6B5B5));


            setBubbleNormalColor(normalColor);
            setBubbleSelectedColor(selectedColor);
            if (bgType == WKMsgBgType.bottom) {
                setLook(BubbleLayout.Look.RIGHT);
                setArrowDownLeftRadius(AndroidUtilities.dp(lookWidth * 2));
                setLookWidth(0);
                setArrowTopLeftRadius(0);
                setRTR(smallRadius);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRDR(normalRadius);
                setLookLength(AndroidUtilities.dp(lookWidth));
            } else if (bgType == WKMsgBgType.center) {
                setLook(Look.TOP);
                setLookWidth(normalRadius * 2);
                setLookLength(0);
                setArrowTopLeftRadius(0);
                setArrowTopRightRadius(0);
                setArrowDownLeftRadius(0);
                setArrowDownRightRadius(0);
                setRTR(smallRadius);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRDR(smallRadius);
            } else if (bgType == WKMsgBgType.top) {
                setLook(Look.TOP);
                setLookWidth(normalRadius * 2);
                setLookLength(0);
                setRTR(normalRadius);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRDR(smallRadius);
                setLook(Look.BOTTOM);
                setLookPosition(getWidth() / 2);
                setArrowTopLeftRadius(0);
            } else if (bgType == WKMsgBgType.single) {
                setRTR(normalRadius);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRDR(normalRadius);
                setLook(BubbleLayout.Look.RIGHT);
                setArrowDownLeftRadius(AndroidUtilities.dp(lookWidth * 2));
                setLookWidth(0);
                setArrowTopLeftRadius(0);
                setLookLength(AndroidUtilities.dp(lookWidth));
            }
        } else {
            setBubbleBorderColor(ContextCompat.getColor(getContext(), R.color.chat_border_color));
            setBubbleNormalColor(normalColor);
            setBubbleSelectedColor(selectedColor);
            if (bgType == WKMsgBgType.bottom) {
                setLook(Look.LEFT);
                setArrowDownRightRadius(AndroidUtilities.dp(lookWidth * 2));
                setLookWidth(0);
                setArrowTopRightRadius(0);
                setLookLength(AndroidUtilities.dp(lookWidth));
                setRDR(normalRadius);
                setRTR(normalRadius);
                setLDR(normalRadius);
                setLTR(smallRadius);
            } else if (bgType == WKMsgBgType.center) {
                setLook(Look.TOP);
                setLookWidth(smallRadius * 2);
                setLookLength(0);
                setLTR(smallRadius);
                setLDR(smallRadius);
                setRTR(normalRadius);
                setRDR(normalRadius);
                setArrowTopRightRadius(0);
            } else if (bgType == WKMsgBgType.top) {
                setLook(Look.TOP);
                setLookWidth(normalRadius * 2);
                setLookLength(0);
                setLTR(normalRadius);
                setLDR(smallRadius);
                setRTR(normalRadius);
                setRDR(normalRadius);
                setArrowTopRightRadius(0);
            } else if (bgType == WKMsgBgType.single) {
                setLook(Look.LEFT);
                setArrowDownRightRadius(AndroidUtilities.dp(lookWidth * 2));
                setLookWidth(0);
                setArrowTopRightRadius(0);
                setLookLength(AndroidUtilities.dp(lookWidth));
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRTR(normalRadius);
                setRDR(normalRadius);
            }
        }
        invalidate();
    }

    public void setAll(WKMsgBgType bgType, WKChatIteMsgFromType msgFrom, int msgType) {
        boolean liquidGlass = msgType == WKContentType.WK_TEXT
                || msgType == WKContentType.WK_VOICE
                || msgType == WKContentType.typing;
        setLiquidGlassEnabled(liquidGlass);
        setLiquidGlassSent(msgFrom == WKChatIteMsgFromType.SEND);
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
        setBubbleBorderSize(liquidGlass ? 0 : 1);
        setShadowRadius(liquidGlass ? dp(1f) : 1f);
        setShadowX(0);
        setShadowY(0);
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams instanceof LayoutParams linearLayout) {
            if (bgType == WKMsgBgType.center || bgType == WKMsgBgType.top) {
                int groupInset = liquidGlass ? AndroidUtilities.dp(7) : AndroidUtilities.dp(10);
                if (msgFrom == WKChatIteMsgFromType.RECEIVED) {
                    linearLayout.leftMargin = groupInset;
                    linearLayout.rightMargin = 0;
                } else {
                    linearLayout.rightMargin = groupInset;
                    linearLayout.leftMargin = 0;
                }
            } else {
                linearLayout.rightMargin = 0;
                linearLayout.leftMargin = 0;
            }
        }

        int lookWidth = liquidGlass ? 6 : 10;
        int normalRadius = liquidGlass ? 18 : 20;
        int smallRadius = liquidGlass ? 8 : 5;
        int tailLength = AndroidUtilities.dp(liquidGlass ? 5 : lookWidth);
        if (msgFrom == WKChatIteMsgFromType.SEND) {
            setBubbleBorderColor(ContextCompat.getColor(getContext(), R.color.transparent));
            if (liquidGlass) {
                setBubbleNormalColor(R.color.chat_liquid_send_bg_normal);
                setBubbleSelectedColor(R.color.chat_liquid_send_bg_selected);
            } else if (msgType == WKContentType.WK_FILE
                    || msgType == WKContentType.WK_MULTIPLE_FORWARD
                    || msgType == WKContentType.WK_LOCATION
                    || msgType == WKContentType.WK_CARD) {
                setBubbleNormalColor(R.color.chat_received_bg_normal);
                setBubbleSelectedColor(R.color.chat_received_bg_selected);
            } else {
                setBubbleNormalColor(R.color.chat_send_bg_normal);
                setBubbleSelectedColor(R.color.chat_send_bg_select);
            }
            if (bgType == WKMsgBgType.bottom) {
                setLook(BubbleLayout.Look.RIGHT);
                setArrowDownLeftRadius(AndroidUtilities.dp(lookWidth * 2));
                setLookWidth(0);
                setArrowTopLeftRadius(0);
                setRTR(smallRadius);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRDR(normalRadius);
                setLookLength(tailLength);
            } else if (bgType == WKMsgBgType.center) {
                setLook(Look.TOP);
                setLookWidth(normalRadius * 2);
                setLookLength(0);
                setArrowTopLeftRadius(0);
                setArrowTopRightRadius(0);
                setArrowDownLeftRadius(0);
                setArrowDownRightRadius(0);
                setRTR(smallRadius);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRDR(smallRadius);
            } else if (bgType == WKMsgBgType.top) {
                setLook(Look.TOP);
                setLookWidth(normalRadius * 2);
                setLookLength(0);
                setRTR(normalRadius);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRDR(smallRadius);
                setLook(Look.BOTTOM);
                setLookPosition(getWidth() / 2);
                setArrowTopLeftRadius(0);
            } else if (bgType == WKMsgBgType.single) {
                setRTR(normalRadius);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRDR(normalRadius);
                setLook(BubbleLayout.Look.RIGHT);
                setArrowDownLeftRadius(AndroidUtilities.dp(lookWidth * 2));
                setLookWidth(0);
                setArrowTopLeftRadius(0);
                setLookLength(tailLength);
            }
        } else {
            setBubbleBorderColor(ContextCompat.getColor(getContext(), R.color.transparent));
            if (liquidGlass) {
                setBubbleNormalColor(R.color.chat_liquid_received_bg_normal);
                setBubbleSelectedColor(R.color.chat_liquid_received_bg_selected);
            } else {
                setBubbleNormalColor(R.color.chat_received_bg_normal);
                setBubbleSelectedColor(R.color.chat_received_bg_selected);
            }
            if (bgType == WKMsgBgType.bottom) {
                setLook(Look.LEFT);
                setArrowDownRightRadius(AndroidUtilities.dp(lookWidth * 2));
                setLookWidth(0);
                setArrowTopRightRadius(0);
                setLookLength(tailLength);
                setRDR(normalRadius);
                setRTR(normalRadius);
                setLDR(normalRadius);
                setLTR(smallRadius);
            } else if (bgType == WKMsgBgType.center) {
                setLook(Look.TOP);
                setLookWidth(smallRadius * 2);
                setLookLength(0);
                setLTR(smallRadius);
                setLDR(smallRadius);
                setRTR(normalRadius);
                setRDR(normalRadius);
                setArrowTopRightRadius(0);
            } else if (bgType == WKMsgBgType.top) {
                setLook(Look.BOTTOM);
                setLookWidth(smallRadius * 2);
                setLookPosition(getWidth() / 2);
                setLookLength(0);
                setLTR(normalRadius);
                setLDR(smallRadius);
                setRTR(normalRadius);
                setRDR(normalRadius);
                setArrowTopRightRadius(0);
            } else if (bgType == WKMsgBgType.single) {
                setLook(Look.LEFT);
                setArrowDownRightRadius(AndroidUtilities.dp(lookWidth * 2));
                setLookWidth(0);
                setArrowTopRightRadius(0);
                setLookLength(tailLength);
                setLTR(normalRadius);
                setLDR(normalRadius);
                setRTR(normalRadius);
                setRDR(normalRadius);
            }
        }
        setShadowColor(Color.TRANSPARENT);
        setShadowX(0);
        setShadowY(0);
        setBubbleBorderColor(Color.TRANSPARENT);
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }
}
