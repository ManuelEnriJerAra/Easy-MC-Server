package vista;

import javax.swing.*;
import java.awt.*;

public class TitledCardPanel extends JPanel {
    private final CardPanel card;
    private final JLabel titleLabel;
    private final JPanel actionsPanel;
    private final JPanel contentPanel;

    public TitledCardPanel(String title){
        this(title, new Insets(10, 10, 10, 10));
    }

    public TitledCardPanel(String title, Insets cardInsets){
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        card = new CardPanel(new BorderLayout(), cardInsets);
        add(card, BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        titleLabel = new JLabel(title == null ? "" : title);
        AppTheme.applyCardTitleStyle(titleLabel);
        header.add(titleLabel, BorderLayout.WEST);
        card.add(header, BorderLayout.NORTH);

        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        JPanel contentWrap = new JPanel(new BorderLayout());
        contentWrap.setOpaque(false);
        contentWrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        contentWrap.add(contentPanel, BorderLayout.CENTER);
        card.add(contentWrap, BorderLayout.CENTER);

        actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)) {
            @Override
            public Component add(Component comp){
                Component added = super.add(comp);
                setVisible(getComponentCount() > 0);
                return added;
            }
        };
        actionsPanel.setOpaque(false);
        actionsPanel.setVisible(false);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        footer.add(actionsPanel, BorderLayout.EAST);
        card.add(footer, BorderLayout.SOUTH);
    }

    public CardPanel getCard(){
        return card;
    }

    public JLabel getTitleLabel(){
        return titleLabel;
    }

    public JPanel getActionsPanel(){
        return actionsPanel;
    }

    public JPanel getContentPanel(){
        return contentPanel;
    }
}
