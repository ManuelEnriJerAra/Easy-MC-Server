package vista;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class SvgIconFactory {
    private static final Map<String, FlatSVGIcon> ICON_CACHE = new ConcurrentHashMap<>();

    private SvgIconFactory() {
    }

    public static FlatSVGIcon create(String resourcePath, int width, int height) {
        return create(resourcePath, width, height, AppTheme::getForeground);
    }

    public static FlatSVGIcon createOriginal(String resourcePath, int width, int height) {
        String key = resourcePath + "|" + width + "|" + height + "|original";
        return ICON_CACHE.computeIfAbsent(key, ignored -> new FlatSVGIcon(resourcePath, width, height));
    }

    public static Icon createWithOpacity(String resourcePath, int width, int height, float opacity) {
        return createWithOpacity(resourcePath, width, height, AppTheme::getForeground, opacity);
    }

    public static Icon createWithOpacity(String resourcePath, int width, int height, Supplier<Color> colorSupplier, float opacity) {
        return new AlphaIcon(create(resourcePath, width, height, colorSupplier), opacity);
    }

    public static FlatSVGIcon create(String resourcePath, int width, int height, Supplier<Color> colorSupplier) {
        Supplier<Color> resolvedSupplier = colorSupplier != null ? colorSupplier : AppTheme::getForeground;
        Color resolvedColor = resolvedSupplier.get();
        int colorRgb = resolvedColor == null ? 0 : resolvedColor.getRGB();
        String key = resourcePath + "|" + width + "|" + height + "|" + colorRgb;
        return ICON_CACHE.computeIfAbsent(key, ignored -> {
            FlatSVGIcon icon = new FlatSVGIcon(resourcePath, width, height);
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> resolvedColor != null ? resolvedColor : color));
            return icon;
        });
    }

    public static RotatingIcon createRotating(String resourcePath, int width, int height, Supplier<Color> colorSupplier) {
        return new RotatingIcon(create(resourcePath, width, height, colorSupplier));
    }

    public static RotatingIcon createSpinning(String resourcePath, int width, int height, Supplier<Color> colorSupplier) {
        return createSpinning(resourcePath, width, height, colorSupplier, false);
    }

    public static RotatingIcon createSpinning(String resourcePath, int width, int height, Supplier<Color> colorSupplier, boolean clockwise) {
        RotatingIcon icon = createRotating(resourcePath, width, height, colorSupplier);
        icon.setClockwise(clockwise);
        return icon;
    }

    public static void apply(AbstractButton button, String resourcePath, int width, int height, Supplier<Color> colorSupplier) {
        if (button == null) {
            return;
        }
        button.setIcon(create(resourcePath, width, height, colorSupplier));
    }

    private static final class AlphaIcon implements Icon {
        private final Icon delegate;
        private final float opacity;

        private AlphaIcon(Icon delegate, float opacity) {
            this.delegate = delegate;
            this.opacity = Math.max(0f, Math.min(1f, opacity));
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (delegate == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            delegate.paintIcon(c, g2, x, y);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return delegate != null ? delegate.getIconWidth() : 0;
        }

        @Override
        public int getIconHeight() {
            return delegate != null ? delegate.getIconHeight() : 0;
        }
    }

    public static final class RotatingIcon implements Icon {
        private final Icon delegate;
        private double angleRadians;
        private boolean clockwise;

        private RotatingIcon(Icon delegate) {
            this.delegate = delegate;
        }

        public void setAngleRadians(double angleRadians) {
            this.angleRadians = angleRadians;
        }

        public void setClockwise(boolean clockwise) {
            this.clockwise = clockwise;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (delegate == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            int width = getIconWidth();
            int height = getIconHeight();
            double centerX = x + (width / 2.0d);
            double centerY = y + (height / 2.0d);
            AffineTransform oldTransform = g2.getTransform();
            g2.rotate(clockwise ? angleRadians : -angleRadians, centerX, centerY);
            delegate.paintIcon(c, g2, x, y);
            g2.setTransform(oldTransform);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return delegate != null ? delegate.getIconWidth() : 0;
        }

        @Override
        public int getIconHeight() {
            return delegate != null ? delegate.getIconHeight() : 0;
        }
    }
}
