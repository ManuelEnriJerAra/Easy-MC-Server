package vista;

import javax.swing.*;
import java.awt.*;

public final class BoxCategory {

    private BoxCategory() {
    }

    public static JPanel createFieldCard(String labelText, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setOpaque(true);
        card.setBackground(AppTheme.getSurfaceBackground());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        card.add(label, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        makeWidthFlexible(card);
        return card;
    }

    public static JPanel createBooleanCard(String labelText, JCheckBox checkBox) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(AppTheme.getSurfaceBackground());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)
        ));

        checkBox.setText(labelText);
        checkBox.setOpaque(false);
        card.add(checkBox, BorderLayout.CENTER);
        makeWidthFlexible(card);
        return card;
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
