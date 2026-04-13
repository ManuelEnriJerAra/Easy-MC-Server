package vista;

import com.formdev.flatlaf.ui.FlatLineBorder;
import controlador.Main;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public final class AppTheme {
    private static final Color FALLBACK_BACKGROUND = Color.LIGHT_GRAY;
    private static final Color FALLBACK_ACCENT = new Color(0, 120, 215);
    private static final Color FALLBACK_BORDER = new Color(0, 0, 0, 60);
    private static final Color FALLBACK_FOREGROUND = Color.BLACK;
    private static final Color FALLBACK_MUTED_FOREGROUND = Color.GRAY;
    private static final Color FALLBACK_LINK = new Color(20, 90, 180);
    private static final Color FALLBACK_DIALOG_BORDER = Color.LIGHT_GRAY;
    private static final Color FALLBACK_SUBTLE_BORDER = new Color(0, 0, 0, 30);
    private static final Color DEFAULT_CONSOLE_BACKGROUND = Color.decode("#1D2036");
    private static final Color DEFAULT_CONSOLE_FOREGROUND = Color.WHITE;
    private static final Color DEFAULT_CONSOLE_CHAT = Color.CYAN;
    private static final Color DEFAULT_CONSOLE_ERROR = Color.RED;
    private static final Color DEFAULT_SUCCESS = new Color(129, 247, 135);
    private static final Color DEFAULT_DANGER = new Color(255, 87, 87);
    private static final Color DEFAULT_WARNING = new Color(255, 214, 99);

    private AppTheme() {
    }

    public static int getArc() {
        int arc = UIManager.getInt("Component.arc");
        return arc > 0 ? arc : Main.DEFAULT_ARC;
    }

    private static Color getBaseThemeBackground() {
        Color bg = UIManager.getColor("Panel.background");
        return bg != null ? bg : FALLBACK_BACKGROUND;
    }

    public static Color getBackground() {
        return darken(getBaseThemeBackground(), 0.07f);
    }

    public static Color getPanelBackground() {
        return getBaseThemeBackground();
    }

    public static Color getSurfaceBackground() {
        Color panel = getPanelBackground();
        float luminance = (0.299f * panel.getRed() + 0.587f * panel.getGreen() + 0.114f * panel.getBlue()) / 255f;
        return luminance >= 0.5f
                ? darken(panel, 0.035f)
                : tint(panel, Color.WHITE, 0.05f);
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

    public static Color getMutedForeground() {
        Color fg = UIManager.getColor("Label.disabledForeground");
        return fg != null ? fg : FALLBACK_MUTED_FOREGROUND;
    }

    public static Color getLinkForeground() {
        Color link = UIManager.getColor("Component.linkColor");
        return link != null ? link : FALLBACK_LINK;
    }

    public static Color getDialogBorderColor() {
        return FALLBACK_DIALOG_BORDER;
    }

    public static Color getSubtleBorderColor() {
        return FALLBACK_SUBTLE_BORDER;
    }

    public static Color getConsoleBackground() {
        return DEFAULT_CONSOLE_BACKGROUND;
    }

    public static Color getConsoleForeground() {
        return DEFAULT_CONSOLE_FOREGROUND;
    }

    public static Color getConsoleInfoForeground() {
        return DEFAULT_CONSOLE_FOREGROUND;
    }

    public static Color getConsoleChatForeground() {
        return DEFAULT_CONSOLE_CHAT;
    }

    public static Color getConsoleErrorForeground() {
        return DEFAULT_CONSOLE_ERROR;
    }

    public static Color getConsoleOutlineColor() {
        return withAlpha(Color.WHITE, 25);
    }

    public static Color getSuccessColor() {
        return DEFAULT_SUCCESS;
    }

    public static Color getDangerColor() {
        return DEFAULT_DANGER;
    }

    public static Color getWarningColor() {
        return DEFAULT_WARNING;
    }

    public static Color getDestructiveColor() {
        return Color.RED;
    }

    public static Color getTransparentColor() {
        return new Color(0, 0, 0, 0);
    }

    public static Color getImageHoverOverlayColor() {
        return withAlpha(Color.BLACK, 90);
    }

    public static Color getImageHoverTextColor() {
        return withAlpha(Color.WHITE, 230);
    }

    public static Color getCropBackground() {
        return Color.DARK_GRAY;
    }

    public static Color getCropOverlayColor() {
        return withAlpha(Color.BLACK, 120);
    }

    public static Color getCropSelectionBorderColor() {
        return withAlpha(Color.WHITE, 220);
    }

    public static Color getCropHandleColor() {
        return withAlpha(Color.WHITE, 240);
    }

    public static void applyCardTitleStyle(JLabel label) {
        if (label == null) return;
        label.setFont(label.getFont().deriveFont(Font.BOLD, Math.max(14f, label.getFont().getSize2D())));
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 2, 10));
        label.setForeground(getCardTitleColor());
    }

    public static Color getCardTitleColor() {
        Color panel = getPanelBackground();
        float luminance = (0.299f * panel.getRed() + 0.587f * panel.getGreen() + 0.114f * panel.getBlue()) / 255f;
        return luminance >= 0.5f
                ? darken(getForeground(), 0.18f)
                : tint(getForeground(), Color.BLACK, 0.22f);
    }

    public static void applyActionButtonStyle(AbstractButton button) {
        if (button == null) return;
        button.setFocusPainted(false);
        applyHandCursor(button);
        button.setOpaque(false);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setBorder(createRoundedBorder(new Insets(6, 12, 6, 12), 1f));
        button.setBackground(getSurfaceBackground());
        button.setForeground(getForeground());
    }

    public static void applyAccentButtonStyle(AbstractButton button) {
        if (button == null) return;
        applyActionButtonStyle(button);
        button.setBackground(getMainAccent());
        button.setForeground(Color.WHITE);
        button.setBorder(createAccentBorder(new Insets(6, 12, 6, 12), 1f));
    }

    public static void applySurfacePreviewStyle(JComponent component, Insets padding) {
        if (component == null) return;
        Insets resolvedPadding = padding != null ? padding : new Insets(10, 10, 10, 10);
        component.setOpaque(true);
        component.setBackground(getSurfaceBackground());
        Border border = createRoundedBorder(resolvedPadding, 1f);
        component.setBorder(border);
    }

    public static void applyHandCursor(Component component) {
        if (component == null) return;
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

    public static Color getSoftSelectionBackground() {
        return tint(getPanelBackground(), getMainAccent(), 0.10f);
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

    public static Color withAlpha(Color color, int alpha) {
        Color base = color != null ? color : getForeground();
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }

    public static FlatLineBorder createRoundedBorder(Insets insets, float thickness) {
        return new FlatLineBorder(insets, getBorderColor(), thickness, getArc());
    }

    public static FlatLineBorder createRoundedBorder(Insets insets, Color color, float thickness) {
        return new FlatLineBorder(insets, color, thickness, getArc());
    }

    public static FlatLineBorder createAccentBorder(Insets insets, float thickness) {
        return createRoundedBorder(insets, getMainAccent(), thickness);
    }
}
