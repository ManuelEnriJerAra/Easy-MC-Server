package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import controlador.GestorServidores;
import modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

final class VentanaPrincipalRightContentBuilder {
    static final class Result {
        private final Server server;
        private final GestorServidores gestorServidores;
        private final Consumer<Server> onServerConverted;

        private JPanel homePanel;
        private JPanel mundoPanel;
        private PanelConfigServidor panelConfigServidor;
        private JPanel extensionesPanel;
        private JPanel statsPanel;
        private JPanel automationPanel;
        private JPanel infoPanel;
        private JSplitPane splitHome;
        private CardPanel jugadoresCard;
        private JPanel consolaCard;
        private PanelConsola panelConsola;

        private Result(Server server,
                       GestorServidores gestorServidores,
                       Consumer<Server> onServerConverted) {
            this.server = server;
            this.gestorServidores = gestorServidores;
            this.onServerConverted = onServerConverted;
        }

        JPanel homePanel() {
            if (homePanel == null) {
                homePanel = crearHomePanel();
            }
            return homePanel;
        }

        JPanel mundoPanel() {
            if (mundoPanel == null) {
                mundoPanel = new PanelMundo(gestorServidores, () -> panelConfigServidor().reload());
            }
            return mundoPanel;
        }

        JPanel configPanel() {
            return panelConfigServidor();
        }

        JPanel extensionesPanel() {
            if (extensionesPanel == null) {
                extensionesPanel = new PanelExtensiones(gestorServidores, server);
            }
            return extensionesPanel;
        }

        JPanel statsPanel() {
            if (statsPanel == null) {
                statsPanel = new PanelEstadisticas(gestorServidores, server);
            }
            return statsPanel;
        }

        JPanel automationPanel() {
            if (automationPanel == null) {
                automationPanel = new PanelAutomatizacion(gestorServidores, server);
            }
            return automationPanel;
        }

        JPanel infoPanel() {
            if (infoPanel == null) {
                infoPanel = new PanelInformacion();
            }
            return infoPanel;
        }

        JSplitPane splitHome() {
            homePanel();
            return splitHome;
        }

        CardPanel jugadoresCard() {
            homePanel();
            return jugadoresCard;
        }

        JPanel consolaCard() {
            homePanel();
            return consolaCard;
        }

        PanelConsola panelConsola() {
            homePanel();
            return panelConsola;
        }

        PanelConfigServidor panelConfigServidor() {
            if (panelConfigServidor == null) {
                panelConfigServidor = new PanelConfigServidor(gestorServidores);
            }
            return panelConfigServidor;
        }

        private JPanel crearHomePanel() {
            JPanel home = new JPanel(new BorderLayout(0, 8));
            home.setOpaque(false);

            PanelTotalServidor panelTotalServidor = new PanelTotalServidor(gestorServidores);
            PanelJugadores panelJugadores = new PanelJugadores(gestorServidores, false);
            panelConsola = new PanelConsola(gestorServidores);
            panelConsola.setPreferredSize(new Dimension(0, 100));

            CardPanel headerCard = new CardPanel("Servidor seleccionado");
            headerCard.setBorder(BorderFactory.createEmptyBorder());
            JButton conversionButton = crearBotonConversion(server, gestorServidores, onServerConverted);
            if (conversionButton != null) {
                headerCard.getHeaderActionsPanel().add(conversionButton);
            }
            headerCard.getContentPanel().add(panelTotalServidor, BorderLayout.CENTER);
            headerCard.setFullHeightSideComponent(new PanelControlServidor(gestorServidores));
            home.add(headerCard, BorderLayout.NORTH);

            jugadoresCard = new CardPanel("Jugadores");
            jugadoresCard.setBorder(BorderFactory.createEmptyBorder());
            panelJugadores.configureHeaderActions(jugadoresCard.getHeaderActionsPanel());
            jugadoresCard.getContentPanel().add(panelJugadores, BorderLayout.CENTER);

            consolaCard = new JPanel(new BorderLayout());
            consolaCard.setOpaque(false);
            consolaCard.setBorder(null);
            consolaCard.add(panelConsola, BorderLayout.CENTER);

            splitHome = new com.formdev.flatlaf.extras.components.FlatSplitPane();
            splitHome.setOrientation(JSplitPane.VERTICAL_SPLIT);
            splitHome.setTopComponent(jugadoresCard);
            splitHome.setBottomComponent(consolaCard);
            splitHome.setResizeWeight(0.7);
            home.add(splitHome, BorderLayout.CENTER);
            return home;
        }
    }

    Result build(
            Server server,
            GestorServidores gestorServidores,
            Consumer<Server> onServerConverted
    ) {
        return new Result(server, gestorServidores, onServerConverted);
    }

    private static JButton crearBotonConversion(
            Server server,
            GestorServidores gestorServidores,
            Consumer<Server> onServerConverted
    ) {
        if (gestorServidores == null || !gestorServidores.puedeConvertirseAPlataformaCompatible(server)) {
            return null;
        }

        FlatButton button = new FlatButton();
        AppTheme.applyHeaderIconButtonStyle(button);
        button.setToolTipText("Convertir a una plataforma compatible");
        button.setIcon(SvgIconFactory.create("doraicons/box-unselected.svg", 18, 18));
        button.addActionListener(e -> {
            Server converted = gestorServidores.convertirServidorAPlataformaCompatible(server);
            if (converted != null && onServerConverted != null) {
                onServerConverted.accept(converted);
            }
        });
        return button;
    }
}
