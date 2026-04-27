package vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class CardPanel extends JPanel {
    private static final Insets CARD_INSETS = new Insets(10, 10, 10, 10);
    private static final int HEADER_ACTIONS_HGAP = 8;
    private static final int FOOTER_ACTIONS_HGAP = 8;
    private static final int FOOTER_TOP_GAP = 8;
    private static final int FULL_HEIGHT_SIDE_GAP = 10;
    private static final Insets TITLE_INSETS = new Insets(0, 0, 8, 0);

    private Color borderColor;
    private float borderThickness;
    private final RoundedBackgroundPanel cardSurface;
    private final JLabel titleLabel;
    private final JPanel headerActionsPanel;
    private final JPanel actionsPanel;
    private final JPanel contentPanel;
    private final JPanel headerPanel;
    private final JPanel footerPanel;
    private final JPanel contentWrap;
    private final JPanel mainPanel;
    private final JPanel fullHeightSidePanel;
    private Component fullHeightSideComponent;

    public CardPanel(String title) {
        super(new BorderLayout());
        this.borderColor = null;
        this.borderThickness = 1f;

        setOpaque(false);

        cardSurface = new RoundedBackgroundPanel(AppTheme.getPanelBackground(), AppTheme.getArc());
        cardSurface.setLayout(new BorderLayout());
        super.add(cardSurface, BorderLayout.CENTER);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        cardSurface.add(mainPanel, BorderLayout.CENTER);

        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        titleLabel = new CardTitleLabel();
        applyTitleStyle();
        headerPanel.add(titleLabel, BorderLayout.WEST);

        headerActionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, HEADER_ACTIONS_HGAP, 0)) {
            @Override
            public Component add(Component comp) {
                Component added = super.add(comp);
                updateHeaderVisibility();
                return added;
            }

            @Override
            public void remove(Component comp) {
                super.remove(comp);
                updateHeaderVisibility();
            }

            @Override
            public void removeAll() {
                super.removeAll();
                updateHeaderVisibility();
            }
        };
        headerActionsPanel.setOpaque(false);
        headerPanel.add(headerActionsPanel, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        contentWrap = new JPanel(new BorderLayout());
        contentWrap.setOpaque(false);
        contentWrap.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(contentWrap, BorderLayout.CENTER);

        actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, FOOTER_ACTIONS_HGAP, 0)) {
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
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        fullHeightSidePanel = new JPanel(new BorderLayout());
        fullHeightSidePanel.setOpaque(false);
        fullHeightSidePanel.setVisible(false);
        cardSurface.add(fullHeightSidePanel, BorderLayout.EAST);

        setTitle(title);
        updateHeaderVisibility();
        updateFooterVisibility();
        refreshCardStyle();
    }

    public void setTitle(String title) {
        String resolved = title == null ? "" : title;
        titleLabel.setText(resolved);
        updateHeaderVisibility();
    }

    public JLabel getTitleLabel() {
        return titleLabel;
    }

    public JPanel getActionsPanel() {
        return actionsPanel;
    }

    public JPanel getHeaderActionsPanel() {
        return headerActionsPanel;
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }

    public void setFullHeightSideComponent(Component component) {
        if (fullHeightSideComponent != null) {
            fullHeightSidePanel.remove(fullHeightSideComponent);
        }
        fullHeightSideComponent = component;
        if (component != null) {
            fullHeightSidePanel.add(component, BorderLayout.CENTER);
        }
        updateFullHeightSideVisibility();
    }

    public Component getFullHeightSideComponent() {
        return fullHeightSideComponent;
    }

    public JPanel getFullHeightSidePanel() {
        return fullHeightSidePanel;
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
        applyTitleStyle();
        refreshBorder();
    }

    private void applyTitleStyle() {
        AppTheme.applyCardTitleStyle(titleLabel);
        titleLabel.setOpaque(false);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(
                TITLE_INSETS.top,
                TITLE_INSETS.left,
                TITLE_INSETS.bottom,
                TITLE_INSETS.right
        ));
    }

    private void refreshBorder() {
        if (cardSurface == null) {
            return;
        }
        Color resolvedBorder = borderColor != null ? borderColor : AppTheme.getBorderColor();
        float resolvedThickness = borderThickness > 0f ? borderThickness : 1f;
        cardSurface.setBorder(AppTheme.createRoundedBorder(CARD_INSETS, resolvedBorder, resolvedThickness));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(actionsPanel.getComponentCount() > 0 ? FOOTER_TOP_GAP : 0, 0, 0, 0));
        updateFullHeightSideVisibility();
    }

    private void updateHeaderVisibility() {
        boolean visible = (titleLabel != null && titleLabel.getText() != null && !titleLabel.getText().isBlank())
                || (headerActionsPanel != null && headerActionsPanel.getComponentCount() > 0);
        headerPanel.setVisible(visible);
        headerPanel.revalidate();
        headerPanel.repaint();
    }

    private void updateFooterVisibility() {
        boolean visible = actionsPanel.getComponentCount() > 0;
        footerPanel.setVisible(visible);
        footerPanel.setBorder(BorderFactory.createEmptyBorder(visible ? FOOTER_TOP_GAP : 0, 0, 0, 0));
        footerPanel.revalidate();
        footerPanel.repaint();
    }

    private void updateFullHeightSideVisibility() {
        if (fullHeightSidePanel == null) {
            return;
        }
        boolean visible = fullHeightSideComponent != null;
        fullHeightSidePanel.setVisible(visible);
        fullHeightSidePanel.setBorder(BorderFactory.createEmptyBorder(0, visible ? FULL_HEIGHT_SIDE_GAP : 0, 0, 0));
        fullHeightSidePanel.revalidate();
        fullHeightSidePanel.repaint();
    }

}
