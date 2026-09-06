/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.portable.gauge.GaugeFaceRenderer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/** Native, scalable adapter for the shared instrument artwork. */
final class FxInstrumentView extends Region {
    private final Canvas canvas = new Canvas();
    private final GaugeFaceRenderer.Style style;
    private final GaugeFaceRenderer.Reading reading;
    private final com.romraider.portable.gauge.GaugeMotion motion;
    private final javafx.animation.AnimationTimer animation = new javafx.animation.AnimationTimer() {
        @Override public void handle(long now) {
            requestLayout();
            if (!motion.isAnimating(now)) stop();
        }
    };
    FxInstrumentView(GaugeFaceRenderer.Style style, GaugeFaceRenderer.Reading reading) {
        this(style, reading, new com.romraider.portable.gauge.GaugeMotion());
    }
    FxInstrumentView(GaugeFaceRenderer.Style style, GaugeFaceRenderer.Reading reading,
            com.romraider.portable.gauge.GaugeMotion motion) {
        this.style = style; this.reading = reading;
        this.motion = motion;
        motion.update(reading.value, System.nanoTime());
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) animation.stop();
            else if (animated() && motion.isAnimating(System.nanoTime())) animation.start();
        });
        getChildren().add(canvas); setMinSize(120, 94); setPrefSize(320, 250);
        setAccessibleText(reading.name + ", " + (reading.available()
                ? reading.display + " " + reading.units : "no valid data") + ", " + reading.state);
    }
    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth()); canvas.setHeight(getHeight());
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        double factor = Math.min(getWidth() / 320, getHeight() / 250);
        if (factor <= 0) return;
        g.save(); g.translate((getWidth() - 320 * factor) / 2, (getHeight() - 250 * factor) / 2); g.scale(factor, factor);
        GaugeFaceRenderer.draw(new NativeSurface(g), style,
                animated() ? reading.withIndicator(motion.valueAt(System.nanoTime())) : reading); g.restore();
    }
    private boolean animated() {
        return style.usesNeedleMotion()
                && !Boolean.getBoolean("romraider2.gauge.reduceMotion");
    }
    private static Color color(int argb) {
        return Color.rgb((argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255, ((argb >>> 24) & 255) / 255.0);
    }
    private static final class NativeSurface implements GaugeFaceRenderer.Surface {
        private final GraphicsContext g;
        NativeSurface(GraphicsContext g) { this.g = g; }
        public void rect(double x, double y, double w, double h, double radius, int argb) {
            g.setFill(color(argb)); g.fillRoundRect(x, y, w, h, radius * 2, radius * 2);
        }
        public void circle(double x, double y, double radius, int argb) {
            g.setFill(color(argb)); g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        }
        public void radialCircle(double x, double y, double radius, int center, int edge) {
            g.setFill(new javafx.scene.paint.RadialGradient(0, 0, x, y, radius, false,
                    javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, color(center)), new javafx.scene.paint.Stop(1, color(edge))));
            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        }
        public void path(double[] commands, int argb) {
            g.beginPath();
            for (int i = 0; i < commands.length;) {
                switch ((int) commands[i++]) {
                    case 0: g.moveTo(commands[i++], commands[i++]); break;
                    case 1: g.lineTo(commands[i++], commands[i++]); break;
                    case 2: g.bezierCurveTo(commands[i++], commands[i++], commands[i++], commands[i++], commands[i++], commands[i++]); break;
                    case 3: g.closePath(); break;
                    default: throw new IllegalArgumentException("Unknown vector command");
                }
            }
            g.setFill(color(argb)); g.fill();
        }
        public void line(double x1, double y1, double x2, double y2, double width, int argb) {
            g.setStroke(color(argb)); g.setLineWidth(width); g.strokeLine(x1, y1, x2, y2);
        }
        public void text(String value, double x, double baseline, double size, int argb, int align, double maxWidth, boolean mono) {
            g.setFill(color(argb));
            String family = mono ? "Monospaced" : "SansSerif";
            Font font = Font.font(family, FontWeight.BOLD, size);
            Text measure = new Text(value); measure.setFont(font);
            if (measure.getLayoutBounds().getWidth() > maxWidth) {
                font = Font.font(family, FontWeight.BOLD, Math.max(size * .65, size * maxWidth / measure.getLayoutBounds().getWidth()));
                measure.setFont(font);
                while (value.length() > 1 && measure.getLayoutBounds().getWidth() > maxWidth) {
                    value = value.substring(0, value.length() - 2) + "…"; measure.setText(value);
                }
            }
            g.setFont(font); g.setTextAlign(align < 0 ? TextAlignment.LEFT : align > 0 ? TextAlignment.RIGHT : TextAlignment.CENTER);
            g.fillText(value, x, baseline);
        }
    }
}
