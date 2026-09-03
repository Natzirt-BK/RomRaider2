/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/** Glanceable hybrid number/needle gauge for the Android logger preview. */
final class MobileGaugeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final android.graphics.Typeface labelTypeface =
            android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD);
    private String name = "Waiting for data";
    private String displayValue = "—";
    private String units = "";
    private double value;
    private double measuredMinimum;
    private double measuredMaximum;
    private MobileGaugeScale scale = new MobileGaugeScale(0, 1);
    private MobileGaugeTheme theme = MobileGaugeTheme.RR2_CLASSIC;

    MobileGaugeView(Context context) {
        super(context);
        setMinimumHeight(dp(205));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setTheme(MobileGaugeTheme next) {
        theme = next == null ? MobileGaugeTheme.RR2_CLASSIC : next;
        invalidate();
    }

    void setValue(String id, String nextName, String nextDisplay, String nextUnits,
            double nextValue, double minimum, double maximum) {
        name = nextName;
        displayValue = nextDisplay;
        units = nextUnits == null ? "" : nextUnits;
        value = nextValue;
        measuredMinimum = minimum;
        measuredMaximum = maximum;
        scale = MobileGaugeScale.forChannel(id, name, units, minimum, maximum);
        setContentDescription(name + ", " + displayValue + " " + units
                + ", measured minimum " + compact(minimum)
                + " and maximum " + compact(maximum));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(205);
        int height = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        float corner = 10 * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF182129);
        canvas.drawRoundRect(1, 1, width - 1, height - 1, corner, corner, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(0xFF344350);
        canvas.drawRoundRect(1, 1, width - 1, height - 1, corner, corner, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(labelTypeface);
        paint.setTextSize(12 * density);
        paint.setColor(theme.primary);
        canvas.drawText(ellipsize(name, 23), 12 * density, 23 * density, paint);

        float centerX = width / 2f;
        float centerY = 112 * density;
        float radius = Math.min(width * .34f, 70 * density);
        paint.setColor(theme.face);
        canvas.drawCircle(centerX, centerY, radius * 1.04f, paint);
        arc.set(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);
        float start = 145f;
        float sweep = 250f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(9 * density);
        paint.setColor(withAlpha(theme.secondary, .36f));
        canvas.drawArc(arc, start, sweep, false, paint);

        for (int index = 0; index < 26; index++) {
            double angle = Math.toRadians(start + sweep * index / 25f);
            boolean major = index % 5 == 0;
            float inner = radius * (major ? .75f : .82f);
            float outer = radius * .91f;
            paint.setStrokeWidth((major ? 2.2f : 1.1f) * density);
            paint.setColor(withAlpha(theme.ink, major ? .92f : .50f));
            canvas.drawLine(centerX + (float) Math.cos(angle) * inner,
                    centerY + (float) Math.sin(angle) * inner,
                    centerX + (float) Math.cos(angle) * outer,
                    centerY + (float) Math.sin(angle) * outer, paint);
        }

        float progress = scale.progress(value);
        if (theme.segmented) {
            paint.setStyle(Paint.Style.FILL);
            for (int index = 0; index <= 30; index++) {
                float fraction = index / 30f;
                double angle = Math.toRadians(start + sweep * fraction);
                paint.setColor(fraction <= progress ? theme.primary
                        : withAlpha(theme.secondary, .35f));
                canvas.drawCircle(centerX + (float) Math.cos(angle) * radius,
                        centerY + (float) Math.sin(angle) * radius,
                        (index % 5 == 0 ? 3.2f : 2.4f) * density, paint);
            }
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (theme.glow) {
                paint.setShadowLayer(10 * density, 0, 0,
                        withAlpha(theme.primary, .55f));
            }
            paint.setStrokeWidth(7 * density);
            paint.setColor(theme.primary);
            canvas.drawArc(arc, start, sweep * progress, false, paint);
            paint.clearShadowLayer();
        }

        if (!theme.segmented) {
            double needleAngle = Math.toRadians(start + sweep * progress);
            paint.setStrokeWidth(3.5f * density);
            paint.setColor(theme.primary);
            canvas.drawLine(centerX, centerY,
                    centerX + (float) Math.cos(needleAngle) * radius * .72f,
                    centerY + (float) Math.sin(needleAngle) * radius * .72f,
                    paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.primary);
        canvas.drawCircle(centerX, centerY, 4 * density, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(25 * density);
        paint.setColor(theme.ink);
        canvas.drawText(displayValue, centerX, 104 * density, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(10 * density);
        paint.setColor(withAlpha(theme.ink, .62f));
        canvas.drawText(units.isEmpty() ? "CURRENT" : units,
                centerX, 120 * density, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(9 * density);
        paint.setColor(0xFF91A0AE);
        canvas.drawText("MIN  " + compact(measuredMinimum), 12 * density,
                height - 27 * density, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("MAX  " + compact(measuredMaximum), width - 12 * density,
                height - 27 * density, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(8 * density);
        paint.setColor(0xFF687886);
        canvas.drawText("FIXED SCALE  " + compact(scale.minimum) + "–"
                + compact(scale.maximum), centerX, height - 11 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private String ellipsize(String value, int limit) {
        return value.length() <= limit ? value
                : value.substring(0, limit - 1) + "…";
    }

    private static String compact(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 100) return String.format(Locale.ROOT, "%.0f", value);
        if (absolute >= 10) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int withAlpha(int color, float amount) {
        return Color.argb(Math.round(255 * Math.max(0f, Math.min(1f, amount))),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
