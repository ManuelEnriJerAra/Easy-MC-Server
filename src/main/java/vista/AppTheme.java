package vista;

import com.formdev.flatlaf.ui.FlatLineBorder;
import controlador.Main;

import javax.swing.*;
import java.awt.*;

public final class AppTheme {
    private static final Color FALLBACK_BACKGROUND = Color.LIGHT_GRAY;
    private static final Color FALLBACK_ACCENT = new Color(0, 120, 215);
    private static final Color FALLBACK_BORDER = new Color(0, 0, 0, 60);
    private static final Color FALLBACK_FOREGROUND = Color.BLACK;
    private static final Color DEFAULT_CONSOLE_BACKGROUND = Color.decode("#1D2036");

    private AppTheme() {
    }

    public static int getArc() {
        int arc = UIManager.getInt("Component.arc");
        return arc > 0 ? arc : Main.DEFAULT_ARC;
    }

    public static Color getBackground() {
        Color bg = UIManager.getColor("Panel.background");
        return bg != null ? bg : FALLBACK_BACKGROUND;
    }

    public static Color getPanelBackground() {
        return getBackground();
    }

    public static Color getMainAccent() {
        Color accent = UIManager.getColor("Component.focusColor");
        return accent != null ? accent : FALLBACK_ACCENT;
    }

    public static Color getBorderColor() {
        Color border = UIManager.getColor("Component.borderColor");
        if (border == null) border = UIManager.getColor("Separator.foreground");
        return border != null ? border : FALLBACK_BORDER;
    }

    public static Color getForeground() {
        Color fg = UIManager.getColor("Label.foreground");
        return fg != null ? fg : FALLBACK_FOREGROUND;
    }

    public static Color getConsoleBackground() {
        return DEFAULT_CONSOLE_BACKGROUND;
    }

    public static Color getConsoleBorderColor() {
        return getConsoleBackground();
    }

    public static Color getHoverBackground() {
        return darken(getPanelBackground(), 0.06f);
    }

    public static Color getPressedBackground() {
        return darken(getPanelBackground(), 0.12f);
    }

    public static Color getSelectionBackground() {
        return tint(getPanelBackground(), getMainAccent(), 0.18f);
    }

    public static Color tint(Color base, Color accent, float t) {
        if (base == null) base = getPanelBackground();
        if (accent == null) accent = getMainAccent();
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (base.getRed() + (accent.getRed() - base.getRed()) * t);
        int g = (int) (base.getGreen() + (accent.getGreen() - base.getGreen()) * t);
        int b = (int) (base.getBlue() + (accent.getBlue() - base.getBlue()) * t);
        return new Color(r, g, b);
    }

    public static Color darken(Color color, float amount) {
        if (color == null) color = getPanelBackground();
        amount = Math.max(0f, Math.min(1f, amount));
        int r = (int) (color.getRed() * (1f - amount));
        int g = (int) (color.getGreen() * (1f - amount));
        int b = (int) (color.getBlue() * (1f - amount));
        return new Color(r, g, b);
    }

    public static FlatLineBorder createRoundedBorder(Insets insets, float thickness) {
        return new FlatLineBorder(insets, getBorderColor(), thickness, getArc());
    }

    public static FlatLineBorder createAccentBorder(Insets insets, float thickness) {
        return new FlatLineBorder(insets, getMainAccent(), thickness, getArc());
    }
}