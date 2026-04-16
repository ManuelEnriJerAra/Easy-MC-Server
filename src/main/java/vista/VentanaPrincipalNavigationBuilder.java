package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

final class VentanaPrincipalNavigationBuilder {
    record Result(JPanel panel, Map<VentanaPrincipal.PaginaDerecha, JButton> navButtons) {
    }

    Result build(
            Consumer<VentanaPrincipal.PaginaDerecha> pageNavigator,
            Runnable openThemeSelector,
            Consumer<JButton> selectionRestorer
    ) {
        CardPanel barra = new CardPanel(new BorderLayout(), new Insets(6, 6, 6, 6));
        barra.setBackground(AppTheme.getPanelBackground());
        barra.setPreferredSize(new Dimension(56, 0));

        Map<VentanaPrincipal.PaginaDerecha, JButton> navButtons = new EnumMap<>(VentanaPrincipal.PaginaDerecha.class);

        JPanel botones = new JPanel();
        botones.setOpaque(false);
        botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS));

        JButton home = crearNavButton("easymcicons/home.svg", "Home", VentanaPrincipal.PaginaDerecha.HOME, pageNavigator, navButtons);
        JButton mundo = crearNavButton("easymcicons/earth.svg", "Mundos", VentanaPrincipal.PaginaDerecha.MUNDO, pageNavigator, navButtons);
        JButton config = crearNavButton("easymcicons/settings.svg", "Configuración del servidor", VentanaPrincipal.PaginaDerecha.CONFIG, pageNavigator, navButtons);
        JButton stats = crearNavButton("easymcicons/chart.svg", "Estadísticas", VentanaPrincipal.PaginaDerecha.STATS, pageNavigator, navButtons);
        JButton temas = crearActionButton("easymcicons/pallete.svg", "Temas", openThemeSelector);
        JButton info = crearNavButton("easymcicons/info.svg", "Información", VentanaPrincipal.PaginaDerecha.INFO, pageNavigator, navButtons);

        botones.add(home);
        botones.add(Box.createVerticalStrut(8));
        botones.add(mundo);
        botones.add(Box.createVerticalStrut(8));
        botones.add(config);
        botones.add(Box.createVerticalStrut(8));
        botones.add(stats);
        botones.add(Box.createVerticalGlue());
        botones.add(temas);
        botones.add(Box.createVerticalStrut(6));
        botones.add(info);
        botones.add(Box.createVerticalStrut(4));

        MouseAdapter navHover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                Object src = e.getSource();
                if (!(src instanceof JButton button)) {
                    return;
                }
                if (button.getBackground() != null && button.isOpaque()) {
                    return;
                }
                aplicarHover(button);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (selectionRestorer != null && e.getSource() instanceof JButton button) {
                    selectionRestorer.accept(button);
                }
            }
        };
        home.addMouseListener(navHover);
        mundo.addMouseListener(navHover);
        config.addMouseListener(navHover);
        stats.addMouseListener(navHover);
        info.addMouseListener(navHover);

        temas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                aplicarHover(temas);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                temas.setOpaque(false);
                temas.setContentAreaFilled(false);
                temas.setBackground(null);
                temas.setBorder(new FlatLineBorder(new Insets(6, 6, 6, 6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc()));
                temas.repaint();
            }
        });

        barra.getContentPanel().add(botones, BorderLayout.CENTER);
        return new Result(barra, navButtons);
    }

    private JButton crearNavButton(
            String iconPath,
            String tooltip,
            VentanaPrincipal.PaginaDerecha pagina,
            Consumer<VentanaPrincipal.PaginaDerecha> pageNavigator,
            Map<VentanaPrincipal.PaginaDerecha, JButton> navButtons
    ) {
        FlatButton button = createBaseButton(iconPath, tooltip);
        button.addActionListener(e -> {
            if (pageNavigator != null) {
                pageNavigator.accept(pagina);
            }
        });
        navButtons.put(pagina, button);
        return button;
    }

    private JButton crearActionButton(String iconPath, String tooltip, Runnable action) {
        FlatButton button = createBaseButton(iconPath, tooltip);
        button.addActionListener(e -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private FlatButton createBaseButton(String iconPath, String tooltip) {
        FlatButton button = new FlatButton();
        button.setIcon(SvgIconFactory.create(iconPath, 32, 32));
        button.setFocusPainted(false);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setToolTipText(tooltip);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 18f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorderPainted(true);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(new FlatLineBorder(new Insets(6, 6, 6, 6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc()));
        button.putClientProperty("JButton.buttonType", "roundRect");
        return button;
    }

    private void aplicarHover(JButton button) {
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(AppTheme.getSelectionBackground());
        button.setBorder(new FlatLineBorder(new Insets(6, 6, 6, 6), AppTheme.getMainAccent(), 1f, AppTheme.getArc()));
        button.repaint();
    }
}
