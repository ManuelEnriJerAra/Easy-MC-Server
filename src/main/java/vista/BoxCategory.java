package vista;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public final class BoxCategory {
    private static final Insets DEFAULT_CARD_PADDING = new Insets(6, 8, 6, 8);
    private static final int DEFAULT_FIELD_GAP = 6;

    private BoxCategory() {
    }

    public static JPanel createFieldCard(String labelText, JComponent content) {
        return createFieldCard(labelText, content, DEFAULT_FIELD_GAP, DEFAULT_CARD_PADDING, null);
    }

    public static JPanel createFieldCard(String labelText, JComponent content, Consumer<JLabel> labelConfigurer) {
        return createFieldCard(labelText, content, DEFAULT_FIELD_GAP, DEFAULT_CARD_PADDING, labelConfigurer);
    }

    public static JPanel createFieldCard(String labelText, JComponent content, int verticalGap, Insets padding, Consumer<JLabel> labelConfigurer) {
        JPanel card = createSurfacePanel(new BorderLayout(0, verticalGap), padding);

        JLabel label = createFieldLabel(labelText);
        if (labelConfigurer != null) {
            labelConfigurer.accept(label);
        }
        card.add(label, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        makeWidthFlexible(card);
        return card;
    }

    public static JPanel createBooleanCard(String labelText, JCheckBox checkBox) {
        JPanel card = createSurfacePanel(new BorderLayout(), DEFAULT_CARD_PADDING);

        checkBox.setText(labelText);
        checkBox.setOpaque(false);
        card.add(checkBox, BorderLayout.CENTER);
        makeWidthFlexible(card);
        return card;
    }

    public static JPanel createSurfacePanel(LayoutManager layout) {
        return createSurfacePanel(layout, DEFAULT_CARD_PADDING);
    }

    public static JPanel createSurfacePanel(LayoutManager layout, Insets padding) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(true);
        panel.setBackground(AppTheme.getSurfaceBackground());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true),
                BorderFactory.createEmptyBorder(padding.top, padding.left, padding.bottom, padding.right)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    public static JPanel createTitledSection(String title, int verticalGap) {
        JPanel section = new JPanel(new BorderLayout(0, verticalGap));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createTitledBorder(title));
        return section;
    }

    public static JLabel createFieldLabel(String labelText) {
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        return label;
    }

    public static CardPanel createSummaryCard(String title, JComponent content) {
        CardPanel card = new CardPanel(title);
        card.setBackground(AppTheme.getSurfaceBackground());
        JLabel titleLabel = card.getTitleLabel();
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        card.getContentPanel().add(content, BorderLayout.CENTER);
        return card;
    }

    public static JPanel createInfoRow(String labelText, JComponent valueComponent) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueComponent.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(label, BorderLayout.WEST);
        row.add(valueComponent, BorderLayout.CENTER);
        Dimension preferredSize = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
        return row;
    }

    public static <T extends AbstractButton> T createSurfaceButtonCard(T button, Insets padding) {
        if (button == null) {
            return null;
        }
        Insets resolvedPadding = padding != null ? padding : new Insets(8, 8, 8, 8);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setVerticalAlignment(SwingConstants.TOP);
        button.setBackground(AppTheme.getSurfaceBackground());
        button.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.createRoundedBorder(resolvedPadding, 1f),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        AppTheme.applyHandCursor(button);
        return button;
    }

    public static void makeWidthFlexible(JComponent component) {
        if (component == null) {
            return;
        }
        Dimension preferred = component.getPreferredSize();
        int preferredHeight = preferred != null ? preferred.height : 24;
        component.setMinimumSize(new Dimension(0, Math.max(preferredHeight, 24)));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(preferredHeight, 24)));
    }
}
