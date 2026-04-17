package vista;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.function.Supplier;

public final class SvgIconFactory {
    private SvgIconFactory() {
    }

    public static FlatSVGIcon create(String resourcePath, int width, int height) {
        return create(resourcePath, width, height, AppTheme::getForeground);
    }

    public static Icon createWithOpacity(String resourcePath, int width, int height, float opacity) {
        return createWithOpacity(resourcePath, width, height, AppTheme::getForeground, opacity);
    }

    public static Icon createWithOpacity(String resourcePath, int width, int height, Supplier<Color> colorSupplier, float opacity) {
        return new AlphaIcon(create(resourcePath, width, height, colorSupplier), opacity);
    }

    public static FlatSVGIcon create(String resourcePath, int width, int height, Supplier<Color> colorSupplier) {
        FlatSVGIcon icon = new FlatSVGIcon(resourcePath, width, height);
        Supplier<Color> resolvedSupplier = colorSupplier != null ? colorSupplier : AppTheme::getForeground;
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> {
            Color themedColor = resolvedSupplier.get();
            return themedColor != null ? themedColor : color;
        }));
        return icon;
    }

    public static RotatingIcon createRotating(String resourcePath, int width, int height, Supplier<Color> colorSupplier) {
        return new RotatingIcon(create(resourcePath, width, height, colorSupplier));
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

        private RotatingIcon(Icon delegate) {
            this.delegate = delegate;
        }

        public void setAngleRadians(double angleRadians) {
            this.angleRadians = angleRadians;
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
            g2.rotate(angleRadians, centerX, centerY);
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
