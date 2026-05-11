package vista;

import controlador.extensions.ExtensionCompatibilityStatus;

import java.awt.Color;

final class ExtensionStatusPresentation {
    private ExtensionStatusPresentation() {
    }

    static Color foreground(ExtensionCompatibilityStatus status) {
        Color accent = iconColor(status);
        Color background = background(status);
        if (contrastRatio(accent, background) >= 4.5d) {
            return accent;
        }
        Color foreground = AppTheme.getForeground();
        if (contrastRatio(foreground, background) >= 4.5d) {
            return foreground;
        }
        return AppTheme.isLightTheme() ? Color.BLACK : Color.WHITE;
    }

    static Color background(ExtensionCompatibilityStatus status) {
        Color accent = iconColor(status);
        return AppTheme.isLightTheme()
                ? AppTheme.tint(AppTheme.getPanelBackground(), accent, 0.16f)
                : AppTheme.tint(AppTheme.getPanelBackground(), accent, 0.24f);
    }

    static Color neutralBadgeBackground() {
        return AppTheme.isLightTheme()
                ? AppTheme.darken(AppTheme.getPanelBackground(), 0.08f)
                : AppTheme.tint(AppTheme.getPanelBackground(), Color.WHITE, 0.14f);
    }

    static Color iconColor(ExtensionCompatibilityStatus status) {
        return switch (status == null ? ExtensionCompatibilityStatus.WARNING : status) {
            case COMPATIBLE -> AppTheme.isLightTheme()
                    ? AppTheme.darken(AppTheme.getSuccessColor(), 0.18f)
                    : AppTheme.getSuccessColor();
            case WARNING -> AppTheme.isLightTheme()
                    ? AppTheme.darken(AppTheme.getWarningColor(), 0.34f)
                    : AppTheme.getWarningColor();
            case INCOMPATIBLE -> AppTheme.isLightTheme()
                    ? AppTheme.darken(AppTheme.getDangerColor(), 0.10f)
                    : AppTheme.getDangerColor();
        };
    }

    static Color foregroundFor(Color background) {
        Color preferred = AppTheme.getForeground();
        if (contrastRatio(preferred, background) >= 4.5d) {
            return preferred;
        }
        return contrastRatio(Color.BLACK, background) >= contrastRatio(Color.WHITE, background)
                ? Color.BLACK
                : Color.WHITE;
    }

    private static double contrastRatio(Color left, Color right) {
        double l1 = relativeLuminance(left) + 0.05d;
        double l2 = relativeLuminance(right) + 0.05d;
        return Math.max(l1, l2) / Math.min(l1, l2);
    }

    private static double relativeLuminance(Color color) {
        if (color == null) {
            color = AppTheme.getPanelBackground();
        }
        return 0.2126d * linear(color.getRed())
                + 0.7152d * linear(color.getGreen())
                + 0.0722d * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255d;
        return value <= 0.03928d ? value / 12.92d : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }

    static String iconPath(ExtensionCompatibilityStatus status) {
        return switch (status == null ? ExtensionCompatibilityStatus.WARNING : status) {
            case COMPATIBLE -> null;
            case WARNING -> "easymcicons/warning.svg";
            case INCOMPATIBLE -> "easymcicons/cross2.svg";
        };
    }
}
