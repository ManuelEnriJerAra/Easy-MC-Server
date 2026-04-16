package vista;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public final class SvgIconFactory {
    private SvgIconFactory() {
    }

    public static FlatSVGIcon create(String resourcePath, int width, int height) {
        return create(resourcePath, width, height, AppTheme::getForeground);
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

    public static void apply(AbstractButton button, String resourcePath, int width, int height, Supplier<Color> colorSupplier) {
        if (button == null) {
            return;
        }
        button.setIcon(create(resourcePath, width, height, colorSupplier));
    }
}
