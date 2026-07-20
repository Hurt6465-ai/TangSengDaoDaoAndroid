package com.chat.forum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Lightweight waveform used only by forum voice comments. */
final class ForumWaveformView extends View {
    interface OnSeekListener {
        void onSeek(float fraction);
    }

    private final Paint futurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> samples = new ArrayList<>();
    private final float barWidth;
    private final float gap;
    private float progress = -1f;
    private boolean seekEnabled;
    private OnSeekListener seekListener;

    ForumWaveformView(Context context) {
        this(context, null);
    }

    ForumWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        barWidth = 2.8f * density;
        gap = 2.8f * density;
        futurePaint.setStyle(Paint.Style.STROKE);
        futurePaint.setStrokeWidth(barWidth);
        futurePaint.setStrokeCap(Paint.Cap.ROUND);
        pastPaint.setStyle(Paint.Style.STROKE);
        pastPaint.setStrokeWidth(barWidth);
        pastPaint.setStrokeCap(Paint.Cap.ROUND);
        thumbPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    void setColors(@ColorInt int future, @ColorInt int past) {
        futurePaint.setColor(future);
        pastPaint.setColor(past);
        thumbPaint.setColor(past);
        invalidate();
    }

    void clear() {
        samples.clear();
        progress = -1f;
        invalidate();
    }

    void appendAmplitude(int amplitude) {
        float level;
        if (amplitude <= 1) {
            level = 0.08f;
        } else {
            double db = 20d * Math.log10(amplitude);
            level = (float) Math.max(0.08d, Math.min(1d, db / 90d));
        }
        samples.add(level);
        if (samples.size() > 2048) {
            // Preserve the overall shape without unbounded memory growth.
            for (int i = 0; i + 1 < samples.size(); i += 2) {
                samples.set(i / 2, (samples.get(i) + samples.get(i + 1)) * 0.5f);
            }
            int keep = samples.size() / 2;
            while (samples.size() > keep) samples.remove(samples.size() - 1);
        }
        invalidate();
    }

    void setSamples(byte[] levels) {
        samples.clear();
        if (levels != null) {
            for (byte value : levels) {
                int db = value & 0xFF;
                samples.add(Math.max(0.08f, Math.min(1f, db / 90f)));
            }
        }
        invalidate();
    }

    void setProgress(float value) {
        progress = value < 0f ? -1f : Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    void setSeekEnabled(boolean enabled) {
        seekEnabled = enabled;
        setClickable(enabled);
    }

    void setOnSeekListener(@Nullable OnSeekListener listener) {
        seekListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        int barCount = Math.max(1, (int) ((width - gap) / (barWidth + gap)));
        float centerY = height * 0.5f;
        int sourceCount = samples.size();
        for (int i = 0; i < barCount; i++) {
            float level;
            if (sourceCount > 0) {
                int start = i * sourceCount / barCount;
                int end = Math.max(start + 1, (i + 1) * sourceCount / barCount);
                end = Math.min(end, sourceCount);
                float max = 0f;
                float sum = 0f;
                for (int j = start; j < end; j++) {
                    float sample = samples.get(j);
                    max = Math.max(max, sample);
                    sum += sample;
                }
                float average = sum / Math.max(1, end - start);
                level = average * 0.72f + max * 0.28f;
            } else {
                // A quiet preview still looks like a natural waveform rather than equal sticks.
                float[] idle = {0.24f, 0.46f, 0.31f, 0.68f, 0.38f, 0.57f, 0.29f, 0.73f};
                level = idle[i % idle.length];
            }
            float variation = 0.82f + ((i * 37) % 9) * 0.025f;
            level = Math.max(0.16f, Math.min(1f, level * variation));
            float half = Math.max(barWidth * 1.2f, level * height * 0.40f);
            float x = gap + i * (barWidth + gap) + barWidth * 0.5f;
            Paint paint = progress >= 0f && i <= progress * (barCount - 1) ? pastPaint : futurePaint;
            canvas.drawLine(x, centerY - half, x, centerY + half, paint);
        }

        if (progress >= 0f) {
            float x = gap + progress * Math.max(0f, width - gap * 2f);
            canvas.drawCircle(x, centerY, barWidth * 1.25f, thumbPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!seekEnabled || seekListener == null || getWidth() <= 0) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE
                || event.getAction() == MotionEvent.ACTION_UP) {
            float fraction = Math.max(0f, Math.min(1f, event.getX() / getWidth()));
            setProgress(fraction);
            seekListener.onSeek(fraction);
            return true;
        }
        return super.onTouchEvent(event);
    }
}
