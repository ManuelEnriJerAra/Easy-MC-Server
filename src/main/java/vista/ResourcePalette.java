package vista;

import java.awt.*;

final class ResourcePalette {
    private ResourcePalette() {
    }

    static Color colorForPercent(double percent) {
        double clamped = Math.max(0d, Math.min(100d, percent));
        if (clamped <= 50d) {
            return blend(AppTheme.getSuccessColor(), AppTheme.getWarningColor(), clamped / 50d);
        }
        return blend(AppTheme.getWarningColor(), AppTheme.getDangerColor(), (clamped - 50d) / 50d);
    }

    static Color blend(Color from, Color to, double ratio) {
        double clamped = Math.max(0d, Math.min(1d, ratio));
        int r = (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped);
        int g = (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped);
        int b = (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped);
        return new Color(r, g, b);
    }
}
