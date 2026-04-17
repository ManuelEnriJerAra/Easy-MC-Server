package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import controlador.GestorServidores;
import modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

final class VentanaPrincipalRightContentBuilder {
    record Result(
            JPanel homePanel,
            JPanel mundoPanel,
            JPanel configPanel,
            JPanel extensionesPanel,
            JPanel statsPanel,
            JPanel infoPanel,
            JSplitPane splitHome,
            CardPanel jugadoresCard,
            JPanel consolaCard,
            PanelConsola panelConsola,
            PanelConfigServidor panelConfigServidor
    ) {
    }

    Result build(
            Server server,
            GestorServidores gestorServidores,
            int preferredConsoleHeight,
            Consumer<Server> onServerConverted
    ) {
        JPanel home = new JPanel(new BorderLayout(0, 8));
        home.setOpaque(false);

        PanelTotalServidor panelTotalServidor = new PanelTotalServidor(gestorServidores);
        PanelJugadores panelJugadores = new PanelJugadores(gestorServidores, false);
        PanelConsola panelConsola = new PanelConsola(gestorServidores);
        panelConsola.setPreferredSize(new Dimension(preferredConsoleHeight, 100));

        CardPanel headerCard = new CardPanel("Servidor seleccionado", new Insets(8, 8, 8, 8));
        headerCard.setBorder(BorderFactory.createEmptyBorder());
        JButton conversionButton = crearBotonConversion(server, gestorServidores, onServerConverted);
        if (conversionButton != null) {
            headerCard.getHeaderActionsPanel().add(conversionButton);
        }
        headerCard.getContentPanel().add(panelTotalServidor, BorderLayout.CENTER);
        home.add(headerCard, BorderLayout.NORTH);

        CardPanel jugadoresCard = new CardPanel("Jugadores", new Insets(8, 8, 8, 8));
        jugadoresCard.setBorder(BorderFactory.createEmptyBorder());
        JButton refrescarJugadoresButton = new com.formdev.flatlaf.extras.components.FlatButton();
        refrescarJugadoresButton.setToolTipText("Refrescar");
        AppTheme.applyRefreshIconButtonStyle(refrescarJugadoresButton);
        refrescarJugadoresButton.addActionListener(e -> panelJugadores.refrescarPanel());
        jugadoresCard.getHeaderActionsPanel().add(refrescarJugadoresButton);
        jugadoresCard.getContentPanel().add(panelJugadores, BorderLayout.CENTER);

        JPanel consolaCard = new JPanel(new BorderLayout());
        consolaCard.setOpaque(false);
        consolaCard.setBorder(null);
        consolaCard.add(panelConsola, BorderLayout.CENTER);

        JSplitPane splitHome = new com.formdev.flatlaf.extras.components.FlatSplitPane();
        splitHome.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitHome.setTopComponent(jugadoresCard);
        splitHome.setBottomComponent(consolaCard);
        splitHome.setResizeWeight(0.7);
        home.add(splitHome, BorderLayout.CENTER);

        PanelConfigServidor panelConfigServidor = new PanelConfigServidor(gestorServidores);
        JPanel mundo = new PanelMundo(gestorServidores, panelConfigServidor::reload);
        JPanel extensiones = new PanelExtensiones(gestorServidores, server);
        JPanel stats = new PanelEstadisticas(gestorServidores, server);
        JPanel info = new JPanel(new BorderLayout());
        info.setOpaque(false);

        return new Result(
                home,
                mundo,
                panelConfigServidor,
                extensiones,
                stats,
                info,
                splitHome,
                jugadoresCard,
                consolaCard,
                panelConsola,
                panelConfigServidor
        );
    }

    private JButton crearBotonConversion(
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
        button.setIcon(SvgIconFactory.create("easymcicons/box-unselected.svg", 18, 18));
        button.addActionListener(e -> {
            Server converted = gestorServidores.convertirServidorAPlataformaCompatible(server);
            if (converted != null && onServerConverted != null) {
                onServerConverted.accept(converted);
            }
        });
        return button;
    }
}
