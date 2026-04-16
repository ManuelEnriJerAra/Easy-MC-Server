package vista;

import javax.swing.*;
import java.awt.*;

public class CardPanel extends JPanel {
    private Insets borderInsets;
    private Color borderColor;
    private float borderThickness;
    private final RoundedBackgroundPanel cardSurface;
    private final JLabel titleLabel;
    private final JPanel actionsPanel;
    private final JPanel contentPanel;
    private final JPanel headerPanel;
    private final JPanel footerPanel;
    private final JPanel contentWrap;

    public CardPanel() {
        this(null, new BorderLayout(), new Insets(8, 8, 8, 8));
    }

    public CardPanel(LayoutManager layout) {
        this(null, layout, new Insets(8, 8, 8, 8));
    }

    public CardPanel(LayoutManager layout, Insets borderInsets) {
        this(null, layout, borderInsets, null, 1f);
    }

    public CardPanel(LayoutManager layout, Insets borderInsets, Color borderColor, float borderThickness) {
        this(null, layout, borderInsets, borderColor, borderThickness);
    }

    public CardPanel(String title) {
        this(title, new BorderLayout(), new Insets(10, 10, 10, 10));
    }

    public CardPanel(String title, Insets borderInsets) {
        this(title, new BorderLayout(), borderInsets, null, 1f);
    }

    public CardPanel(String title, LayoutManager layout, Insets borderInsets) {
        this(title, layout, borderInsets, null, 1f);
    }

    public CardPanel(String title, LayoutManager layout, Insets borderInsets, Color borderColor, float borderThickness) {
        super(new BorderLayout());
        this.borderInsets = borderInsets;
        this.borderColor = borderColor;
        this.borderThickness = borderThickness;

        setOpaque(false);

        cardSurface = new RoundedBackgroundPanel(AppTheme.getPanelBackground(), AppTheme.getArc());
        cardSurface.setLayout(new BorderLayout());
        super.add(cardSurface, BorderLayout.CENTER);

        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        titleLabel = new JLabel();
        AppTheme.applyCardTitleStyle(titleLabel);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        cardSurface.add(headerPanel, BorderLayout.NORTH);

        contentPanel = new JPanel(layout != null ? layout : new BorderLayout());
        contentPanel.setOpaque(false);

        contentWrap = new JPanel(new BorderLayout());
        contentWrap.setOpaque(false);
        contentWrap.add(contentPanel, BorderLayout.CENTER);
        cardSurface.add(contentWrap, BorderLayout.CENTER);

        actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)) {
            @Override
            public Component add(Component comp) {
                Component added = super.add(comp);
                updateFooterVisibility();
                return added;
            }

            @Override
            public void remove(Component comp) {
                super.remove(comp);
                updateFooterVisibility();
            }

            @Override
            public void removeAll() {
                super.removeAll();
                updateFooterVisibility();
            }
        };
        actionsPanel.setOpaque(false);

        footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);
        footerPanel.add(actionsPanel, BorderLayout.EAST);
        cardSurface.add(footerPanel, BorderLayout.SOUTH);

        setTitle(title);
        updateFooterVisibility();
        refreshCardStyle();
    }

    public void setTitle(String title) {
        String resolved = title == null ? "" : title;
        titleLabel.setText(resolved);
        boolean visible = !resolved.isBlank();
        headerPanel.setVisible(visible);
        contentWrap.setBorder(BorderFactory.createEmptyBorder(visible ? 6 : 0, 0, 0, 0));
    }

    public JLabel getTitleLabel() {
        return titleLabel;
    }

    public JPanel getActionsPanel() {
        return actionsPanel;
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }

    public void setBorderInsets(Insets borderInsets) {
        this.borderInsets = borderInsets;
        refreshBorder();
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        refreshBorder();
    }

    public void setBorderThickness(float borderThickness) {
        this.borderThickness = borderThickness;
        refreshBorder();
    }

    @Override
    public void setBackground(Color bg) {
        if (cardSurface != null) {
            cardSurface.setBackground(bg);
        } else {
            super.setBackground(bg);
        }
    }

    @Override
    public Color getBackground() {
        return cardSurface != null ? cardSurface.getBackground() : super.getBackground();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        refreshCardStyle();
    }

    public void refreshTheme() {
        refreshCardStyle();
        revalidate();
        repaint();
    }

    private void refreshCardStyle() {
        if (cardSurface == null) {
            return;
        }
        cardSurface.setBackground(AppTheme.getPanelBackground());
        cardSurface.setArc(AppTheme.getArc());
        AppTheme.applyCardTitleStyle(titleLabel);
        refreshBorder();
    }

    private void refreshBorder() {
        if (cardSurface == null) {
            return;
        }
        Insets resolvedInsets = borderInsets != null ? borderInsets : new Insets(8, 8, 8, 8);
        Color resolvedBorder = borderColor != null ? borderColor : AppTheme.getBorderColor();
        float resolvedThickness = borderThickness > 0f ? borderThickness : 1f;
        cardSurface.setBorder(AppTheme.createRoundedBorder(resolvedInsets, resolvedBorder, resolvedThickness));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(actionsPanel.getComponentCount() > 0 ? 8 : 0, 0, 0, 0));
    }

    private void updateFooterVisibility() {
        boolean visible = actionsPanel.getComponentCount() > 0;
        footerPanel.setVisible(visible);
        footerPanel.setBorder(BorderFactory.createEmptyBorder(visible ? 8 : 0, 0, 0, 0));
        footerPanel.revalidate();
        footerPanel.repaint();
    }
}
