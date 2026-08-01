package com.chat.learning;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

/** Shared visual primitives for the learning flow. */
final class LearningUiKit {
    static final int BG = 0xFFF7F7F7;
    static final int SURFACE = Color.WHITE;
    static final int TEXT = 0xFF3C3C3C;
    static final int SUBTEXT = 0xFF777777;
    static final int MUTED = 0xFFAFAFAF;
    static final int BORDER = 0xFFE5E5E5;
    static final int BORDER_DARK = 0xFFD2D2D2;
    static final int GREEN = 0xFF58CC02;
    static final int GREEN_DARK = 0xFF46A302;
    static final int GREEN_SOFT = 0xFFD7FFB8;
    static final int BLUE = 0xFF1CB0F6;
    static final int BLUE_DARK = 0xFF1899D6;
    static final int BLUE_SOFT = 0xFFDDF4FF;
    static final int RED = 0xFFFF4B4B;
    static final int RED_DARK = 0xFFD32626;
    static final int RED_SOFT = 0xFFFFDFE0;
    static final int YELLOW = 0xFFFFC800;
    static final int YELLOW_DARK = 0xFFE5A500;
    static final int PURPLE = 0xFFCE82FF;
    static final int PURPLE_DARK = 0xFFA85DD1;

    private LearningUiKit() { }

    static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static void applySystemBars(Activity activity, int color) {
        Window window = activity.getWindow();
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    static TextView text(Context context, CharSequence value, float size, int color,
                         boolean bold) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    static GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    static Drawable raised(int topColor, int bottomColor, float radius,
                           int strokeColor, int strokeWidth, int depth) {
        GradientDrawable bottom = rounded(bottomColor, radius, 0, 0);
        GradientDrawable top = rounded(topColor, radius, strokeColor, strokeWidth);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{bottom, top});
        int inset = Math.max(0, depth);
        layers.setLayerInset(0, 0, inset, 0, 0);
        layers.setLayerInset(1, 0, 0, 0, inset);
        return layers;
    }

    static StateListDrawable raisedSelector(int topColor, int bottomColor, float radius,
                                            int strokeColor, int strokeWidth, int depth) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},
                raised(0xFFF2F2F2, 0xFFDADADA, radius, BORDER, strokeWidth, depth));
        states.addState(new int[]{android.R.attr.state_pressed},
                raised(darken(topColor, 0.96f), bottomColor, radius,
                        strokeColor, strokeWidth, Math.max(1, depth / 2)));
        states.addState(new int[]{},
                raised(topColor, bottomColor, radius, strokeColor, strokeWidth, depth));
        return states;
    }

    static int blend(int first, int second, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = (int) (Color.red(first) * (1f - t) + Color.red(second) * t);
        int g = (int) (Color.green(first) * (1f - t) + Color.green(second) * t);
        int b = (int) (Color.blue(first) * (1f - t) + Color.blue(second) * t);
        return Color.rgb(r, g, b);
    }

    static int darken(int color, float factor) {
        float value = Math.max(0f, Math.min(1f, factor));
        return Color.rgb((int) (Color.red(color) * value),
                (int) (Color.green(color) * value),
                (int) (Color.blue(color) * value));
    }

    static final class ProgressView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int trackColor = BORDER;
        private int progressColor = GREEN;
        private int max = 100;
        private int progress;

        ProgressView(Context context) {
            super(context);
        }

        void setColors(int track, int fill) {
            trackColor = track;
            progressColor = fill;
            invalidate();
        }

        void setProgress(int value, int maximum) {
            max = Math.max(1, maximum);
            progress = Math.max(0, Math.min(max, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = getHeight() / 2f;
            rect.set(0, 0, getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(trackColor);
            canvas.drawRoundRect(rect, radius, radius, paint);
            float width = getWidth() * (progress / (float) max);
            if (width <= 0f) return;
            rect.set(0, 0, Math.max(getHeight(), width), getHeight());
            paint.setColor(progressColor);
            canvas.drawRoundRect(rect, radius, radius, paint);
            float highlightHeight = Math.max(1f, getHeight() * 0.28f);
            rect.set(getHeight() * 0.35f, getHeight() * 0.18f,
                    Math.max(getHeight() * 0.35f, width - getHeight() * 0.35f),
                    getHeight() * 0.18f + highlightHeight);
            paint.setColor(0x36FFFFFF);
            canvas.drawRoundRect(rect, highlightHeight / 2f, highlightHeight / 2f, paint);
        }
    }

    static final class ScrimView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int accent;

        ScrimView(Context context, int accent) {
            super(context);
            this.accent = accent;
        }

        void setAccent(int value) {
            accent = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setShader(new LinearGradient(0, 0, getWidth(), 0,
                    new int[]{0xE8111722, 0xA8111722, 0x35111722},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            paint.setColor((accent & 0x00FFFFFF) | 0x33000000);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(new LinearGradient(0, getHeight() * 0.45f, 0, getHeight(),
                    0x00111722, 0xAA111722, Shader.TileMode.CLAMP));
            canvas.drawRect(0, getHeight() * 0.35f, getWidth(), getHeight(), paint);
            paint.setShader(null);
        }
    }


    /** Lightweight category illustration used when a course has no dedicated cover asset. */
    static final class CategoryArtworkView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private int accent;
        private final int variant;
        private final String symbol;

        CategoryArtworkView(Context context, int accent, int variant, String symbol) {
            super(context);
            this.accent = accent == 0 ? BLUE : accent;
            this.variant = Math.floorMod(variant, 4);
            this.symbol = symbol == null || symbol.trim().isEmpty() ? "学" : symbol.trim();
        }

        void setAccent(int value) {
            accent = value == 0 ? BLUE : value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            int bright = blend(accent, Color.WHITE, 0.14f);
            int dark = darken(accent, 0.66f);
            paint.setShader(new LinearGradient(0, 0, width, height,
                    new int[]{bright, accent, dark}, new float[]{0f, 0.58f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(null);

            // Large soft shapes give every category a distinct illustration without bitmap bloat.
            paint.setColor(0x18FFFFFF);
            canvas.drawCircle(width * (variant % 2 == 0 ? 0.82f : 0.70f),
                    height * 0.24f, height * 0.72f, paint);
            paint.setColor(0x12FFFFFF);
            canvas.drawCircle(width * 0.95f, height * 0.90f, height * 0.52f, paint);

            canvas.save();
            float rotation = variant % 2 == 0 ? -8f : 8f;
            canvas.rotate(rotation, width * 0.79f, height * 0.51f);
            rect.set(width * 0.61f, height * 0.18f, width * 0.96f, height * 0.86f);
            paint.setColor(0x2CFFFFFF);
            canvas.drawRoundRect(rect, dp(getContext(), 27), dp(getContext(), 27), paint);
            rect.inset(dp(getContext(), 8), dp(getContext(), 8));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2));
            paint.setColor(0x28FFFFFF);
            canvas.drawRoundRect(rect, dp(getContext(), 22), dp(getContext(), 22), paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restore();

            // Decorative speech/lesson chips, inspired by the clean card rhythm in Notion.
            float chipLeft = width * 0.52f;
            float chipTop = height * (variant < 2 ? 0.62f : 0.22f);
            rect.set(chipLeft, chipTop, chipLeft + width * 0.18f, chipTop + height * 0.13f);
            paint.setColor(0x30FFFFFF);
            canvas.drawRoundRect(rect, dp(getContext(), 13), dp(getContext(), 13), paint);
            rect.offset(width * 0.08f, height * 0.16f);
            paint.setColor(0x20FFFFFF);
            canvas.drawRoundRect(rect, dp(getContext(), 13), dp(getContext(), 13), paint);

            // Subtle curved accent at the bottom-right.
            path.reset();
            path.moveTo(width * 0.47f, height);
            path.cubicTo(width * 0.63f, height * 0.72f,
                    width * 0.81f, height * 1.02f, width, height * 0.72f);
            path.lineTo(width, height);
            path.close();
            paint.setColor(0x18FFFFFF);
            canvas.drawPath(path, paint);

            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(getContext(), symbol.length() > 2 ? 34 : 54));
            textPaint.setShadowLayer(dp(getContext(), 7), 0, dp(getContext(), 3), 0x33000000);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = height * 0.49f - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(symbol, width * 0.79f, baseline, textPaint);
            textPaint.clearShadowLayer();
        }
    }

    /** Original lightweight human companion used on course and unit maps. */
    static final class CharacterView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private int accent = BLUE;
        private int variant;
        private String pose = "wave";

        CharacterView(Context context) {
            super(context);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
        }

        CharacterView(Context context, int accent, int variant, String pose) {
            this(context);
            setStyle(accent, variant, pose);
        }

        void setStyle(int accent, int variant, String pose) {
            this.accent = accent == 0 ? BLUE : accent;
            this.variant = Math.floorMod(variant, 6);
            this.pose = pose == null ? "wave" : pose;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            float scale = Math.min(w / 120f, h / 150f);
            float ox = (w - 120f * scale) / 2f;
            float oy = h - 150f * scale;
            canvas.save();
            canvas.translate(ox, oy);
            canvas.scale(scale, scale);

            int[] skins = {0xFFF7C9A8, 0xFFEDB98F, 0xFFD99A6C, 0xFFF2C39F, 0xFFC9825B, 0xFFE7AD84};
            int[] hairs = {0xFF332720, 0xFF241B18, 0xFF4B2E20, 0xFF1F252C, 0xFF5A321F, 0xFF2B1D17};
            int skin = skins[variant];
            int hair = hairs[variant];
            int shirt = blend(accent, Color.WHITE, variant % 2 == 0 ? 0.08f : 0.22f);
            int shirtDark = darken(shirt, 0.78f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x22000000);
            rect.set(23, 139, 99, 149);
            canvas.drawOval(rect, paint);

            paint.setColor(shirtDark);
            rect.set(32, 83, 90, 143);
            canvas.drawRoundRect(rect, 24, 24, paint);
            paint.setColor(shirt);
            rect.set(32, 79, 90, 137);
            canvas.drawRoundRect(rect, 24, 24, paint);

            // Neck.
            paint.setColor(skin);
            rect.set(51, 67, 69, 88);
            canvas.drawRoundRect(rect, 8, 8, paint);

            // Head.
            paint.setColor(skin);
            canvas.drawCircle(60, 48, 30, paint);
            paint.setColor(0x22A66542);
            canvas.drawCircle(42, 50, 4, paint);
            canvas.drawCircle(78, 50, 4, paint);

            // Hair silhouette, intentionally original and varied.
            paint.setColor(hair);
            path.reset();
            if (variant % 3 == 0) {
                path.moveTo(31, 47); path.cubicTo(29, 16, 46, 10, 62, 14);
                path.cubicTo(84, 8, 94, 31, 88, 52);
                path.cubicTo(77, 32, 63, 28, 48, 33);
                path.cubicTo(43, 39, 38, 44, 31, 47); path.close();
            } else if (variant % 3 == 1) {
                path.moveTo(31, 48); path.cubicTo(30, 18, 45, 11, 62, 14);
                path.cubicTo(79, 13, 91, 27, 88, 49);
                path.lineTo(80, 37); path.cubicTo(61, 41, 51, 25, 35, 42); path.close();
            } else {
                path.moveTo(30, 51); path.cubicTo(27, 22, 42, 11, 62, 13);
                path.cubicTo(85, 12, 95, 34, 87, 55);
                path.cubicTo(77, 41, 67, 29, 38, 40); path.close();
                canvas.drawCircle(88, 24, 9, paint);
            }
            canvas.drawPath(path, paint);

            // Face.
            paint.setColor(0xFF3C2E2A);
            canvas.drawCircle(49, 51, 2.5f, paint);
            canvas.drawCircle(71, 51, 2.5f, paint);
            line.setColor(0xFF9D5B50);
            line.setStrokeWidth(2.2f);
            rect.set(53, 54, 67, 65);
            canvas.drawArc(rect, 15, 150, false, line);
            paint.setColor(0x44FF6B78);
            canvas.drawCircle(41, 59, 4, paint);
            canvas.drawCircle(79, 59, 4, paint);

            // Legs and shoes.
            paint.setColor(0xFF40556F);
            rect.set(42, 127, 56, 144); canvas.drawRoundRect(rect, 6, 6, paint);
            rect.set(65, 127, 79, 144); canvas.drawRoundRect(rect, 6, 6, paint);
            paint.setColor(0xFF29323E);
            rect.set(37, 140, 57, 147); canvas.drawRoundRect(rect, 4, 4, paint);
            rect.set(64, 140, 84, 147); canvas.drawRoundRect(rect, 4, 4, paint);

            if ("book".equals(pose)) {
                drawBookPose(canvas, skin);
            } else if ("trophy".equals(pose)) {
                drawTrophyPose(canvas, skin);
            } else if ("point".equals(pose)) {
                drawPointPose(canvas, skin);
            } else {
                drawWavePose(canvas, skin);
            }
            canvas.restore();
        }

        private void drawWavePose(Canvas canvas, int skin) {
            line.setStrokeWidth(11);
            line.setColor(skin);
            canvas.drawLine(37, 91, 20, 72, line);
            canvas.drawLine(84, 92, 96, 65, line);
            line.setStrokeWidth(8);
            canvas.drawLine(96, 65, 99, 45, line);
            paint.setColor(skin);
            canvas.drawCircle(100, 42, 6, paint);
            line.setStrokeWidth(2);
            line.setColor(0x559D5B50);
            canvas.drawLine(96, 37, 101, 29, line);
            canvas.drawLine(101, 38, 108, 32, line);
        }

        private void drawPointPose(Canvas canvas, int skin) {
            line.setStrokeWidth(11);
            line.setColor(skin);
            canvas.drawLine(36, 91, 21, 112, line);
            canvas.drawLine(84, 91, 103, 78, line);
            line.setStrokeWidth(7);
            canvas.drawLine(103, 78, 112, 69, line);
            paint.setColor(skin);
            canvas.drawCircle(113, 68, 5, paint);
        }

        private void drawBookPose(Canvas canvas, int skin) {
            line.setStrokeWidth(10);
            line.setColor(skin);
            canvas.drawLine(36, 91, 46, 108, line);
            canvas.drawLine(84, 91, 74, 108, line);
            paint.setColor(0xFF3B68B0);
            rect.set(43, 98, 61, 124);
            canvas.drawRoundRect(rect, 3, 3, paint);
            paint.setColor(0xFF4E7FD0);
            rect.set(61, 98, 79, 124);
            canvas.drawRoundRect(rect, 3, 3, paint);
            paint.setColor(0x88FFFFFF);
            rect.set(59, 99, 63, 124);
            canvas.drawRect(rect, paint);
        }

        private void drawTrophyPose(Canvas canvas, int skin) {
            line.setStrokeWidth(10);
            line.setColor(skin);
            canvas.drawLine(36, 91, 49, 108, line);
            canvas.drawLine(84, 91, 71, 108, line);
            paint.setColor(YELLOW);
            rect.set(49, 91, 72, 112);
            canvas.drawRoundRect(rect, 8, 8, paint);
            paint.setColor(YELLOW_DARK);
            rect.set(57, 110, 64, 124); canvas.drawRoundRect(rect, 3, 3, paint);
            rect.set(50, 122, 71, 128); canvas.drawRoundRect(rect, 3, 3, paint);
            line.setColor(YELLOW_DARK);
            line.setStrokeWidth(4);
            rect.set(42, 94, 53, 108); canvas.drawArc(rect, 80, 190, false, line);
            rect.set(68, 94, 79, 108); canvas.drawArc(rect, -90, 190, false, line);
        }
    }

    static final class TriangleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int color = SURFACE;

        TriangleView(Context context) {
            super(context);
        }

        void setColor(int value) {
            color = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            path.reset();
            path.moveTo(0, 0);
            path.lineTo(getWidth(), 0);
            path.lineTo(getWidth() / 2f, getHeight());
            path.close();
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
        }
    }
}
